function alertTone(alert) {
  if (alert.severity === "CRITICAL") return "alert-critical";
  if (alert.severity === "WARNING") return "alert-warning";
  return "alert-info";
}

function statusBadge(status) {
  if (status === "RESOLVING") return "badge-resolving";
  if (status === "RESOLVED") return "badge-resolved";
  if (status === "FAILED") return "badge-failed";
  return "badge-open";
}

export default function AlertsPanel({ alerts, onResolve }) {
  return (
    <section className="panel">
      <header className="panel-header">
        <h3>Active Alerts</h3>
      </header>
      <div className="panel-body scrollable">
        {alerts.length === 0 ? <p className="placeholder">No alerts right now</p> : null}
        {alerts.map((alert) => (
          <article className={`alert-card ${alertTone(alert)}`} key={alert.id}>
            <div className="alert-top-row">
              <p className="alert-title">
                {alert.title}
                {alert.status && (
                  <span className={`alert-status-badge ${statusBadge(alert.status)}`}>
                    {alert.status}
                  </span>
                )}
              </p>
              {alert.open && onResolve ? (
                <button className="btn-resolve" onClick={() => onResolve(alert.id)}>
                  Resolve
                </button>
              ) : null}
            </div>
            <p className="alert-detail">{alert.detail}</p>
            {alert.actionableFields && Object.keys(alert.actionableFields).length > 0 && (
              <p className="alert-actions-info">
                {alert.actionableFields.killPid && <span className="action-tag">PID: {alert.actionableFields.killPid}</span>}
                {alert.actionableFields.blockIp && <span className="action-tag">IP: {alert.actionableFields.blockIp}</span>}
              </p>
            )}
            <p className="alert-meta">
              {alert.mitreTag || alert.mitreId} | {alert.timestamp}
              {alert.host ? ` | ${alert.host}` : ""}
            </p>
          </article>
        ))}
      </div>
    </section>
  );
}
