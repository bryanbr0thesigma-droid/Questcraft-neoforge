package pojlib.install;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

import java.util.HashSet;
import java.util.Set;

class ModLoaderTest {
    @Test
    void launcherNamesAndStoredIdsFindTheSameLoader() {
        assertEquals(ModLoader.NEOFORGE, ModLoader.fromName("NeoForge"));
        assertEquals(ModLoader.NEOFORGE, ModLoader.fromName("neoforge"));
        assertEquals(ModLoader.FABRIC, ModLoader.fromName("Fabric"));
        assertEquals(ModLoader.FABRIC, ModLoader.fromName(" fabric "));
        assertNull(ModLoader.fromName("Quilt"));
        assertNull(ModLoader.fromName(""));
        assertNull(ModLoader.fromName(null));
    }

    @Test
    void instancesFromBeforeTheNeoForgePortAreFabric() {
        assertEquals(ModLoader.FABRIC, ModLoader.fromStored(null));
        assertEquals(ModLoader.FABRIC, ModLoader.fromStored("something unknown"));
        assertEquals(ModLoader.NEOFORGE, ModLoader.fromStored("neoforge"));
    }

    @Test
    void theLauncherListsFabricAndNeoForge() {
        assertArrayEquals(new String[]{"Fabric", "NeoForge"}, ModLoader.displayNames());
        // what the launcher shows is accepted again when it passes it back
        for (String name : ModLoader.displayNames()) {
            assertEquals(name, ModLoader.fromName(name).displayName);
        }
    }

    @Test
    void everyLoaderHasItsOwnFilesAndLists() {
        Set<String> snapshots = new HashSet<>(), caches = new HashSet<>(), mods = new HashSet<>(), versions = new HashSet<>();
        for (ModLoader loader : ModLoader.values()) {
            assertTrue(snapshots.add(loader.getModsSnapshotName()), "mods snapshot shared");
            assertTrue(caches.add(loader.getSupportedVersionsCacheName()), "version cache shared");
            assertTrue(mods.add(loader.getModsUrl(false)), "mod list shared");
            assertTrue(versions.add(loader.getSupportedVersionsUrl()), "version list shared");
            assertNotEquals(loader.getModsUrl(false), loader.getModsUrl(true), "dev list is the normal list");
            assertEquals(loader.id, loader.id.toLowerCase(), "the id is the Modrinth category");
        }
    }

    @Test
    void fabricKeepsTheSnapshotNameItAlwaysHad() {
        assertEquals("mods.json", ModLoader.FABRIC.getModsSnapshotName());
        assertEquals("mods-neoforge.json", ModLoader.NEOFORGE.getModsSnapshotName());
    }

    @Test
    void fabricComesFromUpstreamAndNeoForgeFromThisPort() {
        assertTrue(ModLoader.FABRIC.getModsUrl(false).contains("QuestCraftPlusPlus/Pojlib"));
        assertTrue(ModLoader.NEOFORGE.getModsUrl(false).endsWith("/pojlib/mods-neoforge.json"));
    }
}
