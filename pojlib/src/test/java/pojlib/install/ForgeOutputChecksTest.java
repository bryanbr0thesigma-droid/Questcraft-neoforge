package pojlib.install;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.google.gson.Gson;
import com.google.gson.JsonObject;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.File;
import java.io.FileOutputStream;
import java.nio.charset.StandardCharsets;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;
import java.util.zip.ZipOutputStream;

public class ForgeOutputChecksTest {
    private static final String PROFILE = "{\"data\":{\"MC_OFF_SHA\":{\"client\":\"'30a7'\"}},\"processors\":["
            + "{\"jar\":\"a\",\"outputs\":{\"{MC_OFF}\":\"{MC_OFF_SHA}\"},\"args\":[\"x\"]},"
            + "{\"jar\":\"b\",\"args\":[\"y\"]}]}";

    @TempDir File tmp;

    @Test
    public void processorsLoseTheirOutputsButKeepTheRest() {
        JsonObject profile = new Gson().fromJson(ForgeInstaller.stripProcessorOutputs(PROFILE), JsonObject.class);
        JsonObject first = profile.getAsJsonArray("processors").get(0).getAsJsonObject();
        assertNull(first.get("outputs"));
        assertEquals("a", first.get("jar").getAsString());
        assertEquals("x", first.getAsJsonArray("args").get(0).getAsString());
        assertEquals("'30a7'", profile.getAsJsonObject("data").getAsJsonObject("MC_OFF_SHA").get("client").getAsString());
    }

    @Test
    public void profileWithoutProcessorsIsKept() {
        assertEquals("{\"data\":{}}", ForgeInstaller.stripProcessorOutputs("{\"data\":{}}"));
    }

    @Test
    public void recognizesSignatureFiles() {
        assertTrue(ForgeInstaller.isSignatureFile("META-INF/FORGE.SF"));
        assertTrue(ForgeInstaller.isSignatureFile("META-INF/forge.rsa"));
        assertFalse(ForgeInstaller.isSignatureFile("META-INF/MANIFEST.MF"));
        assertFalse(ForgeInstaller.isSignatureFile("data/client.lzma"));
        assertFalse(ForgeInstaller.isSignatureFile("META-INF/services/x.SF"));
    }

    @Test
    public void rewrittenInstallerKeepsItsFilesAndDropsSignatureAndOutputs() throws Exception {
        File installer = new File(tmp, "installer.jar");
        try (ZipOutputStream out = new ZipOutputStream(new FileOutputStream(installer))) {
            put(out, "META-INF/MANIFEST.MF", "Manifest-Version: 1.0\n");
            put(out, "META-INF/FORGE.SF", "sig");
            put(out, "META-INF/FORGE.RSA", "rsa");
            put(out, "install_profile.json", PROFILE);
            put(out, "data/client.lzma", "payload");
            out.putNextEntry(new ZipEntry("data/"));
            out.closeEntry();
        }

        ForgeInstaller.relaxOutputChecks(installer);

        assertFalse(new File(installer.getPath() + ".tmp").exists());
        try (ZipFile zip = new ZipFile(installer)) {
            assertNotNull(zip.getEntry("META-INF/MANIFEST.MF"));
            assertNull(zip.getEntry("META-INF/FORGE.SF"));
            assertNull(zip.getEntry("META-INF/FORGE.RSA"));
            assertNotNull(zip.getEntry("data/"));
            assertEquals("payload", read(zip, "data/client.lzma"));
            assertFalse(read(zip, "install_profile.json").contains("outputs"));
        }
    }

    private static void put(ZipOutputStream out, String name, String content) throws Exception {
        out.putNextEntry(new ZipEntry(name));
        out.write(content.getBytes(StandardCharsets.UTF_8));
        out.closeEntry();
    }

    private static String read(ZipFile zip, String name) throws Exception {
        try (java.io.InputStream in = zip.getInputStream(zip.getEntry(name))) {
            return new String(org.apache.commons.io.IOUtils.toByteArray(in), StandardCharsets.UTF_8);
        }
    }
}
