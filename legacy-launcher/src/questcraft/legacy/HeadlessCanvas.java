package questcraft.legacy;

import java.awt.Component;

/**
 * What the game gets in place of {@code java.awt.Canvas} (see {@link Transformer}): a component without a native
 * window behind it. It becomes "displayable" when the frame it is in is shown ({@link HeadlessFrame#setVisible}),
 * which is when the game's applet canvas (1.0 to 1.5) starts the game thread.
 */
public class HeadlessCanvas extends Component {
    public HeadlessCanvas() {
    }

    @Override
    public void addNotify() {
    }

    @Override
    public void removeNotify() {
    }
}
