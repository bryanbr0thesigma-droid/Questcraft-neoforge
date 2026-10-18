package questcraft.legacy.vr;

import java.util.ArrayList;
import java.util.List;

/**
 * Checks VR's controllers as the game gets them (VRControls) and the VR keyboard's typing (VRKeyboard), without GL
 * or a headset: run by questcraft.legacy.xr.SelfTest.
 */
public final class ControlsTest {
    private static int failures;

    private ControlsTest() {
    }

    private static void check(boolean ok, String what) {
        if (!ok) {
            failures++;
            System.out.println("FAILED: " + what);
        }
    }

    private static final class Recorder implements VRControls.Sink, VRKeyboard.Sink {
        final List<String> events = new ArrayList<>();

        public void key(int key, boolean down) {
            events.add("key " + key + (down ? " down" : " up"));
        }

        public void button(int button, boolean down) {
            events.add("button " + button + (down ? " down" : " up"));
        }

        public void cursor(double x, double y) {
        }

        public void scroll(double amount) {
            events.add("scroll " + amount);
        }

        public void character(int codepoint) {
            events.add("char " + (char) codepoint);
        }

        List<String> take() {
            List<String> taken = new ArrayList<>(events);
            events.clear();
            return taken;
        }
    }

    /** The number of failed checks. */
    public static int run() {
        failures = 0;
        buttonsKeepWherePressed();
        keyboardTyping();
        return failures;
    }

    private static void buttonsKeepWherePressed() {
        Recorder r = new Recorder();
        VRControls controls = new VRControls(r, 100, 100);
        VRFrame f = new VRFrame();

        // A held (jumping) as the inventory opens: no right click in the inventory
        f.a = true;
        controls.update(f, false, false, 0, 0, null);
        check(r.take().equals(list("key 32 down")), "A in the game jumps");
        controls.update(f, true, true, 0.5f, 0.5f, null);
        List<String> e = r.take();
        check(e.contains("key 32 up") && !e.contains("button 1 down"), "A held as a menu opens does not right-click: " + e);
        f.a = false;
        controls.update(f, true, true, 0.5f, 0.5f, null);
        f.a = true;
        controls.update(f, true, true, 0.5f, 0.5f, null);
        check(r.take().equals(list("button 1 down")), "A pressed in a menu right-clicks");
        f.a = false;
        controls.update(f, true, true, 0.5f, 0.5f, null);
        r.take();

        // the left trigger held as a chest closes: no use (right click) in the game
        f.leftTrigger = 1;
        controls.update(f, true, true, 0.5f, 0.5f, null);
        check(r.take().isEmpty(), "the left trigger in a menu does nothing");
        controls.update(f, false, false, 0, 0, null);
        check(!r.take().contains("button 1 down"), "the left trigger held as a menu closes does not use");
        f.leftTrigger = 0;
        controls.update(f, false, false, 0, 0, null);
        f.leftTrigger = 1;
        controls.update(f, false, false, 0, 0, null);
        check(r.take().equals(list("button 1 down")), "the left trigger pressed in the game uses");
        f.leftTrigger = 0;
        controls.update(f, false, false, 0, 0, null);
        r.take();

        // held through a menu and back: no new use when the game comes back
        f.leftTrigger = 1;
        controls.update(f, false, false, 0, 0, null);
        check(r.take().equals(list("button 1 down")), "the left trigger uses");
        controls.update(f, true, true, 0.5f, 0.5f, null);
        check(r.take().equals(list("button 1 up")), "a menu opening lets go of it");
        controls.update(f, false, false, 0, 0, null);
        check(r.take().isEmpty(), "held through a menu and back, it does not use again");
        f.leftTrigger = 0;
        controls.update(f, false, false, 0, 0, null);
        r.take();

        // the right trigger pressed off the menu panel clicks nothing, even when the pointer comes onto it
        f.rightTrigger = 1;
        controls.update(f, true, false, 0, 0, null);
        controls.update(f, true, true, 0.5f, 0.5f, null);
        check(!r.take().contains("button 0 down"), "a press off the menu panel does not click");
        f.rightTrigger = 0;
        controls.update(f, true, true, 0.5f, 0.5f, null);
        f.rightTrigger = 1;
        controls.update(f, true, true, 0.5f, 0.5f, null);
        check(r.take().equals(list("button 0 down")), "a press on the menu panel clicks");
        // held as the menu closes: let go, not an attack
        controls.update(f, false, false, 0, 0, null);
        check(r.take().equals(list("button 0 up")), "a click held as the menu closes is let go");
        f.rightTrigger = 0;
        controls.update(f, false, false, 0, 0, null);
        r.take();

        // VR stops with A and the right trigger held: both are let go
        f.a = true;
        f.rightTrigger = 1;
        controls.update(f, false, false, 0, 0, null);
        r.take();
        controls.releaseAll();
        e = r.take();
        check(e.contains("key 32 up") && e.contains("button 0 up") && e.size() == 2, "releaseAll lets go of what is held: " + e);
    }

    private static void keyboardTyping() {
        Recorder r = new Recorder();
        VRKeyboard keyboard = new VRKeyboard(r);
        keyboard.setVisible(true);
        keyboard.place(new float[]{0, 1.6f, -1.5f}, 0);
        VRFrame f = new VRFrame();
        pointAt(keyboard, f, 1, 'q');
        keyboard.updatePointers(f);
        check(keyboard.onKeyboard(1), "the right hand points at the keyboard");

        // the trigger was down as it showed: a press after letting go types
        keyboard.trigger(1, false, 0);
        keyboard.trigger(1, true, 0);
        check(r.take().equals(list("key 81 down", "char q", "key 81 up")), "q is its key, then its character");
        // hidden and shown again with the trigger still held: nothing typed
        keyboard.setVisible(false);
        keyboard.setVisible(true);
        keyboard.updatePointers(f);
        keyboard.trigger(1, true, 0);
        check(r.take().isEmpty(), "a trigger held across showing the keyboard types nothing");
        keyboard.trigger(1, false, 0);
        keyboard.trigger(1, true, 0);
        check(r.take().equals(list("key 81 down", "char q", "key 81 up")), "a new press types again");
        keyboard.trigger(1, false, 0);

        // what the game's font.txt does not have is not typed
        check(keyboard.typeable('`'), "without font.txt everything is typeable");
        pointAt(keyboard, f, 1, '`');
        keyboard.updatePointers(f);
        keyboard.trigger(1, true, 0);
        check(r.take().equals(list("key 96 down", "char `", "key 96 up")), "the backtick, when the game takes it");
        keyboard.trigger(1, false, 0);
    }

    /** Aims the hand at the middle of the key that types {@code c}. */
    private static void pointAt(VRKeyboard keyboard, VRFrame f, int hand, char c) {
        VRKeyboard.Key key = null;
        for (VRKeyboard.Key k : VRKeyboard.KEYS) if (k.ch == c && k.glfw == 0) key = k;
        float u = (key.x + key.w / 2) / 15, v = (key.y + 0.5f) / 5;
        float x = (u - 0.5f) * keyboard.width(), y = (0.5f - v) * keyboard.height();
        float[] c0 = keyboard.center(), right = keyboard.right(), up = keyboard.up();
        float[] target = new float[3], origin = {0.2f, 1.1f, -0.2f};
        for (int i = 0; i < 3; i++) target[i] = c0[i] + right[i] * x + up[i] * y;
        float dx = target[0] - origin[0], dy = target[1] - origin[1], dz = target[2] - origin[2];
        float length = (float) Math.sqrt(dx * dx + dy * dy + dz * dz);
        dx /= length;
        dy /= length;
        dz /= length;
        // the rotation of (0, 0, -1) onto the direction: axis (0, 0, -1) x d, w 1 + (0, 0, -1).d
        float qx = dy, qy = -dx, qz = 0, qw = 1 - dz;
        float n = (float) Math.sqrt(qx * qx + qy * qy + qz * qz + qw * qw);
        f.aimPosition[hand] = origin;
        f.aimOrientation[hand] = new float[]{qx / n, qy / n, qz / n, qw / n};
        f.handTracked[hand] = true;
    }

    private static List<String> list(String... events) {
        List<String> l = new ArrayList<>();
        for (String e : events) l.add(e);
        return l;
    }
}
