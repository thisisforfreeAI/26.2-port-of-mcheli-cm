package io.github.mcheliceport.entity.aircraft;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.LiquidBlock;
import org.junit.jupiter.api.Test;

/** Exit criterion E1: the ground probe over 26.2's full height and at negative coordinates. */
class GroundProbeTest {

    @Test
    void probeFindsGroundBelowWithinReach() {
        GroundProbe.BlockTest floor = (x, y, z) -> y <= 62;
        assertTrue(GroundProbe.find(floor, -64, 319, 10.2, 64.0, 10.2, 3, -3));
        assertFalse(GroundProbe.find(floor, -64, 319, 10.2, 66.0, 10.2, 3, -3));
        assertFalse(GroundProbe.find(floor, -64, 319, 10.2, 64.0, 10.2, 3, 0));
        // Upwards: a ceiling two blocks above.
        GroundProbe.BlockTest ceiling = (x, y, z) -> y == 66;
        assertTrue(GroundProbe.find(ceiling, -64, 319, 0, 64.2, 0, 1, 3));
        assertFalse(GroundProbe.find(ceiling, -64, 319, 0, 64.2, 0, 1, 2));
    }

    @Test
    void probeWorksBelowYZeroAndStopsAtBuildLimits() {
        GroundProbe.BlockTest deep = (x, y, z) -> y <= -52;
        assertTrue(GroundProbe.find(deep, -64, 319, 0, -50.0, 0, 3, -3), "upstream treated y < 0 as air");
        GroundProbe.BlockTest everywhere = (x, y, z) -> true;
        assertFalse(GroundProbe.find(everywhere, -64, 319, 0, -66.0, 0, 3, -3), "below the world");
    }

    @Test
    void probeRoundsNegativeCoordinatesDown() {
        // Only the block at x = -1: a vehicle at x = -0.7 stands over it (floor(-0.2) = -1);
        // upstream's (int) (-0.2) = 0 missed it.
        GroundProbe.BlockTest single = (x, y, z) -> x == -1 && z == 0 && y == 62;
        assertTrue(GroundProbe.find(single, -64, 319, -0.7, 63.0, 0.2, 1, -2));
        assertFalse(GroundProbe.find(single, -64, 319, 0.7, 63.0, 0.2, 1, -2));
    }

    @Test
    void probeFootprintFollowsSize() {
        GroundProbe.BlockTest offset = (x, y, z) -> x == 1 && z == -1 && y == 62;
        assertFalse(GroundProbe.find(offset, -64, 319, 0, 63.0, 0, 1, -2));
        assertTrue(GroundProbe.find(offset, -64, 319, 0, 63.0, 0, 3, -2));
        assertTrue(GroundProbe.find(offset, -64, 319, 0, 63.0, 0, 2, -2), "even sizes probe one more");
    }

    @Test
    void probeCountsBlocksLikeUpstream() {
        assertFalse(GroundProbe.counts(Blocks.AIR.defaultBlockState(), false));
        assertTrue(GroundProbe.counts(Blocks.STONE.defaultBlockState(), true));
        assertTrue(GroundProbe.counts(Blocks.WATER.defaultBlockState(), true));
        assertFalse(GroundProbe.counts(Blocks.WATER.defaultBlockState().setValue(LiquidBlock.LEVEL, 3), true));
        assertTrue(GroundProbe.counts(Blocks.WATER.defaultBlockState().setValue(LiquidBlock.LEVEL, 3), false));
        assertTrue(GroundProbe.isWater(Blocks.WATER.defaultBlockState()));
        assertFalse(GroundProbe.isWater(Blocks.LAVA.defaultBlockState()));
        assertFalse(GroundProbe.isWater(Blocks.STONE.defaultBlockState()));
    }
}
