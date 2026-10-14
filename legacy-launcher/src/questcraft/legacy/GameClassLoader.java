package questcraft.legacy;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.JarURLConnection;
import java.net.URL;
import java.net.URLClassLoader;
import java.net.URLConnection;
import java.security.CodeSource;
import java.security.ProtectionDomain;
import java.security.cert.Certificate;

/**
 * The game's class loader: a URLClassLoader (ModLoader adds the mods it finds to it through addURL) whose classes go
 * through {@link Transformer}. LWJGL and the launcher come from the parent (the system class path), so the headset's
 * native code finds them where it looks.
 */
public final class GameClassLoader extends URLClassLoader {
    static {
        ClassLoader.registerAsParallelCapable();
    }

    public GameClassLoader(URL[] urls, ClassLoader parent) {
        super(urls, parent);
    }

    @Override
    protected Class<?> findClass(String name) throws ClassNotFoundException {
        String path = name.replace('.', '/') + ".class";
        URL url = findResource(path);
        if (url == null) throw new ClassNotFoundException(name);
        byte[] bytes;
        URL location;
        try {
            URLConnection connection = url.openConnection();
            try (InputStream in = connection.getInputStream()) {
                bytes = readAll(in);
            }
            // where the class comes from, as ModLoader looks for the game's jar by it
            location = connection instanceof JarURLConnection ? ((JarURLConnection) connection).getJarFileURL()
                    : new URL(url.toExternalForm().substring(0, url.toExternalForm().length() - path.length()));
        } catch (IOException e) {
            throw new ClassNotFoundException(name, e);
        }
        bytes = Transformer.transform(name, bytes);
        ProtectionDomain domain = new ProtectionDomain(new CodeSource(location, (Certificate[]) null), null, this, null);
        Class<?> defined = defineClass(name, bytes, 0, bytes.length, domain);
        // the builds for alpha log nothing: this says ModLoader is there at all
        if (name.equals("ModLoader")) System.out.println("QuestCraft legacy launcher: ModLoader is in the game");
        return defined;
    }

    /** Public, as old mods and ModLoader add jars to the game's class loader. */
    @Override
    public void addURL(URL url) {
        super.addURL(url);
    }

    static byte[] readAll(InputStream in) throws IOException {
        ByteArrayOutputStream out = new ByteArrayOutputStream(16384);
        byte[] buffer = new byte[16384];
        int n;
        while ((n = in.read(buffer)) > 0) out.write(buffer, 0, n);
        return out.toByteArray();
    }
}
