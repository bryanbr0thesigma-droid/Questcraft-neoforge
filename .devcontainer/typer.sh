#!/usr/bin/env bash
# Starts the typing page (see typer.py). Then open port 8000 from the Ports tab.
set -e
command -v xdotool >/dev/null || { sudo apt-get update -qq && sudo apt-get install -y -qq xdotool; }
echo "Open port 8000 in the Ports tab (add it if it is not listed). Leave this running."
exec python3 "$(dirname "$0")/typer.py"
