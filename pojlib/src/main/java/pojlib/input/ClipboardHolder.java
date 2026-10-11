package pojlib.input;

import android.content.ClipboardManager;

/** The system clipboard the game's copy/paste goes through. Set by whichever activity hosts the launcher. */
public final class ClipboardHolder {
    private ClipboardHolder() {}

    public static volatile ClipboardManager clipboard;
}
