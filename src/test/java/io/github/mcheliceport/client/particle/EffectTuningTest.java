package io.github.mcheliceport.client.particle;

import static org.junit.jupiter.api.Assertions.*;

import io.github.mcheliceport.config.ClientConfig.EffectBudget;
import org.junit.jupiter.api.Test;

class EffectTuningTest {
    @Test void primaryBlastIsThinnedOnceButStillPaysCoverageAndDistanceCosts() {
        for (int pressure = 0; pressure <= 3; pressure++) {
            assertEquals(1, EffectTuning.admissionStride(100, 0, false, pressure, true));
            assertEquals(1 + pressure, EffectTuning.admissionStride(100, 0, false, pressure, false));
            assertEquals(16, EffectTuning.admissionStride(200 * 200, 2, false, pressure, true));
            EffectTuning.Budget budget = new EffectTuning.Budget();
            int cap = Math.max(16, EffectTuning.perTick(EffectBudget.BALANCED, false) / (1 + pressure));
            int requested = ExplosionSequence.lobes(12, io.github.mcheliceport.effect.BlastStyle.HEAVY,
                    EffectBudget.BALANCED, pressure);
            int accepted = 0;
            int cost = EffectTuning.coverageCost(8, 100);
            for (int i = 0; i < requested; i++)
                if (budget.admit(1, cap, EffectTuning.admissionStride(100, 0, false, pressure, true), cost)) accepted++;
            assertTrue(accepted > 0 && accepted <= requested && accepted * cost <= cap);
        }
    }

    @Test
    void combatBurstCannotExceedBudgetAndNextTickRecovers() {
        for (EffectBudget quality : EffectBudget.values()) {
            EffectTuning.Budget budget = new EffectTuning.Budget();
            int cap = EffectTuning.perTick(quality, false);
            int accepted = 0;
            for (int i = 0; i < 10000; i++) {
                if (budget.admit(40, cap, 1)) accepted++;
            }
            assertEquals(cap, accepted);
            assertTrue(budget.admit(41, cap, 1));
            assertTrue(budget.admit(0, cap, 1), "world time moving backwards must reset too");
        }
    }

    @Test
    void smokeCannotStarveFlashes() {
        EffectTuning.Budget smoke = new EffectTuning.Budget();
        EffectTuning.Budget flash = new EffectTuning.Budget();
        for (int i = 0; i < 1000; i++) smoke.admit(12, 48, 1);
        assertTrue(flash.admit(12, 24, 1));
        assertFalse(smoke.admit(12, 48, 1));
    }

    @Test
    void distantAndMinimalSmokeThinEvenlyWithoutSuppressingFlashes() {
        assertEquals(1, EffectTuning.stride(64 * 64, 0, false));
        assertEquals(2, EffectTuning.stride(64 * 64 + 1, 0, false));
        assertEquals(2, EffectTuning.stride(128 * 128, 0, false));
        assertEquals(4, EffectTuning.stride(128 * 128 + 1, 0, false));
        int stride = EffectTuning.stride(200 * 200, 2, false);
        EffectTuning.Budget budget = new EffectTuning.Budget();
        int accepted = 0;
        for (int i = 0; i < 160; i++) if (budget.admit(0, 100, stride)) accepted++;
        assertEquals(10, accepted);
        assertEquals(1, EffectTuning.stride(200 * 200, 2, true));
    }

    @Test
    void closeLargeCloudsConsumeMoreBudgetWithoutOverspending() {
        assertEquals(1, EffectTuning.coverageCost(1, 10000));
        assertEquals(16, EffectTuning.coverageCost(10, 0));
        assertEquals(16, EffectTuning.coverageCost(Float.NaN, 100));
        assertTrue(EffectTuning.coverageCost(4, 100) > EffectTuning.coverageCost(4, 10000));
        EffectTuning.Budget budget = new EffectTuning.Budget();
        assertTrue(budget.admit(0, 17, 1, 16));
        assertFalse(budget.admit(0, 17, 1, 2));
        assertTrue(budget.admit(0, 17, 1, 1));
        assertFalse(budget.admit(0, 17, 1, 1));
        assertTrue(budget.admit(1, 17, 1, 16));
    }

    @Test
    void sparksHaveSmallHardCapsAndDisappearBeforeCoreEffectsUnderLoad() {
        assertEquals(0, EffectTuning.sparkCount(10, EffectBudget.LOW, 0));
        assertEquals(0, EffectTuning.sparkCount(10, EffectBudget.HIGH, 2));
        assertEquals(0, EffectTuning.sparkCount(1, EffectBudget.HIGH, 0));
        assertEquals(0, EffectTuning.sparkCount(Float.NaN, EffectBudget.HIGH, 0));
        assertEquals(8, EffectTuning.sparkCount(1000, EffectBudget.BALANCED, 0));
        assertEquals(12, EffectTuning.sparkCount(1000, EffectBudget.HIGH, 0));
        assertEquals(4, EffectTuning.sparkCount(1000, EffectBudget.BALANCED, 1));
    }

    @Test
    void qualityScalesWithinAnExplicitLiveAndSpawnBudget() {
        int distance = 0;
        int live = 0;
        for (EffectBudget quality : EffectBudget.values()) {
            assertTrue(EffectTuning.distance(quality) > distance);
            assertTrue(EffectTuning.smokeLimit(quality) > live);
            assertTrue(EffectTuning.perTick(quality, true) <= EffectTuning.flashLimit(quality));
            distance = EffectTuning.distance(quality);
            live = EffectTuning.smokeLimit(quality);
        }
        assertEquals(1536, EffectTuning.smokeLimit(EffectBudget.BALANCED));
        assertEquals(256, EffectTuning.perTick(EffectBudget.BALANCED, false));
    }
    @Test void onlyContrailsExtendTheBalancedRangeAndTheirEmitterAlreadyProvidesThinning(){
        assertEquals(192,EffectTuning.distance(EffectBudget.BALANCED));assertEquals(256,EffectTuning.contrailDistance(EffectBudget.BALANCED));
        assertEquals(EffectTuning.distance(EffectBudget.LOW),EffectTuning.contrailDistance(EffectBudget.LOW));
        assertEquals(1,EffectTuning.contrailStride(0));assertEquals(4,EffectTuning.contrailStride(2));
        var shared=new EffectTuning.Budget();assertTrue(shared.admit(1,1,1));assertFalse(shared.admit(1,1,EffectTuning.contrailStride(0)));
    }
}
