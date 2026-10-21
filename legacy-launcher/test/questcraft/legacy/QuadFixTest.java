package questcraft.legacy;

import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.Label;
import org.objectweb.asm.MethodVisitor;
import org.objectweb.asm.Opcodes;

/** Checks QuadFix through the Transformer on a made-up Tesselator (its flag, read after the quads check). Run by SelfTest. */
public final class QuadFixTest {
    private static int failures;

    private QuadFixTest() {
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
        try {
            check(!flag(transformed("Tess", true)), "a Tesselator's triangle mode starts off");
            check(flag(transformed("Other", false)), "a class without the Tesselator's message keeps its flag");
            check(QuadFix.triangleFlag(tesselator("Tess", true)).equals("b"), "the flag is the boolean after the quads check");
        } catch (Throwable t) {
            failures++;
            System.out.println("FAILED: QuadFix test: " + t);
        }
        return failures;
    }

    private static boolean flag(Class<?> c) throws Exception {
        return (Boolean) c.getMethod("flag").invoke(null);
    }

    private static Class<?> transformed(String name, boolean message) throws Exception {
        byte[] bytes = Transformer.transform(name, tesselator(name, message));
        return new ClassLoader(QuadFixTest.class.getClassLoader()) {
            Class<?> define() {
                return defineClass(name, bytes, 0, bytes.length);
            }
        }.define();
    }

    /** static boolean b = true; static int draw(int mode) { return mode == 7 && b ? 1 : 0; } and the message. */
    static byte[] tesselator(String name, boolean message) {
        ClassWriter w = new ClassWriter(ClassWriter.COMPUTE_FRAMES | ClassWriter.COMPUTE_MAXS);
        w.visit(Opcodes.V1_6, Opcodes.ACC_PUBLIC, name, null, "java/lang/Object", null);
        w.visitField(Opcodes.ACC_PRIVATE | Opcodes.ACC_STATIC, "b", "Z", null, null).visitEnd();
        MethodVisitor m = w.visitMethod(Opcodes.ACC_STATIC, "<clinit>", "()V", null, null);
        m.visitCode();
        m.visitInsn(Opcodes.ICONST_1);
        m.visitFieldInsn(Opcodes.PUTSTATIC, name, "b", "Z");
        m.visitInsn(Opcodes.RETURN);
        m.visitMaxs(0, 0);
        m.visitEnd();
        m = w.visitMethod(Opcodes.ACC_PUBLIC | Opcodes.ACC_STATIC, "draw", "(I)I", null, null);
        m.visitCode();
        Label no = new Label();
        m.visitVarInsn(Opcodes.ILOAD, 0);
        m.visitIntInsn(Opcodes.BIPUSH, 7);
        m.visitJumpInsn(Opcodes.IF_ICMPNE, no);
        m.visitFieldInsn(Opcodes.GETSTATIC, name, "b", "Z");
        m.visitJumpInsn(Opcodes.IFEQ, no);
        m.visitInsn(Opcodes.ICONST_1);
        m.visitInsn(Opcodes.IRETURN);
        m.visitLabel(no);
        m.visitInsn(Opcodes.ICONST_0);
        m.visitInsn(Opcodes.IRETURN);
        m.visitMaxs(0, 0);
        m.visitEnd();
        m = w.visitMethod(Opcodes.ACC_PUBLIC | Opcodes.ACC_STATIC, "flag", "()Z", null, null);
        m.visitCode();
        m.visitFieldInsn(Opcodes.GETSTATIC, name, "b", "Z");
        m.visitInsn(Opcodes.IRETURN);
        m.visitMaxs(0, 0);
        m.visitEnd();
        m = w.visitMethod(Opcodes.ACC_PUBLIC | Opcodes.ACC_STATIC, "message", "()Ljava/lang/String;", null, null);
        m.visitCode();
        m.visitLdcInsn(message ? "Not tesselating!" : "Something else");
        m.visitInsn(Opcodes.ARETURN);
        m.visitMaxs(0, 0);
        m.visitEnd();
        w.visitEnd();
        return w.toByteArray();
    }
}
