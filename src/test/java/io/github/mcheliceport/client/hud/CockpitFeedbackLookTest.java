package io.github.mcheliceport.client.hud;

import static org.junit.jupiter.api.Assertions.*;
import org.junit.jupiter.api.Test;

class CockpitFeedbackLookTest {
    @Test void onlyAnEnclosedClosedLocalPilotViewGetsTheCockpitLayer(){
        assertTrue(new CockpitFeedbackLook.View(true,true,true,true,true,false,false).visible());
        for(int disabled=0;disabled<7;disabled++){
            boolean[] flags={true,true,true,true,true,false,false};flags[disabled]=!flags[disabled];
            assertFalse(new CockpitFeedbackLook.View(flags[0],flags[1],flags[2],flags[3],flags[4],flags[5],flags[6]).visible());
        }
    }
    @Test void RainBuildsAndDriesWithoutAccumulatingWhileHiddenOrAcrossTickGaps(){
        var look=new CockpitFeedbackLook();for(int i=0;i<40;i++)look.tick(i,true);assertEquals(1,look.wetness(),.001);
        for(int i=40;i<80;i++)look.tick(i,false);assertEquals(0,look.wetness());
        look.tick(81,true);float once=look.wetness();look.tick(81,true);assertEquals(once,look.wetness());
        look.tick(1000,false);assertEquals(0,look.wetness());look.tick(1001,true);look.reset();assertEquals(0,look.wetness());
    }
    @Test void RainStaysOutsideTheAimAndDensityOnlyDropsUnderLoad(){
        for(int quality=0;quality<3;quality++)for(int pressure=0;pressure<4;pressure++){
            int count=CockpitFeedbackLook.drops(quality,pressure,1);
            assertTrue(count<=CockpitFeedbackLook.MAX_DROPS&&count<=CockpitFeedbackLook.drops(quality,0,1));
            assertEquals(0,CockpitFeedbackLook.drops(quality,pressure,0));
            for(int i=0;i<count;i++)for(int t=0;t<500;t+=11){
                var drop=CockpitFeedbackLook.drop(t,123,i,2);
                assertTrue(drop.x()>=0&&drop.x()<=1&&drop.y()>=0&&drop.y()<=1);
                assertTrue(drop.x()<=.33F||drop.x()>=.67F);assertTrue(drop.size()>=0&&drop.size()<=1);
            }
        }
    }
    @Test void InstrumentMotionAndDamageDimmingRemainLocalAndBounded(){
        for(int t=0;t<400;t++)assertTrue(Math.abs(CockpitFeedbackLook.shift(t,19,1,1))<=CockpitFeedbackLook.MAX_SHIFT);
        assertEquals(0,CockpitFeedbackLook.shift(1,1,0,0));assertEquals(0,CockpitFeedbackLook.shift(Double.NaN,1,1,1));
        assertEquals(0,CockpitFeedbackLook.shift(1,1,0,1));
        assertTrue(CockpitFeedbackLook.backlight(0,0,1,true)<1);
        assertEquals(1,CockpitFeedbackLook.backlight(0,0,0,true)); // Repair restores a steady lamp.
        assertEquals(.25F,CockpitFeedbackLook.backlight(0,0,1,false));
    }
}
