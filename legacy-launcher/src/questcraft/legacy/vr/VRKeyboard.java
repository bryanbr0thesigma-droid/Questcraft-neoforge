package questcraft.legacy.vr;

import java.awt.image.BufferedImage;
import java.io.InputStream;
import java.nio.ByteBuffer;

import javax.imageio.ImageIO;

import org.lwjgl.BufferUtils;
import org.lwjgl.opengl.GL11;

import questcraft.legacy.xr.Framebuffers;
import questcraft.legacy.xr.GameFramebuffer;

/**
 * The keyboard in VR, as Vivecraft's: a panel under the menu panel, leaning back, shown when the game asks for text
 * (the glue says which screens) or with X in a menu. Either controller points at it and its trigger types the key it
 * points at. Every key is pressed and let go as a real keyboard's key (so the Controls screen binds it), and letters,
 * digits, signs, Enter and Tab are also typed as characters (text fields read those; some screens read Enter's key,
 * some its character). Shift is for the next key only. The labels are drawn with the game's own font
 * (/font/default.png, ASCII in a 16 x 16 grid), so every old version gets the same keyboard; a character the game
 * cannot type (not in its /font.txt) is greyed out.
 */
final class VRKeyboard {
    /** The panel's width in metres; how far under and in front of the menu panel's centre it is; its lean */
    static final float WIDTH = 1.2f, DROP = 0.7f, CLOSER = 0.35f, TILT = (float) Math.toRadians(35);
    /** Its picture */
    static final int TEX_WIDTH = 1024, TEX_HEIGHT = 384;
    static final int GLFW_ENTER = 257, GLFW_TAB = 258, GLFW_BACKSPACE = 259;
    /** Backspace held: the first repeat after this long, then every REPEAT seconds */
    static final float REPEAT_DELAY = 0.5f, REPEAT = 0.07f;

    /** Where the typing goes: the headset's GLFW stand-in; null on a desktop, where typed keys are only logged */
    interface Sink {
        void key(int glfwKey, boolean down);

        void character(int codepoint);
    }

    /** A key: where it is (in key widths, 15 to a row, 5 rows), what it types */
    static final class Key {
        final float x, y, w;
        final String label, shiftLabel;
        final int glfw;     // Backspace, Enter, Tab; 0 for a character key
        final char ch, shiftCh;
        final boolean shift, hide;

        Key(float x, float y, float w, String label, String shiftLabel, int glfw, char ch, char shiftCh, boolean shift, boolean hide) {
            this.x = x;
            this.y = y;
            this.w = w;
            this.label = label;
            this.shiftLabel = shiftLabel;
            this.glfw = glfw;
            this.ch = ch;
            this.shiftCh = shiftCh;
            this.shift = shift;
            this.hide = hide;
        }
    }

    static final Key[] KEYS = layout();

    private final Sink sink;
    private boolean visible, shift;
    private final float[] center = new float[3], right = new float[3], up = new float[3];
    private final float height = WIDTH * TEX_HEIGHT / TEX_WIDTH;
    /** Per hand: the key pointed at (-1 none), where (u, v), the trigger as the keyboard sees it */
    private final int[] hovered = {-1, -1};
    private final float[][] hit = new float[2][];
    private final boolean[] pressed = new boolean[2];
    private final float[] held = new float[2], nextRepeat = new float[2];
    private final int[] pressedKey = {-1, -1};
    private GameFramebuffer picture;
    private int font = -1;
    /** Each character's width in the font, in its 8 pixel cell (measured from the picture, as the game does) */
    private final int[] glyphWidth = new int[128];
    private ClassLoader gameClassLoader;
    /** The characters the game's text fields take (its font.txt), null if it does not say: all */
    private String allowed;
    private String typedLog = "";

    VRKeyboard(Sink sink) {
        this.sink = sink;
    }

    void setGameClassLoader(ClassLoader loader) {
        gameClassLoader = loader;
        allowed = readAllowed(loader);
    }

    /** The characters of the game's font.txt (lines starting with # are comments), as its text fields read them. */
    static String readAllowed(ClassLoader loader) {
        try (InputStream in = loader != null ? loader.getResourceAsStream("font.txt") : null) {
            if (in == null) return null;
            java.io.BufferedReader reader = new java.io.BufferedReader(new java.io.InputStreamReader(in, "UTF-8"));
            StringBuilder chars = new StringBuilder();
            for (String line; (line = reader.readLine()) != null; ) {
                if (!line.startsWith("#")) chars.append(line);
            }
            return chars.toString();
        } catch (Throwable t) {
            return null;
        }
    }

    /** Whether the game can type this character (Space, Enter and Tab always reach it). */
    boolean typeable(char c) {
        return c == ' ' || c == '\r' || c == '\t' || allowed == null || allowed.indexOf(c) >= 0;
    }

    static Key[] layout() {
        java.util.List<Key> keys = new java.util.ArrayList<>();
        // row 0: ` 1 .. 0 - = Backspace
        String row0 = "`1234567890-=", row0s = "~!@#$%^&*()_+";
        for (int i = 0; i < row0.length(); i++) keys.add(chr(i, 0, 1, row0.charAt(i), row0s.charAt(i)));
        keys.add(new Key(13, 0, 2, "Back", "Back", GLFW_BACKSPACE, '\0', '\0', false, false));
        // row 1: Tab q .. ] \
        keys.add(new Key(0, 1, 1.5f, "Tab", "Tab", GLFW_TAB, '\t', '\t', false, false));
        String row1 = "qwertyuiop[]", row1s = "QWERTYUIOP{}";
        for (int i = 0; i < row1.length(); i++) keys.add(chr(1.5f + i, 1, 1, row1.charAt(i), row1s.charAt(i)));
        keys.add(chr(13.5f, 1, 1.5f, '\\', '|'));
        // row 2: a .. ' Enter
        String row2 = "asdfghjkl;'", row2s = "ASDFGHJKL:\"";
        for (int i = 0; i < row2.length(); i++) keys.add(chr(1.75f + i, 2, 1, row2.charAt(i), row2s.charAt(i)));
        keys.add(new Key(12.75f, 2, 2.25f, "Enter", "Enter", GLFW_ENTER, '\r', '\r', false, false));
        // row 3: Shift z .. / Shift
        keys.add(new Key(0, 3, 2.25f, "Shift", "Shift", 0, '\0', '\0', true, false));
        String row3 = "zxcvbnm,./", row3s = "ZXCVBNM<>?";
        for (int i = 0; i < row3.length(); i++) keys.add(chr(2.25f + i, 3, 1, row3.charAt(i), row3s.charAt(i)));
        keys.add(new Key(12.25f, 3, 2.75f, "Shift", "Shift", 0, '\0', '\0', true, false));
        // row 4: Space, Hide
        keys.add(new Key(3, 4, 9, "Space", "Space", 0, ' ', ' ', false, false));
        keys.add(new Key(12.5f, 4, 2.5f, "Hide", "Hide", 0, '\0', '\0', false, true));
        return keys.toArray(new Key[0]);
    }

    private static Key chr(float x, float y, float w, char ch, char shiftCh) {
        return new Key(x, y, w, String.valueOf(ch), String.valueOf(shiftCh), 0, ch, shiftCh, false, false);
    }

    void setVisible(boolean visible) {
        if (this.visible != visible) {
            this.visible = visible;
            shift = false;
            for (int hand = 0; hand < 2; hand++) {
                hovered[hand] = -1;
                hit[hand] = null;
                // a trigger held as it shows (or hides) types nothing until it is let go and pressed again
                pressed[hand] = true;
                pressedKey[hand] = -1;
            }
        }
    }

    boolean visible() {
        return visible;
    }

    // ---- where it is ----

    /** Under the menu panel (centre, turned by yaw), leaning back. */
    void place(float[] menuCenter, float menuYaw) {
        float c = (float) Math.cos(menuYaw), s = (float) Math.sin(menuYaw);
        float fx = -s, fz = -c;     // the menu's "ahead": away from the eyes that placed it
        right[0] = c;
        right[1] = 0;
        right[2] = -s;
        up[0] = (float) (-Math.sin(TILT) * s);
        up[1] = (float) Math.cos(TILT);
        up[2] = (float) (-Math.sin(TILT) * c);
        center[0] = menuCenter[0] - fx * CLOSER;
        center[1] = menuCenter[1] - DROP;
        center[2] = menuCenter[2] - fz * CLOSER;
    }

    float[] center() {
        return center;
    }

    float[] right() {
        return right;
    }

    float[] up() {
        return up;
    }

    float width() {
        return WIDTH;
    }

    float height() {
        return height;
    }

    /** Which key each controller points at (null: neither points at it). */
    void updatePointers(VRFrame frame) {
        for (int hand = 0; hand < 2; hand++) {
            hovered[hand] = -1;
            hit[hand] = null;
            if (!visible || frame == null || !frame.handTracked[hand]) continue;
            float[] q = frame.aimOrientation[hand];
            float[] direction = VRMath.mul3(VRMath.quatToMatrix(q[0], q[1], q[2], q[3]), 0, 0, -1);
            float[] uv = VRMath.hitPanel(center, right, up, WIDTH, height, frame.aimPosition[hand], direction);
            if (uv == null) continue;
            hit[hand] = uv;
            hovered[hand] = keyAt(uv[0] * 15, uv[1] * 5);
        }
    }

    /** The key at x, y in key units, or -1 (the gaps between keys still count as the key: easier to hit). */
    static int keyAt(float x, float y) {
        for (int i = 0; i < KEYS.length; i++) {
            Key k = KEYS[i];
            if (x >= k.x && x < k.x + k.w && y >= k.y && y < k.y + 1) return i;
        }
        return -1;
    }

    /** The controller points at the keyboard: its trigger types instead of clicking. */
    boolean onKeyboard(int hand) {
        return visible && hit[hand] != null;
    }

    /** Where the controller's pointer meets the keyboard, in the room (null if it does not). */
    float[] hitPoint(int hand) {
        float[] uv = hit[hand];
        if (uv == null) return null;
        float x = (uv[0] - 0.5f) * WIDTH, y = (0.5f - uv[1]) * height;
        return new float[]{center[0] + right[0] * x + up[0] * y, center[1] + right[1] * x + up[1] * y, center[2] + right[2] * x + up[2] * y};
    }

    // ---- typing ----

    /** A controller's trigger, each frame: a press types the key pointed at, Backspace repeats while held. */
    void trigger(int hand, boolean down, float seconds) {
        if (down && !pressed[hand]) {
            pressedKey[hand] = hovered[hand];
            held[hand] = 0;
            nextRepeat[hand] = REPEAT_DELAY;
            if (pressedKey[hand] >= 0) type(KEYS[pressedKey[hand]]);
        } else if (down && pressedKey[hand] >= 0 && KEYS[pressedKey[hand]].glfw == GLFW_BACKSPACE && hovered[hand] == pressedKey[hand]) {
            held[hand] += seconds;
            if (held[hand] >= nextRepeat[hand]) {
                nextRepeat[hand] += REPEAT;
                type(KEYS[pressedKey[hand]]);
            }
        }
        if (!down) pressedKey[hand] = -1;
        pressed[hand] = down;
    }

    void type(Key key) {
        if (key.hide) {
            setVisible(false);
            return;
        }
        if (key.shift) {
            shift = !shift;
            return;
        }
        if (key.glfw != 0) {
            if (sink != null) {
                sink.key(key.glfw, true);
                if (key.ch != '\0') sink.character(key.ch);
                sink.key(key.glfw, false);
            }
            log(key.label);
        } else {
            char c = shift ? key.shiftCh : key.ch;
            if (!typeable(c)) return;
            // its key (GLFW's printable keys are their unshifted ASCII, letters upper case), then the character
            int glfw = Character.toUpperCase(key.ch);
            if (sink != null) {
                sink.key(glfw, true);
                sink.character(c);
                sink.key(glfw, false);
            }
            log(String.valueOf(c));
            shift = false;
        }
    }

    /** On a desktop nothing is typed into the game: the keys are logged (the VR check). */
    private void log(String what) {
        if (sink != null) return;
        typedLog += what.length() > 1 ? "[" + what + "]" : what;
        System.out.println("QuestCraft VR: keyboard typed " + typedLog);
    }

    // ---- its picture ----

    int texture() {
        return picture != null ? picture.texture : 0;
    }

    /** Draws the keyboard into its picture (the GL state it changes is put back, the caller binds its framebuffer again). */
    void render() {
        if (!visible) return;
        if (picture == null) picture = GameFramebuffer.create(TEX_WIDTH, TEX_HEIGHT);
        if (font < 0) font = loadFont();
        GL11.glPushAttrib(GL11.GL_ENABLE_BIT | GL11.GL_COLOR_BUFFER_BIT | GL11.GL_TEXTURE_BIT | GL11.GL_CURRENT_BIT | GL11.GL_VIEWPORT_BIT
                | GL11.GL_TRANSFORM_BIT | GL11.GL_DEPTH_BUFFER_BIT);
        Framebuffers.bind(Framebuffers.FRAMEBUFFER, picture.framebuffer);
        GL11.glViewport(0, 0, TEX_WIDTH, TEX_HEIGHT);
        GL11.glMatrixMode(GL11.GL_PROJECTION);
        GL11.glPushMatrix();
        GL11.glLoadIdentity();
        GL11.glOrtho(0, TEX_WIDTH, TEX_HEIGHT, 0, -1, 1);
        GL11.glMatrixMode(GL11.GL_MODELVIEW);
        GL11.glPushMatrix();
        GL11.glLoadIdentity();
        GL11.glDisable(GL11.GL_DEPTH_TEST);
        GL11.glDisable(GL11.GL_CULL_FACE);
        GL11.glDisable(GL11.GL_LIGHTING);
        GL11.glDisable(GL11.GL_FOG);
        GL11.glDisable(GL11.GL_ALPHA_TEST);
        GL11.glEnable(GL11.GL_BLEND);
        GL11.glBlendFunc(GL11.GL_SRC_ALPHA, GL11.GL_ONE_MINUS_SRC_ALPHA);
        GL11.glClearColor(0.05f, 0.05f, 0.07f, 0.85f);
        GL11.glClear(GL11.GL_COLOR_BUFFER_BIT);

        float unit = TEX_WIDTH / 15f, row = TEX_HEIGHT / 5f, gap = 4;
        GL11.glDisable(GL11.GL_TEXTURE_2D);
        for (int i = 0; i < KEYS.length; i++) {
            Key k = KEYS[i];
            if (i == hovered[1] && i == hovered[0]) GL11.glColor4f(0.55f, 0.6f, 0.75f, 1);
            else if (i == hovered[1]) GL11.glColor4f(0.35f, 0.5f, 0.85f, 1);
            else if (i == hovered[0]) GL11.glColor4f(0.35f, 0.7f, 0.45f, 1);
            else if (k.shift && shift) GL11.glColor4f(0.6f, 0.55f, 0.3f, 1);
            else GL11.glColor4f(0.24f, 0.24f, 0.27f, 1);
            rect(k.x * unit + gap, k.y * row + gap, k.w * unit - 2 * gap, row - 2 * gap);
        }
        if (font > 0) {
            GL11.glEnable(GL11.GL_TEXTURE_2D);
            GL11.glBindTexture(GL11.GL_TEXTURE_2D, font);
            for (Key k : KEYS) {
                String label = shift ? k.shiftLabel : k.label;
                boolean dim = k.glfw == 0 && !k.shift && !k.hide && !typeable(shift ? k.shiftCh : k.ch);
                GL11.glColor4f(1, 1, 1, dim ? 0.25f : 1);
                float size = label.length() > 1 ? 24 : 34;
                float scale = size / 8, width = 0;
                for (int c = 0; c < label.length(); c++) width += advance(label.charAt(c)) * scale;
                float x = k.x * unit + (k.w * unit - width) / 2, y = k.y * row + (row - size) / 2;
                for (int c = 0; c < label.length(); c++) {
                    glyph(label.charAt(c), x, y, size);
                    x += advance(label.charAt(c)) * scale;
                }
            }
        }
        GL11.glMatrixMode(GL11.GL_PROJECTION);
        GL11.glPopMatrix();
        GL11.glMatrixMode(GL11.GL_MODELVIEW);
        GL11.glPopMatrix();
        GL11.glPopAttrib();
    }

    private static void rect(float x, float y, float w, float h) {
        GL11.glBegin(GL11.GL_QUADS);
        GL11.glVertex2f(x, y);
        GL11.glVertex2f(x, y + h);
        GL11.glVertex2f(x + w, y + h);
        GL11.glVertex2f(x + w, y);
        GL11.glEnd();
    }

    /** How far a character moves the next one on, in font pixels (8 a cell). */
    private float advance(char c) {
        if (c == ' ') return 4;
        return c < 128 && glyphWidth[c] > 0 ? glyphWidth[c] + 1 : 6;
    }

    /** A character of the game's font (8 x 8 cells in a 128 x 128 picture, ASCII order). */
    private static void glyph(char c, float x, float y, float size) {
        if (c < 32 || c > 126) return;
        float u = (c % 16) / 16f, v = (c / 16) / 16f, d = 1 / 16f;
        GL11.glBegin(GL11.GL_QUADS);
        GL11.glTexCoord2f(u, v);
        GL11.glVertex2f(x, y);
        GL11.glTexCoord2f(u, v + d);
        GL11.glVertex2f(x, y + size);
        GL11.glTexCoord2f(u + d, v + d);
        GL11.glVertex2f(x + size, y + size);
        GL11.glTexCoord2f(u + d, v);
        GL11.glVertex2f(x + size, y);
        GL11.glEnd();
    }

    /** The game's font as a texture (0 when it cannot be read: the keys are drawn without labels). */
    private int loadFont() {
        ClassLoader loader = gameClassLoader != null ? gameClassLoader : Thread.currentThread().getContextClassLoader();
        try (InputStream in = loader != null ? loader.getResourceAsStream("font/default.png") : null) {
            if (in == null) throw new IllegalStateException("the game has no font/default.png");
            BufferedImage image = ImageIO.read(in);
            int w = image.getWidth(), h = image.getHeight();
            int[] argb = image.getRGB(0, 0, w, h, null, 0, w);
            // each character's width: its rightmost column with something in it
            int cell = w / 16;
            for (int c = 0; c < 128; c++) {
                int x0 = (c % 16) * cell, y0 = (c / 16) * cell, widest = 0;
                for (int x = 0; x < cell; x++) {
                    for (int y = 0; y < cell; y++) {
                        if ((argb[(y0 + y) * w + x0 + x] >>> 24) > 0) widest = x + 1;
                    }
                }
                glyphWidth[c] = widest * 8 / cell;
            }
            ByteBuffer pixels = BufferUtils.createByteBuffer(w * h * 4);
            for (int p : argb) pixels.put((byte) (p >> 16)).put((byte) (p >> 8)).put((byte) p).put((byte) (p >>> 24));
            pixels.flip();
            int texture = GL11.glGenTextures();
            GL11.glBindTexture(GL11.GL_TEXTURE_2D, texture);
            GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_MIN_FILTER, GL11.GL_NEAREST);
            GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_MAG_FILTER, GL11.GL_NEAREST);
            GL11.glTexImage2D(GL11.GL_TEXTURE_2D, 0, GL11.GL_RGBA, w, h, 0, GL11.GL_RGBA, GL11.GL_UNSIGNED_BYTE, pixels);
            GL11.glBindTexture(GL11.GL_TEXTURE_2D, 0);
            return texture;
        } catch (Throwable t) {
            System.out.println("QuestCraft VR: no font for the keyboard: " + t);
            return 0;
        }
    }
}
