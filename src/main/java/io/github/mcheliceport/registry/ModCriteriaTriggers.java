package io.github.mcheliceport.registry;

import io.github.mcheliceport.McheliCePort;
import net.minecraft.advancements.triggers.CriterionTrigger;
import net.minecraft.advancements.triggers.PlayerTrigger;
import net.minecraft.core.registries.Registries;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;
import org.jspecify.annotations.Nullable;

/**
 * Upstream {@code MCH_CriteriaTriggers}: six advancement triggers that take no conditions, as
 * upstream's {@code MCH_SimpleTrigger}, each a vanilla {@link PlayerTrigger} under the mod's ID.
 */
public final class ModCriteriaTriggers {

    public static final DeferredRegister<CriterionTrigger<?>> TRIGGERS =
            DeferredRegister.create(Registries.TRIGGER_TYPE, McheliCePort.MOD_ID);

    /** A player placed a vehicle from its item. */
    public static final DeferredHolder<CriterionTrigger<?>, PlayerTrigger> PUT_AIRCRAFT = register("put_aircraft");
    /** The pilot of a vehicle that was resupplied with ammunition. */
    public static final DeferredHolder<CriterionTrigger<?>, PlayerTrigger> SUPPLY_AMMO = register("supply_ammo");
    /** The pilot of a vehicle that was refuelled from its fuel slot. */
    public static final DeferredHolder<CriterionTrigger<?>, PlayerTrigger> SUPPLY_FUEL = register("supply_fuel");
    /** A pilot who unhitched a towed cargo container more than 20 blocks up. */
    public static final DeferredHolder<CriterionTrigger<?>, PlayerTrigger> RELIEF_SUPPLIES = register("relief_supplies");
    /** A player who boarded a UH-1C as its pilot. */
    public static final DeferredHolder<CriterionTrigger<?>, PlayerTrigger> RIDING_VALKYRIES = register("riding_valkyries");
    /** A player in a vehicle seat whose bullet hurt a villager. */
    public static final DeferredHolder<CriterionTrigger<?>, PlayerTrigger> VILLAGER_HURT_BULLET =
            register("villager_hurt_bullet");

    private ModCriteriaTriggers() {
    }

    private static DeferredHolder<CriterionTrigger<?>, PlayerTrigger> register(String name) {
        return TRIGGERS.register(name, PlayerTrigger::new);
    }

    /** Fires {@code trigger} for {@code entity} when it is a player on the server. */
    public static void trigger(DeferredHolder<CriterionTrigger<?>, PlayerTrigger> trigger, @Nullable Entity entity) {
        if (entity instanceof ServerPlayer player) {
            trigger.get().trigger(player);
        }
    }
}
