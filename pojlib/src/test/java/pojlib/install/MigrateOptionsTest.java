package pojlib.install;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class MigrateOptionsTest {
    @Test
    void theGameWrittenDefaultAndTheOldValuesBecomeTheLauncherValues() {
        String before = "version:3218\ngamma:0.5\nrenderDistance:6\nsimulationDistance:4\nfov:0.0\n";
        String after = Installer.migrateOptions(before);
        assertEquals("version:3218\ngamma:0.0\nrenderDistance:6\nsimulationDistance:5\nfov:0.0\n", after);
    }

    @Test
    void aLargerSimulationDistanceStays() {
        assertTrue(Installer.migrateOptions("simulationDistance:12\n").contains("simulationDistance:12"));
    }

    @Test
    void otherLinesAreNotTouched() {
        String text = "key_key.attack:key.mouse.left\nresourcePacks:[\"vanilla\"]\n";
        assertEquals(text, Installer.migrateOptions(text));
    }
}
