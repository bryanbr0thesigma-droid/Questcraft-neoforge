package pojlib.util;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;

import org.junit.jupiter.api.Test;

class LoadingProgressTest {
    @Test
    void emptyLogSaysNothing() {
        assertNull(LoadingProgress.of(null));
        assertNull(LoadingProgress.of(""));
        assertNull(LoadingProgress.of("[main/INFO] something else"));
    }

    @Test
    void theFurthestLandmarkWins() {
        String log = "[main/INFO] [minecraft/Main]: x\n"
                + "[Render thread/INFO] [minecraft/Minecraft]: Setting user: Dev\n"
                + "[Render thread/INFO] [minecraft/ReloadableResourceManager]: Reloading ResourceManager: vanilla\n"
                + "[Render thread/INFO] [minecraft/Minecraft]: Backend library: LWJGL version 3.3.3\n";
        LoadingProgress p = LoadingProgress.of(log);
        assertEquals(75, p.percent);
        assertEquals("Loading resources", p.label);
    }

    @Test
    void everyLoaderHasALandmarkBeforeTheGameStarts() {
        assertEquals(45, LoadingProgress.of("ModLauncher running: args [--launchTarget forge_client]").percent);
        assertEquals(50, LoadingProgress.of("Forge mod loading, version 61.2.3").percent);
        assertEquals(50, LoadingProgress.of("[main/INFO]: Loading Minecraft 1.21.11 with Fabric Loader").percent);
    }

    @Test
    void atlasesAreNearTheEnd() {
        assertEquals(92, LoadingProgress.of("Created: 512x256x0 minecraft:textures/atlas/particles.png-atlas").percent);
        assertEquals(96, LoadingProgress.of("Created: 2048x2048x4 minecraft:textures/atlas/blocks.png-atlas").percent);
    }

    @Test
    void theBarNeverGoesBack() {
        LoadingProgress now = LoadingProgress.step(LoadingProgress.START_JAVA, "Starting Java");
        assertSame(now, now.orFurther(null));
        assertSame(now, now.orFurther(LoadingProgress.step(10, "older")));
        assertEquals(60, now.orFurther(LoadingProgress.of("Setting user: Dev")).percent);
    }
}
