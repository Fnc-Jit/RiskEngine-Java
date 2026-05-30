"""
Dashboard server for ScenarioForge.

Serves the RiskEngine dashboard HTML on a local port and proxies API calls
to the Java backend (default http://localhost:8080). Serving the dashboard
and the API from the same origin avoids the browser CORS block that occurs
when the dashboard is opened as a file:// page.

Also exposes /scenario-status so the dashboard can show a live banner:
    { "state": "RUNNING" | "COMPLETE" | "IDLE", "scenario": "...",
      "eventsSent": N, "targetEps": E, ... }

The server runs in a background thread so generation can proceed in the
main thread.
"""
from __future__ import annotations
import json
import threading
import urllib.request
import urllib.error
from http.server import BaseHTTPRequestHandler, ThreadingHTTPServer
from pathlib import Path
from typing import Any, Dict


# Shared, mutable status object updated by the generator
SCENARIO_STATUS: Dict[str, Any] = {
    "state": "IDLE",          # IDLE | RUNNING | COMPLETE
    "scenario": None,
    "eventsSent": 0,
    "eventsTarget": None,
    "targetEps": None,
    "durationSec": None,
    "startedAt": None,
    "finishedAt": None,
    "actualEps": None,
}

# Path to the dashboard HTML (repo root / riskengine-dashboard.html)
_DASHBOARD_HTML = Path(__file__).resolve().parent.parent / "riskengine-dashboard.html"


class _Handler(BaseHTTPRequestHandler):
    backend = "http://localhost:8080"

    # Silence default request logging
    def log_message(self, fmt, *args):
        pass

    def _send_json(self, obj, status=200):
        body = json.dumps(obj).encode("utf-8")
        self.send_response(status)
        self.send_header("Content-Type", "application/json")
        self.send_header("Content-Length", str(len(body)))
        self.send_header("Access-Control-Allow-Origin", "*")
        self.end_headers()
        self.wfile.write(body)

    def do_GET(self):
        path = self.path.split("?")[0]

        # Dashboard page
        if path in ("/", "/index.html", "/dashboard"):
            return self._serve_dashboard()

        # Scenario status (served locally, not proxied)
        if path == "/scenario-status":
            return self._send_json(SCENARIO_STATUS)

        # Everything else proxies to the Java backend
        return self._proxy(path)

    def _serve_dashboard(self):
        try:
            html = _DASHBOARD_HTML.read_bytes()
        except FileNotFoundError:
            self.send_error(404, "dashboard html not found")
            return
        self.send_response(200)
        self.send_header("Content-Type", "text/html; charset=utf-8")
        self.send_header("Content-Length", str(len(html)))
        self.end_headers()
        self.wfile.write(html)

    def _proxy(self, path):
        url = self.backend + self.path
        try:
            with urllib.request.urlopen(url, timeout=10) as resp:
                data = resp.read()
                self.send_response(resp.status)
                ctype = resp.headers.get("Content-Type", "application/json")
                self.send_header("Content-Type", ctype)
                self.send_header("Content-Length", str(len(data)))
                self.send_header("Access-Control-Allow-Origin", "*")
                self.end_headers()
                self.wfile.write(data)
        except urllib.error.HTTPError as e:
            body = e.read()
            self.send_response(e.code)
            self.send_header("Content-Type", "application/json")
            self.send_header("Content-Length", str(len(body)))
            self.send_header("Access-Control-Allow-Origin", "*")
            self.end_headers()
            self.wfile.write(body)
        except Exception:
            # Backend not reachable — return a soft error the dashboard can handle
            self._send_json({"error": "backend unreachable", "backend": self.backend}, status=503)


def start_dashboard_server(port: int = 8050, backend: str = "http://localhost:8080") -> str:
    """
    Start the dashboard server in a background daemon thread.
    Returns the dashboard URL.
    """
    _Handler.backend = backend
    httpd = ThreadingHTTPServer(("127.0.0.1", port), _Handler)
    thread = threading.Thread(target=httpd.serve_forever, daemon=True)
    thread.start()
    return f"http://localhost:{port}/"


# ─── Status helpers used by the generator ───────────────────────────────────
def mark_running(scenario, target_eps, duration_sec, events_target):
    import time
    SCENARIO_STATUS.update({
        "state": "RUNNING",
        "scenario": scenario,
        "eventsSent": 0,
        "eventsTarget": events_target,
        "targetEps": target_eps,
        "durationSec": duration_sec,
        "startedAt": time.time(),
        "finishedAt": None,
        "actualEps": None,
    })


def update_progress(events_sent):
    SCENARIO_STATUS["eventsSent"] = events_sent


def mark_complete(events_sent, actual_eps):
    import time
    SCENARIO_STATUS.update({
        "state": "COMPLETE",
        "eventsSent": events_sent,
        "finishedAt": time.time(),
        "actualEps": actual_eps,
    })
