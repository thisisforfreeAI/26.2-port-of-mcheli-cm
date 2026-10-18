package io.github.mcheliceport.client.particle;

import static org.junit.jupiter.api.Assertions.*;
import io.github.mcheliceport.config.ClientConfig.EffectBudget;
import io.github.mcheliceport.effect.BlastStyle;
import org.junit.jupiter.api.Test;

class ExplosionLookTest {
    @Test
    void missileImpactIsTighterAndCoolsBeforeTheBroadBombFireball() {
        assertTrue(ExplosionLook.coreRadius(8, BlastStyle.PENETRATOR) < ExplosionLook.coreRadius(8, BlastStyle.HEAVY));
        assertTrue(ExplosionLook.coreLife(BlastStyle.BLAST) < ExplosionLook.coreLife(BlastStyle.HEAVY));
        assertEquals(0, EffectLook.heat(1, 20, 100, ExplosionLook.coolingShare(BlastStyle.BLAST)));
        assertTrue(EffectLook.heat(1, 20, 100, ExplosionLook.coolingShare(BlastStyle.HEAVY)) > 0);
        assertEquals(0, ExplosionLook.coreRadius(Float.NaN, BlastStyle.HEAVY));
        assertEquals(0, ExplosionLook.coreRadius(8, BlastStyle.CANNON));
    }

    @Test
    void secondaryDustIsBoundedAndShedBeforePrimaryCuesUnderLoad() {
        assertEquals(0, ExplosionLook.dustCount(12, BlastStyle.HEAVY, EffectBudget.LOW, 0));
        assertEquals(0, ExplosionLook.dustCount(12, BlastStyle.HEAVY, EffectBudget.HIGH, 2));
        assertEquals(0, ExplosionLook.dustCount(12, BlastStyle.BLAST, EffectBudget.HIGH, 0));
        assertEquals(6, ExplosionLook.dustCount(12, BlastStyle.HEAVY, EffectBudget.BALANCED, 0));
        assertEquals(3, ExplosionLook.dustCount(12, BlastStyle.HEAVY, EffectBudget.BALANCED, 1));
        assertEquals(10, ExplosionLook.dustCount(Float.MAX_VALUE, BlastStyle.INCENDIARY, EffectBudget.HIGH, 0));
    }
    @Test
    void bombsStartBroaderAndKeepGrowingWithPowerWithinQualityCaps() {
        assertEquals(24, ExplosionLook.puffSize(16, 12, BlastStyle.HEAVY, EffectBudget.BALANCED));
        assertEquals(0.6F, ExplosionLook.startScale(12, BlastStyle.HEAVY, EffectBudget.BALANCED), 1e-6);
        assertTrue(ExplosionLook.startScale(12, BlastStyle.HEAVY, EffectBudget.BALANCED)
                > ExplosionLook.startScale(4, BlastStyle.HEAVY, EffectBudget.BALANCED));
        for (EffectBudget budget : EffectBudget.values()) {
            assertTrue(ExplosionLook.puffSize(16, Float.MAX_VALUE, BlastStyle.HEAVY, budget) <= 28);
            assertTrue(ExplosionLook.glowRadius(Float.MAX_VALUE, BlastStyle.HEAVY, budget) <= 18);
        }
    }

    @Test
    void ordinaryImpactsAndTinyBombsKeepTheirExistingScale() {
        for (BlastStyle style : new BlastStyle[]{BlastStyle.CANNON, BlastStyle.BLAST, BlastStyle.PENETRATOR}) {
            assertEquals(16, ExplosionLook.puffSize(16, 12, style, EffectBudget.HIGH));
            assertEquals(0.2F, ExplosionLook.startScale(12, style, EffectBudget.HIGH));
            assertEquals(12, ExplosionLook.glowRadius(12, style, EffectBudget.HIGH));
        }
        assertEquals(4, ExplosionLook.puffSize(4, 2, BlastStyle.HEAVY, EffectBudget.HIGH));
        assertEquals(0.2F, ExplosionLook.startScale(Float.NaN, BlastStyle.HEAVY, EffectBudget.HIGH));
    }
}
