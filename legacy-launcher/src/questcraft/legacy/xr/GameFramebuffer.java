package questcraft.legacy.xr;

import java.nio.ByteBuffer;

import org.lwjgl.opengl.EXTFramebufferObject;
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
        int texture = GL11.glGenTextures();
        GL11.glBindTexture(GL11.GL_TEXTURE_2D, texture);
        GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_MIN_FILTER, GL11.GL_LINEAR);
        GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_MAG_FILTER, GL11.GL_LINEAR);
        GL11.glTexImage2D(GL11.GL_TEXTURE_2D, 0, GL11.GL_RGBA8, width, height, 0, GL11.GL_RGBA, GL11.GL_UNSIGNED_BYTE, (ByteBuffer) null);
        GL11.glBindTexture(GL11.GL_TEXTURE_2D, 0);

        int depth = EXTFramebufferObject.glGenRenderbuffersEXT();
        EXTFramebufferObject.glBindRenderbufferEXT(EXTFramebufferObject.GL_RENDERBUFFER_EXT, depth);
        EXTFramebufferObject.glRenderbufferStorageEXT(EXTFramebufferObject.GL_RENDERBUFFER_EXT, 0x81A6 /* GL_DEPTH_COMPONENT24 */, width, height);
        EXTFramebufferObject.glBindRenderbufferEXT(EXTFramebufferObject.GL_RENDERBUFFER_EXT, 0);

        int framebuffer = EXTFramebufferObject.glGenFramebuffersEXT();
        EXTFramebufferObject.glBindFramebufferEXT(EXTFramebufferObject.GL_FRAMEBUFFER_EXT, framebuffer);
        EXTFramebufferObject.glFramebufferTexture2DEXT(EXTFramebufferObject.GL_FRAMEBUFFER_EXT,
                EXTFramebufferObject.GL_COLOR_ATTACHMENT0_EXT, GL11.GL_TEXTURE_2D, texture, 0);
        EXTFramebufferObject.glFramebufferRenderbufferEXT(EXTFramebufferObject.GL_FRAMEBUFFER_EXT,
                EXTFramebufferObject.GL_DEPTH_ATTACHMENT_EXT, EXTFramebufferObject.GL_RENDERBUFFER_EXT, depth);
        int status = EXTFramebufferObject.glCheckFramebufferStatusEXT(EXTFramebufferObject.GL_FRAMEBUFFER_EXT);
        if (status != EXTFramebufferObject.GL_FRAMEBUFFER_COMPLETE_EXT) {
            System.out.println("QuestCraft screen: the game's framebuffer is not complete: 0x" + Integer.toHexString(status));
        }
        GL11.glViewport(0, 0, width, height);
        System.out.println("QuestCraft screen: the game draws into framebuffer " + framebuffer + " (" + width + " x " + height + ")");
        return new GameFramebuffer(framebuffer, texture, width, height);
    }
}
