package pojlib.install;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.google.gson.Gson;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import org.junit.jupiter.api.Test;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.List;
import java.util.stream.Collectors;

class HorizonInstallerTest {
    private static String library(String name, String rules) {
        return "{\"name\":\"" + name + "\",\"downloads\":{\"artifact\":{\"path\":\"" + name.replace(':', '/')
                + ".jar\",\"sha1\":\"0\",\"size\":1,\"url\":\"https://libraries.minecraft.net/x.jar\"}}"
                + (rules == null ? "" : ",\"rules\":" + rules) + "}";
    }

    @Test
    void takesTheLibrariesTheOfficialLauncherInstallsOnArmLinux() {
        String linux = "[{\"action\":\"allow\",\"os\":{\"name\":\"linux\"}}]";
        String windows = "[{\"action\":\"allow\",\"os\":{\"name\":\"windows\"}}]";
        String notOsx = "[{\"action\":\"allow\"},{\"action\":\"disallow\",\"os\":{\"name\":\"osx\"}}]";
        String json = "{\"id\":\"26.3\",\"libraries\":["
                + library("com.mojang:brigadier:1.3.10", null) + ","
                + library("org.lwjgl:lwjgl:3.3.3", null) + ","
                + library("org.lwjgl:lwjgl:3.3.3:natives-linux", linux) + ","
                + library("org.lwjgl:lwjgl:3.3.3:natives-linux-arm64", linux) + ","
                + library("org.lwjgl:lwjgl:3.3.3:natives-windows", windows) + ","
                + library("io.netty:netty-transport-native-epoll:4.2.16.Final:linux-x86_64", linux) + ","
                + library("io.netty:netty-transport-native-epoll:4.2.16.Final:linux-aarch_64", linux) + ","
                + library("ca.weblite:java-objc-bridge:1.1", notOsx)
                + "]}";
        VersionInfo info = new Gson().fromJson(json, VersionInfo.class);
        List<String> names = HorizonInstaller.libraries(info).stream().map(l -> l.name).collect(Collectors.toList());
        assertEquals(List.of(
                "com.mojang:brigadier:1.3.10",
                "org.lwjgl:lwjgl:3.3.3",
                "org.lwjgl:lwjgl:3.3.3:natives-linux-arm64",
                "io.netty:netty-transport-native-epoll:4.2.16.Final:linux-aarch_64",
                "ca.weblite:java-objc-bridge:1.1"), names);
    }

    @Test
    void horizonIsMinecraft263Only() {
        assertTrue(HorizonInstaller.isAvailable("26.3"));
        assertFalse(HorizonInstaller.isAvailable("1.21.11"));
    }

    @Test
    void theListsOfferWhatTheInstallerInstalls() throws IOException {
        for (String dir : new String[]{"", "src/main/assets/loaders/"}) {
            JsonObject versions = read(dir + "supportedVersions-horizon.json");
            assertEquals(1, versions.getAsJsonArray("supportedVersions").size());
            assertEquals(HorizonInstaller.VERSION, versions.getAsJsonArray("supportedVersions").get(0).getAsString());
            for (String mods : new String[]{"mods-horizon.json", "devmods-horizon.json"}) {
                JsonElement version = read(dir + mods).getAsJsonArray("versions").get(0);
                assertEquals(HorizonInstaller.VERSION, version.getAsJsonObject().get("name").getAsString());
            }
        }
    }

    private static JsonObject read(String path) throws IOException {
        return JsonParser.parseString(new String(Files.readAllBytes(new File(path).toPath()), StandardCharsets.UTF_8)).getAsJsonObject();
    }
}
