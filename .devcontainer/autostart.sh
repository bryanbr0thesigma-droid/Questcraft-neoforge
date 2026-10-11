#!/usr/bin/env bash
# Runs when the Codespace starts and again whenever you open it: starts Unity Hub on the desktop (display :1) and the
# typing page (port 8000), detached so they outlive this command. Nothing has to be typed into a terminal.
here="$(cd "$(dirname "$0")" && pwd)"
log="$HOME/autostart.log"
{
  echo "== $(date) autostart"
  export DISPLAY=:1
  command -v xdotool >/dev/null || { sudo apt-get update -qq; sudo apt-get install -y -qq xdotool; }
  [ -x "$HOME/start-hub.sh" ] || bash "$here/setup-unity-hub.sh"
  pgrep -f "$here/typer.py" >/dev/null || \
    setsid nohup python3 "$here/typer.py" < /dev/null > "$HOME/typer.log" 2>&1 &
  for _ in $(seq 1 60); do [ -S /tmp/.X11-unix/X1 ] && break; sleep 2; done
  sleep 3
  pgrep -f /opt/unityhub >/dev/null || \
    setsid nohup "$HOME/start-hub.sh" < /dev/null > "$HOME/hub.log" 2>&1 &
  echo "started"
} >> "$log" 2>&1
