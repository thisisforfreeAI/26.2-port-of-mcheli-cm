package io.github.mcheliceport.client.particle;

import static org.junit.jupiter.api.Assertions.*;
import io.github.mcheliceport.config.ClientConfig.EffectBudget;
import io.github.mcheliceport.effect.BlastStyle;
import org.junit.jupiter.api.Test;

class ExplosionSequenceTest {
    @Test void smallerImpactsKeepEarlierSmokeWhileLargeBombsMixFireAndSmoke() {
        assertTrue(ExplosionSequence.smokeLed(4, BlastStyle.BLAST));
        assertTrue(ExplosionSequence.smokeLed(12, BlastStyle.PENETRATOR));
        assertTrue(ExplosionSequence.smokeLed(2, BlastStyle.HEAVY));
        assertFalse(ExplosionSequence.smokeLed(8, BlastStyle.HEAVY));
        assertFalse(ExplosionSequence.smokeLed(8, BlastStyle.INCENDIARY));
    }
    @Test void airburstsAndExtremePowersHaveFiniteWorkAtEveryQualityAndPressure() {
        for (var quality : EffectBudget.values()) for (var style : BlastStyle.values()) {
            int previous = Integer.MAX_VALUE;
            for (int pressure = 0; pressure <= 3; pressure++) {
                int count = ExplosionSequence.lobes(Float.MAX_VALUE, style, quality, pressure);
                assertTrue(count >= 2 && count <= 18 && count <= previous);
                previous = count;
            }
            assertEquals(0, ExplosionSequence.lobes(Float.NaN, style, quality, 0));
            assertEquals(0, ExplosionSequence.lobes(-1, style, quality, 0));
            assertTrue(ExplosionSequence.radius(Float.MAX_VALUE, style) <= 8);
            assertTrue(ExplosionSequence.life(Float.MAX_VALUE, style) <= 100);
        }
    }

    @Test void largeBombsHaveMoreVolumeAndLongerFireThanMissilesAndSmallBombs() {
        assertTrue(ExplosionSequence.radius(12, BlastStyle.HEAVY) > ExplosionSequence.radius(4, BlastStyle.HEAVY) * 2);
        assertTrue(ExplosionSequence.radius(8, BlastStyle.HEAVY) > ExplosionSequence.radius(8, BlastStyle.PENETRATOR));
        assertTrue(ExplosionSequence.fireTicks(12, BlastStyle.HEAVY) > ExplosionSequence.fireTicks(4, BlastStyle.BLAST));
        for (var style : BlastStyle.values()) {
            assertTrue(ExplosionSequence.fireTicks(12, style) < ExplosionSequence.life(12, style));
            float previous = 0;
            for (int age = 0; age <= 100; age++) {
                float scale = ExplosionSequence.growth(age, 100);
                assertTrue(scale >= previous && scale <= 1);
                previous = scale;
            }
        }
    }
}
