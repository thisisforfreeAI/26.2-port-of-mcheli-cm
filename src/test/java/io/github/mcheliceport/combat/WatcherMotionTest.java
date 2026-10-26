package io.github.mcheliceport.combat;

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
}
