package pojlib.util.json;

public class ModsJson {
    public Version[] versions;

    public static class Version {
        public String name;
        public ProjectInfo[] coreMods;
        public ProjectInfo[] defaultMods;
        /** What replaces {@link #coreMods} (Vivecraft) in an instance that uses Visor as its VR mod, null if there is no Visor for this version. */
        public ProjectInfo[] visorCoreMods;

        /** The mods every instance has: Vivecraft's, or Visor's for an instance that uses Visor. */
        public ProjectInfo[] coreModsFor(boolean visor) {
            return visor && visorCoreMods != null && visorCoreMods.length > 0 ? visorCoreMods : coreMods;
        }

        public boolean hasVisor() {
            return visorCoreMods != null && visorCoreMods.length > 0;
        }
    }
}
