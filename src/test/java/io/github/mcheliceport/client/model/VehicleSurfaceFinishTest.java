package io.github.mcheliceport.client.model;
import static org.junit.jupiter.api.Assertions.*;
import org.junit.jupiter.api.Test;
class VehicleSurfaceFinishTest {
    @Test void explicitlyNamedMaterialsStayRestrainedAndPaintStaysNeutral() {
        assertEquals(VehicleSurfaceFinish.Material.RUBBER,VehicleSurfaceFinish.material("$wheelL"));
        assertEquals(VehicleSurfaceFinish.Material.VENT,VehicleSurfaceFinish.material("$exhaust2"));
        assertEquals(VehicleSurfaceFinish.Material.GLASS,VehicleSurfaceFinish.material("Window"));
        assertEquals(VehicleSurfaceFinish.Material.PAINT,VehicleSurfaceFinish.material("$body"));
        assertEquals(1,VehicleSurfaceFinish.shade(VehicleSurfaceFinish.Material.PAINT,1));
        for(var m:VehicleSurfaceFinish.Material.values())for(float crease:new float[]{.88F,1,1.03F}) {
            float shade=VehicleSurfaceFinish.shade(m,crease);
            assertTrue(shade>=.82F && shade<=1.04F);
        }
    }
    @Test void preparedFinishUsesTheSameGreyPaletteForGpuAndVertexRenderers() {
        var group=new McheliModel.Group("$vent",new float[]{0,0,0,0,0,0,1,0, 1,0,0,1,0,0,1,0, 0,0,1,0,1,0,1,0});
        VehicleSurfaceShade.prepare(new McheliModel("test",java.util.List.of(group),0,0,0,1,0,1));
        for(int rgb:VehicleSurfaceShade.get(group)) {
            assertEquals(rgb>>16&255,rgb>>8&255);assertEquals(rgb>>8&255,rgb&255);
            assertTrue((rgb&255)<(VehicleSurfaceShade.BASE_COLOR&255));
        }
    }
}
