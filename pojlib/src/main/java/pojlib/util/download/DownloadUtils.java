package pojlib.util.download;

import org.apache.commons.codec.binary.Hex;
import org.apache.commons.codec.digest.DigestUtils;
import org.apache.commons.io.IOUtils;

import pojlib.API;
import pojlib.util.Constants;
import pojlib.util.Logger;

import javax.net.ssl.SSLException;
import java.io.*;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.file.Files;
import java.util.Objects;
import javax.annotation.Nullable;

public class DownloadUtils {
    private static void download(URL url, OutputStream os, long size) throws IOException {
        final int MAX_RETRIES = 3;
        int attempts = 0;

        while (true) {
            try {
                HttpURLConnection conn = (HttpURLConnection) url.openConnection();
                conn.setRequestProperty("User-Agent", "QuestCraft");
                conn.setConnectTimeout(10000);
                conn.setDoInput(true);
                conn.connect();

                if (conn.getResponseCode() == HttpURLConnection.HTTP_OK) {
                    if(size == -1) {
                        size = conn.getContentLengthLong();
                    }

                    try (InputStream is = new StreamDL(conn.getInputStream(), size)) {
                        IOUtils.copy(is, os);
                    }
                    return;
                }
            } catch (IOException e) {
                if (++attempts >= MAX_RETRIES || e instanceof SSLException) {
                    throw new IOException("Unable to download from " + url, e);
                }
            }
        }
    }

    public static void downloadFile(String url, File out) throws IOException {
        downloadFile(url, out, -1);
    }

    // Some files are bundled in the app (the NeoForge builds of Vivecraft, the patched ModernFix): their download links
    // point at the same files in the repository, which only works once it is public, and the app has them anyway.
    // They are stored as .jar.bin: the build tools would otherwise open the jars inside the AAR and trip over their
    // Java 21 classes.
    private static final String BUNDLED_MARKER = "/pojlib/src/main/assets/";

    /** The asset a download link stands for, or null if the link is of anything else. */
    public static String bundledAssetPath(String url) {
        int at = url.indexOf(BUNDLED_MARKER);
        return at < 0 ? null : url.substring(at + BUNDLED_MARKER.length());
    }

    private static boolean copyBundled(String url, File out) {
        String path = bundledAssetPath(url);
        if (path == null || Constants.APP_CONTEXT == null) return false;
        try (InputStream is = Constants.APP_CONTEXT.getAssets().open(path);
             OutputStream os = new BufferedOutputStream(Files.newOutputStream(out.toPath()))) {
            IOUtils.copy(is, os);
            return true;
        } catch (IOException e) {
            Logger.getInstance().appendToLog("Bundled file " + path + " not available, downloading: " + e.getMessage());
            return false;
        }
    }

    public static void downloadFile(String url, File out, long size) throws IOException {
        Objects.requireNonNull(out.getParentFile()).mkdirs();
        if (copyBundled(url, out)) return;
        File tempOut = File.createTempFile(out.getName(), ".part", out.getParentFile());
        try {
            try (OutputStream bos2 = new BufferedOutputStream(Files.newOutputStream(tempOut.toPath()))) {
                download(new URL(url), bos2, size);
                tempOut.renameTo(out);
                bos2.close();
                if (tempOut.exists()) tempOut.delete();
            } catch (IOException th2) {
                if (tempOut.exists()) tempOut.delete();
                throw th2;
            }
        } catch (IOException e) {
            if (tempOut.exists()) tempOut.delete();
            throw e;
        }
    }

    public static boolean compareSHA1(File f, @Nullable String sourceSHA) {
        try {
            String sha1_dst;
            try (InputStream is = Files.newInputStream(f.toPath())) {
                sha1_dst = new String(Hex.encodeHex(DigestUtils.sha1(is)));
            }
            if (sourceSHA != null) return sha1_dst.equalsIgnoreCase(sourceSHA);
            else return true; // No hash provided

        } catch (IOException e) {
            Logger.getInstance().appendToLog("Issue while comparing SHA1: " + e);
            return false;
        }
    }
}
