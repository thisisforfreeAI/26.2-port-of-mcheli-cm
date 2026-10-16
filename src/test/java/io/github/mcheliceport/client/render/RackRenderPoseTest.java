package io.github.mcheliceport.client.render;

import static org.junit.jupiter.api.Assertions.assertEquals;

import io.github.mcheliceport.util.McheliMath;
import org.joml.Vector3f;
import org.junit.jupiter.api.Test;

class RackRenderPoseTest {
    @Test void cargoTiltMatchesComposedWorldAttitude() {
        for (float yaw : new float[] {-170, 0, 67, 180}) {
            float pitch = 23, roll = -31, fixedYaw = 90, fixedPitch = 12;
            var rendered = RackRenderPose.tilt(yaw, pitch, roll)
                    .rotateY((float) Math.toRadians(-yaw - fixedYaw)).rotateX((float) Math.toRadians(fixedPitch))
                    .transformPosition(new Vector3f(1, 2, 3));
            var expected = McheliMath.worldOrientationQuat(yaw, pitch, roll)
                    .mul(McheliMath.worldOrientationQuat(fixedYaw, fixedPitch, 0)).transform(new Vector3f(1, 2, 3));
            assertEquals(expected.x, rendered.x, 1e-5); assertEquals(expected.y, rendered.y, 1e-5); assertEquals(expected.z, rendered.z, 1e-5);
        }
    }
}
