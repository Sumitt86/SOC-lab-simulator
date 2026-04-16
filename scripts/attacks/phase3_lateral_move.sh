#!/bin/bash
# Phase 3: Lateral Movement — Subnet probe
# Usage: ./phase3_lateral_move.sh <SUBNET>
set -euo pipefail

SUBNET="${1:-192.168.56}"

echo "[*] Phase 3: Probing subnet ${SUBNET}.0/24"

for i in $(seq 1 254); do
    (ping -c 1 -W 1 "${SUBNET}.${i}" &>/dev/null && echo "[+] Host alive: ${SUBNET}.${i}") &
done
wait

echo "[*] Subnet probe complete"
