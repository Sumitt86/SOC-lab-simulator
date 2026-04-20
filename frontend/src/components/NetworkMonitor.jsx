import { useState, useEffect, useCallback } from 'react';
import {
  startNetworkMonitoring, stopNetworkMonitoring,
  getNetworkEvents, getNetworkStats,
  networkBlockIp, networkRejectIp, networkTerminateConnections,
  activeNetworkProbe,
} from '../api/client';
import { useSse } from '../hooks/useSse';

export default function NetworkMonitor() {
  const [active, setActive] = useState(false);
  const [events, setEvents] = useState([]);
  const [stats, setStats] = useState([]);
  const [alerts, setAlerts] = useState([]);
  const [probeResult, setProbeResult] = useState(null);
  const [loading, setLoading] = useState(false);
  const [tab, setTab] = useState('live'); // live | stats | probe

  // SSE for real-time network alerts
  const { data: sseData } = useSse('/api/stream/alerts', {
    onMessage: (msg) => {
      if (msg && (msg.classification === 'PORT_SCAN' || msg.classification === 'BRUTE_FORCE')) {
        setAlerts(prev => [msg, ...prev].slice(0, 50));
      }
    }
  });

  // Poll network events
  useEffect(() => {
    if (!active) return;
    const load = async () => {
      try {
        const evtData = await getNetworkEvents(100);
        if (evtData.events) setEvents(evtData.events);
        const statsData = await getNetworkStats();
        if (statsData.stats) setStats(statsData.stats);
      } catch {}
    };
    load();
    const iv = setInterval(load, 2000);
    return () => clearInterval(iv);
  }, [active]);

  // Check initial state
  useEffect(() => {
    (async () => {
      try {
        const data = await getNetworkEvents(1);
        setActive(data.active || false);
      } catch {}
    })();
  }, []);

  async function handleToggleMonitoring() {
    setLoading(true);
    try {
      if (active) {
        await stopNetworkMonitoring();
        setActive(false);
      } else {
        await startNetworkMonitoring();
        setActive(true);
      }
    } catch {} finally {
      setLoading(false);
    }
  }

  async function handleBlock(ip) {
    try { await networkBlockIp(ip); } catch {}
  }
  async function handleReject(ip) {
    try { await networkRejectIp(ip); } catch {}
  }
  async function handleTerminate(ip) {
    try { await networkTerminateConnections(ip); } catch {}
  }
  async function handleProbe() {
    setLoading(true);
    try {
      const result = await activeNetworkProbe();
      setProbeResult(result);
    } catch {} finally {
      setLoading(false);
    }
  }

  const TABS = [
    { key: 'live', label: 'Live Traffic' },
    { key: 'stats', label: 'IP Stats' },
    { key: 'alerts', label: `Alerts (${alerts.length})` },
    { key: 'probe', label: 'Active Probe' },
  ];

  return (
    <div style={{ display: 'flex', flexDirection: 'column', height: '100%' }}>
      {/* Header */}
      <div style={{
        display: 'flex', justifyContent: 'space-between', alignItems: 'center',
        padding: '0.5rem 0.75rem', borderBottom: '1px solid var(--border)',
      }}>
        <div style={{ display: 'flex', alignItems: 'center', gap: '0.5rem' }}>
          <span style={{ fontFamily: 'Orbitron, sans-serif', fontSize: '0.7rem', color: 'var(--blue)' }}>
            NETWORK MONITOR
          </span>
          <span style={{
            display: 'inline-block', width: 8, height: 8, borderRadius: '50%',
            background: active ? 'var(--green)' : 'var(--muted)',
            animation: active ? 'pulse 1.5s infinite' : 'none',
          }} />
        </div>
        <button
          onClick={handleToggleMonitoring}
          disabled={loading}
          style={{
            padding: '0.25rem 0.6rem',
            background: active ? 'rgba(255,50,90,0.15)' : 'rgba(43,227,138,0.15)',
            border: `1px solid ${active ? 'var(--red)' : 'var(--green)'}`,
            borderRadius: '4px',
            color: active ? 'var(--red)' : 'var(--green)',
            cursor: 'pointer', fontSize: '0.7rem', fontWeight: 600,
          }}
        >
          {loading ? '...' : active ? '■ Stop' : '▶ Start'}
        </button>
      </div>

      {/* Tabs */}
      <div style={{ display: 'flex', borderBottom: '1px solid var(--border)', background: 'rgba(15,23,48,0.6)' }}>
        {TABS.map(t => (
          <button key={t.key} onClick={() => setTab(t.key)} style={{
            flex: 1, padding: '0.4rem', background: tab === t.key ? 'rgba(24,200,255,0.1)' : 'transparent',
            border: 'none', borderBottom: tab === t.key ? '2px solid var(--blue)' : '2px solid transparent',
            color: tab === t.key ? 'var(--blue)' : 'var(--muted)', cursor: 'pointer',
            fontSize: '0.7rem', fontFamily: 'Orbitron, sans-serif',
          }}>
            {t.label}
          </button>
        ))}
      </div>

      {/* Content */}
      <div style={{ flex: 1, overflow: 'auto', padding: '0.5rem' }}>

        {tab === 'live' && (
          <div>
            {!active && (
              <div style={{ color: 'var(--muted)', textAlign: 'center', padding: '2rem', fontSize: '0.8rem' }}>
                Network monitoring is inactive. Click Start to begin capturing traffic.
              </div>
            )}
            {active && events.length === 0 && (
              <div style={{ color: 'var(--muted)', textAlign: 'center', padding: '2rem', fontSize: '0.8rem' }}>
                Listening for network activity...
              </div>
            )}
            {events.length > 0 && (
              <div style={{ fontSize: '0.7rem' }}>
                <div style={{
                  display: 'grid', gridTemplateColumns: '55px 85px 1fr 55px 1fr 55px 60px',
                  gap: '0.2rem', padding: '0.3rem 0.4rem', background: 'rgba(24,200,255,0.08)',
                  borderRadius: '4px 4px 0 0', fontFamily: 'Orbitron, sans-serif', fontSize: '0.6rem',
                  color: 'var(--blue)',
                }}>
                  <span>PROTO</span><span>STATE</span><span>SOURCE</span>
                  <span>PORT</span><span>DEST</span><span>PORT</span><span>FLAGS</span>
                </div>
                {events.slice(0, 50).map((evt, i) => (
                  <div key={evt.id || i} style={{
                    display: 'grid', gridTemplateColumns: '55px 85px 1fr 55px 1fr 55px 60px',
                    gap: '0.2rem', padding: '0.2rem 0.4rem',
                    background: i % 2 === 0 ? '#0a0a0a' : 'transparent',
                    borderLeft: evt.state === 'SYN' ? '2px solid var(--amber)' :
                      evt.state === 'ESTAB' ? '2px solid var(--red)' : '2px solid transparent',
                    fontFamily: 'monospace', fontSize: '0.65rem',
                  }}>
                    <span style={{ color: 'var(--blue)' }}>{evt.protocol}</span>
                    <span style={{
                      color: evt.state === 'ESTAB' ? 'var(--red)' :
                        evt.state === 'SYN' ? 'var(--amber)' :
                        evt.state === 'LISTEN' ? 'var(--green)' : 'var(--muted)',
                    }}>{evt.state}</span>
                    <span>{evt.sourceIp || '—'}</span>
                    <span>{evt.sourcePort || '—'}</span>
                    <span>{evt.destIp || '—'}</span>
                    <span>{evt.destPort || '—'}</span>
                    <span style={{ color: 'var(--amber)' }}>{evt.flags || '—'}</span>
                  </div>
                ))}
              </div>
            )}
          </div>
        )}

        {tab === 'stats' && (
          <div>
            {stats.length === 0 ? (
              <div style={{ color: 'var(--muted)', textAlign: 'center', padding: '2rem', fontSize: '0.8rem' }}>
                No connection data yet.
              </div>
            ) : (
              stats.map((s, i) => (
                <div key={s.ip} style={{
                  padding: '0.5rem', marginBottom: '0.4rem',
                  background: s.portScanDetected ? 'rgba(255,50,90,0.08)' : '#0a0a0a',
                  border: `1px solid ${s.portScanDetected ? 'rgba(255,50,90,0.3)' : 'var(--border)'}`,
                  borderRadius: '6px', fontSize: '0.75rem',
                }}>
                  <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', marginBottom: '0.3rem' }}>
                    <span style={{ fontFamily: 'monospace', fontWeight: 700 }}>{s.ip}</span>
                    <div style={{ display: 'flex', gap: '0.2rem' }}>
                      {s.portScanDetected && (
                        <span style={{ padding: '0.1rem 0.3rem', borderRadius: 3, fontSize: '0.6rem', fontWeight: 700, background: 'rgba(255,50,90,0.2)', color: 'var(--red)' }}>
                          PORT SCAN
                        </span>
                      )}
                      {s.bruteForceDetected && (
                        <span style={{ padding: '0.1rem 0.3rem', borderRadius: 3, fontSize: '0.6rem', fontWeight: 700, background: 'rgba(255,190,47,0.2)', color: 'var(--amber)' }}>
                          BRUTE FORCE
                        </span>
                      )}
                    </div>
                  </div>
                  <div style={{ color: 'var(--muted)', fontSize: '0.7rem', marginBottom: '0.3rem' }}>
                    Connections: {s.totalConnections} | Unique Ports: {s.uniquePorts}
                    {s.recentPorts?.length > 0 && (
                      <span> | Ports: {s.recentPorts.slice(0, 10).join(', ')}{s.recentPorts.length > 10 ? '...' : ''}</span>
                    )}
                  </div>
                  <div style={{ display: 'flex', gap: '0.3rem' }}>
                    <button onClick={() => handleBlock(s.ip)} style={actionBtn('var(--red)')}>Block</button>
                    <button onClick={() => handleReject(s.ip)} style={actionBtn('var(--amber)')}>Reject</button>
                    <button onClick={() => handleTerminate(s.ip)} style={actionBtn('var(--blue)')}>Kill Conns</button>
                  </div>
                </div>
              ))
            )}
          </div>
        )}

        {tab === 'alerts' && (
          <div>
            {alerts.length === 0 ? (
              <div style={{ color: 'var(--muted)', textAlign: 'center', padding: '2rem', fontSize: '0.8rem' }}>
                No network alerts yet. Start monitoring to detect attacks.
              </div>
            ) : (
              alerts.map((a, i) => (
                <div key={a.id || i} style={{
                  padding: '0.5rem', marginBottom: '0.4rem',
                  background: a.severity === 'CRITICAL' ? 'rgba(255,50,90,0.08)' : 'rgba(255,190,47,0.08)',
                  border: `1px solid ${a.severity === 'CRITICAL' ? 'rgba(255,50,90,0.3)' : 'rgba(255,190,47,0.3)'}`,
                  borderRadius: '6px',
                  animation: 'slideIn 0.3s ease-out',
                }}>
                  <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'flex-start', marginBottom: '0.2rem' }}>
                    <div>
                      <span style={{
                        padding: '0.1rem 0.3rem', borderRadius: 3, fontSize: '0.6rem', fontWeight: 700,
                        background: a.severity === 'CRITICAL' ? 'rgba(255,50,90,0.2)' : 'rgba(255,190,47,0.2)',
                        color: a.severity === 'CRITICAL' ? 'var(--red)' : 'var(--amber)',
                        marginRight: '0.3rem',
                      }}>
                        {a.severity}
                      </span>
                      <span style={{ fontSize: '0.8rem', fontWeight: 600 }}>{a.title}</span>
                    </div>
                    <span style={{ fontSize: '0.65rem', color: 'var(--muted)' }}>{a.mitreId}</span>
                  </div>
                  <div style={{ fontSize: '0.7rem', color: 'var(--muted)', marginBottom: '0.3rem' }}>{a.detail}</div>
                  <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center' }}>
                    <div style={{ fontSize: '0.65rem', color: 'var(--muted)' }}>
                      IP: <span style={{ fontFamily: 'monospace', color: 'var(--text)' }}>{a.sourceIp}</span>
                      {a.packetCount > 0 && <span> | Packets: {a.packetCount}</span>}
                      {a.portsScanned?.length > 0 && <span> | Ports: {a.portsScanned.slice(0, 8).join(', ')}</span>}
                    </div>
                    <div style={{ display: 'flex', gap: '0.2rem' }}>
                      <button onClick={() => handleBlock(a.sourceIp)} style={actionBtn('var(--red)')}>Block</button>
                      <button onClick={() => handleTerminate(a.sourceIp)} style={actionBtn('var(--blue)')}>Kill</button>
                    </div>
                  </div>
                </div>
              ))
            )}
          </div>
        )}

        {tab === 'probe' && (
          <div>
            <div style={{ marginBottom: '0.75rem' }}>
              <button onClick={handleProbe} disabled={loading} style={{
                padding: '0.4rem 1rem', background: 'rgba(24,200,255,0.15)',
                border: '1px solid var(--blue)', borderRadius: '4px',
                color: 'var(--blue)', cursor: 'pointer', fontSize: '0.8rem',
              }}>
                {loading ? 'Scanning...' : '🔍 Run Active Network Probe'}
              </button>
              <div style={{ fontSize: '0.7rem', color: 'var(--muted)', marginTop: '0.3rem' }}>
                Scans listening ports, outbound connections, firewall rules, and open sockets.
              </div>
            </div>

            {probeResult && (
              <div>
                <div style={{ fontFamily: 'Orbitron, sans-serif', fontSize: '0.7rem', color: 'var(--blue)', marginBottom: '0.4rem' }}>
                  PROBE RESULTS ({probeResult.count || 0} findings)
                </div>
                {(probeResult.findings || []).map((f, i) => (
                  <div key={i} style={{
                    padding: '0.3rem 0.5rem', marginBottom: '0.2rem',
                    background: '#0a0a0a', borderRadius: '4px',
                    borderLeft: `2px solid ${
                      f.type === 'OUTBOUND_CONNECTION' ? 'var(--red)' :
                      f.type === 'LISTENING_PORT' ? 'var(--amber)' :
                      f.type === 'OPEN_SOCKET' ? 'var(--blue)' : 'var(--muted)'
                    }`,
                  }}>
                    <span style={{
                      fontSize: '0.6rem', fontWeight: 700,
                      color: f.type === 'OUTBOUND_CONNECTION' ? 'var(--red)' :
                        f.type === 'LISTENING_PORT' ? 'var(--amber)' : 'var(--blue)',
                      marginRight: '0.4rem',
                    }}>
                      {f.type}
                    </span>
                    <pre style={{
                      margin: '0.2rem 0 0', fontFamily: 'monospace', fontSize: '0.65rem',
                      color: 'var(--text)', whiteSpace: 'pre-wrap', wordBreak: 'break-all',
                    }}>
                      {f.detail}
                    </pre>
                  </div>
                ))}
              </div>
            )}
          </div>
        )}
      </div>

      <style>{`
        @keyframes pulse {
          0%, 100% { opacity: 1; }
          50% { opacity: 0.3; }
        }
      `}</style>
    </div>
  );
}

function actionBtn(color) {
  return {
    padding: '0.2rem 0.4rem',
    background: `${color}15`,
    border: `1px solid ${color}`,
    borderRadius: '3px',
    color: color,
    cursor: 'pointer',
    fontSize: '0.6rem',
    fontWeight: 600,
  };
}
