package io.github.mcheliceport.client.render;

import io.github.mcheliceport.client.model.AircraftModels;
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
