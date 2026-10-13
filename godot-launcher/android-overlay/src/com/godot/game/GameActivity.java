package com.godot.game;

import android.app.Activity;
import android.content.Intent;
import android.os.Bundle;
import android.os.Process;
import android.util.Log;
import android.graphics.Color;
import android.graphics.PorterDuff;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.view.WindowManager;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.TextView;

import org.json.JSONObject;

import java.io.File;
import java.io.RandomAccessFile;
import java.nio.charset.StandardCharsets;

import pojlib.LauncherApi;
import pojlib.util.LoadingProgress;

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

    private ProgressBar bar;
    private TextView status;
    private LoadingProgress shown = LoadingProgress.step(0, "");

    /** Moves the loading bar (never back), from any thread. */
    private void progress(LoadingProgress next) {
        synchronized (this) {
            LoadingProgress further = shown.orFurther(next);
            if (further == shown) return;
            shown = further;
        }
        final LoadingProgress p = shown;
        runOnUiThread(() -> {
            bar.setProgress(p.percent);
            status.setText(p.label + "...  " + p.percent + "%");
        });
    }

    /** Follows the game's log from where it is now and moves the bar with what Minecraft says it is doing. */
    private void followLog() {
        Thread t = new Thread(() -> {
            File log = new File(pojlib.util.Constants.USER_HOME, "game_log.txt");
            long from = log.length();
            while (true) {
                try {
                    Thread.sleep(500);
                    long length = log.length();
                    if (length < from) from = 0;   // the log was started anew
                    if (length == from) continue;
                    try (RandomAccessFile file = new RandomAccessFile(log, "r")) {
                        file.seek(from);
                        byte[] chunk = new byte[(int) Math.min(length - from, 1 << 20)];
                        file.readFully(chunk);
                        from += chunk.length;
                        progress(LoadingProgress.of(new String(chunk, StandardCharsets.UTF_8)));
                    }
                } catch (InterruptedException e) {
                    return;
                } catch (Throwable ignored) {
                    // the file is being written, the next round gets it
                }
            }
        }, "qc-loading-bar");
        t.setDaemon(true);
        t.start();
    }

    /** The QuestCraft logo on black with a loading bar, the loading screen while Minecraft starts (res/drawable-nodpi/qc_logo.png). */
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
        bar = new ProgressBar(this, null, android.R.attr.progressBarStyleHorizontal);
        bar.setMax(100);
        bar.setProgress(0);
        bar.getProgressDrawable().setColorFilter(Color.rgb(0x2f, 0xbf, 0x5f), PorterDuff.Mode.SRC_IN);
        LinearLayout.LayoutParams barParams = new LinearLayout.LayoutParams(800, 24);
        barParams.topMargin = 60;
        box.addView(bar, barParams);
        status = new TextView(this);
        status.setText("Starting Minecraft...");
        status.setTextColor(Color.LTGRAY);
        status.setTextSize(24);
        status.setGravity(Gravity.CENTER);
        status.setPadding(0, 24, 0, 0);
        box.addView(status);
        return box;
    }

    private void run(String instance, String account) {
        try {
            String error = LauncherApi.init(this);
            if (!error.isEmpty()) throw new IllegalStateException("Pojlib: " + error);

            progress(LoadingProgress.step(LoadingProgress.SIGN_IN, "Signing in"));
            LauncherApi.login(account);
            waitFor(() -> new JSONObject(LauncherApi.loginState()).optBoolean("loggedIn"), 120, "the sign in");

            progress(LoadingProgress.step(LoadingProgress.CHECK_FILES, "Checking the game files"));
            LauncherApi.prepareLight(instance);   // the launcher has just done the downloads and checks
            waitFor(() -> {
                JSONObject progress = new JSONObject(LauncherApi.progress());
                String phase = progress.optString("phase");
                if ("error".equals(phase)) throw new IllegalStateException(progress.optString("error"));
                // 15..30 %: the checks of the files; the launcher has done them, so usually this is over at once
                progress(LoadingProgress.step(LoadingProgress.CHECK_FILES
                        + (int) (progress.optDouble("percent", 0.0) * (LoadingProgress.PREPARE_JAVA - LoadingProgress.CHECK_FILES) / 100.0),
                        "Checking the game files"));
                return "ready".equals(phase);
            }, 900, "preparing " + instance);

            progress(LoadingProgress.step(LoadingProgress.START_JAVA, "Starting Java"));
            followLog();
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
