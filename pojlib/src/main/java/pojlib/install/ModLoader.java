package pojlib.install;

import java.util.Locale;

import pojlib.util.Constants;

/**
 * The mod loaders an instance can be made with.
 * <p>
 * Everything that differs per loader but is not an install step lives here: the id stored in
 * {@code instances.json}, the name shown in the launcher, which Modrinth category its mods are in, and where its
 * mod list and list of supported Minecraft versions are hosted. To add a loader, add a constant here, an install
 * branch in {@code InstanceHandler.create} and its launch arguments; the launcher's loader toggle, the version
 * lists and the Mod Manager pick it up from here.
 * <p>
 * Quilt loads Fabric mods, so it shares Fabric's mod list and accepts mods from the Fabric category on Modrinth,
 * but is its own loader everywhere else (install, instances, version list, mod list snapshot).
 */
public enum ModLoader {
    FABRIC("fabric", "Fabric",
            Constants.UPSTREAM_RAW_URL + "/mods.json",
            Constants.UPSTREAM_RAW_URL + "/devmods.json",
            Constants.UPSTREAM_RAW_URL + "/supportedVersions.json",
            "fabric"),
    NEOFORGE("neoforge", "NeoForge",
            Constants.PORT_RAW_URL + "/pojlib/mods-neoforge.json",
            Constants.PORT_RAW_URL + "/pojlib/devmods-neoforge.json",
            Constants.PORT_RAW_URL + "/pojlib/supportedVersions.json",
            "neoforge"),
    // Fabric's mod list, but only the versions Quilt was tested on
    QUILT("quilt", "Quilt",
            Constants.UPSTREAM_RAW_URL + "/mods.json",
            Constants.UPSTREAM_RAW_URL + "/devmods.json",
            Constants.PORT_RAW_URL + "/pojlib/supportedVersions-quilt.json",
            "quilt", "fabric"),
    // Same game versions as NeoForge, but its own mods (Embeddium instead of Sodium, ...)
    FORGE("forge", "Forge",
            Constants.PORT_RAW_URL + "/pojlib/mods-forge.json",
            Constants.PORT_RAW_URL + "/pojlib/devmods-forge.json",
            Constants.PORT_RAW_URL + "/pojlib/supportedVersions-forge.json",
            "forge"),
    // No mod loader: old versions whose VR is Vivecraft's changed game classes on top of OptiFine (1.12.2, see
    // LegacyVivecraft). Mods do not load, resource packs do
    VANILLA("vanilla", "Vanilla",
            Constants.PORT_RAW_URL + "/pojlib/mods-vanilla.json",
            Constants.PORT_RAW_URL + "/pojlib/devmods-vanilla.json",
            Constants.PORT_RAW_URL + "/pojlib/supportedVersions-vanilla.json",
            "minecraft"),
    // Risugami's ModLoader: alpha 1.1 to 1.6.2, put into the game's client and started by the legacy launcher (see
    // RisugamiModLoader). Its mods are on Modrinth under the "modloader" loader
    RISUGAMI("modloader", "ModLoader",
            Constants.PORT_RAW_URL + "/pojlib/mods-modloader.json",
            Constants.PORT_RAW_URL + "/pojlib/devmods-modloader.json",
            Constants.PORT_RAW_URL + "/pojlib/supportedVersions-modloader.json",
            "modloader");

    /** Stored in instances.json and the Modrinth category of the loader itself: lower case. */
    public final String id;
    /** Shown in the launcher, and what the launcher passes to {@code createNewInstance}. */
    public final String displayName;
    private final String modsUrl;
    private final String devModsUrl;
    private final String supportedVersionsUrl;
    private final String[] modrinthCategories;

    ModLoader(String id, String displayName, String modsUrl, String devModsUrl, String supportedVersionsUrl,
              String... modrinthCategories) {
        this.id = id;
        this.displayName = displayName;
        this.modsUrl = modsUrl;
        this.devModsUrl = devModsUrl;
        this.supportedVersionsUrl = supportedVersionsUrl;
        this.modrinthCategories = modrinthCategories;
    }

    /**
     * The Modrinth loader categories whose mods and modpacks load with this loader, most specific first
     * (Quilt runs its own and Fabric mods).
     */
    public String[] getModrinthCategories() {
        return modrinthCategories.clone();
    }

    public String getModsUrl(boolean developerMods) {
        return developerMods ? devModsUrl : modsUrl;
    }

    public String getSupportedVersionsUrl() {
        return supportedVersionsUrl;
    }

    /**
     * The file in the launcher's files directory the mod list was last updated from. It is how the launcher finds
     * out what changed, so each loader needs its own. Fabric keeps the name it always had.
     */
    public String getModsSnapshotName() {
        return this == FABRIC ? "mods.json" : "mods-" + id + ".json";
    }

    /** The file the list of supported Minecraft versions is cached in. */
    public String getSupportedVersionsCacheName() {
        return "supportedVersions-" + id + ".json";
    }

    /**
     * Finds a loader by the name the launcher passes ("NeoForge") or the id stored in an instance ("neoforge").
     *
     * @return the loader, or null if there is none with that name
     */
    public static ModLoader fromName(String name) {
        if (name == null) return null;
        String wanted = name.trim().toLowerCase(Locale.ROOT);
        for (ModLoader loader : values()) {
            if (loader.id.equals(wanted) || loader.displayName.toLowerCase(Locale.ROOT).equals(wanted)) {
                return loader;
            }
        }
        return null;
    }

    /** The loader an instance was made with. Instances from before NeoForge was added have none stored and are Fabric. */
    public static ModLoader fromStored(String storedId) {
        ModLoader loader = fromName(storedId);
        return loader != null ? loader : FABRIC;
    }

    public static String[] displayNames() {
        ModLoader[] all = values();
        String[] names = new String[all.length];
        for (int i = 0; i < all.length; i++) {
            names[i] = all[i].displayName;
        }
        return names;
    }
}
