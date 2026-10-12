package pojlib.install;

import android.app.Activity;

import com.google.gson.Gson;

import org.apache.commons.io.FileUtils;

import pojlib.util.Logger;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;

/**
 * Installs Forge into a game directory by running Forge's installer headless ({@code --installClient}), like
 * {@link NeoForgeInstaller} does for NeoForge: the installer patches the Minecraft client and writes
 * {@code versions/<minecraft>-forge-<forge>/...json}, it has to run in a JVM of its own, which is what
 * {@link NeoForgeInstaller#runInstaller} sets up for both.
 */
public class ForgeInstaller {

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
            NeoForgeInstaller.runInstaller(activity, installer, gameDir);

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
