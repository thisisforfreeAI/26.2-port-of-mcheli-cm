package io.github.mcheliceport.content;

import java.lang.reflect.Array;
import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import java.util.regex.Pattern;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

/**
 * Field-by-field comparison of content definitions, for the parser parity and round-trip tests.
 * Numbers are compared with a small tolerance, since TXT values pass through {@code float} and
 * YAML values through {@code double}.
 */
final class DeepCompare {

    private static final double REL_TOLERANCE = 1e-5;
    private static final int MAX_DEPTH = 16;

    private final Set<String> ignoredFields;
    private final double absTolerance;
    private final List<String> differences = new ArrayList<>();
    private final Map<Pattern, Double> leftOffsets = new LinkedHashMap<>();

    /**
     * @param ignoredFields field names to skip, as {@code SimpleClassName.field} or {@code field}
     * @param absTolerance  largest absolute difference of two numbers that still counts as equal
     */
    DeepCompare(Set<String> ignoredFields, double absTolerance) {
        this.ignoredFields = ignoredFields;
        this.absTolerance = absTolerance;
    }

    /** Adds {@code offset} to left-hand numbers whose path matches {@code pathRegex} before comparing. */
    DeepCompare withLeftOffset(String pathRegex, double offset) {
        this.leftOffsets.put(Pattern.compile(pathRegex), offset);
        return this;
    }

    List<String> differences() {
        return this.differences;
    }

    void compare(String path, Object a, Object b) {
        this.compare(path, a, b, 0);
    }

    private void compare(String path, Object a, Object b, int depth) {
        if (a == b) {
            return;
        }
        if (a == null || b == null) {
            // An empty list and no list mean the same to the game.
            if (isEmptyCollection(a) || isEmptyCollection(b)) {
                return;
            }
            this.differences.add(path + ": " + describe(a) + " vs " + describe(b));
            return;
        }
        if (depth > MAX_DEPTH) {
            this.differences.add(path + ": too deep");
            return;
        }
        if (a instanceof Number na && b instanceof Number nb) {
            double left = na.doubleValue();
            for (Map.Entry<Pattern, Double> rule : this.leftOffsets.entrySet()) {
                if (rule.getKey().matcher(path).matches()) {
                    left += rule.getValue();
                }
            }
            if (!this.closeEnough(left, nb.doubleValue())) {
                this.differences.add(path + ": " + a + " vs " + b);
            }
            return;
        }
        boolean bothMaps = a instanceof Map<?, ?> && b instanceof Map<?, ?>;
        if (a.getClass() != b.getClass() && !bothMaps) {
            this.differences.add(path + ": type " + a.getClass().getSimpleName() + " vs " + b.getClass().getSimpleName());
            return;
        }
        if (a instanceof String || a instanceof Boolean || a instanceof Character || a instanceof Enum<?>) {
            if (!a.equals(b)) {
                this.differences.add(path + ": " + describe(a) + " vs " + describe(b));
            }
            return;
        }
        if (a instanceof Vec3 va && b instanceof Vec3 vb) {
            this.compare(path + ".x", va.x, vb.x, depth + 1);
            this.compare(path + ".y", va.y, vb.y, depth + 1);
            this.compare(path + ".z", va.z, vb.z, depth + 1);
            return;
        }
        if (a instanceof AABB ba && b instanceof AABB bb) {
            this.compare(path + ".min", new Vec3(ba.minX, ba.minY, ba.minZ), new Vec3(bb.minX, bb.minY, bb.minZ),
                    depth + 1);
            this.compare(path + ".max", new Vec3(ba.maxX, ba.maxY, ba.maxZ), new Vec3(bb.maxX, bb.maxY, bb.maxZ),
                    depth + 1);
            return;
        }
        if (a.getClass().isArray()) {
            int la = Array.getLength(a);
            int lb = Array.getLength(b);
            if (la != lb) {
                this.differences.add(path + ": length " + la + " vs " + lb);
                return;
            }
            for (int i = 0; i < la; i++) {
                this.compare(path + "[" + i + "]", Array.get(a, i), Array.get(b, i), depth + 1);
            }
            return;
        }
        if (a instanceof Map<?, ?> ma && b instanceof Map<?, ?> mb) {
            Set<String> keys = new TreeSet<>();
            ma.keySet().forEach(k -> keys.add(String.valueOf(k)));
            mb.keySet().forEach(k -> keys.add(String.valueOf(k)));
            for (String key : keys) {
                this.compare(path + "{" + key + "}", findKey(ma, key), findKey(mb, key), depth + 1);
            }
            return;
        }
        if (a instanceof Collection<?> ca && b instanceof Collection<?> cb) {
            if (ca.size() != cb.size()) {
                this.differences.add(path + ": size " + ca.size() + " vs " + cb.size());
                return;
            }
            Iterator<?> ia = ca.iterator();
            Iterator<?> ib = cb.iterator();
            for (int i = 0; ia.hasNext(); i++) {
                this.compare(path + "[" + i + "]", ia.next(), ib.next(), depth + 1);
            }
            return;
        }
        if (a.getClass().getName().startsWith("io.github.mcheliceport.")) {
            for (Class<?> c = a.getClass(); c != null && c != Object.class; c = c.getSuperclass()) {
                for (Field f : c.getDeclaredFields()) {
                    if (Modifier.isStatic(f.getModifiers()) || f.isSynthetic()) {
                        continue;
                    }
                    if (this.ignoredFields.contains(f.getName())
                            || this.ignoredFields.contains(c.getSimpleName() + "." + f.getName())) {
                        continue;
                    }
                    f.setAccessible(true);
                    try {
                        this.compare(path + "." + f.getName(), f.get(a), f.get(b), depth + 1);
                    } catch (IllegalAccessException e) {
                        throw new IllegalStateException(e);
                    }
                }
            }
            return;
        }
        if (!a.equals(b)) {
            this.differences.add(path + ": " + describe(a) + " vs " + describe(b));
        }
    }

    private static Object findKey(Map<?, ?> map, String key) {
        for (Map.Entry<?, ?> e : map.entrySet()) {
            if (String.valueOf(e.getKey()).equals(key)) {
                return e.getValue();
            }
        }
        return null;
    }

    private boolean closeEnough(double a, double b) {
        if (Double.compare(a, b) == 0) {
            return true;
        }
        double diff = Math.abs(a - b);
        return diff <= this.absTolerance || diff <= REL_TOLERANCE * Math.max(Math.abs(a), Math.abs(b));
    }

    private static boolean isEmptyCollection(Object o) {
        return (o instanceof Collection<?> c && c.isEmpty()) || (o instanceof Map<?, ?> m && m.isEmpty());
    }

    private static String describe(Object o) {
        return o instanceof String s ? "'" + s + "'" : String.valueOf(o);
    }
}
