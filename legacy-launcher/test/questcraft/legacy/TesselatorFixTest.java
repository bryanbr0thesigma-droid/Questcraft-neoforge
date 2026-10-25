package questcraft.legacy;

import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.MethodVisitor;
import org.objectweb.asm.Opcodes;

/** Checks TesselatorFix through the Transformer on a made-up Tesselator's corner copy. Run by SelfTest. */
public final class TesselatorFixTest {
    private static int failures;

    private TesselatorFixTest() {
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
            check(copied("Tess", true) == 42, "a Tesselator's repeated corner gets its normal");
            check(copied("Other", false) == 0, "a class without the Tesselator's message is left as it is");
            TesselatorFix.Patch patch = TesselatorFix.find(tesselator("Tess", true));
            check(patch != null && patch.index.equals("index") && patch.buffer.equals("vertexBuffer") && patch.offset == 1,
                    "the patch finds the index, the vertex buffer and the offset");
        } catch (Throwable t) {
            failures++;
            System.out.println("FAILED: TesselatorFix test: " + t);
        }
        return failures;
    }

    /** vertexBuffer[30] after repeat() with index 24 and the normal word of corner 0 (vertexBuffer[6]) set to 42 */
    private static int copied(String name, boolean message) throws Exception {
        byte[] bytes = Transformer.transform(name, tesselator(name, message));
        Class<?> c = new ClassLoader(TesselatorFixTest.class.getClassLoader()) {
            Class<?> define() {
                return defineClass(name, bytes, 0, bytes.length);
            }
        }.define();
        Object tess = c.getConstructor().newInstance();
        int[] buffer = new int[64];
        buffer[6] = 42;
        c.getField("vertexBuffer").set(tess, buffer);
        c.getField("index").setInt(tess, 24);
        c.getMethod("repeat").invoke(tess);
        return buffer[30];
    }

    /**
     * int[] vertexBuffer; int index; void repeat() { int offset = 8 * (3 - 0); vertexBuffer[index + 0] =
     * vertexBuffer[index - offset + 0]; } and the message, as the Tesselator's corner copy is laid out.
     */
    static byte[] tesselator(String name, boolean message) {
        ClassWriter w = new ClassWriter(ClassWriter.COMPUTE_MAXS);
        w.visit(Opcodes.V1_6, Opcodes.ACC_PUBLIC, name, null, "java/lang/Object", null);
        w.visitField(Opcodes.ACC_PUBLIC, "vertexBuffer", "[I", null, null).visitEnd();
        w.visitField(Opcodes.ACC_PUBLIC, "index", "I", null, null).visitEnd();
        MethodVisitor m = w.visitMethod(Opcodes.ACC_PUBLIC, "<init>", "()V", null, null);
        m.visitCode();
        m.visitVarInsn(Opcodes.ALOAD, 0);
        m.visitMethodInsn(Opcodes.INVOKESPECIAL, "java/lang/Object", "<init>", "()V", false);
        m.visitInsn(Opcodes.RETURN);
        m.visitMaxs(0, 0);
        m.visitEnd();
        m = w.visitMethod(Opcodes.ACC_PUBLIC, "repeat", "()V", null, null);
        m.visitCode();
        m.visitIntInsn(Opcodes.BIPUSH, 8);
        m.visitInsn(Opcodes.ICONST_3);
        m.visitInsn(Opcodes.ICONST_0);
        m.visitInsn(Opcodes.ISUB);
        m.visitInsn(Opcodes.IMUL);
        m.visitVarInsn(Opcodes.ISTORE, 1);
        m.visitVarInsn(Opcodes.ALOAD, 0);
        m.visitFieldInsn(Opcodes.GETFIELD, name, "vertexBuffer", "[I");
        m.visitVarInsn(Opcodes.ALOAD, 0);
        m.visitFieldInsn(Opcodes.GETFIELD, name, "index", "I");
        m.visitInsn(Opcodes.ICONST_0);
        m.visitInsn(Opcodes.IADD);
        m.visitVarInsn(Opcodes.ALOAD, 0);
        m.visitFieldInsn(Opcodes.GETFIELD, name, "vertexBuffer", "[I");
        m.visitVarInsn(Opcodes.ALOAD, 0);
        m.visitFieldInsn(Opcodes.GETFIELD, name, "index", "I");
        m.visitVarInsn(Opcodes.ILOAD, 1);
        m.visitInsn(Opcodes.ISUB);
        m.visitInsn(Opcodes.ICONST_0);
        m.visitInsn(Opcodes.IADD);
        m.visitInsn(Opcodes.IALOAD);
        m.visitInsn(Opcodes.IASTORE);
        m.visitInsn(Opcodes.RETURN);
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
