#!/bin/bash
# Ransomware Simulation — encrypts files in /tmp (simulation only)
# Detectable by: signature scan (Ransom.FileEncrypt.Generic)
# WARNING: This is a SIMULATION. It only creates .encrypted copies.

TARGET_DIR="${1:-/tmp}"

echo "=== RANSOM NOTE ==="
echo "Your files have been encrypted!"
echo "Send 1 BTC to unlock."
echo "=== RANSOM NOTE ==="

for f in "$TARGET_DIR"/*.txt "$TARGET_DIR"/*.log 2>/dev/null; do
    [ -f "$f" ] || continue
    cp "$f" "${f}.encrypted"
    echo "[ENCRYPTED] $f -> ${f}.encrypted"
done

echo "[*] Ransomware simulation complete"
