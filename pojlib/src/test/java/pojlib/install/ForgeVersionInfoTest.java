package pojlib.install;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
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

/** Runs against the version json the Forge 61.2.3 installer writes for Minecraft 1.21.11. */
class ForgeVersionInfoTest {
    private static VersionInfo vanilla;
    private static VersionInfo forge;

    private static VersionInfo read(String name) throws IOException {
        try (InputStream in = ForgeVersionInfoTest.class.getResourceAsStream("/forge/" + name);
             Reader reader = new InputStreamReader(in, StandardCharsets.UTF_8)) {
            return new Gson().fromJson(reader, VersionInfo.class);
        }
    }

    @BeforeAll
    static void load() throws IOException {
        vanilla = read("vanilla-1.21.11.json");
        forge = read("forge-1.21.11-61.2.3.json");
    }

    @Test
    void profileInheritsFromVanillaAndStartsThroughForgeBootstrap() {
        assertEquals("1.21.11", forge.inheritsFrom);
        assertEquals("1.21.11-forge-61.2.3", forge.id);
        assertEquals("net.minecraftforge.bootstrap.ForgeBootstrap", forge.mainClass);
    }

    @Test
    void mergedVersionHasBothLibrarySetsAndForgesMainClass() {
        VersionInfo merged = VersionInfo.merge(vanilla, forge);
        assertEquals("1.21.11-forge-61.2.3", merged.id);
        assertEquals("net.minecraftforge.bootstrap.ForgeBootstrap", merged.mainClass);
        assertEquals(vanilla.assetIndex.id, merged.assetIndex.id);
        Set<String> names = new HashSet<>();
        for (VersionInfo.Library l : merged.libraries) names.add(l.name);
        assertTrue(names.contains("net.minecraftforge:forge:1.21.11-61.2.3:universal"));
        assertTrue(names.contains("com.mojang:brigadier:1.3.10") || names.stream().anyMatch(n -> n.startsWith("com.mojang:brigadier")));
    }

    @Test
    void launchArgumentsResolveWithoutLeftoverPlaceholders() {
        Map<String, String> variables = new HashMap<>();
        variables.put("library_directory", "/data/libraries");
        variables.put("classpath_separator", ":");
        variables.put("version_name", "client");
        List<String> jvm = LaunchArguments.resolve(forge.arguments.jvm, variables);
        List<String> game = LaunchArguments.resolve(forge.arguments.game, variables);
        assertTrue(jvm.contains("-Djava.net.preferIPv6Addresses=system"));
        assertEquals(List.of("--launchTarget", "forge_client"), game);
        for (String arg : jvm) assertFalse(arg.contains("${"), arg);
    }

    @Test
    void thePatchedClientIsOneOfTheLibrariesTheInstallerMakes() {
        VersionInfo.Library client = null;
        for (VersionInfo.Library l : forge.libraries) {
            if (l.name.equals("net.minecraftforge:forge:1.21.11-61.2.3:client")) client = l;
        }
        assertTrue(client != null, "the client library is listed");
        // it has no download address: the installer writes it, the launcher only has to find it
        assertEquals("net/minecraftforge/forge/1.21.11-61.2.3/forge-1.21.11-61.2.3-client.jar", client.downloads.artifact.path);
        assertTrue(client.downloads.artifact.url == null || client.downloads.artifact.url.isEmpty());
    }

    @Test
    void onlyThePatchedClientIsMadeByTheInstaller() {
        List<String> made = new java.util.ArrayList<>();
        for (VersionInfo info : new VersionInfo[]{vanilla, forge}) {
            for (VersionInfo.Library library : info.libraries) {
                if (library.downloads != null && library.downloads.artifact != null
                        && Installer.madeByInstaller(library.downloads.artifact)) {
                    made.add(library.name);
                }
            }
        }
        assertEquals(List.of("net.minecraftforge:forge:1.21.11-61.2.3:client"), made);
    }
}
