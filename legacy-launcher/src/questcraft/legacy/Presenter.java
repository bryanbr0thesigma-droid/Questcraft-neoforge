package questcraft.legacy;

import java.awt.Dimension;

import org.lwjgl.LWJGLException;
import org.lwjgl.opengl.Display;
import org.lwjgl.opengl.EXTFramebufferBlit;
import org.lwjgl.opengl.EXTFramebufferObject;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.PixelFormat;

import questcraft.legacy.vr.VR;
import questcraft.legacy.xr.GameFramebuffer;
import questcraft.legacy.xr.ScreenXR;

/**
 * Where the game's display calls go ({@link Transformer}). On the headset the game draws into a framebuffer of ours
 * (its GL context has no window), and each finished frame ({@code Display.update()}) goes to the headset: in VR
 * ({@link VR}, for a version with VR glue), or else on a screen in VR ({@link ScreenXR}). On a desktop (the tests)
 * the game draws into its window as it always did.
 */
public final class Presenter {
    /** On the headset: Pojlib starts the game with os.version "Android-..." */
    static final boolean HEADSET = System.getProperty("os.version", "").startsWith("Android");
    /**
     * The game draws into our framebuffer: on the headset, and on a desktop with -Dquestcraft.legacy.framebuffer=true,
     * where it is copied to the window (to check that the game draws right into it without a headset), or with the
     * simulated headset (-Dquestcraft.legacy.vrsim=true).
     */
    static final boolean OWN_FRAMEBUFFER = HEADSET || Boolean.getBoolean("questcraft.legacy.framebuffer")
            || Boolean.getBoolean("questcraft.legacy.vrsim");
    private static GameFramebuffer framebuffer;
    private static ScreenXR screen;
    private static boolean screenTried;

    private Presenter() {
    }

    /** The size the game draws at: its window's. */
    public static Dimension windowSize() {
        return new Dimension(Integer.getInteger("questcraft.legacy.width", 1280), Integer.getInteger("questcraft.legacy.height", 720));
    }

    public static void create() throws LWJGLException {
        Display.create();
        created();
    }

    public static void create(PixelFormat format) throws LWJGLException {
        Display.create(format);
        created();
    }

    private static void created() {
        if (!OWN_FRAMEBUFFER || framebuffer != null) return;
        Dimension size = windowSize();
        framebuffer = GameFramebuffer.create(size.width, size.height);
    }

    /** Framebuffer 0 is the window, which is ours on the headset. */
    public static void glBindFramebuffer(int target, int id) {
        EXTFramebufferObject.glBindFramebufferEXT(target, id == 0 && framebuffer != null ? framebuffer.framebuffer : id);
    }

    public static void glBindFramebufferEXT(int target, int id) {
        glBindFramebuffer(target, id);
    }

    public static void update() {
        show();
        Display.update();
    }

    public static void update(boolean processMessages) {
        show();
        Display.update(processMessages);
    }

    private static void show() {
        if (framebuffer == null) return;
        if (VR.enabled()) {
            VR.setGameFramebuffer(framebuffer);
            try {
                VR.endFrame();
                return;
            } catch (Throwable t) {
                // the game goes on, on the screen in VR, rather than crashing at every frame; the log says why
                System.out.println("QuestCraft VR: stopped, " + t);
                t.printStackTrace();
                VR.disable("it stopped");
            }
        }
        if (!HEADSET) {
            // the desktop check: the picture into the window, then the game draws into ours again
            EXTFramebufferObject.glBindFramebufferEXT(EXTFramebufferBlit.GL_READ_FRAMEBUFFER_EXT, framebuffer.framebuffer);
            EXTFramebufferObject.glBindFramebufferEXT(EXTFramebufferBlit.GL_DRAW_FRAMEBUFFER_EXT, 0);
            EXTFramebufferBlit.glBlitFramebufferEXT(0, 0, framebuffer.width, framebuffer.height, 0, 0, framebuffer.width, framebuffer.height,
                    GL11.GL_COLOR_BUFFER_BIT, GL11.GL_NEAREST);
            EXTFramebufferObject.glBindFramebufferEXT(EXTFramebufferObject.GL_FRAMEBUFFER_EXT, framebuffer.framebuffer);
            return;
        }
        if (!screenTried) {
            screenTried = true;
            screen = ScreenXR.start(framebuffer.width, framebuffer.height);
        }
        if (screen == null) return;
        try {
            screen.frame(framebuffer.texture);
        } catch (Throwable t) {
            // the game goes on without its screen rather than crashing at every frame; the log says why
            System.out.println("QuestCraft screen: stopped, " + t);
            t.printStackTrace();
            screen = null;
        }
    }

    /** The game's canvas is no window: the display stays without a parent and keeps the size the game was given. */
    public static void setParent(java.awt.Canvas canvas) {
    }

    public static void setParent(HeadlessCanvas canvas) {
    }
}
