package pojlib.install;

import java.io.File;
import java.io.IOException;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

import pojlib.account.MinecraftAccount;
import pojlib.util.Constants;
import pojlib.util.GsonUtils;
import pojlib.util.Logger;
import pojlib.util.download.DownloadUtils;
import pojlib.util.json.MinecraftInstances;

/**
 * Horizon (https://github.com/horizon-rust/horizon, MIT): Minecraft Java Edition 26.3 rewritten in Rust. It is not a
 * mod loader but the game itself, run as a native program (the Godot launcher's HorizonActivity loads
 * libhorizon_quest.so, built by horizon-quest/build.sh), so an instance of it has no class path and no Java.
 * <p>
 * Horizon has no Mojang code or files in it: it prepares its resources (textures, sounds, data, and what its
 * exporters read out of the game's own classes) from the player's copy of 26.3, which it reads where the official
 * launcher keeps it, and from the 26.3 server jar. This downloads that copy from Mojang into the launcher's folder
 * the official launcher's way ({@code versions/26.3/26.3.json} as Mojang serves it, {@code versions/26.3/26.3.jar},
 * every library for Linux on arm64 at its path, the assets) and the server jar to {@code horizon/server/26.3}.
 * Horizon's preparer checks each file against the checksums it pins.
 */
public final class HorizonInstaller {
    /** The one version Horizon is: research-lock.json's {@code minecraft.version}. */
    public static final String VERSION = "26.3";

    private HorizonInstaller() {
    }

    public static boolean isAvailable(String minecraftVersion) {
        return VERSION.equals(minecraftVersion);
    }

    /** Horizon's own files in the launcher's folder: the server jar and the prepared resources. */
    public static File folder(String gameDir) {
        return new File(gameDir, "horizon");
    }

    public static File serverJar(String gameDir) {
        return new File(folder(gameDir), "server/" + VERSION + "/server.jar");
    }

    /** Where the resources are prepared to, the game's {@code --resources}. */
    public static File resources(String gameDir) {
        return new File(folder(gameDir), "resources/" + VERSION);
    }

    /**
     * Downloads what is missing or damaged of the game files Horizon prepares its resources from (the assets are
     * {@link Installer#installAssets}'s, as for every instance).
     *
     * @param gameDir the launcher's folder ({@link Constants#USER_HOME}), which is the official launcher's layout
     * @return the version json
     */
    public static VersionInfo install(String gameDir) throws IOException {
        MinecraftMeta.MinecraftVersion listed = null;
        for (MinecraftMeta.MinecraftVersion version : MinecraftMeta.getVersions()) {
            if (VERSION.equals(version.id)) listed = version;
        }
        if (listed == null) throw new IOException("Mojang does not list Minecraft " + VERSION);

        // Mojang's file as it is: the preparer checks its sha1, so it is downloaded, not written from what Gson read
        File json = new File(gameDir, "versions/" + VERSION + "/" + VERSION + ".json");
        download(Constants.MOJANG_META_URL + "/v1/packages/" + listed.sha1 + "/" + VERSION + ".json", json, listed.sha1);
        VersionInfo info;
        try (Reader reader = Files.newBufferedReader(json.toPath(), StandardCharsets.UTF_8)) {
            info = GsonUtils.GLOBAL_GSON.fromJson(reader, VersionInfo.class);
        }

        Logger.getInstance().appendToLog("Horizon: checking the game files of " + VERSION);
        download(info.downloads.client.url, new File(gameDir, "versions/" + VERSION + "/" + VERSION + ".jar"), info.downloads.client.sha1);
        for (VersionInfo.Library library : libraries(info)) {
            VersionInfo.Library.Artifact artifact = library.downloads.artifact;
            download(artifact.url, new File(gameDir, "libraries/" + artifact.path), artifact.sha1);
        }
        if (info.downloads.server == null) throw new IOException("Minecraft " + VERSION + " has no server jar");
        download(info.downloads.server.url, serverJar(gameDir), info.downloads.server.sha1);
        return info;
    }

    /**
     * The libraries the official launcher installs on Linux on arm64, which is what Horizon's preparer imports on the
     * headset: the ones for Linux, and of the natives the arm64 build (a plain {@code natives-linux} jar is the
     * x64 one when an arm64 one is listed too).
     */
    static List<VersionInfo.Library> libraries(VersionInfo info) {
        Set<String> names = new HashSet<>();
        for (VersionInfo.Library library : info.libraries) names.add(library.name);
        List<VersionInfo.Library> wanted = new ArrayList<>();
        for (VersionInfo.Library library : info.libraries) {
            if (library.downloads == null || library.downloads.artifact == null || !library.appliesToLinux()) continue;
            String[] parts = library.name.split(":");
            String classifier = parts.length > 3 ? parts[3].toLowerCase(Locale.ROOT) : "";
            if (endsWith(classifier, "arm64", "aarch64", "aarch_64")) {
                wanted.add(library);
            } else if (endsWith(classifier, "x86_64", "x64")) {
                continue;
            } else if (classifier.startsWith("natives-")
                    && (names.contains(library.name + "-arm64") || names.contains(library.name + "-aarch64"))) {
                continue;
            } else {
                wanted.add(library);
            }
        }
        return wanted;
    }

    private static boolean endsWith(String classifier, String... names) {
        for (String name : names) {
            if (classifier.equals(name) || classifier.endsWith("-" + name)) return true;
        }
        return false;
    }

    private static void download(String url, File file, String sha1) throws IOException {
        for (int attempt = 0; attempt < 3; attempt++) {
            if (file.isFile() && DownloadUtils.compareSHA1(file, sha1)) return;
            file.delete();
            Logger.getInstance().appendToLog("Downloading " + file.getName());
            DownloadUtils.downloadFile(url, file);
        }
        if (!DownloadUtils.compareSHA1(file, sha1)) throw new IOException("Could not download " + url);
    }

    /**
     * The game's command line, which HorizonActivity hands to the library: the instance's folder for its worlds,
     * options, logs and crash reports, the prepared resources, and the player's name.
     */
    public static String[] launchArgs(MinecraftInstances.Instance instance, MinecraftAccount account) {
        List<String> args = new ArrayList<>();
        args.add("--game-dir");
        args.add(instance.gameDir);
        args.add("--home");
        args.add(instance.gameDir);
        args.add("--resources");
        args.add(resources(Constants.USER_HOME).getAbsolutePath());
        if (account != null && account.username != null && !account.username.isEmpty()) {
            args.add("--name");
            args.add(account.username);
        }
        return args.toArray(new String[0]);
    }
}
