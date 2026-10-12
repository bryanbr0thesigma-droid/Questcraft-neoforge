package pojlib.util.json;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;

class VivecraftConfigTest {
    @Test
    void theShippedConfigIsMadeOpenXrAndVrOn() throws Exception {
        JsonObject config = JsonParser.parseString(new String(
                Files.readAllBytes(Paths.get("src/main/assets/vivecraft-client-config.json")), StandardCharsets.UTF_8)).getAsJsonObject();
        // the file as shipped has VR off, it only works because the launcher edits it before every start
        assertEquals("false", config.get("vrEnabled").getAsString());
        MinecraftInstances.applyQuestSettings(config);
        assertEquals("true", config.get("vrEnabled").getAsString());
        assertEquals("OPENXR", config.get("stereoProviderPluginID").getAsString());
        assertEquals("false", config.get("seated").getAsString());
    }

    @Test
    void modernFixDoesNotAskForOtherPerformanceMods() throws Exception {
        String properties = new String(Files.readAllBytes(Paths.get("src/main/assets/modernfix-mixins.properties")), StandardCharsets.UTF_8);
        assertTrue(properties.contains("\nmixin.feature.warn_missing_perf_mods=false"));
    }
}
