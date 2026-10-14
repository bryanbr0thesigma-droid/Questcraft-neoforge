package questcraft.legacy.vr;

import java.nio.ByteBuffer;
import java.nio.IntBuffer;
import java.nio.LongBuffer;
import java.util.HashSet;
import java.util.Set;

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
import org.lwjgl.openxr.XrCompositionLayerProjection;
import org.lwjgl.openxr.XrCompositionLayerProjectionView;
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
import org.lwjgl.openxr.XrView;
import org.lwjgl.openxr.XrViewConfigurationView;
import org.lwjgl.openxr.XrViewLocateInfo;
import org.lwjgl.openxr.XrViewState;
import org.lwjgl.system.MemoryStack;
import org.lwjgl.system.MemoryUtil;
import org.vivecraft.util.VLoader;

import questcraft.legacy.xr.GlesBlit;

/**
 * The headset: an OpenXR session on the game's own GL context (as QuestCraft's Vivecraft makes it), the room as
 * OpenXR's stage space (the floor at y 0), the two eyes as one swapchain with a layer each, and the Quest controllers.
 */
final class VRSessionXR implements VRProvider {
    private static final String TOUCH = "/interaction_profiles/oculus/touch_controller";
    private static final String[] HANDS = {"/user/hand/left", "/user/hand/right"};
    /** The eyes are drawn at this part of the headset's recommended size: the old game's GL is slow on the headset */
    private static final float SCALE = Float.parseFloat(System.getProperty("questcraft.legacy.vr.scale", "0.6"));

    private XrInstance instance;
    private XrSession session;
    private long systemId;
    private XrSpace roomSpace, viewSpace;
    private final XrSpace[] aimSpaces = new XrSpace[2], gripSpaces = new XrSpace[2];
    private XrSwapchain swapchain;
    private int[] images;
    private int width, height;
    private XrEventDataBuffer events;
    private XrView.Buffer views;
    private XrActionSet actionSet;
    private XrAction leftStick, rightStick, leftTrigger, rightTrigger, leftGrip, rightGrip, buttonA, buttonB, buttonX, buttonY,
            menu, leftClick, rightClick;
    private final XrAction[] aim = new XrAction[2], grip = new XrAction[2];
    private boolean running, frameBegun;
    private long displayTime;
    private GlesBlit blit;

    public int eyeWidth() {
        return width;
    }

    public int eyeHeight() {
        return height;
    }

    public boolean start() {
        try {
            createInstance();
            createSession();
            createActions();
            createSpacesAndSwapchain();
            System.out.println("QuestCraft VR: OpenXR started, eyes " + width + " x " + height);
            return true;
        } catch (Throwable t) {
            System.out.println("QuestCraft VR: OpenXR could not start: " + t);
            t.printStackTrace();
            destroy();
            return false;
        }
    }

    /** What was made of OpenXR, given back (its other handles go with the session and the instance). */
    private void destroy() {
        try {
            if (session != null) XR10.xrDestroySession(session);
            if (instance != null) XR10.xrDestroyInstance(instance);
        } catch (Throwable t) {
            System.out.println("QuestCraft VR: could not close OpenXR: " + t);
        }
        session = null;
        instance = null;
        running = false;
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
            systemId = system.get(0);
            XrGraphicsRequirementsOpenGLESKHR requirements = XrGraphicsRequirementsOpenGLESKHR.calloc(stack)
                    .type(KHROpenGLESEnable.XR_TYPE_GRAPHICS_REQUIREMENTS_OPENGL_ES_KHR);
            check(KHROpenGLESEnable.xrGetOpenGLESGraphicsRequirementsKHR(instance, systemId, requirements), "xrGetOpenGLESGraphicsRequirementsKHR");
            XrGraphicsBindingOpenGLESAndroidKHR binding = XrGraphicsBindingOpenGLESAndroidKHR.calloc(stack).set(
                    KHROpenGLESEnable.XR_TYPE_GRAPHICS_BINDING_OPENGL_ES_ANDROID_KHR, MemoryUtil.NULL,
                    VLoader.getEGLDisplay(), VLoader.getEGLConfig(), VLoader.getEGLContext());
            XrSessionCreateInfo info = XrSessionCreateInfo.calloc(stack);
            info.type(XR10.XR_TYPE_SESSION_CREATE_INFO);
            info.next(binding.address());
            info.systemId(systemId);
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
            if (!running) System.out.println("QuestCraft VR: the OpenXR session has not started yet, going on");
        }
    }

    private void createActions() {
        try (MemoryStack stack = MemoryStack.stackPush()) {
            XrActionSetCreateInfo setInfo = XrActionSetCreateInfo.calloc(stack);
            setInfo.type(XR10.XR_TYPE_ACTION_SET_CREATE_INFO);
            setInfo.actionSetName(stack.UTF8("game"));
            setInfo.localizedActionSetName(stack.UTF8("Game"));
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
            leftClick = action("left_stick_click", XR10.XR_ACTION_TYPE_BOOLEAN_INPUT);
            rightClick = action("right_stick_click", XR10.XR_ACTION_TYPE_BOOLEAN_INPUT);
            for (int hand = 0; hand < 2; hand++) {
                String side = hand == 0 ? "left" : "right";
                aim[hand] = action("aim_" + side, XR10.XR_ACTION_TYPE_POSE_INPUT);
                grip[hand] = action("grip_" + side, XR10.XR_ACTION_TYPE_POSE_INPUT);
            }

            Object[][] bindings = {
                    {leftStick, HANDS[0] + "/input/thumbstick"}, {rightStick, HANDS[1] + "/input/thumbstick"},
                    {leftTrigger, HANDS[0] + "/input/trigger/value"}, {rightTrigger, HANDS[1] + "/input/trigger/value"},
                    {leftGrip, HANDS[0] + "/input/squeeze/value"}, {rightGrip, HANDS[1] + "/input/squeeze/value"},
                    {buttonA, HANDS[1] + "/input/a/click"}, {buttonB, HANDS[1] + "/input/b/click"},
                    {buttonX, HANDS[0] + "/input/x/click"}, {buttonY, HANDS[0] + "/input/y/click"},
                    {menu, HANDS[0] + "/input/menu/click"},
                    {leftClick, HANDS[0] + "/input/thumbstick/click"}, {rightClick, HANDS[1] + "/input/thumbstick/click"},
                    {aim[0], HANDS[0] + "/input/aim/pose"}, {aim[1], HANDS[1] + "/input/aim/pose"},
                    {grip[0], HANDS[0] + "/input/grip/pose"}, {grip[1], HANDS[1] + "/input/grip/pose"}};
            XrActionSuggestedBinding.Buffer suggested = XrActionSuggestedBinding.calloc(bindings.length, stack);
            for (int i = 0; i < bindings.length; i++) suggested.get(i).set((XrAction) bindings[i][0], path((String) bindings[i][1]));
            XrInteractionProfileSuggestedBinding profile = XrInteractionProfileSuggestedBinding.calloc(stack);
            profile.type(XR10.XR_TYPE_INTERACTION_PROFILE_SUGGESTED_BINDING);
            profile.interactionProfile(path(TOUCH));
            profile.suggestedBindings(suggested);
            check(XR10.xrSuggestInteractionProfileBindings(instance, profile), "xrSuggestInteractionProfileBindings");

            XrSessionActionSetsAttachInfo attach = XrSessionActionSetsAttachInfo.calloc(stack);
            attach.type(XR10.XR_TYPE_SESSION_ACTION_SETS_ATTACH_INFO);
            attach.actionSets(stack.pointers(actionSet));
            check(XR10.xrAttachSessionActionSets(session, attach), "xrAttachSessionActionSets");
            for (int hand = 0; hand < 2; hand++) {
                aimSpaces[hand] = actionSpace(aim[hand], stack);
                gripSpaces[hand] = actionSpace(grip[hand], stack);
            }
        }
    }

    private XrSpace actionSpace(XrAction action, MemoryStack stack) {
        XrActionSpaceCreateInfo info = XrActionSpaceCreateInfo.calloc(stack);
        info.type(XR10.XR_TYPE_ACTION_SPACE_CREATE_INFO);
        info.action(action);
        info.poseInActionSpace(identity(stack));
        PointerBuffer handle = stack.callocPointer(1);
        check(XR10.xrCreateActionSpace(session, info, handle), "xrCreateActionSpace");
        return new XrSpace(handle.get(0), session);
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
            spaceInfo.referenceSpaceType(XR10.XR_REFERENCE_SPACE_TYPE_STAGE);
            if (XR10.xrCreateReferenceSpace(session, spaceInfo, handle) < 0) {
                // no room set up: the local space, the floor guessed 1.62 m under where the head started
                spaceInfo.referenceSpaceType(XR10.XR_REFERENCE_SPACE_TYPE_LOCAL);
                spaceInfo.poseInReferenceSpace().position$().set(0, -VRMath.EYE_HEIGHT, 0);
                check(XR10.xrCreateReferenceSpace(session, spaceInfo, handle), "xrCreateReferenceSpace local");
            }
            roomSpace = new XrSpace(handle.get(0), session);
            spaceInfo.referenceSpaceType(XR10.XR_REFERENCE_SPACE_TYPE_VIEW);
            spaceInfo.poseInReferenceSpace(identity(stack));
            check(XR10.xrCreateReferenceSpace(session, spaceInfo, handle), "xrCreateReferenceSpace view");
            viewSpace = new XrSpace(handle.get(0), session);

            IntBuffer count = stack.callocInt(1);
            check(XR10.xrEnumerateViewConfigurationViews(instance, systemId, XR10.XR_VIEW_CONFIGURATION_TYPE_PRIMARY_STEREO, count, null), "xrEnumerateViewConfigurationViews");
            XrViewConfigurationView.Buffer configurations = XrViewConfigurationView.calloc(count.get(0), stack);
            for (XrViewConfigurationView view : configurations) view.type(XR10.XR_TYPE_VIEW_CONFIGURATION_VIEW);
            check(XR10.xrEnumerateViewConfigurationViews(instance, systemId, XR10.XR_VIEW_CONFIGURATION_TYPE_PRIMARY_STEREO, count, configurations), "xrEnumerateViewConfigurationViews");
            width = Math.max(256, Math.round(configurations.get(0).recommendedImageRectWidth() * SCALE));
            height = Math.max(256, Math.round(configurations.get(0).recommendedImageRectHeight() * SCALE));
            views = XrView.calloc(2);
            for (XrView view : views) {
                view.type(XR10.XR_TYPE_VIEW);
                view.pose().orientation().w(1);
            }

            check(XR10.xrEnumerateSwapchainFormats(session, count, null), "xrEnumerateSwapchainFormats");
            LongBuffer formats = stack.callocLong(count.get(0));
            check(XR10.xrEnumerateSwapchainFormats(session, count, formats), "xrEnumerateSwapchainFormats");
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
            info.arraySize(2);
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

    public boolean beginFrame(VRFrame frame) {
        pollEvents();
        if (!running) return false;
        try (MemoryStack stack = MemoryStack.stackPush()) {
            XrFrameState state = XrFrameState.calloc(stack).type(XR10.XR_TYPE_FRAME_STATE);
            if (log(XR10.xrWaitFrame(session, XrFrameWaitInfo.calloc(stack).type(XR10.XR_TYPE_FRAME_WAIT_INFO), state), "xrWaitFrame")) return false;
            displayTime = state.predictedDisplayTime();
            frame.shouldRender = state.shouldRender();
            if (log(XR10.xrBeginFrame(session, XrFrameBeginInfo.calloc(stack).type(XR10.XR_TYPE_FRAME_BEGIN_INFO)), "xrBeginFrame")) return false;
            frameBegun = true;

            XrViewLocateInfo locate = XrViewLocateInfo.calloc(stack);
            locate.type(XR10.XR_TYPE_VIEW_LOCATE_INFO);
            locate.viewConfigurationType(XR10.XR_VIEW_CONFIGURATION_TYPE_PRIMARY_STEREO);
            locate.displayTime(displayTime);
            locate.space(roomSpace);
            IntBuffer count = stack.callocInt(1);
            log(XR10.xrLocateViews(session, locate, XrViewState.calloc(stack).type(XR10.XR_TYPE_VIEW_STATE), count, views), "xrLocateViews");
            for (int eye = 0; eye < 2; eye++) {
                XrView view = views.get(eye);
                copyPose(view.pose(), frame.eyePosition[eye], frame.eyeOrientation[eye]);
                frame.eyeFov[eye][0] = view.fov().angleLeft();
                frame.eyeFov[eye][1] = view.fov().angleRight();
                frame.eyeFov[eye][2] = view.fov().angleUp();
                frame.eyeFov[eye][3] = view.fov().angleDown();
            }
            XrSpaceLocation location = XrSpaceLocation.calloc(stack).type(XR10.XR_TYPE_SPACE_LOCATION);
            frame.headTracked = locate(viewSpace, location);
            if (frame.headTracked) copyPose(location.pose(), frame.headPosition, frame.headOrientation);
            readControllers(frame, stack, location);
            return true;
        }
    }

    public void endFrame(VRFrame frame, int leftTexture, int rightTexture) {
        if (!frameBegun) return;
        frameBegun = false;
        try (MemoryStack stack = MemoryStack.stackPush()) {
            PointerBuffer layers = null;
            if (frame.shouldRender && leftTexture > 0 && rightTexture > 0) {
                IntBuffer index = stack.callocInt(1);
                if (!log(XR10.xrAcquireSwapchainImage(swapchain, XrSwapchainImageAcquireInfo.calloc(stack).type(XR10.XR_TYPE_SWAPCHAIN_IMAGE_ACQUIRE_INFO), index), "xrAcquireSwapchainImage")) {
                    XrSwapchainImageWaitInfo wait = XrSwapchainImageWaitInfo.calloc(stack).type(XR10.XR_TYPE_SWAPCHAIN_IMAGE_WAIT_INFO);
                    wait.timeout(XR10.XR_INFINITE_DURATION);
                    log(XR10.xrWaitSwapchainImage(swapchain, wait), "xrWaitSwapchainImage");
                    if (blit == null) blit = new GlesBlit();
                    int image = images[index.get(0)];
                    blit.copy(leftTexture, image, 0, width, height, false, 0, 0);
                    blit.copy(rightTexture, image, 1, width, height, false, 0, 0);
                    log(XR10.xrReleaseSwapchainImage(swapchain, XrSwapchainImageReleaseInfo.calloc(stack).type(XR10.XR_TYPE_SWAPCHAIN_IMAGE_RELEASE_INFO)), "xrReleaseSwapchainImage");

                    XrCompositionLayerProjectionView.Buffer projectionViews = XrCompositionLayerProjectionView.calloc(2, stack);
                    for (int eye = 0; eye < 2; eye++) {
                        XrCompositionLayerProjectionView view = projectionViews.get(eye);
                        view.type(XR10.XR_TYPE_COMPOSITION_LAYER_PROJECTION_VIEW);
                        view.pose(views.get(eye).pose());
                        view.fov(views.get(eye).fov());
                        view.subImage().swapchain(swapchain);
                        view.subImage().imageArrayIndex(eye);
                        view.subImage().imageRect().offset().set(0, 0);
                        view.subImage().imageRect().extent().set(width, height);
                    }
                    XrCompositionLayerProjection projection = XrCompositionLayerProjection.calloc(stack);
                    projection.type(XR10.XR_TYPE_COMPOSITION_LAYER_PROJECTION);
                    projection.space(roomSpace);
                    projection.views(projectionViews);
                    layers = stack.pointers(projection.address());
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

    private void readControllers(VRFrame frame, MemoryStack stack, XrSpaceLocation location) {
        XrActiveActionSet.Buffer active = XrActiveActionSet.calloc(1, stack);
        active.get(0).set(actionSet, MemoryUtil.NULL);
        XrActionsSyncInfo sync = XrActionsSyncInfo.calloc(stack);
        sync.type(XR10.XR_TYPE_ACTIONS_SYNC_INFO);
        sync.activeActionSets(active);
        log(XR10.xrSyncActions(session, sync), "xrSyncActions");
        float[] v = vector(leftStick, stack);
        frame.leftX = v[0];
        frame.leftY = v[1];
        v = vector(rightStick, stack);
        frame.rightX = v[0];
        frame.rightY = v[1];
        frame.leftTrigger = value(leftTrigger, stack);
        frame.rightTrigger = value(rightTrigger, stack);
        frame.leftGrip = value(leftGrip, stack);
        frame.rightGrip = value(rightGrip, stack);
        frame.a = pressed(buttonA, stack);
        frame.b = pressed(buttonB, stack);
        frame.x = pressed(buttonX, stack);
        frame.y = pressed(buttonY, stack);
        frame.menu = pressed(menu, stack);
        frame.leftStickClick = pressed(leftClick, stack);
        frame.rightStickClick = pressed(rightClick, stack);
        for (int hand = 0; hand < 2; hand++) {
            boolean tracked = locate(aimSpaces[hand], location);
            if (tracked) copyPose(location.pose(), frame.aimPosition[hand], frame.aimOrientation[hand]);
            if (locate(gripSpaces[hand], location)) copyPose(location.pose(), frame.gripPosition[hand], frame.gripOrientation[hand]);
            frame.handTracked[hand] = tracked;
        }
    }

    private static void copyPose(XrPosef pose, float[] position, float[] orientation) {
        position[0] = pose.position$().x();
        position[1] = pose.position$().y();
        position[2] = pose.position$().z();
        orientation[0] = pose.orientation().x();
        orientation[1] = pose.orientation().y();
        orientation[2] = pose.orientation().z();
        orientation[3] = pose.orientation().w();
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
        int error = XR10.xrLocateSpace(space, roomSpace, displayTime, location);
        return error >= 0 && (location.locationFlags() & XR10.XR_SPACE_LOCATION_ORIENTATION_VALID_BIT) != 0
                && (location.locationFlags() & XR10.XR_SPACE_LOCATION_POSITION_VALID_BIT) != 0;
    }

    private void pollEvents() {
        while (true) {
            events.clear();
            events.type(XR10.XR_TYPE_EVENT_DATA_BUFFER);
            if (XR10.xrPollEvent(instance, events) != XR10.XR_SUCCESS) return;
            XrEventDataBaseHeader event = XrEventDataBaseHeader.create(events.address());
            if (event.type() == XR10.XR_TYPE_EVENT_DATA_SESSION_STATE_CHANGED) {
                sessionChanged(XrEventDataSessionStateChanged.create(event.address()).state());
            } else if (event.type() == XR10.XR_TYPE_EVENT_DATA_INSTANCE_LOSS_PENDING) {
                System.out.println("QuestCraft VR: OpenXR is going away, closing the game");
                System.exit(0);
            }
        }
    }

    private void sessionChanged(int state) {
        switch (state) {
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
                frameBegun = false;
                log(XR10.xrEndSession(session), "xrEndSession");
                break;
            case XR10.XR_SESSION_STATE_EXITING:
            case XR10.XR_SESSION_STATE_LOSS_PENDING:
                System.out.println("QuestCraft VR: OpenXR stopped, closing the game with it");
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

    private static boolean log(int result, String what) {
        if (result < 0) System.out.println("QuestCraft VR: " + what + " failed: XrResult " + result);
        return result < 0;
    }
}
