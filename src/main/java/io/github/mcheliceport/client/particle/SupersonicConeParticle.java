package io.github.mcheliceport.client.particle;

import com.mojang.blaze3d.vertex.VertexConsumer;
import io.github.mcheliceport.config.*;
import io.github.mcheliceport.damage.AirframeDamage;
import io.github.mcheliceport.entity.plane.PlaneEntity;
import io.github.mcheliceport.util.McheliMath;
import java.lang.ref.WeakReference;
import net.minecraft.client.Camera;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.particle.*;
import net.minecraft.client.renderer.culling.Frustum;
import net.minecraft.client.renderer.state.level.QuadParticleRenderState;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.client.event.RegisterParticleGroupsEvent;
import org.joml.Quaternionf;
import org.joml.Vector3f;

/** Eight translucent wisp faces attached briefly to the moving airframe. No scene/depth reads. */
public final class SupersonicConeParticle extends SmokeParticle {
    private static final ParticleRenderType TYPE=new ParticleRenderType("mcheli_sonic_cone","m8sc");
    private final WeakReference<PlaneEntity> plane;
    private final Vec3 local;
    private final float radius,opacity;
    private Vec3 previousSource;
    private final Quaternionf rotation=new Quaternionf();
    SupersonicConeParticle(ClientLevel level,PlaneEntity plane,Vec3 local,float radius,float wetness){
        super(level,plane.getX(),plane.getY(),plane.getZ(),0,0,0);
        this.plane=new WeakReference<>(plane);this.local=local;this.radius=radius;opacity=.5F*wetness;
        previousSource=plane.position();
        useBillow(SmokeAtlas.EXPLOSION);setColor(.93F,.95F,.98F);enhanced=true;lifetime=14;hasPhysics=false;
        follow(plane);xo=x;yo=y;zo=z;
    }
    private void follow(PlaneEntity ac){
        var at=ac.position().add(McheliMath.rotVec3(local,-ac.getYaw(),-ac.getPitch(),-ac.getRoll()))
                .add(0,io.github.mcheliceport.content.aircraft.AircraftInfo.GLOBAL_Y_OFFSET,0);
        setPos(at.x,at.y,at.z);
        var axis=McheliMath.rotVec3(0,0,-1,-ac.getYaw(),-ac.getPitch(),-ac.getRoll());
        rotation.rotationTo(0,0,1,(float)axis.x,(float)axis.y,(float)axis.z);
    }
    @Override public ParticleRenderType getGroup(){return TYPE;}
    @Override protected Layer getLayer(){return ParticleLayers.CONDENSATION;}
    @Override public void tick(){
        xo=x;yo=y;zo=z;var ac=plane.get();
        if(++age>=lifetime||ac==null||ac.isRemoved()||ac.level()!=level||ac.getAcInfo()==null||ac.isDestroyed()||ac.onGround()||ac.isInWater()||AirframeDamage.missing(ac)!=0
                ||!EffectQuality.enhanced()||!ClientConfig.get(ClientConfig.SUPERSONIC_EFFECTS)||!ClientConfig.get(ClientConfig.AIRCRAFT_CONDENSATION)){
            remove();return;
        }
        double step=Math.max(8,ac.getAcInfo().machOneSpeed*CommonConfig.ALL_PLANE_SPEED.get()*6);
        if(previousSource.distanceToSqr(ac.position())>step*step){remove();return;}previousSource=ac.position();
        follow(ac);frame=SmokeAtlas.frame(age,lifetime);
    }
    @Override public void extract(QuadParticleRenderState state,Camera camera,float partial){
        alpha=opacity*EffectLook.smokeAlpha(age+partial,lifetime);extractRotatedQuad(state,camera,rotation,partial);
    }
    @Override public float getQuadSize(float partial){return radius*(.92F+.08F*(float)Math.sin((age+partial)*Math.PI/lifetime));}
    public static void register(RegisterParticleGroupsEvent event){event.register(TYPE,Group::new);}
    private static final class Group extends ParticleGroup<SupersonicConeParticle>{
        private final State state=new State();Group(ParticleEngine engine){super(engine);}
        @Override public State extractRenderState(Frustum frustum,Camera camera,float partial){
            state.clear();for(var p:particles)if(frustum.isVisible(p.getBoundingBox().inflate(p.radius*1.5)))p.extract(state,camera,partial);return state;
        }
    }
    private static final class State extends QuadParticleRenderState {
        private final Quaternionf rotation=new Quaternionf();private final Vector3f vertex=new Vector3f();
        @Override protected void renderRotatedQuad(VertexConsumer out,float x,float y,float z,float qx,float qy,float qz,float qw,
                float size,float u0,float u1,float v0,float v1,int color,int light){
            rotation.set(qx,qy,qz,qw);
            for(int face=0;face<8;face++)for(int corner=0;corner<4;corner++){
                boolean wide=corner>=2;double angle=(face+(corner==1||corner==2?1:0))*Math.PI/4;
                float r=wide?1:.16F;
                vertex.set((float)Math.cos(angle)*r,(float)Math.sin(angle)*r,wide?.8F:-.45F).mul(size).rotate(rotation).add(x,y,z);
                out.addVertex(vertex.x,vertex.y,vertex.z).setUv(corner==1||corner==2?u1:u0,wide?v1:v0).setColor(color).setLight(light);
            }
        }
    }
}
