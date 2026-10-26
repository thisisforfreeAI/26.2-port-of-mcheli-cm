package io.github.mcheliceport.client.model;
import static org.junit.jupiter.api.Assertions.*;
import java.util.Arrays;
import org.joml.Vector3f;
import org.junit.jupiter.api.Test;
class RotorDamageMeshTest {
    @Test void tipDamageLeavesTheSourceAndOppositeBladeIntact(){
        float[] d=new float[72];float[][] points={{4,0,-.1F},{5,0,-.1F},{5,0,.1F},{4,0,-.1F},{5,0,.1F},{4,0,.1F},{-3,0,-.1F},{-2,0,.1F},{-3,0,.1F}};
        for(int i=0;i<points.length;i++){System.arraycopy(points[i],0,d,i*8,3);d[i*8+6]=1;}
        float[] original=d.clone();var group=new McheliModel.Group("$blade0",d);
        var result=RotorDamageMesh.build(group,new Vector3f(),new Vector3f(0,1,0),true);
        assertNotNull(result);assertArrayEquals(original,d);assertTrue(result.bentVertices()>0);assertTrue(result.group().vertexCount()<=group.vertexCount());
        for(int i=0;i<result.group().data().length;i+=8)if(result.group().data()[i]<0)assertEquals(0,result.group().data()[i+1]);
    }
    @Test void ordinaryDamageDoesNotWobbleAndUnknownHugeMeshesAreSkipped(){
        assertEquals(0,RotorDamageMesh.stage(.5F));assertEquals(2,RotorDamageMesh.stage(.95F));
        assertEquals(0,RotorDamageMesh.wobble(.2F,100,7),0F);
        assertNull(RotorDamageMesh.build(new McheliModel.Group("$blade0",new float[24003*8]),new Vector3f(),new Vector3f(0,1,0),true));
    }
}
