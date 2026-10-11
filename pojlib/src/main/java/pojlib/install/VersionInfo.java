package pojlib.install;

import com.google.gson.annotations.SerializedName;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public class VersionInfo {
    @SerializedName("id")
    public String id;
    @SerializedName("type")
    public String type;
    @SerializedName("assetIndex")
    public AssetIndex assetIndex;
    @SerializedName("downloads")
    public Downloads downloads;
    @SerializedName("libraries")
    public Library[] libraries;
    @SerializedName("mainClass")
    public String mainClass;
    @SerializedName("arguments")
    public Arguments arguments;
    @SerializedName("assets")
    public String assets;
    // mod loaders (Forge, NeoForge) describe themselves as a change on top of a vanilla version
    @SerializedName("inheritsFrom")
    public String inheritsFrom;

    /**
     * Resolves {@code child} (a mod loader version json) on top of {@code parent} (the vanilla version it
     * {@code inheritsFrom}) the way the vanilla launcher does:
     * the child wins, its libraries come first and replace parent libraries with the same
     * {@code group:artifact[:classifier]}, and the arguments are concatenated.
     */
    public static VersionInfo merge(VersionInfo parent, VersionInfo child) {
        VersionInfo merged = new VersionInfo();
        merged.id = child.id;
        merged.type = child.type != null ? child.type : parent.type;
        merged.assetIndex = child.assetIndex != null ? child.assetIndex : parent.assetIndex;
        merged.downloads = child.downloads != null ? child.downloads : parent.downloads;
        merged.mainClass = child.mainClass != null ? child.mainClass : parent.mainClass;
        merged.assets = child.assets != null ? child.assets : parent.assets;

        Map<String, Library> libraries = new LinkedHashMap<>();
        for (VersionInfo info : new VersionInfo[]{child, parent}) {
            if (info.libraries == null) continue;
            for (Library library : info.libraries) {
                libraries.putIfAbsent(libraryKey(library.name), library);
            }
        }
        merged.libraries = libraries.values().toArray(new Library[0]);

        merged.arguments = new Arguments();
        merged.arguments.game = concat(parent.arguments == null ? null : parent.arguments.game,
                child.arguments == null ? null : child.arguments.game);
        merged.arguments.jvm = concat(parent.arguments == null ? null : parent.arguments.jvm,
                child.arguments == null ? null : child.arguments.jvm);
        return merged;
    }

    /** {@code group:artifact:version[:classifier][@ext]} to {@code group:artifact[:classifier]} */
    static String libraryKey(String name) {
        String[] parts = name.split("@")[0].split(":");
        if (parts.length < 3) return name;
        return parts.length > 3 ? parts[0] + ":" + parts[1] + ":" + parts[3] : parts[0] + ":" + parts[1];
    }

    private static Object[] concat(Object[] first, Object[] second) {
        List<Object> all = new ArrayList<>();
        if (first != null) all.addAll(Arrays.asList(first));
        if (second != null) all.addAll(Arrays.asList(second));
        return all.toArray();
    }

    public static class AssetIndex {
        @SerializedName("id")
        public String id;
        @SerializedName("totalSize")
        public int totalSize;
        @SerializedName("url")
        public String url;
    }

    public static class Downloads {
        @SerializedName("client")
        public Client client;

        public static class Client {
            @SerializedName("sha1")
            public String sha1;
            @SerializedName("url")
            public String url;
        }
    }

    public static class Arguments {
        @SerializedName("game")
        public Object[] game;
        @SerializedName("jvm")
        public Object[] jvm;

        public static class ArgValue {
            @SerializedName("rules")
            public ArgRules[] rules;
            @SerializedName("value")
            public String value;

            public static class ArgRules {
                @SerializedName("action")
                public String action;
                @SerializedName("features")
                public String features;
                @SerializedName("os")
                public ArgOS os;

                public static class ArgOS {
                    @SerializedName("name")
                    public String name;
                    @SerializedName("version")
                    public String version;
                }
            }
        }
    }

    public static class Library {
        @SerializedName("downloads")
        public Downloads downloads;
        @SerializedName("name")
        public String name;
        @SerializedName("url")
        public String url;

        public static class Downloads {
            @SerializedName("artifact")
            public Artifact artifact;
        }

        public static class Artifact {
            @SerializedName("path")
            public String path;
            @SerializedName("sha1")
            public String sha1;
            @SerializedName("size")
            public int size;
            @SerializedName("url")
            public String url;
        }
    }

    public static class Asset {
        @SerializedName("hash")
        public String hash;
        @SerializedName("size")
        public int size;
    }
}
