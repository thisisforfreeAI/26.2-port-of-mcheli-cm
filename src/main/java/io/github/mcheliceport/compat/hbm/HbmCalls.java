package io.github.mcheliceport.compat.hbm;

import java.lang.reflect.Executable;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.Arrays;

/** Public API reflection only: keeps HBM optional on clients and dedicated servers. */
final class HbmCalls {
    private HbmCalls() {}

    static Object create(String name, Object... args) throws ReflectiveOperationException {
        var constructor = select(Class.forName(name).getConstructors(), args);
        return constructor.newInstance(convert(constructor.getParameterTypes(), args));
    }

    static Object call(Object target, String name, Object... args) throws ReflectiveOperationException {
        Class<?> type = target instanceof Class<?> c ? c : target.getClass();
        Method[] candidates = Arrays.stream(type.getMethods())
                .filter(m -> m.getName().equals(name)
                        && Modifier.isStatic(m.getModifiers()) == (target instanceof Class<?>))
                .toArray(Method[]::new);
        Method method = select(candidates, args);
        return method.invoke(target instanceof Class<?> ? null : target, convert(method.getParameterTypes(), args));
    }

    static Object callStatic(String type, String method, Object... args) throws ReflectiveOperationException {
        return call(Class.forName(type), method, args);
    }

    static void field(Object target, String name, Object value) throws ReflectiveOperationException {
        var field = target.getClass().getField(name);
        field.set(target, convert(field.getType(), value));
    }

    private static <T extends Executable> T select(T[] members, Object[] args) throws NoSuchMethodException {
        T best = null;
        int bestScore = Integer.MAX_VALUE;
        boolean ambiguous = false;
        for (T member : members) {
            Class<?>[] params = member.getParameterTypes();
            if (params.length != args.length) continue;
            int score = 0;
            try {
                for (int i = 0; i < params.length; i++) {
                    convert(params[i], args[i]);
                    if (args[i] != null && !boxed(params[i]).isInstance(args[i])) score++;
                }
            } catch (IllegalArgumentException ignored) {
                continue;
            }
            if (score < bestScore) {
                best = member;
                bestScore = score;
                ambiguous = false;
            } else if (score == bestScore) {
                ambiguous = true;
            }
        }
        if (best == null || ambiguous) throw new NoSuchMethodException("No unambiguous HBM member for "
                + Arrays.toString(args));
        return best;
    }

    private static Object[] convert(Class<?>[] types, Object[] args) {
        Object[] converted = new Object[args.length];
        for (int i = 0; i < args.length; i++) converted[i] = convert(types[i], args[i]);
        return converted;
    }

    private static Object convert(Class<?> type, Object value) {
        if (value == null && !type.isPrimitive()) return null;
        Class<?> boxed = boxed(type);
        if (value instanceof Number n && !Double.isFinite(n.doubleValue()))
            throw new IllegalArgumentException("Non-finite HBM argument");
        if (boxed.isInstance(value)) return value;
        if (value instanceof Number n && Double.isFinite(n.doubleValue())) {
            if (boxed == Float.class && Math.abs(n.doubleValue()) <= Float.MAX_VALUE) return n.floatValue();
            if (boxed == Double.class) return n.doubleValue();
            if (boxed == Integer.class && n.doubleValue() == n.intValue()) return n.intValue();
            if (boxed == Long.class && n.doubleValue() == n.longValue()) return n.longValue();
        }
        throw new IllegalArgumentException("Cannot pass " + value + " to " + type.getName());
    }

    private static Class<?> boxed(Class<?> type) {
        if (type == int.class) return Integer.class;
        if (type == long.class) return Long.class;
        if (type == float.class) return Float.class;
        if (type == double.class) return Double.class;
        if (type == boolean.class) return Boolean.class;
        return type;
    }
}
