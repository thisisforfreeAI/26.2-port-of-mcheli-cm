package io.github.mcheliceport.client.input;

import static org.junit.jupiter.api.Assertions.*;

import org.junit.jupiter.api.Test;

class JoystickMappingTest {
    @Test void deadZoneAndCurveKeepTheSignAndReachFullDeflection() {
        assertEquals(0.0F, JoystickMapping.shape(0.05F, 0.1F, 1.5F));
        assertEquals(0.0F, JoystickMapping.shape(-0.1F, 0.1F, 1.5F));
        assertEquals(1.0F, JoystickMapping.shape(1.0F, 0.1F, 1.5F), 1e-6);
        assertEquals(-1.0F, JoystickMapping.shape(-1.2F, 0.1F, 2.0F), 1e-6);
        assertEquals(0.5F, JoystickMapping.shape(0.55F, 0.1F, 1.0F), 1e-6);
        assertTrue(JoystickMapping.shape(0.55F, 0.1F, 2.0F) < 0.5F, "a curve is gentler near the centre");
        assertEquals(0.0F, JoystickMapping.shape(Float.NaN, 0.1F, 1.5F));
    }

    @Test void leverForwardIsFullPowerUnlessInverted() {
        assertEquals(1.0, JoystickMapping.lever(-1.0F, false), 1e-9);
        assertEquals(0.0, JoystickMapping.lever(1.0F, false), 1e-9);
        assertEquals(0.5, JoystickMapping.lever(0.0F, false), 1e-9);
        assertEquals(1.0, JoystickMapping.lever(1.0F, true), 1e-9);
    }

    @Test void theThrottleChasesTheLeverAndSettles() {
        assertEquals(1, JoystickMapping.chaseLever(0.8, 0.5));
        assertEquals(-1, JoystickMapping.chaseLever(0.2, 0.5));
        assertEquals(0, JoystickMapping.chaseLever(0.52, 0.5));
        // The ends are exact, so idle really idles and full really is full.
        assertEquals(-1, JoystickMapping.chaseLever(0.0, 0.03));
        assertEquals(1, JoystickMapping.chaseLever(1.0, 0.97));
        assertEquals(0, JoystickMapping.chaseLever(0.0, 0.0));
        assertEquals(0, JoystickMapping.chaseLever(1.0, 1.0));
    }

    @Test void digitalThreshold() {
        assertEquals(1, JoystickMapping.digital(0.4F, 0.35F));
        assertEquals(-1, JoystickMapping.digital(-0.9F, 0.35F));
        assertEquals(0, JoystickMapping.digital(0.2F, 0.35F));
    }

    @Test void buttonBindingsParse() {
        assertEquals(new JoystickMapping.Button(5, "key.mcheli_ce_port.use_weapon"),
                JoystickMapping.parseButton("5=key.mcheli_ce_port.use_weapon"));
        assertEquals(new JoystickMapping.Button(0, "key.jump"), JoystickMapping.parseButton(" 0 = key.jump"));
        assertNull(JoystickMapping.parseButton("key.jump"));
        assertNull(JoystickMapping.parseButton("x=key.jump"));
        assertNull(JoystickMapping.parseButton("-1=key.jump"));
        assertNull(JoystickMapping.parseButton("3="));
        assertNull(JoystickMapping.parseButton("3=key jump"));
    }
}
