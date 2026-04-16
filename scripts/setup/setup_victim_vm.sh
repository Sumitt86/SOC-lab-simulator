#!/bin/bash
# Setup script for victim VM (Ubuntu)
# Run as root: sudo bash setup_victim_vm.sh <BACKEND_HOST_IP>
set -euo pipefail

BACKEND_IP="${1:?Usage: sudo $0 <BACKEND_HOST_IP>}"
AGENT_USER="soc_agent"

echo "============================================"
echo "  SOC Lab — Victim VM Setup"
echo "============================================"

# 1. Create agent user if needed
if ! id "$AGENT_USER" &>/dev/null; then
    useradd -m -s /bin/bash "$AGENT_USER"
    echo "[+] Created user: $AGENT_USER"
fi

# 2. Setup SSH key auth
SSH_DIR="/home/${AGENT_USER}/.ssh"
mkdir -p "$SSH_DIR"
chmod 700 "$SSH_DIR"

if [ ! -f "${SSH_DIR}/authorized_keys" ]; then
    echo "[!] You need to copy the backend's public key to:"
    echo "    ${SSH_DIR}/authorized_keys"
    echo ""
    echo "    On the backend host, run:"
    echo "    ssh-keygen -t ed25519 -f ~/.ssh/soc_lab_key -N ''"
    echo "    ssh-copy-id -i ~/.ssh/soc_lab_key.pub ${AGENT_USER}@<this_vm_ip>"
fi
chown -R "${AGENT_USER}:${AGENT_USER}" "$SSH_DIR"

# 3. Install dependencies
apt-get update -qq
apt-get install -y -qq python3 python3-pip netcat-openbsd cron iptables

pip3 install psutil requests --break-system-packages 2>/dev/null || \
pip3 install psutil requests

echo "[+] Python dependencies installed"

# 4. Copy monitoring agent
AGENT_DIR="/opt/soc-agent"
mkdir -p "$AGENT_DIR"

# Assumes this script is in scripts/setup/ and agent is in scripts/monitor/
SCRIPT_DIR="$(cd "$(dirname "$0")" && pwd)"
if [ -f "${SCRIPT_DIR}/../monitor/process_monitor.py" ]; then
    cp "${SCRIPT_DIR}/../monitor/process_monitor.py" "$AGENT_DIR/"
    echo "[+] Monitoring agent copied to $AGENT_DIR"
else
    echo "[!] Copy process_monitor.py to $AGENT_DIR manually"
fi

# 5. Create systemd service for the agent
cat > /etc/systemd/system/soc-agent.service << EOF
[Unit]
Description=SOC Lab Monitoring Agent
After=network-online.target
Wants=network-online.target

[Service]
Type=simple
User=root
ExecStart=/usr/bin/python3 ${AGENT_DIR}/process_monitor.py --backend http://${BACKEND_IP}:8080 --interval 2
Restart=always
RestartSec=5

[Install]
WantedBy=multi-user.target
EOF

systemctl daemon-reload
systemctl enable soc-agent
echo "[+] systemd service created (soc-agent)"

# 6. Allow sudo for agent user (for defensive commands)
echo "${AGENT_USER} ALL=(ALL) NOPASSWD: /bin/kill, /usr/sbin/iptables, /usr/bin/crontab, /bin/rm" \
    > "/etc/sudoers.d/${AGENT_USER}"
chmod 440 "/etc/sudoers.d/${AGENT_USER}"
echo "[+] Sudo permissions configured for $AGENT_USER"

# 7. Create target data directory for ransomware sim
mkdir -p /tmp/soc_data
echo "Sensitive document 1" > /tmp/soc_data/document_1.txt
echo "Sensitive document 2" > /tmp/soc_data/document_2.txt
echo "[+] Test data created in /tmp/soc_data/"

echo ""
echo "============================================"
echo "  Setup complete!"
echo "============================================"
echo ""
echo "Next steps:"
echo "  1. Copy SSH public key to ${SSH_DIR}/authorized_keys"
echo "  2. Start the agent: systemctl start soc-agent"
echo "  3. Check status: systemctl status soc-agent"
echo "  4. View logs: journalctl -u soc-agent -f"
