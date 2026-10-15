package io.github.mcheliceport.client.render;

import io.github.mcheliceport.client.model.AircraftModels;
import io.github.mcheliceport.content.aircraft.AircraftInfo;
import io.github.mcheliceport.content.aircraft.HeliInfo;
import io.github.mcheliceport.entity.heli.HeliEntity;
import java.util.List;
import net.minecraft.client.renderer.entity.EntityRendererProvider;

/** Helicopter-specific parts on the common vehicle renderer. */
public class HeliRenderer extends AircraftRenderer<HeliEntity> {
    public HeliRenderer(EntityRendererProvider.Context context) { super(context); }

    @Override
    protected String directory() { return "helicopters"; }

    @Override
    protected void drawParts(HeliEntity entity, AircraftInfo info, AircraftModels.VehicleModels models,
            AircraftPartPoses.Input input, float partialTicks, List<AircraftPartPoses.Draw> draws) {
        AircraftPartPoses.heli((HeliInfo) info, models, input, draws);
    }
}
