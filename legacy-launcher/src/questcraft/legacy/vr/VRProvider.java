package questcraft.legacy.vr;

/** Where the picture goes and the poses come from: the headset (OpenXR) or the desktop check ({@link VRSim}). */
interface VRProvider {
    /** Starts, with the game's GL context current. False if it cannot (the reason is in the log). */
    boolean start();

    /** The size each eye is drawn at */
    int eyeWidth();

    int eyeHeight();

    /** Waits for the headset's next frame and fills {@code frame} with its poses and the controllers. False: no frame. */
    boolean beginFrame(VRFrame frame);

    /** Ends the frame begun by {@link #beginFrame} with the two eye pictures (texture names, eyeWidth x eyeHeight). */
    void endFrame(VRFrame frame, int leftTexture, int rightTexture);
}
