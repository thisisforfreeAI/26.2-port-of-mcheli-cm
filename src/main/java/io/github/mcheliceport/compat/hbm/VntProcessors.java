package io.github.mcheliceport.compat.hbm;

import com.hbm.explosion.vanillant.ExplosionVNT;
import com.hbm.explosion.vanillant.interfaces.IBlockAllocator;
import com.hbm.explosion.vanillant.interfaces.IBlockProcessor;
import com.hbm.explosion.vanillant.interfaces.IEntityProcessor;
import com.hbm.explosion.vanillant.interfaces.IExplosionSFX;
import com.hbm.explosion.vanillant.interfaces.IPlayerProcessor;
import java.lang.reflect.Constructor;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.function.Supplier;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import org.jspecify.annotations.Nullable;

/**
 * Upstream's {@code VNTSettingContainer.processMap}: a weapon's VNT {@code Attributes} name NTM's
 * explosion parts by class and configure them, for example
 *
 * <pre>
 * BlockProcessor:
 *   Type: BlockProcessorStandard
 *   NoDrop: true
 *   WithBlockEffect: {Type: BlockMutatorFire}
 * </pre>
 *
 * {@code Type} is a class in NTM's {@code com.hbm.explosion.vanillant.standard} package, and only
 * there; {@code Constructor} lists its arguments. Every other key calls the part's method of that
 * name (lower camel case, or with {@code set}): {@code true} calls a method without arguments, a
 * map builds a nested part, and any other value is the argument. Numbers convert to the parameter
 * type, and a block id gives a block or its default state.
 */
final class VntProcessors {

    static final String PACKAGE = "com.hbm.explosion.vanillant.standard.";

    private VntProcessors() {
    }

    @SuppressWarnings("unchecked")
    static void apply(ExplosionVNT explosion, Map<String, Object> attributes) {
        if (build(attributes.get("BlockAllocator")) instanceof IBlockAllocator allocator) {
            explosion.setBlockAllocator(allocator);
        }
        if (build(attributes.get("BlockProcessor")) instanceof IBlockProcessor processor) {
            explosion.setBlockProcessor(processor);
        }
        if (build(attributes.get("EntityProcessor")) instanceof IEntityProcessor processor) {
            explosion.setEntityProcessor(processor);
        }
        if (build(attributes.get("PlayerProcessor")) instanceof IPlayerProcessor processor) {
            explosion.setPlayerProcessor(processor);
        }
        if (attributes.get("SFX") instanceof List<?> list) {
            List<IExplosionSFX> sfx = new ArrayList<>();
            for (Object entry : list) {
                if (build(entry) instanceof IExplosionSFX effect) {
                    sfx.add(effect);
                }
            }
            if (!sfx.isEmpty()) {
                explosion.setSFX(sfx.toArray(IExplosionSFX[]::new));
            }
        }
    }

    /** A part built from its map, or null (with a warning) when the map does not describe one. */
    @SuppressWarnings("unchecked")
    static @Nullable Object build(@Nullable Object raw) {
        if (!(raw instanceof Map<?, ?> rawMap) || !(rawMap.get("Type") instanceof String type)) {
            return null;
        }
        Map<String, Object> map = (Map<String, Object>) rawMap;
        Class<?> cls;
        try {
            cls = Class.forName(PACKAGE + type.trim());
        } catch (ClassNotFoundException e) {
            NtmPayloads.warnOnce("vnt-type:" + type, "VNT part {} is not in NTM's {} package", type, PACKAGE);
            return null;
        }
        Object part = construct(cls, map.get("Constructor") instanceof List<?> args ? args : List.of());
        if (part == null) {
            NtmPayloads.warnOnce("vnt-ctor:" + type, "VNT part {} has no constructor for {}", type,
                    map.get("Constructor"));
            return null;
        }
        for (Map.Entry<String, Object> entry : map.entrySet()) {
            String key = entry.getKey();
            if (!key.equals("Type") && !key.equals("Constructor")) {
                Object value = entry.getValue() instanceof Map<?, ?> ? build(entry.getValue()) : entry.getValue();
                if (value != null && !configure(part, key, value)) {
                    NtmPayloads.warnOnce("vnt-key:" + type + "." + key, "VNT part {} has no setting {}", type, key);
                }
            }
        }
        return part;
    }

    private static @Nullable Object construct(Class<?> cls, List<?> args) {
        for (Constructor<?> constructor : cls.getConstructors()) {
            Object[] converted = convertAll(constructor.getParameterTypes(), args);
            if (converted != null) {
                try {
                    return constructor.newInstance(converted);
                } catch (ReflectiveOperationException e) {
                    return null;
                }
            }
        }
        return null;
    }

    /** Calls {@code key} on {@code part}; false when it has no such method for the value. */
    private static boolean configure(Object part, String key, Object value) {
        String lower = Character.toLowerCase(key.charAt(0)) + key.substring(1);
        String set = "set" + Character.toUpperCase(key.charAt(0)) + key.substring(1);
        for (String name : new String[] {lower, set}) {
            for (Method method : part.getClass().getMethods()) {
                if (!method.getName().equals(name) || Modifier.isStatic(method.getModifiers())) {
                    continue;
                }
                Object[] args;
                if (value instanceof Boolean flag && method.getParameterCount() == 0) {
                    if (!flag) {
                        return true;
                    }
                    args = new Object[0];
                } else {
                    args = convertAll(method.getParameterTypes(), List.of(value));
                }
                if (args != null) {
                    try {
                        method.invoke(part, args);
                        return true;
                    } catch (ReflectiveOperationException e) {
                        return false;
                    }
                }
            }
        }
        return false;
    }

    private static Object @Nullable [] convertAll(Class<?>[] types, List<?> values) {
        if (types.length != values.size()) {
            return null;
        }
        Object[] out = new Object[types.length];
        for (int i = 0; i < types.length; i++) {
            out[i] = convert(types[i], values.get(i));
            if (out[i] == null) {
                return null;
            }
        }
        return out;
    }

    /** {@code value} as a {@code type} argument, or null when it cannot be one. */
    static @Nullable Object convert(Class<?> type, @Nullable Object value) {
        if (value == null) {
            return null;
        }
        if (value instanceof Number number) {
            boolean whole = number.doubleValue() == Math.rint(number.doubleValue());
            if (type == int.class || type == Integer.class) {
                return whole ? number.intValue() : null;
            }
            if (type == long.class || type == Long.class) {
                return whole ? number.longValue() : null;
            }
            if (type == float.class || type == Float.class) {
                return number.floatValue();
            }
            if (type == double.class || type == Double.class) {
                return number.doubleValue();
            }
            return null;
        }
        if (type == boolean.class || type == Boolean.class) {
            return value instanceof Boolean ? value : null;
        }
        if (value instanceof String text && type != String.class) {
            Block block = block(text);
            if (block == null) {
                return null;
            }
            if (type == Block.class) {
                return block;
            }
            if (type == BlockState.class) {
                return block.defaultBlockState();
            }
            if (type == Supplier.class) {
                return (Supplier<BlockState>) block::defaultBlockState;
            }
            return null;
        }
        return type.isInstance(value) ? value : null;
    }

    private static @Nullable Block block(String id) {
        Identifier location = Identifier.tryParse(id.trim());
        return location == null ? null : BuiltInRegistries.BLOCK.getOptional(location).orElse(null);
    }
}
