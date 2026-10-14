package questcraft.legacy;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Consumer;

import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassVisitor;
import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.FieldVisitor;
import org.objectweb.asm.MethodVisitor;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.Type;

/**
 * Hooks of a version's VR "glue" into the game's classes, put in as they load ({@link Transformer}). The list comes
 * with the glue (its hooks.txt), in the game's own names: it is written with readable names and translated when the
 * launcher is built, as the glue itself is. One hook a line ({@code #} starts a comment):
 * <pre>
 * hooks  HooksClass                                   the class whose static methods the hooks are
 * public Class field                                  a field made public (for the glue)
 * public Class method descriptor                      a method made public
 * head   Class method descriptor hook                 hook(object, arguments...) called first
 * return Class method descriptor hook                 hook(object, arguments...) called before each return
 * call   Class method descriptor Owner name descriptor hook    in that method, Owner.name(...) calls hook(object, arguments...) instead
 * get    Class method descriptor Owner field type hook          in that method, reading Owner.field calls hook(object) instead
 * </pre>
 * A hook has the descriptor of what it stands for, with the object first (none for static ones). A class may be
 * written {@code Class:Declaring} when the method or field is declared in a superclass (an override, or a call through
 * a subclass): only the translation into the game's names uses the second name.
 * <p>
 * The game has to be the one the glue was made for: a hook that does not find its place is logged and makes
 * {@link #failures()} non-empty, so VR stays off (the glue's hooks do what the game did when it is off).
 */
public final class HookSpec {
    private static volatile HookSpec active;
    private static volatile Consumer<String> onFailure;

    final String hooksClass;
    private final List<Directive> directives;
    private final Map<String, List<Directive>> byClass = new HashMap<>();
    private final Set<String> failures = new LinkedHashSet<>();

    static final class Directive {
        final String kind, owner;
        final String[] parts;   // the tokens after the class
        final String line;
        boolean applied;

        Directive(String kind, String owner, String[] parts, String line) {
            this.kind = kind;
            this.owner = owner;
            this.parts = parts;
            this.line = line;
        }
    }

    private HookSpec(String hooksClass, List<Directive> directives) {
        this.hooksClass = hooksClass;
        this.directives = directives;
        for (Directive directive : directives) byClass.computeIfAbsent(directive.owner, k -> new ArrayList<>()).add(directive);
    }

    public static HookSpec parse(String text) {
        String hooksClass = null;
        List<Directive> directives = new ArrayList<>();
        for (String raw : text.split("\n")) {
            String line = raw.contains("#") ? raw.substring(0, raw.indexOf('#')) : raw;
            line = line.trim();
            if (line.isEmpty()) continue;
            String[] tokens = line.split("\\s+");
            String kind = tokens[0];
            int expected;
            switch (kind) {
                case "hooks":
                    if (tokens.length != 2) throw new IllegalArgumentException("hooks: " + line);
                    hooksClass = tokens[1];
                    continue;
                case "public":
                    expected = tokens.length == 3 ? 3 : 4;
                    break;
                case "head":
                case "return":
                    expected = 5;
                    break;
                case "call":
                case "get":
                    expected = 8;
                    break;
                default:
                    throw new IllegalArgumentException("unknown hook: " + line);
            }
            if (tokens.length != expected) throw new IllegalArgumentException("wrong number of words: " + line);
            String[] parts = new String[tokens.length - 2];
            System.arraycopy(tokens, 2, parts, 0, parts.length);
            if (!kind.equals("public") && parts.length > 2 && kind.matches("call|get")) parts[2] = withoutDeclaring(parts[2]);
            directives.add(new Directive(kind, withoutDeclaring(tokens[1]), parts, line));
        }
        if (hooksClass == null && hasHooks(directives)) throw new IllegalArgumentException("no hooks class");
        return new HookSpec(hooksClass, directives);
    }

    private static String withoutDeclaring(String type) {
        return type.contains(":") ? type.substring(0, type.indexOf(':')) : type;
    }

    private static boolean hasHooks(List<Directive> directives) {
        for (Directive directive : directives) {
            if (!directive.kind.equals("public")) return true;
        }
        return false;
    }

    /** The hooks every game class loaded from now on gets; {@code onFailure} hears of a hook without its place. */
    public static void activate(HookSpec spec, Consumer<String> onFailure) {
        HookSpec.onFailure = onFailure;
        active = spec;
    }

    static HookSpec active() {
        return active;
    }

    public List<Directive> directives() {
        return directives;
    }

    public boolean targets(String internalName) {
        return byClass.containsKey(internalName);
    }

    /** The hooks that did not find their place, as their lines. */
    public synchronized Set<String> failures() {
        return new LinkedHashSet<>(failures);
    }

    /** The class with its hooks in (and only the access changes when {@code accessOnly}: for the glue's compiler). */
    public byte[] apply(String internalName, byte[] bytes, boolean accessOnly) {
        ClassReader reader = new ClassReader(bytes);
        ClassWriter writer = new ClassWriter(ClassWriter.COMPUTE_MAXS);
        reader.accept(visitor(internalName, writer, accessOnly), 0);
        byte[] result = writer.toByteArray();
        checked(internalName, accessOnly);
        return result;
    }

    /** What puts the hooks in; {@link #checked} afterwards says which did not find their place. */
    ClassVisitor visitor(String internalName, ClassVisitor next, boolean accessOnly) {
        List<Directive> list = byClass.get(internalName);
        if (list == null) return next;
        for (Directive directive : list) directive.applied = false;
        return new HookClassVisitor(next, list, accessOnly);
    }

    synchronized void checked(String internalName, boolean accessOnly) {
        List<Directive> list = byClass.get(internalName);
        if (list == null) return;
        for (Directive directive : list) {
            if (directive.applied || (accessOnly && !directive.kind.equals("public"))) continue;
            failures.add(directive.line);
            System.out.println("QuestCraft VR: this game has no place for the hook \"" + directive.line + "\"");
            Consumer<String> listener = onFailure;
            if (!accessOnly && listener != null) listener.accept(directive.line);
        }
    }

    private final class HookClassVisitor extends ClassVisitor {
        private final List<Directive> list;
        private final boolean accessOnly;

        HookClassVisitor(ClassVisitor next, List<Directive> list, boolean accessOnly) {
            super(Opcodes.ASM9, next);
            this.list = list;
            this.accessOnly = accessOnly;
        }

        @Override
        public FieldVisitor visitField(int access, String name, String descriptor, String signature, Object value) {
            for (Directive directive : list) {
                if (directive.kind.equals("public") && directive.parts.length == 1 && directive.parts[0].equals(name)) {
                    access = widened(access);
                    directive.applied = true;
                }
            }
            return super.visitField(access, name, descriptor, signature, value);
        }

        @Override
        public MethodVisitor visitMethod(int access, String name, String descriptor, String signature, String[] exceptions) {
            List<Directive> hooks = new ArrayList<>();
            for (Directive directive : list) {
                if (directive.parts.length < 2 || !directive.parts[0].equals(name) || !directive.parts[1].equals(descriptor)) continue;
                if (directive.kind.equals("public")) {
                    access = widened(access);
                    directive.applied = true;
                } else if (!accessOnly) {
                    hooks.add(directive);
                }
            }
            MethodVisitor next = super.visitMethod(access, name, descriptor, signature, exceptions);
            return hooks.isEmpty() ? next : new HookMethodVisitor(next, hooks, (access & Opcodes.ACC_STATIC) != 0, descriptor);
        }
    }

    static int widened(int access) {
        return (access & ~(Opcodes.ACC_PRIVATE | Opcodes.ACC_PROTECTED)) | Opcodes.ACC_PUBLIC;
    }

    private final class HookMethodVisitor extends MethodVisitor {
        private final List<Directive> hooks;
        private final boolean isStatic;
        private final String descriptor;
        private final String self;

        HookMethodVisitor(MethodVisitor next, List<Directive> hooks, boolean isStatic, String descriptor) {
            super(Opcodes.ASM9, next);
            this.hooks = hooks;
            this.isStatic = isStatic;
            this.descriptor = descriptor;
            this.self = hooks.get(0).owner;
        }

        @Override
        public void visitCode() {
            super.visitCode();
            for (Directive directive : hooks) {
                if (directive.kind.equals("head")) callWithArguments(directive);
            }
        }

        @Override
        public void visitInsn(int opcode) {
            if (opcode >= Opcodes.IRETURN && opcode <= Opcodes.RETURN) {
                for (Directive directive : hooks) {
                    if (directive.kind.equals("return")) callWithArguments(directive);
                }
            }
            super.visitInsn(opcode);
        }

        /** hook(this, arguments...), returning nothing */
        private void callWithArguments(Directive directive) {
            StringBuilder hookDescriptor = new StringBuilder("(");
            int slot = 0;
            if (!isStatic) {
                super.visitVarInsn(Opcodes.ALOAD, 0);
                hookDescriptor.append('L').append(self).append(';');
                slot = 1;
            }
            for (Type argument : Type.getArgumentTypes(descriptor)) {
                super.visitVarInsn(argument.getOpcode(Opcodes.ILOAD), slot);
                hookDescriptor.append(argument.getDescriptor());
                slot += argument.getSize();
            }
            hookDescriptor.append(")V");
            super.visitMethodInsn(Opcodes.INVOKESTATIC, hooksClass, directive.parts[2], hookDescriptor.toString(), false);
            directive.applied = true;
        }

        @Override
        public void visitMethodInsn(int opcode, String owner, String name, String descriptor, boolean isInterface) {
            for (Directive directive : hooks) {
                if (directive.kind.equals("call") && directive.parts[2].equals(owner) && directive.parts[3].equals(name)
                        && directive.parts[4].equals(descriptor)) {
                    String hookDescriptor = opcode == Opcodes.INVOKESTATIC ? descriptor : "(L" + owner + ";" + descriptor.substring(1);
                    super.visitMethodInsn(Opcodes.INVOKESTATIC, hooksClass, directive.parts[5], hookDescriptor, false);
                    directive.applied = true;
                    return;
                }
            }
            super.visitMethodInsn(opcode, owner, name, descriptor, isInterface);
        }

        @Override
        public void visitFieldInsn(int opcode, String owner, String name, String descriptor) {
            if (opcode == Opcodes.GETFIELD || opcode == Opcodes.GETSTATIC) {
                for (Directive directive : hooks) {
                    if (directive.kind.equals("get") && directive.parts[2].equals(owner) && directive.parts[3].equals(name)
                            && directive.parts[4].equals(descriptor)) {
                        String hookDescriptor = (opcode == Opcodes.GETFIELD ? "(L" + owner + ";)" : "()") + descriptor;
                        super.visitMethodInsn(Opcodes.INVOKESTATIC, hooksClass, directive.parts[5], hookDescriptor, false);
                        directive.applied = true;
                        return;
                    }
                }
            }
            super.visitFieldInsn(opcode, owner, name, descriptor);
        }
    }
}
