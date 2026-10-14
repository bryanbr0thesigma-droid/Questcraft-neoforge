package questcraft.legacy.xr;

import org.lwjgl.glfw.GLFW;
import org.lwjgl.glfw.GLFWCursorPosCallback;
import org.lwjgl.glfw.GLFWKeyCallback;
import org.lwjgl.glfw.GLFWMouseButtonCallback;
import org.lwjgl.glfw.GLFWScrollCallback;
import org.lwjgl.glfw.GLFWWindowFocusCallback;

/**
 * Key presses, mouse buttons and the cursor, handed to the game the way the headset's GLFW stand-in hands them over:
 * LWJGL 2's Display registered its callbacks there (in GLFW's static fields), and Keyboard and Mouse read what those
 * get. Called on the game's thread, from Display.update(): by the screen in VR and by VR's controllers.
 */
public final class GameInput {
    private GameInput() {
    }

    private static long window() {
        return GLFW.glfwGetCurrentContext();
    }

    public static void key(int glfwKey, boolean down) {
        GLFWKeyCallback callback = GLFW.mGLFWKeyCallback;
        if (callback != null) callback.invoke(window(), glfwKey, 0, down ? GLFW.GLFW_PRESS : GLFW.GLFW_RELEASE, 0);
    }

    public static void button(int glfwButton, boolean down) {
        GLFWMouseButtonCallback callback = GLFW.mGLFWMouseButtonCallback;
        if (callback != null) callback.invoke(window(), glfwButton, down ? GLFW.GLFW_PRESS : GLFW.GLFW_RELEASE, 0);
    }

    /** The cursor at x, y: pixels from the top left of the game's picture. */
    public static void cursor(double x, double y) {
        GLFWCursorPosCallback callback = GLFW.mGLFWCursorPosCallback;
        if (callback != null) callback.invoke(window(), x, y);
    }

    public static void scroll(double amount) {
        GLFWScrollCallback callback = GLFW.mGLFWScrollCallback;
        if (callback != null) callback.invoke(window(), 0, amount);
    }

    /** The game pauses itself when its window is not focused: it is, there is no other. */
    public static void focus() {
        GLFWWindowFocusCallback callback = GLFW.mGLFWWindowFocusCallback;
        if (callback != null) callback.invoke(window(), true);
    }
}
