package io.github.mcheliceport.client.particle;

import static org.junit.jupiter.api.Assertions.*;
import io.github.mcheliceport.config.ClientConfig.EffectBudget;
import org.junit.jupiter.api.Test;
class BlastReadabilityTest {
    @Test void distantBlastsKeepCoresAndNeverIncreaseRequests() {
        for(int total=2;total<=28;total++) {
            int previous=total;
            for(int distance=0;distance<=300;distance++) {
                int count=BlastReadability.distantLobes(total,distance*distance);
                assertTrue(count>=2 && count<=previous);previous=count;
            }
        }
        assertEquals(0,BlastReadability.distantLobes(0,100));
    }
    @Test void coldSmokeNeverUsesFireFramesAndPocketLightExpires() {
        for(int phase=0;phase<4;phase++)for(int age=0;age<128;age++) {
            int frame=BlastReadability.smokeFrame(age,0,100,phase);
            assertTrue(frame>=12 && frame<=15);
            float opacity=BlastReadability.pocketOpacity(age,16,phase);
            assertTrue(opacity>=0 && opacity<=1);
            assertTrue(BlastReadability.amber(age,16)>=0 && BlastReadability.amber(age,16)<=1);
        }
        assertEquals(0,BlastReadability.amber(16,16));
        assertEquals(0,BlastReadability.amber(-1,16));
        assertEquals(0,BlastReadability.amber(0,0));
        assertTrue(BlastReadability.pocketOpacity(14,16,0)<BlastReadability.pocketOpacity(2,16,0));
    }
    @Test void fireToSmokeOpacityHasNoBoundaryJump() {
        for(int duration:new int[]{1,4,8,16,45})for(int phase=0;phase<4;phase++) {
            float before=BlastReadability.pocketOpacity(duration-.001F,duration,phase);
            float after=BlastReadability.pocketOpacity(duration+.001F,duration,phase);
            assertEquals(before,after,.002F);
            float previous=after;
            for(float age=duration+.1F;age<=duration+6;age+=.1F) {
                float opacity=BlastReadability.pocketOpacity(age,duration,phase);
                assertTrue(opacity>=previous && opacity<=1);previous=opacity;
            }
            assertEquals(1,BlastReadability.pocketOpacity(duration+6,duration,phase));
        }
    }
    @Test void extraPlanesFadeContinuouslyAndShedUnderPressure() {
        for(var quality:new EffectBudget[]{EffectBudget.BALANCED,EffectBudget.HIGH}) {
            int range=quality==EffectBudget.HIGH?64:48;
            float previous=1;
            for(double d=0;d<=range;d+=.1) {
                float w=BlastDepthLook.visibility(quality,0,d*d);
                assertTrue(w>=0 && w<=previous+1e-6);assertTrue(previous-w<.03);previous=w;
            }
            assertEquals(0,BlastDepthLook.visibility(quality,0,range*range));
            assertEquals(0,BlastDepthLook.visibility(quality,1,1));
        }
    }
}
