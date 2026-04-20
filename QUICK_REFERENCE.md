# MITRE ATT&CK Exercise: Quick Reference & Next Steps

## Executive Summary

You now have a **comprehensive 5-vector attack simulation** built on MITRE ATT&CK framework with automated scoring, detection, and remediation. Each vector chains multiple techniques through a 4-stage kill chain, allowing red team to penetrate deeper for more points while blue team gets maximum points for early detection.

---

## Kill Chain Visual (All Vectors)

```
┌─────────────────────────────────────────────────────────────────┐
│ STAGE 1: RECONNAISSANCE & INITIAL ACCESS (Blue +150 max)        │
├─────────────────────────────────────────────────────────────────┤
│ Vector 1: nmap -sV → SQL Injection (T1190)                      │
│ Vector 2: ssh-keyscan → SSH Brute Force (T1110)                │
│ Vector 3: dig axfr → DNS Poisoning (T1584)                     │
│ Vector 4: Analyze dependencies → Supply Chain (T1195)           │
│ Vector 5: docker ps → Container Discovery (T1610)               │
└─────────────────────────────────────────────────────────────────┘
                              ↓
┌─────────────────────────────────────────────────────────────────┐
│ STAGE 2: PERSISTENCE (Blue +100 max, Red +75)                   │
├─────────────────────────────────────────────────────────────────┤
│ Vector 1: Cron job injection (T1053)                            │
│ Vector 2: SSH key injection (T1098)                             │
│ Vector 3: Systemd service creation (T1547)                      │
│ Vector 4: npm postinstall backdoor (T1547)                      │
│ Vector 5: Host cron from escaped container (T1053)              │
└─────────────────────────────────────────────────────────────────┘
                              ↓
┌─────────────────────────────────────────────────────────────────┐
│ STAGE 3: PRIVILEGE ESCALATION (Blue +75 max, Red +100)          │
├─────────────────────────────────────────────────────────────────┤
│ Vector 1: sudo www-data → root bash (T1548)                     │
│ Vector 2: Exploited sudoers (T1548)                             │
│ Vector 3: Service running as root (implicit priv esc)           │
│ Vector 4: Service running as root (implicit priv esc)           │
│ Vector 5: Cgroup escape (T1611)                                 │
└─────────────────────────────────────────────────────────────────┘
                              ↓
┌─────────────────────────────────────────────────────────────────┐
│ STAGE 4: EXFILTRATION (Blue +50 max, Red +200 MAX)              │
├─────────────────────────────────────────────────────────────────┤
│ Vector 1: curl /etc/passwd to C2 (T1041)                        │
│ Vector 2: scp sensitive files (T1041)                           │
│ Vector 3: DNS tunnel exfiltration (T1041)                       │
│ Vector 4: C2 reverse shell (T1041)                              │
│ Vector 5: Host data tar → C2 (T1041)                            │
└─────────────────────────────────────────────────────────────────┘
      ↓ RED WINS (MAX: 475 pts)         ↑ BLUE WINS (MAX: 375 pts)
```

---

## Attack Vector Comparison Matrix

| Aspect | Vector 1 (Web) | Vector 2 (SSH) | Vector 3 (DNS) | Vector 4 (Supply) | Vector 5 (Docker) |
|--------|---|---|---|---|---|
| **Entry Point** | HTTP 80 | SSH 22 | DNS 53 | npm install | Docker socket |
| **Primary Tactic** | Exploitation | Brute Force | Infrastructure | Supply Chain | Privilege Escape |
| **Complexity** | Easy | Easy | Medium | Medium | Hard |
| **Time to Access** | < 1 min | 1-2 min | < 1 min | 5-10 min | 2-5 min |
| **Detection Difficulty** | Easy | Medium | Hard | Hard | Very Hard |
| **Damage Potential** | High | Very High | Very High | Extreme | Extreme |
| **MITRE Techniques** | T1190, T1053, T1548, T1041 | T1110, T1098, T1548, T1041 | T1590, T1584, T1547, T1041 | T1195, T1027, T1036, T1547 | T1610, T1611, T1053, T1041 |

---

## Detection Strategy By Technique

### Quick Reference: Detection Rules Per Technique

| Technique | Code | Detection Rule | Severity | Latency |
|-----------|------|---|---|---|
| Reconnaissance | T1589, T1590 | Port scan, enumeration commands | MEDIUM | 30-60s |
| **Initial Access** | **T1190** | **SQL injection patterns, file upload** | **HIGH** | **< 5s** |
| | T1110 | SSH login failures threshold | MEDIUM | 60-300s |
| | T1584 | DNS query anomalies | MEDIUM | < 10s |
| | T1195 | Package integrity mismatch | HIGH | < 5s |
| | T1610 | Docker daemon access | HIGH | < 2s |
| **Persistence** | **T1053** | **Cron file modifications** | **CRITICAL** | **< 2s** |
| | T1098 | SSH key injection | CRITICAL | < 5s |
| | T1547 | Service file creation | CRITICAL | < 5s |
| | T1027 | Obfuscated process execution | HIGH | 10-30s |
| **Privilege Escalation** | **T1548** | **sudo command execution** | **CRITICAL** | **< 5s** |
| | T1611 | Cgroup escape syscalls | CRITICAL | < 2s |
| **Exfiltration** | **T1041** | **Outbound connections to C2** | **CRITICAL** | **< 10s** |

---

## Scoring Reference Card

### Points Breakdown

```
VECTOR 1 (Web App) - Max 525 pts if Red wins
├─ T1190 Detected → Blue +150
└─ T1190 Not Detected → Red +50
   ├─ T1053 Detected → Blue +100
   └─ T1053 Not Detected → Red +75
      ├─ T1548 Detected → Blue +75
      └─ T1548 Not Detected → Red +100
         ├─ T1041 Detected → Blue +50
         └─ T1041 Not Detected → Red +200

TIME BONUSES
├─ Red finishes chain < 5 min: +50
├─ Red evades detection > 30 min: +100
├─ Blue detects < 2 min: +25
└─ Blue completes remediation < 5 min: +25
```

### Example Game Outcomes

**Red Wins Decisively** (All stages complete)
```
T1190 (+50) → T1053 (+75) → T1548 (+100) → T1041 (+200) + Time Bonus (+50)
Total: 475 points (RED TEAM)
Blue detected 0 attack techniques
```

**Blue Wins Early** (Catches at Persistence)
```
T1190 Detected (+150) → T1053 Detected (+100) → Remediate (+25)
Total: 275 points (BLUE TEAM)
Prevented stages 3 & 4 = 150 + 200 = 350 pts saved
```

**Blue Wins Late** (Catches at Exfiltration)
```
T1190 (+50) → T1053 (+75) → T1548 (+100) → T1041 Detected (+50)
Red Total: 275 | Blue Total: 50
Close match, but exfiltration = most critical
```

---

## Frontend Component Structure

### Current Mini-SOC Components to Integrate

```
BlueTeamSOC.jsx (Main Dashboard)
├── AlertsPanel (EXTEND)
│   ├── Real-time alerts with DET-codes
│   ├── Severity coloring (RED/YELLOW/BLUE)
│   └── Remediation action buttons
│
├── LiveLog (EXTEND)
│   ├── Attack timeline (Red events)
│   ├── Detection timeline (Blue events)
│   ├── Latency indicators
│   └── Visual correlation lines
│
├── NetworkMonitor (EXTEND)
│   ├── Suspicious flow visualization
│   ├── C2 connection highlights
│   └── Exfiltration data volume
│
├── StatCard (NEW)
│   ├── Red Team Score
│   ├── Blue Team Score
│   ├── Current Stage (Recon → Initial Access → Persistence → ...)
│   └── Time Elapsed
│
└── MitreMapper (NEW)
    ├── Technique T-codes with descriptions
    ├── Detection rule used
    └── Mitigation strategy displayed
```

---

## Backend Service Architecture

### New Java Services to Create

```
com.minisoc.lab.service/
├── AttackVectorService.java
│   ├── executeAttack(vectorId, technique)
│   ├── getAvailableTechniques(currentStage)
│   └── recordAttackEvent(technique, timestamp)
│
├── DetectionRuleEngine.java
│   ├── evaluateRule(logEntry, ruleId)
│   ├── correlateEvents(events)
│   └── generateAlert(technique, severity)
│
├── ScoringService.java
│   ├── calculateStageScore(stage, outcome)
│   ├── applyTimeBonus(technique, completionTime)
│   └── getGameScore(sessionId)
│
├── LogAggregationService.java
│   ├── collectLogs(containerName)
│   ├── parseLogs(rawLogs)
│   └── streamLogsToDetection(logStream)
│
└── RemediationService.java
    ├── executeRemediationAction(action)
    ├── verifyActionSuccess(action)
    └── rollbackAction(actionId)
```

---

## Vulnerable Endpoints to Expose

### Victim Container Ports & Vulnerabilities

```
PORT 80 (HTTP)
├─ /index.php?username=VULNERABLE (SQL Injection)
│  └─ ' OR '1'='1' → Authentication bypass
├─ /admin.php (File Upload RCE)
│  └─ Upload .php file → Execute as www-data
├─ /config.php (Credential Exposure)
│  └─ Contains $db_user, $db_pass
└─ Log location: /var/log/apache2/access.log

PORT 22 (SSH)
├─ Default credentials: www-data:www-data
├─ Weak sudoers: www-data ALL=(ALL) NOPASSWD:/bin/bash
└─ Log location: /var/log/auth.log

PORT 53 (DNS)
├─ Zone transfer enabled (dig @localhost axfr)
├─ DNSSEC disabled
└─ Log location: /var/log/dnsmasq.log

PORT 3306 (MySQL) - Optional
├─ Default creds: root:rootpass
└─ Accessible after database credential discovery

/var/spool/cron/crontabs/ (Writable by www-data)
/etc/systemd/system/ (Writable for service persistence)
/tmp/ (World writable, executable)
/home/www-data/ (Contains .env, .ssh with weak perms)
```

---

## Detection Rules (Simplified Reference)

### DET-001: SQL Injection Pattern
```
Pattern: `'` + (`OR` | `UNION` | `SELECT` | `DROP`)
Source: Apache access.log query parameters
Severity: HIGH → CRITICAL if RCE follows
Remediation: Block IP for 5 min
```

### DET-003: Cron Modification
```
Pattern: Write event on /var/spool/cron/ or /etc/cron.d/
Source: auditd logs (WRITE action)
Severity: CRITICAL (immediate persistence)
Remediation: Remove cron entry, kill cron daemon
```

### DET-006: SSH Privilege Escalation
```
Pattern: sudo command from SSH session spawning shell
Source: auditd EXECVE records, sshd parent process
Severity: CRITICAL
Remediation: Kill SSH session, reset sudoers
```

### DET-011: C2 Beacon
```
Pattern: Recurring outbound connection (TCP/UDP) to unknown IP/domain
Source: netflow or tcpdump analysis
Severity: CRITICAL
Remediation: Block destination IP/domain via firewall
```

---

## IMMEDIATE ACTION ITEMS (Next Steps)

### PRIORITY 1: Infrastructure (Do This First - Day 1)

- [ ] **Create vulnerable Dockerfile**
  - File: `containers/victim/Dockerfile`
  - Contains: Apache2, PHP, MySQL, OpenSSH, dnsmasq
  - Vulnerabilities: SQL injection endpoint, weak SSH creds, writable cron/systemd
  - Deadline: EOD

- [ ] **Modify docker-compose.yml**
  - Add victim service with exposed ports: 80, 22, 53, 3306
  - Mount `/var/log/` from victim to host
  - Deadline: EOD

### PRIORITY 2: Backend Controllers (Days 1-2)

- [ ] **Extend RedTeamController.java**
  - Add endpoints:
    - `POST /api/red/attack/start`
    - `GET /api/red/attack/{sessionId}/status`
    - `POST /api/red/attack/{sessionId}/next-step`
  - Deadline: End of Day 1

- [ ] **Extend BlueTeamController.java**
  - Add endpoints:
    - `GET /api/blue/alerts`
    - `GET /api/blue/detection-status`
    - `POST /api/blue/remediate/{alertId}`
  - Deadline: End of Day 1

### PRIORITY 3: Detection Engine (Days 2-3)

- [ ] **Create DetectionRuleEngine.java**
  - Implement basic rules: DET-001, DET-003, DET-006, DET-011
  - Support for pattern matching and log parsing
  - Deadline: End of Day 2

- [ ] **Create LogAggregationService.java**
  - Collect Apache logs, SSH logs, auditd logs
  - Parse and normalize log entries
  - Deadline: End of Day 2

### PRIORITY 4: Attack Scripts (Days 3-4)

- [ ] **Create Vector 1 Attack Script** (`containers/attacker/attacks/vector1_web_sqli.sh`)
  - SQL injection → RCE → Cron persistence
  - Deadline: End of Day 3

- [ ] **Create Vector 2 Attack Script** (`containers/attacker/attacks/vector2_ssh_bruteforce.sh`)
  - SSH brute force → Key injection → Privilege escalation
  - Deadline: End of Day 3

- [ ] **Create Vector 3 Attack Script** (`containers/attacker/attacks/vector3_dns_poison.sh`)
  - DNS enumeration → Zone transfer → Malware delivery
  - Deadline: End of Day 4

### PRIORITY 5: Frontend Updates (Days 4-5)

- [ ] **Update BlueTeamSOC.jsx**
  - Extend AlertsPanel with real-time alerts
  - Extend LiveLog with attack timeline
  - Add StatCard component for scoring
  - Deadline: End of Day 4

### PRIORITY 6: Scoring System (Day 5)

- [ ] **Create ScoringService.java**
  - Calculate points per stage
  - Apply time bonuses
  - Track game state
  - Deadline: End of Day 5

---

## File Structure to Create

```
mini-soc-lab-fullstack/
├── MITRE_ATTACK_PLAN.md (✅ Created)
├── IMPLEMENTATION_PHASES.md (✅ Created)
│
├── backend/
│   ├── src/main/java/com/minisoc/lab/
│   │   ├── service/
│   │   │   ├── AttackVectorService.java (TODO)
│   │   │   ├── DetectionRuleEngine.java (TODO)
│   │   │   ├── LogAggregationService.java (TODO)
│   │   │   ├── ScoringService.java (TODO)
│   │   │   └── RemediationService.java (TODO)
│   │   │
│   │   └── controller/
│   │       ├── RedTeamController.java (EXTEND)
│   │       ├── BlueTeamController.java (EXTEND)
│   │       └── ScoringController.java (NEW)
│   │
│   └── resources/
│       └── detection-rules.yaml (NEW)
│           ├── DET-001 to DET-018 definitions
│           └── Pattern matching rules
│
├── containers/
│   ├── victim/
│   │   ├── Dockerfile (MODIFY)
│   │   ├── vulnerable-app/
│   │   │   ├── index.php (SQL injection)
│   │   │   ├── admin.php (RCE upload)
│   │   │   ├── config.php (credentials)
│   │   │   └── database.sql (sample DB)
│   │   └── startup.sh (NEW)
│   │
│   └── attacker/
│       └── attacks/
│           ├── vector1_web_sqli.sh (TODO)
│           ├── vector2_ssh_bruteforce.sh (TODO)
│           ├── vector3_dns_poison.sh (TODO)
│           ├── vector4_supply_chain.sh (TODO)
│           ├── vector5_container_escape.sh (TODO)
│           └── malware-sim/ (Backdoor scripts)
│
├── frontend/
│   └── src/
│       ├── components/
│       │   ├── BlueTeamSOC.jsx (EXTEND)
│       │   ├── AlertsPanel.jsx (EXTEND)
│       │   ├── LiveLog.jsx (EXTEND)
│       │   ├── StatCard.jsx (EXTEND)
│       │   ├── MitreMapper.jsx (NEW)
│       │   └── KillChainVisualizer.jsx (NEW)
│       │
│       └── hooks/
│           ├── useAttackStream.js (NEW)
│           ├── useDetectionAlerts.js (NEW)
│           └── useGameScore.js (NEW)
│
└── docker-compose.yml (MODIFY)
    ├── Add victim service
    ├── Add attacker service
    ├── Add log volume mounts
    └── Wire up port mappings
```

---

## Testing Checklist Before Going Live

### Infrastructure Testing
- [ ] Victim container starts without errors
- [ ] All ports (80, 22, 53, 3306) accessible
- [ ] Web app SQL injection confirmed working
- [ ] SSH login with default creds works
- [ ] Logs are collected in `/var/log/`

### Attack Vector Testing
- [ ] Vector 1 (Web): Complete SQL → RCE → Cron chain
- [ ] Vector 2 (SSH): Complete brute force → key inject → priv esc chain
- [ ] Vector 3 (DNS): Zone transfer detected + malware delivery works
- [ ] Vector 4 (Supply): npm package installation triggers backdoor
- [ ] Vector 5 (Docker): Container escape attempted (simulated)

### Detection Testing
- [ ] Each DET rule fires when triggered
- [ ] Alerts appear in frontend < 2 seconds
- [ ] Alert correlation works (multi-event alerts)
- [ ] Remediation actions reduce attack success rate

### Scoring Testing
- [ ] Points awarded correctly per stage
- [ ] Time bonuses applied
- [ ] Winner determined correctly
- [ ] Leaderboard updated

### Integration Testing
- [ ] Full end-to-end: Attack → Detection → Remediation → Scoring
- [ ] WebSocket streaming stable
- [ ] No database errors
- [ ] No frontend crashes

---

## Success Criteria (Final Goal)

By end of Week 5:

✅ **5 complete attack vectors** with 3-5 chained techniques each
✅ **18 detection rules** (DET-001 to DET-018) mapped to MITRE techniques
✅ **Automated remediation** for blue team (block, kill, remove, etc.)
✅ **Real-time scoring** visible on dashboard (Red vs Blue points)
✅ **Kill chain visualization** showing attack progression
✅ **Full MITRE ATT&CK mapping** documented in project
✅ **Response latency < 3 seconds** (attack detection to alert)
✅ **95%+ detection accuracy** (catch 19/20 techniques)
✅ **Complete documentation** (attacker + defender guides)
✅ **All existing components integrated** (controllers, panels, etc.)

---

## Questions Resolved ✅

1. ✅ **Attack Depth**: 3-5 vectors → **5 chosen**
2. ✅ **Chaining**: Chained techniques with different entry points → **Implementation plan ready**
3. ✅ **Technical Focus**: Malware analysis & reverse engineering → **Vectors designed around this**
4. ✅ **Detection Complexity**: Layered but intermediate → **Rule engine designed**
5. ✅ **Scoring**: Custom point distribution → **Full matrix provided**
6. ✅ **Component Reuse**: Extend existing components → **Architecture documented**
7. ✅ **Phases**: Multi-phase implementation → **5 phases with deliverables**

---

## Still Need Your Input On

Nothing critical - you're ready to start! But optional:

- [ ] Prefer malware binaries to be actual ELF files, or bash scripts with .bin extension?
- [ ] Should blue team have "practice mode" where attacks slow down for learning?
- [ ] Any specific tools/frameworks you want integrated (Wazuh, ELK, Splunk)?
- [ ] Want vector difficulty tiers (Easy, Medium, Hard, Expert)?

**Ready to proceed?** Start with PRIORITY 1 (Infrastructure) tomorrow!

