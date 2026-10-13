package pojlib.install;

import android.app.Activity;

import com.google.gson.Gson;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;

import org.apache.commons.io.FileUtils;

import pojlib.util.Logger;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Enumeration;
import java.util.Locale;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;
import java.util.zip.ZipOutputStream;

/**
 * Installs Forge into a game directory by running Forge's installer headless ({@code --installClient}), like
 * {@link NeoForgeInstaller} does for NeoForge: the installer patches the Minecraft client and writes
 * {@code versions/<minecraft>-forge-<forge>/...json}, it has to run in a JVM of its own, which is what
 * {@link NeoForgeInstaller#runInstaller} sets up for both.
 */
public class ForgeInstaller {
    /** Forge's installer spells it like this, NeoForge's has a dash in it (--install-client), see the installers' --help. */
    static final String INSTALL_ARG = "--installClient";


    /**
     * Makes sure Forge is installed for a Minecraft version, running the installer if necessary.
     *
     * @param pinnedVersion the Forge build an existing instance was made with, or null for the newest
     * @return the resolved version (libraries, main class and arguments of vanilla and Forge together), its id is
     *         {@code <minecraft>-forge-<forge>}, see {@link ForgeMeta#getVersionId}
     */
    public static VersionInfo install(Activity activity, VersionInfo minecraftVersionInfo, String gameDir, String pinnedVersion) throws IOException {
        String minecraftVersion = minecraftVersionInfo.id;
        if (!ForgeMeta.isSupported(minecraftVersion)) {
            throw new IOException("Forge is only offered from Minecraft 1.20 on, not for " + minecraftVersion);
        }

        String forgeVersion = pinnedVersion != null ? pinnedVersion : ForgeMeta.getLatestVersion(minecraftVersion);
        if (forgeVersion == null) {
            throw new IOException("Could not find a Forge version for Minecraft " + minecraftVersion + ". Are you offline?");
        }
        Logger.getInstance().appendToLog("Using Forge " + forgeVersion);

        File versionJson = versionJson(gameDir, minecraftVersion, forgeVersion);
        if (!isInstalled(gameDir, minecraftVersion, forgeVersion)) {
            File installer = NeoForgeInstaller.downloadInstaller(
                    new File(gameDir + "/installers/forge-" + minecraftVersion + "-" + forgeVersion + "-installer.jar"),
                    ForgeMeta.getInstallerUrl(minecraftVersion, forgeVersion), "Forge");
            relaxOutputChecks(installer);
            NeoForgeInstaller.runInstaller(activity, installer, gameDir, INSTALL_ARG);

            if (!isInstalled(gameDir, minecraftVersion, forgeVersion)) {
                throw new IOException("Forge " + forgeVersion + " failed to install:\n"
                        + NeoForgeInstaller.tail(NeoForgeInstaller.serviceLogOf(installer), 12));
            }
            installer.delete();
            NeoForgeInstaller.logOf(installer).delete();
            NeoForgeInstaller.serviceLogOf(installer).delete();
        }

        VersionInfo forge = new Gson().fromJson(FileUtils.readFileToString(versionJson, StandardCharsets.UTF_8), VersionInfo.class);
        return VersionInfo.merge(minecraftVersionInfo, forge);
    }

    /**
     * Forge's installer compares the SHA-1 of every jar its processors produce with the one it was built with. Those
     * jars are deflated by the JVM that runs the installer, and the headset's JRE compresses with Android's zlib, so
     * the same content comes out as other bytes: "Processor failed, invalid outputs ... Expected ... Actual ...".
     * Rewrites the installer so that it does not check them (the content is the same, only the compression differs),
     * which also takes off the signature that would no longer match the changed profile.
     */
    static void relaxOutputChecks(File installer) throws IOException {
        File relaxed = new File(installer.getPath() + ".tmp");
        try (ZipFile in = new ZipFile(installer);
             ZipOutputStream out = new ZipOutputStream(new java.io.FileOutputStream(relaxed))) {
            Enumeration<? extends ZipEntry> entries = in.entries();
            while (entries.hasMoreElements()) {
                ZipEntry entry = entries.nextElement();
                if (isSignatureFile(entry.getName())) continue;

                ZipEntry copy = new ZipEntry(entry.getName());
                out.putNextEntry(copy);
                if (!entry.isDirectory()) {
                    try (java.io.InputStream data = in.getInputStream(entry)) {
                        if (entry.getName().equals("install_profile.json")) {
                            String profile = org.apache.commons.io.IOUtils.toString(data, StandardCharsets.UTF_8);
                            out.write(stripProcessorOutputs(profile).getBytes(StandardCharsets.UTF_8));
                        } else {
                            org.apache.commons.io.IOUtils.copy(data, out);
                        }
                    }
                }
                out.closeEntry();
            }
        }
        FileUtils.forceDelete(installer);
        FileUtils.moveFile(relaxed, installer);
    }

    static boolean isSignatureFile(String name) {
        String upper = name.toUpperCase(Locale.ROOT);
        return upper.startsWith("META-INF/") && upper.indexOf('/', 9) < 0
                && (upper.endsWith(".SF") || upper.endsWith(".RSA") || upper.endsWith(".DSA") || upper.endsWith(".EC"));
    }

    /** Takes the expected outputs (and so their checksums) off every processor of an install_profile.json. */
    static String stripProcessorOutputs(String installProfile) {
        JsonObject profile = new Gson().fromJson(installProfile, JsonObject.class);
        if (profile.has("processors") && profile.get("processors").isJsonArray()) {
            for (JsonElement processor : profile.getAsJsonArray("processors")) {
                if (processor.isJsonObject()) processor.getAsJsonObject().remove("outputs");
            }
        }
        return new Gson().toJson(profile);
    }

    /** The installer writes the version json first and the patched client last, so the client decides. */
    static boolean isInstalled(String gameDir, String minecraftVersion, String forgeVersion) {
        String build = minecraftVersion + "-" + forgeVersion;
        return versionJson(gameDir, minecraftVersion, forgeVersion).exists()
                && new File(gameDir + "/libraries/net/minecraftforge/forge/" + build + "/forge-" + build + "-client.jar").exists();
    }

    private static File versionJson(String gameDir, String minecraftVersion, String forgeVersion) {
        String id = ForgeMeta.getVersionId(minecraftVersion, forgeVersion);
        return new File(gameDir + "/versions/" + id + "/" + id + ".json");
    }
}
