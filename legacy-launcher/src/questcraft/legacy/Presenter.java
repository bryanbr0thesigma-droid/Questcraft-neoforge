package questcraft.legacy;

import java.awt.Dimension;

import org.lwjgl.opengl.Display;

/** Where each finished frame of the game goes ({@code Display.update()}, see {@link Transformer}). */
public final class Presenter {
    private Presenter() {
    }

    /** The size the game draws at: its window's. */
    public static Dimension windowSize() {
        return new Dimension(Integer.getInteger("questcraft.legacy.width", 1280), Integer.getInteger("questcraft.legacy.height", 720));
    }

    public static void update() {
        Display.update();
    }

    /** The game's canvas is no window: the display stays without a parent and keeps the size the game was given. */
    public static void setParent(java.awt.Canvas canvas) {
    }

    public static void setParent(HeadlessCanvas canvas) {
    }

    public static void update(boolean processMessages) {
        Display.update(processMessages);
    }
}
