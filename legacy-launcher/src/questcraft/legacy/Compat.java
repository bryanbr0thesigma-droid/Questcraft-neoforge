package questcraft.legacy;

import java.lang.reflect.Field;
import java.lang.reflect.Modifier;

/**
 * What {@link Transformer} puts in place of reflection and threading calls that Java 22 no longer allows the old way.
 * Every method takes the instance the original call was made on as its first argument.
 */
public final class Compat {
    /**
     * Stands in for {@code Field.modifiers}, which old code looks up to clear a field's {@code final}. Reading it
     * through {@link #getInt} gives the field's real modifiers, writing it through {@link #setInt} does nothing: setting
     * a final field then goes through {@link #set}, which can.
     */
    public static int modifiers;
    private static final Field MODIFIERS;

    static {
        try {
            MODIFIERS = Compat.class.getDeclaredField("modifiers");
        } catch (NoSuchFieldException e) {
            throw new ExceptionInInitializerError(e);
        }
    }

    private Compat() {
    }

    public static Field getDeclaredField(Class<?> owner, String name) throws NoSuchFieldException {
        if (owner == Field.class && "modifiers".equals(name)) return MODIFIERS;
        return owner.getDeclaredField(name);
    }

    public static int getInt(Field field, Object instance) throws IllegalAccessException {
        if (field.equals(MODIFIERS) && instance instanceof Field) return ((Field) instance).getModifiers();
        return field.getInt(instance);
    }

    public static void setInt(Field field, Object instance, int value) throws IllegalAccessException {
        if (field.equals(MODIFIERS) && instance instanceof Field) return;
        field.setInt(instance, value);
    }

    /** {@code Field.set}, which also sets final fields as Java 8 did once their final was cleared. */
    public static void set(Field field, Object instance, Object value) throws IllegalAccessException {
        try {
            field.set(instance, value);
        } catch (IllegalAccessException e) {
            if (!Modifier.isFinal(field.getModifiers())) throw e;
            UnsafeAccess.set(field, instance, value);
        }
    }

    /** {@code Class.getPackage()} as Java 8 had it: null for a class in no package (Java 9 gives an unnamed one). */
    public static Package getPackage(Class<?> type) {
        Package pack = type.getPackage();
        return pack == null || pack.getName().isEmpty() ? null : pack;
    }

    /** {@code Thread.stop()} throws since Java 20: the thread is interrupted instead. */
    public static void stop(Thread thread) {
        thread.interrupt();
    }
}
