#!/usr/bin/env bash
# Builds the QuestCraft launcher APK (Godot).
#
# Needs: Godot 4.4.1 (headless) with its Android export templates installed, JDK 17, the Android SDK (platform 34,
# build-tools 34.0.0, cmdline-tools) and Pojlib's AAR. Without a keystore it makes a throw-away one, which is fine for
# sideloading. Everything it needs can be overridden:
#   GODOT, ANDROID_HOME, JAVA_HOME, POJLIB_AAR, OUT, KEYSTORE, KEYSTORE_PASS
set -euo pipefail

here="$(cd "$(dirname "$0")" && pwd)"
repo="$(cd "$here/.." && pwd)"
GODOT="${GODOT:-godot}"
OUT="${OUT:-$here/build/QuestCraft.apk}"
AAR="${POJLIB_AAR:-}"
if [ -z "$AAR" ]; then
  for candidate in "$repo/pojlib/build/outputs/aar/Pojlib-release.aar" "$repo/unity-wrapper/Assets/Plugins/Android/Pojlib-release.aar"; do
    [ -f "$candidate" ] && AAR="$candidate" && break
  done
fi
[ -f "$AAR" ] || { echo "Pojlib-release.aar not found (set POJLIB_AAR)"; exit 1; }
: "${ANDROID_HOME:?set ANDROID_HOME to the Android SDK}"
: "${JAVA_HOME:?set JAVA_HOME to a JDK 17}"
export ANDROID_HOME ANDROID_SDK_ROOT="$ANDROID_HOME" JAVA_HOME PATH="$JAVA_HOME/bin:$PATH"

version="$("$GODOT" --version | cut -d. -f1-3)"            # 4.4.1
templates="${GODOT_TEMPLATES:-$HOME/.local/share/godot/export_templates/$version.stable}"
[ -f "$templates/android_source.zip" ] || { echo "Godot export templates for $version are missing in $templates"; exit 1; }

# 1. the editor settings headless Godot reads (where the SDK and JDK are)
settings_dir="${XDG_CONFIG_HOME:-$HOME/.config}/godot"
mkdir -p "$settings_dir"
cat > "$settings_dir/editor_settings-4.4.tres" <<EOS
[gd_resource type="EditorSettings" format=3]

[resource]
export/android/android_sdk_path = "$ANDROID_HOME"
export/android/java_sdk_path = "$JAVA_HOME"
EOS

# 2. signing key
KEYSTORE="${KEYSTORE:-$here/build/questcraft.keystore}"
KEYSTORE_PASS="${KEYSTORE_PASS:-questcraft}"
mkdir -p "$here/build"
if [ ! -f "$KEYSTORE" ]; then
  keytool -genkeypair -v -keystore "$KEYSTORE" -alias questcraft -keyalg RSA -keysize 2048 -validity 10000 \
    -storepass "$KEYSTORE_PASS" -keypass "$KEYSTORE_PASS" -dname "CN=QuestCraft, O=QuestCraft" >/dev/null 2>&1
fi
export GODOT_ANDROID_KEYSTORE_RELEASE_PATH="$KEYSTORE"
export GODOT_ANDROID_KEYSTORE_RELEASE_USER="questcraft"
export GODOT_ANDROID_KEYSTORE_RELEASE_PASSWORD="$KEYSTORE_PASS"

# 3. the Android build template, plus our parts of it
build="$here/android/build"
rm -rf "$here/android"
mkdir -p "$build"
unzip -q "$templates/android_source.zip" -d "$build"
echo "$version.stable" > "$here/android/.build_version"
touch "$build/.gdignore"
cp "$here/android-overlay/AndroidManifest.xml" "$build/AndroidManifest.xml"
cp -r "$here/android-overlay/src/." "$build/src/"
cp "$here/android-overlay/questcraft.gradle" "$build/questcraft.gradle"
mkdir -p "$build/pojlib-libs"
cp "$AAR" "$build/pojlib-libs/Pojlib-release.aar"
echo "apply from: 'questcraft.gradle'" >> "$build/build.gradle"
# the template insists on JDK 17 exactly and on an NDK nobody has installed; any JDK 17+ and the installed NDK do
sed -i 's/JavaVersion.current() != versions.javaVersion/JavaVersion.current() < JavaVersion.VERSION_17/' "$build/build.gradle"
ndk="$(ls "$ANDROID_HOME/ndk" 2>/dev/null | head -1 || true)"
[ -n "$ndk" ] && sed -i "s/ndkVersion         : '[0-9.]*'/ndkVersion         : '$ndk'/" "$build/config.gradle"

# which build is this? the launcher shows it on its Log screen
echo "$(date -u '+%Y-%m-%d %H:%M UTC') $(git -C "$repo" rev-parse --short HEAD 2>/dev/null)" > "$here/build_info.txt"

# 4. import and export
"$GODOT" --headless --path "$here" --import >/dev/null 2>&1 || true
mkdir -p "$(dirname "$OUT")"
"$GODOT" --headless --path "$here" --export-release "Meta Quest" "$OUT"
ls -la "$OUT"
