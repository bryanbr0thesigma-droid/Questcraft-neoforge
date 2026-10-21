package questcraft.legacy;

import java.nio.charset.StandardCharsets;

import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassVisitor;
import org.objectweb.asm.Handle;
import org.objectweb.asm.Label;
import org.objectweb.asm.MethodVisitor;
import org.objectweb.asm.Opcodes;

/**
 * Alpha and beta (up to b1.7.3) draw their quads as triangles: the Tesselator ("convertQuadsToTriangles", on in its
 * static initializer) repeats two corners of each quad for the second triangle, but not their normals, which keep
 * whatever an earlier draw left there. The desktop never shows it: lit models are drawn with flat shading, and flat
 * shading takes a triangle's last corner, which has its normal. gl4es (GLES 2 has no flat shading) mixes all three
 * corners, so on the headset half of every lit face (the held item, arms, players, mobs, chests, items in the
 * inventory) is shaded wrong. With the flag off the Tesselator draws real quads, every corner with its normal, as 1.0
 * and later always do.
 * <p>The class is found by its "Not tesselating!" message, the flag as the boolean read right after the check for
 * quads ({@code drawMode == 7}), and only an initializer that sets it to true is changed.</p>
 */
final class QuadFix {
    private QuadFix() {
    }

    /** The triangle-mode flag of the class if it is a Tesselator that has one, else null. */
    static String triangleFlag(byte[] bytes) {
        if (!new String(bytes, StandardCharsets.ISO_8859_1).contains("Not tesselating!")) return null;
        ClassReader reader = new ClassReader(bytes);
        String owner = reader.getClassName();
        String[] flag = new String[1];
        reader.accept(new ClassVisitor(Opcodes.ASM9) {
            @Override
            public MethodVisitor visitMethod(int access, String name, String descriptor, String signature, String[] exceptions) {
                return new Scan(owner, flag);
            }
        }, ClassReader.SKIP_DEBUG | ClassReader.SKIP_FRAMES);
        return flag[0];
    }

    /** Finds {@code bipush 7; if_icmpne; getstatic owner.flag:Z} (the quads check, then the flag). */
    private static final class Scan extends MethodVisitor {
        private final String owner;
        private final String[] flag;
        private int step;

        Scan(String owner, String[] flag) {
            super(Opcodes.ASM9);
            this.owner = owner;
            this.flag = flag;
        }

        @Override
        public void visitIntInsn(int opcode, int operand) {
            step = opcode == Opcodes.BIPUSH && operand == 7 ? 1 : 0;
        }

        @Override
        public void visitJumpInsn(int opcode, Label label) {
            step = step == 1 && opcode == Opcodes.IF_ICMPNE ? 2 : 0;
        }

        @Override
        public void visitFieldInsn(int opcode, String fieldOwner, String name, String descriptor) {
            if (step == 2 && opcode == Opcodes.GETSTATIC && fieldOwner.equals(owner) && descriptor.equals("Z") && flag[0] == null) flag[0] = name;
            step = 0;
        }

        @Override
        public void visitInsn(int opcode) {
            step = 0;
        }

        @Override
        public void visitVarInsn(int opcode, int var) {
            step = 0;
        }

        @Override
        public void visitTypeInsn(int opcode, String type) {
            step = 0;
        }

        @Override
        public void visitMethodInsn(int opcode, String methodOwner, String name, String descriptor, boolean isInterface) {
            step = 0;
        }

        @Override
        public void visitLdcInsn(Object value) {
            step = 0;
        }

        @Override
        public void visitIincInsn(int var, int increment) {
            step = 0;
        }
    }

    /** The static initializer with {@code iconst_1; putstatic owner.flag} made {@code iconst_0; putstatic owner.flag}. */
    static MethodVisitor initializer(MethodVisitor next, String owner, String flag) {
        return new Initializer(next, owner, flag);
    }

    /** Holds back an iconst_1 until it is known whether the flag is set with it. */
    private static final class Initializer extends MethodVisitor {
        private final String owner, flag;
        private boolean pendingTrue;

        Initializer(MethodVisitor next, String owner, String flag) {
            super(Opcodes.ASM9, next);
            this.owner = owner;
            this.flag = flag;
        }

        private void flush() {
            if (pendingTrue) {
                pendingTrue = false;
                super.visitInsn(Opcodes.ICONST_1);
            }
        }

        @Override
        public void visitInsn(int opcode) {
            flush();
            if (opcode == Opcodes.ICONST_1) pendingTrue = true;
            else super.visitInsn(opcode);
        }

        @Override
        public void visitFieldInsn(int opcode, String fieldOwner, String name, String descriptor) {
            if (pendingTrue && opcode == Opcodes.PUTSTATIC && fieldOwner.equals(owner) && name.equals(flag) && descriptor.equals("Z")) {
                pendingTrue = false;
                super.visitInsn(Opcodes.ICONST_0);
                System.out.println("QuestCraft legacy launcher: the Tesselator draws quads as quads (its triangles lose their normals on GLES)");
            } else {
                flush();
            }
            super.visitFieldInsn(opcode, fieldOwner, name, descriptor);
        }

        @Override
        public void visitIntInsn(int opcode, int operand) {
            flush();
            super.visitIntInsn(opcode, operand);
        }

        @Override
        public void visitVarInsn(int opcode, int var) {
            flush();
            super.visitVarInsn(opcode, var);
        }

        @Override
        public void visitTypeInsn(int opcode, String type) {
            flush();
            super.visitTypeInsn(opcode, type);
        }

        @Override
        public void visitMethodInsn(int opcode, String methodOwner, String name, String descriptor, boolean isInterface) {
            flush();
            super.visitMethodInsn(opcode, methodOwner, name, descriptor, isInterface);
        }

        @Override
        public void visitInvokeDynamicInsn(String name, String descriptor, Handle bootstrap, Object... arguments) {
            flush();
            super.visitInvokeDynamicInsn(name, descriptor, bootstrap, arguments);
        }

        @Override
        public void visitJumpInsn(int opcode, Label label) {
            flush();
            super.visitJumpInsn(opcode, label);
        }

        @Override
        public void visitLabel(Label label) {
            flush();
            super.visitLabel(label);
        }

        @Override
        public void visitLdcInsn(Object value) {
            flush();
            super.visitLdcInsn(value);
        }

        @Override
        public void visitIincInsn(int var, int increment) {
            flush();
            super.visitIincInsn(var, increment);
        }

        @Override
        public void visitTableSwitchInsn(int min, int max, Label dflt, Label... labels) {
            flush();
            super.visitTableSwitchInsn(min, max, dflt, labels);
        }

        @Override
        public void visitLookupSwitchInsn(Label dflt, int[] keys, Label[] labels) {
            flush();
            super.visitLookupSwitchInsn(dflt, keys, labels);
        }

        @Override
        public void visitMultiANewArrayInsn(String descriptor, int numDimensions) {
            flush();
            super.visitMultiANewArrayInsn(descriptor, numDimensions);
        }

        @Override
        public void visitFrame(int type, int numLocal, Object[] local, int numStack, Object[] stack) {
            flush();
            super.visitFrame(type, numLocal, local, numStack, stack);
        }

        @Override
        public void visitLineNumber(int line, Label start) {
            flush();
            super.visitLineNumber(line, start);
        }

        @Override
        public void visitMaxs(int maxStack, int maxLocals) {
            flush();
            super.visitMaxs(maxStack, maxLocals);
        }

        @Override
        public void visitEnd() {
            flush();
            super.visitEnd();
        }
    }
}
