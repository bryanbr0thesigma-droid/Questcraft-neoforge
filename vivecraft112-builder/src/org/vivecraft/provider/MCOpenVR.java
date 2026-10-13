package org.vivecraft.provider;

import java.awt.event.KeyEvent;
import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.IntBuffer;
import java.nio.LongBuffer;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import org.lwjgl.PointerBuffer;
import org.lwjgl.openxr.KHRAndroidCreateInstance;
import org.lwjgl.openxr.KHRLoaderInit;
import org.lwjgl.openxr.KHRLoaderInitAndroid;
import org.lwjgl.openxr.KHROpenGLESEnable;
import org.lwjgl.openxr.XR10;
import org.lwjgl.openxr.XrAction;
import org.lwjgl.openxr.XrActionCreateInfo;
import org.lwjgl.openxr.XrActionSet;
import org.lwjgl.openxr.XrActionSetCreateInfo;
import org.lwjgl.openxr.XrActionSpaceCreateInfo;
import org.lwjgl.openxr.XrActionStateBoolean;
import org.lwjgl.openxr.XrActionStateFloat;
import org.lwjgl.openxr.XrActionStateGetInfo;
import org.lwjgl.openxr.XrActionStateVector2f;
import org.lwjgl.openxr.XrActionSuggestedBinding;
import org.lwjgl.openxr.XrActionsSyncInfo;
import org.lwjgl.openxr.XrActiveActionSet;
import org.lwjgl.openxr.XrApplicationInfo;
import org.lwjgl.openxr.XrBoundSourcesForActionEnumerateInfo;
import org.lwjgl.openxr.XrCompositionLayerProjection;
import org.lwjgl.openxr.XrCompositionLayerProjectionView;
import org.lwjgl.openxr.XrEventDataBaseHeader;
import org.lwjgl.openxr.XrEventDataBuffer;
import org.lwjgl.openxr.XrEventDataSessionStateChanged;
import org.lwjgl.openxr.XrExtensionProperties;
import org.lwjgl.openxr.XrExtent2Df;
import org.lwjgl.openxr.XrFrameBeginInfo;
import org.lwjgl.openxr.XrFrameEndInfo;
import org.lwjgl.openxr.XrFrameState;
import org.lwjgl.openxr.XrFrameWaitInfo;
import org.lwjgl.openxr.XrGraphicsBindingOpenGLESAndroidKHR;
import org.lwjgl.openxr.XrGraphicsRequirementsOpenGLESKHR;
import org.lwjgl.openxr.XrHapticActionInfo;
import org.lwjgl.openxr.XrHapticBaseHeader;
import org.lwjgl.openxr.XrHapticVibration;
import org.lwjgl.openxr.XrInputSourceLocalizedNameGetInfo;
import org.lwjgl.openxr.XrInstance;
import org.lwjgl.openxr.XrInstanceCreateInfo;
import org.lwjgl.openxr.XrInstanceCreateInfoAndroidKHR;
import org.lwjgl.openxr.XrInteractionProfileSuggestedBinding;
import org.lwjgl.openxr.XrLoaderInitInfoAndroidKHR;
import org.lwjgl.openxr.XrLoaderInitInfoBaseHeaderKHR;
import org.lwjgl.openxr.XrPosef;
import org.lwjgl.openxr.XrReferenceSpaceCreateInfo;
import org.lwjgl.openxr.XrSession;
import org.lwjgl.openxr.XrSessionActionSetsAttachInfo;
import org.lwjgl.openxr.XrSessionBeginInfo;
import org.lwjgl.openxr.XrSessionCreateInfo;
import org.lwjgl.openxr.XrSpace;
import org.lwjgl.openxr.XrSpaceLocation;
import org.lwjgl.openxr.XrSwapchain;
import org.lwjgl.openxr.XrSwapchainCreateInfo;
import org.lwjgl.openxr.XrSwapchainImageAcquireInfo;
import org.lwjgl.openxr.XrSwapchainImageBaseHeader;
import org.lwjgl.openxr.XrSwapchainImageOpenGLESKHR;
import org.lwjgl.openxr.XrSwapchainImageReleaseInfo;
import org.lwjgl.openxr.XrSwapchainImageWaitInfo;
import org.lwjgl.openxr.XrSystemGetInfo;
import org.lwjgl.openxr.XrSystemProperties;
import org.lwjgl.openxr.XrView;
import org.lwjgl.openxr.XrViewConfigurationView;
import org.lwjgl.openxr.XrViewLocateInfo;
import org.lwjgl.openxr.XrViewState;
import org.lwjgl.system.MemoryStack;
import org.lwjgl.system.MemoryUtil;
import org.vivecraft.api.Vec3History;
import org.vivecraft.control.ControllerType;
import org.vivecraft.control.HandedKeyBinding;
import org.vivecraft.control.HapticScheduler;
import org.vivecraft.control.TrackedController;
import org.vivecraft.control.TrackpadSwipeSampler;
import org.vivecraft.control.VRInputAction;
import org.vivecraft.control.VRInputActionSet;
import org.vivecraft.gameplay.screenhandlers.GuiHandler;
import org.vivecraft.gameplay.screenhandlers.KeyboardHandler;
import org.vivecraft.gameplay.screenhandlers.RadialHandler;
import org.vivecraft.render.RenderPass;
import org.vivecraft.settings.VRHotkeys;
import org.vivecraft.settings.VRSettings;
import org.vivecraft.util.VLoader;
import org.vivecraft.utils.Angle;
import org.vivecraft.utils.AndroidInput;
import org.vivecraft.utils.HardwareType;
import org.vivecraft.utils.InputInjector;
import org.vivecraft.utils.KeyboardSimulator;
import org.vivecraft.utils.LangHelper;
import org.vivecraft.utils.MCReflection;
import org.vivecraft.utils.Matrix4f;
import org.vivecraft.utils.MenuWorldExporter;
import org.vivecraft.utils.OpenVRUtil;
import org.vivecraft.utils.Quaternion;
import org.vivecraft.utils.Utils;
import org.vivecraft.utils.Vector3;
import org.vivecraft.utils.jkatvr;

import com.google.common.util.concurrent.ListenableFuture;
import net.minecraft.block.BlockTorch;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiChat;
import net.minecraft.client.gui.GuiWinGame;
import net.minecraft.client.gui.inventory.GuiContainer;
import net.minecraft.client.main.Main;
import net.minecraft.client.resources.I18n;
import net.minecraft.client.settings.KeyBinding;
import net.minecraft.item.ItemBlock;
import net.minecraft.item.ItemStack;
import net.minecraft.util.MovementInputFromOptions;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.World;
import net.optifine.Lang;
import net.optifine.reflect.Reflector;
import org.apache.commons.lang3.ArrayUtils;
import org.lwjgl.input.Keyboard;
import org.lwjgl.input.Mouse;
import org.lwjgl.opengl.Display;

/**
 * Vivecraft's VR provider, on OpenXR for QuestCraft (it was SteamVR's OpenVR). The class keeps its name and its
 * static API, which the patched game classes call; the OpenXR side follows QuestCraft's MCOpenXR for Vivecraft 1.19
 * (instance, session, spaces, actions, bindings), the game side is Vivecraft 1.12.2's own.
 */
public class MCOpenVR
{
	static String initStatus;
	private static boolean initialized;
	private static boolean inputInitialized;

	static Minecraft mc;

	// OpenXR
	static XrInstance instance;
	static XrSession session;
	/** the stage (standing) space, SteamVR's TrackingUniverseStanding; poses and the submitted frame are in it */
	static XrSpace xrAppSpace;
	/** the headset's own space, the eyes are located in it for the eye-to-head transforms */
	static XrSpace xrViewSpace;
	static XrSwapchain swapchain;
	static int[] swapchainImages;
	static int swapchainWidth, swapchainHeight;
	private static long systemId;
	private static XrEventDataBuffer eventDataBuffer;
	/** the eyes in the stage space, as the frame is submitted */
	static XrView.Buffer viewBuffer;
	/** the eyes relative to the head */
	private static XrView.Buffer headViewBuffer;
	static long predictedDisplayTime;
	/** xrBeginFrame happened and xrEndFrame is still owed */
	static boolean frameBegun;
	static boolean shouldRender;
	private static boolean sessionRunning;
	private static boolean sessionFocused;
	private static final Map<String, Long> paths = new HashMap<>();
	private static final Map<Long, String> pathNames = new HashMap<>();
	private static final Map<Long, XrAction> xrActions = new HashMap<>();
	private static final Map<Long, List<Long>> boundSources = new HashMap<>();
	private static XrActiveActionSet.Buffer activeActionSetsBuffer;
	private static final String[] HANDS = {"/user/hand/left", "/user/hand/right"};
	private static final int LEFT_HAND = 0, RIGHT_HAND = 1;
	private static long gripAction, aimAction, hapticAction;
	// made with the actions: OpenXR's classes are only loaded once VR starts
	private static XrSpace[] gripSpaces;
	private static XrSpace[] aimSpaces;
	/** per controller: the aim pose in the grip pose's frame, SteamVR's "tip" render model component */
	private static final Matrix4f[] tipTransforms = {new Matrix4f(), new Matrix4f()};

	private static Matrix4f[] poseMatrices;
	private static Vec3d[] deviceVelocity;

	// position/orientation of headset and eye offsets
	private static final Matrix4f hmdPose = new Matrix4f();
	public static final Matrix4f hmdRotation = new Matrix4f();
	public static Matrix4f hmdProjectionLeftEye;
	public static Matrix4f hmdProjectionRightEye;

	static Matrix4f hmdPoseLeftEye = new Matrix4f();
	static Matrix4f hmdPoseRightEye = new Matrix4f();
	static boolean initSuccess = false, flipEyes = false;

	public static Vec3History hmdHistory = new Vec3History();
	public static Vec3History hmdPivotHistory = new Vec3History();
	public static Vec3History[] controllerHistory = new Vec3History[] { new Vec3History(), new Vec3History()};
	public static Vec3History[] controllerForwardHistory = new Vec3History[] { new Vec3History(), new Vec3History()};

	/**
	 * Do not make this public and reference it! Call the {@link #getHardwareType()} method instead!
	 */
	private static HardwareType detectedHardware = HardwareType.OCULUS;

	// aiming

	static Vec3d[] aimSource = new Vec3d[3];

	public static Vector3 offset=new Vector3(0,0,0);

	static boolean[] controllerTracking = new boolean[3];
	public static TrackedController[] controllers = new TrackedController[2];

	// Controllers
	public static final int RIGHT_CONTROLLER = 0;
	public static final int LEFT_CONTROLLER = 1;
	public static final int THIRD_CONTROLLER = 2;
	private static Matrix4f[] controllerPose = new Matrix4f[3];
	private static Matrix4f[] controllerRotation = new Matrix4f[3];
	private static Matrix4f[] handRotation = new Matrix4f[3];
	public static int[] controllerDeviceIndex = new int[3];

	public static boolean hudPopup = true;


	static boolean headIsTracking;

	private static int moveModeSwitchCount = 0;

	public static boolean isWalkingAbout;
	private static boolean isFreeRotate;
	private static ControllerType walkaboutController;
	private static ControllerType freeRotateController;
	private static float walkaboutYawStart;
	private static float hmdForwardYaw;
	public static boolean ignorePressesNextFrame = false;

	private static Map<String, VRInputAction> inputActions = new HashMap<>();
	private static Map<String, VRInputAction> inputActionsByKeyBinding = new HashMap<>();
	private static Map<VRInputActionSet, Long> actionSetHandles = new EnumMap<>(VRInputActionSet.class);

	private static Map<String, TrackpadSwipeSampler> trackpadSwipeSamplers = new HashMap<>();

	private static HapticScheduler hapticScheduler;

	public static boolean mrMovingCamActive;
	public static Vec3d mrControllerPos = Vec3d.ZERO;
	public static float mrControllerPitch;
	public static float mrControllerYaw;
	public static float mrControllerRoll;

	private static Set<KeyBinding> keyBindingSet;
	// Vivecraft bindings included
	private static Set<KeyBinding> vanillaBindingSet;

	public String getName() {
		return "OpenXR";
	}

	public String getID() {
		return "openxr";
	}

	public static final KeyBinding keyHotbarNext = new KeyBinding("vivecraft.key.hotbarNext", Keyboard.KEY_NEXT, "key.categories.inventory");
	public static final KeyBinding keyHotbarPrev = new KeyBinding("vivecraft.key.hotbarPrev", Keyboard.KEY_PRIOR, "key.categories.inventory");
	public static final KeyBinding keyHotbarScroll = new KeyBinding("vivecraft.key.hotbarScroll", Keyboard.KEY_NONE, "key.categories.inventory"); // dummy binding
	public static final KeyBinding keyHotbarSwipeX = new KeyBinding("vivecraft.key.hotbarSwipeX", Keyboard.KEY_NONE, "key.categories.inventory"); // dummy binding
	public static final KeyBinding keyHotbarSwipeY = new KeyBinding("vivecraft.key.hotbarSwipeY", Keyboard.KEY_NONE, "key.categories.inventory"); // dummy binding
	public static final KeyBinding keyRotateLeft = new KeyBinding("vivecraft.key.rotateLeft", Keyboard.KEY_LEFT, "key.categories.movement");
	public static final KeyBinding keyRotateRight = new KeyBinding("vivecraft.key.rotateRight", Keyboard.KEY_RIGHT, "key.categories.movement");
	public static final KeyBinding keyRotateAxis = new KeyBinding("vivecraft.key.rotateAxis", Keyboard.KEY_NONE, "key.categories.movement"); // dummy binding
	public static final KeyBinding keyWalkabout = new KeyBinding("vivecraft.key.walkabout", Keyboard.KEY_END, "key.categories.movement");
	public static final KeyBinding keyRotateFree = new KeyBinding("vivecraft.key.rotateFree", Keyboard.KEY_HOME, "key.categories.movement");
	public static final KeyBinding keyTeleport = new KeyBinding("vivecraft.key.teleport", Keyboard.KEY_NONE, "key.categories.movement");
	public static final KeyBinding keyTeleportFallback = new KeyBinding("vivecraft.key.teleportFallback", Keyboard.KEY_NONE, "key.categories.movement");
	public static final KeyBinding keyFreeMoveRotate = new KeyBinding("vivecraft.key.freeMoveRotate", Keyboard.KEY_NONE, "key.categories.movement"); // dummy binding
	public static final KeyBinding keyFreeMoveStrafe = new KeyBinding("vivecraft.key.freeMoveStrafe", Keyboard.KEY_NONE, "key.categories.movement"); // dummy binding
	public static final KeyBinding keyToggleMovement = new KeyBinding("vivecraft.key.toggleMovement", Keyboard.KEY_NONE, "key.categories.movement");
	public static final KeyBinding keyQuickTorch = new KeyBinding("vivecraft.key.quickTorch", Keyboard.KEY_INSERT, "key.categories.gameplay");
	public static final KeyBinding keyMenuButton = new KeyBinding("vivecraft.key.ingameMenuButton", Keyboard.KEY_NONE, "key.categories.ui");
	public static final KeyBinding keyExportWorld = new KeyBinding("vivecraft.key.exportWorld", Keyboard.KEY_NONE, "key.categories.misc");
	public static final KeyBinding keyRadialMenu = new KeyBinding("vivecraft.key.radialMenu", Keyboard.KEY_NONE, "key.categories.ui");
	public static final KeyBinding keySwapMirrorView = new KeyBinding("vivecraft.key.swapMirrorView", Keyboard.KEY_NONE, "key.categories.misc");
	public static final KeyBinding keyToggleKeyboard = new KeyBinding("vivecraft.key.toggleKeyboard", Keyboard.KEY_NONE, "key.categories.ui");
	public static final KeyBinding keyMoveThirdPersonCam = new KeyBinding("vivecraft.key.moveThirdPersonCam", Keyboard.KEY_NONE, "key.categories.misc");
	public static final KeyBinding keyTogglePlayerList = new KeyBinding("vivecraft.key.togglePlayerList", Keyboard.KEY_NONE, "key.categories.multiplayer");
	public static final HandedKeyBinding keyTrackpadTouch = new HandedKeyBinding("vivecraft.key.trackpadTouch", Keyboard.KEY_NONE, "key.categories.misc"); // used for swipe sampler
	public static final HandedKeyBinding keyVRInteract = new HandedKeyBinding("vivecraft.key.vrInteract", Keyboard.KEY_NONE,"key.categories.gameplay") {
		@Override
		public boolean isPriorityOnController(ControllerType type) {
			// TODO: use this to only take priority on the hand that's interacting
			return super.isPriorityOnController(type);
		}
	};
	public static final HandedKeyBinding keyClimbeyGrab = new HandedKeyBinding("vivecraft.key.climbeyGrab", -1,"vivecraft.key.category.climbey");
	public static final HandedKeyBinding keyClimbeyJump = new HandedKeyBinding("vivecraft.key.climbeyJump", -1,"vivecraft.key.category.climbey");
	
	
	public MCOpenVR()
	{
		super();

		for (int c=0;c<3;c++)
		{
			aimSource[c] = new Vec3d(0.0D, 0.0D, 0.0D);
			controllerPose[c] = new Matrix4f();
			controllerRotation[c] = new Matrix4f();
			handRotation[c] = new Matrix4f();
			controllerDeviceIndex[c] = -1;
		}
	}

	private static boolean tried;

	public static boolean init()  throws Exception
	{
		if ( initialized )
			return true;

		if ( tried )
			return initialized;

		tried = true;

		mc = Minecraft.getMinecraft();

		if (!System.getProperty("os.version", "").contains("Android")) {
			initStatus = "This Vivecraft runs on QuestCraft only (OpenXR on the headset)";
			return false;
		}

		try {
			initializeOpenXRInstance();
			initializeOpenXRSession();
			initializeOpenXRSpaces();
			initializeOpenXRSwapchain();
		} catch (Throwable e) {
			e.printStackTrace();
			initSuccess = false;
			initStatus = e.getLocalizedMessage();
			return false;
		}

		System.out.println( "OpenXR initialized & VR connected." );

		controllers[RIGHT_CONTROLLER] = new TrackedController(ControllerType.RIGHT);
		controllers[LEFT_CONTROLLER] = new TrackedController(ControllerType.LEFT);

		poseMatrices = new Matrix4f[64];
		deviceVelocity = new Vec3d[64];

		for(int i=0;i<poseMatrices.length;i++)
		{
			poseMatrices[i] = new Matrix4f();
			deviceVelocity[i] = new Vec3d(0,0,0);
		}

		hapticScheduler = new HapticScheduler();

		initialized = true;

		return true;
	}


	public static Set<KeyBinding> getKeyBindings() {
		if (keyBindingSet == null) {
			keyBindingSet = new LinkedHashSet<>();
			keyBindingSet.add(keyRotateLeft);
			keyBindingSet.add(keyRotateRight);
			keyBindingSet.add(keyRotateAxis);
			keyBindingSet.add(keyRotateFree);
			keyBindingSet.add(keyWalkabout);
			keyBindingSet.add(keyTeleport);
			keyBindingSet.add(keyTeleportFallback);
			keyBindingSet.add(keyFreeMoveRotate);
			keyBindingSet.add(keyFreeMoveStrafe);
			keyBindingSet.add(keyToggleMovement);
			keyBindingSet.add(keyQuickTorch);
			keyBindingSet.add(keyHotbarNext);
			keyBindingSet.add(keyHotbarPrev);
			keyBindingSet.add(keyHotbarScroll);
			keyBindingSet.add(keyHotbarSwipeX);
			keyBindingSet.add(keyHotbarSwipeY);
			keyBindingSet.add(keyMenuButton);
			keyBindingSet.add(keyRadialMenu);
			keyBindingSet.add(keyVRInteract);
			keyBindingSet.add(keySwapMirrorView);
			keyBindingSet.add(keyExportWorld);
			keyBindingSet.add(keyToggleKeyboard);
			keyBindingSet.add(keyMoveThirdPersonCam);
			keyBindingSet.add(keyTogglePlayerList);
			keyBindingSet.add(keyTrackpadTouch);
			keyBindingSet.add(GuiHandler.keyLeftClick);
			keyBindingSet.add(GuiHandler.keyRightClick);
			keyBindingSet.add(GuiHandler.keyMiddleClick);
			keyBindingSet.add(GuiHandler.keyShift);
			keyBindingSet.add(GuiHandler.keyCtrl);
			keyBindingSet.add(GuiHandler.keyAlt);
			keyBindingSet.add(GuiHandler.keyScrollUp);
			keyBindingSet.add(GuiHandler.keyScrollDown);
			keyBindingSet.add(GuiHandler.keyScrollAxis);
			keyBindingSet.add(GuiHandler.keyKeyboardClick);
			keyBindingSet.add(GuiHandler.keyKeyboardShift);
			keyBindingSet.add(keyClimbeyGrab);
			keyBindingSet.add(keyClimbeyJump);
		}

		return keyBindingSet;
	}

	@SuppressWarnings("unchecked")
	public static KeyBinding[] initializeBindings(KeyBinding[] keyBindings) {
		for (KeyBinding keyBinding : getKeyBindings())
			keyBindings = ArrayUtils.add(keyBindings, keyBinding);

		// Copy the bindings array here so we know which ones are from mods
		setVanillaBindings(keyBindings);

		Map<String, Integer> co = (Map<String, Integer>) MCReflection.KeyBinding_CATEGORY_ORDER.get(null);
		co.put("vivecraft.key.category.gui", 8);
		co.put("vivecraft.key.category.climbey", 9);
		co.put("vivecraft.key.category.keyboard", 10);

		return keyBindings;
	}

	private static class ActionParams {
		final String requirement;
		final String type;
		final VRInputActionSet actionSetOverride;

		ActionParams(String requirement, String type, VRInputActionSet actionSetOverride) {
			this.requirement = requirement;
			this.type = type;
			this.actionSetOverride = actionSetOverride;
		}
	}

	public static void initInputAndApplication() {
		populateInputActions();
		if (!initialized) return;
		try {
			createActions();
			suggestBindings();
			attachActions();
			inputInitialized = true;
		} catch (Throwable e) {
			e.printStackTrace();
			System.out.println("Controller input not available. Forcing seated mode.");
			mc.vrSettings.seated = true;
		}
	}

	private static void populateInputActions() {
		Map<String, ActionParams> actionParams = getSpecialActionParams();
		for (final KeyBinding keyBinding : mc.gameSettings.keyBindings) {
			ActionParams params = actionParams.getOrDefault(keyBinding.getKeyDescription(), new ActionParams("optional", "boolean", null));
			VRInputAction action = new VRInputAction(keyBinding, params.requirement, params.type, params.actionSetOverride);
			inputActions.put(action.name, action);
		}
		for (VRInputAction action : inputActions.values()) {
			inputActionsByKeyBinding.put(action.keyBinding.getKeyDescription(), action);
		}

		// TODO: implement VR interact
		getInputAction(MCOpenVR.keyVRInteract).setPriority(5).setEnabled(false);
		getInputAction(MCOpenVR.keyClimbeyGrab).setPriority(10).setEnabled(false);
		//getInputAction(MCOpenVR.keyClimbeyJump).setPriority(10).setEnabled(false);
		getInputAction(MCOpenVR.keyClimbeyJump).setEnabled(false);
		getInputAction(GuiHandler.keyKeyboardClick).setPriority(50);
		getInputAction(GuiHandler.keyKeyboardShift).setPriority(50);
	}

	// This is for bindings with specific requirement/type params, anything not listed will default to optional and boolean
	// See OpenVR docs for valid values: https://github.com/ValveSoftware/openvr/wiki/Action-manifest#actions
	private static Map<String, ActionParams> getSpecialActionParams() {
		Map<String, ActionParams> map = new HashMap<>();

		addActionParams(map, mc.gameSettings.keyBindForward, "optional", "vector1", null);
		addActionParams(map, mc.gameSettings.keyBindBack, "optional", "vector1", null);
		addActionParams(map, mc.gameSettings.keyBindLeft, "optional", "vector1", null);
		addActionParams(map, mc.gameSettings.keyBindRight, "optional", "vector1", null);
		addActionParams(map, mc.gameSettings.keyBindInventory, "suggested", "boolean", VRInputActionSet.GLOBAL);
		addActionParams(map, mc.gameSettings.keyBindAttack, "suggested", "boolean", null);
		addActionParams(map, mc.gameSettings.keyBindUseItem, "suggested", "boolean", null);
		addActionParams(map, mc.gameSettings.keyBindChat, "optional", "boolean", VRInputActionSet.GLOBAL);
		addActionParams(map, MCOpenVR.keyHotbarScroll, "optional", "vector2", null);
		addActionParams(map, MCOpenVR.keyHotbarSwipeX, "optional", "vector2", null);
		addActionParams(map, MCOpenVR.keyHotbarSwipeY, "optional", "vector2", null);
		addActionParams(map, MCOpenVR.keyMenuButton, "suggested", "boolean", VRInputActionSet.GLOBAL);
		addActionParams(map, MCOpenVR.keyTeleportFallback, "suggested", "vector1", null);
		addActionParams(map, MCOpenVR.keyFreeMoveRotate, "optional", "vector2", null);
		addActionParams(map, MCOpenVR.keyFreeMoveStrafe, "optional", "vector2", null);
		addActionParams(map, MCOpenVR.keyRotateLeft, "optional", "vector1", null);
		addActionParams(map, MCOpenVR.keyRotateRight, "optional", "vector1", null);
		addActionParams(map, MCOpenVR.keyRotateAxis, "optional", "vector2", null);
		addActionParams(map, MCOpenVR.keyRadialMenu, "suggested", "boolean", null);
		addActionParams(map, MCOpenVR.keySwapMirrorView, "optional", "boolean", VRInputActionSet.GLOBAL);
		addActionParams(map, MCOpenVR.keyToggleKeyboard, "optional", "boolean", VRInputActionSet.GLOBAL);
		addActionParams(map, MCOpenVR.keyMoveThirdPersonCam, "optional", "boolean", VRInputActionSet.GLOBAL);
		addActionParams(map, MCOpenVR.keyTrackpadTouch, "optional", "boolean", VRInputActionSet.TECHNICAL);
		addActionParams(map, MCOpenVR.keyVRInteract, "suggested", "boolean", VRInputActionSet.CONTEXTUAL);
		addActionParams(map, MCOpenVR.keyClimbeyGrab, "suggested", "boolean", null);
		addActionParams(map, MCOpenVR.keyClimbeyJump, "suggested", "boolean", null);
		addActionParams(map, GuiHandler.keyLeftClick, "suggested", "boolean", null);
		addActionParams(map, GuiHandler.keyScrollAxis, "optional", "vector2", null);
		addActionParams(map, GuiHandler.keyRightClick, "suggested", "boolean", null);
		addActionParams(map, GuiHandler.keyShift, "suggested", "boolean", null);
		addActionParams(map, GuiHandler.keyKeyboardClick, "suggested", "boolean", null);
		addActionParams(map, GuiHandler.keyKeyboardShift, "suggested", "boolean", null);

		File file = new File("customactionsets.txt");
		if (file.exists()) {
			System.out.println("Loading custom action set definitions...");
			try (BufferedReader br = new BufferedReader(new FileReader(file))) {
				String line;
				while ((line = br.readLine()) != null) {
					String[] tokens = line.split(":", 2);
					if (tokens.length < 2) {
						System.out.println("Invalid tokens: " + line);
						continue;
					}

					KeyBinding keyBinding = findKeyBinding(tokens[0]);
					if (keyBinding == null) {
						System.out.println("Unknown key binding: " + tokens[0]);
						continue;
					}
					if (getKeyBindings().contains(keyBinding)) {
						System.out.println("NO! Don't touch Vivecraft bindings!");
						continue;
					}

					VRInputActionSet actionSet = null;
					switch (tokens[1].toLowerCase()) {
						case "ingame":
							actionSet = VRInputActionSet.INGAME;
							break;
						case "gui":
							actionSet = VRInputActionSet.GUI;
							break;
						case "global":
							actionSet = VRInputActionSet.GLOBAL;
							break;
					}
					if (actionSet == null) {
						System.out.println("Unknown action set: " + tokens[1]);
						continue;
					}

					addActionParams(map, keyBinding, "optional", "boolean", actionSet);
				}
			} catch (IOException e) {
				e.printStackTrace();
			}
		}

		return map;
	}

	private static void addActionParams(Map<String, ActionParams> map, KeyBinding keyBinding, String requirement, String type, VRInputActionSet actionSetOverride) {
		ActionParams params = new ActionParams(requirement, type, actionSetOverride);
		map.put(keyBinding.getKeyDescription(), params);
	}

	private static KeyBinding findKeyBinding(String name) {
		return Arrays.stream(mc.gameSettings.keyBindings).filter(kb -> name.equals(kb.getKeyDescription())).findFirst().orElse(null);
	}

	public static final String ACTION_LEFT_HAND = "/actions/global/in/lefthand";
	public static final String ACTION_RIGHT_HAND = "/actions/global/in/righthand";
	public static final String ACTION_LEFT_HAPTIC = "/actions/global/out/lefthaptic";
	public static final String ACTION_RIGHT_HAPTIC = "/actions/global/out/righthaptic";
	public static final String ACTION_EXTERNAL_CAMERA = "/actions/mixedreality/in/externalcamera";

	private static void initializeOpenXRInstance() {
		try (MemoryStack stack = MemoryStack.stackPush()) {
			System.out.println("Platform: " + System.getProperty("os.version"));
			// as QuestCraft's Vivecraft: the launcher hands the headset over to the game, then the loader starts
			VLoader.setupAndroid();
			try { Thread.sleep(5000); } catch (InterruptedException ignored) { }
			XrLoaderInitInfoAndroidKHR loaderInfo = XrLoaderInitInfoAndroidKHR.calloc(stack).set(
					KHRLoaderInitAndroid.XR_TYPE_LOADER_INIT_INFO_ANDROID_KHR, MemoryUtil.NULL,
					VLoader.getDalvikVM(), VLoader.getDalvikActivity());
			check(KHRLoaderInit.xrInitializeLoaderKHR(XrLoaderInitInfoBaseHeaderKHR.create(loaderInfo.address())), "xrInitializeLoaderKHR");

			IntBuffer count = stack.callocInt(1);
			check(XR10.xrEnumerateInstanceExtensionProperties((ByteBuffer) null, count, null), "xrEnumerateInstanceExtensionProperties");
			XrExtensionProperties.Buffer properties = XrExtensionProperties.calloc(count.get(0), stack);
			for (XrExtensionProperties property : properties)
				property.type(XR10.XR_TYPE_EXTENSION_PROPERTIES);
			check(XR10.xrEnumerateInstanceExtensionProperties((ByteBuffer) null, count, properties), "xrEnumerateInstanceExtensionProperties");

			Set<String> available = new HashSet<>();
			for (XrExtensionProperties property : properties)
				available.add(property.extensionNameString());
			if (!available.contains(KHROpenGLESEnable.XR_KHR_OPENGL_ES_ENABLE_EXTENSION_NAME))
				throw new RuntimeException("The OpenXR runtime has no OpenGL ES support");

			PointerBuffer extensions = stack.callocPointer(2);
			extensions.put(MemoryUtil.memAddress(stack.UTF8(KHROpenGLESEnable.XR_KHR_OPENGL_ES_ENABLE_EXTENSION_NAME)));
			if (available.contains(KHRAndroidCreateInstance.XR_KHR_ANDROID_CREATE_INSTANCE_EXTENSION_NAME))
				extensions.put(MemoryUtil.memAddress(stack.UTF8(KHRAndroidCreateInstance.XR_KHR_ANDROID_CREATE_INSTANCE_EXTENSION_NAME)));
			extensions.flip();

			XrApplicationInfo applicationInfo = XrApplicationInfo.calloc(stack);
			applicationInfo.apiVersion(XR10.XR_MAKE_VERSION(1, 0, 40));
			applicationInfo.applicationName(stack.UTF8("Vivecraft"));
			applicationInfo.applicationVersion(1);

			XrInstanceCreateInfoAndroidKHR androidInfo = XrInstanceCreateInfoAndroidKHR.calloc(stack).set(
					KHRAndroidCreateInstance.XR_TYPE_INSTANCE_CREATE_INFO_ANDROID_KHR, MemoryUtil.NULL,
					VLoader.getDalvikVM(), VLoader.getDalvikActivity());

			XrInstanceCreateInfo createInfo = XrInstanceCreateInfo.calloc(stack);
			createInfo.type(XR10.XR_TYPE_INSTANCE_CREATE_INFO);
			createInfo.next(androidInfo.address());
			createInfo.createFlags(0);
			createInfo.applicationInfo(applicationInfo);
			createInfo.enabledApiLayerNames(null);
			createInfo.enabledExtensionNames(extensions);

			PointerBuffer instancePointer = stack.callocPointer(1);
			int error = XR10.xrCreateInstance(createInfo, instancePointer);
			if (error < 0)
				throw new RuntimeException("Could not create the OpenXR instance: " + resultName(error));
			instance = new XrInstance(instancePointer.get(0), createInfo);
			eventDataBuffer = XrEventDataBuffer.calloc();
			initSuccess = true;
		}
	}

	private static void initializeOpenXRSession() {
		try (MemoryStack stack = MemoryStack.stackPush()) {
			XrSystemGetInfo systemInfo = XrSystemGetInfo.calloc(stack);
			systemInfo.type(XR10.XR_TYPE_SYSTEM_GET_INFO);
			systemInfo.formFactor(XR10.XR_FORM_FACTOR_HEAD_MOUNTED_DISPLAY);
			LongBuffer system = stack.callocLong(1);
			check(XR10.xrGetSystem(instance, systemInfo, system), "xrGetSystem");
			systemId = system.get(0);
			if (systemId == 0)
				throw new RuntimeException("No headset found");

			XrSystemProperties systemProperties = XrSystemProperties.calloc(stack).type(XR10.XR_TYPE_SYSTEM_PROPERTIES);
			logError(XR10.xrGetSystemProperties(instance, systemId, systemProperties), "xrGetSystemProperties");
			System.out.println("Headset: " + systemProperties.systemNameString() + ", vendor " + systemProperties.vendorId());
			detectedHardware = HardwareType.OCULUS;
			mc.vrSettings.loadOptions();

			// OpenXR wants the graphics requirements asked for before a session is made
			XrGraphicsRequirementsOpenGLESKHR requirements = XrGraphicsRequirementsOpenGLESKHR.calloc(stack)
					.type(KHROpenGLESEnable.XR_TYPE_GRAPHICS_REQUIREMENTS_OPENGL_ES_KHR);
			check(KHROpenGLESEnable.xrGetOpenGLESGraphicsRequirementsKHR(instance, systemId, requirements), "xrGetOpenGLESGraphicsRequirementsKHR");

			// the game's own EGL context (gl4es draws through it), current on this thread
			XrGraphicsBindingOpenGLESAndroidKHR graphicsBinding = XrGraphicsBindingOpenGLESAndroidKHR.calloc(stack).set(
					KHROpenGLESEnable.XR_TYPE_GRAPHICS_BINDING_OPENGL_ES_ANDROID_KHR, MemoryUtil.NULL,
					VLoader.getEGLDisplay(), VLoader.getEGLConfig(), VLoader.getEGLContext());

			XrSessionCreateInfo sessionInfo = XrSessionCreateInfo.calloc(stack);
			sessionInfo.type(XR10.XR_TYPE_SESSION_CREATE_INFO);
			sessionInfo.next(graphicsBinding.address());
			sessionInfo.createFlags(0);
			sessionInfo.systemId(systemId);
			PointerBuffer sessionPointer = stack.callocPointer(1);
			check(XR10.xrCreateSession(instance, sessionInfo, sessionPointer), "xrCreateSession");
			session = new XrSession(sessionPointer.get(0), instance);

			System.out.println("Vivecraft: waiting for the OpenXR session to start");
			long until = System.currentTimeMillis() + 15000;
			while (!sessionRunning && System.currentTimeMillis() < until) {
				pollVREvents();
				if (!sessionRunning) {
					try { Thread.sleep(10); } catch (InterruptedException ignored) { }
				}
			}
		}
	}

	private static void initializeOpenXRSpaces() {
		try (MemoryStack stack = MemoryStack.stackPush()) {
			XrReferenceSpaceCreateInfo spaceInfo = XrReferenceSpaceCreateInfo.calloc(stack);
			spaceInfo.type(XR10.XR_TYPE_REFERENCE_SPACE_CREATE_INFO);
			spaceInfo.poseInReferenceSpace(identityPose(stack));
			PointerBuffer space = stack.callocPointer(1);

			spaceInfo.referenceSpaceType(XR10.XR_REFERENCE_SPACE_TYPE_STAGE);
			check(XR10.xrCreateReferenceSpace(session, spaceInfo, space), "xrCreateReferenceSpace stage");
			xrAppSpace = new XrSpace(space.get(0), session);

			spaceInfo.referenceSpaceType(XR10.XR_REFERENCE_SPACE_TYPE_VIEW);
			check(XR10.xrCreateReferenceSpace(session, spaceInfo, space), "xrCreateReferenceSpace view");
			xrViewSpace = new XrSpace(space.get(0), session);
		}
	}

	private static XrPosef identityPose(MemoryStack stack) {
		XrPosef pose = XrPosef.calloc(stack);
		pose.orientation().set(0, 0, 0, 1);
		return pose;
	}

	private static void initializeOpenXRSwapchain() {
		try (MemoryStack stack = MemoryStack.stackPush()) {
			IntBuffer count = stack.callocInt(1);
			check(XR10.xrEnumerateViewConfigurationViews(instance, systemId, XR10.XR_VIEW_CONFIGURATION_TYPE_PRIMARY_STEREO, count, null), "xrEnumerateViewConfigurationViews");
			XrViewConfigurationView.Buffer views = XrViewConfigurationView.calloc(count.get(0), stack);
			for (XrViewConfigurationView view : views)
				view.type(XR10.XR_TYPE_VIEW_CONFIGURATION_VIEW);
			check(XR10.xrEnumerateViewConfigurationViews(instance, systemId, XR10.XR_VIEW_CONFIGURATION_TYPE_PRIMARY_STEREO, count, views), "xrEnumerateViewConfigurationViews");

			viewBuffer = XrView.calloc(2);
			headViewBuffer = XrView.calloc(2);
			for (int i = 0; i < 2; i++) {
				viewBuffer.get(i).type(XR10.XR_TYPE_VIEW);
				headViewBuffer.get(i).type(XR10.XR_TYPE_VIEW);
				viewBuffer.get(i).pose().orientation().w(1);
				headViewBuffer.get(i).pose().orientation().w(1);
			}

			check(XR10.xrEnumerateSwapchainFormats(session, count, null), "xrEnumerateSwapchainFormats");
			LongBuffer formats = stack.callocLong(count.get(0));
			check(XR10.xrEnumerateSwapchainFormats(session, count, formats), "xrEnumerateSwapchainFormats");
			// sRGB as QuestCraft's Vivecraft: the eye images are gamma encoded and get copied in as they are
			long chosen = 0;
			for (long wanted : new long[] {0x8C43 /* GL_SRGB8_ALPHA8 */, 0x8058 /* GL_RGBA8 */}) {
				for (int i = 0; i < formats.limit() && chosen == 0; i++) {
					if (formats.get(i) == wanted)
						chosen = wanted;
				}
			}
			if (chosen == 0)
				throw new RuntimeException("No usable swapchain format");

			XrViewConfigurationView view = views.get(0);
			XrSwapchainCreateInfo swapchainInfo = XrSwapchainCreateInfo.calloc(stack);
			swapchainInfo.type(XR10.XR_TYPE_SWAPCHAIN_CREATE_INFO);
			swapchainInfo.usageFlags(XR10.XR_SWAPCHAIN_USAGE_COLOR_ATTACHMENT_BIT);
			swapchainInfo.format(chosen);
			swapchainInfo.sampleCount(1);
			swapchainInfo.width(view.recommendedImageRectWidth());
			swapchainInfo.height(view.recommendedImageRectHeight());
			swapchainInfo.faceCount(1);
			swapchainInfo.arraySize(2); // one layer per eye
			swapchainInfo.mipCount(1);
			PointerBuffer handle = stack.callocPointer(1);
			check(XR10.xrCreateSwapchain(session, swapchainInfo, handle), "xrCreateSwapchain");
			swapchain = new XrSwapchain(handle.get(0), session);
			swapchainWidth = swapchainInfo.width();
			swapchainHeight = swapchainInfo.height();

			check(XR10.xrEnumerateSwapchainImages(swapchain, count, null), "xrEnumerateSwapchainImages");
			XrSwapchainImageOpenGLESKHR.Buffer images = XrSwapchainImageOpenGLESKHR.calloc(count.get(0), stack);
			for (XrSwapchainImageOpenGLESKHR image : images)
				image.type(KHROpenGLESEnable.XR_TYPE_SWAPCHAIN_IMAGE_OPENGL_ES_KHR);
			check(XR10.xrEnumerateSwapchainImages(swapchain, count, XrSwapchainImageBaseHeader.create(images.address(), images.capacity())), "xrEnumerateSwapchainImages");
			swapchainImages = new int[images.capacity()];
			for (int i = 0; i < swapchainImages.length; i++)
				swapchainImages[i] = images.get(i).image();
			System.out.println("OpenXR swapchain: " + swapchainWidth + " x " + swapchainHeight + ", format 0x" + Long.toHexString(chosen) + ", " + swapchainImages.length + " images");
		}
	}

	private static void createActions() {
		for (VRInputActionSet set : VRInputActionSet.values()) {
			try (MemoryStack stack = MemoryStack.stackPush()) {
				String name = set.localizedName.toLowerCase();
				XrActionSetCreateInfo info = XrActionSetCreateInfo.calloc(stack);
				info.type(XR10.XR_TYPE_ACTION_SET_CREATE_INFO);
				info.actionSetName(stack.UTF8(name));
				info.localizedActionSetName(stack.UTF8(name));
				info.priority(0);
				PointerBuffer handle = stack.callocPointer(1);
				check(XR10.xrCreateActionSet(instance, info, handle), "xrCreateActionSet " + name);
				actionSetHandles.put(set, handle.get(0));
			}
		}

		Map<VRInputActionSet, Set<String>> taken = new EnumMap<>(VRInputActionSet.class);
		for (VRInputAction action : inputActions.values()) {
			Set<String> names = taken.computeIfAbsent(action.actionSet, k -> new HashSet<>());
			String name = actionName(action.name);
			for (int i = 2; names.contains(name); i++)
				name = actionName(action.name) + "_" + i;
			names.add(name);
			int type;
			switch (action.type) {
				case "vector1": type = XR10.XR_ACTION_TYPE_FLOAT_INPUT; break;
				case "vector2":
				case "vector3": type = XR10.XR_ACTION_TYPE_VECTOR2F_INPUT; break;
				default: type = XR10.XR_ACTION_TYPE_BOOLEAN_INPUT; break;
			}
			long handle = createAction(name, type, action.actionSet);
			if (handle != 0)
				action.setHandle(handle);
		}

		gripAction = createAction("handpose", XR10.XR_ACTION_TYPE_POSE_INPUT, VRInputActionSet.GLOBAL);
		aimAction = createAction("handaim", XR10.XR_ACTION_TYPE_POSE_INPUT, VRInputActionSet.GLOBAL);
		hapticAction = createAction("haptic", XR10.XR_ACTION_TYPE_VIBRATION_OUTPUT, VRInputActionSet.GLOBAL);
	}

	/** OpenXR action names: lower case letters, digits, '-', '_' and '.' */
	private static String actionName(String vivecraftName) {
		String[] parts = vivecraftName.split("/");
		return parts[parts.length - 1].toLowerCase().replaceAll("[^a-z0-9._-]", "_");
	}

	private static long createAction(String name, int type, VRInputActionSet set) {
		try (MemoryStack stack = MemoryStack.stackPush()) {
			LongBuffer hands = stack.callocLong(2);
			hands.put(0, getPath(HANDS[LEFT_HAND]));
			hands.put(1, getPath(HANDS[RIGHT_HAND]));
			XrActionCreateInfo info = XrActionCreateInfo.calloc(stack);
			info.type(XR10.XR_TYPE_ACTION_CREATE_INFO);
			info.actionName(stack.UTF8(name));
			info.actionType(type);
			info.subactionPaths(hands);
			info.localizedActionName(stack.UTF8(name));
			PointerBuffer handle = stack.callocPointer(1);
			int error = XR10.xrCreateAction(actionSet(set), info, handle);
			logError(error, "xrCreateAction", name);
			if (error < 0)
				return 0;
			xrActions.put(handle.get(0), new XrAction(handle.get(0), actionSet(set)));
			return handle.get(0);
		}
	}

	private static XrActionSet actionSet(VRInputActionSet set) {
		return new XrActionSet(actionSetHandles.get(set), instance);
	}

	/** Quest's defaults, as QuestCraft's Vivecraft (XRBindings), plus the hand poses and haptics */
	private static void suggestBindings() {
		try (MemoryStack stack = MemoryStack.stackPush()) {
			List<long[]> pairs = new ArrayList<>();
			for (String[] binding : XRBindings.QUEST) {
				VRInputAction action = getInputActionByName(binding[0]);
				if (action == null || action.getHandle() == 0) {
					System.out.println("Vivecraft: no action " + binding[0] + " for " + binding[1]);
					continue;
				}
				pairs.add(new long[] {action.getHandle(), getPath(binding[1])});
			}
			for (int hand = 0; hand < 2; hand++) {
				pairs.add(new long[] {gripAction, getPath(HANDS[hand] + "/input/grip/pose")});
				pairs.add(new long[] {aimAction, getPath(HANDS[hand] + "/input/aim/pose")});
				pairs.add(new long[] {hapticAction, getPath(HANDS[hand] + "/output/haptic")});
			}

			XrActionSuggestedBinding.Buffer bindings = XrActionSuggestedBinding.calloc(pairs.size(), stack);
			for (int i = 0; i < pairs.size(); i++)
				bindings.get(i).set(xrActions.get(pairs.get(i)[0]), pairs.get(i)[1]);

			XrInteractionProfileSuggestedBinding suggested = XrInteractionProfileSuggestedBinding.calloc(stack);
			suggested.type(XR10.XR_TYPE_INTERACTION_PROFILE_SUGGESTED_BINDING);
			suggested.interactionProfile(getPath(XRBindings.QUEST_PROFILE));
			suggested.suggestedBindings(bindings);
			check(XR10.xrSuggestInteractionProfileBindings(instance, suggested), "xrSuggestInteractionProfileBindings");
		}
	}

	private static void attachActions() {
		try (MemoryStack stack = MemoryStack.stackPush()) {
			PointerBuffer sets = stack.callocPointer(actionSetHandles.size());
			for (long handle : actionSetHandles.values())
				sets.put(handle);
			sets.flip();
			XrSessionActionSetsAttachInfo attachInfo = XrSessionActionSetsAttachInfo.calloc(stack);
			attachInfo.type(XR10.XR_TYPE_SESSION_ACTION_SETS_ATTACH_INFO);
			attachInfo.actionSets(sets);
			check(XR10.xrAttachSessionActionSets(session, attachInfo), "xrAttachSessionActionSets");

			gripSpaces = new XrSpace[2];
			aimSpaces = new XrSpace[2];
			for (int hand = 0; hand < 2; hand++) {
				gripSpaces[hand] = createActionSpace(gripAction, HANDS[hand], stack);
				aimSpaces[hand] = createActionSpace(aimAction, HANDS[hand], stack);
			}
		}
	}

	private static XrSpace createActionSpace(long action, String hand, MemoryStack stack) {
		XrActionSpaceCreateInfo info = XrActionSpaceCreateInfo.calloc(stack);
		info.type(XR10.XR_TYPE_ACTION_SPACE_CREATE_INFO);
		info.action(xrActions.get(action));
		info.subactionPath(getPath(hand));
		info.poseInActionSpace(identityPose(stack));
		PointerBuffer space = stack.callocPointer(1);
		check(XR10.xrCreateActionSpace(session, info, space), "xrCreateActionSpace " + hand);
		return new XrSpace(space.get(0), session);
	}

	static long getPath(String path) {
		Long known = paths.get(path);
		if (known != null)
			return known;
		try (MemoryStack stack = MemoryStack.stackPush()) {
			LongBuffer buffer = stack.callocLong(1);
			check(XR10.xrStringToPath(instance, path, buffer), "xrStringToPath " + path);
			paths.put(path, buffer.get(0));
			pathNames.put(buffer.get(0), path);
			return buffer.get(0);
		}
	}

	private static String pathString(long path) {
		if (path == 0)
			return "";
		String known = pathNames.get(path);
		if (known != null)
			return known;
		try (MemoryStack stack = MemoryStack.stackPush()) {
			IntBuffer length = stack.callocInt(1);
			if (XR10.xrPathToString(instance, path, length, null) < 0 || length.get(0) <= 0)
				return "";
			ByteBuffer text = stack.calloc(length.get(0));
			XR10.xrPathToString(instance, path, length, text);
			String name = MemoryUtil.memUTF8(text, length.get(0) - 1);
			pathNames.put(path, name);
			return name;
		}
	}

	private static List<VRInputActionSet> activeActionSets() {
		ArrayList<VRInputActionSet> list = new ArrayList<>();
		list.add(VRInputActionSet.GLOBAL);
		if (Reflector.forgeExists())
			list.add(VRInputActionSet.MOD);
		list.add(VRInputActionSet.MIXED_REALITY);
		list.add(VRInputActionSet.TECHNICAL);
		if (mc.currentScreen == null) {
			list.add(VRInputActionSet.INGAME);
			if (getInputActionsInSet(VRInputActionSet.CONTEXTUAL).stream().anyMatch(VRInputAction::isEnabledRaw))
				list.add(VRInputActionSet.CONTEXTUAL);
		} else {
			list.add(VRInputActionSet.GUI);
		}
		if (KeyboardHandler.Showing || RadialHandler.isShowing())
			list.add(VRInputActionSet.KEYBOARD);
		return list;
	}

	private static void syncActions(MemoryStack stack) {
		List<VRInputActionSet> list = activeActionSets();
		if (activeActionSetsBuffer == null || activeActionSetsBuffer.capacity() != list.size()) {
			if (activeActionSetsBuffer != null)
				activeActionSetsBuffer.free();
			activeActionSetsBuffer = XrActiveActionSet.calloc(list.size());
		}
		for (int i = 0; i < list.size(); i++)
			activeActionSetsBuffer.get(i).set(actionSet(list.get(i)), MemoryUtil.NULL);
		XrActionsSyncInfo syncInfo = XrActionsSyncInfo.calloc(stack);
		syncInfo.type(XR10.XR_TYPE_ACTIONS_SYNC_INFO);
		syncInfo.activeActionSets(activeActionSetsBuffer);
		logError(XR10.xrSyncActions(session, syncInfo), "xrSyncActions");
	}

	private static void check(int result, String what) {
		if (result < 0)
			throw new RuntimeException(what + " failed: " + resultName(result));
	}

	private static void logError(int result, String what, String... details) {
		if (result < 0)
			System.out.println("OpenXR: " + what + (details.length > 0 ? " " + String.join(" ", details) : "") + " failed: " + resultName(result));
	}

	static String resultName(int result) {
		if (instance != null) {
			try (MemoryStack stack = MemoryStack.stackPush()) {
				ByteBuffer text = stack.calloc(XR10.XR_MAX_RESULT_STRING_SIZE);
				if (XR10.xrResultToString(instance, result, text) >= 0)
					return MemoryUtil.memUTF8(MemoryUtil.memAddress(text));
			}
		}
		return "XrResult " + result;
	}

	private static boolean gunStyle = false;

	/** SteamVR's render model components: "tip" is where the controller aims from, everything else its origin */
	public static Matrix4f getControllerComponentTransform(int controllerIndex, String componenetName){
		if ("tip".equals(componenetName) && controllerIndex >= 0 && controllerIndex < 2)
			return tipTransforms[controllerIndex];
		return OpenVRUtil.Matrix4fSetIdentity(new Matrix4f());
	}

	public static Matrix4f getControllerComponentTransformFromButton(int controllerIndex, long button){
		return getControllerComponentTransform(controllerIndex, "");
	}

	public static boolean hasOpenComposite() {
		return false;
	}

	public static boolean paused =false;

	public static void poll(long frameIndex)
	{
		if (!initialized) return;

		mc.mcProfiler.startSection("events");
		pollVREvents();
		// SteamVR's ShouldApplicationPause: the headset's system menu is up
		paused = sessionRunning && !sessionFocused;

		if(!mc.vrSettings.seated){
			mc.mcProfiler.endStartSection("controllers");


			// GUI controls

			mc.mcProfiler.startSection("gui");

			if(mc.currentScreen == null && mc.vrSettings.vrTouchHotbar && mc.vrSettings.vrHudLockMode != mc.vrSettings.HUD_LOCK_HEAD && hudPopup){
				processHotbar();
			}

			mc.mcProfiler.endSection();
		}

		mc.mcProfiler.endStartSection("updatePose");
		updatePose();

		mc.mcProfiler.endSection();
		processInputs();

		mc.mcProfiler.endSection();
	}

	private static int quickTorchPreviousSlot;

	private static void processHotbar() {
		mc.interactTracker.hotbar = -1;
		if(mc.player == null) return;
		if(mc.player.inventory == null) return;
		
		if(mc.climbTracker.isGrabbingLadder() && 
				mc.climbTracker.isClaws(mc.player.getHeldItemMainhand())) return;
		if(!mc.interactTracker.isActive(mc.player)) return;
		Vec3d main = getAimSource(0);
		Vec3d off = getAimSource(1);

		Vec3d barStartos = null,barEndos = null;

		int i = 1;
		if(mc.vrSettings.vrReverseHands) i = -1;

		if (mc.vrSettings.vrHudLockMode == VRSettings.HUD_LOCK_WRIST){
			barStartos =  getAimRotation(1).transform(new Vector3(i*0.02f,0.05f,0.26f)).toVec3d();
			barEndos =  getAimRotation(1).transform(new Vector3(i*0.02f,0.05f,0.01f)).toVec3d();
		} else if (mc.vrSettings.vrHudLockMode == VRSettings.HUD_LOCK_HAND){
			barStartos =  getAimRotation(1).transform(new Vector3(i*-.18f,0.08f,-0.01f)).toVec3d();
			barEndos =  getAimRotation(1).transform(new Vector3(i*0.19f,0.04f,-0.08f)).toVec3d();
		} else return; //how did u get here


		Vec3d barStart = off.addVector(barStartos.x, barStartos.y, barStartos.z);	
		Vec3d barEnd = off.addVector(barEndos.x, barEndos.y, barEndos.z);

		Vec3d u = barStart.subtract(barEnd);
		Vec3d pq = barStart.subtract(main);
		float dist = (float) (pq.crossProduct(u).lengthVector() / u.lengthVector());

		if(dist > 0.06) return;

		float fact = (float) (pq.dotProduct(u) / (u.x*u.x + u.y*u.y + u.z*u.z));

		if(fact < -1) return;
		
		Vec3d w2 = u.scale(fact).subtract(pq);

		Vec3d point = main.subtract(w2);
		float linelen = (float) u.lengthVector();
		float ilen = (float) barStart.subtract(point).lengthVector();
		if(fact < 0) ilen *= -1;
		float pos = ilen / linelen * 9; 

		if(mc.vrSettings.vrReverseHands) pos = 9 - pos;

		int box = (int) Math.floor(pos);

		if(box > 8) return;
		if(box < 0) {
			if(pos <= -0.5 && pos >= -1.5) //TODO fix reversed hands situation.
				box = 9;
			else
				return;
		}
		//all that maths for this.
		mc.interactTracker.hotbar = box;
		if(box != mc.interactTracker.hotbar){
			triggerHapticPulse(0, 750);
		}
	}

	
	public static void destroy()
	{
		if (initialized)
		{
			try {
				if (gripSpaces != null)
					for (XrSpace space : new XrSpace[] {gripSpaces[0], gripSpaces[1], aimSpaces[0], aimSpaces[1]})
						if (space != null) XR10.xrDestroySpace(space);
				if (xrViewSpace != null) XR10.xrDestroySpace(xrViewSpace);
				if (xrAppSpace != null) XR10.xrDestroySpace(xrAppSpace);
				if (swapchain != null) XR10.xrDestroySwapchain(swapchain);
				if (session != null) XR10.xrDestroySession(session);
				if (instance != null) XR10.xrDestroyInstance(instance);
				initialized = false;
			} catch (Throwable e) {
				e.printStackTrace();
			}
		}
	}

	private static void processScrollInput(KeyBinding keyBinding, Runnable upCallback, Runnable downCallback) {
		VRInputAction action = getInputAction(keyBinding);
		if (action.isEnabled() && action.getLastOrigin() != 0L && action.getAxis2D(true).getY() != 0) {
			float value = action.getAxis2D(false).getY();
			if (value > 0)
				upCallback.run();
			else if (value < 0)
				downCallback.run();
		}
	}

	private static void processSwipeInput(KeyBinding keyBinding, Runnable leftCallback, Runnable rightCallback, Runnable upCallback, Runnable downCallback) {
		VRInputAction action = getInputAction(keyBinding);
		if (action.isEnabled() && action.getLastOrigin() != 0L) {
			ControllerType controller = findActiveBindingControllerType(keyBinding);
			if (controller != null) {
				if (!trackpadSwipeSamplers.containsKey(keyBinding.getKeyDescription()))
					trackpadSwipeSamplers.put(keyBinding.getKeyDescription(), new TrackpadSwipeSampler());
				TrackpadSwipeSampler sampler = trackpadSwipeSamplers.get(keyBinding.getKeyDescription());
				sampler.update(controller, action.getAxis2D(false));

				if (sampler.isSwipedUp() && upCallback != null) {
					triggerHapticPulse(controller, 0.001f, 400, 0.5f);
					upCallback.run();
				}
				if (sampler.isSwipedDown() && downCallback != null) {
					triggerHapticPulse(controller, 0.001f, 400, 0.5f);
					downCallback.run();
				}
				if (sampler.isSwipedLeft() && leftCallback != null) {
					triggerHapticPulse(controller, 0.001f, 400, 0.5f);
					leftCallback.run();
				}
				if (sampler.isSwipedRight() && rightCallback != null) {
					triggerHapticPulse(controller, 0.001f, 400, 0.5f);
					rightCallback.run();
				}
			}
		}
	}

	private static void processInputAction(VRInputAction action) {
		if (!action.isActive() || !action.isEnabledRaw()) {
			action.unpressBinding();
		} else {
			if (action.isButtonChanged()) {
				if (action.isButtonPressed() && action.isEnabled()) {
					// We do this so shit like closing a GUI by clicking a button won't
					// also click in the world immediately after.
					if (!ignorePressesNextFrame)
						action.pressBinding();
				} else {
					action.unpressBinding();
				}
			}
		}
	}

	public static void processInputs() {
		if (mc.vrSettings.seated || Main.viewonly || !inputInitialized) return;

		for (VRInputAction action : inputActions.values()) {
			if (action.isHanded()) {
				for (ControllerType hand : ControllerType.values()) {
					action.setCurrentHand(hand);
					processInputAction(action);
				}
			} else {
				processInputAction(action);
			}
		}

		processScrollInput(GuiHandler.keyScrollAxis, () -> {
			if (KeyboardSimulator.canUseRobot())
				KeyboardSimulator.robot.mouseWheel(-120);
			else if (InputInjector.isSupported())
				InputInjector.mouseWheelUp();
			else
				AndroidInput.scroll(1);
		}, () -> {
			if (KeyboardSimulator.canUseRobot())
				KeyboardSimulator.robot.mouseWheel(120);
			else if (InputInjector.isSupported())
				InputInjector.mouseWheelDown();
			else
				AndroidInput.scroll(-1);
		});
		processScrollInput(keyHotbarScroll, () -> changeHotbar(-1), () -> changeHotbar(1));
		processSwipeInput(keyHotbarSwipeX, () -> changeHotbar(1), () -> changeHotbar(-1), null, null);
		processSwipeInput(keyHotbarSwipeY, null, null, () -> changeHotbar(-1), () -> changeHotbar(1));

		// Reset this flag
		ignorePressesNextFrame = false;
	}

	public static void processBindings() {
		//VIVE SPECIFIC FUNCTIONALITY
		//TODO: Find a better home for these. (uh?)
		if (inputActions.isEmpty()) return;

		boolean sleeping = (mc.world !=null && mc.player != null && mc.player.isPlayerSleeping());
		boolean gui = mc.currentScreen != null;

		boolean toggleMovementPressed = keyToggleMovement.isPressed();
		if (mc.gameSettings.keyBindPickBlock.isKeyDown() || toggleMovementPressed) {
			if (++moveModeSwitchCount == 20 * 4 || toggleMovementPressed) {
				if (mc.vrSettings.seated) {
					mc.vrSettings.seatedFreeMove = !mc.vrSettings.seatedFreeMove;
					mc.printChatMessage(LangHelper.get("vivecraft.messages.movementmodeswitch", mc.vrSettings.seatedFreeMove ? Lang.get("vivecraft.options.freemove") : Lang.get("vivecraft.options.teleport")));
				} else {
					if (mc.vrPlayer.isTeleportSupported()) {
						mc.vrSettings.forceStandingFreeMove = !mc.vrSettings.forceStandingFreeMove;
						mc.printChatMessage(LangHelper.get("vivecraft.messages.movementmodeswitch", mc.vrSettings.seatedFreeMove ? Lang.get("vivecraft.options.freemove") : Lang.get("vivecraft.options.teleport")));
					} else {
						if (mc.vrPlayer.isTeleportOverridden()) {
							mc.vrPlayer.setTeleportOverride(false);
							mc.printChatMessage(Lang.get("vivecraft.messages.teleportdisabled"));
						} else {
							mc.vrPlayer.setTeleportOverride(true);
							mc.printChatMessage(Lang.get("vivecraft.messages.teleportenabled"));
						}
					}
				}				
			}
		} else {
			moveModeSwitchCount = 0;
		}

		Vec3d main = getAimVector(0);
		Vec3d off = getAimVector(1);

		float myaw = (float) Math.toDegrees(Math.atan2(-main.x, main.z));
		float oyaw= (float) Math.toDegrees(Math.atan2(-off.x, off.z));;

		if(!gui){
			if(keyWalkabout.isKeyDown()){
				float yaw = myaw;

				//oh this is ugly. TODO: cache which hand when binding button.
				TrackedController controller = findActiveBindingController(keyWalkabout);
				if (controller != null && controller.getType() == ControllerType.LEFT) {
					yaw = oyaw;
				}

				if (!isWalkingAbout){
					isWalkingAbout = true;
					walkaboutYawStart = mc.vrSettings.vrWorldRotation - yaw;  
				}
				else {
					mc.vrSettings.vrWorldRotation = walkaboutYawStart + yaw;
					mc.vrSettings.vrWorldRotation %= 360; // Prevent stupidly large values (can they even happen here?)
					//	mc.vrPlayer.checkandUpdateRotateScale(true);
				}
			} else {
				isWalkingAbout = false;
			}

			if(keyRotateFree.isKeyDown()){
				float yaw = myaw;

				//oh this is ugly. TODO: cache which hand when binding button.
				TrackedController controller = findActiveBindingController(keyRotateFree);
				if (controller != null && controller.getType() == ControllerType.LEFT) {
					yaw = oyaw;
				}

				if (!isFreeRotate){
					isFreeRotate = true;
					walkaboutYawStart = mc.vrSettings.vrWorldRotation + yaw;  
				}
				else {
					mc.vrSettings.vrWorldRotation = walkaboutYawStart - yaw;
					//	mc.vrPlayer.checkandUpdateRotateScale(true,0);
				}
			} else {
				isFreeRotate = false;
			}
		}


		if(keyHotbarNext.isPressed()) {
			changeHotbar(-1);
			MCOpenVR.triggerBindingHapticPulse(keyHotbarNext, 250);
		}

		if(keyHotbarPrev.isPressed()){
			changeHotbar(1);
			MCOpenVR.triggerBindingHapticPulse(keyHotbarPrev, 250);
		}

		if(keyQuickTorch.isPressed() && mc.player != null){
			for (int slot=0;slot<9;slot++)
			{  
				ItemStack itemStack = mc.player.inventory.getStackInSlot(slot);
				if (itemStack.getItem() instanceof ItemBlock && ((ItemBlock)itemStack.getItem()).getBlock() instanceof BlockTorch  && mc.currentScreen == null)
				{
					quickTorchPreviousSlot = mc.player.inventory.currentItem;
					mc.player.inventory.currentItem = slot;
					mc.rightClickMouse();
					// switch back immediately
					mc.player.inventory.currentItem = quickTorchPreviousSlot;
					quickTorchPreviousSlot = -1;
					break;
				}
			}
		}

		// if you start teleporting, close any UI
		if (gui && !sleeping && keyTeleport.isKeyDown() && !(mc.currentScreen instanceof GuiWinGame))
		{
			if(mc.player !=null) mc.player.closeScreen();
		}

		//GuiContainer.java only listens directly to the keyboard to close.
		if (mc.currentScreen instanceof GuiContainer && mc.gameSettings.keyBindInventory.isPressed()) {
			if (mc.player != null)
				mc.player.closeScreen();
		}

		// allow toggling chat window with chat keybind
		if (mc.currentScreen instanceof GuiChat && mc.gameSettings.keyBindChat.isPressed()) {
			mc.displayGuiScreen(null);
		}

		if(mc.vrSettings.vrWorldRotationIncrement == 0){
			float ax = getInputAction(keyRotateAxis).getAxis2DUseTracked().getX();
			if (ax == 0) ax = getInputAction(keyFreeMoveRotate).getAxis2DUseTracked().getX();
			if (ax != 0) {
				float analogRotSpeed = 10 * ax;
				mc.vrSettings.vrWorldRotation -= analogRotSpeed;
				mc.vrSettings.vrWorldRotation = mc.vrSettings.vrWorldRotation % 360;
			}
		} else {
			if (keyRotateAxis.isPressed() || keyFreeMoveRotate.isPressed()) {
				float ax = getInputAction(keyRotateAxis).getAxis2D(false).getX();
				if (ax == 0) ax = getInputAction(keyFreeMoveRotate).getAxis2D(false).getX();
				if (Math.abs(ax) > 0.5f) {
					mc.vrSettings.vrWorldRotation -= mc.vrSettings.vrWorldRotationIncrement * Math.signum(ax);
					mc.vrSettings.vrWorldRotation = mc.vrSettings.vrWorldRotation % 360;
				}
			}
		}

		if(mc.vrSettings.vrWorldRotationIncrement == 0){
			float ax = MovementInputFromOptions.getMovementAxisValue(keyRotateLeft);
			if(ax > 0){
				float analogRotSpeed = 5;
				if(ax > 0)	analogRotSpeed= 10 * ax;
				mc.vrSettings.vrWorldRotation+=analogRotSpeed;
				mc.vrSettings.vrWorldRotation = mc.vrSettings.vrWorldRotation % 360;
			}
		}else{
			if(keyRotateLeft.isPressed()){
				mc.vrSettings.vrWorldRotation+=mc.vrSettings.vrWorldRotationIncrement;
				mc.vrSettings.vrWorldRotation = mc.vrSettings.vrWorldRotation % 360;
			}
		}

		if(mc.vrSettings.vrWorldRotationIncrement == 0){
			float ax = MovementInputFromOptions.getMovementAxisValue(keyRotateRight);
			if(ax > 0){
				float analogRotSpeed = 5;
				if(ax > 0)	analogRotSpeed = 10 * ax;
				mc.vrSettings.vrWorldRotation-=analogRotSpeed;
				mc.vrSettings.vrWorldRotation = mc.vrSettings.vrWorldRotation % 360;
			}
		}else{
			if(keyRotateRight.isPressed()){
				mc.vrSettings.vrWorldRotation-=mc.vrSettings.vrWorldRotationIncrement;
				mc.vrSettings.vrWorldRotation = mc.vrSettings.vrWorldRotation % 360;
			}
		}

		seatedRot = mc.vrSettings.vrWorldRotation;

		if(keyRadialMenu.isPressed() && !gui) {
			TrackedController controller = findActiveBindingController(keyRadialMenu);
			if (controller != null)
				RadialHandler.setOverlayShowing(!RadialHandler.isShowing(), controller.getType());
		}
		
		if (keySwapMirrorView.isPressed()) {
			if (mc.vrSettings.displayMirrorMode == VRSettings.MIRROR_THIRD_PERSON)
				mc.vrSettings.displayMirrorMode = VRSettings.MIRROR_FIRST_PERSON;
			else if (mc.vrSettings.displayMirrorMode == VRSettings.MIRROR_FIRST_PERSON)
				mc.vrSettings.displayMirrorMode = VRSettings.MIRROR_THIRD_PERSON;
			mc.stereoProvider.reinitFrameBuffers("Mirror Setting Changed");
		}

		if (keyToggleKeyboard.isPressed()) {
			KeyboardHandler.setOverlayShowing(!KeyboardHandler.Showing);
		}

		if (keyMoveThirdPersonCam.isPressed() && !Main.kiosk && !mc.vrSettings.seated && (mc.vrSettings.displayMirrorMode == VRSettings.MIRROR_MIXED_REALITY || mc.vrSettings.displayMirrorMode == VRSettings.MIRROR_THIRD_PERSON)) {
				TrackedController controller = MCOpenVR.findActiveBindingController(keyMoveThirdPersonCam);
				if (controller != null)
					VRHotkeys.startMovingThirdPersonCam(controller.getType().ordinal());
			}
			if (!keyMoveThirdPersonCam.isKeyDown() && VRHotkeys.isMovingThirdPersonCam()) {
				VRHotkeys.stopMovingThirdPersonCam();
				mc.vrSettings.saveOptions();
			}

		if(KeyboardHandler.Showing && mc.currentScreen == null && keyMenuButton.isPressed()) { //super special case.
			KeyboardHandler.setOverlayShowing(false);
		}

		if(RadialHandler.isShowing() && keyMenuButton.isPressed()) { //super special case.
			RadialHandler.setOverlayShowing(false, null);
		}
	
		if(keyMenuButton.isPressed()) { //handle menu directly
			if(!gui) {
				if(!Main.kiosk){
					mc.displayInGameMenu();
				}
			} else {
				if(KeyboardSimulator.canUseRobot()) {
					KeyboardSimulator.robot.keyPress(KeyEvent.VK_ESCAPE); //window focus... yadda yadda
					KeyboardSimulator.robot.keyRelease(KeyEvent.VK_ESCAPE); //window focus... yadda yadda
				} else if (mc.currentScreen != null) {
					// what Escape does in the screen (back, close), as the robot's key press did
					try {
						mc.currentScreen.keyTypedPublic((char) 0, Keyboard.KEY_ESCAPE);
					} catch (IOException e) {
						e.printStackTrace();
					}
				} else {
					if (mc.player != null) mc.player.closeScreen();
				}
			}
			KeyboardHandler.setOverlayShowing(false);
		}
		
		if (keyExportWorld.isPressed()) {
			if (mc.world != null && mc.player != null) {
				try {
					final BlockPos pos = mc.player.getPosition();
					final int size = 320;
					File dir = new File("menuworlds/custom");
					dir.mkdirs();
					File foundFile;
					for (int i = 0;; i++) {
						foundFile = new File(dir, "world" + i + ".mmw");
						if (!foundFile.exists())
							break;
					}
					final File file = foundFile;
					System.out.println("Exporting world... area size: " + size);
					System.out.println("Saving to " + file.getAbsolutePath());
					if (mc.isIntegratedServerRunning()) {
						final World world = mc.getIntegratedServer().getWorld(mc.player.dimension);
						ListenableFuture task = mc.getIntegratedServer().addScheduledTask(new Runnable() {
							@Override
							public void run() {
								try {
									MenuWorldExporter.saveAreaToFile(world, pos.getX() - size / 2, pos.getZ() - size / 2, size, size, pos.getY(), file);
								} catch (IOException e) {
									e.printStackTrace();
								}
							}
						});
						while (!task.isDone()) {
							Thread.sleep(10);
						}
					} else {
						MenuWorldExporter.saveAreaToFile(mc.world, pos.getX() - size / 2, pos.getZ() - size / 2, size, size, pos.getY(), file);
					}
					mc.printChatMessage(LangHelper.get("vivecraft.messages.menuworldexportcomplete.1", size));
					mc.printChatMessage(LangHelper.get("vivecraft.messages.menuworldexportcomplete.2", file.getAbsolutePath()));
				} catch (Exception e) {
					e.printStackTrace();
				}
			}
		}
		
		if(KeyboardHandler.Showing && mc.currentScreen == null && keyMenuButton.isPressed()) { //super special case.
			KeyboardHandler.setOverlayShowing(false);
		}
		
		if(RadialHandler.isShowing() && keyMenuButton.isPressed()) { //super special case.
			RadialHandler.setOverlayShowing(false, null);
		}

		GuiHandler.processBindingsGui();
		RadialHandler.processBindings();
		KeyboardHandler.processBindings();
		mc.interactTracker.processBindings();
	}

	private static void changeHotbar(int dir){
		if(mc.player == null || (mc.climbTracker.isGrabbingLadder() && 
				mc.climbTracker.isClaws(mc.player.getHeldItemMainhand()))) //never let go, jack.
		{}
		else{
			if (Reflector.forgeExists() && mc.currentScreen == null && KeyboardSimulator.canUseRobot())
				KeyboardSimulator.robot.mouseWheel(-dir * 120);
			else
				mc.player.inventory.changeCurrentItem(dir);
		}
	}

	private static void pollVREvents()
	{
		if (instance == null) return;
		while (true) {
			eventDataBuffer.clear();
			eventDataBuffer.type(XR10.XR_TYPE_EVENT_DATA_BUFFER);
			if (XR10.xrPollEvent(instance, eventDataBuffer) != XR10.XR_SUCCESS)
				break;
			XrEventDataBaseHeader event = XrEventDataBaseHeader.create(eventDataBuffer.address());
			switch (event.type()) {
				case XR10.XR_TYPE_EVENT_DATA_SESSION_STATE_CHANGED:
					sessionChanged(XrEventDataSessionStateChanged.create(event.address()).state());
					break;
				case XR10.XR_TYPE_EVENT_DATA_INTERACTION_PROFILE_CHANGED:
					boundSources.clear();
					break;
				case XR10.XR_TYPE_EVENT_DATA_INSTANCE_LOSS_PENDING:
					System.out.println("Vivecraft: OpenXR is going away, closing the game");
					mc.shutdown();
					break;
				default:
					break;
			}
		}
	}

	private static void sessionChanged(int state) {
		switch (state) {
			case XR10.XR_SESSION_STATE_READY:
				try (MemoryStack stack = MemoryStack.stackPush()) {
					XrSessionBeginInfo beginInfo = XrSessionBeginInfo.calloc(stack);
					beginInfo.type(XR10.XR_TYPE_SESSION_BEGIN_INFO);
					beginInfo.primaryViewConfigurationType(XR10.XR_VIEW_CONFIGURATION_TYPE_PRIMARY_STEREO);
					int error = XR10.xrBeginSession(session, beginInfo);
					logError(error, "xrBeginSession");
					sessionRunning = error >= 0;
				}
				break;
			case XR10.XR_SESSION_STATE_FOCUSED:
				sessionFocused = true;
				break;
			case XR10.XR_SESSION_STATE_VISIBLE:
			case XR10.XR_SESSION_STATE_SYNCHRONIZED:
			case XR10.XR_SESSION_STATE_IDLE:
				sessionFocused = false;
				break;
			case XR10.XR_SESSION_STATE_STOPPING:
				sessionFocused = false;
				sessionRunning = false;
				frameBegun = false;
				logError(XR10.xrEndSession(session), "xrEndSession");
				break;
			case XR10.XR_SESSION_STATE_EXITING:
			case XR10.XR_SESSION_STATE_LOSS_PENDING:
				System.out.println("Vivecraft: OpenXR stopped, closing the game with it");
				mc.shutdown();
				break;
			default:
				break;
		}
	}

	public static boolean isBoundInActiveActionSets(KeyBinding binding) {
		List<Long> origins = getInputAction(binding).getOrigins();
		return !origins.isEmpty();
	}

	public static ControllerType findActiveBindingControllerType(KeyBinding binding) {
		if (!inputInitialized) return null;
		long origin = getInputAction(binding).getLastOrigin();
		if (origin != 0L) {
			return getOriginControllerType(origin);
		}
		return null;
	}

	public static TrackedController findActiveBindingController(KeyBinding binding) {
		ControllerType type = findActiveBindingControllerType(binding);
		if (type != null) return type.getController();
		return null;
	}

	public static void triggerBindingHapticPulse(KeyBinding binding, float durationSeconds, float frequency, float amplitude) {
		TrackedController controller = findActiveBindingController(binding);
		if (controller != null) controller.triggerHapticPulse(durationSeconds, frequency, amplitude);
	}

	@Deprecated
	public static void triggerBindingHapticPulse(KeyBinding binding, int duration) {
		TrackedController controller = findActiveBindingController(binding);
		if (controller != null) controller.triggerHapticPulse(duration);
		}

	public static VRInputAction getInputActionByName(String name) {
		return inputActions.get(name);
	}

	public static VRInputAction getInputAction(String keyBindingDesc) {
		return inputActionsByKeyBinding.get(keyBindingDesc);
			}

	public static VRInputAction getInputAction(KeyBinding keyBinding) {
		return getInputAction(keyBinding.getKeyDescription());
		}

	public static Collection<VRInputAction> getInputActions() {
		return Collections.unmodifiableCollection(inputActions.values());
	}

	public static Collection<VRInputAction> getInputActionsInSet(VRInputActionSet set) {
		return Collections.unmodifiableCollection(inputActions.values().stream().filter(action -> action.actionSet == set).collect(Collectors.toList()));
	}

	public static long getActionSetHandle(VRInputActionSet actionSet) {
		return actionSetHandles.get(actionSet);
	}

	/** The OpenXR path of the hand that is the given controller (the hands swap with "reverse hands") */
	public static long getControllerHandle(ControllerType hand) {
		return getPath(HANDS[handIndex(hand)]);
	}

	private static int handIndex(ControllerType controller) {
		boolean right = controller == ControllerType.RIGHT;
		return right != mc.vrSettings.vrReverseHands ? RIGHT_HAND : LEFT_HAND;
	}

	public static long getHapticHandle(ControllerType hand) {
		return hapticAction;
	}

	public static String getInputError(int code){
		return resultName(code);
	}

	private static void updatePose()
	{
		if (!sessionRunning)
			return;

		try (MemoryStack stack = MemoryStack.stackPush()) {
			if (frameBegun)
				submitFrame(-1, -1); // the last frame never reached the renderer, OpenXR wants it ended

			XrFrameState frameState = XrFrameState.calloc(stack).type(XR10.XR_TYPE_FRAME_STATE);
			XrFrameWaitInfo waitInfo = XrFrameWaitInfo.calloc(stack).type(XR10.XR_TYPE_FRAME_WAIT_INFO);
			int error = XR10.xrWaitFrame(session, waitInfo, frameState);
			logError(error, "xrWaitFrame");
			if (error < 0)
				return;
			predictedDisplayTime = frameState.predictedDisplayTime();
			shouldRender = frameState.shouldRender();

			error = XR10.xrBeginFrame(session, XrFrameBeginInfo.calloc(stack).type(XR10.XR_TYPE_FRAME_BEGIN_INFO));
			logError(error, "xrBeginFrame");
			frameBegun = error >= 0;

			locateViews(xrAppSpace, viewBuffer, stack);
			locateViews(xrViewSpace, headViewBuffer, stack);
			poseToMatrix(headViewBuffer.get(0).pose(), hmdPoseLeftEye);
			poseToMatrix(headViewBuffer.get(1).pose(), hmdPoseRightEye);
			if (mc.stereoProvider != null)
				mc.stereoProvider.updateProjections();

			XrSpaceLocation location = XrSpaceLocation.calloc(stack).type(XR10.XR_TYPE_SPACE_LOCATION);
			if (locate(xrViewSpace, location)) {
				poseToMatrix(location.pose(), hmdPose);
				headIsTracking = true;
			} else {
				headIsTracking = false;
				OpenVRUtil.Matrix4fSetIdentity(hmdPose);
				hmdPose.M[1][3] = 1.62f;
			}

			if (inputInitialized) {
				// Gotta do this here so we can get the poses
				mc.mcProfiler.startSection("updateActionState");
				syncActions(stack);
				inputActions.values().forEach(VRInputAction::readNewData);
				mc.mcProfiler.endSection();

				updateControllerPose(RIGHT_CONTROLLER, handIndex(ControllerType.RIGHT), location);
				updateControllerPose(LEFT_CONTROLLER, handIndex(ControllerType.LEFT), location);
			}
			controllerTracking[THIRD_CONTROLLER] = false;
		}

		updateAim();
		//VRHotkeys.snapMRCam(mc, 0);

	}

	private static void locateViews(XrSpace space, XrView.Buffer views, MemoryStack stack) {
		XrViewLocateInfo locateInfo = XrViewLocateInfo.calloc(stack);
		locateInfo.type(XR10.XR_TYPE_VIEW_LOCATE_INFO);
		locateInfo.viewConfigurationType(XR10.XR_VIEW_CONFIGURATION_TYPE_PRIMARY_STEREO);
		locateInfo.displayTime(predictedDisplayTime);
		locateInfo.space(space);
		XrViewState viewState = XrViewState.calloc(stack).type(XR10.XR_TYPE_VIEW_STATE);
		IntBuffer count = stack.callocInt(1);
		logError(XR10.xrLocateViews(session, locateInfo, viewState, count, views), "xrLocateViews");
	}

	private static boolean locate(XrSpace space, XrSpaceLocation location) {
		if (space == null)
			return false;
		int error = XR10.xrLocateSpace(space, xrAppSpace, predictedDisplayTime, location);
		return error >= 0 && (location.locationFlags() & XR10.XR_SPACE_LOCATION_ORIENTATION_VALID_BIT) != 0;
	}

	private static void poseToMatrix(XrPosef pose, Matrix4f matrix) {
		OpenVRUtil.convertXrPoseToMatrix4f(pose.orientation().x(), pose.orientation().y(), pose.orientation().z(), pose.orientation().w(),
				pose.position$().x(), pose.position$().y(), pose.position$().z(), matrix);
	}

	/**
	 * The grip pose is the controller's pose as SteamVR gave it (the one held items and the hand follow), the aim
	 * pose becomes the "tip" component in its frame, which Vivecraft aims and points the GUI with.
	 */
	private static void updateControllerPose(int controller, int hand, XrSpaceLocation location) {
		Matrix4f grip = new Matrix4f();
		Matrix4f aim = new Matrix4f();
		boolean tracking = locate(gripSpaces[hand], location);
		if (tracking)
			poseToMatrix(location.pose(), grip);
		tracking = tracking && locate(aimSpaces[hand], location);
		if (tracking) {
			poseToMatrix(location.pose(), aim);
			OpenVRUtil.Matrix4fCopy(grip, controllerPose[controller]);
			OpenVRUtil.Matrix4fCopy(Matrix4f.multiply(grip.inverted(), aim), tipTransforms[controller]);
			OpenVRUtil.Matrix4fCopy(grip, poseMatrices[controller]);
			controllerDeviceIndex[controller] = controller;
		} else {
			controllerDeviceIndex[controller] = -1;
		}
		controllerTracking[controller] = tracking;
	}

	/** Ends the frame begun in {@link #updatePose()}, with the eye textures as its picture (none for -1). */
	static void submitFrame(int leftTexture, int rightTexture) {
		if (!frameBegun)
			return;
		frameBegun = false;
		try (MemoryStack stack = MemoryStack.stackPush()) {
			PointerBuffer layers = null;
			if (shouldRender && leftTexture > 0 && rightTexture > 0 && sessionRunning) {
				IntBuffer index = stack.callocInt(1);
				int error = XR10.xrAcquireSwapchainImage(swapchain, XrSwapchainImageAcquireInfo.calloc(stack).type(XR10.XR_TYPE_SWAPCHAIN_IMAGE_ACQUIRE_INFO), index);
				logError(error, "xrAcquireSwapchainImage");
				if (error >= 0) {
					XrSwapchainImageWaitInfo waitInfo = XrSwapchainImageWaitInfo.calloc(stack).type(XR10.XR_TYPE_SWAPCHAIN_IMAGE_WAIT_INFO);
					waitInfo.timeout(XR10.XR_INFINITE_DURATION);
					logError(XR10.xrWaitSwapchainImage(swapchain, waitInfo), "xrWaitSwapchainImage");
					int image = swapchainImages[index.get(0)];
					if (blit == null)
						blit = new XrBlit();
					blit.copy(leftTexture, image, 0, swapchainWidth, swapchainHeight);
					blit.copy(rightTexture, image, 1, swapchainWidth, swapchainHeight);
					logError(XR10.xrReleaseSwapchainImage(swapchain, XrSwapchainImageReleaseInfo.calloc(stack).type(XR10.XR_TYPE_SWAPCHAIN_IMAGE_RELEASE_INFO)), "xrReleaseSwapchainImage");

					XrCompositionLayerProjectionView.Buffer views = XrCompositionLayerProjectionView.calloc(2, stack);
					for (int eye = 0; eye < 2; eye++) {
						XrCompositionLayerProjectionView view = views.get(eye);
						view.type(XR10.XR_TYPE_COMPOSITION_LAYER_PROJECTION_VIEW);
						view.pose(viewBuffer.get(eye).pose());
						view.fov(viewBuffer.get(eye).fov());
						view.subImage().swapchain(swapchain);
						view.subImage().imageArrayIndex(eye);
						view.subImage().imageRect().offset().set(0, 0);
						view.subImage().imageRect().extent().set(swapchainWidth, swapchainHeight);
					}
					XrCompositionLayerProjection projection = XrCompositionLayerProjection.calloc(stack);
					projection.type(XR10.XR_TYPE_COMPOSITION_LAYER_PROJECTION);
					projection.space(xrAppSpace);
					projection.views(views);
					layers = stack.callocPointer(1);
					layers.put(0, projection.address());
				}
			}
			XrFrameEndInfo endInfo = XrFrameEndInfo.calloc(stack);
			endInfo.type(XR10.XR_TYPE_FRAME_END_INFO);
			endInfo.displayTime(predictedDisplayTime);
			endInfo.environmentBlendMode(XR10.XR_ENVIRONMENT_BLEND_MODE_OPAQUE);
			endInfo.layers(layers);
			logError(XR10.xrEndFrame(session, endInfo), "xrEndFrame");
		}
	}

	private static XrBlit blit;

	/** Reads the action's state from OpenXR, per hand for the handed ones (SteamVR's restrict-to-device). */
	public static void readNewData(VRInputAction action) {
		if (action.getHandle() == 0)
			return;
		if (action.isHanded()) {
			for (ControllerType hand : ControllerType.values())
				readActionState(action, hand, hand.ordinal());
		} else {
			readActionState(action, null, 0);
		}
	}

	private static void readActionState(VRInputAction action, ControllerType hand, int index) {
		try (MemoryStack stack = MemoryStack.stackPush()) {
			XrActionStateGetInfo info = XrActionStateGetInfo.calloc(stack);
			info.type(XR10.XR_TYPE_ACTION_STATE_GET_INFO);
			info.action(xrActions.get(action.getHandle()));
			info.subactionPath(hand != null ? getControllerHandle(hand) : MemoryUtil.NULL);
			long origin = activeOrigin(action, hand);
			switch (action.type) {
				case "vector1": {
					XrActionStateFloat state = XrActionStateFloat.calloc(stack).type(XR10.XR_TYPE_ACTION_STATE_FLOAT);
					logError(XR10.xrGetActionStateFloat(session, info, state), "xrGetActionStateFloat", action.name);
					VRInputAction.AnalogData data = action.analogData[index];
					data.deltaX = state.currentState() - data.x;
					data.x = state.currentState();
					data.isActive = state.isActive();
					data.isChanged = state.changedSinceLastSync();
					data.activeOrigin = origin;
					break;
				}
				case "vector2":
				case "vector3": {
					XrActionStateVector2f state = XrActionStateVector2f.calloc(stack).type(XR10.XR_TYPE_ACTION_STATE_VECTOR2F);
					logError(XR10.xrGetActionStateVector2f(session, info, state), "xrGetActionStateVector2f", action.name);
					VRInputAction.AnalogData data = action.analogData[index];
					data.deltaX = state.currentState().x() - data.x;
					data.deltaY = state.currentState().y() - data.y;
					data.x = state.currentState().x();
					data.y = state.currentState().y();
					data.isActive = state.isActive();
					data.isChanged = state.changedSinceLastSync();
					data.activeOrigin = origin;
					break;
				}
				default: {
					XrActionStateBoolean state = XrActionStateBoolean.calloc(stack).type(XR10.XR_TYPE_ACTION_STATE_BOOLEAN);
					logError(XR10.xrGetActionStateBoolean(session, info, state), "xrGetActionStateBoolean", action.name);
					VRInputAction.DigitalData data = action.digitalData[index];
					data.state = state.currentState();
					data.isActive = state.isActive();
					data.isChanged = state.changedSinceLastSync();
					data.activeOrigin = origin;
					break;
				}
			}
		}
	}

	/** The input an action is bound to, of the given hand for handed actions (SteamVR's active origin) */
	private static long activeOrigin(VRInputAction action, ControllerType hand) {
		List<Long> sources = getOrigins(action);
		if (hand == null)
			return sources.isEmpty() ? 0L : sources.get(0);
		String handPath = HANDS[handIndex(hand)] + "/";
		for (long source : sources) {
			if (pathString(source).startsWith(handPath))
				return source;
		}
		return 0L;
	}

	/** What the action is bound to, asked once per interaction profile */
	public static List<Long> getOrigins(VRInputAction action) {
		if (!inputInitialized || action.getHandle() == 0)
			return Collections.emptyList();
		List<Long> known = boundSources.get(action.getHandle());
		if (known != null)
			return known;
		List<Long> list = new ArrayList<>();
		try (MemoryStack stack = MemoryStack.stackPush()) {
			XrBoundSourcesForActionEnumerateInfo info = XrBoundSourcesForActionEnumerateInfo.calloc(stack);
			info.type(XR10.XR_TYPE_BOUND_SOURCES_FOR_ACTION_ENUMERATE_INFO);
			info.action(xrActions.get(action.getHandle()));
			IntBuffer count = stack.callocInt(1);
			if (XR10.xrEnumerateBoundSourcesForAction(session, info, count, null) >= 0 && count.get(0) > 0) {
				LongBuffer sources = stack.callocLong(count.get(0));
				if (XR10.xrEnumerateBoundSourcesForAction(session, info, count, sources) >= 0) {
					for (int i = 0; i < count.get(0); i++)
						if (sources.get(i) != 0) list.add(sources.get(i));
				}
			}
		}
		boundSources.put(action.getHandle(), list);
		return list;
	}

	public static String getOriginName(long handle) {
		try (MemoryStack stack = MemoryStack.stackPush()) {
			XrInputSourceLocalizedNameGetInfo info = XrInputSourceLocalizedNameGetInfo.calloc(stack);
			info.type(XR10.XR_TYPE_INPUT_SOURCE_LOCALIZED_NAME_GET_INFO);
			info.sourcePath(handle);
			info.whichComponents(XR10.XR_INPUT_SOURCE_LOCALIZED_NAME_USER_PATH_BIT | XR10.XR_INPUT_SOURCE_LOCALIZED_NAME_COMPONENT_BIT);
			IntBuffer length = stack.callocInt(1);
			if (XR10.xrGetInputSourceLocalizedName(session, info, length, null) < 0 || length.get(0) <= 0)
				return pathString(handle);
			ByteBuffer text = stack.calloc(length.get(0));
			XR10.xrGetInputSourceLocalizedName(session, info, length, text);
			return MemoryUtil.memUTF8(text, length.get(0) - 1);
		}
	}

	public static ControllerType getOriginControllerType(long inputValueHandle) {
		if (inputValueHandle == 0)
			return null;
		String path = pathString(inputValueHandle);
		boolean right;
		if (path.startsWith(HANDS[RIGHT_HAND] + "/"))
			right = true;
		else if (path.startsWith(HANDS[LEFT_HAND] + "/"))
			right = false;
		else
			return null;
		return right != mc.vrSettings.vrReverseHands ? ControllerType.RIGHT : ControllerType.LEFT;
	}

	/** One vibration on the controller's hand, from the haptics scheduler (its own thread) */
	public static void applyHapticFeedback(ControllerType controller, float durationSeconds, float frequency, float amplitude) {
		if (!inputInitialized || hapticAction == 0)
			return;
		try (MemoryStack stack = MemoryStack.stackPush()) {
			XrHapticVibration vibration = XrHapticVibration.calloc(stack);
			vibration.type(XR10.XR_TYPE_HAPTIC_VIBRATION);
			vibration.duration(Math.max(1L, (long) (durationSeconds * 1_000_000_000L)));
			vibration.frequency(frequency);
			vibration.amplitude(amplitude);
			XrHapticActionInfo info = XrHapticActionInfo.calloc(stack);
			info.type(XR10.XR_TYPE_HAPTIC_ACTION_INFO);
			info.action(xrActions.get(hapticAction));
			info.subactionPath(getPath(HANDS[controller == ControllerType.RIGHT ? RIGHT_HAND : LEFT_HAND]));
			logError(XR10.xrApplyHapticFeedback(session, info, XrHapticBaseHeader.create(vibration.address())), "xrApplyHapticFeedback");
		}
	}

	public static boolean isControllerTracking(int controller) {
		return controllerTracking[controller];
	}

	public static boolean isControllerTracking(ControllerType controller) {
		return isControllerTracking(controller.ordinal());
	}

	/**
	 * @return The coordinate of the 'center' eye position relative to the head yaw plane
	 */

	public static Vec3d getCenterEyePosition() {
		Vector3 pos = OpenVRUtil.convertMatrix4ftoTranslationVector(hmdPose);
		if (mc.vrSettings.seated || mc.vrSettings.allowStandingOriginOffset)
		pos=pos.add(offset);
		return pos.toVec3d();
	}

	/**
	 * @return The coordinate of the left or right eye position relative to the head yaw plane
	 */

	public static Vec3d getEyePosition(RenderPass eye)
	{
		Matrix4f hmdToEye = hmdPoseRightEye;
		if ( eye == RenderPass.LEFT)
		{
			hmdToEye = hmdPoseLeftEye;
		} else if ( eye == RenderPass.RIGHT)
		{
			hmdToEye = hmdPoseRightEye;
		} else {
			hmdToEye = null;
		}

		if(hmdToEye == null){
			Matrix4f pose = hmdPose;
			Vector3 pos = OpenVRUtil.convertMatrix4ftoTranslationVector(pose);
			if (mc.vrSettings.seated || mc.vrSettings.allowStandingOriginOffset)
			pos=pos.add(offset);
			return pos.toVec3d();
		} else {
			Matrix4f pose = Matrix4f.multiply( hmdPose, hmdToEye );
			Vector3 pos = OpenVRUtil.convertMatrix4ftoTranslationVector(pose);
			if (mc.vrSettings.seated || mc.vrSettings.allowStandingOriginOffset)
			pos=pos.add(offset);
			return pos.toVec3d();
		}
	}

	public static Matrix4f getEyeRotation(RenderPass eye)
	{
		Matrix4f hmdToEye;
		if ( eye == RenderPass.LEFT) {
			hmdToEye = hmdPoseLeftEye;
		} else if ( eye == RenderPass.RIGHT) {
			hmdToEye = hmdPoseRightEye;
		} else {
			hmdToEye = null;
		}

		if (hmdToEye != null) {
			Matrix4f eyeRot = new Matrix4f();
			eyeRot.M[0][0] = hmdToEye.M[0][0];
			eyeRot.M[0][1] = hmdToEye.M[0][1];
			eyeRot.M[0][2] = hmdToEye.M[0][2];
			eyeRot.M[0][3] = 0.0F;
			eyeRot.M[1][0] = hmdToEye.M[1][0];
			eyeRot.M[1][1] = hmdToEye.M[1][1];
			eyeRot.M[1][2] = hmdToEye.M[1][2];
			eyeRot.M[1][3] = 0.0F;
			eyeRot.M[2][0] = hmdToEye.M[2][0];
			eyeRot.M[2][1] = hmdToEye.M[2][1];
			eyeRot.M[2][2] = hmdToEye.M[2][2];
			eyeRot.M[2][3] = 0.0F;
			eyeRot.M[3][0] = 0.0F;
			eyeRot.M[3][1] = 0.0F;
			eyeRot.M[3][2] = 0.0F;
			eyeRot.M[3][3] = 1.0F;

			return Matrix4f.multiply(hmdRotation, eyeRot);
		} else {
			return hmdRotation;
		}
	}

	/**
	 *
	 * @return Play area size or null if not valid
	 */
	public static float[] getPlayAreaSize() {
		if (session == null) return null;
		try (MemoryStack stack = MemoryStack.stackPush()) {
			XrExtent2Df bounds = XrExtent2Df.calloc(stack);
			if (XR10.xrGetReferenceSpaceBoundsRect(session, XR10.XR_REFERENCE_SPACE_TYPE_STAGE, bounds) != XR10.XR_SUCCESS)
				return null;
			if (bounds.width() <= 0 || bounds.height() <= 0)
				return null;
			return new float[]{bounds.width()*mc.vrSettings.walkMultiplier, bounds.height()*mc.vrSettings.walkMultiplier};
		}
	}

	/**
	 * Gets the orientation quaternion
	 *
	 * @return quaternion w, x, y & z components
	 */

	static Angle getOrientationEuler()
	{
		Quaternion orient = OpenVRUtil.convertMatrix4ftoRotationQuat(hmdPose);
		return orient.toEuler();
	}

	final String k_pch_SteamVR_Section = "steamvr";
	final String k_pch_SteamVR_RenderTargetMultiplier_Float = "renderTargetMultiplier";



	//-------------------------------------------------------
	// IBodyAimController

	float getBodyPitchDegrees() {
		return 0; //Always return 0 for body pitch
	}

	public static Vec3d getAimVector( int controller ) {
		Vector3 v = controllerRotation[controller].transform(forward);
		return v.toVec3d();

	}

	public static Vec3d getHmdVector() {
		Vector3 v = hmdRotation.transform(forward);
		return v.toVec3d();
	}

	public static Vec3d getHandVector( int controller ) {
		Vector3 forward = new Vector3(0,0,-1);
		Matrix4f aimRotation = handRotation[controller];
		Vector3 controllerDirection = aimRotation.transform(forward);
		return controllerDirection.toVec3d();
	}

	public static Matrix4f getAimRotation( int controller ) {
		return controllerRotation[controller];
	}

	public static Matrix4f getHandRotation( int controller ) {
		return handRotation[controller];
	}


	public boolean initBodyAim() throws Exception
	{
		return init();
	}


	public static Vec3d getAimSource( int controller ) {
		Vec3d out = new Vec3d(aimSource[controller].x, aimSource[controller].y, aimSource[controller].z);
		if(!mc.vrSettings.seated && mc.vrSettings.allowStandingOriginOffset)
			out = out.addVector(offset.getX(), offset.getY(), offset.getZ());
		return out;
	}

	public static void triggerHapticPulse(ControllerType controller, float durationSeconds, float frequency, float amplitude, float delaySeconds) {
		if (Minecraft.getMinecraft().vrSettings.seated || !inputInitialized) return;
		if (mc.vrSettings.vrReverseHands) {
			if (controller == ControllerType.RIGHT)
				controller = ControllerType.LEFT;
			else
				controller = ControllerType.RIGHT;
		}

		hapticScheduler.queueHapticPulse(controller, durationSeconds, frequency, amplitude, delaySeconds);
	}

	public static void triggerHapticPulse(ControllerType controller, float durationSeconds, float frequency, float amplitude) {
		triggerHapticPulse(controller, durationSeconds, frequency, amplitude, 0);
	}
	
	@Deprecated
	public static void triggerHapticPulse(ControllerType controller, int strength) {
		if (strength < 1) return;
		// Through careful analysis of the haptics in the legacy API (read: I put the controller to
		// my ear, listened to the vibration, and reproduced the frequency in Audacity), I have determined
		// that the old haptics used 160Hz. So, these parameters will match the "feel" of the old haptics.
		triggerHapticPulse(controller, strength / 1000000f, 160, 1);
	}

	@Deprecated
	public static void triggerHapticPulse(int controller, int strength) {
		if (controller < 0 || controller >= ControllerType.values().length) return;
		triggerHapticPulse(ControllerType.values()[controller], strength);
	}

	public static float seatedRot;

	public static Vector3 forward = new Vector3(0,0,-1);
	static double aimPitch = 0; //needed for seated mode.


	private static void updateAim() {
		if (mc==null)
			return;

		{//hmd
			hmdRotation.M[0][0] = hmdPose.M[0][0];
			hmdRotation.M[0][1] = hmdPose.M[0][1];
			hmdRotation.M[0][2] = hmdPose.M[0][2];
			hmdRotation.M[0][3] = 0.0F;
			hmdRotation.M[1][0] = hmdPose.M[1][0];
			hmdRotation.M[1][1] = hmdPose.M[1][1];
			hmdRotation.M[1][2] = hmdPose.M[1][2];
			hmdRotation.M[1][3] = 0.0F;
			hmdRotation.M[2][0] = hmdPose.M[2][0];
			hmdRotation.M[2][1] = hmdPose.M[2][1];
			hmdRotation.M[2][2] = hmdPose.M[2][2];
			hmdRotation.M[2][3] = 0.0F;
			hmdRotation.M[3][0] = 0.0F;
			hmdRotation.M[3][1] = 0.0F;
			hmdRotation.M[3][2] = 0.0F;
			hmdRotation.M[3][3] = 1.0F;


			Vec3d eye = getCenterEyePosition();
			hmdHistory.add(eye);
			Vector3 v3 = MCOpenVR.hmdRotation.transform(new Vector3(0,-.1f, .1f));
			hmdPivotHistory.add(new Vec3d(v3.getX()+eye.x, v3.getY()+eye.y, v3.getZ()+eye.z));

		}

		Matrix4f[] controllerPoseTip = new Matrix4f[2];
		controllerPoseTip[0] = new Matrix4f();
		controllerPoseTip[1] = new Matrix4f();

		{//right controller
			handRotation[0].M[0][0] = controllerPose[0].M[0][0];
			handRotation[0].M[0][1] = controllerPose[0].M[0][1];
			handRotation[0].M[0][2] = controllerPose[0].M[0][2];
			handRotation[0].M[0][3] = 0.0F;
			handRotation[0].M[1][0] = controllerPose[0].M[1][0];
			handRotation[0].M[1][1] = controllerPose[0].M[1][1];
			handRotation[0].M[1][2] = controllerPose[0].M[1][2];
			handRotation[0].M[1][3] = 0.0F;
			handRotation[0].M[2][0] = controllerPose[0].M[2][0];
			handRotation[0].M[2][1] = controllerPose[0].M[2][1];
			handRotation[0].M[2][2] = controllerPose[0].M[2][2];
			handRotation[0].M[2][3] = 0.0F;
			handRotation[0].M[3][0] = 0.0F;
			handRotation[0].M[3][1] = 0.0F;
			handRotation[0].M[3][2] = 0.0F;
			handRotation[0].M[3][3] = 1.0F;	

			if(mc.vrSettings.seated){
				controllerPose[0] = hmdPose.inverted().inverted();
				controllerPose[1] = hmdPose.inverted().inverted();
				controllerPoseTip[0] = controllerPose[0];
				controllerPoseTip[1] = controllerPose[1];
			} else	
				controllerPoseTip[0] = Matrix4f.multiply(controllerPose[0], getControllerComponentTransform(0,"tip"));

			// grab controller position in tracker space, scaled to minecraft units
			Vector3 controllerPos = OpenVRUtil.convertMatrix4ftoTranslationVector(controllerPoseTip[0]);
			aimSource[0] = controllerPos.toVec3d();

			controllerHistory[0].add(aimSource[0]);

			// build matrix describing controller rotation
			controllerRotation[0].M[0][0] = controllerPoseTip[0].M[0][0];
			controllerRotation[0].M[0][1] = controllerPoseTip[0].M[0][1];
			controllerRotation[0].M[0][2] = controllerPoseTip[0].M[0][2];
			controllerRotation[0].M[0][3] = 0.0F;
			controllerRotation[0].M[1][0] = controllerPoseTip[0].M[1][0];
			controllerRotation[0].M[1][1] = controllerPoseTip[0].M[1][1];
			controllerRotation[0].M[1][2] = controllerPoseTip[0].M[1][2];
			controllerRotation[0].M[1][3] = 0.0F;
			controllerRotation[0].M[2][0] = controllerPoseTip[0].M[2][0];
			controllerRotation[0].M[2][1] = controllerPoseTip[0].M[2][1];
			controllerRotation[0].M[2][2] = controllerPoseTip[0].M[2][2];
			controllerRotation[0].M[2][3] = 0.0F;
			controllerRotation[0].M[3][0] = 0.0F;
			controllerRotation[0].M[3][1] = 0.0F;
			controllerRotation[0].M[3][2] = 0.0F;
			controllerRotation[0].M[3][3] = 1.0F;

			Vec3d hdir = getHmdVector();

			if(mc.vrSettings.seated && mc.currentScreen == null){
				org.lwjgl.util.vector.Matrix4f temp = new org.lwjgl.util.vector.Matrix4f();

				float hRange = 110;
				float vRange = 180;
				double h = Mouse.getX() / (double) mc.displayWidth * hRange - (hRange / 2);

				double hei  = mc.displayHeight;
				if(mc.displayHeight % 2 != 0)
					hei-=1; //fix drifting vertical mouse.

				double v = Mouse.getY() / (double) hei * vRange - (vRange / 2);				
				double nPitch=-v;
				if(Display.isActive()){
					float rotStart = mc.vrSettings.keyholeX;
					float rotSpeed = 11 * mc.vrSettings.xSensitivity;
					int leftedge=(int)((-rotStart + (hRange / 2)) *(double) mc.displayWidth / hRange )+1;
					int rightedge=(int)((rotStart + (hRange / 2)) *(double) mc.displayWidth / hRange )-1;
					float rotMul = ((float)Math.abs(h) - rotStart) / ((hRange / 2) - rotStart); // Scaled 0...1 from rotStart to FOV edge
					//if(rotMul > 0.15) rotMul = 0.15f;

					if(h < -rotStart){
						seatedRot += rotSpeed * rotMul;
						seatedRot %= 360; // Prevent stupidly large values
						hmdForwardYaw = (float)Math.toDegrees(Math.atan2(hdir.x, hdir.z));    
						Mouse.setCursorPosition(leftedge,Mouse.getY());
						h=-rotStart;
					}
					if(h > rotStart){
						seatedRot -= rotSpeed * rotMul;
						seatedRot %= 360; // Prevent stupidly large values
						hmdForwardYaw = (float)Math.toDegrees(Math.atan2(hdir.x, hdir.z));    
						Mouse.setCursorPosition(rightedge,Mouse.getY());
						h=rotStart;
					}

					double ySpeed=0.5 * mc.vrSettings.ySensitivity;
					nPitch=aimPitch+(v)*ySpeed;
					nPitch=MathHelper.clamp(nPitch,-89.9,89.9);
					Mouse.setCursorPosition(Mouse.getX(),mc.displayHeight/2);

					temp.rotate((float) Math.toRadians(-nPitch), new org.lwjgl.util.vector.Vector3f(1,0,0));
					temp.rotate((float) Math.toRadians(-180 + h - hmdForwardYaw), new org.lwjgl.util.vector.Vector3f(0,1,0));

				}


				controllerRotation[0].M[0][0] = temp.m00;
				controllerRotation[0].M[0][1] = temp.m01;
				controllerRotation[0].M[0][2] = temp.m02;

				controllerRotation[0].M[1][0] = temp.m10;
				controllerRotation[0].M[1][1] = temp.m11;
				controllerRotation[0].M[1][2] = temp.m12;

				controllerRotation[0].M[2][0] = temp.m20;
				controllerRotation[0].M[2][1] = temp.m21;
				controllerRotation[0].M[2][2] = temp.m22;
			}	

			Vec3d dir = getAimVector(0);
			aimPitch = (float)Math.toDegrees(Math.asin(dir.y/dir.lengthVector()));
			controllerForwardHistory[0].add(dir);

		}

		{//left controller
			handRotation[1].M[0][0] = controllerPose[1].M[0][0];
			handRotation[1].M[0][1] = controllerPose[1].M[0][1];
			handRotation[1].M[0][2] = controllerPose[1].M[0][2];
			handRotation[1].M[0][3] = 0.0F;
			handRotation[1].M[1][0] = controllerPose[1].M[1][0];
			handRotation[1].M[1][1] = controllerPose[1].M[1][1];
			handRotation[1].M[1][2] = controllerPose[1].M[1][2];
			handRotation[1].M[1][3] = 0.0F;
			handRotation[1].M[2][0] = controllerPose[1].M[2][0];
			handRotation[1].M[2][1] = controllerPose[1].M[2][1];
			handRotation[1].M[2][2] = controllerPose[1].M[2][2];
			handRotation[1].M[2][3] = 0.0F;
			handRotation[1].M[3][0] = 0.0F;
			handRotation[1].M[3][1] = 0.0F;
			handRotation[1].M[3][2] = 0.0F;
			handRotation[1].M[3][3] = 1.0F;	

			// update off hand aim
			if(!mc.vrSettings.seated) 
				controllerPoseTip[1] = Matrix4f.multiply(controllerPose[1], getControllerComponentTransform(1,"tip"));

			Vector3 leftControllerPos = OpenVRUtil.convertMatrix4ftoTranslationVector(controllerPoseTip[1]);
			aimSource[1] = leftControllerPos.toVec3d();
			controllerHistory[1].add(aimSource[1]);

			// build matrix describing controller rotation
			controllerRotation[1].M[0][0] = controllerPoseTip[1].M[0][0];
			controllerRotation[1].M[0][1] = controllerPoseTip[1].M[0][1];
			controllerRotation[1].M[0][2] = controllerPoseTip[1].M[0][2];
			controllerRotation[1].M[0][3] = 0.0F;
			controllerRotation[1].M[1][0] = controllerPoseTip[1].M[1][0];
			controllerRotation[1].M[1][1] = controllerPoseTip[1].M[1][1];
			controllerRotation[1].M[1][2] = controllerPoseTip[1].M[1][2];
			controllerRotation[1].M[1][3] = 0.0F;
			controllerRotation[1].M[2][0] = controllerPoseTip[1].M[2][0];
			controllerRotation[1].M[2][1] = controllerPoseTip[1].M[2][1];
			controllerRotation[1].M[2][2] = controllerPoseTip[1].M[2][2];
			controllerRotation[1].M[2][3] = 0.0F;
			controllerRotation[1].M[3][0] = 0.0F;
			controllerRotation[1].M[3][1] = 0.0F;
			controllerRotation[1].M[3][2] = 0.0F;
			controllerRotation[1].M[3][3] = 1.0F;

			if(mc.vrSettings.seated){
				aimSource[1] = getCenterEyePosition();
				aimSource[0] = getCenterEyePosition();
			}

		}

		boolean debugThirdController = false;
		if(debugThirdController) controllerPose[2] = controllerPose[0];

		// build matrix describing controller rotation
		controllerRotation[2].M[0][0] = controllerPose[2].M[0][0];
		controllerRotation[2].M[0][1] = controllerPose[2].M[0][1];
		controllerRotation[2].M[0][2] = controllerPose[2].M[0][2];
		controllerRotation[2].M[0][3] = 0.0F;
		controllerRotation[2].M[1][0] = controllerPose[2].M[1][0];
		controllerRotation[2].M[1][1] = controllerPose[2].M[1][1];
		controllerRotation[2].M[1][2] = controllerPose[2].M[1][2];
		controllerRotation[2].M[1][3] = 0.0F;
		controllerRotation[2].M[2][0] = controllerPose[2].M[2][0];
		controllerRotation[2].M[2][1] = controllerPose[2].M[2][1];
		controllerRotation[2].M[2][2] = controllerPose[2].M[2][2];
		controllerRotation[2].M[2][3] = 0.0F;
		controllerRotation[2].M[3][0] = 0.0F;
		controllerRotation[2].M[3][1] = 0.0F;
		controllerRotation[2].M[3][2] = 0.0F;
		controllerRotation[2].M[3][3] = 1.0F;

		if(controllerDeviceIndex[THIRD_CONTROLLER]!=-1 && (mc.vrSettings.displayMirrorMode == VRSettings.MIRROR_MIXED_REALITY || mc.vrSettings.displayMirrorMode == VRSettings.MIRROR_THIRD_PERSON )|| debugThirdController) {
			mrMovingCamActive = true;
			Vector3 thirdControllerPos = OpenVRUtil.convertMatrix4ftoTranslationVector(controllerPose[2]);
			aimSource[2] = thirdControllerPos.toVec3d();
		} else {
			mrMovingCamActive = false;
			aimSource[2] = new Vec3d(
					mc.vrSettings.vrFixedCamposX,
					mc.vrSettings.vrFixedCamposY,
					mc.vrSettings.vrFixedCamposZ);
		}


	}

	public static void debugOutput(){

	}

	public static double getCurrentTimeSecs()
	{
		return System.nanoTime() / 1000000000d;
	}

	public static HardwareType getHardwareType() {
		return mc.vrSettings.forceHardwareDetection > 0 ? HardwareType.values()[mc.vrSettings.forceHardwareDetection - 1] : detectedHardware;
	}

	
	public static boolean isGunStyle() {
		return gunStyle;
	}

	public static void resetPosition() {
		Vec3d pos= getCenterEyePosition().scale(-1).addVector(offset.getX(),offset.getY(),offset.getZ());
		offset=new Vector3((float) pos.x,(float)pos.y+1.62f,(float)pos.z);
	}

	public static void clearOffset() {
		offset=new Vector3(0,0,0);
	}

	public static void setVanillaBindings(KeyBinding[] bindings) {
		vanillaBindingSet = new HashSet<>(Arrays.asList(bindings));
	}

	public static boolean isSafeBinding(KeyBinding kb) {
		// Stupid hard-coded junk
		return getKeyBindings().contains(kb) || kb == mc.gameSettings.keyBindChat || kb == mc.gameSettings.keyBindInventory;
	}

	public static boolean isModBinding(KeyBinding kb) {
		return !vanillaBindingSet.contains(kb);
	}

	public static boolean isHMDTracking() {
		return headIsTracking;
	}
}
