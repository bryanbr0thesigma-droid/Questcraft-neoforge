package org.vivecraft.utils;

import java.lang.reflect.Method;

/**
 * Input on the headset, where desktop Vivecraft typed into the game window with the AWT robot. QuestCraft runs the
 * game on a GLFW stand-in with LWJGL 2's Mouse on top of it, so a scroll is handed to the scroll callback LWJGL 2
 * registered there. Reflection, as GLFW does not exist with the real LWJGL 2.
 */
public class AndroidInput {
	private static boolean failed;

	public static void scroll(int direction) {
		if (failed) return;
		try {
			Class<?> glfw = Class.forName("org.lwjgl.glfw.GLFW");
			Class<?> callbackType = Class.forName("org.lwjgl.glfw.GLFWScrollCallbackI");
			long window = (Long) glfw.getMethod("glfwGetCurrentContext").invoke(null);
			Method set = glfw.getMethod("glfwSetScrollCallback", long.class, callbackType);
			Object callback = set.invoke(null, window, null); // the one in place, put straight back
			if (callback == null) return;
			set.invoke(null, window, callback);
			callbackType.getMethod("invoke", long.class, double.class, double.class).invoke(callback, window, 0.0, (double) direction);
		} catch (Throwable e) {
			failed = true;
			System.out.println("Vivecraft: scrolling is not available: " + e);
		}
	}
}
