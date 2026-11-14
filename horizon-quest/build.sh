#!/usr/bin/env bash
# Builds Horizon (https://github.com/horizon-rust/horizon, MIT: Minecraft Java Edition 26.3 rewritten in
# Rust) for Meta Quest, as QuestCraft's launcher runs it: dist/horizon-android.zip, with the game's library,
# horizon-prepare and the `java` it starts (dist/arm64-v8a, not committed) and Horizon's release files
# the preparer reads (root.zip). godot-launcher/build-apk.sh puts them in the launcher's APK.
#
# Horizon is fetched at the pinned commit into work/horizon, patches/ is applied (an Android entry
# for its game: arguments and event loop, and no system clipboard), and crate/ becomes its
# crates/horizon-quest: the library Android's NativeActivity loads, which starts Horizon's own
# game (horizon-client's entry).
#
# Needs: Rust 1.100 (rustup, with the aarch64-linux-android target; RUST_TOOLCHAIN for another), cargo-ndk, the Android NDK, and the first time a
# JDK 25 (JAVA25_HOME) to compile Horizon's exporters. Override: ANDROID_HOME, ANDROID_NDK_HOME, WORK,
# PROFILE (fast or release).
set -euo pipefail

here="$(cd "$(dirname "$0")" && pwd)"
HORIZON_REPO="https://github.com/horizon-rust/horizon.git"
HORIZON_COMMIT="f4d8d62c31ec2b08046faded7bef1b476783f319"
WORK="${WORK:-$here/work}"
PROFILE="${PROFILE:-release}"
# Rust 1.98 or newer: older standard libraries have no file locks on Android ("try_lock() not supported"), which the
# preparer and the game's worlds use
RUST="+${RUST_TOOLCHAIN:-1.100.0}"
ANDROID_HOME="${ANDROID_HOME:-/tmp/claude-0/android-sdk}"
ANDROID_NDK_HOME="${ANDROID_NDK_HOME:-$(ls -d "$ANDROID_HOME"/ndk/* | sort -V | tail -1)}"
export ANDROID_HOME ANDROID_NDK_HOME

src="$WORK/horizon"

# 1. Horizon at the pinned commit, with QuestCraft's patches (a clean tree each time: work/ is ours)
mkdir -p "$WORK"
if [ ! -d "$src/.git" ]; then
  git init -q "$src"
  git -C "$src" remote add origin "$HORIZON_REPO"
fi
if [ "$(git -C "$src" rev-parse -q --verify HEAD 2>/dev/null)" != "$HORIZON_COMMIT" ]; then
  git -C "$src" fetch -q --depth 1 origin "$HORIZON_COMMIT"
fi
git -C "$src" checkout -q -f "$HORIZON_COMMIT"
git -C "$src" clean -q -fd -e target
for patch in "$here"/patches/*.patch; do
  git -C "$src" apply "$patch"
done

# 2. crates/horizon-quest: the library that starts Horizon's game (horizon-client's entry)
crate="$src/crates/horizon-quest"
mkdir -p "$crate/src"
cp "$here/crate/Cargo.toml" "$crate/Cargo.toml"
cp "$here/crate/src/lib.rs" "$crate/src/lib.rs"

# 3. For the Quest (arm64): the game's library, and horizon-prepare, which prepares its resources on the headset
#    (a program: named lib*.so so that Android installs it with the libraries, where programs may be started)
# (two builds: `--bin` would leave the game's library out of a build that names both)
(cd "$src" && cargo "$RUST" ndk -t arm64-v8a --platform 29 build --profile "$PROFILE" -p horizon-quest --lib)
(cd "$src" && cargo "$RUST" ndk -t arm64-v8a --platform 29 build --profile "$PROFILE" -p horizon-prepare --bin horizon-prepare)
ndkbin="$ANDROID_NDK_HOME/toolchains/llvm/prebuilt/linux-x86_64/bin"
out="$here/dist/arm64-v8a"
rm -rf "$out" && mkdir -p "$out"
cp "$src/target/aarch64-linux-android/$PROFILE/libhorizon_quest.so" "$out/"
cp "$src/target/aarch64-linux-android/$PROFILE/horizon-prepare" "$out/libhorizon_prepare.so"
"$ndkbin/llvm-strip" --strip-debug "$out/libhorizon_quest.so" "$out/libhorizon_prepare.so"
# and `java` for it: Java runtimes the app downloads can't start their own bin/java (javalauncher/)
"$ndkbin/aarch64-linux-android29-clang" -O2 -Wall -fPIE -pie -o "$out/libhqjava.so" "$here/javalauncher/javalauncher.c" -ldl

# 4. Horizon's release files the preparer reads, as Horizon's release folder has them (xtask package): research-lock.json,
#    the exporters' sources and their compiled bundle (Horizon's own classes, compiled against your 26.3 like its
#    release's are), and the preparer's sources. The bundle needs Java 25 (JAVA25_HOME) and 26.3 from Mojang, once.
root="$WORK/root"
exporters="$WORK/exporters-$HORIZON_COMMIT"
if [ ! -f "$exporters/manifest.json" ]; then
  : "${JAVA25_HOME:?set JAVA25_HOME to a JDK 25 to compile the exporters}"
  python3 -I "$here/tools/fetch_minecraft.py" "$WORK/mc"
  (cd "$src" && CARGO_TARGET_DIR="$WORK/target-host" cargo build --release -p horizon-prepare --bin horizon-prepare)
  host="$WORK/target-host/release/horizon-prepare"
  scratch="$WORK/exporters-root"
  rm -rf "$scratch" && mkdir -p "$scratch"
  git -C "$src" archive HEAD research-lock.json mods/horizon/src/test/java crates/horizon-prepare | tar x -C "$scratch"
  env -u JAVA_TOOL_OPTIONS JAVA_HOME="$JAVA25_HOME" "$host" --prepare --root "$scratch" --minecraft-dir "$WORK/mc" \
    --server-jar "$WORK/mc/server.jar" --output "$scratch/.local/resources"
  bundle=$(env -u JAVA_TOOL_OPTIONS JAVA_HOME="$JAVA25_HOME" "$host" compile-exporters --root "$scratch" | tail -1)
  rm -rf "$exporters" && mkdir -p "$exporters"
  cp -R "$bundle/server" "$bundle/client" "$bundle/manifest.json" "$exporters/"
fi
rm -rf "$root" && mkdir -p "$root"
(cd "$src" && tar c research-lock.json LICENSE mods/horizon/src/test/java crates/horizon-prepare/Cargo.toml \
  crates/horizon-prepare/build.rs crates/horizon-prepare/src) | tar x -C "$root"
cp -R "$exporters" "$root/exporters"
rm -f "$here/dist/root.zip"
(cd "$root" && zip -q -r -X "$here/dist/root.zip" .)

# 5. One archive of it all, which godot-launcher/build-apk.sh puts in the launcher's APK
rm -f "$here/dist/horizon-android.zip"
(cd "$here/dist" && zip -q -r -X horizon-android.zip arm64-v8a root.zip)
rm -f "$here/dist/root.zip"
echo "built $here/dist/horizon-android.zip ($(du -h "$here/dist/horizon-android.zip" | cut -f1))"
