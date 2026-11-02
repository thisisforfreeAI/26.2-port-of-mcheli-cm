package io.github.mcheliceport.weapon;

import io.github.mcheliceport.config.CommonConfig;
import io.github.mcheliceport.entity.aircraft.AircraftEntity;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.WeakHashMap;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import org.jspecify.annotations.Nullable;

/**
 * The config's damage multipliers ({@code damageVs…} and {@code damage…ByExternal}).
 *
 * <p>Ported from upstream {@code MCH_Config#applyDamageVsEntity} and {@code #applyDamageByExternal}.
 * Upstream matched each entry's name as a substring of the Java class name; the port matches it
 * against the entity type ID (decision D19), so names such as {@code flansmod} keep working.
 * An entry without a name applies to everything, and every matching entry multiplies.
 */
public final class DamageScaling {

    /** Which list applies to a target: upstream's class tests, in its order. */
    public enum TargetKind {
        AIRCRAFT,
        TANK,
        VEHICLE,
        MCHELI_OTHER,
        PLAYER,
        LIVING,
        ENTITY
    }

    private static final Map<List<? extends String>, List<Entry>> PARSED =
            Collections.synchronizedMap(new WeakHashMap<>());

    private DamageScaling() {
    }

    /** One config entry: {@code factor} or {@code factor, name}. */
    public record Entry(double factor, String name) {

        /**
         * Upstream {@code newDamageEntity}: one value is a factor for everything, two are a factor
         * and a name, anything else is a neutral entry.
         */
        public static Entry parse(String s) {
            String[] split = s.trim().split("\\s*,\\s*");
            if (split.length == 1) {
                return new Entry(Double.parseDouble(split[0]), "");
            }
            return split.length == 2 ? new Entry(Double.parseDouble(split[0]), split[1]) : new Entry(1.0, "");
        }

        public static boolean isValid(String s) {
            try {
                parse(s);
                return true;
            } catch (NumberFormatException e) {
                return false;
            }
        }

        boolean matches(@Nullable String id) {
            return this.name.isEmpty() || id != null && id.contains(this.name);
        }
    }

    /** Multiplies {@code damage} by every entry matching {@code id}. */
    public static float apply(List<Entry> entries, @Nullable String id, float damage) {
        for (Entry entry : entries) {
            if (entry.matches(id)) {
                damage = (float) (damage * entry.factor());
            }
        }
        return damage;
    }

    /** Multiplies by every entry matching either ID (attacker or direct source), as {@code applyDamageByExternal}. */
    public static float applyEither(List<Entry> entries, @Nullable String id1, @Nullable String id2, float damage) {
        for (Entry entry : entries) {
            if (entry.name().isEmpty() || id1 != null && id1.contains(entry.name())
                    || id2 != null && id2.contains(entry.name())) {
                damage = (float) (damage * entry.factor());
            }
        }
        return damage;
    }

    public static List<Entry> parseAll(List<? extends String> lines) {
        // The config hands out the same list until it reloads, so parse each list once.
        List<Entry> cached = PARSED.get(lines);
        if (cached != null) {
            return cached;
        }
        List<Entry> list = new ArrayList<>();
        for (String line : lines) {
            if (Entry.isValid(line)) {
                list.add(Entry.parse(line));
            }
        }
        synchronized (PARSED) {
            PARSED.put(lines, list);
        }
        return list;
    }

    /** Upstream's choice of list: McHeli vehicles first, then players, mobs and other entities. */
    public static TargetKind kindOf(Entity target) {
        if (target instanceof AircraftEntity) {
            return TargetKind.AIRCRAFT;
        }
        if (isMcheliEntity(target)) {
            return TargetKind.MCHELI_OTHER;
        }
        if (target instanceof Player) {
            return TargetKind.PLAYER;
        }
        return target instanceof LivingEntity ? TargetKind.LIVING : TargetKind.ENTITY;
    }

    /** Upstream {@code applyDamageVsEntity}: the damage a McHeli weapon does to {@code target}. */
    public static float applyDamageVsEntity(@Nullable Entity target, float damage) {
        if (target == null) {
            return damage;
        }
        List<? extends String> lines = switch (kindOf(target)) {
            case AIRCRAFT -> CommonConfig.get(CommonConfig.DAMAGE_VS_AIRCRAFT);
            case TANK -> CommonConfig.get(CommonConfig.DAMAGE_VS_TANK);
            case VEHICLE -> CommonConfig.get(CommonConfig.DAMAGE_VS_VEHICLE);
            case MCHELI_OTHER -> CommonConfig.get(CommonConfig.DAMAGE_VS_OTHER);
            case PLAYER -> CommonConfig.get(CommonConfig.DAMAGE_VS_PLAYER);
            case LIVING -> CommonConfig.get(CommonConfig.DAMAGE_VS_LIVING);
            case ENTITY -> CommonConfig.get(CommonConfig.DAMAGE_VS_ENTITY);
        };
        return apply(parseAll(lines), typeId(target), damage);
    }

    /** Upstream {@code applyDamageByExternal}: the damage a McHeli entity takes from {@code source}. */
    public static float applyDamageByExternal(Entity target, DamageSource source, float damage) {
        List<? extends String> lines = switch (kindOf(target)) {
            case AIRCRAFT -> CommonConfig.get(CommonConfig.DAMAGE_AIRCRAFT_BY_EXTERNAL);
            case TANK -> CommonConfig.get(CommonConfig.DAMAGE_TANK_BY_EXTERNAL);
            case VEHICLE -> CommonConfig.get(CommonConfig.DAMAGE_VEHICLE_BY_EXTERNAL);
            default -> CommonConfig.get(CommonConfig.DAMAGE_OTHER_BY_EXTERNAL);
        };
        return applyEither(parseAll(lines), typeId(source.getEntity()), typeId(source.getDirectEntity()), damage);
    }

    /** The entity type ID, such as {@code minecraft:zombie}, or null. */
    public static @Nullable String typeId(@Nullable Entity entity) {
        return entity == null ? null : BuiltInRegistries.ENTITY_TYPE.getKey(entity.getType()).toString();
    }

    /** Whether the entity is one of this mod's (upstream: its class is in {@code com.norwood.mcheli}). */
    public static boolean isMcheliEntity(Entity entity) {
        return BuiltInRegistries.ENTITY_TYPE.getKey(entity.getType()).getNamespace()
                .equals(io.github.mcheliceport.McheliCePort.MOD_ID);
    }

    /**
     * Upstream {@code MCH_Lib#applyEntityHurtResistantTimeConfig}: scales the invulnerability a mob
     * still has from its last hit, so bursts of bullets all land.
     */
    public static void applyHurtResistantTime(Entity entity) {
        if (entity instanceof LivingEntity living) {
            double time = CommonConfig.get(CommonConfig.HURT_RESISTANT_TIME) * living.invulnerableTime;
            living.invulnerableTime = (int) time;
        }
    }
}
