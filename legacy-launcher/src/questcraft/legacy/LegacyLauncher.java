package questcraft.legacy;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.net.URL;
import java.util.ArrayList;
import java.util.List;
import java.util.jar.JarFile;
import java.util.zip.ZipEntry;

import questcraft.legacy.vr.VR;

/**
 * Starts Minecraft versions from before 1.6 (alpha, beta, 1.0 to 1.5.2) and 1.6.x on QuestCraft, where Mojang's
 * launcher used LaunchWrapper with Java 8 and an AWT window. Here:
 * <ul>
 * <li>the game runs in a class loader of its own ({@link GameClassLoader}, a URLClassLoader, as ModLoader adds mods to
 * it), which rewrites what Java 22 or a headless JVM do not allow as the classes load ({@link Transformer});</li>
 * <li>the game's own main() still runs, so each version sets itself up as it always did, its AWT window being a
 * stand-in that draws nothing ({@link HeadlessFrame});</li>
 * <li>the game's folder is set in its static field, as LaunchWrapper's VanillaTweakInjector did;</li>
 * <li>a version with VR glue in the launcher (vr/VERSION.jar: classes made against that version, and the hooks that
 * call them, {@link HookSpec}) is played in VR ({@link VR}): on the headset, and on a desktop with
 * {@code -Dquestcraft.legacy.vrsim=true}; {@code -Dquestcraft.legacy.vr=false} turns it off.</li>
 * </ul>
 * System properties: {@code questcraft.legacy.classpath} (the game's jars), {@code questcraft.legacy.main} (the class
 * whose main() starts the game: {@code net.minecraft.client.Minecraft} before 1.6, {@code net.minecraft.client.main.Main}
 * from 1.6), {@code questcraft.legacy.width}/{@code .height} (the game's window size). The arguments are Pojlib's
 * (--username, --gameDir, --assetsDir, --accessToken, --version ...); {@code questcraft.legacy.version} names the
 * version when --version does not.
 */
public final class LegacyLauncher {
    public static final String OLD_MAIN = "net.minecraft.client.Minecraft";

    private LegacyLauncher() {
    }

    public static void main(String[] args) throws Throwable {
        Arguments arguments = new Arguments(args);
        String classpath = System.getProperty("questcraft.legacy.classpath");
        if (classpath == null || classpath.isEmpty()) throw new IllegalStateException("questcraft.legacy.classpath is not set");
        String mainClass = System.getProperty("questcraft.legacy.main", OLD_MAIN);
        File gameDir = new File(arguments.get("--gameDir", "."));

        List<URL> gameUrls = urls(classpath);
        String version = System.getProperty("questcraft.legacy.version", arguments.get("--version", ""));
        File glue = wantsVR() ? glue(version) : null;
        if (glue != null) {
            gameUrls.add(glue.toURI().toURL());
            HookSpec.activate(HookSpec.parse(hooks(glue)), line -> VR.disable("this game is not the " + version + " the VR was made for"));
            VR.enable(Presenter.windowSize().width, Presenter.windowSize().height, Presenter.HEADSET);
            System.out.println("QuestCraft legacy launcher: " + version + " in VR");
        }
        GameClassLoader loader = new GameClassLoader(gameUrls.toArray(new URL[0]), LegacyLauncher.class.getClassLoader());
        Thread.currentThread().setContextClassLoader(loader);
        Class<?> main = Class.forName(mainClass, false, loader);
        Method mainMethod = main.getMethod("main", String[].class);
        System.out.println("QuestCraft legacy launcher: " + mainClass + " in " + gameDir);

        String[] gameArgs;
        if (OLD_MAIN.equals(mainClass)) {
            // before 1.6: main(username, session), the folder in a static field
            setGameDir(main, gameDir);
            gameArgs = new String[]{arguments.get("--username", "Player"), "-"};
        } else {
            gameArgs = new String[]{"--username", arguments.get("--username", "Player"), "--session", "-",
                    "--version", arguments.get("--version", "legacy"), "--gameDir", gameDir.getPath(),
                    "--assetsDir", arguments.get("--assetsDir", new File(gameDir, "assets").getPath()),
                    "--width", String.valueOf(Presenter.windowSize().width),
                    "--height", String.valueOf(Presenter.windowSize().height)};
        }
        mainMethod.invoke(null, (Object) gameArgs);
    }

    /** The static File field of the game's Minecraft class holds its folder (it is ~/.minecraft when unset). */
    static void setGameDir(Class<?> minecraft, File gameDir) throws IllegalAccessException {
        for (Field field : minecraft.getDeclaredFields()) {
            if (Modifier.isStatic(field.getModifiers()) && field.getType() == File.class) {
                field.setAccessible(true);
                field.set(null, gameDir);
                return;
            }
        }
        System.out.println("QuestCraft legacy launcher: no game folder field in " + minecraft.getName());
    }

    static List<URL> urls(String classpath) throws Exception {
        List<URL> urls = new ArrayList<>();
        for (String entry : classpath.split(File.pathSeparator)) {
            if (!entry.isEmpty()) urls.add(new File(entry).toURI().toURL());
        }
        return urls;
    }

    static boolean wantsVR() {
        if ("false".equals(System.getProperty("questcraft.legacy.vr"))) return false;
        return Presenter.HEADSET || Boolean.getBoolean("questcraft.legacy.vrsim");
    }

    /**
     * The version's VR glue, written out of the launcher next to it (the game's class loader reads it from a file), or
     * null when the version has none.
     */
    static File glue(String version) throws IOException {
        byte[] jar;
        try (InputStream in = LegacyLauncher.class.getResourceAsStream("/vr/" + version + ".jar")) {
            if (in == null) return null;
            jar = GameClassLoader.readAll(in);
        }
        File dir;
        try {
            dir = new File(LegacyLauncher.class.getProtectionDomain().getCodeSource().getLocation().toURI()).getParentFile();
        } catch (Exception e) {
            dir = null;
        }
        if (dir == null || !dir.canWrite()) dir = new File(System.getProperty("java.io.tmpdir"));
        File file = new File(dir, "questcraft-vr-" + version + ".jar");
        try (OutputStream out = new FileOutputStream(file)) {
            out.write(jar);
        }
        return file;
    }

    /** The glue's hooks.txt */
    static String hooks(File glue) throws IOException {
        try (JarFile jar = new JarFile(glue)) {
            ZipEntry entry = jar.getEntry("hooks.txt");
            if (entry == null) throw new IOException(glue + " has no hooks.txt");
            try (InputStream in = jar.getInputStream(entry)) {
                return new String(GameClassLoader.readAll(in), "UTF-8");
            }
        }
    }

    /** Pojlib's "--name value" arguments. */
    static final class Arguments {
        private final String[] args;

        Arguments(String[] args) {
            this.args = args;
        }

        String get(String name, String fallback) {
            for (int i = 0; i + 1 < args.length; i++) {
                if (args[i].equals(name)) return args[i + 1];
            }
            return fallback;
        }
    }
}
