package pojlib;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class LogLinesTest {
    @Test
    void keepsTheLinesThatSayWhyVrDidOrDidNotStart() {
        String[] log = {
                "[12:00:01] [main/INFO] [FML]: Loading mods",
                "[12:00:02] [Render thread/ERROR] [Vivecraft/]: Vivecraft: Failed to initialize VR:",
                "java.lang.NoSuchMethodError: 'void X.y()'",
                "\tat org.lwjgl.openxr.XrPosef.orientation(XrPosef.java:91)",
                "[12:00:03] [Render thread/INFO] [minecraft/Minecraft]: Setting user: Dev",
                "[12:00:04] [Render thread/INFO] [Vivecraft/]: OpenXR session started",
        };
        String result = LauncherApi.importantLines(log, 10);
        assertTrue(result.contains("Failed to initialize VR"));
        assertTrue(result.contains("NoSuchMethodError"));
        assertTrue(result.contains("OpenXR session started"));
        assertFalse(result.contains("Loading mods"));
        assertFalse(result.contains("Setting user"));
        assertFalse(result.contains("XrPosef.orientation"), "stack frames are left out");
    }

    @Test
    void onlyTheLastLinesAreKept() {
        String[] log = {"Vivecraft one", "Vivecraft two", "Vivecraft three"};
        assertEquals("Vivecraft two\nVivecraft three\n", LauncherApi.importantLines(log, 2));
    }
}
