#!/bin/bash
# Victim container startup — launches monitoring agent + keeps container alive
set -e

BACKEND_URL="${BACKEND_URL:-http://backend:8080}"
MONITOR_INTERVAL="${MONITOR_INTERVAL:-2}"

echo "[victim] Starting victim container..."
echo "[victim] Backend URL: ${BACKEND_URL}"

# Start cron daemon (needed for persistence attack phase)
service cron start 2>/dev/null || true

# Start the monitoring agent in background
echo "[victim] Starting monitoring agent..."
python3 /opt/monitor/process_monitor.py \
    --backend "${BACKEND_URL}" \
    --interval "${MONITOR_INTERVAL}" &

echo "[victim] Monitoring agent started (PID $!)"
echo "[victim] Victim container ready."

# Keep container alive
exec tail -f /dev/null
