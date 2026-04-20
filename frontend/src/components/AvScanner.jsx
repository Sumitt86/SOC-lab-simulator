import { useState } from 'react';
import { avScanHash, avScanDeploy } from '../api/client';

const ENGINE_ICONS = {
  'AliCloud': '🔷', 'Avast': '🛡️', 'ClamAV': '🦪', 'DrWeb': '🕷️',
  'Google': '🔍', 'Ikarus': '☀️', 'Sangfor': '🔶', 'Engine Zero': '⚡',
  'TrendMicro': '🌊', 'Varist': '🔬', 'AhnLab-V3': '🇰🇷', 'Arcabit': '🧬',
  'Avira': '☂️', 'BitDefender': '🅱️',
};

export default function AvScanner({ currentHash }) {
  const [hash, setHash] = useState('');
  const [result, setResult] = useState(null);
  const [loading, setLoading] = useState(false);
  const [deployed, setDeployed] = useState(false);
  const [error, setError] = useState('');

  const effectiveHash = hash || currentHash || '';

  async function handleScan(e) {
    e.preventDefault();
    if (!effectiveHash.trim()) return;
    setLoading(true);
    setError('');
    setDeployed(false);
    try {
      const r = await avScanHash(effectiveHash.trim());
      setResult(r);
    } catch (err) {
      setError(err.message);
    } finally {
      setLoading(false);
    }
  }

  async function handleDeploy() {
    if (!effectiveHash.trim()) return;
    try {
      await avScanDeploy(effectiveHash.trim());
      setDeployed(true);
    } catch (err) {
      setError(err.message);
    }
  }

  const detections = result?.engines?.filter(e => e.detected) || [];
  const clean = result?.engines?.filter(e => !e.detected) || [];
  const total = result?.engines?.length || 0;
  const rate = total > 0 ? ((detections.length / total) * 100).toFixed(0) : 0;

  return (
    <div style={{ fontSize: '0.8rem' }}>
      <div style={{ fontFamily: 'Orbitron, sans-serif', fontSize: '0.7rem', color: 'var(--blue)', marginBottom: '0.5rem' }}>
        VIRUSTOTAL-STYLE AV SCAN
      </div>

      <form onSubmit={handleScan} style={{ display: 'flex', gap: '0.4rem', marginBottom: '0.75rem' }}>
        <input
          value={hash || currentHash || ''}
          onChange={e => setHash(e.target.value)}
          placeholder="Enter SHA-256 hash to scan..."
          style={{
            flex: 1,
            background: '#0a0a0a',
            border: '1px solid var(--border)',
            borderRadius: '4px',
            padding: '0.4rem 0.6rem',
            color: 'var(--text)',
            fontFamily: 'monospace',
            fontSize: '0.75rem',
          }}
        />
        <button type="submit" disabled={loading} style={{
          padding: '0.4rem 0.8rem',
          background: 'rgba(24,200,255,0.15)',
          border: '1px solid var(--blue)',
          borderRadius: '4px',
          color: 'var(--blue)',
          cursor: 'pointer',
          fontSize: '0.75rem',
        }}>
          {loading ? 'Scanning...' : '🔬 Scan'}
        </button>
      </form>

      {currentHash && !hash && (
        <div style={{ fontSize: '0.7rem', color: 'var(--muted)', marginBottom: '0.5rem' }}>
          Using hash from Static Analysis: <span style={{ fontFamily: 'monospace', color: 'var(--text)' }}>{currentHash.slice(0, 16)}...</span>
        </div>
      )}

      {error && <div style={{ color: 'var(--red)', marginBottom: '0.5rem' }}>{error}</div>}

      {result && (
        <div>
          {/* Detection Rate Bar */}
          <div style={{
            marginBottom: '0.75rem', padding: '0.6rem',
            background: '#0a0a0a', borderRadius: '6px',
            border: `1px solid ${rate >= 70 ? 'rgba(255,50,90,0.3)' : rate >= 40 ? 'rgba(255,190,47,0.3)' : 'rgba(43,227,138,0.3)'}`,
          }}>
            <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', marginBottom: '0.4rem' }}>
              <span style={{ fontFamily: 'Orbitron, sans-serif', fontSize: '0.65rem', color: 'var(--muted)' }}>
                DETECTION RATE
              </span>
              <span style={{
                fontSize: '1.2rem', fontWeight: 800,
                color: rate >= 70 ? 'var(--red)' : rate >= 40 ? 'var(--amber)' : 'var(--green)',
              }}>
                {detections.length}/{total}
              </span>
            </div>
            <div style={{ height: 8, background: 'rgba(255,255,255,0.05)', borderRadius: 4, overflow: 'hidden' }}>
              <div style={{
                height: '100%', width: `${rate}%`, borderRadius: 4, transition: 'width 0.5s ease',
                background: rate >= 70 ? 'var(--red)' : rate >= 40 ? 'var(--amber)' : 'var(--green)',
              }} />
            </div>
            <div style={{ display: 'flex', justifyContent: 'space-between', marginTop: '0.3rem' }}>
              <span style={{ fontSize: '0.65rem', color: 'var(--muted)' }}>
                Confidence: <strong style={{ color: 'var(--text)' }}>{result.confidence || rate}%</strong>
              </span>
              <span style={{ fontSize: '0.65rem', color: 'var(--muted)' }}>
                Score: <strong style={{ color: 'var(--amber)' }}>+{result.scoreImpact || 0} pts</strong>
              </span>
            </div>
          </div>

          {/* Threat Classification */}
          {result.primaryFamily && (
            <div style={{
              marginBottom: '0.75rem', padding: '0.4rem 0.6rem',
              background: 'rgba(255,50,90,0.08)', border: '1px solid rgba(255,50,90,0.2)',
              borderRadius: '6px', display: 'flex', alignItems: 'center', gap: '0.5rem',
            }}>
              <span style={{ fontSize: '0.65rem', color: 'var(--muted)' }}>PRIMARY CLASSIFICATION:</span>
              <span style={{
                padding: '0.15rem 0.5rem', borderRadius: 3, fontSize: '0.75rem',
                fontWeight: 700, background: 'rgba(255,50,90,0.2)', color: 'var(--red)',
              }}>
                {result.primaryFamily}
              </span>
            </div>
          )}

          {/* Engine Results Grid */}
          <div style={{ fontFamily: 'Orbitron, sans-serif', fontSize: '0.65rem', color: 'var(--blue)', marginBottom: '0.3rem' }}>
            ENGINE RESULTS
          </div>
          <div style={{ display: 'grid', gridTemplateColumns: 'repeat(2, 1fr)', gap: '0.3rem', marginBottom: '0.75rem' }}>
            {(result.engines || []).map((eng, i) => (
              <div key={eng.name || i} style={{
                display: 'flex', alignItems: 'center', gap: '0.4rem',
                padding: '0.3rem 0.5rem',
                background: eng.detected ? 'rgba(255,50,90,0.06)' : '#0a0a0a',
                border: `1px solid ${eng.detected ? 'rgba(255,50,90,0.2)' : 'var(--border)'}`,
                borderRadius: '4px',
              }}>
                <span style={{ fontSize: '0.8rem' }}>{ENGINE_ICONS[eng.name] || '🔧'}</span>
                <div style={{ flex: 1, minWidth: 0 }}>
                  <div style={{ fontSize: '0.7rem', fontWeight: 600, color: eng.detected ? 'var(--red)' : 'var(--green)' }}>
                    {eng.name}
                  </div>
                  <div style={{ fontSize: '0.6rem', color: 'var(--muted)', overflow: 'hidden', textOverflow: 'ellipsis', whiteSpace: 'nowrap' }}>
                    {eng.detected ? eng.classification || 'Detected' : 'Clean'}
                  </div>
                </div>
                <span style={{
                  width: 16, height: 16, borderRadius: '50%', display: 'flex',
                  alignItems: 'center', justifyContent: 'center', fontSize: '0.6rem',
                  background: eng.detected ? 'rgba(255,50,90,0.2)' : 'rgba(43,227,138,0.2)',
                  color: eng.detected ? 'var(--red)' : 'var(--green)',
                }}>
                  {eng.detected ? '✗' : '✓'}
                </span>
              </div>
            ))}
          </div>

          {/* Deploy Button */}
          {detections.length > 0 && (
            <button
              onClick={handleDeploy}
              disabled={deployed}
              style={{
                width: '100%', padding: '0.5rem',
                background: deployed ? 'rgba(43,227,138,0.15)' : 'rgba(255,50,90,0.15)',
                border: `1px solid ${deployed ? 'var(--green)' : 'var(--red)'}`,
                borderRadius: '6px', cursor: deployed ? 'default' : 'pointer',
                color: deployed ? 'var(--green)' : 'var(--red)',
                fontSize: '0.8rem', fontWeight: 700,
              }}
            >
              {deployed ? '✓ Signature Deployed' : '🛡️ Deploy as Detection Signature'}
            </button>
          )}
        </div>
      )}
    </div>
  );
}
