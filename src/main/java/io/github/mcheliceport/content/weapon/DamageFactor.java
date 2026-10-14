package io.github.mcheliceport.content.weapon;

import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import org.jspecify.annotations.Nullable;

/**
 * Per-target damage multipliers of a weapon.
 *
 * <p>Ported from upstream {@code com.norwood.mcheli.MCH_DamageFactor}, which keyed the factors by
 * Java entity class. The port keys them by target name instead: the built-in names below, or (an
 * extension for YAML files, which upstream let name any entity class) an entity type ID such as
 * {@code minecraft:zombie}. The factors are applied when weapons are ported (M5).
 */
public final class DamageFactor {

    public static final String PLAYER = "player";
    public static final String HELICOPTER = "helicopter";
    public static final String PLANE = "plane";
    public static final String SHIP = "ship";
    public static final String TANK = "tank";
    public static final String VEHICLE = "vehicle";

    private final Map<String, Float> factors = new LinkedHashMap<>();

    /**
     * Maps a target name from a content file to the stored key: a built-in name (case-insensitive,
     * {@code heli} is short for {@code helicopter}), else the name itself.
     */
    public static String normalizeTarget(String name) {
        String lower = name.trim().toLowerCase(Locale.ROOT);
        return switch (lower) {
            case "player" -> PLAYER;
            case "heli", "helicopter" -> HELICOPTER;
            case "plane" -> PLANE;
            case "ship" -> SHIP;
            case "tank" -> TANK;
            case "vehicle" -> VEHICLE;
            default -> name.trim();
        };
    }

    /** Whether {@code key} (already normalised) is one of the built-in target names. */
    public static boolean isBuiltinTarget(String key) {
        return switch (key) {
            case PLAYER, HELICOPTER, PLANE, SHIP, TANK, VEHICLE -> true;
            default -> false;
        };
    }

    public void clear() {
        this.factors.clear();
    }

    public void add(String target, float value) {
        this.factors.put(normalizeTarget(target), value);
    }

    public float getDamageFactor(@Nullable String target) {
        return target == null ? 1.0F : this.factors.getOrDefault(target, 1.0F);
    }

    /** The stored factors, in file order. */
    public Map<String, Float> getFactors() {
        return this.factors;
    }

    @Override
    public String toString() {
        return "DamageFactor" + this.factors;
    }
}
