package pojlib.install;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.google.gson.Gson;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Runs against the version jsons the NeoForge 21.1.259 installer produced for Minecraft 1.21.1.
 */
class NeoForgeVersionInfoTest {
    private static VersionInfo vanilla;
    private static VersionInfo neoForge;

    private static VersionInfo read(String name) throws IOException {
        try (InputStream in = NeoForgeVersionInfoTest.class.getResourceAsStream("/neoforge/" + name);
             Reader reader = new InputStreamReader(in, StandardCharsets.UTF_8)) {
            return new Gson().fromJson(reader, VersionInfo.class);
        }
    }

    private static Map<String, String> variables() {
        Map<String, String> variables = new HashMap<>();
        variables.put("library_directory", "/data/libraries");
        variables.put("classpath_separator", ":");
        variables.put("version_name", "1.21.1");
        return variables;
    }

    @BeforeAll
    static void load() throws IOException {
        vanilla = read("vanilla-1.21.1.json");
        neoForge = read("neoforge-21.1.259.json");
    }

    @Test
    void profileInheritsFromVanilla() {
        assertEquals("1.21.1", neoForge.inheritsFrom);
        assertEquals("cpw.mods.bootstraplauncher.BootstrapLauncher", neoForge.mainClass);
        assertNotNull(vanilla.assetIndex);
    }

    @Test
    void jvmArgumentsAreResolved() {
        List<String> jvm = LaunchArguments.resolve(neoForge.arguments.jvm, variables());

        assertTrue(jvm.contains("--add-modules"));
        assertTrue(jvm.contains("ALL-MODULE-PATH"));
        assertTrue(jvm.contains("-DlibraryDirectory=/data/libraries"));
        assertTrue(jvm.contains("-DignoreList=client-extra,1.21.1.jar"));

        String modulePath = jvm.get(jvm.indexOf("-p") + 1);
        assertTrue(modulePath.startsWith("/data/libraries/cpw/mods/bootstraplauncher/"), modulePath);
        assertTrue(modulePath.contains(":/data/libraries/cpw/mods/securejarhandler/"), modulePath);

        for (String argument : jvm) {
            assertFalse(LaunchArguments.hasUnresolvedPlaceholder(argument), argument);
        }
    }

    @Test
    void gameArgumentsCarryTheFmlLaunchTarget() {
        List<String> game = LaunchArguments.resolve(neoForge.arguments.game, variables());

        assertEquals("forgeclient", game.get(game.indexOf("--launchTarget") + 1));
        assertEquals("21.1.259", game.get(game.indexOf("--fml.neoForgeVersion") + 1));
        assertEquals("1.21.1", game.get(game.indexOf("--fml.mcVersion") + 1));
    }

    @Test
    void ruleGatedArgumentsFollowTheDevice() {
        Object[] arguments = new Gson().fromJson("[\"-plain\","
                + "{\"rules\":[{\"action\":\"allow\",\"os\":{\"name\":\"osx\"}}],\"value\":\"-mac\"},"
                + "{\"rules\":[{\"action\":\"allow\",\"os\":{\"name\":\"linux\"}}],\"value\":[\"-a\",\"-b\"]},"
                + "{\"rules\":[{\"action\":\"allow\",\"os\":{\"name\":\"windows\"}},{\"action\":\"allow\",\"os\":{\"arch\":\"x86\"}}],\"value\":\"-x86\"},"
                + "{\"rules\":[{\"action\":\"allow\",\"features\":{\"is_demo_user\":true}}],\"value\":\"--demo\"}]",
                Object[].class);

        assertEquals(List.of("-plain", "-a", "-b"), LaunchArguments.resolve(arguments, new HashMap<>()));
    }

    @Test
    void mergeLetsTheLoaderReplaceVanillaLibraries() {
        VersionInfo merged = VersionInfo.merge(vanilla, neoForge);

        assertEquals("neoforge-21.1.259", merged.id);
        assertEquals("cpw.mods.bootstraplauncher.BootstrapLauncher", merged.mainClass);
        assertEquals(vanilla.assetIndex.id, merged.assetIndex.id);
        assertEquals(vanilla.downloads.client.url, merged.downloads.client.url);

        // no artifact twice, otherwise the module layer sees split packages
        Set<String> keys = new HashSet<>();
        for (VersionInfo.Library library : merged.libraries) {
            assertTrue(keys.add(VersionInfo.libraryKey(library.name)), "duplicate " + library.name);
        }

        // where both define a library, the NeoForge one is used
        for (VersionInfo.Library loaderLibrary : neoForge.libraries) {
            for (VersionInfo.Library mergedLibrary : merged.libraries) {
                if (VersionInfo.libraryKey(mergedLibrary.name).equals(VersionInfo.libraryKey(loaderLibrary.name))) {
                    assertEquals(loaderLibrary.name, mergedLibrary.name);
                }
            }
        }
        assertTrue(merged.libraries.length >= neoForge.libraries.length);
    }

    @Test
    void libraryKeyIgnoresVersionButKeepsClassifier() {
        assertEquals("org.ow2.asm:asm", VersionInfo.libraryKey("org.ow2.asm:asm:9.7"));
        assertEquals("org.lwjgl:lwjgl:natives-linux", VersionInfo.libraryKey("org.lwjgl:lwjgl:3.3.3:natives-linux"));
        assertEquals("a.b:c", VersionInfo.libraryKey("a.b:c:1.0@jar"));
    }
}
