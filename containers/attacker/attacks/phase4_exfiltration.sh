#!/bin/bash
# Phase 4: Exfiltration — Ransomware simulation
# Usage: ./phase4_exfiltration.sh [TARGET_DIR]
set -euo pipefail

TARGET_DIR="${1:-/tmp/soc_data}"

echo "[*] Phase 4: Ransomware simulation in ${TARGET_DIR}"

# Create dummy data files if they don't exist
mkdir -p "$TARGET_DIR"
for i in $(seq 1 5); do
    echo "Sensitive data file ${i}" > "${TARGET_DIR}/document_${i}.txt"
done

# "Encrypt" — just rename with .encrypted extension (non-destructive)
for f in "${TARGET_DIR}"/*.txt; do
    [ -f "$f" ] || continue
    cp "$f" "${f}.encrypted"
    echo "[!] Encrypted: ${f}"
done

# Drop ransom note
cat > "${TARGET_DIR}/README_RANSOM.txt" << 'EOF'
YOUR FILES HAVE BEEN ENCRYPTED
This is a simulation. No real encryption occurred.
EOF

echo "[+] Ransomware simulation complete. Files in ${TARGET_DIR}"
