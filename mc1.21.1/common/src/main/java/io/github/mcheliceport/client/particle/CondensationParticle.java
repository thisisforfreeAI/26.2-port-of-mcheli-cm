package io.github.mcheliceport.client.particle;

import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.Camera;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.particle.ParticleEngine;
import net.minecraft.client.particle.ParticleGroup;
import net.minecraft.client.particle.ParticleRenderType;
import net.minecraft.client.renderer.culling.Frustum;
import net.minecraft.client.renderer.state.level.QuadParticleRenderState;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.client.event.RegisterParticleGroupsEvent;
import org.joml.Quaternionf;
import org.joml.Vector3f;

/** One thin ambient-lit wisp quad, aligned with its birth flight direction. */
public final class CondensationParticle extends SmokeParticle {
    private static final ParticleRenderType TYPE=new ParticleRenderType("mcheli_condensation","m8cv");
    private final float radius,opacity;
    private final Vector3f axis;
    private final Quaternionf rotation=new Quaternionf();
    private final Vector3f projected=new Vector3f();

    CondensationParticle(ClientLevel level,Vec3 at,Vec3 motion,float radius,float opacity,int life) {
        super(level,at.x,at.y,at.z,motion.x*.03,motion.y*.03,motion.z*.03);
        useBillow(SmokeAtlas.WISPS);setColor(.92F,.94F,.96F);enhanced=true;
        Vec3 direction=motion.lengthSqr()>1E-8?motion.normalize():new Vec3(0,0,1);
        axis=new Vector3f((float)direction.x,(float)direction.y,(float)direction.z);
        this.radius=radius;this.opacity=opacity;lifetime=life;hasPhysics=false;
    }
    @Override public ParticleRenderType getGroup(){return TYPE;}
    @Override protected Layer getLayer(){return ParticleLayers.CONDENSATION;}
    @Override public void tick() {
        xo=x;yo=y;zo=z;if(++age>=lifetime){remove();return;}
        frame=billowFrames?SmokeAtlas.frame(age,lifetime):Math.min(7,age*8/lifetime);
        xd=BlastSmokeMotion.advect(xd,BlastSmokeMotion.windX(level.getGameTime()),.96,age);
        zd=BlastSmokeMotion.advect(zd,BlastSmokeMotion.windZ(level.getGameTime()),.96,age);
        yd*=.96;move(xd,yd,zd);
    }
    @Override public void extract(QuadParticleRenderState state,Camera camera,float partial) {
        float t=age+partial;
        alpha=opacity*EffectLook.smokeAlpha(t,lifetime);
        getFacingCameraMode().setRotation(rotation,camera,partial);
        projected.set(axis);rotation.transformInverse(projected);
        float projectedLength=projected.x*projected.x+projected.y*projected.y;
        if(projectedLength>1.0E-6F)rotation.rotateZ((float)Math.atan2(-projected.x,projected.y));
        extractRotatedQuad(state,camera,rotation,partial);
    }
    @Override public float getQuadSize(float partial){return radius*(.75F+.45F*Math.clamp((age+partial)/lifetime,0,1));}

    public static void register(RegisterParticleGroupsEvent event){event.register(TYPE,Group::new);}
    private static final class Group extends ParticleGroup<CondensationParticle> {
        private final State state=new State();
        Group(ParticleEngine engine){super(engine);}
        @Override public State extractRenderState(Frustum frustum,Camera camera,float partial) {
            state.clear();for(var p:particles)if(frustum.isVisible(p.getBoundingBox().inflate(p.radius*2.2)))p.extract(state,camera,partial);return state;
        }
    }
    private static final class State extends QuadParticleRenderState {
        private final Quaternionf rotation=new Quaternionf();
        private final Vector3f point=new Vector3f();
        @Override protected void renderRotatedQuad(VertexConsumer buffer,float x,float y,float z,
                float qx,float qy,float qz,float qw,float size,float u0,float u1,float v0,float v1,int color,int light) {
            rotation.set(qx,qy,qz,qw);
            for(int corner=0;corner<4;corner++) {
                boolean right=corner==1||corner==2,top=corner>=2;
                point.set((right?1:-1)*(top?.08F:.16F),top?.55F:-1.8F,0).mul(size).rotate(rotation).add(x,y,z);
                buffer.addVertex(point.x,point.y,point.z).setUv(right?u1:u0,top?v0:v1).setColor(color).setLight(light);
            }
        }
    }
}
