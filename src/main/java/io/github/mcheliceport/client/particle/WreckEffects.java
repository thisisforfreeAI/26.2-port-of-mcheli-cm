package io.github.mcheliceport.client.particle;

import java.util.List;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.world.phys.Vec3;

/** Finite fire pockets, rising embers and cooler smoke, on the shared atmosphere budget. */
final class WreckEffects {
    private WreckEffects() {}
    static void emit(ClientLevel level, List<Vec3> origins, WreckCoolingLook cooling, io.github.mcheliceport.entity.aircraft.AircraftEntity aircraft, long tick) {
        int id=aircraft.getId();
        float fire=cooling.fire(), density=cooling.smoke();
        if (density<=0) return;
        var engine=Minecraft.getInstance().particleEngine;
        Vec3 origin=origins.get(Math.floorMod((int)(tick/9+id),origins.size()));
        Vec3 pos=origin.add(Math.sin(tick*.7+id)*.22,.2,Math.cos(tick*.43+id)*.22);
        int pressure=1+EffectPerformance.pressure();
        if (fire>0 && Math.floorMod(tick+id,5*pressure)==0) {
            float radius=.55F+.55F*fire;
            if (VehicleAtmosphere.admit(level,pos,radius*1.35F)) {
                engine.add(new WreckFlameParticle(level,pos.x,pos.y+.15,pos.z,radius,.85F*fire));
            }
        }
        if (fire>.1F && Math.floorMod(tick+id,13*pressure)==0
                && VehicleAtmosphere.admit(level,pos,.035F)) {
            engine.add(new SparkParticle(level,pos.x,pos.y,pos.z,
                    Math.sin(tick)*.04,.17,Math.cos(tick)*.04,18,.035F));
        }
        int period=(density>.65F?4:density>.25F?8:16)*pressure;
        if (Math.floorMod(tick+id,period)!=0) return;
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
