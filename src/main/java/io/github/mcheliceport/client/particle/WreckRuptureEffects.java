package io.github.mcheliceport.client.particle;

import io.github.mcheliceport.entity.aircraft.AircraftEntity;
import io.github.mcheliceport.client.render.SceneLights;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.core.BlockPos;
import net.minecraft.sounds.*;
import net.minecraft.util.RandomSource;
import net.minecraft.world.phys.Vec3;

/** Short, directed machinery ruptures. Two events per world tick, on shared particle admission. */
public final class WreckRuptureEffects {
    private static ClientLevel world;private static long tick=Long.MIN_VALUE;
    private static int events,primary,secondary;
    private WreckRuptureEffects(){}
    public static void clear(){world=null;tick=Long.MIN_VALUE;events=primary=secondary=0;}
    public static DestructionLook.Kind kind(AircraftEntity ac){
        return ac instanceof io.github.mcheliceport.entity.tank.TankEntity?DestructionLook.Kind.TANK:
                ac instanceof io.github.mcheliceport.entity.heli.HeliEntity?DestructionLook.Kind.HELICOPTER:
                ac instanceof io.github.mcheliceport.entity.plane.PlaneEntity?DestructionLook.Kind.PLANE:DestructionLook.Kind.OTHER;
    }
    public static Vec3 direction(AircraftEntity ac){
        var outlets=EngineExhaustLook.outlets(ac.getAcInfo(),0);
        var local=DestructionLook.direction(outlets.isEmpty()?null:outlets.getFirst().direction(),kind(ac));
        return io.github.mcheliceport.util.McheliMath.rotVec3(local,-ac.getYaw(),-ac.getPitch(),-ac.getRoll()).normalize();
    }
    public static boolean emit(ClientLevel level,AircraftEntity ac,Vec3 at,boolean opening){
        if(!WreckSequenceEffects.enabled()||!ac.isDestroyed()||at==null
                ||Minecraft.getInstance().gameRenderer.mainCamera().position().distanceToSqr(at)>40*40)return false;
        var block=BlockPos.containing(at);
        if(!level.hasChunkAt(block)||!level.getFluidState(block).isEmpty())return false;
        if(world!=level){clear();world=level;}
        if(tick!=level.getGameTime()){tick=level.getGameTime();events=0;}
        if(events>=2||!VehicleAtmosphere.admit(level,at,.8F))return false;
        events++;if(opening)primary++;else secondary++;
        var engine=Minecraft.getInstance().particleEngine;
        int seed=ac.getUUID().hashCode(),age=VehicleAtmosphere.wreckAge(ac);
        float size=DestructionLook.scale(kind(ac))*(opening?1:DestructionLook.burstStrength(seed,age)*.65F);
        var direction=direction(ac);var side=direction.cross(new Vec3(0,1,0));
        if(side.lengthSqr()<1e-4)side=new Vec3(1,0,0);else side=side.normalize();
        var up=side.cross(direction).normalize();
        engine.add(new ImpactCoreParticle(level,at.x,at.y,at.z,size,opening?3:2,true));
        SceneLights.muzzle(level,at.x,at.y,at.z,size,1,.78F,.5F);
        var state=WreckSequenceEffects.get(ac);if(state!=null)state.flashTick=level.getGameTime();
        int pressure=1+EffectPerformance.pressure(),count=(opening?8:4)/pressure;
        for(int i=0;i<count;i++){
            double angle=seed*.13+i*2.399,spread=.08+(i%3)*.06;
            var velocity=direction.scale((opening?.28:.17)+(i%4)*.065).add(side.scale(Math.cos(angle)*spread)).add(up.scale(Math.sin(angle)*spread));
            if(VehicleAtmosphere.admit(level,at,.04F))engine.add(new SparkParticle(level,at.x,at.y,at.z,velocity.x,velocity.y,velocity.z,12+i%4*3,.035F+i%2*.015F));
        }
        for(int i=0;i<(opening?2:1);i++){
            var p=at.add(direction.scale(i*.22));
            if(VehicleAtmosphere.admit(level,p,.4F))engine.add(new WreckFlameParticle(level,p.x,p.y,p.z,.3F+size*.22F,.8F));
        }
        if(VehicleAtmosphere.admit(level,at,.55F)){
            var puff=new SmokeParticle(level,at.x,at.y,at.z,direction.x*.065,direction.y*.065,direction.z*.065);
            puff.enhanced=true;puff.coherentDrift=true;puff.disableCollision();puff.useBillow(SmokeAtlas.WISPS);
            puff.setColor(.31F,.29F,.26F);puff.setAlphaF(.4F);puff.setParticleMaxAge(26);puff.diffusible=true;
            puff.particleScale=2;puff.particleMaxScale=7*size;engine.add(puff);
        }
        if(opening&&VehicleAtmosphere.admit(level,at,.04F)){
            var v=direction.scale(.2).add(side.scale(.1));
            engine.add(new SurfaceDebrisParticle(level,at.x,at.y,at.z,v.x,v.y+.13,v.z,.065F,.4F,.38F,.34F,Double.NEGATIVE_INFINITY,28,.32F));
        }
        var sound=opening?SoundEvents.ANVIL_BREAK:SoundEvents.FIREWORK_ROCKET_BLAST;
        Minecraft.getInstance().getSoundManager().play(new SimpleSoundInstance(sound,SoundSource.BLOCKS,
                opening?.55F:.35F+size*.25F,opening?.7F:1.05F+size*.25F,RandomSource.create(seed+age),at.x,at.y,at.z));
        return true;
    }
    public static int primary(){return primary;}public static int secondary(){return secondary;}
}
