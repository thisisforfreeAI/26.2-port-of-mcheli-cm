package io.github.mcheliceport.client.sound;

import static org.junit.jupiter.api.Assertions.*;
import net.minecraft.world.phys.Vec3;
import org.junit.jupiter.api.Test;

class FlightAudioLookTest {
    @Test void threeDimensionalPassRaisesThenLowersPitchWithoutAnInitialSpike(){
        var look=new FlightAudioLook();var ear=Vec3.ZERO;
        assertEquals(1,look.tick(new Vec3(0,20,0),ear,false,true));
        assertTrue(look.tick(new Vec3(0,18,0),ear,false,true)>1);
        for(int y=16;y>=-30;y-=2)look.tick(new Vec3(0,y,0),ear,false,true);
        assertTrue(look.value()<1);assertTrue(look.value()>=.85);
    }
    @Test void listenerMotionAndOwnCockpitDoNotInventRelativeMotion(){
        var look=new FlightAudioLook();look.tick(new Vec3(0,0,10),Vec3.ZERO,false,true);
        assertEquals(1,look.tick(new Vec3(0,0,12),new Vec3(0,0,2),false,true));
        assertEquals(1,look.tick(new Vec3(0,0,10),Vec3.ZERO,true,true));
        assertEquals(1,look.tick(new Vec3(0,0,8),Vec3.ZERO,false,false));
    }
    @Test void observerAndSourceWarpsResetRatherThanCreateFlybys(){
        var look=new FlightAudioLook();look.tick(new Vec3(0,0,10),Vec3.ZERO,false,true);
        assertEquals(1,look.tick(new Vec3(0,0,9),new Vec3(100,0,0),false,true));
        assertEquals(1,look.tick(new Vec3(100,0,9),new Vec3(100,0,0),false,true));
        assertEquals(1,look.tick(new Vec3(Double.NaN,0,9),Vec3.ZERO,false,true));
    }
    @Test void rangeAndGainsStayFiniteAndTheEdgeFadesContinuously(){
        for(float authored:new float[]{0,-4,16,1000,Float.NaN})assertTrue(FlightAudioLook.range(authored)>=1&&FlightAudioLook.range(authored)<=512);
        assertEquals(0,FlightAudioLook.edgeFade(100,100));assertEquals(1,FlightAudioLook.edgeFade(75,100));
        assertTrue(FlightAudioLook.edgeFade(95,100)<FlightAudioLook.edgeFade(85,100));
        assertTrue(FlightAudioLook.rearGain(-1,true)>FlightAudioLook.rearGain(1,true));
        assertEquals(1,FlightAudioLook.rearGain(-1,false));
        assertEquals(12,FlightAudioLook.voiceCap(0,0));assertEquals(24,FlightAudioLook.voiceCap(1,0));
        assertTrue(FlightAudioLook.voiceCap(2,3)<=FlightAudioLook.voiceCap(2,0));
        assertEquals(1,FlightAudioLook.pitch(Double.NaN));
    }
}
