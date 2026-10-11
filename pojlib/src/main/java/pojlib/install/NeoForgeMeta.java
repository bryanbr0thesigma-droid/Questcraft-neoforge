package pojlib.install;

import com.google.gson.annotations.SerializedName;

import pojlib.APIHandler;
import pojlib.util.Constants;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Looks up NeoForge versions on the NeoForge maven.
 * <p>
 * NeoForge versions are derived from the Minecraft version they target:
 * Minecraft {@code 1.A.B} is {@code A.B.<build>[-beta]} (so 1.21.1 is {@code 21.1.x}),
 * and a Minecraft version without a patch number ({@code 1.21}) is {@code A.0.<build>}.
 */
public class NeoForgeMeta {

    private static final Pattern MC_VERSION = Pattern.compile("^1\\.(\\d+)(?:\\.(\\d+))?$");
    private static final Pattern NF_VERSION = Pattern.compile("^(\\d+)\\.(\\d+)\\.(\\d+)(-[A-Za-z0-9.]+)?$");

    public static class NeoForgeVersions {
        @SerializedName("versions")
        public String[] versions;
    }

    /** All released NeoForge versions, oldest first. */
    public static String[] getVersions() {
        // created on demand, Constants needs an Android environment
        APIHandler handler = new APIHandler(Constants.NEOFORGE_API_URL);
        NeoForgeVersions versions = handler.get("versions/releases/net/neoforged/neoforge", NeoForgeVersions.class);
        return versions == null ? null : versions.versions;
    }

    /** The newest NeoForge version for a Minecraft version, or null if there is none. */
    public static String getLatestVersion(String minecraftVersion) {
        String[] versions = getVersions();
        return versions == null ? null : selectLatest(versions, minecraftVersion);
    }

    /**
     * Picks the newest NeoForge version for a Minecraft version out of {@code versions}.
     * Stable releases are preferred, a beta is only returned if there is no stable release.
     *
     * @return the NeoForge version or null if none of {@code versions} targets {@code minecraftVersion}
     */
    public static String selectLatest(String[] versions, String minecraftVersion) {
        String prefix = versionPrefix(minecraftVersion);
        if (prefix == null) return null;

        int stableBuild = -1, betaBuild = -1;
        String stable = null, beta = null;
        for (String version : versions) {
            if (!version.startsWith(prefix)) continue;
            Matcher m = NF_VERSION.matcher(version);
            if (!m.matches()) continue;

            int build = Integer.parseInt(m.group(3));
            if (m.group(4) == null) {
                if (build > stableBuild) {
                    stableBuild = build;
                    stable = version;
                }
            } else if (build > betaBuild) {
                betaBuild = build;
                beta = version;
            }
        }
        return stable != null ? stable : beta;
    }

    /** {@code "1.21.1"} gives {@code "21.1."}, {@code "1.21"} gives {@code "21.0."}, null for anything else. */
    public static String versionPrefix(String minecraftVersion) {
        Matcher m = MC_VERSION.matcher(minecraftVersion);
        if (!m.matches()) return null;
        return m.group(1) + "." + (m.group(2) == null ? "0" : m.group(2)) + ".";
    }

    /** The first Minecraft version NeoForge exists for. Older versions only have Forge. */
    public static boolean isSupported(String minecraftVersion) {
        Matcher m = MC_VERSION.matcher(minecraftVersion);
        if (!m.matches()) return false;
        int major = Integer.parseInt(m.group(1));
        int minor = m.group(2) == null ? 0 : Integer.parseInt(m.group(2));
        return major > 20 || (major == 20 && minor >= 2);
    }

    public static String getInstallerUrl(String neoForgeVersion) {
        return String.format("%s/net/neoforged/neoforge/%s/neoforge-%s-installer.jar",
                Constants.NEOFORGE_MAVEN_URL, neoForgeVersion, neoForgeVersion);
    }

    /** Version id the installer gives the profile it writes to {@code versions/}. */
    public static String getVersionId(String neoForgeVersion) {
        return "neoforge-" + neoForgeVersion;
    }

    /** The inverse of {@link #getVersionId}: {@code neoforge-21.1.259} gives {@code 21.1.259}. */
    public static String getVersionFromId(String versionId) {
        return versionId.substring("neoforge-".length());
    }
}
