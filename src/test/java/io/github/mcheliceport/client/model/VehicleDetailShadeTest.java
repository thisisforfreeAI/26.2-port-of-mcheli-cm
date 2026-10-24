package io.github.mcheliceport.client.model;

import static org.junit.jupiter.api.Assertions.*;
import io.github.mcheliceport.content.ContentLocation;
import io.github.mcheliceport.content.aircraft.AircraftInfo;
import io.github.mcheliceport.content.aircraft.HeliInfo;
import java.util.List;
import net.minecraft.world.phys.Vec3;
import org.junit.jupiter.api.Test;

class VehicleDetailShadeTest {
    @Test void zonesFadeWithoutSeamsAndOverlapsDoNotStackIntoBlack() {
        var zone = new VehicleDetailShade.Zone(Vec3.ZERO, 1, 1, 1, 0.06F);
        assertEquals(0.94F, VehicleDetailShade.factor(List.of(zone, zone), 0, 0, 0));
        assertEquals(1, VehicleDetailShade.factor(List.of(zone), 1, 0, 0));
        assertEquals(1, VehicleDetailShade.factor(List.of(zone), 10, 0, 0));
        assertTrue(VehicleDetailShade.factor(List.of(zone), 0.5F, 0, 0) > 0.94F);
    }
    @Test void onlyAuthoredMachineryGetsZonesAndInputWorkIsBounded() {
        var info = new HeliInfo(ContentLocation.builtin("test"), "test", "test");
        assertTrue(VehicleDetailShade.zones(info).isEmpty());
        for (int i = 0; i < 100; i++) {
            info.engineOutlets.add(new AircraftInfo.EngineOutlet(new Vec3(i, 1, 0), new Vec3(0, 0, -1), 1, false));
            info.rotorList.add(new HeliInfo.Rotor(4, 90, 0, 3, i, 0, 1, 0, "rotor", false, false));
        }
        assertEquals(8, VehicleDetailShade.zones(info).size());
    }
    @Test void staticBodyGetsDetailWithoutBakingShadowsOntoMovingParts() {
        float[] data = {0,0,0,0,0,0,1,0, 1,0,0,0,0,0,1,0, 0,0,1,0,0,0,1,0};
        var body = new McheliModel.Group("$body", data);
        var rotor = new McheliModel.Group("$blade0", data.clone());
        var model = new McheliModel("test", List.of(body,rotor), 0,0,0,1,1,1);
        VehicleSurfaceShade.prepare(model, List.of(new VehicleDetailShade.Zone(Vec3.ZERO,1,1,1,0.06F)));
        assertTrue((VehicleSurfaceShade.get(body)[0] & 255) < (VehicleSurfaceShade.BASE_COLOR & 255));
        assertEquals(VehicleSurfaceShade.BASE_COLOR, VehicleSurfaceShade.get(rotor)[0]);
    }
}
