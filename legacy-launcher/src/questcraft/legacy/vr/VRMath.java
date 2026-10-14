package questcraft.legacy.vr;

/**
 * From the headset's room (OpenXR's stage space: metres, y up, -z ahead of where the room was set up) to the old
 * game's world, and the matrices the game draws each eye with. No GL and no OpenXR here: the self test runs it on
 * any JVM.
 * <p>
 * Minecraft's world: y up, yaw 0 looks along +z (south), yaw 90 along -x (west), pitch positive looks down. The room
 * is turned into the world by {@link #bodyYaw} (the snap turns), and its origin is put where the player's eyes are:
 * the headset's spot on the floor under the game's camera, at 1.62 m (the height the game's eyes are at above the
 * player's feet), so a person standing at 1.62 m sees exactly from the player's eyes, and ducking lowers the view.
 */
public final class VRMath {
    /** The game's eye height above the feet: the room height that is the player's eye level */
    public static final float EYE_HEIGHT = 1.62f;

    private VRMath() {
    }

    /** The 3x3 rotation, row major, that turns the room's axes into the world's for the given turn (degrees). */
    public static float[] roomToWorld(float bodyYaw) {
        // the room's -z ahead becomes the world's ahead at that yaw: the room turned half way round (x and z
        // flipped), then by -yaw about y (the game's yaw turns the other way round)
        double a = Math.toRadians(-bodyYaw);
        float c = (float) Math.cos(a), s = (float) Math.sin(a);
        // Ry(a) * diag(-1, 1, -1)
        return new float[]{-c, 0, -s,
                0, 1, 0,
                s, 0, -c};
    }

    /** m (3x3, row major) times v */
    public static float[] mul3(float[] m, float x, float y, float z) {
        return new float[]{m[0] * x + m[1] * y + m[2] * z, m[3] * x + m[4] * y + m[5] * z, m[6] * x + m[7] * y + m[8] * z};
    }

    /** a * b, both 3x3 row major */
    public static float[] mul33(float[] a, float[] b) {
        float[] r = new float[9];
        for (int i = 0; i < 3; i++) {
            for (int j = 0; j < 3; j++) {
                r[i * 3 + j] = a[i * 3] * b[j] + a[i * 3 + 1] * b[3 + j] + a[i * 3 + 2] * b[6 + j];
            }
        }
        return r;
    }

    /** The rotation of a unit quaternion as a 3x3 matrix, row major. */
    public static float[] quatToMatrix(float qx, float qy, float qz, float qw) {
        return new float[]{
                1 - 2 * (qy * qy + qz * qz), 2 * (qx * qy - qz * qw), 2 * (qx * qz + qy * qw),
                2 * (qx * qy + qz * qw), 1 - 2 * (qx * qx + qz * qz), 2 * (qy * qz - qx * qw),
                2 * (qx * qz - qy * qw), 2 * (qy * qz + qx * qw), 1 - 2 * (qx * qx + qy * qy)};
    }

    /**
     * Where a point of the room is in the world, relative to the game's camera (the player's eyes): {@code head} is
     * the headset's position in the room, whose spot on the floor is under the camera.
     */
    public static float[] roomPointToWorld(float[] room, float[] head, float x, float y, float z) {
        return mul3(room, x - head[0], y - EYE_HEIGHT, z - head[2]);
    }

    /** Yaw and pitch (degrees, the game's) of a direction in the world. */
    public static float[] yawPitch(float[] direction) {
        float yaw = (float) Math.toDegrees(Math.atan2(-direction[0], direction[2]));
        float length = (float) Math.sqrt(direction[0] * direction[0] + direction[1] * direction[1] + direction[2] * direction[2]);
        float pitch = (float) Math.toDegrees(-Math.asin(Math.max(-1, Math.min(1, direction[1] / length))));
        return new float[]{yaw, pitch};
    }

    /**
     * The model-view matrix of an eye (column major, for glLoadMatrix): from the world relative to the camera into the
     * eye's space (x right, y up, -z ahead). {@code orientation} is the eye's rotation in the room (3x3 row major),
     * {@code position} its place in the world relative to the camera.
     */
    public static float[] viewMatrix(float[] room, float[] orientation, float[] position) {
        float[] world = mul33(room, orientation);   // the eye's axes in the world
        // the inverse: transpose of the rotation, and the position moved back by it
        float[] m = new float[16];
        for (int row = 0; row < 3; row++) {
            for (int column = 0; column < 3; column++) {
                m[column * 4 + row] = world[column * 3 + row];   // transposed, then stored column major
            }
        }
        for (int row = 0; row < 3; row++) {
            m[12 + row] = -(m[row] * position[0] + m[4 + row] * position[1] + m[8 + row] * position[2]);
        }
        m[15] = 1;
        return m;
    }

    /** OpenXR's field of view (radians, left and down negative) as a projection matrix, column major. */
    public static float[] projection(float left, float right, float up, float down, float near, float far) {
        float l = (float) Math.tan(left) * near, r = (float) Math.tan(right) * near;
        float u = (float) Math.tan(up) * near, d = (float) Math.tan(down) * near;
        float[] m = new float[16];
        m[0] = 2 * near / (r - l);
        m[5] = 2 * near / (u - d);
        m[8] = (r + l) / (r - l);
        m[9] = (u + d) / (u - d);
        m[10] = -(far + near) / (far - near);
        m[11] = -1;
        m[14] = -2 * far * near / (far - near);
        return m;
    }

    /** A point through a column-major 4x4 matrix (w = 1), as x, y, z, w. */
    public static float[] transform(float[] m, float x, float y, float z) {
        return new float[]{m[0] * x + m[4] * y + m[8] * z + m[12], m[1] * x + m[5] * y + m[9] * z + m[13],
                m[2] * x + m[6] * y + m[10] * z + m[14], m[3] * x + m[7] * y + m[11] * z + m[15]};
    }
}
