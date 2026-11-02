package io.github.mcheliceport.client.particle;

import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.Camera;
import io.github.mcheliceport.shim.client.SingleQuadParticle;
import io.github.mcheliceport.shim.client.QuadParticleRenderState;
import org.joml.Quaternionf;
import org.joml.Vector3f;

/** Low triangular dust canopies, batched through vanilla particle shaders/atlas on both backends. */
public final class BlastDustWedges {
    // Three tapered faces; repeated last vertices encode triangles in the vanilla quad stream.
    private static final float[][] POINTS = {{-1,0,-1},{1,0,-1},{-.35F,.02F,1},{.35F,.02F,1},{0,.32F,-.15F}};
    private static final int[][] FACES = {{0,2,4,4},{4,3,1,1},{2,3,4,4}};
    private BlastDustWedges() {}
    static float coordinate(int face, int corner, int axis) { return POINTS[FACES[face][corner]][axis]; }
    static final class State extends QuadParticleRenderState {
        State(VertexConsumer buffer) { super(buffer); }
        boolean billboard;
        @Override public void add(SingleQuadParticle.Layer layer,float x,float y,float z,float qx,float qy,float qz,float qw,
                float size,float u0,float u1,float v0,float v1,int color,int light) {
            // A signed internal size distinguishes the charged fallback card in this same batch.
            super.add(layer,x,y,z,qx,qy,qz,qw,billboard?-size:size,u0,u1,v0,v1,color,light);
        }
        private final Quaternionf rotation = new Quaternionf();
        private final Vector3f vertex = new Vector3f();
        @Override protected void renderRotatedQuad(VertexConsumer buffer, float x,float y,float z,
                float qx,float qy,float qz,float qw,float size,float u0,float u1,float v0,float v1,int color,int light) {
            if(size<0){super.renderRotatedQuad(buffer,x,y,z,qx,qy,qz,qw,-size,u0,u1,v0,v1,color,light);return;}
            rotation.set(qx,qy,qz,qw);
            for(int face=0;face<3;face++)for(int corner=0;corner<4;corner++) {
                vertex.set(coordinate(face,corner,0),coordinate(face,corner,1),coordinate(face,corner,2));
                vertex.mul(size).rotate(rotation).add(x,y,z);
                float u=corner==0?u0:corner==1?u1:(u0+u1)*.5F;
                float v=corner<2?v1:v0;
                buffer.addVertex(vertex.x,vertex.y,vertex.z).setUv(u,v).setColor(color).setLight(light);
            }
        }
    }
}
