#!/bin/bash
# Starts every client with ModLoader in it (work/merged, from RisugamiModLoaderTest; libraries from fetch_clients.py)
# the way the headset does: through the legacy launcher, on a desktop Java with headless AWT, LWJGL 2 standing in for
# the headset's LWJGL, on its own X display. A version passes when ModLoader's class is in the game, nothing fatal is
# in the log and the screenshot after $SECONDS_TO_RUN is not blank. Results in work/test/results.txt, screenshots next to it.
#
# Needs: Xvfb, ImageMagick (import, convert), JAVA (21+), LWJGL2 (folder with lwjgl, lwjgl_util, jinput, jutils jars)
# and LWJGL2_NATIVES. Usage: test_versions.sh [version...]
set -u
cd "$(dirname "$0")"
REPO=$(cd .. && pwd)
JAVA=${JAVA:-/usr/lib/jvm/java-21-openjdk-amd64/bin/java}
SECONDS_TO_RUN=${SECONDS_TO_RUN:-25}
LAUNCHER=$REPO/pojlib/src/main/assets/legacy/questcraft-legacy-launcher.jar
SYS=$LAUNCHER$(for j in "$LWJGL2"/*.jar; do printf ":%s" "$j"; done)
OUT=work/test
mkdir -p "$OUT"
VERSIONS=("$@")
[ ${#VERSIONS[@]} -eq 0 ] && VERSIONS=($(python3 -c "import json; print(' '.join(v['name'] for v in json.load(open('$REPO/pojlib/modloader-builds.json'))['versions']))"))
for v in "${VERSIONS[@]}"; do
  dir=$PWD/$OUT/$v
  rm -rf "$dir"; mkdir -p "$dir"
  cp="$PWD/work/merged/$v.jar"
  libs=$(cat "work/clients/$v.classpath")
  [ -n "$libs" ] && cp="$cp:$libs"
  main=$(python3 -c "import json; m=json.load(open('work/clients/$v.json'))['mainClass']; print(m if m == 'net.minecraft.client.main.Main' else 'net.minecraft.client.Minecraft')")
  Xvfb :79 -screen 0 1280x720x24 >/dev/null 2>&1 &
  xvfb=$!
  sleep 2
  DISPLAY=:79 "$JAVA" -Xmx1G -Djava.awt.headless=true \
    --add-opens java.base/java.lang=ALL-UNNAMED --add-opens java.base/java.lang.reflect=ALL-UNNAMED \
    --add-opens java.base/java.net=ALL-UNNAMED --add-opens java.base/java.nio=ALL-UNNAMED --add-opens java.base/java.util=ALL-UNNAMED \
    -Djava.library.path="$LWJGL2_NATIVES" -Dquestcraft.legacy.classpath="$cp" -Dquestcraft.legacy.main="$main" \
    -cp "$SYS" questcraft.legacy.LegacyLauncher --username QuestCraftTest --version "$v" --gameDir "$dir" --assetsDir "$dir/assets" \
    > "$OUT/$v.log" 2>&1 &
  game=$!
  sleep "$SECONDS_TO_RUN"
  DISPLAY=:79 import -window root "$OUT/$v.png" 2>/dev/null
  kill $game 2>/dev/null; sleep 1; kill -9 $game 2>/dev/null; kill $xvfb 2>/dev/null; wait $xvfb 2>/dev/null
  logs="$OUT/$v.log"; [ -f "$dir/ModLoader.txt" ] && logs="$logs $dir/ModLoader.txt"
  # ModLoader's class was loaded (the launcher says so), and whether it logged that it initialized (alpha builds log nothing)
  loaded=$(grep -c "ModLoader is in the game" "$OUT/$v.log")
  initialized=$(cat $logs | grep -c -i "Initializ")
  fatal=$(grep -c -E "Exception in thread \"(main|Minecraft main thread|Thread-[0-9]+)\"|StackOverflowError|NoClassDefFoundError|NoSuchFieldException|NoSuchMethodError|VerifyError|UnsupportedOperationException|IllegalAccessError|InaccessibleObjectException" "$OUT/$v.log")
  brightness=$(convert "$OUT/$v.png" -colorspace Gray -format "%[fx:mean]" info: 2>/dev/null || echo 0)
  if [ "$loaded" -gt 0 ] && [ "$fatal" -eq 0 ] && python3 -c "import sys; sys.exit(0 if float('$brightness') > 0.02 else 1)"; then r=PASS; else r=FAIL; fi
  printf '%-10s %s  loaded=%s initialized=%s fatal=%s brightness=%s\n' "$v" "$r" "$loaded" "$initialized" "$fatal" "$brightness" | tee -a "$OUT/results.txt"
done
