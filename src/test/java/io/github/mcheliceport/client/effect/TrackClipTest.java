package io.github.mcheliceport.client.effect;

import static org.junit.jupiter.api.Assertions.*;
import org.junit.jupiter.api.Test;

class TrackClipTest {
    @Test void RotatedFootprintsPartitionAcrossBlocksWithoutBridgingOrLosingUvContinuity() {
        for (int angle = 0; angle < 360; angle += 7) for (double x : new double[]{-12.3, 0.95, 29_999_990.2}) {
            var polygon = TrackClip.rectangle(x, -0.8, Math.sin(Math.toRadians(angle)), Math.cos(Math.toRadians(angle)), 1.2, 0.45);
            double area = 0;
            int base = (int) Math.floor(x);
            for (int bx = base - 1; bx <= base + 1; bx++) for (int bz = -2; bz <= 0; bz++) {
                var clipped = TrackClip.clip(polygon, bx, bz, bx + 1, bz + 1);
                area += Math.abs(TrackClip.area(clipped));
                for (var p : clipped) {
                    assertTrue(p.x() >= bx - 1.0E-7 && p.x() <= bx + 1 + 1.0E-7);
                    assertTrue(p.z() >= bz - 1.0E-7 && p.z() <= bz + 1 + 1.0E-7);
                    assertTrue(p.u() >= -0.00001 && p.u() <= 1.00001 && p.v() >= -0.00001 && p.v() <= 1.00001);
                }
                assertTrue(TrackClip.quads(clipped) <= 6);
            }
            assertEquals(1.2 * 0.45, area, 1.0E-7);
        }
    }
    @Test void DegenerateOrDisjointFacesProduceNoGeometry() {
        assertTrue(TrackClip.rectangle(0, 0, 0, 0, 1, 1).isEmpty());
        assertTrue(TrackClip.rectangle(Double.NaN, 0, 0, 1, 1, 1).isEmpty());
        var polygon = TrackClip.rectangle(0, 0, 0, 1, 1, 1);
        assertTrue(TrackClip.clip(polygon, 1, 1, 2, 2).isEmpty());
        assertTrue(TrackClip.clip(polygon, 0.5, -1, 1, 1).isEmpty());
    }
}
