package pojlib.install;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class NeoForgeMetaTest {
    private static final String[] VERSIONS = {
            "20.4.1-beta", "20.4.9", "20.4.251", "20.4.88",
            "21.0.167", "21.1.9", "21.1.259", "21.1.100", "21.10.64", "21.1.300-beta",
            "21.5.2-beta", "21.5.98", "21.2.1-beta", "26.1.2.116"
    };

    @Test
    void versionPrefixFollowsMinecraftVersion() {
        assertEquals("21.1.", NeoForgeMeta.versionPrefix("1.21.1"));
        assertEquals("21.0.", NeoForgeMeta.versionPrefix("1.21"));
        assertEquals("20.4.", NeoForgeMeta.versionPrefix("1.20.4"));
        assertNull(NeoForgeMeta.versionPrefix("24w14potato"));
        assertNull(NeoForgeMeta.versionPrefix("1.21.1-pre1"));
    }

    @Test
    void latestStableIsPickedByBuildNumberNotLexically() {
        assertEquals("20.4.251", NeoForgeMeta.selectLatest(VERSIONS, "1.20.4"));
        assertEquals("21.1.259", NeoForgeMeta.selectLatest(VERSIONS, "1.21.1"));
        assertEquals("21.0.167", NeoForgeMeta.selectLatest(VERSIONS, "1.21"));
        assertEquals("21.5.98", NeoForgeMeta.selectLatest(VERSIONS, "1.21.5"));
    }

    @Test
    void prefixDoesNotMatchLongerMinorVersions() {
        // 1.21.1 must not pick up 21.10.x
        assertEquals("21.1.259", NeoForgeMeta.selectLatest(new String[]{"21.10.64", "21.1.259"}, "1.21.1"));
        assertNull(NeoForgeMeta.selectLatest(new String[]{"21.10.64"}, "1.21.1"));
    }

    @Test
    void betaIsOnlyUsedWithoutAStableRelease() {
        assertEquals("21.2.1-beta", NeoForgeMeta.selectLatest(VERSIONS, "1.21.2"));
        assertNull(NeoForgeMeta.selectLatest(VERSIONS, "1.19.2"));
    }

    @Test
    void neoForgeStartsAt1202() {
        assertFalse(NeoForgeMeta.isSupported("1.19.2"));
        assertFalse(NeoForgeMeta.isSupported("1.20.1"));
        assertTrue(NeoForgeMeta.isSupported("1.20.4"));
        assertTrue(NeoForgeMeta.isSupported("1.21"));
        assertTrue(NeoForgeMeta.isSupported("1.21.5"));
    }

    @Test
    void versionIdMatchesInstallerProfile() {
        assertEquals("neoforge-21.1.259", NeoForgeMeta.getVersionId("21.1.259"));
        assertEquals("21.1.259", NeoForgeMeta.getVersionFromId("neoforge-21.1.259"));
        assertEquals("21.5.2-beta", NeoForgeMeta.getVersionFromId(NeoForgeMeta.getVersionId("21.5.2-beta")));
    }
}
