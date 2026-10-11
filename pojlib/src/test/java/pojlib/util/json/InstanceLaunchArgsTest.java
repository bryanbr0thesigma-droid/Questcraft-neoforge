package pojlib.util.json;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

import java.util.List;

import pojlib.account.MinecraftAccount;
import pojlib.install.ModLoader;

/** What the loader toggle changes about an instance at launch time. */
class InstanceLaunchArgsTest {
    private static MinecraftInstances.Instance instance(String modLoader) {
        MinecraftInstances.Instance instance = new MinecraftInstances.Instance();
        instance.modLoader = modLoader;
        instance.versionName = "1.21.1";
        instance.gameDir = "/game";
        instance.assetsDir = "/assets";
        instance.assetIndex = "17";
        instance.classpath = "/a.jar:/b.jar";
        return instance;
    }

    private static MinecraftAccount account() {
        MinecraftAccount account = new MinecraftAccount();
        account.username = "Steve";
        account.uuid = "11-22";
        account.accessToken = "token";
        return account;
    }

    @Test
    void anInstanceKnowsItsLoader() {
        assertEquals(ModLoader.FABRIC, instance("fabric").loader());
        assertEquals(ModLoader.NEOFORGE, instance("neoforge").loader());
        assertEquals(ModLoader.FABRIC, instance(null).loader(), "instances from before the NeoForge port are Fabric");
        assertTrue(instance("neoforge").isNeoForge());
        assertFalse(instance("fabric").isNeoForge());
    }

    @Test
    void anInstanceReportsWhatTheLauncherShowsAndSearches() {
        assertEquals("Quilt", instance("quilt").loaderName());
        assertEquals("Fabric", instance(null).loaderName());
        assertEquals(java.util.Arrays.asList("quilt", "fabric"), java.util.Arrays.asList(instance("quilt").modrinthCategories()));
        assertEquals(java.util.Arrays.asList("neoforge"), java.util.Arrays.asList(instance("neoforge").modrinthCategories()));
        assertFalse(instance("quilt").isNeoForge());
    }

    @Test
    void theModListSnapshotIsPerLoader() {
        assertTrue(instance("fabric").modsSnapshotPath().endsWith("/mods.json"));
        assertTrue(instance("neoforge").modsSnapshotPath().endsWith("/mods-neoforge.json"));
        assertTrue(instance("quilt").modsSnapshotPath().endsWith("/mods-quilt.json"));
    }

    @Test
    void aFabricInstanceLaunchesWithoutAnyLoaderArguments() {
        MinecraftInstances.Instance fabric = instance("fabric");
        fabric.mainClass = "net.fabricmc.loader.impl.launch.knot.KnotClient";

        List<String> args = fabric.generateLaunchArgs(account());

        assertEquals(List.of("-cp", "/a.jar:/b.jar", "net.fabricmc.loader.impl.launch.knot.KnotClient"), args.subList(0, 3));
        assertFalse(args.contains("--launchTarget"));
        assertEquals("Steve", args.get(args.indexOf("--username") + 1));
        assertEquals("1122", args.get(args.indexOf("--uuid") + 1));
    }

    @Test
    void aNeoForgeInstanceHasItsModulePathBeforeTheClasspathAndItsGameArgumentsAfter() {
        MinecraftInstances.Instance neoForge = instance("neoforge");
        neoForge.mainClass = "cpw.mods.bootstraplauncher.BootstrapLauncher";
        neoForge.loaderJvmArgs = new String[]{"-DlibraryDirectory=/libs", "-p", "/libs/a.jar", "--add-modules", "ALL-MODULE-PATH"};
        neoForge.loaderGameArgs = new String[]{"--launchTarget", "forgeclient"};

        List<String> args = neoForge.generateLaunchArgs(account());

        assertEquals(List.of("-DlibraryDirectory=/libs", "-p", "/libs/a.jar", "--add-modules", "ALL-MODULE-PATH", "-cp"),
                args.subList(0, 6));
        assertTrue(args.indexOf("-p") < args.indexOf("-cp"));
        assertEquals("cpw.mods.bootstraplauncher.BootstrapLauncher", args.get(args.indexOf("-cp") + 2));
        assertEquals(List.of("--launchTarget", "forgeclient"), args.subList(args.size() - 2, args.size()));
    }
}
