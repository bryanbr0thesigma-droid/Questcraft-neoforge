# Visor for the headset

[Visor](https://github.com/VisorModStudio/Visor) is the other VR mod for Minecraft Java (next to Vivecraft), built on the OpenXR library
[AtumVR](https://github.com/Phoenix-Ra/AtumVR). Both are open source (MPL-2.0 and Apache-2.0). Neither runs on Android as published: they
create their OpenXR session for a desktop OpenGL context. QuestCraft's build changes that the way QuestCraft's Vivecraft does:

- `patches/atumvr-questcraft-android.patch` - AtumVR gets an `OPENGL_ES` graphics API and `AndroidSupport`: the loader is given the
  JVM and the activity (`xrInitializeLoaderKHR`), the instance is created with `XR_KHR_android_create_instance`, the session binds
  the game's EGL display, config and context (`XrGraphicsBindingOpenGLESAndroidKHR`), and the swapchain images are OpenGL ES ones.
  `org.vivecraft.util.VLoader` is the native side (`libvloader.so`, built into Pojlib); it has Vivecraft's name because the native
  functions are bound to it.
- `patches/visor-questcraft-quest.patch` - Visor is built against QuestCraft's LWJGL OpenXR (`lib/`, it has the Android parts the
  standard one lacks) and ships it inside the mod jar instead of the desktop natives; no Vulkan; no Parchment names.

`./build.sh <version> <loader>` makes the jars in `pojlib/visor-jars/`, listed in `pojlib/visor-mods.json`, which the launcher reads.
An instance picks Vivecraft or Visor when it is created and never has both (they conflict).

**Status: built, not tried on a headset.**
