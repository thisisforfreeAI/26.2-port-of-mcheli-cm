package io.github.mcheliceport.client.particle;
import static org.junit.jupiter.api.Assertions.*;
import org.junit.jupiter.api.Test;
class VehicleWaterLookTest {
    @Test void parkedVehiclesHaveNoWakeAndFoamExpires(){
        assertFalse(VehicleWaterLook.moving(0));assertFalse(VehicleWaterLook.moving(Double.NaN));assertTrue(VehicleWaterLook.moving(.04));
        assertEquals(0,VehicleWaterLook.alpha(0));assertEquals(0,VehicleWaterLook.alpha(40));assertTrue(VehicleWaterLook.alpha(15)>.4F);
    }
    @Test void largeMeshesCannotCreateUnboundedWakes(){assertEquals(2.5F,VehicleWaterLook.width(1000));assertEquals(.35F,VehicleWaterLook.width(0));}
}
