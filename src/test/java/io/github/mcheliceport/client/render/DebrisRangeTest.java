package io.github.mcheliceport.client.render;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

class DebrisRangeTest {
    @Test void configuredRangeHoldsInsideTheRenderDistance(){assertEquals(256,DebrisRange.clamp(256,384));}
    @Test void neverPastTheLoadedTerrain(){assertEquals(128,DebrisRange.clamp(256,128));}
    @Test void neverBelowTheNearDetailRadius(){assertEquals(DebrisRange.DETAIL,DebrisRange.clamp(256,32));}
}
