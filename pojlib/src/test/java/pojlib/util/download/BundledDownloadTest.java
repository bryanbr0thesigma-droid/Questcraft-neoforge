package pojlib.util.download;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.Paths;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

class BundledDownloadTest {
    private static final String REPO = "https://raw.githubusercontent.com/bryanbr0thesigma-droid/Questcraft-neoforge/claude/fervent-mayer-8idkzr";

    @Test
    void linksIntoTheAssetsFolderAreBundledFiles() {
        assertEquals("vivecraft-jars/neoforge/vivecraft-neoforge-1.21.11.jar.bin",
                DownloadUtils.bundledAssetPath(REPO + "/pojlib/src/main/assets/vivecraft-jars/neoforge/vivecraft-neoforge-1.21.11.jar.bin"));
        assertEquals("bundled-mods/neoforge/x.jar.bin",
                DownloadUtils.bundledAssetPath(REPO + "/pojlib/src/main/assets/bundled-mods/neoforge/x.jar.bin"));
    }

    @Test
    void otherLinksAreDownloaded() {
        assertNull(DownloadUtils.bundledAssetPath("https://cdn.modrinth.com/data/AANobbMI/versions/x/sodium.jar"));
    }

    /** Every bundled link in the NeoForge mod lists has its file in the assets, or the app would try to download it. */
    @Test
    void everyBundledLinkOfTheModListsHasItsFile() throws Exception {
        for (String list : new String[]{"mods-neoforge.json", "devmods-neoforge.json",
                "src/main/assets/loaders/mods-neoforge.json", "src/main/assets/loaders/devmods-neoforge.json",
                "mods-forge.json", "devmods-forge.json",
                "src/main/assets/loaders/mods-forge.json", "src/main/assets/loaders/devmods-forge.json"}) {
            JsonObject root = JsonParser.parseString(new String(Files.readAllBytes(Paths.get(list)))).getAsJsonObject();
            int bundled = 0;
            for (JsonElement version : root.getAsJsonArray("versions")) {
                for (String group : new String[]{"coreMods", "defaultMods"}) {
                    JsonArray mods = version.getAsJsonObject().getAsJsonArray(group);
                    if (mods == null) continue;
                    for (JsonElement mod : mods) {
                        String path = DownloadUtils.bundledAssetPath(mod.getAsJsonObject().get("download_link").getAsString());
                        if (path == null) continue;
                        bundled++;
                        assertTrue(new File("src/main/assets/" + path).isFile(), list + ": missing " + path);
                    }
                }
            }
            // NeoForge: the 7 Vivecraft jars and the 2 patched ModernFix jars, Forge: the 7 Vivecraft jars
            assertTrue(bundled >= (list.contains("forge.json") && !list.contains("neoforge") ? 7 : 9),
                    list + " is missing bundled jars");
        }
    }
}
