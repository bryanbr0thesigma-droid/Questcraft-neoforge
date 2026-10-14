package questcraft.legacy;

import java.awt.Canvas;

/**
 * What the game gets in place of {@code java.awt.TextArea} (see {@link Transformer}), which a headless JVM cannot
 * make. The game only uses it for its crash screen: the text goes to the log instead, where the launcher shows it.
 */
public class HeadlessTextArea extends Canvas {
    private String text;

    public HeadlessTextArea(String text, int rows, int columns, int scrollbars) {
        this.text = text;
        System.out.println(text);
    }

    public HeadlessTextArea(String text) {
        this(text, 0, 0, 0);
    }

    public String getText() {
        return text;
    }

    public void setText(String text) {
        this.text = text;
        System.out.println(text);
    }

    public void setEditable(boolean editable) {
    }
}
