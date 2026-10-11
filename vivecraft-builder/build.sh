#!/usr/bin/env bash
# Builds the NeoForge jar of QuestCraft's Vivecraft fork for one Minecraft version.
#
#   ./build.sh 1.21.1      ->  dist/vivecraft-neoforge-1.21.1.jar
#
# The fork (QuestCraftPlusPlus/VivecraftMod) is a multiloader project with the QuestCraft OpenXR changes in
# its shared "common" module, but only publishes the Fabric jar. patches/<mc>/*.patch are applied on top
# of the branch listed in versions.json before building.
set -euo pipefail

mc="${1:?usage: $0 <minecraft version, one of the keys of versions.json>}"
here="$(cd "$(dirname "$0")" && pwd)"
branch="$(python3 -c 'import json,sys; print(json.load(open(sys.argv[1]))[sys.argv[2]])' "$here/versions.json" "$mc")"
work="$here/work/$mc"

rm -rf "$work"
git clone --depth 1 --branch "$branch" https://github.com/QuestCraftPlusPlus/VivecraftMod.git "$work"

for patch in "$here"/patches/"$mc"/*.patch; do
  [ -e "$patch" ] || continue
  echo "Applying $(basename "$patch")"
  git -C "$work" apply --index "$patch"
done

# Some branches (1.21.5 and newer) have a neoforge module but switch it off: it is neither in the enabled platforms
# of the shared module nor in settings.gradle. Turn it on.
if ! grep -q '^enabled_platforms=.*neoforge' "$work/gradle.properties"; then
  sed -i -E 's/^(enabled_platforms=.*)$/\1,neoforge/' "$work/gradle.properties"
fi
if ! grep -q 'include("neoforge")' "$work/settings.gradle"; then
  printf '\ninclude("neoforge")\n' >> "$work/settings.gradle"
fi

# Pojlib's LWJGL jar already provides org.lwjgl.openvr (with its JNI natives, which LWJGL binds to the class
# loader that loaded the classes). On Fabric everything shares one class loader so a second copy was harmless;
# NeoForge loads mods in a loader of their own, where a second copy could not use those natives. Don't bundle it.
# org.lwjgl.openxr is QuestCraft's own build and only exists in this jar, it stays.
sed -i '/^[[:space:]]*bundle("org.lwjgl:lwjgl-openvr/d' "$work/neoforge/build.gradle"

(cd "$work" && chmod +x gradlew && ./gradlew --no-daemon :neoforge:build -x test)

# remapJar writes <name>-neoforge.jar, the other jars in there are dev/shadow intermediates
jar="$(ls "$work"/neoforge/build/libs/*-neoforge.jar | grep -v -e '-dev' -e '-sources' | head -n 1)"

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

mkdir -p "$here/dist"
cp "$jar" "$here/dist/vivecraft-neoforge-$mc.jar"
echo "Built $here/dist/vivecraft-neoforge-$mc.jar"
