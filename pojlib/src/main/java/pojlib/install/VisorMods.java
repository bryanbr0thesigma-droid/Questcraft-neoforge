package pojlib.install;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;

import pojlib.util.Constants;
import pojlib.util.FileUtil;
import pojlib.util.Logger;
import pojlib.util.download.DownloadUtils;
import pojlib.util.json.ModsJson;
import pojlib.util.json.ProjectInfo;

/**
 * Visor, the other VR mod for Minecraft Java (next to Vivecraft). An instance runs one of them. QuestCraft's builds of
 * Visor for the headset are listed in {@code visor-mods.json}: for each Minecraft version, the jar of each loader (Quilt
 * runs the Fabric one). They take the place of the Vivecraft entry among an instance's core mods, see
 * {@link ModsJson.Version#coreModsFor}.
 */
public final class VisorMods {
    public static final String FILE = "visor-mods.json";

    private static volatile JsonObject cached;

    private VisorMods() {
    }

    /** Which jar a loader uses: Quilt runs Fabric mods. */
    static String jarLoader(String loaderId) {
        return "quilt".equals(loaderId) ? "fabric" : loaderId;
    }

    /** The Visor entry of one Minecraft version and loader in a registry, or null if there is none. */
    public static ProjectInfo[] coreMods(JsonObject registry, String loaderId, String minecraftVersion) {
        if (registry == null || !registry.has("versions")) return null;
        for (JsonElement version : registry.getAsJsonArray("versions")) {
            JsonObject v = version.getAsJsonObject();
            if (!minecraftVersion.equals(v.get("name").getAsString())) continue;
            JsonElement entry = v.get(jarLoader(loaderId));
            if (entry == null || !entry.isJsonObject()) return null;
            JsonObject o = entry.getAsJsonObject();
            ProjectInfo info = new ProjectInfo();
            info.slug = o.get("slug").getAsString();
            info.version = o.get("version").getAsString();
            info.download_link = o.get("download_link").getAsString();
            return new ProjectInfo[]{info};
        }
        return null;
    }

    /** Whether there is a Visor for this loader and version. */
    public static boolean isAvailable(JsonObject registry, String loaderId, String minecraftVersion) {
        return coreMods(registry, loaderId, minecraftVersion) != null;
    }

    /** Gives every version of a mod list its Visor entry for the loader, where there is one. */
    public static void attach(ModsJson mods, JsonObject registry, String loaderId) {
        if (mods == null || mods.versions == null) return;
        for (ModsJson.Version version : mods.versions) {
            version.visorCoreMods = coreMods(registry, loaderId, version.name);
        }
    }

    public static void attach(ModsJson mods, ModLoader loader) {
        attach(mods, registry(), loader.id);
    }

    /** The registry: downloaded when there is a connection, else the last one, else the copy in the app. Null if there is none at all. */
    public static JsonObject registry() {
        File file = new File(Constants.USER_HOME + "/visor_mods.json");
        try {
            DownloadUtils.downloadFile(Constants.PORT_RAW_URL + "/pojlib/" + FILE, file);
        } catch (Throwable offline) {
            if (cached != null) return cached;
        }
        try {
            if (!file.exists()) {
                byte[] bundled = FileUtil.loadFromAssetToByte(Constants.APP_CONTEXT, "loaders/" + FILE);
                FileUtil.write(file.getAbsolutePath(), bundled);
            }
            cached = JsonParser.parseString(new String(Files.readAllBytes(file.toPath()), StandardCharsets.UTF_8)).getAsJsonObject();
        } catch (Throwable t) {
            Logger.getInstance().appendToLog("No Visor list: " + t);
        }
        return cached;
    }
}
