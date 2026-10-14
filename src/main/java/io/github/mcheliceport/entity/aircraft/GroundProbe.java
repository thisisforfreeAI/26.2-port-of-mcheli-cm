package io.github.mcheliceport.entity.aircraft;

import net.minecraft.core.BlockPos;
import net.minecraft.util.Mth;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.LiquidBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.material.Fluids;

/**
 * "Is there a block within N blocks above or below?", upstream's {@code MCH_Lib.getBlockY} and
 * {@code getBlockIdY}, which the helicopter rules use for auto-levelling, on-ground pitch, strafing
 * roll, destroyed throttle and fold-blade taxiing.
 *
 * <p>Upstream probes a {@code size × size} footprint (odd sizes; even sizes probe one more) in
 * layers, starting at the vehicle's block and moving {@code |lenY|} layers up or down. Two 1.12
 * limits are fixed (M3_PLAN.md section 3.3):
 * <ul>
 *   <li>Upstream treats every layer outside y 0 to 255 as air and stops there. The port stops at
 *       the level's real build limits instead, so a 26.2 overworld's -64 to 319 works.</li>
 *   <li>Upstream converts positions with {@code (int) (pos + 0.5)}, which rounds towards zero and
 *       probes one block off at negative coordinates. The port uses {@code floor(pos + 0.5)}.</li>
 * </ul>
 * Inside y 0 to 255 at positive coordinates the results are upstream's.
 */
public final class GroundProbe {

    /** Answers whether a block counts; {@code collidableOnly} as upstream's {@code canColliableOnly}. */
    @FunctionalInterface
    public interface BlockTest {
        boolean isBlock(int x, int y, int z);
    }

    private GroundProbe() {
    }

    /**
     * Whether a counting block lies within {@code lenY} layers (negative: downwards) of the given
     * position, inside {@code [minY, maxY]}.
     */
    public static boolean find(BlockTest test, int minY, int maxY, double posX, double posY, double posZ,
            int size, int lenY) {
        if (lenY == 0) {
            return false;
        }
        int px = Mth.floor(posX + 0.5);
        int py = Mth.floor(posY + 0.5);
        int pz = Mth.floor(posZ + 0.5);
        int cntY = lenY > 0 ? lenY : -lenY;
        for (int y = 0; y < cntY; y++) {
            int by = py + (lenY > 0 ? y : -y);
            if (by < minY || by > maxY) {
                return false;
            }
            for (int x = -size / 2; x <= size / 2; x++) {
                for (int z = -size / 2; z <= size / 2; z++) {
                    if (test.isBlock(px + x, by, pz + z)) {
                        return true;
                    }
                }
            }
        }
        return false;
    }

    /** Probes the level. {@code collidableOnly} as upstream's {@code canColliableOnly}. */
    public static boolean find(Level level, double posX, double posY, double posZ, int size, int lenY,
            boolean collidableOnly) {
        BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();
        return find((x, y, z) -> counts(level.getBlockState(pos.set(x, y, z)), collidableOnly),
                level.getMinY(), level.getMaxY(), posX, posY, posZ, size, lenY);
    }

    /**
     * Whether a block counts as found. Upstream skipped air and, with {@code canColliableOnly}, asked
     * 1.12's {@code Block#canCollideCheck(state, true)}: true for every block except flowing
     * liquids (a liquid only counted as its source block).
     */
    public static boolean counts(BlockState state, boolean collidableOnly) {
        if (state.isAir()) {
            return false;
        }
        if (!collidableOnly) {
            return true;
        }
        if (state.getBlock() instanceof LiquidBlock) {
            return state.getFluidState().isSource();
        }
        return true;
    }

    /** Whether the first counting block below is water (upstream compared its block ID with water's). */
    public static boolean isWater(BlockState state) {
        return state.getFluidState().getType().isSame(Fluids.WATER) && state.getBlock() instanceof LiquidBlock;
    }
}
