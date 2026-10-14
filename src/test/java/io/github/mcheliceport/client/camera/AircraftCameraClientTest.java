package io.github.mcheliceport.client.camera;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.github.mcheliceport.util.McheliMath;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.Vec3;
import org.joml.Quaternionf;
import org.joml.Vector3f;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

class AircraftCameraClientTest {

    @AfterEach
    void reset() {
        AircraftCameraClient.reset();
    }

    @Test
    void pageUpAndDownStepByFourBetweenFourAndSeventyTwo() {
        assertEquals(8.0F, AircraftCameraClient.stepThirdPersonDistance(4.0F, true));
        assertEquals(4.0F, AircraftCameraClient.stepThirdPersonDistance(4.0F, false));
        assertEquals(72.0F, AircraftCameraClient.stepThirdPersonDistance(70.0F, true));
        // Upstream truncates before stepping.
        assertEquals(10.0F, AircraftCameraClient.stepThirdPersonDistance(6.9F, true));
    }

    @Test
    void distanceIsKeptBetweenFourAndSixtyAsUpstream() {
        AircraftCameraClient.setThirdPersonDistance(64.0F);
        assertEquals(60.0F, AircraftCameraClient.thirdPersonDistance());
        AircraftCameraClient.setThirdPersonDistance(2.0F);
        assertEquals(4.0F, AircraftCameraClient.thirdPersonDistance());
        AircraftCameraClient.setThirdPersonDistance(0.05F);
        assertEquals(4.0F, AircraftCameraClient.thirdPersonDistance(), "below 0.1 is ignored");
        AircraftCameraClient.setThirdPersonDistance(12.0F);
        AircraftCameraClient.reset();
        assertEquals(4.0F, AircraftCameraClient.thirdPersonDistance());
    }

    private static final double EYE = 1.62;

    /** The camera's up vector as {@code Camera#setRotation} computes it. */
    private static Vector3f cameraUp(float yRot, float xRot, float roll) {
        return new Vector3f(0.0F, 1.0F, 0.0F).rotate(new Quaternionf().rotationYXZ(
                (float) Math.PI - yRot * Mth.DEG_TO_RAD, -xRot * Mth.DEG_TO_RAD, -roll * Mth.DEG_TO_RAD));
    }

    private static void assertVec(Vec3 expected, Vec3 actual) {
        // The camera's vectors are floats.
        assertEquals(0.0, expected.distanceTo(actual), 1.0E-4, () -> "expected " + expected + " but was " + actual);
    }

    @Test
    void levelEyeIsStraightAbove() {
        Vec3 up = new Vec3(0.0, EYE, 0.0);
        assertVec(up, AircraftCameraClient.cockpitEyeOffset(true, new float[] {40.0F, 0.0F, 0.0F}, 40.0F, 0.0F,
                cameraUp(40.0F, 0.0F, 0.0F), EYE));
        assertVec(up, AircraftCameraClient.cockpitEyeOffset(false, new float[] {40.0F, 10.0F, 0.0F}, 40.0F, 10.0F,
                cameraUp(40.0F, 10.0F, 0.0F), EYE));
    }

    @Test
    void noseDownCarriesTheEyeForward() {
        float[] ypr = {0.0F, 25.0F, 0.0F};
        Vec3 offset = AircraftCameraClient.cockpitEyeOffset(true, ypr, 0.0F, 25.0F, cameraUp(0.0F, 25.0F, 0.0F), EYE);
        assertVec(McheliMath.rotVec3(0.0, EYE, 0.0, -0.0F, -25.0F, -0.0F), offset);
        // The vehicle's nose is body +z; the eye leans towards it by sin(25 degrees).
        Vec3 nose = McheliMath.rotVec3(0.0, 0.0, 1.0, -0.0F, -25.0F, -0.0F);
        assertTrue(nose.y < 0.0, "positive pitch is nose down");
        assertEquals(EYE * Mth.sin(25.0F * Mth.DEG_TO_RAD), offset.horizontalDistance(), 1.0E-5);
        assertTrue(offset.x * nose.x + offset.z * nose.z > 0.0, "the eye leans towards the nose");
    }

    @Test
    void eyeLeansWithTheViewsRoll() {
        // The camera banks with the vehicle's roll, so the eye leans the same way either way.
        float roll = 30.0F;
        Vec3 body = AircraftCameraClient.cockpitEyeOffset(true, new float[] {0.0F, 0.0F, roll}, 0.0F, 0.0F,
                cameraUp(0.0F, 0.0F, roll), EYE);
        Vec3 view = AircraftCameraClient.cockpitEyeOffset(false, new float[] {0.0F, 0.0F, roll}, 0.0F, 0.0F,
                cameraUp(0.0F, 0.0F, roll), EYE);
        assertVec(body, view);
        assertEquals(EYE * Mth.cos(roll * Mth.DEG_TO_RAD), view.y, 1.0E-4);
    }
}
