package io.github.mcheliceport.registry;

import io.github.mcheliceport.entity.gunner.GunnerEntity;
import io.github.mcheliceport.entity.flare.ChaffEntity;
import io.github.mcheliceport.entity.throwable.ThrowableEntity;
import io.github.mcheliceport.entity.flare.FlareEntity;
import io.github.mcheliceport.entity.weapon.A10Entity;
import io.github.mcheliceport.entity.weapon.AAMissileEntity;
import io.github.mcheliceport.entity.weapon.ASMissileEntity;
import io.github.mcheliceport.entity.weapon.ATMissileEntity;
import io.github.mcheliceport.entity.weapon.BombEntity;
import io.github.mcheliceport.entity.weapon.BulletEntity;
import io.github.mcheliceport.entity.weapon.DispensedItemEntity;
import io.github.mcheliceport.entity.weapon.MarkerRocketEntity;
import io.github.mcheliceport.entity.weapon.RocketEntity;
import io.github.mcheliceport.entity.weapon.TorpedoEntity;
import io.github.mcheliceport.entity.weapon.TvMissileEntity;
import io.github.mcheliceport.entity.heli.HeliEntity;
import io.github.mcheliceport.entity.plane.PlaneEntity;
import io.github.mcheliceport.entity.ship.ShipEntity;
import io.github.mcheliceport.entity.tank.TankEntity;
import io.github.mcheliceport.entity.vehicle.VehicleEntity;
import io.github.mcheliceport.entity.parachute.ParachuteEntity;
import io.github.mcheliceport.entity.seat.SeatEntity;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobCategory;
import net.neoforged.neoforge.registries.DeferredHolder;
import io.github.mcheliceport.entity.container.CargoContainerEntity;
import io.github.mcheliceport.entity.chain.ChainEntity;

/**
 * Upstream's entity types ({@code MCH_MOD#registerEntity}), with its sizes, tracking ranges and
 * update intervals. Upstream gave tracking ranges in blocks; vanilla counts chunks, so they are
 * rounded up to whole chunks (the server's view distance still caps them, as it did upstream
 * without its tracking mixin).
 *
 * <p>Upstream's two hit box types ({@code MCH.E.HitBox} and
 * {@code MCH.E.PSeat}) are not registered: hit boxes become parts of the vehicle entity
 * (PORTING_PLAN.md section 5.2).
 */
public final class ModEntityTypes {

    private static final List<DeferredHolder<EntityType<?>, ? extends EntityType<?>>> ALL =
            new ArrayList<>();

    /** A crew seat; it rides its vehicle, so it is never summoned on its own. */
    public static final DeferredHolder<EntityType<?>, EntityType<SeatEntity>> SEAT = register("seat",
            ModRegistries.ENTITY_TYPES.registerEntityType("seat", SeatEntity::new, MobCategory.MISC, builder -> builder
                    .sized(1.0f, 1.0f)
                    .clientTrackingRange(toChunks(1200))
                    .updateInterval(2)
                    .fireImmune()
                    .noSummon()));
    /** Every helicopter; its content type (a saved and synced type name) picks the definition. */
    public static final DeferredHolder<EntityType<?>, EntityType<HeliEntity>> HELICOPTER = register("helicopter",
            ModRegistries.ENTITY_TYPES.registerEntityType("helicopter", HeliEntity::new, MobCategory.MISC,
                    builder -> builder
                            .sized(2.0f, 0.7f)
                            .clientTrackingRange(toChunks(1200))
                            .updateInterval(2)));
    /** Upstream's {@code MCH_EntityGLTD}, the ground laser target designator. */
    public static final DeferredHolder<EntityType<?>, EntityType<io.github.mcheliceport.entity.gltd.GltdEntity>> GLTD = register("gltd",
            ModRegistries.ENTITY_TYPES.registerEntityType("gltd", io.github.mcheliceport.entity.gltd.GltdEntity::new,
                    MobCategory.MISC, builder -> builder.sized(0.5F, 0.5F).clientTrackingRange(toChunks(1200)).updateInterval(10)));
    public static final DeferredHolder<EntityType<?>, EntityType<PlaneEntity>> PLANE = register("plane",
            ModRegistries.ENTITY_TYPES.registerEntityType("plane", PlaneEntity::new, MobCategory.MISC,
                    builder -> builder.sized(2.0F, 0.7F).clientTrackingRange(toChunks(1200)).updateInterval(2)));
    public static final DeferredHolder<EntityType<?>, EntityType<ShipEntity>> SHIP = register("ship",
            ModRegistries.ENTITY_TYPES.registerEntityType("ship", ShipEntity::new, MobCategory.MISC,
                    builder -> builder.sized(2.0F, 0.7F).clientTrackingRange(toChunks(1200)).updateInterval(2)));
    public static final DeferredHolder<EntityType<?>, EntityType<TankEntity>> TANK = register("tank",
            ModRegistries.ENTITY_TYPES.registerEntityType("tank", TankEntity::new, MobCategory.MISC,
                    builder -> builder.sized(2, 0.7F).clientTrackingRange(toChunks(1200)).updateInterval(2)));
    public static final DeferredHolder<EntityType<?>, EntityType<ChainEntity>> CHAIN = register("chain",
            ModRegistries.ENTITY_TYPES.registerEntityType("chain", ChainEntity::new, MobCategory.MISC,
                    builder -> builder.sized(1, 1).clientTrackingRange(toChunks(600)).updateInterval(10)));
    public static final DeferredHolder<EntityType<?>, EntityType<ParachuteEntity>> PARACHUTE = register("parachute",
            ModRegistries.ENTITY_TYPES.registerEntityType("parachute", ParachuteEntity::new, MobCategory.MISC,
                    builder -> builder.sized(1.5F, 0.6F).clientTrackingRange(toChunks(200)).updateInterval(10)));
    public static final DeferredHolder<EntityType<?>, EntityType<CargoContainerEntity>> CONTAINER = register("container",
            ModRegistries.ENTITY_TYPES.registerEntityType("container", CargoContainerEntity::new,
                    MobCategory.MISC, builder -> builder.sized(2, 1).fireImmune().clientTrackingRange(toChunks(200))
                            .updateInterval(10).setShouldReceiveVelocityUpdates(true)));
    public static final DeferredHolder<EntityType<?>, EntityType<VehicleEntity>> VEHICLE = register("vehicle",
            ModRegistries.ENTITY_TYPES.registerEntityType("vehicle", VehicleEntity::new, MobCategory.MISC,
                    builder -> builder.sized(2, 0.7F).clientTrackingRange(toChunks(600)).updateInterval(2)));
    public static final DeferredHolder<EntityType<?>, EntityType<io.github.mcheliceport.entity.uav.UavStationEntity>> UAV_STATION = register("uav_station",
            ModRegistries.ENTITY_TYPES.registerEntityType("uav_station", io.github.mcheliceport.entity.uav.UavStationEntity::new,
                    MobCategory.MISC, builder -> builder.sized(2, .7F).clientTrackingRange(toChunks(400)).updateInterval(2)));
    /** Upstream's {@code MCH_EntityHide}, which carries a player down a rope from a helicopter. */
    public static final DeferredHolder<EntityType<?>, EntityType<io.github.mcheliceport.entity.rappel.RappellingEntity>> HIDE = register("hide",
            ModRegistries.ENTITY_TYPES.registerEntityType("hide", io.github.mcheliceport.entity.rappel.RappellingEntity::new,
                    MobCategory.MISC, builder -> builder.sized(1, 1).clientTrackingRange(toChunks(200)).updateInterval(2)));
    public static final DeferredHolder<EntityType<?>, EntityType<RocketEntity>> ROCKET = weapon("rocket",
            RocketEntity::new, 1.0f, 1.0f, 530, 5);
    public static final DeferredHolder<EntityType<?>, EntityType<TvMissileEntity>> TV_MISSILE = weapon("tv_missile",
            TvMissileEntity::new, 1.0f, 1.0f, 530, 5);
    public static final DeferredHolder<EntityType<?>, EntityType<BulletEntity>> BULLET = weapon("bullet",
            BulletEntity::new, 1.0f, 1.0f, 530, 5);
    public static final DeferredHolder<EntityType<?>, EntityType<A10Entity>> A10 = weapon("a10",
            A10Entity::new, 5.0f, 3.0f, 530, 5);
    public static final DeferredHolder<EntityType<?>, EntityType<AAMissileEntity>> AA_MISSILE = weapon("aa_missile",
            AAMissileEntity::new, 1.0f, 1.0f, 530, 5);
    public static final DeferredHolder<EntityType<?>, EntityType<ASMissileEntity>> AS_MISSILE = weapon("as_missile",
            ASMissileEntity::new, 1.0f, 1.0f, 530, 5);
    public static final DeferredHolder<EntityType<?>, EntityType<TorpedoEntity>> TORPEDO = weapon("torpedo",
            TorpedoEntity::new, 1.0f, 1.0f, 530, 5);
    public static final DeferredHolder<EntityType<?>, EntityType<ATMissileEntity>> AT_MISSILE = weapon("at_missile",
            ATMissileEntity::new, 1.0f, 1.0f, 530, 5);
    public static final DeferredHolder<EntityType<?>, EntityType<BombEntity>> BOMB = weapon("bomb",
            BombEntity::new, 1.0f, 1.0f, 530, 5);
    public static final DeferredHolder<EntityType<?>, EntityType<MarkerRocketEntity>> MARKER_ROCKET = weapon("marker_rocket",
            MarkerRocketEntity::new, 1.0f, 1.0f, 530, 5);
    public static final DeferredHolder<EntityType<?>, EntityType<DispensedItemEntity>> DISPENSED_ITEM = weapon("dispensed_item",
            DispensedItemEntity::new, 1.0f, 1.0f, 530, 5);
    public static final DeferredHolder<EntityType<?>, EntityType<FlareEntity>> FLARE = weapon("flare",
            FlareEntity::new, 1.0f, 1.0f, 330, 10);
    public static final DeferredHolder<EntityType<?>, EntityType<ThrowableEntity>> THROWABLE = weapon("throwable",
            ThrowableEntity::new, 0.25f, 0.25f, 330, 10);
    /** Upstream's {@code MCH_EntityGunner}, the NPC gunner. */
    public static final DeferredHolder<EntityType<?>, EntityType<GunnerEntity>> GUNNER = register("gunner",
            ModRegistries.ENTITY_TYPES.registerEntityType("gunner", GunnerEntity::new, MobCategory.MISC,
                    builder -> builder.sized(0.6F, 1.8F).clientTrackingRange(toChunks(530)).updateInterval(5)
                            .setShouldReceiveVelocityUpdates(true)));
    public static final DeferredHolder<EntityType<?>, EntityType<ChaffEntity>> CHAFF = weapon("chaff",
            ChaffEntity::new, 1.0f, 1.0f, 330, 10);

    private ModEntityTypes() {
    }

    /** Every entity type of the port, for renderer registration. */
    public static List<DeferredHolder<EntityType<?>, ? extends EntityType<?>>> all() {
        return Collections.unmodifiableList(ALL);
    }

    /** Upstream's tracking range in blocks as whole chunks, rounded up. */
    static int toChunks(int blocks) {
        return (blocks + 15) / 16;
    }

    private static <T extends net.minecraft.world.entity.Entity> DeferredHolder<EntityType<?>, EntityType<T>> register(
            String name, DeferredHolder<EntityType<?>, EntityType<T>> holder) {
        ALL.add(holder);
        return holder;
    }

    /**
     * A projectile, flare or chaff (M5). The server sends their velocity, which they keep moving
     * with between updates; each entity decides whether it is saved (only mines are).
     */
    private static <T extends net.minecraft.world.entity.Entity> DeferredHolder<EntityType<?>, EntityType<T>> weapon(
            String name, EntityType.EntityFactory<T> factory, float width, float height, int trackingRangeBlocks,
            int updateInterval) {
        DeferredHolder<EntityType<?>, EntityType<T>> holder = ModRegistries.ENTITY_TYPES
                .registerEntityType(name, factory, MobCategory.MISC, builder -> builder
                        .sized(width, height)
                        .clientTrackingRange(toChunks(trackingRangeBlocks))
                        .updateInterval(updateInterval)
                        .setShouldReceiveVelocityUpdates(true));
        ALL.add(holder);
        return holder;
    }

    /** Loads the class, which registers the entity types. */
    static void init() {
    }
}
