package io.github.mcheliceport.registry;

import io.github.mcheliceport.McheliCePort;
import net.minecraft.advancements.triggers.CriterionTrigger;
import net.minecraft.advancements.triggers.PlayerTrigger;
import net.minecraft.core.registries.Registries;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import org.jspecify.annotations.Nullable;

/**
 * Upstream {@code MCH_CriteriaTriggers}: six advancement triggers that take no conditions, as
 * upstream's {@code MCH_SimpleTrigger}, each a vanilla {@link PlayerTrigger} under the mod's ID.
 */
public final class ModCriteriaTriggers {

    public static final ModRegister<CriterionTrigger<?>> TRIGGERS = ModRegister.create(Registries.TRIGGER_TYPE);

    /** A player placed a vehicle from its item. */
    public static final RegEntry<CriterionTrigger<?>, PlayerTrigger> PUT_AIRCRAFT = register("put_aircraft");
    /** The pilot of a vehicle that was resupplied with ammunition. */
    public static final RegEntry<CriterionTrigger<?>, PlayerTrigger> SUPPLY_AMMO = register("supply_ammo");
    /** The pilot of a vehicle that was refuelled from its fuel slot. */
    public static final RegEntry<CriterionTrigger<?>, PlayerTrigger> SUPPLY_FUEL = register("supply_fuel");
    /** A pilot who unhitched a towed cargo container more than 20 blocks up. */
    public static final RegEntry<CriterionTrigger<?>, PlayerTrigger> RELIEF_SUPPLIES = register("relief_supplies");
    /** A player who boarded a UH-1C as its pilot. */
    public static final RegEntry<CriterionTrigger<?>, PlayerTrigger> RIDING_VALKYRIES = register("riding_valkyries");
    /** A player in a vehicle seat whose bullet hurt a villager. */
    public static final RegEntry<CriterionTrigger<?>, PlayerTrigger> VILLAGER_HURT_BULLET =
            register("villager_hurt_bullet");

    private ModCriteriaTriggers() {
    }

    /** Loads the class, which registers the triggers. */
    static void init() {
    }

    private static RegEntry<CriterionTrigger<?>, PlayerTrigger> register(String name) {
        return TRIGGERS.register(name, PlayerTrigger::new);
    }

    /** Fires {@code trigger} for {@code entity} when it is a player on the server. */
    public static void trigger(RegEntry<CriterionTrigger<?>, PlayerTrigger> trigger, @Nullable Entity entity) {
        if (entity instanceof ServerPlayer player) {
            trigger.get().trigger(player);
        }
    }
}
