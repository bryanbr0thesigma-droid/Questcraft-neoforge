package pojlib.install;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import org.junit.jupiter.api.Test;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;

/** The list of Visor builds the app carries: each entry has its jar, and it covers the versions of every loader. */
class VisorRegistryTest {
    private static JsonObject read(String path) throws Exception {
        return JsonParser.parseString(new String(Files.readAllBytes(new File(path).toPath()), StandardCharsets.UTF_8)).getAsJsonObject();
    }

    @Test
    void theAppCarriesTheSameListAsTheRepository() throws Exception {
        assertEquals(read("visor-mods.json"), read("src/main/assets/loaders/visor-mods.json"));
    }

    @Test
    void everyEntryHasItsJar() throws Exception {
        JsonObject registry = read("visor-mods.json");
        int jars = 0;
        for (JsonElement version : registry.getAsJsonArray("versions")) {
            JsonObject v = version.getAsJsonObject();
            for (String loader : new String[]{"fabric", "forge", "neoforge"}) {
                if (!v.has(loader)) continue;
                String link = v.getAsJsonObject(loader).get("download_link").getAsString();
                String file = link.substring(link.lastIndexOf('/') + 1);
                File jar = new File("visor-jars/" + loader + "/" + file);
                assertTrue(jar.isFile() && jar.length() > 1_000_000, "missing or too small: " + jar);
                assertTrue(file.equals("visor-" + loader + "-" + v.get("name").getAsString() + ".jar"), "misnamed: " + file);
                jars++;
            }
        }
        assertTrue(jars >= 21, "expected a jar of every loader for each of the 7 versions, found " + jars);
    }

    @Test
    void everyVersionOfEveryLoaderHasAVisor() throws Exception {
        JsonObject registry = read("visor-mods.json");
        for (String loader : new String[]{"fabric", "quilt", "neoforge", "forge"}) {
            JsonObject versions = read("src/main/assets/loaders/supportedVersions-" + loader + ".json");
            for (JsonElement mc : versions.getAsJsonArray("supportedVersions")) {
                String name = mc.getAsString();
                // Fabric and Quilt also list 1.21.10, 1.20.1, 1.19.x, which Visor has no build for: that is not offered, not an error
                boolean old = name.startsWith("1.19") || name.equals("1.20.1") || name.equals("1.21.10");
                if (old) continue;
                assertTrue(VisorMods.isAvailable(registry, loader, name), "no Visor for " + loader + " " + name);
            }
        }
    }
}
