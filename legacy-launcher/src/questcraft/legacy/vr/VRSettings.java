package questcraft.legacy.vr;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.Properties;

/**
 * VR's own options, changed in the game's Options screen (the glue adds the buttons) and kept in the game's folder,
 * questcraft-vr.txt.
 */
public final class VRSettings {
    static final String FILE = "questcraft-vr.txt";

    /** The left stick walks where the head looks (HMD), or where the left controller points */
    public static boolean walkWithHead;

    private static File file;

    private VRSettings() {
    }

    /** Reads the options of the game in {@code gameDir} (none yet: the defaults). */
    public static void load(File gameDir) {
        file = new File(gameDir, FILE);
        Properties properties = new Properties();
        if (file.isFile()) {
            try (InputStream in = new FileInputStream(file)) {
                properties.load(in);
            } catch (IOException e) {
                System.out.println("QuestCraft VR: could not read " + file + ": " + e);
            }
        }
        walkWithHead = "hmd".equalsIgnoreCase(properties.getProperty("walk", "controller").trim());
    }

    public static void save() {
        if (file == null) return;
        Properties properties = new Properties();
        properties.setProperty("walk", walkWithHead ? "hmd" : "controller");
        try (OutputStream out = new FileOutputStream(file)) {
            properties.store(out, "QuestCraft VR options (also in the game's Options screen)");
        } catch (IOException e) {
            System.out.println("QuestCraft VR: could not write " + file + ": " + e);
        }
    }

    /** The walk button's text */
    public static String walkLabel() {
        return "VR Walk: " + (walkWithHead ? "HMD" : "Controller");
    }
}
