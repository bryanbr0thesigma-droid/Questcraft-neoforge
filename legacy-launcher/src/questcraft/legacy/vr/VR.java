package questcraft.legacy.vr;

import java.nio.FloatBuffer;
import java.nio.IntBuffer;

import org.lwjgl.BufferUtils;
import org.lwjgl.opengl.GL11;

import questcraft.legacy.xr.Framebuffers;
import questcraft.legacy.xr.GameFramebuffer;

/**
 * VR for the old versions, the part every version shares: the headset ({@link VRProvider}), each eye's framebuffer,
 * the framebuffer the game's menus and HUD are drawn into and the panels they are shown on in the world, the
 * controllers, and the order of it all in a frame. A version's own code (its "glue", made against that version's
 * classes) puts the game's camera on the headset, aims with the controllers and draws through {@link #beginEye}.
 * <p>
 * A frame: the game renders (the glue calls {@link #beginEye} / {@link #endEyes} around each eye and
 * {@link #beginGui} / {@link #endGui} around its menus and HUD), then Display.update() calls {@link #endFrame}, which
 * hands the eyes to the headset and waits for its next frame, so the poses are fresh when the game renders again.
 */
public final class VR {
    /** The menu panel's width in metres and its distance; its height follows the game's window. */
    static final float MENU_WIDTH = 1.6f, MENU_DISTANCE = 1.5f;
    /** The HUD panel (the hotbar, health): under the view, following the head */
    static final float HUD_WIDTH = 1.4f, HUD_DISTANCE = 1.3f, HUD_DROP = 0.55f;
    /** One snap turn, degrees */
    static final float SNAP = 45;
    /**
     * The HUD on the left wrist (Vivecraft's "wrist" HUD): its width in metres; where its centre is in the left
     * controller's grip frame (above the wrist, back from the hand); how far it leans towards the eyes from lying flat
     * on the forearm; how near the right controller has to come to it to touch a hotbar slot.
     */
    static final float WRIST_WIDTH = 0.24f, WRIST_TILT = (float) Math.toRadians(30), WRIST_TOUCH = 0.06f;
    static final float[] WRIST_OFFSET = {0, 0.05f, 0.1f};

    private static boolean enabled, headset;
    private static VRProvider provider;
    private static boolean started, failed, frameBegun;
    private static final VRFrame frame = new VRFrame();
    /** The game's turn of the room, degrees: snap turns */
    public static float bodyYaw;
    private static GameFramebuffer[] eyes;
    private static GameFramebuffer gui;
    private static GameFramebuffer game;
    private static int windowWidth, windowHeight;
    private static boolean eyesRendered, guiCleared;
    /** The menus or the HUD were drawn into the panel's framebuffer last frame (the HUD can be hidden: F1) */
    private static boolean guiShown;
    /** What it was: a menu (or the HUD) */
    private static boolean guiWasMenu;
    /** Nothing was drawn through the glue this frame (a loading screen): the game's own picture goes on the panel */
    private static boolean showGame;
    private static int currentEye = -1;
    /** A menu is open (set by the glue each frame): the panel in front, the pointer on it */
    private static boolean menuOpen, menuWasOpen;
    private static final float[] menuCenter = new float[3];
    private static float menuYaw;
    private static boolean menuPlaced;
    private static final float[] hudCenter = new float[3];
    private static float hudYaw;
    private static boolean hudPlaced;
    private static boolean pointing;
    private static float pointerU, pointerV;
    /**
     * The part of the HUD that goes on the wrist (health, armor, air, the hotbar), in the panel's pixels from its top
     * left: left, top, right, bottom; and the hotbar's slots: the first one's left, a slot's width, their top. Set by
     * the glue every frame; null: the whole HUD stays in front.
     */
    private static int[] wristPart, hotbarSlots;
    private static boolean wristShown;
    private static final float[] wristCenter = new float[3], wristRight = new float[3], wristUp = new float[3], wristNormal = new float[3];
    private static int touchedSlot = -1;
    private static VRControls controls;
    private static VRKeyboard keyboard;
    /** The game wanted the keyboard last frame (a text screen): it shows when that starts, hides when it ends */
    private static boolean keyboardWanted;

    private VR() {
    }

    /** Turned on by the launcher for a version that has VR glue. */
    public static void enable(int width, int height, boolean headset) {
        enabled = true;
        VR.headset = headset;
        windowWidth = width;
        windowHeight = height;
        provider = headset ? new VRSessionXR() : new VRSim(width, height);
        controls = new VRControls(headset, width, height);
        keyboard = new VRKeyboard(VRControls.keyboardSink(headset));
    }

    /** The game's class loader: the keyboard draws with the game's font. */
    public static void setGameClassLoader(ClassLoader loader) {
        if (keyboard != null) keyboard.setGameClassLoader(loader);
    }

    /**
     * Whether the game asks for text now (set by the glue every frame: chat, a sign, a world's name...). The keyboard
     * shows when that starts and hides when it ends; X in a menu shows or hides it in between.
     */
    public static void setKeyboardWanted(boolean wanted) {
        if (keyboard == null) return;
        if (wanted != keyboardWanted) keyboard.setVisible(wanted);
        keyboardWanted = wanted;
    }

    public static boolean enabled() {
        return enabled && !failed;
    }

    /** VR goes off for good (a hook the glue needs is not in this game): the game is drawn as it always was. */
    public static void disable(String reason) {
        if (failed) return;
        failed = true;
        // what the controllers hold for the game is let go: nothing else ever would
        if (controls != null) controls.releaseAll();
        System.out.println("QuestCraft VR: off, " + reason);
    }

    /** The game's own framebuffer (where it draws outside the eyes and menus), made by the launcher's Presenter. */
    public static void setGameFramebuffer(GameFramebuffer framebuffer) {
        game = framebuffer;
    }

    public static VRFrame frame() {
        return frame;
    }

    /** Starts the headset, the first time: the game's GL context has to be current. */
    private static boolean ensureStarted() {
        if (started || failed) return started;
        if (!provider.start()) {
            failed = true;
            System.out.println("QuestCraft VR: no headset, the game runs on without VR");
            return false;
        }
        started = true;
        eyes = new GameFramebuffer[]{GameFramebuffer.create(provider.eyeWidth(), provider.eyeHeight()),
                GameFramebuffer.create(provider.eyeWidth(), provider.eyeHeight())};
        gui = GameFramebuffer.create(windowWidth, windowHeight);
        bindGame();
        // the game pauses when its window is not focused; on the headset nothing else focuses it
        if (headset) questcraft.legacy.xr.GameInput.focus();
        frameBegun = provider.beginFrame(frame);
        System.out.println("QuestCraft VR: on, eyes " + provider.eyeWidth() + " x " + provider.eyeHeight());
        return true;
    }

    // ---- the world, as seen from the headset ----

    /** The room's axes in the world (3x3, row major) at the current turn. */
    public static float[] room() {
        return VRMath.roomToWorld(bodyYaw);
    }

    /** A point of the room in the world, relative to the game's camera (the player's eyes). */
    public static float[] toWorld(float[] roomPoint) {
        return VRMath.roomPointToWorld(room(), frame.headPosition, roomPoint[0], roomPoint[1], roomPoint[2]);
    }

    /** The direction {@code (x, y, z)} of something oriented as {@code quaternion} in the room, in the world. */
    public static float[] directionToWorld(float[] quaternion, float x, float y, float z) {
        float[] local = VRMath.mul3(VRMath.quatToMatrix(quaternion[0], quaternion[1], quaternion[2], quaternion[3]), x, y, z);
        return VRMath.mul3(room(), local[0], local[1], local[2]);
    }

    /** The game's yaw and pitch (degrees) of where the head looks. */
    public static float[] headYawPitch() {
        return VRMath.yawPitch(directionToWorld(frame.headOrientation, 0, 0, -1));
    }

    /** The eye's model-view matrix (column major): from the world relative to the camera into the eye. */
    public static float[] eyeView(int eye) {
        float[] q = frame.eyeOrientation[eye];
        return VRMath.viewMatrix(room(), VRMath.quatToMatrix(q[0], q[1], q[2], q[3]), toWorld(frame.eyePosition[eye]));
    }

    public static float[] eyeProjection(int eye, float near, float far) {
        float[] fov = frame.eyeFov[eye];
        return VRMath.projection(fov[0], fov[1], fov[2], fov[3], near, far);
    }

    // ---- drawing ----

    /** Draws into an eye (0 left, 1 right) from now on. False without VR: nothing changed. */
    public static boolean beginEye(int eye) {
        if (!enabled() || !ensureStarted()) return false;
        currentEye = eye;
        Framebuffers.bind(Framebuffers.FRAMEBUFFER, eyes[eye].framebuffer);
        GL11.glViewport(0, 0, eyes[eye].width, eyes[eye].height);
        return true;
    }

    public static int currentEye() {
        return currentEye;
    }

    /** The size of the eye being drawn: the glue's viewport. */
    public static int eyeWidth() {
        return eyes != null ? eyes[0].width : windowWidth;
    }

    public static int eyeHeight() {
        return eyes != null ? eyes[0].height : windowHeight;
    }

    /** Back to the game's own framebuffer after the eyes. */
    public static void endEyes() {
        if (currentEye >= 0) eyesRendered = true;
        currentEye = -1;
        bindGame();
    }

    private static void bindGame() {
        Framebuffers.bind(Framebuffers.FRAMEBUFFER, game != null ? game.framebuffer : 0);
        GL11.glViewport(0, 0, windowWidth, windowHeight);
    }

    /** The game's menus or HUD are drawn from now on: into the panel's framebuffer, cleared once a frame. */
    public static void beginGui() {
        if (!enabled() || !ensureStarted()) return;
        Framebuffers.bind(Framebuffers.FRAMEBUFFER, gui.framebuffer);
        GL11.glViewport(0, 0, windowWidth, windowHeight);
        if (!guiCleared) {
            guiCleared = true;
            FloatBuffer clear = BufferUtils.createFloatBuffer(16);
            GL11.glGetFloat(GL11.GL_COLOR_CLEAR_VALUE, clear);
            GL11.glClearColor(0, 0, 0, 0);
            GL11.glClear(GL11.GL_COLOR_BUFFER_BIT);
            GL11.glClearColor(clear.get(0), clear.get(1), clear.get(2), clear.get(3));
        }
        // the HUD's items and a menu over it are drawn with depth, each on its own
        GL11.glClear(GL11.GL_DEPTH_BUFFER_BIT);
    }

    public static void endGui() {
        if (!enabled() || !started) return;
        if (menuOpen && pointing) dot((int) (pointerU * windowWidth), windowHeight - (int) (pointerV * windowHeight));
        bindGame();
    }

    /** Whether a menu is open: set by the glue every frame. */
    public static void setMenuOpen(boolean open) {
        menuOpen = open;
    }

    public static boolean menuOpen() {
        return menuOpen;
    }

    /**
     * Where the wrist HUD's part of the HUD is (window pixels from the top left: left, top, right, bottom) and its hotbar
     * slots (the first slot's left, a slot's width, the slots' top), every frame from the glue.
     */
    public static void setWristHud(int left, int top, int right, int bottom, int slotsLeft, int slotWidth, int slotsTop) {
        wristPart = new int[]{left, top, right, bottom};
        hotbarSlots = new int[]{slotsLeft, slotWidth, slotsTop};
    }

    /** The hotbar slot the right controller touches on the wrist HUD (-1 none). */
    public static int touchedSlot() {
        return touchedSlot;
    }

    /** Whether the right controller points at the menu panel (not off it, not at the keyboard): the cursor is its. */
    public static boolean pointing() {
        return pointing;
    }

    /**
     * Draws the panel the menus (or, without one, the HUD) are on, in the eye being drawn: over everything, as the
     * menu should not hide in a wall. The GL state it changes is put back.
     */
    public static void drawPanels(int eye) {
        if (!started || gui == null) return;
        boolean menu = menuOpen || showGame;
        float[] center = menu ? menuCenter : hudCenter;
        float yaw = menu ? menuYaw : hudYaw;
        float width = menu ? MENU_WIDTH : HUD_WIDTH;
        float height = width * windowHeight / windowWidth;
        float tilt = menu ? 0 : (float) Math.toRadians(25);   // the HUD leans back towards the eyes
        // the panel's picture is last frame's: none, if nothing was drawn into it
        boolean panel = showGame || (guiShown && guiWasMenu == menuOpen);

        // the panel's corners in the room, then in the world
        float c = (float) Math.cos(yaw), s = (float) Math.sin(yaw);
        float[] right = {c, 0, -s};
        // leaning back: the top edge away from the eyes (the panel's front, +z, tilted up)
        float[] up = {(float) (-Math.sin(tilt) * s), (float) Math.cos(tilt), (float) (-Math.sin(tilt) * c)};
        float[][] corners = new float[4][];
        float[][] signs = {{-1, -1}, {1, -1}, {1, 1}, {-1, 1}};
        for (int i = 0; i < 4; i++) {
            float[] p = new float[3];
            for (int k = 0; k < 3; k++) p[k] = center[k] + right[k] * width / 2 * signs[i][0] + up[k] * height / 2 * signs[i][1];
            corners[i] = toWorld(p);
        }

        GL11.glPushAttrib(GL11.GL_ENABLE_BIT | GL11.GL_COLOR_BUFFER_BIT | GL11.GL_DEPTH_BUFFER_BIT | GL11.GL_TEXTURE_BIT | GL11.GL_CURRENT_BIT
                | GL11.GL_LINE_BIT);
        GL11.glMatrixMode(GL11.GL_PROJECTION);
        GL11.glPushMatrix();
        GL11.glLoadMatrix(buffer(eyeProjection(eye, 0.05f, 100f)));
        GL11.glMatrixMode(GL11.GL_MODELVIEW);
        GL11.glPushMatrix();
        GL11.glLoadMatrix(buffer(eyeView(eye)));
        GL11.glDisable(GL11.GL_DEPTH_TEST);
        GL11.glDisable(GL11.GL_CULL_FACE);
        GL11.glDisable(GL11.GL_LIGHTING);
        GL11.glDisable(GL11.GL_FOG);
        GL11.glDisable(GL11.GL_ALPHA_TEST);
        GL11.glEnable(GL11.GL_BLEND);
        GL11.glBlendFunc(GL11.GL_SRC_ALPHA, GL11.GL_ONE_MINUS_SRC_ALPHA);
        GL11.glEnable(GL11.GL_TEXTURE_2D);
        GL11.glBindTexture(GL11.GL_TEXTURE_2D, showGame && game != null ? game.texture : gui.texture);
        GL11.glColor4f(1, 1, 1, 1);
        // the panel pictures already hold colour times alpha (the game blends into a clear framebuffer)
        GL11.glBlendFunc(GL11.GL_ONE, GL11.GL_ONE_MINUS_SRC_ALPHA);
        float[][] uv = {{0, 0}, {1, 0}, {1, 1}, {0, 1}};
        if (panel && !menu && wristShown) {
            // the HUD in front without what is on the wrist (the bottom middle): the strip above it, and beside it
            float u0 = (float) wristPart[0] / windowWidth, u1 = (float) wristPart[2] / windowWidth, v1 = 1 - (float) wristPart[1] / windowHeight;
            panelPart(center, right, up, width, height, 0, v1, 1, 1, 0, v1, 1, 1);
            panelPart(center, right, up, width, height, 0, 0, u0, v1, 0, 0, u0, v1);
            panelPart(center, right, up, width, height, u1, 0, 1, v1, u1, 0, 1, v1);
            // and on the wrist
            float wristHeight = WRIST_WIDTH * (wristPart[3] - wristPart[1]) / (wristPart[2] - wristPart[0]);
            panelPart(wristCenter, wristRight, wristUp, WRIST_WIDTH, wristHeight, 0, 0, 1, 1,
                    u0, 1 - (float) wristPart[3] / windowHeight, u1, v1);
        } else if (panel) {
            GL11.glBegin(GL11.GL_QUADS);
            for (int i = 0; i < 4; i++) {
                GL11.glTexCoord2f(uv[i][0], uv[i][1]);
                GL11.glVertex3f(corners[i][0], corners[i][1], corners[i][2]);
            }
            GL11.glEnd();
        }
        // not under a loading screen: no menu is there, whatever the last one was
        boolean keys = menuOpen && !showGame && keyboard.visible() && keyboard.texture() != 0;
        if (keys) {
            // the keyboard, under the menu
            GL11.glBindTexture(GL11.GL_TEXTURE_2D, keyboard.texture());
            quad(keyboard.center(), keyboard.right(), keyboard.up(), keyboard.width(), keyboard.height());
        }
        GL11.glDisable(GL11.GL_TEXTURE_2D);
        GL11.glBlendFunc(GL11.GL_SRC_ALPHA, GL11.GL_ONE_MINUS_SRC_ALPHA);
        GL11.glLineWidth(2);
        GL11.glColor4f(1, 1, 1, 0.6f);
        GL11.glBegin(GL11.GL_LINES);
        if (menuOpen && pointing) {
            // the pointer's line, from the right controller to its dot on the menu
            line(frame.aimPosition[1], pointerHit());
        }
        for (int hand = 0; keys && hand < 2; hand++) {
            if (keyboard.onKeyboard(hand)) line(frame.aimPosition[hand], keyboard.hitPoint(hand));
        }
        GL11.glEnd();
        GL11.glPopMatrix();
        GL11.glMatrixMode(GL11.GL_PROJECTION);
        GL11.glPopMatrix();
        GL11.glMatrixMode(GL11.GL_MODELVIEW);
        GL11.glPopAttrib();
    }

    /**
     * A part of a panel (centre, right and up vectors, size; in the room): from pu0, pv0 to pu1, pv1 of it (0..1 from its
     * bottom left), showing u0, v0 to u1, v1 of the bound texture.
     */
    private static void panelPart(float[] center, float[] right, float[] up, float width, float height,
                                  float pu0, float pv0, float pu1, float pv1, float u0, float v0, float u1, float v1) {
        float[][] p = {{pu0, pv0}, {pu1, pv0}, {pu1, pv1}, {pu0, pv1}};
        float[][] t = {{u0, v0}, {u1, v0}, {u1, v1}, {u0, v1}};
        GL11.glBegin(GL11.GL_QUADS);
        for (int i = 0; i < 4; i++) {
            float[] point = new float[3];
            for (int k = 0; k < 3; k++) point[k] = center[k] + right[k] * (p[i][0] - 0.5f) * width + up[k] * (p[i][1] - 0.5f) * height;
            float[] w = toWorld(point);
            GL11.glTexCoord2f(t[i][0], t[i][1]);
            GL11.glVertex3f(w[0], w[1], w[2]);
        }
        GL11.glEnd();
    }

    /**
     * The wrist HUD's place, from the left controller's pose: above the wrist, lying on the forearm and leaning towards
     * the eyes, its left to right across the arm (it reads as a watch does). Shown while the HUD is (no menu, HUD on)
     * and the left controller is tracked.
     */
    private static void placeWrist() {
        wristShown = wristPart != null && !menuOpen && !showGame && guiShown && !guiWasMenu && frame.handTracked[0];
        touchedSlot = -1;
        if (!wristShown) return;
        float[] q = frame.gripOrientation[0];
        float[] m = VRMath.quatToMatrix(q[0], q[1], q[2], q[3]);
        float c = (float) Math.cos(WRIST_TILT), s = (float) Math.sin(WRIST_TILT);
        // in the controller's frame: right +x, up (the HUD's top) forward and a little up, the face up and towards the eyes
        float[] right = VRMath.mul3(m, 1, 0, 0), up = VRMath.mul3(m, 0, s, -c), normal = VRMath.mul3(m, 0, c, s);
        float[] offset = VRMath.mul3(m, WRIST_OFFSET[0], WRIST_OFFSET[1], WRIST_OFFSET[2]);
        for (int k = 0; k < 3; k++) {
            wristCenter[k] = frame.gripPosition[0][k] + offset[k];
            wristRight[k] = right[k];
            wristUp[k] = up[k];
            wristNormal[k] = normal[k];
        }
        // the right controller's tip on a hotbar slot: that slot (as Vivecraft's wrist HUD)
        if (!frame.handTracked[1] || hotbarSlots == null) return;
        float wristHeight = WRIST_WIDTH * (wristPart[3] - wristPart[1]) / (wristPart[2] - wristPart[0]);
        float[] d = new float[3];
        for (int k = 0; k < 3; k++) d[k] = frame.aimPosition[1][k] - wristCenter[k];
        float u = dot(d, wristRight) / WRIST_WIDTH + 0.5f, v = dot(d, wristUp) / wristHeight + 0.5f;
        if (Math.abs(dot(d, wristNormal)) > WRIST_TOUCH || u < 0 || u > 1 || v < 0 || v > 1) return;
        float x = wristPart[0] + u * (wristPart[2] - wristPart[0]), y = wristPart[3] - v * (wristPart[3] - wristPart[1]);
        if (y < hotbarSlots[2]) return;
        int slot = (int) Math.floor((x - hotbarSlots[0]) / hotbarSlots[1]);
        if (slot >= 0 && slot < 9) touchedSlot = slot;
    }

    private static float dot(float[] a, float[] b) {
        return a[0] * b[0] + a[1] * b[1] + a[2] * b[2];
    }

    /** A textured quad (centre, right and up vectors, size; in the room), the texture bound. */
    private static void quad(float[] center, float[] right, float[] up, float width, float height) {
        float[][] signs = {{-1, -1}, {1, -1}, {1, 1}, {-1, 1}};
        float[][] uv = {{0, 0}, {1, 0}, {1, 1}, {0, 1}};
        GL11.glBegin(GL11.GL_QUADS);
        for (int i = 0; i < 4; i++) {
            float[] p = new float[3];
            for (int k = 0; k < 3; k++) p[k] = center[k] + right[k] * width / 2 * signs[i][0] + up[k] * height / 2 * signs[i][1];
            float[] w = toWorld(p);
            GL11.glTexCoord2f(uv[i][0], uv[i][1]);
            GL11.glVertex3f(w[0], w[1], w[2]);
        }
        GL11.glEnd();
    }

    /** A line between two points of the room (inside glBegin(GL_LINES)). */
    private static void line(float[] from, float[] to) {
        float[] a = toWorld(from), b = toWorld(to);
        GL11.glVertex3f(a[0], a[1], a[2]);
        GL11.glVertex3f(b[0], b[1], b[2]);
    }

    private static FloatBuffer buffer(float[] m) {
        FloatBuffer b = BufferUtils.createFloatBuffer(16);
        b.put(m).flip();
        return b;
    }

    /** The pointer's dot on the menu: a white square with a dark edge, cleared into the panel's framebuffer. */
    private static void dot(int x, int y) {
        IntBuffer box = BufferUtils.createIntBuffer(16);
        GL11.glGetInteger(GL11.GL_SCISSOR_BOX, box);
        FloatBuffer clear = BufferUtils.createFloatBuffer(16);
        GL11.glGetFloat(GL11.GL_COLOR_CLEAR_VALUE, clear);
        boolean scissor = GL11.glIsEnabled(GL11.GL_SCISSOR_TEST);
        GL11.glEnable(GL11.GL_SCISSOR_TEST);
        GL11.glScissor(x - 6, y - 6, 12, 12);
        GL11.glClearColor(0.1f, 0.1f, 0.1f, 1);
        GL11.glClear(GL11.GL_COLOR_BUFFER_BIT);
        GL11.glScissor(x - 4, y - 4, 8, 8);
        GL11.glClearColor(1, 1, 1, 1);
        GL11.glClear(GL11.GL_COLOR_BUFFER_BIT);
        if (!scissor) GL11.glDisable(GL11.GL_SCISSOR_TEST);
        GL11.glScissor(box.get(0), box.get(1), box.get(2), box.get(3));
        GL11.glClearColor(clear.get(0), clear.get(1), clear.get(2), clear.get(3));
    }

    // ---- the frame ----

    /** The end of a game frame (Display.update): the eyes to the headset, the controllers to the game, the next frame. */
    public static void endFrame() {
        if (!enabled() || !ensureStarted()) return;
        // neither the world nor a menu through the glue: a loading screen, drawn by the game into its own picture
        showGame = !eyesRendered && !guiCleared && game != null;
        placePanels();
        if (!menuOpen) keyboard.setVisible(false);
        keyboard.place(menuCenter, menuYaw);
        if (!showGame) keyboard.render();
        bindGame();
        if (frameBegun) {
            if (!eyesRendered) menuEyes();
            provider.endFrame(frame, eyes[0].texture, eyes[1].texture);
        }
        eyesRendered = false;
        guiShown = guiCleared;
        guiWasMenu = menuOpen;
        guiCleared = false;
        frameBegun = provider.beginFrame(frame);
        updatePointer();
        placeWrist();
        // a loading screen (no menu this frame) types nothing
        if (showGame) keyboard.updatePointers(null);
        else keyboard.updatePointers(frame);
        // the right controller on the keyboard does not move the menu's cursor
        if (keyboard.onKeyboard(1)) pointing = false;
        controls.update(frame, menuOpen, pointing, pointerU, pointerV, keyboard);
        bodyYaw += controls.takeTurn() * SNAP;
        menuWasOpen = menuOpen || showGame;
        bindGame();
    }

    /** Without a world (the title screen and its menus): each eye is a dark room with the menu panel in it. */
    private static void menuEyes() {
        for (int eye = 0; eye < 2; eye++) {
            Framebuffers.bind(Framebuffers.FRAMEBUFFER, eyes[eye].framebuffer);
            GL11.glViewport(0, 0, eyes[eye].width, eyes[eye].height);
            GL11.glClearColor(0.06f, 0.06f, 0.09f, 1);
            GL11.glClear(GL11.GL_COLOR_BUFFER_BIT | GL11.GL_DEPTH_BUFFER_BIT);
            drawPanels(eye);
        }
        bindGame();
    }

    /** The menu panel in front of the head when a menu opens; the HUD follows the head's turn, a bit behind it. */
    private static void placePanels() {
        float[] forward = VRMath.mul3(VRMath.quatToMatrix(frame.headOrientation[0], frame.headOrientation[1],
                frame.headOrientation[2], frame.headOrientation[3]), 0, 0, -1);
        float length = (float) Math.hypot(forward[0], forward[2]);
        float fx = length > 1e-3f ? forward[0] / length : 0, fz = length > 1e-3f ? forward[2] / length : -1;
        float headYaw = (float) Math.atan2(-fx, -fz);
        if ((menuOpen || showGame) && (!menuWasOpen || !menuPlaced)) {
            menuCenter[0] = frame.headPosition[0] + fx * MENU_DISTANCE;
            menuCenter[1] = frame.headPosition[1];
            menuCenter[2] = frame.headPosition[2] + fz * MENU_DISTANCE;
            menuYaw = headYaw;
            menuPlaced = true;
        }
        // the HUD turns with the head when it has turned well away from it, smoothly
        if (!hudPlaced) {
            hudYaw = headYaw;
            hudPlaced = true;
        }
        float difference = (float) Math.atan2(Math.sin(headYaw - hudYaw), Math.cos(headYaw - hudYaw));
        if (Math.abs(difference) > Math.toRadians(30)) hudYaw += difference * 0.1f;
        float hx = (float) -Math.sin(hudYaw), hz = (float) -Math.cos(hudYaw);
        hudCenter[0] = frame.headPosition[0] + hx * HUD_DISTANCE;
        hudCenter[1] = frame.headPosition[1] - HUD_DROP;
        hudCenter[2] = frame.headPosition[2] + hz * HUD_DISTANCE;
    }

    /** Where the right controller's pointer meets the menu panel, in the room. */
    private static float[] pointerHit() {
        float height = MENU_WIDTH * windowHeight / windowWidth;
        float c = (float) Math.cos(menuYaw), s = (float) Math.sin(menuYaw);
        float x = (pointerU - 0.5f) * MENU_WIDTH, y = (0.5f - pointerV) * height;
        return new float[]{menuCenter[0] + c * x, menuCenter[1] + y, menuCenter[2] - s * x};
    }

    /** Where the right controller points on the menu panel (u, v from its top left). */
    private static void updatePointer() {
        pointing = false;
        if (!menuOpen || !menuPlaced || !frame.handTracked[1]) return;
        float[] q = frame.aimOrientation[1];
        float[] direction = VRMath.mul3(VRMath.quatToMatrix(q[0], q[1], q[2], q[3]), 0, 0, -1);
        float height = MENU_WIDTH * windowHeight / windowWidth;
        float[] hit = questcraft.legacy.xr.ScreenMath.hit(menuCenter, menuYaw, MENU_WIDTH, height, frame.aimPosition[1], direction);
        if (hit != null) {
            pointing = true;
            pointerU = hit[0];
            pointerV = hit[1];
        }
    }

    /** Turns the room by snap turns (+1 right, -1 left): the radial menu's rotate items. */
    public static void turn(int steps) {
        bodyYaw += steps * SNAP;
    }

    /** How the left stick wants the player to move: forward and sideways, -1..1 (the glue's movement input). */
    public static float moveForward() {
        return controls.moveForward();
    }

    public static float moveSideways() {
        return controls.moveSideways();
    }
}
