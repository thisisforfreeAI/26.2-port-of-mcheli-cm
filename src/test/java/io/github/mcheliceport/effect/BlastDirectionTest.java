package io.github.mcheliceport.effect;

import static org.junit.jupiter.api.Assertions.*;
import net.minecraft.world.phys.Vec3;
import org.junit.jupiter.api.Test;

class BlastDirectionTest {
    @Test void directionsRetainHeadingWithoutEmittingIntoTheGroundOrAcceptingInvalidVelocity() {
        assertEquals(BlastDirection.NONE, BlastDirection.from(Vec3.ZERO));
        assertEquals(BlastDirection.NONE, BlastDirection.from(new Vec3(Double.NaN, 1, 0)));
        assertEquals(BlastDirection.NONE, BlastDirection.from(new Vec3(1, Double.POSITIVE_INFINITY, 0)));
        assertEquals(Vec3.ZERO, BlastDirection.NONE.burstAxis());
        for (var v : java.util.List.of(new Vec3(1, -2, 3), new Vec3(-3, 1, -2),
                new Vec3(0, -10, 0), new Vec3(Double.MAX_VALUE, -Double.MAX_VALUE, 0))) {
            var d = BlastDirection.from(v);
            assertNotEquals(BlastDirection.NONE, d);
            var axis = d.burstAxis();
            assertEquals(1, axis.length(), 0.00001);
            assertTrue(axis.y > 0);
            assertEquals(Math.signum(v.x), Math.signum(axis.x));
            assertEquals(Math.signum(v.z), Math.signum(axis.z));
        }
    }
}
