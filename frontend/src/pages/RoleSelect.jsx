import { useState } from 'react';
import { useNavigate } from 'react-router-dom';
import { startGame } from '../api/client';

const ROLES = [
  { key: 'red', label: 'Red Team', icon: '🔴', desc: 'Offensive operator — execute attacks, deploy malware, establish persistence', color: 'var(--red)' },
  { key: 'blue', label: 'Blue Team', icon: '🔵', desc: 'SOC analyst — detect threats, analyze malware, deploy signatures, respond', color: 'var(--blue)' },
  { key: 'spectator', label: 'Mission Control', icon: '📡', desc: 'Observe both teams — full dashboard, real-time scores, kill chain', color: 'var(--amber)' },
];

const DIFFICULTIES = ['EASY', 'MEDIUM', 'HARD'];

export default function RoleSelect() {
  const navigate = useNavigate();
  const [difficulty, setDifficulty] = useState('MEDIUM');
  const [persistSigs, setPersistSigs] = useState(false);
  const [starting, setStarting] = useState(false);

  async function handleSelect(role) {
    setStarting(true);
    localStorage.setItem('soc-role', role);
    try {
      await startGame(difficulty, persistSigs);
    } catch { /* ignore — game may already be running */ }
    navigate(`/${role}`);
  }

  return (
    <main className="app" style={{ display: 'flex', flexDirection: 'column', alignItems: 'center', justifyContent: 'center', minHeight: '100vh' }}>
      <h1 style={{ fontFamily: 'Orbitron, sans-serif', fontSize: '2rem', marginBottom: '0.25rem' }}>Mini SOC Lab</h1>
      <p style={{ color: 'var(--muted)', marginBottom: '2rem' }}>Choose your role to begin the adversarial exercise</p>

      <div style={{ display: 'flex', gap: '1.5rem', flexWrap: 'wrap', justifyContent: 'center', marginBottom: '2rem' }}>
        {ROLES.map(r => (
          <button
            key={r.key}
            disabled={starting}
            onClick={() => handleSelect(r.key)}
            style={{
              background: 'var(--panel)',
              border: `1px solid ${r.color}`,
              borderRadius: '12px',
              padding: '2rem 1.5rem',
              cursor: 'pointer',
              width: '240px',
              textAlign: 'center',
              transition: 'transform 0.15s, box-shadow 0.15s',
              color: 'var(--text)',
            }}
            onMouseEnter={e => { e.currentTarget.style.transform = 'translateY(-4px)'; e.currentTarget.style.boxShadow = `0 8px 24px ${r.color}33`; }}
            onMouseLeave={e => { e.currentTarget.style.transform = ''; e.currentTarget.style.boxShadow = ''; }}
          >
            <div style={{ fontSize: '2.5rem', marginBottom: '0.5rem' }}>{r.icon}</div>
            <div style={{ fontFamily: 'Orbitron, sans-serif', fontSize: '1.1rem', color: r.color, marginBottom: '0.5rem' }}>{r.label}</div>
            <div style={{ fontSize: '0.85rem', color: 'var(--muted)', lineHeight: 1.4 }}>{r.desc}</div>
          </button>
        ))}
      </div>

      <div style={{ background: 'var(--panel)', border: '1px solid var(--border)', borderRadius: '8px', padding: '1rem 1.5rem', display: 'flex', gap: '2rem', alignItems: 'center', flexWrap: 'wrap' }}>
        <div>
          <label style={{ fontSize: '0.8rem', color: 'var(--muted)', display: 'block', marginBottom: '0.3rem' }}>Difficulty</label>
          <div style={{ display: 'flex', gap: '0.5rem' }}>
            {DIFFICULTIES.map(d => (
              <button
                key={d}
                onClick={() => setDifficulty(d)}
                style={{
                  padding: '0.3rem 0.8rem',
                  borderRadius: '4px',
                  border: d === difficulty ? '1px solid var(--blue)' : '1px solid var(--border)',
                  background: d === difficulty ? 'rgba(24,200,255,0.15)' : 'transparent',
                  color: d === difficulty ? 'var(--blue)' : 'var(--muted)',
                  cursor: 'pointer',
                  fontSize: '0.85rem',
                }}
              >
                {d}
              </button>
            ))}
          </div>
        </div>
        <label style={{ display: 'flex', alignItems: 'center', gap: '0.5rem', fontSize: '0.85rem', color: 'var(--muted)', cursor: 'pointer' }}>
          <input type="checkbox" checked={persistSigs} onChange={e => setPersistSigs(e.target.checked)} />
          Persist signatures across games
        </label>
      </div>
    </main>
  );
}
