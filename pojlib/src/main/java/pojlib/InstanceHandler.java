package pojlib;

import android.app.Activity;

import com.google.common.collect.Lists;

import java.io.File;
import java.io.IOException;
import java.nio.file.FileVisitResult;
import java.nio.file.FileVisitor;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Future;
import java.util.Set;
import java.util.function.Consumer;

import pojlib.account.MinecraftAccount;
import pojlib.install.FabricMeta;
import pojlib.install.Installer;
import pojlib.install.LaunchArguments;
import pojlib.install.MinecraftMeta;
import pojlib.install.NeoForgeInstaller;
import pojlib.install.NeoForgeMeta;
import pojlib.install.QuiltMeta;
import pojlib.install.VersionInfo;
import pojlib.util.Constants;
import pojlib.util.FileUtil;
import pojlib.util.VLoader;
import pojlib.util.download.DownloadManager;
import pojlib.util.json.MinecraftInstances;
import pojlib.util.json.ModsJson;
import pojlib.util.json.ProjectInfo;
import pojlib.util.GsonUtils;
import pojlib.util.JREUtils;
import pojlib.util.Logger;
import pojlib.util.json.ModrinthIndexJson;

public class InstanceHandler {
    public static final String MODS = "https://raw.githubusercontent.com/QuestCraftPlusPlus/Pojlib/refs/heads/" + Constants.GIT_BRANCH + "/mods.json";
    public static final String DEV_MODS = "https://raw.githubusercontent.com/QuestCraftPlusPlus/Pojlib/refs/heads/" + Constants.GIT_BRANCH + "/devmods.json";
    // NeoForge instances get their mods from this port, the Fabric lists above only exist for instances made before it
    public static final String NEOFORGE_MODS = Constants.PORT_RAW_URL + "/pojlib/mods-neoforge.json";
    public static final String NEOFORGE_DEV_MODS = Constants.PORT_RAW_URL + "/pojlib/devmods-neoforge.json";

    public static String modsUrl(boolean developerMods, boolean neoForge) {
        if (neoForge) return developerMods ? NEOFORGE_DEV_MODS : NEOFORGE_MODS;
        return developerMods ? DEV_MODS : MODS;
    }

    public static MinecraftInstances.Instance create(Activity activity, MinecraftInstances instances, String instanceName, String userHome, String modLoader, String mrpackFilePath, String imageURL) {
        File mrpackJson = new File(Constants.USER_HOME + "/instances/" + instanceName.toLowerCase(Locale.ROOT).replaceAll(" ", "_") + "/setup/modrinth.index.json");

        mrpackJson.getParentFile().mkdirs();
        File setupFile = new File(Constants.USER_HOME + "/instances/" + instanceName.toLowerCase(Locale.ROOT).replaceAll(" ", "_") + "/setup");
        FileUtil.unzipArchive(mrpackFilePath, setupFile.getPath());

        ModrinthIndexJson index = GsonUtils.jsonFileToObject(mrpackJson.getAbsolutePath(), ModrinthIndexJson.class);
        if(index == null) {
            Logger.getInstance().appendToLog("Couldn't install the modpack with path " + mrpackJson.getAbsolutePath());
            return null;
        }

        if (modLoader.equals("NeoForge") && index.dependencies.neoForge == null) {
            Logger.getInstance().appendToLog("This modpack is not for NeoForge, its mods would not load. Pick a NeoForge modpack.");
            return null;
        }

        return create(activity, instances, instanceName, userHome, false, index.dependencies.minecraft, modLoader, imageURL, (instance) -> {
            if (instance.extProjects == null) {
                instance.extProjects = new ProjectInfo[0];
            }
            ArrayList<ProjectInfo> mods = Lists.newArrayList(instance.extProjects);
            for (ModrinthIndexJson.ModpackFile file : index.files) {
                if (file.path.contains("mods")) {
                    ProjectInfo info = new ProjectInfo();
                    info.slug = file.path
                            .replaceAll(".*/", "")
                            .replaceAll("\\..*", "");
                    info.version = "1.0.0";
                    info.download_link = file.downloads[0];
                    info.type = "mod";
                    if(isCoreMod(instance, info)) {
                        continue;
                    }

                    mods.add(info);
                }
            }
            instance.extProjects = mods.toArray(new ProjectInfo[0]);
            try {
                Files.walkFileTree(Paths.get(setupFile + "/overrides"), new FileVisitor<Path>() {
                    @Override
                    public FileVisitResult preVisitDirectory(Path dir, BasicFileAttributes attrs) throws IOException {
                        return FileVisitResult.CONTINUE;
                    }

                    @Override
                    public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) throws IOException {
                        String filtered = file.toString().replaceAll(setupFile.getAbsolutePath() + "/overrides/", "");
                        File newFile = new File(setupFile.getParentFile().getAbsolutePath() + "/" + filtered);
                        newFile.getParentFile().mkdirs();

                        Files.copy(file, newFile.toPath(), StandardCopyOption.REPLACE_EXISTING);
                        return FileVisitResult.CONTINUE;
                    }

                    @Override
                    public FileVisitResult visitFileFailed(Path file, IOException exc) throws IOException {
                        return FileVisitResult.CONTINUE;
                    }

                    @Override
                    public FileVisitResult postVisitDirectory(Path dir, IOException exc) throws IOException {
                        return FileVisitResult.CONTINUE;
                    }
                });
            } catch (IOException e) {
                throw new RuntimeException(e);
            }
            instance.updateMods(instances);
            GsonUtils.objectToJsonFile(userHome + "/instances.json", instances);
        });
    }

    //creates a new instance of a minecraft version, install game + mod loader, stores non login related launch info to json
    public static MinecraftInstances.Instance create(Activity activity, MinecraftInstances instances, String instanceName, String gameDir, boolean useDefaultMods, String minecraftVersion, String modLoader, String imageURL, Consumer<MinecraftInstances.Instance> postInstall) {
        File instancesFile = new File(gameDir + "/instances.json");
        if (instancesFile.exists()) {
            for (MinecraftInstances.Instance instance : instances.instances) {
                if (instance.instanceName.equals(instanceName)) {
                    Logger.getInstance().appendToLog("Instance " + instanceName + " already exists! Using original instance.");
                    return instance;
                }
            }
        }

        Logger.getInstance().appendToLog("Creating new instance: " + instanceName);

        MinecraftInstances.Instance instance = new MinecraftInstances.Instance();
        instance.instanceName = instanceName;
        instance.instanceImageURL = imageURL;
        instance.versionName = minecraftVersion;
        instance.gameDir = Constants.USER_HOME + "/instances/" + instanceName.toLowerCase(Locale.ROOT).replaceAll(" ", "_");
        instance.defaultMods = useDefaultMods;
        instance.modLoader = modLoader.toLowerCase(Locale.ROOT);

        if (modLoader.equals("NeoForge") && !NeoForgeMeta.isSupported(minecraftVersion)) {
            Logger.getInstance().appendToLog("NeoForge does not exist for Minecraft " + minecraftVersion + " (it starts at 1.20.2)");
            return null;
        }

        File gameDirFile = new File(instance.gameDir);
        if(!gameDirFile.exists()) {
            gameDirFile.mkdirs();
        }

        VersionInfo modLoaderVersionInfo = null;
        switch (modLoader) {
            case "Fabric": {
                FabricMeta.FabricVersion fabricVersion = FabricMeta.getLatestVersion();
                assert fabricVersion != null;
                modLoaderVersionInfo = FabricMeta.getVersionInfo(fabricVersion, minecraftVersion);
                break;
            }
            case "Quilt": {
                QuiltMeta.QuiltVersion quiltVersion = QuiltMeta.getLatestVersion();
                assert quiltVersion != null;
                modLoaderVersionInfo = QuiltMeta.getVersionInfo(quiltVersion, minecraftVersion);
                break;
            }
            case "Forge":
            case "NeoForge": {
                // NeoForge is installed with its installer, which needs the game files, see below
                break;
            }
        }

        VersionInfo minecraftVersionInfo = MinecraftMeta.getVersionInfo(minecraftVersion);
        instance.versionType = minecraftVersionInfo.type;
        // NeoForge only knows its main class once the installer has run
        if (modLoaderVersionInfo != null) {
            instance.mainClass = modLoaderVersionInfo.mainClass;
        }

        // Install minecraft
        VersionInfo finalModLoaderVersionInfo = modLoaderVersionInfo;

        if(instances.instances == null) {
            instances.instances = new MinecraftInstances.Instance[0];
        }

        ArrayList<MinecraftInstances.Instance> instances1 = Lists.newArrayList(instances.instances);
        instances1.add(instance);
        instances.instances = instances1.toArray(new MinecraftInstances.Instance[0]);

        CompletableFuture.supplyAsync(() ->
        {
            try {
                CompletableFuture<String> clientClasspath = Installer.installClient(minecraftVersionInfo, gameDir);
                CompletableFuture<String> assetsFuture = Installer.installAssets(minecraftVersionInfo, gameDir);
                String lwjgl = UnityPlayerActivity.installLWJGL(activity);

                if (modLoader.equals("NeoForge")) {
                    // the installer needs the vanilla client, and the instance is vanilla + NeoForge as one version
                    clientClasspath.get();
                    VersionInfo neoForge = NeoForgeInstaller.install(activity, minecraftVersionInfo, gameDir, null);
                    instance.loaderVersion = NeoForgeMeta.getVersionFromId(neoForge.id);
                    String libraries = Installer.installLibraries(neoForge, gameDir).get();

                    instance.mainClass = neoForge.mainClass;
                    instance.classpath = joinClasspath(libraries, clientClasspath.get(), lwjgl);
                    Map<String, String> variables = new HashMap<>();
                    variables.put("library_directory", new File(gameDir, "libraries").getAbsolutePath());
                    variables.put("classpath_separator", File.pathSeparator);
                    // FML is told to keep "<version_name>.jar" out of the module layer. Pojlib's vanilla client is client.jar
                    variables.put("version_name", "client");
                    variables.put("launcher_name", "QuestCraft");
                    variables.put("launcher_version", "1");
                    instance.loaderJvmArgs = LaunchArguments.resolve(neoForge.arguments.jvm, variables).toArray(new String[0]);
                    instance.loaderGameArgs = LaunchArguments.resolve(neoForge.arguments.game, variables).toArray(new String[0]);
                } else {
                    CompletableFuture<String> minecraftClasspath = Installer.installLibraries(minecraftVersionInfo, gameDir);
                    CompletableFuture<String> modLoaderClasspath = Installer.installLibraries(finalModLoaderVersionInfo, gameDir);

                    CompletableFuture<Void> installFuture = CompletableFuture.allOf(clientClasspath, minecraftClasspath, modLoaderClasspath);
                    installFuture.get();

                    instance.classpath = clientClasspath.get() + File.pathSeparator + minecraftClasspath.get() + File.pathSeparator + modLoaderClasspath.get() + File.pathSeparator + lwjgl;
                }

                instance.assetsDir = assetsFuture.get();
                Installer.moveLocalAssets(activity, instance);
            } catch (IOException | ExecutionException | InterruptedException e) {
                e.printStackTrace();
                if (modLoader.equals("NeoForge")) {
                    // an instance without a classpath can not be launched, so do not keep it around
                    Logger.getInstance().appendToLog("Could not create the instance: " + e.getMessage());
                    instances.instances = Arrays.stream(instances.instances).filter(i -> i != instance).toArray(MinecraftInstances.Instance[]::new);
                    DownloadManager.reset();
                    return null;
                }
            }
            instance.assetIndex = minecraftVersionInfo.assetIndex.id;

            // Write instance to json file
            instance.updateMods(instances);
            GsonUtils.objectToJsonFile(gameDir + "/instances.json", instances);

            if(postInstall != null)
                postInstall.accept(instance);

            DownloadManager.reset();
            Logger.getInstance().appendToLog("Finished Creating Instance!");
            return null;
        });

        return instance;
    }

    // Joins classpath strings, leaving out entries that are in there more than once.
    // A jar twice on the classpath would be two modules with the same packages for NeoForge's module layer.
    static String joinClasspath(String... classpaths) {
        Set<String> entries = new LinkedHashSet<>();
        for (String classpath : classpaths) {
            entries.addAll(Arrays.asList(classpath.split(File.pathSeparator)));
        }
        return String.join(File.pathSeparator, entries);
    }

    // Load an instance from json
    public static MinecraftInstances load(String gameDir) {
        MinecraftInstances instances;
        try {
            instances = GsonUtils.jsonFileToObject(gameDir + "/instances.json", MinecraftInstances.class);
        } catch (Exception e) {
            instances = new MinecraftInstances();
            instances.instances = new MinecraftInstances.Instance[0];
        }
        if(instances == null) {
            instances = new MinecraftInstances();
            instances.instances = new MinecraftInstances.Instance[0];
            GsonUtils.objectToJsonFile(gameDir + "/instances.json", instances);
        }

        return instances;
    }

    public static void addExtraProject(MinecraftInstances instances, MinecraftInstances.Instance instance, String name, String fileName, String version, String url, String type) {
        ProjectInfo info = new ProjectInfo();
        info.slug = name;
        info.fileName = fileName;
        info.download_link = url;
        info.version = version;
        info.type = type;

        if(instance.extProjects == null) {
            instance.extProjects = new ProjectInfo[0];
        }

        ArrayList<ProjectInfo> mods = Lists.newArrayList(instance.extProjects);
        mods.add(info);
        instance.extProjects = mods.toArray(mods.toArray(new ProjectInfo[0]));

        GsonUtils.objectToJsonFile(Constants.USER_HOME + "/instances.json", instances);
    }

    public static boolean hasExtraProject(MinecraftInstances.Instance instance, String name) {
        for(ProjectInfo info : instance.extProjects) {
            if(info.slug.equalsIgnoreCase(name)) {
                return true;
            }
        }

        return false;
    }

    private static boolean isCoreMod(MinecraftInstances.Instance instance, ProjectInfo oldInfo) {
        // Check if its a coremod
        ModsJson oldMods = instance.parseModsJson(instance.modsSnapshotPath());
        if(oldMods != null) {
            Optional<ModsJson.Version> ver = Arrays.stream(oldMods.versions).filter((v) -> v.name.equals(instance.versionName)).findFirst();
            if(ver.isPresent()) {
                ModsJson.Version version = ver.get();
                Optional<ProjectInfo> info = Arrays.stream(version.coreMods).filter((mod) -> mod.slug.equals(oldInfo.slug)).findFirst();
                return info.isPresent();
            }
        }

        return false;
    }

    public static boolean removeExtraProject(MinecraftInstances instances, MinecraftInstances.Instance instance, String name) {
        ProjectInfo oldInfo = Arrays.stream(instance.extProjects).filter(info -> info.slug.equalsIgnoreCase(name)).findFirst().orElse(null);

        if(oldInfo != null) {
            boolean isMod = oldInfo.type.equals("mod");
            boolean legacyMod = oldInfo.fileName == null;

            if(isCoreMod(instance, oldInfo)) {
                return false;
            }

            // Delete the mod
            File modFile = new File(instance.gameDir + (isMod ? "/mods/" : "/resourcepacks/") + (legacyMod ? oldInfo.slug : oldInfo.fileName) + (isMod ? ".jar" : ".zip"));
            modFile.delete();

            ArrayList<ProjectInfo> mods = Lists.newArrayList(instance.extProjects);
            mods.remove(oldInfo);
            instance.extProjects = mods.toArray(mods.toArray(new ProjectInfo[0]));
            GsonUtils.objectToJsonFile(Constants.USER_HOME + "/instances.json", instances);
            return true;
        }

        return false;
    }

    // Return true if instance was deleted
    public static boolean delete(MinecraftInstances instances, MinecraftInstances.Instance instance) throws IOException {
        File instanceDir = new File(instance.gameDir);
        Files.walkFileTree(instanceDir.toPath(), new FileVisitor<Path>() {
            @Override
            public FileVisitResult preVisitDirectory(Path dir, BasicFileAttributes attrs) throws IOException {
                return FileVisitResult.CONTINUE;
            }

            @Override
            public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) throws IOException {
                Files.delete(file);
                return FileVisitResult.CONTINUE;
            }

            @Override
            public FileVisitResult visitFileFailed(Path file, IOException exc) throws IOException {
                return FileVisitResult.CONTINUE;
            }

            @Override
            public FileVisitResult postVisitDirectory(Path dir, IOException exc) throws IOException {
                Files.delete(dir);
                return FileVisitResult.CONTINUE;
            }
        });

        ArrayList<MinecraftInstances.Instance> instances1 = Lists.newArrayList(instances.instances);
        instances1.remove(instance);
        instances.instances = instances1.toArray(new MinecraftInstances.Instance[0]);
        GsonUtils.objectToJsonFile(Constants.USER_HOME + "/instances.json", instances);

        return instanceDir.delete();
    }

    public static void launchInstance(Activity activity, MinecraftAccount account, MinecraftInstances.Instance instance) {
        try {
            API.currentInstance = instance;
            JREUtils.redirectAndPrintJRELog();
            VLoader.setAndroidInitInfo(activity);
            JREUtils.launchJavaVM(activity, instance.generateLaunchArgs(account), instance);
        } catch (Throwable e) {
            e.printStackTrace();
        }
    }
}