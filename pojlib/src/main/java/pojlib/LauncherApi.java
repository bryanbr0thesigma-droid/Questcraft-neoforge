package pojlib;

import android.app.Activity;
import android.content.ClipboardManager;
import android.content.Context;
import android.util.DisplayMetrics;

import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;

import org.lwjgl.glfw.CallbackBridge;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.io.RandomAccessFile;

import pojlib.account.MinecraftAccount;
import pojlib.input.ClipboardHolder;
import pojlib.install.ModLoader;
import pojlib.install.VisorMods;
import pojlib.util.Constants;
import pojlib.util.download.DownloadUtils;
import pojlib.util.Logger;
import pojlib.util.json.MinecraftInstances;
import pojlib.util.json.ProjectInfo;

/**
 * What a launcher written in another engine (the Godot launcher) needs from Pojlib, without any Unity classes.
 * <p>
 * Everything takes and returns strings, numbers and booleans (lists and objects are JSON strings), so the engine's
 * Java bridge never has to hold on to Pojlib's own objects. Everything slow runs on a thread of its own; the caller
 * polls {@link #progress()} and {@link #loginState()}.
 */
public final class LauncherApi {
    private LauncherApi() {}

    private static final Gson GSON = new Gson();

    private static Activity activity;
    private static MinecraftInstances instances;

    // idle | working | ready | running | error
    private static volatile String phase = "idle";
    private static volatile String task = "";
    private static volatile String error = "";
    private static volatile String readyInstance = "";

    /** Call once with the app's activity before anything else. Returns "" or an error message. */
    public static synchronized String init(Activity host) {
        try {
            activity = host;
            Constants.initConstants(host);
            ClipboardHolder.clipboard = (ClipboardManager) host.getSystemService(Context.CLIPBOARD_SERVICE);

            DisplayMetrics metrics = host.getResources().getDisplayMetrics();
            CallbackBridge.physicalWidth = metrics.widthPixels;
            CallbackBridge.physicalHeight = metrics.heightPixels;
            CallbackBridge.nativeSetUseInputStackQueue(true);

            instances = API.loadAll();
            return "";
        } catch (Throwable t) {
            Logger.getInstance().appendToLog("LauncherApi.init failed | " + t);
            return String.valueOf(t);
        }
    }

    /** The memory the game gets, in MB. */
    public static void setMemory(String megabytes) {
        if (megabytes != null && megabytes.matches("\\d{3,5}")) {
            API.memoryValue = megabytes;
            API.customRAMValue = true;
        }
    }

    public static void setModel(String headsetName) {
        if (headsetName != null && !headsetName.isEmpty()) API.model = headsetName;
    }

    public static boolean hasConnection() {
        return activity != null && API.hasConnection(activity);
    }

    // ---- loaders and versions ----

    public static String modLoaders() {
        return GSON.toJson(API.getModLoaders());
    }

    /** The Minecraft versions one loader supports, from what was last downloaded (newest first). */
    public static String supportedVersions(String loader) {
        String[] versions = API.getCachedSupportedVersions(loader);
        return GSON.toJson(versions == null ? new String[0] : versions);
    }

    /** Downloads the version lists of all loaders in the background. */
    public static void refreshVersions() {
        new Thread(() -> {
            for (String loader : API.getModLoaders()) {
                try {
                    API.getQCSupportedVersions(activity, loader);
                } catch (Throwable t) {
                    Logger.getInstance().appendToLog("Could not update the version list of " + loader + " | " + t);
                }
            }
        }, "qc-versions").start();
    }

    public static String modrinthCategories(String instanceName) {
        MinecraftInstances.Instance instance = find(instanceName);
        String[] categories = instance == null ? new String[]{"fabric"} : instance.modrinthCategories();
        return GSON.toJson(categories);
    }

    // ---- instances ----

    public static synchronized String listInstances() {
        JsonArray array = new JsonArray();
        if (instances != null) {
            for (MinecraftInstances.Instance instance : instances.toArray()) {
                JsonObject o = new JsonObject();
                o.addProperty("name", instance.instanceName);
                o.addProperty("version", instance.versionName);
                o.addProperty("loader", instance.loaderName());
                o.addProperty("vr", instance.vrModName());
                o.addProperty("image", instance.instanceImageURL);
                o.addProperty("mods", instance.extProjects == null ? 0 : instance.extProjects.length);
                array.add(o);
            }
        }
        return array.toString();
    }

    /**
     * The VR mods an instance of this loader and Minecraft version can run: Vivecraft, and Visor where QuestCraft has a
     * build of it. ModLoader's versions have no VR mod: "VR", QuestCraft's own for the versions that have it (beta
     * 1.7.3), else "Screen", the game on a screen in VR.
     */
    public static String vrMods(String loader, String minecraftVersion) {
        JsonArray array = new JsonArray();
        ModLoader ml = ModLoader.fromName(loader);
        if (ml == ModLoader.RISUGAMI) {
            array.add(pojlib.install.RisugamiModLoader.hasVR(minecraftVersion) ? MinecraftInstances.Instance.VR : MinecraftInstances.Instance.SCREEN);
            return array.toString();
        }
        array.add("Vivecraft");
        if (ml != null && VisorMods.isAvailable(VisorMods.registry(), ml.id, minecraftVersion)) array.add("Visor");
        return array.toString();
    }

    public static void createInstance(String name, String minecraftVersion, String loader) {
        createInstance(name, minecraftVersion, loader, "Vivecraft");
    }

    /** Creates an instance in the background. Poll {@link #progress()}; the error is in its "error" field. */
    public static void createInstance(String name, String minecraftVersion, String loader, String vrMod) {
        if (!begin("Installing " + name)) return;
        new Thread(() -> {
            try {
                if (name == null || name.trim().isEmpty() || name.contains("/") || name.contains("!")) {
                    throw new IOException("Instance names cannot be empty or contain / or !");
                }
                if (find(name) != null) throw new IOException("There already is an instance called " + name);
                if ("visor".equalsIgnoreCase(vrMod) && !vrMods(loader, minecraftVersion).contains("Visor")) {
                    throw new IOException("There is no Visor for " + loader + " " + minecraftVersion);
                }
                MinecraftInstances.Instance created = API.createNewInstance(activity, instances, name, true, minecraftVersion, loader, null, vrMod);
                awaitCreation(created, InstanceHandler.creation, () -> find(name) != null, () -> InstanceHandler.lastCreateError);
                finish("");
            } catch (Throwable t) {
                Logger.getInstance().appendToLog("Instance creation failed | " + t);
                finish(String.valueOf(t.getMessage() != null ? t.getMessage() : t));
            }
        }, "qc-create").start();
    }

    /**
     * Makes an instance of a Modrinth modpack (a .mrpack file at {@code url}, e.g. from the Modrinth search) for one
     * loader, in the background like {@link #createInstance}: poll {@link #progress()}. The pack has to be for that
     * loader (a Fabric pack also runs on Quilt).
     */
    public static void installModpack(String name, String loader, String url, String imageUrl) {
        if (!begin("Installing " + name)) return;
        new Thread(() -> {
            java.io.File pack = null;
            try {
                if (name == null || name.trim().isEmpty() || name.contains("/") || name.contains("!")) {
                    throw new IOException("Instance names cannot be empty or contain / or !");
                }
                if (url == null || !url.startsWith("https://")) throw new IOException("The modpack has no download address");
                if (find(name) != null) throw new IOException("There already is an instance called " + name);
                pack = new java.io.File(Constants.USER_HOME + "/modpacks/" + System.currentTimeMillis() + ".mrpack");
                pack.getParentFile().mkdirs();
                task = "Downloading the modpack";
                DownloadUtils.downloadFile(url, pack);
                task = "Installing " + name;
                MinecraftInstances.Instance created = API.createNewInstance(activity, instances, name,
                        imageUrl == null || imageUrl.isEmpty() ? null : imageUrl, loader, pack.getAbsolutePath());
                awaitCreation(created, InstanceHandler.creation, () -> find(name) != null, () -> InstanceHandler.lastCreateError);
                finish("");
            } catch (Throwable t) {
                Logger.getInstance().appendToLog("Modpack install failed | " + t);
                finish(String.valueOf(t.getMessage() != null ? t.getMessage() : t));
            } finally {
                if (pack != null) pack.delete();
            }
        }, "qc-modpack").start();
    }

    /**
     * An instance is made in the background, it is done when that is. Fails if it was not made (the creation returned
     * nothing, or the instance is not there at the end, with the reason the creation gave if it gave one).
     */
    static void awaitCreation(MinecraftInstances.Instance created, java.util.concurrent.CompletableFuture<?> creation,
                              java.util.function.BooleanSupplier exists, java.util.function.Supplier<String> failure) throws Exception {
        if (created == null) {
            String why = failure.get();
            throw new IOException(why == null || why.isEmpty() ? "The instance could not be created (see the log)" : why);
        }
        if (creation != null) creation.get();
        if (!exists.getAsBoolean()) {
            String why = failure.get();
            throw new IOException(why == null || why.isEmpty() ? "The instance could not be created (see the log)" : why);
        }
    }

    public static synchronized boolean deleteInstance(String name) {
        MinecraftInstances.Instance instance = find(name);
        if (instance == null) return false;
        try {
            return API.deleteInstance(instances, instance);
        } catch (IOException e) {
            return false;
        }
    }

    // ---- mods ----

    public static synchronized String listMods(String instanceName) {
        JsonArray array = new JsonArray();
        MinecraftInstances.Instance instance = find(instanceName);
        if (instance != null && instance.extProjects != null) {
            for (ProjectInfo info : instance.extProjects) {
                JsonObject o = new JsonObject();
                o.addProperty("slug", info.slug);
                o.addProperty("version", info.version);
                o.addProperty("type", info.type);
                o.addProperty("file", info.fileName);
                array.add(o);
            }
        }
        return array.toString();
    }

    public static synchronized boolean hasMod(String instanceName, String slug) {
        MinecraftInstances.Instance instance = find(instanceName);
        return instance != null && instance.extProjects != null && API.hasExtraProject(instance, slug);
    }

    public static synchronized boolean addMod(String instanceName, String slug, String fileName, String version,
                                              String url, String type) {
        MinecraftInstances.Instance instance = find(instanceName);
        if (instance == null) return false;
        API.addExtraProject(instances, instance, slug, fileName, version, url, type);
        return true;
    }

    public static synchronized boolean removeMod(String instanceName, String slug) {
        MinecraftInstances.Instance instance = find(instanceName);
        return instance != null && API.removeExtraProject(instances, instance, slug);
    }

    // ---- accounts ----

    /** The accounts that signed in on this device: [{"name": ..., "uuid": ...}] */
    public static String listAccounts() {
        JsonArray array = new JsonArray();
        File dir = new File(activity.getFilesDir() + "/accounts");
        File[] files = dir.listFiles((d, n) -> n.endsWith(".json"));
        if (files != null) {
            for (File file : files) {
                String uuid = file.getName().substring(0, file.getName().length() - ".json".length());
                MinecraftAccount account = MinecraftAccount.load(dir.getPath(), uuid);
                if (account == null) continue;
                JsonObject o = new JsonObject();
                o.addProperty("name", account.username);
                o.addProperty("uuid", account.uuid);
                array.add(o);
            }
        }
        return array.toString();
    }

    /** Starts signing in; an empty uuid signs in with a new Microsoft account (device code). */
    public static void login(String uuid) {
        API.msaMessage = "";
        API.currentAcc = null;
        new Thread(() -> {
            try {
                API.login(activity, uuid == null || uuid.isEmpty() ? null : uuid);
            } catch (Throwable t) {
                API.msaMessage = "Sign in failed: " + t;
            }
        }, "qc-login").start();
    }

    /** {"loggedIn": bool, "name": ..., "uuid": ..., "image": ..., "demo": bool, "message": "go to ... and enter ..."} */
    public static String loginState() {
        JsonObject o = new JsonObject();
        boolean in = API.currentAcc != null;
        o.addProperty("loggedIn", in);
        o.addProperty("name", in ? API.profileName : "");
        o.addProperty("uuid", in ? API.profileUUID : "");
        o.addProperty("image", in && API.profileImage != null ? API.profileImage : "");
        o.addProperty("demo", API.isDemoMode);
        o.addProperty("message", API.msaMessage == null ? "" : API.msaMessage);
        return o.toString();
    }

    public static synchronized boolean removeAccount(String uuid) {
        return API.removeAccount(activity, uuid);
    }

    // ---- starting the game ----

    /**
     * Step 1 of starting: downloads and checks everything the instance needs. When it is done, {@link #progress()}
     * says "ready" and the launcher can give up the headset (end its OpenXR session) and call {@link #startGame()}.
     */
    public static void prepare(String instanceName) {
        MinecraftInstances.Instance instance = find(instanceName);
        if (instance == null) {
            error = "No instance called " + instanceName;
            phase = "error";
            return;
        }
        if (API.currentAcc == null) {
            error = "Sign in first";
            phase = "error";
            return;
        }
        if (!begin("Preparing " + instanceName)) return;
        new Thread(() -> {
            try {
                if (API.prelaunch(activity, instances, instance)) {
                    readyInstance = instanceName;
                    task = "Ready to start " + instanceName;
                    phase = "ready";
                } else {
                    finish("Prelaunch checks failed, see the log");
                }
            } catch (Throwable t) {
                Logger.getInstance().appendToLog("Prepare failed | " + t);
                finish(String.valueOf(t));
            }
        }, "qc-prepare").start();
    }

    /**
     * {@link #prepare} for a process that starts the game after the launcher's process downloaded and checked
     * everything: only sets this process up (Java runtime, libraries). Falls back to the full {@link #prepare} if that
     * does not work, so a missing file is still fetched.
     */
    public static void prepareLight(String instanceName) {
        MinecraftInstances.Instance instance = find(instanceName);
        if (instance == null || API.currentAcc == null || !begin("Preparing " + instanceName)) {
            prepare(instanceName);
            return;
        }
        new Thread(() -> {
            boolean ok = false;
            try {
                ok = API.prelaunchLight(activity, instance);
            } catch (Throwable t) {
                Logger.getInstance().appendToLog("Light prepare failed | " + t);
            }
            if (ok) {
                readyInstance = instanceName;
                task = "Ready to start " + instanceName;
                phase = "ready";
            } else {
                finish("");        // idle again, then the full check
                prepare(instanceName);
            }
        }, "qc-prepare-light").start();
    }

    /** Step 2: runs the game in this process. Does not return until the game ends, so it has a thread of its own. */
    public static void startGame() {
        MinecraftInstances.Instance instance = find(readyInstance);
        if (instance == null || !"ready".equals(phase)) return;
        phase = "running";
        task = "Running " + readyInstance;
        new Thread(() -> {
            try {
                API.launchInstance(activity, API.currentAcc, instance);
            } catch (Throwable t) {
                Logger.getInstance().appendToLog("Launch failed | " + t);
            }
        }, "qc-game").start();
    }

    /**
     * Like {@link #startGame()} but runs the game on the calling thread and returns when it ends (normally the
     * process ends first, a game ending by {@code System.exit} takes it with it). For a host that runs the game in
     * a process and activity of its own.
     */
    public static void runGameBlocking() {
        MinecraftInstances.Instance instance = find(readyInstance);
        if (instance == null || !"ready".equals(phase)) return;
        phase = "running";
        task = "Running " + readyInstance;
        try {
            API.launchInstance(activity, API.currentAcc, instance);
        } catch (Throwable t) {
            Logger.getInstance().appendToLog("Launch failed | " + t);
        }
    }

    /** Ends the game and starts the launcher again. */
    public static void restart() {
        API.restartLauncher(activity);
    }

    /** {"phase": idle|working|ready|running|error, "task": ..., "percent": 0-100, "error": ..., "gameReady": bool} */
    public static String progress() {
        JsonObject o = new JsonObject();
        o.addProperty("phase", phase);
        o.addProperty("task", task);
        o.addProperty("percent", API.getDownloadPercentage());
        o.addProperty("error", error);
        o.addProperty("gameReady", API.gameReady);
        return o.toString();
    }

    /** Clears an error so the launcher is idle again. */
    public static void acknowledge() {
        if ("error".equals(phase)) {
            error = "";
            phase = "idle";
        }
    }

    public static String logTail(int lines) {
        // only the end of the file: the log grows large and this is polled from the launcher's render thread
        try (RandomAccessFile file = new RandomAccessFile(new File(Constants.USER_HOME, "latestlog.txt"), "r")) {
            long length = file.length();
            int want = (int) Math.min(length, 16 * 1024);
            byte[] bytes = new byte[want];
            file.seek(length - want);
            file.readFully(bytes);
            String[] all = new String(bytes, StandardCharsets.UTF_8).split("\\r?\\n");
            int from = Math.max(0, all.length - Math.max(1, lines));
            return String.join("\n", java.util.Arrays.copyOfRange(all, from, all.length));
        } catch (Throwable t) {
            return "";
        }
    }

    /**
     * What is known about how the last game ended, for a launcher's log screen: how Android says the game process ended
     * (it is the only one that can tell a native crash from an out-of-memory kill), the crash file of the Java VM
     * and the log of the game, as far as there is a recent one. Empty if there is nothing.
     */
    public static String crashInfo() {
        StringBuilder out = new StringBuilder();
        try {
            android.content.Context context = Constants.APP_CONTEXT;
            android.app.ActivityManager am = (android.app.ActivityManager) context.getSystemService(android.content.Context.ACTIVITY_SERVICE);
            for (android.app.ApplicationExitInfo info : am.getHistoricalProcessExitReasons(context.getPackageName(), 0, 10)) {
                if (info.getProcessName() != null && info.getProcessName().endsWith(":game")) {
                    out.append("Android says the game process ended: reason ").append(info.getReason())
                            .append(" (").append(exitReasonName(info.getReason())).append("), status ").append(info.getStatus());
                    if (info.getDescription() != null) out.append(", ").append(info.getDescription());
                    out.append("\n");
                    break;
                }
            }
        } catch (Throwable t) {
            out.append("(exit reason not available: ").append(t).append(")\n");
        }
        try {
            File newestCrash = null;
            File newestLog = null;
            File[] dirs = new File(Constants.USER_HOME, "instances").listFiles();
            if (dirs != null) {
                for (File dir : dirs) {
                    File[] files = dir.listFiles();
                    if (files != null) {
                        for (File f : files) {
                            if (f.getName().startsWith("hs_err_pid") && (newestCrash == null || f.lastModified() > newestCrash.lastModified())) newestCrash = f;
                        }
                    }
                    File log = new File(dir, "logs/latest.log");
                    if (log.isFile() && (newestLog == null || log.lastModified() > newestLog.lastModified())) newestLog = log;
                }
            }
            File gameLog = new File(Constants.USER_HOME, "game_log.txt");
            if (gameLog.isFile()) {
                out.append("--- game_log.txt: Vivecraft, OpenXR and error lines ---\n").append(importantLines(gameLog, 30));
                out.append("--- game_log.txt: the last lines ---\n").append(tailOf(gameLog, 12));
            }
            File vmCrash = new File(Constants.USER_HOME, "hs_err_game.log");
            if (vmCrash.isFile()) {
                out.append("--- hs_err_game.log (Java VM crash report) ---\n").append(head(vmCrash, 24));
            }
            long recent = System.currentTimeMillis() - 30 * 60 * 1000L;
            if (newestCrash != null && newestCrash.lastModified() > recent) {
                out.append("--- ").append(newestCrash.getName()).append(" (Java VM crash report) ---\n")
                        .append(head(newestCrash, 24));
            }
            if (newestLog != null && newestLog.lastModified() > recent) {
                out.append("--- ").append(newestLog.getParentFile().getParentFile().getName()).append("/logs/latest.log ---\n")
                        .append(tailOf(newestLog, 25));
            }
        } catch (Throwable t) {
            out.append("(logs not available: ").append(t).append(")\n");
        }
        return out.toString();
    }

    private static String exitReasonName(int reason) {
        switch (reason) {
            case 1: return "exit by itself";
            case 2: return "signal";
            case 3: return "low memory";
            case 4: return "crash (Java)";
            case 5: return "crash (native)";
            case 6: return "ANR";
            case 7: return "initialization failure";
            case 8: return "permission change";
            case 9: return "excessive resource usage";
            case 10: return "user request";
            case 11: return "user stopped";
            case 12: return "dependency died";
            case 13: return "other";
            default: return "unknown";
        }
    }

    /** The lines of a log that tell why VR did or did not start (or what failed), the last {@code max} of them. */
    static String importantLines(File file, int max) throws java.io.IOException {
        try (RandomAccessFile f = new RandomAccessFile(file, "r")) {
            int want = (int) Math.min(f.length(), 96 * 1024);
            byte[] bytes = new byte[want];
            f.seek(f.length() - want);
            f.readFully(bytes);
            return importantLines(new String(bytes, StandardCharsets.UTF_8).split("\\r?\\n"), max);
        }
    }

    static String importantLines(String[] lines, int max) {
        java.util.regex.Pattern pattern = java.util.regex.Pattern.compile(
                "(?i)vivecraft|openxr|\\bxr\\b|stereo|ERROR|FATAL|Exception|Caused by|crash");
        java.util.List<String> kept = new java.util.ArrayList<>();
        for (String line : lines) {
            if (pattern.matcher(line).find() && !line.trim().startsWith("at ")) {
                kept.add(line.length() > 160 ? line.substring(0, 160) : line);
            }
        }
        return String.join("\n", kept.subList(Math.max(0, kept.size() - max), kept.size())) + "\n";
    }

    private static String head(File file, int lines) throws java.io.IOException {
        java.util.List<String> all = java.nio.file.Files.readAllLines(file.toPath(), StandardCharsets.UTF_8);
        return String.join("\n", all.subList(0, Math.min(lines, all.size()))) + "\n";
    }

    private static String tailOf(File file, int lines) throws java.io.IOException {
        try (RandomAccessFile f = new RandomAccessFile(file, "r")) {
            int want = (int) Math.min(f.length(), 8 * 1024);
            byte[] bytes = new byte[want];
            f.seek(f.length() - want);
            f.readFully(bytes);
            String[] all = new String(bytes, StandardCharsets.UTF_8).split("\\r?\\n");
            return String.join("\n", java.util.Arrays.copyOfRange(all, Math.max(0, all.length - lines), all.length)) + "\n";
        }
    }

    // ---- helpers ----

    private static synchronized MinecraftInstances.Instance find(String name) {
        if (instances == null || name == null) return null;
        for (MinecraftInstances.Instance instance : instances.toArray()) {
            if (name.equals(instance.instanceName)) return instance;
        }
        return null;
    }

    private static synchronized boolean begin(String what) {
        if ("working".equals(phase) || "running".equals(phase)) return false;
        phase = "working";
        task = what;
        error = "";
        return true;
    }

    private static void finish(String failure) {
        error = failure;
        phase = failure.isEmpty() ? "idle" : "error";
        task = "";
    }
}
