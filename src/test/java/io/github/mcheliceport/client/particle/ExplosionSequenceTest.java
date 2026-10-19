package io.github.mcheliceport.client.particle;

import static org.junit.jupiter.api.Assertions.*;
import io.github.mcheliceport.config.ClientConfig.EffectBudget;
import io.github.mcheliceport.effect.BlastStyle;
import org.junit.jupiter.api.Test;

class ExplosionSequenceTest {
    @Test void largeMissileWarheadsGetVolumeAndRollingFireWithoutChangingSmallSmokeProfiles() {
        assertTrue(ExplosionSequence.smokeLed(4, BlastStyle.BLAST));
        assertFalse(ExplosionSequence.smokeLed(10, BlastStyle.BLAST));
        assertTrue(ExplosionSequence.largeMissile(10, BlastStyle.BLAST));
        assertFalse(ExplosionSequence.largeMissile(Float.NaN, BlastStyle.BLAST));
        assertFalse(ExplosionSequence.largeMissile(10, BlastStyle.CANNON));
        assertTrue(ExplosionSequence.radius(10, BlastStyle.BLAST) > ExplosionSequence.radius(7, BlastStyle.BLAST) * 1.5F);
        assertTrue(ExplosionSequence.fireTicks(10, BlastStyle.BLAST) > ExplosionSequence.fireTicks(4, BlastStyle.BLAST));
        for (var budget : EffectBudget.values()) for (int pressure = 0; pressure <= 3; pressure++) {
            int count = ExplosionSequence.lobes(4, BlastStyle.BLAST, budget, pressure);
            int fire = ExplosionSequence.smokeFireLobes(4, BlastStyle.BLAST, count);
            assertTrue(fire >= 1 && fire <= 2);
            assertTrue(count - fire >= 1, "Retain the old growing smoke even under load");
        }
        assertEquals(0, ExplosionSequence.smokeFireLobes(4, BlastStyle.CANNON, 4));
        assertEquals(0, ExplosionSequence.smokeFireLobes(Float.NaN, BlastStyle.BLAST, 12));
    }
    @Test void backlightingExpiresAndIncendiariesKeepFireLongerThanHighExplosives() {
        for (int duration = 1; duration <= 56; duration++) {
            assertTrue(ExplosionSequence.backlight(0, duration) > 0);
            assertEquals(0, ExplosionSequence.backlight(duration, duration));
            assertEquals(0, ExplosionSequence.backlight(duration + 10, duration));
        }
        assertEquals(0, ExplosionSequence.backlight(0, 0));
        assertTrue(ExplosionSequence.fireTicks(12, BlastStyle.INCENDIARY)
                >= ExplosionSequence.fireTicks(12, BlastStyle.HEAVY) * 2);
    }

    @Test void billowsCanDissolveAtDifferentRatesWithoutGrowingOrOutlivingTheCloud() {
        assertTrue(ExplosionSequence.cloudAlpha(85, 100, 0, false, 24)
                > ExplosionSequence.cloudAlpha(85, 100, 0, false, 47));
        for (int fade = 24; fade <= 47; fade++) {
            assertEquals(0, ExplosionSequence.cloudAlpha(100, 100, 0, false, fade));
            assertEquals(0, ExplosionSequence.cloudAlpha(4, 100, 4, false, fade));
            assertEquals(1, ExplosionSequence.cloudAlpha(30, 100, 4, false, fade));
        }
    }

    @Test void smallerImpactsKeepEarlierSmokeWhileLargeBombsMixFireAndSmoke() {
        assertTrue(ExplosionSequence.smokeLed(4, BlastStyle.BLAST));
        assertTrue(ExplosionSequence.smokeLed(12, BlastStyle.PENETRATOR));
        assertTrue(ExplosionSequence.smokeLed(2, BlastStyle.HEAVY));
        assertFalse(ExplosionSequence.smokeLed(8, BlastStyle.HEAVY));
        assertFalse(ExplosionSequence.smokeLed(8, BlastStyle.INCENDIARY));
        assertFalse(ExplosionSequence.smokeLed(2, BlastStyle.INCENDIARY));
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
                float scale = ExplosionSequence.cloudGrowth(age, 0, false, false);
                assertTrue(scale >= previous && scale <= 1);
                previous = scale;
            }
        }
    }

    @Test void smokeGetsMostRequestsWithoutRaisingThePrimaryCap() {
        for (var quality : EffectBudget.values()) for (int pressure = 0; pressure <= 3; pressure++) {
            int total = ExplosionSequence.lobes(12, BlastStyle.HEAVY, quality, pressure);
            int smoke = ExplosionSequence.smokeLobes(total);
            assertTrue(smoke >= total / 2);
            assertTrue(smoke < total, "Keep at least one pocket of fire even when thinned");
        }
        assertEquals(8, ExplosionSequence.smokeLobes(12));
        assertEquals(0, ExplosionSequence.smokeLobes(0));
    }

    @Test void stagedSmokeIsInvisibleBeforeItsStartAndFadesBeforeExpiry() {
        for (int delay = 0; delay <= 12; delay++) {
            for (boolean fire : new boolean[] {false, true}) {
                assertEquals(0, ExplosionSequence.cloudAlpha(delay, 100, delay, fire));
                assertEquals(0, ExplosionSequence.cloudAlpha(100, 100, delay, fire));
                assertEquals(1, ExplosionSequence.cloudAlpha(delay + 8, 100, delay, fire));
                assertTrue(ExplosionSequence.cloudAlpha(90, 100, delay, fire) < 0.5F);
                float previous = 0;
                for (int age = 0; age <= 140; age++) {
                    float size = ExplosionSequence.cloudGrowth(age, delay, fire, false);
                    assertTrue(size >= previous && size <= 1, "Final size must stay inside admission charge");
                    previous = size;
                }
            }
        }
        assertTrue(ExplosionSequence.cloudGrowth(6, 0, true, false) > ExplosionSequence.cloudGrowth(6, 0, false, false));
        assertTrue(ExplosionSequence.cloudGrowth(12, 0, false, true) > ExplosionSequence.cloudGrowth(12, 0, false, false));
        assertEquals(0.25F, ExplosionSequence.flashGrowth(0, 6));
        assertEquals(1, ExplosionSequence.flashGrowth(6, 6));
    }

    @Test void fireStaysBrightUntilCoolingAndNeverLightsTheSmokeAfterward() {
        assertEquals(0, ExplosionSequence.fireHeat(0, 0));
        for (int duration = 4; duration <= 36; duration++) {
            assertEquals(1, ExplosionSequence.fireHeat(duration * 0.4F, duration));
            float cooling = ExplosionSequence.fireHeat(duration * 0.75F, duration);
            assertTrue(cooling > 0 && cooling < 0.5F);
            assertEquals(0, ExplosionSequence.fireHeat(duration, duration));
            assertEquals(0, ExplosionSequence.fireHeat(duration + 20, duration));
        }
    }
}
