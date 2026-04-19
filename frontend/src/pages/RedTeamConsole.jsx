import { useEffect, useState } from 'react';
import { getSummary, redTeamHistory } from '../api/client';
import OperatorTerminal from '../components/OperatorTerminal';
import { useNavigate } from 'react-router-dom';

const CAMPAIGNS = [
  {
    name: 'APT Smash & Grab',
    steps: [
      { label: 'Recon: nmap -sV victim', cmd: 'nmap -sV soc-victim' },
      { label: 'Initial Access: reverse shell', cmd: 'bash /attacks/reverse_shell.sh' },
      { label: 'Persistence: cron backdoor', cmd: 'bash /attacks/persistence_cron.sh' },
      { label: 'Exfiltrate: data theft', cmd: 'bash /attacks/exfiltrate.sh' },
    ],
  },
  {
    name: 'Stealth Operator',
    steps: [
      { label: 'Quiet recon: port scan', cmd: 'nmap -sS -T2 soc-victim' },
      { label: 'Drop encoded payload', cmd: 'bash /attacks/dropper.sh' },
      { label: 'Establish C2 beacon', cmd: 'bash /attacks/beacon.sh' },
      { label: 'Lateral move', cmd: 'bash /attacks/phase3_lateral_move.sh' },
    ],
  },
  {
    name: 'Ransomware Blitz',
    steps: [
      { label: 'Initial access', cmd: 'bash /attacks/phase1_initial_access.sh' },
      { label: 'Deploy ransomware', cmd: 'bash /attacks/ransomware_sim.sh' },
      { label: 'Persistence', cmd: 'bash /attacks/phase2_persistence.sh' },
      { label: 'Full exfiltration', cmd: 'bash /attacks/phase4_exfiltration.sh' },
    ],
  },
];

export default function RedTeamConsole() {
  const navigate = useNavigate();
  const [summary, setSummary] = useState(null);
  const [history, setHistory] = useState([]);
  const [selectedCampaign, setSelectedCampaign] = useState(0);
  const [checkedSteps, setCheckedSteps] = useState({});

  useEffect(() => {
    const load = async () => {
      try {
        setSummary(await getSummary());
        setHistory(await redTeamHistory().catch(() => []));
      } catch {}
    };
    load();
    const iv = setInterval(load, 4000);
    return () => clearInterval(iv);
  }, []);

  const campaign = CAMPAIGNS[selectedCampaign];

  function toggleStep(idx) {
    setCheckedSteps(prev => ({ ...prev, [`${selectedCampaign}-${idx}`]: !prev[`${selectedCampaign}-${idx}`] }));
  }

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
          <span style={{ fontFamily: 'Orbitron, sans-serif', color: 'var(--red)', fontSize: '1rem' }}>🔴 RED TEAM</span>
          <span style={{ color: 'var(--muted)', fontSize: '0.8rem' }}>
            Score: <strong style={{ color: 'var(--red)' }}>{summary?.redScore ?? 0}</strong>
            {' | '}Phase: {summary?.attackPhaseDisplay || '—'}
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

      {/* Main layout */}
      <div style={{ display: 'flex', flex: 1, overflow: 'hidden' }}>
        {/* Left: Campaign sidebar */}
        <div style={{
          width: '260px',
          borderRight: '1px solid var(--border)',
          background: 'rgba(15,23,48,0.4)',
          padding: '0.75rem',
          overflow: 'auto',
          flexShrink: 0,
        }}>
          <div style={{ fontFamily: 'Orbitron, sans-serif', fontSize: '0.7rem', color: 'var(--red)', marginBottom: '0.75rem' }}>
            APT CAMPAIGNS
          </div>
          {CAMPAIGNS.map((c, ci) => (
            <div key={ci}>
              <button
                onClick={() => setSelectedCampaign(ci)}
                style={{
                  width: '100%',
                  textAlign: 'left',
                  padding: '0.5rem',
                  background: ci === selectedCampaign ? 'rgba(255,50,90,0.1)' : 'transparent',
                  border: ci === selectedCampaign ? '1px solid var(--red)' : '1px solid transparent',
                  borderRadius: '4px',
                  color: ci === selectedCampaign ? 'var(--red)' : 'var(--muted)',
                  cursor: 'pointer',
                  fontSize: '0.8rem',
                  marginBottom: '0.3rem',
                }}
              >
                {c.name}
              </button>
            </div>
          ))}

          <div style={{ marginTop: '1rem', borderTop: '1px solid var(--border)', paddingTop: '0.75rem' }}>
            <div style={{ fontFamily: 'Orbitron, sans-serif', fontSize: '0.65rem', color: 'var(--muted)', marginBottom: '0.5rem' }}>
              KILL CHAIN: {campaign.name}
            </div>
            {campaign.steps.map((step, si) => {
              const checked = checkedSteps[`${selectedCampaign}-${si}`];
              return (
                <div
                  key={si}
                  style={{
                    display: 'flex',
                    alignItems: 'flex-start',
                    gap: '0.4rem',
                    marginBottom: '0.5rem',
                    padding: '0.3rem',
                    borderRadius: '4px',
                    background: checked ? 'rgba(43,227,138,0.05)' : 'transparent',
                  }}
                >
                  <input
                    type="checkbox"
                    checked={!!checked}
                    onChange={() => toggleStep(si)}
                    style={{ marginTop: '0.2rem', cursor: 'pointer' }}
                  />
                  <div style={{ fontSize: '0.75rem', lineHeight: 1.3 }}>
                    <div style={{ color: checked ? 'var(--green)' : 'var(--text)', textDecoration: checked ? 'line-through' : 'none' }}>
                      {step.label}
                    </div>
                    <code style={{ fontSize: '0.65rem', color: 'var(--muted)' }}>{step.cmd}</code>
                  </div>
                </div>
              );
            })}
          </div>

          {/* Command History */}
          <div style={{ marginTop: '1rem', borderTop: '1px solid var(--border)', paddingTop: '0.75rem' }}>
            <div style={{ fontFamily: 'Orbitron, sans-serif', fontSize: '0.65rem', color: 'var(--muted)', marginBottom: '0.5rem' }}>
              COMMAND HISTORY ({history.length})
            </div>
            <div style={{ maxHeight: '200px', overflow: 'auto' }}>
              {history.slice(-20).reverse().map((h, i) => (
                <div key={i} style={{
                  fontSize: '0.65rem',
                  padding: '0.2rem 0',
                  borderBottom: '1px solid rgba(38,64,109,0.3)',
                  color: h.success ? 'var(--text)' : 'var(--red)',
                }}>
                  <code>{h.command}</code>
                </div>
              ))}
            </div>
          </div>
        </div>

        {/* Center: Terminal */}
        <div style={{ flex: 1, display: 'flex', flexDirection: 'column', padding: '0.75rem' }}>
          <OperatorTerminal defaultContainer="soc-attacker" />
        </div>

        {/* Right: C2 Dashboard */}
        <div style={{
          width: '260px',
          borderLeft: '1px solid var(--border)',
          background: 'rgba(15,23,48,0.4)',
          padding: '0.75rem',
          overflow: 'auto',
          flexShrink: 0,
        }}>
          <div style={{ fontFamily: 'Orbitron, sans-serif', fontSize: '0.7rem', color: 'var(--red)', marginBottom: '0.75rem' }}>
            C2 DASHBOARD
          </div>

          <div style={{ marginBottom: '1rem' }}>
            <div style={{ fontSize: '0.7rem', color: 'var(--muted)', marginBottom: '0.3rem' }}>Active Sessions</div>
            <div style={{
              padding: '0.5rem',
              background: '#0a0a0a',
              borderRadius: '4px',
              border: '1px solid var(--border)',
              fontSize: '0.75rem',
            }}>
              {summary?.beaconCount > 0 ? (
                <div style={{ color: 'var(--green)' }}>
                  🟢 {summary.beaconCount} beacon(s) active
                </div>
              ) : (
                <div style={{ color: 'var(--muted)' }}>No active sessions</div>
              )}
            </div>
          </div>

          <div style={{ marginBottom: '1rem' }}>
            <div style={{ fontSize: '0.7rem', color: 'var(--muted)', marginBottom: '0.3rem' }}>Evasion Status</div>
            <div style={{
              padding: '0.5rem',
              background: '#0a0a0a',
              borderRadius: '4px',
              border: '1px solid var(--border)',
              fontSize: '0.75rem',
            }}>
              <div>Persistence: {summary?.persistenceActive ? <span style={{ color: 'var(--green)' }}>✓ Active</span> : <span style={{ color: 'var(--muted)' }}>✗ None</span>}</div>
              <div>Hosts owned: {summary?.activeHostCount ?? 0}</div>
              <div>IPs blocked: {summary?.blockedIPCount ?? 0}</div>
            </div>
          </div>

          <div>
            <div style={{ fontSize: '0.7rem', color: 'var(--muted)', marginBottom: '0.3rem' }}>Quick Actions</div>
            <div style={{ display: 'flex', flexDirection: 'column', gap: '0.3rem' }}>
              {campaign.steps.map((step, i) => (
                <button
                  key={i}
                  onClick={() => {
                    // Copy command to clipboard
                    navigator.clipboard?.writeText(step.cmd);
                  }}
                  style={{
                    padding: '0.3rem 0.5rem',
                    background: 'rgba(255,50,90,0.08)',
                    border: '1px solid rgba(255,50,90,0.2)',
                    borderRadius: '4px',
                    color: 'var(--text)',
                    cursor: 'pointer',
                    fontSize: '0.7rem',
                    textAlign: 'left',
                  }}
                  title="Click to copy command"
                >
                  📋 {step.label}
                </button>
              ))}
            </div>
          </div>
        </div>
      </div>
    </div>
  );
}
