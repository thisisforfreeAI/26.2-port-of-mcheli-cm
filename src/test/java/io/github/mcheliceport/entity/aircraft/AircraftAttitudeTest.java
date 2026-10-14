package io.github.mcheliceport.entity.aircraft;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.github.mcheliceport.util.McheliMath;
import org.joml.Quaternionf;
import org.jspecify.annotations.Nullable;
import org.junit.jupiter.api.Test;

/** Exit criterion E3: mouse turning is frame-rate independent, wraps and limits like upstream. */
class AircraftAttitudeTest {

    static final class Body implements AircraftAttitude.Rotatable {
        float yaw;
        float pitch;
        float roll;
        @Nullable Quaternionf orientation;

        @Override
        public float getYaw() {
            return this.yaw;
        }

        @Override
        public void setYaw(float yaw) {
            this.yaw = yaw;
        }

        @Override
        public float getPitch() {
            return this.pitch;
        }

        @Override
        public void setPitch(float pitch) {
            this.pitch = pitch;
        }

        @Override
        public float getRoll() {
            return this.roll;
        }

        @Override
        public void setRoll(float roll) {
            this.roll = roll;
        }

        @Override
        public @Nullable Quaternionf getOrientation() {
            return this.orientation;
        }

        @Override
        public void setOrientation(@Nullable Quaternionf orientation) {
            this.orientation = orientation;
        }
    }

    /** Turns for one second at the given frame rate with a constant stick; returns the body. */
    private static Body turnForOneSecond(int fps, boolean quaternion, float yawIn, float pitchIn, float rollIn) {
        Body b = new Body();
        float dt = 1.0F / fps;
        for (int i = 0; i < fps; i++) {
            float[] d = AircraftAttitude.controlDelta(false, true, true, true, yawIn, pitchIn, rollIn, 40, 40, 40,
                    1.0F, 1.0F, 1.0F, dt);
            AircraftAttitude.apply(b, d[0], d[1], d[2], dt, quaternion, AircraftAttitude.Limits.NONE, s -> {
            });
        }
        return b;
    }

    @Test
    void controlDeltaScalesWithFrameTimeAndClamps() {
        float[] d = AircraftAttitude.controlDelta(false, true, true, true, 100, -100, 20, 40, 30, 50, 2.0F, 3.0F,
                0.5F, 0.05F);
        assertEquals(40 * 2.0F * 0.05F, d[0], 1.0E-6);
        assertEquals(30 * 3.0F * 0.05F, d[1], 1.0E-6);
        assertEquals(20 * 0.5F * 0.05F, d[2], 1.0E-6);
        float[] free = AircraftAttitude.controlDelta(true, true, true, true, 10, 10, 10, 40, 40, 40, 1, 1, 1, 0.05F);
        assertEquals(0.0F, free[0] + free[1] + free[2]);
        float[] none = AircraftAttitude.controlDelta(false, false, false, false, 10, 10, 10, 40, 40, 40, 1, 1, 1,
                0.05F);
        assertEquals(0.0F, Math.abs(none[0]) + Math.abs(none[1]) + Math.abs(none[2]));
    }

    @Test
    void turnRateDoesNotDependOnFrameRate() {
        for (boolean quaternion : new boolean[] {false, true}) {
            Body at30 = turnForOneSecond(30, quaternion, 20.0F, 0.0F, 0.0F);
            Body at60 = turnForOneSecond(60, quaternion, 20.0F, 0.0F, 0.0F);
            Body at144 = turnForOneSecond(144, quaternion, 20.0F, 0.0F, 0.0F);
            assertEquals(Math.abs(at30.yaw), 20.0F, 0.05F, "yaw turned by the stick per second");
            assertEquals(at30.yaw, at60.yaw, 0.05F);
            assertEquals(at30.yaw, at144.yaw, 0.05F);

            Body p30 = turnForOneSecond(30, quaternion, 0.0F, 25.0F, 0.0F);
            Body p144 = turnForOneSecond(144, quaternion, 0.0F, 25.0F, 0.0F);
            assertEquals(25.0F, Math.abs(p30.pitch), 0.05F);
            assertEquals(p30.pitch, p144.pitch, 0.05F);
        }
    }

    @Test
    void quaternionPathFliesThroughVertical() {
        Body b = new Body();
        float dt = 1.0F / 60.0F;
        float maxAbsPitch = 0.0F;
        for (int i = 0; i < 240; i++) {
            float[] d = AircraftAttitude.controlDelta(false, true, true, true, 0, 40, 0, 40, 40, 40, 1, 1, 1, dt);
            AircraftAttitude.apply(b, d[0], d[1], d[2], dt, true, AircraftAttitude.Limits.NONE, s -> {
            });
            assertFalse(Float.isNaN(b.yaw) || Float.isNaN(b.pitch) || Float.isNaN(b.roll), "frame " + i);
            assertTrue(Math.abs(b.pitch) <= 90.0F + 1.0E-3F, "pitch stays within ±90: " + b.pitch);
            assertTrue(b.roll >= -180.0F && b.roll <= 180.0F);
            maxAbsPitch = Math.max(maxAbsPitch, Math.abs(b.pitch));
        }
        // 160 degrees of looping: past vertical the vehicle is upside down and heading the other way.
        assertTrue(maxAbsPitch > 85.0F);
        assertEquals(180.0F, Math.abs(b.roll), 1.0F);
    }

    @Test
    void rollWrapsIntoHalfTurn() {
        for (boolean quaternion : new boolean[] {false, true}) {
            Body b = turnForOneSecond(60, quaternion, 0.0F, 0.0F, 40.0F);
            Body more = new Body();
            float dt = 1.0F / 60.0F;
            for (int i = 0; i < 600; i++) {
                float[] d = AircraftAttitude.controlDelta(false, true, true, true, 0, 0, 40, 40, 40, 40, 1, 1, 1, dt);
                AircraftAttitude.apply(more, d[0], d[1], d[2], dt, quaternion, AircraftAttitude.Limits.NONE, s -> {
                });
                assertTrue(more.roll > -180.0F - 1.0E-3F && more.roll <= 180.0F + 1.0E-3F, "roll " + more.roll);
            }
            assertEquals(40.0F, Math.abs(b.roll), 0.05F);
        }
    }

    @Test
    void contentLimitsClampPitchAndRoll() {
        AircraftAttitude.Limits limits = new AircraftAttitude.Limits(true, -20.0F, 15.0F, -30.0F, 25.0F);
        for (boolean quaternion : new boolean[] {false, true}) {
            Body b = new Body();
            for (int i = 0; i < 200; i++) {
                AircraftAttitude.apply(b, 0.0F, 2.0F, 2.0F, 0.05F, quaternion, limits, s -> {
                });
                assertTrue(b.pitch >= -20.0F - 1.0E-3F && b.pitch <= 15.0F + 1.0E-3F, "pitch " + b.pitch);
                assertTrue(b.roll >= -30.0F - 1.0E-3F && b.roll <= 25.0F + 1.0E-3F, "roll " + b.roll);
            }
        }
    }

    @Test
    void updaterChangesAreKeptOnTheQuaternionPath() {
        Body b = new Body();
        b.pitch = 20.0F;
        AircraftAttitude.apply(b, 0.0F, 0.0F, 0.0F, 0.05F, true, AircraftAttitude.Limits.NONE,
                s -> b.setPitch(b.getPitch() * 0.5F));
        assertEquals(10.0F, b.pitch, 1.0E-3F);
        AircraftAttitude.apply(b, 0.0F, 0.0F, 0.0F, 0.05F, true, AircraftAttitude.Limits.NONE, s -> {
        });
        assertEquals(10.0F, b.pitch, 1.0E-3F, "the quaternion carries the levelled pitch");

        b.setPitch(-5.0F);
        AircraftAttitude.resyncOrientation(b);
        AircraftAttitude.apply(b, 0.0F, 0.0F, 0.0F, 0.05F, true, AircraftAttitude.Limits.NONE, s -> {
        });
        assertEquals(-5.0F, b.pitch, 1.0E-3F);
    }

    @Test
    void eulerHelpersInvertTheirQuaternions() {
        float[][] cases = {{0, 0, 0}, {45, 10, -20}, {-135, -60, 170}, {179, 80, 5}, {-30, -89, 30}};
        for (float[] c : cases) {
            float[] world = McheliMath.worldEuler(McheliMath.worldOrientationQuat(c[0], c[1], c[2]));
            assertEquals(c[1], world[0], 1.0E-2F, "pitch");
            assertEquals(c[0], world[1], 1.0E-2F, "yaw");
            assertEquals(c[2], world[2], 1.0E-2F, "roll");

            Quaternionf q = new Quaternionf()
                    .rotateZ(c[2] * ((float) Math.PI / 180.0F))
                    .rotateX(c[1] * ((float) Math.PI / 180.0F))
                    .rotateY(c[0] * ((float) Math.PI / 180.0F));
            float[] e = McheliMath.eulerFromOrientationQuat(q);
            Quaternionf back = new Quaternionf()
                    .rotateZ(e[2] * ((float) Math.PI / 180.0F))
                    .rotateX(e[0] * ((float) Math.PI / 180.0F))
                    .rotateY(e[1] * ((float) Math.PI / 180.0F));
            assertEquals(1.0F, Math.abs(q.dot(back)), 1.0E-3F, "same rotation for " + c[0] + "," + c[1] + "," + c[2]);
        }
    }
}
