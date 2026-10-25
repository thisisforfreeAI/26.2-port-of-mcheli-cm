package io.github.mcheliceport.combat;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

/** M9 lag compensation: the pose history rewinds a vehicle to where a lagging shooter saw it. */
class PoseHistoryTest {

    /** A jet flying +x at 3.5 blocks a tick, about 250 km/h. */
    private static PoseHistory jet(long from, long to) {
        PoseHistory h = new PoseHistory();
        for (long t = from; t <= to; t++) {
            h.record(t, 3.5 * t, 80.0, 0.0);
        }
        return h;
    }

    @Test
    void emptyHistoryHasNoPositionAndNoShift() {
        PoseHistory h = new PoseHistory();
        double[] out = new double[3];
        assertFalse(h.positionAt(10, out));
        h.displacement(10, 5, 1, 2, 3, out);
        assertArrayEquals(new double[] {0, 0, 0}, out);
    }

    @Test
    void wholeAndFractionalTicksInterpolate() {
        PoseHistory h = jet(0, 10);
        double[] out = new double[3];
        assertTrue(h.positionAt(7, out));
        assertEquals(24.5, out[0], 1e-9);
        assertTrue(h.positionAt(7.5, out));
        assertEquals(26.25, out[0], 1e-9);
        assertEquals(80.0, out[1], 1e-9);
    }

    @Test
    void displacementMovesCurrentBoxesBackAlongTheFlight() {
        PoseHistory h = jet(0, 100);
        double[] out = new double[3];
        h.displacement(100, 5, 350.0, 80.0, 0.0, out);
        assertEquals(-17.5, out[0], 1e-9);
        assertEquals(0.0, out[1], 1e-9);
        h.displacement(100, 0, 350.0, 80.0, 0.0, out);
        assertEquals(0.0, out[0], 1e-9);
    }

    @Test
    void keepsOneSecondAndClampsOlderRequests() {
        PoseHistory h = jet(0, 100);
        assertEquals(PoseHistory.SIZE, h.size());
        double[] out = new double[3];
        assertTrue(h.positionAt(0, out));
        assertEquals(3.5 * (100 - PoseHistory.SIZE + 1), out[0], 1e-9);
        assertTrue(h.positionAt(500, out));
        assertEquals(350.0, out[0], 1e-9);
    }

    @Test
    void sameTickReplacesAndAClockJumpBackStartsOver() {
        PoseHistory h = jet(0, 5);
        h.record(5, 0.0, 0.0, 0.0);
        assertEquals(6, h.size());
        double[] out = new double[3];
        h.positionAt(5, out);
        assertEquals(0.0, out[0], 1e-9);
        h.record(2, 9.0, 9.0, 9.0);
        assertEquals(1, h.size());
    }

    @Test
    void skippedTicksStillInterpolate() {
        PoseHistory h = new PoseHistory();
        h.record(10, 0, 0, 0);
        h.record(14, 8, 0, 0);
        double[] out = new double[3];
        h.positionAt(11, out);
        assertEquals(2.0, out[0], 1e-9);
    }

    @Test
    void rewindIsRoundTripPlusInterpolationCapped() {
        assertEquals(0, LagCompensation.rewindTicks(0, 250));
        assertEquals(0, LagCompensation.rewindTicks(150, 0));
        assertEquals(3, LagCompensation.rewindTicks(40, 250));
        assertEquals(5, LagCompensation.rewindTicks(150, 250));
        assertEquals(5, LagCompensation.rewindTicks(900, 250));
        assertEquals(10, LagCompensation.rewindTicks(900, 500));
    }
}
