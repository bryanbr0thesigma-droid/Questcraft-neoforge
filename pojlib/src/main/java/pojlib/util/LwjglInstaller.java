package pojlib.util;

import android.app.Activity;

import java.io.File;
import java.io.IOException;
import java.util.Objects;

/** Copies the LWJGL GLFW bridge jar from the app's assets, independent of which engine hosts the launcher. */
public final class LwjglInstaller {
    private LwjglInstaller() {}

    public static String installLWJGL(Activity activity) throws IOException {
        Logger.getInstance().appendToLog("Checking LWJGL");
        File lwjgl = new File(Constants.USER_HOME + "/lwjgl3/lwjgl-glfw-classes.jar");
        byte[] lwjglAsset = FileUtil.loadFromAssetToByte(activity, "lwjgl/lwjgl-glfw-classes.jar");

        if (!lwjgl.exists()) {
            Objects.requireNonNull(lwjgl.getParentFile()).mkdirs();
            FileUtil.write(lwjgl.getAbsolutePath(), lwjglAsset);
        } else if (!FileUtil.matchingAssetFile(lwjgl, lwjglAsset)) {
            lwjgl.delete();
            Objects.requireNonNull(lwjgl.getParentFile()).mkdirs();
            FileUtil.write(lwjgl.getAbsolutePath(), lwjglAsset);
        }

        Logger.getInstance().appendToLog("LWJGL installed");
        return lwjgl.getAbsolutePath();
    }
}
