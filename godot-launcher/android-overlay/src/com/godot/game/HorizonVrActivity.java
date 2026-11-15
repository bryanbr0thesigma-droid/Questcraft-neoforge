package com.godot.game;

import android.app.ActivityManager;
import android.os.Bundle;
import android.os.SystemClock;
import android.util.Log;
import java.util.List;

/**
 * Horizon in VR: the same as HorizonActivity, but the headset starts it as an immersive app (the manifest's VR
 * category), so the game's OpenXR session (horizon-quest's XR core) shows it in the headset instead of a panel. The
 * launcher starts it for an instance in VR (its command line has "--vr").
 */
public class HorizonVrActivity extends HorizonActivity {
    /**
     * The eyes' images at this share of the size the headset recommends (the XR core's HORIZON_XR_SCALE): Horizon
     * draws the whole frame once per eye, and at the full 1680x1760 of a Quest 3 that is six times the pixels of its
     * panel, twice.
     */
    private static final String EYE_SCALE = "0.75";

    /** How long to wait at most for the launcher's process to end, and then for the runtime to let its session go. */
    private static final long LAUNCHER_GONE_MS = 5000;
    private static final long SESSION_GONE_MS = 2000;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        waitForLauncher();
        try {
            android.system.Os.setenv("HORIZON_XR_SCALE", EYE_SCALE, true);
        } catch (Exception e) {
            Log.e("QuestCraftHorizon", "Could not set the eyes' size", e);
        }
        super.onCreate(savedInstanceState);
    }

    /**
     * The launcher (Godot, in the app's main process) holds an OpenXR session until its process ends, which it ends
     * itself once it has started the game (QuestCraftPlugin). A session the game starts while that one is still
     * there can stay hidden behind it: a black headset, the game's picture showing only as it closes. So the game's
     * native side, which starts its session, starts once the launcher's process is gone.
     */
    private void waitForLauncher() {
        ActivityManager manager = (ActivityManager) getSystemService(ACTIVITY_SERVICE);
        if (manager == null) return;
        String launcher = getPackageName();
        long until = SystemClock.uptimeMillis() + LAUNCHER_GONE_MS;
        boolean waited = false;
        while (SystemClock.uptimeMillis() < until) {
            boolean alive = false;
            List<ActivityManager.RunningAppProcessInfo> processes = manager.getRunningAppProcesses();
            if (processes != null) {
                for (ActivityManager.RunningAppProcessInfo process : processes) {
                    if (launcher.equals(process.processName)) {
                        alive = true;
                        break;
                    }
                }
            }
            if (!alive) break;
            waited = true;
            SystemClock.sleep(50);
        }
        // and the headset's runtime to let its session go (the Java game starts its own much later)
        SystemClock.sleep(SESSION_GONE_MS);
        Log.i("QuestCraftHorizon", waited ? "Started once the launcher had ended" : "The launcher had already ended");
    }
}
