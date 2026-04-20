#!/bin/bash
# Victim container startup — launches all vulnerable services + monitoring
# INTENTIONALLY VULNERABLE: For educational/lab use only.
set -e

BACKEND_URL="${BACKEND_URL:-http://backend:8080}"
MONITOR_INTERVAL="${MONITOR_INTERVAL:-2}"

echo "=============================================="
echo "[victim] Starting Vulnerable Victim Container"
echo "[victim] Backend URL: ${BACKEND_URL}"
echo "=============================================="

# ─── 1. MySQL Server ──────────────────────────────────────────────────
echo "[victim] Starting MySQL server..."
# Initialize MySQL data directory if needed
if [ ! -d /var/lib/mysql/mysql ]; then
    mysqld --initialize-insecure --user=mysql 2>/dev/null
fi

# Phase 1: Start MySQL with skip-grant-tables (socket-only, MySQL 8.0.27+ disables networking)
mysqld --user=mysql --skip-grant-tables &
sleep 3

# Wait for MySQL socket to be ready
for i in $(seq 1 30); do
    if mysqladmin ping --silent 2>/dev/null; then
        echo "[victim] MySQL socket ready."
        break
    fi
    sleep 1
done

# Import vulnerable database while in skip-grant-tables mode
mysql -u root < /docker-entrypoint-initdb.d/database.sql 2>/dev/null || true
# Set root to use mysql_native_password with empty password (VULNERABLE)
mysql -u root -e "FLUSH PRIVILEGES; ALTER USER 'root'@'localhost' IDENTIFIED WITH mysql_native_password BY '';" 2>/dev/null || true
echo "[victim] Database imported."

# Phase 2: Restart MySQL with networking enabled (INTENTIONALLY VULNERABLE — no auth)
# Flush privileges to clear skip-grant-tables, then restart with open bind
mysqladmin shutdown 2>/dev/null || true
sleep 2
mysqld --user=mysql --bind-address=0.0.0.0 &
sleep 3

# Wait for MySQL TCP port
for i in $(seq 1 30); do
    if mysqladmin ping --silent 2>/dev/null; then
        echo "[victim] MySQL is ready (TCP port 3306)."
        break
    fi
    sleep 1
done
echo "[victim] MySQL database initialized with vulnerable data."

# ─── 2. Apache + PHP Web Server ───────────────────────────────────────
echo "[victim] Starting Apache web server..."
# Enable Apache error logging
echo "ServerName victim.minisoc.local" >> /etc/apache2/apache2.conf
# Ensure Apache logs to files (for blue team detection)
a2enmod rewrite 2>/dev/null || true
apachectl start 2>/dev/null || true
echo "[victim] Apache started on port 80."

# ─── 3. SSH Server ────────────────────────────────────────────────────
echo "[victim] Starting SSH server..."
service ssh start 2>/dev/null || /usr/sbin/sshd 2>/dev/null || true
echo "[victim] SSH started on port 22."

# ─── 4. DNS Server (dnsmasq) ──────────────────────────────────────────
echo "[victim] Starting dnsmasq DNS server..."
# Stop systemd-resolved if running (it binds port 53)
systemctl stop systemd-resolved 2>/dev/null || true
# Start dnsmasq
dnsmasq --no-daemon --log-queries --log-facility=/var/log/dnsmasq.log &
echo "[victim] dnsmasq started on port 53."

# ─── 5. Cron Daemon ───────────────────────────────────────────────────
echo "[victim] Starting cron daemon..."
service cron start 2>/dev/null || cron 2>/dev/null || true
echo "[victim] Cron daemon started."

# ─── 6. Audit Daemon ──────────────────────────────────────────────────
echo "[victim] Starting audit daemon..."
auditd -l 2>/dev/null || service auditd start 2>/dev/null || true
echo "[victim] Audit daemon started."

# ─── 7. Process Monitoring Agent ──────────────────────────────────────
echo "[victim] Starting monitoring agent..."
python3 /opt/monitor/process_monitor.py \
    --backend "${BACKEND_URL}" \
    --interval "${MONITOR_INTERVAL}" &
echo "[victim] Monitoring agent started (PID $!)"

# ─── 8. Log Forwarder (sends logs to backend via HTTP) ────────────────
echo "[victim] Starting log forwarder..."

# Helper: JSON-escape a string using jq and POST it
forward_log() {
    local source="$1"
    local line="$2"
    local json
    json=$(printf '%s' "$line" | jq -Rc --arg src "$source" '{"source": $src, "message": .}')
    curl -s -X POST "${BACKEND_URL}/api/logs/ingest" \
        -H "Content-Type: application/json" \
        -d "$json" 2>/dev/null || true
}

(
    # Forward Apache access logs
    tail -F /var/log/apache2/access.log 2>/dev/null | while read line; do
        forward_log "apache" "$line"
    done
) &

(
    # Forward auth logs (SSH)
    tail -F /var/log/auth.log 2>/dev/null | while read line; do
        forward_log "ssh" "$line"
    done
) &

(
    # Forward DNS logs
    tail -F /var/log/dnsmasq.log 2>/dev/null | while read line; do
        forward_log "dns" "$line"
    done
) &

(
    # Forward cron logs
    tail -F /var/log/syslog 2>/dev/null | grep --line-buffered -i cron | while read line; do
        forward_log "cron" "$line"
    done
) &

(
    # Forward audit logs
    tail -F /var/log/audit/audit.log 2>/dev/null | while read line; do
        forward_log "audit" "$line"
    done
) &

echo "[victim] Log forwarders started."

echo "=============================================="
echo "[victim] All services running:"
echo "  - Apache (HTTP)   : port 80"
echo "  - SSH             : port 22"
echo "  - MySQL           : port 3306"
echo "  - dnsmasq (DNS)   : port 53"
echo "  - Cron            : active"
echo "  - Audit           : active"
echo "  - Monitor Agent   : active"
echo "  - Log Forwarders  : active"
echo "[victim] Victim container READY."
echo "=============================================="

# Keep container alive
exec tail -f /dev/null
