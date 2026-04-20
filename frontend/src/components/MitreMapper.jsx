import { useState } from 'react';

const MITRE_TECHNIQUES = {
  'T1190': { name: 'Exploit Public-Facing Application', tactic: 'Initial Access', description: 'Adversary exploits a vulnerability in an internet-facing application.', detection: 'DET-001', mitigation: 'WAF rules, input validation, patching' },
  'T1110.001': { name: 'Brute Force: Password Guessing', tactic: 'Credential Access', description: 'Adversary attempts to gain access by trying many passwords.', detection: 'DET-003', mitigation: 'Account lockout, MFA, rate limiting' },
  'T1078': { name: 'Valid Accounts', tactic: 'Initial Access', description: 'Adversary uses legitimate credentials to access systems.', detection: 'DET-004', mitigation: 'MFA, credential rotation, monitoring' },
  'T1059.004': { name: 'Unix Shell', tactic: 'Execution', description: 'Adversary executes commands via Unix shell.', detection: 'DET-002', mitigation: 'AppArmor/SELinux, command auditing' },
  'T1548.003': { name: 'Sudo and Sudo Caching', tactic: 'Privilege Escalation', description: 'Adversary escalates privileges using sudo.', detection: 'DET-005', mitigation: 'Restrict sudoers, require password' },
  'T1505.003': { name: 'Web Shell', tactic: 'Persistence', description: 'Adversary plants a web shell for persistent access.', detection: 'DET-008', mitigation: 'File integrity monitoring, webroot hardening' },
  'T1053.003': { name: 'Cron', tactic: 'Persistence', description: 'Adversary creates cron jobs for persistence.', detection: 'DET-009', mitigation: 'Monitor cron changes, restrict cron access' },
  'T1572': { name: 'Protocol Tunneling', tactic: 'Command and Control', description: 'Adversary tunnels data through DNS or other protocols.', detection: 'DET-006', mitigation: 'DNS filtering, protocol inspection' },
  'T1018': { name: 'Remote System Discovery', tactic: 'Discovery', description: 'Adversary discovers remote systems on the network.', detection: 'DET-007', mitigation: 'Network segmentation, IDS' },
  'T1048.003': { name: 'Exfiltration Over Unencrypted Protocol', tactic: 'Exfiltration', description: 'Adversary exfiltrates data using unencrypted protocols.', detection: 'DET-011', mitigation: 'DLP, egress filtering, TLS inspection' },
  'T1046': { name: 'Network Service Discovery', tactic: 'Discovery', description: 'Adversary scans for open ports and services.', detection: 'DET-012', mitigation: 'IDS/IPS, port knocking' },
  'T1083': { name: 'File and Directory Discovery', tactic: 'Discovery', description: 'Adversary enumerates files and directories on a system.', detection: 'DET-013', mitigation: 'File permissions, audit logging' },
  'T1552.001': { name: 'Credentials in Files', tactic: 'Credential Access', description: 'Adversary searches files for credentials.', detection: 'DET-010', mitigation: 'Secrets management, file permissions' },
  'T1041': { name: 'Exfiltration Over C2 Channel', tactic: 'Exfiltration', description: 'Adversary exfiltrates data over existing C2 channel.', detection: 'DET-011', mitigation: 'DLP, traffic analysis' },
  'T1595': { name: 'Active Scanning', tactic: 'Reconnaissance', description: 'Adversary actively scans target infrastructure.', detection: 'DET-012', mitigation: 'Rate limiting, honeypots' },
  'T1098': { name: 'Account Manipulation', tactic: 'Persistence', description: 'Adversary modifies accounts to maintain access.', detection: 'DET-010', mitigation: 'Audit account changes, MFA' },
  'T1005': { name: 'Data from Local System', tactic: 'Collection', description: 'Adversary collects data from the local file system.', detection: 'DET-013', mitigation: 'DLP, encryption at rest' },
};

const TACTIC_COLORS = {
  'Reconnaissance': '#6366f1',
  'Initial Access': '#ef4444',
  'Execution': '#f97316',
  'Persistence': '#eab308',
  'Privilege Escalation': '#a855f7',
  'Credential Access': '#ec4899',
  'Discovery': '#3b82f6',
  'Collection': '#14b8a6',
  'Command and Control': '#f59e0b',
  'Exfiltration': '#dc2626',
};

export default function MitreMapper({ alerts = [] }) {
  const [expanded, setExpanded] = useState(null);

  // Get unique detected techniques
  const detectedTechniques = {};
  alerts.forEach(a => {
    if (a.mitreId && MITRE_TECHNIQUES[a.mitreId]) {
      if (!detectedTechniques[a.mitreId]) {
        detectedTechniques[a.mitreId] = { ...MITRE_TECHNIQUES[a.mitreId], id: a.mitreId, count: 0, firstSeen: a.timestamp };
      }
      detectedTechniques[a.mitreId].count++;
    }
  });

  const techniques = Object.values(detectedTechniques).sort((a, b) => b.count - a.count);

  return (
    <div style={{ padding: '0.5rem' }}>
      <div style={{
        fontFamily: 'Orbitron, sans-serif', fontSize: '0.65rem',
        color: 'var(--blue)', marginBottom: '0.5rem',
        display: 'flex', justifyContent: 'space-between', alignItems: 'center',
      }}>
        <span>MITRE ATT&CK MAPPER</span>
        <span style={{ fontSize: '0.6rem', color: 'var(--muted)', fontFamily: 'monospace' }}>
          {techniques.length} techniques detected
        </span>
      </div>

      {techniques.length === 0 ? (
        <div style={{ color: 'var(--muted)', textAlign: 'center', padding: '1rem', fontSize: '0.75rem' }}>
          No MITRE techniques detected yet. Waiting for attack activity...
        </div>
      ) : (
        <div style={{ display: 'flex', flexDirection: 'column', gap: '0.3rem', maxHeight: '300px', overflow: 'auto' }}>
          {techniques.map(tech => {
            const isExpanded = expanded === tech.id;
            const tacticColor = TACTIC_COLORS[tech.tactic] || 'var(--muted)';
            return (
              <div key={tech.id} style={{
                background: '#0a0a0a',
                border: `1px solid ${isExpanded ? tacticColor : 'var(--border)'}`,
                borderRadius: '4px',
                overflow: 'hidden',
              }}>
                <div
                  onClick={() => setExpanded(isExpanded ? null : tech.id)}
                  style={{
                    padding: '0.4rem 0.5rem',
                    cursor: 'pointer',
                    display: 'flex', justifyContent: 'space-between', alignItems: 'center',
                  }}
                >
                  <div style={{ display: 'flex', alignItems: 'center', gap: '0.4rem' }}>
                    <code style={{
                      fontSize: '0.65rem', padding: '0.1rem 0.3rem',
                      background: `${tacticColor}20`, color: tacticColor,
                      borderRadius: '3px', fontWeight: 700,
                    }}>
                      {tech.id}
                    </code>
                    <span style={{ fontSize: '0.7rem', color: 'var(--text)' }}>{tech.name}</span>
                  </div>
                  <div style={{ display: 'flex', alignItems: 'center', gap: '0.4rem' }}>
                    <span style={{
                      fontSize: '0.6rem', padding: '0.1rem 0.3rem',
                      background: 'rgba(255,50,90,0.15)', color: 'var(--red)',
                      borderRadius: '3px', fontWeight: 600,
                    }}>
                      {tech.count}x
                    </span>
                    <span style={{ fontSize: '0.6rem', color: 'var(--muted)' }}>
                      {isExpanded ? '▲' : '▼'}
                    </span>
                  </div>
                </div>

                {isExpanded && (
                  <div style={{ padding: '0 0.5rem 0.5rem', borderTop: '1px solid var(--border)' }}>
                    <div style={{ fontSize: '0.65rem', marginTop: '0.3rem' }}>
                      <div style={{ color: 'var(--muted)', marginBottom: '0.2rem' }}>
                        <strong style={{ color: tacticColor }}>Tactic:</strong> {tech.tactic}
                      </div>
                      <div style={{ color: 'var(--muted)', marginBottom: '0.2rem' }}>
                        <strong style={{ color: 'var(--text)' }}>Description:</strong> {tech.description}
                      </div>
                      <div style={{ color: 'var(--muted)', marginBottom: '0.2rem' }}>
                        <strong style={{ color: 'var(--blue)' }}>Detection Rule:</strong> {tech.detection}
                      </div>
                      <div style={{
                        padding: '0.3rem 0.4rem', marginTop: '0.3rem',
                        background: 'rgba(43,227,138,0.08)', border: '1px solid rgba(43,227,138,0.2)',
                        borderRadius: '4px', fontSize: '0.6rem',
                      }}>
                        <strong style={{ color: 'var(--green)' }}>Mitigation:</strong>{' '}
                        <span style={{ color: 'var(--text)' }}>{tech.mitigation}</span>
                      </div>
                    </div>
                  </div>
                )}
              </div>
            );
          })}
        </div>
      )}
    </div>
  );
}
