package io.github.mcheliceport.client.model;
import static org.junit.jupiter.api.Assertions.*;
import org.junit.jupiter.api.Test;
class SmallMotionMeshTest {
    @Test void unknownModelsRemainUntouchedAndMotionIsRestrained(){
        assertNull(SmallMotionMesh.build("addon",new McheliModel.Group("$weapon0",new float[24])));
        for(int t=0;t<1000;t++)assertTrue(Math.abs(SmallMotionMesh.sway(t,47,true))<=.0251F);
    }
    @Test void selectedWhipIsSplitWithContinuousUvsAndSourceIsUnchanged(){
        float[] data={.44F,2,-.94F,0,0,1,0,0, .48F,2.8F,-.94F,1,1,1,0,0, .44F,2.8F,-.94F,0,1,1,0,0,
            3,1,0,0,0,0,1,0, 4,1,0,1,0,0,1,0, 3,1,1,0,1,0,1,0};
        var original=data.clone();var parts=SmallMotionMesh.build("t-90",new McheliModel.Group("$weapon0",data));
        assertNotNull(parts);assertArrayEquals(original,data);assertEquals(3,parts.fixed().vertexCount());assertEquals(1,parts.whips().size());
        var whip=parts.whips().getFirst();assertTrue(whip.lower().vertexCount()>0&&whip.upper().vertexCount()>0);
        for(var g:new McheliModel.Group[]{whip.lower(),whip.upper()})for(int i=0;i<g.data().length;i+=8){assertTrue(g.data()[i+3]>=0&&g.data()[i+3]<=1);assertTrue(g.data()[i+4]>=0&&g.data()[i+4]<=1);}
    }
}
