package org.vivecraft.util;

/**
 * QuestCraft's libvloader.so (Pojlib): the EGL context the game draws with and the Android activity, which OpenXR
 * needs to create its session on the headset. Same class and natives as QuestCraft's Vivecraft for 1.18 and newer.
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
