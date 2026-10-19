package io.github.mcheliceport.client.render;

import static org.junit.jupiter.api.Assertions.*;
import org.junit.jupiter.api.Test;

class TracerLookTest {
    @Test void beltSamplingStaysSparseAndExposureDoesNotTurnIntoALongBeam() {
        int selected = 0;
        for (long i = 0; i < 64; i++) if (TracerLook.selected(i)) selected++;
        assertEquals(16, selected);
        assertEquals(2.8, TracerLook.length(8), 0.00001);
        assertEquals(3, TracerLook.length(Double.MAX_VALUE));
        assertEquals(0, TracerLook.length(Double.NaN));
        assertEquals(0, TracerLook.length(Double.POSITIVE_INFINITY));
        assertEquals(0, TracerLook.length(-1));
        assertEquals(0, TracerLook.length(0));
        assertEquals(0.14F, TracerLook.width(10000));
        assertEquals(0, TracerLook.width(Double.NaN));
        assertEquals(0, TracerLook.width(-1));
        assertTrue(TracerLook.width(64) > TracerLook.width(8));
    }

    @Test void DaylightRearVisibilityAndDefinitionTransparencyRemainControlled() {
        assertTrue(TracerLook.alpha(0, -1) > TracerLook.alpha(11, -1));
        assertEquals(0, TracerLook.alpha(0, 0x00FF0000));
        assertEquals(TracerLook.alpha(0, -1) * 128 / 255, TracerLook.alpha(0, 0x80FFFFFF), 0.00001F);
        assertEquals(1, TracerLook.viewFactor(1));
        assertEquals(0.8F, TracerLook.viewFactor(0));
        assertEquals(0.4F, TracerLook.viewFactor(-1));
        assertEquals(0, TracerLook.viewFactor(Double.NaN));
    }

    @Test void ExplicitTracerHuesSurviveAndNeutralRoundsGetAWarmBurningBase() {
        assertEquals(0xFFAC43, TracerLook.color(0xFFFFFFFF));
        assertEquals(0xFF2200, TracerLook.color(0xFFFF2200));
        assertEquals(0x22FF22, TracerLook.color(0xFF22FF22));
        int bright = TracerLook.coreColor(TracerLook.color(-1));
        assertTrue((bright >> 8 & 255) > 172);
        assertTrue((bright & 255) > 67);
    }
}
