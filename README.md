# 🔴🔵 Mini SOC Lab — Immersive Red vs Blue Adversarial Exercise

A **real-time, role-based Red Team vs Blue Team security operations simulation** built on Docker. Choose your role — Red Team operator, Blue Team SOC analyst, or Spectator — and engage in an adversarial exercise with SSE-streamed telemetry, malware analysis, detection signatures, honeypots, and consequence-driven scoring.

---

## 🎯 Overview

This is an **immersive, event-driven adversarial exercise** with role-based views:

- **🔴 Red Team**: Operator terminal with SSE-streamed output, APT campaign presets, kill chain tracking, C2 dashboard
- **🔵 Blue Team**: SOC analyst workstation with SIEM alerts (real-time SSE), malware analysis workbench, detection signature management, honeypot deployment
- **📡 Spectator**: Full mission control dashboard — both teams' scores, kill chain, alerts, logs, all stats
- **⚔️ Consequence-Driven Scoring**: Triage countdowns on critical alerts, auto-escalation on idle defenders, win/lose/draw conditions

---

## 🏗️ Technology Stack

- **Frontend**: React 18 + Vite + React Router (role-based SPA with SSE streaming)
- **Backend**: Java 17 + Spring Boot (SSE via SseEmitter, malware analysis engine, signature matching)
- **Deployment**: Docker Compose (4-container ecosystem, single port 3000)
- **Monitoring**: Python psutil agent on victim (process, network, file, cron detection)

---

## 📁 Project Structure

```
mini-soc-lab-fullstack/
├── frontend/                      # React SPA (role-based routing)
│   ├── src/
│   │   ├── pages/
│   │   │   ├── RoleSelect.jsx         # Role selection + difficulty + game start
│   │   │   ├── RedTeamConsole.jsx     # 🔴 3-column operator console
│   │   │   ├── BlueTeamSOC.jsx        # 🔵 3-column SOC analyst view
│   │   │   └── MissionControl.jsx     # 📡 Spectator dashboard
│   │   ├── components/
│   │   │   ├── OperatorTerminal.jsx   # Dark terminal with SSE streaming
│   │   │   ├── MalwareWorkbench.jsx   # Static analysis + file browser
│   │   │   ├── SignatureManager.jsx   # Detection signature CRUD
│   │   │   ├── RedTeamPanel.jsx       # Legacy Red Team panel (spectator)
│   │   │   ├── BlueTeamPanel.jsx      # Legacy Blue Team panel (spectator)
│   │   │   ├── ActionPanel.jsx        # Blue Team defensive actions
│   │   │   ├── AlertsPanel.jsx        # Alert display & resolution
│   │   │   └── ...
│   │   ├── hooks/
│   │   │   └── useSse.js             # SSE hooks (alerts, logs, command streams)
│   │   ├── api/
│   │   │   └── client.js             # API calls (all endpoints)
│   │   └── styles/app.css
│   ├── Dockerfile
│   ├── nginx.conf                 # SSE proxy config (proxy_buffering off)
│   └── package.json
├── backend/                       # Spring Boot API
│   ├── src/main/java/com/minisoc/lab/
│   │   ├── controller/
│   │   │   ├── RedTeamController.java     # 🔴 execute, stream, upload (hash-block)
│   │   │   ├── BlueTeamController.java    # 🔵 analyze, signatures, honeypots, notes
│   │   │   └── SimulationController.java  # Game control + SSE endpoints
│   │   ├── service/
│   │   │   ├── SimulationService.java     # Core orchestrator (win conditions, escalation)
│   │   │   ├── StreamingService.java      # SSE emitter pools (alerts/logs/commands)
│   │   │   ├── CommandStreamService.java  # Docker exec with line-by-line SSE
│   │   │   ├── MalwareAnalysisService.java # Static analysis (strings, entropy, IOCs)
│   │   │   ├── SignatureService.java      # Detection signature CRUD + matching
│   │   │   ├── EventAlertMapper.java      # Event → Alert (MITRE + signatures + honeypots)
│   │   │   └── ActionExecutor.java        # Blue Team actions (kill, block, isolate)
│   │   └── model/
│   │       ├── GameAlert.java            # Actionable alert (triage countdown, notes)
│   │       ├── DetectionSignature.java   # HASH/STRING/NETWORK_IOC signatures
│   │       ├── AnalysisReport.java       # Malware analysis results
│   │       ├── IncidentReport.java       # Post-game incident report
│   │       ├── GameState.java            # Score, threat, phase, honeypots
│   │       └── ...
│   ├── Dockerfile
│   └── pom.xml
├── containers/
│   ├── victim/                    # Ubuntu target with monitoring agent
│   └── attacker/                  # Ubuntu C2/staging with attack scripts
├── docker-compose.yml
├── RED_TEAM_ATTACK_GUIDE.md
└── README.md
```

---

## 🚀 Quick Start

### Prerequisites
- Docker + Docker Compose
- Port 3000 available

### Run the Stack

```bash
cd mini-soc-lab-fullstack
docker compose up --build
```

**Wait ~10 seconds**, then open: **http://localhost:3000**

You'll see the **Role Selection** screen. Choose your role:
- **🔴 Red Team** — Operator console with terminal + campaign presets
- **🔵 Blue Team** — SOC analyst with SIEM alerts + malware workbench
- **📡 Spectator** — Full mission control dashboard

### Verify Health

```bash
curl http://localhost:8080/api/health
# {"status":"UP"}
```

---

## 🎮 Roles & Gameplay

### 🔴 Red Team Console (`/red`)

3-column operator layout:

| Left Panel | Center | Right Panel |
|------------|--------|-------------|
| APT campaign presets (APT29, FIN7, Lazarus) | **Operator Terminal** with SSE-streamed output | C2 Dashboard (beacon count, persistence) |
| Kill chain checklist | Container toggle (victim/attacker) | Quick action buttons |
| Command history | Command history (↑/↓ navigation) | Score + phase display |

**Key capabilities:**
- Stream command output in real-time via SSE (no polling)
- Upload malware (auto-blocked if Blue Team deployed matching hash signature → -15 pts)
- APT campaign presets with pre-built kill chain checklists
- Container switching between soc-victim and soc-attacker

### 🔵 Blue Team SOC (`/blue`)

3-column SOC analyst layout:

| Left Panel | Center | Right Panel |
|------------|--------|-------------|
| **SIEM Alerts** (SSE real-time) | **Malware Workbench** | Defense Controls |
| Triage countdown timers | Static analysis (strings, entropy, hex) | Quick response buttons |
| Inline actions (kill PID, block IP) | File browser | 🍯 Honeypot planting |
| Analyst notes per alert | Signature workshop | Threat level gauge |

**Key capabilities:**
- Real-time alerts via SSE with severity badges and countdown timers
- Critical alerts have triage deadlines — miss them and threat spikes +15
- Malware analysis: file type, SHA256, extracted strings, entropy, IOC detection
- Deploy detection signatures (HASH, STRING, NETWORK_IOC) that auto-upgrade future alerts
- Plant honeypot files that trigger CRITICAL alerts when Red Team accesses them
- Analyst notes on alerts for investigation documentation
- Redacted command lines until file is analyzed (attribution delay mechanic)

### 📡 Spectator / Mission Control (`/spectator`)

Full dashboard with both teams' views — kill chain visualization, stat cards, alerts, logs, todos.

---

## ⚔️ Scoring & Win Conditions

### Win Conditions

| Outcome | Condition |
|---------|-----------|
| 🔵 **BLUE WIN** | Threat = 0, no malicious PIDs, no persistence, no open critical alerts |
| 🔴 **RED WIN** | Threat ≥ 100 OR attack reaches EXFILTRATION phase |
| 🤝 **DRAW** | Game exceeds 900 seconds (15 minutes) |

### Escalation Mechanics

- **Idle penalty**: If Blue Team takes no action for 45 seconds, attack phase auto-advances
- **Triage countdown**: CRITICAL alerts expire after a set time → threat spikes +15
- **C2 session bonus**: Active C2 beacons add +15 threat every 60 seconds

### Red Team Points

| Action | Points |
|--------|--------|
| Upload malware | +5 |
| Reverse shell / C2 beacon | +10 |
| Cron persistence | +10 |
| Ransomware simulation | +20 |
| Data exfiltration | +20 |
| Malware blocked by signature | **-15** |

### Blue Team Points

| Action | Points |
|--------|--------|
| Analyze file (malware workbench) | +20 |
| Kill process (within 10s of alert) | +40 (speed bonus) |
| Kill process (normal) | +30 |
| Block C2 IP | +35 |
| Block other IP | +25 |
| Deploy detection signature | +30 |
| Remove cron persistence | +20 |
| Isolate host | +40 |

---

## 🔍 New Features

### SSE Streaming
All telemetry streams via Server-Sent Events — no polling lag:
- `GET /api/stream/alerts` — Real-time alert feed
- `GET /api/stream/logs` — Live log entries
- `GET /api/stream/command/{id}` — Per-command stdout/stderr streaming

### Malware Analysis Engine
Static analysis via docker exec on the victim container:
- File type identification (`file` command)
- SHA256 hash computation
- String extraction with suspicious pattern matching
- Entropy calculation (packed/encrypted detection)
- IOC extraction (IPs, URLs, base64 blobs)
- Threat rule matching (reverse shells, droppers, C2 beacons, ransomware)

### Detection Signatures
Blue Team can deploy custom signatures:
- **HASH** — Block known-bad SHA256 hashes (auto-blocks Red Team uploads)
- **STRING** — Match patterns in event data (upgrades alerts to CRITICAL)
- **NETWORK_IOC** — Match IPs/domains (auto-created when blocking C2 IPs)

### Honeypots
Blue Team plants decoy files (e.g., `/tmp/.soc_honeypot_credentials.txt`). When Red Team accesses them, a CRITICAL alert fires with +40 threat impact.

### Post-Game Incident Report
`GET /api/game/report` returns a full incident report: timeline, alerts raised, signatures deployed, Blue Team actions, Red Team commands.

---

## 🗂️ API Endpoints Reference

### Simulation Control
- `POST /api/simulation/start` — Start game (body: `{difficulty, persistSignatures}`)
- `POST /api/simulation/reset` — Reset game state
- `GET /api/dashboard/summary` — Game state
- `GET /api/dashboard/alerts` — Current alerts
- `GET /api/dashboard/logs` — Event logs
- `GET /api/game/report` — Post-game incident report

### SSE Streams
- `GET /api/stream/alerts` — Alert SSE stream
- `GET /api/stream/logs` — Log SSE stream
- `GET /api/stream/command/{commandId}` — Command output stream

### Red Team 🔴
- `POST /api/red-team/execute` — Execute command
- `POST /api/red-team/execute-stream` — Execute with SSE streaming (returns commandId)
- `POST /api/red-team/upload-malware` — Upload malware (hash-checked against signatures)
- `GET /api/red-team/history` — Command audit trail

### Blue Team 🔵
- `GET /api/blue-team/processes` — List processes
- `GET /api/blue-team/connections` — List network connections
- `GET /api/blue-team/files` — List suspicious files
- `POST /api/blue-team/scan-file` — Legacy file scan
- `POST /api/blue-team/analyze-file` — Malware workbench analysis (+20 pts)
- `GET /api/blue-team/behavioral-analysis` — Anomaly detection
- `POST /api/blue-team/signatures` — Deploy detection signature (+30 pts)
- `GET /api/blue-team/signatures` — List signatures
- `DELETE /api/blue-team/signatures/{id}` — Remove signature
- `POST /api/blue-team/plant-honeypot` — Plant honeypot file
- `PATCH /api/blue-team/alerts/{id}/notes` — Update analyst notes

### Blue Team Actions 🛡️
- `POST /api/actions/kill-process` — Kill process by PID
- `POST /api/actions/block-ip` — Block IP (also kills beacon processes)
- `POST /api/actions/isolate-host` — Full host isolation
- `POST /api/actions/remove-cron` — Remove cron persistence

---

## 🐳 Container Architecture

### `soc-backend` (Spring Boot)
- Port 8080, mounts Docker socket
- SSE emitter pools for real-time streaming
- Malware analysis via docker exec subprocess calls
- Signature matching engine, honeypot tracking
- Win condition evaluation, escalation timers

### `soc-frontend` (React + Nginx)
- Port 3000, serves React SPA with role-based routing
- Nginx configured for SSE passthrough (`proxy_buffering off`)
- Routes: `/` (role select), `/red`, `/blue`, `/spectator`

### `soc-victim` (Ubuntu 22.04)
- Attack target with monitoring agent (2s polling)
- Tools: bash, netcat, cron, iptables, python3, psutil
- Honeypot files planted by Blue Team

### `soc-attacker` (Ubuntu 22.04)
- C2/staging container with attack scripts
- Tools: nc, nmap, bash, curl

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
```

### Frontend Only
```bash
cd frontend
npm install
npm run dev
# http://localhost:5173 (requires backend on :8080)
```

### Full Stack (Docker)
```bash
docker compose up --build
```

---

## 🐛 Troubleshooting

### Containers won't start
```bash
docker compose down
docker compose up --build
```

### SSE streams not connecting
- Verify nginx config has `proxy_buffering off`
- Check browser DevTools → Network → EventStream tab
- Backend logs: `docker logs soc-backend | tail -20`

### Malware analysis returns empty
- Ensure game is started and victim container is running
- Check file path exists: `docker exec soc-victim ls -la /tmp/`

### Blue Team sees no alerts
- Wait 2-3 seconds for monitoring agent detection cycle
- Check monitoring agent: `docker exec soc-victim ps aux | grep monitor`

---

## 📚 References

- **MITRE ATT&CK Framework**: https://attack.mitre.org/
- **Docker Exec Reference**: https://docs.docker.com/engine/reference/commandline/exec/

---

**Last Updated**: April 2026  
**Status**: Active Development — Immersive RE-Focused Adversarial Exercise
