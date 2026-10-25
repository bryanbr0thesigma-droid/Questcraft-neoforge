package questcraft.legacy.vr;

import java.awt.image.BufferedImage;
import java.nio.ByteBuffer;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;

import org.lwjgl.BufferUtils;
import org.lwjgl.opengl.GL11;

import questcraft.legacy.SkinFix;

/**
 * Today's skins on the old player model: the old versions draw a 64 x 32 skin (the left arm and leg mirror the right
 * ones, no outer layer but the hat, 4 pixel arms). With a player's whole skin from Mojang ({@link SkinFix}) this draws
 * what they cannot: the left arm and leg from their own place in the skin, slim (3 pixel) arms, and the outer layer
 * (jacket, sleeves, trouser legs) a little bigger than the part under it, as today's game does. A version's glue calls
 * it where the game draws its player model, with the model's own part positions and rotations, so every pose (walking,
 * sneaking, swinging, riding) carries over.
 * <p>Boxes are laid out on the texture as the game lays out its model parts (and today's skins are): for a box w wide,
 * h high and d deep at (u, v), the top and bottom faces are at (u + d, v), the sides and front and back under them.</p>
 */
public final class SkinModel {
    public static final int HEAD = 0, BODY = 1, RIGHT_ARM = 2, LEFT_ARM = 3, RIGHT_LEG = 4, LEFT_LEG = 5;
    /** Each part's own place in a 64 x 64 skin, and its outer layer's: u, v */
    private static final int[][] UV = {{0, 0}, {16, 16}, {40, 16}, {32, 48}, {0, 16}, {16, 48}};
    private static final int[][] OUTER_UV = {{32, 0}, {16, 32}, {40, 32}, {48, 48}, {0, 32}, {0, 48}};
    /** How much bigger the outer layer is, in model pixels (the hat is the game's own, half a pixel) */
    public static final float OUTER = 0.25f;

    /** A player's skin: its GL texture, whether it has today's 64 x 64 layout, slim arms */
    public static final class Skin {
        public final int texture;
        public final boolean modern, slim;

        Skin(int texture, boolean modern, boolean slim) {
            this.texture = texture;
            this.modern = modern;
            this.slim = slim;
        }
    }

    /** Each name's uploaded skin (null value: Mojang has none, the game's own drawing stays) */
    private static final Map<String, Skin> skins = new HashMap<>();

    private SkinModel() {
    }

    /**
     * The player's skin from Mojang, uploaded the first time (the GL thread: where the game draws), null while it is not
     * there yet or when there is none.
     */
    public static Skin skin(String name) {
        if (name == null) return null;
        String key = name.toLowerCase(Locale.ROOT);
        if (skins.containsKey(key)) return skins.get(key);
        SkinFix.Textures textures = SkinFix.textures(name);
        if (textures == null) return null;    // not looked up (yet)
        // an old 64 x 32 skin: the game draws it as it always did
        Skin skin = textures.skin != null && textures.skin.getHeight() * 64 / textures.skin.getWidth() >= 64 ? upload(textures.skin, textures.slim) : null;
        skins.put(key, skin);
        return skin;
    }

    private static Skin upload(BufferedImage image, boolean slim) {
        int scale = Math.max(1, image.getWidth() / 64);
        int width = 64, height = image.getHeight() / scale >= 64 ? 64 : 32;
        ByteBuffer pixels = BufferUtils.createByteBuffer(width * height * 4);
        for (int y = 0; y < height; y++) {
            for (int x = 0; x < width; x++) {
                int p = image.getRGB(x * scale, y * scale);
                // the parts themselves are solid, as the game (and today's) make them: only the outer layer has holes
                if (base(x, y)) p |= 0xFF000000;
                pixels.put((byte) (p >> 16)).put((byte) (p >> 8)).put((byte) p).put((byte) (p >>> 24));
            }
        }
        pixels.flip();
        int texture = GL11.glGenTextures();
        GL11.glBindTexture(GL11.GL_TEXTURE_2D, texture);
        GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_MIN_FILTER, GL11.GL_NEAREST);
        GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_MAG_FILTER, GL11.GL_NEAREST);
        GL11.glTexImage2D(GL11.GL_TEXTURE_2D, 0, GL11.GL_RGBA, width, height, 0, GL11.GL_RGBA, GL11.GL_UNSIGNED_BYTE, pixels);
        return new Skin(texture, height == 64, slim && height == 64);
    }

    /** Whether a pixel of a 64 x 64 skin is on a part itself (the head, body, arms and legs), not on the outer layer. */
    static boolean base(int x, int y) {
        return (y < 16 && x < 32) || (y >= 16 && y < 32) || (y >= 48 && x >= 16 && x < 48);
    }

    /**
     * Draws a part of the player model where the game's part is (pivot x, y, z and rotations as the game's ModelPart
     * keeps them; scale 1/16 as the game draws), the texture bound: the part itself if {@code base}, and its outer
     * layer if {@code outer} (not the head's: the game draws the hat).
     */
    public static void part(Skin skin, int part, float x, float y, float z, float rotationX, float rotationY, float rotationZ,
                            float scale, boolean base, boolean outer) {
        GL11.glPushMatrix();
        GL11.glTranslatef(x * scale, y * scale, z * scale);
        if (rotationZ != 0) GL11.glRotatef((float) Math.toDegrees(rotationZ), 0, 0, 1);
        if (rotationY != 0) GL11.glRotatef((float) Math.toDegrees(rotationY), 0, 1, 0);
        if (rotationX != 0) GL11.glRotatef((float) Math.toDegrees(rotationX), 1, 0, 0);
        local(skin, part, scale, base, outer);
        GL11.glPopMatrix();
    }

    /** The same, at the current origin (the pivot): what the VR glue's first person arms use. */
    public static void local(Skin skin, int part, float scale, boolean base, boolean outer) {
        float[] box = box(part, skin.slim);
        int sizeX = (int) box[3];
        float texHeight = skin.modern ? 64 : 32;
        // an old 64 x 32 skin has no left limbs of its own: they are the right ones mirrored, as the game draws them
        boolean mirrored = !skin.modern && (part == LEFT_ARM || part == LEFT_LEG);
        int[] uv = mirrored ? UV[part - 1] : UV[part];
        if (base) box(box[0], box[1], box[2], sizeX, (int) box[4], (int) box[5], 0, uv[0], uv[1], texHeight, mirrored, scale);
        if (outer && skin.modern && part != HEAD) {
            int[] outerUv = OUTER_UV[part];
            box(box[0], box[1], box[2], sizeX, (int) box[4], (int) box[5], OUTER, outerUv[0], outerUv[1], texHeight, false, scale);
        }
    }

    /** A part's box from its pivot, as the game's humanoid model has it: x, y, z, width, height, depth */
    static float[] box(int part, boolean slim) {
        switch (part) {
            case HEAD:
                return new float[]{-4, -8, -4, 8, 8, 8};
            case BODY:
                return new float[]{-4, 0, -2, 8, 12, 4};
            case RIGHT_ARM:
                return slim ? new float[]{-2, -2, -2, 3, 12, 4} : new float[]{-3, -2, -2, 4, 12, 4};
            case LEFT_ARM:
                return slim ? new float[]{-1, -2, -2, 3, 12, 4} : new float[]{-1, -2, -2, 4, 12, 4};
            default:
                return new float[]{-2, 0, -2, 4, 12, 4};
        }
    }

    /**
     * A box as the game's ModelPart.addBox makes it (the same faces, corners and texture places), on a 64 x texHeight
     * texture, {@code inflate} pixels bigger all round, mirrored left to right if asked (the game's "flipped" part).
     */
    static void box(float x0, float y0, float z0, int w, int h, int d, float inflate, int u, int v, float texHeight,
                    boolean mirror, float scale) {
        float x1 = x0 + w + inflate, y1 = y0 + h + inflate, z1 = z0 + d + inflate;
        x0 -= inflate;
        y0 -= inflate;
        z0 -= inflate;
        if (mirror) {
            float t = x1;
            x1 = x0;
            x0 = t;
        }
        // the corners: 0..3 at z0, 4..7 at z1, as the game numbers them
        float[][] c = {{x0, y0, z0}, {x1, y0, z0}, {x1, y1, z0}, {x0, y1, z0}, {x0, y0, z1}, {x1, y0, z1}, {x1, y1, z1}, {x0, y1, z1}};
        // each face: its corners and its texture rectangle (u1, v1, u2, v2), in the game's order
        face(c, new int[]{5, 1, 2, 6}, u + d + w, v + d, u + d + w + d, v + d + h, texHeight, mirror, scale);
        face(c, new int[]{0, 4, 7, 3}, u, v + d, u + d, v + d + h, texHeight, mirror, scale);
        face(c, new int[]{5, 4, 0, 1}, u + d, v, u + d + w, v + d, texHeight, mirror, scale);
        face(c, new int[]{2, 3, 7, 6}, u + d + w, v, u + d + w + w, v + d, texHeight, mirror, scale);
        face(c, new int[]{1, 0, 3, 2}, u + d, v + d, u + d + w, v + d + h, texHeight, mirror, scale);
        face(c, new int[]{4, 5, 6, 7}, u + d + w + d, v + d, u + d + w + d + w, v + d + h, texHeight, mirror, scale);
    }

    /** One face, its corners given as the game gives them (texture u2 v1, u1 v1, u1 v2, u2 v2), reversed if mirrored. */
    private static void face(float[][] c, int[] corners, int u1, int v1, int u2, int v2, float texHeight, boolean mirror, float scale) {
        // the game's tiny inset keeps neighbouring texels out
        float du = 0.0015625f, dv = 0.003125f * 32 / texHeight;
        float[][] uv = {{u2 / 64f - du, v1 / texHeight + dv}, {u1 / 64f + du, v1 / texHeight + dv}, {u1 / 64f + du, v2 / texHeight - dv},
                {u2 / 64f - du, v2 / texHeight - dv}};
        int[] order = mirror ? new int[]{3, 2, 1, 0} : new int[]{0, 1, 2, 3};
        float[] a = c[corners[order[0]]], b = c[corners[order[1]]], e = c[corners[order[2]]];
        // the normal as the game works it out: (v1 - v2) x (v1 - v0)
        float[] p = {b[0] - e[0], b[1] - e[1], b[2] - e[2]}, q = {b[0] - a[0], b[1] - a[1], b[2] - a[2]};
        float nx = p[1] * q[2] - p[2] * q[1], ny = p[2] * q[0] - p[0] * q[2], nz = p[0] * q[1] - p[1] * q[0];
        float length = (float) Math.sqrt(nx * nx + ny * ny + nz * nz);
        GL11.glBegin(GL11.GL_QUADS);
        if (length > 0) GL11.glNormal3f(nx / length, ny / length, nz / length);
        for (int i = 0; i < 4; i++) {
            float[] corner = c[corners[order[i]]];
            float[] t = uv[order[i]];
            GL11.glTexCoord2f(t[0], t[1]);
            GL11.glVertex3f(corner[0] * scale, corner[1] * scale, corner[2] * scale);
        }
        GL11.glEnd();
    }
}
