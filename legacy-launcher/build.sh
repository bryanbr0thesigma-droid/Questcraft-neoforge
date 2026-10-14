#!/bin/bash
# Builds the launcher for old Minecraft versions (see src/questcraft/legacy/LegacyLauncher.java) into the app:
# pojlib/src/main/assets/legacy/questcraft-legacy-launcher.jar. Java 8 bytecode, with ASM inside (BSD licence).
# Needs a JDK 11 or newer (JAVA_HOME) and the ASM 9.5 jars (ASM, ASM_COMMONS: downloaded when not given).
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
(cd work/classes && unzip -q -o "$OLDPWD/$ASM" 'org/*' && unzip -q -o "$OLDPWD/$ASM_COMMONS" 'org/*')
OUT=$REPO/pojlib/src/main/assets/legacy/questcraft-legacy-launcher.jar
mkdir -p "$(dirname "$OUT")"
rm -f "$OUT"
(cd work/classes && "$JAVA_HOME/bin/jar" --create --file "$OUT" --main-class questcraft.legacy.LegacyLauncher .)
echo "built $OUT ($(du -h "$OUT" | cut -f1))"
