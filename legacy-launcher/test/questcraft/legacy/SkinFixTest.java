package questcraft.legacy;

import java.awt.image.BufferedImage;

/** Checks SkinFix without the network: which addresses it answers, and the old skin layout. Run by SelfTest. */
public final class SkinFixTest {
    private static int failures;

    private SkinFixTest() {
    }

    private static void check(boolean ok, String what) {
        if (!ok) {
            failures++;
            System.out.println("FAILED: " + what);
        }
    }

    /** The number of failed checks. */
    public static int run() {
        failures = 0;
        check("skin jeb_".equals(SkinFix.target("http://s3.amazonaws.com/MinecraftSkins/jeb_.png")), "b1.7.3's skin address");
        check("cape jeb_".equals(SkinFix.target("http://s3.amazonaws.com/MinecraftCloaks/jeb_.png")), "b1.7.3's cape address");
        check("skin Notch".equals(SkinFix.target("http://skins.minecraft.net/MinecraftSkins/Notch.png")), "1.6's skin address");
        check("skin Notch".equals(SkinFix.target("http://www.minecraft.net/skin/Notch.png")), "alpha's skin address");
        check("cape Notch".equals(SkinFix.target("http://www.minecraft.net/cloak/get.jsp?user=Notch")), "alpha's cape address");
        check(SkinFix.target("http://s3.amazonaws.com/MinecraftResources/") == null, "the resources are left alone");
        check(SkinFix.target("jar:file:/x.jar!/a.ogg") == null, "other addresses are left alone");
        check(SkinFix.validName("jeb_") && SkinFix.validName("Notch"), "Mojang's names");
        check(!SkinFix.validName("jeb_#x") && !SkinFix.validName("%6Aeb_") && !SkinFix.validName("J\u00f6hn") && !SkinFix.validName(""),
                "other names are not looked up");

        // a 64 x 64 slim skin: the right arm's front (3 wide at 44, 20) has columns a, b, c
        BufferedImage modern = new BufferedImage(64, 64, BufferedImage.TYPE_INT_ARGB);
        int a = 0xFF0000AA, b = 0xFF00BB00, c = 0xFFCC0000, side = 0xFF123456, below = 0xFF654321;
        for (int y = 20; y < 32; y++) {
            modern.setRGB(44, y, a);
            modern.setRGB(45, y, b);
            modern.setRGB(46, y, c);
            modern.setRGB(47, y, side);     // the slim arm's left side starts here
        }
        modern.setRGB(10, 40, below);
        BufferedImage old = SkinFix.oldLayout(modern, true);
        check(old.getWidth() == 64 && old.getHeight() == 32, "the old layout is 64 x 32");
        check(old.getRGB(44, 25) == a && old.getRGB(45, 25) == b && old.getRGB(46, 25) == b && old.getRGB(47, 25) == c,
                "a slim arm's front is widened to 4");
        check(old.getRGB(48, 25) == side, "its left side moves along");
        old = SkinFix.oldLayout(modern, false);
        check(old.getRGB(47, 25) == side, "a classic arm stays as it is");
        return failures;
    }
}
