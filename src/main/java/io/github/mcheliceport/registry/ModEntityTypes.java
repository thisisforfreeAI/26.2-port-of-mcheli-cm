package io.github.mcheliceport.registry;

import io.github.mcheliceport.entity.PlaceholderEntity;
import io.github.mcheliceport.entity.flare.ChaffEntity;
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
import io.github.mcheliceport.entity.seat.SeatEntity;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobCategory;
import net.neoforged.neoforge.registries.DeferredHolder;

/**
 * Upstream's entity types ({@code MCH_MOD#registerEntity}), with its sizes, tracking ranges and
 * update intervals. Upstream gave tracking ranges in blocks; vanilla counts chunks, so they are
 * rounded up to whole chunks (the server's view distance still caps them, as it did upstream
 * without its tracking mixin).
 *
 * <p>Helicopters and seats are real since M3. The others use {@link PlaceholderEntity} until their
 * entity is ported: the weapon entities are real since M5, the other vehicles and the rest come in M6. Upstream's two hit box types ({@code MCH.E.HitBox} and
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
    public static final DeferredHolder<EntityType<?>, EntityType<PlaceholderEntity>> GLTD = placeholder("gltd", 0.5f, 0.5f, 1200, 10);
    public static final DeferredHolder<EntityType<?>, EntityType<PlaceholderEntity>> PLANE = placeholder("plane", 2.0f, 0.7f, 1200, 2);
    public static final DeferredHolder<EntityType<?>, EntityType<PlaceholderEntity>> SHIP = placeholder("ship", 2.0f, 0.7f, 1200, 2);
    public static final DeferredHolder<EntityType<?>, EntityType<PlaceholderEntity>> TANK = placeholder("tank", 2.0f, 0.7f, 1200, 2);
    public static final DeferredHolder<EntityType<?>, EntityType<PlaceholderEntity>> CHAIN = placeholder("chain", 1.0f, 1.0f, 600, 10);
    public static final DeferredHolder<EntityType<?>, EntityType<PlaceholderEntity>> PARACHUTE = placeholder("parachute", 1.5f, 0.6f, 200, 10);
    public static final DeferredHolder<EntityType<?>, EntityType<PlaceholderEntity>> CONTAINER = placeholder("container", 2.0f, 1.0f, 200, 10);
    public static final DeferredHolder<EntityType<?>, EntityType<PlaceholderEntity>> VEHICLE = placeholder("vehicle", 2.0f, 0.7f, 600, 2);
    public static final DeferredHolder<EntityType<?>, EntityType<PlaceholderEntity>> UAV_STATION = placeholder("uav_station", 2.0f, 0.7f, 400, 10);
    /** Upstream's {@code MCH_EntityHide}, which carries a player down a rope from a helicopter. */
    public static final DeferredHolder<EntityType<?>, EntityType<PlaceholderEntity>> HIDE = placeholder("hide", 1.0f, 1.0f, 200, 10);
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
    public static final DeferredHolder<EntityType<?>, EntityType<PlaceholderEntity>> THROWABLE = placeholder("throwable", 0.25f, 0.25f, 330, 10);
    public static final DeferredHolder<EntityType<?>, EntityType<PlaceholderEntity>> GUNNER = placeholder("gunner", 0.6f, 1.8f, 530, 5);
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

    private static DeferredHolder<EntityType<?>, EntityType<PlaceholderEntity>> placeholder(String name, float width,
            float height, int trackingRangeBlocks, int updateInterval) {
        DeferredHolder<EntityType<?>, EntityType<PlaceholderEntity>> holder = ModRegistries.ENTITY_TYPES
                .registerEntityType(name, PlaceholderEntity::new, MobCategory.MISC, builder -> builder
                        .sized(width, height)
                        .clientTrackingRange(toChunks(trackingRangeBlocks))
                        .updateInterval(updateInterval)
                        .noSummon()
                        .noSave());
        ALL.add(holder);
        return holder;
    }

    /** Loads the class, which registers the entity types. */
    static void init() {
    }
}
