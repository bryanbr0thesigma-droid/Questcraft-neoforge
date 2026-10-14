package questcraft.legacy.vr;

/**
 * One frame of the headset: where the head, the eyes and the controllers are in the room (OpenXR's stage space,
 * metres), each eye's field of view, and the controllers' sticks and buttons. Filled by a {@link VRProvider}.
 */
public final class VRFrame {
    /** Positions x, y, z and orientations as quaternions x, y, z, w */
    public final float[] headPosition = {0, VRMath.EYE_HEIGHT, 0};
    public final float[] headOrientation = {0, 0, 0, 1};
    public final float[][] eyePosition = {{-0.032f, VRMath.EYE_HEIGHT, 0}, {0.032f, VRMath.EYE_HEIGHT, 0}};
    public final float[][] eyeOrientation = {{0, 0, 0, 1}, {0, 0, 0, 1}};
    /** Per eye: left, right, up, down angles in radians (left and down negative) */
    public final float[][] eyeFov = {{-0.8f, 0.8f, 0.8f, -0.8f}, {-0.8f, 0.8f, 0.8f, -0.8f}};
    /** Per hand (0 left, 1 right): where it points from and its orientation (-z is the pointing direction) */
    public final float[][] aimPosition = {{-0.2f, 1.3f, -0.3f}, {0.2f, 1.3f, -0.3f}};
    public final float[][] aimOrientation = {{0, 0, 0, 1}, {0, 0, 0, 1}};
    /** Per hand: the controller's grip, where a held thing sits */
    public final float[][] gripPosition = {{-0.2f, 1.3f, -0.3f}, {0.2f, 1.3f, -0.3f}};
    public final float[][] gripOrientation = {{0, 0, 0, 1}, {0, 0, 0, 1}};
    public final boolean[] handTracked = new boolean[2];
    public boolean headTracked;
    /** The headset wants a picture this frame */
    public boolean shouldRender = true;

    public float leftX, leftY, rightX, rightY, leftTrigger, rightTrigger, leftGrip, rightGrip;
    public boolean a, b, x, y, menu, leftStickClick, rightStickClick;
}
