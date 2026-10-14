package questcraft.legacy.vr;

import java.util.HashMap;
import java.util.Map;

import questcraft.legacy.xr.GameInput;

/**
 * The Quest controllers in VR, as QuestCraft's Vivecraft has them where the old game allows:
 * <ul>
 * <li>left stick: walking (analog, through the game's movement input, see {@link VR#moveForward});</li>
 * <li>right stick: snap turns of 45 degrees; in menus it scrolls;</li>
 * <li>right trigger: hit / break (left click) where the right controller points; left trigger: use / place;</li>
 * <li>A: jump; B: sneak; X: inventory; Y and the menu button: Esc (pause, back);</li>
 * <li>grips: the next and previous hotbar slot;</li>
 * <li>in menus the right controller points at the menu panel, the triggers click.</li>
 * </ul>
 * Keys and buttons reach the game through the headset's GLFW stand-in ({@code GameInput}); on a desktop (the VR
 * check) nothing is pressed, the keyboard and mouse are the desktop's.
 */
final class VRControls {
    static final int KEY_SPACE = 32, KEY_E = 69, KEY_ESCAPE = 256, KEY_LEFT_SHIFT = 340;
    static final float DEADZONE = 0.15f;

    /** Where key presses go: GLFW's callbacks on the headset */
    interface Sink {
        void key(int glfwKey, boolean down);

        void button(int glfwButton, boolean down);

        void cursor(double x, double y);

        void scroll(double amount);
    }

    private final Sink sink;
    private final int width, height;
    private final Map<Integer, Boolean> keys = new HashMap<>();
    private final boolean[] buttons = new boolean[2];
    private boolean leftGripDown, rightGripDown, turnArmed = true;
    private int turn;
    private float forward, sideways, scrollTimer;
    private double cursorX = -1, cursorY = -1;

    VRControls(boolean headset, int width, int height) {
        this(headset ? new Sink() {
            public void key(int glfwKey, boolean down) {
                GameInput.key(glfwKey, down);
            }

            public void button(int glfwButton, boolean down) {
                GameInput.button(glfwButton, down);
            }

            public void cursor(double x, double y) {
                GameInput.cursor(x, y);
            }

            public void scroll(double amount) {
                GameInput.scroll(amount);
            }
        } : null, width, height);
    }

    VRControls(Sink sink, int width, int height) {
        this.sink = sink;
        this.width = width;
        this.height = height;
    }

    void update(VRFrame f, boolean menu, boolean pointing, float u, float v) {
        forward = menu ? 0 : deadzone(f.leftY);
        // the game's sideways is positive to the left
        sideways = menu ? 0 : -deadzone(f.leftX);

        if (menu) {
            turnArmed = Math.abs(f.rightX) < 0.3f;
        } else if (turnArmed && Math.abs(f.rightX) > 0.6f) {
            turn += f.rightX > 0 ? 1 : -1;
            turnArmed = false;
        } else if (Math.abs(f.rightX) < 0.3f) {
            turnArmed = true;
        }
        if (sink == null) return;

        hold(KEY_SPACE, f.a);
        hold(KEY_LEFT_SHIFT, f.b);
        hold(KEY_E, f.x);
        hold(KEY_ESCAPE, f.y || f.menu);

        if (menu && pointing) {
            double x = u * width, y = v * height;
            if (x != cursorX || y != cursorY) {
                cursorX = x;
                cursorY = y;
                sink.cursor(x, y);
            }
        }
        holdButton(0, f.rightTrigger);
        holdButton(1, f.leftTrigger);

        if (menu) {
            float ry = deadzone(f.rightY);
            scrollTimer -= 1 / 72f;
            if (Math.abs(ry) > 0.5f && scrollTimer <= 0) {
                sink.scroll(ry > 0 ? 1 : -1);
                scrollTimer = 0.2f;
            }
        }
        boolean rightGrip = rightGripDown ? f.rightGrip > 0.4f : f.rightGrip > 0.6f;
        if (rightGrip && !rightGripDown && !menu) sink.scroll(-1);
        rightGripDown = rightGrip;
        boolean leftGrip = leftGripDown ? f.leftGrip > 0.4f : f.leftGrip > 0.6f;
        if (leftGrip && !leftGripDown && !menu) sink.scroll(1);
        leftGripDown = leftGrip;
    }

    /** Snap turns asked for since the last call: +1 right, -1 left. */
    int takeTurn() {
        int t = turn;
        turn = 0;
        return t;
    }

    float moveForward() {
        return forward;
    }

    float moveSideways() {
        return sideways;
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
}
