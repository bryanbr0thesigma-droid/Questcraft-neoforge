#!/bin/bash
# Builds libgl4es.so (arm64) for Pojlib from ptitSeb/gl4es: the OpenGL 2.1 layer old versions (1.12.2 and
# older) run on, since MobileGlues only stubs fixed-function OpenGL. Needs the Android NDK (ANDROID_NDK_HOME).
set -e
cd "$(dirname "$0")"
COMMIT=ec16bedd8819c475326f4f1a3063772c6d986e06
NDK="${ANDROID_NDK_HOME:-${ANDROID_HOME:-/opt/android-sdk}/ndk/26.1.10909125}"
mkdir -p work
if [ ! -d work/gl4es ]; then
    git clone -q https://github.com/ptitSeb/gl4es.git work/gl4es
fi
git -C work/gl4es checkout -q "$COMMIT"

# gl4es' Android.mk makes a static library, Pojlib loads a shared one (LWJGL's org.lwjgl.opengl.libname)
mkdir -p work/jni
sed -e 's/^LOCAL_MODULE := GL/LOCAL_MODULE := gl4es/' \
    -e 's/^LOCAL_CFLAGS += -DSTATICLIB//' \
    -e 's/include \$(BUILD_STATIC_LIBRARY)/include $(BUILD_SHARED_LIBRARY)/' \
    -e 's#^//TODO.*##' \
    -e 's#^LOCAL_PATH := $(call my-dir)#LOCAL_PATH := $(call my-dir)/../gl4es#' \
    work/gl4es/Android.mk > work/jni/Android.mk
printf 'APP_ABI := arm64-v8a\nAPP_PLATFORM := android-26\nAPP_STL := none\n' > work/jni/Application.mk
"$NDK/ndk-build" -C work -j"$(nproc)"
cp work/libs/arm64-v8a/libgl4es.so ../pojlib/src/main/jniLibs/arm64-v8a/libgl4es.so
echo "built pojlib/src/main/jniLibs/arm64-v8a/libgl4es.so"
