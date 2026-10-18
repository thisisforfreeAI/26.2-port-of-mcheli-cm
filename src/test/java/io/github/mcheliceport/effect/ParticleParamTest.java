package io.github.mcheliceport.effect;

import static org.junit.jupiter.api.Assertions.*;
import org.junit.jupiter.api.Test;

class ParticleParamTest {
    @Test
    void damageHeatOnlyBeginsBelowThirtyPercentAndHandlesInvalidHealth() {
        ParticleParam p = new ParticleParam("smoke", 0, 0, 0);
        assertEquals(0, p.damageHeat);
        p.setDamageHeat(30, 100);
        assertEquals(0, p.damageHeat);
        p.setDamageHeat(20, 100);
        assertEquals(0.5F, p.damageHeat, 1.0E-6);
        p.setDamageHeat(10, 100);
        assertEquals(1, p.damageHeat);
        p.setDamageHeat(-10, 100);
        assertEquals(1, p.damageHeat);
        p.setDamageHeat(20, 0);
        assertEquals(0, p.damageHeat);
        p.setDamageHeat(100, 100);
        assertEquals(0, p.damageHeat);
    }
}
