package io.github.mcheliceport.client.particle;

import static org.junit.jupiter.api.Assertions.*;
import io.github.mcheliceport.config.ClientConfig.EffectBudget;
import org.junit.jupiter.api.Test;

class FlareLookTest {
    @Test
    void crowdedHalosStayInsideTheirWeightedViewAllowance() {
        var budget = new EffectTuning.Budget();
        int cap = FlareLook.haloBudget(EffectBudget.BALANCED, 0);
        for (int i = 0; i < cap; i++) assertTrue(budget.admit(1, cap, 1, 1));
        assertFalse(budget.admit(1, cap, 1, 1));
        assertTrue(budget.admit(2, cap, 1, 16));
        assertFalse(budget.admit(2, cap, 1, 16));
        assertEquals(12, FlareLook.haloBudget(EffectBudget.BALANCED, 1));
        assertEquals(0, FlareLook.haloBudget(EffectBudget.HIGH, 2));
        assertEquals(0, FlareLook.haloBudget(EffectBudget.LOW, 0));
    }

    @Test
    void restrainedFlickerDoesNotSynchronizeEveryFlare() {
        for (int age = 0; age < 300; age++) {
            float value = FlareLook.flicker(age, 10);
            assertTrue(value >= 0.879F && value <= 1);
        }
        assertNotEquals(FlareLook.flicker(10, 10), FlareLook.flicker(10, 11));
    }
}
