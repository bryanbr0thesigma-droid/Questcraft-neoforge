package questcraft.legacy;

import java.io.File;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.net.URL;
import java.util.ArrayList;
import java.util.List;

/**
 * Starts Minecraft versions from before 1.6 (alpha, beta, 1.0 to 1.5.2) and 1.6.x on QuestCraft, where Mojang's
 * launcher used LaunchWrapper with Java 8 and an AWT window. Here:
 * <ul>
 * <li>the game runs in a class loader of its own ({@link GameClassLoader}, a URLClassLoader, as ModLoader adds mods to
 * it), which rewrites what Java 22 or a headless JVM do not allow as the classes load ({@link Transformer});</li>
 * <li>the game's own main() still runs, so each version sets itself up as it always did, its AWT window being a
 * stand-in that draws nothing ({@link HeadlessFrame});</li>
 * <li>the game's folder is set in its static field, as LaunchWrapper's VanillaTweakInjector did.</li>
 * </ul>
 * System properties: {@code questcraft.legacy.classpath} (the game's jars), {@code questcraft.legacy.main} (the class
 * whose main() starts the game: {@code net.minecraft.client.Minecraft} before 1.6, {@code net.minecraft.client.main.Main}
 * from 1.6), {@code questcraft.legacy.width}/{@code .height} (the game's window size). The arguments are Pojlib's
 * (--username, --gameDir, --assetsDir, --accessToken, --version ...).
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

        GameClassLoader loader = new GameClassLoader(urls(classpath), LegacyLauncher.class.getClassLoader());
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

    static URL[] urls(String classpath) throws Exception {
        List<URL> urls = new ArrayList<>();
        for (String entry : classpath.split(File.pathSeparator)) {
            if (!entry.isEmpty()) urls.add(new File(entry).toURI().toURL());
        }
        return urls.toArray(new URL[0]);
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
