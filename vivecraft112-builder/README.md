# Vivecraft 1.12.2 for QuestCraft

jrbudda's [Vivecraft_112](https://github.com/jrbudda/Vivecraft_112) (`jrbudda-11-r6`, Minecraft 1.12.2 with OptiFine HD U F5) with its SteamVR provider replaced by OpenXR for the Quest, and built against the LWJGL QuestCraft runs. The main README's [Minecraft 1.12.2 in VR](../README.md#minecraft-1122-in-vr-vanilla) says how it fits into the app.

`./build.sh` (Python 2.7 as `PYTHON2`, a JDK 8 as `JAVA8_HOME`, astyle, Python 3) does all of it:

1. Vivecraft's own setup (`install.py`): MCP 9.40 decompiles the game with OptiFine merged in, Vivecraft's patches go on top. MCP's stop for root is taken out (containers), the game's download address is today's.
2. `fixups.py`: edits to the decompiled sources that the headset needs (OptiFine's GL debug output uses an LWJGL 2 class the headset does not have). Made at build time, so no Minecraft or OptiFine code is kept here.
3. `src/`: the files that replace Vivecraft's, copied over its sources:
   - `provider/MCOpenVR.java`: the VR provider on OpenXR. It keeps its name and static API, which Vivecraft's 70 patched game classes call; the OpenXR side follows QuestCraft's MCOpenXR for Vivecraft 1.19, the game side is Vivecraft 1.12.2's own. The controller's grip pose is the controller pose SteamVR gave, and the aim pose its "tip" component, so aiming and held items sit as they did.
   - `provider/OpenVRStereoRenderer.java`: projections from OpenXR's field of view (per frame), the frame submitted through `MCOpenVR.submitFrame`.
   - `provider/XrBlit.java`: copies the eye textures into the OpenXR swapchain with OpenGL ES directly (gl4es has no array textures).
   - `provider/XRBindings.java`: QuestCraft's Quest bindings.
   - `control/`: input actions and haptics fed from OpenXR instead of SteamVR's input structs.
   - `utils/KeyboardSimulator.java`, `gameplay/screenhandlers/GuiHandler.java`, `utils/AndroidInput.java`: no AWT robot on the headset, so presses, clicks, Escape and scrolling go to the game directly.
   - `util/VLoader.java`: QuestCraft's native helper for the EGL context and the Android activity.
4. MCP compiles everything against the headset's LWJGL (`pojlib/src/main/assets/lwjgl/lwjgl-glfw-classes.jar`: LWJGL 3 with LWJGL 2's classes) and QuestCraft's LWJGL OpenXR, instead of desktop LWJGL 2, then reobfuscates to the game's names (`reobf.py`).
5. `package.py`: the reobfuscated classes, Vivecraft's assets, MCP's names (Vivecraft looks fields up by them) and LWJGL's OpenXR classes, as `vivecraft-1.12.2-questcraft.jar`. `package.py --merge` does what the launcher does with it.
6. `make_registry.py`: puts the jar into the app and writes `pojlib/vivecraft-legacy.json`.

Vivecraft's code is LGPL 3.0 (see Vivecraft_112's LICENSE.md), and so are the files in `src/` changed from it.
