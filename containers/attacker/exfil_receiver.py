#!/usr/bin/env python3
"""Simple HTTP server to receive exfiltrated data from attack vectors."""
import http.server
import os
import sys
from datetime import datetime

EXFIL_DIR = "/tmp/exfil_data"
PORT = 8888

os.makedirs(EXFIL_DIR, exist_ok=True)

class ExfilHandler(http.server.BaseHTTPRequestHandler):
    def do_POST(self):
        content_length = int(self.headers.get('Content-Length', 0))
        if content_length > 10 * 1024 * 1024:  # 10MB limit
            self.send_response(413)
            self.end_headers()
            return

        body = self.rfile.read(content_length)
        ts = datetime.now().strftime("%Y%m%d_%H%M%S")
        filename = os.path.join(EXFIL_DIR, f"exfil_{ts}.dat")
        with open(filename, "wb") as f:
            f.write(body)
        print(f"[EXFIL] Received {len(body)} bytes → {filename}")
        self.send_response(200)
        self.send_header("Content-Type", "application/json")
        self.end_headers()
        self.wfile.write(b'{"status":"received"}')

    def do_GET(self):
        files = os.listdir(EXFIL_DIR)
        self.send_response(200)
        self.send_header("Content-Type", "text/plain")
        self.end_headers()
        self.wfile.write(f"Exfil receiver — {len(files)} files captured\n".encode())

    def log_message(self, format, *args):
        print(f"[EXFIL-HTTP] {args[0]}")

if __name__ == "__main__":
    server = http.server.HTTPServer(("0.0.0.0", PORT), ExfilHandler)
    print(f"[EXFIL] Listening on 0.0.0.0:{PORT}")
    server.serve_forever()
