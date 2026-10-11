#!/usr/bin/env python3
"""A web page that types into the Codespace desktop (display :1) with xdotool, for headsets whose browser cannot
type into noVNC. Click a field on the desktop, type the text here, press a button. Port 8000 (private to you)."""
import html
import os
import subprocess
import urllib.parse
from http.server import BaseHTTPRequestHandler, HTTPServer

ENV = dict(os.environ, DISPLAY=os.environ.get("DISPLAY", ":1"))
PAGE = """<!doctype html><meta name=viewport content="width=device-width,initial-scale=1"><title>Type</title>
<style>body{font:26px sans-serif;margin:20px;max-width:900px}input[type=text],input[type=password]{width:100%;font-size:30px;padding:14px;box-sizing:border-box}
button{font-size:28px;padding:16px 24px;margin:8px 8px 8px 0}.s{color:#070}</style>
<h2>Type into the desktop</h2>
<p>1. Tap the field on the desktop tab. 2. Type here. 3. Tap a button.</p>
<form method=post>
<input type=text name=text autofocus autocomplete=off autocapitalize=off placeholder="text (email, code...)"><br>
<button name=do value=type>Type</button><button name=do value=tab>Type + Tab</button><button name=do value=enter>Type + Enter</button><br>
<button name=do value=k_Tab>Tab</button><button name=do value=k_Return>Enter</button><button name=do value=k_BackSpace>Backspace</button>
</form>
<h3>Password</h3>
<form method=post><input type=password name=text autocomplete=off placeholder="password">
<br><button name=do value=enter>Type + Enter</button><button name=do value=type>Type</button></form>
<p class=s>@@MSG@@</p>"""


def xdo(*args):
    return subprocess.run(["xdotool", *args], env=ENV, capture_output=True, text=True, timeout=30)


class Handler(BaseHTTPRequestHandler):
    def _send(self, message=""):
        body = PAGE.replace("@@MSG@@", html.escape(message)).encode()
        self.send_response(200)
        self.send_header("Content-Type", "text/html; charset=utf-8")
        self.send_header("Content-Length", str(len(body)))
        self.end_headers()
        self.wfile.write(body)

    def do_GET(self):
        self._send()

    def do_POST(self):
        form = urllib.parse.parse_qs(self.rfile.read(int(self.headers.get("Content-Length", 0))).decode())
        text, action = form.get("text", [""])[0], form.get("do", ["type"])[0]
        try:
            if action.startswith("k_"):
                result = xdo("key", action[2:])
            else:
                result = xdo("type", "--delay", "40", "--clearmodifiers", "--", text) if text else None
                if action in ("tab", "enter"):
                    result = xdo("key", "Tab" if action == "tab" else "Return")
            ok = result is None or result.returncode == 0
            self._send("Sent." if ok else "Failed: " + result.stderr.strip())
        except Exception as e:
            self._send("Failed: %s" % e)

    def log_message(self, *args):
        pass  # never log what was typed


if __name__ == "__main__":
    HTTPServer(("127.0.0.1", 8000), Handler).serve_forever()
