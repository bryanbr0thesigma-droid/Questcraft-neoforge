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
import pojlib.util.Constants;
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
                o.addProperty("image", instance.instanceImageURL);
                o.addProperty("mods", instance.extProjects == null ? 0 : instance.extProjects.length);
                array.add(o);
            }
        }
        return array.toString();
    }

    /** Creates an instance in the background. Poll {@link #progress()}; the error is in its "error" field. */
    public static void createInstance(String name, String minecraftVersion, String loader) {
        if (!begin("Installing " + name)) return;
        new Thread(() -> {
            try {
                if (name == null || name.trim().isEmpty() || name.contains("/") || name.contains("!")) {
                    throw new IOException("Instance names cannot be empty or contain / or !");
                }
                if (find(name) != null) throw new IOException("There already is an instance called " + name);
                API.createNewInstance(activity, instances, name, true, minecraftVersion, loader, null);
                finish("");
            } catch (Throwable t) {
                Logger.getInstance().appendToLog("Instance creation failed | " + t);
                finish(String.valueOf(t.getMessage() != null ? t.getMessage() : t));
            }
        }, "qc-create").start();
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
