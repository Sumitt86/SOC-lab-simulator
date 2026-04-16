function severityClass(severity) {
  if (severity === "CRIT") return "sev-crit";
  if (severity === "WARN") return "sev-warn";
  if (severity === "OK") return "sev-ok";
  return "sev-info";
}

export default function LiveLog({ logs }) {
  return (
    <section className="panel">
      <header className="panel-header">
        <h3>Live Event Log</h3>
      </header>
      <div className="panel-body scrollable">
        {logs.length === 0 ? <p className="placeholder">No events yet</p> : null}
        {logs.map((log) => (
          <div className="log-entry" key={log.id}>
            <span className="log-time">{log.timestamp}</span>
            <span className={`log-severity ${severityClass(log.severity)}`}>{log.severity}</span>
            <span className="log-message">{log.message}</span>
          </div>
        ))}
      </div>
    </section>
  );
}
