package pojlib.install;

import android.app.Service;
import android.content.Intent;
import android.os.Binder;
import android.os.IBinder;
import android.util.Log;

import pojlib.util.JREUtils;

import java.util.Arrays;

/**
 * Runs the NeoForge installer in its own Android process ({@code :neoforge_installer}, see the manifest).
 * <p>
 * The installer has to run in a real JVM, and a process can only ever create one of those. The game needs the one of
 * the main process, so the installer gets a process of its own. The installer ends by calling {@code System.exit},
 * which ends this process too, that is how {@link NeoForgeInstaller} knows it is done (the binding dies).
 */
public class NeoForgeInstallService extends Service {
    public static final String EXTRA_INSTALLER = "installer";
    public static final String EXTRA_GAME_DIR = "gameDir";
    /** The option that makes the installer install the client: NeoForge calls it --install-client, Forge --installClient. */
    public static final String EXTRA_INSTALL_ARG = "installArg";

    /** Next to the installer's own log: what this process itself did and printed, see {@link NeoForgeInstaller} */
    public static final String SERVICE_LOG_SUFFIX = ".service.log";

    private static final String TAG = "NeoForgeInstaller";

    private static void note(String file, String text) {
        try (java.io.FileWriter w = new java.io.FileWriter(file, true)) {
            w.write("[installer process] " + text + "\n");
        } catch (java.io.IOException ignored) {
        }
    }

    private boolean started = false;

    @Override
    public synchronized IBinder onBind(Intent intent) {
        if (!started) {
            started = true;
            final String installer = intent.getStringExtra(EXTRA_INSTALLER);
            final String gameDir = intent.getStringExtra(EXTRA_GAME_DIR);
            final String installArg = intent.hasExtra(EXTRA_INSTALL_ARG) ? intent.getStringExtra(EXTRA_INSTALL_ARG) : "--install-client";
            new Thread(() -> run(installer, gameDir, installArg), "neoforge-installer").start();
        }
        return new Binder();
    }

    private void run(String installer, String gameDir, String installArg) {
        // The statics of the main process (Pojlib's home directory, which the logger writes into) do not exist in this
        // process. Give it a directory of its own so the logger works and does not touch the log of the main process.
        java.io.File logDir = new java.io.File(gameDir, "installers");
        logDir.mkdirs();
        pojlib.util.Constants.USER_HOME = logDir.getAbsolutePath();
        final String serviceLog = installer + SERVICE_LOG_SUFFIX;
        try {
            // what the JVM prints (it reports why it cannot start on stderr) goes to the service log as well
            java.io.FileDescriptor fd = android.system.Os.open(serviceLog,
                    android.system.OsConstants.O_WRONLY | android.system.OsConstants.O_CREAT | android.system.OsConstants.O_APPEND, 0644);
            android.system.Os.dup2(fd, 1);
            android.system.Os.dup2(fd, 2);
        } catch (Throwable t) {
            Log.e(TAG, "Could not redirect the output", t);
        }
        note(serviceLog, "service started, starting the JVM");
        try {
            int exitCode = JREUtils.launchInstallerVM(this,
                    Arrays.asList("-jar", installer, installArg, gameDir), gameDir);
            Log.i(TAG, "Installer returned with exit code " + exitCode);
            note(serviceLog, "the JVM returned with exit code " + exitCode);
        } catch (Throwable t) {
            Log.e(TAG, "Installer failed to run", t);
            note(serviceLog, "the installer could not be started:\n" + Log.getStackTraceString(t));
        }
        // the installer only returns if it did not exit by itself, the JVM is of no use to anyone anymore
        android.os.Process.killProcess(android.os.Process.myPid());
    }
}
