#!/usr/bin/env bash
# Runs on every start of the Codespace: starts Unity Hub on the desktop (display :1) and the typing page (port 8000),
# so nothing has to be typed into a terminal.
export DISPLAY=:1
here="$(cd "$(dirname "$0")" && pwd)"
# the desktop starts with the container; wait for it
for _ in $(seq 1 60); do [ -S /tmp/.X11-unix/X1 ] && break; sleep 2; done
sleep 3
pgrep -x unityhub >/dev/null || pgrep -f /opt/unityhub >/dev/null || \
  nohup "$HOME/start-hub.sh" > "$HOME/hub.log" 2>&1 &
pgrep -f typer.py >/dev/null || nohup python3 "$here/typer.py" > "$HOME/typer.log" 2>&1 &
disown -a 2>/dev/null || true
