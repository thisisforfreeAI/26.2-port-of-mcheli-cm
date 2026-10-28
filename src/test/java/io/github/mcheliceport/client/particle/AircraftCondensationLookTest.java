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
    @Test void contrailsRequireAltitudeMovementAndPowerAndWarmBiomesRaiseTheirThreshold(){
        assertEquals(0,AircraftCondensationLook.coldTarget(100,.8F,2,.9,true,true));
        assertTrue(AircraftCondensationLook.coldTarget(270,.8F,2,.9,true,true)>.9F);
        assertTrue(AircraftCondensationLook.coldTarget(210,-.5F,2,.9,true,true)>AircraftCondensationLook.coldTarget(210,2,2,.9,true,true));
        assertEquals(0,AircraftCondensationLook.coldTarget(270,.8F,0,.9,true,true));
        assertEquals(0,AircraftCondensationLook.coldTarget(270,.8F,2,0,true,true));
        assertEquals(0,AircraftCondensationLook.coldTarget(270,.8F,2,.9,false,true));
        assertEquals(0,AircraftCondensationLook.coldTarget(270,.8F,2,.9,true,false));
        assertEquals(0,AircraftCondensationLook.coldTarget(Double.NaN,.8F,2,.9,true,true));
    }
    @Test void coldTrailStrengthAndTemperatureSamplingStayBoundedAndOffClearsEmission(){
        var look=new AircraftCondensationLook();assertTrue(look.sampleTemperature(0));look.temperature(0,.8F);assertFalse(look.sampleTemperature(19));assertTrue(look.sampleTemperature(20));
        float previous=0;for(int tick=0;tick<40;tick++){look.cold(1,true);assertTrue(look.contrail()>=previous&&look.contrail()<=1);previous=look.contrail();}
        look.cold(0,false);assertEquals(0,look.contrail());
    }
    @Test void farColdStatesCannotTakeTheSlotsReservedForNearbyAtmosphere(){
        assertTrue(AircraftCondensationLook.stateAllowed(true,63));assertFalse(AircraftCondensationLook.stateAllowed(true,64));
        assertTrue(AircraftCondensationLook.stateAllowed(false,64));assertTrue(AircraftCondensationLook.stateAllowed(false,127));
        assertFalse(AircraftCondensationLook.stateAllowed(false,128));
    }
    @Test void coldTrailSpacingWidensWithinTheSameBoundedQuad(){
        float near=AircraftCondensationLook.contrailRadius(1.3,3,2,false),far=AircraftCondensationLook.contrailRadius(1.3,8,1,true);
        assertTrue(near>=1.25F&&near<=2.5F);assertTrue(far>near&&far<=4);
        assertTrue(near*2.35*.75>=1.3*3/2);assertTrue(far*2.35*1.2>=1.3*8);
        assertEquals(1.25F,AircraftCondensationLook.contrailRadius(Double.NaN,3,1,false));
    }
    @Test void nearbyStatesMovingAwayCannotKeepAllTheNearbySlots(){
        int kept=0;for(int i=0;i<128;i++)if(AircraftCondensationLook.retainFarState(kept))kept++;
        assertEquals(64,kept);assertTrue(AircraftCondensationLook.stateAllowed(false,kept));
    }
}
