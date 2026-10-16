package io.github.mcheliceport.client.render;

import io.github.mcheliceport.entity.aircraft.AircraftRacks;
import io.github.mcheliceport.util.McheliMath;
import net.minecraft.world.entity.Entity;
import org.joml.Matrix4f;

/** Upstream renderEntitySimple tilts cargo around its own position in carrier yaw coordinates. */
public final class RackRenderPose {
    private RackRenderPose() { }
    public static Matrix4f forEntity(Entity cargo, float partialTick) {
        var seat = AircraftRacks.rackSeat(cargo);
        if (seat == null || seat.getParent() == null) { return new Matrix4f(); }
        var parent = seat.getParent();
        var ypr = McheliMath.slerpOrientationYPR(parent.yRotO, parent.xRotO, parent.prevRoll,
                parent.getYaw(), parent.getPitch(), parent.getRoll(), partialTick);
        return tilt(ypr[0], ypr[1], ypr[2]);
    }
    public static Matrix4f tilt(float yaw, float pitch, float roll) {
        var pose = new GlMatrixStack();
        pose.rotate(yaw, 0, -1, 0); pose.rotate(pitch, 1, 0, 0);
        pose.rotate(roll, 0, 0, 1); pose.rotate(-yaw, 0, -1, 0);
        return pose.current();
    }
}
