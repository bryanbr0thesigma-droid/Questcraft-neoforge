package questcraft.legacy.xr;

import org.lwjgl.opengl.ARBFramebufferObject;
import org.lwjgl.opengl.ContextCapabilities;
import org.lwjgl.opengl.EXTFramebufferObject;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL30;
import org.lwjgl.opengl.GLContext;

/**
 * Framebuffer objects through whichever functions the GL has: ARB_framebuffer_object's (the same functions as OpenGL
 * 3.0's), else OpenGL 3.0's on a real 3.0 GL, else EXT_framebuffer_object's. The headset's LWJGL reports every OpenGL
 * version up to 3.3 as present whatever the GL is, so the version is read from GL_VERSION; its extension flags are
 * right. It calls a function its GL does not have without checking (the game dies without a word), so none is
 * called that is not there. gl4es always has the unsuffixed (ARB) names.
 */
public final class Framebuffers {
    public static final int FRAMEBUFFER = 0x8D40, RENDERBUFFER = 0x8D41, READ_FRAMEBUFFER = 0x8CA8, DRAW_FRAMEBUFFER = 0x8CA9;
    public static final int COLOR_ATTACHMENT0 = 0x8CE0, DEPTH_ATTACHMENT = 0x8D00, FRAMEBUFFER_COMPLETE = 0x8CD5;
    /** GL_DEPTH_COMPONENT24 */
    public static final int DEPTH24 = 0x81A6;

    private enum Kind {CORE, ARB, EXT}

    private static Kind kind;

    private Framebuffers() {
    }

    private static Kind kind() {
        if (kind != null) return kind;
        ContextCapabilities caps = GLContext.getCapabilities();
        String version = String.valueOf(GL11.glGetString(GL11.GL_VERSION));
        boolean gl4es = version.contains("gl4es");
        if (caps.GL_ARB_framebuffer_object) kind = Kind.ARB;
        else if (majorVersion(version) >= 3 && !gl4es) kind = Kind.CORE;
        else if (caps.GL_EXT_framebuffer_object) kind = Kind.EXT;
        else if (gl4es) kind = Kind.ARB;
        System.out.println("QuestCraft screen: GL " + version + " (" + GL11.glGetString(GL11.GL_RENDERER) + "), framebuffers: "
                + (kind != null ? kind : "none") + " (ARB " + caps.GL_ARB_framebuffer_object + ", EXT " + caps.GL_EXT_framebuffer_object + ")");
        if (kind == null) throw new IllegalStateException("this GL has no framebuffer objects (OpenGL 3.0, ARB or EXT)");
        return kind;
    }

    /** The leading number of GL_VERSION ("2.1 gl4es wrapper", "4.5 (Compatibility Profile) Mesa"), 0 if none */
    static int majorVersion(String version) {
        int end = 0;
        while (end < version.length() && Character.isDigit(version.charAt(end))) end++;
        return end > 0 ? Integer.parseInt(version.substring(0, end)) : 0;
    }

    public static int gen() {
        switch (kind()) {
            case CORE:
                return GL30.glGenFramebuffers();
            case ARB:
                return ARBFramebufferObject.glGenFramebuffers();
            default:
                return EXTFramebufferObject.glGenFramebuffersEXT();
        }
    }

    public static void bind(int target, int framebuffer) {
        switch (kind()) {
            case CORE:
                GL30.glBindFramebuffer(target, framebuffer);
                break;
            case ARB:
                ARBFramebufferObject.glBindFramebuffer(target, framebuffer);
                break;
            default:
                EXTFramebufferObject.glBindFramebufferEXT(target, framebuffer);
        }
    }

    public static void texture(int target, int attachment, int texture) {
        switch (kind()) {
            case CORE:
                GL30.glFramebufferTexture2D(target, attachment, GL11.GL_TEXTURE_2D, texture, 0);
                break;
            case ARB:
                ARBFramebufferObject.glFramebufferTexture2D(target, attachment, GL11.GL_TEXTURE_2D, texture, 0);
                break;
            default:
                EXTFramebufferObject.glFramebufferTexture2DEXT(target, attachment, GL11.GL_TEXTURE_2D, texture, 0);
        }
    }

    /** A depth renderbuffer of that size, attached to the bound framebuffer. */
    public static void depth(int width, int height) {
        int renderbuffer;
        switch (kind()) {
            case CORE:
                renderbuffer = GL30.glGenRenderbuffers();
                GL30.glBindRenderbuffer(RENDERBUFFER, renderbuffer);
                GL30.glRenderbufferStorage(RENDERBUFFER, DEPTH24, width, height);
                GL30.glBindRenderbuffer(RENDERBUFFER, 0);
                GL30.glFramebufferRenderbuffer(FRAMEBUFFER, DEPTH_ATTACHMENT, RENDERBUFFER, renderbuffer);
                break;
            case ARB:
                renderbuffer = ARBFramebufferObject.glGenRenderbuffers();
                ARBFramebufferObject.glBindRenderbuffer(RENDERBUFFER, renderbuffer);
                ARBFramebufferObject.glRenderbufferStorage(RENDERBUFFER, DEPTH24, width, height);
                ARBFramebufferObject.glBindRenderbuffer(RENDERBUFFER, 0);
                ARBFramebufferObject.glFramebufferRenderbuffer(FRAMEBUFFER, DEPTH_ATTACHMENT, RENDERBUFFER, renderbuffer);
                break;
            default:
                renderbuffer = EXTFramebufferObject.glGenRenderbuffersEXT();
                EXTFramebufferObject.glBindRenderbufferEXT(RENDERBUFFER, renderbuffer);
                EXTFramebufferObject.glRenderbufferStorageEXT(RENDERBUFFER, DEPTH24, width, height);
                EXTFramebufferObject.glBindRenderbufferEXT(RENDERBUFFER, 0);
                EXTFramebufferObject.glFramebufferRenderbufferEXT(FRAMEBUFFER, DEPTH_ATTACHMENT, RENDERBUFFER, renderbuffer);
        }
    }

    public static int status() {
        switch (kind()) {
            case CORE:
                return GL30.glCheckFramebufferStatus(FRAMEBUFFER);
            case ARB:
                return ARBFramebufferObject.glCheckFramebufferStatus(FRAMEBUFFER);
            default:
                return EXTFramebufferObject.glCheckFramebufferStatusEXT(FRAMEBUFFER);
        }
    }

    public static void delete(int framebuffer) {
        switch (kind()) {
            case CORE:
                GL30.glDeleteFramebuffers(framebuffer);
                break;
            case ARB:
                ARBFramebufferObject.glDeleteFramebuffers(framebuffer);
                break;
            default:
                EXTFramebufferObject.glDeleteFramebuffersEXT(framebuffer);
        }
    }
}
