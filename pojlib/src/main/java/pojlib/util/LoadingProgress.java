package pojlib.util;

/**
 * How far Minecraft is with starting, read off its log. The loading screen of the game's activity shows it as a bar:
 * the JVM gives no progress of its own, but Minecraft and the mod loaders log the same landmarks in the same order on
 * every start, so the furthest landmark seen so far says roughly where it is.
 */
public final class LoadingProgress {
    /** Percentages of the steps before the log has anything to say (the game activity sets them itself). */
    public static final int SIGN_IN = 5;
    public static final int CHECK_FILES = 15;
    public static final int PREPARE_JAVA = 30;
    public static final int START_JAVA = 40;

    private static final Object[][] LANDMARKS = {
            // {text of the log line, percent, what the bar says}
            {"ModLauncher running", 45, "Loading mods"},
            {"Forge mod loading", 50, "Loading mods"},
            {"Loading Minecraft", 50, "Loading mods"},
            {"Loading for game Minecraft", 50, "Loading mods"},
            {"Setting user:", 60, "Starting Minecraft"},
            {"Backend library:", 65, "Setting up graphics"},
            {"Reloading ResourceManager", 75, "Loading resources"},
            {"Sound engine started", 85, "Starting sound"},
            {"textures/atlas/", 92, "Building textures"},
            {"textures/atlas/blocks.png-atlas", 96, "Almost there"},
    };

    public final int percent;
    public final String label;

    private LoadingProgress(int percent, String label) {
        this.percent = percent;
        this.label = label;
    }

    /** The furthest landmark in a piece of the game's log, or null if there is none yet. */
    public static LoadingProgress of(String log) {
        if (log == null || log.isEmpty()) return null;
        LoadingProgress best = null;
        for (Object[] landmark : LANDMARKS) {
            int percent = (Integer) landmark[1];
            if ((best == null || percent > best.percent) && log.contains((String) landmark[0])) {
                best = new LoadingProgress(percent, (String) landmark[2]);
            }
        }
        return best;
    }

    public static LoadingProgress step(int percent, String label) {
        return new LoadingProgress(percent, label);
    }

    /** The bar never goes back: log pieces are read one after the other and a later one may not repeat an old landmark. */
    public LoadingProgress orFurther(LoadingProgress other) {
        return other != null && other.percent > percent ? other : this;
    }
}
