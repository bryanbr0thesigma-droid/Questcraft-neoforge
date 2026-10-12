package pojlib.install;

import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;

/** Values Minecraft rejects are replaced by its defaults without a word, brightness 50 % looks like a gray veil. */
class OptionsTxtTest {
    private static double value(String key) throws Exception {
        for (String line : Files.readAllLines(Paths.get("src/main/assets/options.txt"), StandardCharsets.UTF_8)) {
            if (line.startsWith(key + ":")) return Double.parseDouble(line.substring(key.length() + 1));
        }
        throw new AssertionError(key + " is not in options.txt");
    }

    @Test
    void brightnessIsInsideTheRangeTheGameAccepts() throws Exception {
        double gamma = value("gamma");
        assertTrue(gamma >= 0.0 && gamma <= 1.0, "gamma " + gamma);
    }

    @Test
    void simulationDistanceIsNotBelowTheMinimum() throws Exception {
        assertTrue(value("simulationDistance") >= 5, "simulation distance");
    }

    @Test
    void renderDistanceIsInTheAllowedRange() throws Exception {
        double rd = value("renderDistance");
        assertTrue(rd >= 2 && rd <= 32, "render distance " + rd);
    }
}
