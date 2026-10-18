package io.github.mcheliceport.client.particle;

import static org.junit.jupiter.api.Assertions.*;
import org.junit.jupiter.api.Test;

class EffectFrameBudgetTest {
    private static void run(EffectFrameBudget budget, double millis, int frames, int target) {
        for (int i = 0; i < frames; i++) budget.sample(millis, target);
    }

    @Test
    void sustainedOverloadReducesDetailAndRecoveryIsSlower() {
        EffectFrameBudget budget = new EffectFrameBudget();
        run(budget, 20, 100, 80); // Two-second warmup.
        assertEquals(0, budget.pressure());
        run(budget, 20, 120, 80);
        assertEquals(3, budget.pressure());
        run(budget, 10, 400, 80);
        assertEquals(3, budget.pressure(), "do not restore detail on brief headroom");
        run(budget, 10, 200, 80);
        assertEquals(2, budget.pressure());
        run(budget, 10, 2000, 80);
        assertEquals(0, budget.pressure());
    }

    @Test
    void isolatedSpikesAndLoadingDoNotDriveDetailDown() {
        EffectFrameBudget budget = new EffectFrameBudget();
        run(budget, 10, 200, 80);
        budget.sample(100, 80);
        run(budget, 10, 100, 80);
        assertEquals(0, budget.pressure());
        for (double invalid : new double[]{1000, Double.NaN, Double.POSITIVE_INFINITY, -1, 0}) {
            budget.sample(invalid, 80);
        }
        assertEquals(0, budget.pressure());
        run(budget, 12.5, 1000, 80);
        assertEquals(0, budget.pressure(), "an 80 FPS cap should preserve full detail");
    }

    @Test
    void respectsUserCapsAndVsyncAndResetsOnWorldChange() {
        assertEquals(80, EffectFrameBudget.effectiveTarget(80, 120, false, 60));
        assertEquals(60, EffectFrameBudget.effectiveTarget(80, 60, false, 144));
        assertEquals(60, EffectFrameBudget.effectiveTarget(80, 120, true, 60));
        assertEquals(80, EffectFrameBudget.effectiveTarget(80, 120, true, 0));
        EffectFrameBudget budget = new EffectFrameBudget();
        run(budget, 1000.0 / 60, 1000, 60);
        assertEquals(0, budget.pressure());
        run(budget, 25, 200, 80);
        assertEquals(3, budget.pressure());
        budget.reset();
        assertEquals(0, budget.pressure());
        run(budget, 25, 40, 80);
        assertEquals(0, budget.pressure());
    }
}
