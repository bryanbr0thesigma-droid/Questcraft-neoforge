package com.godot.game;

import android.app.Activity;
import android.os.Bundle;
import android.os.Process;
import android.util.Log;
import android.view.View;
import android.view.WindowManager;

import org.json.JSONObject;

import pojlib.LauncherApi;

/**
 * Runs Minecraft. It lives in a process of its own (see the manifest), next to the launcher's: Godot keeps its OpenXR
 * session for as long as its process lives, and Minecraft (Vivecraft) needs the headset for a session of its own. The
 * launcher's activity just pauses while this one is in front, and is there again with its menu when the game ends.
 */
public class GameActivity extends Activity {
    private static final String TAG = "QuestCraftGame";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        setContentView(new View(this));   // nothing to show: the game draws into the headset's own session
        final String instance = getIntent().getStringExtra("instance");
        final String account = getIntent().getStringExtra("account");
        new Thread(() -> run(instance, account), "qc-game-launch").start();
    }

    private void run(String instance, String account) {
        try {
            String error = LauncherApi.init(this);
            if (!error.isEmpty()) throw new IllegalStateException("Pojlib: " + error);

            LauncherApi.login(account);
            waitFor(() -> new JSONObject(LauncherApi.loginState()).optBoolean("loggedIn"), 120, "the sign in");

            LauncherApi.prepare(instance);   // quick: the launcher has just done the downloads
            waitFor(() -> {
                JSONObject progress = new JSONObject(LauncherApi.progress());
                String phase = progress.optString("phase");
                if ("error".equals(phase)) throw new IllegalStateException(progress.optString("error"));
                return "ready".equals(phase);
            }, 900, "preparing " + instance);

            LauncherApi.runGameBlocking();
        } catch (Throwable t) {
            Log.e(TAG, "The game did not start", t);
        } finally {
            // back to the launcher: close this activity and end this process (the launcher's lives on)
            runOnUiThread(this::finishAndRemoveTask);
            try {
                Thread.sleep(500);
            } catch (InterruptedException ignored) {
            }
            Process.killProcess(Process.myPid());
        }
    }

    private interface Check {
        boolean done() throws Exception;
    }

    private static void waitFor(Check check, int seconds, String what) throws Exception {
        long end = System.currentTimeMillis() + seconds * 1000L;
        while (!check.done()) {
            if (System.currentTimeMillis() > end) throw new IllegalStateException("Timed out waiting for " + what);
            Thread.sleep(300);
        }
    }

    @Override
    public void onBackPressed() {
        // the game has its own way to quit
    }
}
