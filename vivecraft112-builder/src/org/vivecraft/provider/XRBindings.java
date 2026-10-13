package org.vivecraft.provider;

/**
 * Default OpenXR bindings for Quest controllers, the same as QuestCraft's Vivecraft for 1.18 and newer
 * (XRBindings.quest2Bindings there). The action names are Vivecraft's: action set + "/in/" + key binding name,
 * which 1.12.2 shares with the newer versions.
 */
public class XRBindings {
	public static final String QUEST_PROFILE = "/interaction_profiles/oculus/touch_controller";

	public static final String[][] QUEST = {
		{"/actions/global/in/vivecraft.key.ingameMenuButton", "/user/hand/left/input/menu/click"},
		{"/actions/global/in/key.inventory", "/user/hand/left/input/y/click"},

		{"/actions/gui/in/vivecraft.key.guiShift", "/user/hand/left/input/squeeze"},
		{"/actions/gui/in/vivecraft.key.guiMiddleClick", "/user/hand/right/input/squeeze"},
		{"/actions/gui/in/vivecraft.key.guiLeftClick", "/user/hand/right/input/trigger"},
		{"/actions/gui/in/vivecraft.key.guiRightClick", "/user/hand/right/input/a/click"},
		{"/actions/gui/in/vivecraft.key.guiScrollAxis", "/user/hand/right/input/thumbstick"},

		{"/actions/ingame/in/vivecraft.key.hotbarPrev", "/user/hand/left/input/squeeze"},
		{"/actions/ingame/in/vivecraft.key.hotbarNext", "/user/hand/right/input/squeeze"},
		{"/actions/ingame/in/key.attack", "/user/hand/right/input/trigger"},
		{"/actions/ingame/in/vivecraft.key.teleport", "/user/hand/left/input/x/click"},
		{"/actions/ingame/in/vivecraft.key.radialMenu", "/user/hand/right/input/b/click"},
		{"/actions/ingame/in/key.use", "/user/hand/left/input/trigger"},
		{"/actions/ingame/in/vivecraft.key.freeMoveStrafe", "/user/hand/left/input/thumbstick"},
		{"/actions/ingame/in/vivecraft.key.rotateAxis", "/user/hand/right/input/thumbstick"},
		{"/actions/ingame/in/vivecraft.key.teleportFallback", "/user/hand/left/input/x/click"},
		{"/actions/ingame/in/key.jump", "/user/hand/right/input/a/click"},
		{"/actions/ingame/in/key.sneak", "/user/hand/right/input/thumbstick/click"},

		{"/actions/keyboard/in/vivecraft.key.keyboardShift", "/user/hand/left/input/squeeze"},
		{"/actions/keyboard/in/vivecraft.key.keyboardClick", "/user/hand/left/input/trigger"},
		{"/actions/keyboard/in/vivecraft.key.keyboardClick", "/user/hand/right/input/trigger"},

		{"/actions/contextual/in/vivecraft.key.vrInteract", "/user/hand/left/input/squeeze"},
		{"/actions/contextual/in/vivecraft.key.vrInteract", "/user/hand/right/input/squeeze"},
		{"/actions/contextual/in/vivecraft.key.vrInteract", "/user/hand/left/input/trigger"},
		{"/actions/contextual/in/vivecraft.key.vrInteract", "/user/hand/right/input/trigger"},

		{"/actions/contextual/in/vivecraft.key.climbeyGrab", "/user/hand/left/input/squeeze"},
		{"/actions/contextual/in/vivecraft.key.climbeyGrab", "/user/hand/right/input/squeeze"},
		{"/actions/contextual/in/vivecraft.key.climbeyGrab", "/user/hand/left/input/trigger"},
		{"/actions/contextual/in/vivecraft.key.climbeyGrab", "/user/hand/right/input/trigger"},
	};
}
