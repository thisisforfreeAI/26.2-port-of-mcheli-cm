package io.github.mcheliceport.client.render;

import io.github.mcheliceport.client.model.AircraftModels;
import io.github.mcheliceport.content.aircraft.AircraftInfo;
import io.github.mcheliceport.content.aircraft.ShipInfo;
import io.github.mcheliceport.entity.ship.ShipEntity;
import java.util.List;
import net.minecraft.client.renderer.entity.EntityRendererProvider;

/** Upstream ship body and parts, using the shared OpenGL/Vulkan submission pipeline. */
public final class ShipRenderer extends AircraftRenderer<ShipEntity> {
    public ShipRenderer(EntityRendererProvider.Context context) { super(context); }
    @Override protected String directory() { return "ships"; }

    @Override
    protected void drawParts(ShipEntity ship, AircraftInfo info, AircraftModels.VehicleModels models,
            AircraftPartPoses.Input input, float partialTicks, List<AircraftPartPoses.Draw> draws) {
        float nozzle = ship.partNozzle != null
                ? ship.partNozzle.prevRotation + (ship.partNozzle.rotation - ship.partNozzle.prevRotation) * partialTicks : 0.0F;
        float wing = ship.partWing != null
                ? ship.partWing.prevRotation + (ship.partWing.rotation - ship.partWing.prevRotation) * partialTicks : 0.0F;
        float rotor = ship.prevRotationRotor + (ship.rotationRotor - ship.prevRotationRotor) * partialTicks;
        PlanePartPoses.draw((ShipInfo) info, models, input, nozzle, wing, rotor, draws);
    }
}
