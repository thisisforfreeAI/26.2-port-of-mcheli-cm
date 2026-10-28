package io.github.mcheliceport.client.particle;

import static org.junit.jupiter.api.Assertions.*;
import org.junit.jupiter.api.Test;

class SupersonicLookTest {
    @Test void crossingProducesOneConeAndThresholdChatterCannotRepeatIt(){
        var look=new SupersonicLook();assertFalse(look.tick(0,.5,1,10,20,true,true).cone());
        assertTrue(look.tick(1,1,1,9,20,true,true).cone());
        for(int i=2;i<150;i++)assertFalse(look.tick(i,i%2==0?.95:1.02,1,9,20,true,true).cone());
        for(int i=150;i<202;i++)look.tick(i,.8,1,9,20,true,true);
        assertTrue(look.tick(202,1,1,9,20,true,true).cone());
    }
    @Test void observingAnAlreadyFastJetDoesNotInventAnInitialConeOrBoom(){
        var look=new SupersonicLook();assertEquals(SupersonicLook.Event.NONE,look.tick(100,1.4,1,-3,20,true,true));
        assertEquals(SupersonicLook.Event.NONE,look.tick(101,1.4,1,-4,20,true,true));
    }
    @Test void listenerGetsABoomOnlyWhenTheJetPassesAndCooldownPreventsDuplicatePasses(){
        var look=new SupersonicLook();look.tick(0,1.3,1,10,20,true,true);
        assertTrue(look.tick(1,1.3,1,-1,20,true,true).boom());
        look.tick(2,1.3,1,10,20,true,true);assertFalse(look.tick(3,1.3,1,-1,20,true,true).boom());
        var slow=new SupersonicLook();slow.tick(0,.8,1,10,20,true,true);assertFalse(slow.tick(1,.8,1,-1,20,true,true).boom());
    }
    @Test void gapsWarpsInvalidInputAndUnsupportedProfilesDoNotCreatePassEvents(){
        for(int mode=0;mode<4;mode++){
            var look=new SupersonicLook();look.tick(0,.8,1,10,20,true,true);
            assertEquals(SupersonicLook.Event.NONE,look.tick(mode==0?100:1,mode==1?Double.NaN:1.4,mode==2?0:1,-1,20,mode!=3,true));
        }
    }
    @Test void soundDelayAndVolumeAreBoundedAndDependOnDistance(){
        assertEquals(1,SupersonicLook.delay(0));assertTrue(SupersonicLook.delay(343)>SupersonicLook.delay(34));
        assertEquals(30,SupersonicLook.delay(9999));assertTrue(SupersonicLook.volume(400)<SupersonicLook.volume(40));
        assertEquals(0,SupersonicLook.volume(1000));
    }
    @Test void shortObservationGapsRetainARealPassButWarpsStillResetTracking(){
        assertTrue(SupersonicLook.continuous(2,1.8,.75));assertFalse(SupersonicLook.continuous(2,100,.75));
        assertFalse(SupersonicLook.continuous(20,1.8,.75));
        var look=new SupersonicLook();look.tick(0,1.2,1,1,50,true,true);
        assertTrue(look.tick(2,1.2,1,-1,50,SupersonicLook.continuous(2,2.4,1),true).boom());
    }
}
