package io.github.mcheliceport.client.particle;

import static org.junit.jupiter.api.Assertions.*;
import io.github.mcheliceport.config.ClientConfig.EffectBudget;
import io.github.mcheliceport.effect.BlastStyle;
import org.junit.jupiter.api.Test;

class BlastGroundLookTest {
    @Test void optionalGroundBurstsRespectQualityPressureAndWeaponSize() {
        for (var quality : EffectBudget.values()) for (var style : BlastStyle.values()) {
            int previous = Integer.MAX_VALUE;
            for (int pressure = 0; pressure <= 3; pressure++) {
                int requests = BlastGroundLook.requests(Float.MAX_VALUE, style, quality, pressure);
                assertTrue(requests <= previous && requests <= 16);
                if (pressure >= 2 || quality == EffectBudget.LOW || style == BlastStyle.CANNON) assertEquals(0, requests);
                previous = requests;
            }
            assertEquals(0, BlastGroundLook.requests(Float.NaN, style, quality, 0));
            assertEquals(0, BlastGroundLook.requests(-1, style, quality, 0));
        }
        assertEquals(10, BlastGroundLook.requests(12, BlastStyle.HEAVY, EffectBudget.BALANCED, 0));
        assertEquals(3, BlastGroundLook.requests(4, BlastStyle.BLAST, EffectBudget.BALANCED, 0));
        assertEquals(0, BlastGroundLook.fragmentTickCap(EffectBudget.HIGH, 2));
        assertEquals(0, BlastGroundLook.fragmentLiveCap(EffectBudget.LOW));
        assertEquals(36, BlastGroundLook.fragmentLiveCap(EffectBudget.BALANCED));
        assertEquals(72, BlastGroundLook.fragmentLiveCap(EffectBudget.HIGH));
        assertEquals(0, BlastGroundLook.surfaceReadCap(EffectBudget.LOW));
        assertEquals(96, BlastGroundLook.surfaceReadCap(EffectBudget.BALANCED));
        assertEquals(192, BlastGroundLook.surfaceReadCap(EffectBudget.HIGH));
        for (var surface : BlastGroundLook.Surface.values()) for (var style : BlastStyle.values()) {
            boolean heated = BlastGroundLook.hotFragment(12, style, surface, 2);
            assertEquals(style == BlastStyle.INCENDIARY && surface == BlastGroundLook.Surface.SOLID, heated);
        }
        assertFalse(BlastGroundLook.hotFragment(Float.NaN, BlastStyle.INCENDIARY, BlastGroundLook.Surface.SOLID, 2));
    }

    @Test void hardImpactsTradeDirtJetsForBoundedChipsInsteadOfAddingEffects() {
        for (int count : new int[]{6, 10}) {
            int solid = 0, soil = 0;
            for (int i = 0; i < count; i++) {
                boolean jet = i % 3 == 1;
                if (BlastGroundLook.fragmentSlot(i, count, true, jet, BlastGroundLook.Surface.SOLID)) solid++;
                if (BlastGroundLook.fragmentSlot(i, count, true, jet, BlastGroundLook.Surface.SOIL)) soil++;
            }
            assertTrue(solid > soil);
            assertTrue(solid <= BlastGroundLook.fragmentTickCap(count == 6 ? EffectBudget.BALANCED : EffectBudget.HIGH, 0));
            assertTrue(solid <= count);
        }
        assertFalse(BlastGroundLook.fragmentSlot(0, 1, true, true, BlastGroundLook.Surface.SOLID));
    }
    @Test void materialVolumeIsFiniteAndSmallImpactsStaySmallerThanBombs() {
        for (var surface : BlastGroundLook.Surface.values()) for (var style : BlastStyle.values()) {
            assertTrue(BlastGroundLook.dustRadius(Float.MAX_VALUE, style, surface) <= 4);
            assertTrue(BlastGroundLook.fragmentRadius(Float.MAX_VALUE, style, surface) <= 0.24F);
            assertTrue(Float.isFinite(BlastGroundLook.dustRadius(Float.NaN, style, surface)));
        }
        var sand = BlastGroundLook.Surface.SAND;
        var stone = BlastGroundLook.Surface.SOLID;
        assertTrue(BlastGroundLook.dustRadius(12, BlastStyle.HEAVY, sand) > BlastGroundLook.dustRadius(12, BlastStyle.HEAVY, stone));
        assertTrue(BlastGroundLook.dustRadius(4, BlastStyle.BLAST, sand) < BlastGroundLook.dustRadius(12, BlastStyle.HEAVY, sand) / 2);
        for (int age = 0; age <= 36; age++) assertTrue(BlastGroundLook.fragmentScale(age, 30) <= 1);
        assertEquals(0, BlastGroundLook.fragmentScale(30, 30));
        assertEquals(1, BlastGroundLook.fragmentScale(20, 30));
    }
}
