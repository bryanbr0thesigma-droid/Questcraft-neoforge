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
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import javax.imageio.ImageIO;

/**
 * Skins and capes for the old versions, as Prism Launcher's legacy launcher fixes them: the old versions download
 * them from servers that are gone (Amazon S3's MinecraftSkins and MinecraftCloaks, skins.minecraft.net,
 * minecraft.net's skin/ and cloak/), so those downloads ({@code URL.openConnection} in the game's classes, through
 * {@link Transformer}) get the player's skin and cape from Mojang's profile service instead. The skin comes in the
 * old 64 x 32 layout: the top half of today's 64 x 64 skins, a slim (3 pixel) arm widened to the old 4 pixels; the
 * whole skin is kept for the VR glue's second layer ({@link #textures}). A name Mojang does not know, or no network,
 * gets the old answer (nothing), and the game draws its default skin. One lookup a name serves its skin and cape; a
 * name Mojang has nothing for is not asked again, a failed lookup (no network, Mojang busy) is tried again after a
 * minute.
 */
public final class SkinFix {
    private static final Pattern SKIN = Pattern.compile("^https?://(?:s3\\.amazonaws\\.com|skins\\.minecraft\\.net)/MinecraftSkins/([^/?]+)\\.png$|"
            + "^https?://(?:www\\.)?minecraft\\.net/skin/([^/?]+)\\.png$");
    private static final Pattern CAPE = Pattern.compile("^https?://(?:s3\\.amazonaws\\.com|skins\\.minecraft\\.net)/MinecraftCloaks/([^/?]+)\\.png$|"
            + "^https?://(?:www\\.)?minecraft\\.net/cloak/get\\.jsp\\?user=([^&]+)$");
    /** Mojang's names: letters, digits and _ (very old accounts have up to 25 characters) */
    private static final Pattern NAME = Pattern.compile("^[A-Za-z0-9_]{1,25}$");
    private static final int TIMEOUT = 10000;
    /** A failed lookup is tried again after this long */
    private static final long RETRY = 60000;
    /** Each name's textures, once Mojang has answered */
    private static final Map<String, Textures> found = new HashMap<>();
    /** When each name's lookup last failed */
    private static final Map<String, Long> failed = new HashMap<>();
    /** One lookup at a time a name: the skin and the cape threads ask together */
    private static final Map<String, Object> locks = new ConcurrentHashMap<>();

    /** What Mojang has for a name: the skin as it is served (64 x 64, or 64 x 32 for old ones) and its model, the cape. */
    public static final class Textures {
        static final Textures NONE = new Textures(null, false, null);
        /** null: none */
        public final BufferedImage skin, cape;
        public final boolean slim;
        final byte[] skinPng, capePng;

        Textures(BufferedImage skin, boolean slim, BufferedImage cape) {
            this.skin = skin;
            this.slim = slim;
            this.cape = cape;
            skinPng = skin != null ? png(oldLayout(skin, slim)) : new byte[0];
            capePng = cape != null ? png(cape) : new byte[0];
        }
    }

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
        Textures textures = lookup(key.substring(5));
        return new Answer(url, key.startsWith("skin ") ? textures.skinPng : textures.capePng);
    }

    /** What Mojang had for the name when it was looked up, null if it has not been yet (the VR glue's second layer). */
    public static Textures textures(String name) {
        if (name == null) return null;
        synchronized (found) {
            return found.get(name.toLowerCase(Locale.ROOT));
        }
    }

    /** The name's textures: Mojang's answer, kept; NONE for a name Mojang cannot have, or while a failed lookup waits. */
    static Textures lookup(String name) {
        if (!validName(name)) return Textures.NONE;
        String key = name.toLowerCase(Locale.ROOT);
        synchronized (locks.computeIfAbsent(key, k -> new Object())) {
            synchronized (found) {
                Textures known = found.get(key);
                if (known != null) return known;
                Long at = failed.get(key);
                if (at != null && System.currentTimeMillis() - at < RETRY) return Textures.NONE;
            }
            try {
                Textures textures = fetch(name);
                synchronized (found) {
                    found.put(key, textures);
                    failed.remove(key);
                }
                return textures;
            } catch (Throwable t) {
                System.out.println("QuestCraft legacy launcher: no skin for " + name + " for now: " + t);
                synchronized (found) {
                    failed.put(key, System.currentTimeMillis());
                }
                return Textures.NONE;
            }
        }
    }

    static boolean validName(String name) {
        return name != null && NAME.matcher(name).matches();
    }

    /** "skin NAME" or "cape NAME" for an old skin or cape address, null for any other. */
    static String target(String address) {
        Matcher skin = SKIN.matcher(address), cape = CAPE.matcher(address);
        boolean isSkin = skin.matches();
        if (!isSkin && !cape.matches()) return null;
        Matcher m = isSkin ? skin : cape;
        return (isSkin ? "skin " : "cape ") + (m.group(1) != null ? m.group(1) : m.group(2));
    }

    /** Mojang's skin and cape for the name (NONE if it has none); throws if Mojang cannot be asked now. */
    private static Textures fetch(String name) throws IOException {
        String profile = read("https://api.mojang.com/users/profiles/minecraft/" + name, true);
        String id = profile != null ? group("\"id\"\\s*:\\s*\"([0-9a-fA-F-]+)\"", profile) : null;
        if (id == null) return Textures.NONE;
        String session = read("https://sessionserver.mojang.com/session/minecraft/profile/" + id, false);
        String value = group("\"value\"\\s*:\\s*\"([^\"]+)\"", session);
        if (value == null) return Textures.NONE;
        String textures = new String(Base64.getDecoder().decode(value), StandardCharsets.UTF_8).replace("\\/", "/");
        String skinPart = part(textures, "SKIN"), capePart = part(textures, "CAPE");
        BufferedImage skin = image(skinPart), cape = image(capePart);
        boolean slim = skinPart != null && skinPart.matches("(?s).*\"model\"\\s*:\\s*\"slim\".*");
        System.out.println("QuestCraft legacy launcher: " + name + " from Mojang: " + (skin != null ? (slim ? "slim skin" : "skin") : "no skin")
                + (cape != null ? ", cape" : ""));
        return new Textures(skin, slim, cape);
    }

    /** The SKIN or CAPE entry of the textures value (null if there is none). */
    private static String part(String textures, String which) {
        return group("\"" + which + "\"\\s*:\\s*\\{([^}]*(?:\\{[^}]*\\}[^}]*)?)\\}", textures);
    }

    /** The picture an entry points at (the textures server answers on https too). */
    private static BufferedImage image(String part) throws IOException {
        String address = part != null ? group("\"url\"\\s*:\\s*\"([^\"]+)\"", part) : null;
        if (address == null) return null;
        HttpURLConnection connection = open(address.replaceFirst("^http://", "https://"));
        try {
            if (connection.getResponseCode() != 200) throw new IOException("textures server: HTTP " + connection.getResponseCode());
            try (InputStream in = connection.getInputStream()) {
                return ImageIO.read(in);
            }
        } finally {
            connection.disconnect();
        }
    }

    private static byte[] png(BufferedImage image) {
        try {
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            ImageIO.write(image, "png", out);
            return out.toByteArray();
        } catch (IOException e) {
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

    /**
     * The body of a GET. For a name lookup, null when Mojang has no such name (204 or 404); any other answer that is not
     * 200 (Mojang busy: 429, 5xx) throws, so it is tried again later.
     */
    private static String read(String address, boolean nameLookup) throws IOException {
        HttpURLConnection connection = open(address);
        try {
            int code = connection.getResponseCode();
            if (nameLookup && (code == 204 || code == 404)) return null;
            if (code != 200) throw new IOException(address.replaceFirst("^https://([^/]+)/.*", "$1") + ": HTTP " + code);
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
