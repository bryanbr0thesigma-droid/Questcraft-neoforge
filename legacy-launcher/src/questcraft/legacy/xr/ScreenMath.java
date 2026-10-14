package questcraft.legacy.xr;

/** The geometry of the screen in VR, apart from OpenXR so that it can be checked anywhere (SelfTest). */
public final class ScreenMath {
    private ScreenMath() {
    }

    /**
     * Where a ray from {@code origin} along {@code direction} meets the screen (centre {@code center}, turned by
     * {@code yaw} about the vertical, front +z), as u, v from its top left (0..1), or null if it misses it.
     */
    public static float[] hit(float[] center, float yaw, float width, float height, float[] origin, float[] direction) {
        // into the screen's frame: turn by -yaw about y
        float c = (float) Math.cos(-yaw), s = (float) Math.sin(-yaw);
        float ox = origin[0] - center[0], oy = origin[1] - center[1], oz = origin[2] - center[2];
        float lox = c * ox + s * oz, loz = -s * ox + c * oz;
        float ldx = c * direction[0] + s * direction[2], ldz = -s * direction[0] + c * direction[2];
        if (loz <= 0 || ldz >= 0) return null; // behind the screen, or pointing away from it
        float t = -loz / ldz;
        float x = lox + t * ldx, y = oy + t * direction[1];
        float u = x / width + 0.5f, v = 0.5f - y / height;
        if (u < 0 || u > 1 || v < 0 || v > 1) return null;
        return new float[]{u, v};
    }

    /** v rotated by the quaternion (qx, qy, qz, qw). */
    public static float[] rotate(float qx, float qy, float qz, float qw, float vx, float vy, float vz) {
        // v + 2 * q.xyz x (q.xyz x v + w v)
        float cx = qy * vz - qz * vy + qw * vx;
        float cy = qz * vx - qx * vz + qw * vy;
        float cz = qx * vy - qy * vx + qw * vz;
        return new float[]{vx + 2 * (qy * cz - qz * cy), vy + 2 * (qz * cx - qx * cz), vz + 2 * (qx * cy - qy * cx)};
    }
}
