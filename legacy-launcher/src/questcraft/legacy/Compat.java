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
    /**
     * Stands in for Thread's private {@code target} (the Runnable it runs), which ModLoader reads off the game's
     * thread to find the game. Java 19 moved it ({@code Thread.holder.task}); {@link #get} reads it from there.
     */
    public static Runnable threadTarget;
    private static final Field THREAD_TARGET;

    static {
        try {
            MODIFIERS = Compat.class.getDeclaredField("modifiers");
            THREAD_TARGET = Compat.class.getDeclaredField("threadTarget");
        } catch (NoSuchFieldException e) {
            throw new ExceptionInInitializerError(e);
        }
    }

    private Compat() {
    }

    public static Field getDeclaredField(Class<?> owner, String name) throws NoSuchFieldException {
        if (owner == Field.class && "modifiers".equals(name)) return MODIFIERS;
        if (owner == Thread.class && "target".equals(name)) return THREAD_TARGET;
        return owner.getDeclaredField(name);
    }

    public static Object get(Field field, Object instance) throws IllegalAccessException {
        if (field.equals(THREAD_TARGET) && instance instanceof Thread) return task((Thread) instance);
        return field.get(instance);
    }

    /** What a thread runs: Thread.holder.task since Java 19, Thread.target before (java.lang is opened to the game). */
    static Runnable task(Thread thread) {
        try {
            Field holder = Thread.class.getDeclaredField("holder");
            holder.setAccessible(true);
            Object fields = holder.get(thread);
            Field task = fields.getClass().getDeclaredField("task");
            task.setAccessible(true);
            return (Runnable) task.get(fields);
        } catch (ReflectiveOperationException | RuntimeException newer) {
            try {
                Field target = Thread.class.getDeclaredField("target");
                target.setAccessible(true);
                return (Runnable) target.get(thread);
            } catch (ReflectiveOperationException | RuntimeException older) {
                System.out.println("QuestCraft legacy launcher: cannot tell what thread " + thread.getName() + " runs: " + newer);
                return null;
            }
        }
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
