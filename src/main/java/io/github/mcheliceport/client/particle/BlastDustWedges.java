package io.github.mcheliceport.client.particle;

import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.Camera;
import net.minecraft.client.particle.ParticleEngine;
import net.minecraft.client.particle.ParticleGroup;
import net.minecraft.client.particle.ParticleRenderType;
import net.minecraft.client.particle.SingleQuadParticle;
import net.minecraft.client.renderer.culling.Frustum;
import net.minecraft.client.renderer.state.level.QuadParticleRenderState;
import net.neoforged.neoforge.client.event.RegisterParticleGroupsEvent;
import org.joml.Quaternionf;
import org.joml.Vector3f;

/** Low triangular dust canopies, batched through vanilla particle shaders/atlas on both backends. */
public final class BlastDustWedges {
    static final ParticleRenderType TYPE = new ParticleRenderType("mcheli_dust_wedges", "m8dw");
    // Three tapered faces; repeated last vertices encode triangles in the vanilla quad stream.
    private static final float[][] POINTS = {{-1,0,-1},{1,0,-1},{-.35F,.02F,1},{.35F,.02F,1},{0,.32F,-.15F}};
    private static final int[][] FACES = {{0,2,4,4},{4,3,1,1},{2,3,4,4}};
    private BlastDustWedges() {}
    static float coordinate(int face, int corner, int axis) { return POINTS[FACES[face][corner]][axis]; }
    public static void register(RegisterParticleGroupsEvent event) { event.register(TYPE, Group::new); }

    private static final class Group extends ParticleGroup<BlastRingParticle> {
        private final State state = new State();
        Group(ParticleEngine engine) { super(engine); }
        @Override public State extractRenderState(Frustum frustum, Camera camera, float partial) {
            state.clear();
            for (BlastRingParticle p : particles) if (frustum.isVisible(p.getBoundingBox().inflate(4)))
                p.extract(state,camera,partial);
            return state;
        }
    }
    static final class State extends QuadParticleRenderState {
        private final Quaternionf rotation = new Quaternionf();
        private final Vector3f vertex = new Vector3f();
        @Override protected void renderRotatedQuad(VertexConsumer buffer, float x,float y,float z,
                float qx,float qy,float qz,float qw,float size,float u0,float u1,float v0,float v1,int color,int light) {
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
