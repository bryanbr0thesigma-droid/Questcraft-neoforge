#!/usr/bin/env bash
# Builds QuestCraft's Vivecraft for one Minecraft version.
#
#   ./build.sh 1.21.1             ->  dist/vivecraft-neoforge-1.21.1.jar
#   ./build.sh 1.21.11 fabric     ->  dist/vivecraft-fabric-1.21.11.jar
#   ./build.sh 1.21.11 both       ->  both of them
#
# versions.json says where each version comes from:
#  - "<branch>"                       a branch of the QuestCraft fork (QuestCraftPlusPlus/VivecraftMod). The fork is a
#                                     multiloader project with the QuestCraft OpenXR changes in its shared module,
#                                     but only the Fabric jar is published.
#  - {"repo": ..., "branch": ...}     another repository, for versions the fork has no source for. patches/<mc>/*.patch
#                                     are applied on top, for 1.21.11 that is QuestCraft's change set on upstream Vivecraft.
set -euo pipefail

mc="${1:?usage: $0 <minecraft version, one of the keys of versions.json> [neoforge|fabric|both]}"
loaders="${2:-neoforge}"
here="$(cd "$(dirname "$0")" && pwd)"
work="$here/work/$mc"

read -r repo branch < <(python3 - "$here/versions.json" "$mc" <<'PY'
import json, sys
entry = json.load(open(sys.argv[1]))[sys.argv[2]]
if isinstance(entry, str):
    entry = {"branch": entry}
print(entry.get("repo", "https://github.com/QuestCraftPlusPlus/VivecraftMod.git"), entry["branch"])
PY
)

rm -rf "$work"
git clone --depth 1 --branch "$branch" "$repo" "$work"

for patch in "$here"/patches/"$mc"/*.patch; do
  [ -e "$patch" ] || continue
  echo "Applying $(basename "$patch")"
  git -C "$work" apply --index "$patch"
done

tasks=()
case "$loaders" in
  neoforge|both) tasks+=(":neoforge:build") ;;&
  fabric|both)   tasks+=(":fabric:build") ;;&
  neoforge|fabric|both) ;;
  *) echo "error: unknown loader '$loaders'" >&2; exit 1 ;;
esac

# Some branches (1.21.5 and newer) have a neoforge module but switch it off: it is neither in the enabled platforms
# of the shared module nor in settings.gradle. Turn it on.
if [ "$loaders" != "fabric" ]; then
  if ! grep -q '^enabled_platforms=.*neoforge' "$work/gradle.properties"; then
    sed -i -E 's/^(enabled_platforms=.*)$/\1,neoforge/' "$work/gradle.properties"
  fi
  if ! grep -q 'neoforge' "$work/settings.gradle"; then
    printf '\ninclude("neoforge")\n' >> "$work/settings.gradle"
  fi
  # Pojlib's LWJGL jar already provides org.lwjgl.openvr (with its JNI natives, which LWJGL binds to the class
  # loader that loaded the classes). On Fabric everything shares one class loader so a second copy was harmless;
  # NeoForge loads mods in a loader of their own, where a second copy could not use those natives. Don't bundle it.
  # org.lwjgl.openxr is QuestCraft's own build and only exists in this jar, it stays.
  sed -i '/^[[:space:]]*bundle("org.lwjgl:lwjgl-openvr/d' "$work/neoforge/build.gradle"
fi

# newer upstream settings can leave Forge out of the build, it is not needed here and slows it down
if grep -q 'project.build.excludeForge' "$work/settings.gradle"; then
  printf 'project.build.excludeForge=true\n' >> "$work/local.properties"
fi

(cd "$work" && chmod +x gradlew && ./gradlew --no-daemon "${tasks[@]}" -x test)

mkdir -p "$here/dist"
for loader in neoforge fabric; do
  [ "$loaders" = "both" ] || [ "$loaders" = "$loader" ] || continue

  # <name>-<loader>.jar is what the remap step writes, the other jars in there are dev/shadow intermediates
  jar="$(ls "$work/$loader"/build/libs/*-"$loader".jar | grep -v -e '-dev' -e '-sources' | head -n 1)"

  if [ "$loader" = "neoforge" ]; then
    # make sure the tweak above did what it is for. The listing goes to a file first: with pipefail, grep -q ending
    # a pipe early would make the pipeline "fail" even though it found what it was looking for.
    listing="$(mktemp)"
    unzip -Z1 "$jar" > "$listing"
    if grep -q '^org/lwjgl/openvr/' "$listing"; then
      echo "error: $jar still contains org/lwjgl/openvr" >&2; rm -f "$listing"; exit 1
    fi
    if ! grep -q '^org/lwjgl/openxr/' "$listing"; then
      echo "error: $jar lost org/lwjgl/openxr" >&2; rm -f "$listing"; exit 1
    fi
    rm -f "$listing"
  fi

  cp "$jar" "$here/dist/vivecraft-$loader-$mc.jar"
  echo "Built $here/dist/vivecraft-$loader-$mc.jar"
done
