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
        assertEquals(ModLoader.QUILT, ModLoader.fromName("Quilt"));
        assertEquals(ModLoader.RISUGAMI, ModLoader.fromName("ModLoader"));
        assertEquals(ModLoader.RISUGAMI, ModLoader.fromStored("modloader"));
        assertNull(ModLoader.fromName("LiteLoader"));
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
    void theLauncherListsFabricNeoForgeQuiltForgeVanillaAndModLoader() {
        assertArrayEquals(new String[]{"Fabric", "NeoForge", "Quilt", "Forge", "Vanilla", "ModLoader"}, ModLoader.displayNames());
        // what the launcher shows is accepted again when it passes it back
        for (String name : ModLoader.displayNames()) {
            assertEquals(name, ModLoader.fromName(name).displayName);
        }
    }

    @Test
    void everyLoaderHasItsOwnFilesAndVersionList() {
        Set<String> snapshots = new HashSet<>(), caches = new HashSet<>(), versions = new HashSet<>();
        for (ModLoader loader : ModLoader.values()) {
            assertTrue(snapshots.add(loader.getModsSnapshotName()), "mods snapshot shared");
            assertTrue(caches.add(loader.getSupportedVersionsCacheName()), "version cache shared");
            assertTrue(versions.add(loader.getSupportedVersionsUrl()), "version list shared");
            assertNotEquals(loader.getModsUrl(false), loader.getModsUrl(true), "dev list is the normal list");
            assertEquals(loader.id, loader.id.toLowerCase(), "the id is the Modrinth category");
        }
    }

    @Test
    void quiltReadsFabricsModListButNotItsVersionList() {
        assertEquals(ModLoader.FABRIC.getModsUrl(false), ModLoader.QUILT.getModsUrl(false));
        assertEquals(ModLoader.FABRIC.getModsUrl(true), ModLoader.QUILT.getModsUrl(true));
        assertNotEquals(ModLoader.FABRIC.getSupportedVersionsUrl(), ModLoader.QUILT.getSupportedVersionsUrl());
        // but what it was last updated from is its own
        assertNotEquals(ModLoader.FABRIC.getModsSnapshotName(), ModLoader.QUILT.getModsSnapshotName());
    }

    @Test
    void modrinthCategoriesSaySoWhichModsLoad() {
        assertArrayEquals(new String[]{"fabric"}, ModLoader.FABRIC.getModrinthCategories());
        assertArrayEquals(new String[]{"neoforge"}, ModLoader.NEOFORGE.getModrinthCategories());
        // Quilt loads its own mods and Fabric's, its own first
        assertArrayEquals(new String[]{"quilt", "fabric"}, ModLoader.QUILT.getModrinthCategories());
        // callers can not change the registry through the returned array
        ModLoader.QUILT.getModrinthCategories()[0] = "changed";
        assertEquals("quilt", ModLoader.QUILT.getModrinthCategories()[0]);
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

    @Test
    void forgeIsItsOwnLoaderNotNeoForge() {
        assertEquals(ModLoader.FORGE, ModLoader.fromName("Forge"));
        assertEquals(ModLoader.FORGE, ModLoader.fromName("forge"));
        assertEquals(ModLoader.FORGE, ModLoader.fromStored("forge"));
        assertEquals(ModLoader.NEOFORGE, ModLoader.fromName("neoforge"));
        assertArrayEquals(new String[]{"forge"}, ModLoader.FORGE.getModrinthCategories());
        assertEquals("mods-forge.json", ModLoader.FORGE.getModsSnapshotName());
        assertTrue(ModLoader.FORGE.getModsUrl(false).endsWith("/pojlib/mods-forge.json"));
        assertTrue(ModLoader.FORGE.getSupportedVersionsUrl().endsWith("/pojlib/supportedVersions-forge.json"));
    }
}
