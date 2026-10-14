package questcraft.legacy;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;

/**
 * Writes a final field, as old code did by clearing its final with reflection: through sun.misc.Unsafe, reached by
 * reflection (it is not part of the Java 8 API the launcher is compiled against). Rarely called: only when a mod or
 * ModLoader sets a final field.
 */
final class UnsafeAccess {
    private static Object unsafe;
    private static Class<?> type;

    private UnsafeAccess() {
    }

    private static synchronized Object unsafe() throws ReflectiveOperationException {
        if (unsafe == null) {
            type = Class.forName("sun.misc.Unsafe");
            Field theUnsafe = type.getDeclaredField("theUnsafe");
            theUnsafe.setAccessible(true);
            unsafe = theUnsafe.get(null);
        }
        return unsafe;
    }

    private static Object call(String name, Class<?>[] parameters, Object... arguments) throws ReflectiveOperationException {
        Object u = unsafe();
        Method method = type.getMethod(name, parameters);
        return method.invoke(u, arguments);
    }

    static void set(Field field, Object instance, Object value) throws IllegalAccessException {
        try {
            boolean isStatic = Modifier.isStatic(field.getModifiers());
            Object base = isStatic ? call("staticFieldBase", new Class<?>[]{Field.class}, field) : instance;
            long offset = (Long) call(isStatic ? "staticFieldOffset" : "objectFieldOffset", new Class<?>[]{Field.class}, field);
            Class<?> fieldType = field.getType();
            String put;
            Class<?> valueType;
            if (!fieldType.isPrimitive()) {
                put = "putObjectVolatile";
                valueType = Object.class;
            } else {
                // putIntVolatile, putBooleanVolatile, ...
                String name = fieldType.getName();
                put = "put" + Character.toUpperCase(name.charAt(0)) + name.substring(1) + "Volatile";
                valueType = fieldType;
            }
            call(put, new Class<?>[]{Object.class, long.class, valueType}, base, offset, value);
        } catch (ReflectiveOperationException e) {
            IllegalAccessException failure = new IllegalAccessException("Cannot set final field " + field + ": " + e);
            failure.initCause(e);
            throw failure;
        }
    }
}
