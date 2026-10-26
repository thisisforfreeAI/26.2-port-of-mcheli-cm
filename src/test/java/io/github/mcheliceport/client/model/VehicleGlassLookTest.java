package io.github.mcheliceport.client.model;

import static org.junit.jupiter.api.Assertions.*;
import org.junit.jupiter.api.Test;
class VehicleGlassLookTest {
    @Test void repeatedHitsSpreadButStopAtBoundedRadiusAndDensity(){
        float radius=0;int stage=0;
        for(int i=0;i<20;i++){float before=radius;radius=VehicleGlassLook.radius(radius,.12F);stage=VehicleGlassLook.stage(stage);assertTrue(radius>=before&&radius<=.6F);}
        assertEquals(.6F,radius);assertEquals(4,stage);
    }
    @Test void mixedCanopyAlphaUsesTextureUvAndWrapsLikeModelSampling(){
        var mask=new VehicleGlassAlpha.Mask(2,2,new byte[]{0,(byte)255,100,(byte)255});
        assertEquals(0,mask.alpha(.1F,.1F));assertEquals(255,mask.alpha(.9F,.1F));
        assertEquals(100,mask.alpha(1.1F,-.1F));assertEquals(255,mask.alpha(Float.NaN,0));
    }
    @Test void sourceUvInterpolatesInsideATriangleAndRejectsDegenerateFaces(){
        float[] d=new float[24];d[8]=2;d[11]=1;d[17]=2;d[20]=1;
        var uv=VehicleImpactSurface.textureUv(d,0,new org.joml.Vector3f(.5F,1,0));
        assertNotNull(uv);assertEquals(.25F,uv.x,1e-6);assertEquals(.5F,uv.y,1e-6);
        assertNull(VehicleImpactSurface.textureUv(new float[24],0,new org.joml.Vector3f()));
    }
}
