package questcraft.legacy.vr.b173;

import java.nio.FloatBuffer;
import java.util.List;

import org.lwjgl.BufferUtils;
import org.lwjgl.opengl.GL11;

import net.minecraft.client.CreativeInteractionManager;
import net.minecraft.client.Minecraft;
import net.minecraft.client.entity.mob.player.KeyboardInput;
import net.minecraft.client.gui.GameGui;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.options.GameOptions;
import net.minecraft.client.render.GameRenderer;
import net.minecraft.client.render.platform.Lighting;
import net.minecraft.entity.Entity;
import net.minecraft.entity.mob.MobEntity;
import net.minecraft.entity.mob.player.PlayerEntity;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.HitResult;

import questcraft.legacy.vr.VR;
import questcraft.legacy.vr.VRFrame;
import questcraft.legacy.vr.VRMath;

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
        VR.setMenuOpen(minecraft.screen != null);
        // the camera is the headset's: there is no third person
        minecraft.options.perspective = false;
    }

    /** GameGui.render (the HUD), from GameRenderer.render */
    public static void renderHud(GameGui gui, float tickDelta, boolean screenOpen, int mouseX, int mouseY) {
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
    public static void renderWorldStart(GameRenderer renderer, float tickDelta, long limit) {
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
    public static void renderWorldEnd(GameRenderer renderer, float tickDelta, long limit) {
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
        if (!wasAnchored || !frame.headTracked || player.vehicle != null || player.isSleeping()) return;
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
        if (!VR.enabled() || minecraft.camera == null || minecraft.world == null || !frame.handTracked[1]) {
            renderer.pick(tickDelta);
            return;
        }
        MobEntity camera = minecraft.camera;
        double cx = camera.prevX + (camera.x - camera.prevX) * tickDelta;
        double cy = camera.prevY + (camera.y - camera.prevY) * tickDelta;
        double cz = camera.prevZ + (camera.z - camera.prevZ) * tickDelta;
        float[] hand = VR.toWorld(frame.aimPosition[1]);
        float[] direction = VR.directionToWorld(frame.aimOrientation[1], 0, 0, -1);
        double reach = minecraft.interactionManager.getReach();
        Vec3d from = Vec3d.fromPool(cx + hand[0], cy + hand[1], cz + hand[2]);
        Vec3d to = from.add(direction[0] * reach, direction[1] * reach, direction[2] * reach);
        HitResult hit = minecraft.world.rayTrace(from, to);
        minecraft.crosshairTarget = hit;

        // entities, as the game looks for them: nearer than the block, within 3 blocks (32 in creative)
        double distance = hit != null ? hit.facePos.distanceTo(from) : reach;
        boolean creative = minecraft.interactionManager instanceof CreativeInteractionManager;
        double entityReach = creative ? 32 : Math.min(distance, 3);
        Vec3d end = from.add(direction[0] * entityReach, direction[1] * entityReach, direction[2] * entityReach);
        Entity target = null;
        double nearest = 0;
        Box around = camera.shape.expanded(direction[0] * entityReach, direction[1] * entityReach, direction[2] * entityReach).grown(2, 2, 2);
        List entities = minecraft.world.getEntities(camera, around);
        for (Object each : entities) {
            Entity entity = (Entity) each;
            if (!entity.hasCollision()) continue;
            float radius = entity.getPickRadius();
            Box box = entity.shape.grown(radius, radius, radius);
            HitResult clip = box.clip(from, end);
            if (box.contains(from)) {
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
        if (player != null && !minecraft.options.hideGui && !player.isSleeping()) {
            heldItem(renderer, minecraft, player, tickDelta);
            if (!VR.menuOpen()) line();
        }
        VR.drawPanels(eye);
    }

    private static void heldItem(GameRenderer renderer, Minecraft minecraft, PlayerEntity player, float tickDelta) {
        VRFrame frame = VR.frame();
        ItemStack stack = player.inventory.getSelectedItem();
        if (stack == null || !frame.handTracked[1]) return;
        float[] at = VR.toWorld(frame.gripPosition[1]);
        float[] q = frame.gripOrientation[1];
        float[] rotation = VRMath.mul33(VR.room(), VRMath.quatToMatrix(q[0], q[1], q[2], q[3]));

        float light = minecraft.world.getBrightness(MathHelper.floor(player.x), MathHelper.floor(player.y), MathHelper.floor(player.z));
        int color = Item.BY_ID[stack.id].getDisplayColor(stack.getMetadata());
        GL11.glPushMatrix();
        // the lights in the world, as the game's world has them
        Lighting.turnOn();
        GL11.glTranslatef(at[0], at[1], at[2]);
        multiply(rotation);
        // a hit swings the hand down
        float swing = player.getAttackAnimationProgress(tickDelta);
        GL11.glRotatef(-MathHelper.sin((float) Math.sqrt(swing) * (float) Math.PI) * 50, 1, 0, 0);
        // held in the fist, pointing along the controller
        GL11.glRotatef(-90, 1, 0, 0);
        GL11.glTranslatef(0, -0.04F, 0);
        float scale = stack.id < 256 ? 0.2F : 0.35F;
        GL11.glScalef(scale, scale, scale);
        if (stack.getItem().shouldRotate()) GL11.glRotatef(180, 0, 1, 0);
        GL11.glColor4f(light * (color >> 16 & 255) / 255f, light * (color >> 8 & 255) / 255f, light * (color & 255) / 255f, 1);
        GL11.glEnable(32826); // GL_RESCALE_NORMAL
        renderer.itemInHandRenderer.render(player, stack);
        GL11.glDisable(32826);
        Lighting.turnOff();
        GL11.glPopMatrix();
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

    // ---- walking ----

    /** KeyboardInput.tick, last: the left stick walks where the left controller points. */
    public static void input(KeyboardInput input, PlayerEntity player) {
        if (!VR.enabled()) return;
        float forward = VR.moveForward(), sideways = VR.moveSideways();
        if (forward == 0 && sideways == 0) return;
        VRFrame frame = VR.frame();
        if (frame.handTracked[0]) {
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

    static float wrap(float degrees) {
        degrees %= 360;
        if (degrees >= 180) degrees -= 360;
        if (degrees < -180) degrees += 360;
        return degrees;
    }
}
