package io.github.mcheliceport.client.effect;

import static org.junit.jupiter.api.Assertions.*;
import io.github.mcheliceport.config.ClientConfig.EffectBudget;
import org.junit.jupiter.api.Test;

class BlastMarkWorkTest {
    @Test void impactStormCannotResetOrExceedTheSharedTickAllowance() {
        int[][] limits = {{128, 16, 24, 16}, {256, 32, 48, 40}, {384, 48, 64, 80}};
        int q = 0;
        for (var quality : EffectBudget.values()) {
            var work = new BlastMarkWork();
            work.begin(50, quality);
            int[] admitted = new int[4];
            for (int impact = 0; impact < 1000; impact++) {
                work.begin(50, quality);
                if (work.read()) admitted[0]++;
                if (work.ray()) admitted[1]++;
                if (work.column()) admitted[2]++;
                if (work.check()) admitted[3]++;
            }
            assertArrayEquals(limits[q++], admitted);
            work.begin(51, quality);
            assertTrue(work.read()); assertTrue(work.ray()); assertTrue(work.column()); assertTrue(work.check());
            assertEquals(1, work.reads); assertEquals(1, work.rays); assertEquals(1, work.columns); assertEquals(1, work.checks);
        }
    }

    @Test void worldReplacementAndClockRewindStartFreshWork() {
        var work = new BlastMarkWork();
        work.begin(100, EffectBudget.BALANCED);
        while (work.read()) {}
        work.clear(); work.begin(100, EffectBudget.BALANCED);
        assertTrue(work.read()); assertEquals(1, work.reads);
        work.begin(20, EffectBudget.BALANCED);
        assertTrue(work.read()); assertEquals(1, work.reads);
    }
}
