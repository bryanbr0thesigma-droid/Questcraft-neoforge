package pojlib.util.json;

import com.google.gson.JsonObject;
import com.google.gson.JsonPrimitive;

import org.apache.commons.io.FileUtils;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import pojlib.account.MinecraftAccount;
import pojlib.install.ModLoader;
import pojlib.API;
import pojlib.InstanceHandler;
import pojlib.util.Constants;
import pojlib.util.FileUtil;
import pojlib.util.download.DownloadManager;
import pojlib.util.download.DownloadUtils;
import pojlib.util.GsonUtils;
import pojlib.util.Logger;

public class MinecraftInstances {
    public Instance[] instances;

    public Instance load(String name) {
        for(Instance instance : instances) {
            if(instance.instanceName.equals(name)) {
                return instance;
            }
        }

        return null;
    }

    public Instance[] toArray() {
        if(instances == null) {
            return new Instance[0];
        }
        return instances;
    }

    /** The settings QuestCraft needs in Vivecraft's config: OpenXR, VR on, no desktop-only extras. */
    public static void applyQuestSettings(JsonObject obj) {
        obj.addProperty("stereoProviderPluginID", "OPENXR");
        obj.addProperty("alwaysShowUpdates", "false");
        obj.addProperty("vrEnabled", "true");
        obj.addProperty("vrToggleButtonEnabled", "false");
        obj.addProperty("disableGarbageCollectorMessage", "true");
        obj.addProperty("vrHotswitchingEnabled", "false");
        obj.addProperty("seated", "false");
    }

    public static void CheckVivecraftConfig(MinecraftInstances.Instance instance) {
        File config = new File(instance.gameDir + "/config/vivecraft-client-config.json");
        if (!config.exists()) {
            // Without the file Vivecraft starts with its desktop defaults (not OpenXR) and the game is not in VR, so
            // start from the app's copy instead of skipping this
            Logger.getInstance().appendToLog("Vivecraft config not found, creating it");
            try {
                byte[] bundled = FileUtil.loadFromAssetToByte(Constants.APP_CONTEXT, "vivecraft-client-config.json");
                if (bundled == null) throw new IOException("the app has no copy of it");
                FileUtils.writeByteArrayToFile(config, bundled);
            } catch (Exception e) {
                Logger.getInstance().appendToLog("Could not create the Vivecraft config: " + e);
                return;
            }
        }

        try {
            Logger.getInstance().appendToLog("Modifying Vivecraft config for QuestCraft");
            JsonObject obj = GsonUtils.jsonFileToObject(config.getAbsolutePath(), JsonObject.class);
            applyQuestSettings(obj);
            GsonUtils.objectToJsonFile(config.getAbsolutePath(), obj);
        } catch (Exception e) {
            Logger.getInstance().appendToLog("Failed to modify Vivecraft config");
            e.printStackTrace();
        }
    }

    public static class Instance {
        public String instanceName;
        public String instanceImageURL;
        public String versionName;
        public String versionType;
        public String classpath;
        public String gameDir;
        public String assetIndex;
        public String assetsDir;
        public String mainClass;
        public String extraNatives;
        public ProjectInfo[] extProjects;
        public boolean defaultMods;
        // "fabric" or "neoforge", instances that were created before NeoForge existed have none and are Fabric
        public String modLoader;
        // The loader version the instance was made with, for NeoForge. Its classpath and arguments belong to it.
        public String loaderVersion;
        // Arguments the mod loader needs on top of the ones every instance gets, already resolved for this device
        public String[] loaderJvmArgs;
        public String[] loaderGameArgs;

        public ModLoader loader() {
            return ModLoader.fromStored(modLoader);
        }

        /** Shown in the launcher next to the version. */
        public String loaderName() {
            return loader().displayName;
        }

        /** The Modrinth categories whose mods load in this instance. */
        public String[] modrinthCategories() {
            return loader().getModrinthCategories();
        }

        /** "vivecraft" (the default, also for instances from before there was a choice) or "visor". */
        public String vrMod;

        public boolean usesVisor() {
            return "visor".equals(vrMod);
        }

        /** Shown in the launcher next to the loader. */
        public String vrModName() {
            return usesVisor() ? "Visor" : "Vivecraft";
        }

        public boolean isNeoForge() {
            return loader() == ModLoader.NEOFORGE;
        }

        public boolean isForge() {
            return loader() == ModLoader.FORGE;
        }

        /** NeoForge and Forge are installed by their own installer and start through a module layer of their own. */
        public boolean usesLoaderInstaller() {
            return isNeoForge() || isForge();
        }

        // The mod list this instance was last updated from, to find out what changed. Each loader has its own list.
        public String modsSnapshotPath() {
            return Constants.USER_HOME + "/" + loader().getModsSnapshotName();
        }

        // One JVM argument per line in <files>/loader_jvm_args.txt. Lets the module setup of NeoForge be adjusted
        // on a headset without a new build, for example -DignoreList=... to keep a jar out of the module layer.
        private static List<String> readExtraJvmArgs() {
            List<String> extra = new ArrayList<>();
            File file = new File(Constants.USER_HOME, "loader_jvm_args.txt");
            if (!file.isFile()) return extra;
            try {
                for (String line : Files.readAllLines(file.toPath(), StandardCharsets.UTF_8)) {
                    if (!line.trim().isEmpty() && !line.startsWith("#")) extra.add(line.trim());
                }
            } catch (IOException e) {
                Logger.getInstance().appendToLog("Could not read loader_jvm_args.txt: " + e);
            }
            return extra;
        }

        public List<String> generateLaunchArgs(MinecraftAccount account) {
            String[] mcArgs = {"--username", account.username, "--version", versionName, "--gameDir", gameDir,
                    "--assetsDir", assetsDir, "--assetIndex", assetIndex, "--uuid", account.uuid.replace("-", ""),
                    "--accessToken", account.accessToken, "--userType", account.userType, "--versionType", "release"};

            List<String> allArgs = new ArrayList<>();
            if (loaderJvmArgs != null) allArgs.addAll(Arrays.asList(loaderJvmArgs));
            allArgs.addAll(readExtraJvmArgs());
            allArgs.add("-cp");
            allArgs.add(classpath);
            allArgs.add(mainClass);
            allArgs.addAll(Arrays.asList(mcArgs));
            if (loaderGameArgs != null) allArgs.addAll(Arrays.asList(loaderGameArgs));
            if (account.isDemoMode) {
                allArgs.add("--demo");
            }

            return allArgs;
        }

        public ProjectInfo[] toArray() {
            if(extProjects == null) {
                return new ProjectInfo[0];
            }
            return extProjects;
        }

        /** The mods of {@code wanted} that {@code have} has no entry for (by slug). */
        public static List<ProjectInfo> missingMods(ProjectInfo[] have, ProjectInfo[] wanted) {
            List<ProjectInfo> missing = new ArrayList<>();
            for (ProjectInfo w : wanted) {
                boolean found = false;
                for (ProjectInfo h : have) {
                    if (h.slug != null && h.slug.equalsIgnoreCase(w.slug)) {
                        found = true;
                        break;
                    }
                }
                if (!found) missing.add(w);
            }
            return missing;
        }

        public ModsJson parseModsJson(String jsonPath) {
            ModsJson mods = GsonUtils.jsonFileToObject(jsonPath, ModsJson.class);
            // the Visor builds of this loader, for an instance that runs Visor instead of Vivecraft
            if (mods != null && usesVisor()) pojlib.install.VisorMods.attach(mods, loader());
            return mods;
        }

        private ModsJson downloadCurrentModsJson(String userHome) throws Exception {
            File mods = new File(userHome + "/new_mods.json");
            String url = loader().getModsUrl(API.developerMods);
            try {
                DownloadUtils.downloadFile(url, mods);
            } catch (java.io.IOException e) {
                // the list cannot be read (the repository it lives in is private, or there is no connection): use the
                // copy built into the app, if there is one
                String name = url.substring(url.lastIndexOf('/') + 1);
                try {
                    byte[] bundled = pojlib.util.FileUtil.loadFromAssetToByte(Constants.APP_CONTEXT, "loaders/" + name);
                    pojlib.util.FileUtil.write(mods.getAbsolutePath(), bundled);
                    pojlib.util.Logger.getInstance().appendToLog("Using the mod list built into the app: " + name);
                } catch (Throwable t) {
                    throw e;
                }
            }

            return parseModsJson(mods.getAbsolutePath());
        }

        private void removeModByType(List<ProjectInfo> oldMods, List<ProjectInfo> newMods) {
            ArrayList<ProjectInfo> removedMods = new ArrayList<>();

            for(ProjectInfo oldMod : oldMods) {
                boolean exists = false;
                for(ProjectInfo newMod : newMods) {
                    if(!oldMod.slug.equals(newMod.slug)) {
                        continue;
                    }
                    exists = true;
                    break;
                }

                if(!exists) {
                    removedMods.add(oldMod);
                }
            }

            ArrayList<ProjectInfo> newExtProjects = new ArrayList<>();
            for(ProjectInfo extProject : extProjects) {
                boolean remove = false;
                for(ProjectInfo removedMod : removedMods) {
                    if (extProject.slug.equals(removedMod.slug)) {
                        remove = true;
                        break;
                    }
                }
                if(!remove) {
                    newExtProjects.add(extProject);
                } else {
                    boolean legacyMod = extProject.fileName == null;
                    File mod = new File(
                            gameDir + (extProject.type.equals("mod") ? "/mods" : "/resourcepacks"),
                            (legacyMod ? extProject.slug : extProject.fileName) + (extProject.type.equals("resourcepack") ? ".zip" : ".jar")
                    );
                    if(mod.exists()) {
                        mod.delete();
                    }
                }
            }
            extProjects = newExtProjects.toArray(new ProjectInfo[0]);
        }

        private void removeOldMods(ModsJson oldMods, ModsJson newMods) {
            for(ModsJson.Version oldVersion : oldMods.versions) {
                if(!versionName.equals(oldVersion.name)) {
                    continue;
                }

                for(ModsJson.Version newVersion : newMods.versions) {
                    if(!versionName.equals(newVersion.name)) {
                        continue;
                    }

                    ArrayList<ProjectInfo> mergedOldMods = new ArrayList<>();
                    mergedOldMods.addAll(Arrays.asList(oldVersion.coreModsFor(usesVisor())));
                    mergedOldMods.addAll(Arrays.asList(oldVersion.defaultMods));

                    ArrayList<ProjectInfo> mergedNewMods = new ArrayList<>();
                    mergedNewMods.addAll(Arrays.asList(newVersion.coreModsFor(usesVisor())));
                    mergedNewMods.addAll(Arrays.asList(newVersion.defaultMods));

                    if(defaultMods) {
                        removeModByType(mergedOldMods, mergedNewMods);
                    } else {
                        removeModByType(Arrays.asList(oldVersion.coreModsFor(usesVisor())), Arrays.asList(newVersion.coreModsFor(usesVisor())));
                    }
                    break;
                }
            }
        }

        private void updateModByType(List<ProjectInfo> newMods) throws IOException {
            ArrayList<ProjectInfo> newExtMods = new ArrayList<>();
            for(ProjectInfo extMod : extProjects) {
                boolean manual = true;
                for(ProjectInfo newMod : newMods) {
                    if(!extMod.slug.equals(newMod.slug)) {
                        continue;
                    }
                    manual = false;
                    boolean legacyMod = newMod.fileName == null;
                    File mod = new File(
                            gameDir + (newMod.type.equals("mod") ? "/mods" : "/resourcepacks"),
                            (legacyMod ? newMod.slug : newMod.fileName) + (newMod.type.equals("resourcepack") ? ".zip" : ".jar")
                    );
                    if(!mod.exists() || !extMod.version.equals(newMod.version)) {
                        DownloadUtils.downloadFile(newMod.download_link, mod);
                        extMod = newMod;
                        break;
                    }
                }
                if(manual) {
                    boolean legacyMod = extMod.fileName == null;
                    File mod = new File(
                            gameDir + (extMod.type.equals("mod") ? "/mods" : "/resourcepacks"),
                            (legacyMod ? extMod.slug : extMod.fileName) + (extMod.type.equals("resourcepack") ? ".zip" : ".jar")
                    );
                    if(!mod.exists()) {
                        DownloadUtils.downloadFile(extMod.download_link, mod, 0);
                    }
                }
                newExtMods.add(extMod);
            }

            extProjects = newExtMods.toArray(new ProjectInfo[0]);
        }

        private void downloadAllMods(List<ProjectInfo> newMods) throws IOException {
            for(ProjectInfo newMod : newMods) {
                boolean legacyMod = newMod.fileName == null;
                File mod = new File(
                        gameDir + (newMod.type.equals("mod") ? "/mods" : "/resourcepacks"),
                        (legacyMod ? newMod.slug : newMod.fileName) + (newMod.type.equals("resourcepack") ? ".zip" : ".jar")
                );
                DownloadUtils.downloadFile(newMod.download_link, mod, 0);
            }

            extProjects = newMods.toArray(new ProjectInfo[0]);
        }

        public void updateMods(MinecraftInstances instances) {
            if(extProjects == null) {
                extProjects = new ProjectInfo[0];
            }
            try {
                ModsJson newMods = downloadCurrentModsJson(Constants.USER_HOME);
                ModsJson oldMods = parseModsJson(modsSnapshotPath());

                if(oldMods != null) {
                    removeOldMods(oldMods, newMods);
                }

                File modsFolder = new File(gameDir + "/mods");
                for (ModsJson.Version newVersion : newMods.versions) {
                    if (!versionName.equals(newVersion.name)) {
                        continue;
                    }

                    ArrayList<ProjectInfo> mergedNewMods = new ArrayList<>(Arrays.asList(newVersion.coreModsFor(usesVisor())));
                    if(defaultMods)
                        mergedNewMods.addAll(Arrays.asList(newVersion.defaultMods));

                    if(extProjects.length == 0 || !modsFolder.exists())
                        downloadAllMods(mergedNewMods);
                    else
                        updateModByType(mergedNewMods);
                    // a core mod the instance has not got yet (the list gained one, or the instance was made without it)
                    for (ProjectInfo missing : missingMods(extProjects, newVersion.coreModsFor(usesVisor()))) {
                        boolean legacyMod = missing.fileName == null;
                        File mod = new File(gameDir + (missing.type.equals("mod") ? "/mods" : "/resourcepacks"),
                                (legacyMod ? missing.slug : missing.fileName) + (missing.type.equals("resourcepack") ? ".zip" : ".jar"));
                        DownloadUtils.downloadFile(missing.download_link, mod, 0);
                        ArrayList<ProjectInfo> withIt = new ArrayList<>(Arrays.asList(extProjects));
                        withIt.add(missing);
                        extProjects = withIt.toArray(new ProjectInfo[0]);
                    }
                    break;
                }

                GsonUtils.objectToJsonFile(Constants.USER_HOME + "/instances.json", instances);
                File newModsFile = new File(Constants.USER_HOME + "/new_mods.json");
                File modsFile = new File(modsSnapshotPath());

                modsFile.delete();
                Files.copy(newModsFile.toPath(), modsFile.toPath());
                newModsFile.delete();
            } catch (Exception e) {
                Logger.getInstance().appendToLog("Mods failed to download! Are you offline?\n" + e);
            }
        }
    }
}
