import { useEffect, useState } from "react";
import ActionPanel from "./components/ActionPanel";
import AlertsPanel from "./components/AlertsPanel";
import GameOverModal from "./components/GameOverModal";
import LiveLog from "./components/LiveLog";
import StatCard from "./components/StatCard";
import TodoBoard from "./components/TodoBoard";
import {
  blockIP,
  getAlerts,
  getAvailableActions,
  getLogs,
  getSummary,
  getTodos,
  isolateHost,
  killProcess,
  removeCron,
  resolveAlert,
  startGame,
  toggleTodo,
} from "./api/client";

const PHASE_LABELS = {
  INITIAL_ACCESS: "Initial Access",
  PERSISTENCE: "Persistence",
  LATERAL_MOVEMENT: "Lateral Movement",
  EXFILTRATION: "Exfiltration",
  CONTAINED: "Contained",
};

const PHASE_ORDER = ["INITIAL_ACCESS", "PERSISTENCE", "LATERAL_MOVEMENT", "EXFILTRATION"];

function threatTone(level) {
  if (level === "HIGH") return "high";
  if (level === "MEDIUM") return "medium";
  return "low";
}

export default function App() {
  const [summary, setSummary] = useState(null);
  const [logs, setLogs] = useState([]);
  const [alerts, setAlerts] = useState([]);
  const [todos, setTodos] = useState([]);
  const [actions, setActions] = useState(null);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState("");

  async function loadDashboard() {
    try {
      const [summaryData, logsData, alertsData, todosData, actionsData] = await Promise.all([
        getSummary(),
        getLogs(),
        getAlerts(),
        getTodos(),
        getAvailableActions(),
      ]);
      setSummary(summaryData);
      setLogs(logsData);
      setAlerts(alertsData.filter((a) => a.open));
      setTodos(todosData);
      setActions(actionsData);
      setError("");
    } catch (err) {
      setError(err.message || "Unable to load dashboard data.");
    } finally {
      setLoading(false);
    }
  }

  useEffect(() => {
    loadDashboard();
    const intervalId = setInterval(loadDashboard, 4000);
    return () => clearInterval(intervalId);
  }, []);

  async function onToggleTodo(id) {
    try {
      await toggleTodo(id);
      await loadDashboard();
    } catch (err) {
      setError(err.message || "Unable to update task.");
    }
  }

  async function onRestart(difficulty) {
    try {
      await startGame(difficulty);
      await loadDashboard();
    } catch (err) {
      setError(err.message || "Unable to start game.");
    }
  }

  async function onResolveAlert(id) {
    try {
      await resolveAlert(id);
      // Optimistic: remove from UI immediately
      setAlerts((prev) => prev.filter((a) => a.id !== id));
      await loadDashboard();
    } catch (err) {
      setError(err.message || "Unable to resolve alert.");
    }
  }

  async function onBlockIP(ip) {
    try {
      await blockIP(ip);
      await loadDashboard();
    } catch (err) {
      setError(err.message || "Unable to block IP.");
    }
  }

  async function onIsolateHost(hostId) {
    try {
      await isolateHost(hostId);
      await loadDashboard();
    } catch (err) {
      setError(err.message || "Unable to isolate host.");
    }
  }

  async function onKillProcess(pid) {
    try {
      await killProcess(pid);
      await loadDashboard();
    } catch (err) {
      setError(err.message || "Unable to kill process.");
    }
  }

  async function onRemoveCron() {
    try {
      await removeCron();
      await loadDashboard();
    } catch (err) {
      setError(err.message || "Unable to remove cron.");
    }
  }

  if (loading) {
    return <main className="app loading">Loading dashboard...</main>;
  }

  const isGameOver = summary && summary.gameStatus !== "ACTIVE";

  return (
    <main className={`app threat-${threatTone(summary?.threatLevel || "LOW")}`}>
      <nav className="topbar">
        <div>
          <p className="label">Mini SOC Lab</p>
          <h1>Red Team vs Blue Team</h1>
        </div>
        <div className="topbar-right">
          <span className={`game-status-badge status-${(summary?.gameStatus || "ACTIVE").toLowerCase()}`}>
            {summary?.gameStatus || "ACTIVE"}
          </span>
          <span className="difficulty-badge">{summary?.difficulty || "MEDIUM"}</span>
          <button className="btn" onClick={() => onRestart("MEDIUM")}>New Game</button>
        </div>
      </nav>

      {error ? <p className="error-banner">{error}</p> : null}

      {/* Game State Bar */}
      <section className="game-bar">
        <div className="game-bar-item">
          <span className="game-bar-label">Phase</span>
          <span className="game-bar-value">{summary?.attackPhaseDisplay || "—"}</span>
        </div>
        <div className="game-bar-item">
          <span className="game-bar-label">Threat</span>
          <span className={`game-bar-value threat-${threatTone(summary?.threatLevel)}`}>
            {summary?.threatScore ?? 0} ({summary?.threatLevel || "LOW"})
          </span>
        </div>
        <div className="game-bar-item">
          <span className="game-bar-label">Timer</span>
          <span className="game-bar-value">{summary?.elapsedSeconds ?? 0}s</span>
        </div>
        <div className="game-bar-item score-red">
          <span className="game-bar-label">Red Score</span>
          <span className="game-bar-value">{summary?.redScore ?? 0}</span>
        </div>
        <div className="game-bar-item score-blue">
          <span className="game-bar-label">Blue Score</span>
          <span className="game-bar-value">{summary?.blueScore ?? 0}</span>
        </div>
      </section>

      {/* Threat Progress Bar */}
      <section className="threat-bar-container">
        <div className="threat-bar-track">
          <div
            className={`threat-bar-fill threat-fill-${threatTone(summary?.threatLevel)}`}
            style={{ width: `${summary?.threatScore ?? 0}%` }}
          />
        </div>
      </section>

      <section className="stats-grid">
        <StatCard
          label="Active Alerts"
          value={summary?.activeAlerts ?? 0}
          subtext={`${summary?.criticalAlerts ?? 0} critical`}
          tone="tone-red"
        />
        <StatCard
          label="Beacon Count"
          value={summary?.beaconCount ?? 0}
          subtext={summary?.persistenceActive ? "Persistence ACTIVE" : "No persistence"}
          tone="tone-amber"
        />
        <StatCard
          label="Active Hosts"
          value={summary?.activeHostCount ?? 0}
          subtext="Compromised endpoints"
          tone="tone-red"
        />
        <StatCard
          label="Blocks Fired"
          value={summary?.blocksFired ?? 0}
          subtext="Blue Team response actions"
          tone="tone-green"
        />
      </section>

      {/* Attack Phase Chain */}
      <section className="kill-chain">
        {PHASE_ORDER.map((phase) => {
          const currentIdx = PHASE_ORDER.indexOf(summary?.attackPhase);
          const idx = PHASE_ORDER.indexOf(phase);
          const state = summary?.attackPhase === "CONTAINED" ? "done"
            : idx < currentIdx ? "done"
            : idx === currentIdx ? "active"
            : "pending";
          return (
            <article className={`chain-node ${state}`} key={phase}>
              <p className="chain-number">{idx + 1}</p>
              <p className="chain-state">{PHASE_LABELS[phase]}</p>
            </article>
          );
        })}
      </section>

      {/* Blue Team Action Panel */}
      {!isGameOver && (
        <ActionPanel
          actions={actions}
          onBlockIP={onBlockIP}
          onIsolateHost={onIsolateHost}
          onKillProcess={onKillProcess}
          onRemoveCron={onRemoveCron}
        />
      )}

      <section className="dual-panels">
        <LiveLog logs={logs} />
        <AlertsPanel alerts={alerts} onResolve={isGameOver ? null : onResolveAlert} />
      </section>

      <section className="todo-grid">
        <TodoBoard team="RED" todos={todos} onToggle={onToggleTodo} />
        <TodoBoard team="BLUE" todos={todos} onToggle={onToggleTodo} />
      </section>

      {/* Game Over Modal */}
      <GameOverModal summary={summary} onRestart={onRestart} />
    </main>
  );
}
