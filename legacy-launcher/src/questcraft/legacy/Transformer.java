package questcraft.legacy;

import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;

import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassVisitor;
import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.MethodVisitor;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.commons.ClassRemapper;
import org.objectweb.asm.commons.SimpleRemapper;

/**
 * Rewrites, in the game's classes and its mods' as they load, what does not work on Java 22 without a screen. The
 * changes keep each instruction's stack shape, so the classes' stack maps stay valid as they are.
 * <ul>
 * <li>{@code java.awt.Frame}, {@code Canvas} (and LWJGL 2's {@code AWTGLCanvas}), {@code TextArea} and {@code java.applet.Applet} become
 * {@link HeadlessFrame}, {@link HeadlessCanvas}, {@link HeadlessTextArea} and {@link HeadlessApplet}: a headless JVM
 * cannot make windows, and the game's window draws nothing on the headset anyway.</li>
 * <li>{@code Field.modifiers} tricks (ModLoader's setPrivateValue, and mods' copies of it) go through {@link Compat}:
 * Java 12 hides that field, Java 18 refuses to set static finals by reflection. So does reading Thread's private
 * {@code target}, which ModLoader finds the game by and which Java 19 moved.</li>
 * <li>{@code Thread.stop()}, which throws since Java 20, interrupts instead (the network code stops its threads).</li>
 * <li>{@code Class.getPackage()} is null again for a class in no package, as before Java 9: ModLoader puts the
 * package's name in front of the mod classes it loads, which made ".mod_Something".</li>
 * <li>{@code Display.update()} goes through {@link Presenter}, where the frame is shown on the headset, and
 * {@code Display.setParent(canvas)} is dropped: the game draws without a window around it.</li>
 * <li>{@code Display.create(...)} goes through {@link Presenter} too, which gives the game a framebuffer to draw into
 * (the headset's GL context has no window), and binding framebuffer 0 binds that one.</li>
 * <li>The game's check for occlusion queries ("Advanced OpenGL") goes through {@link Compat}: the headset's LWJGL
 * says every extension is there, and the game would call query functions it does not have.</li>
 * <li>{@code URL.openConnection} goes through {@link SkinFix}: the servers the old versions get skins and capes from
 * are gone, Mojang's profile service has them.</li>
 * <li>With VR, the hooks of the version's glue ({@link HookSpec}) go into the classes they are for.</li>
 * </ul>
 */
public final class Transformer {
    private static final String COMPAT = "questcraft/legacy/Compat";
    private static final String PRESENTER = "questcraft/legacy/Presenter";
    private static final String SKIN_FIX = "questcraft/legacy/SkinFix";
    private static final SimpleRemapper AWT;

    static {
        Map<String, String> names = new HashMap<>();
        names.put("java/awt/Frame", "questcraft/legacy/HeadlessFrame");
        names.put("java/awt/TextArea", "questcraft/legacy/HeadlessTextArea");
        names.put("java/awt/Canvas", "questcraft/legacy/HeadlessCanvas");
        // 1.2.3 and 1.2.4 make their canvas from LWJGL 2's AWTGLCanvas (an AWT Canvas with a GL context of its own)
        names.put("org/lwjgl/opengl/AWTGLCanvas", "questcraft/legacy/HeadlessCanvas");
        names.put("java/applet/Applet", "questcraft/legacy/HeadlessApplet");
        AWT = new SimpleRemapper(names);
    }

    private Transformer() {
    }

    public static byte[] transform(String name, byte[] bytes) {
        HookSpec hooks = HookSpec.active();
        String internalName = name.replace('.', '/');
        boolean hooked = hooks != null && hooks.targets(internalName);
        if (!hooked && !mayNeedWork(bytes)) return bytes;
        try {
            ClassReader reader = new ClassReader(bytes);
            // the hooks add calls with arguments: the stack may need more room (no branches are added, the frames stay)
            ClassWriter writer = new ClassWriter(hooked ? ClassWriter.COMPUTE_MAXS : 0);
            ClassVisitor next = hooked ? hooks.visitor(internalName, writer, false) : writer;
            reader.accept(new ClassRemapper(new CompatClassVisitor(next), AWT), 0);
            byte[] result = writer.toByteArray();
            if (hooked) hooks.checked(internalName, false);
            return result;
        } catch (RuntimeException e) {
            System.out.println("QuestCraft legacy launcher: could not adapt " + name + ", loading it as it is: " + e);
            return bytes;
        }
    }

    private static boolean mayNeedWork(byte[] bytes) {
        String text = new String(bytes, StandardCharsets.ISO_8859_1);
        return text.contains("java/awt/Frame") || text.contains("java/awt/TextArea") || text.contains("java/awt/Canvas")
                || text.contains("java/applet/Applet") || text.contains("org/lwjgl/opengl/AWTGLCanvas")
                || text.contains("java/lang/reflect/Field") || text.contains("org/lwjgl/opengl/Display")
                || text.contains("glBindFramebuffer")
                || (text.contains("java/lang/Thread") && text.contains("stop")) || text.contains("getPackage")
                || text.contains("GL_ARB_occlusion_query") || text.contains("openConnection");
    }

    private static final class CompatClassVisitor extends ClassVisitor {
        CompatClassVisitor(ClassVisitor next) {
            super(Opcodes.ASM9, next);
        }

        @Override
        public MethodVisitor visitMethod(int access, String name, String descriptor, String signature, String[] exceptions) {
            return new CompatMethodVisitor(super.visitMethod(access, name, descriptor, signature, exceptions));
        }
    }

    private static final class CompatMethodVisitor extends MethodVisitor {
        CompatMethodVisitor(MethodVisitor next) {
            super(Opcodes.ASM9, next);
        }

        @Override
        public void visitFieldInsn(int opcode, String owner, String name, String descriptor) {
            if (opcode == Opcodes.GETFIELD && owner.equals("org/lwjgl/opengl/ContextCapabilities") && name.equals("GL_ARB_occlusion_query")) {
                super.visitMethodInsn(Opcodes.INVOKESTATIC, COMPAT, "occlusionQuery", "(Lorg/lwjgl/opengl/ContextCapabilities;)Z", false);
                return;
            }
            super.visitFieldInsn(opcode, owner, name, descriptor);
        }

        @Override
        public void visitMethodInsn(int opcode, String owner, String name, String descriptor, boolean isInterface) {
            if (opcode == Opcodes.INVOKEVIRTUAL) {
                String replacement = replacement(owner, name, descriptor);
                if (replacement != null) {
                    super.visitMethodInsn(Opcodes.INVOKESTATIC, replacement.substring(0, replacement.indexOf(' ')), name,
                            replacement.substring(replacement.indexOf(' ') + 1), false);
                    return;
                }
            } else if (opcode == Opcodes.INVOKESTATIC && presented(owner, name, descriptor)) {
                super.visitMethodInsn(Opcodes.INVOKESTATIC, PRESENTER, name, descriptor, false);
                return;
            }
            super.visitMethodInsn(opcode, owner, name, descriptor, isInterface);
        }

        /** A static call {@link Presenter} has a method of the same name and descriptor for. */
        private static boolean presented(String owner, String name, String descriptor) {
            switch (owner) {
                case "org/lwjgl/opengl/Display":
                    return name.equals("update") || name.equals("setParent")
                            || (name.equals("create") && (descriptor.equals("()V") || descriptor.equals("(Lorg/lwjgl/opengl/PixelFormat;)V")));
                case "org/lwjgl/opengl/GL30":
                case "org/lwjgl/opengl/ARBFramebufferObject":
                    return name.equals("glBindFramebuffer") && descriptor.equals("(II)V");
                case "org/lwjgl/opengl/EXTFramebufferObject":
                    return name.equals("glBindFramebufferEXT") && descriptor.equals("(II)V");
                default:
                    return false;
            }
        }

        /** "owner descriptor" of the static stand-in for an instance call, the receiver becoming the first argument */
        private static String replacement(String owner, String name, String descriptor) {
            switch (owner) {
                case "java/lang/reflect/Field":
                    if ((name.equals("set") && descriptor.equals("(Ljava/lang/Object;Ljava/lang/Object;)V"))
                            || (name.equals("get") && descriptor.equals("(Ljava/lang/Object;)Ljava/lang/Object;"))
                            || (name.equals("getInt") && descriptor.equals("(Ljava/lang/Object;)I"))
                            || (name.equals("setInt") && descriptor.equals("(Ljava/lang/Object;I)V"))) {
                        return COMPAT + " (Ljava/lang/reflect/Field;" + descriptor.substring(1);
                    }
                    return null;
                case "java/lang/Class":
                    if (name.equals("getDeclaredField") && descriptor.equals("(Ljava/lang/String;)Ljava/lang/reflect/Field;")) {
                        return COMPAT + " (Ljava/lang/Class;Ljava/lang/String;)Ljava/lang/reflect/Field;";
                    }
                    if (name.equals("getPackage") && descriptor.equals("()Ljava/lang/Package;")) {
                        return COMPAT + " (Ljava/lang/Class;)Ljava/lang/Package;";
                    }
                    return null;
                case "java/lang/Thread":
                    if (name.equals("stop") && descriptor.equals("()V")) return COMPAT + " (Ljava/lang/Thread;)V";
                    return null;
                case "java/net/URL":
                    if (name.equals("openConnection") && (descriptor.equals("()Ljava/net/URLConnection;")
                            || descriptor.equals("(Ljava/net/Proxy;)Ljava/net/URLConnection;"))) {
                        return SKIN_FIX + " (Ljava/net/URL;" + descriptor.substring(1);
                    }
                    return null;
                default:
                    return null;
            }
        }
    }
}
