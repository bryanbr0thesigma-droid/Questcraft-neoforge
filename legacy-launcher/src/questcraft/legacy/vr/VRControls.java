package questcraft.legacy.vr;

import java.util.HashMap;
import java.util.Map;

import questcraft.legacy.xr.GameInput;

/**
 * The Quest controllers in VR, laid out as QuestCraft's Vivecraft has them (its Quest bindings) where the old game
 * allows.
 * <p>In the game:</p>
 * <ul>
 * <li>left stick: walking (analog, through the game's movement input, see {@link VR#moveForward});</li>
 * <li>right stick: snap turns of 45 degrees; right stick click: sneak (held);</li>
 * <li>right trigger: hit / break (left click) where the right controller points; left trigger: use / place;</li>
 * <li>A: jump; Y: inventory; menu button: pause (Esc); grips: the previous and next hotbar slot;</li>
 * <li>B: the radial menu (held: point at an item, let go), X: teleport in QuestCraft (none here).</li>
 * </ul>
 * <p>In menus (the right controller points at the menu panel):</p>
 * <ul>
 * <li>right trigger: left click; A: right click (half a stack); left grip: shift (shift-click); right grip: middle
 * click; right stick: scroll; Y: inventory (closes it); menu button: Esc (back);</li>
 * <li>X: shows or hides the keyboard; a controller pointing at the keyboard types with its trigger.</li>
 * </ul>
 * A button keeps the meaning it had where it was pressed (in the game or in a menu, on the menu panel or not) until it
 * is let go: A held as the inventory opens does not right-click in it, and a press off the menu panel clicks nothing.
 * Keys and buttons reach the game through the headset's GLFW stand-in ({@code GameInput}); on a desktop (the VR
 * check) nothing is pressed, the keyboard and mouse are the desktop's.
 */
final class VRControls {
    static final int KEY_SPACE = 32, KEY_E = 69, KEY_ESCAPE = 256, KEY_LEFT_SHIFT = 340;
    static final int LEFT_BUTTON = 0, RIGHT_BUTTON = 1, MIDDLE_BUTTON = 2;
    static final float DEADZONE = 0.15f;
    static final float FRAME = 1 / 72f;

    /** Where key presses go: GLFW's callbacks on the headset */
    interface Sink {
        void key(int glfwKey, boolean down);

        void button(int glfwButton, boolean down);

        void cursor(double x, double y);

        void scroll(double amount);
    }

    /** A controller button, and where it was pressed: it counts only there (in a menu or not), until it is let go */
    static final class Press {
        boolean down, inMenu, onPanel;

        /** This frame's state; true while it is held and was pressed in the current mode. */
        boolean update(boolean now, boolean menu, boolean pointing) {
            if (now && !down) {
                inMenu = menu;
                onPanel = pointing;
            }
            down = now;
            return now && inMenu == menu;
        }

        /** Held, pressed in a menu while pointing at the menu panel: a click there. */
        boolean clicks(boolean now, boolean menu, boolean pointing) {
            return update(now, menu, pointing) && menu && onPanel;
        }
    }

    private final Sink sink;
    private final int width, height;
    private final Press a = new Press(), leftTrigger = new Press(), rightTrigger = new Press(), leftGrip = new Press(),
            rightGrip = new Press(), rightStick = new Press();
    private final Map<Integer, Boolean> keys = new HashMap<>();
    private final boolean[] buttons = new boolean[3];
    private final boolean[] triggerDown = new boolean[2];
    /** A trigger pressed on the keyboard types, and does not click until it is let go */
    private final boolean[] typing = new boolean[2];
    private boolean leftGripDown, rightGripDown, turnArmed = true, xWasDown;
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

    /** The keyboard's typing, for a headset (null on a desktop: the keyboard only logs). */
    static VRKeyboard.Sink keyboardSink(boolean headset) {
        if (!headset) return null;
        return new VRKeyboard.Sink() {
            public void key(int glfwKey, boolean down) {
                GameInput.key(glfwKey, down);
            }

            public void character(int codepoint) {
                GameInput.character(codepoint);
            }
        };
    }

    void update(VRFrame f, boolean menu, boolean pointing, float u, float v, VRKeyboard keyboard) {
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

        // the triggers: on the keyboard they type (both hands), elsewhere they click
        float[] triggers = {f.leftTrigger, f.rightTrigger};
        for (int hand = 0; hand < 2; hand++) {
            boolean down = triggerDown[hand] ? triggers[hand] > 0.4f : triggers[hand] > 0.6f;
            boolean onKeyboard = menu && keyboard != null && keyboard.onKeyboard(hand);
            if (down && !triggerDown[hand]) typing[hand] = onKeyboard;
            if (!down) typing[hand] = false;
            triggerDown[hand] = down;
            if (keyboard != null) keyboard.trigger(hand, typing[hand], FRAME);
        }
        // X in a menu: the keyboard
        if (f.x && !xWasDown && menu && keyboard != null) keyboard.setVisible(!keyboard.visible());
        xWasDown = f.x;

        if (sink == null) return;

        boolean leftGripNow = leftGripDown ? f.leftGrip > 0.4f : f.leftGrip > 0.6f;
        boolean rightGripNow = rightGripDown ? f.rightGrip > 0.4f : f.rightGrip > 0.6f;

        if (menu && pointing) {
            double x = u * width, y = v * height;
            if (x != cursorX || y != cursorY) {
                cursorX = x;
                cursorY = y;
                sink.cursor(x, y);
            }
        }
        // each button as it was where it was pressed
        boolean aHeld = a.update(f.a, menu, pointing);
        boolean attack = rightTrigger.update(triggerDown[1] && !typing[1], menu, pointing);
        boolean use = leftTrigger.update(triggerDown[0] && !typing[0], menu, pointing);
        boolean shift = leftGrip.update(leftGripNow, menu, pointing);
        boolean middle = rightGrip.clicks(rightGripNow, menu, pointing);
        boolean sneak = rightStick.update(f.rightStickClick, menu, pointing);

        hold(KEY_SPACE, !menu && aHeld);
        hold(KEY_LEFT_SHIFT, menu ? shift : sneak);
        hold(KEY_E, f.y);
        hold(KEY_ESCAPE, f.menu);
        holdButton(LEFT_BUTTON, attack && (!menu || rightTrigger.onPanel));
        holdButton(RIGHT_BUTTON, menu ? aHeld && a.onPanel : use);
        holdButton(MIDDLE_BUTTON, middle);

        if (menu) {
            float ry = deadzone(f.rightY);
            scrollTimer -= FRAME;
            if (Math.abs(ry) > 0.5f && scrollTimer <= 0) {
                sink.scroll(ry > 0 ? 1 : -1);
                scrollTimer = 0.2f;
            }
        } else {
            // the grips: the next and previous hotbar slot (the game scrolls the other way round)
            if (rightGripNow && !rightGripDown) sink.scroll(-1);
            if (leftGripNow && !leftGripDown) sink.scroll(1);
        }
        rightGripDown = rightGripNow;
        leftGripDown = leftGripNow;
    }

    /** Lets go of every key and button held for the game (VR stops: nothing may stay pressed). */
    void releaseAll() {
        if (sink == null) return;
        for (Map.Entry<Integer, Boolean> key : keys.entrySet()) {
            if (key.getValue()) sink.key(key.getKey(), false);
        }
        keys.clear();
        for (int button = 0; button < buttons.length; button++) holdButton(button, false);
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

    private void holdButton(int button, boolean down) {
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
