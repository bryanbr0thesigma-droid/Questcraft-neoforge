package pojlib.install;

import android.content.Context;

import com.github.junrar.Archive;
import com.github.junrar.rarfile.FileHeader;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;

import org.apache.commons.codec.digest.DigestUtils;

import java.io.BufferedOutputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.io.Reader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.Enumeration;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;
import java.util.zip.ZipOutputStream;

import pojlib.util.FileUtil;
import pojlib.util.Logger;
import pojlib.util.download.DownloadUtils;

/**
 * Risugami's ModLoader, for Minecraft alpha 1.1 to 1.6.2. It is not a mod but classes put into the game's client, the
 * way its readme said to install it, and it loads mods from {@code mods/} itself. The game then starts through
 * QuestCraft's legacy launcher ({@code legacy-launcher/}), which runs these versions on the headset's Java.
 * <p>
 * {@code modloader-builds.json} lists, per Minecraft version, the newest ModLoader build and where to download it, as
 * MCArchive (mcarchive.net) lists them: MCArchive's own copy when it has one, else Risugami's Mediafire page, the file
 * checked against MCArchive's SHA-256 either way. Builds for alpha and early beta are RAR archives.
 */
public final class RisugamiModLoader {
    public static final String FILE = "modloader-builds.json";
    /** The game's client with ModLoader in it, next to client.jar */
    public static final String JAR = "client-modloader.jar";
    /** The legacy launcher: in the app, and where it is put to be started */
    public static final String LAUNCHER_ASSET = "legacy/questcraft-legacy-launcher.jar";
    public static final String LAUNCHER_MAIN = "questcraft.legacy.LegacyLauncher";
    /** The game class whose main() starts versions before 1.6 (Mojang's version file has LaunchWrapper's) */
    static final String OLD_MAIN = "net.minecraft.client.Minecraft";
    static final String MAIN_1_6 = "net.minecraft.client.main.Main";
    /** What the game and its mods, made for Java 8, reach into in Java's own classes */
    static final String[] OPENS = {"java.base/java.lang", "java.base/java.lang.reflect", "java.base/java.net",
            "java.base/java.nio", "java.base/java.util"};

    private static final HostedList LIST = new HostedList(FILE, "modloader_builds.json");
    /** A desktop browser's: Mediafire gives Android browsers a mobile page without the file's address on it */
    static final String PAGE_USER_AGENT = "Mozilla/5.0 (X11; Linux x86_64)";
    private static final Pattern MEDIAFIRE_FILE = Pattern.compile("href=\"(https?://download\\d+\\.mediafire\\.com/[^\"]+)\"");

    private RisugamiModLoader() {
    }

    /** The list of builds: downloaded when there is a connection, else the last one, else the app's copy. */
    public static JsonObject registry() {
        return LIST.get();
    }

    public static boolean isAvailable(String minecraftVersion) {
        return HostedList.entry(registry(), minecraftVersion) != null;
    }

    /** The Minecraft versions there is a ModLoader for, as in the list. */
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

    /** The class whose main() starts the game: Main from 1.6, Minecraft before (where Mojang's file names LaunchWrapper). */
    static String gameMain(VersionInfo minecraft) {
        return MAIN_1_6.equals(minecraft.mainClass) ? MAIN_1_6 : OLD_MAIN;
    }

    /**
     * Makes (or brings up to date) the client of {@code minecraft} with ModLoader in it, in {@code gameDir}.
     *
     * @param clientJar the game's own client.jar
     * @return the merged client
     */
    public static String install(VersionInfo minecraft, String gameDir, String clientJar) throws Exception {
        JsonObject entry = HostedList.entry(registry(), minecraft.id);
        if (entry == null) throw new IOException("No ModLoader for Minecraft " + minecraft.id);
        String sha256 = entry.get("sha256").getAsString();

        File dir = new File(gameDir, "versions/" + minecraft.id);
        File merged = new File(dir, JAR);
        File stamp = new File(dir, JAR + ".version");
        boolean current = merged.exists() && stamp.exists()
                && sha256.equals(new String(Files.readAllBytes(stamp.toPath()), StandardCharsets.UTF_8).trim());
        if (!current) {
            File archive = new File(gameDir, "libraries/modloader/" + minecraft.id + "/" + entry.get("file").getAsString());
            download(entry, archive);
            Logger.getInstance().appendToLog("Putting " + entry.get("file").getAsString() + " into Minecraft " + minecraft.id);
            merge(new File(clientJar), archive, merged);
            FileUtil.write(stamp.getAbsolutePath(), sha256.getBytes(StandardCharsets.UTF_8));
        }
        return merged.getAbsolutePath();
    }

    /** The legacy launcher from the app, written where the game is started from. Every time: an app update brings a new one. */
    public static String installLauncher(Context context, String launcherDir) throws IOException {
        byte[] jar = FileUtil.loadFromAssetToByte(context, LAUNCHER_ASSET);
        if (jar == null) throw new IOException("The app has no " + LAUNCHER_ASSET);
        File target = new File(launcherDir, LAUNCHER_ASSET);
        if (!target.exists() || !FileUtil.matchingAssetFile(target, jar)) {
            FileUtil.write(target.getAbsolutePath(), jar);
        }
        return target.getAbsolutePath();
    }

    /**
     * The arguments that make the legacy launcher start the game: it is the program, the game (its client and
     * libraries) is loaded by it from {@code gameClasspath}.
     */
    public static String[] jvmArgs(VersionInfo minecraft, String gameClasspath) {
        List<String> args = new ArrayList<>();
        for (String open : OPENS) {
            args.add("--add-opens");
            args.add(open + "=ALL-UNNAMED");
        }
        args.add("-Dquestcraft.legacy.classpath=" + gameClasspath);
        args.add("-Dquestcraft.legacy.main=" + gameMain(minecraft));
        return args.toArray(new String[0]);
    }

    private static void download(JsonObject entry, File target) throws IOException {
        String sha256 = entry.get("sha256").getAsString();
        String name = entry.get("file").getAsString();
        IOException last = null;
        for (int attempt = 0; attempt < 2; attempt++) {
            // MCArchive's copy first, Risugami's Mediafire page when there is none or it fails
            for (String source : new String[]{"url", "mediafire"}) {
                if (target.exists() && sha256.equalsIgnoreCase(sha256(target))) return;
                if (!entry.has(source)) continue;
                target.delete();
                try {
                    String address = entry.get(source).getAsString();
                    if (source.equals("mediafire")) address = mediafireLink(readPage(address));
                    if (address == null) throw new IOException("no download address on the Mediafire page of " + name);
                    Logger.getInstance().appendToLog("Downloading " + name);
                    DownloadUtils.downloadFile(address, target);
                } catch (IOException e) {
                    last = e;
                }
            }
        }
        if (target.exists() && sha256.equalsIgnoreCase(sha256(target))) return;
        target.delete();
        IOException failure = new IOException("Could not download " + name + (last != null ? ": " + last.getMessage() : " (not the expected file)"));
        if (last != null) failure.initCause(last);
        throw failure;
    }

    /** The file's address on a Mediafire download page, over https (the page gives http, which Android refuses). */
    static String mediafireLink(String page) {
        if (page == null) return null;
        Matcher matcher = MEDIAFIRE_FILE.matcher(page);
        return matcher.find() ? matcher.group(1).replaceFirst("^http://", "https://") : null;
    }

    private static String readPage(String address) throws IOException {
        HttpURLConnection connection = (HttpURLConnection) new URL(address).openConnection();
        connection.setRequestProperty("User-Agent", PAGE_USER_AGENT);
        connection.setConnectTimeout(15000);
        connection.setReadTimeout(30000);
        try (Reader in = new InputStreamReader(connection.getInputStream(), StandardCharsets.UTF_8)) {
            StringBuilder page = new StringBuilder();
            char[] buffer = new char[8192];
            for (int n; (n = in.read(buffer)) > 0; ) page.append(buffer, 0, n);
            return page.toString();
        } finally {
            connection.disconnect();
        }
    }

    static String sha256(File file) throws IOException {
        try (InputStream in = Files.newInputStream(file.toPath())) {
            return DigestUtils.sha256Hex(in);
        }
    }

    /**
     * The files of a ModLoader build (a zip or a RAR archive) by name, relative to the folder ModLoader.class is in
     * (the archive's root in every build, but a repacked one may have a folder around it).
     */
    static Map<String, byte[]> readArchive(File archive) throws IOException {
        Map<String, byte[]> files = new LinkedHashMap<>();
        if (archive.getName().toLowerCase(Locale.ROOT).endsWith(".rar")) {
            try (Archive rar = new Archive(archive)) {
                for (FileHeader header : rar.getFileHeaders()) {
                    if (header.isDirectory()) continue;
                    ByteArrayOutputStream out = new ByteArrayOutputStream();
                    rar.extractFile(header, out);
                    files.put(header.getFileName().replace('\\', '/'), out.toByteArray());
                }
            } catch (IOException e) {
                throw e;
            } catch (Exception e) {
                throw new IOException("Could not read " + archive.getName() + ": " + e, e);
            }
        } else {
            try (ZipFile zip = new ZipFile(archive)) {
                Enumeration<? extends ZipEntry> entries = zip.entries();
                while (entries.hasMoreElements()) {
                    ZipEntry entry = entries.nextElement();
                    if (entry.isDirectory()) continue;
                    try (InputStream in = zip.getInputStream(entry)) {
                        ByteArrayOutputStream out = new ByteArrayOutputStream();
                        byte[] buffer = new byte[65536];
                        for (int n; (n = in.read(buffer)) > 0; ) out.write(buffer, 0, n);
                        files.put(entry.getName(), out.toByteArray());
                    }
                }
            }
        }
        String root = null;
        for (String name : files.keySet()) {
            if (name.equals("ModLoader.class") || name.endsWith("/ModLoader.class")) {
                root = name.substring(0, name.length() - "ModLoader.class".length());
                break;
            }
        }
        if (root == null) throw new IOException(archive.getName() + " has no ModLoader.class");
        Map<String, byte[]> relative = new LinkedHashMap<>();
        for (Map.Entry<String, byte[]> file : files.entrySet()) {
            if (file.getKey().startsWith(root)) relative.put(file.getKey().substring(root.length()), file.getValue());
        }
        return relative;
    }

    /**
     * The game's client with ModLoader's classes over it: ModLoader replaces some of the game's classes. Mojang's
     * signature goes, the classes it covers change.
     */
    static void merge(File vanilla, File modLoader, File target) throws IOException {
        Map<String, byte[]> modLoaderFiles = readArchive(modLoader);
        File temp = new File(target.getPath() + ".part");
        target.getParentFile().mkdirs();
        Set<String> written = new HashSet<>();
        try (ZipOutputStream out = new ZipOutputStream(new BufferedOutputStream(Files.newOutputStream(temp.toPath())))) {
            out.putNextEntry(new ZipEntry("META-INF/MANIFEST.MF"));
            out.write("Manifest-Version: 1.0\r\n\r\n".getBytes(StandardCharsets.UTF_8));
            out.closeEntry();
            written.add("META-INF/MANIFEST.MF");
            for (Map.Entry<String, byte[]> file : modLoaderFiles.entrySet()) {
                if (LegacyVivecraft.isSignature(file.getKey()) || !written.add(file.getKey())) continue;
                out.putNextEntry(new ZipEntry(file.getKey()));
                out.write(file.getValue());
                out.closeEntry();
            }
            byte[] buffer = new byte[65536];
            try (ZipFile zip = new ZipFile(vanilla)) {
                Enumeration<? extends ZipEntry> entries = zip.entries();
                while (entries.hasMoreElements()) {
                    ZipEntry entry = entries.nextElement();
                    String name = entry.getName();
                    if (entry.isDirectory() || LegacyVivecraft.isSignature(name) || !written.add(name)) continue;
                    out.putNextEntry(new ZipEntry(name));
                    try (InputStream in = zip.getInputStream(entry)) {
                        copy(in, out, buffer);
                    }
                    out.closeEntry();
                }
            }
        }
        if (target.exists() && !target.delete()) throw new IOException("Could not replace " + target);
        if (!temp.renameTo(target)) throw new IOException("Could not write " + target);
    }

    private static void copy(InputStream in, OutputStream out, byte[] buffer) throws IOException {
        for (int n; (n = in.read(buffer)) > 0; ) out.write(buffer, 0, n);
    }
}
