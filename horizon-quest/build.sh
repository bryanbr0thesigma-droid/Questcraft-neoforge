#!/usr/bin/env bash
# Builds Horizon (https://github.com/horizon-rust/horizon, MIT: Minecraft Java Edition 26.3 rewritten in
# Rust) for Meta Quest: dist/Horizon-quest.apk.
#
# Horizon is fetched at the pinned commit into work/horizon, patches/ is applied (an Android entry
# for its game: arguments and event loop, and no system clipboard), and crate/ becomes its
# crates/horizon-quest: the library Android's NativeActivity loads. Its src/game.rs and src/game/
# link to horizon-client's, so the game is Horizon's own code; its dependencies are horizon-client's,
# copied from its Cargo.toml here so they stay in step.
#
# Needs: Rust with the aarch64-linux-android target, cargo-ndk, the Android NDK and SDK
# (build-tools 34, platform 34). Override: ANDROID_HOME, ANDROID_NDK_HOME, WORK, PROFILE (fast or
# release), KEYSTORE, KEYSTORE_PASS.
set -euo pipefail

here="$(cd "$(dirname "$0")" && pwd)"
HORIZON_REPO="https://github.com/horizon-rust/horizon.git"
HORIZON_COMMIT="2fc88ea632aa0675b421b8a81ca66dfdc38ebfa9"
WORK="${WORK:-$here/work}"
PROFILE="${PROFILE:-release}"
ANDROID_HOME="${ANDROID_HOME:-/tmp/claude-0/android-sdk}"
ANDROID_NDK_HOME="${ANDROID_NDK_HOME:-$(ls -d "$ANDROID_HOME"/ndk/* | sort -V | tail -1)}"
BUILD_TOOLS="$ANDROID_HOME/build-tools/34.0.0"
PLATFORM="$ANDROID_HOME/platforms/android-34/android.jar"
KEYSTORE="${KEYSTORE:-$here/../godot-launcher/build/questcraft.keystore}"
KEYSTORE_PASS="${KEYSTORE_PASS:-questcraft}"
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

# 2. crates/horizon-quest: lib.rs, the links to horizon-client's game module, and Cargo.toml with
#    horizon-client's dependencies
crate="$src/crates/horizon-quest"
mkdir -p "$crate/src"
cp "$here/crate/src/lib.rs" "$crate/src/lib.rs"
ln -sfn ../../horizon-client/src/game.rs "$crate/src/game.rs"
ln -sfn ../../horizon-client/src/game "$crate/src/game"
python3 - "$src/crates/horizon-client/Cargo.toml" "$here/crate/Cargo.toml.in" "$crate/Cargo.toml" <<'EOF'
import re, sys
client, template, out = sys.argv[1:]
text = open(client).read()
deps = text.split('[dependencies]\n', 1)[1].split('\n[', 1)[0].strip()
unix = re.search(r"\[target\.'cfg\(unix\)'\.dependencies\]\n(.*?)(?:\n\n|\n\[|\Z)", text, re.S).group(1).strip()
open(out, 'w').write(open(template).read().replace('@DEPENDENCIES@', deps).replace('@UNIX_DEPENDENCIES@', unix))
EOF

# 3. The library for the Quest (arm64)
(cd "$src" && cargo ndk -t arm64-v8a --platform 29 build --profile "$PROFILE" -p horizon-quest)
lib="$src/target/aarch64-linux-android/$PROFILE/libhorizon_quest.so"

# 4. The APK: the manifest, the library, aligned and signed
out="$WORK/apk"
rm -rf "$out" && mkdir -p "$out/lib/arm64-v8a"
"$BUILD_TOOLS/aapt2" link -o "$out/unsigned.apk" --manifest "$here/android/AndroidManifest.xml" -I "$PLATFORM"
cp "$lib" "$out/lib/arm64-v8a/"
"$ANDROID_NDK_HOME"/toolchains/llvm/prebuilt/linux-x86_64/bin/llvm-strip --strip-debug "$out/lib/arm64-v8a/libhorizon_quest.so"
(cd "$out" && zip -q -r unsigned.apk lib)
"$BUILD_TOOLS/zipalign" -f -p 4 "$out/unsigned.apk" "$out/aligned.apk"
if [ ! -f "$KEYSTORE" ]; then
  KEYSTORE="$WORK/debug.keystore"
  [ -f "$KEYSTORE" ] || keytool -genkeypair -keystore "$KEYSTORE" -alias questcraft -keyalg RSA -keysize 2048 \
    -validity 10000 -storepass "$KEYSTORE_PASS" -keypass "$KEYSTORE_PASS" -dname "CN=QuestCraft" >/dev/null
fi
mkdir -p "$here/dist"
"$BUILD_TOOLS/apksigner" sign --ks "$KEYSTORE" --ks-pass "pass:$KEYSTORE_PASS" --out "$here/dist/Horizon-quest.apk" "$out/aligned.apk"
echo "built $here/dist/Horizon-quest.apk ($(du -h "$here/dist/Horizon-quest.apk" | cut -f1))"
