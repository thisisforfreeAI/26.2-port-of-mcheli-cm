package io.github.mcheliceport.client.render;

import io.github.mcheliceport.client.model.AircraftModels;
import io.github.mcheliceport.client.model.BulletModels;
import io.github.mcheliceport.client.model.PlaneExhaustTextures;
import net.minecraft.client.Minecraft;
import io.github.mcheliceport.content.aircraft.AircraftInfo;
import io.github.mcheliceport.content.aircraft.PlaneInfo;
import io.github.mcheliceport.entity.plane.PlaneEntity;
import java.util.List;
import net.minecraft.client.renderer.entity.EntityRendererProvider;

/** Plane model submission; all transforms are extracted before rendering, for GL and Vulkan. */
public final class PlaneRenderer extends AircraftRenderer<PlaneEntity> {
    public PlaneRenderer(EntityRendererProvider.Context context) { super(context); }

    @Override
    protected String directory() { return "planes"; }

    @Override
    public void extractRenderState(PlaneEntity plane, AircraftRenderState state, float partialTicks) {
        super.extractRenderState(plane, state, partialTicks);
        PlaneInfo info = plane.getPlaneInfo();
        float throttle = Math.clamp((float) plane.getCurrentThrottle(), 0.0F, 1.0F);
        if (state.texture == null || info == null || throttle == 0.0F || plane.getVtolMode() != 0) {
            return;
        }
        float steer = plane.previousExhaustYaw + (plane.exhaustYaw - plane.previousExhaustYaw) * partialTicks;
        for (int i = 0; i < info.exhaustFlames.size(); i++) {
            PlaneInfo.ExhaustFlame flame = info.exhaustFlames.get(i);
            var textures = PlaneExhaustTextures.get(Minecraft.getInstance().getResourceManager(), flame.texturePrefix);
            String modelName = flame.modelName.isEmpty() ? "Exhaustflame" : flame.modelName;
            var model = BulletModels.get("exhaustflames/" + modelName);
            if (textures.isEmpty() || model == null) {
                continue;
            }
            int frame = plane.exhaustAnimation.advance(i, info.exhaustFlames.size(), flame.delay, textures.size());
            state.emissiveDraws.add(new AircraftRenderState.EmissiveDraw(textures.get(frame),
                    PlaneExhaustPose.of(flame, state.yaw, state.pitch, state.roll, steer, throttle), model.groups));
        }
    }

    @Override
    protected void drawParts(PlaneEntity plane, AircraftInfo info, AircraftModels.VehicleModels models,
            AircraftPartPoses.Input input, float partialTicks, List<AircraftPartPoses.Draw> draws) {
        float nozzle = plane.partNozzle != null
                ? plane.partNozzle.prevRotation + (plane.partNozzle.rotation - plane.partNozzle.prevRotation) * partialTicks : 0.0F;
        float wing = plane.partWing != null
                ? plane.partWing.prevRotation + (plane.partWing.rotation - plane.partWing.prevRotation) * partialTicks : 0.0F;
        float rotor = plane.prevRotationRotor + (plane.rotationRotor - plane.prevRotationRotor) * partialTicks;
        PlanePartPoses.draw((PlaneInfo) info, models, input, nozzle, wing, rotor, draws);
    }
}
