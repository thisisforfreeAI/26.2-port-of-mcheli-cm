package io.github.mcheliceport.item;

import io.github.mcheliceport.config.CommonConfig;
import io.github.mcheliceport.content.ContentRegistries;
import io.github.mcheliceport.content.ContentType;
import io.github.mcheliceport.content.aircraft.AircraftInfo;
import io.github.mcheliceport.entity.aircraft.AircraftEntity;
import io.github.mcheliceport.registry.ModEntityTypes;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.material.Fluids;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

/**
 * The item of one vehicle definition (helicopter, plane, ship, tank or vehicle). As upstream's
 * {@code MCH_ItemAircraft}, its durability is the vehicle's hit points.
 *
 * <p>Helicopters can be placed since M3; the other kinds stay inert until M6.
 */
public class AircraftItem extends Item {

    /** Upstream's reach for placing a vehicle. */
    public static final double PLACE_REACH = 5.0;

    private final ContentType type;
    private final String contentName;

    public AircraftItem(Properties properties, ContentType type, String contentName) {
        super(properties);
        this.type = type;
        this.contentName = contentName;
    }

    public ContentType getContentType() {
        return this.type;
    }

    /** The content name, which may differ from the item ID (see {@code ModItems#toItemPath}). */
    public String getContentName() {
        return this.contentName;
    }

    /** The current definition of this vehicle, or null if it is no longer loaded. */
    public @Nullable AircraftInfo getAircraftInfo() {
        ContentRegistries.Snapshot content = ContentRegistries.get();
        return switch (this.type) {
            case HELICOPTER -> content.heli().get(this.contentName);
            case PLANE -> content.plane().get(this.contentName);
            case SHIP -> content.ship().get(this.contentName);
            case TANK -> content.tank().get(this.contentName);
            case VEHICLE -> content.vehicle().get(this.contentName);
            default -> null;
        };
    }

    /** Vehicle kinds with functioning entities. */
    public boolean isPlaceable() {
        return switch (this.type) {
            case HELICOPTER, PLANE, SHIP, TANK, VEHICLE -> true;
            default -> false;
        };
    }

    /**
     * Upstream {@code createAircraft}: a new vehicle entity of this item at a position, not yet
     * added to the level. Null if the definition is missing or the kind is not ported yet.
     */
    public @Nullable AircraftEntity createAircraft(Level level, double x, double y, double z) {
        AircraftInfo info = this.getAircraftInfo();
        if (info == null || !this.isPlaceable()) {
            return null;
        }
        AircraftEntity aircraft = switch (this.type) {
            case HELICOPTER -> ModEntityTypes.HELICOPTER.get().create(level, EntitySpawnReason.SPAWN_ITEM_USE);
            case PLANE -> ModEntityTypes.PLANE.get().create(level, EntitySpawnReason.SPAWN_ITEM_USE);
            case SHIP -> ModEntityTypes.SHIP.get().create(level, EntitySpawnReason.SPAWN_ITEM_USE);
            case TANK -> ModEntityTypes.TANK.get().create(level, EntitySpawnReason.SPAWN_ITEM_USE);
            case VEHICLE -> ModEntityTypes.VEHICLE.get().create(level, EntitySpawnReason.SPAWN_ITEM_USE);
            default -> null;
        };
        if (aircraft == null) {
            return null;
        }
        aircraft.setPos(x, y, z);
        aircraft.xo = x;
        aircraft.yo = y;
        aircraft.zo = z;
        aircraft.setAircraftTypeName(info.name);
        if (!level.isClientSide()) {
            aircraft.setTextureName(info.getTextureName());
        }
        return aircraft;
    }

    /**
     * Upstream {@code onTileClick}: the vehicle for a click on block ({@code x}, {@code y},
     * {@code z}), one block above it, facing the nearest quarter of {@code yaw}; null if blocks
     * or entities are in the way.
     */
    public @Nullable AircraftEntity onTileClick(Level level, float yaw, int x, int y, int z) {
        // Upstream added 0.5F, which is float maths: past about 8 million blocks the vehicle landed
        // on a block edge. Doubles keep it centred everywhere in the world.
        AircraftEntity aircraft = this.createAircraft(level, x + 0.5, y + 1.0, z + 0.5);
        if (aircraft == null) {
            return null;
        }
        aircraft.initRotationYaw(snapYaw(yaw));
        return level.noCollision(aircraft, aircraft.getBoundingBox().deflate(0.1)) ? aircraft : null;
    }

    /** Upstream's yaw snap: {@code ((floor(yaw * 4 / 360 + 0.5) & 3) - 1) * 90}. */
    public static float snapYaw(float yaw) {
        return ((Mth.floor(yaw * 4.0F / 360.0F + 0.5) & 3) - 1) * 90;
    }

    /** Upstream {@code onItemRightClick}. */
    @Override
    public InteractionResult use(Level level, Player player, InteractionHand hand) {
        if (!this.isPlaceable()) {
            return InteractionResult.PASS;
        }
        ItemStack stack = player.getItemInHand(hand);
        Vec3 eye = player.getEyePosition();
        Vec3 look = player.getViewVector(1.0F);
        Vec3 end = eye.add(look.scale(PLACE_REACH));
        BlockHitResult hit = level.clip(new ClipContext(eye, end, ClipContext.Block.OUTLINE,
                ClipContext.Fluid.SOURCE_ONLY, player));
        if (hit.getType() == HitResult.Type.MISS) {
            return InteractionResult.PASS;
        }
        AABB searchBox = player.getBoundingBox().expandTowards(look.scale(PLACE_REACH)).inflate(1.0);
        for (Entity entity : level.getEntities(player, searchBox)) {
            if (entity.isPickable()) {
                float border = entity.getPickRadius();
                if (entity.getBoundingBox().inflate(border).contains(eye)) {
                    return InteractionResult.PASS;
                }
            }
        }
        BlockPos pos = hit.getBlockPos();
        if (CommonConfig.PLACEABLE_ON_SPONGE_ONLY.get() && !this.canPlaceOnSpongeRules(level, pos)) {
            return InteractionResult.FAIL;
        }
        this.spawnAircraft(stack, level, player, pos);
        return InteractionResult.SUCCESS;
    }

    /** Upstream's {@code PlaceableOnSpongeOnly} rules for a click on {@code pos}. */
    private boolean canPlaceOnSpongeRules(Level level, BlockPos pos) {
        AircraftInfo info = this.getAircraftInfo();
        if (info != null && info.isFloat && !info.canMoveOnGround) {
            if (!level.getBlockState(pos.below()).is(Blocks.SPONGE)) {
                return false;
            }
            for (int dx = -1; dx <= 1; dx++) {
                for (int dz = -1; dz <= 1; dz++) {
                    BlockState state = level.getBlockState(pos.offset(dx, 0, dz));
                    if (!state.is(Blocks.WATER) || !state.getFluidState().isSourceOfType(Fluids.WATER)) {
                        return false;
                    }
                }
            }
            return true;
        }
        BlockState state = level.getBlockState(pos);
        // 1.12's BlockSponge covered both the dry and the wet sponge.
        return state.is(Blocks.SPONGE) || state.is(Blocks.WET_SPONGE);
    }

    /** Upstream {@code spawnAircraft}. */
    public @Nullable AircraftEntity spawnAircraft(ItemStack stack, Level level, Player player, BlockPos pos) {
        AircraftEntity aircraft = this.onTileClick(level, player.getYRot(), pos.getX(), pos.getY(), pos.getZ());
        if (aircraft == null) {
            return null;
        }
        AircraftInfo info = aircraft.getAcInfo();
        if (info != null && info.creativeOnly && !player.isCreative()) {
            return null;
        }
        if (info != null && info.isUAV) {
            if (!level.isClientSide()) {
                player.sendSystemMessage(Component.literal("Please use the UAV station"));
            }
            return null;
        }
        if (!level.isClientSide()) {
            aircraft.readDataFromItem(stack);
            level.addFreshEntity(aircraft);
        }
        stack.consume(1, player);
        return aircraft;
    }
}
