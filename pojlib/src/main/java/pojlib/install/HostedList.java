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

/**
 * A list kept in this repository as {@code pojlib/<file>}, with a copy in the app ({@code assets/loaders/<file>}): read
 * from the repository when there is a connection, else the last one read, else the app's copy.
 */
final class HostedList {
    private final String file;
    private final String cacheName;
    private volatile JsonObject cached;

    /**
     * @param file      its name in {@code pojlib/} and in the app's {@code loaders/}
     * @param cacheName the file it is kept in, in the launcher's folder
     */
    HostedList(String file, String cacheName) {
        this.file = file;
        this.cacheName = cacheName;
    }

    /** The list, or null if there is none at all. */
    JsonObject get() {
        File local = new File(Constants.USER_HOME + "/" + cacheName);
        try {
            DownloadUtils.downloadFile(Constants.PORT_RAW_URL + "/pojlib/" + file, local);
        } catch (Throwable offline) {
            if (cached != null) return cached;
        }
        try {
            if (!local.exists()) {
                byte[] bundled = FileUtil.loadFromAssetToByte(Constants.APP_CONTEXT, "loaders/" + file);
                FileUtil.write(local.getAbsolutePath(), bundled);
            }
            cached = JsonParser.parseString(new String(Files.readAllBytes(local.toPath()), StandardCharsets.UTF_8)).getAsJsonObject();
        } catch (Throwable t) {
            Logger.getInstance().appendToLog("Could not read " + file + ": " + t);
        }
        return cached;
    }

    /** The entry of a list's "versions" whose "name" is {@code minecraftVersion}, or null. */
    static JsonObject entry(JsonObject list, String minecraftVersion) {
        if (list == null || !list.has("versions") || minecraftVersion == null) return null;
        for (JsonElement version : list.getAsJsonArray("versions")) {
            JsonObject v = version.getAsJsonObject();
            if (minecraftVersion.equals(v.get("name").getAsString())) return v;
        }
        return null;
    }
}
