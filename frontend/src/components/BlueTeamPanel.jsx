import { useState } from "react";

export default function BlueTeamPanel({
  onGetProcesses, onGetConnections, onGetFiles,
  onScanFile, onBehavioralAnalysis
}) {
  const [output, setOutput] = useState(null);
  const [scanPath, setScanPath] = useState("/tmp/");
  const [loading, setLoading] = useState("");
  const [scanResult, setScanResult] = useState(null);
  const [behaviorResult, setBehaviorResult] = useState(null);

  async function run(label, fn, setter) {
    setLoading(label);
    try {
      const result = await fn();
      setter(result);
    } catch (err) {
      setter({ success: false, error: err.message });
    } finally {
      setLoading("");
    }
  }

  return (
    <section className="panel blue-team-panel">
      <header className="panel-header panel-header-blue">
        <h3>🔵 Blue Team Investigation</h3>
      </header>
      <div className="blue-team-body">
        {/* Investigation buttons */}
        <div className="investigation-buttons">
          <button
            className="btn-investigate"
            disabled={!!loading}
            onClick={() => run("processes", onGetProcesses, setOutput)}
          >
            {loading === "processes" ? "⏳" : "🔍"} Processes
          </button>
          <button
            className="btn-investigate"
            disabled={!!loading}
            onClick={() => run("connections", onGetConnections, setOutput)}
          >
            {loading === "connections" ? "⏳" : "🌐"} Connections
          </button>
          <button
            className="btn-investigate"
            disabled={!!loading}
            onClick={() => run("files", onGetFiles, setOutput)}
          >
            {loading === "files" ? "⏳" : "📁"} Files
          </button>
          <button
            className="btn-investigate btn-investigate-behavior"
            disabled={!!loading}
            onClick={() => run("behavior", () => onBehavioralAnalysis(5), setBehaviorResult)}
          >
            {loading === "behavior" ? "⏳" : "🧠"} Behavioral Analysis
          </button>
        </div>

        {/* File scanner */}
        <div className="file-scanner">
          <form onSubmit={(e) => {
            e.preventDefault();
            if (!scanPath.trim()) return;
            run("scan", () => onScanFile(scanPath.trim()), setScanResult);
          }}>
            <input
              type="text"
              className="scan-input"
              value={scanPath}
              onChange={(e) => setScanPath(e.target.value)}
              placeholder="/tmp/suspicious_file"
            />
            <button className="btn-scan" type="submit" disabled={!!loading}>
              {loading === "scan" ? "⏳ Scanning..." : "🔬 Scan File"}
            </button>
          </form>
        </div>

        {/* Scan result */}
        {scanResult && (
          <div className={`scan-result verdict-${(scanResult.verdict || "unknown").toLowerCase()}`}>
            <h4>Scan Result: {scanResult.filePath}</h4>
            <div className="scan-details">
              <span>Verdict: <strong>{scanResult.verdict}</strong></span>
              <span>Score: <strong>{scanResult.threatScore}/100</strong></span>
              <span>Entropy: <strong>{scanResult.entropy?.toFixed(2)}</strong></span>
              <span>Hash: <code>{scanResult.hash?.substring(0, 16)}...</code></span>
            </div>
            {scanResult.threats && scanResult.threats.length > 0 && (
              <div className="threat-list">
                {scanResult.threats.map((t, i) => (
                  <div key={i} className={`threat-item severity-${(t.severity || "").toLowerCase()}`}>
                    <span className="threat-name">{t.name}</span>
                    <span className="threat-type">{t.type}</span>
                    <span className="threat-severity">{t.severity}</span>
                    <p className="threat-detail">{t.detail}</p>
                  </div>
                ))}
              </div>
            )}
          </div>
        )}

        {/* Behavioral analysis result */}
        {behaviorResult && (
          <div className={`behavior-result rec-${(behaviorResult.recommendation || "").toLowerCase().replace(/_/g, "-")}`}>
            <h4>Behavioral Analysis ({behaviorResult.analyzedMinutes} min window)</h4>
            <div className="behavior-summary">
              <span>Events: <strong>{behaviorResult.eventCount}</strong></span>
              <span>Score: <strong>{behaviorResult.combinedThreatScore}/100</strong></span>
              <span>Action: <strong>{behaviorResult.recommendation?.replace(/_/g, " ")}</strong></span>
            </div>
            {behaviorResult.anomalies && behaviorResult.anomalies.length > 0 && (
              <div className="anomaly-list">
                {behaviorResult.anomalies.map((a, i) => (
                  <div key={i} className="anomaly-item">
                    <span className="anomaly-type">{a.type?.replace(/_/g, " ")}</span>
                    <span className="anomaly-score">Score: {a.score}</span>
                    <p className="anomaly-desc">{a.description}</p>
                  </div>
                ))}
              </div>
            )}
            {(!behaviorResult.anomalies || behaviorResult.anomalies.length === 0) && (
              <p className="no-anomalies">No behavioral anomalies detected.</p>
            )}
          </div>
        )}

        {/* Raw investigation output */}
        {output && (
          <div className="investigation-output">
            <h4>Investigation Output</h4>
            <pre className="investigation-pre">
              {output.output || output.error || "No output"}
            </pre>
          </div>
        )}
      </div>
    </section>
  );
}
