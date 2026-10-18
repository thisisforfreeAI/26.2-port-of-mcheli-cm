package io.github.mcheliceport.client.particle;

import static org.junit.jupiter.api.Assertions.*;
import io.github.mcheliceport.config.ClientConfig.EffectBudget;
import io.github.mcheliceport.content.ContentLocation;
import io.github.mcheliceport.content.aircraft.HeliInfo;
import net.minecraft.world.phys.Vec3;
import org.junit.jupiter.api.Test;

class RotorWashLookTest {
    @Test
    void hoverWashFadesWithAltitudePowerAndForwardSpeed() {
        assertEquals(0, RotorWashLook.strength(4, 1, 0.1, 0));
        assertEquals(0, RotorWashLook.strength(30, 1, 1, 0));
        assertTrue(RotorWashLook.strength(4, 1, 1, 0) > RotorWashLook.strength(12, 1, 1, 0));
        assertTrue(RotorWashLook.strength(4, 1, 1, 0) > RotorWashLook.strength(4, 1, 1, 1));
        assertEquals(0, RotorWashLook.strength(Double.NaN, 1, 1, 0));
        assertEquals(0, RotorWashLook.scale(Float.NaN));
        assertEquals(0, RotorWashLook.scale(0));
        assertEquals(2, RotorWashLook.scale(Float.MAX_VALUE));
    }

    @Test
    void densityAndLifetimesAreSoftButBounded() {
        assertTrue(RotorWashLook.Surface.GRASS.opacity < RotorWashLook.Surface.SAND.opacity);
        assertTrue(RotorWashLook.Surface.SOLID.density < RotorWashLook.Surface.SNOW.density);
        for (EffectBudget budget : EffectBudget.values()) {
            assertTrue(RotorWashLook.candidates(budget) <= 2);
            assertTrue(RotorWashLook.period(budget, 3) > RotorWashLook.period(budget, 0));
            assertTrue(RotorWashLook.range(budget) <= 96);
        }
        assertEquals(0, RotorWashLook.fade(0, 30));
        assertEquals(1, RotorWashLook.fade(10, 30));
        assertEquals(0, RotorWashLook.fade(30, 30));
        assertTrue(RotorWashLook.puffRadius(2) <= 1.8F);
    }

    @Test
    void tandemRotorsRemainSeparateWhileCoaxialAndTailRotorsDoNotDuplicateWash() {
        var info = new HeliInfo(new ContentLocation("test", "heli"), "heli", "test");
        info.rotorList.add(new HeliInfo.Rotor(4, 90, 0, 3, -4, 0, 1, 0, "main", false, false));
        info.rotorList.add(new HeliInfo.Rotor(4, 90, 0, 4, -4, 0, -1, 0, "coaxial", false, false));
        info.rotorList.add(new HeliInfo.Rotor(4, 90, 0, 1, -7, 1, 0, 0, "tail", false, false));
        info.rotorList.add(new HeliInfo.Rotor(4, 90, 0, 3, 4, 0, 1, 0, "second", false, false));
        var positions = RotorWashEmitter.liftOrigins(info, new Vec3(10, 10, 10), 90, 0, 0);
        assertEquals(2, positions.size());
        assertEquals(13, positions.getFirst().y);
        assertEquals(8, Math.abs(positions.getFirst().x - positions.getLast().x), 1e-5);
        info.rotorList.clear();
        assertEquals(new Vec3(0, 2, 0), RotorWashEmitter.liftOrigins(info, Vec3.ZERO, 0, 0, 0).getFirst());
    }

}
