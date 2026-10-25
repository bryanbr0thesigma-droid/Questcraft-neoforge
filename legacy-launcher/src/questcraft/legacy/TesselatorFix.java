package questcraft.legacy;

import java.nio.charset.StandardCharsets;

import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassVisitor;
import org.objectweb.asm.Label;
import org.objectweb.asm.MethodVisitor;
import org.objectweb.asm.Opcodes;

/**
 * Alpha and beta (up to b1.7.3) draw their quads as triangles: the Tesselator ("convertQuadsToTriangles") repeats two
 * corners of each quad for the second triangle, copying their position, texture and colour words from the vertex
 * buffer but not their normal, which keeps whatever an earlier draw left there. The desktop never shows it: lit models
 * are drawn with flat shading, which takes a triangle's last corner, and that one has its normal. gl4es (GLES 2 has no
 * flat shading) mixes all three corners, so on the headset half of every lit face (the held item, arms, players, mobs,
 * chests, items in the inventory) was shaded wrong. This copies the normal word too, as the loop copies the others.
 * <p>The loop is found where it is in every version: the copy's offset is {@code int offset = 8 * (3 - i)} (an imul
 * stored in a local), read as {@code vertexBuffer[index - offset + k]} (a getfield of an int field, that local, isub);
 * the vertex buffer is the class's int[] field. The copy {@code vertexBuffer[index + 6] = vertexBuffer[index - offset
 * + 6]} goes right after the offset is stored. The class is found by its "Not tesselating!" message.</p>
 */
final class TesselatorFix {
    /** The normal's word in a vertex (x, y, z, u, v, colour, normal, unused) */
    static final int NORMAL = 6;

    /** Where the copy goes: the method, the offset's local, the index field, the vertex buffer field. */
    static final class Patch {
        final String method, descriptor, index, buffer;
        final int offset;

        Patch(String method, String descriptor, int offset, String index, String buffer) {
            this.method = method;
            this.descriptor = descriptor;
            this.offset = offset;
            this.index = index;
            this.buffer = buffer;
        }
    }

    private TesselatorFix() {
    }

    /** The patch for the class if it is a Tesselator with the copy loop, else null. */
    static Patch find(byte[] bytes) {
        if (!new String(bytes, StandardCharsets.ISO_8859_1).contains("Not tesselating!")) return null;
        ClassReader reader = new ClassReader(bytes);
        String owner = reader.getClassName();
        Patch[] found = new Patch[1];
        reader.accept(new ClassVisitor(Opcodes.ASM9) {
            @Override
            public MethodVisitor visitMethod(int access, String name, String descriptor, String signature, String[] exceptions) {
                if (found[0] != null) return null;
                return new Scan(owner, name, descriptor, found);
            }
        }, ClassReader.SKIP_DEBUG | ClassReader.SKIP_FRAMES);
        return found[0];
    }

    /** Looks in one method for {@code imul; istore n} and later {@code getfield owner.f:I; iload n; isub}. */
    private static final class Scan extends MethodVisitor {
        private final String owner, name, descriptor;
        private final Patch[] found;
        private int offset = -1, last = -1, lastVar = -1;
        private String lastIntField, buffer;

        Scan(String owner, String name, String descriptor, Patch[] found) {
            super(Opcodes.ASM9);
            this.owner = owner;
            this.name = name;
            this.descriptor = descriptor;
            this.found = found;
        }

        private void step(int opcode) {
            last = opcode;
        }

        @Override
        public void visitInsn(int opcode) {
            if (opcode == Opcodes.ISUB && offset >= 0 && lastVar == offset && lastIntField != null && buffer != null && found[0] == null) {
                found[0] = new Patch(name, descriptor, offset, lastIntField, buffer);
            }
            step(opcode);
            lastIntField = null;
            lastVar = -1;
        }

        @Override
        public void visitVarInsn(int opcode, int var) {
            if (opcode == Opcodes.ISTORE && last == Opcodes.IMUL && offset < 0) offset = var;
            // iload offset right after a getfield of an int: keep the field for the isub that follows
            lastVar = opcode == Opcodes.ILOAD ? var : -1;
            if (opcode != Opcodes.ILOAD) lastIntField = null;
            step(opcode);
        }

        @Override
        public void visitFieldInsn(int opcode, String fieldOwner, String fieldName, String fieldDescriptor) {
            if (opcode == Opcodes.GETFIELD && fieldOwner.equals(owner) && fieldDescriptor.equals("[I")) buffer = fieldName;
            lastIntField = opcode == Opcodes.GETFIELD && fieldOwner.equals(owner) && fieldDescriptor.equals("I") ? fieldName : null;
            lastVar = -1;
            step(opcode);
        }

        @Override
        public void visitIntInsn(int opcode, int operand) {
            lastIntField = null;
            lastVar = -1;
            step(opcode);
        }

        @Override
        public void visitJumpInsn(int opcode, Label label) {
            lastIntField = null;
            lastVar = -1;
            step(opcode);
        }

        @Override
        public void visitMethodInsn(int opcode, String methodOwner, String methodName, String methodDescriptor, boolean isInterface) {
            lastIntField = null;
            lastVar = -1;
            step(opcode);
        }

        @Override
        public void visitTypeInsn(int opcode, String type) {
            lastIntField = null;
            lastVar = -1;
            step(opcode);
        }

        @Override
        public void visitLdcInsn(Object value) {
            lastIntField = null;
            lastVar = -1;
            step(Opcodes.LDC);
        }

        @Override
        public void visitIincInsn(int var, int increment) {
            lastIntField = null;
            lastVar = -1;
            step(Opcodes.IINC);
        }
    }

    /** The method with the normal's copy after the offset is stored (no branch added, the stack no deeper). */
    static MethodVisitor patch(MethodVisitor next, String owner, Patch patch) {
        return new MethodVisitor(Opcodes.ASM9, next) {
            private int last = -1;
            private boolean done;

            @Override
            public void visitInsn(int opcode) {
                last = opcode;
                super.visitInsn(opcode);
            }

            @Override
            public void visitVarInsn(int opcode, int var) {
                super.visitVarInsn(opcode, var);
                if (!done && opcode == Opcodes.ISTORE && var == patch.offset && last == Opcodes.IMUL) {
                    done = true;
                    // vertexBuffer[index + 6] = vertexBuffer[index - offset + 6]
                    super.visitVarInsn(Opcodes.ALOAD, 0);
                    super.visitFieldInsn(Opcodes.GETFIELD, owner, patch.buffer, "[I");
                    super.visitVarInsn(Opcodes.ALOAD, 0);
                    super.visitFieldInsn(Opcodes.GETFIELD, owner, patch.index, "I");
                    super.visitIntInsn(Opcodes.BIPUSH, NORMAL);
                    super.visitInsn(Opcodes.IADD);
                    super.visitVarInsn(Opcodes.ALOAD, 0);
                    super.visitFieldInsn(Opcodes.GETFIELD, owner, patch.buffer, "[I");
                    super.visitVarInsn(Opcodes.ALOAD, 0);
                    super.visitFieldInsn(Opcodes.GETFIELD, owner, patch.index, "I");
                    super.visitVarInsn(Opcodes.ILOAD, patch.offset);
                    super.visitInsn(Opcodes.ISUB);
                    super.visitIntInsn(Opcodes.BIPUSH, NORMAL);
                    super.visitInsn(Opcodes.IADD);
                    super.visitInsn(Opcodes.IALOAD);
                    super.visitInsn(Opcodes.IASTORE);
                    System.out.println("QuestCraft legacy launcher: the Tesselator's triangles keep their normals");
                }
                last = opcode;
            }

            @Override
            public void visitIntInsn(int opcode, int operand) {
                last = opcode;
                super.visitIntInsn(opcode, operand);
            }

            @Override
            public void visitFieldInsn(int opcode, String fieldOwner, String name, String descriptor) {
                last = opcode;
                super.visitFieldInsn(opcode, fieldOwner, name, descriptor);
            }

            @Override
            public void visitJumpInsn(int opcode, Label label) {
                last = opcode;
                super.visitJumpInsn(opcode, label);
            }

            @Override
            public void visitMethodInsn(int opcode, String methodOwner, String name, String descriptor, boolean isInterface) {
                last = opcode;
                super.visitMethodInsn(opcode, methodOwner, name, descriptor, isInterface);
            }
        };
    }
}
