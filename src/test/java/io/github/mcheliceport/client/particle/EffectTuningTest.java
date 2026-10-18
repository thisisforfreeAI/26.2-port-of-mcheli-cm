package io.github.mcheliceport.client.particle;

import static org.junit.jupiter.api.Assertions.*;

import io.github.mcheliceport.config.ClientConfig.EffectQuality;
import org.junit.jupiter.api.Test;

class EffectTuningTest {
    @Test
    void combatBurstCannotExceedBudgetAndNextTickRecovers() {
        for (EffectQuality quality : EffectQuality.values()) {
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
    void smokeFadesAtEndpointsAndPreservesBodyOpacity() {
        assertEquals(0.0F, EffectTuning.fade(0, 100));
        assertEquals(1.0F, EffectTuning.fade(2, 100));
        assertEquals(1.0F, EffectTuning.fade(60, 100));
        float previous = 1.0F;
        for (int age = 65; age <= 100; age++) {
            float alpha = EffectTuning.fade(age, 100);
            assertTrue(alpha >= 0 && alpha <= previous);
            previous = alpha;
        }
        assertEquals(0.0F, EffectTuning.fade(100, 100));
        assertEquals(0.0F, EffectTuning.fade(1, 0));
    }

    @Test
    void qualityScalesWithinAnExplicitLiveAndSpawnBudget() {
        int distance = 0;
        int live = 0;
        for (EffectQuality quality : EffectQuality.values()) {
            assertTrue(EffectTuning.distance(quality) > distance);
            assertTrue(EffectTuning.smokeLimit(quality) > live);
            assertTrue(EffectTuning.perTick(quality, true) <= EffectTuning.flashLimit(quality));
            distance = EffectTuning.distance(quality);
            live = EffectTuning.smokeLimit(quality);
        }
        assertEquals(768, EffectTuning.smokeLimit(EffectQuality.BALANCED));
        assertEquals(96, EffectTuning.perTick(EffectQuality.BALANCED, false));
    }
}
