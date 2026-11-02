package io.github.mcheliceport.combat;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

/** M9 anti-cheat rules on plain values: decay, the fire-packet bucket, the fire origin and aim. */
class AntiCheatTest {

    @Test
    void scoreDecaysWithAOneMinuteTimeConstant() {
        assertEquals(10.0, AntiCheat.decay(10.0, 0), 1e-9);
        assertEquals(10.0 / Math.E, AntiCheat.decay(10.0, 1200), 1e-9);
        assertTrue(AntiCheat.decay(20.0, 20 * 60 * 5) < 0.2);
    }

    @Test
    void bucketAllowsABurstThenTwoATick() {
        AntiCheat.Record r = new AntiCheat.Record();
        for (int i = 0; i < 4; i++) {
            assertTrue(AntiCheat.takeToken(r, 100), "burst " + i);
        }
        assertFalse(AntiCheat.takeToken(r, 100));
        assertTrue(AntiCheat.takeToken(r, 101));
        assertTrue(AntiCheat.takeToken(r, 101));
        assertFalse(AntiCheat.takeToken(r, 101));
        // An honest gun firing every tick never runs dry.
        for (long t = 102; t < 400; t++) {
            assertTrue(AntiCheat.takeToken(r, t));
        }
    }

    @Test
    void originMustBeNearARecentPose() {
        PoseHistory h = new PoseHistory();
        for (long t = 0; t <= 20; t++) {
            h.record(t, 3.5 * t, 80.0, 0.0);
        }
        // Where a lagging gunner saw the jet 6 ticks ago.
        assertTrue(AntiCheat.originPlausible(h, 20, 8, 3.5, 70.0, 80.0, 0.0, 49.0, 80.0, 0.0));
        // Ahead of the jet by a tick, as a pilot's own client can be.
        assertTrue(AntiCheat.originPlausible(h, 20, 4, 3.5, 70.0, 80.0, 0.0, 73.5, 80.5, 0.0));
        // Fifteen blocks to the side: never was there.
        assertFalse(AntiCheat.originPlausible(h, 20, 8, 3.5, 70.0, 80.0, 0.0, 70.0, 80.0, 15.0));
        // Too old for this shooter's window.
        assertFalse(AntiCheat.originPlausible(h, 20, 2, 3.5, 70.0, 80.0, 0.0, 35.0, 80.0, 0.0));
    }

    @Test
    void parkedVehicleWithNoHistoryUsesItsPosition() {
        PoseHistory empty = new PoseHistory();
        assertTrue(AntiCheat.originPlausible(empty, 5, 4, 0.0, 1.0, 2.0, 3.0, 2.0, 2.0, 3.0));
        assertFalse(AntiCheat.originPlausible(empty, 5, 4, 0.0, 1.0, 2.0, 3.0, 9.0, 2.0, 3.0));
    }

    @Test
    void aimIsADirection() {
        assertTrue(AntiCheat.aimValid(170.0F, -45.0F));
        assertTrue(AntiCheat.aimValid(-720.0F, 90.0F));
        assertFalse(AntiCheat.aimValid(Float.NaN, 0.0F));
        assertFalse(AntiCheat.aimValid(0.0F, Float.POSITIVE_INFINITY));
        assertFalse(AntiCheat.aimValid(0.0F, 91.0F));
    }

    @Test
    void accelerationAllowsStopsAndRealGainsButNotJumps() {
        // A jet with a checked top speed of 6 blocks a tick may gain 1.2 a tick.
        assertTrue(AntiCheat.accelerationPlausible(3.0, 0.0, 1, 6.0), "a crash may stop it at once");
        assertTrue(AntiCheat.accelerationPlausible(3.0, 4.2, 1, 6.0));
        assertFalse(AntiCheat.accelerationPlausible(0.0, 6.0, 1, 6.0), "not zero to top speed in a tick");
        assertTrue(AntiCheat.accelerationPlausible(0.0, 6.0, 5, 6.0), "but in five ticks of lost packets");
        assertFalse(AntiCheat.accelerationPlausible(0.0, 7.0, 500, 6.0), "a long stall earns no more than five");
        // Slow vehicles still get a quarter block a tick.
        assertTrue(AntiCheat.accelerationPlausible(0.0, 0.25, 1, 0.5));
        assertFalse(AntiCheat.accelerationPlausible(0.0, 0.3, 1, 0.5));
    }
}
