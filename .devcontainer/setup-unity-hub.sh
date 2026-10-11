#!/usr/bin/env bash
# Installs Unity Hub and Firefox in the Codespace so a Personal license can be activated from the browser desktop
# (port 6080). It exists only because the Unity license cannot be made without Unity Hub, see README "Getting the APK".
set -euo pipefail

sudo apt-get update
sudo apt-get install -y --no-install-recommends wget gnupg ca-certificates firefox-esr xdg-utils desktop-file-utils

# Unity Hub's own apt repository
wget -qO - https://hub.unity3d.com/linux/keys/public | gpg --dearmor | sudo tee /usr/share/keyrings/Unity_Technologies_ApS.gpg >/dev/null
echo "deb [signed-by=/usr/share/keyrings/Unity_Technologies_ApS.gpg] https://hub.unity3d.com/linux/repos/deb stable main" \
  | sudo tee /etc/apt/sources.list.d/unityhub.list >/dev/null
sudo apt-get update
sudo apt-get install -y unityhub

# Containers have no user namespaces for Electron's sandbox; the sign-in redirect (unityhub://) also starts Hub
HUB_DESKTOP=/usr/share/applications/unityhub.desktop
sudo sed -i 's|^Exec=\(/opt/unityhub/unityhub\|unityhub\)|Exec=\1 --no-sandbox|' "$HUB_DESKTOP" || true
sudo update-desktop-database || true
xdg-settings set default-web-browser firefox-esr.desktop 2>/dev/null || true
sudo xdg-mime default unityhub.desktop x-scheme-handler/unityhub || true
xdg-mime default unityhub.desktop x-scheme-handler/unityhub || true

cat > "$HOME/start-hub.sh" <<'EOS'
#!/usr/bin/env bash
# Run this inside the desktop (port 6080): opens Unity Hub
export DISPLAY="${DISPLAY:-:1}"
exec unityhub --no-sandbox --disable-gpu
EOS
cat > "$HOME/show-license.sh" <<'EOS'
#!/usr/bin/env bash
# Prints the Unity license file once Hub has activated a license; copy everything it prints into the UNITY_LICENSE secret
f=$(find "$HOME/.local/share/unity3d" "$HOME/.config/unity3d" -name '*.ulf' 2>/dev/null | head -1)
if [ -z "$f" ]; then
  echo "No .ulf found yet. Activate a license in Unity Hub first (Licenses -> Add -> Get a free personal license)."
  echo "Files Hub wrote:"; find "$HOME/.local/share/unity3d" "$HOME/.config/unity3d" -type f 2>/dev/null
  exit 1
fi
echo "== $f =="; cat "$f"
EOS
chmod +x "$HOME/start-hub.sh" "$HOME/show-license.sh"
echo "Done. Open port 6080 (password: unity), then run ~/start-hub.sh in the desktop's terminal."
