package io.github.mcheliceport.client.particle;

import static org.junit.jupiter.api.Assertions.*;
import org.junit.jupiter.api.Test;

class BlastSmokeMotionTest {
    @Test void ejectionSettlesIntoSharedWindAcrossBlastSizes() {
        for (double impulse : new double[]{-0.7, 0, 0.7}) {
            double velocity = impulse;
            for (int age = 0; age < 128; age++)
                velocity = BlastSmokeMotion.advect(velocity, 0.024, 0.91, age);
            assertEquals(0.024, velocity, 0.0001);
        }
        assertEquals(0.7 * 0.91, BlastSmokeMotion.advect(0.7, 0.024, 0.91, 1));
    }

    @Test void windIsSharedSlowAndContinuousAtDayBoundary() {
        for (long tick : new long[]{0, 4000, 12000, 23999, 24000}) {
            double x = BlastSmokeMotion.windX(tick), z = BlastSmokeMotion.windZ(tick);
            assertEquals(x, BlastSmokeMotion.windX(tick));
            assertEquals(z, BlastSmokeMotion.windZ(tick));
            assertTrue(Math.hypot(x, z) < 0.04);
            assertEquals(x, BlastSmokeMotion.windX(tick + 1), 0.00001);
            assertEquals(z, BlastSmokeMotion.windZ(tick + 1), 0.00001);
        }
        assertTrue(BlastSmokeMotion.lifetime(100, true) < BlastSmokeMotion.lifetime(100, false));
        assertEquals(100, BlastSmokeMotion.lifetime(100, false));
    }
}
