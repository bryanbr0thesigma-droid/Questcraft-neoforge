# Horizon on Meta Quest

[Horizon](https://github.com/horizon-rust/horizon) (MIT) is Minecraft Java Edition 26.3's client and game logic
rewritten in Rust, checked against the real game, with no Mojang code or files in it: it prepares its resources
from your own copy of 26.3. This folder builds it for Meta Quest, as a native Android app, and is where its VR goes.

**Status: in progress.** The Android build compiles and packages: `dist/Horizon-quest.apk` (package
`dev.questcraft.horizon`, signed with QuestCraft's key), not yet tried on a headset. There are no prepared
resources on the headset yet, so it opens Horizon's own "resources missing" screen: a test that the game starts and
draws on the Quest (Vulkan through wgpu, winit's Android backend). The same code runs a world on the desktop here
(software Vulkan, resources prepared from Minecraft 26.3 with Java 25).

- `build.sh` fetches Horizon at a pinned commit into `work/horizon`, applies `patches/`, adds `crate/` as
  `crates/horizon-quest` and builds `dist/Horizon-quest.apk`.
- `patches/0001-android-entry.patch`: Horizon's game takes its arguments and event loop from the Android
  entry point (there is no command line); Android, which has no clipboard arboard reaches, pastes what was copied
  in the game; and keys are named as typed there (winit has no key without modifiers on Android).
- `crate/`: `android_main`, the library Android's NativeActivity loads. Its `src/game.rs` and `src/game/` link
  to horizon-client's, so the game is Horizon's own code; its dependencies are copied from horizon-client's.
- `android/AndroidManifest.xml`: an ordinary app for now, which the Quest shows on a panel.

Planned: the resources prepared on the headset (Horizon's exporters need Java 25; QuestCraft's Android Java is
22), then VR: one OpenXR core (a picture per eye through wgpu's Vulkan, tracking, panels for menus and the HUD)
with two modes, Vivecraft's (LGPL-3.0, ported) and Visor's (MPL-2.0; its library AtumVR is Apache-2.0), each
ported file keeping its licence.
