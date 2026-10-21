package questcraft.legacy.vr;

import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.Properties;

import org.lwjgl.opengl.EXTFramebufferBlit;
import org.lwjgl.opengl.GL11;

import questcraft.legacy.xr.Framebuffers;

/**
 * The headset on a desktop, to check VR without one (-Dquestcraft.legacy.vrsim=true): a head standing at
 * questcraft.legacy.vrsim.x, .height, .z in the room (0, the game's eye height, 0), turned by .yaw and .pitch
 * (degrees), the controllers held in front pointing where the head looks (.aimYaw and .aimPitch turn them, .leftHandX/Y/Z
 * and .rightHandX/Y/Z move them), the
 * sticks at .leftX, .leftY, .rightX, .rightY, the triggers and grips at .leftTrigger, .rightTrigger, .leftGrip,
 * .rightGrip, the buttons at .a, .b, .xButton, .yButton, .menu, .rightStickClick (1 pressed), and both eyes shown side by side in
 * the game's window. On a desktop the buttons reach only VR's own parts (the keyboard), not the game. With
 * questcraft.legacy.vrsim.file, these are read from that properties file as it changes, while the game runs.
 */
final class VRSim implements VRProvider {
    private static final int EYE = Integer.getInteger("questcraft.legacy.vrsim.eye", 800);
    private static final String FILE = System.getProperty("questcraft.legacy.vrsim.file");
    private final int windowWidth, windowHeight;
    private long lastRead;

    VRSim(int windowWidth, int windowHeight) {
        this.windowWidth = windowWidth;
        this.windowHeight = windowHeight;
    }

    public boolean start() {
        System.out.println("QuestCraft VR: simulated headset, eyes " + EYE + " x " + EYE);
        return true;
    }

    public int eyeWidth() {
        return EYE;
    }

    public int eyeHeight() {
        return EYE;
    }

    static float[] yawPitchQuat(float yawDegrees, float pitchDegrees) {
        // the room's rotation: yaw about +y (left positive, as OpenXR), then pitch about +x (up positive)
        double y = Math.toRadians(yawDegrees) / 2, p = Math.toRadians(pitchDegrees) / 2;
        float cy = (float) Math.cos(y), sy = (float) Math.sin(y), cp = (float) Math.cos(p), sp = (float) Math.sin(p);
        // q = qYaw * qPitch
        return new float[]{cy * sp, sy * cp, -sy * sp, cy * cp};
    }

    private static float get(String name, float fallback) {
        String value = System.getProperty("questcraft.legacy.vrsim." + name);
        return value != null ? Float.parseFloat(value.trim()) : fallback;
    }

    public boolean beginFrame(VRFrame frame) {
        if (FILE != null && System.currentTimeMillis() - lastRead > 250) {
            lastRead = System.currentTimeMillis();
            Properties properties = new Properties();
            try (InputStream in = new FileInputStream(FILE)) {
                properties.load(in);
                for (String name : properties.stringPropertyNames()) System.setProperty(name, properties.getProperty(name));
            } catch (IOException e) {
                // not there yet
            }
        }
        float yaw = get("yaw", 0), pitch = get("pitch", 0);
        float aimYaw = get("aimYaw", yaw), aimPitch = get("aimPitch", pitch);
        float[] head = yawPitchQuat(yaw, pitch);
        System.arraycopy(head, 0, frame.headOrientation, 0, 4);
        frame.headPosition[0] = get("x", 0);
        frame.headPosition[1] = get("height", VRMath.EYE_HEIGHT);
        frame.headPosition[2] = get("z", 0);
        float[] right = VRMath.mul3(VRMath.quatToMatrix(head[0], head[1], head[2], head[3]), 1, 0, 0);
        for (int eye = 0; eye < 2; eye++) {
            float side = eye == 0 ? -0.032f : 0.032f;
            for (int i = 0; i < 3; i++) frame.eyePosition[eye][i] = frame.headPosition[i] + right[i] * side;
            System.arraycopy(head, 0, frame.eyeOrientation[eye], 0, 4);
            frame.eyeFov[eye][0] = -0.8f;
            frame.eyeFov[eye][1] = 0.8f;
            frame.eyeFov[eye][2] = 0.8f;
            frame.eyeFov[eye][3] = -0.8f;
        }
        float[] aim = yawPitchQuat(aimYaw, aimPitch);
        // .leftHandX/Y/Z and .rightHandX/Y/Z move a hand from there (metres, in the body's frame)
        float[][] offset = {{-0.2f + get("leftHandX", 0), -0.45f + get("leftHandY", 0), -0.4f + get("leftHandZ", 0)},
                {0.2f + get("rightHandX", 0), -0.45f + get("rightHandY", 0), -0.4f + get("rightHandZ", 0)}};
        // the hands where a person holds them: in front of the body, turned with the head but not tilted with it
        float[] turn = yawPitchQuat(yaw, 0);
        float[] m = VRMath.quatToMatrix(turn[0], turn[1], turn[2], turn[3]);
        for (int hand = 0; hand < 2; hand++) {
            float[] o = VRMath.mul3(m, offset[hand][0], offset[hand][1], offset[hand][2]);
            for (int i = 0; i < 3; i++) {
                frame.aimPosition[hand][i] = frame.headPosition[i] + o[i];
                frame.gripPosition[hand][i] = frame.aimPosition[hand][i];
            }
            System.arraycopy(aim, 0, frame.aimOrientation[hand], 0, 4);
            System.arraycopy(aim, 0, frame.gripOrientation[hand], 0, 4);
            frame.handTracked[hand] = true;
        }
        frame.leftX = get("leftX", 0);
        frame.leftY = get("leftY", 0);
        frame.rightX = get("rightX", 0);
        frame.rightY = get("rightY", 0);
        frame.leftTrigger = get("leftTrigger", 0);
        frame.rightTrigger = get("rightTrigger", 0);
        frame.leftGrip = get("leftGrip", 0);
        frame.rightGrip = get("rightGrip", 0);
        frame.a = get("a", 0) > 0.5f;
        frame.b = get("b", 0) > 0.5f;
        // not .x: that is where the head is
        frame.x = get("xButton", 0) > 0.5f;
        frame.y = get("yButton", 0) > 0.5f;
        frame.menu = get("menu", 0) > 0.5f;
        frame.rightStickClick = get("rightStickClick", 0) > 0.5f;
        frame.headTracked = true;
        frame.shouldRender = true;
        return true;
    }

    public void endFrame(VRFrame frame, int leftTexture, int rightTexture) {
        // both eyes into the window, side by side: read through a framebuffer of their own
        int read = Framebuffers.gen();
        Framebuffers.bind(Framebuffers.DRAW_FRAMEBUFFER, 0);
        GL11.glViewport(0, 0, windowWidth, windowHeight);
        GL11.glClearColor(0, 0, 0, 1);
        GL11.glClear(GL11.GL_COLOR_BUFFER_BIT);
        int half = windowWidth / 2;
        int size = Math.min(half, windowHeight);
        for (int eye = 0; eye < 2; eye++) {
            Framebuffers.bind(Framebuffers.READ_FRAMEBUFFER, read);
            Framebuffers.texture(Framebuffers.READ_FRAMEBUFFER, Framebuffers.COLOR_ATTACHMENT0, eye == 0 ? leftTexture : rightTexture);
            int x = eye * half + (half - size) / 2, y = (windowHeight - size) / 2;
            EXTFramebufferBlit.glBlitFramebufferEXT(0, 0, EYE, EYE, x, y, x + size, y + size, GL11.GL_COLOR_BUFFER_BIT, GL11.GL_LINEAR);
        }
        Framebuffers.bind(Framebuffers.FRAMEBUFFER, 0);
        Framebuffers.delete(read);
    }
}
