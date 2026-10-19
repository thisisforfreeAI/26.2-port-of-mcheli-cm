package io.github.mcheliceport.client.render;

import static org.junit.jupiter.api.Assertions.*;
import io.github.mcheliceport.config.ClientConfig.EffectBudget;
import net.minecraft.world.phys.Vec3;
import org.joml.Matrix4f;
import org.junit.jupiter.api.Test;

class EngineHeatLookTest {
    @Test void projectionRejectsBehindCameraAndOffscreenHeatAndBoundsVisibleCapsules() {
        for (boolean zeroToOne : new boolean[]{false, true}) {
            var matrix = new Matrix4f().setPerspective((float) Math.toRadians(70), 16F / 9, 100, 0.05F, zeroToOne);
            var s = EngineHeatLook.project(matrix, new Vec3(0, 0, -10), new Vec3(1, 0, -12), 0.3F, 1, 1920, 1080, zeroToOne);
            assertNotNull(s); assertEquals(0.5F, s.ax(), 0.0001);
            assertTrue(s.radius() > 1.5 && s.radius() <= 48);
            assertTrue(s.az() >= 0 && s.az() <= 1);
            assertNull(EngineHeatLook.project(matrix, new Vec3(0, 0, 1), new Vec3(0, 0, 2), 0.3F, 1, 1920, 1080, zeroToOne));
            assertNull(EngineHeatLook.project(matrix, new Vec3(100, 0, -10), new Vec3(100, 0, -12), 0.3F, 1, 1920, 1080, zeroToOne));
            matrix.rotateZ((float) Math.PI / 2);
            var rolled = EngineHeatLook.project(matrix, new Vec3(0, 0, -10), new Vec3(0, 1, -12), 0.3F, 1, 1920, 1080, zeroToOne);
            assertNotNull(rolled); assertEquals(s.radius(), rolled.radius(), 0.001);
        }
    }
    @Test void lowAndPressureRemoveThePassAndJetVolumesRemainSmall() {
        assertFalse(EngineHeatLook.enabled(EffectBudget.LOW, 0));
        for (var q : EffectBudget.values()) for (int pressure = 1; pressure <= 3; pressure++)
            assertFalse(EngineHeatLook.enabled(q, pressure));
        assertTrue(EngineHeatLook.enabled(EffectBudget.BALANCED, 0));
        assertEquals(4, EngineHeatLook.cap(EffectBudget.BALANCED));
        assertEquals(8, EngineHeatLook.cap(EffectBudget.HIGH));
        assertTrue(EngineHeatLook.length(true, 1) > EngineHeatLook.length(false, 1));
        assertTrue(EngineHeatLook.length(true, 100) <= 4);
    }
}
