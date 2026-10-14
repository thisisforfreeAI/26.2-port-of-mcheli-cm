package io.github.mcheliceport.config;

import net.neoforged.neoforge.common.ModConfigSpec;

/**
 * COMMON config ({@code config/mcheli_ce_port-common.toml}): settings both sides read at startup.
 *
 * <p>Upstream's gameplay and balance keys from {@code mcheli.cfg} (vehicle speeds, damage factors,
 * explosions, collisions, gunner ranges) move here as the features that read them are ported
 * (PORTING_PLAN.md section 5.6).
 */
public final class CommonConfig {
    public static final ModConfigSpec.BooleanValue DEBUG_LOG;
    public static final ModConfigSpec.BooleanValue PLACEABLE_ON_SPONGE_ONLY;
    public static final ModConfigSpec.BooleanValue ITEM_DAMAGE;
    public static final ModConfigSpec.BooleanValue ITEM_FUEL;
    public static final ModConfigSpec.BooleanValue INFINITY_FUEL;
    public static final ModConfigSpec.BooleanValue PREVENTING_BROKEN;
    public static final ModConfigSpec.BooleanValue DROP_ITEM_IN_CREATIVE_MODE;
    public static final ModConfigSpec.BooleanValue BREAKABLE_ONLY_PICKAXE;
    public static final ModConfigSpec.DoubleValue ALL_HELI_SPEED;
    public static final ModConfigSpec.BooleanValue AUTO_THROTTLE_DOWN_HELI;
    public static final ModConfigSpec.BooleanValue EXPERIMENTAL_QUATERNION_ROTATION;
    public static final ModConfigSpec.IntValue DESPAWN_COUNT;
    public static final ModConfigSpec.BooleanValue KILL_PASSENGERS_WHEN_DESTROYED;
    public static final ModConfigSpec.DoubleValue AUTO_REPAIR_HP;
    public static final ModConfigSpec.BooleanValue COLLISION_DESTROY_BLOCK;
    public static final ModConfigSpec SPEC;

    static {
        ModConfigSpec.Builder builder = new ModConfigSpec.Builder();

        builder.push("general");
        DEBUG_LOG = builder
                .comment("Write extra diagnostic messages to the log.",
                        "Replaces upstream's hidden 'McHeliOutputDebugLog' line in mcheli.cfg.")
                .define("debugLog", false);
        builder.pop();

        builder.comment("Vehicle rules (upstream's lines of the same names in mcheli.cfg).").push("vehicles");
        PLACEABLE_ON_SPONGE_ONLY = builder
                .comment("Vehicles can only be placed on a sponge (floating vehicles: on water above a sponge).",
                        "Upstream: PlaceableOnSpongeOnly.")
                .define("placeableOnSpongeOnly", false);
        ITEM_DAMAGE = builder
                .comment("A vehicle's damage moves to its item when it is picked up, and back when it is placed.",
                        "Upstream: ItemDamage.")
                .define("itemDamage", true);
        ITEM_FUEL = builder
                .comment("A vehicle's fuel moves to its item when it is picked up, and back when it is placed.",
                        "Upstream: ItemFuel.")
                .define("itemFuel", true);
        INFINITY_FUEL = builder
                .comment("Vehicles never run out of fuel. Upstream: InfinityFuel.")
                .define("infinityFuel", false);
        PREVENTING_BROKEN = builder
                .comment("Players in survival cannot pick vehicles up by hitting them. Upstream: PreventingBroken.")
                .define("preventingBroken", false);
        DROP_ITEM_IN_CREATIVE_MODE = builder
                .comment("A creative player's hit drops the vehicle's item (sneak to invert).",
                        "Upstream: DropItemInCreativeMode.")
                .define("dropItemInCreativeMode", false);
        BREAKABLE_ONLY_PICKAXE = builder
                .comment("Players in survival can only pick vehicles up with a pickaxe. Upstream: BreakableOnlyPickaxe.")
                .define("breakableOnlyPickaxe", false);
        ALL_HELI_SPEED = builder
                .comment("Multiplies the speed of every helicopter. Upstream: AllHeliSpeed.")
                .defineInRange("allHeliSpeed", 1.0, 0.0, 1000.0);
        AUTO_THROTTLE_DOWN_HELI = builder
                .comment("A helicopter's throttle settles at hover (0.5) when no throttle key is held.",
                        "Upstream: AutoThrottleDownHeli (a client setting there; here each side reads its own copy).")
                .define("autoThrottleDownHeli", true);
        EXPERIMENTAL_QUATERNION_ROTATION = builder
                .comment("Turn vehicles with a persistent quaternion, so they can fly through vertical.",
                        "Upstream: ExperimentalQuaternionRotation.")
                .define("experimentalQuaternionRotation", true);
        DESPAWN_COUNT = builder
                .comment("Seconds a destroyed vehicle stays before it disappears. Upstream: DespawnCount.")
                .defineInRange("despawnCount", 25, 1, 100000);
        KILL_PASSENGERS_WHEN_DESTROYED = builder
                .comment("Kill everyone aboard when a vehicle is destroyed. Upstream: KillPassengersWhenDestroyed.")
                .define("killPassengersWhenDestroyed", false);
        AUTO_REPAIR_HP = builder
                .comment("Vehicles above this fraction of their hit points slowly repair themselves. Upstream: AutoRepairHP.")
                .defineInRange("autoRepairHP", 0.5, 0.0, 1.0);
        COLLISION_DESTROY_BLOCK = builder
                .comment("Vehicles break snow layers, lily pads and cakes they touch. Upstream: Collision_DestroyBlock.")
                .define("collisionDestroyBlock", true);
        builder.pop();

        SPEC = builder.build();
    }

    private CommonConfig() {}
}
