package io.github.mcheliceport.client.effect;

import static org.junit.jupiter.api.Assertions.*;
import net.minecraft.world.phys.Vec3;
import org.junit.jupiter.api.Test;

class TrackPathTest {
    @Test void DistanceSamplingHandlesTurnsReversingAndRepeatedTicksWithoutStationaryStamps() {
        var path = new TrackPath();
        assertTrue(path.update(Vec3.ZERO, true, 1, 0.25).isEmpty());
        assertTrue(path.update(Vec3.ZERO, true, 2, 0.25).isEmpty());
        assertTrue(path.update(new Vec3(0, 0, 0.1), true, 3, 0.25).isEmpty());
        var first = path.update(new Vec3(0, 0, 0.5), true, 4, 0.25);
        assertEquals(2, first.size());
        assertEquals(0.25, first.getFirst().position().z, 0.0001);
        assertTrue(path.update(new Vec3(0, 0, 0.5), true, 4, 0.25).isEmpty());
        assertTrue(path.update(new Vec3(0, 0, 0.1), true, 5, 0.25).getFirst().direction().z < 0);
        assertTrue(path.update(new Vec3(0.5, 0, 0.1), true, 6, 0.25).getFirst().direction().x > 0);
    }
    @Test void AirGapsTeleportsAndLargeStepsResetInsteadOfCreatingBridgesOrBacklogs() {
        var path = new TrackPath(); path.update(Vec3.ZERO, true, 1, 0.25);
        assertTrue(path.update(new Vec3(100, 0, 0), true, 2, 0.25).isEmpty());
        assertEquals(2, path.update(new Vec3(103, 0, 0), true, 3, 0.25).size());
        assertTrue(path.update(new Vec3(103, 0, 0), false, 4, 0.25).isEmpty());
        assertTrue(path.update(new Vec3(104, 0, 0), true, 5, 0.25).isEmpty());
        assertTrue(path.update(new Vec3(104, 2, 0), true, 6, 0.25).isEmpty());
        assertTrue(path.update(new Vec3(104, 2, 1), true, 20, 0.25).isEmpty());
    }
}
