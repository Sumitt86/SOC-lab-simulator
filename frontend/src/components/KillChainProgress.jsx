import { useState, useEffect } from 'react';

const KILL_CHAIN_STAGES = [
  { id: 'recon', label: 'Recon', icon: '🔍', mitre: 'TA0043', tactics: ['T1595', 'T1046', 'T1018', 'T1083'] },
  { id: 'initial_access', label: 'Initial Access', icon: '🚪', mitre: 'TA0001', tactics: ['T1190', 'T1110.001', 'T1078'] },
  { id: 'persistence', label: 'Persistence', icon: '⚓', mitre: 'TA0003', tactics: ['T1505.003', 'T1053.003', 'T1098'] },
  { id: 'priv_esc', label: 'Priv Esc', icon: '⬆️', mitre: 'TA0004', tactics: ['T1548.003'] },
  { id: 'exfil', label: 'Exfiltration', icon: '📤', mitre: 'TA0010', tactics: ['T1041', 'T1048.003', 'T1572'] },
];

function getStageStatus(stage, alerts) {
  const detected = alerts.some(a => a.mitreId && stage.tactics.includes(a.mitreId));
  const blocked = alerts.some(a =>
    a.mitreId && stage.tactics.includes(a.mitreId) &&
    (a.status === 'RESOLVED' || a.remediated)
  );
  if (blocked) return 'blocked';
  if (detected) return 'detected';
  return 'clear';
}

const STATUS_COLORS = {
  clear: { bg: 'rgba(255,255,255,0.05)', border: 'var(--border)', text: 'var(--muted)', dot: 'var(--muted)' },
  detected: { bg: 'rgba(255,50,90,0.12)', border: 'var(--red)', text: 'var(--red)', dot: 'var(--red)' },
  blocked: { bg: 'rgba(43,227,138,0.12)', border: 'var(--green)', text: 'var(--green)', dot: 'var(--green)' },
};

export default function KillChainProgress({ alerts = [] }) {
  const [stages, setStages] = useState([]);

  useEffect(() => {
    setStages(KILL_CHAIN_STAGES.map(s => ({
      ...s,
      status: getStageStatus(s, alerts),
      matchCount: alerts.filter(a => a.mitreId && s.tactics.includes(a.mitreId)).length,
    })));
  }, [alerts]);

  return (
    <div style={{ padding: '0.5rem' }}>
      <div style={{
        fontFamily: 'Orbitron, sans-serif', fontSize: '0.65rem',
        color: 'var(--blue)', marginBottom: '0.5rem',
      }}>
        KILL CHAIN TRACKER
      </div>

      {/* Pipeline */}
      <div style={{ display: 'flex', alignItems: 'center', gap: '2px', marginBottom: '0.75rem' }}>
        {stages.map((stage, i) => {
          const colors = STATUS_COLORS[stage.status];
          return (
            <div key={stage.id} style={{ display: 'flex', alignItems: 'center', flex: 1 }}>
              <div style={{
                flex: 1,
                padding: '0.4rem 0.3rem',
                background: colors.bg,
                border: `1px solid ${colors.border}`,
                borderRadius: i === 0 ? '6px 0 0 6px' : i === stages.length - 1 ? '0 6px 6px 0' : '0',
                textAlign: 'center',
                position: 'relative',
              }}>
                <div style={{ fontSize: '0.9rem' }}>{stage.icon}</div>
                <div style={{
                  fontSize: '0.55rem', fontFamily: 'Orbitron, sans-serif',
                  color: colors.text, marginTop: '0.1rem',
                }}>
                  {stage.label}
                </div>
                {stage.matchCount > 0 && (
                  <div style={{
                    position: 'absolute', top: '-4px', right: '-4px',
                    background: colors.dot, color: '#000',
                    borderRadius: '50%', width: '14px', height: '14px',
                    fontSize: '0.55rem', fontWeight: 700,
                    display: 'flex', alignItems: 'center', justifyContent: 'center',
                  }}>
                    {stage.matchCount}
                  </div>
                )}
                <div style={{
                  width: '6px', height: '6px', borderRadius: '50%',
                  background: colors.dot, margin: '0.2rem auto 0',
                  animation: stage.status === 'detected' ? 'pulse 1.5s infinite' : 'none',
                }} />
              </div>
              {i < stages.length - 1 && (
                <div style={{
                  width: '8px', height: '2px',
                  background: stage.status !== 'clear' ? colors.border : 'var(--border)',
                }} />
              )}
            </div>
          );
        })}
      </div>

      {/* Legend */}
      <div style={{ display: 'flex', gap: '1rem', justifyContent: 'center', fontSize: '0.6rem' }}>
        <span style={{ color: 'var(--muted)' }}>● Clear</span>
        <span style={{ color: 'var(--red)' }}>● Detected</span>
        <span style={{ color: 'var(--green)' }}>● Blocked</span>
      </div>
    </div>
  );
}
