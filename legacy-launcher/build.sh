#!/bin/bash
# Builds the launcher for old Minecraft versions (see src/questcraft/legacy/LegacyLauncher.java) into the app:
# pojlib/src/main/assets/legacy/questcraft-legacy-launcher.jar. Java 8 bytecode, with ASM inside (BSD licence),
# LWJGL's OpenXR binding (BSD licence, QuestCraft's build) for VR, and the VR glue of each version (vr/VERSION.jar).
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

# each version's VR glue (glue/modloader, for each version in glue/versions.txt): its #if lines worked out for the
# version, its hooks and classes in the game's names, checked against the game, into vr/VERSION.jar. A version whose
# glue, launcher sources and tools did not change keeps the glue it has (work/glue/VERSION/stamp); GLUE_VERSIONS="b1.7.3
# 1.2.5" builds only those (the others keep the glue last built for them).
TINY_REMAPPER=work/tiny-remapper-0.11.1-fat.jar
[ -f "$TINY_REMAPPER" ] || curl -sSfL -o "$TINY_REMAPPER" https://maven.fabricmc.net/net/fabricmc/tiny-remapper/0.11.1/tiny-remapper-0.11.1-fat.jar
rm -rf work/tool-classes && mkdir -p work/tool-classes
"$JAVA_HOME/bin/javac" --release 8 -nowarn -d work/tool-classes -cp "work/classes:$ASM" $(find tools -name "*.java")
TOOL="$JAVA_HOME/bin/java -cp work/tool-classes:work/classes:$ASM questcraft.legacy.GlueTool"
GLUE=glue/modloader
STAMP=$( (find "$GLUE" glue/versions.txt src tools -type f | sort | xargs sha1sum) | sha1sum | cut -d' ' -f1)
mkdir -p work/classes/vr
while read -r VERSION SHA1 URL FEATHER; do
  case "$VERSION" in ""|"#"*) continue ;; esac
  # no mappings for it (yet): no VR
  [ "$FEATHER" = "-" ] && continue
  W=work/glue/$VERSION
  if [ -n "$GLUE_VERSIONS" ] && ! echo " $GLUE_VERSIONS " | grep -q " $VERSION "; then
    [ -f "$W/glue.jar" ] && cp "$W/glue.jar" "work/classes/vr/$VERSION.jar"
    continue
  fi
  mkdir -p "$W"
  if [ -f "$W/glue.jar" ] && [ "$(cat "$W/stamp" 2>/dev/null)" = "$STAMP" ]; then
    cp "$W/glue.jar" "work/classes/vr/$VERSION.jar"
    continue
  fi
  if [ ! -f "$W/client.jar" ] || [ "$(sha1sum "$W/client.jar" | cut -d' ' -f1)" != "$SHA1" ]; then
    curl -sSfL -o "$W/client.jar" "$URL"
    [ "$(sha1sum "$W/client.jar" | cut -d' ' -f1)" = "$SHA1" ] || { echo "$VERSION: not the expected client"; exit 1; }
  fi
  if [ ! -f "$W/mappings.tiny" ]; then
    MAVEN=$(echo "$FEATHER" | sed 's/+/%2B/')
    curl -sSfL -o "$W/mappings.jar" "https://maven.ornithemc.net/releases/net/ornithemc/feather/$MAVEN/feather-$MAVEN-mergedv2.jar"
    unzip -q -o -p "$W/mappings.jar" mappings/mappings.tiny > "$W/mappings.tiny"
  fi
  [ -f "$W/named.jar" ] || "$JAVA_HOME/bin/java" -jar "$TINY_REMAPPER" "$W/client.jar" "$W/named.jar" "$W/mappings.tiny" official named > "$W/remap.log"
  rm -rf "$W/glue" && mkdir -p "$W/glue"
  $TOOL pp "$VERSION" glue/versions.txt "$GLUE" "$W/glue"
  $TOOL map "$W/mappings.tiny" named official "$W/glue/hooks.txt" "$W/hooks.txt"
  $TOOL check "$W/hooks.txt" "$W/client.jar"
  $TOOL widen "$W/glue/hooks.txt" "$W/named.jar" "$W/named-open.jar"
  rm -rf "$W/classes" && mkdir -p "$W/classes"
  "$JAVA_HOME/bin/javac" --release 8 -nowarn -d "$W/classes" -cp "$W/named-open.jar:work/classes:$LWJGL" $(find "$W/glue/src" -name "*.java")
  rm -f "$W/glue-named.jar" "$W/glue.jar"
  (cd "$W/classes" && "$JAVA_HOME/bin/jar" --create --file ../glue-named.jar .)
  # conflicts in the game's own names (classes the glue does not use) are left alone; verify checks what the glue uses
  "$JAVA_HOME/bin/java" -jar "$TINY_REMAPPER" "$W/glue-named.jar" "$W/glue.jar" "$W/mappings.tiny" named official "$W/named-open.jar" --ignoreConflicts >> "$W/remap.log"
  $TOOL verify "$W/glue-named.jar" "$W/named-open.jar" "$W/glue.jar" "$W/client.jar"
  (cd "$W" && "$JAVA_HOME/bin/jar" --update --file glue.jar hooks.txt)
  cp "$W/glue.jar" "work/classes/vr/$VERSION.jar"
  echo "$STAMP" > "$W/stamp"
  echo "VR glue for $VERSION: $(unzip -l "$W/glue.jar" | tail -1 | awk '{print $2}') files"
done < glue/versions.txt

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
