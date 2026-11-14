# Horizon on Meta Quest

[Horizon](https://github.com/horizon-rust/horizon) (MIT) is Minecraft Java Edition 26.3's client and game logic
rewritten in Rust, checked against the real game, with no Mojang code or files in it: it prepares its resources
from your own copy of 26.3. This folder builds it for Meta Quest, as QuestCraft's launcher runs it (the loader
**Horizon**), and is where its VR goes.

`build.sh` fetches Horizon at a pinned commit into `work/horizon`, applies `patches/`, adds `crate/` as
`crates/horizon-quest`, and makes `dist/horizon-android.zip`, which godot-launcher/build-apk.sh puts in the
launcher's APK:

- `arm64-v8a/libhorizon_quest.so`: the game, the library HorizonActivity (a NativeActivity) loads. `crate/` is its
  `android_main`: it reads the arguments the launcher wrote (`horizon-args.txt`: the instance's folder, the prepared
  resources, the player's name) and starts Horizon's own entry (horizon-client's `entry::main`).
- `arm64-v8a/libhorizon_prepare.so`: horizon-prepare, which prepares the resources on the headset (a program, named
  lib*.so so that Android installs it where programs may be started).
- `arm64-v8a/libhqjava.so`: `java` for it (`javalauncher/`). The Java 25 runtime the app downloads can't start its own
  bin/java; this loads its `lib/libjli.so` and hands it the command line, as bin/java does.
- `root.zip`: Horizon's release files the preparer reads, as Horizon's release folder has them: research-lock.json,
  the exporters' sources, the preparer's sources, and the exporters compiled (`exporters/`: Horizon's own classes,
  compiled against 26.3 as Horizon's releases are; the first build needs a JDK 25 in `JAVA25_HOME` and downloads 26.3
  from Mojang for that, `tools/fetch_minecraft.py`).

The patches:

- `0001-android-entry.patch`: Horizon's game takes its arguments and event loop from the Android entry point (there is
  no command line) and returns instead of exiting after an error (the activity then ends and the launcher comes
  back); Android, which has no clipboard arboard reaches, pastes what was copied in the game; and keys are named as
  typed there (winit has no key without modifiers on Android).
- `0002-prepare-java-launcher.patch`: horizon-prepare starts Java through the program `HORIZON_JAVA` names.

**Status**: on a Quest 3S Horizon prepares its resources, starts, and shows its title in the headset (VR) or on a
panel (Screen). Built with `PROFILE=fast`.

**VR** (`xr/`, the crate `horizon-xr`, MIT): QuestCraft's OpenXR core. The headset's runtime and Horizon share one
Vulkan device: wgpu creates the instance and device with the extensions the runtime asks for (`XR_KHR_vulkan_enable`)
on the GPU it names. Each eye has an 8-bit sRGB swapchain whose images are wgpu textures. Horizon's side is
`patches/xr.rs` (its renderer's `xr` module) and `tools/patch_xr_renderer.py` / `tools/patch_xr_game.py`, which build.sh
runs: with a headset installed the renderer opens its device through it, draws offscreen at the eyes' size and format,
and each frame waits for the headset, draws once per eye (the head's turn and lean and the eye's place on top of the
player's eyes, the lens's own angles) and copies the frame byte for byte into the eye's image; the player looks where
the head does (turning turns the body); the Touch controllers are a gamepad merged into the game's, and the window has
the focus while the headset's session does (the pad counts only then). The HUD shader's `vr` transform puts the HUD on
a panel in the tracking space, seen in perspective (`xr::Panel`): while playing it is 1.6 m ahead of the head and turns
with it; a menu (the title, pause, inventory, a dialog...) goes on a panel put level 1.5 m ahead where the player looks
as it opens, which stays there, as Vivecraft's and Visor's menus do (looked away from for 1.5 s, it comes round). In a
menu the controller's laser (its aim pose) is the mouse: where it meets the panel the game gets the pointer, and the
trigger is the left button (the hand whose trigger was pulled last points). The controllers are drawn as small boxes
(left blue, right orange at the front), with the laser and its dot in a menu, over each eye's picture. The title's
panorama is around the player, turned as the head is. The launcher passes `--vr` (the library takes it out) and
starts it as an immersive activity.

`cargo run -p horizon-xr --example probe` tries the core against any OpenXR runtime; `--example desktop` runs the game
with it. On a desktop with Monado's simulated headset (`monado-service` on a virtual display, software Vulkan;
`SIMULATED_ENABLE=1 SIMULATED_LEFT=simple SIMULATED_RIGHT=simple` for two controllers) both eyes show the world and the
HUD (`vr-desktop-test.png`), and a menu on its panel with the controllers and the laser (`vr-menu-test.png`).

Still to come: Vivecraft's way (hands, room scale; LGPL-3.0, ported) and Visor's (MPL-2.0; its library AtumVR is
Apache-2.0), each ported file keeping its licence.
