package io.github.mcheliceport.weapon;

import io.github.mcheliceport.entity.aircraft.AircraftEntity;
import io.github.mcheliceport.entity.seat.SeatEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.CollisionContext;
import org.jspecify.annotations.Nullable;

/**
 * The world queries of upstream's weapon code ({@code W_WorldFunc}, {@code MCH_Lib}) in 26.2
 * terms.
 *
 * <p>1.12's {@code rayTraceBlocks(from, to)} hit every block by its selection box, grass and
 * flowers included, and ignored liquids; that is {@link ClipContext.Block#OUTLINE} with no fluids.
 * With {@code ignoreBlockWithoutBoundingBox} it used collision boxes ({@code COLLIDER}), and
 * {@code stopOnLiquid} hit liquid source blocks ({@code SOURCE_ONLY}). A miss is {@code null},
 * as upstream's.
 *
 * <p>Block positions follow the M3 convention ({@code GroundProbe}): {@code floor(pos + 0.5)}
 * where upstream wrote {@code (int) (pos + 0.5)}, which is one block off at negative coordinates.
 */
public final class WeaponWorld {

    private WeaponWorld() {
    }

    /** Upstream {@code W_WorldFunc.clip(w, from, to)}: selection boxes, no liquids. */
    public static @Nullable BlockHitResult clip(Level level, Vec3 from, Vec3 to) {
        return clip(level, from, to, ClipContext.Block.OUTLINE, ClipContext.Fluid.NONE);
    }

    /**
     * Upstream {@code rayTraceBlocks(from, to, stopOnLiquid, ignoreBlockWithoutBoundingBox, ...)},
     * returning only real hits.
     */
    public static @Nullable BlockHitResult rayTrace(Level level, Vec3 from, Vec3 to, boolean stopOnLiquid,
            boolean ignoreBlockWithoutBoundingBox) {
        return clip(level, from, to,
                ignoreBlockWithoutBoundingBox ? ClipContext.Block.COLLIDER : ClipContext.Block.OUTLINE,
                stopOnLiquid ? ClipContext.Fluid.SOURCE_ONLY : ClipContext.Fluid.NONE);
    }

    private static @Nullable BlockHitResult clip(Level level, Vec3 from, Vec3 to, ClipContext.Block block,
            ClipContext.Fluid fluid) {
        BlockHitResult hit = level.clip(new ClipContext(from, to, block, fluid, CollisionContext.empty()));
        return hit.getType() == HitResult.Type.MISS ? null : hit;
    }

    /** Upstream {@code W_WorldFunc.isBlockWater}: the block is water (a water block, not a waterlogged one). */
    public static boolean isBlockWater(Level level, int x, int y, int z) {
        return level.getBlockState(new BlockPos(x, y, z)).is(Blocks.WATER);
    }

    /** {@link #isBlockWater} at the block upstream picked with {@code (int) (pos + 0.5)}. */
    public static boolean isBlockWater(Level level, double x, double y, double z) {
        return isBlockWater(level, Mth.floor(x + 0.5), Mth.floor(y + 0.5), Mth.floor(z + 0.5));
    }

    /** Upstream {@code MCH_Lib.isBlockInWater}: water at or next to the block (not above when y is 0). */
    public static boolean isBlockInWater(Level level, BlockPos pos) {
        int[][] offset = {{0, -1, 0}, {0, 0, 0}, {0, 0, -1}, {0, 0, 1}, {-1, 0, 0}, {1, 0, 0}, {0, 1, 0}};
        if (pos.getY() > level.getMinY()) {
            for (int[] o : offset) {
                if (isBlockWater(level, pos.getX() + o[0], pos.getY() + o[1], pos.getZ() + o[2])) {
                    return true;
                }
            }
        }
        return false;
    }

    /** Upstream {@code getAircraft_RiddenOrControl}: the vehicle a rider sits in, by seat or pilot seat. */
    public static @Nullable AircraftEntity aircraftOf(@Nullable Entity rider) {
        if (rider == null) {
            return null;
        }
        Entity vehicle = rider.getVehicle();
        if (vehicle instanceof AircraftEntity aircraft) {
            return aircraft;
        }
        if (vehicle instanceof SeatEntity seat) {
            return seat.getParent();
        }
        return null;
    }

    /** Upstream {@code W_Entity.isEqual}: the same entity ID, where two nulls count as equal. */
    public static boolean isEqual(@Nullable Entity a, @Nullable Entity b) {
        return (a != null ? a.getId() : -1) == (b != null ? b.getId() : -1);
    }

    /** {@code Vec3d.fromPitchYaw(pitch, yaw)}: the unit vector a yaw and pitch look along. */
    public static Vec3 fromPitchYaw(float pitch, float yaw) {
        float f = Mth.cos(-yaw * Mth.DEG_TO_RAD - (float) Math.PI);
        float f1 = Mth.sin(-yaw * Mth.DEG_TO_RAD - (float) Math.PI);
        float f2 = -Mth.cos(-pitch * Mth.DEG_TO_RAD);
        float f3 = Mth.sin(-pitch * Mth.DEG_TO_RAD);
        return new Vec3(f1 * f2, f3, f * f2);
    }
}
