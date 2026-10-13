package pojlib.install;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.google.gson.Gson;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.File;
import java.io.FileOutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;
import java.util.zip.ZipOutputStream;

/** Vivecraft 1.12.2: its list, the jar the app carries, and how it is put together with the game and OptiFine. */
class LegacyVivecraftTest {
    private static JsonObject read(String path) throws Exception {
        return JsonParser.parseString(new String(Files.readAllBytes(new File(path).toPath()), StandardCharsets.UTF_8)).getAsJsonObject();
    }

    @Test
    void theAppCarriesTheSameListAsTheRepository() throws Exception {
        assertEquals(read("vivecraft-legacy.json"), read("src/main/assets/loaders/vivecraft-legacy.json"));
    }

    @Test
    void the1122EntryHasTheJarTheAppCarriesAndOptiFine() throws Exception {
        JsonObject entry = LegacyVivecraft.entry(read("vivecraft-legacy.json"), "1.12.2");
        assertNotNull(entry);
        String link = entry.getAsJsonObject("vivecraft").get("url").getAsString();
        // a link into the app's assets is copied from the app (DownloadUtils), it does not need the repository
        String asset = pojlib.util.download.DownloadUtils.bundledAssetPath(link);
        assertNotNull(asset, link);
        File jar = new File("src/main/assets/" + asset);
        assertTrue(jar.isFile(), "missing: " + jar);
        assertEquals(entry.getAsJsonObject("vivecraft").get("size").getAsLong(), jar.length());
        assertTrue(entry.getAsJsonObject("optifine").get("url").getAsString().startsWith("https://"), "Android wants https");
        assertNull(LegacyVivecraft.entry(read("vivecraft-legacy.json"), "1.21.1"));
    }

    @Test
    void itsLibrariesInstallLikeAVersionsOwn() throws Exception {
        VersionInfo libraries = LegacyVivecraft.libraries(LegacyVivecraft.entry(read("vivecraft-legacy.json"), "1.12.2"));
        List<String> names = new ArrayList<>();
        for (VersionInfo.Library library : libraries.libraries) {
            names.add(library.name);
            assertNotNull(library.downloads.artifact.sha1);
            assertTrue(library.downloads.artifact.url.startsWith("https://"));
        }
        Collections.sort(names);
        assertEquals(List.of("java3d:vecmath:1.3.1", "net.minecraft:launchwrapper:1.12", "org.json:json:20140107"), names);
    }

    @Test
    void theVanillaLoaderOffersWhatThereIsAVivecraftFor() throws Exception {
        JsonObject versions = read("supportedVersions-vanilla.json");
        for (com.google.gson.JsonElement v : versions.getAsJsonArray("supportedVersions")) {
            assertNotNull(LegacyVivecraft.entry(read("vivecraft-legacy.json"), v.getAsString()), v.getAsString());
        }
    }

    private static File jar(File dir, String name, String... entries) throws Exception {
        File file = new File(dir, name);
        try (ZipOutputStream out = new ZipOutputStream(new FileOutputStream(file))) {
            for (int i = 0; i < entries.length; i += 2) {
                out.putNextEntry(new ZipEntry(entries[i]));
                out.write(entries[i + 1].getBytes(StandardCharsets.UTF_8));
                out.closeEntry();
            }
        }
        return file;
    }

    private static String text(ZipFile zip, String name) throws Exception {
        ZipEntry entry = zip.getEntry(name);
        return entry == null ? null : new String(zip.getInputStream(entry).readAllBytes(), StandardCharsets.UTF_8);
    }

    @Test
    void vivecraftGoesOverOptiFineWhichGoesOverTheGame(@TempDir File dir) throws Exception {
        File vanilla = jar(dir, "client.jar",
                "META-INF/MANIFEST.MF", "Manifest-Version: 1.0\nName: bib.class\nSHA-256-Digest: x\n",
                "META-INF/MOJANGCS.SF", "signature", "META-INF/MOJANGCS.RSA", "key",
                "bib.class", "vanilla", "bhz.class", "vanilla", "pack.png", "vanilla");
        File optifine = jar(dir, "optifine.jar", "bib.class", "optifine", "bhz.class", "optifine", "net/optifine/Config.class", "optifine");
        File vivecraft = jar(dir, "vivecraft.jar", "bib.class", "vivecraft", "org/vivecraft/provider/MCOpenVR.class", "vivecraft");
        File merged = new File(dir, "client-vivecraft.jar");
        LegacyVivecraft.merge(vanilla, optifine, vivecraft, merged);
        try (ZipFile zip = new ZipFile(merged)) {
            assertEquals("vivecraft", text(zip, "bib.class"));
            assertEquals("optifine", text(zip, "bhz.class"));
            assertEquals("vanilla", text(zip, "pack.png"));
            assertEquals("optifine", text(zip, "net/optifine/Config.class"));
            assertEquals("vivecraft", text(zip, "org/vivecraft/provider/MCOpenVR.class"));
            // Mojang's signature would make the changed classes fail to load
            assertNull(zip.getEntry("META-INF/MOJANGCS.SF"));
            assertNull(zip.getEntry("META-INF/MOJANGCS.RSA"));
            assertFalse(text(zip, "META-INF/MANIFEST.MF").contains("Digest"));
            assertEquals(6, Collections.list(zip.entries()).size()); // the manifest and the five classes and files
        }
        assertFalse(new File(dir, "client-vivecraft.jar.part").exists());
    }

    @Test
    void librariesForAnotherOsOrOfOnlyNativesAreLeftOut() {
        Gson gson = new Gson();
        VersionInfo.Library macOnly = gson.fromJson("{\"name\":\"ca.weblite:java-objc-bridge:1.0.0\",\"downloads\":{\"artifact\":{\"path\":\"a\"}},"
                + "\"rules\":[{\"action\":\"allow\",\"os\":{\"name\":\"osx\"}}]}", VersionInfo.Library.class);
        VersionInfo.Library notMac = gson.fromJson("{\"name\":\"x:y:1\",\"downloads\":{\"artifact\":{\"path\":\"a\"}},"
                + "\"rules\":[{\"action\":\"allow\"},{\"action\":\"disallow\",\"os\":{\"name\":\"osx\"}}]}", VersionInfo.Library.class);
        VersionInfo.Library everywhere = gson.fromJson("{\"name\":\"x:y:1\",\"downloads\":{\"artifact\":{\"path\":\"a\"}}}", VersionInfo.Library.class);
        VersionInfo.Library nativesOnly = gson.fromJson("{\"name\":\"net.java.jinput:jinput-platform:2.0.5\",\"downloads\":{\"classifiers\":{}}}", VersionInfo.Library.class);
        VersionInfo.Library modLibrary = gson.fromJson("{\"name\":\"x:y:1\",\"url\":\"https://maven\"}", VersionInfo.Library.class);
        assertFalse(macOnly.appliesToLinux());
        assertTrue(notMac.appliesToLinux());
        assertTrue(everywhere.appliesToLinux() && !everywhere.nativesOnly());
        assertTrue(nativesOnly.nativesOnly());
        assertFalse(modLibrary.nativesOnly());
    }

    @Test
    void oldVersionsGetJavaOpenedUp() {
        assertArrayEquals(new String[]{"--add-opens", "java.base/java.lang=ALL-UNNAMED", "--add-opens", "java.base/java.nio=ALL-UNNAMED",
                "--add-opens", "java.base/java.util=ALL-UNNAMED"}, LegacyVivecraft.JVM_ARGS);
    }
}
