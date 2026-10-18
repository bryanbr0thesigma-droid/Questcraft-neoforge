package questcraft.legacy;

import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.Proxy;
import java.net.URL;
import java.net.URLConnection;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.HashMap;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import javax.imageio.ImageIO;

/**
 * Skins and capes for the old versions, as Prism Launcher's legacy launcher fixes them: the old versions download
 * them from servers that are gone (Amazon S3's MinecraftSkins and MinecraftCloaks, skins.minecraft.net,
 * minecraft.net's skin/ and cloak/), so those downloads ({@code URL.openConnection} in the game's classes, through
 * {@link Transformer}) get the player's skin and cape from Mojang's profile service instead. The skin comes in the
 * old 64 x 32 layout: the top half of today's 64 x 64 skins, a slim (3 pixel) arm widened to the old 4 pixels. A name
 * Mojang does not know, or no network, gets the old answer (nothing), and the game draws its default skin.
 */
public final class SkinFix {
    private static final Pattern SKIN = Pattern.compile("^https?://(?:s3\\.amazonaws\\.com|skins\\.minecraft\\.net)/MinecraftSkins/([^/?]+)\\.png$|"
            + "^https?://(?:www\\.)?minecraft\\.net/skin/([^/?]+)\\.png$");
    private static final Pattern CAPE = Pattern.compile("^https?://(?:s3\\.amazonaws\\.com|skins\\.minecraft\\.net)/MinecraftCloaks/([^/?]+)\\.png$|"
            + "^https?://(?:www\\.)?minecraft\\.net/cloak/get\\.jsp\\?user=([^&]+)$");
    private static final int TIMEOUT = 10000;
    /** What each name's skin and cape came to (null: none), so a texture is fetched once a game */
    private static final Map<String, byte[]> cache = new HashMap<>();

    private SkinFix() {
    }

    /** URL.openConnection() in the game's classes. */
    public static URLConnection openConnection(URL url) throws IOException {
        URLConnection fixed = fix(url);
        return fixed != null ? fixed : url.openConnection();
    }

    /** URL.openConnection(Proxy) in the game's classes (1.6 downloads skins so). */
    public static URLConnection openConnection(URL url, Proxy proxy) throws IOException {
        URLConnection fixed = fix(url);
        return fixed != null ? fixed : url.openConnection(proxy);
    }

    /** A connection answering with the skin or cape for an old skin or cape address, null for any other address. */
    static URLConnection fix(URL url) {
        String key = target(url.toString());
        if (key == null) return null;
        boolean isSkin = key.startsWith("skin ");
        String name = key.substring(5);
        byte[] png;
        synchronized (cache) {
            png = cache.get(key);
        }
        if (png == null) {
            png = fetch(name, isSkin);
            synchronized (cache) {
                cache.put(key, png);
            }
        }
        return new Answer(url, png);
    }

    /** "skin NAME" or "cape NAME" for an old skin or cape address, null for any other. */
    static String target(String address) {
        Matcher skin = SKIN.matcher(address), cape = CAPE.matcher(address);
        boolean isSkin = skin.matches();
        if (!isSkin && !cape.matches()) return null;
        Matcher m = isSkin ? skin : cape;
        return (isSkin ? "skin " : "cape ") + (m.group(1) != null ? m.group(1) : m.group(2));
    }

    /** The name's skin (in the old layout) or cape as a PNG, an empty array if there is none. */
    private static byte[] fetch(String name, boolean skin) {
        try {
            String profile = read("https://api.mojang.com/users/profiles/minecraft/" + name);
            String id = group("\"id\"\\s*:\\s*\"([0-9a-fA-F-]+)\"", profile);
            if (id == null) return new byte[0];
            String session = read("https://sessionserver.mojang.com/session/minecraft/profile/" + id);
            String value = group("\"value\"\\s*:\\s*\"([^\"]+)\"", session);
            if (value == null) return new byte[0];
            String textures = new String(Base64.getDecoder().decode(value), StandardCharsets.UTF_8).replace("\\/", "/");
            String part = group("\"" + (skin ? "SKIN" : "CAPE") + "\"\\s*:\\s*\\{([^}]*(?:\\{[^}]*\\}[^}]*)?)\\}", textures);
            String address = part != null ? group("\"url\"\\s*:\\s*\"([^\"]+)\"", part) : null;
            if (address == null) return new byte[0];
            // the textures server answers on https too
            address = address.replaceFirst("^http://", "https://");
            BufferedImage image;
            try (InputStream in = open(address).getInputStream()) {
                image = ImageIO.read(in);
            }
            if (image == null) return new byte[0];
            if (skin) image = oldLayout(image, part.matches("(?s).*\"model\"\\s*:\\s*\"slim\".*"));
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            ImageIO.write(image, "png", out);
            System.out.println("QuestCraft legacy launcher: " + (skin ? "skin" : "cape") + " of " + name + " from Mojang");
            return out.toByteArray();
        } catch (Throwable t) {
            System.out.println("QuestCraft legacy launcher: no " + (skin ? "skin" : "cape") + " for " + name + ": " + t);
            return new byte[0];
        }
    }

    /**
     * A skin in the old 64 x 32 layout: today's top half (the old versions draw no second layer and mirror the right
     * arm and leg for the left ones), a slim right arm widened from 3 to 4 pixels.
     */
    static BufferedImage oldLayout(BufferedImage image, boolean slim) {
        int scale = Math.max(1, image.getWidth() / 64);
        BufferedImage old = new BufferedImage(64, 32, BufferedImage.TYPE_INT_ARGB);
        for (int y = 0; y < 32; y++) {
            for (int x = 0; x < 64; x++) old.setRGB(x, y, image.getRGB(x * scale, y * scale));
        }
        if (slim) {
            // the right arm's faces (an arm 3 wide, 12 high, 4 deep at 40, 16): top, bottom, right, front, left, back
            BufferedImage arm = new BufferedImage(16, 16, BufferedImage.TYPE_INT_ARGB);
            for (int y = 0; y < 16; y++) {
                for (int x = 0; x < 16; x++) arm.setRGB(x, y, old.getRGB(40 + x, 16 + y));
            }
            for (int y = 0; y < 16; y++) {
                for (int x = 0; x < 16; x++) old.setRGB(40 + x, 16 + y, 0);
            }
            widen(arm, old, 4, 0, 3, 4, 44, 16);     // top
            widen(arm, old, 7, 0, 3, 4, 48, 16);     // bottom
            copy(arm, old, 0, 4, 4, 12, 40, 20);     // right side
            widen(arm, old, 4, 4, 3, 12, 44, 20);    // front
            copy(arm, old, 7, 4, 4, 12, 48, 20);     // left side
            widen(arm, old, 11, 4, 3, 12, 52, 20);   // back
        }
        return old;
    }

    /** A face 3 pixels wide made 4 wide (its middle column twice). */
    private static void widen(BufferedImage from, BufferedImage to, int x, int y, int w, int h, int toX, int toY) {
        int[] columns = {0, 1, 1, 2};
        for (int row = 0; row < h; row++) {
            for (int c = 0; c < 4; c++) to.setRGB(toX + c, toY + row, from.getRGB(x + columns[c], y + row));
        }
    }

    private static void copy(BufferedImage from, BufferedImage to, int x, int y, int w, int h, int toX, int toY) {
        for (int row = 0; row < h; row++) {
            for (int c = 0; c < w; c++) to.setRGB(toX + c, toY + row, from.getRGB(x + c, y + row));
        }
    }

    private static HttpURLConnection open(String address) throws IOException {
        HttpURLConnection connection = (HttpURLConnection) new URL(address).openConnection();
        connection.setConnectTimeout(TIMEOUT);
        connection.setReadTimeout(TIMEOUT);
        return connection;
    }

    /** The body of a GET, empty when the answer is not 200 (Mojang answers 204 or 404 for an unknown name). */
    private static String read(String address) throws IOException {
        HttpURLConnection connection = open(address);
        try {
            if (connection.getResponseCode() != 200) return "";
            try (InputStream in = connection.getInputStream()) {
                ByteArrayOutputStream out = new ByteArrayOutputStream();
                byte[] buffer = new byte[4096];
                for (int n; (n = in.read(buffer)) > 0; ) out.write(buffer, 0, n);
                return new String(out.toByteArray(), StandardCharsets.UTF_8);
            }
        } finally {
            connection.disconnect();
        }
    }

    private static String group(String regex, String text) {
        Matcher m = Pattern.compile(regex).matcher(text);
        return m.find() ? m.group(1) : null;
    }

    /** The answer to the game's request: 200 and the picture, or 404 when there is none. */
    private static final class Answer extends HttpURLConnection {
        private final byte[] png;

        Answer(URL url, byte[] png) {
            super(url);
            this.png = png;
            responseCode = png.length > 0 ? 200 : 404;
        }

        @Override
        public void connect() {
            connected = true;
        }

        @Override
        public void disconnect() {
        }

        @Override
        public boolean usingProxy() {
            return false;
        }

        @Override
        public int getResponseCode() {
            return responseCode;
        }

        @Override
        public InputStream getInputStream() throws IOException {
            if (png.length == 0) throw new java.io.FileNotFoundException(url.toString());
            return new ByteArrayInputStream(png);
        }

        @Override
        public String getContentType() {
            return png.length > 0 ? "image/png" : null;
        }

        @Override
        public int getContentLength() {
            return png.length;
        }
    }
}
