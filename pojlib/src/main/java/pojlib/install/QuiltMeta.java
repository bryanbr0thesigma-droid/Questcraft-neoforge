package pojlib.install;

import com.google.gson.annotations.SerializedName;
import pojlib.APIHandler;
import pojlib.util.Constants;

public class QuiltMeta {

    public static class QuiltVersion {
        @SerializedName("version")
        public String version;
    }

    // created on demand, Constants needs an Android environment
    private static APIHandler handler() {
        return new APIHandler(Constants.QUILT_META_URL);
    }

    /** Quilt loader versions, newest first. */
    public static QuiltVersion[] getVersions() {
        return handler().get("versions/loader", QuiltVersion[].class);
    }

    /** The newest stable Quilt loader, or the newest pre-release if there is no stable one. Null if offline. */
    public static QuiltVersion getLatestVersion() {
        QuiltVersion[] versions = getVersions();
        return versions == null ? null : selectLatest(versions);
    }

    /**
     * The first loader that is not a beta/alpha/pre-release ({@code 0.30.1}, not {@code 0.31.0-beta.4}),
     * the first one at all otherwise. Quilt's loader is not tied to a Minecraft version, so stable is enough.
     */
    public static QuiltVersion selectLatest(QuiltVersion[] versionsNewestFirst) {
        for (QuiltVersion version : versionsNewestFirst) {
            if (version.version != null && !version.version.contains("-")) return version;
        }
        return versionsNewestFirst.length > 0 ? versionsNewestFirst[0] : null;
    }

    public static VersionInfo getVersionInfo(QuiltVersion quiltVersion, String minecraftVersion) {
        return handler().get(String.format("versions/loader/%s/%s/profile/json", minecraftVersion, quiltVersion.version), VersionInfo.class);
    }
}