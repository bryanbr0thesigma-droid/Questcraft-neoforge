package questcraft.legacy.xr;

import java.nio.ByteBuffer;
import java.nio.FloatBuffer;
import java.nio.IntBuffer;

import org.lwjgl.opengl.GL11;
import org.lwjgl.system.JNI;
import org.lwjgl.system.Library;
import org.lwjgl.system.MemoryStack;
import org.lwjgl.system.MemoryUtil;
import org.lwjgl.system.SharedLibrary;

/**
 * Copies the game's picture into the OpenXR swapchain image with the system's OpenGL ES, called directly (as Vivecraft
 * 1.12.2's XrBlit): the game draws through gl4es, which keeps its own idea of the GL state, so everything changed here
 * is put back afterwards. Also draws the pointer's dot, where the right controller points at the screen.
 */
public final class GlesBlit {
    private static final int GL_READ_FRAMEBUFFER = 0x8CA8, GL_DRAW_FRAMEBUFFER = 0x8CA9;
    private static final int GL_READ_FRAMEBUFFER_BINDING = 0x8CAA, GL_DRAW_FRAMEBUFFER_BINDING = 0x8CA6;
    private static final int GL_COLOR_ATTACHMENT0 = 0x8CE0, GL_COLOR_BUFFER_BIT = 0x4000, GL_LINEAR = 0x2601;
    private static final int GL_TEXTURE_2D = 0x0DE1, GL_SCISSOR_TEST = 0x0C11, GL_SCISSOR_BOX = 0x0C10;
    private static final int GL_COLOR_CLEAR_VALUE = 0x0C22, GL_COLOR_WRITEMASK = 0x0C23;
    /** EXT_sRGB_write_control: off, the game's already gamma-encoded picture goes into the sRGB swapchain as it is */
    private static final int GL_FRAMEBUFFER_SRGB = 0x8DB9;

    private final long glGetIntegerv, glGetFloatv, glGetBooleanv, glGenFramebuffers, glBindFramebuffer, glFramebufferTexture2D,
            glFramebufferTextureLayer, glBlitFramebuffer, glEnable, glDisable, glIsEnabled, glGetError, glScissor, glClearColor,
            glClear, glColorMask;
    private int readFbo, drawFbo;
    private boolean srgbLogged;

    public GlesBlit() {
        SharedLibrary gles = Library.loadNative(GlesBlit.class, "org.lwjgl.opengles",
                System.getProperty("org.lwjgl.opengles.libname", "libGLESv3.so"));
        glGetIntegerv = function(gles, "glGetIntegerv");
        glGetFloatv = function(gles, "glGetFloatv");
        glGetBooleanv = function(gles, "glGetBooleanv");
        glGenFramebuffers = function(gles, "glGenFramebuffers");
        glBindFramebuffer = function(gles, "glBindFramebuffer");
        glFramebufferTexture2D = function(gles, "glFramebufferTexture2D");
        glFramebufferTextureLayer = function(gles, "glFramebufferTextureLayer");
        glBlitFramebuffer = function(gles, "glBlitFramebuffer");
        glEnable = function(gles, "glEnable");
        glDisable = function(gles, "glDisable");
        glIsEnabled = function(gles, "glIsEnabled");
        glGetError = function(gles, "glGetError");
        glScissor = function(gles, "glScissor");
        glClearColor = function(gles, "glClearColor");
        glClear = function(gles, "glClear");
        glColorMask = function(gles, "glColorMask");
    }

    private static long function(SharedLibrary library, String name) {
        long address = library.getFunctionAddress(name);
        if (address == 0) throw new IllegalStateException("OpenGL ES has no " + name);
        return address;
    }

    /**
     * Copies {@code texture} (the game's picture, {@code width} x {@code height}) into {@code image} (a swapchain
     * image of the same size), with the pointer's dot at {@code pointerX}, {@code pointerY} (pixels from the top left)
     * when {@code pointer} is set.
     */
    void copy(int texture, int image, int width, int height, boolean pointer, int pointerX, int pointerY) {
        copy(texture, image, -1, width, height, pointer, pointerX, pointerY);
    }

    /** As {@link #copy(int, int, int, int, boolean, int, int)}, into layer {@code layer} of an array image (-1: a plain one). */
    public void copy(int texture, int image, int layer, int width, int height, boolean pointer, int pointerX, int pointerY) {
        GL11.glFlush(); // what gl4es still holds goes to the driver before the texture is read behind its back

        try (MemoryStack stack = MemoryStack.stackPush()) {
            IntBuffer value = stack.mallocInt(4);
            int previousRead = getInteger(GL_READ_FRAMEBUFFER_BINDING, value);
            int previousDraw = getInteger(GL_DRAW_FRAMEBUFFER_BINDING, value);
            drainErrors();

            if (readFbo == 0) {
                readFbo = generateFramebuffer(value);
                drawFbo = generateFramebuffer(value);
            }
            JNI.callV(GL_READ_FRAMEBUFFER, readFbo, glBindFramebuffer);
            JNI.callV(GL_READ_FRAMEBUFFER, GL_COLOR_ATTACHMENT0, GL_TEXTURE_2D, texture, 0, glFramebufferTexture2D);
            JNI.callV(GL_DRAW_FRAMEBUFFER, drawFbo, glBindFramebuffer);
            if (layer < 0) JNI.callV(GL_DRAW_FRAMEBUFFER, GL_COLOR_ATTACHMENT0, GL_TEXTURE_2D, image, 0, glFramebufferTexture2D);
            else JNI.callV(GL_DRAW_FRAMEBUFFER, GL_COLOR_ATTACHMENT0, image, 0, layer, glFramebufferTextureLayer);

            boolean scissor = JNI.callZ(GL_SCISSOR_TEST, glIsEnabled);
            if (scissor) JNI.callV(GL_SCISSOR_TEST, glDisable);
            boolean srgbWrites = JNI.callZ(GL_FRAMEBUFFER_SRGB, glIsEnabled);
            if (!srgbLogged) {
                // without sRGB write control an sRGB swapchain encodes the game's (already encoded) picture again:
                // everything would look washed out
                srgbLogged = true;
                int error = JNI.callI(glGetError);
                System.out.println("QuestCraft screen: sRGB write control " + (error == 0 ? "there, " + (srgbWrites ? "on (turned off for the copy)" : "off")
                        : "missing (GL error " + error + ")"));
            }
            if (srgbWrites) JNI.callV(GL_FRAMEBUFFER_SRGB, glDisable);

            JNI.callV(0, 0, width, height, 0, 0, width, height, GL_COLOR_BUFFER_BIT, GL_LINEAR, glBlitFramebuffer);
            if (pointer) dot(pointerX, height - pointerY, stack);

            if (srgbWrites) JNI.callV(GL_FRAMEBUFFER_SRGB, glEnable);
            if (scissor) JNI.callV(GL_SCISSOR_TEST, glEnable);
            JNI.callV(GL_READ_FRAMEBUFFER, previousRead, glBindFramebuffer);
            JNI.callV(GL_DRAW_FRAMEBUFFER, previousDraw, glBindFramebuffer);
            // an unknown enum above (no sRGB write control) must not show up as the game's own GL error
            drainErrors();
        }
    }

    /** A white square with a dark edge at x, y (from the bottom left), drawn by clearing small scissor boxes. */
    private void dot(int x, int y, MemoryStack stack) {
        IntBuffer box = stack.mallocInt(4);
        JNI.callPV(GL_SCISSOR_BOX, MemoryUtil.memAddress(box), glGetIntegerv);
        FloatBuffer clear = stack.mallocFloat(4);
        JNI.callPV(GL_COLOR_CLEAR_VALUE, MemoryUtil.memAddress(clear), glGetFloatv);
        ByteBuffer mask = stack.malloc(4);
        JNI.callPV(GL_COLOR_WRITEMASK, MemoryUtil.memAddress(mask), glGetBooleanv);

        JNI.callV(1, 1, 1, 1, glColorMask);
        JNI.callV(GL_SCISSOR_TEST, glEnable);
        square(x, y, 7, 0.1f, 0.1f, 0.1f);
        square(x, y, 5, 1f, 1f, 1f);
        JNI.callV(GL_SCISSOR_TEST, glDisable);

        JNI.callV(mask.get(0), mask.get(1), mask.get(2), mask.get(3), glColorMask);
        JNI.callV(clear.get(0), clear.get(1), clear.get(2), clear.get(3), glClearColor);
        JNI.callV(box.get(0), box.get(1), box.get(2), box.get(3), glScissor);
    }

    private void square(int x, int y, int half, float r, float g, float b) {
        JNI.callV(x - half, y - half, half * 2, half * 2, glScissor);
        JNI.callV(r, g, b, 1f, glClearColor);
        JNI.callV(GL_COLOR_BUFFER_BIT, glClear);
    }

    private int getInteger(int name, IntBuffer value) {
        value.put(0, 0);
        JNI.callPV(name, MemoryUtil.memAddress(value), glGetIntegerv);
        return value.get(0);
    }

    private int generateFramebuffer(IntBuffer value) {
        value.put(0, 0);
        JNI.callPV(1, MemoryUtil.memAddress(value), glGenFramebuffers);
        return value.get(0);
    }

    private void drainErrors() {
        for (int i = 0; i < 16 && JNI.callI(glGetError) != 0; i++) {
        }
    }
}
