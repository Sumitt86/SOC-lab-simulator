import { useEffect, useState } from 'react';
import { useNavigate } from 'react-router-dom';
import ActionPanel from '../components/ActionPanel';
import AlertsPanel from '../components/AlertsPanel';
import BlueTeamPanel from '../components/BlueTeamPanel';
import GameOverModal from '../components/GameOverModal';
import LiveLog from '../components/LiveLog';
import RedTeamPanel from '../components/RedTeamPanel';
import StatCard from '../components/StatCard';
import TodoBoard from '../components/TodoBoard';
import {
  blockIP, blueTeamBehavioralAnalysis, blueTeamConnections,
  blueTeamFiles, blueTeamProcesses, blueTeamScanFile,
  getAlerts, getAvailableActions, getLogs, getSummary,
  getTodos, isolateHost, killProcess, redTeamExecute,
  redTeamHistory, redTeamUploadMalware, removeCron,
  resolveAlert, startGame, toggleTodo,
} from '../api/client';

const PHASE_LABELS = {
  INITIAL_ACCESS: 'Initial Access',
  PERSISTENCE: 'Persistence',
  LATERAL_MOVEMENT: 'Lateral Movement',
  EXFILTRATION: 'Exfiltration',
  CONTAINED: 'Contained',
};
const PHASE_ORDER = ['INITIAL_ACCESS', 'PERSISTENCE', 'LATERAL_MOVEMENT', 'EXFILTRATION'];

function threatTone(level) {
  if (level === 'HIGH') return 'high';
  if (level === 'MEDIUM') return 'medium';
  return 'low';
}

export default function MissionControl() {
  const navigate = useNavigate();
  const [summary, setSummary] = useState(null);
  const [logs, setLogs] = useState([]);
  const [alerts, setAlerts] = useState([]);
  const [todos, setTodos] = useState([]);
  const [actions, setActions] = useState(null);
  const [redHistory, setRedHistory] = useState([]);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState('');

  async function loadDashboard() {
    try {
      const [s, l, a, t, ac, h] = await Promise.all([
        getSummary(), getLogs(), getAlerts(), getTodos(),
        getAvailableActions(), redTeamHistory().catch(() => []),
      ]);
      setSummary(s); setLogs(l); setAlerts(a.filter(x => x.open !== false)); setTodos(t); setActions(ac); setRedHistory(h);
      setError('');
    } catch (err) {
      setError(err.message || 'Unable to load dashboard');
    } finally { setLoading(false); }
  }

  useEffect(() => { loadDashboard(); const iv = setInterval(loadDashboard, 4000); return () => clearInterval(iv); }, []);

  const wrap = fn => async (...args) => { try { await fn(...args); await loadDashboard(); } catch (e) { setError(e.message); } };
  const onToggleTodo = wrap((id) => toggleTodo(id));
  const onRestart = wrap((d) => startGame(d));
  const onResolveAlert = wrap((id) => resolveAlert(id));
  const onBlockIP = wrap((ip) => blockIP(ip));
  const onIsolateHost = wrap((h) => isolateHost(h));
  const onKillProcess = wrap((pid) => killProcess(pid));
  const onRemoveCron = wrap(() => removeCron());
  const onRedTeamExecute = wrap((c, cmd) => redTeamExecute(c, cmd));

  if (loading) return <main className="app loading">Loading dashboard...</main>;

  const isGameOver = summary && summary.gameStatus !== 'ACTIVE';

  return (
    <main className={`app threat-${threatTone(summary?.threatLevel || 'LOW')}`}>
      <nav className="topbar">
        <div>
          <p className="label">Mini SOC Lab</p>
          <h1>Mission Control</h1>
        </div>
        <div className="topbar-right">
          <span className={`game-status-badge status-${(summary?.gameStatus || 'ACTIVE').toLowerCase()}`}>
            {summary?.gameStatus || 'ACTIVE'}
          </span>
          <span className="difficulty-badge">{summary?.difficulty || 'MEDIUM'}</span>
          <button className="btn" onClick={() => navigate('/red')}>🔴 Red</button>
          <button className="btn" onClick={() => navigate('/blue')}>🔵 Blue</button>
          <button className="btn" onClick={() => navigate('/')}>Switch Role</button>
          <button className="btn" onClick={() => onRestart('MEDIUM')}>New Game</button>
        </div>
      </nav>

      {error && <p className="error-banner">{error}</p>}

      <section className="game-bar">
        <div className="game-bar-item">
          <span className="game-bar-label">Phase</span>
          <span className="game-bar-value">{summary?.attackPhaseDisplay || '—'}</span>
        </div>
        <div className="game-bar-item">
          <span className="game-bar-label">Threat</span>
          <span className={`game-bar-value threat-${threatTone(summary?.threatLevel)}`}>
            {summary?.threatScore ?? 0} ({summary?.threatLevel || 'LOW'})
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

      <section className="threat-bar-container">
        <div className="threat-bar-track">
          <div className={`threat-bar-fill threat-fill-${threatTone(summary?.threatLevel)}`}
            style={{ width: `${summary?.threatScore ?? 0}%` }} />
        </div>
      </section>

      <section className="stats-grid">
        <StatCard label="Active Alerts" value={summary?.activeAlerts ?? 0} subtext={`${summary?.criticalAlerts ?? 0} critical`} tone="tone-red" />
        <StatCard label="Beacon Count" value={summary?.beaconCount ?? 0} subtext={summary?.persistenceActive ? 'Persistence ACTIVE' : 'No persistence'} tone="tone-amber" />
        <StatCard label="Active Hosts" value={summary?.activeHostCount ?? 0} subtext="Compromised endpoints" tone="tone-red" />
        <StatCard label="Blocks Fired" value={summary?.blocksFired ?? 0} subtext="Blue Team response actions" tone="tone-green" />
      </section>

      <section className="kill-chain">
        {PHASE_ORDER.map((phase) => {
          const currentIdx = PHASE_ORDER.indexOf(summary?.attackPhase);
          const idx = PHASE_ORDER.indexOf(phase);
          const state = summary?.attackPhase === 'CONTAINED' ? 'done'
            : idx < currentIdx ? 'done' : idx === currentIdx ? 'active' : 'pending';
          return (
            <article className={`chain-node ${state}`} key={phase}>
              <p className="chain-number">{idx + 1}</p>
              <p className="chain-state">{PHASE_LABELS[phase]}</p>
            </article>
          );
        })}
      </section>

      <section className="team-panels">
        <RedTeamPanel onExecute={onRedTeamExecute} onUploadMalware={redTeamUploadMalware} history={redHistory} />
        <BlueTeamPanel
          onGetProcesses={blueTeamProcesses} onGetConnections={blueTeamConnections}
          onGetFiles={blueTeamFiles} onScanFile={blueTeamScanFile}
          onBehavioralAnalysis={blueTeamBehavioralAnalysis}
        />
      </section>

      {!isGameOver && (
        <ActionPanel actions={actions} onBlockIP={onBlockIP} onIsolateHost={onIsolateHost}
          onKillProcess={onKillProcess} onRemoveCron={onRemoveCron} />
      )}

      <section className="dual-panels">
        <LiveLog logs={logs} />
        <AlertsPanel alerts={alerts} onResolve={isGameOver ? null : onResolveAlert} />
      </section>

      <section className="todo-grid">
        <TodoBoard team="RED" todos={todos} onToggle={onToggleTodo} />
        <TodoBoard team="BLUE" todos={todos} onToggle={onToggleTodo} />
      </section>

      {isGameOver && <GameOverModal summary={summary} onRestart={onRestart} />}
    </main>
  );
}
