package io.github.mcheliceport.wingman.ai;

import net.minecraft.core.BlockPos;
import net.minecraft.util.Mth;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.phys.Vec3;

/**
 * M10d terrain lookahead: the highest ground an aircraft will cross where it is and a few seconds
 * ahead along its horizontal velocity, so an AI pilot climbs before a hill instead of into it.
 */
public final class TerrainAvoidance {

    /** The top of the ground at a column: the first free block above the highest that blocks motion. */
    @FunctionalInterface
    public interface Ground {
        double top(int x, int z);
    }

    /** Below this speed, in blocks a tick, the aircraft is hovering and only the ground under it counts. */
    static final double MIN_SPEED = 0.05;

    private TerrainAvoidance() {
    }

    /** The level's ground, leaves left out; an unloaded column counts as the bottom of the world. */
    public static Ground of(Level level) {
        return (x, z) -> level.hasChunkAt(new BlockPos(x, 0, z))
                ? level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, x, z) : level.getMinY();
    }

    /**
     * The highest ground under {@code pos} and every second ahead at the horizontal {@code velocity}
     * (blocks a tick) up to the furthest of {@code seconds}, each with the columns around it.
     */
    public static double highestAhead(Ground ground, Vec3 pos, Vec3 velocity, int[] seconds) {
        double top = around(ground, pos.x, pos.z);
        double vx = velocity.x;
        double vz = velocity.z;
        if (vx * vx + vz * vz < MIN_SPEED * MIN_SPEED) {
            return top;
        }
        int last = 0;
        for (int s : seconds) {
            last = Math.max(last, s);
        }
        // Every second up to the furthest, so a ridge between two sample points is not missed.
        for (int s = 1; s <= last; s++) {
            double t = s * 20.0;
            top = Math.max(top, around(ground, pos.x + vx * t, pos.z + vz * t));
        }
        return top;
    }

    /** The highest of the column and its four neighbours. */
    private static double around(Ground ground, double x, double z) {
        int bx = Mth.floor(x);
        int bz = Mth.floor(z);
        double top = ground.top(bx, bz);
        top = Math.max(top, ground.top(bx + 1, bz));
        top = Math.max(top, ground.top(bx - 1, bz));
        top = Math.max(top, ground.top(bx, bz + 1));
        return Math.max(top, ground.top(bx, bz - 1));
    }

    /** The height to fly at: {@code wanted}, or {@code clearance} over the ground ahead when that is higher. */
    public static double safeY(double wanted, double groundAhead, double clearance) {
        return Math.max(wanted, groundAhead + clearance);
    }
}
