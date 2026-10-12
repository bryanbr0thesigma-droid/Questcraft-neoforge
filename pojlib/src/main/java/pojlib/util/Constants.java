package pojlib.util;

import android.app.Activity;

public class Constants {

    public static final String MOJANG_META_URL = "https://piston-meta.mojang.com";

    public static final String MOJANG_RESOURCES_URL = "https://resources.download.minecraft.net";

    public static final String FABRIC_META_URL = "https://meta.fabricmc.net/v2";

    public static final String QUILT_META_URL = "https://meta.quiltmc.org/v3";

    public static final String NEOFORGE_MAVEN_URL = "https://maven.neoforged.net/releases";

    public static final String NEOFORGE_API_URL = "https://maven.neoforged.net/api/maven";

    public static final String OAUTH_TOKEN_URL = "https://login.live.com/oauth20_token.srf";

    public static final String XBL_AUTH_URL = "https://user.auth.xboxlive.com/user/authenticate";

    public static final String XSTS_AUTH_URL = "https://xsts.auth.xboxlive.com/xsts/authorize";

    public static final String MC_LOGIN_URL = "https://api.minecraftservices.com/authentication/login_with_xbox";

    public static final String MC_STORE_URL = "https://api.minecraftservices.com/entitlements/mcstore";

    public static final String MC_PROFILE_URL = "https://api.minecraftservices.com/minecraft/profile";

    public static final String MINOTAR_URL = "https://minotar.net";
    public static final String GIT_BRANCH = "QuestCraft-6.0.1";
    // Upstream QuestCraft's Pojlib: its Fabric mod list and supported versions are still the source for Fabric instances
    public static final String UPSTREAM_RAW_URL = "https://raw.githubusercontent.com/QuestCraftPlusPlus/Pojlib/refs/heads/" + GIT_BRANCH;
    // Where this port hosts the files the launcher downloads at runtime (mod lists, supported versions).
    // TODO: point this at the default branch once the port is merged, the repo has no main yet.
    public static final String PORT_RAW_URL = "https://raw.githubusercontent.com/bryanbr0thesigma-droid/Questcraft-neoforge/claude/fervent-mayer-8idkzr";
    public static String USER_HOME;

    /** The application context, for the lists that are built into the app. */
    public static android.content.Context APP_CONTEXT;

    public static void initConstants(Activity activity) {
        APP_CONTEXT = activity.getApplicationContext();
        USER_HOME = activity.getExternalFilesDir(null).getAbsolutePath();
    }
}