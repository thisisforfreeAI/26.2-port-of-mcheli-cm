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

    @Test void opposingBillowCurlsConvergeOnOneWindInsteadOfSplittingForever() {
        for (double turn : new double[]{-0.043, 0.018, 0.043}) {
            double x = 0.4, z = -0.1;
            for (int age = 1; age <= 128; age++) {
                double curl = BlastSmokeMotion.curlShare(age);
                double dx = x * Math.cos(turn) - z * Math.sin(turn);
                double dz = x * Math.sin(turn) + z * Math.cos(turn);
                x += (dx - x) * curl; z += (dz - z) * curl;
                x = BlastSmokeMotion.advect(x, 0.024, 0.95, age);
                z = BlastSmokeMotion.advect(z, 0.012, 0.95, age);
            }
            assertEquals(0.024, x, 0.001);
            assertEquals(0.012, z, 0.001);
        }
        assertEquals(1, BlastSmokeMotion.curlShare(8));
        assertEquals(0, BlastSmokeMotion.curlShare(40));
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
