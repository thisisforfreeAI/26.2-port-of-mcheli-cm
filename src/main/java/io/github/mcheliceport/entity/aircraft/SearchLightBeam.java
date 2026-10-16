package io.github.mcheliceport.entity.aircraft;

import io.github.mcheliceport.content.aircraft.AircraftInfo;
import net.minecraft.world.phys.Vec3;
import org.joml.Matrix4f;
import org.joml.Vector3f;

/** The stock searchlight's cone frame, shared by rendering, culling and living-entity lighting. */
public record SearchLightBeam(Matrix4f pose, AircraftInfo.SearchLight light) {
    public static boolean usable(AircraftInfo.SearchLight light) {
        return light.height > 0 && light.width > 0 && Float.isFinite(light.height) && Float.isFinite(light.width)
                && Float.isFinite(light.yaw) && Float.isFinite(light.pitch) && Float.isFinite(light.stRot)
                && Double.isFinite(light.pos.x) && Double.isFinite(light.pos.y) && Double.isFinite(light.pos.z);
    }

    /** Upstream renderLight: the cone extends along local +Y, after the body and lamp rotations. */
    public static SearchLightBeam of(AircraftInfo.SearchLight light, float yaw, float pitch, float roll,
            float aimYaw, float aimPitch, float steering) {
        var pose = new Matrix4f().translate(0, AircraftInfo.GLOBAL_Y_OFFSET, 0)
                .rotateY(rad(-yaw)).rotateX(rad(pitch)).rotateZ(rad(roll))
                .translate((float) light.pos.x, (float) light.pos.y, (float) light.pos.z);
        if (light.fixDir) {
            pose.rotateY(rad(-light.yaw + (light.steering ? steering * light.stRot : 0)))
                    .rotateX(rad(90 + light.pitch));
        } else {
            pose.rotateY(rad(-(aimYaw - yaw + light.yaw)))
                    .rotateX(rad(aimPitch + 90 - pitch + light.pitch));
        }
        return new SearchLightBeam(pose, light);
    }

    private static float rad(float degrees) { return (float) Math.toRadians(degrees); }
    public Vec3 point(float x, float y, float z) {
        var v = pose.transformPosition(x, y, z, new Vector3f());
        return new Vec3(v.x, v.y, v.z);
    }

    /** Upstream's broad, saturated living-entity highlight, without replacing existing light. */
    public int blockLight(Vec3 relativeTarget) {
        Vec3 offset = relativeTarget.subtract(point(0, 0, 0));
        double distance2 = offset.lengthSqr();
        if (distance2 <= 2 || distance2 >= light.height * light.height + 20) { return 0; }
        Vec3 axis = point(0, light.height, 0).subtract(point(0, 0, 0)).normalize();
        double angle = Math.toDegrees(Math.acos(Math.clamp(axis.dot(offset.normalize()), -1, 1)));
        double limit = light.angle * 3;
        return limit > 0 ? (int) Math.clamp(90 * (1 - angle / limit), 0, 15) : 0;
    }
}
