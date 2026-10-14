package questcraft.legacy.xr;

import java.nio.ByteBuffer;

import org.lwjgl.opengl.GL11;

/**
 * What the game draws into on the headset. Its GL context there has no window (Pojlib makes it without a surface), so
 * the default framebuffer draws nowhere; this one stands in for it ({@code Presenter} binds it where the game binds
 * framebuffer 0) and its picture goes to the screen in VR. Made through gl4es, whose texture names are the driver's
 * own, so the OpenXR copy can read it.
 */
public final class GameFramebuffer {
    public final int framebuffer;
    public final int texture;
    public final int width;
    public final int height;

    private GameFramebuffer(int framebuffer, int texture, int width, int height) {
        this.framebuffer = framebuffer;
        this.texture = texture;
        this.width = width;
        this.height = height;
    }

    /** Makes it and binds it. */
    public static GameFramebuffer create(int width, int height) {
        System.out.println("QuestCraft screen: making a framebuffer of " + width + " x " + height);
        int texture = GL11.glGenTextures();
        GL11.glBindTexture(GL11.GL_TEXTURE_2D, texture);
        GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_MIN_FILTER, GL11.GL_LINEAR);
        GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_MAG_FILTER, GL11.GL_LINEAR);
        GL11.glTexImage2D(GL11.GL_TEXTURE_2D, 0, GL11.GL_RGBA8, width, height, 0, GL11.GL_RGBA, GL11.GL_UNSIGNED_BYTE, (ByteBuffer) null);
        GL11.glBindTexture(GL11.GL_TEXTURE_2D, 0);

        int framebuffer = Framebuffers.gen();
        Framebuffers.bind(Framebuffers.FRAMEBUFFER, framebuffer);
        Framebuffers.texture(Framebuffers.FRAMEBUFFER, Framebuffers.COLOR_ATTACHMENT0, texture);
        Framebuffers.depth(width, height);
        int status = Framebuffers.status();
        if (status != Framebuffers.FRAMEBUFFER_COMPLETE) {
            System.out.println("QuestCraft screen: the game's framebuffer is not complete: 0x" + Integer.toHexString(status));
        }
        GL11.glViewport(0, 0, width, height);
        System.out.println("QuestCraft screen: the game draws into framebuffer " + framebuffer + " (" + width + " x " + height + ")");
        return new GameFramebuffer(framebuffer, texture, width, height);
    }
}
