package questcraft.legacy.vr.glue;

import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.ButtonWidget;

import questcraft.legacy.vr.VR;
import questcraft.legacy.vr.VRSettings;

/**
 * The radial menu (B, as QuestCraft's Vivecraft has it): eight buttons around the centre in Vivecraft's places, the
 * right controller points at one and letting go of B picks it (or the trigger clicks it). Vivecraft's items, as far as
 * beta 1.7.3 has them: drop, chat (multiplayer), rotate right, pick block, VR walk (in place of Vivecraft's movement
 * toggle: there is no teleport), the HUD (in place of the player list, which beta 1.7.3 does not have), rotate left,
 * quick torch. The game goes on behind it.
 */
public final class RadialScreen extends Screen {
    static final int DROP = 0, CHAT = 1, ROTATE_RIGHT = 2, PICK_BLOCK = 3, WALK = 4, HUD = 5, ROTATE_LEFT = 6, QUICK_TORCH = 7;
    static final int BUTTON_WIDTH = 120, BUTTON_HEIGHT = 20, DISTANCE = 48;

    private final int[][] rects = new int[8][];
    private int hovered = -1;

    @Override
    public void init() {
        buttons.clear();
        boolean multiplayer = minecraft.isMultiplayer();
        int cx = width / 2, cy = height / 2;
        for (int i = 0; i < 8; i++) {
            // Vivecraft's places, clockwise from the top
            int[][] offsets = {{0, -DISTANCE}, {BUTTON_WIDTH / 2 + 8, -DISTANCE / 2}, {BUTTON_WIDTH / 2 + 32, 0}, {BUTTON_WIDTH / 2 + 8, DISTANCE / 2},
                    {0, DISTANCE}, {-BUTTON_WIDTH / 2 - 8, DISTANCE / 2}, {-BUTTON_WIDTH / 2 - 32, 0}, {-BUTTON_WIDTH / 2 - 8, -DISTANCE / 2}};
            int x = cx + offsets[i][0] - BUTTON_WIDTH / 2, y = cy + offsets[i][1] - BUTTON_HEIGHT / 2;
            rects[i] = new int[]{x, y, BUTTON_WIDTH, BUTTON_HEIGHT};
            ButtonWidget button = new ButtonWidget(i, x, y, BUTTON_WIDTH, BUTTON_HEIGHT, label(i));
            if (i == CHAT && !multiplayer) button.active = false;
            buttons.add(button);
        }
    }

    private String label(int item) {
        switch (item) {
            case DROP:
                return "Drop";
            case CHAT:
                return "Chat";
            case ROTATE_RIGHT:
                return "Rotate Right";
            case PICK_BLOCK:
                return "Pick Block";
            case WALK:
                return VRSettings.walkLabel();
            case HUD:
                return minecraft.options.hideGui ? "Show HUD" : "Hide HUD";
            case ROTATE_LEFT:
                return "Rotate Left";
            default:
                return "Quick Torch";
        }
    }

    @Override
    protected void buttonClicked(ButtonWidget button) {
        if (!button.active) return;
        minecraft.openScreen(null);
        Hooks.radialAction(minecraft, button.id);
    }

    @Override
    public void render(int mouseX, int mouseY, float tickDelta) {
        hovered = -1;
        // off the panel the game's cursor is wherever the pointer last was: nothing is pointed at
        if (!VR.pointing()) {
            mouseX = -1000;
            mouseY = -1000;
        }
        for (int i = 0; i < 8; i++) {
            int[] r = rects[i];
            if (r != null && mouseX >= r[0] && mouseY >= r[1] && mouseX < r[0] + r[2] && mouseY < r[1] + r[3]
                    && ((ButtonWidget) buttons.get(i)).active) {
                hovered = i;
            }
        }
        super.render(mouseX, mouseY, tickDelta);
    }

    /** The item the pointer is on (-1 none): B let go picks it. */
    int hovered() {
        return hovered;
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }
}
