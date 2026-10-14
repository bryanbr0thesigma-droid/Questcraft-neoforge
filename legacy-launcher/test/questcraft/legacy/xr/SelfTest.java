package questcraft.legacy.xr;

import java.util.ArrayList;
import java.util.List;

/**
 * Checks, on a desktop JVM (build.sh runs it), what of the screen in VR can be checked without a headset: how the
 * controllers become keys, buttons and the cursor, and where a pointer meets the screen.
 */
public final class SelfTest {
    private static int failures;

    private static void check(boolean ok, String what) {
        if (!ok) {
            failures++;
            System.out.println("FAILED: " + what);
        }
    }

    private static final class Recorder implements Controls.Sink {
        final List<String> events = new ArrayList<>();
        double x, y;

        public void key(int key, boolean down) {
            events.add("key " + key + (down ? " down" : " up"));
        }

        public void button(int button, boolean down) {
            events.add("button " + button + (down ? " down" : " up"));
        }

        public void cursor(double x, double y) {
            this.x = x;
            this.y = y;
            events.add("cursor");
        }

        public void scroll(double amount) {
            events.add("scroll " + (int) amount);
        }
    }

    public static void main(String[] args) {
        Recorder r = new Recorder();
        Controls controls = new Controls(r, 1280, 720);
        Controls.State s = new Controls.State();

        // walking: forward past half way presses W once, it stays down until the stick is nearly back
        s.leftY = 0.6f;
        controls.update(s, true, 0.016f);
        check(r.events.contains("key 87 down"), "stick forward presses W: " + r.events);
        r.events.clear();
        s.leftY = 0.4f;
        controls.update(s, true, 0.016f);
        check(r.events.isEmpty(), "W stays down at 0.4: " + r.events);
        s.leftY = 0.2f;
        controls.update(s, true, 0.016f);
        check(r.events.contains("key 87 up"), "W comes up near the middle: " + r.events);

        // a menu opening mid-step lets go of the movement keys
        r.events.clear();
        s.leftX = -0.9f;
        controls.update(s, true, 0.016f);
        check(r.events.contains("key 65 down"), "stick left presses A");
        controls.update(s, false, 0.016f);
        check(r.events.contains("key 65 up"), "a menu releases A: " + r.events);

        // looking: right stick right moves the cursor right, stick up moves it up (the game turns the view)
        r.events.clear();
        s.leftX = 0;
        s.rightX = 1;
        s.rightY = 1;
        controls.update(s, true, 0.5f);
        check(r.x > 640 && r.y < 360, "right stick moves the view: " + r.x + ", " + r.y);
        double turned = r.x - 640;
        check(Math.abs(turned - Controls.LOOK_X * 0.5) < 1, "full tilt turns LOOK_X pixels a second: " + turned);
        s.rightX = 0.1f;
        s.rightY = 0;
        r.events.clear();
        controls.update(s, true, 0.5f);
        check(!r.events.contains("cursor"), "inside the dead zone nothing moves");

        // buttons and triggers
        r.events.clear();
        s.rightX = 0;
        s.a = true;
        s.y = true;
        s.rightTrigger = 0.9f;
        s.leftTrigger = 0.7f;
        controls.update(s, true, 0.016f);
        check(r.events.contains("key 32 down") && r.events.contains("key 256 down"), "A is space, Y is Esc: " + r.events);
        check(r.events.contains("button 0 down") && r.events.contains("button 1 down"), "right trigger left click, left trigger right click: " + r.events);
        r.events.clear();
        s.a = false;
        s.y = false;
        s.rightTrigger = 0.5f;
        controls.update(s, true, 0.016f);
        check(r.events.contains("key 32 up") && !r.events.contains("button 0 up"), "release, trigger held at 0.5: " + r.events);

        // grips step the hotbar once per squeeze
        r.events.clear();
        s.rightGrip = 1;
        controls.update(s, true, 0.016f);
        controls.update(s, true, 0.016f);
        check(r.events.stream().filter(e -> e.equals("scroll -1")).count() == 1, "right grip: one slot per squeeze: " + r.events);

        // in a menu the cursor goes where the controller points, and the pointer is shown there
        r.events.clear();
        s.pointing = true;
        s.pointerU = 0.25f;
        s.pointerV = 0.75f;
        controls.update(s, false, 0.016f);
        check(r.x == 320 && r.y == 540, "pointer at a quarter, three quarters: " + r.x + ", " + r.y);
        check(controls.pointerVisible() && controls.pointerX() == 320 && controls.pointerY() == 540, "pointer shown there");
        controls.update(s, true, 0.016f);
        check(!controls.pointerVisible(), "no pointer while playing");

        // the pointer meets the screen: 1.6 m in front, straight at its centre, then at its top left corner
        float[] center = {0, 1.6f, -1.6f};
        float[] hit = ScreenMath.hit(center, 0, 1.8f, 1.0125f, new float[]{0, 1.6f, 0}, new float[]{0, 0, -1});
        check(hit != null && Math.abs(hit[0] - 0.5f) < 1e-4 && Math.abs(hit[1] - 0.5f) < 1e-4, "centre: " + str(hit));
        float[] corner = {-0.9f, 1.6f + 0.50625f, -1.6f};
        float[] d = norm(corner[0], corner[1] - 1.6f, corner[2]);
        hit = ScreenMath.hit(center, 0, 1.8f, 1.0125f, new float[]{0, 1.6f, 0}, d);
        check(hit != null && Math.abs(hit[0]) < 1e-3 && Math.abs(hit[1]) < 1e-3, "top left: " + str(hit));
        check(ScreenMath.hit(center, 0, 1.8f, 1.0125f, new float[]{0, 1.6f, 0}, new float[]{0, 0, 1}) == null, "pointing away misses");
        check(ScreenMath.hit(center, 0, 1.8f, 1.0125f, new float[]{0, 1.6f, 0}, new float[]{1, 0, 0}) == null, "pointing aside misses");

        // a screen to the player's right (turned to face them): pointing right hits its centre
        float yaw = (float) Math.atan2(-1, -0); // the player looked along +x
        hit = ScreenMath.hit(new float[]{1.6f, 1.6f, 0}, yaw, 1.8f, 1.0125f, new float[]{0, 1.6f, 0}, new float[]{1, 0, 0});
        check(hit != null && Math.abs(hit[0] - 0.5f) < 1e-4 && Math.abs(hit[1] - 0.5f) < 1e-4, "screen to the right: " + str(hit));
        // its left edge is towards -z for a player looking along +x
        hit = ScreenMath.hit(new float[]{1.6f, 1.6f, 0}, yaw, 1.8f, 1.0125f, new float[]{0, 1.6f, 0}, norm(1.6f, 0, -0.8f));
        check(hit != null && hit[0] < 0.1f, "its left edge is on the player's left: " + str(hit));

        // quaternion rotation: a quarter turn about y takes -z to -x
        float h = (float) Math.sqrt(0.5);
        float[] v = ScreenMath.rotate(0, h, 0, h, 0, 0, -1);
        check(Math.abs(v[0] + 1) < 1e-5 && Math.abs(v[2]) < 1e-5, "rotate: " + str(v));

        System.out.println(failures == 0 ? "screen self test: all passed" : "screen self test: " + failures + " failed");
        System.exit(failures == 0 ? 0 : 1);
    }

    private static float[] norm(float x, float y, float z) {
        float l = (float) Math.sqrt(x * x + y * y + z * z);
        return new float[]{x / l, y / l, z / l};
    }

    private static String str(float[] v) {
        if (v == null) return "null";
        StringBuilder b = new StringBuilder();
        for (float f : v) b.append(f).append(' ');
        return b.toString().trim();
    }
}
