package io.github.mcheliceport.client.effect;

import static org.junit.jupiter.api.Assertions.*;
import org.junit.jupiter.api.Test;

class TrackLookTest {
    @Test void ImprintsStaySmallAndSeparateTreadBarsFromTireGrooves() {
        for (var kind : TrackMarks.Kind.values()) for (int angle = 0; angle < 360; angle += 11) {
            var p = TrackLook.pattern(10, -2, Math.sin(Math.toRadians(angle)), Math.cos(Math.toRadians(angle)), 0.7F, kind);
            assertEquals(2, p.size());
            for (var piece : p) {
                assertEquals(4, piece.size());
                assertTrue(Math.abs(TrackClip.area(piece)) > 0);
                for (var v : piece) assertTrue(Math.hypot(v.x() - 10, v.z() + 2) < 0.5);
            }
        }
        assertTrue(TrackLook.pattern(0, 0, 0, 0, 1, TrackMarks.Kind.TREAD).isEmpty());
    }
    @Test void HardSurfacesStayRestrainedAndSoftMaterialsRetainVisibleImpressions() {
        for (var kind : TrackMarks.Kind.values()) {
            assertTrue(TrackLook.opacity(TrackLook.Surface.SOIL, kind, 0.6F) > TrackLook.opacity(TrackLook.Surface.HARD, kind, 0.6F) * 3);
            assertTrue(TrackLook.opacity(TrackLook.Surface.HARD, kind, 1) > TrackLook.opacity(TrackLook.Surface.HARD, kind, 0.6F));
            for (var material : TrackLook.Surface.values()) {
                assertEquals(0, TrackLook.opacity(material, kind, Float.NaN));
                for (float strength : new float[]{0, 0.5F, 1, 2}) {
                    float a = TrackLook.opacity(material, kind, strength);
                    assertTrue(a >= 0 && a <= 0.48F);
                }
            }
        }
    }
}
