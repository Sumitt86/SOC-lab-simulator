import { useState, useRef, useEffect, useCallback } from 'react';
import { useCommandStream } from '../hooks/useSse';
import { redTeamExecute, redTeamExecuteStream } from '../api/client';

export default function OperatorTerminal({ defaultContainer = 'soc-attacker' }) {
  const [history, setHistory] = useState([]);
  const [input, setInput] = useState('');
  const [container, setContainer] = useState(defaultContainer);
  const [commandId, setCommandId] = useState(null);
  const [historyIdx, setHistoryIdx] = useState(-1);
  const [cmdHistory, setCmdHistory] = useState([]);
  const termRef = useRef(null);
  const inputRef = useRef(null);

  const { lines, exitCode, running } = useCommandStream(commandId);

  // Auto-scroll
  useEffect(() => {
    if (termRef.current) termRef.current.scrollTop = termRef.current.scrollHeight;
  }, [history, lines]);

  // Stream output into history when command completes
  useEffect(() => {
    if (commandId && !running && exitCode !== null) {
      setHistory(prev => [
        ...prev,
        ...lines.map(l => ({ type: l.type, text: l.text })),
        { type: 'info', text: `[exit ${exitCode}]` }
      ]);
      setCommandId(null);
    }
  }, [running, exitCode]);

  const handleSubmit = useCallback(async (e) => {
    e.preventDefault();
    const cmd = input.trim();
    if (!cmd) return;

    setCmdHistory(prev => [...prev, cmd]);
    setHistoryIdx(-1);
    setInput('');
    setHistory(prev => [...prev, { type: 'prompt', text: `${container}$ ${cmd}` }]);

    // Try streaming first, fall back to regular exec
    try {
      const res = await redTeamExecuteStream(container, cmd);
      if (res.commandId) {
        setCommandId(res.commandId);
        return;
      }
    } catch {
      // fallback
    }

    try {
      const res = await redTeamExecute(container, cmd);
      if (res.output) {
        setHistory(prev => [...prev, { type: 'stdout', text: res.output }]);
      }
      if (res.error) {
        setHistory(prev => [...prev, { type: 'stderr', text: res.error }]);
      }
    } catch (err) {
      setHistory(prev => [...prev, { type: 'stderr', text: err.message }]);
    }
  }, [input, container]);

  function handleKeyDown(e) {
    if (e.key === 'ArrowUp') {
      e.preventDefault();
      const newIdx = Math.min(historyIdx + 1, cmdHistory.length - 1);
      setHistoryIdx(newIdx);
      if (cmdHistory.length > 0) setInput(cmdHistory[cmdHistory.length - 1 - newIdx]);
    } else if (e.key === 'ArrowDown') {
      e.preventDefault();
      const newIdx = historyIdx - 1;
      if (newIdx < 0) {
        setHistoryIdx(-1);
        setInput('');
      } else {
        setHistoryIdx(newIdx);
        setInput(cmdHistory[cmdHistory.length - 1 - newIdx]);
      }
    }
  }

  return (
    <div style={{
      background: '#0a0a0a',
      border: '1px solid var(--border)',
      borderRadius: '8px',
      display: 'flex',
      flexDirection: 'column',
      height: '100%',
      minHeight: '400px',
    }}>
      {/* Header */}
      <div style={{
        display: 'flex',
        alignItems: 'center',
        justifyContent: 'space-between',
        padding: '0.5rem 0.75rem',
        borderBottom: '1px solid var(--border)',
        background: 'rgba(15,23,48,0.6)',
        borderRadius: '8px 8px 0 0',
      }}>
        <span style={{ fontFamily: 'Orbitron, sans-serif', fontSize: '0.75rem', color: 'var(--red)' }}>
          OPERATOR TERMINAL
        </span>
        <div style={{ display: 'flex', gap: '0.5rem' }}>
          {['soc-attacker', 'soc-victim'].map(c => (
            <button
              key={c}
              onClick={() => setContainer(c)}
              style={{
                padding: '0.2rem 0.6rem',
                fontSize: '0.7rem',
                borderRadius: '3px',
                border: c === container ? '1px solid var(--red)' : '1px solid var(--border)',
                background: c === container ? 'rgba(255,50,90,0.15)' : 'transparent',
                color: c === container ? 'var(--red)' : 'var(--muted)',
                cursor: 'pointer',
              }}
            >
              {c}
            </button>
          ))}
        </div>
      </div>

      {/* Output */}
      <div
        ref={termRef}
        onClick={() => inputRef.current?.focus()}
        style={{
          flex: 1,
          overflowY: 'auto',
          padding: '0.5rem 0.75rem',
          fontFamily: '"Fira Code", "Cascadia Code", monospace',
          fontSize: '0.8rem',
          lineHeight: 1.5,
          cursor: 'text',
        }}
      >
        {history.map((line, i) => (
          <div key={i} style={{
            color: line.type === 'stderr' ? 'var(--red)'
              : line.type === 'prompt' ? 'var(--green)'
              : line.type === 'info' ? 'var(--muted)'
              : 'var(--text)',
            whiteSpace: 'pre-wrap',
            wordBreak: 'break-all',
          }}>
            {line.text}
          </div>
        ))}
        {/* Live streaming output */}
        {running && lines.map((line, i) => (
          <div key={`live-${i}`} style={{
            color: line.type === 'stderr' ? 'var(--red)' : 'var(--text)',
            whiteSpace: 'pre-wrap',
            wordBreak: 'break-all',
          }}>
            {line.text}
          </div>
        ))}
        {running && <div style={{ color: 'var(--amber)' }}>⏳ running...</div>}
      </div>

      {/* Input */}
      <form onSubmit={handleSubmit} style={{
        display: 'flex',
        borderTop: '1px solid var(--border)',
        background: 'rgba(10,10,10,0.8)',
        borderRadius: '0 0 8px 8px',
      }}>
        <span style={{
          padding: '0.5rem 0.5rem 0.5rem 0.75rem',
          color: 'var(--green)',
          fontFamily: 'monospace',
          fontSize: '0.8rem',
          whiteSpace: 'nowrap',
        }}>
          {container}$
        </span>
        <input
          ref={inputRef}
          value={input}
          onChange={e => setInput(e.target.value)}
          onKeyDown={handleKeyDown}
          disabled={running}
          placeholder={running ? 'waiting for command...' : 'type command...'}
          style={{
            flex: 1,
            background: 'transparent',
            border: 'none',
            outline: 'none',
            color: 'var(--text)',
            fontFamily: '"Fira Code", "Cascadia Code", monospace',
            fontSize: '0.8rem',
            padding: '0.5rem 0.75rem 0.5rem 0',
          }}
        />
      </form>
    </div>
  );
}
