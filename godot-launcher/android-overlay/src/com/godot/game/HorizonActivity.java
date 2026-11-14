package com.godot.game;

import android.app.NativeActivity;
import android.content.Intent;
import android.os.Bundle;
import android.os.Process;
import android.util.Log;

import java.io.File;
import java.io.FileOutputStream;
import java.nio.charset.StandardCharsets;

/**
 * Runs Horizon (Minecraft 26.3 rewritten in Rust, see pojlib.install.HorizonInstaller): Android's NativeActivity
 * loads libhorizon_quest.so (the manifest's {@code android.app.lib_name}), whose {@code android_main} is the game.
 * Like GameActivity it has a process of its own, and the launcher, whose process ended when the game started, is
 * started again when it ends.
 * <p>
 * The library has no command line: its arguments, which the launcher worked out (LauncherApi.horizonArgs), go to it
 * in {@code horizon-args.txt} in the app's files, one per line, written before the library starts.
 */
public class HorizonActivity extends NativeActivity {
    private static final String TAG = "QuestCraftHorizon";
    static final String ARGS = "args";

    /**
     * Stack for the threads Rust starts without a size of their own, android_main among them: the thread the game
     * runs on, which the glue (android-activity) starts with Rust's default of 2 MB. The game needs the 8 MB a main
     * thread has on a desktop (Horizon asks for 8 MB on Windows too); with 2 MB it overflowed its stack as soon as it
     * got going (SIGSEGV, "likely due to stack overflow" in android_main). Twice that, to be safe.
     */
    private static final int STACK = 16 << 20;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        // before the library loads: Rust reads it when it starts its first thread
        try {
            android.system.Os.setenv("RUST_MIN_STACK", String.valueOf(STACK), true);
        } catch (Exception e) {
            Log.e(TAG, "Could not set Horizon's stack size", e);
        }
        String[] args = getIntent().getStringArrayExtra(ARGS);
        if (args != null) {
            try (FileOutputStream out = new FileOutputStream(new File(getFilesDir(), "horizon-args.txt"))) {
                out.write(String.join("\n", args).getBytes(StandardCharsets.UTF_8));
            } catch (Exception e) {
                Log.e(TAG, "Could not hand Horizon its arguments", e);
            }
        }
        super.onCreate(savedInstanceState);
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        // the game ended (it finishes the activity when its loop is over) or was closed: back to the launcher's menu
        try {
            Intent launcher = getPackageManager().getLaunchIntentForPackage(getPackageName());
            launcher.putExtra(GameActivity.FROM_GAME, true);
            launcher.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
            startActivity(launcher);
        } catch (Throwable t) {
            Log.e(TAG, "Could not start the launcher again", t);
        }
        Process.killProcess(Process.myPid());
    }
}
