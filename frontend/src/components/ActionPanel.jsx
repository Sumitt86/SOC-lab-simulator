export default function ActionPanel({ actions, onBlockIP, onIsolateHost, onKillProcess, onRemoveCron }) {
  if (!actions) return null;

  const {
    blockableIPs = [],
    isolatableHosts = [],
    killableProcesses = [],
    canRemoveCron = false,
  } = actions;

  if (blockableIPs.length === 0 && isolatableHosts.length === 0 && killableProcesses.length === 0 && !canRemoveCron) {
    return null;
  }

  return (
    <section className="panel action-panel">
      <header className="panel-header">
        <h3>Blue Team Actions</h3>
      </header>
      <div className="panel-body">
        {killableProcesses.length > 0 && (
          <div className="action-group">
            <p className="action-label">Kill Malicious Process</p>
            <div className="action-buttons">
              {killableProcesses.map((proc) => (
                <button
                  key={proc.pid}
                  className="btn-action btn-kill"
                  onClick={() => onKillProcess(proc.pid)}
                  title={proc.cmdline || ""}
                >
                  Kill PID {proc.pid} ({proc.name || "unknown"})
                </button>
              ))}
            </div>
          </div>
        )}

        {blockableIPs.length > 0 && (
          <div className="action-group">
            <p className="action-label">Block C2 IP</p>
            <div className="action-buttons">
              {blockableIPs.map((ip) => (
                <button
                  key={ip}
                  className="btn-action btn-block"
                  onClick={() => onBlockIP(ip)}
                >
                  Block {ip}
                </button>
              ))}
            </div>
          </div>
        )}

        {canRemoveCron && (
          <div className="action-group">
            <p className="action-label">Remove Persistence</p>
            <div className="action-buttons">
              <button
                className="btn-action btn-cron"
                onClick={() => onRemoveCron()}
              >
                Remove Cron Jobs
              </button>
            </div>
          </div>
        )}

        {isolatableHosts.length > 0 && (
          <div className="action-group">
            <p className="action-label">Isolate Host</p>
            <div className="action-buttons">
              {isolatableHosts.map((host) => (
                <button
                  key={host}
                  className="btn-action btn-isolate"
                  onClick={() => onIsolateHost(host)}
                >
                  Isolate {host}
                </button>
              ))}
            </div>
          </div>
        )}
      </div>
    </section>
  );
}
