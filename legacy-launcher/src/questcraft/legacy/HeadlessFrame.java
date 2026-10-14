package questcraft.legacy;

import java.awt.Component;
import java.awt.Container;
import java.awt.Dimension;
import java.awt.Image;
import java.awt.Panel;
import java.awt.event.WindowListener;

/**
 * What the game gets in place of {@code java.awt.Frame} (see {@link Transformer}): a headless JVM makes no windows,
 * and on the headset the picture goes to OpenXR, not to a window. It is a panel, so the game can add its canvas,
 * applet and crash report to it; the window calls do nothing.
 * <p>
 * AWT lays windows out and makes their components "displayable" through native peers, which do not exist here, so
 * that is done by hand: everything put in the frame (or in a {@link HeadlessApplet}) gets the window size QuestCraft
 * wants (the game draws at its canvas's size), and canvases become displayable once the frame is shown, which is when
 * the applet versions of the game (1.0 to 1.5) start their game thread.
 */
public class HeadlessFrame extends Panel {
    private String title = "";
    private boolean shown;

    public HeadlessFrame() {
    }

    public HeadlessFrame(String title) {
        this.title = title;
    }

    public void pack() {
        size(this);
    }

    @Override
    protected void addImpl(Component component, Object constraints, int index) {
        super.addImpl(component, constraints, index);
        added(component);
    }

    @Override
    public void setVisible(boolean visible) {
        super.setVisible(visible);
        if (visible && !shown) {
            shown = true;
            size(this);
            notifyDisplayable(this);
        }
    }

    /** What {@code addImpl} of a stand-in container does with a component added to it. */
    static void added(Component component) {
        size(component);
        if (isShown(component)) notifyDisplayable(component);
    }

    static void size(Component component) {
        component.setSize(Presenter.windowSize());
        if (component instanceof Container) {
            for (Component child : ((Container) component).getComponents()) size(child);
        }
    }

    private static boolean isShown(Component component) {
        for (Component c = component; c != null; c = c.getParent()) {
            if (c instanceof HeadlessFrame && ((HeadlessFrame) c).shown) return true;
        }
        return false;
    }

    private static void notifyDisplayable(Component component) {
        if (component instanceof HeadlessCanvas) component.addNotify();
        if (component instanceof Container) {
            for (Component child : ((Container) component).getComponents()) notifyDisplayable(child);
        }
    }

    public void setLocationRelativeTo(Component component) {
    }

    public void addWindowListener(WindowListener listener) {
    }

    public void removeWindowListener(WindowListener listener) {
    }

    public void dispose() {
    }

    public void toFront() {
    }

    public void setTitle(String title) {
        this.title = title;
    }

    public String getTitle() {
        return title;
    }

    public void setIconImage(Image image) {
    }

    public void setResizable(boolean resizable) {
    }

    /** For {@link Presenter#windowSize()} callers that want it as a Dimension. */
    static Dimension windowSize() {
        return Presenter.windowSize();
    }
}
