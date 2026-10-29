package io.github.mcheliceport.client.render;

import static org.junit.jupiter.api.Assertions.*;
import org.junit.jupiter.api.Test;

class FlightLightLookTest {
    @Test void strobesHaveTwoBriefFlashesRatherThanContinuousGlow(){
        for(int source:new int[]{0,1000000,Integer.MAX_VALUE}){
            int lit=0;for(int i=0;i<80;i++){float value=FlightLightLook.strobe(i,source,2);assertTrue(value==0||value==1);lit+=(int)value;}
            assertEquals(4,lit);assertEquals(FlightLightLook.strobe(0,source,2),FlightLightLook.strobe(80,source,2));
        }
    }
    @Test void daytimeHasNoTerrainGlowAndRadiusAndFleetDetailAreBounded(){
        assertEquals(0,FlightLightLook.night(0));assertEquals(1,FlightLightLook.night(11));assertTrue(FlightLightLook.night(7)>0);
        assertTrue(FlightLightLook.radius(.1F,2000)<=.35F);assertTrue(Float.isFinite(FlightLightLook.radius(Float.NaN,0)));
        assertEquals(32,FlightLightLook.lampLimit(0,0));assertEquals(64,FlightLightLook.lampLimit(1,0));
        assertTrue(FlightLightLook.lampLimit(2,2)<FlightLightLook.lampLimit(2,0));
    }
}
