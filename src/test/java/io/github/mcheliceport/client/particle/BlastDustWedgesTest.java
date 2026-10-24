package io.github.mcheliceport.client.particle;
import static org.junit.jupiter.api.Assertions.*;
import com.mojang.blaze3d.vertex.VertexConsumer;
import java.lang.reflect.Proxy;
import java.util.ArrayList;
import net.minecraft.client.particle.SingleQuadParticle;
import org.junit.jupiter.api.Test;
class BlastDustWedgesTest {
    @Test void batchedMeshEmitsThreeRealTriangleFacesInsideItsChargedExtent() {
        var vertices=new ArrayList<float[]>();
        VertexConsumer buffer=(VertexConsumer)Proxy.newProxyInstance(VertexConsumer.class.getClassLoader(),
                new Class<?>[]{VertexConsumer.class},(proxy,method,args)->{
                    if(method.getName().equals("addVertex"))vertices.add(new float[]{(float)args[0],(float)args[1],(float)args[2]});
                    return proxy;
                });
        var state=new BlastDustWedges.State();
        state.add(SingleQuadParticle.Layer.TRANSLUCENT,0,0,0,0,0,0,1,1,0,1,0,1,-1,0);
        assertFalse(state.isEmpty());state.buildLayer(SingleQuadParticle.Layer.TRANSLUCENT,buffer);
        assertEquals(12,vertices.size());
        for(int f=0;f<3;f++) {
            var a=vertices.get(f*4);var b=vertices.get(f*4+1);var c=vertices.get(f*4+2);
            assertArrayEquals(c,vertices.get(f*4+3));
            float nx=(b[1]-a[1])*(c[2]-a[2])-(b[2]-a[2])*(c[1]-a[1]);
            float ny=(b[2]-a[2])*(c[0]-a[0])-(b[0]-a[0])*(c[2]-a[2]);
            float nz=(b[0]-a[0])*(c[1]-a[1])-(b[1]-a[1])*(c[0]-a[0]);
            assertTrue(nx*nx+ny*ny+nz*nz>0.01);assertTrue(ny>0);
        }
        for(var v:vertices)assertTrue(Math.sqrt(v[0]*v[0]+v[1]*v[1]+v[2]*v[2])<1.6);
        state.clear();assertTrue(state.isEmpty());
    }
}
