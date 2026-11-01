package io.github.mcheliceport.client.render;

import io.github.mcheliceport.content.aircraft.AircraftInfo;
import io.github.mcheliceport.content.aircraft.PlaneInfo;
import org.joml.Matrix4f;

/** Upstream exhaust-flame body rotation, local translation, steering deflection and throttle scale. */
public final class PlaneExhaustPose {
    private PlaneExhaustPose() {
    }

    public static Matrix4f of(PlaneInfo.ExhaustFlame part, float yaw, float pitch, float roll,
            float steer, float throttle) {
        GlMatrixStack gl = new GlMatrixStack();
        gl.translate(0, AircraftInfo.GLOBAL_Y_OFFSET, 0);
        gl.rotate(yaw, 0, -1, 0);
        gl.rotate(pitch, 1, 0, 0);
        gl.rotate(roll, 0, 0, 1);
        gl.translate(part.pos.x, part.pos.y, part.pos.z);
        float angle = Math.clamp(steer, -1.0F, 1.0F) * part.degreeYaw;
        // Preserve upstream's clamp order even for a negative DegreeYaw in addon content.
        angle = angle < -part.degreeYaw ? -part.degreeYaw : Math.min(angle, part.degreeYaw);
        gl.rotate(-angle, (float) part.rot.x, (float) part.rot.y, (float) part.rot.z);
        gl.scale(1, 1, Math.clamp(throttle, 0.0F, 1.0F));
        return gl.current();
    }
}
