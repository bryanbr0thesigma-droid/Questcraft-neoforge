package questcraft.legacy.xr;

import java.util.HashMap;
import java.util.Map;

/**
 * The Quest controllers as the old game's keyboard and mouse:
 * <ul>
 * <li>left stick: W A S D; right stick: looking around (the mouse, while the game has it), scrolling in menus;</li>
 * <li>right trigger: left click (hit, break, click in menus); left trigger: right click (use, place);</li>
 * <li>A: jump (space); B: sneak (shift); X: inventory (E); Y and the menu button: Esc (pause, back);</li>
 * <li>grips: the hotbar slot right and left (the mouse wheel);</li>
 * <li>in menus the right controller points at the screen, and the cursor goes where it points.</li>
 * </ul>
 * Only changes go to the game: a press when a button goes down, a release when it comes up.
 */
final class Controls {
    /** Where the game's input goes (GameInput on the headset). Keys and buttons are GLFW's. */
    interface Sink {
        void key(int glfwKey, boolean down);

        void button(int glfwButton, boolean down);

        void cursor(double x, double y);

        void scroll(double amount);
    }

    /** The controllers this frame. Sticks -1..1 (up and right positive), triggers and grips 0..1. */
    static final class State {
        float leftX, leftY, rightX, rightY, leftTrigger, rightTrigger, leftGrip, rightGrip;
        boolean a, b, x, y, menu;
        /** The right controller points at the screen, at u, v (0..1 from the top left). */
        boolean pointing;
        float pointerU, pointerV;
    }

    static final int KEY_SPACE = 32, KEY_A = 65, KEY_D = 68, KEY_E = 69, KEY_S = 83, KEY_W = 87;
    static final int KEY_ESCAPE = 256, KEY_LEFT_SHIFT = 340;
    static final int BUTTON_LEFT = 0, BUTTON_RIGHT = 1;
    /** How fast the right stick turns: pixels of mouse movement per second at full tilt (about 135 and 90 degrees at
     * the game's default sensitivity) */
    static final float LOOK_X = 900, LOOK_Y = 600;
    static final float DEADZONE = 0.15f;

    private final Sink sink;
    private final int width, height;
    private final Map<Integer, Boolean> keys = new HashMap<>();
    private final boolean[] buttons = new boolean[2];
    private boolean leftGripDown, rightGripDown;
    private double cursorX, cursorY;
    private boolean wasGrabbed;
    private float scrollTimer;
    private boolean pointerVisible;
    private int pointerX, pointerY;

    Controls(Sink sink, int width, int height) {
        this.sink = sink;
        this.width = width;
        this.height = height;
        this.cursorX = width / 2.0;
        this.cursorY = height / 2.0;
    }

    /**
     * @param grabbed the game has the mouse (playing: the right stick looks around), else a menu is open
     * @param seconds since the last update
     */
    void update(State s, boolean grabbed, float seconds) {
        // moving only while playing: a menu opened mid-step must not leave the player walking
        hold(KEY_W, grabbed && press(KEY_W, s.leftY, 1));
        hold(KEY_S, grabbed && press(KEY_S, s.leftY, -1));
        hold(KEY_D, grabbed && press(KEY_D, s.leftX, 1));
        hold(KEY_A, grabbed && press(KEY_A, s.leftX, -1));
        hold(KEY_SPACE, s.a);
        hold(KEY_LEFT_SHIFT, s.b);
        hold(KEY_E, s.x);
        hold(KEY_ESCAPE, s.y || s.menu);

        if (grabbed) {
            float rx = deadzone(s.rightX), ry = deadzone(s.rightY);
            if (rx != 0 || ry != 0) {
                cursorX += rx * LOOK_X * seconds;
                cursorY -= ry * LOOK_Y * seconds;
                sink.cursor(cursorX, cursorY);
            }
            pointerVisible = false;
        } else {
            if (s.pointing) {
                double x = clamp(s.pointerU, 0, 1) * width, y = clamp(s.pointerV, 0, 1) * height;
                if (x != cursorX || y != cursorY || wasGrabbed) {
                    cursorX = x;
                    cursorY = y;
                    sink.cursor(x, y);
                }
                pointerX = (int) x;
                pointerY = (int) y;
            }
            pointerVisible = s.pointing;
            // the right stick scrolls lists, a few lines a second while it is held
            float ry = deadzone(s.rightY);
            scrollTimer -= seconds;
            if (Math.abs(ry) > 0.5f && scrollTimer <= 0) {
                sink.scroll(ry > 0 ? 1 : -1);
                scrollTimer = 0.2f;
            } else if (Math.abs(ry) <= 0.5f) {
                scrollTimer = 0;
            }
        }
        wasGrabbed = grabbed;

        holdButton(BUTTON_LEFT, s.rightTrigger);
        holdButton(BUTTON_RIGHT, s.leftTrigger);

        boolean rightGrip = rightGripDown ? s.rightGrip > 0.4f : s.rightGrip > 0.6f;
        if (rightGrip && !rightGripDown) sink.scroll(-1);
        rightGripDown = rightGrip;
        boolean leftGrip = leftGripDown ? s.leftGrip > 0.4f : s.leftGrip > 0.6f;
        if (leftGrip && !leftGripDown) sink.scroll(1);
        leftGripDown = leftGrip;
    }

    boolean pointerVisible() {
        return pointerVisible;
    }

    int pointerX() {
        return pointerX;
    }

    int pointerY() {
        return pointerY;
    }

    /** A stick direction is pressed past half way, and let go below a third (so it does not flicker at the edge). */
    private boolean press(int key, float value, int sign) {
        float v = value * sign;
        return Boolean.TRUE.equals(keys.get(key)) ? v > 0.35f : v > 0.5f;
    }

    private void hold(int key, boolean down) {
        boolean was = Boolean.TRUE.equals(keys.get(key));
        if (down != was) {
            keys.put(key, down);
            sink.key(key, down);
        }
    }

    private void holdButton(int button, float value) {
        boolean down = buttons[button] ? value > 0.4f : value > 0.6f;
        if (down != buttons[button]) {
            buttons[button] = down;
            sink.button(button, down);
        }
    }

    static float deadzone(float value) {
        if (Math.abs(value) < DEADZONE) return 0;
        return Math.signum(value) * (Math.abs(value) - DEADZONE) / (1 - DEADZONE);
    }

    private static double clamp(double value, double min, double max) {
        return Math.max(min, Math.min(max, value));
    }
}
