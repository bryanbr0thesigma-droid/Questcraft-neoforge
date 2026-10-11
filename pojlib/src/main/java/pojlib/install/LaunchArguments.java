package pojlib.install;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Resolves the {@code arguments.jvm} / {@code arguments.game} lists of a version json.
 * <p>
 * Entries are either plain strings or objects of the form
 * {@code {"rules": [...], "value": "arg" | ["arg", ...]}}. Strings may contain {@code ${placeholders}}.
 * Gson hands the objects over as {@link Map}s and the arrays as {@link List}s.
 */
public final class LaunchArguments {
    private LaunchArguments() {}

    /**
     * @param arguments the raw {@code jvm} or {@code game} array, may be null
     * @param variables placeholder name (without {@code ${}}) to value
     * @return the arguments that apply to this device, with all known placeholders replaced
     */
    public static List<String> resolve(Object[] arguments, Map<String, String> variables) {
        List<String> resolved = new ArrayList<>();
        if (arguments == null) return resolved;

        for (Object argument : arguments) {
            if (argument instanceof String) {
                resolved.add(substitute((String) argument, variables));
            } else if (argument instanceof Map) {
                Map<?, ?> entry = (Map<?, ?>) argument;
                if (!rulesAllow(entry.get("rules"))) continue;

                Object value = entry.get("value");
                if (value instanceof String) {
                    resolved.add(substitute((String) value, variables));
                } else if (value instanceof List) {
                    for (Object v : (List<?>) value) {
                        resolved.add(substitute(String.valueOf(v), variables));
                    }
                }
            }
        }
        return resolved;
    }

    /** True if {@code argument} still has a {@code ${placeholder}} nobody supplied a value for. */
    public static boolean hasUnresolvedPlaceholder(String argument) {
        int start = argument.indexOf("${");
        return start >= 0 && argument.indexOf('}', start) > start;
    }

    static String substitute(String argument, Map<String, String> variables) {
        String result = argument;
        for (Map.Entry<String, String> variable : variables.entrySet()) {
            result = result.replace("${" + variable.getKey() + "}", variable.getValue());
        }
        return result;
    }

    /**
     * Mojang rule semantics: no rules means allowed; otherwise the last rule that applies wins
     * and nothing applying means disallowed.
     */
    static boolean rulesAllow(Object rules) {
        if (!(rules instanceof List) || ((List<?>) rules).isEmpty()) return true;

        boolean allowed = false;
        for (Object rule : (List<?>) rules) {
            if (!(rule instanceof Map)) continue;
            Map<?, ?> r = (Map<?, ?>) rule;
            if (ruleApplies(r)) {
                allowed = "allow".equals(r.get("action"));
            }
        }
        return allowed;
    }

    private static boolean ruleApplies(Map<?, ?> rule) {
        Object os = rule.get("os");
        if (os instanceof Map) {
            Map<?, ?> osRule = (Map<?, ?>) os;
            // Android is linux for every purpose the version jsons care about, and we are always arm64
            Object name = osRule.get("name");
            if (name != null && !"linux".equals(name)) return false;
            Object arch = osRule.get("arch");
            if (arch != null && !"arm64".equals(arch) && !"aarch64".equals(arch)) return false;
        }

        Object features = rule.get("features");
        if (features instanceof Map) {
            // we do not support any of the optional launcher features (demo mode, custom resolution, quick play)
            for (Object required : ((Map<?, ?>) features).values()) {
                if (Boolean.TRUE.equals(required)) return false;
            }
        }
        return true;
    }
}
