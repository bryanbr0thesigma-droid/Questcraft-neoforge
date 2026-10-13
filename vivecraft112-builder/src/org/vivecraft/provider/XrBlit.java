package org.vivecraft.provider;

import java.nio.IntBuffer;

import org.lwjgl.opengl.GL11;
import org.lwjgl.system.JNI;
import org.lwjgl.system.Library;
import org.lwjgl.system.MemoryStack;
import org.lwjgl.system.MemoryUtil;
import org.lwjgl.system.SharedLibrary;

/**
 * Copies an eye texture into a layer of the OpenXR swapchain image with the system's OpenGL ES, called directly.
 *
 * 1.12.2 runs on gl4es (OpenGL 2.1 on top of OpenGL ES), which has no array textures, so the game cannot draw into
 * the swapchain image the way QuestCraft's newer Vivecraft does. It draws into its own eye textures, as it did for
 * SteamVR, and this blits them across. Texture names are the driver's own under gl4es (glGenTextures passes through),
 * the framebuffers here are this class's own, and the bindings gl4es left are put back afterwards.
 */
class XrBlit {
	private static final int GL_READ_FRAMEBUFFER = 0x8CA8, GL_DRAW_FRAMEBUFFER = 0x8CA9;
	private static final int GL_READ_FRAMEBUFFER_BINDING = 0x8CAA, GL_DRAW_FRAMEBUFFER_BINDING = 0x8CA6;
	private static final int GL_COLOR_ATTACHMENT0 = 0x8CE0, GL_COLOR_BUFFER_BIT = 0x4000, GL_NEAREST = 0x2600;
	private static final int GL_TEXTURE_2D = 0x0DE1, GL_SCISSOR_TEST = 0x0C11;
	/** EXT_sRGB_write_control: off, the already gamma-encoded eye image is written to an sRGB swapchain as it is */
	private static final int GL_FRAMEBUFFER_SRGB = 0x8DB9;

	private final long glGetIntegerv, glGenFramebuffers, glBindFramebuffer, glFramebufferTexture2D,
			glFramebufferTextureLayer, glBlitFramebuffer, glEnable, glDisable, glIsEnabled, glGetError;
	private int readFbo, drawFbo;

	XrBlit() {
		SharedLibrary gles = Library.loadNative(XrBlit.class, "org.lwjgl.opengles",
				System.getProperty("org.lwjgl.opengles.libname", "libGLESv3.so"));
		glGetIntegerv = function(gles, "glGetIntegerv");
		glGenFramebuffers = function(gles, "glGenFramebuffers");
		glBindFramebuffer = function(gles, "glBindFramebuffer");
		glFramebufferTexture2D = function(gles, "glFramebufferTexture2D");
		glFramebufferTextureLayer = function(gles, "glFramebufferTextureLayer");
		glBlitFramebuffer = function(gles, "glBlitFramebuffer");
		glEnable = function(gles, "glEnable");
		glDisable = function(gles, "glDisable");
		glIsEnabled = function(gles, "glIsEnabled");
		glGetError = function(gles, "glGetError");
	}

	private static long function(SharedLibrary library, String name) {
		long address = library.getFunctionAddress(name);
		if (address == 0) throw new IllegalStateException("OpenGL ES has no " + name);
		return address;
	}

	/** Copies {@code eyeTexture} (width x height) into layer {@code layer} of {@code swapchainImage}. */
	void copy(int eyeTexture, int swapchainImage, int layer, int width, int height) {
		GL11.glFlush(); // what gl4es still holds goes to the driver before the texture is read behind its back

		try (MemoryStack stack = MemoryStack.stackPush()) {
			IntBuffer value = stack.mallocInt(1);
			int previousRead = getInteger(GL_READ_FRAMEBUFFER_BINDING, value);
			int previousDraw = getInteger(GL_DRAW_FRAMEBUFFER_BINDING, value);
			drainErrors();

			if (readFbo == 0) {
				readFbo = generateFramebuffer(value);
				drawFbo = generateFramebuffer(value);
			}

			JNI.callV(GL_READ_FRAMEBUFFER, readFbo, glBindFramebuffer);
			JNI.callV(GL_READ_FRAMEBUFFER, GL_COLOR_ATTACHMENT0, GL_TEXTURE_2D, eyeTexture, 0, glFramebufferTexture2D);
			JNI.callV(GL_DRAW_FRAMEBUFFER, drawFbo, glBindFramebuffer);
			JNI.callV(GL_DRAW_FRAMEBUFFER, GL_COLOR_ATTACHMENT0, swapchainImage, 0, layer, glFramebufferTextureLayer);

			boolean scissor = JNI.callZ(GL_SCISSOR_TEST, glIsEnabled);
			if (scissor) JNI.callV(GL_SCISSOR_TEST, glDisable);
			boolean srgbWrites = JNI.callZ(GL_FRAMEBUFFER_SRGB, glIsEnabled);
			if (srgbWrites) JNI.callV(GL_FRAMEBUFFER_SRGB, glDisable);

			JNI.callV(0, 0, width, height, 0, 0, width, height, GL_COLOR_BUFFER_BIT, GL_NEAREST, glBlitFramebuffer);

			if (srgbWrites) JNI.callV(GL_FRAMEBUFFER_SRGB, glEnable);
			if (scissor) JNI.callV(GL_SCISSOR_TEST, glEnable);
			JNI.callV(GL_READ_FRAMEBUFFER, previousRead, glBindFramebuffer);
			JNI.callV(GL_DRAW_FRAMEBUFFER, previousDraw, glBindFramebuffer);
			// an unknown enum above (no sRGB write control) must not show up as the game's own GL error
			drainErrors();
		}
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
