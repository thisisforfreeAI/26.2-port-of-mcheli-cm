package io.github.mcheliceport.registry;

import io.github.mcheliceport.entity.PlaceholderEntity;
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
 * entity is ported: weapons in M5, the other vehicles and the rest in M6. Upstream's two hit box types ({@code MCH.E.HitBox} and
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
    public static final DeferredHolder<EntityType<?>, EntityType<PlaceholderEntity>> ROCKET = placeholder("rocket", 1.0f, 1.0f, 530, 5);
    public static final DeferredHolder<EntityType<?>, EntityType<PlaceholderEntity>> TV_MISSILE = placeholder("tv_missile", 1.0f, 1.0f, 530, 5);
    public static final DeferredHolder<EntityType<?>, EntityType<PlaceholderEntity>> BULLET = placeholder("bullet", 1.0f, 1.0f, 530, 5);
    public static final DeferredHolder<EntityType<?>, EntityType<PlaceholderEntity>> A10 = placeholder("a10", 5.0f, 3.0f, 530, 5);
    public static final DeferredHolder<EntityType<?>, EntityType<PlaceholderEntity>> AA_MISSILE = placeholder("aa_missile", 1.0f, 1.0f, 530, 5);
    public static final DeferredHolder<EntityType<?>, EntityType<PlaceholderEntity>> AS_MISSILE = placeholder("as_missile", 1.0f, 1.0f, 530, 5);
    public static final DeferredHolder<EntityType<?>, EntityType<PlaceholderEntity>> TORPEDO = placeholder("torpedo", 1.0f, 1.0f, 530, 5);
    public static final DeferredHolder<EntityType<?>, EntityType<PlaceholderEntity>> AT_MISSILE = placeholder("at_missile", 1.0f, 1.0f, 530, 5);
    public static final DeferredHolder<EntityType<?>, EntityType<PlaceholderEntity>> BOMB = placeholder("bomb", 1.0f, 1.0f, 530, 5);
    public static final DeferredHolder<EntityType<?>, EntityType<PlaceholderEntity>> MARKER_ROCKET = placeholder("marker_rocket", 1.0f, 1.0f, 530, 5);
    public static final DeferredHolder<EntityType<?>, EntityType<PlaceholderEntity>> DISPENSED_ITEM = placeholder("dispensed_item", 1.0f, 1.0f, 530, 5);
    public static final DeferredHolder<EntityType<?>, EntityType<PlaceholderEntity>> FLARE = placeholder("flare", 1.0f, 1.0f, 330, 10);
    public static final DeferredHolder<EntityType<?>, EntityType<PlaceholderEntity>> THROWABLE = placeholder("throwable", 0.25f, 0.25f, 330, 10);
    public static final DeferredHolder<EntityType<?>, EntityType<PlaceholderEntity>> GUNNER = placeholder("gunner", 0.6f, 1.8f, 530, 5);
    public static final DeferredHolder<EntityType<?>, EntityType<PlaceholderEntity>> CHAFF = placeholder("chaff", 1.0f, 1.0f, 330, 10);

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
