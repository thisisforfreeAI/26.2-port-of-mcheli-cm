package io.github.mcheliceport.client.particle;

import io.github.mcheliceport.config.*;
import io.github.mcheliceport.content.ContentRegistries;
import io.github.mcheliceport.content.weapon.WeaponInfo;
import io.github.mcheliceport.entity.weapon.*;
import io.github.mcheliceport.entity.plane.PlaneEntity;
import io.github.mcheliceport.registry.ModEntityTypes;
import java.util.*;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Screenshot;
import net.minecraft.world.phys.Vec3;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.*;

/** Real weapon definitions/models on held trajectories through BaseBullet.spawnParticle, not combat. */
@EventBusSubscriber(modid="mcheli_ce_port",value=Dist.CLIENT)
public final class ProjectileTrailProbe {
    private static final boolean ENABLED=Boolean.getBoolean("mcheli.probeProjectileTrails");
    private static final int START=Integer.getInteger("mcheli.probeProjectileStart",0);
    private static final String[] NAMES={"hydra70","aim9","agm84","agm158-smokeless","authored-burnout","classic","ntm-bypass","shared-load","low-load"};
    private static final List<BaseBulletEntity> shots=new ArrayList<>();private static PlaneEntity carrier;
    private static WeaponInfo definition;private static int ticks,phase=-1,age,peak,trailPeak;private static String capture;
    private static void hold(BaseBulletEntity shot){
        int i=shots.indexOf(shot);shot.xo=shot.getX();shot.yo=shot.getY();shot.zo=shot.getZ();shot.tickCount++;
        double t=Math.max(0,Math.min(140,age-40));
        Vec3 at=new Vec3(Math.sin(t*.025)*7+(phase>=7?(i%4-1.5)*2:0),-40+(phase>=7?i/4*.5:0),20+t*.85);
        shot.setPos(at.x,at.y,at.z);Vec3 motion=at.subtract(shot.xo,shot.yo,shot.zo);shot.setDeltaMovement(motion);
        shot.prevMotionX=motion.x;shot.prevMotionY=motion.y;shot.prevMotionZ=motion.z;
        shot.yRotO=shot.getYRot();shot.setYRot((float)Math.toDegrees(Math.atan2(-motion.x,motion.z)));
        if(age>=40&&age<180&&!definition.disableSmoke)shot.spawnParticle(definition.trajectoryParticleName,3,
                definition.smokeSize*(shot instanceof ASMissileEntity?5:shot instanceof AAMissileEntity?3.5F:2.5F));
    }
    private static final class Rocket extends RocketEntity {
        Rocket(Minecraft mc){super(ModEntityTypes.ROCKET.get(),mc.level);}
        @Override public void tick(){hold(this);}@Override public WeaponInfo getInfo(){return definition;}@Override public int getCountOnUpdate(){return Math.max(0,age-40);}
    }
    private static final class AA extends AAMissileEntity {
        AA(Minecraft mc){super(ModEntityTypes.AA_MISSILE.get(),mc.level);}
        @Override public void tick(){hold(this);}@Override public WeaponInfo getInfo(){return definition;}@Override public int getCountOnUpdate(){return Math.max(0,age-40);}
    }
    private static final class AS extends ASMissileEntity {
        AS(Minecraft mc){super(ModEntityTypes.AS_MISSILE.get(),mc.level);}
        @Override public void tick(){hold(this);}@Override public WeaponInfo getInfo(){return definition;}@Override public int getCountOnUpdate(){return Math.max(0,age-40);}
    }
    private static final class TV extends TvMissileEntity {
        TV(Minecraft mc){super(ModEntityTypes.TV_MISSILE.get(),mc.level);}
        @Override public void tick(){hold(this);}@Override public WeaponInfo getInfo(){return definition;}@Override public int getCountOnUpdate(){return Math.max(0,age-40);}
    }
    private static final class Carrier extends PlaneEntity {
        Carrier(Minecraft mc){super(ModEntityTypes.PLANE.get(),mc.level);}
        @Override public void tick(){xo=getX();yo=getY();zo=getZ();setDeltaMovement(Vec3.ZERO);}
        @Override public boolean isDestroyed(){return false;}
    }
    @SubscribeEvent public static void tick(ClientTickEvent.Post event){
        if(!ENABLED)return;var mc=Minecraft.getInstance();if(mc.level==null||mc.player==null||mc.isPaused())return;
        if(++ticks==40){
            for(String c:new String[]{"kill @e[type=!minecraft:player]","gamemode spectator","time set noon","weather clear","gamerule minecraft:advance_time false"})mc.player.connection.sendCommand(c);
            mc.getWindow().setWindowed(1280,720);mc.options.enableVsync().set(false);mc.options.framerateLimit().set(120);if(!mc.gui.hud.isHidden())mc.gui.hud.toggle();
            ClientConfig.EFFECT_QUALITY.set(EffectQuality.ENHANCED);ClientConfig.ADAPTIVE_EFFECTS.set(false);ClientConfig.ENGINE_STARTUP_EFFECTS.set(false);
        }
        if(ticks<160)return;int next=START+(ticks-160)/330;age=(ticks-160)%330;
        if(next>=NAMES.length){System.out.println("M8_TRAIL all_passed=true");mc.stop();return;}
        if(next!=phase){
            for(var shot:shots)shot.discard();shots.clear();if(carrier!=null)carrier.discard();VehicleAtmosphere.clear();ProjectileTrailEffects.clear();mc.particleEngine.clearParticles();
            phase=next;peak=trailPeak=0;ClientConfig.EFFECT_STYLE.set(phase==5?ClientConfig.EffectStyle.CLASSIC:ClientConfig.EffectStyle.POLISHED);
            ClientConfig.EFFECT_BUDGET.set(phase==8?ClientConfig.EffectBudget.LOW:ClientConfig.EffectBudget.BALANCED);
            String weapon=phase==0||phase==5||phase==6?"hydra70":phase==2?"agm84":phase==3?"agm158":"aim9";
            definition=ContentRegistries.weapon().get(weapon);if(definition==null)throw new IllegalStateException("Missing native weapon "+weapon);
            if(phase==4||phase==6){var base=definition;definition=new WeaponInfo(base.location,base.filePath,"probe");
                definition.type=base.type;definition.bulletModelName=base.bulletModelName;definition.smokeSize=base.smokeSize;definition.smokeMaxAge=base.smokeMaxAge;
                definition.trajectoryParticleName=base.trajectoryParticleName;definition.color=base.color;definition.colorInWater=base.colorInWater;
                if(phase==4){definition.trajectoryParticleStartTick=10;definition.trajectoryParticleEndTick=90;}else definition.useHBM=true;
            }
            carrier=new Carrier(mc);carrier.setId(971000+phase);carrier.setAircraftTypeName(phase==0?"a-10":phase==2?"f-15e":"f-35a");carrier.setTextureName(carrier.getAircraftTypeName());carrier.setPos(-6,-41,7);
            var parts=carrier.getParts();for(int i=0;i<parts.length;i++)parts[i].setId(991000+phase*100+i);mc.level.addEntity(carrier);
            for(int j=0;j<(phase>=7?32:1);j++){
                BaseBulletEntity shot=weapon.equals("hydra70")?new Rocket(mc):weapon.equals("agm84")?new AS(mc):weapon.equals("agm158")?new TV(mc):new AA(mc);
                shot.setId(961000+phase*100+j);shot.isBomblet=0;shots.add(shot);shot.setPos(phase>=7?(j%4-1.5)*2:0,-40,20);mc.level.addEntity(shot);
            }
            System.out.println("M8_TRAIL scene="+NAMES[phase]+" actors="+shots.size()+" real_weapon="+weapon+" smoke_disabled="+definition.disableSmoke);
        }
        if(age%30==0){var p=shots.getFirst().position();mc.player.connection.sendCommand("tp @s "+(p.x+10)+" "+(p.y+7)+" "+(p.z-17)+" 32 21");}
        var all=particles(mc);int trail=(int)all.stream().filter(ProjectileSmokeParticle.class::isInstance).count();int total=(int)all.stream().filter(McheliParticle.class::isInstance).count();
        peak=Math.max(peak,total);trailPeak=Math.max(trailPeak,trail);
        var budget=ClientConfig.get(ClientConfig.EFFECT_BUDGET);
        if(ProjectileTrailEffects.births()>ProjectileTrailLook.birthLimit(budget)||ProjectileTrailEffects.tracks()>128||total>EffectTuning.smokeLimit(budget)+EffectTuning.flashLimit(budget)+32)throw new IllegalStateException("Projectile trail exceeded shared budget");
        if((phase==3||phase==5||phase==6)&&trail!=0)throw new IllegalStateException("Smokeless/Classic/NTM path emitted new trail particles");
        if(age==110&&(phase<3||phase==4||phase>=7)&&trail==0)throw new IllegalStateException("No enhanced trail "+NAMES[phase]);
        if(phase==4&&age==150&&(ProjectileTrailEffects.births()!=0||ProjectileTrailEffects.motorScale(shots.getFirst())!=0))throw new IllegalStateException("Authored motor cutoff ignored");
        if(age==60||age==100||age==150||age==230)capture=NAMES[phase]+(age==60?"-launch":age==100?"-flight":age==150?"-late":"-fading");
        if(age==310){if(trail!=0)throw new IllegalStateException("Projectile smoke failed to drain");
            System.out.println("M8_TRAIL drained=true scene="+NAMES[phase]+" peak_mod_particles="+peak+" peak_trail_particles="+trailPeak);}
    }
    private static Collection<?> particles(Minecraft mc){try{
        var f=net.minecraft.client.particle.ParticleEngine.class.getDeclaredField("particles");f.setAccessible(true);
        var p=net.minecraft.client.particle.ParticleGroup.class.getDeclaredField("particles");p.setAccessible(true);var all=new ArrayList<>();
        for(var group:((Map<?,?>)f.get(mc.particleEngine)).values())all.addAll((Collection<?>)p.get(group));return all;
    }catch(ReflectiveOperationException ex){throw new IllegalStateException(ex);}}
    @SubscribeEvent public static void camera(ViewportEvent.ComputeFov e){if(ENABLED&&!shots.isEmpty())e.getCamera().setPosition(age>=50&&age<70?new Vec3(35,-22,-12):shots.getFirst().position().add(age>=140&&age<160&&phase<4?new Vec3(6,4,-10):new Vec3(10,7,-17)));}
    @SubscribeEvent public static void angles(ViewportEvent.ComputeCameraAngles e){if(ENABLED&&phase>=0){e.setYaw(age>=50&&age<70?45:32);e.setPitch(age>=50&&age<70?22:21);e.setRoll(0);}}
    @SubscribeEvent public static void frame(RenderFrameEvent.Post e){if(capture!=null){var mc=Minecraft.getInstance();String n=capture;capture=null;Screenshot.grab(mc.gameDirectory,"m8-projectile-"+n+".png",mc.gameRenderer.mainRenderTarget(),1,m->System.out.println("M8_TRAIL "+m.getString()));}}
}
