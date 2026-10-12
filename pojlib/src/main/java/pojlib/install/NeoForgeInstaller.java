package pojlib.install;

import android.app.Activity;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.ServiceConnection;
import android.os.IBinder;

import com.google.gson.Gson;

import org.apache.commons.io.FileUtils;

import pojlib.APIHandler;
import pojlib.util.download.DownloadManager;
import pojlib.util.download.DownloadUtils;
import pojlib.util.Logger;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

/**
 * Installs NeoForge into a game directory by running the official NeoForge installer.
 * <p>
 * Unlike Fabric, NeoForge is not just a list of libraries: the installer has to patch and remap the
 * Minecraft client (mappings, jar splitting, binary patching) into {@code libraries/net/neoforged/neoforge/} and
 * {@code libraries/net/minecraft/client/}. That is done by running the installer headless
 * ({@code --install-client}) which also downloads the libraries and writes
 * {@code versions/neoforge-<version>/neoforge-<version>.json}.
 */
public class NeoForgeInstaller {
    private static final long TIMEOUT_MINUTES = 30;
    private static final String SUCCESS_MARKER = "Successfully installed client";

    /**
     * Makes sure NeoForge is installed for a Minecraft version, running the installer if necessary.
     *
     * @param minecraftVersionInfo version json of the vanilla version, NeoForge is resolved on top of it
     * @param pinnedVersion        the NeoForge version an existing instance was made with, or null for the newest.
     *                             An instance keeps its version, its saved classpath and arguments belong to it.
     * @return the resolved version (libraries, main class and arguments of vanilla and NeoForge together);
     *         its id is {@code neoforge-<version>}, see {@link NeoForgeMeta#getVersionId}
     * @throws IOException if there is no NeoForge for this version, or it could not be installed
     */
    public static VersionInfo install(Activity activity, VersionInfo minecraftVersionInfo, String gameDir, String pinnedVersion) throws IOException {
        String minecraftVersion = minecraftVersionInfo.id;
        if (!NeoForgeMeta.isSupported(minecraftVersion)) {
            throw new IOException("NeoForge does not exist for Minecraft " + minecraftVersion + ", it starts at 1.20.2");
        }

        String neoForgeVersion = pinnedVersion != null ? pinnedVersion : NeoForgeMeta.getLatestVersion(minecraftVersion);
        if (neoForgeVersion == null) {
            throw new IOException("Could not find a NeoForge version for Minecraft " + minecraftVersion + ". Are you offline?");
        }
        Logger.getInstance().appendToLog("Using NeoForge " + neoForgeVersion);

        File versionJson = versionJson(gameDir, neoForgeVersion);
        if (!isInstalled(gameDir, neoForgeVersion)) {
            File installer = downloadInstaller(neoForgeVersion, gameDir);
            runInstaller(activity, installer, gameDir);

            if (!isInstalled(gameDir, neoForgeVersion)) {
                throw new IOException("NeoForge " + neoForgeVersion + " failed to install:\n" + tail(serviceLogOf(installer), 12));
            }
            installer.delete();
            logOf(installer).delete();
            serviceLogOf(installer).delete();
        }

        VersionInfo neoForge = new Gson().fromJson(FileUtils.readFileToString(versionJson, StandardCharsets.UTF_8), VersionInfo.class);
        return VersionInfo.merge(minecraftVersionInfo, neoForge);
    }

    /** The installer writes the version json first and the patched client last, so the client decides. */
    static boolean isInstalled(String gameDir, String neoForgeVersion) {
        return versionJson(gameDir, neoForgeVersion).exists()
                && new File(gameDir + "/libraries/net/neoforged/neoforge/" + neoForgeVersion
                + "/neoforge-" + neoForgeVersion + "-client.jar").exists();
    }

    private static File versionJson(String gameDir, String neoForgeVersion) {
        String id = NeoForgeMeta.getVersionId(neoForgeVersion);
        return new File(gameDir + "/versions/" + id + "/" + id + ".json");
    }

    private static File downloadInstaller(String neoForgeVersion, String gameDir) throws IOException {
        File installer = new File(gameDir + "/installers/neoforge-" + neoForgeVersion + "-installer.jar");
        String url = NeoForgeMeta.getInstallerUrl(neoForgeVersion);
        String sha1 = APIHandler.getRaw(url + ".sha1");

        for (int i = 0; i < 3; i++) {
            if (!installer.exists()) {
                Logger.getInstance().appendToLog("Downloading NeoForge installer");
                DownloadUtils.downloadFile(url, installer);
                DownloadManager.reset();
            }
            if (DownloadUtils.compareSHA1(installer, sha1)) return installer;
            installer.delete();
        }
        throw new IOException("NeoForge installer download is corrupt: " + url);
    }

    private static void runInstaller(Activity activity, File installer, String gameDir) throws IOException {
        // the installer refuses to run without a launcher profile it can add itself to
        File profiles = new File(gameDir + "/launcher_profiles.json");
        if (!profiles.exists()) {
            FileUtils.writeStringToFile(profiles, "{\"profiles\":{}}", StandardCharsets.UTF_8);
        }
        logOf(installer).delete();
        serviceLogOf(installer).delete();

        // the JRE is downloaded on first use, the installer is the first thing that needs it
        Installer.installJVM(activity, false);

        Logger.getInstance().appendToLog("Installing NeoForge, this takes a few minutes");
        final CountDownLatch finished = new CountDownLatch(1);
        ServiceConnection connection = new ServiceConnection() {
            @Override
            public void onServiceConnected(ComponentName name, IBinder service) {}

            // the installer process ended, either by itself (System.exit) or because it crashed
            @Override
            public void onServiceDisconnected(ComponentName name) {
                finished.countDown();
            }

            @Override
            public void onBindingDied(ComponentName name) {
                finished.countDown();
            }
        };

        Intent intent = new Intent(activity, NeoForgeInstallService.class)
                .putExtra(NeoForgeInstallService.EXTRA_INSTALLER, installer.getAbsolutePath())
                .putExtra(NeoForgeInstallService.EXTRA_GAME_DIR, gameDir);
        if (!activity.bindService(intent, connection, Context.BIND_AUTO_CREATE)) {
            throw new IOException("Could not start the NeoForge installer process");
        }

        try {
            if (!finished.await(TIMEOUT_MINUTES, TimeUnit.MINUTES)) {
                throw new IOException("NeoForge installer timed out after " + TIMEOUT_MINUTES + " minutes");
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IOException("Interrupted while installing NeoForge", e);
        } finally {
            activity.unbindService(connection);
        }

        // The installer prints to its stdout, which the installer process wrote to the service log (a file of its own
        // is only written by some installer versions), so either one can hold the success line.
        File log = logOf(installer);
        File serviceLog = serviceLogOf(installer);
        if (!contains(log, SUCCESS_MARKER) && !contains(serviceLog, SUCCESS_MARKER)) {
            String why = log.exists() ? tail(log, 12) : tail(serviceLog, 12);
            if (!log.exists() && !serviceLog.exists()) {
                // the installer never wrote anything: say how Android ended its process
                why = "(no installer output)\n" + exitReason(activity);
            }
            throw new IOException("NeoForge installer did not finish:\n" + why);
        }
    }

    private static boolean contains(File file, String text) {
        try {
            return file.exists() && FileUtils.readFileToString(file, StandardCharsets.UTF_8).contains(text);
        } catch (IOException e) {
            return false;
        }
    }

    private static File serviceLogOf(File installer) {
        return new File(installer.getAbsolutePath() + NeoForgeInstallService.SERVICE_LOG_SUFFIX);
    }

    /** Why Android says the installer process ended last (a crash, a signal, low memory, ...), if it says. */
    private static String exitReason(Context context) {
        try {
            android.app.ActivityManager am = (android.app.ActivityManager) context.getSystemService(Context.ACTIVITY_SERVICE);
            for (android.app.ApplicationExitInfo info : am.getHistoricalProcessExitReasons(context.getPackageName(), 0, 10)) {
                if (info.getProcessName() != null && info.getProcessName().endsWith(":neoforge_installer")) {
                    return "Android: process ended with reason " + info.getReason() + ", status " + info.getStatus()
                            + (info.getDescription() != null ? ", " + info.getDescription() : "");
                }
            }
            return "Android: no record of the installer process ending";
        } catch (Throwable t) {
            return "Android: exit reason not available (" + t + ")";
        }
    }

    /** The installer writes its log next to itself. */
    private static File logOf(File installer) {
        return new File(installer.getAbsolutePath() + ".log");
    }

    private static String tail(File file, int lines) {
        try {
            List<String> all = FileUtils.readLines(file, StandardCharsets.UTF_8);
            return String.join("\n", all.subList(Math.max(0, all.size() - lines), all.size()));
        } catch (IOException e) {
            return "(nothing written)";
        }
    }
}
