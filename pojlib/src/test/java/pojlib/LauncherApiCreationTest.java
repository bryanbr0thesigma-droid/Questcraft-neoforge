package pojlib;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicBoolean;

import pojlib.util.json.MinecraftInstances;

class LauncherApiCreationTest {
    private static final MinecraftInstances.Instance INSTANCE = new MinecraftInstances.Instance();

    @Test
    void waitsForTheBackgroundWorkBeforeReturning() throws Exception {
        CompletableFuture<Void> work = new CompletableFuture<>();
        AtomicBoolean returned = new AtomicBoolean(false);
        Thread waiter = new Thread(() -> {
            try {
                LauncherApi.awaitCreation(INSTANCE, work, () -> true, () -> "");
                returned.set(true);
            } catch (Exception e) {
                throw new RuntimeException(e);
            }
        });
        waiter.start();
        Thread.sleep(200);
        assertFalse(returned.get(), "returned while the instance was still being made");
        work.complete(null);
        waiter.join(5000);
        assertTrue(returned.get());
    }

    @Test
    void reportsWhyTheInstanceWasNotMade() throws Exception {
        CompletableFuture<Void> work = CompletableFuture.completedFuture(null);
        try {
            LauncherApi.awaitCreation(INSTANCE, work, () -> false, () -> "NeoForge installer did not finish");
            fail("should have failed");
        } catch (IOException e) {
            assertEquals("NeoForge installer did not finish", e.getMessage());
        }
    }

    @Test
    void failsWithoutAnInstance() throws Exception {
        try {
            LauncherApi.awaitCreation(null, null, () -> true, () -> "");
            fail("should have failed");
        } catch (IOException e) {
            assertTrue(e.getMessage().contains("could not be created"));
        }
    }

    @Test
    void succeedsWhenThereIsNothingToWaitFor() throws Exception {
        LauncherApi.awaitCreation(INSTANCE, null, () -> true, () -> "");
    }
}
