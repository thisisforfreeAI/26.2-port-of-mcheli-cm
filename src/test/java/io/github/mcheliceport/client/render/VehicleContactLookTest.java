package io.github.mcheliceport.client.render;

import static org.junit.jupiter.api.Assertions.*;
import io.github.mcheliceport.config.ClientConfig.EffectBudget;
import org.junit.jupiter.api.Test;

class VehicleContactLookTest {
    @Test void footprintSizingIsBoundedAndFallsBackUnderLoad() {
        assertEquals(2, VehicleContactLook.radius(0.5F, 0, EffectBudget.BALANCED, 0));
        assertEquals(2.5F, VehicleContactLook.radius(100, 100, EffectBudget.BALANCED, 0));
        assertEquals(3, VehicleContactLook.radius(100, 100, EffectBudget.HIGH, 0));
        assertEquals(2, VehicleContactLook.radius(100, 100, EffectBudget.LOW, 0));
        for (var quality : EffectBudget.values()) for (int pressure = 1; pressure <= 3; pressure++)
            assertEquals(2, VehicleContactLook.radius(100, 100, quality, pressure));
        assertEquals(2, VehicleContactLook.radius(Float.NaN, 0, EffectBudget.HIGH, 0));
        assertEquals(2, VehicleContactLook.radius(1, Double.NaN, EffectBudget.HIGH, 0));
        assertTrue(VehicleContactLook.radius(1, 3, EffectBudget.HIGH, 0)
                > VehicleContactLook.radius(1, 0, EffectBudget.HIGH, 0));
    }
}
