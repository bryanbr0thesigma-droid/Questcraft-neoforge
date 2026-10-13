package pojlib.install;

import com.google.gson.Gson;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import java.io.BufferedOutputStream;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.Enumeration;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;
import java.util.zip.ZipOutputStream;

import pojlib.util.Constants;
import pojlib.util.FileUtil;
import pojlib.util.Logger;
import pojlib.util.download.DownloadUtils;

/**
 * Vivecraft for the versions before 1.13: Vivecraft 1.12.2 (jrbudda's) on OpenXR, built by {@code vivecraft112-builder}.
 * It is not a mod but changed game classes on top of OptiFine, the way Vivecraft's own installer set it up: the game's
 * client, OptiFine over it, Vivecraft over both, as one jar. One jar because Mojang's client is signed, and a class
 * from another jar in one of its packages would not load.
 * <p>
 * {@code vivecraft-legacy.json} lists, per Minecraft version, the Vivecraft jar, the OptiFine build it was made
 * against (the one Vivecraft's installer downloads from vivecraft.org) and the libraries Vivecraft adds.
 */
public final class LegacyVivecraft {
    public static final String FILE = "vivecraft-legacy.json";
    /** The merged client, next to the game's client.jar */
    public static final String JAR = "client-vivecraft.jar";

    /** The game and OptiFine were made for Java 8, the headset runs 22: what they may reach into in Java's own classes */
    public static final String[] JVM_ARGS = {
            "--add-opens", "java.base/java.lang=ALL-UNNAMED",
            "--add-opens", "java.base/java.nio=ALL-UNNAMED",
            "--add-opens", "java.base/java.util=ALL-UNNAMED"};

    private static volatile JsonObject cached;

    private LegacyVivecraft() {
    }

    /** The registry's entry for a Minecraft version, or null if there is no Vivecraft for it. */
    public static JsonObject entry(JsonObject registry, String minecraftVersion) {
        if (registry == null || !registry.has("versions") || minecraftVersion == null) return null;
        for (JsonElement version : registry.getAsJsonArray("versions")) {
            JsonObject v = version.getAsJsonObject();
            if (minecraftVersion.equals(v.get("name").getAsString())) return v;
        }
        return null;
    }

    public static boolean isAvailable(String minecraftVersion) {
        return entry(registry(), minecraftVersion) != null;
    }

    /** Vivecraft's libraries (LaunchWrapper for OptiFine's checks, org.json, vecmath) as a version to install. */
    static VersionInfo libraries(JsonObject entry) {
        VersionInfo info = new VersionInfo();
        info.id = "vivecraft-" + entry.get("name").getAsString();
        info.libraries = new Gson().fromJson(entry.get("libraries"), VersionInfo.Library[].class);
        return info;
    }

    /**
     * Makes (or brings up to date) the merged client of {@code minecraft} in {@code gameDir} and installs Vivecraft's
     * libraries.
     *
     * @param clientJar the game's own client.jar
     * @return the class path: the merged client, then Vivecraft's libraries
     */
    public static String install(VersionInfo minecraft, String gameDir, String clientJar) throws Exception {
        JsonObject entry = entry(registry(), minecraft.id);
        if (entry == null) throw new IOException("No Vivecraft for Minecraft " + minecraft.id);
        String version = entry.get("version").getAsString();

        File dir = new File(gameDir, "versions/" + minecraft.id);
        File merged = new File(dir, JAR);
        File stamp = new File(dir, JAR + ".version");
        boolean current = merged.exists() && stamp.exists()
                && version.equals(new String(Files.readAllBytes(stamp.toPath()), StandardCharsets.UTF_8).trim());
        if (!current) {
            File optifine = new File(gameDir, "libraries/optifine/" + minecraft.id + "/" + fileName(entry.getAsJsonObject("optifine")));
            File vivecraft = new File(dir, fileName(entry.getAsJsonObject("vivecraft")));
            download(entry.getAsJsonObject("optifine"), optifine, "OptiFine");
            download(entry.getAsJsonObject("vivecraft"), vivecraft, "Vivecraft");
            Logger.getInstance().appendToLog("Putting Vivecraft " + version + " together with the game and OptiFine");
            merge(new File(clientJar), optifine, vivecraft, merged);
            FileUtil.write(stamp.getAbsolutePath(), version.getBytes(StandardCharsets.UTF_8));
        }

        String libraries = Installer.installLibraries(libraries(entry), gameDir).get();
        return merged.getAbsolutePath() + File.pathSeparator + libraries;
    }

    private static String fileName(JsonObject download) {
        String url = download.get("url").getAsString();
        String name = url.substring(url.lastIndexOf('/') + 1);
        return name.endsWith(".jar.bin") ? name.substring(0, name.length() - 4) : name;
    }

    private static void download(JsonObject download, File file, String what) throws IOException {
        String sha1 = download.get("sha1").getAsString();
        for (int attempt = 0; attempt < 3; attempt++) {
            if (file.exists() && DownloadUtils.compareSHA1(file, sha1)) return;
            Logger.getInstance().appendToLog("Downloading " + what);
            file.delete();
            DownloadUtils.downloadFile(download.get("url").getAsString(), file);
        }
        if (!DownloadUtils.compareSHA1(file, sha1)) throw new IOException(what + " download is not the expected file");
    }

    static boolean isSignature(String name) {
        String upper = name.toUpperCase(Locale.ROOT);
        return upper.startsWith("META-INF/") && (upper.endsWith(".SF") || upper.endsWith(".RSA")
                || upper.endsWith(".DSA") || upper.endsWith(".EC") || upper.equals("META-INF/MANIFEST.MF"));
    }

    /**
     * The game's client, OptiFine over it, Vivecraft over both: of entries with the same name the last one's is kept.
     * Mojang's signature goes, the classes it covers change.
     */
    static void merge(File vanilla, File optifine, File vivecraft, File target) throws IOException {
        File temp = new File(target.getPath() + ".part");
        Set<String> written = new HashSet<>();
        try (ZipOutputStream out = new ZipOutputStream(new BufferedOutputStream(Files.newOutputStream(temp.toPath())))) {
            out.putNextEntry(new ZipEntry("META-INF/MANIFEST.MF"));
            out.write("Manifest-Version: 1.0\r\n\r\n".getBytes(StandardCharsets.UTF_8));
            out.closeEntry();
            byte[] buffer = new byte[65536];
            // the last layer first: an entry already written is one a later layer replaced
            for (File layer : new File[]{vivecraft, optifine, vanilla}) {
                try (ZipFile zip = new ZipFile(layer)) {
                    Enumeration<? extends ZipEntry> entries = zip.entries();
                    while (entries.hasMoreElements()) {
                        ZipEntry entry = entries.nextElement();
                        String name = entry.getName();
                        if (entry.isDirectory() || isSignature(name) || !written.add(name)) continue;
                        out.putNextEntry(new ZipEntry(name));
                        try (InputStream in = zip.getInputStream(entry)) {
                            int n;
                            while ((n = in.read(buffer)) > 0) out.write(buffer, 0, n);
                        }
                        out.closeEntry();
                    }
                }
            }
        }
        if (target.exists() && !target.delete()) throw new IOException("Could not replace " + target);
        if (!temp.renameTo(target)) throw new IOException("Could not write " + target);
    }

    /** The Minecraft versions there is a Vivecraft for, as in the registry. */
    public static String[] versions() {
        JsonObject registry = registry();
        List<String> names = new ArrayList<>();
        if (registry != null && registry.has("versions")) {
            for (JsonElement version : registry.getAsJsonArray("versions")) {
                names.add(version.getAsJsonObject().get("name").getAsString());
            }
        }
        return names.toArray(new String[0]);
    }

    /** The registry: downloaded when there is a connection, else the last one, else the copy in the app. Null if there is none at all. */
    public static JsonObject registry() {
        File file = new File(Constants.USER_HOME + "/vivecraft_legacy.json");
        try {
            DownloadUtils.downloadFile(Constants.PORT_RAW_URL + "/pojlib/" + FILE, file);
        } catch (Throwable offline) {
            if (cached != null) return cached;
        }
        try {
            if (!file.exists()) {
                byte[] bundled = FileUtil.loadFromAssetToByte(Constants.APP_CONTEXT, "loaders/" + FILE);
                FileUtil.write(file.getAbsolutePath(), bundled);
            }
            cached = JsonParser.parseString(new String(Files.readAllBytes(file.toPath()), StandardCharsets.UTF_8)).getAsJsonObject();
        } catch (Throwable t) {
            Logger.getInstance().appendToLog("No list of Vivecraft for old versions: " + t);
        }
        return cached;
    }
}
