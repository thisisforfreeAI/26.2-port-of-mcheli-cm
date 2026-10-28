package io.github.mcheliceport.client.particle;

import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.Camera;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.particle.ParticleEngine;
import net.minecraft.client.particle.ParticleGroup;
import net.minecraft.client.particle.ParticleRenderType;
import net.minecraft.client.renderer.culling.Frustum;
import net.minecraft.client.renderer.state.level.QuadParticleRenderState;
import net.minecraft.util.LightCoordsUtil;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.client.event.RegisterParticleGroupsEvent;
import org.joml.Quaternionf;
import org.joml.Vector3f;

/** One tapered flame quad per admitted particle, through the vanilla atlas/depth pipeline. */
public final class AirflowFireParticle extends McheliParticle {
    private static final ParticleRenderType TYPE=new ParticleRenderType("mcheli_airflow_fire","m8af");
    private final float radius,opacity;
    private final Vector3f axis;
    private final Quaternionf rotation=new Quaternionf();
    private final Vector3f projected=new Vector3f();
    private int frame;

    AirflowFireParticle(ClientLevel level,Vec3 at,Vec3 motion,float radius,float opacity) {
        super(level,at.x,at.y,at.z,0,0,0,BlastCloudParticle.TEXTURE);
        Vec3 bounded=AirflowFireLook.motion(motion),velocity=AirflowFireLook.velocity(bounded),direction=AirflowFireLook.axis(bounded);
        xd=velocity.x;yd=velocity.y;zd=velocity.z;
        axis=new Vector3f((float)direction.x,(float)direction.y,(float)direction.z);
        this.radius=radius;this.opacity=opacity;lifetime=AirflowFireLook.lifetime(bounded);hasPhysics=false;
    }
    @Override public ParticleRenderType getGroup(){return TYPE;}
    @Override public void tick() {
        xo=x;yo=y;zo=z;if(++age>=lifetime){remove();return;}
        move(xd,yd,zd);xd*=.94;zd*=.94;yd=yd*.94+.004;
    }
    @Override public void extract(QuadParticleRenderState state,Camera camera,float partial) {
        float t=age+partial;frame=Math.min(7,(int)(t*8/lifetime));
        alpha=opacity*Math.clamp(t/2,0,1)*Math.clamp((lifetime-t)/5,0,1);
        getFacingCameraMode().setRotation(rotation,camera,partial);
        projected.set(axis);rotation.transformInverse(projected);
        float projectedLength=projected.x*projected.x+projected.y*projected.y;
        if(projectedLength>1.0E-6F)rotation.rotateZ((float)Math.atan2(-projected.x,projected.y));
        extractRotatedQuad(state,camera,rotation,partial);
    }
    @Override public float getQuadSize(float partial){return radius*(.88F+.12F*(float)Math.sin((age+partial)*.7));}
    @Override protected float getU0(){return frameU(frame%4,4,false);}
    @Override protected float getU1(){return frameU(frame%4,4,true);}
    @Override protected float getV0(){return sprite.getV(frame/4*.25F);}
    @Override protected float getV1(){return sprite.getV((frame/4+1)*.25F);}
    @Override protected int getLightCoords(float partial){return LightCoordsUtil.FULL_BRIGHT;}

    public static void register(RegisterParticleGroupsEvent event){event.register(TYPE,Group::new);}
    private static final class Group extends ParticleGroup<AirflowFireParticle> {
        private final State state=new State();
        Group(ParticleEngine engine){super(engine);}
        @Override public State extractRenderState(Frustum frustum,Camera camera,float partial) {
            state.clear();for(var p:particles)if(frustum.isVisible(p.getBoundingBox().inflate(p.radius*1.6)))p.extract(state,camera,partial);return state;
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
                point.set((right?1:-1)*(top?.5F:.75F),top?1.5F:-.9F,0).mul(size).rotate(rotation).add(x,y,z);
                buffer.addVertex(point.x,point.y,point.z).setUv(right?u1:u0,top?v0:v1).setColor(color).setLight(light);
            }
        }
    }
}
