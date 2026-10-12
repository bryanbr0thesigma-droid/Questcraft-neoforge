package pojlib.install;

import com.google.gson.Gson;

import pojlib.APIHandler;
import pojlib.util.Constants;

import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Looks up Forge versions. Forge's own site publishes which build is the newest and which the recommended one for
 * every Minecraft version ({@code promotions_slim.json}, keys like {@code 1.21.1-latest}).
 * <p>
 * A Forge build is written {@code <minecraft>-<forge>} on the maven ({@code 1.21.1-52.1.16}), the profile its
 * installer writes is called {@code <minecraft>-forge-<forge>}. The launcher only stores the Forge part with an
 * instance, the Minecraft version is stored next to it.
 */
public class ForgeMeta {

    private static final Pattern MC_VERSION = Pattern.compile("^1\\.(\\d+)(?:\\.(\\d+))?$");

    public static class Promotions {
        public Map<String, String> promos;
    }

    /** The newest Forge build for a Minecraft version ({@code "52.1.16"}), or null if there is none or no connection. */
    public static String getLatestVersion(String minecraftVersion) {
        String raw = APIHandler.getRaw(Constants.FORGE_PROMOTIONS_URL);
        if (raw == null) return null;
        Promotions promotions = new Gson().fromJson(raw, Promotions.class);
        return promotions == null ? null : selectLatest(promotions.promos, minecraftVersion);
    }

    /** The newest build in {@code promos}, else the recommended one, null if neither is listed. */
    public static String selectLatest(Map<String, String> promos, String minecraftVersion) {
        if (promos == null) return null;
        String latest = promos.get(minecraftVersion + "-latest");
        return latest != null ? latest : promos.get(minecraftVersion + "-recommended");
    }

    /**
     * Forge from 1.20 on (all of its installers of the versions the launcher offers) starts through ForgeBootstrap,
     * which is what the launch code of the launcher is written for. Older ones start through a launch wrapper.
     */
    public static boolean isSupported(String minecraftVersion) {
        Matcher m = MC_VERSION.matcher(minecraftVersion);
        if (!m.matches()) return false;
        return Integer.parseInt(m.group(1)) >= 20;
    }

    public static String getInstallerUrl(String minecraftVersion, String forgeVersion) {
        String build = minecraftVersion + "-" + forgeVersion;
        return String.format("%s/net/minecraftforge/forge/%s/forge-%s-installer.jar", Constants.FORGE_MAVEN_URL, build, build);
    }

    /** Id of the profile the installer writes to {@code versions/}. */
    public static String getVersionId(String minecraftVersion, String forgeVersion) {
        return minecraftVersion + "-forge-" + forgeVersion;
    }

    /** The inverse of {@link #getVersionId}: {@code 1.21.1-forge-52.1.16} gives {@code 52.1.16}. */
    public static String getVersionFromId(String versionId) {
        int at = versionId.indexOf("-forge-");
        return at < 0 ? versionId : versionId.substring(at + "-forge-".length());
    }
}
