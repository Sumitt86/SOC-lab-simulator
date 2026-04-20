import { useState, useRef, useEffect } from 'react';

function getEventType(entry) {
  if (entry.type === 'remediation' || entry.severity === 'REMEDIATION') return 'remediation';
  if (entry.type === 'detection' || entry.severity === 'CRIT' || entry.severity === 'WARN' || entry.mitreId) return 'detection';
  if (entry.type === 'attack') return 'attack';
  return 'info';
}

const DOT_COLORS = {
  attack: 'var(--red)',
  detection: 'var(--green)',
  remediation: 'var(--blue)',
  info: 'var(--muted)',
};

const DOT_LABELS = {
  attack: '🔴',
  detection: '🟢',
  remediation: '🔵',
  info: '⚪',
};

export default function LiveLog({ logs = [], alerts = [] }) {
  const [filter, setFilter] = useState('all');
  const scrollRef = useRef(null);

  // Merge logs and alerts into a unified timeline
  const timeline = [];

  logs.forEach(log => {
    timeline.push({
      id: log.id || `log-${log.timestamp}-${Math.random()}`,
      timestamp: log.timestamp,
      time: log.timestamp ? new Date(log.timestamp).toLocaleTimeString() : '',
      severity: log.severity,
      message: log.message,
      type: getEventType(log),
      source: 'log',
    });
  });

  alerts.forEach(alert => {
    timeline.push({
      id: alert.id || `alert-${Math.random()}`,
      timestamp: alert.timestamp,
      time: alert.timestamp ? new Date(alert.timestamp).toLocaleTimeString() : '',
      severity: alert.severity,
      message: `[${alert.mitreId || '?'}] ${alert.title || alert.message || ''}`,
      type: 'detection',
      source: 'alert',
      mitreId: alert.mitreId,
      latency: alert.detectionLatency,
    });
  });

  // Sort by timestamp
  timeline.sort((a, b) => {
    const ta = a.timestamp ? new Date(a.timestamp).getTime() : 0;
    const tb = b.timestamp ? new Date(b.timestamp).getTime() : 0;
    return tb - ta;
  });

  const filtered = filter === 'all' ? timeline : timeline.filter(e => e.type === filter);

  // Auto-scroll
  useEffect(() => {
    if (scrollRef.current) {
      scrollRef.current.scrollTop = 0;
    }
  }, [filtered.length]);

  const FILTERS = [
    { key: 'all', label: 'All' },
    { key: 'attack', label: '🔴 Attack' },
    { key: 'detection', label: '🟢 Detect' },
    { key: 'remediation', label: '🔵 Remediate' },
  ];

  return (
    <div style={{ display: 'flex', flexDirection: 'column', height: '100%' }}>
      <div style={{
        display: 'flex', justifyContent: 'space-between', alignItems: 'center',
        padding: '0.4rem 0.6rem', borderBottom: '1px solid var(--border)',
      }}>
        <span style={{ fontFamily: 'Orbitron, sans-serif', fontSize: '0.65rem', color: 'var(--blue)' }}>
          EVENT TIMELINE
        </span>
        <div style={{ display: 'flex', gap: '0.2rem' }}>
          {FILTERS.map(f => (
            <button key={f.key} onClick={() => setFilter(f.key)} style={{
              padding: '0.15rem 0.4rem', fontSize: '0.6rem',
              background: filter === f.key ? 'rgba(24,200,255,0.15)' : 'transparent',
              border: `1px solid ${filter === f.key ? 'var(--blue)' : 'var(--border)'}`,
              borderRadius: '3px',
              color: filter === f.key ? 'var(--blue)' : 'var(--muted)',
              cursor: 'pointer',
            }}>
              {f.label}
            </button>
          ))}
        </div>
      </div>

      <div ref={scrollRef} style={{ flex: 1, overflow: 'auto', padding: '0.3rem 0.5rem' }}>
        {filtered.length === 0 ? (
          <div style={{ color: 'var(--muted)', textAlign: 'center', padding: '1.5rem', fontSize: '0.75rem' }}>
            No events yet. Waiting for activity...
          </div>
        ) : (
          filtered.slice(0, 200).map(entry => {
            const dotColor = DOT_COLORS[entry.type];
            return (
              <div key={entry.id} style={{
                display: 'flex', alignItems: 'flex-start', gap: '0.4rem',
                padding: '0.25rem 0', borderBottom: '1px solid rgba(38,64,109,0.15)',
                animation: 'slideIn 0.2s ease-out',
              }}>
                {/* Timeline dot */}
                <div style={{
                  width: '8px', height: '8px', borderRadius: '50%',
                  background: dotColor, marginTop: '0.3rem', flexShrink: 0,
                  boxShadow: `0 0 4px ${dotColor}`,
                }} />
                {/* Time */}
                <span style={{
                  fontSize: '0.6rem', color: 'var(--muted)',
                  fontFamily: 'monospace', minWidth: '55px', flexShrink: 0,
                }}>
                  {entry.time}
                </span>
                {/* Severity badge */}
                <span style={{
                  fontSize: '0.55rem', padding: '0.05rem 0.2rem',
                  borderRadius: '2px', flexShrink: 0, fontWeight: 600,
                  background: entry.severity === 'CRITICAL' ? 'rgba(255,50,90,0.2)' :
                    entry.severity === 'HIGH' || entry.severity === 'CRIT' || entry.severity === 'WARN' ? 'rgba(255,190,47,0.2)' :
                    'rgba(24,200,255,0.1)',
                  color: entry.severity === 'CRITICAL' ? 'var(--red)' :
                    entry.severity === 'HIGH' || entry.severity === 'CRIT' || entry.severity === 'WARN' ? 'var(--amber)' :
                    'var(--blue)',
                }}>
                  {entry.severity || 'INFO'}
                </span>
                {/* Message */}
                <span style={{ fontSize: '0.7rem', color: 'var(--text)', flex: 1, lineHeight: 1.3 }}>
                  {entry.message}
                </span>
                {/* Detection latency */}
                {entry.latency != null && (
                  <span style={{
                    fontSize: '0.55rem', color: 'var(--green)',
                    fontFamily: 'monospace', flexShrink: 0,
                  }}>
                    ⚡{entry.latency}s
                  </span>
                )}
              </div>
            );
          })
        )}
      </div>
    </div>
  );
}
