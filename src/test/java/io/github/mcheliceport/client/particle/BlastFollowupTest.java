package io.github.mcheliceport.client.particle;

import static org.junit.jupiter.api.Assertions.*;
import io.github.mcheliceport.config.ClientConfig.EffectBudget;
import io.github.mcheliceport.effect.BlastStyle;
import org.junit.jupiter.api.Test;

class BlastFollowupTest {
    @Test void rocketsGetMoreSmokeWithoutRaisingPrimaryCapsOrRemovingFireUnderLoad() {
        assertTrue(ExplosionSequence.lobes(4, BlastStyle.BLAST, EffectBudget.BALANCED, 0) > 7);
        assertTrue(ExplosionSequence.smokeLobes(12, BlastStyle.BLAST) > ExplosionSequence.smokeLobes(12));
        for (var quality : EffectBudget.values()) for (var style : new BlastStyle[]{BlastStyle.BLAST, BlastStyle.PENETRATOR})
            for (int pressure = 0; pressure <= 3; pressure++) for (int power = 2; power <= 32; power++) {
                int count = ExplosionSequence.lobes(power, style, quality, pressure);
                assertTrue(count <= (quality == EffectBudget.LOW ? 6 : quality == EffectBudget.HIGH ? 28 : 18));
                int fire = ExplosionSequence.smokeFireLobes(power, style, count);
                assertTrue(fire > 0 && count - fire > 0);
                assertTrue(ExplosionLook.startScale(power, style, quality) < 2);
                assertTrue(ExplosionLook.puffSize(16, power, style, quality) * 0.2F <= 4.2F);
            }
    }
    @Test void dustFrontsShedUnderLoadAndStayShortAndBounded() {
        assertTrue(BlastRingLook.sectors(12, BlastStyle.HEAVY, EffectBudget.BALANCED, 0) > 0);
        for (var q : EffectBudget.values()) {
            assertEquals(0, BlastRingLook.sectors(12, BlastStyle.HEAVY, q, 1));
            assertEquals(0, BlastRingLook.sectors(12, BlastStyle.PENETRATOR, q, 0));
            assertEquals(0, BlastRingLook.sectors(Float.NaN, BlastStyle.BLAST, q, 0));
        }
        assertEquals(0, BlastRingLook.sectors(12, BlastStyle.HEAVY, EffectBudget.LOW, 0));
        assertTrue(BlastRingLook.reach(Float.MAX_VALUE) <= 10);
        assertEquals(0, BlastRingLook.fade(18, 18));
        assertEquals(0, BlastRingLook.fade(30, 18));
        assertTrue(BlastRingLook.travel(4) > 0.6F);
        assertEquals(1, BlastRingLook.travel(10));
        assertEquals(1, BlastRingLook.travel(18));
    }
}
