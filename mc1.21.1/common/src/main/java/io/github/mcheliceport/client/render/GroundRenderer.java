package io.github.mcheliceport.client.render;

import io.github.mcheliceport.client.model.AircraftModels;
import io.github.mcheliceport.content.aircraft.AircraftInfo;
import io.github.mcheliceport.content.aircraft.VehicleInfo;
import io.github.mcheliceport.entity.ground.GroundVehicleEntity;
import java.util.List;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.entity.EntityRendererProvider;

/** Tanks and placed vehicles use the shared supported model-submission pipeline. */
public final class GroundRenderer<T extends GroundVehicleEntity> extends AircraftRenderer<T> {
    private final String directory;
    public GroundRenderer(EntityRendererProvider.Context context, String directory) { super(context); this.directory = directory; }
    @Override protected String directory() { return this.directory; }
    @Override protected void drawParts(T entity, AircraftInfo info, AircraftModels.VehicleModels models,
            AircraftPartPoses.Input in, float t, List<AircraftPartPoses.Draw> out) {
        GlMatrixStack gl = AircraftPartPoses.bodyStack(in);
        out.add(new AircraftPartPoses.Draw(gl.current(), models.body().body()));
        AircraftPartPoses.commonParts(info, models, in, gl, out);
        GroundPartPoses.wheels(info, models, entity.animation, in.steeringYaw(), t, gl, out);
        if (info instanceof VehicleInfo vehicle) {
            Minecraft mc = Minecraft.getInstance();
            GroundPartPoses.vehicle(vehicle, models, in.yaw(), in.pitch(), entity.lastRiderYaw, entity.lastRiderPitch,
                    entity.weapons().getFirstSeatWeapon(), t, entity.isPilot(mc.player) && mc.options.getCameraType().isFirstPerson(), gl, out,
                    index -> entity.weapons().isWeaponOnCooldown(entity.weapons().getFirstSeatWeapon(), index));
        }
    }
}
