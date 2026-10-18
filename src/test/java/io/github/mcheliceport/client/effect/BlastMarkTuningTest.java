package io.github.mcheliceport.client.effect;

import static org.junit.jupiter.api.Assertions.*;
import io.github.mcheliceport.config.ClientConfig.EffectBudget;
import io.github.mcheliceport.effect.BlastStyle;
import org.junit.jupiter.api.Test;

class BlastMarkTuningTest {
    @Test
    void familiesScaleWithPowerButCannotCreateUnboundedFootprints() {
        assertTrue(BlastMarkTuning.radius(4, BlastStyle.HEAVY, EffectBudget.BALANCED)
                > BlastMarkTuning.radius(4, BlastStyle.CANNON, EffectBudget.BALANCED));
        assertTrue(BlastMarkTuning.radius(4, BlastStyle.INCENDIARY, EffectBudget.BALANCED)
                > BlastMarkTuning.radius(4, BlastStyle.PENETRATOR, EffectBudget.BALANCED));
        for (EffectBudget budget : EffectBudget.values()) {
            for (BlastStyle style : BlastStyle.values()) {
                assertTrue(BlastMarkTuning.radius(Float.MAX_VALUE, style, budget) <= 5);
                assertEquals(0, BlastMarkTuning.radius(Float.NaN, style, budget));
                assertEquals(0, BlastMarkTuning.radius(-10, style, budget));
            }
            assertTrue(BlastMarkTuning.markLimit(budget) <= 48);
            assertTrue(BlastMarkTuning.quadLimit(budget) <= 768);
        }
    }

    @Test
    void distantAirburstsDoNotReachGroundAndMarksFadeToZero() {
        assertFalse(BlastMarkTuning.reached(2, 0, 10, 0));
        assertTrue(BlastMarkTuning.reached(4, 2, 1, 2));
        assertFalse(BlastMarkTuning.reached(4, 20, 0, 0));
        int life = BlastMarkTuning.lifetime(EffectBudget.BALANCED);
        assertEquals(1, BlastMarkTuning.alpha(0, life));
        assertEquals(0.5F, BlastMarkTuning.alpha(life - 100, life));
        assertEquals(0, BlastMarkTuning.alpha(life, life));
        assertEquals(0, BlastMarkTuning.alpha(-1, life));
    }

    @Test
    void quarterTurnsKeepTextureCoordinatesContinuousAtBlockEdges() {
        float[][] expected = {{0.625F, 0.75F}, {0.25F, 0.625F}, {0.375F, 0.25F}, {0.75F, 0.375F}};
        for (int turn = 0; turn < 4; turn++) {
            assertArrayEquals(new float[]{0.5F, 0.5F}, BlastMarkTuning.uv(0, 0, 4, turn));
            float[] corner = BlastMarkTuning.uv(4, 4, 4, turn);
            for (float value : corner) assertTrue(value == 0 || value == 1);
            assertArrayEquals(expected[turn], BlastMarkTuning.uv(1, 2, 4, turn));
        }
    }
}
