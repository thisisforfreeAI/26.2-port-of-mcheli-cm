package io.github.mcheliceport.client.particle;

import io.github.mcheliceport.client.render.WreckScene;
import io.github.mcheliceport.entity.aircraft.AircraftEntity;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.world.phys.Vec3;

/**
 * The pool of burning fuel a crashed plane or helicopter lies in: flames spread across the ground under
 * the wreck with heavy black smoke, then die down. It replaces the fire blocks upstream scattered around
 * a wreck. Fresh crashes only; joining near an old wreck never relights it.
 */
public final class WreckFuelFire {
    /** Ticks at full strength, then ticks to die out. */
    public static final int FULL=140,DECAY=620;
    private WreckFuelFire(){}
    /** Pool fire strength for a wreck {@code age} ticks old (the cooling age), 0 to 1. */
    public static float strength(int age){
        if(age<0)return 0;if(age<FULL)return 1;return Math.max(0,1-(age-FULL)/(float)DECAY);
    }
    static void emit(ClientLevel level,AircraftEntity aircraft,WreckCoolingLook cooling,long tick){
        var scene=WreckScene.get(aircraft);
        if(scene==null||!scene.fresh()||scene.position==null||tick-scene.impactTick<3)return;
        float fire=strength(cooling.age());if(fire<=0)return;
        var camera=Minecraft.getInstance().gameRenderer.mainCamera().position();
        if(camera.distanceToSqr(scene.position)>64*64)return;
        float radius=io.github.mcheliceport.client.effect.WreckGround.poolRadius(scene.size())
                *io.github.mcheliceport.client.effect.WreckGround.poolGrowth(tick-scene.impactTick);
        var engine=Minecraft.getInstance().particleEngine;int id=aircraft.getId();int pressure=1+EffectPerformance.pressure();
        double ground=scene.position.y;
        // Two or three tongues of flame a tick at full strength, anywhere on the pool; they shrink as it dies.
        int flames=Math.max(1,Math.round((1+2*fire)/pressure));
        for(int i=0;i<flames;i++){
            if(fire<.35F&&Math.floorMod(tick+id+i,3)!=0)continue;
            double a=hash(tick,id,i*2)*Math.PI*2,r=Math.sqrt(hash(tick,id,i*2+1))*radius;
            var at=new Vec3(scene.position.x+Math.cos(a)*r,ground+.12,scene.position.z+Math.sin(a)*r);
            float size=(.55F+.55F*fire)*(float)(1-.35*r/Math.max(.1,radius));
            if(VehicleAtmosphere.admit(level,at,size*1.3F))engine.add(new WreckFlameParticle(level,at.x,at.y,at.z,size,.9F*Math.min(1,fire*1.4F)));
        }
        if(fire>.2F&&Math.floorMod(tick+id,6)==0&&VehicleAtmosphere.admit(level,scene.position,.04F)){
            double a=hash(tick,id,9)*Math.PI*2;
            engine.add(new SparkParticle(level,scene.position.x+Math.cos(a)*radius*.5,ground+.2,scene.position.z+Math.sin(a)*radius*.5,
                    Math.cos(a)*.03,.16,Math.sin(a)*.03,30,.03F));
        }
        // Burning fuel smokes black and thick, well above the hull's own plume.
        if(Math.floorMod(tick+id,4*pressure)!=0)return;
        var at=scene.position.add(Math.sin(tick*.21+id)*radius*.4,.6,Math.cos(tick*.17+id)*radius*.4);
        float size=1.4F+radius*.55F*fire;
        if(!VehicleAtmosphere.admit(level,at,size))return;
        var smoke=new WreckSmokeParticle(level,at.x,at.y,at.z,size,fire,Math.min(.85F,.45F+.4F*fire),false,id+7,hash(tick,id,5));
        smoke.soot=.30F+.25F*(1-fire);engine.add(smoke);
    }
    private static float hash(long tick,int id,int salt){
        long h=tick*0x9E3779B97F4A7C15L^(long)id*0xC2B2AE3D27D4EB4FL^salt*0x165667B19E3779F9L;h^=h>>>29;h*=0xBF58476D1CE4E5B9L;h^=h>>>32;
        return (h&0xFFFFFF)/(float)0x1000000;
    }
}
