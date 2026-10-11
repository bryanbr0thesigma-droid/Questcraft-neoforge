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

(cd "$work" && chmod +x gradlew && ./gradlew --no-daemon :neoforge:build -x test)

# remapJar writes <name>-neoforge.jar, the other jars in there are dev/shadow intermediates
jar="$(ls "$work"/neoforge/build/libs/*-neoforge.jar | grep -v -e '-dev' -e '-sources' | head -n 1)"
mkdir -p "$here/dist"
cp "$jar" "$here/dist/vivecraft-neoforge-$mc.jar"
echo "Built $here/dist/vivecraft-neoforge-$mc.jar"
