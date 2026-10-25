package questcraft.legacy;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.Enumeration;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;
import java.util.zip.ZipOutputStream;

/**
 * What build.sh makes a version's VR glue with (not in the launcher):
 * <pre>
 * map   mappings.tiny from to hooks.txt out.txt   the hooks in the other names (Feather's named to the game's official)
 * widen hooks.txt in.jar out.jar                  the jar with the members the glue uses made public (to compile it)
 * check hooks.txt game.jar                        every hook finds its place in the game (else the build fails)
 * pp    version versions.txt in out               the glue for one version: its #if lines worked out (below)
 * verify glue-named.jar named.jar glue.jar game.jar   the glue in the game's names uses only what the game has, and
 *                                                  overrides what it overrode in Feather's names (a remapping gone wrong)
 * </pre>
 * One glue serves many versions: in its sources {@code //#if >=b1.8.1}, {@code //#elif <1.3.1}, {@code //#else},
 * {@code //#endif} (in hooks.txt {@code #if ...}) keep or leave out the lines up to the next such line, comparing the
 * version built with others in the order of versions.txt; several comparisons are joined by {@code &&}. Lines left out
 * stay as empty lines, so the compiler's line numbers are the sources'. {@code #set NAME text} (kept like the lines
 * around it) makes {@code ${NAME}} in the lines after it that text: a member whose name or descriptor differs between
 * versions, named once.
 */
public final class GlueTool {
    private GlueTool() {
    }

    public static void main(String[] args) throws Exception {
        switch (args.length > 0 ? args[0] : "") {
            case "map":
                write(new File(args[5]), map(new Mappings(new File(args[1]), args[2], args[3]), read(new File(args[4]))));
                break;
            case "widen":
                widen(HookSpec.parse(read(new File(args[1]))), new File(args[2]), new File(args[3]));
                break;
            case "check":
                check(HookSpec.parse(read(new File(args[1]))), new File(args[2]));
                break;
            case "pp":
                preprocess(args[1], versions(new File(args[2])), new File(args[3]), new File(args[4]));
                break;
            case "verify":
                verify(new File(args[1]), new File(args[2]), new File(args[3]), new File(args[4]));
                break;
            default:
                throw new IllegalArgumentException("map | widen | check | pp | verify");
        }
    }

    // ---- pp ----

    /** The versions of versions.txt, oldest first. */
    static java.util.List<String> versions(File file) throws IOException {
        java.util.List<String> versions = new java.util.ArrayList<>();
        for (String line : read(file).split("\n")) {
            line = line.trim();
            if (!line.isEmpty() && !line.startsWith("#")) versions.add(line.split("\\s+")[0]);
        }
        return versions;
    }

    /** The glue tree for one version: Java sources and hooks.txt with their #if lines worked out, other files as they are. */
    static void preprocess(String version, java.util.List<String> order, File in, File out) throws IOException {
        if (!order.contains(version)) throw new IllegalArgumentException(version + " is not in versions.txt");
        File[] files = in.listFiles();
        if (files == null) return;
        for (File file : files) {
            File target = new File(out, file.getName());
            if (file.isDirectory()) {
                target.mkdirs();
                preprocess(version, order, file, target);
            } else if (file.getName().endsWith(".java") || file.getName().equals("hooks.txt")) {
                String prefix = file.getName().endsWith(".java") ? "//#" : "#";
                write(target, conditional(version, order, read(file), prefix, file.getPath()));
            } else {
                Files.copy(file.toPath(), target.toPath(), java.nio.file.StandardCopyOption.REPLACE_EXISTING);
            }
        }
    }

    /** The text with the lines its #if / #elif / #else / #endif lines leave out for the version made empty. */
    static String conditional(String version, java.util.List<String> order, String text, String prefix, String where) {
        StringBuilder out = new StringBuilder();
        // each open #if: whether its outside is kept, whether a branch has been kept, whether this branch is
        java.util.ArrayDeque<boolean[]> stack = new java.util.ArrayDeque<>();
        Map<String, String> names = new HashMap<>();
        boolean keep = true;
        int number = 0;
        for (String line : text.split("\n", -1)) {
            number++;
            String t = line.trim();
            String directive = t.startsWith(prefix + "if ") ? "if" : t.startsWith(prefix + "elif ") ? "elif"
                    : t.equals(prefix + "else") || t.startsWith(prefix + "else ") ? "else" : t.equals(prefix + "endif") || t.startsWith(prefix + "endif ") ? "endif"
                    : t.startsWith(prefix + "set ") ? "set" : null;
            if (directive == null) {
                if (keep) {
                    Matcher m = Pattern.compile("\\$\\{(\\w+)\\}").matcher(line);
                    StringBuffer substituted = new StringBuffer();
                    while (m.find()) {
                        String value = names.get(m.group(1));
                        if (value == null) throw new IllegalArgumentException(where + ":" + number + ": ${" + m.group(1) + "} is not set");
                        m.appendReplacement(substituted, Matcher.quoteReplacement(value));
                    }
                    m.appendTail(substituted);
                    out.append(substituted);
                }
                out.append('\n');
                continue;
            }
            String condition = t.substring(prefix.length() + directive.length()).trim();
            switch (directive) {
                case "set": {
                    String[] w = condition.split("\\s+", 2);
                    if (keep) names.put(w[0], w.length > 1 ? w[1] : "");
                    break;
                }
                case "if": {
                    boolean taken = keep && holds(condition, version, order, where, number);
                    stack.push(new boolean[]{keep, taken});
                    keep = taken;
                    break;
                }
                case "elif": {
                    if (stack.isEmpty()) throw new IllegalArgumentException(where + ":" + number + ": #elif without #if");
                    boolean[] frame = stack.peek();
                    boolean taken = frame[0] && !frame[1] && holds(condition, version, order, where, number);
                    if (taken) frame[1] = true;
                    keep = taken;
                    break;
                }
                case "else": {
                    if (stack.isEmpty()) throw new IllegalArgumentException(where + ":" + number + ": #else without #if");
                    boolean[] frame = stack.peek();
                    keep = frame[0] && !frame[1];
                    frame[1] = true;
                    break;
                }
                default: {
                    if (stack.isEmpty()) throw new IllegalArgumentException(where + ":" + number + ": #endif without #if");
                    keep = stack.pop()[0];
                }
            }
            out.append('\n');
        }
        if (!stack.isEmpty()) throw new IllegalArgumentException(where + ": #if without #endif");
        out.setLength(out.length() - 1);
        return out.toString();
    }

    /** ">=b1.8.1 && <1.3.1" for the version (all comparisons must hold). */
    static boolean holds(String condition, String version, java.util.List<String> order, String where, int number) {
        int index = order.indexOf(version);
        for (String part : condition.split("&&")) {
            Matcher m = Pattern.compile("^\\s*(>=|<=|==|!=|>|<)\\s*(\\S+)\\s*$").matcher(part);
            if (!m.matches()) throw new IllegalArgumentException(where + ":" + number + ": not a condition: " + condition);
            int other = order.indexOf(m.group(2));
            if (other < 0) throw new IllegalArgumentException(where + ":" + number + ": " + m.group(2) + " is not in versions.txt");
            boolean ok;
            switch (m.group(1)) {
                case ">=": ok = index >= other; break;
                case "<=": ok = index <= other; break;
                case ">": ok = index > other; break;
                case "<": ok = index < other; break;
                case "==": ok = index == other; break;
                default: ok = index != other;
            }
            if (!ok) return false;
        }
        return true;
    }

    // ---- map ----

    /** Tiny v2 mappings: classes, methods and fields from one namespace to another. */
    static final class Mappings {
        final Map<String, String> classes = new HashMap<>();
        final Map<String, String> methods = new HashMap<>();   // owner.name(desc) in "from" -> name in "to"
        final Map<String, String> fields = new HashMap<>();    // owner.name -> name

        Mappings(File tiny, String from, String to) throws IOException {
            String[] lines = read(tiny).split("\n");
            String[] header = lines[0].trim().split("\t");
            if (!header[0].equals("tiny") || !header[1].equals("2")) throw new IOException(tiny + " is not tiny v2");
            int f = index(header, from) - 3, t = index(header, to) - 3;
            // the class names of namespace 0 in "from": descriptors in the file are in namespace 0
            Map<String, String> toFrom = new HashMap<>();
            for (String line : lines) {
                String[] p = line.split("\t", -1);
                if (p[0].equals("c")) toFrom.put(p[1], name(p, 1 + f, 1));
            }
            String owner0 = null;
            for (String line : lines) {
                String[] p = line.split("\t", -1);
                if (p[0].equals("c")) {
                    owner0 = p[1];
                    classes.put(name(p, 1 + f, 1), name(p, 1 + t, 1));
                } else if (p.length > 1 && p[0].isEmpty() && (p[1].equals("m") || p[1].equals("f")) && owner0 != null) {
                    String owner = toFrom.get(owner0);
                    String descriptor = descriptor(p[2], toFrom);
                    String fromName = name(p, 3 + f, 3), toName = name(p, 3 + t, 3);
                    if (p[1].equals("m")) methods.put(owner + "." + fromName + descriptor, toName);
                    else fields.put(owner + "." + fromName, toName);
                }
            }
        }

        private static int index(String[] header, String namespace) throws IOException {
            for (int i = 3; i < header.length; i++) {
                if (header[i].equals(namespace)) return i;
            }
            throw new IOException("no namespace " + namespace);
        }

        /** A name, or the first namespace's when this one is left empty */
        private static String name(String[] p, int i, int first) {
            return i < p.length && !p[i].isEmpty() ? p[i] : p[first];
        }

        String type(String name) {
            String mapped = classes.get(name);
            return mapped != null ? mapped : name;
        }

        String descriptor(String descriptor) {
            return descriptor(descriptor, classes);
        }

        static String descriptor(String descriptor, Map<String, String> names) {
            Matcher m = Pattern.compile("L([^;]+);").matcher(descriptor);
            StringBuffer out = new StringBuffer();
            while (m.find()) {
                String mapped = names.get(m.group(1));
                m.appendReplacement(out, Matcher.quoteReplacement("L" + (mapped != null ? mapped : m.group(1)) + ";"));
            }
            m.appendTail(out);
            return out.toString();
        }

        String method(String owner, String name, String descriptor) {
            if (name.startsWith("<") || !classes.containsKey(owner)) return name;
            String mapped = methods.get(owner + "." + name + descriptor);
            if (mapped == null) throw new IllegalArgumentException("no method " + owner + "." + name + descriptor + " in the mappings");
            return mapped;
        }

        String field(String owner, String name) {
            if (!classes.containsKey(owner)) return name;
            String mapped = fields.get(owner + "." + name);
            if (mapped == null) throw new IllegalArgumentException("no field " + owner + "." + name + " in the mappings");
            return mapped;
        }
    }

    static String map(Mappings m, String text) {
        StringBuilder out = new StringBuilder("# made by build.sh from the hooks.txt in Feather's names: do not edit\n");
        // every line that does not map is told, not only the first (porting the glue to another version)
        StringBuilder missing = new StringBuilder();
        for (String raw : text.split("\n")) {
            String line = raw.contains("#") ? raw.substring(0, raw.indexOf('#')).trim() : raw.trim();
            if (line.isEmpty()) continue;
            try {
                out.append(mapLine(m, line)).append('\n');
            } catch (IllegalArgumentException e) {
                missing.append("\n  ").append(e.getMessage()).append("  (").append(line).append(')');
            }
        }
        if (missing.length() > 0) throw new IllegalArgumentException("lines that do not map:" + missing);
        return out.toString();
    }

    private static String mapLine(Mappings m, String line) {
        StringBuilder out = new StringBuilder();
        {
            String[] w = line.split("\\s+");
            // Class:Declaring, the method or field being the superclass's
            String declaringClass = w.length > 1 && w[1].contains(":") ? w[1].substring(w[1].indexOf(':') + 1) : w.length > 1 ? w[1] : null;
            if (w.length > 1 && w[1].contains(":")) w[1] = w[1].substring(0, w[1].indexOf(':'));
            switch (w[0]) {
                case "hooks":
                    out.append(line);
                    break;
                case "public":
                    out.append("public ").append(m.type(w[1])).append(' ');
                    if (w.length == 3) out.append(m.field(declaringClass, w[2]));
                    else out.append(m.method(declaringClass, w[2], w[3])).append(' ').append(m.descriptor(w[3]));
                    break;
                case "head":
                case "return":
                    out.append(w[0]).append(' ').append(m.type(w[1])).append(' ').append(m.method(declaringClass, w[2], w[3])).append(' ')
                            .append(m.descriptor(w[3])).append(' ').append(w[4]);
                    break;
                case "call":
                case "get": {
                    String owner = w[4], declaring = w[4];
                    if (owner.contains(":")) {
                        declaring = owner.substring(owner.indexOf(':') + 1);
                        owner = owner.substring(0, owner.indexOf(':'));
                    }
                    String member = w[0].equals("call") ? m.method(declaring, w[5], w[6]) : m.field(declaring, w[5]);
                    out.append(w[0]).append(' ').append(m.type(w[1])).append(' ').append(m.method(declaringClass, w[2], w[3])).append(' ')
                            .append(m.descriptor(w[3])).append(' ').append(m.type(owner)).append(' ').append(member).append(' ')
                            .append(m.descriptor(w[6])).append(' ').append(w[7]);
                    break;
                }
                default:
                    throw new IllegalArgumentException("unknown hook: " + line);
            }
        }
        return out.toString();
    }

    // ---- widen, check ----

    static void widen(HookSpec spec, File in, File out) throws IOException {
        Set<String> seen = new LinkedHashSet<>();
        try (ZipFile zip = new ZipFile(in); ZipOutputStream o = new ZipOutputStream(new FileOutputStream(out))) {
            Enumeration<? extends ZipEntry> entries = zip.entries();
            while (entries.hasMoreElements()) {
                ZipEntry entry = entries.nextElement();
                if (entry.isDirectory()) continue;
                byte[] bytes = bytes(zip, entry);
                String name = entry.getName();
                if (name.endsWith(".class")) {
                    String internal = name.substring(0, name.length() - 6);
                    if (spec.targets(internal)) {
                        bytes = spec.apply(internal, bytes, true);
                        seen.add(internal);
                    }
                }
                o.putNextEntry(new ZipEntry(name));
                o.write(bytes);
                o.closeEntry();
            }
        }
        fail(spec, seen, true);
    }

    static void check(HookSpec spec, File jar) throws IOException {
        Set<String> seen = new LinkedHashSet<>();
        try (ZipFile zip = new ZipFile(jar)) {
            Enumeration<? extends ZipEntry> entries = zip.entries();
            while (entries.hasMoreElements()) {
                ZipEntry entry = entries.nextElement();
                String name = entry.getName();
                if (!name.endsWith(".class")) continue;
                String internal = name.substring(0, name.length() - 6);
                if (!spec.targets(internal)) continue;
                spec.apply(internal, bytes(zip, entry), false);
                seen.add(internal);
            }
        }
        fail(spec, seen, false);
        System.out.println("all " + spec.directives().size() + " hooks find their place in " + jar.getName());
    }

    private static void fail(HookSpec spec, Set<String> seen, boolean accessOnly) {
        Set<String> failures = spec.failures();
        for (HookSpec.Directive directive : spec.directives()) {
            if (accessOnly && !directive.kind.equals("public")) continue;
            if (!seen.contains(directive.owner)) failures.add(directive.line + " (no such class)");
        }
        if (!failures.isEmpty()) throw new IllegalStateException("hooks without their place: " + failures);
    }

    // ---- verify ----

    /** A jar's classes: name, superclass and interfaces, members (name + descriptor). */
    private static final class Classes {
        final Map<String, String[]> parents = new HashMap<>();
        final Map<String, Set<String>> members = new HashMap<>();
        final Map<String, java.util.List<String>> overridable = new HashMap<>();

        Classes(File jar) throws IOException {
            try (ZipFile zip = new ZipFile(jar)) {
                Enumeration<? extends ZipEntry> entries = zip.entries();
                while (entries.hasMoreElements()) {
                    ZipEntry entry = entries.nextElement();
                    if (!entry.getName().endsWith(".class")) continue;
                    org.objectweb.asm.ClassReader reader = new org.objectweb.asm.ClassReader(bytes(zip, entry));
                    String name = reader.getClassName();
                    String[] interfaces = reader.getInterfaces();
                    String[] up = new String[interfaces.length + 1];
                    up[0] = reader.getSuperName();
                    System.arraycopy(interfaces, 0, up, 1, interfaces.length);
                    parents.put(name, up);
                    Set<String> own = new LinkedHashSet<>();
                    java.util.List<String> methods = new java.util.ArrayList<>();
                    reader.accept(new org.objectweb.asm.ClassVisitor(org.objectweb.asm.Opcodes.ASM9) {
                        @Override
                        public org.objectweb.asm.FieldVisitor visitField(int access, String field, String descriptor, String signature, Object value) {
                            own.add(field + ":" + descriptor);
                            return null;
                        }

                        @Override
                        public org.objectweb.asm.MethodVisitor visitMethod(int access, String method, String descriptor, String signature, String[] exceptions) {
                            own.add(method + descriptor);
                            boolean plain = (access & (org.objectweb.asm.Opcodes.ACC_STATIC | org.objectweb.asm.Opcodes.ACC_PRIVATE
                                    | org.objectweb.asm.Opcodes.ACC_SYNTHETIC)) == 0 && !method.startsWith("<");
                            if (plain) methods.add(method + descriptor);
                            return null;
                        }
                    }, org.objectweb.asm.ClassReader.SKIP_CODE);
                    members.put(name, own);
                    overridable.put(name, methods);
                }
            }
        }

        /** Whether the class or one above it in the jar has the member; false when it is not the jar's at all. */
        boolean has(String owner, String member) {
            java.util.ArrayDeque<String> todo = new java.util.ArrayDeque<>();
            todo.add(owner);
            while (!todo.isEmpty()) {
                String c = todo.poll();
                Set<String> own = members.get(c);
                if (own == null) continue;
                if (own.contains(member)) return true;
                if (member.startsWith("<init>")) return false;
                for (String up : parents.get(c)) if (up != null) todo.add(up);
            }
            return false;
        }

        /** Whether the class is the jar's, or has one of the jar's above it. */
        boolean ours(String name) {
            return members.containsKey(name);
        }
    }

    static void verify(File glueNamed, File named, File glue, File game) throws IOException {
        Classes gameClasses = new Classes(game), namedClasses = new Classes(named);
        Classes glueNamedClasses = new Classes(glueNamed), glueClasses = new Classes(glue);
        Set<String> problems = new LinkedHashSet<>();
        // every member of the game the glue uses is there
        try (ZipFile zip = new ZipFile(glue)) {
            Enumeration<? extends ZipEntry> entries = zip.entries();
            while (entries.hasMoreElements()) {
                ZipEntry entry = entries.nextElement();
                if (!entry.getName().endsWith(".class")) continue;
                String where = entry.getName();
                new org.objectweb.asm.ClassReader(bytes(zip, entry)).accept(new org.objectweb.asm.ClassVisitor(org.objectweb.asm.Opcodes.ASM9) {
                    @Override
                    public org.objectweb.asm.MethodVisitor visitMethod(int access, String name, String descriptor, String signature, String[] exceptions) {
                        return new org.objectweb.asm.MethodVisitor(org.objectweb.asm.Opcodes.ASM9) {
                            @Override
                            public void visitMethodInsn(int opcode, String owner, String method, String desc, boolean isInterface) {
                                look(owner, method + desc);
                            }

                            @Override
                            public void visitFieldInsn(int opcode, String owner, String field, String desc) {
                                look(owner, field + ":" + desc);
                            }

                            private void look(String owner, String member) {
                                if (gameClasses.ours(owner) && !gameClasses.has(owner, member)) problems.add(where + ": " + owner + "." + member);
                            }
                        };
                    }
                }, 0);
            }
        }
        // a glue class on a game class (a screen) overrides in the game's names what it overrode in Feather's
        for (String name : glueNamedClasses.members.keySet()) {
            String namedParent = glueNamedClasses.parents.get(name)[0];
            if (namedParent == null || !namedClasses.ours(namedParent)) continue;
            int before = 0, after = 0;
            for (String method : glueNamedClasses.overridable.get(name)) if (namedClasses.has(namedParent, method)) before++;
            String parent = glueClasses.parents.get(name)[0];
            for (String method : glueClasses.overridable.get(name)) if (gameClasses.has(parent, method)) after++;
            if (before != after) problems.add(name + " overrides " + before + " of the game's methods in Feather's names, " + after + " in the game's");
        }
        if (!problems.isEmpty()) throw new IllegalStateException("the glue in the game's names does not fit the game: " + problems);
        System.out.println("the glue uses only what " + game.getName() + " has");
    }

    private static byte[] bytes(ZipFile zip, ZipEntry entry) throws IOException {
        try (InputStream in = zip.getInputStream(entry)) {
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            byte[] buffer = new byte[16384];
            int n;
            while ((n = in.read(buffer)) > 0) out.write(buffer, 0, n);
            return out.toByteArray();
        }
    }

    static String read(File file) throws IOException {
        return new String(Files.readAllBytes(file.toPath()), StandardCharsets.UTF_8);
    }

    static void write(File file, String text) throws IOException {
        Files.write(file.toPath(), text.getBytes(StandardCharsets.UTF_8));
    }
}
