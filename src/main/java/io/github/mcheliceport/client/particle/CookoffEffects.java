package io.github.mcheliceport.client.particle;

import io.github.mcheliceport.entity.aircraft.AircraftEntity;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.sounds.*;
import net.minecraft.util.RandomSource;
import net.minecraft.world.phys.Vec3;

/** Tiny local bursts on the shared atmosphere admission; at most two pops per world tick. */
public final class CookoffEffects {
    private static long lastTick=Long.MIN_VALUE;private static int births,events,sounds;
    private CookoffEffects(){}
    public static void clear(){lastTick=Long.MIN_VALUE;births=events=sounds=0;}
    public static int events(){return events;}
    public static int sounds(){return sounds;}
    public static int conventionalAmmo(AircraftEntity aircraft){
        int ammunition=0;
        for(int i=0;i<Math.min(16,aircraft.getWeaponNum());i++){
            var set=aircraft.getWeapon(i);
            if(set==null||set.getFirstWeapon()==null||set.getInfo()==null||set.getInfo().useHBM||set.getInfo().payloadNTM!=io.github.mcheliceport.content.weapon.WeaponInfo.Payload.NONE)continue;
            ammunition=(int)Math.min(256L,ammunition+(long)Math.max(0,set.getAmmo())+Math.max(0,set.getRestAllAmmoNum()));
        }
        return ammunition;
    }
    public static boolean explosiveStore(io.github.mcheliceport.content.weapon.WeaponInfo info){
        return info!=null&&!info.useHBM&&info.payloadNTM==io.github.mcheliceport.content.weapon.WeaponInfo.Payload.NONE&&!info.effectOnly&&info.explosion>0;
    }
    public static int explosiveAmmo(AircraftEntity aircraft){
        int ammunition=0;for(int i=0;i<Math.min(16,aircraft.getWeaponNum());i++){
            var set=aircraft.getWeapon(i);if(set==null||set.getFirstWeapon()==null||!explosiveStore(set.getInfo()))continue;
            ammunition=(int)Math.min(256L,ammunition+(long)Math.max(0,set.getAmmo())+Math.max(0,set.getRestAllAmmoNum()));
        }return ammunition;
    }
    static void emit(ClientLevel level,AircraftEntity aircraft,WreckCoolingLook cooling,Vec3 position){
        if(!CookoffLook.candidate(cooling.age(),aircraft.getId()))return;
        if(Minecraft.getInstance().gameRenderer.mainCamera().position().distanceToSqr(position)>40*40)return;
        int ammunition=conventionalAmmo(aircraft);
        if(!cooling.cookoff(aircraft.getId(),ammunition))return;
        long now=level.getGameTime();if(now!=lastTick){lastTick=now;births=0;}
        if(births>=2)return;
        if(WreckSequenceEffects.enabled()){
            if(WreckRuptureEffects.emit(level,aircraft,position,false)){births++;events++;sounds++;}
            return;
        }
        if(!VehicleAtmosphere.admit(level,position,.65F))return;
        var engine=Minecraft.getInstance().particleEngine;
        engine.add(new ImpactCoreParticle(level,position.x,position.y+.15,position.z,.5F,3));births++;events++;
        for(int i=0;i<3;i++)if(VehicleAtmosphere.admit(level,position,.04F)){
            double angle=i*2.4+aircraft.getId();
            engine.add(new SparkParticle(level,position.x,position.y,position.z,Math.cos(angle)*.1,.12+i*.02,Math.sin(angle)*.1,12,.035F));
        }
        if(VehicleAtmosphere.admit(level,position,.6F)){
            var smoke=new SmokeParticle(level,position.x,position.y+.2,position.z,0,.04,0);
            smoke.enhanced=true;smoke.coherentDrift=true;smoke.disableCollision();smoke.setColor(.29F,.27F,.24F);
            smoke.setAlphaF(.45F);smoke.setParticleMaxAge(23);smoke.diffusible=true;smoke.particleScale=2;smoke.particleMaxScale=6;
            engine.add(smoke);
        }
        Minecraft.getInstance().getSoundManager().play(new SimpleSoundInstance(SoundEvents.FIREWORK_ROCKET_BLAST,
                SoundSource.BLOCKS,.65F,1.15F,RandomSource.create(),position.x,position.y,position.z));sounds++;
    }
}
