package pojlib.install;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import org.junit.jupiter.api.Test;

import pojlib.util.json.MinecraftInstances;
import pojlib.util.json.ModsJson;
import pojlib.util.json.ProjectInfo;

class VisorModsTest {
    private static final JsonObject REGISTRY = JsonParser.parseString("{\"versions\":["
            + "{\"name\":\"1.21.1\","
            + "\"fabric\":{\"slug\":\"Visor\",\"version\":\"0.6.0-beta\",\"download_link\":\"https://x/visor-fabric-1.21.1.jar\"},"
            + "\"neoforge\":{\"slug\":\"Visor\",\"version\":\"0.6.0-beta\",\"download_link\":\"https://x/visor-neoforge-1.21.1.jar\"}},"
            + "{\"name\":\"1.20.4\",\"fabric\":{\"slug\":\"Visor\",\"version\":\"0.6.0-beta\",\"download_link\":\"https://x/f.jar\"}}"
            + "]}").getAsJsonObject();

    @Test
    void findsTheJarOfALoaderAndVersion() {
        ProjectInfo[] mods = VisorMods.coreMods(REGISTRY, "neoforge", "1.21.1");
        assertEquals(1, mods.length);
        assertEquals("Visor", mods[0].slug);
        assertEquals("https://x/visor-neoforge-1.21.1.jar", mods[0].download_link);
        assertEquals("mod", mods[0].type);
    }

    @Test
    void quiltRunsTheFabricJar() {
        assertEquals("https://x/visor-fabric-1.21.1.jar", VisorMods.coreMods(REGISTRY, "quilt", "1.21.1")[0].download_link);
    }

    @Test
    void sayNoWhereThereIsNone() {
        assertNull(VisorMods.coreMods(REGISTRY, "forge", "1.21.1"));       // no Forge jar of that version
        assertNull(VisorMods.coreMods(REGISTRY, "fabric", "1.19.2"));      // no such version
        assertNull(VisorMods.coreMods(null, "fabric", "1.21.1"));          // no list
        assertTrue(VisorMods.isAvailable(REGISTRY, "fabric", "1.20.4"));
        assertFalse(VisorMods.isAvailable(REGISTRY, "neoforge", "1.20.4"));
    }

    @Test
    void attachGivesEachVersionItsVisor() {
        ModsJson mods = new ModsJson();
        ModsJson.Version a = version("1.21.1", "Vivecraft");
        ModsJson.Version b = version("1.19.2", "Vivecraft");
        mods.versions = new ModsJson.Version[]{a, b};
        VisorMods.attach(mods, REGISTRY, "neoforge");
        assertTrue(a.hasVisor());
        assertFalse(b.hasVisor());
    }

    @Test
    void visorReplacesVivecraftOnlyWhereThereIsOne() {
        ModsJson.Version v = version("1.21.1", "Vivecraft");
        assertSame(v.coreMods, v.coreModsFor(false));
        assertSame(v.coreMods, v.coreModsFor(true));   // no Visor entry: Vivecraft stays
        v.visorCoreMods = VisorMods.coreMods(REGISTRY, "fabric", "1.21.1");
        assertSame(v.coreMods, v.coreModsFor(false));
        assertArrayEquals(v.visorCoreMods, v.coreModsFor(true));
    }

    @Test
    void anInstanceKnowsItsVrMod() {
        MinecraftInstances.Instance old = new MinecraftInstances.Instance();
        assertFalse(old.usesVisor());                        // made before there was a choice
        assertEquals("Vivecraft", old.vrModName());
        old.vrMod = "visor";
        assertTrue(old.usesVisor());
        assertEquals("Visor", old.vrModName());
    }

    private static ModsJson.Version version(String name, String coreSlug) {
        ModsJson.Version v = new ModsJson.Version();
        v.name = name;
        ProjectInfo core = new ProjectInfo();
        core.slug = coreSlug;
        v.coreMods = new ProjectInfo[]{core};
        v.defaultMods = new ProjectInfo[0];
        return v;
    }
}
