package com.godot.game;

import android.app.Activity;
import android.content.Intent;
import android.os.Bundle;
import android.os.Process;
import android.util.Log;
import android.graphics.Color;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.view.WindowManager;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import org.json.JSONObject;

import pojlib.LauncherApi;

/**
 * Runs Minecraft. It lives in a process of its own (see the manifest), next to the launcher's: Godot keeps its OpenXR
 * session for as long as its process lives, and Minecraft (Vivecraft) needs the headset for a session of its own. The
 * launcher's activity just pauses while this one is in front, and is there again with its menu when the game ends.
 */
public class GameActivity extends Activity {
    private static final String TAG = "QuestCraftGame";
    /** Extra of the intent that starts the launcher again when the game ends: no logo screen then, see QuestCraftPlugin. */
    static final String FROM_GAME = "from_game";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        // this process logs to a file of its own: the launcher starts again when the game ends, and its log would
        // otherwise wipe the game's (the launcher's Log screen shows this one, see LauncherApi.crashInfo)
        pojlib.util.Logger.fileName = "game_log.txt";
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        setContentView(loadingView());   // until the game draws into the headset's own session
        final String instance = getIntent().getStringExtra("instance");
        final String account = getIntent().getStringExtra("account");
        new Thread(() -> run(instance, account), "qc-game-launch").start();
    }

    /** The QuestCraft logo on black, the loading screen while Minecraft starts (res/drawable-nodpi/qc_logo.png). */
    private View loadingView() {
        LinearLayout box = new LinearLayout(this);
        box.setOrientation(LinearLayout.VERTICAL);
        box.setGravity(Gravity.CENTER);
        box.setBackgroundColor(Color.BLACK);
        int logo = getResources().getIdentifier("qc_logo", "drawable", getPackageName());
        if (logo != 0) {
            ImageView image = new ImageView(this);
            image.setImageResource(logo);
            image.setAdjustViewBounds(true);
            image.setMaxWidth(1000);
            box.addView(image, new LinearLayout.LayoutParams(1000, ViewGroup.LayoutParams.WRAP_CONTENT));
        }
        TextView text = new TextView(this);
        text.setText("Starting Minecraft...");
        text.setTextColor(Color.LTGRAY);
        text.setTextSize(24);
        text.setGravity(Gravity.CENTER);
        text.setPadding(0, 40, 0, 0);
        box.addView(text);
        return box;
    }

    private void run(String instance, String account) {
        try {
            String error = LauncherApi.init(this);
            if (!error.isEmpty()) throw new IllegalStateException("Pojlib: " + error);

            LauncherApi.login(account);
            waitFor(() -> new JSONObject(LauncherApi.loginState()).optBoolean("loggedIn"), 120, "the sign in");

            LauncherApi.prepareLight(instance);   // the launcher has just done the downloads and checks
            waitFor(() -> {
                JSONObject progress = new JSONObject(LauncherApi.progress());
                String phase = progress.optString("phase");
                if ("error".equals(phase)) throw new IllegalStateException(progress.optString("error"));
                return "ready".equals(phase);
            }, 900, "preparing " + instance);

            LauncherApi.runGameBlocking();
        } catch (Throwable t) {
            Log.e(TAG, "The game did not start", t);
            try {
                pojlib.util.Logger.getInstance().appendToLog("The game did not start: " + Log.getStackTraceString(t));
            } catch (Throwable ignored) {
            }
        } finally {
            // back to the menu: the launcher's process was ended when the game started (so that its OpenXR session and
            // its memory are gone), start it again from scratch, then end this one
            try {
                Intent launcher = getPackageManager().getLaunchIntentForPackage(getPackageName());
                launcher.putExtra(FROM_GAME, true);
                launcher.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
                startActivity(launcher);
                Thread.sleep(800);
            } catch (Throwable t) {
                Log.e(TAG, "Could not start the launcher again", t);
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
