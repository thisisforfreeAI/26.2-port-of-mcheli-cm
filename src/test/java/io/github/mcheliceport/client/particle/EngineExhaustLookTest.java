package io.github.mcheliceport.client.particle;

import static org.junit.jupiter.api.Assertions.*;
import io.github.mcheliceport.config.ClientConfig.EffectBudget;
import io.github.mcheliceport.content.ContentLocation;
import io.github.mcheliceport.content.aircraft.AircraftInfo;
import io.github.mcheliceport.content.aircraft.HeliInfo;
import io.github.mcheliceport.content.aircraft.PlaneInfo;
import net.minecraft.world.phys.Vec3;
import org.junit.jupiter.api.Test;

class EngineExhaustLookTest {
    @Test void undeclaredHelicoptersHaveNoGuessedOutletsAndExplicitAnchorsTakePriority() {
        var heli = new HeliInfo(ContentLocation.builtin("test"), "test", "test");
        assertTrue(EngineExhaustLook.outlets(heli, 0).isEmpty());
        var plane = new PlaneInfo(ContentLocation.builtin("test"), "test", "test");
        for (int i = 0; i < 50; i++) plane.nozzles.add(new AircraftInfo.DrawnPart(i, 2, -5, 0, 1, 0, "nozzle"));
        assertEquals(4, EngineExhaustLook.outlets(plane, 0).size());
        var vtol = EngineExhaustLook.outlets(plane, 90).getFirst().direction();
        assertEquals(-1, vtol.y, 1E-6);
        assertEquals(0, vtol.z, 1E-6);
        var explicit = new AircraftInfo.EngineOutlet(new Vec3(1, 3, -4), new Vec3(1, 0, -2), 0.8F, false);
        plane.engineOutlets.add(explicit);
        assertEquals(java.util.List.of(explicit), EngineExhaustLook.outlets(plane, 90));
        assertEquals(1, explicit.direction().length(), 1E-6);
    }
    @Test void offEnginesDoNotEmitAndQualityPressureBoundsRemainSmall() {
        assertEquals(0, EngineExhaustLook.strength(0.1));
        assertEquals(0, EngineExhaustLook.strength(Double.NaN));
        assertEquals(1, EngineExhaustLook.strength(1));
        for (var quality : EffectBudget.values()) {
            assertTrue(EngineExhaustLook.period(quality, 3) > EngineExhaustLook.period(quality, 0));
            assertTrue(EngineExhaustLook.cap(quality) <= 24);
        }
        assertEquals(0, EngineExhaustLook.fade(18, 18));
        assertTrue(EngineExhaustLook.radius(4, 1) <= 1.36F);
        assertThrows(IllegalArgumentException.class, () -> new AircraftInfo.EngineOutlet(Vec3.ZERO, Vec3.ZERO, 1, false));
        assertThrows(IllegalArgumentException.class, () -> new AircraftInfo.EngineOutlet(Vec3.ZERO, new Vec3(0, 0, -1), Float.NaN, false));
    }
}
