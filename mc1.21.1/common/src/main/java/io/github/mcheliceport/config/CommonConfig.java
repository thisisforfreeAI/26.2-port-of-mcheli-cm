package io.github.mcheliceport.config;

import java.util.List;
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
    public static final ModConfigSpec.DoubleValue ALL_PLANE_SPEED;
    public static final ModConfigSpec.DoubleValue ALL_SHIP_SPEED;
    public static final ModConfigSpec.BooleanValue AUTO_THROTTLE_DOWN_TANK;
    public static final ModConfigSpec.BooleanValue FIX_VEHICLE_AT_PLACED_POINT;
    public static final ModConfigSpec.BooleanValue ENABLE_PUT_RACK_IN_FLYING;
    public static final ModConfigSpec.BooleanValue AUTO_THROTTLE_DOWN_PLANE;
    public static final ModConfigSpec.BooleanValue AUTO_THROTTLE_DOWN_HELI;
    public static final ModConfigSpec.BooleanValue DISMOUNT_ALL;
    public static final ModConfigSpec.BooleanValue EXPERIMENTAL_QUATERNION_ROTATION;
    public static final ModConfigSpec.IntValue DESPAWN_COUNT;
    public static final ModConfigSpec.BooleanValue KILL_PASSENGERS_WHEN_DESTROYED;
    public static final ModConfigSpec.DoubleValue AUTO_REPAIR_HP;
    public static final ModConfigSpec.BooleanValue COLLISION_DESTROY_BLOCK;
    public static final ModConfigSpec.BooleanValue COLLISION_ENTITY_DAMAGE;
    public static final ModConfigSpec.BooleanValue COLLISION_ENTITY_TANK_DAMAGE;
    public static final ModConfigSpec.ConfigValue<List<? extends String>> CAR_BREAKABLE_BLOCKS;
    public static final ModConfigSpec.ConfigValue<List<? extends String>> CAR_PROTECTED_BLOCKS;
    public static final ModConfigSpec.ConfigValue<List<? extends String>> TANK_BREAKABLE_BLOCKS;
    public static final ModConfigSpec.ConfigValue<List<? extends String>> TANK_PROTECTED_BLOCKS;
    public static final ModConfigSpec.BooleanValue INFINITY_AMMO;
    public static final ModConfigSpec.IntValue TV_MISSILE_MAX_RANGE;
    public static final ModConfigSpec.DoubleValue HURT_RESISTANT_TIME;
    public static final ModConfigSpec.IntValue HIT_BOX_DELAY_TICK;
    public static final ModConfigSpec.IntValue GUNNER_VS_MONSTER_HORIZONTAL;
    public static final ModConfigSpec.IntValue GUNNER_VS_MONSTER_VERTICAL;
    public static final ModConfigSpec.IntValue GUNNER_VS_PLAYER_HORIZONTAL;
    public static final ModConfigSpec.IntValue GUNNER_VS_PLAYER_VERTICAL;
    public static final ModConfigSpec.ConfigValue<List<? extends String>> BULLET_BREAKABLE_BLOCKS;
    public static final ModConfigSpec.ConfigValue<List<? extends String>> DAMAGE_VS_ENTITY;
    public static final ModConfigSpec.ConfigValue<List<? extends String>> DAMAGE_VS_LIVING;
    public static final ModConfigSpec.ConfigValue<List<? extends String>> DAMAGE_VS_PLAYER;
    public static final ModConfigSpec.ConfigValue<List<? extends String>> DAMAGE_VS_AIRCRAFT;
    public static final ModConfigSpec.ConfigValue<List<? extends String>> DAMAGE_VS_TANK;
    public static final ModConfigSpec.ConfigValue<List<? extends String>> DAMAGE_VS_VEHICLE;
    public static final ModConfigSpec.ConfigValue<List<? extends String>> DAMAGE_VS_OTHER;
    public static final ModConfigSpec.ConfigValue<List<? extends String>> DAMAGE_AIRCRAFT_BY_EXTERNAL;
    public static final ModConfigSpec.ConfigValue<List<? extends String>> DAMAGE_TANK_BY_EXTERNAL;
    public static final ModConfigSpec.ConfigValue<List<? extends String>> DAMAGE_VEHICLE_BY_EXTERNAL;
    public static final ModConfigSpec.ConfigValue<List<? extends String>> DAMAGE_OTHER_BY_EXTERNAL;
    public static final ModConfigSpec.BooleanValue EXPLOSION_DESTROY_BLOCK;
    public static final ModConfigSpec.BooleanValue EXPLOSION_FLAMING_BLOCK;
    public static final ModConfigSpec.BooleanValue WRECK_FIRE_BLOCKS;
    public static final ModConfigSpec.BooleanValue WRECK_CRATERS;
    public static final ModConfigSpec.BooleanValue PERSISTENT_WRECKS;
    public static final ModConfigSpec.IntValue WRECK_LIFETIME;
    public static final ModConfigSpec SPEC;

    static {
        ModConfigSpec.Builder builder = new ModConfigSpec.Builder();

        builder.push("general");
        DEBUG_LOG = builder
                .comment("Write extra diagnostic messages to the log.",
                        "Replaces upstream's hidden 'McHeliOutputDebugLog' line in mcheli.cfg.",
                        "On a client, also lets server operators see your mod list with /mcheli modlist, as upstream.")
                .define("debugLog", false);
        builder.pop();

        builder.comment("Vehicle rules (upstream's lines of the same names in mcheli.cfg).").push("vehicles");
        ENABLE_PUT_RACK_IN_FLYING = builder.comment("Allow carriers to load racks while flying. Upstream: EnablePutRackInFlying.")
                .define("enablePutRackInFlying", true);
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
        ALL_PLANE_SPEED = builder
                .comment("Multiplies the speed of every plane. Upstream: AllPlaneSpeed.")
                .defineInRange("allPlaneSpeed", 1.0, 0.0, 1000.0);
        ALL_SHIP_SPEED = builder
                .comment("Multiplies the speed of every ship. Upstream: AllShipSpeed.")
                .defineInRange("allShipSpeed", 1.0, 0.0, 1000.0);
        AUTO_THROTTLE_DOWN_TANK = builder.comment("Reduce tank throttle when neither throttle key is held. Upstream: AutoThrottleDownTank.")
                .define("autoThrottleDownTank", false);
        FIX_VEHICLE_AT_PLACED_POINT = builder.comment("Anchor placed vehicles after 200 ticks. Upstream: FixVehicleAtPlacedPoint.")
                .define("fixVehicleAtPlacedPoint", true);
        AUTO_THROTTLE_DOWN_PLANE = builder
                .comment("Reduce a plane's throttle when neither throttle key is held. Upstream: AutoThrottleDownPlane.")
                .define("autoThrottleDownPlane", false);
        AUTO_THROTTLE_DOWN_HELI = builder
                .comment("A helicopter's throttle settles at hover (0.5) when no throttle key is held.",
                        "Upstream: AutoThrottleDownHeli (a client setting there; here each side reads its own copy).")
                .define("autoThrottleDownHeli", true);
        DISMOUNT_ALL = builder
                .comment("When the pilot gets out, every rider but players gets out too.",
                        "Upstream: DismountAll (a client setting there; here the server's copy decides).")
                .define("dismountAll", false);
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
                .comment("Vehicles break soft blocks they touch; cars/tanks also crush their configured blocks.",
                        "Upstream: Collision_DestroyBlock. Server-side; independent of movement authority.")
                .define("collisionDestroyBlock", true);
        COLLISION_ENTITY_DAMAGE = builder.comment("Tanks/cars can ram other entities. Upstream: Collision_EntityDamage.")
                .define("collisionEntityDamage", true);
        COLLISION_ENTITY_TANK_DAMAGE = builder.comment("Ramming can hurt heavy tanks (WeightType TANK).",
                        "Separate from collisionEntityDamage. Upstream: Collision_EntityTankDamage.")
                .define("collisionEntityTankDamage", false);
        builder.comment("Crushing selectors: block IDs or #block tags. These replace upstream's block plus Material lists.",
                "Protected blocks stop crushing upward through their column. The collisionDestroyBlock switch gates all crushing.")
                .push("crushing");
        CAR_BREAKABLE_BLOCKS = builder.defineListAllowEmpty("carBreakableBlocks",
                io.github.mcheliceport.entity.tank.GroundCollisionBlocks.CAR, () -> "", CommonConfig::isString);
        CAR_PROTECTED_BLOCKS = builder.defineListAllowEmpty("carProtectedBlocks",
                List.of("minecraft:torch", "minecraft:wall_torch"), () -> "", CommonConfig::isString);
        TANK_BREAKABLE_BLOCKS = builder.defineListAllowEmpty("tankBreakableBlocks",
                io.github.mcheliceport.entity.tank.GroundCollisionBlocks.TANK, () -> "", CommonConfig::isString);
        TANK_PROTECTED_BLOCKS = builder.defineListAllowEmpty("tankProtectedBlocks",
                List.of("minecraft:torch", "minecraft:wall_torch", "minecraft:glowstone"), () -> "", CommonConfig::isString);
        builder.pop();
        builder.pop();

        builder.comment("Weapons (upstream's lines of the same names in mcheli.cfg).").push("weapons");
        INFINITY_AMMO = builder
                .comment("Vehicle weapons never run out of ammunition. Upstream: InfinityAmmo.")
                .define("infinityAmmo", false);
        TV_MISSILE_MAX_RANGE = builder
                .comment("A TV missile farther than this from its shooter self-destructs; -1 for no limit.",
                        "Upstream: tvMissileMaxRange (wingman settings).")
                .defineInRange("tvMissileMaxRange", 500, -1, 100000);
        HURT_RESISTANT_TIME = builder
                .comment("Multiplies the invulnerability time a mob gets after each bullet or explosion hit;",
                        "0 lets every bullet of a burst hurt. Upstream: HurtResistantTime.")
                .defineInRange("hurtResistantTime", 0.0, 0.0, 10000.0);
        HIT_BOX_DELAY_TICK = builder
                .comment("Ticks gunners lead less when their target rides a vehicle. Upstream: HitBoxDelayTick.")
                .defineInRange("hitBoxDelayTick", 0, -1000, 1000);
        GUNNER_VS_MONSTER_HORIZONTAL = builder
                .comment("How far sideways a gunner (vs monster) looks for monsters.",
                        "Upstream: RangeOfGunner_VsMonster_Horizontal.")
                .defineInRange("rangeOfGunnerVsMonsterHorizontal", 80, 0, 10000);
        GUNNER_VS_MONSTER_VERTICAL = builder
                .comment("How far up and down a gunner (vs monster) looks for monsters.",
                        "Upstream: RangeOfGunner_VsMonster_Vertical.")
                .defineInRange("rangeOfGunnerVsMonsterVertical", 160, 0, 10000);
        GUNNER_VS_PLAYER_HORIZONTAL = builder
                .comment("How far sideways a gunner (vs player) looks for players of other teams.",
                        "Upstream: RangeOfGunner_VsPlayer_Horizontal.")
                .defineInRange("rangeOfGunnerVsPlayerHorizontal", 200, 0, 10000);
        GUNNER_VS_PLAYER_VERTICAL = builder
                .comment("How far up and down a gunner (vs player) looks for players of other teams.",
                        "Upstream: RangeOfGunner_VsPlayer_Vertical.")
                .defineInRange("rangeOfGunnerVsPlayerVertical", 300, 0, 10000);
        BULLET_BREAKABLE_BLOCKS = builder
                .comment("Blocks bullets break and fly through: block IDs, or block tags with a leading '#'.",
                        "Upstream: BulletBreakableBlocks (1.12 block names).")
                .defineListAllowEmpty("bulletBreakableBlocks", List.of(
                        "#c:glass_panes", "minecraft:short_grass", "minecraft:fern", "minecraft:tall_grass",
                        "minecraft:large_fern", "minecraft:sunflower", "minecraft:lilac", "minecraft:rose_bush",
                        "minecraft:peony", "#minecraft:small_flowers", "minecraft:vine", "minecraft:wheat",
                        "minecraft:sugar_cane", "minecraft:lily_pad"), () -> "", CommonConfig::isString);
        builder.comment("Damage multipliers: each entry is 'factor' (applies to all) or 'factor, name' (applies when the",
                "entity type ID contains name). Upstream: the lines of the same names in mcheli.cfg, which matched",
                "Java class names instead.");
        DAMAGE_VS_ENTITY = damageList(builder, "damageVsEntity", "Damage McHeli weapons do to other entities.",
                "1.0", "3.0, flansmod");
        DAMAGE_VS_LIVING = damageList(builder, "damageVsLiving", "Damage McHeli weapons do to mobs.", "1.0");
        DAMAGE_VS_PLAYER = damageList(builder, "damageVsPlayer", "Damage McHeli weapons do to players.", "1.0");
        DAMAGE_VS_AIRCRAFT = damageList(builder, "damageVsMCHeliAircraft",
                "Damage McHeli weapons do to helicopters and planes.", "1.0");
        DAMAGE_VS_TANK = damageList(builder, "damageVsMCHeliTank", "Damage McHeli weapons do to tanks.", "1.0");
        DAMAGE_VS_VEHICLE = damageList(builder, "damageVsMCHeliVehicle", "Damage McHeli weapons do to vehicles.",
                "1.0");
        DAMAGE_VS_OTHER = damageList(builder, "damageVsMCHeliOther",
                "Damage McHeli weapons do to McHeli's other entities.", "1.0");
        DAMAGE_AIRCRAFT_BY_EXTERNAL = damageList(builder, "damageMCHeliAircraftByExternal",
                "Damage helicopters and planes take from anything, matched against the attacker.", "1.0",
                "0.5, flansmod");
        DAMAGE_TANK_BY_EXTERNAL = damageList(builder, "damageMCHeliTankByExternal",
                "Damage tanks take from anything, matched against the attacker.", "1.0");
        DAMAGE_VEHICLE_BY_EXTERNAL = damageList(builder, "damageMCHeliVehicleByExternal",
                "Damage vehicles take from anything, matched against the attacker.", "1.0", "0.5, flansmod");
        DAMAGE_OTHER_BY_EXTERNAL = damageList(builder, "damageMCHeliOtherByExternal",
                "Damage McHeli's other entities take from anything, matched against the attacker.", "1.0");
        builder.pop();

        builder.comment("McHeli's explosions (upstream's lines of the same names in mcheli.cfg).").push("explosions");
        EXPLOSION_DESTROY_BLOCK = builder
                .comment("Explosions of weapons and crashes break blocks (the mobGriefing game rule must allow it too).",
                        "Upstream: Explosion_DestroyBlock.")
                .define("destroyBlock", true);
        EXPLOSION_FLAMING_BLOCK = builder
                .comment("Incendiary explosions set blocks on fire. Upstream: Explosion_FlamingBlock.")
                .define("flamingBlock", true);
        builder.pop();

        builder.comment("Destroyed vehicles (not in upstream).").push("wrecks");
        WRECK_FIRE_BLOCKS = builder
                .comment("Crashing and destroyed vehicles set fire blocks around the wreck, as upstream does.",
                        "Off: the wreck burns with a cosmetic fuel fire instead, and blocks are not set alight.")
                .define("fireBlocks", false);
        WRECK_CRATERS = builder
                .comment("A crashing or destroyed vehicle's blast digs a crater under it, as upstream does, and the",
                        "hulk drops into the hole. Off: the blast breaks only what stands above the ground the wreck",
                        "lies on, and the hulk rests on the surface.")
                .define("wreckCraters", false);
        PERSISTENT_WRECKS = builder
                .comment("A destroyed vehicle stays as a burnt-out hulk for wreckLifetime seconds instead of",
                        "despawnCount. Remove hulks early with /mcheli clearwrecks.")
                .define("persistentWrecks", true);
        WRECK_LIFETIME = builder
                .comment("Seconds a hulk stays when persistentWrecks is on. 0 keeps it until /mcheli clearwrecks.")
                .defineInRange("wreckLifetime", 600, 0, 1000000);
        builder.pop();

        SPEC = builder.build();
    }

    private CommonConfig() {}

    /** A config value, or its default while the config is not loaded (unit tests). */
    public static <T> T get(ModConfigSpec.ConfigValue<T> value) {
        return SPEC.isLoaded() ? value.get() : value.getDefault();
    }

    private static boolean isString(Object o) {
        return o instanceof String;
    }

    private static ModConfigSpec.ConfigValue<List<? extends String>> damageList(ModConfigSpec.Builder builder,
            String key, String comment, String... defaults) {
        return builder.comment(comment).defineListAllowEmpty(key, List.of(defaults), () -> "1.0",
                o -> o instanceof String s && io.github.mcheliceport.weapon.DamageScaling.Entry.isValid(s));
    }
}
