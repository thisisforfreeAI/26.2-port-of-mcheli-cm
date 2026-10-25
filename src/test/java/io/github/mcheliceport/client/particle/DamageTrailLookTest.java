package io.github.mcheliceport.client.particle;

import static org.junit.jupiter.api.Assertions.*;
import org.junit.jupiter.api.Test;
class DamageTrailLookTest {
    @Test void movingVehiclesOutrunTheirShedSmoke(){
        for(double speed:new double[]{.1,.5,1,3,20})assertTrue(DamageTrailLook.inherited(speed)<speed);
        assertEquals(0,DamageTrailLook.inherited(Double.NaN));
        assertEquals(-DamageTrailLook.inherited(2),DamageTrailLook.inherited(-2));
    }
    @Test void wispsAreLighterAndExpireBeforeDenseCores(){
        for(float severity:new float[]{0,.5F,1}){
            assertTrue(DamageTrailLook.lifetime(severity,true)<DamageTrailLook.lifetime(severity,false));
            assertTrue(DamageTrailLook.gray(severity,true)>DamageTrailLook.gray(severity,false));
            assertTrue(DamageTrailLook.radius(severity,10)<=1.61F);
        }
    }
}
