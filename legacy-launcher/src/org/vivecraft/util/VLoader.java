package org.vivecraft.util;

/**
 * QuestCraft's libvloader.so (Pojlib): the EGL context the game draws with and the Android activity, which OpenXR
 * needs to create its session on the headset. The native functions are named after this class, so it has Vivecraft's
 * name (the same class as in QuestCraft's Vivecraft and in Vivecraft 1.12.2).
 */
public class VLoader {
	static {
		System.loadLibrary("vloader");
	}

	public static native long getEGLContext();
	public static native long getEGLConfig();
	public static native long getEGLDisplay();
	public static native long getDalvikVM();
	public static native long getDalvikActivity();
	public static native void setupAndroid();
}
