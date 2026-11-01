package io.github.mcheliceport.client.particle;

import java.util.List;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.world.phys.Vec3;

/** Finite fire pockets, rising embers and cooler smoke, on the shared atmosphere budget. */
final class WreckEffects {
    private WreckEffects() {}
    static boolean refined(){return io.github.mcheliceport.config.EffectQuality.enhanced()
            && io.github.mcheliceport.config.ClientConfig.get(io.github.mcheliceport.config.ClientConfig.EFFECT_STYLE)
            ==io.github.mcheliceport.config.ClientConfig.EffectStyle.POLISHED;}
    /** Lighting is refreshed independently of particle admission and its per-tick cap. */
    static void light(ClientLevel level, List<Vec3> origins, WreckCoolingLook cooling,
            io.github.mcheliceport.entity.aircraft.AircraftEntity aircraft, long tick) {
        WreckFuelFire.light(level, aircraft, cooling, tick);
        float fire=cooling.fire();
        if(fire<=0||origins.isEmpty())return;
        var sequence=WreckSequenceEffects.get(aircraft);
        Vec3 heart=Vec3.ZERO;
        if(sequence!=null&&sequence.opening!=null&&(sequence.cover||sequence.tail))heart=sequence.opening;
        else {for(Vec3 o:origins)heart=heart.add(o);heart=heart.scale(1.0/origins.size());}
        io.github.mcheliceport.client.render.SceneLights.fire(level,aircraft,heart,fire);
    }
    static void emit(ClientLevel level, List<Vec3> origins, WreckCoolingLook cooling, io.github.mcheliceport.entity.aircraft.AircraftEntity aircraft, long tick) {
        var sequence=WreckSequenceEffects.get(aircraft);
        if(sequence!=null&&sequence.opening!=null&&(sequence.cover||sequence.tail))origins=List.of(sequence.opening);
        int id=aircraft.getId();
        WreckFuelFire.emit(level,aircraft,cooling,tick);
        float fire=cooling.fire(), density=cooling.smoke();
        if (density<=0) density=cooling.lingering();
        if (density<=0) return;
        var engine=Minecraft.getInstance().particleEngine;
        Vec3 origin=origins.get(Math.floorMod((int)(tick/45+id),origins.size()));
        Vec3 pos=origin.add(Math.sin(tick*.7+id)*.09,.05,Math.cos(tick*.43+id)*.09);
        Vec3 ammunition=aircraft instanceof io.github.mcheliceport.entity.tank.TankEntity
                ?aircraft.position().add(io.github.mcheliceport.util.McheliMath.rotVec3(0,2.1,-.45,-aircraft.getYaw(),-aircraft.getPitch(),-aircraft.getRoll())):pos;
        if(sequence!=null&&sequence.turret&&sequence.ring!=null)ammunition=sequence.ring;
        CookoffEffects.emit(level,aircraft,cooling,ammunition);
        int pressure=1+EffectPerformance.pressure();
        if (fire>0 && Math.floorMod(tick+id,5*pressure)==0) {
            float radius=.4F+.5F*fire;
            if (VehicleAtmosphere.admit(level,pos,radius*1.35F)) {
                engine.add(AirflowFireEffects.flame(level,aircraft,pos.add(0,.15,0),radius,.85F*fire));
            }
        }
        if (fire>.1F && Math.floorMod(tick+id,17*pressure)==0
                && VehicleAtmosphere.admit(level,pos,.035F)) {
            engine.add(new SparkParticle(level,pos.x,pos.y,pos.z,
                    Math.sin(tick)*.04,.17,Math.cos(tick)*.04,28,.028F));
        }
        int period=(density>.65F?4:density>.25F?8:16)*pressure;
        if (Math.floorMod(tick+id,period)!=0) return;
        if(refined()){
            if(SmokeBillowLook.quiet(density,tick,id))return;
            float pulse=SmokeBillowLook.surge(tick,id);
            boolean wisp=Math.floorMod(Math.floorDiv(tick+id,period),5)==4;
            float radius=(1.25F+density*.95F)*(wisp?.66F:1);
            Vec3 at=pos.add(Math.sin(tick*.075+id)*.24,wisp?1.05:.15,Math.cos(tick*.075+id)*.24);
            if(!VehicleAtmosphere.admit(level,at,radius))return;
            float variation=Math.floorMod((int)(tick/period)+id,17)/16F;
            engine.add(new WreckSmokeParticle(level,at.x,at.y,at.z,radius,fire,
                    Math.min(.76F,density*(wisp?.38F:.58F)*(.8F+pulse*.35F)),wisp,id,variation));
            return;
        }
        float radius=.7F+density*.6F;
        if (!VehicleAtmosphere.admit(level,pos,radius)) return;
        var smoke=new SmokeParticle(level,pos.x,pos.y+.15,pos.z,0,.045,0);
        smoke.enhanced=true; smoke.coherentDrift=true; smoke.disableCollision();
        float gray=.48F-.3F*fire; smoke.setColor(gray,gray,gray);
        smoke.setAlphaF(.65F*density); smoke.setParticleMaxAge(55);
        smoke.motionYUpAge=.7F; smoke.diffusible=true;
        smoke.particleScale=4; smoke.particleMaxScale=radius*10;
        smoke.heat=fire*.25F; engine.add(smoke);
    }
}
