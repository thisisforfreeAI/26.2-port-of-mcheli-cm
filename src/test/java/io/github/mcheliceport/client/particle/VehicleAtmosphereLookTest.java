package io.github.mcheliceport.client.particle;

import static org.junit.jupiter.api.Assertions.*;
import io.github.mcheliceport.config.ClientConfig.EffectBudget;
import org.junit.jupiter.api.Test;

class VehicleAtmosphereLookTest {
    @Test void enginesSpoolGraduallyAndStartupEndsWithoutReplaying() {
        var engine = new VehicleAtmosphereLook();
        engine.tick(0, true);
        engine.tick(1, true);
        assertTrue(engine.justStarted());
        assertTrue(engine.spool() < 0.02F);
        float pitch = engine.whinePitch();
        for (int i = 0; i < 40; i++) engine.tick(1, true);
        assertTrue(engine.spool() > 0.4F && engine.spool() < 0.6F);
        assertTrue(engine.whinePitch() > pitch);
        assertTrue(engine.heat() > 0 && engine.heat() < 1);
        for (int i = 0; i < 60; i++) engine.tick(1, true);
        assertEquals(0, engine.startup());
        assertEquals(0, engine.whineVolume());
        assertEquals(1, engine.heat());
        engine.tick(0, false);
        assertEquals(0, engine.startup());
        for (int i = 0; i < 50; i++) engine.tick(0, false);
        assertEquals(0, engine.heat());
        engine.tick(0.2, true);
        assertTrue(engine.justStarted());
    }

    @Test void observingRunningEnginesDoesNotReplayStartupAndInvalidInputsStopThem() {
        var engine = new VehicleAtmosphereLook();
        engine.tick(0.8, true);
        assertEquals(0, engine.startup());
        assertEquals(0.8F, engine.spool());
        engine.tick(Double.NaN, true);
        assertEquals(0, engine.startup());
        assertTrue(Float.isFinite(engine.heat()));
        engine.tick(1, false);
        assertEquals(0, engine.startup());
    }

    @Test void damageAndWashStayMaterialAwareAndBoundedUnderLoad() {
        assertEquals(0, VehicleAtmosphereLook.damage(100, 100));
        assertEquals(0, VehicleAtmosphereLook.damage(0, 0));
        assertTrue(VehicleAtmosphereLook.damage(20, 100) > VehicleAtmosphereLook.damage(50, 100));
        int bursts = 0;
        for (int tick = 0; tick < 41; tick++) if (VehicleAtmosphereLook.smokePulse(tick, 7, 0.8F)) bursts++;
        assertTrue(bursts > 0 && bursts < 41, "damaged engines must have gaps between smoke surges");
        for (var budget : EffectBudget.values()) {
            for (var surface : RotorWashLook.Surface.values()) {
                assertEquals(0, RotorWashLook.debrisCount(surface, 0, budget, 0));
                assertEquals(0, RotorWashLook.debrisCount(surface, 1, budget, 3));
                assertTrue(RotorWashLook.debrisCount(surface, 1, budget, 0) <= 2);
            }
            assertEquals(0, RotorWashLook.debrisCount(RotorWashLook.Surface.SOLID, 1, budget, 0));
            assertTrue(VehicleAtmosphereLook.casingLimit(budget) <= 192);
            assertTrue(VehicleAtmosphereLook.casingsPerTick(budget) <= 12);
        }
        assertFalse(WeaponGroundEffects.gun("rocket"));
        assertFalse(WeaponGroundEffects.gun("bomb"));
        assertTrue(WeaponGroundEffects.gun("machinegun2"));
        assertEquals(0, WeaponGroundEffects.falloff(0, -5, 0, 3));
        assertTrue(WeaponGroundEffects.falloff(0, -1, 0, 3) > WeaponGroundEffects.falloff(2, -1, 0, 3));
    }
}
