package io.github.mcheliceport.client.particle;
import static org.junit.jupiter.api.Assertions.*;
import org.junit.jupiter.api.Test;
class VehicleWeatherLookTest {
    @Test void shelteredVehiclesStayDryAndColdParkedVehiclesAccumulateThenShedSnow(){
        var look=new VehicleWeatherLook();for(int i=0;i<200;i++)look.tick(true,true,true,false,false);
        assertEquals(0,look.wet());assertEquals(0,look.snow());
        for(int i=0;i<200;i++)look.tick(false,true,true,true,false);assertEquals(1,look.snow());
        for(int i=0;i<20;i++)look.tick(false,true,true,true,true);assertTrue(look.snow()<.5F);
    }
    @Test void wetPaintRetainsShadeContrastAndDriesWithoutNegativeAmounts(){
        var look=new VehicleWeatherLook();for(int i=0;i<200;i++)look.tick(true,false,false,true,false);
        int base=io.github.mcheliceport.client.model.VehicleSurfaceShade.BASE_COLOR,tint=look.tint(base);
        assertTrue((tint&255)<(base&255));
        int shaded=0xFFA0A0A0;assertEquals(shaded,io.github.mcheliceport.client.model.VehicleSurfaceShade.multiply(shaded,base));
        assertTrue((io.github.mcheliceport.client.model.VehicleSurfaceShade.multiply(shaded,tint)&255)<(tint&255));
        for(int i=0;i<2000;i++)look.tick(false,false,false,true,false);assertEquals(0,look.wet());
    }
}
