#!/usr/bin/env python3
"""
SOC Lab — Victim VM Monitoring Agent
=====================================
Runs on the victim VM. Monitors processes, network connections, cron jobs,
and file system changes. Sends SystemEvent objects to the backend via HTTP.

Usage:
    python process_monitor.py --backend http://192.168.56.1:8080 --interval 2

Requires: psutil, requests  (pip install -r requirements.txt)
"""

import argparse
import hashlib
import json
import logging
import os
import platform
import re
import socket
import subprocess
import sys
import time
import uuid
from datetime import datetime, timezone
from pathlib import Path

import psutil
import requests

# ---------------------------------------------------------------------------
# Configuration
# ---------------------------------------------------------------------------
LOG_FORMAT = "%(asctime)s [%(levelname)s] %(message)s"
WATCH_DIRS = ["/tmp", "/var/tmp", "/dev/shm", "/home"]
CRON_SPOOL = "/var/spool/cron/crontabs"
MONITORED_USERS = {"root", "soc_agent"}

# Suspicious patterns — anything the agent should flag with high confidence
SUSPICIOUS_PROCS = re.compile(
    r"\b(nc|ncat|netcat|socat|nmap|hydra|john|hashcat|msfconsole|"
    r"msfvenom|responder|crackmapexec|mimikatz|chisel)\b", re.I
)

logger = logging.getLogger("soc-agent")


# ---------------------------------------------------------------------------
# Helpers
# ---------------------------------------------------------------------------
def make_event(event_type: str, **fields) -> dict:
    """Build a SystemEvent-compatible dict."""
    evt = {
        "id": str(uuid.uuid4()),
        "type": event_type,
        "host": socket.gethostname(),
        "timestamp": datetime.now(timezone.utc).isoformat(),
    }
    evt.update({k: v for k, v in fields.items() if v is not None})
    return evt


def send_event(backend_url: str, event: dict, session: requests.Session):
    """POST a single event to the backend."""
    url = f"{backend_url}/api/events"
    try:
        resp = session.post(url, json=event, timeout=5)
        if resp.status_code < 300:
            logger.debug("Event sent: %s (%s)", event["type"], event["id"])
        else:
            logger.warning("Backend returned %s for event %s", resp.status_code, event["id"])
    except requests.RequestException as exc:
        logger.error("Failed to send event: %s", exc)


def send_events(backend_url: str, events: list, session: requests.Session):
    """Send a batch of events."""
    for evt in events:
        send_event(backend_url, evt, session)


# ---------------------------------------------------------------------------
# Collectors
# ---------------------------------------------------------------------------
class ProcessCollector:
    """Tracks process creation and termination."""

    def __init__(self):
        self.known_pids: dict[int, dict] = {}  # pid -> {name, cmdline, ppid, user, create_time}
        self._snapshot()

    def _snapshot(self):
        """Take initial snapshot without emitting events."""
        for proc in psutil.process_iter(["pid", "name", "ppid", "username", "cmdline", "create_time"]):
            try:
                info = proc.info
                self.known_pids[info["pid"]] = {
                    "name": info["name"] or "",
                    "cmdline": " ".join(info["cmdline"] or []),
                    "ppid": info["ppid"],
                    "user": info["username"] or "",
                    "create_time": info["create_time"],
                }
            except (psutil.NoSuchProcess, psutil.AccessDenied):
                continue

    def collect(self) -> list[dict]:
        events = []
        current_pids: dict[int, dict] = {}

        for proc in psutil.process_iter(["pid", "name", "ppid", "username", "cmdline", "create_time"]):
            try:
                info = proc.info
                pid = info["pid"]
                current_pids[pid] = {
                    "name": info["name"] or "",
                    "cmdline": " ".join(info["cmdline"] or []),
                    "ppid": info["ppid"],
                    "user": info["username"] or "",
                    "create_time": info["create_time"],
                }

                # New process?
                if pid not in self.known_pids:
                    cmdline = current_pids[pid]["cmdline"]
                    confidence = 0.9 if SUSPICIOUS_PROCS.search(cmdline) else 0.3
                    events.append(make_event(
                        "PROCESS_SPAWNED",
                        pid=pid,
                        processName=info["name"],
                        ppid=info["ppid"],
                        user=info["username"],
                        cmdline=cmdline,
                        confidence=confidence,
                    ))
            except (psutil.NoSuchProcess, psutil.AccessDenied):
                continue

        # Terminated processes
        for pid, pinfo in self.known_pids.items():
            if pid not in current_pids:
                events.append(make_event(
                    "PROCESS_TERMINATED",
                    pid=pid,
                    processName=pinfo["name"],
                    user=pinfo["user"],
                    cmdline=pinfo["cmdline"],
                ))

        self.known_pids = current_pids
        return events


class NetworkCollector:
    """Tracks new network connections (ESTABLISHED + LISTEN)."""

    def __init__(self):
        self.known_conns: set[tuple] = set()
        self._snapshot()

    def _conn_key(self, conn) -> tuple:
        laddr = conn.laddr if conn.laddr else ("", 0)
        raddr = conn.raddr if conn.raddr else ("", 0)
        return (conn.pid or 0, laddr[0], laddr[1], raddr[0], raddr[1], conn.status)

    def _snapshot(self):
        try:
            for conn in psutil.net_connections(kind="inet"):
                if conn.status in ("ESTABLISHED", "LISTEN"):
                    self.known_conns.add(self._conn_key(conn))
        except psutil.AccessDenied:
            pass

    def collect(self) -> list[dict]:
        events = []
        current_conns: set[tuple] = set()

        try:
            for conn in psutil.net_connections(kind="inet"):
                if conn.status not in ("ESTABLISHED", "LISTEN"):
                    continue
                key = self._conn_key(conn)
                current_conns.add(key)

                if key not in self.known_conns:
                    laddr = conn.laddr if conn.laddr else ("", 0)
                    raddr = conn.raddr if conn.raddr else ("", 0)
                    events.append(make_event(
                        "NETWORK_CONNECTION",
                        pid=conn.pid,
                        localIp=laddr[0] if laddr else None,
                        localPort=laddr[1] if laddr else None,
                        remoteIp=raddr[0] if raddr else None,
                        remotePort=raddr[1] if raddr else None,
                        connectionState=conn.status,
                        protocol="tcp",
                    ))
        except psutil.AccessDenied:
            pass

        self.known_conns = current_conns
        return events


class FileCollector:
    """Watches directories for new or modified files."""

    def __init__(self, watch_dirs: list[str]):
        self.watch_dirs = [d for d in watch_dirs if os.path.isdir(d)]
        self.known_files: dict[str, float] = {}  # path -> mtime
        self._snapshot()

    def _snapshot(self):
        for d in self.watch_dirs:
            try:
                for entry in os.scandir(d):
                    if entry.is_file(follow_symlinks=False):
                        self.known_files[entry.path] = entry.stat().st_mtime
            except PermissionError:
                continue

    def collect(self) -> list[dict]:
        events = []
        current_files: dict[str, float] = {}

        for d in self.watch_dirs:
            try:
                for entry in os.scandir(d):
                    if not entry.is_file(follow_symlinks=False):
                        continue
                    path = entry.path
                    mtime = entry.stat().st_mtime
                    current_files[path] = mtime

                    if path not in self.known_files:
                        events.append(make_event(
                            "FILE_CREATED",
                            filePath=path,
                            fileAction="created",
                        ))
                    elif mtime != self.known_files.get(path):
                        events.append(make_event(
                            "FILE_MODIFIED",
                            filePath=path,
                            fileAction="modified",
                        ))
            except PermissionError:
                continue

        self.known_files = current_files
        return events


class CronCollector:
    """Watches crontab entries for additions/removals."""

    def __init__(self):
        self.known_entries: set[str] = set()
        self._snapshot()

    def _get_cron_entries(self) -> set[str]:
        entries = set()
        # Check spool directory
        if os.path.isdir(CRON_SPOOL):
            try:
                for fname in os.listdir(CRON_SPOOL):
                    filepath = os.path.join(CRON_SPOOL, fname)
                    with open(filepath, "r") as f:
                        for line in f:
                            line = line.strip()
                            if line and not line.startswith("#"):
                                entries.add(f"{fname}:{line}")
            except PermissionError:
                pass

        # Also check current user's crontab
        try:
            result = subprocess.run(
                ["crontab", "-l"],
                capture_output=True, text=True, timeout=5
            )
            if result.returncode == 0:
                for line in result.stdout.splitlines():
                    line = line.strip()
                    if line and not line.startswith("#"):
                        entries.add(f"current:{line}")
        except (subprocess.TimeoutExpired, FileNotFoundError):
            pass

        return entries

    def _snapshot(self):
        self.known_entries = self._get_cron_entries()

    def collect(self) -> list[dict]:
        events = []
        current = self._get_cron_entries()

        for entry in current - self.known_entries:
            events.append(make_event(
                "CRON_ADDED",
                cronEntry=entry.split(":", 1)[1] if ":" in entry else entry,
            ))

        for entry in self.known_entries - current:
            events.append(make_event(
                "CRON_REMOVED",
                cronEntry=entry.split(":", 1)[1] if ":" in entry else entry,
            ))

        self.known_entries = current
        return events


# ---------------------------------------------------------------------------
# Main loop
# ---------------------------------------------------------------------------
def main():
    parser = argparse.ArgumentParser(description="SOC Lab Monitoring Agent")
    parser.add_argument("--backend", required=True,
                        help="Backend URL, e.g. http://192.168.56.1:8080")
    parser.add_argument("--interval", type=float, default=2.0,
                        help="Polling interval in seconds (default: 2)")
    parser.add_argument("--watch-dirs", nargs="*", default=WATCH_DIRS,
                        help="Directories to watch for file changes")
    parser.add_argument("--verbose", "-v", action="store_true",
                        help="Enable debug logging")
    args = parser.parse_args()

    logging.basicConfig(level=logging.DEBUG if args.verbose else logging.INFO, format=LOG_FORMAT)
    logger.info("Starting SOC monitoring agent")
    logger.info("Backend: %s", args.backend)
    logger.info("Poll interval: %.1fs", args.interval)
    logger.info("Watch dirs: %s", args.watch_dirs)
    logger.info("Hostname: %s", socket.gethostname())

    # Initialize collectors
    proc_collector = ProcessCollector()
    net_collector = NetworkCollector()
    file_collector = FileCollector(args.watch_dirs)
    cron_collector = CronCollector()

    session = requests.Session()
    session.headers.update({"Content-Type": "application/json"})

    # Verify backend connectivity
    try:
        resp = session.get(f"{args.backend}/api/health", timeout=5)
        logger.info("Backend health check: %s", resp.status_code)
    except requests.RequestException as exc:
        logger.warning("Backend not reachable (will keep retrying): %s", exc)

    logger.info("Agent running — press Ctrl+C to stop")
    try:
        while True:
            events = []
            events.extend(proc_collector.collect())
            events.extend(net_collector.collect())
            events.extend(file_collector.collect())
            events.extend(cron_collector.collect())

            if events:
                logger.info("Collected %d events", len(events))
                send_events(args.backend, events, session)

            time.sleep(args.interval)
    except KeyboardInterrupt:
        logger.info("Agent stopped")


if __name__ == "__main__":
    main()
