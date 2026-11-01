package io.github.mcheliceport.client.render;

import io.github.mcheliceport.client.model.AircraftModels;
import io.github.mcheliceport.content.aircraft.AircraftInfo;
import io.github.mcheliceport.content.aircraft.VehicleInfo;
import io.github.mcheliceport.entity.ground.GroundAnimation;
import io.github.mcheliceport.weapon.WeaponSet;
import java.util.List;
import net.minecraft.util.Mth;

/** Wheel, roller, link and recursive placed-gun transforms; no graphics backend calls. */
public final class GroundPartPoses {
    private GroundPartPoses() { }
    public static void wheels(AircraftInfo info, AircraftModels.VehicleModels models,
            GroundAnimation animation, float steer, float t, GlMatrixStack gl, List<AircraftPartPoses.Draw> out) {
        for (var part : info.partWheel) {
            gl.pushMatrix();
            gl.translate(part.pos2.x, part.pos2.y, part.pos2.z);
            gl.rotate(steer * part.rotDir, (float) part.rot.x, (float) part.rot.y, (float) part.rot.z);
            gl.translate(-part.pos2.x, -part.pos2.y, -part.pos2.z);
            gl.translate(part.pos.x, part.pos.y, part.pos.z);
            gl.rotate(Mth.lerp(t, animation.previousWheel, animation.wheel), 1, 0, 0);
            gl.translate(-part.pos.x, -part.pos.y, -part.pos.z);
            out.add(new AircraftPartPoses.Draw(gl.current(), models.part(part.modelName))); gl.popMatrix();
        }
        for (var part : info.partTrackRoller) {
            gl.pushMatrix(); gl.translate(part.pos.x, part.pos.y, part.pos.z);
            gl.rotate(Mth.lerp(t, animation.previousRoller[part.side], animation.roller[part.side]), 1, 0, 0);
            gl.translate(-part.pos.x, -part.pos.y, -part.pos.z);
            out.add(new AircraftPartPoses.Draw(gl.current(), models.part(part.modelName))); gl.popMatrix();
        }
        for (var part : info.partCrawlerTrack) {
            int count = part.lp.size() - 1;
            float progress = Mth.lerp(t, animation.previousTrack[part.side], animation.track[part.side]);
            for (int i = 0; i < count; i++) {
                var p = part.lp.get(i); var next = part.lp.get((i + 1) % count);
                gl.pushMatrix();
                gl.translate(0, Mth.lerp(progress, p.x, next.x), Mth.lerp(progress, p.y, next.y));
                gl.rotate(p.r + Mth.wrapDegrees(next.r - p.r) * progress, -1, 0, 0);
                out.add(new AircraftPartPoses.Draw(gl.current(), models.part(part.modelName))); gl.popMatrix();
            }
        }
    }

    public static void vehicle(VehicleInfo info, AircraftModels.VehicleModels models, float yaw, float pitch,
            float riderYaw, float riderPitch, WeaponSet weapon, float t, boolean pilotFirstPerson,
            GlMatrixStack gl, List<AircraftPartPoses.Draw> out, java.util.function.IntPredicate drawn) {
        int[] index = {0};
        for (var part : info.partList) { vehiclePart(part, info, models, yaw, pitch, riderYaw, riderPitch,
                weapon, t, pilotFirstPerson, gl, out, index, drawn); }
    }

    private static void vehiclePart(VehicleInfo.VPart p, VehicleInfo info, AircraftModels.VehicleModels models,
            float yaw, float pitch, float riderYaw, float riderPitch, WeaponSet weapon, float t, boolean firstPerson,
            GlMatrixStack gl, List<AircraftPartPoses.Draw> out, int[] index, java.util.function.IntPredicate drawn) {
        gl.pushMatrix();
        int before = index[0];
        float recoil = before < weapon.recoilBuffer.length
                ? Mth.lerp(t, weapon.recoilBuffer[before].prevRecoilBuf, weapon.recoilBuffer[before].recoilBuf) : 0;
        if (p.rotPitch || p.rotYaw || p.type == 1) {
            gl.translate(p.pos.x, p.pos.y, p.pos.z);
            if (p.rotYaw) { gl.rotate(-riderYaw + yaw, 0, 1, 0); }
            if (p.rotPitch) { gl.rotate(Math.clamp(riderPitch, info.minRotationPitch, info.maxRotationPitch) - pitch, 1, 0, 0); }
            if (p.type == 1) { gl.rotate(Mth.lerp(t, weapon.getPrevRotBarrel(), weapon.getRotBarrel()), 0, 0, -1); }
            gl.translate(-p.pos.x, -p.pos.y, -p.pos.z);
        }
        if (p.type == 2) { gl.translate(0, 0, -p.recoilBuf * recoil); }
        if (p.type == 2 || p.type == 3) { index[0]++; }
        if (p.child != null) {
            // Pinned upstream passes the parent's interpolated recoil as the child's tick fraction.
            for (var child : p.child) { vehiclePart(child, info, models, yaw, pitch, riderYaw, riderPitch,
                    weapon, recoil, firstPerson, gl, out, index, drawn); }
        }
        if ((p.drawFP || !firstPerson) && (p.type != 3 || drawn.test(before))) {
            out.add(new AircraftPartPoses.Draw(gl.current(), models.part(p.modelName)));
        }
        gl.popMatrix();
    }
}
