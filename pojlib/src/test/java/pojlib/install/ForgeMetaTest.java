package pojlib.install;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.Map;

class ForgeMetaTest {
    private static Map<String, String> promos() {
        Map<String, String> promos = new HashMap<>();
        promos.put("1.21.1-latest", "52.1.16");
        promos.put("1.21.1-recommended", "52.1.0");
        promos.put("1.21.6-latest", "56.0.9");   // nothing recommended yet
        promos.put("1.21.9-recommended", "59.0.0");
        return promos;
    }

    @Test
    void newestBuildIsPreferredThenTheRecommendedOne() {
        assertEquals("52.1.16", ForgeMeta.selectLatest(promos(), "1.21.1"));
        assertEquals("56.0.9", ForgeMeta.selectLatest(promos(), "1.21.6"));
        assertEquals("59.0.0", ForgeMeta.selectLatest(promos(), "1.21.9"));
        assertNull(ForgeMeta.selectLatest(promos(), "1.21.2"));
        assertNull(ForgeMeta.selectLatest(null, "1.21.1"));
    }

    @Test
    void forgeIsOfferedFrom1_20On() {
        assertTrue(ForgeMeta.isSupported("1.20.4"));
        assertTrue(ForgeMeta.isSupported("1.21.11"));
        assertTrue(ForgeMeta.isSupported("1.20"));
        assertFalse(ForgeMeta.isSupported("1.19.4"));
        assertFalse(ForgeMeta.isSupported("1.12.2"));
        assertFalse(ForgeMeta.isSupported("24w14potato"));
    }

    @Test
    void installerAndProfileNamesFollowForgesScheme() {
        assertEquals("https://maven.minecraftforge.net/net/minecraftforge/forge/1.21.1-52.1.16/forge-1.21.1-52.1.16-installer.jar",
                ForgeMeta.getInstallerUrl("1.21.1", "52.1.16"));
        assertEquals("1.21.1-forge-52.1.16", ForgeMeta.getVersionId("1.21.1", "52.1.16"));
        assertEquals("52.1.16", ForgeMeta.getVersionFromId("1.21.1-forge-52.1.16"));
        assertEquals("61.2.3", ForgeMeta.getVersionFromId(ForgeMeta.getVersionId("1.21.11", "61.2.3")));
    }
}
