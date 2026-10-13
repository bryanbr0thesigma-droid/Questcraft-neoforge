#!/bin/bash
# Builds QuestCraft's Vivecraft 1.12.2: jrbudda's Vivecraft_112 with an OpenXR provider for the headset (src/),
# compiled against the LWJGL the headset runs (LWJGL 3 with its LWJGL 2 layer, and OpenXR), then puts it into the
# app (make_registry.py). See README.md.
#
# Needs: Python 2.7 (PYTHON2), a JDK 8 (JAVA8_HOME), astyle, Python 3, git, curl, patch.
set -e
cd "$(dirname "$0")"
HERE=$PWD
REPO=$(cd .. && pwd)
PYTHON2=${PYTHON2:-python2}
JAVA8_HOME=${JAVA8_HOME:?set JAVA8_HOME to a JDK 8}
COMMIT=0eac8e676942cacfa6afd2e570ada6e233a16dda   # Vivecraft_112, jrbudda-11-r6
CLIENT_URL=https://piston-data.mojang.com/v1/objects/0f275bc1547d01fa5f56ba34bdc87d981ee12daf/client.jar
OPTIFINE_URL=https://vivecraft.org/jar/build/OptiFine-1.12.2_HD_U_F5.jar
command -v astyle >/dev/null || { echo "astyle is needed (MCP formats the decompiled game with it, Vivecraft's patches expect that)"; exit 1; }

mkdir -p work
W=$HERE/work/Vivecraft_112
if [ ! -d "$W" ]; then
    git clone -q https://github.com/jrbudda/Vivecraft_112.git "$W"
fi
git -C "$W" checkout -q "$COMMIT"
M=$W/mcp940

# MCP 9.40, unpacked here so its "do not run as root" stop can be taken out before install.py runs it (a container)
if [ ! -f "$M/runtime/commands.py" ]; then
    curl -sSfL -o "$W/mcp940.zip" http://www.modcoderpack.com/files/mcp940.zip
    mkdir -p "$M" && (cd "$M" && unzip -q -o ../mcp940.zip)
    sed -i 's/            if not os.getuid():  # pylint: disable-msg=E1101/            if False:  # root is fine in a container/' "$M/runtime/commands.py"
fi
# the game jar's old address (Amazon S3) is gone
sed -i "s#jar_url = repo + \"versions/\"+mc_version+\"/\"+mc_version+\".jar\"#jar_url = \"$CLIENT_URL\"#" "$W/install.py"

export JAVA_HOME=$JAVA8_HOME PATH=$JAVA8_HOME/bin:$PATH
if [ ! -d "$M/src/minecraft/org/vivecraft" ]; then
    # MCP and its scripts call "python"
    mkdir -p work/bin && ln -sf "$(command -v $PYTHON2)" work/bin/python && export PATH=$HERE/work/bin:$PATH
    (cd "$W" && $PYTHON2 install.py) > work/install.log 2>&1 || { tail -30 work/install.log; exit 1; }
    # hunks that only differ from this astyle's spacing
    for rej in $(cd "$M/src/minecraft" && find . -name "*.rej"); do
        (cd "$M/src/minecraft" && patch --ignore-whitespace -F 3 -p0 "${rej%.rej}" < "$rej" && rm "$rej")
    done
fi
export PATH=$HERE/work/bin:$PATH
[ -z "$(cd "$M/src/minecraft" && find . -name '*.rej')" ] || { echo "Vivecraft's patches did not all apply"; exit 1; }

python3 fixups.py "$M/src/minecraft"
cp -r src/. "$M/src/minecraft/"

# compile against the headset's LWJGL: LWJGL 3 with the LWJGL 2 classes the game uses (Pojlib's), and OpenXR. An
# LWJGL 2 call the headset does not have fails the build instead of the game
L=$M/jars/libraries/org/lwjgl/lwjgl
python3 -c "import zipfile; zipfile.ZipFile('work/empty.jar','w').writestr('META-INF/MANIFEST.MF','Manifest-Version: 1.0\n')"
cp work/empty.jar "$L/lwjgl/2.9.4-nightly-20150209/lwjgl-2.9.4-nightly-20150209.jar"
cp work/empty.jar "$L/lwjgl_util/2.9.4-nightly-20150209/lwjgl_util-2.9.4-nightly-20150209.jar"
mkdir -p "$M/lib"
cp "$REPO/pojlib/src/main/assets/lwjgl/lwjgl-glfw-classes.jar" "$REPO/visor-builder/lib/questcraft-lwjgl-openxr-3.3.3.jar" "$M/lib/"

rm -rf "$M/reobf/minecraft"
(cd "$M" && $PYTHON2 "$HERE/reobf.py" .) > work/reobf.log 2>&1
if grep -q "ERRORS FOUND" work/reobf.log; then grep -A6 "error:" work/reobf.log | head -60; exit 1; fi

python3 package.py "$M/reobf/minecraft" "$W/resources" "$REPO/visor-builder/lib/questcraft-lwjgl-openxr-3.3.3.jar" \
    "$M/conf/joined.srg" work/vivecraft-1.12.2-questcraft.jar
curl -sSfL -o work/OptiFine-1.12.2_HD_U_F5.jar "$OPTIFINE_URL"
python3 make_registry.py work/vivecraft-1.12.2-questcraft.jar work/OptiFine-1.12.2_HD_U_F5.jar
