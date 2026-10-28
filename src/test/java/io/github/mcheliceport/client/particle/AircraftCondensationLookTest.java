package io.github.mcheliceport.client.particle;

import static org.junit.jupiter.api.Assertions.*;
import org.junit.jupiter.api.Test;

class AircraftCondensationLookTest {
    @Test void humidTurnsBuildVaporButStraightDryAndGroundedFlightDoNot(){
        for(int mode=0;mode<4;mode++){
            var look=new AircraftCondensationLook();for(int tick=0;tick<40;tick++)look.tick(tick,mode==1?0:tick*2,0,1,mode==2?0:1,mode!=3,true);
            if(mode==0)assertTrue(look.strength()>.5F);else assertEquals(0,look.strength());}
    }
    @Test void wraparoundIsSmoothAndWarpsOrDisablingDoNotInventAGust(){
        var wrap=new AircraftCondensationLook();var plain=new AircraftCondensationLook();
        for(int tick=0;tick<40;tick++)assertEquals(plain.tick(tick,179+tick*2,0,1,1,true,true),wrap.tick(tick,(179+tick*2+180)%360-180,0,1,1,true,true),.00001);
        assertEquals(0,wrap.tick(1000,0,0,1,1,true,true));assertEquals(0,wrap.tick(1001,Float.NaN,0,1,1,true,true));
        assertEquals(0,plain.tick(40,260,0,1,1,true,false));
    }
    @Test void VaporFadesAndLoadDistancePresetsNeverIncreaseItsBirthRate(){
        var look=new AircraftCondensationLook();for(int tick=0;tick<40;tick++)look.tick(tick,tick*2,0,1,1,true,true);
        float previous=look.strength();for(int tick=40;tick<80;tick++){float value=look.tick(tick,78,0,1,0,true,true);assertTrue(value<=previous);previous=value;}
        assertEquals(0,look.strength());int normal=AircraftCondensationLook.period(false,1,0);
        assertTrue(AircraftCondensationLook.period(true,1,0)>=normal);assertTrue(AircraftCondensationLook.period(false,32,0)>=normal);
        assertTrue(AircraftCondensationLook.period(false,1,3)>=normal);
    }
}
