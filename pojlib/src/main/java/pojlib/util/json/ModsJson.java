package pojlib.util.json;

import java.util.ArrayList;
import java.util.List;

public class ModsJson {
    public Version[] versions;

    public static class Version {
        public String name;
        public ProjectInfo[] coreMods;
        public ProjectInfo[] defaultMods;
        /** What replaces Vivecraft among the {@link #coreMods} in an instance that uses Visor as its VR mod, null if there is no Visor for this version. */
        public ProjectInfo[] visorCoreMods;

        /**
         * The mods every instance has. For an instance that uses Visor, Vivecraft is swapped for Visor and the rest stays
         * (Fabric API is a core mod of the Fabric lists, and Visor needs it too).
         */
        public ProjectInfo[] coreModsFor(boolean visor) {
            if (!visor || !hasVisor()) return coreMods;
            List<ProjectInfo> mods = new ArrayList<>();
            for (ProjectInfo mod : coreMods) {
                if (!isVivecraft(mod)) mods.add(mod);
            }
            for (ProjectInfo mod : visorCoreMods) mods.add(mod);
            return mods.toArray(new ProjectInfo[0]);
        }

        public boolean hasVisor() {
            return visorCoreMods != null && visorCoreMods.length > 0;
        }

        static boolean isVivecraft(ProjectInfo mod) {
            return mod.slug != null && mod.slug.equalsIgnoreCase("vivecraft");
        }
    }
}
