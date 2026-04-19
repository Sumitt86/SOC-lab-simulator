import { useState, useEffect } from 'react';
import { getSignatures, createSignature, deleteSignature } from '../api/client';

export default function SignatureManager() {
  const [sigs, setSigs] = useState([]);
  const [type, setType] = useState('HASH');
  const [pattern, setPattern] = useState('');
  const [desc, setDesc] = useState('');
  const [error, setError] = useState('');

  async function load() {
    try {
      setSigs(await getSignatures());
    } catch {}
  }

  useEffect(() => { load(); const iv = setInterval(load, 5000); return () => clearInterval(iv); }, []);

  async function handleCreate(e) {
    e.preventDefault();
    if (!pattern.trim()) return;
    setError('');
    try {
      await createSignature(type, pattern.trim(), desc.trim());
      setPattern('');
      setDesc('');
      await load();
    } catch (err) {
      setError(err.message);
    }
  }

  async function handleDelete(id) {
    try {
      await deleteSignature(id);
      await load();
    } catch {}
  }

  return (
    <div style={{ fontSize: '0.8rem' }}>
      <div style={{ fontFamily: 'Orbitron, sans-serif', fontSize: '0.7rem', color: 'var(--blue)', marginBottom: '0.5rem' }}>
        DETECTION SIGNATURES ({sigs.length})
      </div>

      <form onSubmit={handleCreate} style={{ display: 'flex', gap: '0.4rem', marginBottom: '0.75rem', flexWrap: 'wrap' }}>
        <select
          value={type}
          onChange={e => setType(e.target.value)}
          style={{
            background: '#0a0a0a',
            border: '1px solid var(--border)',
            borderRadius: '4px',
            padding: '0.3rem',
            color: 'var(--text)',
            fontSize: '0.75rem',
          }}
        >
          <option value="HASH">HASH</option>
          <option value="STRING">STRING</option>
          <option value="NETWORK_IOC">NETWORK_IOC</option>
        </select>
        <input
          value={pattern}
          onChange={e => setPattern(e.target.value)}
          placeholder={type === 'HASH' ? 'SHA-256 hash...' : type === 'NETWORK_IOC' ? 'IP or domain...' : 'String pattern...'}
          style={{
            flex: 1,
            minWidth: '150px',
            background: '#0a0a0a',
            border: '1px solid var(--border)',
            borderRadius: '4px',
            padding: '0.3rem 0.5rem',
            color: 'var(--text)',
            fontFamily: 'monospace',
            fontSize: '0.75rem',
          }}
        />
        <input
          value={desc}
          onChange={e => setDesc(e.target.value)}
          placeholder="Description (optional)"
          style={{
            width: '150px',
            background: '#0a0a0a',
            border: '1px solid var(--border)',
            borderRadius: '4px',
            padding: '0.3rem 0.5rem',
            color: 'var(--text)',
            fontSize: '0.75rem',
          }}
        />
        <button type="submit" style={{
          padding: '0.3rem 0.8rem',
          background: 'rgba(43,227,138,0.15)',
          border: '1px solid var(--green)',
          borderRadius: '4px',
          color: 'var(--green)',
          cursor: 'pointer',
          fontSize: '0.75rem',
        }}>
          Deploy
        </button>
      </form>

      {error && <div style={{ color: 'var(--red)', marginBottom: '0.5rem' }}>{error}</div>}

      {sigs.length === 0 ? (
        <div style={{ color: 'var(--muted)', textAlign: 'center', padding: '1rem' }}>
          No signatures deployed. Create one above to start detecting threats.
        </div>
      ) : (
        <div style={{ display: 'flex', flexDirection: 'column', gap: '0.3rem' }}>
          {sigs.map(sig => (
            <div key={sig.id} style={{
              display: 'flex',
              alignItems: 'center',
              gap: '0.5rem',
              padding: '0.4rem 0.5rem',
              background: sig.active ? '#0a0a0a' : 'rgba(100,100,100,0.1)',
              border: `1px solid ${sig.active ? 'var(--border)' : 'rgba(100,100,100,0.3)'}`,
              borderRadius: '4px',
              opacity: sig.active ? 1 : 0.5,
            }}>
              <span style={{
                padding: '0.1rem 0.4rem',
                borderRadius: '3px',
                fontSize: '0.65rem',
                fontFamily: 'Orbitron, sans-serif',
                background: sig.type === 'HASH' ? 'rgba(255,50,90,0.15)' :
                  sig.type === 'STRING' ? 'rgba(255,190,47,0.15)' : 'rgba(24,200,255,0.15)',
                color: sig.type === 'HASH' ? 'var(--red)' :
                  sig.type === 'STRING' ? 'var(--amber)' : 'var(--blue)',
              }}>
                {sig.type}
              </span>
              <span style={{ fontFamily: 'monospace', fontSize: '0.7rem', flex: 1, overflow: 'hidden', textOverflow: 'ellipsis', whiteSpace: 'nowrap' }}>
                {sig.pattern}
              </span>
              <span style={{ color: 'var(--muted)', fontSize: '0.7rem', minWidth: '60px', textAlign: 'right' }}>
                {sig.matchCount || 0} hits
              </span>
              {sig.active && (
                <button onClick={() => handleDelete(sig.id)} style={{
                  padding: '0.15rem 0.4rem',
                  background: 'transparent',
                  border: '1px solid var(--red)',
                  borderRadius: '3px',
                  color: 'var(--red)',
                  cursor: 'pointer',
                  fontSize: '0.65rem',
                }}>
                  ✕
                </button>
              )}
            </div>
          ))}
        </div>
      )}
    </div>
  );
}
