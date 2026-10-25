#!/usr/bin/env bash
# Builds the Android port of Cemu (the Wii U emulator, SSimco/Cemu branch android-port, MPL-2.0) into an APK. Step one
# of Minecraft: Wii U Edition in VR on the Quest: find out whether the emulator runs the game there at all, flat,
# before any VR work goes into it.
#
#   ./build.sh                 ->  dist/Cemu-android.apk, from the commit pinned below
#   ./build.sh <commit|branch> ->  the same from another commit of the fork
#
# Needs JDK 17 or newer, the Android SDK (ANDROID_HOME) with NDK 29.0.14206865, cmake 3.25+, ninja and git; gettext
# (msgunfmt) for the translations. The first build compiles the vcpkg libraries (boost, openssl, curl, ...) for
# arm64-android, which takes a while; they are kept in vcpkg-cache/ (VCPKG_DEFAULT_BINARY_CACHE) for the next one.
#
# The APK is the emulator only. Games are your own dumps, picked from the headset's storage in the app.
#
# Signing: with ANDROID_KEYSTORE_BASE64, ANDROID_KEYSTORE_PASS and ANDROID_KEYALIAS_NAME set (the secrets of the Build
# APK workflow; the fork uses one password for the store and the key) the APK is signed with that key, otherwise with
# a debug key made on the spot, so uninstall before installing a build signed on another machine.
set -euo pipefail

COMMIT=8759323e5b72594665b55e02f99db3c1e1e7de8a
NDK_VERSION=29.0.14206865
ref="${1:-$COMMIT}"
here="$(cd "$(dirname "$0")" && pwd)"
work="$here/work"

rm -rf "$work"
git init -q "$work"
git -C "$work" remote add origin https://github.com/SSimco/Cemu.git
git -C "$work" fetch -q --depth 1 origin "$ref"
git -C "$work" checkout -q FETCH_HEAD
# vcpkg needs its history (the manifest pins a baseline commit), the others come shallow as .gitmodules asks
git -C "$work" submodule update --init --recursive --recommend-shallow

for patch in "$here"/patches/*.patch; do
  [ -e "$patch" ] || continue
  echo "Applying $(basename "$patch")"
  git -C "$work" apply --index "$patch"
done

# the Android app reads its translations as .po files (as the fork's own workflow makes them)
if command -v msgunfmt >/dev/null; then
  for mo in "$work"/bin/resources/*/cemu.mo; do
    [ -e "$mo" ] || continue
    po="$work/src/android/app/src/main/assets/translations/$(basename "$(dirname "$mo")")/cemu.po"
    mkdir -p "$(dirname "$po")"
    msgunfmt "$mo" -o "$po" || echo "warning: could not convert $mo"
  done
fi

export ANDROID_HOME="${ANDROID_HOME:-${ANDROID_SDK_ROOT:?set ANDROID_HOME to the Android SDK}}"
export ANDROID_NDK_HOME="$ANDROID_HOME/ndk/$NDK_VERSION"
export VCPKG_DEFAULT_BINARY_CACHE="${VCPKG_DEFAULT_BINARY_CACHE:-$here/vcpkg-cache}"
mkdir -p "$VCPKG_DEFAULT_BINARY_CACHE"
bash "$work/dependencies/vcpkg/bootstrap-vcpkg.sh" -disableMetrics

if [ -n "${ANDROID_KEYSTORE_BASE64:-}" ]; then
  base64 --decode <<< "$ANDROID_KEYSTORE_BASE64" > "$work/release.jks"
  export ANDROID_STORE_FILE="$work/release.jks"
  export ANDROID_KEY_STORE_PASSWORD="${ANDROID_KEYSTORE_PASS:?}"
  export ANDROID_KEY_ALIAS="${ANDROID_KEYALIAS_NAME:?}"
fi

(cd "$work/src/android" && chmod +x gradlew && ./gradlew --no-daemon assembleRelease)

mkdir -p "$here/dist"
cp "$work"/src/android/app/build/outputs/apk/release/*.apk "$here/dist/Cemu-android.apk"
echo "built cemu-builder/dist/Cemu-android.apk ($(git -C "$work" rev-parse --short HEAD))"
