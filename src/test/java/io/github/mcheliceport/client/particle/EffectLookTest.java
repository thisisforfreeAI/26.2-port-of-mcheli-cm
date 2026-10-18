package io.github.mcheliceport.client.particle;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class EffectLookTest {

    @Test
    void smokeFadesInThenHoldsThenFadesOut() {
        int life = 40;
        assertTrue(EffectLook.smokeAlpha(-1.0F, life) == 0.0F, "invisible before its first tick");
        assertEquals(1.0F, EffectLook.smokeAlpha(10.0F, life), 1.0E-6F, "full in the middle");
        assertEquals(1.0F, EffectLook.smokeAlpha(life * (1.0F - EffectLook.FADE_OUT_SHARE), life), 1.0E-6F);
        assertEquals(0.0F, EffectLook.smokeAlpha(life, life), 1.0E-6F, "gone at the end");
        float prev = 1.0F;
        for (float age = 28.0F; age <= life; age += 0.5F) {
            float a = EffectLook.smokeAlpha(age, life);
            assertTrue(a <= prev + 1.0E-6F, "never brightens while fading out");
            prev = a;
        }
    }

    @Test
    void zeroLifetimeIsHarmless() {
        assertEquals(1.0F, EffectLook.smokeAlpha(3.0F, 0));
        assertEquals(0.0F, EffectLook.heat(1.0F, 3.0F, 0));
        assertEquals(0.0F, EffectLook.glowAlpha(3.0F, 0));
    }

    @Test
    void fireballCoolsToItsOwnColour() {
        int life = 100;
        assertEquals(0.8F, EffectLook.heat(0.8F, 0.0F, life), 1.0E-6F);
        assertEquals(0.0F, EffectLook.heat(0.8F, life * EffectLook.COOL_SHARE, life), 1.0E-6F);
        assertEquals(0.0F, EffectLook.heat(0.8F, life, life), 1.0E-6F);
        assertEquals(0.0F, EffectLook.heat(0.0F, 0.0F, life), "cold puffs stay cold");
        assertEquals(0.4F, EffectLook.fireBlend(0.4F, EffectLook.FIRE_R, 0.0F), 1.0E-6F);
        assertEquals(EffectLook.FIRE_G, EffectLook.fireBlend(0.4F, EffectLook.FIRE_G, 1.0F), 1.0E-6F);
        assertEquals(15, EffectLook.heatBlockLight(1.0F));
        assertEquals(0, EffectLook.heatBlockLight(0.0F));
        assertEquals(15, EffectLook.heatBlockLight(2.0F), "clamped");
    }

    @Test
    void smokeUsesAmbientLightAndHeatOnlyRaisesItsBlockLight() {
        int cave = 0;
        int torch = (12 << 4) | (3 << 20);
        int sky = 15 << 20;
        assertEquals(cave, EffectLook.smokeLight(cave, 0));
        assertEquals(torch, EffectLook.smokeLight(torch, 0));
        assertEquals(sky, EffectLook.smokeLight(sky, 0));
        assertEquals(15 << 4, EffectLook.smokeLight(cave, 1));
        assertEquals(torch, EffectLook.smokeLight(torch, 0.3F));
        assertEquals((15 << 4) | (3 << 20), EffectLook.smokeLight(torch, 1));
    }

    @Test
    void lightingCacheRespondsToMotionAndStationaryLightChangesWithoutPerFrameSampling() {
        assertTrue(EffectLook.refreshLight(0, -4, true));
        assertFalse(EffectLook.refreshLight(0, 0, true));
        assertFalse(EffectLook.refreshLight(3, 0, false));
        assertTrue(EffectLook.refreshLight(4, 0, false));
        assertTrue(EffectLook.refreshLight(1, 0, true));
    }

    @Test
    void glowDiesAway() {
        assertEquals(1.0F, EffectLook.glowAlpha(0.0F, 10), 1.0E-6F);
        assertEquals(0.25F, EffectLook.glowAlpha(5.0F, 10), 1.0E-6F);
        assertEquals(0.0F, EffectLook.glowAlpha(12.0F, 10), 1.0E-6F);
    }
}
