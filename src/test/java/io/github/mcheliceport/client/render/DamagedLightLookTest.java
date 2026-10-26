package io.github.mcheliceport.client.render;

import static org.junit.jupiter.api.Assertions.*;
import io.github.mcheliceport.entity.aircraft.DamagedLightLook;
import org.junit.jupiter.api.Test;

class DamagedLightLookTest {
    @Test void healthyAndRepairedLightsStaySteadyAndWreckLightsFail() {
        for (int tick=0; tick<100; tick++) {
            assertEquals(1, DamagedLightLook.intensity(.2F, false, 7, 1, tick));
            assertEquals(0, DamagedLightLook.intensity(1, true, 7, 1, tick));
        }
    }
    @Test void DamageFaultsAreBoundedRepeatableAndComponentSpecific() {
        boolean dark=false, lit=false, different=false;
        for (int tick=0; tick<100; tick++) {
            float a=DamagedLightLook.intensity(.9F, false, 7, 0, tick);
            assertEquals(a, DamagedLightLook.intensity(.9F, false, 7, 0, tick));
            assertTrue(a>=0&&a<=1); dark|=a==0; lit|=a>0;
            different|=a!=DamagedLightLook.intensity(.9F, false, 7, 1, tick);
        }
        assertTrue(dark&&lit&&different);
    }
}
