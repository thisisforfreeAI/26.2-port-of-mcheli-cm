package io.github.mcheliceport.entity.ground;

import static org.junit.jupiter.api.Assertions.*;
import io.github.mcheliceport.content.ContentLocation;
import io.github.mcheliceport.content.aircraft.TankInfo;
import io.github.mcheliceport.content.aircraft.VehicleInfo;
import io.github.mcheliceport.entity.plane.PlaneFlightModel;
import org.joml.Quaternionf;
import org.junit.jupiter.api.Test;

class GroundMotionTest {
    /** Flat collision plane: one move per tick, vertical velocity stopped by contact. */
    static class Body implements PlaneFlightModel.Body {
        double x, y, z, mx, my, mz, throttle = 0.7, speed = 0.07;
        float yaw, pitch, roll, back;
        int moves;
        @Override public double motionX() { return mx; }
        @Override public double motionY() { return my; }
        @Override public double motionZ() { return mz; }
        @Override public void setMotion(double x, double y, double z) { mx = x; my = y; mz = z; }
        @Override public double currentThrottle() { return throttle; }
        @Override public void setCurrentThrottle(double v) { throttle = v; }
        @Override public float throttleBack() { return back; }
        @Override public void setThrottleBack(float v) { back = v; }
        @Override public double currentSpeed() { return speed; }
        @Override public void setCurrentSpeed(double v) { speed = v; }
        @Override public boolean onGround() { return true; }
        @Override public boolean inWater() { return false; }
        @Override public double waterDepth() { return 0; }
        @Override public boolean blockWithin(int s, int d) { return true; }
        @Override public boolean groundIsSolid() { return true; }
        @Override public void move(double dx, double dy, double dz) { moves++; x += dx; y = Math.max(0, y + dy); z += dz; my = 0; }
        @Override public float getYaw() { return yaw; }
        @Override public void setYaw(float v) { yaw = v; }
        @Override public float getPitch() { return pitch; }
        @Override public void setPitch(float v) { pitch = v; }
        @Override public float getRoll() { return roll; }
        @Override public void setRoll(float v) { roll = v; }
        @Override public Quaternionf getOrientation() { return null; }
        @Override public void setOrientation(Quaternionf q) { }
    }

    @Test void trackedAndWheeledFlatTrajectoriesMatchUpstreamAndMoveOnce() {
        for (int weight : new int[] {1, 2}) {
            TankInfo info = new TankInfo(ContentLocation.builtin("test"), "test.yml", "yaml");
            info.weightType = weight; info.motionFactor = 0.9F; info.speed = 0.8F; info.gravity = -0.1F;
            Body b = new Body();
            double velocity = 0, position = 0;
            // Transcription reduced to upstream's flat, yaw-zero, grounded case:
            // thrust -> cap -> first grip -> move -> second grip.
            for (int tick = 0; tick < 300; tick++) {
                velocity = Math.min(info.speed, velocity + (float) (0.7 / 10));
                velocity *= weight == 1 ? 0.985 : info.motionFactor;
                position += velocity;
                velocity *= weight == 1 ? 0.992 : info.motionFactor;
                GroundMotion.tankPhysics(b, info, false, () -> { });
                assertEquals(position, b.z, 1.0E-9, "position at tick " + tick);
                assertEquals(velocity, b.mz, 1.0E-9, "velocity at tick " + tick);
                assertEquals(tick + 1, b.moves);
            }
        }
    }

    @Test void stationaryDefinitionsRefuseKeyboardMovementAndRotation() {
        VehicleInfo info = new VehicleInfo(ContentLocation.builtin("test"), "test.yml", "yaml");
        Body b = new Body(); b.throttle = 0;
        for (int i = 0; i < 300; i++) { GroundMotion.vehiclePhysics(b, info, true, false, 1, true, false); }
        assertEquals(0, b.x); assertEquals(0, b.z); assertEquals(0, b.yaw);
    }

    @Test void brakingDropsThrottleAndReverseAndAutoDownIsOptional() {
        TankInfo info = new TankInfo(ContentLocation.builtin("test"), "test.yml", "yaml");
        info.enableBack = true; Body b = new Body(); b.back = 0.1F;
        GroundMotion.tankControl(b, info, false, false, true, true, false);
        assertEquals(0.68, b.throttle, 1.0E-7); assertEquals(0.04, b.back, 1.0E-7);
        b.throttle = 0.5;
        GroundMotion.tankControl(b, info, false, false, false, true, false); assertEquals(0.5, b.throttle);
        GroundMotion.tankControl(b, info, false, false, false, true, true); assertEquals(0.495, b.throttle, 1.0E-7);
        b.throttle = 0; b.back = 0;
        for (int i = 0; i < 30; i++) { GroundMotion.tankControl(b, info, false, true, false, true, false); }
        assertTrue(b.back > 0); assertEquals(0, b.throttle);
    }

    @Test void crawlerPivotReverseAndWrapMatchPinnedSequence() {
        TankInfo info = new TankInfo(ContentLocation.builtin("test"), "test.yml", "yaml");
        info.enableBack = true; info.trackRollerRot = 30; info.partWheelRot = 30;
        GroundAnimation a = new GroundAnimation();
        a.tick(info, 1, 0, false, false);
        assertEquals(30, a.wheel); assertEquals(6, a.roller[0], 1.0E-6); assertEquals(0.8, a.track[0], 1.0E-6);
        a = new GroundAnimation(); a.tick(info, 0, 0, true, false);
        assertEquals(6, a.roller[0], 1.0E-6); assertEquals(354, a.roller[1], 1.0E-6);
        a = new GroundAnimation(); a.tick(info, 0, 0.1F, true, false);
        assertEquals(315, a.wheel, 1.0E-5); assertEquals(6, a.roller[0], 1.0E-6); assertEquals(354, a.roller[1], 1.0E-6);
    }
}
