package io.github.mcheliceport.entity.parachute;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import net.minecraft.world.phys.Vec3;
import org.junit.jupiter.api.Test;

class ParachuteMotionTest {
    @Test
    void seatOpensOnlyBelowTheUpstreamDescentThreshold() {
        assertFalse(ParachuteMotion.isOpen(2, 2));
        assertFalse(ParachuteMotion.isOpen(2, -0.1));
        assertTrue(ParachuteMotion.isOpen(2, Math.nextDown(-0.1)));
        assertTrue(ParachuteMotion.isOpen(1, 2));
        assertTrue(ParachuteMotion.isOpen(3, 2));
    }

    @Test
    void ejectionGravityAndDragChangeAtTickTwenty() {
        Vec3 lift = new Vec3(0.3, 1, 0.2);
        for (int age : new int[] {1, 19, 20, 21}) {
            var step = ParachuteMotion.accelerate(2, age, false, lift, 0.07, true, 0);
            assertEquals(age < 20 ? 0.99 : 0.97, step.velocity().y, 1.0E-12);
            assertEquals(0.3, step.velocity().x, 1.0E-12, "closed seat has no speed cap or steering");
            Vec3 drag = ParachuteMotion.drag(2, age, step.velocity());
            assertEquals(step.velocity().y * (age < 20 ? 0.95 : 0.9), drag.y, 1.0E-12);
            assertEquals(0.3 * 0.99, drag.x, 1.0E-12);
        }
    }

    /** Independent transcription of onUpdateServer's movement section at cbaff7f. */
    @Test
    void hundredsOfTicksMatchThePinnedSourceForAllThreeForms() {
        for (int type = 1; type <= 3; type++) {
            Vec3 actual = new Vec3(0.6, type == 2 ? 2 : -0.8, -0.4);
            double x = actual.x, y = actual.y, z = actual.z;
            double multiplier = 0.07, expectedMultiplier = 0.07;
            for (int tick = 1; tick <= 350; tick++) {
                boolean forward = tick % 40 < 25;
                boolean onGround = tick > 345;
                float yaw = tick * 3.1F;
                double oldSpeed = Math.sqrt(x * x + z * z);
                double gravity = onGround ? 0.01 : 0.03;
                if (type == 2 && tick < 20) gravity = 0.01;
                y -= gravity;
                if (type != 2 || y < -0.1) {
                    if (forward) {
                        x += -Math.sin(yaw * (float) Math.PI / 180.0F) * expectedMultiplier * 0.05;
                        z += Math.cos(yaw * (float) Math.PI / 180.0F) * expectedMultiplier * 0.05;
                    }
                    double speed = Math.sqrt(x * x + z * z);
                    if (speed > 0.35) {
                        x *= 0.35 / speed;
                        z *= 0.35 / speed;
                        speed = 0.35;
                    }
                    if (speed > oldSpeed && expectedMultiplier < 0.35) {
                        expectedMultiplier += (0.35 - expectedMultiplier) / 35.0;
                        if (expectedMultiplier > 0.35) expectedMultiplier = 0.35;
                    } else {
                        expectedMultiplier -= (expectedMultiplier - 0.07) / 35.0;
                        if (expectedMultiplier < 0.07) expectedMultiplier = 0.07;
                    }
                }
                var step = ParachuteMotion.accelerate(type, tick, onGround, actual, multiplier, forward, yaw);
                assertEquals(x, step.velocity().x, 1.0E-12, "type " + type + " tick " + tick);
                assertEquals(y, step.velocity().y, 1.0E-12);
                assertEquals(z, step.velocity().z, 1.0E-12);
                assertEquals(expectedMultiplier, step.speedMultiplier(), 1.0E-12);
                y *= type == 2 && tick < 20 ? 0.95 : 0.9;
                double drag = type != 2 || y < -0.1 ? 0.95 : 0.99;
                x *= drag;
                z *= drag;
                actual = ParachuteMotion.drag(type, tick, step.velocity());
                multiplier = step.speedMultiplier();
                assertEquals(0, actual.distanceTo(new Vec3(x, y, z)), 1.0E-12);
            }
        }
    }

    @Test
    void openChuteCapsHorizontalSpeedAndSettlesAtPointTwoSevenDown() {
        Vec3 motion = new Vec3(4, -0.9, 3);
        double multiplier = 0.07;
        for (int tick = 1; tick <= 300; tick++) {
            var step = ParachuteMotion.accelerate(1, tick, false, motion, multiplier, true, 45);
            assertTrue(step.velocity().horizontalDistance() <= 0.35 + 1.0E-12);
            motion = ParachuteMotion.drag(1, tick, step.velocity());
            multiplier = step.speedMultiplier();
        }
        assertEquals(-0.27, motion.y, 1.0E-12);
    }
}
