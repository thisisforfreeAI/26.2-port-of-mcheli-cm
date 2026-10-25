package io.github.mcheliceport.client.effect;
import static org.junit.jupiter.api.Assertions.*;
import io.github.mcheliceport.client.particle.RotorWashLook;
import io.github.mcheliceport.client.render.VehicleContactLook;
import org.junit.jupiter.api.Test;
class SurfaceVariationTest {
    @Test void scarCoreIsDarkAndOuterCoverageIsBrokenWithoutTileSeams() {
        assertTrue(BlastMarkTuning.scarCoverage(.5F,.5F)>.7);
        for(float u:new float[]{0,1})for(float v:new float[]{0,1})
            assertTrue(BlastMarkTuning.scarCoverage(u,v)>.5,"A small mark on one quad must remain visible");
        assertNotEquals(BlastMarkTuning.scarCoverage(.7F,.5F,0),BlastMarkTuning.scarCoverage(.7F,.5F,57));
        assertTrue((BlastMarkTuning.scarColor(0xCCCCCC,.5F,.5F)&255)<(BlastMarkTuning.scarColor(0xCCCCCC,1,.5F)&255));
        for(float u=0;u<=1;u+=.01F)for(float v=0;v<=1;v+=.1F) {
            float a=BlastMarkTuning.scarCoverage(u,v),b=BlastMarkTuning.scarCoverage(u+.00001F,v);
            assertTrue(a>=0 && a<=1);assertEquals(a,b,.002);
        }
    }
    @Test void washAndTrackVariationNeverMultiplyDensityBeyondTheOriginalBudget() {
        float min=1,max=0;
        for(int i=0;i<300;i++) {
            float p=RotorWashLook.patch(i*.1,i*.23,5,120);
            min=Math.min(min,p);max=Math.max(max,p);assertTrue(p>=0 && p<=1);
            float grain=TrackLook.grain(i*.1,i*.23);assertTrue(grain>=.88 && grain<=1);
        }
        assertTrue(max-min>.25);
        assertEquals(0,VehicleContactLook.distanceWeight(256));
        assertEquals(1,VehicleContactLook.distanceWeight(25));
        assertEquals(VehicleContactLook.distanceWeight(255.9),VehicleContactLook.distanceWeight(256),.001);
    }
}
