package questcraft.legacy.xr;

import java.util.ArrayList;
import java.util.List;

import questcraft.legacy.vr.VRMath;

/**
 * Checks, on a desktop JVM (build.sh runs it), what of the screen in VR can be checked without a headset: how the
 * controllers become keys, buttons and the cursor, where a pointer meets the screen, and VR's room in the game's world.
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

        vr();

        // VR's controllers and keyboard
        failures += questcraft.legacy.vr.ControlsTest.run();
        System.out.println(failures == 0 ? "screen self test: all passed" : "screen self test: " + failures + " failed");
        System.exit(failures == 0 ? 0 : 1);
    }

    /** VR's room in the old game's world (questcraft.legacy.vr.VRMath) */
    private static void vr() {
        float[] room = VRMath.roomToWorld(0);
        float[] ahead = VRMath.mul3(room, 0, 0, -1);
        check(close(ahead, 0, 0, 1), "the room's ahead is the world's south (+z) at no turn: " + str(ahead));
        float[] look = VRMath.yawPitch(ahead);
        check(Math.abs(look[0]) < 1e-3 && Math.abs(look[1]) < 1e-3, "which is yaw 0, pitch 0: " + str(look));
        float[] right = VRMath.mul3(room, 1, 0, 0);
        check(close(right, -1, 0, 0), "facing south, the right is west (-x): " + str(right));
        look = VRMath.yawPitch(VRMath.mul3(VRMath.roomToWorld(90), 0, 0, -1));
        check(Math.abs(look[0] - 90) < 1e-3, "a turn of 90 looks along yaw 90: " + str(look));
        // the head turned 30 degrees to the left (about +y): the game's yaw goes down
        float s = (float) Math.sin(Math.toRadians(15)), c = (float) Math.cos(Math.toRadians(15));
        float[] head = VRMath.quatToMatrix(0, s, 0, c);
        look = VRMath.yawPitch(VRMath.mul3(room, VRMath.mul3(head, 0, 0, -1)[0], VRMath.mul3(head, 0, 0, -1)[1], VRMath.mul3(head, 0, 0, -1)[2]));
        check(Math.abs(look[0] + 30) < 1e-2, "a head turned left 30 is yaw -30: " + str(look));
        // looking down 20 degrees (about +x, negative): pitch +20
        s = (float) Math.sin(Math.toRadians(-10));
        c = (float) Math.cos(Math.toRadians(-10));
        float[] down = VRMath.mul3(VRMath.quatToMatrix(s, 0, 0, c), 0, 0, -1);
        look = VRMath.yawPitch(VRMath.mul3(room, down[0], down[1], down[2]));
        check(Math.abs(look[1] - 20) < 1e-2, "looking down 20 is pitch 20: " + str(look));
        // a point a metre ahead of the head, wherever the head is in the room, is a metre south of the camera
        float[] point = VRMath.roomPointToWorld(room, new float[]{0.3f, 1.62f, -0.4f}, 0.3f, 1.62f, -1.4f);
        check(close(point, 0, 0, 1), "a metre ahead of the head: " + str(point));
        // ducking 0.5 m lowers the view 0.5 m below the player's eyes
        point = VRMath.roomPointToWorld(room, new float[]{0, 1.12f, 0}, 0, 1.12f, 0);
        check(close(point, 0, -0.5f, 0), "ducking: " + str(point));
        // the eye's view: the world south of the camera is ahead (-z) of an unturned eye
        float[] view = VRMath.viewMatrix(room, VRMath.quatToMatrix(0, 0, 0, 1), new float[]{0, 0, 0});
        float[] eye = VRMath.transform(view, 0, 0, 1);
        check(close(eye, 0, 0, -1), "view: south is ahead: " + str(eye));
        // an eye 3 cm west of the camera sees the camera 3 cm to its left... in its own x
        view = VRMath.viewMatrix(room, VRMath.quatToMatrix(0, 0, 0, 1), new float[]{-0.03f, 0, 0});
        eye = VRMath.transform(view, 0, 0, 0);
        check(close(eye, -0.03f, 0, 0), "view: an eye to the right sees the centre on its left: " + str(eye));
        // the projection: straight ahead is the centre, near and far are -1 and 1
        float[] p = VRMath.projection(-0.8f, 0.8f, 0.8f, -0.8f, 0.05f, 100);
        float[] near = VRMath.transform(p, 0, 0, -0.05f), far = VRMath.transform(p, 0, 0, -100);
        check(Math.abs(near[0]) < 1e-5 && Math.abs(near[2] / near[3] + 1) < 1e-3 && Math.abs(far[2] / far[3] - 1) < 1e-3,
                "projection depth: " + str(near) + " / " + str(far));
        float[] edge = VRMath.transform(p, (float) Math.tan(0.8f), 0, -1);
        check(Math.abs(edge[0] / edge[3] - 1) < 1e-4, "projection: the right edge of the view is x 1: " + str(edge));
    }

    private static boolean close(float[] v, float x, float y, float z) {
        return Math.abs(v[0] - x) < 1e-4 && Math.abs(v[1] - y) < 1e-4 && Math.abs(v[2] - z) < 1e-4;
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
