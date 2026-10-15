package io.github.mcheliceport.entity.plane;

import static org.junit.jupiter.api.Assertions.*;

import java.util.function.Consumer;
import org.joml.Quaternionf;
import org.junit.jupiter.api.Test;

/** Runs the port alongside the pinned upstream movement step, with the same collision response. */
class PlaneFlightModelTest {
    static final PlaneFlightModel.Params PARAMS = new PlaneFlightModel.Params(
            0.9F, 1.4F, true, -0.04F, -0.04F, 0.96F, 3.0F, 1.0F, true, 0.1F, 2.0F, true, -0.6F);

    static class State implements PlaneFlightModel.Body {
        double x, y = 100.0, z;
        double motionX, motionY, motionZ;
        double throttle = 0.8, currentSpeed = 0.07, depth;
        float yaw, pitch, roll, back, nozzle, wing;
        boolean grounded, water, groundSolid, nearbyGround, gunner, floating, drone, fuel = true, destroyed;
        PlaneFlightModel.Params p = PARAMS;
        @Override public double motionX() { return motionX; }
        @Override public double motionY() { return motionY; }
        @Override public double motionZ() { return motionZ; }
        @Override public void setMotion(double x, double y, double z) { motionX = x; motionY = y; motionZ = z; }
        @Override public double currentThrottle() { return throttle; }
        @Override public void setCurrentThrottle(double value) { throttle = value; }
        @Override public float throttleBack() { return back; }
        @Override public void setThrottleBack(float value) { back = value; }
        @Override public double currentSpeed() { return currentSpeed; }
        @Override public void setCurrentSpeed(double value) { currentSpeed = value; }
        @Override public boolean onGround() { return grounded; }
        @Override public boolean inWater() { return water; }
        @Override public double waterDepth() { return depth; }
        @Override public boolean groundIsSolid() { return groundSolid; }
        @Override public boolean blockWithin(int size, int depth) { return nearbyGround && y < -depth; }
        @Override public void move(double dx, double dy, double dz) {
            x += dx; y += dy; z += dz;
            if (nearbyGround && y <= 0.0 && dy <= 0.0) {
                y = 0.0; motionY = 0.0; grounded = true;
            } else { grounded = false; }
        }
        @Override public float getYaw() { return yaw; }
        @Override public void setYaw(float value) { yaw = value; }
        @Override public float getPitch() { return pitch; }
        @Override public void setPitch(float value) { pitch = value; }
        @Override public float getRoll() { return roll; }
        @Override public void setRoll(float value) { roll = value; }
        @Override public Quaternionf getOrientation() { return null; }
        @Override public void setOrientation(Quaternionf value) { }
    }

    private void compare(Consumer<State> configure) {
        State port = new State();
        UpstreamPlaneOracle upstream = new UpstreamPlaneOracle();
        configure.accept(port); configure.accept(upstream);
        for (int tick = 0; tick < 300; tick++) {
                PlaneFlightModel.physics(port, port.p, port.nozzle, port.wing, port.gunner,
                        port.floating, port.drone && port.fuel && !port.destroyed);
                upstream.physics();
                assertArrayEquals(new double[] {upstream.x, upstream.y, upstream.z, upstream.motionX,
                                upstream.motionY, upstream.motionZ, upstream.currentSpeed, upstream.yaw, upstream.pitch, upstream.roll},
                        new double[] {port.x, port.y, port.z, port.motionX, port.motionY, port.motionZ,
                                port.currentSpeed, port.yaw, port.pitch, port.roll},
                        1.0E-9, "tick " + tick);
        }
    }

    @Test void cruiseMatchesUpstream() { compare(s -> { s.yaw = 28.0F; s.pitch = -8.0F; }); }
    @Test void fullVtolMatchesUpstream() { compare(s -> { s.nozzle = 90.0F; s.throttle = 1.0; }); }
    @Test void transitionMatchesUpstream() { compare(s -> { s.nozzle = 35.0F; s.pitch = 10.0F; }); }
    @Test void reverseMatchesUpstream() { compare(s -> { s.back = 0.4F; s.yaw = -75.0F; }); }
    @Test void sweptWingLimitMatchesUpstream() { compare(s -> { s.wing = 0.75F; s.motionZ = 8.0; }); }
    @Test void gunnerAutopilotMatchesUpstream() { compare(s -> { s.gunner = true; s.motionY = -0.7; }); }
    @Test void halfSubmergedMatchesUpstream() { compare(s -> { s.floating = true; s.water = true; s.depth = 0.6; s.pitch = 10; s.roll = 25; }); }
    @Test void fullySubmergedMatchesUpstream() { compare(s -> { s.floating = true; s.water = true; s.depth = 1.0; s.motionY = -1.0; }); }
    @Test void landingMatchesUpstream() { compare(s -> { s.nearbyGround = true; s.throttle = 0; s.y = 8; s.motionZ = 0.7; }); }
    @Test void droneAtAltitudeMatchesUpstream() { compare(s -> { s.drone = true; s.motionY = -0.1; s.pitch = 15; }); }
    @Test void droneNearTerrainMatchesUpstream() { compare(s -> { s.drone = true; s.nearbyGround = true; s.y = 20; }); }
    @Test void destroyedDroneMatchesUpstream() { compare(s -> { s.drone = true; s.destroyed = true; s.throttle = 0; }); }

    @Test void throttleAndReverseFollowUpstreamRates() {
        State s = new State(); s.throttle = 0.0;
        PlaneFlightModel.control(s, PARAMS, new PlaneFlightModel.Input(true, false, false, false), false, false, -1);
        assertEquals(0.01, s.throttle, 1.0E-12);
        s.throttle = 0.0;
        PlaneFlightModel.control(s, PARAMS, new PlaneFlightModel.Input(false, true, false, false), false, false, -1);
        assertEquals(0.0025F, s.back, 1.0E-9);
        s.throttle = 0.0; s.back = 0.0F;
        PlaneFlightModel.control(s, PARAMS, new PlaneFlightModel.Input(false, false, true, false), false, false, -1);
        assertEquals(0.02, s.throttle, 1.0E-12, "pivot turns raise throttle at twice the normal rate");
    }

    @Test void carrierAndIdleAndGunnerControls() {
        State s = new State(); s.throttle = 0.2;
        PlaneFlightModel.control(s, PARAMS, new PlaneFlightModel.Input(true, false, false, false), false, false, 0.5);
        assertEquals(0.21, s.throttle, 1.0E-12);
        PlaneFlightModel.control(s, PARAMS, PlaneFlightModel.Input.NONE, false, true, -1);
        assertEquals(0.205, s.throttle, 1.0E-12);
        PlaneFlightModel.control(s, PARAMS, new PlaneFlightModel.Input(true, false, false, false), true, false, -1);
        assertEquals(0.205, s.throttle, 1.0E-12, "gunner mode holds throttle");
        PlaneFlightModel.idle(s, PARAMS);
        assertEquals(0.2025, s.throttle, 1.0E-12);
    }
}
