package io.github.mcheliceport.client.particle;

import static org.junit.jupiter.api.Assertions.*;
import org.junit.jupiter.api.Test;

class LandingLookTest {
    @Test void firstParkedObservationHasLoadWithoutAnInventedTouchdown(){
        var look=new LandingLook();for(int i=0;i<50;i++)assertFalse(look.tick(true,0,0,0,true).touchdown());
        assertEquals(.03,look.compression(1),.0001);
    }
    @Test void touchdownHasOnePulseAndCompressionSettlesBackToParkingLoad(){
        var look=new LandingLook();for(int i=0;i<5;i++)look.tick(false,-.25,.3,0,true);
        assertTrue(look.tick(true,0,.3,0,true).touchdown());assertTrue(look.compression(1)>.03);
        for(int i=0;i<80;i++)assertFalse(look.tick(true,0,.3,0,true).touchdown());
        assertEquals(.03,look.compression(1),.001);
        for(int i=0;i<50;i++)look.tick(false,.2,.3,0,true);assertEquals(0,look.compression(1),.0001);
    }
    @Test void delayedGroundSamplingRetainsHardImpactButDoesNotKeepOldDescentForever(){
        var look=new LandingLook();look.tick(false,-.7,.4,0,true);for(int i=0;i<4;i++)look.tick(false,0,.4,0,true);
        assertTrue(look.tick(true,0,.4,0,true).hard());
        var leveled=new LandingLook();leveled.tick(false,-.7,.4,0,true);for(int i=0;i<40;i++)leveled.tick(false,0,.4,0,true);
        assertFalse(leveled.tick(true,0,.4,0,true).touchdown());
    }
    @Test void warpsAndNonFiniteInputResetAndTravelRemainsBounded(){
        var look=new LandingLook();look.tick(false,-2,1,0,true);
        assertEquals(LandingLook.Impact.NONE,look.tick(true,0,1,0,false));assertEquals(0,look.compression(1));
        assertFalse(look.tick(true,0,1,0,true).touchdown());
        for(int i=0;i<100;i++)look.tick(i%2==0,-2,2,100,true);assertTrue(look.compression(1)<=.12);
        assertFalse(look.tick(true,Double.NaN,0,0,true).touchdown());assertEquals(0,look.compression(1));
    }
}
