package pojlib;

import android.app.Activity;
import android.content.Intent;
import android.os.Process;

/** Engine independent parts of the hand-over between the launcher and the game. */
public final class LauncherHost {
    private LauncherHost() {}

    /** Starts the launcher again from scratch (the game owns the process until it ends) and ends this process. */
    public static void relaunch(Activity activity) {
        activity.runOnUiThread(() -> {
            Intent start = activity.getPackageManager().getLaunchIntentForPackage(activity.getApplicationInfo().packageName);
            start.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
            activity.startActivity(start);
            activity.finish();
            Process.killProcess(Process.myPid());
        });
    }
}
