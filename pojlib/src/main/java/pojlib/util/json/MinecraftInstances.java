package pojlib.util.json;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import pojlib.account.MinecraftAccount;
import pojlib.API;
import pojlib.InstanceHandler;
import pojlib.util.Constants;
import pojlib.util.DownloadUtils;
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
        public ProjectInfo[] extProjects;
        public boolean defaultMods;
        // "fabric" or "neoforge", instances that were created before NeoForge existed have none and are Fabric
        public String modLoader;
        // Arguments the mod loader needs on top of the ones every instance gets, already resolved for this device
        public String[] loaderJvmArgs;
        public String[] loaderGameArgs;

        public boolean isNeoForge() {
            return "neoforge".equals(modLoader);
        }

        public List<String> generateLaunchArgs(MinecraftAccount account) {
            String[] mcArgs = {"--username", account.username, "--version", versionName, "--gameDir", gameDir,
                    "--assetsDir", assetsDir, "--assetIndex", assetIndex, "--uuid", account.uuid,
                    "--accessToken", account.accessToken, "--userType", account.userType, "--versionType", "release"};

            List<String> allArgs = new ArrayList<>();
            if (loaderJvmArgs != null) allArgs.addAll(Arrays.asList(loaderJvmArgs));
            allArgs.addAll(readExtraJvmArgs());
            allArgs.add("-cp");
            allArgs.add(classpath);
            allArgs.add(mainClass);
            allArgs.addAll(Arrays.asList(mcArgs));
            if (loaderGameArgs != null) allArgs.addAll(Arrays.asList(loaderGameArgs));
            return allArgs;
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

        public ProjectInfo[] toArray() {
            if(extProjects == null) {
                return new ProjectInfo[0];
            }
            return extProjects;
        }

        private ModsJson parseModsJson(String gameDir) throws Exception {
            File mods = new File(gameDir + "/mods.json");
            DownloadUtils.downloadFile(InstanceHandler.modsUrl(API.developerMods, isNeoForge()), mods);

            return GsonUtils.jsonFileToObject(mods.getAbsolutePath(), ModsJson.class);
        }

        public void updateMods(MinecraftInstances instances) {
            API.finishedDownloading = false;
            if(extProjects == null) {
                extProjects = new ProjectInfo[0];
            }
            try {
                ModsJson modsJson = parseModsJson(Constants.USER_HOME);

                ModsJson.Version version = null;
                for(ModsJson.Version info : modsJson.versions) {
                    if(info.name.equals(versionName)) {
                        version = info;
                        break;
                    }
                }

                if(version == null) {
                    Logger.getInstance().appendToLog("Mods could not be downloaded because version was null. Open a ticket.");
                    return;
                }

                File modsDir = new File(gameDir + "/mods");
                if(!modsDir.exists()) {
                    ArrayList<ProjectInfo> modInfos = new ArrayList<>();
                    for(ProjectInfo info : version.coreMods) {
                        File mod = new File(modsDir + "/" + info.slug + ".jar");
                        DownloadUtils.downloadFile(info.download_link, mod);
                        info.type = "mod";
                        modInfos.add(info);
                    }
                    if(defaultMods) {
                        for (ProjectInfo info : version.defaultMods) {
                            File mod = new File(modsDir + "/" + info.slug + ".jar");
                            DownloadUtils.downloadFile(info.download_link, mod);
                            info.type = "mod";
                            modInfos.add(info);
                        }
                    }
                    extProjects = modInfos.toArray(modInfos.toArray(new ProjectInfo[0]));
                    GsonUtils.objectToJsonFile(Constants.USER_HOME + "/instances.json", instances);
                    API.finishedDownloading = true;
                    return;
                }

                for(ProjectInfo info : version.coreMods) {
                    for(ProjectInfo currInfo : extProjects) {
                        if(!currInfo.slug.equals(info.slug)) {
                            continue;
                        }
                        if(currInfo.version.equals(info.version)) {
                            continue;
                        }

                        File mod = new File(modsDir + "/" + info.slug + ".jar");
                        DownloadUtils.downloadFile(info.download_link, mod);
                        info = currInfo;
                    }
                }

                if(defaultMods) {
                    for (ProjectInfo info : version.defaultMods) {
                        for (ProjectInfo currInfo : extProjects) {
                            if (!currInfo.slug.equals(info.slug)) {
                                continue;
                            }
                            if (currInfo.version.equals(info.version)) {
                                continue;
                            }

                            File mod = new File(modsDir + "/" + info.slug + ".jar");
                            DownloadUtils.downloadFile(info.download_link, mod);
                            info = currInfo;
                        }
                    }
                }

                // Download custom mods
                for(ProjectInfo currInfo : extProjects) {
                    boolean isMod = currInfo.type.equals("mod");
                    File mod = new File((isMod ? modsDir : gameDir + "/resourcepacks") + "/" + currInfo.slug + (isMod ? ".jar" : ".zip"));
                    if(!mod.exists()) {
                        DownloadUtils.downloadFile(currInfo.download_link, mod);
                    }
                }

                GsonUtils.objectToJsonFile(Constants.USER_HOME + "/instances.json", instances);
                API.finishedDownloading = true;
            } catch (Exception e) {
                Logger.getInstance().appendToLog("Mods failed to download! Are you offline?\n" + e);
                API.finishedDownloading = true;
            }
        }
    }
}
