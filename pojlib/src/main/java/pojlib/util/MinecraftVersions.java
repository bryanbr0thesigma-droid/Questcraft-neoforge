package pojlib.util;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/** Helpers for lists of Minecraft release versions such as {@code 1.21.1}. */
public final class MinecraftVersions {
    private MinecraftVersions() {}

    /**
     * Orders release versions by their numbers ({@code 1.21.10} is newer than {@code 1.21.8}). Anything that is not
     * made of numbers and dots (snapshots) sorts after all releases, in the order it came.
     */
    public static final Comparator<String> NEWEST_FIRST = (a, b) -> {
        int[] x = parse(a), y = parse(b);
        if (x == null || y == null) {
            return x == null ? (y == null ? 0 : 1) : -1;
        }
        for (int i = 0; i < Math.max(x.length, y.length); i++) {
            int p = i < x.length ? x[i] : 0, q = i < y.length ? y[i] : 0;
            if (p != q) return Integer.compare(q, p);
        }
        return 0;
    };

    /** All versions of all lists, once each, newest first. Null lists are skipped. */
    public static String[] union(String[]... lists) {
        Set<String> all = new LinkedHashSet<>();
        for (String[] list : lists) {
            if (list == null) continue;
            for (String version : list) {
                if (version != null && !version.isEmpty()) all.add(version);
            }
        }
        List<String> sorted = new ArrayList<>(all);
        sorted.sort(NEWEST_FIRST); // stable, so snapshots keep their order
        return sorted.toArray(new String[0]);
    }

    /**
     * Whether the version draws with OpenGL's fixed-function calls ({@code glBegin}, {@code glMatrixMode}, display
     * lists), which MobileGlues only stubs: releases before 1.13 and the alpha, beta and classic versions
     * ({@code b1.7.3}, {@code a1.2.6}, {@code c0.30_01c}, {@code rd-132211}). Snapshots like {@code 24w14a} are modern.
     */
    public static boolean isLegacy(String version) {
        if (version == null || version.isEmpty()) return false;
        if (version.matches("(a|b|c|inf-|rd-).*")) return true;
        int[] numbers = parse(version.replaceAll("[-_ ].*", ""));
        return numbers != null && numbers.length >= 2 && numbers[0] == 1 && numbers[1] < 13;
    }

    public static boolean contains(String[] versions, String version) {
        if (versions == null) return false;
        for (String v : versions) {
            if (v.equals(version)) return true;
        }
        return false;
    }

    private static int[] parse(String version) {
        if (!version.matches("\\d+(\\.\\d+)*")) return null;
        String[] parts = version.split("\\.");
        int[] numbers = new int[parts.length];
        for (int i = 0; i < parts.length; i++) {
            numbers[i] = Integer.parseInt(parts[i]);
        }
        return numbers;
    }
}
