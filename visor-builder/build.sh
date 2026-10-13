#!/usr/bin/env bash
# Builds QuestCraft's Visor, the VR mod for Minecraft Java that runs on the headset (OpenGL ES, Android): Visor's sources
# (tag 0.6.0-beta) with its OpenXR library AtumVR patched for Android, the way QuestCraft patches Vivecraft.
#
#   ./build.sh <minecraft version> <fabric|forge|neoforge|all>      e.g. ./build.sh 1.21.1 neoforge
#   ./build.sh all all                                                every version QuestCraft supports
#
# The jars land in pojlib/visor-jars/<loader>/ (Quilt runs the Fabric one), which is where visor-mods.json points.
# Needs JDK 21 and an internet connection (Gradle fetches Minecraft and the mappings). Everything it needs is overridable:
#   GRADLE_USER_HOME   where Gradle keeps its files (each Minecraft version takes about 1.5 GB in there)
#   KEEP_CACHES=1      do not clean the per-version caches between versions
set -euo pipefail

here="$(cd "$(dirname "$0")" && pwd)"
repo="$(cd "$here/.." && pwd)"
work="$here/work"
VISOR_REPO="https://github.com/VisorModStudio/Visor.git"
VISOR_TAG="0.6.0-beta"
ATUM_REPO="https://github.com/Phoenix-Ra/AtumVR.git"
ATUM_COMMIT="4b18239"          # the AtumVR this Visor was built with is a little newer than any tag; this is its newest public commit
VERSIONS="1.20.4 1.20.6 1.21.1 1.21.4 1.21.5 1.21.8 1.21.11"
export QC_OPENXR_JAR="$here/lib/questcraft-lwjgl-openxr-3.3.3.jar"   # QuestCraft's LWJGL OpenXR (it has the Android parts)

mc="${1:?minecraft version or all}"
loaders="${2:?fabric, forge, neoforge or all}"
[ "$mc" = all ] && mc="$VERSIONS"
[ "$loaders" = all ] && loaders="fabric forge neoforge"

mkdir -p "$work"

# 1. sources, patched
if [ ! -d "$work/AtumVR" ]; then
  git clone --quiet "$ATUM_REPO" "$work/AtumVR"
  git -C "$work/AtumVR" checkout --quiet "$ATUM_COMMIT"
  git -C "$work/AtumVR" apply "$here/patches/atumvr-questcraft-android.patch"
  # this sandbox's Gradle only knows Maven Central; the plugin portal has to be named
  grep -q pluginManagement "$work/AtumVR/settings.gradle" || sed -i '1i pluginManagement { repositories { gradlePluginPortal(); mavenCentral() } }' "$work/AtumVR/settings.gradle"
  chmod +x "$work/AtumVR/gradlew"
fi
if [ ! -d "$work/Visor" ]; then
  git clone --quiet --branch "$VISOR_TAG" --depth 1 "$VISOR_REPO" "$work/Visor"
  git -C "$work/Visor" apply "$here/patches/visor-questcraft-quest.patch"
  chmod +x "$work/Visor/gradlew"
fi

# 2. AtumVR for the headset, in place of the jars Visor ships in libs/ (same file names, that is what its build looks for)
if [ ! -f "$work/atum-built" ]; then
  (cd "$work/AtumVR" && ./gradlew -I "$here/gradle-init.gradle" :atumvr-api:build :atumvr-core:build -x test -q)
  api="$(ls "$work"/AtumVR/atumvr-api/build/libs/atumvr-api-*[0-9].jar | grep -v -- '-all\|javadoc\|sources' | head -1)"
  core="$(ls "$work"/AtumVR/atumvr-core/build/libs/atumvr-core-*[0-9].jar | grep -v -- '-all\|javadoc\|sources' | head -1)"
  grep -h atumvr_version "$work/Visor/stonecutter.properties.toml" | sed 's/.*= *"\(.*\)".*/\1/' > "$work/atum-version"
  cp "$api"  "$work/Visor/libs/atumvr-api-$(cat "$work/atum-version").jar"
  cp "$core" "$work/Visor/libs/atumvr-core-$(cat "$work/atum-version").jar"
  touch "$work/atum-built"
fi

# 3. Visor, one Minecraft version and loader at a time
for v in $mc; do
  for l in $loaders; do
    out="$repo/pojlib/visor-jars/$l"
    mkdir -p "$out"
    (cd "$work/Visor" && ./gradlew -I "$here/gradle-init.gradle" --configure-on-demand ":visor-$l:$v:build" -x test -q)
    jar="$(ls "$work"/Visor/visor-"$l"/versions/"$v"/build/libs/Visor-*+mc"$v"-"$l".jar | head -1)"
    cp "$jar" "$out/visor-$l-$v.jar"
    echo "built $out/visor-$l-$v.jar ($(du -h "$out/visor-$l-$v.jar" | cut -f1))"
  done
  if [ -z "${KEEP_CACHES:-}" ]; then
    # Minecraft's files of this version are not needed again, and a disk fills quickly
    rm -rf "$work"/Visor/visor-*/versions/"$v"/build "$work"/Visor/visor-*/versions/"$v"/.gradle 2>/dev/null || true
    find "${GRADLE_USER_HOME:-$HOME/.gradle}/caches/fabric-loom" -maxdepth 1 -type d -name "*$v*" -exec rm -rf {} + 2>/dev/null || true
  fi
done
