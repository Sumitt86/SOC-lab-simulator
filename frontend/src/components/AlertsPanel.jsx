function alertTone(alert) {
  if (alert.severity === "CRITICAL") return "alert-critical";
  if (alert.severity === "WARNING") return "alert-warning";
  return "alert-info";
}

export default function AlertsPanel({ alerts }) {
  return (
    <section className="panel">
      <header className="panel-header">
        <h3>Active Alerts</h3>
      </header>
      <div className="panel-body scrollable">
        {alerts.length === 0 ? <p className="placeholder">No alerts right now</p> : null}
        {alerts.map((alert) => (
          <article className={`alert-card ${alertTone(alert)}`} key={alert.id}>
            <p className="alert-title">{alert.title}</p>
            <p className="alert-detail">{alert.detail}</p>
            <p className="alert-meta">{alert.mitreTag} | {alert.timestamp}</p>
          </article>
        ))}
      </div>
    </section>
  );
}
