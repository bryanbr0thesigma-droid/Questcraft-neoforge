package com.godot.game;

import android.content.Intent;
import android.os.Handler;
import android.os.Looper;
import android.os.Process;
import android.util.Log;

import androidx.annotation.NonNull;

import org.godotengine.godot.Godot;
import org.godotengine.godot.plugin.GodotPlugin;
import org.godotengine.godot.plugin.UsedByGodot;
import org.json.JSONArray;

import pojlib.LauncherApi;

/**
 * The bridge between the Godot launcher (GDScript) and Pojlib. One method, {@code invoke(method, argsJson)}, so the
 * GDScript side never needs to know Java types: arguments are a JSON array of strings, the result is a string
 * (lists and objects are JSON text).
 */
public class QuestCraftPlugin extends GodotPlugin {
    private static final String TAG = "QuestCraft";

    public QuestCraftPlugin(Godot godot) {
        super(godot);
    }

    @NonNull
    @Override
    public String getPluginName() {
        return "QuestCraft";
    }

    /** Starts the game in its own activity and process; this one (the VR menu) pauses until the game ends. */
    private String startGameProcess(String instance, String account) {
        Intent intent = new Intent(getActivity(), GameActivity.class);
        intent.putExtra("instance", instance);
        intent.putExtra("account", account);
        getActivity().startActivity(intent);
        // This process holds an OpenXR session (Godot only ends it with the process) and a lot of memory, and the game
        // needs both: end it. The game's activity starts the launcher again when the game ends.
        new Handler(Looper.getMainLooper()).postDelayed(() -> Process.killProcess(Process.myPid()), 1500);
        return "";
    }

    @UsedByGodot
    public String invoke(String method, String argsJson) {
        try {
            JSONArray a = new JSONArray(argsJson == null ? "[]" : argsJson);
            switch (method) {
                case "init": return LauncherApi.init(getActivity());
                case "setModel": LauncherApi.setModel(a.optString(0)); return "";
                case "setMemory": LauncherApi.setMemory(a.optString(0)); return "";
                case "hasConnection": return String.valueOf(LauncherApi.hasConnection());
                case "modLoaders": return LauncherApi.modLoaders();
                case "supportedVersions": return LauncherApi.supportedVersions(a.optString(0));
                case "refreshVersions": LauncherApi.refreshVersions(); return "";
                case "modrinthCategories": return LauncherApi.modrinthCategories(a.optString(0));
                case "listInstances": return LauncherApi.listInstances();
                case "createInstance": LauncherApi.createInstance(a.optString(0), a.optString(1), a.optString(2), a.optString(3, "Vivecraft")); return "";
                case "vrMods": return LauncherApi.vrMods(a.optString(0), a.optString(1));
                case "installModpack": LauncherApi.installModpack(a.optString(0), a.optString(1), a.optString(2), a.optString(3)); return "";
                case "deleteInstance": return String.valueOf(LauncherApi.deleteInstance(a.optString(0)));
                case "listMods": return LauncherApi.listMods(a.optString(0));
                case "hasMod": return String.valueOf(LauncherApi.hasMod(a.optString(0), a.optString(1)));
                case "addMod":
                    return String.valueOf(LauncherApi.addMod(a.optString(0), a.optString(1), a.optString(2),
                            a.optString(3), a.optString(4), a.optString(5)));
                case "removeMod": return String.valueOf(LauncherApi.removeMod(a.optString(0), a.optString(1)));
                case "listAccounts": return LauncherApi.listAccounts();
                case "login": LauncherApi.login(a.optString(0)); return "";
                case "loginState": return LauncherApi.loginState();
                case "removeAccount": return String.valueOf(LauncherApi.removeAccount(a.optString(0)));
                case "prepare": LauncherApi.prepare(a.optString(0)); return "";
                case "startGame": LauncherApi.startGame(); return "";
                case "returnedFromGame":
                    return String.valueOf(getActivity().getIntent().getBooleanExtra(GameActivity.FROM_GAME, false));
                case "startGameProcess": return startGameProcess(a.optString(0), a.optString(1));
                case "restart": LauncherApi.restart(); return "";
                case "progress": return LauncherApi.progress();
                case "acknowledge": LauncherApi.acknowledge(); return "";
                case "logTail": return LauncherApi.logTail(a.optInt(0, 10));
                case "crashInfo": return LauncherApi.crashInfo();
                default:
                    Log.w(TAG, "Unknown call " + method);
                    return "";
            }
        } catch (Throwable t) {
            Log.e(TAG, "call " + method + " failed", t);
            return "";
        }
    }
}
