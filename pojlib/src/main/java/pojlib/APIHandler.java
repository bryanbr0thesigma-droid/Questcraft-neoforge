package pojlib;

import android.content.Context;

import com.google.gson.Gson;

import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.stream.Collectors;

import pojlib.install.ModLoader;
import pojlib.util.Constants;
import pojlib.util.download.DownloadManager;
import pojlib.util.download.DownloadUtils;
import pojlib.util.GsonUtils;
import pojlib.util.Logger;
import pojlib.util.MinecraftVersions;

public class APIHandler {
    public final String baseUrl;

    public APIHandler(String url) {
        baseUrl = url;
    }

    public <T> T get(String endpoint, Class<T> tClass) {
        return getFullUrl(baseUrl + "/" + endpoint, tClass);
    }

    public <T> T get(String endpoint, HashMap<String, Object> query, Class<T> tClass) {
        return getFullUrl(baseUrl + "/" + endpoint, query, tClass);
    }

    public <T> T post(String endpoint, T body, Class<T> tClass) {
        return postFullUrl(baseUrl + "/" + endpoint, body, tClass);
    }

    public <T> T post(String endpoint, HashMap<String, Object> query, T body, Class<T> tClass) {
        return postFullUrl(baseUrl + "/" + endpoint, query, body, tClass);
    }

    //Make a get request and return the response as a raw string;
    public static String getRaw(String url) {
        try {
            HttpURLConnection conn = (HttpURLConnection) new URL(url).openConnection();
            InputStream inputStream = conn.getInputStream();
            String data = new BufferedReader(new InputStreamReader(inputStream, StandardCharsets.UTF_8)).lines().collect(Collectors.joining("\n"));
            inputStream.close();
            conn.disconnect();
            return data;
        } catch (IOException e) {
            e.printStackTrace();
        }
        return null;
    }

    public static String postRaw(String url, String body) {
        try {
            HttpURLConnection conn = (HttpURLConnection) new URL(url).openConnection();
            conn.setRequestMethod("POST");
            conn.setRequestProperty("Content-Type", "application/json");
            conn.setRequestProperty("Accept", "application/json");
            conn.setDoOutput(true);

            OutputStream outputStream = conn.getOutputStream();
            byte[] input = body.getBytes(StandardCharsets.UTF_8);
            outputStream.write(input, 0, input.length);
            outputStream.close();

            InputStream inputStream = conn.getInputStream();
            String data = new BufferedReader(new InputStreamReader(inputStream, StandardCharsets.UTF_8)).lines().collect(Collectors.joining("\n"));
            inputStream.close();

            conn.disconnect();
            return data;
        } catch (IOException e) {
            e.printStackTrace();
        }
        return null;
    }

    private static String parseQueries(HashMap<String, Object> query) {
        StringBuilder params = new StringBuilder("?");
        for (String param : query.keySet()) {
            Object value = query.get(param);
            params.append(param).append("=").append(value).append("&");
        }
        return params.substring(0, params.length() - 1);
    }

    public static <T> T getFullUrl(String url, Class<T> tClass) {
        return new Gson().fromJson(getRaw(url), tClass);
    }

    public static <T> T getFullUrl(String url, HashMap<String, Object> query, Class<T> tClass) {
        return getFullUrl(url + parseQueries(query), tClass);
    }

    public static <T> T postFullUrl(String url, T body, Class<T> tClass) {
        return new Gson().fromJson(postRaw(url, body.toString()), tClass);
    }

    public static <T> T postFullUrl(String url, HashMap<String, Object> query, T body, Class<T> tClass) {
        return new Gson().fromJson(postRaw(url + parseQueries(query), body.toString()), tClass);
    }

    /** The Minecraft versions every loader supports together, newest first. */
    public static String[] getQCSupportedVersions(Context ctx) {
        String[][] lists = new String[ModLoader.values().length][];
        for (int i = 0; i < lists.length; i++) {
            lists[i] = getQCSupportedVersions(ctx, ModLoader.values()[i]);
        }
        return MinecraftVersions.union(lists);
    }

    /** The Minecraft versions one loader supports, newest first. Downloads the list when there is a connection. */
    public static String[] getQCSupportedVersions(Context ctx, ModLoader loader) {
        File versionsJson = new File(Constants.USER_HOME + "/" + loader.getSupportedVersionsCacheName());
        if(API.hasConnection(ctx)) {
            try {
                DownloadUtils.downloadFile(loader.getSupportedVersionsUrl(), versionsJson);
            } catch (IOException e) {
                Logger.getInstance().appendToLog("Error while grabbing supported versions of " + loader.displayName + "!\n" + e);
            }
        } else {
            Logger.getInstance().appendToLog("Skipping supported versions download.");
        }

        DownloadManager.reset();
        return getCachedSupportedVersions(loader);
    }

    /** What was last downloaded for a loader (or a built in fallback), without touching the network. */
    public static String[] getCachedSupportedVersions(ModLoader loader) {
        File versionsJson = new File(Constants.USER_HOME + "/" + loader.getSupportedVersionsCacheName());
        SupportedVersions versions = versionsJson.exists()
                ? GsonUtils.jsonFileToObject(versionsJson.getAbsolutePath(), SupportedVersions.class) : null;
        if(versions == null || versions.supportedVersions == null) {
            // the list could not be downloaded (and never was): the copy built into the app
            versions = bundledSupportedVersions(loader);
        }
        if(versions == null || versions.supportedVersions == null) {
            return new String[] {
                    "1.21.4"
            };
        }

        return MinecraftVersions.union(versions.supportedVersions);
    }

    private static SupportedVersions bundledSupportedVersions(ModLoader loader) {
        try {
            byte[] bytes = pojlib.util.FileUtil.loadFromAssetToByte(Constants.APP_CONTEXT, "loaders/" + loader.getSupportedVersionsCacheName());
            return new Gson().fromJson(new String(bytes, java.nio.charset.StandardCharsets.UTF_8), SupportedVersions.class);
        } catch (Throwable t) {
            return null;
        }
    }

    public static class SupportedVersions {
        public String[] supportedVersions;
    }
}