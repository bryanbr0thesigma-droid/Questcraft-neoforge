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

**Status**: on the headset Horizon starts and draws (its "resources missing" screen, before preparing existed). Preparing
on the headset is new and untried there; on a desktop the same files, arguments and launcher prepare in 30 seconds.
Built with `PROFILE=fast`.

Planned: VR. One OpenXR core (a picture per eye through wgpu's Vulkan, tracking, panels for menus and the HUD) with two
modes, Vivecraft's (LGPL-3.0, ported) and Visor's (MPL-2.0; its library AtumVR is Apache-2.0), each ported file
keeping its licence.
