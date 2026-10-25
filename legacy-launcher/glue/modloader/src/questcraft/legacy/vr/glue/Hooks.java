package questcraft.legacy.vr.glue;

import java.nio.FloatBuffer;
import java.util.List;

import org.lwjgl.BufferUtils;
import org.lwjgl.opengl.GL11;
//#if >=b1.8.1 && <1.0
import org.lwjgl.opengl.GL13;
//#endif

//#if <b1.8.1
import net.minecraft.client.CreativeInteractionManager;
//#endif
import net.minecraft.client.Minecraft;
//#if >=1.6.1
import net.minecraft.client.entity.living.player.ClientPlayerEntity;
import net.minecraft.client.entity.living.player.KeyboardInput;
//#else
import net.minecraft.client.entity.mob.player.KeyboardInput;
//#endif
import net.minecraft.client.gui.GameGui;
import net.minecraft.client.gui.screen.ChatScreen;
//#if >=b1.8.1
import net.minecraft.client.gui.screen.DirectConnectScreen;
import net.minecraft.client.gui.screen.menu.AddServerScreen;
//#else
import net.minecraft.client.gui.screen.JoinMultiplayerScreen;
//#endif
import net.minecraft.client.gui.screen.Screen;
//#if <1.6.1
import net.minecraft.client.gui.screen.TitleScreen;
//#endif
import net.minecraft.client.gui.screen.inventory.menu.SignEditScreen;
//#if >=b1.3_01
import net.minecraft.client.gui.screen.world.CreateWorldScreen;
import net.minecraft.client.gui.screen.world.EditWorldScreen;
//#endif
import net.minecraft.client.gui.screen.options.OptionsScreen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.client.options.GameOptions;
import net.minecraft.client.render.GameRenderer;
import net.minecraft.client.render.Window;
//#if >=1.0
import net.minecraft.client.render.platform.GLX;
//#endif
import net.minecraft.client.render.entity.EntityRenderDispatcher;
import net.minecraft.client.render.entity.PlayerRenderer;
import net.minecraft.client.render.model.ModelPart;
import net.minecraft.client.render.model.entity.HumanoidModel;
import net.minecraft.client.render.platform.Lighting;
import net.minecraft.block.Block;
import net.minecraft.entity.Entity;
//#if >=1.6.1
import net.minecraft.entity.living.player.PlayerEntity;
//#else
import net.minecraft.entity.mob.player.PlayerEntity;
//#endif
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.HitResult;
//#if >=b1.2
import net.minecraft.world.HitResult__Type;
//#endif

import questcraft.legacy.vr.SkinModel;
import questcraft.legacy.vr.VR;
import questcraft.legacy.vr.VRFrame;
import questcraft.legacy.vr.VRMath;
import questcraft.legacy.vr.VRSettings;

/**
 * Beta 1.7.3 in VR: what the game calls where hooks.txt puts its hooks. The headset is the camera (the game's two
 * anaglyph passes draw the two eyes), the right controller aims and holds the item, the left stick walks, walking in
 * the room walks in the world, and the HUD and the menus are drawn onto panels in VR ({@link VR}).
 * <p>
 * Each hook does exactly what the game did when VR is off.
 */
public final class Hooks {
    /** The eye the world is drawn for next: the anaglyph pass's */
    private static int nextEye;
    /** The headset's spot in the room the player was last moved to (walking in the room walks in the world) */
    private static float anchorX, anchorZ;
    private static boolean anchored;
    /** The right controller's ray this frame, relative to the camera: its line is drawn to where it hits */
    private static final float[] rayStart = new float[3], rayEnd = new float[3];
    private static boolean rayValid;
    private static final FloatBuffer matrix = BufferUtils.createFloatBuffer(16);

    private Hooks() {
    }

    // ---- a frame ----

    /** GameRenderer.render(float), first. */
    public static void frameStart(GameRenderer renderer, float tickDelta) {
        if (!VR.enabled()) return;
        Minecraft minecraft = renderer.minecraft;
        // 3D anaglyph would tint every texture, block and item red and cyan: the eyes are VR's
        if (minecraft.options.anaglyph) {
            minecraft.options.anaglyph = false;
            //#if >=1.6.1
            minecraft.reloadResources();
            //#else
            minecraft.textureManager.reload();
            //#endif
            if (minecraft.worldRenderer != null) minecraft.worldRenderer.reload();
        }
        radial(minecraft);
        wristHud(minecraft);
        VR.setMenuOpen(minecraft.screen != null);
        VR.setKeyboardWanted(wantsText(minecraft.screen));
        // the camera is the headset's: there is no third person
        //#if >=1.0
        minecraft.options.perspective = 0;
        //#else
        minecraft.options.perspective = false;
        //#endif
        //#if >=1.2.5
        inputPlayer = minecraft.player;
        //#endif
    }

    /** The hotbar slot the right hand touched on the wrist last frame (selected once, when the touch starts) */
    private static int lastTouched = -1;

    /**
     * The HUD on the forearm (VR): where GameGui draws the hearts, armor, air and the hotbar (in scaled pixels: the
     * hotbar 182 wide, 22 high at the bottom middle, the rows of icons over it up to 41 from the bottom, where the
     * chat's lowest line ends; from beta 1.8 the hearts and hunger over the experience bar, armor and air over them, up
     * to 49), in the HUD panel's pixels; and the slot the right hand touches there selected.
     */
    private static void wristHud(Minecraft minecraft) {
        //#if >=b1.4_01
        Window window = new Window(minecraft.options, minecraft.width, minecraft.height);
        //#else
        Window window = new Window(minecraft.width, minecraft.height);
        //#endif
        //#if >=1.3.1
        int w = window.getWidth(), h = window.getHeight(), scale = window.getScale();
        //#else
        int w = window.getWidth(), h = window.getHeight(), scale = window.scale;
        //#endif
        //#if >=b1.8.1
        int rows = 49;
        //#else
        int rows = 41;
        //#endif
        VR.setWristHud((w / 2 - 92) * scale, (h - rows) * scale, (w / 2 + 92) * scale, Math.min(h * scale, minecraft.height),
                (w / 2 - 90) * scale, 20 * scale, (h - 22) * scale);
        int touched = VR.touchedSlot();
        if (touched >= 0 && touched != lastTouched && minecraft.player != null && minecraft.screen == null) {
            minecraft.player.inventory.selectedSlot = touched;
        }
        lastTouched = touched;
    }

    /** The screens the game types text in: the VR keyboard shows with them. */
    static boolean wantsText(Screen screen) {
        //#if >=b1.3_01
        if (screen instanceof CreateWorldScreen || screen instanceof EditWorldScreen) return true;
        //#endif
        //#if >=b1.8.1
        if (screen instanceof AddServerScreen || screen instanceof DirectConnectScreen) return true;
        //#else
        if (screen instanceof JoinMultiplayerScreen) return true;
        //#endif
        return screen instanceof ChatScreen || screen instanceof SignEditScreen;
    }

    /** GameGui.render (the HUD), from GameRenderer.render */
    public static void renderHud(GameGui gui, float tickDelta, boolean screenOpen, int mouseX, int mouseY) {
        //#if <b1.3_01
        // no option of the game's hides the HUD: the radial menu's
        if (hudHidden && VR.enabled()) return;
        //#endif
        VR.beginGui();
        gui.render(tickDelta, screenOpen, mouseX, mouseY);
        VR.endGui();
    }

    /** Screen.render (a menu), from GameRenderer.render */
    public static void renderScreen(Screen screen, int mouseX, int mouseY, float tickDelta) {
        VR.beginGui();
        screen.render(mouseX, mouseY, tickDelta);
        VR.endGui();
    }

    /** The HUD's crosshair (in GameGui.render) is left out: the right controller aims, its line shows where. */
    public static void drawGuiTexture(GameGui gui, int x, int y, int u, int v, int width, int height) {
        if (VR.enabled() && u == 0 && v == 0 && width == 16 && height == 16) return;
        gui.drawTexture(x, y, u, v, width, height);
    }

    // ---- the world ----

    /** GameRenderer.renderWorld, first: the player looks where the head does and has walked where it did. */
    //#if >=b1.6.4
    public static void renderWorldStart(GameRenderer renderer, float tickDelta, long limit) {
    //#else
    public static void renderWorldStart(GameRenderer renderer, float tickDelta) {
    //#endif
        nextEye = 0;
        rayValid = false;
        if (!VR.enabled()) return;
        PlayerEntity player = renderer.minecraft.player;
        if (player == null) return;
        walkRoom(player);
        float[] look = VR.headYawPitch();
        // the game's yaw goes on past 360: the nearest one to where it was
        float yaw = player.yaw + wrap(look[0] - player.yaw);
        player.yaw = player.lastYaw = yaw;
        player.pitch = player.lastPitch = Math.max(-90, Math.min(90, look[1]));
    }

    /** GameRenderer.renderWorld, last: the game draws into its own picture again. */
    //#if >=b1.6.4
    public static void renderWorldEnd(GameRenderer renderer, float tickDelta, long limit) {
    //#else
    public static void renderWorldEnd(GameRenderer renderer, float tickDelta) {
    //#endif
        if (VR.enabled()) VR.endEyes();
    }

    /** The headset's steps in the room since the last frame, walked by the player (blocked by walls as walking is). */
    private static void walkRoom(PlayerEntity player) {
        VRFrame frame = VR.frame();
        float x = frame.headPosition[0], z = frame.headPosition[2];
        float dx = x - anchorX, dz = z - anchorZ;
        boolean wasAnchored = anchored;
        anchorX = x;
        anchorZ = z;
        anchored = true;
        if (!wasAnchored || !frame.headTracked || player.vehicle != null || sleeping(player)) return;
        // a jump (the room recentred) is no step
        if ((dx == 0 && dz == 0) || dx * dx + dz * dz > 1) return;
        float[] step = VRMath.mul3(VR.room(), dx, 0, dz);
        double x0 = player.x, y0 = player.y, z0 = player.z;
        boolean onGround = player.onGround;
        player.move(step[0], 0, step[2]);
        // move() with no fall says it is not on the ground
        player.onGround = onGround;
        double mx = player.x - x0, my = player.y - y0, mz = player.z - z0;
        // where the frame is drawn from moves with it, not a tick later
        player.prevX += mx;
        player.lastX += mx;
        player.prevY += my;
        player.lastY += my;
        player.prevZ += mz;
        player.lastZ += mz;
    }

    /** The anaglyph option, in renderWorld: on in VR, its two passes are the eyes. */
    public static boolean stereo(GameOptions options) {
        return VR.enabled() || options.anaglyph;
    }

    /** renderWorld's colour masks: the anaglyph ones draw all colours in VR. */
    public static void colorMask(boolean red, boolean green, boolean blue, boolean alpha) {
        if (VR.enabled() && (red || green || blue)) GL11.glColorMask(true, true, true, true);
        else GL11.glColorMask(red, green, blue, alpha);
    }

    /** renderWorld's viewport, at the start of each pass: the pass draws into its eye. */
    public static void viewport(int x, int y, int width, int height) {
        if (nextEye < 2 && VR.beginEye(nextEye)) {
            nextEye++;
            return;
        }
        GL11.glViewport(x, y, width, height);
    }

    /** renderWorld's clears: not the depth before the hand, which in VR is in the world. */
    public static void clear(int mask) {
        if (mask == GL11.GL_DEPTH_BUFFER_BIT && VR.enabled() && VR.currentEye() >= 0) return;
        GL11.glClear(mask);
    }

    /** The game's camera, then the eye's: its projection and where it is, from the player's eyes. */
    public static void setupCamera(GameRenderer renderer, float tickDelta, int pass) {
        renderer.setupCamera(tickDelta, pass);
        int eye = VR.currentEye();
        if (!VR.enabled() || eye < 0) return;
        loadEye(eye, renderer.minecraft);
    }

    private static void loadEye(int eye, Minecraft minecraft) {
        // the game's own far plane
        float far = (256 >> minecraft.options.viewDistance) * 2.0F;
        GL11.glMatrixMode(GL11.GL_PROJECTION);
        load(VR.eyeProjection(eye, 0.05F, far));
        GL11.glMatrixMode(GL11.GL_MODELVIEW);
        load(VR.eyeView(eye));
    }

    private static void load(float[] m) {
        matrix.clear();
        matrix.put(m).flip();
        GL11.glLoadMatrix(matrix);
    }

    // ---- aiming ----

    /** What the player aims at (Minecraft.crosshairTarget): where the right controller points, in VR. */
    public static void pick(GameRenderer renderer, float tickDelta) {
        Minecraft minecraft = renderer.minecraft;
        VRFrame frame = VR.frame();
        if (!VR.enabled() || camera(minecraft) == null || minecraft.world == null || !frame.handTracked[1]) {
            renderer.pick(tickDelta);
            return;
        }
        // the radial menu acts on what was aimed at when it opened: that stays outlined
        if (minecraft.screen instanceof RadialScreen) {
            minecraft.crosshairTarget = radialTarget;
            rayValid = false;
            return;
        }
        Entity camera = camera(minecraft);
        double cx = camera.prevX + (camera.x - camera.prevX) * tickDelta;
        double cy = camera.prevY + (camera.y - camera.prevY) * tickDelta;
        double cz = camera.prevZ + (camera.z - camera.prevZ) * tickDelta;
        float[] hand = VR.toWorld(frame.aimPosition[1]);
        float[] direction = VR.directionToWorld(frame.aimOrientation[1], 0, 0, -1);
        double reach = minecraft.interactionManager.getReach();
        //#if >=1.3.1
        Vec3d from = Vec3d.of(cx + hand[0], cy + hand[1], cz + hand[2]);
        //#else
        Vec3d from = Vec3d.fromPool(cx + hand[0], cy + hand[1], cz + hand[2]);
        //#endif
        Vec3d to = from.add(direction[0] * reach, direction[1] * reach, direction[2] * reach);
        HitResult hit = minecraft.world.rayTrace(from, to);
        minecraft.crosshairTarget = hit;

        // entities, as the game looks for them: nearer than the block, within 3 blocks (more in creative: 32, from 1.3
        // 6; before beta 1.8 creative aimed at no entity)
        double distance = hit != null ? hit.facePos.distanceTo(from) : reach;
        //#if >=1.3.1
        boolean creative = false;
        double entityReach = Math.min(distance, minecraft.interactionManager.hasExtendedReach() ? 6 : 3);
        //#elif >=b1.8.1
        boolean creative = false;
        double entityReach = minecraft.interactionManager.hasExtendedReach() ? 32 : Math.min(distance, 3);
        //#else
        boolean creative = minecraft.interactionManager instanceof CreativeInteractionManager;
        double entityReach = creative ? 32 : Math.min(distance, 3);
        //#endif
        Vec3d end = from.add(direction[0] * entityReach, direction[1] * entityReach, direction[2] * entityReach);
        Entity target = null;
        double nearest = 0;
        Box around = camera.shape.expanded(direction[0] * entityReach, direction[1] * entityReach, direction[2] * entityReach).grown(2, 2, 2);
        List entities = minecraft.world.getEntities(camera, around);
        for (Object each : entities) {
            Entity entity = (Entity) each;
            if (!entity.hasCollision()) continue;
            //#if >=a1.2.0_02
            float radius = entity.getPickRadius();
            //#else
            float radius = 0.1F;
            //#endif
            Box box = entity.shape.grown(radius, radius, radius);
            HitResult clip = box.clip(from, end);
            if (contains(box, from)) {
                if (nearest >= 0) {
                    target = entity;
                    nearest = 0;
                }
            } else if (clip != null) {
                double d = from.distanceTo(clip.facePos);
                if (d < nearest || nearest == 0) {
                    target = entity;
                    nearest = d;
                }
            }
        }
        if (target != null && !creative) {
            minecraft.crosshairTarget = new HitResult(target);
            distance = nearest;
        }

        // the line: from the controller to what it hits, or as far as it reaches
        double length = minecraft.crosshairTarget != null ? Math.min(distance, reach) : reach;
        for (int i = 0; i < 3; i++) {
            rayStart[i] = hand[i];
            rayEnd[i] = (float) (hand[i] + direction[i] * length);
        }
        rayValid = true;
    }

    // ---- the hand, and the panels ----

    /** After the world in an eye (where the game draws its hand): the held item on the controller, the line, the panels. */
    public static void renderItemInHand(GameRenderer renderer, float tickDelta, int pass) {
        int eye = VR.currentEye();
        if (!VR.enabled() || eye < 0) {
            renderer.renderItemInHand(tickDelta, pass);
            return;
        }
        Minecraft minecraft = renderer.minecraft;
        loadEye(eye, minecraft);
        PlayerEntity player = minecraft.player;
        // the HUD option (F1, the radial menu) hides the HUD only: the hands and their line stay
        if (player != null && !sleeping(player)) {
            hands(renderer, minecraft, player, tickDelta);
            if (!VR.menuOpen()) line();
        }
        VR.drawPanels(eye);
        //#if >=1.5 && <1.6.1
        // the game's TextureManager keeps the texture it bound last: it was not
        minecraft.textureManager.clear();
        //#endif
    }

    /**
     * How big the arms are drawn: the game's 12 pixel arm, 0.375 m; how far it slopes down from the hand to the elbow
     * (the wrist HUD lies on the left one: VR.WRIST_SLOPE, WRIST_ABOVE)
     */
    static final float ARM_SCALE = 0.5F, ARM_SLOPE = 40;

    /** The player's arms at the controllers (skin and model of their own), the selected item in the right hand. */
    private static void hands(GameRenderer renderer, Minecraft minecraft, PlayerEntity player, float tickDelta) {
        VRFrame frame = VR.frame();
        int px = MathHelper.floor(player.x), py = MathHelper.floor(player.y), pz = MathHelper.floor(player.z);
        //#if >=b1.8.1
        // lit by the lightmap, as the game's hand is: the item drawing leaves the light to it (blocks at full brightness)
        int headLight = minecraft.world.getLightColor(px, py, pz, 0);
        renderer.enableLightMap(tickDelta);
        //#else
        float headLight = minecraft.world.getBrightness(px, py, pz);
        //#endif
        float swing = player.getAttackAnimationProgress(tickDelta);
        ItemStack stack = player.inventory.getSelectedItem();
        // the lights in the world, as the game's world has them (the eye's view is loaded)
        Lighting.turnOn();
        // a little more ambient, a little less from the lights: a held block's faces as bright as the same block's in
        // the world (top 1, sides 0.8 and 0.5 to 0.7, bottom 0.5 rather than the game's 0.4 for its hand, whose bottom
        // never shows on a screen)
        GL11.glLightModel(GL11.GL_LIGHT_MODEL_AMBIENT, vector(0.5F));
        GL11.glLight(GL11.GL_LIGHT0, GL11.GL_DIFFUSE, vector(0.55F));
        GL11.glLight(GL11.GL_LIGHT1, GL11.GL_DIFFUSE, vector(0.55F));
        double cx = player.prevX + (player.x - player.prevX) * tickDelta;
        double cy = player.prevY + (player.y - player.prevY) * tickDelta;
        double cz = player.prevZ + (player.z - player.prevZ) * tickDelta;
        for (int hand = 0; hand < 2; hand++) {
            if (!frame.handTracked[hand]) continue;
            float[] at = VR.toWorld(frame.gripPosition[hand]);
            // lit as the place the hand is (no darker than the head's: a hand in a wall)
            int hx = MathHelper.floor(cx + at[0]), hy = MathHelper.floor(cy + at[1]), hz = MathHelper.floor(cz + at[2]);
            //#if >=b1.8.1
            int here = minecraft.world.getLightColor(hx, hy, hz, 0);
            // the sky's and the blocks' light (4 bits each, at bits 20 and 4), the brighter of each
            int lightColor = Math.max(headLight >> 20 & 15, here >> 20 & 15) << 20 | Math.max(headLight >> 4 & 15, here >> 4 & 15) << 4;
            //#if >=1.0
            GLX.multiTexCoord2f(GLX.GL_TEXTURE1, lightColor % 65536, lightColor / 65536);
            //#else
            GL13.glMultiTexCoord2f(GL13.GL_TEXTURE1, lightColor % 65536, lightColor / 65536);
            //#endif
            float light = 1;
            //#else
            float light = Math.max(headLight, minecraft.world.getBrightness(hx, hy, hz));
            //#endif
            // the game's item drawing turns normal rescaling off (flat items): on again for each hand
            GL11.glEnable(32826); // GL_RESCALE_NORMAL
            float[] q = frame.gripOrientation[hand];
            GL11.glPushMatrix();
            GL11.glTranslatef(at[0], at[1], at[2]);
            multiply(VRMath.mul33(VR.room(), VRMath.quatToMatrix(q[0], q[1], q[2], q[3])));
            // a hit swings the right hand down
            if (hand == 1) GL11.glRotatef(-MathHelper.sin((float) Math.sqrt(swing) * (float) Math.PI) * 50, 1, 0, 0);
            arm(minecraft, player, hand, light);
            if (hand == 1 && stack != null) item(renderer, player, stack, light);
            GL11.glPopMatrix();
        }
        GL11.glDisable(32826);
        Lighting.turnOff();
        //#if >=b1.8.1
        renderer.disableLightMap(tickDelta);
        //#endif
    }

    /** One of the player's arms in the controller's frame: the hand at the grip, the arm running back along it. */
    private static void arm(Minecraft minecraft, PlayerEntity player, int hand, float light) {
        PlayerRenderer playerRenderer = (PlayerRenderer) EntityRenderDispatcher.INSTANCE.getRenderer(player);
        HumanoidModel model = playerRenderer.player;
        ModelPart part = hand == 1 ? model.rightArm : model.leftArm;
        // the whole skin from Mojang when there is one (its own left arm, slim arms, the sleeve), else the game's
        SkinModel.Skin skin = SkinModel.skin(name(player));
        if (skin != null) GL11.glBindTexture(GL11.GL_TEXTURE_2D, skin.texture);
        //#if >=1.6.1
        else minecraft.getTextureManager().bind(((ClientPlayerEntity) player).getSkinTextureLocation());
        //#elif >=1.5
        else GL11.glBindTexture(GL11.GL_TEXTURE_2D, minecraft.textureManager.loadHttpTexture(player.skin, player.getTexture()));
        //#else
        else minecraft.textureManager.bind(minecraft.textureManager.loadHttpTexture(player.skin, player.getTexture()));
        //#endif
        GL11.glColor4f(light, light, light, 1);
        GL11.glPushMatrix();
        // the model's arm hangs along +y, from the shoulder to the hand: that is ahead along the controller (-z),
        // the elbow lower than the hand
        GL11.glRotatef(-90 + ARM_SLOPE, 1, 0, 0);
        GL11.glScalef(ARM_SCALE, ARM_SCALE, ARM_SCALE);
        // its hand end (12 pixels under the shoulder, 6 to the side) at the grip
        GL11.glTranslatef((hand == 1 ? 6 : -6) / 16F, -12 / 16F, 0);
        // the shoulder where the model has it at rest: the shared model's pivot moves with the last player it posed
        float pivotX = hand == 1 ? -5 : 5, pivotY = 2;
        if (skin != null) {
            SkinModel.part(skin, hand == 1 ? SkinModel.RIGHT_ARM : SkinModel.LEFT_ARM, pivotX, pivotY, 0, 0, 0, 0, 1 / 16F, true, true);
        } else {
            float x = part.x, y = part.y, z = part.z, rotationX = part.rotationX, rotationY = part.rotationY, rotationZ = part.rotationZ;
            part.x = pivotX;
            part.y = pivotY;
            part.z = 0;
            part.rotationX = part.rotationY = part.rotationZ = 0;
            part.render(1 / 16F);
            part.x = x;
            part.y = y;
            part.z = z;
            part.rotationX = rotationX;
            part.rotationY = rotationY;
            part.rotationZ = rotationZ;
        }
        GL11.glPopMatrix();
    }

    /** The selected item in the right hand: held in the fist, pointing along the controller. */
    private static void item(GameRenderer renderer, PlayerEntity player, ItemStack stack, float light) {
        GL11.glPushMatrix();
        GL11.glRotatef(-90, 1, 0, 0);
        GL11.glTranslatef(0, -0.04F, 0);
        float scale = stack.id < 256 ? 0.2F : 0.35F;
        GL11.glScalef(scale, scale, scale);
        //#if >=a1.2.0_02
        if (stack.getItem().shouldRotate()) GL11.glRotatef(180, 0, 1, 0);
        //#endif
        //#if >=1.1
        // a layered item (potions, dyed armour, spawn eggs): its second layer coloured as the game colours it
        Item item = Item.BY_ID[stack.id];
        color(displayColor(item, stack, 0), light);
        renderer.itemInHandRenderer.render(player, stack, 0);
        if (item.isLayered()) {
            color(displayColor(item, stack, 1), light);
            renderer.itemInHandRenderer.render(player, stack, 1);
        }
        //#elif >=1.0
        color(Item.BY_ID[stack.id].getDisplayColor(stack.getMetadata()), light);
        renderer.itemInHandRenderer.render(player, stack, 0);
        if (stack.id == Item.POTION.id) {
            color(0xFFFFFF, light);
            renderer.itemInHandRenderer.render(player, stack, 1);
        }
        //#elif >=b1.6.4
        color(Item.BY_ID[stack.id].getDisplayColor(stack.getMetadata()), light);
        renderer.itemInHandRenderer.render(player, stack);
        //#elif >=b1.5_01
        color(Item.BY_ID[stack.id].getDisplayColor(stack.getMetadata()), light);
        renderer.itemInHandRenderer.render(stack);
        //#else
        color(0xFFFFFF, light);
        renderer.itemInHandRenderer.render(stack);
        //#endif
        GL11.glPopMatrix();
    }

    //#if >=1.1
    /** An item's colour for one of its layers. */
    private static int displayColor(Item item, ItemStack stack, int layer) {
        //#if >=1.4.2
        return item.getDisplayColor(stack, layer);
        //#else
        return item.getDisplayColor(stack.getMetadata(), layer);
        //#endif
    }
    //#endif

    /** The colour an item is drawn with (0xRRGGBB), lit. */
    private static void color(int color, float light) {
        GL11.glColor4f(light * (color >> 16 & 255) / 255f, light * (color >> 8 & 255) / 255f, light * (color & 255) / 255f, 1);
    }

    /** The right controller's ray: a thin line to what it aims at. */
    private static void line() {
        if (!rayValid) return;
        GL11.glPushAttrib(GL11.GL_ENABLE_BIT | GL11.GL_CURRENT_BIT | GL11.GL_LINE_BIT | GL11.GL_COLOR_BUFFER_BIT);
        GL11.glDisable(GL11.GL_TEXTURE_2D);
        GL11.glDisable(GL11.GL_LIGHTING);
        GL11.glDisable(GL11.GL_FOG);
        GL11.glDisable(GL11.GL_ALPHA_TEST);
        GL11.glEnable(GL11.GL_BLEND);
        GL11.glBlendFunc(GL11.GL_SRC_ALPHA, GL11.GL_ONE_MINUS_SRC_ALPHA);
        GL11.glLineWidth(2);
        GL11.glColor4f(1, 1, 1, 0.45F);
        GL11.glBegin(GL11.GL_LINES);
        GL11.glVertex3f(rayStart[0], rayStart[1], rayStart[2]);
        GL11.glVertex3f(rayEnd[0], rayEnd[1], rayEnd[2]);
        GL11.glEnd();
        GL11.glPopAttrib();
    }

    private static final FloatBuffer vector = BufferUtils.createFloatBuffer(16);

    /** (v, v, v, 1) for glLight / glLightModel */
    private static FloatBuffer vector(float v) {
        vector.clear();
        vector.put(v).put(v).put(v).put(1).flip();
        return vector;
    }

    /** Multiplies the model-view matrix by a rotation (3x3, row major). */
    private static void multiply(float[] r) {
        matrix.clear();
        for (int column = 0; column < 4; column++) {
            for (int row = 0; row < 4; row++) {
                matrix.put(row < 3 && column < 3 ? r[row * 3 + column] : row == column ? 1 : 0);
            }
        }
        matrix.flip();
        GL11.glMultMatrix(matrix);
    }

    // ---- today's skins on the old model ----

    /** The player PlayerRenderer.render is drawing, the model it draws with and the player's skin from Mojang (or null) */
    private static HumanoidModel drawnModel;
    private static SkinModel.Skin drawnSkin;

    /** PlayerRenderer.render, first: whose skin the model gets. */
    //#if >=1.6.1
    public static void playerStart(PlayerRenderer renderer, ClientPlayerEntity player, double x, double y, double z, float yaw, float tickDelta) {
    //#else
    public static void playerStart(PlayerRenderer renderer, PlayerEntity player, double x, double y, double z, float yaw, float tickDelta) {
    //#endif
        drawnModel = renderer.player;
        drawnSkin = SkinModel.skin(name(player));
    }

    /** PlayerRenderer.render, last. */
    //#if >=1.6.1
    public static void playerEnd(PlayerRenderer renderer, ClientPlayerEntity player, double x, double y, double z, float yaw, float tickDelta) {
    //#else
    public static void playerEnd(PlayerRenderer renderer, PlayerEntity player, double x, double y, double z, float yaw, float tickDelta) {
    //#endif
        drawnModel = null;
        drawnSkin = null;
    }

    /**
     * HumanoidModel.render, first: for a player with a skin from Mojang, the game does not draw the parts it would
     * draw from the wrong place (the left arm and leg: the right ones mirrored; a slim right arm: 4 pixels wide).
     */
    //#if >=b1.8.1
    public static void modelStart(HumanoidModel model, Entity entity, float walk, float walkSpeed, float bob, float yaw, float pitch, float scale) {
    //#else
    public static void modelStart(HumanoidModel model, float walk, float walkSpeed, float bob, float yaw, float pitch, float scale) {
    //#endif
        if (drawnSkin == null || model != drawnModel) return;
        model.leftArm.visible = false;
        model.leftLeg.visible = false;
        if (drawnSkin.slim) model.rightArm.visible = false;
    }

    /** HumanoidModel.render, last: those parts from the whole skin, and the outer layer over the body and limbs. */
    //#if >=b1.8.1
    public static void modelEnd(HumanoidModel model, Entity entity, float walk, float walkSpeed, float bob, float yaw, float pitch, float scale) {
    //#else
    public static void modelEnd(HumanoidModel model, float walk, float walkSpeed, float bob, float yaw, float pitch, float scale) {
    //#endif
        if (drawnSkin == null || model != drawnModel) return;
        model.leftArm.visible = true;
        model.leftLeg.visible = true;
        model.rightArm.visible = true;
        int bound = GL11.glGetInteger(GL11.GL_TEXTURE_BINDING_2D);
        GL11.glBindTexture(GL11.GL_TEXTURE_2D, drawnSkin.texture);
        SkinModel.Skin skin = drawnSkin;
        draw(skin, SkinModel.BODY, model.body, scale, false);
        draw(skin, SkinModel.RIGHT_ARM, model.rightArm, scale, skin.slim);
        draw(skin, SkinModel.LEFT_ARM, model.leftArm, scale, true);
        draw(skin, SkinModel.RIGHT_LEG, model.rightLeg, scale, false);
        draw(skin, SkinModel.LEFT_LEG, model.leftLeg, scale, true);
        GL11.glBindTexture(GL11.GL_TEXTURE_2D, bound);
    }

    /** A part of the whole skin where the game's part is (when the game shows it). */
    private static void draw(SkinModel.Skin skin, int which, ModelPart part, float scale, boolean base) {
        if (!part.visible || part.invisible) return;
        SkinModel.part(skin, which, part.x, part.y, part.z, part.rotationX, part.rotationY, part.rotationZ, scale, base, true);
    }

    // ---- the radial menu ----

    private static boolean bWasDown;
    /** What the right controller aimed at when B opened the radial menu: the menu panel takes the aim while it is open */
    private static HitResult radialTarget;

    /** B in the game opens the radial menu; letting it go picks the item the pointer is on (or none). */
    private static void radial(Minecraft minecraft) {
        boolean b = VR.frame().b;
        if (b && !bWasDown && minecraft.screen == null && minecraft.world != null && minecraft.player != null) {
            radialTarget = minecraft.crosshairTarget;
            minecraft.openScreen(new RadialScreen());
        } else if (!b && bWasDown && minecraft.screen instanceof RadialScreen) {
            // only what the pointer is on: letting go pointing elsewhere picks nothing (the game's cursor stays where
            // the pointer last was)
            int item = VR.pointing() ? ((RadialScreen) minecraft.screen).hovered() : -1;
            minecraft.openScreen(null);
            if (item >= 0) radialAction(minecraft, item);
        }
        bWasDown = b;
    }

    /** A radial menu item, the menu closed. */
    static void radialAction(Minecraft minecraft, int item) {
        PlayerEntity player = minecraft.player;
        if (player == null) return;
        switch (item) {
            case RadialScreen.DROP:
                drop(minecraft.player);
                break;
            case RadialScreen.CHAT:
                if (chat(minecraft)) minecraft.openScreen(new ChatScreen());
                break;
            case RadialScreen.ROTATE_RIGHT:
                VR.turn(1);
                break;
            case RadialScreen.ROTATE_LEFT:
                VR.turn(-1);
                break;
            case RadialScreen.PICK_BLOCK:
                if (radialTarget != null) {
                    HitResult target = minecraft.crosshairTarget;
                    minecraft.crosshairTarget = radialTarget;
                    //#if >=b1.8.1
                    minecraft.doPick();
                    //#else
                    minecraft.handlePickBlock();
                    //#endif
                    minecraft.crosshairTarget = target;
                }
                break;
            case RadialScreen.WALK:
                VRSettings.walkWithHead = !VRSettings.walkWithHead;
                VRSettings.save();
                break;
            case RadialScreen.HUD:
                toggleHud(minecraft);
                break;
            case RadialScreen.QUICK_TORCH:
                quickTorch(minecraft, player);
                break;
        }
        radialTarget = null;
    }

    /** A torch from the hotbar onto the block that was aimed at (the right click, with the torch selected for it). */
    private static void quickTorch(Minecraft minecraft, PlayerEntity player) {
        //#if >=b1.2
        if (radialTarget == null || radialTarget.type != HitResult__Type.BLOCK) return;
        //#else
        if (radialTarget == null || radialTarget.type != 0) return;
        //#endif
        int torch = -1;
        for (int slot = 0; slot < 9 && torch < 0; slot++) {
            ItemStack stack = player.inventory.items[slot];
            if (stack != null && stack.id == Block.TORCH.id) torch = slot;
        }
        if (torch < 0) return;
        int selected = player.inventory.selectedSlot;
        HitResult target = minecraft.crosshairTarget;
        player.inventory.selectedSlot = torch;
        minecraft.crosshairTarget = radialTarget;
        minecraft.handleMouseClick(1);
        minecraft.crosshairTarget = target;
        player.inventory.selectedSlot = selected;
    }

    // ---- VR's options ----

    /** The walk button's id: none of the Options screen's own (below 100, 100, 101, 200) */
    static final int WALK_BUTTON = 3001;

    /** OptionsScreen.init, last: VR's options, in the free place right of Difficulty. */
    @SuppressWarnings("unchecked")
    public static void optionsInit(OptionsScreen screen) {
        if (!VR.enabled()) return;
        screen.buttons.add(new ButtonWidget(WALK_BUTTON, screen.width / 2 + 5, screen.height / 6 + 48, 150, 20, VRSettings.walkLabel()));
    }

    /** OptionsScreen.buttonClicked, first: VR's buttons (the screen's own code leaves them alone). */
    public static void optionsClicked(OptionsScreen screen, ButtonWidget button) {
        if (button.id != WALK_BUTTON || !button.active) return;
        VRSettings.walkWithHead = !VRSettings.walkWithHead;
        VRSettings.save();
        button.message = VRSettings.walkLabel();
    }

    //#if <1.6.1
    // ---- the title screen's Quit Game ----

    /** The game's own Quit Game button's id (beta 1.0 to 1.5), and QuestCraft's: none of the title screen's own */
    static final int GAME_QUIT_BUTTON = 4, QUIT_BUTTON = 3002;

    /**
     * TitleScreen.init, last: when the title screen has no Quit Game (alpha has none; beta 1.0 to 1.5 leave it out as
     * an applet, which beta 1.0 to 1.2 always believe they are and 1.0 to 1.5 are run as), there is no way out on a
     * headset, which has no window to close. As the game's own: Options and Quit Game side by side where Options was.
     */
    @SuppressWarnings("unchecked")
    public static void titleInit(TitleScreen screen) {
        List buttons = screen.buttons;
        for (Object each : buttons) if (((ButtonWidget) each).id == GAME_QUIT_BUTTON) return;
        for (int i = 0; i < buttons.size(); i++) {
            ButtonWidget options = (ButtonWidget) buttons.get(i);
            if (options.id != 0) continue;
            buttons.set(i, new ButtonWidget(0, options.x, options.y, 98, 20, options.message));
            buttons.add(new ButtonWidget(QUIT_BUTTON, options.x + 102, options.y, 98, 20, "Quit Game"));
            return;
        }
    }

    /** TitleScreen.buttonClicked, first: Quit Game stops the game, as beta's does (the title screen leaves it alone). */
    public static void titleClicked(TitleScreen screen, ButtonWidget button) {
        if (button.id == QUIT_BUTTON && button.active) screen.minecraft.stop();
    }
    //#endif

    //#if <b1.3_01
    /**
     * Minecraft.run, last: until beta 1.3 the game's loop just ends (Quit Game, a crash) and the desktop applet's stop
     * or window close shut the game down and ended Java; a headset has neither, so it would be left with nothing to see
     * and no way out. As beta 1.3's loop: the game shuts down (sound, display) and Java ends.
     */
    public static void gameEnded(Minecraft minecraft) {
        try {
            minecraft.shutdown();
        } finally {
            System.exit(0);
        }
    }
    //#endif

    // ---- walking ----

    //#if >=1.2.5
    /** The player walking (KeyboardInput.tick has it no more) */
    private static PlayerEntity inputPlayer;
    //#endif

    /** KeyboardInput.tick, last: the left stick walks where the left controller points, or the head looks (the VR Walk option). */
    //#if >=1.2.5
    public static void input(KeyboardInput input) {
        PlayerEntity player = inputPlayer;
        if (!VR.enabled() || player == null) return;
    //#else
    public static void input(KeyboardInput input, PlayerEntity player) {
        if (!VR.enabled()) return;
    //#endif
        float forward = VR.moveForward(), sideways = VR.moveSideways();
        if (forward == 0 && sideways == 0) return;
        VRFrame frame = VR.frame();
        // with HMD the player's own yaw (the head's) is the way: the game's movement already goes there
        if (frame.handTracked[0] && !VRSettings.walkWithHead) {
            float[] direction = VR.directionToWorld(frame.aimOrientation[0], 0, 0, -1);
            double turn = Math.toRadians(VRMath.yawPitch(direction)[0] - player.yaw);
            float f = (float) (forward * Math.cos(turn) + sideways * Math.sin(turn));
            float s = (float) (sideways * Math.cos(turn) - forward * Math.sin(turn));
            forward = f;
            sideways = s;
        }
        if (input.sneaking) {
            forward *= 0.3F;
            sideways *= 0.3F;
        }
        input.movementForward = Math.max(-1, Math.min(1, input.movementForward + forward));
        input.movementSideways = Math.max(-1, Math.min(1, input.movementSideways + sideways));
    }

    // ---- what differs between versions ----

    /** The player's name. */
    static String name(PlayerEntity player) {
        //#if >=1.6.1
        return player.getName();
        //#else
        return player.name;
        //#endif
    }

    /** Whether the player is in a bed (none before beta 1.3). */
    static boolean sleeping(PlayerEntity player) {
        //#if >=b1.3_01
        return player.isSleeping();
        //#else
        return false;
        //#endif
    }

    /** What the world is seen from (the player, before beta 1.3 had a camera of its own). */
    static Entity camera(Minecraft minecraft) {
        //#if >=b1.3_01
        return minecraft.camera;
        //#else
        return minecraft.player;
        //#endif
    }

    /** Box.contains(Vec3d), as the game has it (alpha 1.1 has not). */
    static boolean contains(Box box, Vec3d p) {
        return p.x > box.minX && p.x < box.maxX && p.y > box.minY && p.y < box.maxY && p.z > box.minZ && p.z < box.maxZ;
    }

    //#if <b1.3_01
    /** The radial menu's HUD item (no option of the game's hides it before beta 1.3) */
    private static boolean hudHidden;
    //#endif

    /** Whether the HUD is hidden (F1, or the radial menu). */
    static boolean hudHidden(Minecraft minecraft) {
        //#if >=b1.3_01
        return minecraft.options.hideGui;
        //#else
        return hudHidden;
        //#endif
    }

    static void toggleHud(Minecraft minecraft) {
        //#if >=b1.3_01
        minecraft.options.hideGui = !minecraft.options.hideGui;
        //#else
        hudHidden = !hudHidden;
        //#endif
    }

    /** Whether there is a chat (multiplayer; from 1.3 single player too, its server being in the game). */
    static boolean chat(Minecraft minecraft) {
        //#if >=1.3.1
        return true;
        //#else
        return minecraft.isMultiplayer();
        //#endif
    }

    /** The selected item dropped (Q). */
    static void drop(PlayerEntity player) {
        //#if >=1.4.6
        player.dropItem(false);
        //#elif >=b1.0_01
        player.dropItem();
        //#else
        player.dropItem(player.inventory.removeItem(player.inventory.selectedSlot, 1), false);
        //#endif
    }

    static float wrap(float degrees) {
        degrees %= 360;
        if (degrees >= 180) degrees -= 360;
        if (degrees < -180) degrees += 360;
        return degrees;
    }
}
