#!/bin/bash
# Builds the launcher for old Minecraft versions (see src/questcraft/legacy/LegacyLauncher.java) into the app:
# pojlib/src/main/assets/legacy/questcraft-legacy-launcher.jar. Java 8 bytecode, with ASM inside (BSD licence),
# LWJGL's OpenXR binding (BSD licence, QuestCraft's build) for VR, and the VR glue of each version in glue/ (vr/VERSION.jar).
# Needs a JDK 11 or newer (JAVA_HOME) and the ASM 9.5 jars (ASM, ASM_COMMONS: downloaded when not given).
# The glue is written with OrnitheMC's Feather names and translated into the game's with Fabric's tiny-remapper
# (downloaded, with the game's client and the mappings, into work/).
set -e
cd "$(dirname "$0")"
REPO=$(cd .. && pwd)
JAVA_HOME=${JAVA_HOME:-/usr/lib/jvm/java-21-openjdk-amd64}
mkdir -p work
ASM=${ASM:-work/asm-9.5.jar}
ASM_COMMONS=${ASM_COMMONS:-work/asm-commons-9.5.jar}
[ -f "$ASM" ] || curl -sSfL -o "$ASM" https://repo1.maven.org/maven2/org/ow2/asm/asm/9.5/asm-9.5.jar
[ -f "$ASM_COMMONS" ] || curl -sSfL -o "$ASM_COMMONS" https://repo1.maven.org/maven2/org/ow2/asm/asm-commons/9.5/asm-commons-9.5.jar
# compiled against the headset's LWJGL (LWJGL 3 with LWJGL 2's classes), so what is called exists there
LWJGL=$REPO/pojlib/src/main/assets/lwjgl/lwjgl-glfw-classes.jar
OPENXR=$REPO/visor-builder/lib/questcraft-lwjgl-openxr-3.3.3.jar
rm -rf work/classes && mkdir -p work/classes
"$JAVA_HOME/bin/javac" --release 8 -nowarn -d work/classes -cp "$ASM:$ASM_COMMONS:$LWJGL:$OPENXR" $(find src -name "*.java")

# each version's VR glue: its hooks and classes in the game's names, checked against the game, into vr/VERSION.jar
TINY_REMAPPER=work/tiny-remapper-0.11.1-fat.jar
[ -f "$TINY_REMAPPER" ] || curl -sSfL -o "$TINY_REMAPPER" https://maven.fabricmc.net/net/fabricmc/tiny-remapper/0.11.1/tiny-remapper-0.11.1-fat.jar
rm -rf work/tool-classes && mkdir -p work/tool-classes
"$JAVA_HOME/bin/javac" --release 8 -nowarn -d work/tool-classes -cp "work/classes:$ASM" $(find tools -name "*.java")
TOOL="$JAVA_HOME/bin/java -cp work/tool-classes:work/classes:$ASM questcraft.legacy.GlueTool"
for GLUE in glue/*/; do
  VERSION=$(basename "$GLUE")
  W=work/glue/$VERSION
  mkdir -p "$W"
  prop() { grep "^$1=" "$GLUE/glue.properties" | cut -d= -f2-; }
  if [ ! -f "$W/client.jar" ] || [ "$(sha1sum "$W/client.jar" | cut -d' ' -f1)" != "$(prop client.sha1)" ]; then
    curl -sSfL -o "$W/client.jar" "$(prop client)"
    [ "$(sha1sum "$W/client.jar" | cut -d' ' -f1)" = "$(prop client.sha1)" ] || { echo "$VERSION: not the expected client"; exit 1; }
  fi
  if [ ! -f "$W/mappings.tiny" ]; then
    curl -sSfL -o "$W/mappings.jar" "$(prop mappings)"
    unzip -q -o -p "$W/mappings.jar" mappings/mappings.tiny > "$W/mappings.tiny"
  fi
  [ -f "$W/named.jar" ] || "$JAVA_HOME/bin/java" -jar "$TINY_REMAPPER" "$W/client.jar" "$W/named.jar" "$W/mappings.tiny" official named > "$W/remap.log"
  $TOOL map "$W/mappings.tiny" named official "$GLUE/hooks.txt" "$W/hooks.txt"
  $TOOL check "$W/hooks.txt" "$W/client.jar"
  $TOOL widen "$GLUE/hooks.txt" "$W/named.jar" "$W/named-open.jar"
  rm -rf "$W/classes" && mkdir -p "$W/classes"
  "$JAVA_HOME/bin/javac" --release 8 -nowarn -d "$W/classes" -cp "$W/named-open.jar:work/classes:$LWJGL" $(find "$GLUE/src" -name "*.java")
  rm -f "$W/glue-named.jar" "$W/glue.jar"
  (cd "$W/classes" && "$JAVA_HOME/bin/jar" --create --file ../glue-named.jar .)
  "$JAVA_HOME/bin/java" -jar "$TINY_REMAPPER" "$W/glue-named.jar" "$W/glue.jar" "$W/mappings.tiny" named official "$W/named-open.jar" >> "$W/remap.log"
  (cd "$W" && "$JAVA_HOME/bin/jar" --update --file glue.jar hooks.txt)
  mkdir -p work/classes/vr
  cp "$W/glue.jar" "work/classes/vr/$VERSION.jar"
  echo "VR glue for $VERSION: $(unzip -l "$W/glue.jar" | tail -1 | awk '{print $2}') files"
done

# what can be checked without a headset: the controllers as keys and mouse, the pointer on the screen
rm -rf work/test-classes && mkdir -p work/test-classes
"$JAVA_HOME/bin/javac" --release 8 -nowarn -d work/test-classes -cp "work/classes:$ASM:$ASM_COMMONS" $(find test -name "*.java")
"$JAVA_HOME/bin/java" -cp "work/classes:work/test-classes:$ASM:$ASM_COMMONS" questcraft.legacy.xr.SelfTest
(cd work/classes && unzip -q -o "$OLDPWD/$ASM" 'org/*' && unzip -q -o "$OLDPWD/$ASM_COMMONS" 'org/*')
# the OpenXR binding the screen in VR uses (LWJGL's core is the headset's own, on the class path)
(cd work/classes && unzip -q -o "$OPENXR" 'org/lwjgl/openxr/*')
OUT=$REPO/pojlib/src/main/assets/legacy/questcraft-legacy-launcher.jar
mkdir -p "$(dirname "$OUT")"
rm -f "$OUT"
(cd work/classes && "$JAVA_HOME/bin/jar" --create --file "$OUT" --main-class questcraft.legacy.LegacyLauncher .)
echo "built $OUT ($(du -h "$OUT" | cut -f1))"
