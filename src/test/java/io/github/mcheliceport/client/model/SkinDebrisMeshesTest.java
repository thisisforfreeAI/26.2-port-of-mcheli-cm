package io.github.mcheliceport.client.model;

import static org.junit.jupiter.api.Assertions.*;
import org.junit.jupiter.api.Test;

class SkinDebrisMeshesTest {
    @Test void smallPiecesKeepTheirPaintUvsWithoutChangingTheAircraft() {
        var boxes=new DetailBoxes();boxes.box(-7,0,-10,7,2,6);var body=boxes.group("$body");
        var original=body.data().clone();var frame=BreakupMeshes.airframeBuild(body,7);
        for(int i=0;i<2;i++) {
            int axis=0,sign=i==1?1:-1;float cut=i==0?frame.leftCut():frame.rightCut();
            var source=frame.sections().get(i);var saved=source.data().clone();var chips=SkinDebrisMeshes.build(source,axis,cut,sign);
            assertEquals(3,chips.size());
            for(var chip:chips) {
                assertTrue(chip.skin().vertexCount()<=192);assertTrue(BreakupMeshes.area(chip.skin())<=.5);
                var d=chip.skin().data();for(int p=0;p<d.length;p+=8) {
                    for(int n=0;n<8;n++)assertTrue(Float.isFinite(d[p+n]));
                    assertTrue((d[p+axis]-cut)*sign>=-.002);
                    assertTrue(d[p+3]>=0&&d[p+3]<=1);assertTrue(d[p+4]>=0&&d[p+4]<=1);
                    // The sampled top skin's UVs keep the original box's linear world-space mapping.
                    assertEquals((d[p+2]-(-10F))/16F,d[p+3],.0001);
                    assertEquals(1-(d[p]-(-7F))/14F,d[p+4],.0001);
                }
            }
            assertArrayEquals(saved,source.data());
        }
        assertArrayEquals(original,body.data());
    }
    @Test void absentCutsAndUnsupportedInputsDoNotInventFloatingSkin() {
        var b=new DetailBoxes();b.box(-1,0,-1,1,1,1);var source=b.group("$body");
        assertTrue(SkinDebrisMeshes.build(source,0,0,-1).isEmpty());
        assertTrue(SkinDebrisMeshes.build(source,1,0,-1).isEmpty());
        assertTrue(SkinDebrisMeshes.build(source,0,Float.NaN,-1).isEmpty());
        assertTrue(SkinDebrisMeshes.build(new McheliModel.Group("$body",new float[9003*8]),0,0,-1).isEmpty());
    }
}
