package pojlib.util;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class MinecraftVersionsTest {
    @Test
    void numbersAreComparedAsNumbersNotText() {
        assertArrayEquals(new String[]{"1.21.11", "1.21.10", "1.21.8", "1.21.1", "1.21", "1.20.4", "1.19.2"},
                MinecraftVersions.union(new String[]{"1.21.1", "1.19.2", "1.21.10", "1.20.4", "1.21", "1.21.8", "1.21.11"}));
    }

    @Test
    void unionKeepsEachVersionOnceAndSkipsNullLists() {
        assertArrayEquals(new String[]{"1.21.5", "1.21.4", "1.20.4", "1.19.2"},
                MinecraftVersions.union(new String[]{"1.21.5", "1.21.4", "1.20.4"}, null, new String[]{"1.21.4", "1.19.2"}));
        assertArrayEquals(new String[0], MinecraftVersions.union());
    }

    @Test
    void snapshotsGoAfterReleasesInTheirOwnOrder() {
        assertArrayEquals(new String[]{"1.21.4", "25w14craftmine", "24w14potato"},
                MinecraftVersions.union(new String[]{"25w14craftmine", "24w14potato", "1.21.4"}));
    }

    @Test
    void containsFindsExactVersionsOnly() {
        String[] versions = {"1.21.1", "1.20.4"};
        assertTrue(MinecraftVersions.contains(versions, "1.20.4"));
        assertFalse(MinecraftVersions.contains(versions, "1.20"));
        assertFalse(MinecraftVersions.contains(null, "1.20.4"));
    }
}
