import { useState, useEffect, useRef, useCallback } from 'react';

/**
 * Hook to subscribe to a Server-Sent Events endpoint.
 * @param {string} url - SSE endpoint URL
 * @param {object} options
 * @param {boolean} options.enabled - whether to connect (default true)
 * @param {function} options.onMessage - callback for each message
 * @returns {{ data: any[], connected: boolean, error: string|null }}
 */
export function useSse(url, { enabled = true, onMessage } = {}) {
  const [data, setData] = useState([]);
  const [connected, setConnected] = useState(false);
  const [error, setError] = useState(null);
  const esRef = useRef(null);
  const onMessageRef = useRef(onMessage);
  onMessageRef.current = onMessage;

  useEffect(() => {
    if (!enabled || !url) return;

    const es = new EventSource(url);
    esRef.current = es;

    es.onopen = () => {
      setConnected(true);
      setError(null);
    };

    es.onmessage = (event) => {
      try {
        const parsed = JSON.parse(event.data);
        setData(prev => [...prev.slice(-500), parsed]);
        if (onMessageRef.current) onMessageRef.current(parsed);
      } catch {
        // plain text
        setData(prev => [...prev.slice(-500), event.data]);
        if (onMessageRef.current) onMessageRef.current(event.data);
      }
    };

    es.onerror = () => {
      setConnected(false);
      setError('SSE connection lost');
    };

    return () => {
      es.close();
      esRef.current = null;
      setConnected(false);
    };
  }, [url, enabled]);

  const clear = useCallback(() => setData([]), []);

  return { data, connected, error, clear };
}

/**
 * Hook for streaming command output via SSE.
 * @param {string} commandId
 * @returns {{ lines: Array, exitCode: number|null, running: boolean }}
 */
export function useCommandStream(commandId) {
  const [lines, setLines] = useState([]);
  const [exitCode, setExitCode] = useState(null);
  const [running, setRunning] = useState(false);

  useEffect(() => {
    if (!commandId) return;
    setLines([]);
    setExitCode(null);
    setRunning(true);

    const es = new EventSource(`/api/stream/command/${commandId}`);

    es.addEventListener('stdout', (e) => {
      setLines(prev => [...prev, { type: 'stdout', text: e.data }]);
    });
    es.addEventListener('stderr', (e) => {
      setLines(prev => [...prev, { type: 'stderr', text: e.data }]);
    });
    es.addEventListener('complete', (e) => {
      try {
        const data = JSON.parse(e.data);
        setExitCode(data.exitCode);
      } catch {
        setExitCode(-1);
      }
      setRunning(false);
      es.close();
    });
    es.onerror = () => {
      setRunning(false);
      es.close();
    };

    return () => es.close();
  }, [commandId]);

  return { lines, exitCode, running };
}
