import { useEffect, useMemo, useState } from "react";
import AlertsPanel from "./components/AlertsPanel";
import LiveLog from "./components/LiveLog";
import StatCard from "./components/StatCard";
import TodoBoard from "./components/TodoBoard";
import {
  getAlerts,
  getLogs,
  getSummary,
  getTodos,
  resetSimulation,
  toggleTodo
} from "./api/client";

const SCENARIOS = [
  {
    id: "s1",
    title: "Scenario 1",
    subtitle: "Beaconing",
    red: "Run beacon from victim to C2 every 30 seconds.",
    blue: "Detect repeated callback pattern and terminate process."
  },
  {
    id: "s2",
    title: "Scenario 2",
    subtitle: "Persistence",
    red: "Install cron or registry persistence path.",
    blue: "Detect and remove malicious persistence entries."
  },
  {
    id: "s3",
    title: "Scenario 3",
    subtitle: "File Manipulation",
    red: "Drop hidden payload files into temporary paths.",
    blue: "Trigger file integrity monitoring and quarantine files."
  },
  {
    id: "s4",
    title: "Scenario 4",
    subtitle: "Full Chain",
    red: "Execute beacon + persistence + exfil sequence.",
    blue: "Detect each phase and execute playbooks before exfil completes."
  }
];

function toneByAlerts(activeAlerts) {
  if (activeAlerts >= 6) return "high";
  if (activeAlerts >= 3) return "medium";
  return "low";
}

export default function App() {
  const [summary, setSummary] = useState(null);
  const [logs, setLogs] = useState([]);
  const [alerts, setAlerts] = useState([]);
  const [todos, setTodos] = useState([]);
  const [activeScenario, setActiveScenario] = useState("s1");
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState("");

  const activeScenarioData = useMemo(
    () => SCENARIOS.find((scenario) => scenario.id === activeScenario),
    [activeScenario]
  );

  async function loadDashboard() {
    try {
      const [summaryData, logsData, alertsData, todosData] = await Promise.all([
        getSummary(),
        getLogs(),
        getAlerts(),
        getTodos()
      ]);
      setSummary(summaryData);
      setLogs(logsData);
      setAlerts(alertsData.filter((alert) => alert.open));
      setTodos(todosData);
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

  async function onResetSimulation() {
    try {
      await resetSimulation();
      await loadDashboard();
    } catch (err) {
      setError(err.message || "Unable to reset simulation.");
    }
  }

  if (loading) {
    return <main className="app loading">Loading dashboard...</main>;
  }

  return (
    <main className={`app threat-${toneByAlerts(summary.activeAlerts)}`}>
      <nav className="topbar">
        <div>
          <p className="label">Mini SOC Lab</p>
          <h1>Red Team vs Blue Team</h1>
        </div>
        <button className="btn" onClick={onResetSimulation}>Reset Simulation</button>
      </nav>

      {error ? <p className="error-banner">{error}</p> : null}

      <section className="stats-grid">
        <StatCard
          label="Active Alerts"
          value={summary.activeAlerts}
          subtext={`${summary.criticalAlerts} critical`}
          tone="tone-red"
        />
        <StatCard
          label="Events / Min"
          value={summary.eventsPerMinute}
          subtext="Streaming from backend simulator"
          tone="tone-blue"
        />
        <StatCard
          label="Beacon Count"
          value={summary.beaconCount}
          subtext="Incremented by simulation ticks"
          tone="tone-amber"
        />
        <StatCard
          label="Blocks Fired"
          value={summary.blocksFired}
          subtext="Automated response actions"
          tone="tone-green"
        />
      </section>

      <section className="kill-chain">
        {Array.from({ length: 7 }).map((_, idx) => {
          const phase = idx + 1;
          const state = phase < summary.simulationPhase ? "done" : phase === summary.simulationPhase ? "active" : "pending";
          return (
            <article className={`chain-node ${state}`} key={phase}>
              <p className="chain-number">{phase}</p>
              <p className="chain-state">{state.toUpperCase()}</p>
            </article>
          );
        })}
      </section>

      <section className="dual-panels">
        <LiveLog logs={logs} />
        <AlertsPanel alerts={alerts} />
      </section>

      <section className="todo-grid">
        <TodoBoard team="RED" todos={todos} onToggle={onToggleTodo} />
        <TodoBoard team="BLUE" todos={todos} onToggle={onToggleTodo} />
      </section>

      <section className="scenario-panel">
        <header className="scenario-tabs">
          {SCENARIOS.map((scenario) => (
            <button
              key={scenario.id}
              className={`scenario-tab ${activeScenario === scenario.id ? "active" : ""}`}
              onClick={() => setActiveScenario(scenario.id)}
            >
              <span>{scenario.title}</span>
              <small>{scenario.subtitle}</small>
            </button>
          ))}
        </header>

        <article className="scenario-body">
          <h2>{activeScenarioData.title}: {activeScenarioData.subtitle}</h2>
          <div className="scenario-columns">
            <div>
              <h3>Red Team</h3>
              <p>{activeScenarioData.red}</p>
            </div>
            <div>
              <h3>Blue Team</h3>
              <p>{activeScenarioData.blue}</p>
            </div>
          </div>
        </article>
      </section>
    </main>
  );
}
