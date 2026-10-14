# ModLoader builder

Makes the list of Risugami's ModLoader builds the app installs from, and checks every version on a desktop. See
"Risugami's ModLoader" in the top README for what the app does with it.

1. `make_registry.py` reads MCArchive's ModLoader page, takes the newest build of every Minecraft version Mojang still
   lists, downloads it (MCArchive's copy, else Risugami's Mediafire page) and checks MCArchive's SHA-256. Writes
   `pojlib/modloader-builds.json` and the app's copy; the builds stay in `work/archives`.
2. `fetch_clients.py` downloads Mojang's clients, version files and (for 1.6) libraries into `work/`.
3. Pojlib's `RisugamiModLoaderTest` merges each build into its client with the app's own code (RAR archives included):
   `QC_MODLOADER_ARCHIVES=work/archives QC_MINECRAFT_CLIENTS=work/clients QC_MODLOADER_OUT=work/merged ./gradlew testDebugUnitTest --tests '*RisugamiModLoaderTest*'`
   in `pojlib/`.
4. `test_versions.sh` starts each merged client through the legacy launcher (`legacy-launcher/build.sh` first) on its
   own X display and screenshots it. Needs Xvfb, ImageMagick, `LWJGL2` (a folder with LWJGL 2's jars, jinput and
   jutils) and `LWJGL2_NATIVES`. Results in `work/test/results.txt`.
5. `make_lists.py` writes the versions that passed into `supportedVersions-modloader.json`, the (empty) mod lists and
   the Godot launcher's desktop mock.
