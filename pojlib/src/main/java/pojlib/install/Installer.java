package pojlib.install;

import android.app.Activity;

import com.google.gson.Gson;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;

import org.apache.commons.io.FileUtils;

import pojlib.APIHandler;
import pojlib.util.download.DownloadManager;
import pojlib.util.download.DownloadUtils;
import pojlib.util.json.MinecraftInstances;
import pojlib.util.*;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.Map;
import java.util.StringJoiner;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Future;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

//This class reads data from a game version json and downloads its contents.
//This works for the base game as well as mod loaders
public class Installer {

    public static void installJVM(Activity activity, boolean force) {
        Logger.getInstance().appendToLog("Checking JRE");
        File jre = new File(activity.getFilesDir(), "runtimes/JRE");
        String jreURL = "https://github.com/QuestCraftPlusPlus/android-openjdk-build-multiarch/releases/latest/download/JRE.zip";

        try {
            if (!jre.exists() || force) {
                Logger.getInstance().appendToLog("Installing JRE");
                File jreZip = new File(activity.getFilesDir() + "/runtimes/JRE.zip");
                DownloadUtils.downloadFile(jreURL, jreZip);
                DownloadManager.reset();
                FileUtil.unzipArchive(jreZip.getPath(), activity.getFilesDir() + "/runtimes/JRE");
                Files.copy(Paths.get(activity.getApplicationInfo().nativeLibraryDir + "/libawt_xawt.so"), Paths.get(activity.getFilesDir() + "/runtimes/JRE/lib/libawt_xawt.so"));
                jreZip.delete();
            }

            Logger.getInstance().appendToLog("JRE installed");
        } catch (IOException e) {
            Logger.getInstance().appendToLog("Failed to install JRE: " + e.getMessage());
            e.printStackTrace();
        }
    }

    // Will only download client if it is missing, however it will overwrite if sha1 does not match the downloaded client
    // Returns client classpath
    public static CompletableFuture<String> installClient(VersionInfo minecraftVersionInfo, String gameDir) throws IOException {
        return CompletableFuture.supplyAsync(() -> {
            Logger.getInstance().appendToLog("Checking Client");

            File clientFile = new File(gameDir + "/versions/" + minecraftVersionInfo.id + "/client.jar");

            try {
                for (int i = 0; i < 5; i++) {
                    if (i == 4)
                        throw new RuntimeException("Client download failed after 5 retries");

                    if (!clientFile.exists()) {
                        DownloadUtils.downloadFile(minecraftVersionInfo.downloads.client.url, clientFile);
                    } else if (DownloadUtils.compareSHA1(clientFile, minecraftVersionInfo.downloads.client.sha1)) {
                        clientFile.delete();
                        DownloadUtils.downloadFile(minecraftVersionInfo.downloads.client.url, clientFile);
                    }

                    // Check if the downloaded client matches the expected SHA1 hash
                    if (DownloadUtils.compareSHA1(clientFile, minecraftVersionInfo.downloads.client.sha1)) {
                        Logger.getInstance().appendToLog("Client downloaded");
                        return clientFile.getAbsolutePath();
                    }
                }
            } catch (IOException e) {
                Logger.getInstance().appendToLog("Failed to download client: " + e.getMessage());
                e.printStackTrace();
            }
            return null;
        });
    }

    // Will only download library if it is missing, however it will overwrite if sha1 does not match the downloaded library
    // Returns the classpath of the downloaded libraries
    public static CompletableFuture<String> installLibraries(VersionInfo versionInfo, String gameDir) throws IOException {
        return CompletableFuture.supplyAsync(() -> {
            Logger.getInstance().appendToLog("Checking Libraries for: " + versionInfo.id);
            StringJoiner classpath = new StringJoiner(File.pathSeparator);

            for (VersionInfo.Library library : versionInfo.libraries) {
                // vanilla does not use ASM, the loaders do (NeoForge even loads it from its module path)
                boolean isLoader = versionInfo.id.contains("fabric") || versionInfo.id.contains("neoforge")
                        || versionInfo.id.contains("forge") || versionInfo.id.contains("quilt");
                // LWJGL is the headset's own (lwjgl-glfw-classes.jar). Only LWJGL's own group: the sound system's OpenAL
                // plug-in of 1.12.2 and older (com.paulscode:librarylwjglopenal) has "lwjgl" in its name and is needed
                if (library.name.startsWith("org.lwjgl") || (library.name.contains("org.ow2.asm") && !isLoader)) {
                    continue;
                }
                if (!library.appliesToLinux() || library.nativesOnly()) {
                    continue;
                }
                for (int i = 0; i < 5; i++) {
                    if (i == 4)
                        throw new RuntimeException(String.format("Library download of %s failed after 5 retries", library.name));

                    File libraryFile;
                    String sha1;

                    //Null means mod lib, otherwise vanilla lib
                    try {
                        if (library.downloads == null) {
                            String path = parseLibraryNameToPath(library.name);
                            libraryFile = new File(gameDir + "/libraries/", path);
                            sha1 = APIHandler.getRaw(library.url + path + ".sha1");
                            if (!libraryFile.exists()) {
                                Logger.getInstance().appendToLog("Downloading: " + library.name);
                                DownloadUtils.downloadFile(library.url + path, libraryFile);
                            }
                        } else {
                            VersionInfo.Library.Artifact artifact = library.downloads.artifact;
                            libraryFile = new File(gameDir + "/libraries/", artifact.path);
                            sha1 = artifact.sha1;
                            if (madeByInstaller(artifact)) {
                                // Forge's patched client: no address, the loader's installer wrote it. Its checksum is of the
                                // bytes the installer's maker got, and the headset's zlib compresses to other ones, so it
                                // is the file as it is or nothing
                                if (!libraryFile.exists()) throw new IOException("Not made by the loader's installer: " + libraryFile);
                                classpath.add(libraryFile.getAbsolutePath());
                                break;
                            }
                            if (!libraryFile.exists()) {
                                Logger.getInstance().appendToLog("Downloading: " + library.name);
                                DownloadUtils.downloadFile(artifact.url, libraryFile, artifact.size);
                            }
                        }
                        if (DownloadUtils.compareSHA1(libraryFile, sha1)) {
                            classpath.add(libraryFile.getAbsolutePath());
                            break;
                        }
                    } catch (IOException e) {
                        e.printStackTrace();
                    }
                }
            }

            // Add our GLFW
            classpath.add(Constants.USER_HOME + "/lwjgl3/lwjgl-glfw-classes.jar");
            // DNS SRV Resolver fix
            classpath.add(Constants.USER_HOME + "/hacks/ResConfHack.jar");

            Logger.getInstance().appendToLog("Libraries installed");
            return classpath.toString();
        });
    }

    /** A library without a download address is one the loader's installer makes (Forge's patched client). */
    static boolean madeByInstaller(VersionInfo.Library.Artifact artifact) {
        return artifact.url == null || artifact.url.isEmpty();
    }

    //Only works on minecraft, not fabric, neoforge, etc...
    //Will only download asset if it is missing
    public static CompletableFuture<String> installAssets(VersionInfo minecraftVersionInfo, String gameDir) throws IOException {
        return CompletableFuture.supplyAsync(() -> {
            Logger.getInstance().appendToLog("Checking assets");
            JsonObject assets = APIHandler.getFullUrl(minecraftVersionInfo.assetIndex.url, JsonObject.class);

            for (Map.Entry<String, JsonElement> entry : assets.getAsJsonObject("objects").entrySet()) {
                VersionInfo.Asset asset = new Gson().fromJson(entry.getValue(), VersionInfo.Asset.class);
                DownloadManager.addTotalBytes(asset.size);
            }

            ThreadPoolExecutor tp = new ThreadPoolExecutor(8, 8, 100, TimeUnit.MILLISECONDS, new LinkedBlockingQueue<>());

            for (Map.Entry<String, JsonElement> entry : assets.getAsJsonObject("objects").entrySet()) {
                AsyncDownload thread = new AsyncDownload(entry, gameDir);
                tp.execute(thread);
            }

            tp.shutdown();
            try {
                while (!tp.awaitTermination(100, TimeUnit.MILLISECONDS)) ;
            } catch (InterruptedException e) {
                Logger.getInstance().appendToLog("Download thread interrupted" + e.getMessage());
            }

            File indexJson = new File(gameDir + "/assets/indexes/" + minecraftVersionInfo.assets + ".json");
            if (!indexJson.exists()) {
                try {
                    DownloadUtils.downloadFile(minecraftVersionInfo.assetIndex.url, indexJson);
                } catch (IOException e) {
                    e.printStackTrace();
                }
            }

            return new File(gameDir + "/assets").getAbsolutePath();
        });
    }

    /**
     * Minecraft before 1.7 reads its sounds and music from files under their own names, not from the objects
     * {@link #installAssets} downloads: before 1.6 from {@code resources/} in its folder (Mojang's index says
     * "map_to_resources"), 1.6 from {@code assets/virtual/legacy} ("virtual"). Copies them there, once.
     *
     * @param gameDir     where installAssets put the objects and the index
     * @param instanceDir the instance's folder (the game's folder)
     * @return the assets folder to give the game
     */
    public static String mapLegacyAssets(VersionInfo minecraftVersionInfo, String gameDir, String instanceDir) throws IOException {
        File assetsDir = new File(gameDir, "assets");
        File index = new File(assetsDir, "indexes/" + minecraftVersionInfo.assets + ".json");
        JsonObject json = new Gson().fromJson(FileUtils.readFileToString(index, java.nio.charset.StandardCharsets.UTF_8), JsonObject.class);
        boolean toResources = json.has("map_to_resources") && json.get("map_to_resources").getAsBoolean();
        boolean virtual = json.has("virtual") && json.get("virtual").getAsBoolean();
        if (!toResources && !virtual) return assetsDir.getAbsolutePath();

        File target = toResources ? new File(instanceDir, "resources") : new File(assetsDir, "virtual/" + minecraftVersionInfo.assets);
        for (Map.Entry<String, JsonElement> entry : json.getAsJsonObject("objects").entrySet()) {
            VersionInfo.Asset asset = new Gson().fromJson(entry.getValue(), VersionInfo.Asset.class);
            File file = new File(target, entry.getKey());
            if (file.isFile() && file.length() == asset.size) continue;
            File object = new File(assetsDir, "objects/" + asset.hash.substring(0, 2) + "/" + asset.hash);
            if (!object.isFile()) {
                Logger.getInstance().appendToLog("Missing sound or music file: " + entry.getKey());
                continue;
            }
            FileUtils.copyFile(object, file);
        }
        return (toResources ? assetsDir : target).getAbsolutePath();
    }

    /** ModernFix's settings, written at every launch too: an instance made earlier has an older file. */
    public static void writeModernFixConfig(android.content.Context context, MinecraftInstances.Instance instance) throws IOException {
        byte[] bundled = FileUtil.loadFromAssetToByte(context, "modernfix-mixins.properties");
        if (bundled != null) {
            FileUtils.writeByteArrayToFile(new File(instance.gameDir + "/config/modernfix-mixins.properties"), bundled);
        }
    }

    /**
     * The launcher used to ship options Minecraft 1.21.11 rejects (brightness -0.2, simulation distance 4) and the game
     * answers by writing its own defaults back, a 50 % brightness that looks like a gray veil. This puts the values
     * the launcher means into an options.txt that has them wrong. Done once per instance, after that it is the
     * player's file.
     */
    public static String migrateOptions(String options) {
        StringBuilder out = new StringBuilder();
        for (String line : options.split("\\r?\\n", -1)) {
            if (line.startsWith("gamma:")) {
                line = "gamma:0.0";
            } else if (line.startsWith("simulationDistance:")) {
                try {
                    if (Integer.parseInt(line.substring("simulationDistance:".length()).trim()) < 5) line = "simulationDistance:5";
                } catch (NumberFormatException ignored) {
                }
            }
            out.append(line).append('\n');
        }
        // split keeps a trailing empty element for a final newline, do not add a second one
        return out.toString().replaceAll("\\n+$", "\n");
    }

    public static void migrateOptionsOnce(MinecraftInstances.Instance instance) {
        File marker = new File(instance.gameDir + "/config/.questcraft-options-1");
        File options = new File(instance.gameDir + "/options.txt");
        if (marker.exists() || !options.isFile()) return;
        try {
            String text = FileUtils.readFileToString(options, java.nio.charset.StandardCharsets.UTF_8);
            FileUtils.writeStringToFile(options, migrateOptions(text), java.nio.charset.StandardCharsets.UTF_8);
            FileUtils.writeStringToFile(marker, "1", java.nio.charset.StandardCharsets.UTF_8);
            Logger.getInstance().appendToLog("Fixed brightness and simulation distance in options.txt");
        } catch (IOException e) {
            Logger.getInstance().appendToLog("Could not update options.txt: " + e);
        }
    }

    public static void moveLocalAssets(Activity activity, MinecraftInstances.Instance instance) throws IOException {
        try {
            FileUtils.writeByteArrayToFile(new File(instance.gameDir + "/config/sodium-options.json"), FileUtil.loadFromAssetToByte(activity, "sodium-options.json"));
            FileUtils.writeByteArrayToFile(new File(instance.gameDir + "/config/smoothboot.json"), FileUtil.loadFromAssetToByte(activity, "smoothboot.json"));
            FileUtils.writeByteArrayToFile(new File(instance.gameDir + "/config/immediatelyfast.json"), FileUtil.loadFromAssetToByte(activity, "immediatelyfast.json"));
            FileUtils.writeByteArrayToFile(new File(instance.gameDir + "/config/moreculling.toml"), FileUtil.loadFromAssetToByte(activity,"moreculling.toml"));
            writeModernFixConfig(activity, instance);
            if (instance.usesLoaderInstaller()) {
                FileUtils.writeByteArrayToFile(new File(instance.gameDir + "/config/fml.toml"), FileUtil.loadFromAssetToByte(activity, "fml.toml"));
            }
            // QuestCraft's options and Vivecraft settings are those of the newer versions, 1.12.2 and older make their own
            boolean legacy = pojlib.util.MinecraftVersions.isLegacy(instance.versionName);
            if (!legacy) {
                FileUtils.writeByteArrayToFile(new File(instance.gameDir + "/options.txt"), FileUtil.loadFromAssetToByte(activity, "options.txt"));
                FileUtils.writeStringToFile(new File(instance.gameDir + "/config/.questcraft-options-1"), "1", java.nio.charset.StandardCharsets.UTF_8);
            }
            FileUtils.writeByteArrayToFile(new File(instance.gameDir + "/servers.dat"), FileUtil.loadFromAssetToByte(activity, "servers.dat"));
            if (!instance.usesVisor() && !legacy) FileUtils.writeByteArrayToFile(new File(instance.gameDir + "/config/vivecraft-client-config.json"), FileUtil.loadFromAssetToByte(activity, "vivecraft-client-config.json"));
            FileUtils.writeByteArrayToFile(new File(Constants.USER_HOME + "/hacks/ResConfHack.jar"), FileUtil.loadFromAssetToByte(activity, "hacks/ResConfHack.jar"));
            FileUtils.writeByteArrayToFile(new File(Constants.USER_HOME + "/hacks/resolv.conf"), FileUtil.loadFromAssetToByte(activity, "hacks/resolv.conf"));
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    public static class AsyncDownload implements Runnable {
        private final Map.Entry<String, JsonElement> entry;
        private final String gameDir;
        private final String fileName;

        public AsyncDownload(Map.Entry<String, JsonElement> entry, String gameDir) {
            this.entry = entry;
            this.gameDir = gameDir;
            this.fileName = entry.getKey();
        }

        @Override
        public void run() {
            VersionInfo.Asset asset = new Gson().fromJson(entry.getValue(), VersionInfo.Asset.class);
            String path = asset.hash.substring(0, 2) + "/" + asset.hash;
            File assetFile = new File(gameDir + "/assets/objects/", path);

            for (int i = 0; i < 5; i++) {
                if (i == 4) throw new RuntimeException(String.format("Asset download of %s failed after 5 retries", fileName));

                if (!assetFile.exists()) {
                    Logger.getInstance().appendToLog("Downloading: " + fileName);
                    try {
                        DownloadUtils.downloadFile(Constants.MOJANG_RESOURCES_URL + "/" + path, assetFile, 0);
                    } catch (IOException e) {
                        throw new RuntimeException(e);
                    }
                }

                if (DownloadUtils.compareSHA1(assetFile, asset.hash)) {
                    break;
                } else {
                    assetFile.delete();
                }
            }
        }
    }


    //Used for mod libraries, vanilla is handled a different (tbh better) way
    private static String parseLibraryNameToPath(String libraryName) {
        String[] parts = libraryName.split(":");
        String location = parts[0].replace(".", "/");
        String name = parts[1];
        String version = parts[2];

        return String.format("%s/%s/%s/%s", location, name, version, name + "-" + version + ".jar");
    }
}
