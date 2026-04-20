import { useEffect, useState, useCallback } from 'react';
import {
  getSummary, getAlerts, blockIP, killProcess, removeCron, isolateHost,
  resolveAlert, plantHoneypot, plantHttpHoneypot, updateAlertNotes,
  getDetectionAlerts, executeRemediation, getRemediationActions,
  getCorrelatedChains,
} from '../api/client';
import { useSse } from '../hooks/useSse';
import MalwareWorkbench from '../components/MalwareWorkbench';
import NetworkMonitor from '../components/NetworkMonitor';
import KillChainProgress from '../components/KillChainProgress';
import MitreMapper from '../components/MitreMapper';
import LiveLog from '../components/LiveLog';
import ScoreDisplay from '../components/ScoreDisplay';
import { useNavigate } from 'react-router-dom';

function formatCountdown(expiresAt) {
  if (!expiresAt) return null;
  const ms = new Date(expiresAt).getTime() - Date.now();
  if (ms <= 0) return 'EXPIRED';
  const s = Math.ceil(ms / 1000);
  return `${s}s`;
}

export default function BlueTeamSOC() {
  const navigate = useNavigate();
  const [summary, setSummary] = useState(null);
  const [alerts, setAlerts] = useState([]);
  const [expandedAlert, setExpandedAlert] = useState(null);
  const [notesDraft, setNotesDraft] = useState({});
  const [honeypotPath, setHoneypotPath] = useState('');
  const [honeypotType, setHoneypotType] = useState('file');
  const [httpEndpoint, setHttpEndpoint] = useState('/api/c2/execute');
  const [, forceUpdate] = useState(0);
  const [detectionAlerts, setDetectionAlerts] = useState([]);
  const [correlationChains, setCorrelationChains] = useState([]);
  const [logs, setLogs] = useState([]);
  const [remediationActions, setRemediationActions] = useState({});

  // SSE for real-time alerts
  const { data: sseAlerts } = useSse('/api/stream/alerts', {
    onMessage: (alert) => {
      setAlerts(prev => {
        const exists = prev.findIndex(a => a.id === alert.id);
        if (exists >= 0) {
          const updated = [...prev];
          updated[exists] = alert;
          return updated;
        }
        return [alert, ...prev];
      });
      // Also update detection alerts
      setDetectionAlerts(prev => {
        const exists = prev.findIndex(a => a.id === alert.id);
        if (exists >= 0) {
          const updated = [...prev];
          updated[exists] = alert;
          return updated;
        }
        return [alert, ...prev];
      });
    }
  });

  // SSE for real-time logs
  const { data: sseLogs } = useSse('/api/stream/logs', {
    onMessage: (log) => {
      setLogs(prev => [...prev.slice(-500), log]);
    }
  });

  // Polling for summary
  useEffect(() => {
    const load = async () => {
      try {
        setSummary(await getSummary());
        const alertData = await getAlerts();
        setAlerts(alertData);
        // Fetch detection alerts and correlations
        try {
          const detAlerts = await getDetectionAlerts();
          setDetectionAlerts(detAlerts);
        } catch {}
        try {
          const chains = await getCorrelatedChains();
          setCorrelationChains(chains);
        } catch {}
      } catch {}
    };
    load();
    const iv = setInterval(load, 5000);
    return () => clearInterval(iv);
  }, []);

  // Tick for countdown
  useEffect(() => {
    const iv = setInterval(() => forceUpdate(n => n + 1), 1000);
    return () => clearInterval(iv);
  }, []);

  async function handleAction(action, params) {
    try {
      switch (action) {
        case 'kill': await killProcess(params.pid); break;
        case 'block': await blockIP(params.ip); break;
        case 'cron': await removeCron(); break;
        case 'isolate': await isolateHost(params.hostId || 'soc-victim'); break;
        case 'resolve': await resolveAlert(params.alertId); break;
        case 'remediate':
          await executeRemediation(params.alertId, params.actionType, params.targetIp);
          break;
      }
      const data = await getAlerts();
      setAlerts(data);
      setSummary(await getSummary());
      try { setDetectionAlerts(await getDetectionAlerts()); } catch {}
    } catch {}
  }

  async function handlePlantHoneypot(e) {
    e.preventDefault();
    if (honeypotType === 'file') {
      if (!honeypotPath.trim()) return;
      try {
        await plantHoneypot(honeypotPath.trim());
        setHoneypotPath('');
      } catch {}
    } else if (honeypotType === 'http') {
      try {
        await plantHttpHoneypot(httpEndpoint || '/api/c2/execute');
        setHttpEndpoint('/api/c2/execute');
      } catch {}
    }
  }

  async function handleSaveNotes(alertId) {
    try {
      await updateAlertNotes(alertId, notesDraft[alertId] || '');
    } catch {}
  }

  const openAlerts = alerts.filter(a => a.status === 'OPEN' || a.open);
  const criticalCount = openAlerts.filter(a => a.severity === 'CRITICAL').length;

  return (
    <div style={{ display: 'flex', flexDirection: 'column', minHeight: '100vh', background: 'var(--bg)' }}>
      {/* Header */}
      <nav style={{
        display: 'flex',
        alignItems: 'center',
        justifyContent: 'space-between',
        padding: '0.75rem 1rem',
        borderBottom: '1px solid var(--border)',
        background: 'rgba(15,23,48,0.8)',
      }}>
        <div style={{ display: 'flex', alignItems: 'center', gap: '1rem' }}>
          <span style={{ fontFamily: 'Orbitron, sans-serif', color: 'var(--blue)', fontSize: '1rem' }}>🔵 BLUE TEAM SOC</span>
          <span style={{ color: 'var(--muted)', fontSize: '0.8rem' }}>
            Score: <strong style={{ color: 'var(--blue)' }}>{summary?.blueScore ?? 0}</strong>
            {' | '}Alerts: <strong style={{ color: criticalCount > 0 ? 'var(--red)' : 'var(--text)' }}>{openAlerts.length}</strong>
            {criticalCount > 0 && <span style={{ color: 'var(--red)' }}> ({criticalCount} CRIT)</span>}
            {' | '}Threat: {summary?.threatScore ?? 0}%
          </span>
        </div>
        <div style={{ display: 'flex', gap: '0.5rem' }}>
          <button onClick={() => navigate('/spectator')} style={{
            padding: '0.3rem 0.8rem',
            background: 'transparent',
            border: '1px solid var(--border)',
            borderRadius: '4px',
            color: 'var(--muted)',
            cursor: 'pointer',
            fontSize: '0.75rem',
          }}>
            📡 Spectator
          </button>
          <button onClick={() => navigate('/')} style={{
            padding: '0.3rem 0.8rem',
            background: 'transparent',
            border: '1px solid var(--border)',
            borderRadius: '4px',
            color: 'var(--muted)',
            cursor: 'pointer',
            fontSize: '0.75rem',
          }}>
            Switch Role
          </button>
        </div>
      </nav>

      {/* Main 3-column layout */}
      <div style={{ display: 'flex', flex: 1, overflow: 'hidden' }}>
        {/* Left: Score & SIEM Alerts */}
        <div style={{
          width: '380px',
          borderRight: '1px solid var(--border)',
          background: 'rgba(15,23,48,0.4)',
          display: 'flex',
          flexDirection: 'column',
          flexShrink: 0,
        }}>
          {/* Score Display */}
          <div style={{ padding: '0.75rem', borderBottom: '1px solid var(--border)' }}>
            <ScoreDisplay />
          </div>

          <div style={{
            padding: '0.5rem 0.75rem',
            borderBottom: '1px solid var(--border)',
            display: 'flex',
            justifyContent: 'space-between',
            alignItems: 'center',
          }}>
            <span style={{ fontFamily: 'Orbitron, sans-serif', fontSize: '0.7rem', color: 'var(--blue)' }}>
              SIEM ALERTS
            </span>
            <span style={{ fontSize: '0.7rem', color: 'var(--muted)' }}>{openAlerts.length} open</span>
          </div>

          <div style={{ flex: 1, overflow: 'auto', padding: '0.5rem' }}>
            {openAlerts.length === 0 ? (
              <div style={{ color: 'var(--muted)', textAlign: 'center', padding: '2rem', fontSize: '0.8rem' }}>
                No active alerts. System is quiet.
              </div>
            ) : (
              openAlerts.map(alert => {
                const isExpanded = expandedAlert === alert.id;
                const countdown = formatCountdown(alert.expiresAt);
                return (
                  <div
                    key={alert.id}
                    style={{
                      marginBottom: '0.5rem',
                      background: alert.severity === 'CRITICAL' ? 'rgba(255,50,90,0.08)' : '#0a0a0a',
                      border: `1px solid ${alert.severity === 'CRITICAL' ? 'rgba(255,50,90,0.3)' : 'var(--border)'}`,
                      borderRadius: '6px',
                      overflow: 'hidden',
                      animation: 'slideIn 0.3s ease-out',
                    }}
                  >
                    <div
                      onClick={() => setExpandedAlert(isExpanded ? null : alert.id)}
                      style={{
                        padding: '0.5rem 0.6rem',
                        cursor: 'pointer',
                        display: 'flex',
                        justifyContent: 'space-between',
                        alignItems: 'flex-start',
                      }}
                    >
                      <div style={{ flex: 1 }}>
                        <div style={{ display: 'flex', alignItems: 'center', gap: '0.4rem', marginBottom: '0.2rem' }}>
                          <span style={{
                            padding: '0.1rem 0.3rem',
                            borderRadius: '3px',
                            fontSize: '0.6rem',
                            fontWeight: 700,
                            background: alert.severity === 'CRITICAL' ? 'rgba(255,50,90,0.2)' :
                              alert.severity === 'HIGH' ? 'rgba(255,190,47,0.2)' : 'rgba(24,200,255,0.2)',
                            color: alert.severity === 'CRITICAL' ? 'var(--red)' :
                              alert.severity === 'HIGH' ? 'var(--amber)' : 'var(--blue)',
                          }}>
                            {alert.severity}
                          </span>
                          <span style={{ fontSize: '0.75rem', fontWeight: 600 }}>{alert.title}</span>
                        </div>
                        <div style={{ fontSize: '0.7rem', color: 'var(--muted)' }}>
                          {alert.cmdlineRedacted ? '[REDACTED — analyze file to reveal]' : alert.detail}
                        </div>
                        {alert.mitreId && (
                          <div style={{ fontSize: '0.6rem', color: 'var(--muted)', marginTop: '0.2rem' }}>
                            {alert.mitreId}: {alert.mitreName}
                          </div>
                        )}
                      </div>
                      {countdown && (
                        <div style={{
                          minWidth: '50px',
                          textAlign: 'right',
                          fontSize: '0.7rem',
                          fontFamily: 'monospace',
                          color: countdown === 'EXPIRED' ? 'var(--red)' : 'var(--amber)',
                          fontWeight: 700,
                        }}>
                          ⏱ {countdown}
                        </div>
                      )}
                    </div>

                    {isExpanded && (
                      <div style={{ padding: '0 0.6rem 0.5rem', borderTop: '1px solid var(--border)' }}>
                        {/* Actions */}
                        <div style={{ display: 'flex', gap: '0.3rem', marginTop: '0.4rem', flexWrap: 'wrap' }}>
                          {alert.actionableFields?.killPid && (
                            <button onClick={() => handleAction('kill', { pid: alert.actionableFields.killPid })} style={actionBtn('var(--red)')}>
                              Kill PID {alert.actionableFields.killPid}
                            </button>
                          )}
                          {alert.actionableFields?.blockIp && !alert.cmdlineRedacted && (
                            <button onClick={() => handleAction('block', { ip: alert.actionableFields.blockIp })} style={actionBtn('var(--amber)')}>
                              Block {alert.actionableFields.blockIp}
                            </button>
                          )}
                          {alert.actionableFields?.removeCron && (
                            <button onClick={() => handleAction('cron', {})} style={actionBtn('var(--blue)')}>
                              Remove Cron
                            </button>
                          )}
                          <button onClick={() => handleAction('resolve', { alertId: alert.id })} style={actionBtn('var(--green)')}>
                            Resolve
                          </button>
                          {alert.mitreId && (
                            <button onClick={() => handleAction('remediate', { alertId: alert.id, actionType: 'block_ip' })} style={actionBtn('var(--amber)')}>
                              Auto-Remediate
                            </button>
                          )}
                        </div>

                        {/* Analyst notes */}
                        <div style={{ marginTop: '0.5rem' }}>
                          <textarea
                            value={notesDraft[alert.id] ?? alert.analystNotes ?? ''}
                            onChange={e => setNotesDraft(prev => ({ ...prev, [alert.id]: e.target.value }))}
                            placeholder="Analyst notes..."
                            rows={2}
                            style={{
                              width: '100%',
                              background: '#0a0a0a',
                              border: '1px solid var(--border)',
                              borderRadius: '4px',
                              padding: '0.3rem',
                              color: 'var(--text)',
                              fontSize: '0.7rem',
                              resize: 'vertical',
                            }}
                          />
                          <button onClick={() => handleSaveNotes(alert.id)} style={{
                            padding: '0.2rem 0.5rem',
                            background: 'rgba(24,200,255,0.1)',
                            border: '1px solid var(--border)',
                            borderRadius: '3px',
                            color: 'var(--blue)',
                            cursor: 'pointer',
                            fontSize: '0.65rem',
                            marginTop: '0.2rem',
                          }}>
                            Save Notes
                          </button>
                        </div>
                      </div>
                    )}
                  </div>
                );
              })
            )}
          </div>
        </div>

        {/* Center: Kill Chain + Live Log + Network Monitor */}
        <div style={{ flex: 1, display: 'flex', flexDirection: 'column', padding: '0.75rem', gap: '0.75rem', overflow: 'hidden' }}>
          {/* Kill Chain Progress */}
          <div style={{
            background: 'var(--panel)',
            border: '1px solid var(--border)',
            borderRadius: '8px',
            overflow: 'hidden',
            flexShrink: 0,
          }}>
            <KillChainProgress alerts={detectionAlerts} />
          </div>

          {/* Live Timeline Log */}
          <div style={{
            flex: 1, minHeight: 0,
            background: 'var(--panel)',
            border: '1px solid var(--border)',
            borderRadius: '8px',
            overflow: 'hidden',
          }}>
            <LiveLog logs={logs} alerts={detectionAlerts} />
          </div>

          {/* Network Monitor */}
          <div style={{
            height: '280px',
            flexShrink: 0,
            background: 'var(--panel)',
            border: '1px solid var(--border)',
            borderRadius: '8px',
            overflow: 'hidden',
          }}>
            <NetworkMonitor />
          </div>
        </div>

        {/* Right: MITRE Mapper + Defenses + Honeypot */}
        <div style={{
          width: '300px',
          borderLeft: '1px solid var(--border)',
          background: 'rgba(15,23,48,0.4)',
          padding: '0.75rem',
          overflow: 'auto',
          flexShrink: 0,
        }}>
          {/* MITRE Technique Mapper */}
          <div style={{
            background: 'var(--panel)',
            border: '1px solid var(--border)',
            borderRadius: '8px',
            marginBottom: '0.75rem',
            overflow: 'hidden',
          }}>
            <MitreMapper alerts={detectionAlerts} />
          </div>

          {/* Correlation Chains */}
          {correlationChains.length > 0 && (
            <div style={{ marginBottom: '0.75rem' }}>
              <div style={{ fontFamily: 'Orbitron, sans-serif', fontSize: '0.65rem', color: 'var(--red)', marginBottom: '0.3rem' }}>
                ATTACK CHAINS ({correlationChains.length})
              </div>
              {correlationChains.map(chain => (
                <div key={chain.chainId} style={{
                  padding: '0.4rem',
                  background: chain.status === 'COMPLETED' ? 'rgba(255,50,90,0.1)' :
                    chain.status === 'BLOCKED' ? 'rgba(43,227,138,0.1)' : '#0a0a0a',
                  border: `1px solid ${chain.status === 'COMPLETED' ? 'var(--red)' :
                    chain.status === 'BLOCKED' ? 'var(--green)' : 'var(--border)'}`,
                  borderRadius: '4px',
                  marginBottom: '0.3rem',
                  fontSize: '0.7rem',
                }}>
                  <div style={{ fontWeight: 600, color: chain.status === 'BLOCKED' ? 'var(--green)' : 'var(--text)' }}>
                    {chain.name}
                  </div>
                  <div style={{ fontSize: '0.6rem', color: 'var(--muted)', marginTop: '0.1rem' }}>
                    Techniques: {chain.mitreIds?.join(' → ')} | Score: {chain.score}
                  </div>
                  <div style={{ fontSize: '0.6rem', marginTop: '0.1rem' }}>
                    <span style={{
                      padding: '0.1rem 0.2rem', borderRadius: '2px',
                      background: chain.status === 'COMPLETED' ? 'rgba(255,50,90,0.2)' :
                        chain.status === 'BLOCKED' ? 'rgba(43,227,138,0.2)' : 'rgba(255,190,47,0.2)',
                      color: chain.status === 'COMPLETED' ? 'var(--red)' :
                        chain.status === 'BLOCKED' ? 'var(--green)' : 'var(--amber)',
                      fontSize: '0.55rem', fontWeight: 600,
                    }}>
                      {chain.status}
                    </span>
                  </div>
                </div>
              ))}
            </div>
          )}

          <div style={{ fontFamily: 'Orbitron, sans-serif', fontSize: '0.7rem', color: 'var(--blue)', marginBottom: '0.75rem' }}>
            DEFENSE CONTROLS
          </div>

          {/* Quick Actions */}
          <div style={{ marginBottom: '1rem' }}>
            <div style={{ fontSize: '0.7rem', color: 'var(--muted)', marginBottom: '0.3rem' }}>Quick Response</div>
            <div style={{ display: 'flex', flexDirection: 'column', gap: '0.3rem' }}>
              <button onClick={() => handleAction('cron', {})} style={actionBtn('var(--blue)')}>
                Remove Cron Persistence
              </button>
              <button onClick={() => handleAction('isolate', { hostId: 'soc-victim' })} style={actionBtn('var(--red)')}>
                ☢ Isolate Host (Nuclear)
              </button>
            </div>
          </div>

          {/* Honeypot */}
          <div style={{ marginBottom: '1rem' }}>
            <div style={{ fontSize: '0.7rem', color: 'var(--muted)', marginBottom: '0.3rem' }}>🍯 Plant Honeypot</div>
            <form onSubmit={handlePlantHoneypot} style={{ display: 'flex', flexDirection: 'column', gap: '0.3rem' }}>
              <select
                value={honeypotType}
                onChange={e => setHoneypotType(e.target.value)}
                style={{
                  background: '#0a0a0a',
                  border: '1px solid var(--border)',
                  borderRadius: '4px',
                  padding: '0.3rem',
                  color: 'var(--text)',
                  fontSize: '0.7rem',
                }}
              >
                <option value="file">📁 File Honeypot</option>
                <option value="http">🌐 HTTP C2 Endpoint</option>
              </select>

              {honeypotType === 'file' ? (
                <div style={{ display: 'flex', gap: '0.3rem' }}>
                  <input
                    value={honeypotPath}
                    onChange={e => setHoneypotPath(e.target.value)}
                    placeholder="/tmp/.secrets.txt"
                    style={{
                      flex: 1,
                      background: '#0a0a0a',
                      border: '1px solid var(--border)',
                      borderRadius: '4px',
                      padding: '0.3rem',
                      color: 'var(--text)',
                      fontFamily: 'monospace',
                      fontSize: '0.7rem',
                    }}
                  />
                  <button type="submit" style={{
                    padding: '0.3rem 0.5rem',
                    background: 'rgba(43,227,138,0.15)',
                    border: '1px solid var(--green)',
                    borderRadius: '4px',
                    color: 'var(--green)',
                    cursor: 'pointer',
                    fontSize: '0.7rem',
                  }}>
                    Plant
                  </button>
                </div>
              ) : (
                <div style={{ display: 'flex', gap: '0.3rem' }}>
                  <input
                    value={httpEndpoint}
                    onChange={e => setHttpEndpoint(e.target.value)}
                    placeholder="/api/c2/execute"
                    style={{
                      flex: 1,
                      background: '#0a0a0a',
                      border: '1px solid var(--border)',
                      borderRadius: '4px',
                      padding: '0.3rem',
                      color: 'var(--text)',
                      fontFamily: 'monospace',
                      fontSize: '0.7rem',
                    }}
                  />
                  <button type="submit" style={{
                    padding: '0.3rem 0.5rem',
                    background: 'rgba(43,227,138,0.15)',
                    border: '1px solid var(--green)',
                    borderRadius: '4px',
                    color: 'var(--green)',
                    cursor: 'pointer',
                    fontSize: '0.7rem',
                  }}>
                    Deploy
                  </button>
                </div>
              )}
            </form>
          </div>

          {/* Threat gauge */}
          <div style={{ marginBottom: '1rem' }}>
            <div style={{ fontSize: '0.7rem', color: 'var(--muted)', marginBottom: '0.3rem' }}>Threat Level</div>
            <div style={{
              height: '8px',
              background: 'rgba(255,255,255,0.1)',
              borderRadius: '4px',
              overflow: 'hidden',
            }}>
              <div style={{
                height: '100%',
                width: `${summary?.threatScore ?? 0}%`,
                background: (summary?.threatScore ?? 0) > 70 ? 'var(--red)' : (summary?.threatScore ?? 0) > 40 ? 'var(--amber)' : 'var(--green)',
                borderRadius: '4px',
                transition: 'width 0.5s',
              }} />
            </div>
            <div style={{ fontSize: '0.7rem', textAlign: 'center', marginTop: '0.2rem', color: 'var(--muted)' }}>
              {summary?.threatScore ?? 0}% — {summary?.threatLevel || 'LOW'}
            </div>
          </div>

          {/* Game status */}
          <div style={{
            padding: '0.5rem',
            background: '#0a0a0a',
            border: '1px solid var(--border)',
            borderRadius: '4px',
            fontSize: '0.7rem',
          }}>
            <div>Phase: {summary?.attackPhaseDisplay || '—'}</div>
            <div>Beacons: {summary?.beaconCount ?? 0}</div>
            <div>Active hosts: {summary?.activeHostCount ?? 0}</div>
            <div>Time: {summary?.elapsedSeconds ?? 0}s</div>
          </div>
        </div>
      </div>

      <style>{`
        @keyframes slideIn {
          from { opacity: 0; transform: translateX(-10px); }
          to { opacity: 1; transform: translateX(0); }
        }
      `}</style>
    </div>
  );
}

function actionBtn(color) {
  return {
    padding: '0.25rem 0.5rem',
    background: `${color}15`,
    border: `1px solid ${color}`,
    borderRadius: '4px',
    color: color,
    cursor: 'pointer',
    fontSize: '0.65rem',
    fontWeight: 600,
  };
}
