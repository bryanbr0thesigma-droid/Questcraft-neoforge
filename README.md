# QuestCraft for NeoForge

A port of [QuestCraft](https://github.com/QuestCraftPlusPlus/QuestCraft) (Minecraft: Java Edition in VR on Meta Quest and other standalone headsets) from the **Fabric** mod loader to **NeoForge**. The launcher has a **loader toggle** between **Fabric**, **NeoForge** and **Quilt**, and NeoForge is the default.

![QC](/QCSimple3.jpg)

QuestCraft itself is not a mod. It is a launcher app (a Unity project that calls into an Android Java library, Pojlib) that installs Minecraft, a mod loader, and a set of mods - Vivecraft above all - and runs the game on the headset. Porting it to NeoForge therefore touches three projects, which upstream keeps in separate repositories and which live side by side here:

| Directory | Upstream | What changed for NeoForge |
|---|---|---|
| [`pojlib/`](pojlib) | [Pojlib](https://github.com/QuestCraftPlusPlus/Pojlib), branch `QuestCraft-6.0.1` | Installs and launches NeoForge instead of Fabric, NeoForge mod lists, Microsoft login and mod handling unchanged in behaviour |
| [`unity-wrapper/`](unity-wrapper) | [QCXR-XR-Wrapper](https://github.com/QuestCraftPlusPlus/QCXR-XR-Wrapper), `master` | Creates NeoForge instances, Mod Manager searches/validates mods for the instance's loader |
| [`vivecraft-builder/`](vivecraft-builder) | [VivecraftMod](https://github.com/QuestCraftPlusPlus/VivecraftMod) (QuestCraft's Vivecraft fork), `OpenXR-*` branches | Builds the fork's `neoforge` module; upstream only publishes the Fabric jar |

## Supported Minecraft versions

**NeoForge: 1.20.4, 1.20.6, 1.21.1, 1.21.4, 1.21.5, 1.21.8 and 1.21.11.** NeoForge only exists from Minecraft 1.20.2 (older versions only have Forge), so QuestCraft's 1.19.x and 1.20.1 cannot be ported to it.

**Quilt: every version QuestCraft's Fabric list has** (1.19.2, 1.19.4, 1.20.1, 1.20.4, 1.20.6, 1.21.1, 1.21.4, 1.21.5, 1.21.8, 1.21.10, 1.21.11), see [Quilt](#quilt).

Left out of NeoForge: **1.21.10**. Upstream QuestCraft ships it, but the fork's `OpenXR-1.21.10` branch does not compile its shared module in this setup even when built exactly as shipped (Fabric only; the Minecraft classes never reach the compiler), so there is no working source to start from. (1.21.11 is newer and does not depend on it, see below.)

## Loader toggle (Fabric / NeoForge / Quilt)

The instance creator has a loader dropdown with **Fabric**, **NeoForge** and **Quilt**. It decides which Minecraft versions the creator offers (each loader has its own list), what the new instance is installed with, and which mod list its default mods come from. The choice is remembered (`lastSelectedLoader` in `launcher.conf`); a config from before the toggle counts as NeoForge.

- Each instance keeps the loader it was made with (shown next to the version in the instance list). Mod Manager searches Modrinth for, and checks mods and dependencies against, the loader of the selected instance; a modpack becomes an instance of that loader, and a modpack for the other loader is refused.
- **Quick play** (picking a version in the main dropdown and pressing Play when there is no instance of that name yet) creates the instance with the selected loader, or with the other one if the selected loader does not support that version.
- Fabric instances use upstream QuestCraft's Fabric mod list and version list as before; NeoForge uses the ones in this repository; Quilt uses Fabric's mod list with its own version list. A Fabric instance made before the port keeps working unchanged.
- The loaders live in one place, [`ModLoader`](pojlib/src/main/java/pojlib/install/ModLoader.java): id, display name, Modrinth category, where its mod list and version list are hosted. A further loader (this is how Quilt was added) needs an entry there, an install branch in `InstanceHandler.create` and its launch arguments; the dropdown, the version lists and Mod Manager follow from the registry.

Limits: a default instance is named after its version (`1.21.1`), so once one exists the main dropdown launches that instance whichever loader is selected now. For the same version on the other loader, create it in the instance creator under another name.

## Quilt

Quilt Loader runs Fabric mods (its own `quilt.mod.json` is optional), so the port adds it without a Quilt build of anything:

- **Install**: like Fabric, it is a profile of libraries plus `KnotClient` from the Quilt meta API (`QuiltMeta`), so Pojlib installs it with the same library path. The newest *stable* loader is used (the newest overall is a beta), and Pojlib keeps ASM libraries on the classpath for it as it does for Fabric.
- **Mods**: the Fabric mod list (core mods, default mods, Vivecraft's Fabric jar) installs unchanged. Mod Manager searches the `quilt` **and** `fabric` categories on Modrinth and accepts files and dependencies from either; a `.mrpack` is accepted if it lists `quilt-loader` or `fabric-loader`. Plain Fabric API is used (Quilted Fabric API has no builds for Minecraft 1.21+).
- **Versions**: `pojlib/supportedVersions-quilt.json`, written by `update_mods.py`.
- **Instances** keep `quilt` as their loader, show it next to the version and keep their own mod list snapshot (`mods-quilt.json`).

## How it works

### NeoForge instead of Fabric

Fabric is a list of libraries plus a main class, so Pojlib could install it by downloading that list. NeoForge is not: its installer patches and remaps the Minecraft client (mappings, jar splitting, binary patching) and launches through `BootstrapLauncher` on a Java module path. Pojlib therefore:

1. downloads the newest NeoForge installer for the Minecraft version (`NeoForgeMeta`),
2. runs it headless (`--install-client`) in a JVM of its own, in a separate Android process (`NeoForgeInstallService`; a process can only host one JVM and the game needs the main one),
3. reads the `neoforge-<version>.json` the installer wrote, merges it over the vanilla version json the way the vanilla launcher does (`VersionInfo.merge`) and resolves its templated JVM/game arguments (`LaunchArguments`),
4. launches with the module-path arguments NeoForge asks for, and
5. writes `config/fml.toml` with `earlyWindowControl = false`, because FML's early loading screen would open a second GLFW/GL context next to Vivecraft's.

If NeoForge's module setup needs adjusting on a headset without a rebuild, put JVM arguments (one per line) in `<app files>/loader_jvm_args.txt`; they are added to the launch command.

### Mods

NeoForge instances get their mods from [`pojlib/mods-neoforge.json`](pojlib/mods-neoforge.json) (and `devmods-neoforge.json`). They are generated by [`pojlib/tools/update_mods.py`](pojlib/tools/update_mods.py): QuestCraft's Fabric mod set for each version, mapped to the newest NeoForge build on Modrinth, with a check that every required dependency is in the list. Mods without a NeoForge build for a version are left out (the generator prints them): Krypton, Lithium on 1.20.x and MoreCulling on 1.20.6. Sodium has no 1.20.4 NeoForge build and is replaced by Embeddium there.

In the app, **Mod Manager** searches Modrinth for the loader of the selected instance and checks mod files and their dependencies against it, so only mods that can load are offered.

### Microsoft login

The login (device-code flow through MSAL, then Xbox Live, XSTS and Minecraft services) is part of Pojlib and does not depend on the mod loader. It is unchanged from upstream.

### Vivecraft

QuestCraft's Vivecraft fork has all of its Quest/OpenXR changes in the shared `common` module, and a `neoforge` module on every branch, so no code port was needed. [`vivecraft-builder/build.sh <mc version>`](vivecraft-builder/build.sh) builds that module. Two things it has to do:

- On the 1.21.5 and 1.21.8 branches the module exists but is switched off (not in `enabled_platforms`, not in `settings.gradle`); the script turns it on.
- It leaves out the `org.lwjgl.openvr` classes the module would bundle, because Pojlib's LWJGL jar already provides them (with their JNI natives, which LWJGL binds to the class loader that loaded the classes; NeoForge gives mods a loader of their own, where a second copy could not use them). `org.lwjgl.openxr` is QuestCraft's own build and stays. The script checks both in the finished jar.

**1.21.11** is different: QuestCraft's fork has no source for it (its `6.0.1-1.21.11` tag points at an old Minecraft 1.21 commit; only a prebuilt Fabric jar exists). So it is ported here: QuestCraft's change set (about 44 files, nearly all of it the OpenXR provider) was merged onto upstream Vivecraft's `Multiloader-1.21.11` the way the fork's maintainers update the fork, starting from the fork's newest branch so only one Minecraft version had to be crossed instead of ten. The result is [`vivecraft-builder/patches/1.21.11/questcraft-openxr.patch`](vivecraft-builder/patches/1.21.11/questcraft-openxr.patch), applied by `build.sh` on a clone of upstream, and it builds for **both** loaders (`build.sh 1.21.11 both`). The OpenXR renderer had to be adapted to upstream's new eye-target handling: the swapchain image the runtime hands out each frame is now put into `framebufferEye[]`, and `setupRenderConfiguration()` delegates to QuestCraft's two-mode `setupRenderConfiguration(boolean)`. The Fabric 1.21.11 mod list still points at upstream QuestCraft's prebuilt jar (1.3.7); the Fabric jar built here is attached to the same release for anyone who wants to use it instead.

The mod lists link to the jars at `releases/download/vivecraft-<mc>/vivecraft-neoforge-<mc>.jar` of this repository: run the **Build Vivecraft (NeoForge)** workflow with *publish* ticked to create them.

## Getting the APK

The launcher is a Unity project, so the APK is built by the **Build APK** workflow (`.github/workflows/apk.yml`, GameCI, Unity 2022.3.62f3): Actions tab -> *Build APK* -> *Run workflow*, then download `QuestCraft-apk` from the run and sideload the `.apk` (SideQuest or `adb install`). It needs a Unity license once as a repository secret: activate a Personal license in Unity Hub on your computer and paste the contents of `Unity_lic.ulf` (Windows `C:\ProgramData\Unity\`, macOS `/Library/Application Support/Unity/`, Linux `~/.local/share/unity3d/Unity/`) into `UNITY_LICENSE`, see [GameCI's activation guide](https://game.ci/docs/github/activation). Plus/Pro seats can use `UNITY_EMAIL`/`UNITY_PASSWORD`/`UNITY_SERIAL` instead. The APK is debug-signed unless you add the `ANDROID_KEYSTORE_*` secrets. This workflow has not been run (no Unity here), so expect to fix a first-build problem or two.

## Building

1. **Pojlib AAR** - `cd pojlib && ./gradlew assembleRelease` (JDK 17, Android SDK 34, build tools 30.0.3, NDK 26.1.10909125; the **Build Pojlib** workflow does this and runs the unit tests). Copy `build/outputs/aar/*-release.aar` to `unity-wrapper/Assets/Plugins/Android/Pojlib-release.aar`. The wrapper calls into that AAR, so **a NeoForge launcher needs this rebuilt AAR**.
2. **Vivecraft** - `./vivecraft-builder/build.sh 1.21.1` (JDK 21), or the workflow.
3. **Unity wrapper** - open `unity-wrapper/` in Unity and build the Android player as upstream does.
4. **Mod lists** - `python3 pojlib/tools/update_mods.py`.

`Constants.PORT_RAW_URL` in Pojlib is where the launcher downloads the NeoForge mod list and supported versions from, and the mod list links to this repository's releases for the Vivecraft jars. **That only works while the repository is public** (anonymous downloads of a private repository return 404), or if you host those files elsewhere and change the URL. It points at this repository's current default branch.

## What has and has not been verified

Done here:
- **Pojlib builds**: `./gradlew testDebugUnitTest assembleRelease` succeeds with the real Android SDK/NDK, and the resulting AAR is the one in `unity-wrapper/Assets/Plugins/Android/`. It has the same file set as the AAR the wrapper shipped with, and every Java method and field the wrapper's C# calls (`createNewInstance`, `prelaunch`, `login`, `addExtraProject`, ...) exists with a matching signature.
- **Unit tests** ([`pojlib/src/test`](pojlib/src/test)) run against the profile the real NeoForge installer generated: version selection, argument resolution, library de-duplication, and that only the loader's arguments reach the command line.
- **The NeoForge installer** was run headless against the real maven and produced the layout Pojlib expects (`--install-client`).
- **A desktop simulation of the launch**: the command line Pojlib builds (same merged libraries, resolved module-path arguments, Pojlib's own LWJGL jar, `fml.toml`) was run on a desktop JVM. BootstrapLauncher and ModLauncher start, FML discovers Minecraft, NeoForge and (with the jar built here in `mods/`) Vivecraft, Vivecraft's Mixin configs apply, and Minecraft's client `main` runs - until it needs Android's arm64 `liblwjgl.so`, which cannot exist on a desktop. That run is what found two bugs (vanilla's templated arguments leaking into the command line, and the vanilla client jar name FML has to ignore).
- **Vivecraft**: the NeoForge jars of all seven versions build with `vivecraft-builder/build.sh` and pass its content checks, and each one loads under NeoForge in the desktop simulation above (FML lists `Vivecraft`, its Mixin configs apply, no module-layer errors) before reaching the Android-only native libraries. The 1.21.11 Fabric jar was boot-tested the same way (Fabric Loader with Fabric API: `vivecraft 1.21.11-1.3.15` loads, then the same native-library wall). NeoForge 1.21.11 uses a new FML (version 10, `net.neoforged.fml.startup.Client` as the main class); the simulation confirms the early window is still switched off by `earlyWindowControl = false`.
- **Quilt**: for each of the 11 versions, Quilt Loader 0.30.1 was booted on Pojlib's classpath model with the mods Pojlib would install (core + default mods of the Fabric list; the loader reports 72-98 mods per version, nested jars included). All of them resolve and load (Vivecraft, Fabric API, Sodium, ...) and apply their Mixins up to the same Android-only native-library wall as Fabric and NeoForge. That run also showed that Pojlib must keep dropping vanilla's own ASM jar for these versions (it ships 9.6, the loader's is newer; both on the classpath give VerifyErrors in MixinExtras) - Pojlib does, the first version of the simulation did not.
- **Mod lists** are generated from live Modrinth data and dependency-checked.

**Not verified: running on a headset.** There is no device here, so the following is untested and is where problems, if any, will show up first:
- Quilt on a device at all (it is the Fabric install path with a different loader; the desktop boot above is the only test), and mods that rely on Fabric-only behaviour Quilt does not provide.
- The NeoForge installer (a separate Android process) and the game on the Android JRE, including the OpenXR path of Vivecraft under NeoForge.
- Whether the default mods (Sodium/Embeddium, ImmediatelyFast, ...) behave on the Quest's GL driver as their Fabric builds do. QuestCraft ran patched Sodium builds on older versions.
- **Microsoft login** and the **Mod Manager** UI on device. Their Java side is unchanged/loader-aware and was checked for API compatibility with the Unity scripts, but they were not run.

# Upstream README (QuestCraft)

Everything below describes QuestCraft itself and still applies, except where it says Fabric.

- Roomscale Minecraft VR, Quest 2 / 3 / Pro native, Pico and other headsets, multiplayer, immersive controls through Vivecraft, fully standalone.
- Install: build or download the APK, sideload it with [SideQuest](https://sidequestvr.com/setup-howto), open QuestCraft in Unknown Sources, log in with the Microsoft account that owns Minecraft: Java Edition and press Play. The first launch of each version downloads its files (about 10 minutes); keep the headset on while it does.
- Renderer: Light Thin Wrapper, made jointly with [PojavLauncherTeam](https://github.com/PojavLauncherTeam).
- Support: [QuestCraft Discord](https://discord.gg/questcraft). **questcraft.net is not maintained by the QuestCraft team**; the project site is [questcraft.org](https://questcraft.org/).
- Tips: set VR Settings / Stereo Rendering resolution to 80%, keep render distance at 4-6, Skyblock and Oneblock worlds run best.

## Controls

![Diagram of the table below](/QC_Controls.png)

| Button | Function |
|---|---|
| Left Thumbstick | Move (press to sprint) |
| Right Thumbstick | Left / right turn (press to crouch) |
| Left Trigger | Place / use |
| Right Trigger | Break / grab |
| Left Grab | Hotbar left |
| Right Grab | Hotbar right |
| X | Inventory |
| Y | Teleport |
| A | Radial menu |
| B | Jump |
| Menu Button | Pause / Esc |
| Meta Button | Meta Home |
