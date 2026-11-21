package io.github.mcheliceport.combat;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.github.mcheliceport.net.VehicleMotionPayload;
import org.junit.jupiter.api.Test;

/** M9 smoother sync: which vehicles send velocity, how long a client uses it, when it carries on. */
class WatcherMotionTest {

    @Test
    void onlyFastVehiclesSendEveryOtherTick() {
        assertTrue(WatcherMotion.shouldSend(10, 3.5 * 3.5));
        assertFalse(WatcherMotion.shouldSend(11, 3.5 * 3.5));
        assertFalse(WatcherMotion.shouldSend(10, 0.5 * 0.5));
        assertTrue(WatcherMotion.shouldSend(-4, 1.0));
    }

    @Test
    void velocityGoesStale() {
        assertTrue(WatcherMotion.fresh(100, 100));
        assertTrue(WatcherMotion.fresh(106, 100));
        assertFalse(WatcherMotion.fresh(107, 100));
        assertFalse(WatcherMotion.fresh(99, 100));
        assertFalse(WatcherMotion.fresh(100, Long.MIN_VALUE));
    }

    @Test
    void carriesOnOnlyBetweenLateUpdatesAndOnlyBriefly() {
        assertTrue(WatcherMotion.extrapolates(false, true, 0));
        assertTrue(WatcherMotion.extrapolates(false, true, WatcherMotion.MAX_EXTRAPOLATION - 1));
        assertFalse(WatcherMotion.extrapolates(false, true, WatcherMotion.MAX_EXTRAPOLATION));
        assertFalse(WatcherMotion.extrapolates(true, true, 0));
        assertFalse(WatcherMotion.extrapolates(false, false, 0));
    }

    @Test
    void payloadRejectsNonsense() {
        assertTrue(new VehicleMotionPayload(1, 3.5F, 0.0F, -1.0F).valid());
        assertFalse(new VehicleMotionPayload(1, Float.NaN, 0.0F, 0.0F).valid());
        assertFalse(new VehicleMotionPayload(1, 30.0F, 0.0F, 0.0F).valid());
        assertFalse(new VehicleMotionPayload(-1, 0.0F, 0.0F, 0.0F).valid());
    }

    @Test
    void attitudeTurnsTheShortWayAndKeepsRolling() {
        float[] a = WatcherMotion.attitudeStep(0.0F, 0.0F, 170.0F, 0.0F, 0.0F, -170.0F, 0.5F);
        assertEquals(180.0F, a[2], 0.05F, "a roll through 180 keeps going");
        a = WatcherMotion.attitudeStep(179.0F, 0.0F, 0.0F, -179.0F, 0.0F, 0.0F, 0.5F);
        assertEquals(180.0F, a[0], 0.05F, "yaw crosses 180 the short way");
        a = WatcherMotion.attitudeStep(10.0F, 5.0F, 20.0F, 40.0F, -15.0F, 60.0F, 1.0F);
        assertEquals(40.0F, a[0], 0.05F);
        assertEquals(-15.0F, a[1], 0.05F);
        assertEquals(60.0F, a[2], 0.05F);
    }

    @Test
    void aLoopGoesOverTheTopInsteadOfSwingingSideways() {
        // Pitch 80 climbing, then the same loop 20 degrees later: past the vertical it reads as
        // yaw and roll flipped by 180. Euler lerp would swing both by 90 at the midpoint.
        float[] a = WatcherMotion.attitudeStep(0.0F, 80.0F, 0.0F, 180.0F, 80.0F, 180.0F, 0.5F);
        assertTrue(Math.abs(a[1]) > 89.0F, "the midpoint is at the top of the loop: " + a[1]);
    }
}
