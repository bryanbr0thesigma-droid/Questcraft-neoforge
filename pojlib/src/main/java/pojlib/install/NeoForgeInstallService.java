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

    private static final String TAG = "NeoForgeInstaller";

    private boolean started = false;

    @Override
    public synchronized IBinder onBind(Intent intent) {
        if (!started) {
            started = true;
            final String installer = intent.getStringExtra(EXTRA_INSTALLER);
            final String gameDir = intent.getStringExtra(EXTRA_GAME_DIR);
            new Thread(() -> run(installer, gameDir), "neoforge-installer").start();
        }
        return new Binder();
    }

    private void run(String installer, String gameDir) {
        try {
            int exitCode = JREUtils.launchInstallerVM(this,
                    Arrays.asList("-jar", installer, "--install-client", gameDir), gameDir);
            Log.i(TAG, "Installer returned with exit code " + exitCode);
        } catch (Throwable t) {
            Log.e(TAG, "Installer failed to run", t);
        }
        // the installer only returns if it did not exit by itself, the JVM is of no use to anyone anymore
        android.os.Process.killProcess(android.os.Process.myPid());
    }
}
