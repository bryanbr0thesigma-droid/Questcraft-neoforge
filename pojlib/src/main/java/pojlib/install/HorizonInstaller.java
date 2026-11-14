package pojlib.install;

import android.content.Context;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import org.apache.commons.codec.digest.DigestUtils;
import org.apache.commons.compress.archivers.tar.TarArchiveEntry;
import org.apache.commons.compress.archivers.tar.TarArchiveInputStream;
import org.apache.commons.compress.compressors.xz.XZCompressorInputStream;
import org.apache.commons.io.FileUtils;

import java.io.BufferedInputStream;
import java.io.BufferedReader;
import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.function.Consumer;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

import pojlib.account.MinecraftAccount;
import pojlib.util.FileUtil;
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
 * <p>
 * Then {@link #prepare} runs that preparer on the headset. It is a program (libhorizon_prepare.so in the app's native
 * library folder, the only place Android lets an app start programs from) that reads Horizon's release files
 * ({@code horizon/root.zip} in the app's assets: research-lock.json, the exporters precompiled from Horizon's sources)
 * and runs the exporters, which call the game's own code, in Java 25. Java 25 for Android is downloaded once, and
 * started through libhqjava.so, which stands in for its bin/java (horizon-quest/javalauncher).
 */
public final class HorizonInstaller {
    /** The one version Horizon is: research-lock.json's {@code minecraft.version}. */
    public static final String VERSION = "26.3";

    private HorizonInstaller() {
    }

    public static boolean isAvailable(String minecraftVersion) {
        return VERSION.equals(minecraftVersion);
    }

    /** Horizon's own downloads in the launcher's folder: the server jar. */
    public static File folder(String gameDir) {
        return new File(gameDir, "horizon");
    }

    /**
     * Horizon's working files in the app's own storage: its release files with the preparer's scratch, and the prepared
     * resources. Not on shared storage, which is slower with many small files and does not do everything a file
     * system does.
     */
    public static File home(Context context) {
        return new File(context.getFilesDir(), "horizon");
    }

    public static File serverJar(String gameDir) {
        return new File(folder(gameDir), "server/" + VERSION + "/server.jar");
    }

    /** Where the resources are prepared to, the game's {@code --resources}. */
    public static File resources(Context context) {
        return new File(home(context), "resources/" + VERSION);
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
     * Java 25 for Android on arm64, which Horizon's exporters run in: the runtime AngelAuraMC builds for the Amethyst
     * launcher (OpenJDK, GPL-2.0 with the Classpath Exception). Pinned: a different file is refused.
     */
    static final String RUNTIME_URL =
            "https://github.com/AngelAuraMC/angelauramc-openjdk-build/releases/download/download_jre25/jre25-android-arm64.tar.xz";
    static final String RUNTIME_SHA256 = "d3eb7afe2240c26728a1bb440502c5f18ac3883e932d202dd7f0c9bcbbce4c37";
    /** Horizon's release files the preparer reads, in the app's assets (godot-launcher/build-apk.sh puts them there). */
    static final String ROOT_ASSET = "horizon/root.zip";

    public static File runtime(Context context) {
        return new File(context.getFilesDir(), "runtimes/JRE25");
    }

    /** Java 25, downloaded and unpacked once (the stamp says which file it came from). */
    static File installRuntime(Context context) throws IOException {
        File home = runtime(context);
        File stamp = new File(home, ".questcraft-sha256");
        if (stamp.isFile() && RUNTIME_SHA256.equals(FileUtils.readFileToString(stamp, StandardCharsets.UTF_8).trim())
                && new File(home, "lib/libjli.so").isFile()) {
            return home;
        }
        Logger.getInstance().appendToLog("Horizon: downloading Java 25");
        File archive = new File(home.getParentFile(), "jre25-android-arm64.tar.xz");
        archive.delete();
        DownloadUtils.downloadFile(RUNTIME_URL, archive);
        String sha256;
        try (InputStream in = Files.newInputStream(archive.toPath())) {
            sha256 = DigestUtils.sha256Hex(in);
        }
        if (!RUNTIME_SHA256.equals(sha256)) {
            archive.delete();
            throw new IOException("The Java 25 download is not the one QuestCraft expects (" + sha256 + "): update QuestCraft");
        }
        FileUtils.deleteDirectory(home);
        try (TarArchiveInputStream tar = new TarArchiveInputStream(new XZCompressorInputStream(
                new BufferedInputStream(Files.newInputStream(archive.toPath()))))) {
            TarArchiveEntry entry;
            while ((entry = tar.getNextEntry()) != null) {
                File target = new File(home, entry.getName());
                if (!target.getCanonicalPath().startsWith(home.getCanonicalPath() + File.separator)) continue;
                if (entry.isDirectory()) {
                    target.mkdirs();
                } else if (entry.isSymbolicLink()) {
                    // only the licence files link to java.base's
                    target.getParentFile().mkdirs();
                    try {
                        Files.createSymbolicLink(target.toPath(), Paths.get(entry.getLinkName()));
                    } catch (Exception ignored) {
                    }
                } else if (entry.isFile()) {
                    target.getParentFile().mkdirs();
                    Files.copy(tar, target.toPath());
                }
            }
        }
        archive.delete();
        FileUtils.writeStringToFile(stamp, RUNTIME_SHA256, StandardCharsets.UTF_8);
        return home;
    }

    /** Horizon's release files, unpacked into {@code horizon/root} when the app carries others than were unpacked. */
    static File installRoot(Context context) throws IOException {
        byte[] zip = FileUtil.loadFromAssetToByte(context, ROOT_ASSET);
        if (zip == null) throw new IOException("This app has no Horizon (" + ROOT_ASSET + ")");
        File root = new File(home(context), "root");
        File stamp = new File(root, ".questcraft-sha256");
        String sha256 = DigestUtils.sha256Hex(zip);
        if (stamp.isFile() && sha256.equals(FileUtils.readFileToString(stamp, StandardCharsets.UTF_8).trim())) {
            return root;
        }
        // over what is there: .local keeps the import and the exporters' scratch, which the preparer checks itself
        try (ZipInputStream in = new ZipInputStream(new ByteArrayInputStream(zip))) {
            ZipEntry entry;
            while ((entry = in.getNextEntry()) != null) {
                File target = new File(root, entry.getName());
                if (!target.getCanonicalPath().startsWith(root.getCanonicalPath() + File.separator)) continue;
                if (entry.isDirectory()) {
                    target.mkdirs();
                } else {
                    target.getParentFile().mkdirs();
                    Files.copy(in, target.toPath(), java.nio.file.StandardCopyOption.REPLACE_EXISTING);
                }
            }
        }
        FileUtils.writeStringToFile(stamp, sha256, StandardCharsets.UTF_8);
        return root;
    }

    /**
     * Prepares Horizon's resources from the downloaded game files, unless they are prepared already ({@code --check}).
     * Takes a minute or a few the first time.
     *
     * @param status hears what it is doing, for the launcher's progress screen
     * @param fraction hears how far it is, 0 to 1
     */
    public static void prepare(Context context, String gameDir, Consumer<String> status, Consumer<Double> fraction)
            throws IOException, InterruptedException {
        String nativeDir = context.getApplicationInfo().nativeLibraryDir;
        File preparer = new File(nativeDir, "libhorizon_prepare.so");
        if (!preparer.isFile()) throw new IOException("This app has no Horizon preparer (" + preparer + ")");
        File root = installRoot(context);
        File resources = resources(context);
        if (run(context, preparer, root, null, null, "--check", "--root", root.getPath(), "--output", resources.getPath()) == 0) {
            return;
        }
        status.accept("Downloading Java 25 for Horizon");
        File java = installRuntime(context);
        status.accept("Preparing Horizon's game files");
        int exit = run(context, preparer, root, java, line -> {
            JsonObject o;
            try {
                o = JsonParser.parseString(line).getAsJsonObject();
            } catch (Exception e) {
                Logger.getInstance().appendToLog("Horizon: " + line);
                return;
            }
            if (o.has("message")) Logger.getInstance().appendToLog("Horizon: " + o.get("message").getAsString());
            if (o.has("fraction")) fraction.accept(o.get("fraction").getAsDouble());
            if (o.has("error")) Logger.getInstance().appendToLog("Horizon failed: " + o.get("error").getAsString());
        }, "--prepare", "--complete", "--root", root.getPath(), "--minecraft-dir", gameDir,
                "--server-jar", serverJar(gameDir).getPath(), "--output", resources.getPath(), "--progress", "json");
        if (exit != 0) throw new IOException("Horizon could not prepare its game files (see the log)");
    }

    /** Runs the preparer and returns its exit code; with {@code java}, the preparer can start Java 25. */
    private static int run(Context context, File preparer, File root, File java, Consumer<String> lines, String... args)
            throws IOException, InterruptedException {
        List<String> command = new ArrayList<>();
        command.add(preparer.getPath());
        for (String arg : args) command.add(arg);
        ProcessBuilder builder = new ProcessBuilder(command).directory(root).redirectErrorStream(true);
        Map<String, String> env = builder.environment();
        String nativeDir = context.getApplicationInfo().nativeLibraryDir;
        env.put("HOME", context.getFilesDir().getPath());
        env.put("TMPDIR", context.getCacheDir().getPath());
        if (java != null) {
            env.put("JAVA_HOME", java.getPath());
            env.put("HORIZON_JAVA", new File(nativeDir, "libhqjava.so").getPath());
            // the runtime's libraries need the app's libc++_shared.so
            env.put("LD_LIBRARY_PATH", java + "/lib:" + java + "/lib/server:" + nativeDir);
        }
        Process process = builder.start();
        try (BufferedReader out = new BufferedReader(new InputStreamReader(process.getInputStream(), StandardCharsets.UTF_8))) {
            String line;
            while ((line = out.readLine()) != null) {
                if (lines != null) lines.accept(line);
                else Logger.getInstance().appendToLog("Horizon: " + line);
            }
        }
        return process.waitFor();
    }

    /**
     * The game's command line, which HorizonActivity hands to the library: the instance's folder for its worlds,
     * options, logs and crash reports, the prepared resources, and the player's name.
     */
    public static String[] launchArgs(Context context, MinecraftInstances.Instance instance, MinecraftAccount account) {
        List<String> args = new ArrayList<>();
        args.add("--game-dir");
        args.add(instance.gameDir);
        args.add("--home");
        args.add(instance.gameDir);
        args.add("--resources");
        args.add(resources(context).getAbsolutePath());
        if (account != null && account.username != null && !account.username.isEmpty()) {
            args.add("--name");
            args.add(account.username);
        }
        return args.toArray(new String[0]);
    }
}
