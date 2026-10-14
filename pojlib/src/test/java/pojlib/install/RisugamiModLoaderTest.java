package pojlib.install;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.junit.jupiter.api.io.TempDir;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;
import java.util.zip.ZipOutputStream;

/** Risugami's ModLoader: its list of builds, how a build is read and put into the game, and how the game is started. */
class RisugamiModLoaderTest {
    private static JsonObject read(String path) throws IOException {
        return JsonParser.parseString(new String(Files.readAllBytes(new File(path).toPath()), StandardCharsets.UTF_8)).getAsJsonObject();
    }

    private static void zip(File file, String... namesAndContents) throws IOException {
        try (ZipOutputStream out = new ZipOutputStream(new FileOutputStream(file))) {
            for (int i = 0; i < namesAndContents.length; i += 2) {
                out.putNextEntry(new ZipEntry(namesAndContents[i]));
                out.write(namesAndContents[i + 1].getBytes(StandardCharsets.UTF_8));
                out.closeEntry();
            }
        }
    }

    private static List<String> versionNames(JsonObject list) {
        List<String> names = new ArrayList<>();
        for (JsonElement v : list.getAsJsonArray("versions")) names.add(v.getAsJsonObject().get("name").getAsString());
        return names;
    }

    @Test
    void theAppCarriesTheSameListsAsTheRepository() throws Exception {
        for (String file : new String[]{"modloader-builds.json", "mods-modloader.json", "devmods-modloader.json", "supportedVersions-modloader.json"}) {
            assertEquals(read(file), read("src/main/assets/loaders/" + file), file);
        }
    }

    @Test
    void everyBuildCanBeDownloadedAndChecked() throws Exception {
        JsonObject builds = read("modloader-builds.json");
        Set<String> names = new HashSet<>();
        for (JsonElement element : builds.getAsJsonArray("versions")) {
            JsonObject v = element.getAsJsonObject();
            String name = v.get("name").getAsString();
            assertTrue(names.add(name), "listed twice: " + name);
            String file = v.get("file").getAsString();
            String format = v.get("format").getAsString();
            assertTrue(format.equals("zip") || format.equals("rar"), name);
            assertTrue(file.toLowerCase().endsWith("." + format), name);
            assertTrue(v.get("sha256").getAsString().matches("[0-9a-f]{64}"), name);
            assertTrue(v.get("size").getAsLong() > 0, name);
            assertTrue(v.has("url") || v.has("mediafire"), name + " has nowhere to be downloaded from");
            if (v.has("url")) assertTrue(v.get("url").getAsString().startsWith("https://"), name);
            if (v.has("mediafire")) assertTrue(v.get("mediafire").getAsString().startsWith("https://www.mediafire.com/"), name);
        }
        // alpha, beta and release builds are all there
        assertTrue(names.containsAll(Arrays.asList("a1.2.6", "b1.7.3", "1.2.5", "1.5.2", "1.6.2")), names.toString());
    }

    @Test
    void theOfferedVersionsHaveABuildAndAModList() throws Exception {
        JsonObject builds = read("modloader-builds.json");
        List<String> offered = new ArrayList<>();
        for (JsonElement v : read("supportedVersions-modloader.json").getAsJsonArray("supportedVersions")) offered.add(v.getAsString());
        assertFalse(offered.isEmpty());
        for (String version : offered) assertNotNull(HostedList.entry(builds, version), version);
        assertEquals(offered, versionNames(read("mods-modloader.json")));
        assertEquals(offered, versionNames(read("devmods-modloader.json")));
    }

    @Test
    void theFileAddressIsReadFromMediafiresPageOverHttps() {
        String page = "<a class=\"input popsok\"\n aria-label=\"Download file\"\n href=\"http://download2388.mediafire.com/0ywto/vb5bq3aoitdkgx8/ModLoader+B1.7.3.zip\"\n id=\"downloadButton\">";
        assertEquals("https://download2388.mediafire.com/0ywto/vb5bq3aoitdkgx8/ModLoader+B1.7.3.zip", RisugamiModLoader.mediafireLink(page));
        assertNull(RisugamiModLoader.mediafireLink("<html>no file here</html>"));
        assertNull(RisugamiModLoader.mediafireLink(null));
    }

    @Test
    void aBuildIsReadFromTheFolderModLoaderIsIn(@TempDir File dir) throws Exception {
        File plain = new File(dir, "ModLoader B1.7.3.zip");
        zip(plain, "ModLoader.class", "ml", "BaseMod.class", "base", "xq.class", "changed");
        assertEquals(new HashSet<>(Arrays.asList("ModLoader.class", "BaseMod.class", "xq.class")), RisugamiModLoader.readArchive(plain).keySet());

        File wrapped = new File(dir, "repacked.zip");
        zip(wrapped, "ModLoader 1.2.5/ModLoader.class", "ml", "ModLoader 1.2.5/vx.class", "changed", "readme.txt", "outside");
        Map<String, byte[]> files = RisugamiModLoader.readArchive(wrapped);
        assertEquals(new HashSet<>(Arrays.asList("ModLoader.class", "vx.class")), files.keySet());
        assertEquals("changed", new String(files.get("vx.class"), StandardCharsets.UTF_8));

        File notModLoader = new File(dir, "other.zip");
        zip(notModLoader, "mod_Something.class", "x");
        assertThrows(IOException.class, () -> RisugamiModLoader.readArchive(notModLoader));
    }

    @Test
    void modLoadersClassesReplaceTheGamesAndTheSignatureGoes(@TempDir File dir) throws Exception {
        File vanilla = new File(dir, "client.jar");
        zip(vanilla, "META-INF/MANIFEST.MF", "signed", "META-INF/MOJANG_C.SF", "sig", "META-INF/MOJANG_C.DSA", "sig",
                "xq.class", "vanilla", "terrain.png", "png", "net/minecraft/client/Minecraft.class", "main");
        File modLoader = new File(dir, "ModLoader B1.7.3.zip");
        zip(modLoader, "ModLoader.class", "ml", "xq.class", "changed");
        File merged = new File(dir, "out/client-modloader.jar");

        RisugamiModLoader.merge(vanilla, modLoader, merged);

        try (ZipFile zip = new ZipFile(merged)) {
            List<String> names = new ArrayList<>();
            for (ZipEntry e : Collections.list(zip.entries())) names.add(e.getName());
            assertEquals(new HashSet<>(Arrays.asList("META-INF/MANIFEST.MF", "ModLoader.class", "xq.class", "terrain.png",
                    "net/minecraft/client/Minecraft.class")), new HashSet<>(names));
            assertEquals(names.size(), new HashSet<>(names).size(), "an entry twice");
            assertEquals("changed", new String(readAll(zip, "xq.class"), StandardCharsets.UTF_8));
            assertEquals("main", new String(readAll(zip, "net/minecraft/client/Minecraft.class"), StandardCharsets.UTF_8));
            assertFalse(new String(readAll(zip, "META-INF/MANIFEST.MF"), StandardCharsets.UTF_8).contains("signed"));
        }
        assertFalse(new File(dir, "out/client-modloader.jar.part").exists());
    }

    private static byte[] readAll(ZipFile zip, String name) throws IOException {
        return pojlib.util.FileUtil.read(zip.getInputStream(zip.getEntry(name))).getBytes(StandardCharsets.UTF_8);
    }

    @Test
    void theLegacyLauncherStartsTheGameFromItsOwnClassPath() {
        VersionInfo beta = new VersionInfo();
        beta.id = "b1.7.3";
        beta.mainClass = "net.minecraft.launchwrapper.Launch";
        VersionInfo v16 = new VersionInfo();
        v16.id = "1.6.2";
        v16.mainClass = "net.minecraft.client.main.Main";
        assertEquals("net.minecraft.client.Minecraft", RisugamiModLoader.gameMain(beta));
        assertEquals("net.minecraft.client.main.Main", RisugamiModLoader.gameMain(v16));

        List<String> args = Arrays.asList(RisugamiModLoader.jvmArgs(beta, "/g/client-modloader.jar:/g/jutils.jar"));
        assertTrue(args.contains("-Dquestcraft.legacy.classpath=/g/client-modloader.jar:/g/jutils.jar"), args.toString());
        assertTrue(args.contains("-Dquestcraft.legacy.main=net.minecraft.client.Minecraft"), args.toString());
        // ModLoader sets fields of the game by reflection and the old network code reaches into java.net
        for (String open : new String[]{"java.base/java.lang=ALL-UNNAMED", "java.base/java.lang.reflect=ALL-UNNAMED", "java.base/java.net=ALL-UNNAMED"}) {
            int i = args.indexOf(open);
            assertTrue(i > 0 && args.get(i - 1).equals("--add-opens"), open);
        }
        assertTrue(Arrays.asList(RisugamiModLoader.jvmArgs(v16, "x")).contains("-Dquestcraft.legacy.main=net.minecraft.client.main.Main"));
    }

    @Test
    void theAppCarriesTheLegacyLauncher() throws Exception {
        File jar = new File("src/main/assets/" + RisugamiModLoader.LAUNCHER_ASSET);
        try (ZipFile zip = new ZipFile(jar)) {
            assertNotNull(zip.getEntry(RisugamiModLoader.LAUNCHER_MAIN.replace('.', '/') + ".class"));
            // ASM is inside: the game's classes are rewritten while they load
            assertNotNull(zip.getEntry("org/objectweb/asm/ClassReader.class"));
        }
    }

    /**
     * With QC_MODLOADER_ARCHIVES (modloader-builder/work/archives, written by make_registry.py): every build in the
     * list is read the way the headset reads it, RAR ones included. With QC_MINECRAFT_CLIENTS too (a folder with
     * <version>.jar, Mojang's clients) each is put into its client in QC_MODLOADER_OUT, for test_versions.sh.
     */
    @Test
    @EnabledIfEnvironmentVariable(named = "QC_MODLOADER_ARCHIVES", matches = ".+")
    void everyRealBuildIsReadAndPutIntoItsClient() throws Exception {
        File archives = new File(System.getenv("QC_MODLOADER_ARCHIVES"));
        String clients = System.getenv("QC_MINECRAFT_CLIENTS");
        String out = System.getenv("QC_MODLOADER_OUT");
        List<String> problems = new ArrayList<>();
        int merged = 0;
        for (JsonElement element : read("modloader-builds.json").getAsJsonArray("versions")) {
            JsonObject v = element.getAsJsonObject();
            String name = v.get("name").getAsString();
            File archive = new File(archives, v.get("file").getAsString());
            try {
                if (!v.get("sha256").getAsString().equals(RisugamiModLoader.sha256(archive))) throw new IOException("not the listed file");
                Map<String, byte[]> files = RisugamiModLoader.readArchive(archive);
                if (!files.containsKey("BaseMod.class")) throw new IOException("no BaseMod.class");
                if (clients != null && out != null) {
                    File client = new File(clients, name + ".jar");
                    if (client.exists()) {
                        RisugamiModLoader.merge(client, archive, new File(out, name + ".jar"));
                        merged++;
                    }
                }
            } catch (Exception e) {
                problems.add(name + ": " + e);
            }
        }
        System.out.println("ModLoader builds read, " + merged + " put into their client");
        assertEquals(Collections.emptyList(), problems);
    }
}
