package pojlib.install;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;

/** The sounds of versions before 1.7, which read them under their own names (Installer.mapLegacyAssets). */
class MapLegacyAssetsTest {
    private static final String SOUND_HASH = "0b3ce1aee4ec7ba4c4bd4b7d4d7d7af3a3a3ac0c";

    private static VersionInfo version(String assets) {
        VersionInfo v = new VersionInfo();
        v.id = "test";
        v.assets = assets;
        return v;
    }

    private static void setUp(File shared, String index, String flags) throws Exception {
        File indexes = new File(shared, "assets/indexes");
        indexes.mkdirs();
        Files.write(new File(indexes, index + ".json").toPath(), ("{" + flags + "\"objects\":{\"sound3/random/click.ogg\":{\"hash\":\""
                + SOUND_HASH + "\",\"size\":5},\"music/calm1.ogg\":{\"hash\":\"ffffffffffffffffffffffffffffffffffffffff\",\"size\":3}}}").getBytes(StandardCharsets.UTF_8));
        File object = new File(shared, "assets/objects/0b/" + SOUND_HASH);
        object.getParentFile().mkdirs();
        Files.write(object.toPath(), "click".getBytes(StandardCharsets.UTF_8));
    }

    @Test
    void before16TheSoundsGoIntoTheGamesResourcesFolder(@TempDir File dir) throws Exception {
        File shared = new File(dir, "home"), instance = new File(dir, "instances/beta");
        setUp(shared, "pre-1.6", "\"map_to_resources\":true,");
        String assets = Installer.mapLegacyAssets(version("pre-1.6"), shared.getPath(), instance.getPath());
        assertEquals(new File(shared, "assets").getAbsolutePath(), assets);
        File click = new File(instance, "resources/sound3/random/click.ogg");
        assertEquals("click", new String(Files.readAllBytes(click.toPath()), StandardCharsets.UTF_8));
        assertFalse(new File(instance, "resources/music/calm1.ogg").exists(), "an object that was never downloaded");
    }

    @Test
    void in16TheGameGetsTheVirtualFolder(@TempDir File dir) throws Exception {
        File shared = new File(dir, "home"), instance = new File(dir, "instances/one-six");
        setUp(shared, "legacy", "\"virtual\":true,");
        String assets = Installer.mapLegacyAssets(version("legacy"), shared.getPath(), instance.getPath());
        assertEquals(new File(shared, "assets/virtual/legacy").getAbsolutePath(), assets);
        assertTrue(new File(assets, "sound3/random/click.ogg").isFile());
        assertFalse(new File(instance, "resources").exists());
    }

    @Test
    void newerVersionsAreLeftAsTheyAre(@TempDir File dir) throws Exception {
        File shared = new File(dir, "home"), instance = new File(dir, "instances/new");
        setUp(shared, "1.12", "");
        assertEquals(new File(shared, "assets").getAbsolutePath(), Installer.mapLegacyAssets(version("1.12"), shared.getPath(), instance.getPath()));
        assertFalse(new File(shared, "assets/virtual").exists());
        assertFalse(new File(instance, "resources").exists());
    }
}
