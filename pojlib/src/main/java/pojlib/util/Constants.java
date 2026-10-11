package pojlib.util;

import android.os.Environment;

import java.io.File;

public class Constants {

    public static String MOJANG_META_URL = "https://piston-meta.mojang.com";

    public static String MOJANG_RESOURCES_URL = "https://resources.download.minecraft.net";

    public static String FABRIC_META_URL = "https://meta.fabricmc.net/v2";

    public static String QUILT_META_URL = "https://meta.quiltmc.org/v3";

    public static String NEOFORGE_MAVEN_URL = "https://maven.neoforged.net/releases";

    // Where this port hosts the files the launcher downloads at runtime (mod lists, supported versions).
    // TODO: point this at the default branch once the port is merged, the repo has no main yet.
    public static String PORT_RAW_URL = "https://raw.githubusercontent.com/bryanbr0thesigma-droid/Questcraft-neoforge/claude/fervent-mayer-8idkzr";

    public static String NEOFORGE_API_URL = "https://maven.neoforged.net/api/maven";

    public static String OAUTH_TOKEN_URL = "https://login.live.com/oauth20_token.srf";

    public static String XBL_AUTH_URL = "https://user.auth.xboxlive.com/user/authenticate";

    public static String XSTS_AUTH_URL = "https://xsts.auth.xboxlive.com/xsts/authorize";

    public static String MC_LOGIN_URL = "https://api.minecraftservices.com/authentication/login_with_xbox";

    public static String MC_STORE_URL = "https://api.minecraftservices.com/entitlements/mcstore";

    public static String MC_PROFILE_URL = "https://api.minecraftservices.com/minecraft/profile";

    public static String MINOTAR_URL = "https://minotar.net";

    public static String USER_HOME = new File(Environment.getExternalStorageDirectory(),"Android/data/com.qcxr.qcxr/files").getAbsolutePath();
}