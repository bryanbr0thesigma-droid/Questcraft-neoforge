package questcraft.legacy.xr;

import java.nio.ByteBuffer;
import java.nio.IntBuffer;
import java.nio.LongBuffer;
import java.util.HashSet;
import java.util.Set;

import org.lwjgl.PointerBuffer;
import org.lwjgl.input.Mouse;
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
import org.lwjgl.openxr.XrCompositionLayerQuad;
import org.lwjgl.openxr.XrEventDataBaseHeader;
import org.lwjgl.openxr.XrEventDataBuffer;
import org.lwjgl.openxr.XrEventDataSessionStateChanged;
import org.lwjgl.openxr.XrExtensionProperties;
import org.lwjgl.openxr.XrFrameBeginInfo;
import org.lwjgl.openxr.XrFrameEndInfo;
import org.lwjgl.openxr.XrFrameState;
import org.lwjgl.openxr.XrFrameWaitInfo;
import org.lwjgl.openxr.XrGraphicsBindingOpenGLESAndroidKHR;
import org.lwjgl.openxr.XrGraphicsRequirementsOpenGLESKHR;
import org.lwjgl.openxr.XrInstance;
import org.lwjgl.openxr.XrInstanceCreateInfo;
import org.lwjgl.openxr.XrInstanceCreateInfoAndroidKHR;
import org.lwjgl.openxr.XrInteractionProfileSuggestedBinding;
import org.lwjgl.openxr.XrLoaderInitInfoAndroidKHR;
import org.lwjgl.openxr.XrLoaderInitInfoBaseHeaderKHR;
import org.lwjgl.openxr.XrPosef;
import org.lwjgl.openxr.XrQuaternionf;
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
import org.lwjgl.openxr.XrVector3f;
import org.lwjgl.system.MemoryStack;
import org.lwjgl.system.MemoryUtil;
import org.vivecraft.util.VLoader;

/**
 * The game on a screen in VR: what QuestCraft shows for the versions no VR mod exists for (ModLoader's, alpha to 1.6).
 * An OpenXR session on the game's own GL context (as QuestCraft's Vivecraft makes it), one quad layer with the game's
 * picture on it, in front of where the player looked when the game started (again after the headset is recentred),
 * and the controllers as keyboard and mouse ({@link Controls}). Everything happens on the game's thread, at
 * Display.update(): one OpenXR frame per game frame.
 */
public final class ScreenXR {
    /** The screen's width in metres and its distance; its height follows the game's picture. */
    static final float SCREEN_WIDTH = 1.8f, DISTANCE = 1.6f;
    private static final String TOUCH = "/interaction_profiles/oculus/touch_controller";
    private static final String LEFT = "/user/hand/left", RIGHT = "/user/hand/right";

    private final int width, height;
    private final float screenHeight;
    private XrInstance instance;
    private XrSession session;
    private XrSpace localSpace, viewSpace, aimSpace;
    private XrSwapchain swapchain;
    private int[] images;
    private XrEventDataBuffer events;
    private XrActionSet actionSet;
    private XrAction leftStick, rightStick, leftTrigger, rightTrigger, leftGrip, rightGrip, buttonA, buttonB, buttonX, buttonY, menu, aim;
    private boolean running;
    private long displayTime;
    /** The screen's place in the local space: centre and turn about the vertical (radians) */
    private final float[] center = new float[3];
    private float yaw;
    private boolean placed;
    private final Controls controls;
    private final Controls.State state = new Controls.State();
    private GlesBlit blit;
    private long lastFrame;

    private ScreenXR(int width, int height) {
        this.width = width;
        this.height = height;
        this.screenHeight = SCREEN_WIDTH * height / width;
        this.controls = new Controls(new Controls.Sink() {
            public void key(int glfwKey, boolean down) {
                GameInput.key(glfwKey, down);
            }

            public void button(int glfwButton, boolean down) {
                GameInput.button(glfwButton, down);
            }

            public void cursor(double x, double y) {
                GameInput.cursor(x, y);
            }

            public void scroll(double amount) {
                GameInput.scroll(amount);
            }
        }, width, height);
    }

    /**
     * Starts OpenXR for a game picture of {@code width} x {@code height}. Null if it cannot (the reason is in the log):
     * the game then runs on without being seen.
     */
    public static ScreenXR start(int width, int height) {
        ScreenXR screen = new ScreenXR(width, height);
        try {
            screen.createInstance();
            screen.createSession();
            screen.createActions();
            screen.createSpacesAndSwapchain();
            GameInput.focus();
            System.out.println("QuestCraft screen: the game is on a screen in VR (" + width + " x " + height + ")");
            return screen;
        } catch (Throwable t) {
            System.out.println("QuestCraft screen: OpenXR could not start, the game will not be seen: " + t);
            t.printStackTrace();
            return null;
        }
    }

    private void createInstance() {
        try (MemoryStack stack = MemoryStack.stackPush()) {
            // as QuestCraft's Vivecraft: the launcher hands the headset over to the game, then the loader starts
            VLoader.setupAndroid();
            try {
                Thread.sleep(5000);
            } catch (InterruptedException ignored) {
            }
            XrLoaderInitInfoAndroidKHR loaderInfo = XrLoaderInitInfoAndroidKHR.calloc(stack).set(
                    KHRLoaderInitAndroid.XR_TYPE_LOADER_INIT_INFO_ANDROID_KHR, MemoryUtil.NULL,
                    VLoader.getDalvikVM(), VLoader.getDalvikActivity());
            check(KHRLoaderInit.xrInitializeLoaderKHR(XrLoaderInitInfoBaseHeaderKHR.create(loaderInfo.address())), "xrInitializeLoaderKHR");

            IntBuffer count = stack.callocInt(1);
            check(XR10.xrEnumerateInstanceExtensionProperties((ByteBuffer) null, count, null), "xrEnumerateInstanceExtensionProperties");
            XrExtensionProperties.Buffer properties = XrExtensionProperties.calloc(count.get(0), stack);
            for (XrExtensionProperties property : properties) property.type(XR10.XR_TYPE_EXTENSION_PROPERTIES);
            check(XR10.xrEnumerateInstanceExtensionProperties((ByteBuffer) null, count, properties), "xrEnumerateInstanceExtensionProperties");
            Set<String> available = new HashSet<>();
            for (XrExtensionProperties property : properties) available.add(property.extensionNameString());
            if (!available.contains(KHROpenGLESEnable.XR_KHR_OPENGL_ES_ENABLE_EXTENSION_NAME)) {
                throw new IllegalStateException("the OpenXR runtime has no OpenGL ES support");
            }

            PointerBuffer extensions = stack.callocPointer(2);
            extensions.put(MemoryUtil.memAddress(stack.UTF8(KHROpenGLESEnable.XR_KHR_OPENGL_ES_ENABLE_EXTENSION_NAME)));
            if (available.contains(KHRAndroidCreateInstance.XR_KHR_ANDROID_CREATE_INSTANCE_EXTENSION_NAME)) {
                extensions.put(MemoryUtil.memAddress(stack.UTF8(KHRAndroidCreateInstance.XR_KHR_ANDROID_CREATE_INSTANCE_EXTENSION_NAME)));
            }
            extensions.flip();

            XrApplicationInfo application = XrApplicationInfo.calloc(stack);
            application.apiVersion(XR10.XR_MAKE_VERSION(1, 0, 40));
            application.applicationName(stack.UTF8("QuestCraft"));
            application.applicationVersion(1);
            XrInstanceCreateInfoAndroidKHR android = XrInstanceCreateInfoAndroidKHR.calloc(stack).set(
                    KHRAndroidCreateInstance.XR_TYPE_INSTANCE_CREATE_INFO_ANDROID_KHR, MemoryUtil.NULL,
                    VLoader.getDalvikVM(), VLoader.getDalvikActivity());
            XrInstanceCreateInfo info = XrInstanceCreateInfo.calloc(stack);
            info.type(XR10.XR_TYPE_INSTANCE_CREATE_INFO);
            info.next(android.address());
            info.applicationInfo(application);
            info.enabledExtensionNames(extensions);
            PointerBuffer handle = stack.callocPointer(1);
            check(XR10.xrCreateInstance(info, handle), "xrCreateInstance");
            instance = new XrInstance(handle.get(0), info);
            events = XrEventDataBuffer.calloc();
        }
    }

    private void createSession() {
        try (MemoryStack stack = MemoryStack.stackPush()) {
            XrSystemGetInfo systemInfo = XrSystemGetInfo.calloc(stack);
            systemInfo.type(XR10.XR_TYPE_SYSTEM_GET_INFO);
            systemInfo.formFactor(XR10.XR_FORM_FACTOR_HEAD_MOUNTED_DISPLAY);
            LongBuffer system = stack.callocLong(1);
            check(XR10.xrGetSystem(instance, systemInfo, system), "xrGetSystem");
            // OpenXR wants the graphics requirements asked for before a session is made
            XrGraphicsRequirementsOpenGLESKHR requirements = XrGraphicsRequirementsOpenGLESKHR.calloc(stack)
                    .type(KHROpenGLESEnable.XR_TYPE_GRAPHICS_REQUIREMENTS_OPENGL_ES_KHR);
            check(KHROpenGLESEnable.xrGetOpenGLESGraphicsRequirementsKHR(instance, system.get(0), requirements), "xrGetOpenGLESGraphicsRequirementsKHR");
            // the game's own EGL context, current on this thread
            XrGraphicsBindingOpenGLESAndroidKHR binding = XrGraphicsBindingOpenGLESAndroidKHR.calloc(stack).set(
                    KHROpenGLESEnable.XR_TYPE_GRAPHICS_BINDING_OPENGL_ES_ANDROID_KHR, MemoryUtil.NULL,
                    VLoader.getEGLDisplay(), VLoader.getEGLConfig(), VLoader.getEGLContext());
            XrSessionCreateInfo info = XrSessionCreateInfo.calloc(stack);
            info.type(XR10.XR_TYPE_SESSION_CREATE_INFO);
            info.next(binding.address());
            info.systemId(system.get(0));
            PointerBuffer handle = stack.callocPointer(1);
            check(XR10.xrCreateSession(instance, info, handle), "xrCreateSession");
            session = new XrSession(handle.get(0), instance);

            long until = System.currentTimeMillis() + 15000;
            while (!running && System.currentTimeMillis() < until) {
                pollEvents();
                if (!running) {
                    try {
                        Thread.sleep(10);
                    } catch (InterruptedException ignored) {
                    }
                }
            }
            if (!running) System.out.println("QuestCraft screen: the OpenXR session has not started yet, going on");
        }
    }

    private void createActions() {
        try (MemoryStack stack = MemoryStack.stackPush()) {
            XrActionSetCreateInfo setInfo = XrActionSetCreateInfo.calloc(stack);
            setInfo.type(XR10.XR_TYPE_ACTION_SET_CREATE_INFO);
            setInfo.actionSetName(stack.UTF8("screen"));
            setInfo.localizedActionSetName(stack.UTF8("Screen"));
            PointerBuffer handle = stack.callocPointer(1);
            check(XR10.xrCreateActionSet(instance, setInfo, handle), "xrCreateActionSet");
            actionSet = new XrActionSet(handle.get(0), instance);

            leftStick = action("left_stick", XR10.XR_ACTION_TYPE_VECTOR2F_INPUT);
            rightStick = action("right_stick", XR10.XR_ACTION_TYPE_VECTOR2F_INPUT);
            leftTrigger = action("left_trigger", XR10.XR_ACTION_TYPE_FLOAT_INPUT);
            rightTrigger = action("right_trigger", XR10.XR_ACTION_TYPE_FLOAT_INPUT);
            leftGrip = action("left_grip", XR10.XR_ACTION_TYPE_FLOAT_INPUT);
            rightGrip = action("right_grip", XR10.XR_ACTION_TYPE_FLOAT_INPUT);
            buttonA = action("a", XR10.XR_ACTION_TYPE_BOOLEAN_INPUT);
            buttonB = action("b", XR10.XR_ACTION_TYPE_BOOLEAN_INPUT);
            buttonX = action("x", XR10.XR_ACTION_TYPE_BOOLEAN_INPUT);
            buttonY = action("y", XR10.XR_ACTION_TYPE_BOOLEAN_INPUT);
            menu = action("menu", XR10.XR_ACTION_TYPE_BOOLEAN_INPUT);
            aim = action("aim", XR10.XR_ACTION_TYPE_POSE_INPUT);

            Object[][] bindings = {
                    {leftStick, LEFT + "/input/thumbstick"}, {rightStick, RIGHT + "/input/thumbstick"},
                    {leftTrigger, LEFT + "/input/trigger/value"}, {rightTrigger, RIGHT + "/input/trigger/value"},
                    {leftGrip, LEFT + "/input/squeeze/value"}, {rightGrip, RIGHT + "/input/squeeze/value"},
                    {buttonA, RIGHT + "/input/a/click"}, {buttonB, RIGHT + "/input/b/click"},
                    {buttonX, LEFT + "/input/x/click"}, {buttonY, LEFT + "/input/y/click"},
                    {menu, LEFT + "/input/menu/click"}, {aim, RIGHT + "/input/aim/pose"}};
            XrActionSuggestedBinding.Buffer suggested = XrActionSuggestedBinding.calloc(bindings.length, stack);
            for (int i = 0; i < bindings.length; i++) {
                suggested.get(i).set((XrAction) bindings[i][0], path((String) bindings[i][1]));
            }
            XrInteractionProfileSuggestedBinding profile = XrInteractionProfileSuggestedBinding.calloc(stack);
            profile.type(XR10.XR_TYPE_INTERACTION_PROFILE_SUGGESTED_BINDING);
            profile.interactionProfile(path(TOUCH));
            profile.suggestedBindings(suggested);
            check(XR10.xrSuggestInteractionProfileBindings(instance, profile), "xrSuggestInteractionProfileBindings");

            XrSessionActionSetsAttachInfo attach = XrSessionActionSetsAttachInfo.calloc(stack);
            attach.type(XR10.XR_TYPE_SESSION_ACTION_SETS_ATTACH_INFO);
            attach.actionSets(stack.pointers(actionSet));
            check(XR10.xrAttachSessionActionSets(session, attach), "xrAttachSessionActionSets");

            XrActionSpaceCreateInfo spaceInfo = XrActionSpaceCreateInfo.calloc(stack);
            spaceInfo.type(XR10.XR_TYPE_ACTION_SPACE_CREATE_INFO);
            spaceInfo.action(aim);
            spaceInfo.poseInActionSpace(identity(stack));
            check(XR10.xrCreateActionSpace(session, spaceInfo, handle), "xrCreateActionSpace");
            aimSpace = new XrSpace(handle.get(0), session);
        }
    }

    private XrAction action(String name, int type) {
        try (MemoryStack stack = MemoryStack.stackPush()) {
            XrActionCreateInfo info = XrActionCreateInfo.calloc(stack);
            info.type(XR10.XR_TYPE_ACTION_CREATE_INFO);
            info.actionName(stack.UTF8(name));
            info.localizedActionName(stack.UTF8(name));
            info.actionType(type);
            PointerBuffer handle = stack.callocPointer(1);
            check(XR10.xrCreateAction(actionSet, info, handle), "xrCreateAction " + name);
            return new XrAction(handle.get(0), actionSet);
        }
    }

    private long path(String path) {
        try (MemoryStack stack = MemoryStack.stackPush()) {
            LongBuffer buffer = stack.callocLong(1);
            check(XR10.xrStringToPath(instance, path, buffer), "xrStringToPath " + path);
            return buffer.get(0);
        }
    }

    private void createSpacesAndSwapchain() {
        try (MemoryStack stack = MemoryStack.stackPush()) {
            XrReferenceSpaceCreateInfo spaceInfo = XrReferenceSpaceCreateInfo.calloc(stack);
            spaceInfo.type(XR10.XR_TYPE_REFERENCE_SPACE_CREATE_INFO);
            spaceInfo.poseInReferenceSpace(identity(stack));
            PointerBuffer handle = stack.callocPointer(1);
            spaceInfo.referenceSpaceType(XR10.XR_REFERENCE_SPACE_TYPE_LOCAL);
            check(XR10.xrCreateReferenceSpace(session, spaceInfo, handle), "xrCreateReferenceSpace local");
            localSpace = new XrSpace(handle.get(0), session);
            spaceInfo.referenceSpaceType(XR10.XR_REFERENCE_SPACE_TYPE_VIEW);
            check(XR10.xrCreateReferenceSpace(session, spaceInfo, handle), "xrCreateReferenceSpace view");
            viewSpace = new XrSpace(handle.get(0), session);

            IntBuffer count = stack.callocInt(1);
            check(XR10.xrEnumerateSwapchainFormats(session, count, null), "xrEnumerateSwapchainFormats");
            LongBuffer formats = stack.callocLong(count.get(0));
            check(XR10.xrEnumerateSwapchainFormats(session, count, formats), "xrEnumerateSwapchainFormats");
            // sRGB as QuestCraft's Vivecraft: the game's picture is gamma encoded and is copied in as it is
            long format = 0;
            for (long wanted : new long[]{0x8C43 /* GL_SRGB8_ALPHA8 */, 0x8058 /* GL_RGBA8 */}) {
                for (int i = 0; i < formats.limit() && format == 0; i++) {
                    if (formats.get(i) == wanted) format = wanted;
                }
            }
            if (format == 0) throw new IllegalStateException("no usable swapchain format");
            XrSwapchainCreateInfo info = XrSwapchainCreateInfo.calloc(stack);
            info.type(XR10.XR_TYPE_SWAPCHAIN_CREATE_INFO);
            info.usageFlags(XR10.XR_SWAPCHAIN_USAGE_COLOR_ATTACHMENT_BIT | XR10.XR_SWAPCHAIN_USAGE_TRANSFER_DST_BIT);
            info.format(format);
            info.sampleCount(1);
            info.width(width);
            info.height(height);
            info.faceCount(1);
            info.arraySize(1);
            info.mipCount(1);
            check(XR10.xrCreateSwapchain(session, info, handle), "xrCreateSwapchain");
            swapchain = new XrSwapchain(handle.get(0), session);

            check(XR10.xrEnumerateSwapchainImages(swapchain, count, null), "xrEnumerateSwapchainImages");
            XrSwapchainImageOpenGLESKHR.Buffer list = XrSwapchainImageOpenGLESKHR.calloc(count.get(0), stack);
            for (XrSwapchainImageOpenGLESKHR image : list) image.type(KHROpenGLESEnable.XR_TYPE_SWAPCHAIN_IMAGE_OPENGL_ES_KHR);
            check(XR10.xrEnumerateSwapchainImages(swapchain, count, XrSwapchainImageBaseHeader.create(list.address(), list.capacity())), "xrEnumerateSwapchainImages");
            images = new int[list.capacity()];
            for (int i = 0; i < images.length; i++) images[i] = list.get(i).image();
        }
    }

    /** One OpenXR frame: the game's picture ({@code texture}) on the screen, the controllers to the game. */
    public void frame(int texture) {
        pollEvents();
        if (!running) return;
        long now = System.nanoTime();
        float seconds = lastFrame == 0 ? 0 : Math.min(0.1f, (now - lastFrame) / 1e9f);
        lastFrame = now;

        try (MemoryStack stack = MemoryStack.stackPush()) {
            XrFrameState frameState = XrFrameState.calloc(stack).type(XR10.XR_TYPE_FRAME_STATE);
            XrFrameWaitInfo waitInfo = XrFrameWaitInfo.calloc(stack).type(XR10.XR_TYPE_FRAME_WAIT_INFO);
            if (log(XR10.xrWaitFrame(session, waitInfo, frameState), "xrWaitFrame")) return;
            displayTime = frameState.predictedDisplayTime();
            if (log(XR10.xrBeginFrame(session, XrFrameBeginInfo.calloc(stack).type(XR10.XR_TYPE_FRAME_BEGIN_INFO)), "xrBeginFrame")) return;

            XrSpaceLocation location = XrSpaceLocation.calloc(stack).type(XR10.XR_TYPE_SPACE_LOCATION);
            if (!placed && locate(viewSpace, location)) place(location.pose());

            readControllers(stack, location);
            controls.update(state, Mouse.isGrabbed(), seconds);

            PointerBuffer layers = null;
            if (frameState.shouldRender() && placed) {
                IntBuffer index = stack.callocInt(1);
                if (!log(XR10.xrAcquireSwapchainImage(swapchain, XrSwapchainImageAcquireInfo.calloc(stack).type(XR10.XR_TYPE_SWAPCHAIN_IMAGE_ACQUIRE_INFO), index), "xrAcquireSwapchainImage")) {
                    XrSwapchainImageWaitInfo wait = XrSwapchainImageWaitInfo.calloc(stack).type(XR10.XR_TYPE_SWAPCHAIN_IMAGE_WAIT_INFO);
                    wait.timeout(XR10.XR_INFINITE_DURATION);
                    log(XR10.xrWaitSwapchainImage(swapchain, wait), "xrWaitSwapchainImage");
                    if (blit == null) blit = new GlesBlit();
                    blit.copy(texture, images[index.get(0)], width, height, controls.pointerVisible(), controls.pointerX(), controls.pointerY());
                    log(XR10.xrReleaseSwapchainImage(swapchain, XrSwapchainImageReleaseInfo.calloc(stack).type(XR10.XR_TYPE_SWAPCHAIN_IMAGE_RELEASE_INFO)), "xrReleaseSwapchainImage");
                    layers = stack.pointers(quad(stack).address());
                }
            }
            XrFrameEndInfo end = XrFrameEndInfo.calloc(stack);
            end.type(XR10.XR_TYPE_FRAME_END_INFO);
            end.displayTime(displayTime);
            end.environmentBlendMode(XR10.XR_ENVIRONMENT_BLEND_MODE_OPAQUE);
            end.layers(layers);
            log(XR10.xrEndFrame(session, end), "xrEndFrame");
        }
    }

    private XrCompositionLayerQuad quad(MemoryStack stack) {
        XrCompositionLayerQuad quad = XrCompositionLayerQuad.calloc(stack);
        quad.type(XR10.XR_TYPE_COMPOSITION_LAYER_QUAD);
        quad.space(localSpace);
        quad.eyeVisibility(XR10.XR_EYE_VISIBILITY_BOTH);
        quad.subImage().swapchain(swapchain);
        quad.subImage().imageArrayIndex(0);
        quad.subImage().imageRect().offset().set(0, 0);
        quad.subImage().imageRect().extent().set(width, height);
        quad.pose().position$().set(center[0], center[1], center[2]);
        quad.pose().orientation().set(0, (float) Math.sin(yaw / 2), 0, (float) Math.cos(yaw / 2));
        quad.size().set(SCREEN_WIDTH, screenHeight);
        return quad;
    }

    /** The screen at eye height, {@link #DISTANCE} in front of where the head looks, turned to face it. */
    private void place(XrPosef head) {
        float[] forward = rotate(head.orientation(), 0, 0, -1);
        float length = (float) Math.hypot(forward[0], forward[2]);
        float fx = length > 1e-3f ? forward[0] / length : 0, fz = length > 1e-3f ? forward[2] / length : -1;
        XrVector3f p = head.position$();
        center[0] = p.x() + fx * DISTANCE;
        center[1] = p.y();
        center[2] = p.z() + fz * DISTANCE;
        // its front (+z) towards the player
        yaw = (float) Math.atan2(-fx, -fz);
        placed = true;
    }

    private void readControllers(MemoryStack stack, XrSpaceLocation location) {
        XrActiveActionSet.Buffer active = XrActiveActionSet.calloc(1, stack);
        active.get(0).set(actionSet, MemoryUtil.NULL);
        XrActionsSyncInfo sync = XrActionsSyncInfo.calloc(stack);
        sync.type(XR10.XR_TYPE_ACTIONS_SYNC_INFO);
        sync.activeActionSets(active);
        log(XR10.xrSyncActions(session, sync), "xrSyncActions");

        float[] v = vector(leftStick, stack);
        state.leftX = v[0];
        state.leftY = v[1];
        v = vector(rightStick, stack);
        state.rightX = v[0];
        state.rightY = v[1];
        state.leftTrigger = value(leftTrigger, stack);
        state.rightTrigger = value(rightTrigger, stack);
        state.leftGrip = value(leftGrip, stack);
        state.rightGrip = value(rightGrip, stack);
        state.a = pressed(buttonA, stack);
        state.b = pressed(buttonB, stack);
        state.x = pressed(buttonX, stack);
        state.y = pressed(buttonY, stack);
        state.menu = pressed(menu, stack);

        state.pointing = false;
        if (placed && locate(aimSpace, location)) {
            XrPosef pose = location.pose();
            float[] direction = rotate(pose.orientation(), 0, 0, -1);
            XrVector3f p = pose.position$();
            float[] hit = ScreenMath.hit(center, yaw, SCREEN_WIDTH, screenHeight, new float[]{p.x(), p.y(), p.z()}, direction);
            if (hit != null) {
                state.pointing = true;
                state.pointerU = hit[0];
                state.pointerV = hit[1];
            }
        }
    }

    /** v rotated by the quaternion q. */
    static float[] rotate(XrQuaternionf q, float vx, float vy, float vz) {
        return ScreenMath.rotate(q.x(), q.y(), q.z(), q.w(), vx, vy, vz);
    }

    private float[] vector(XrAction action, MemoryStack stack) {
        XrActionStateVector2f state = XrActionStateVector2f.calloc(stack).type(XR10.XR_TYPE_ACTION_STATE_VECTOR2F);
        log(XR10.xrGetActionStateVector2f(session, getInfo(action, stack), state), "xrGetActionStateVector2f");
        return state.isActive() ? new float[]{state.currentState().x(), state.currentState().y()} : new float[2];
    }

    private float value(XrAction action, MemoryStack stack) {
        XrActionStateFloat state = XrActionStateFloat.calloc(stack).type(XR10.XR_TYPE_ACTION_STATE_FLOAT);
        log(XR10.xrGetActionStateFloat(session, getInfo(action, stack), state), "xrGetActionStateFloat");
        return state.isActive() ? state.currentState() : 0;
    }

    private boolean pressed(XrAction action, MemoryStack stack) {
        XrActionStateBoolean state = XrActionStateBoolean.calloc(stack).type(XR10.XR_TYPE_ACTION_STATE_BOOLEAN);
        log(XR10.xrGetActionStateBoolean(session, getInfo(action, stack), state), "xrGetActionStateBoolean");
        return state.isActive() && state.currentState();
    }

    private static XrActionStateGetInfo getInfo(XrAction action, MemoryStack stack) {
        XrActionStateGetInfo info = XrActionStateGetInfo.calloc(stack);
        info.type(XR10.XR_TYPE_ACTION_STATE_GET_INFO);
        info.action(action);
        return info;
    }

    private boolean locate(XrSpace space, XrSpaceLocation location) {
        int error = XR10.xrLocateSpace(space, localSpace, displayTime, location);
        return error >= 0 && (location.locationFlags() & XR10.XR_SPACE_LOCATION_ORIENTATION_VALID_BIT) != 0
                && (location.locationFlags() & XR10.XR_SPACE_LOCATION_POSITION_VALID_BIT) != 0;
    }

    private void pollEvents() {
        while (true) {
            events.clear();
            events.type(XR10.XR_TYPE_EVENT_DATA_BUFFER);
            if (XR10.xrPollEvent(instance, events) != XR10.XR_SUCCESS) return;
            XrEventDataBaseHeader event = XrEventDataBaseHeader.create(events.address());
            switch (event.type()) {
                case XR10.XR_TYPE_EVENT_DATA_SESSION_STATE_CHANGED:
                    sessionChanged(XrEventDataSessionStateChanged.create(event.address()).state());
                    break;
                case XR10.XR_TYPE_EVENT_DATA_REFERENCE_SPACE_CHANGE_PENDING:
                    // the headset was recentred: the screen goes in front of the player again
                    placed = false;
                    break;
                case XR10.XR_TYPE_EVENT_DATA_INSTANCE_LOSS_PENDING:
                    System.out.println("QuestCraft screen: OpenXR is going away, closing the game");
                    System.exit(0);
                    break;
                default:
                    break;
            }
        }
    }

    private void sessionChanged(int sessionState) {
        switch (sessionState) {
            case XR10.XR_SESSION_STATE_READY:
                try (MemoryStack stack = MemoryStack.stackPush()) {
                    XrSessionBeginInfo begin = XrSessionBeginInfo.calloc(stack);
                    begin.type(XR10.XR_TYPE_SESSION_BEGIN_INFO);
                    begin.primaryViewConfigurationType(XR10.XR_VIEW_CONFIGURATION_TYPE_PRIMARY_STEREO);
                    running = !log(XR10.xrBeginSession(session, begin), "xrBeginSession");
                }
                break;
            case XR10.XR_SESSION_STATE_STOPPING:
                running = false;
                log(XR10.xrEndSession(session), "xrEndSession");
                break;
            case XR10.XR_SESSION_STATE_EXITING:
            case XR10.XR_SESSION_STATE_LOSS_PENDING:
                System.out.println("QuestCraft screen: OpenXR stopped, closing the game with it");
                System.exit(0);
                break;
            default:
                break;
        }
    }

    private static XrPosef identity(MemoryStack stack) {
        XrPosef pose = XrPosef.calloc(stack);
        pose.orientation().set(0, 0, 0, 1);
        return pose;
    }

    private static void check(int result, String what) {
        if (result < 0) throw new IllegalStateException(what + " failed: XrResult " + result);
    }

    /** Logs a failed call; true if it failed. */
    private static boolean log(int result, String what) {
        if (result < 0) System.out.println("QuestCraft screen: " + what + " failed: XrResult " + result);
        return result < 0;
    }
}
