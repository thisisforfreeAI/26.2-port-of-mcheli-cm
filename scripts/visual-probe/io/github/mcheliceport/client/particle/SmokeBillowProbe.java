package io.github.mcheliceport.client.particle;

import io.github.mcheliceport.client.render.*;
import io.github.mcheliceport.config.*;
import io.github.mcheliceport.effect.BlastStyle;
import io.github.mcheliceport.entity.aircraft.AircraftEntity;
import io.github.mcheliceport.entity.heli.HeliEntity;
import io.github.mcheliceport.entity.plane.PlaneEntity;
import io.github.mcheliceport.entity.tank.TankEntity;
import io.github.mcheliceport.registry.ModEntityTypes;
import java.util.List;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Screenshot;
import net.minecraft.world.phys.Vec3;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.*;

/** Controlled effects and wreck ages through the production emitters. Never included in normal builds. */
@EventBusSubscriber(modid="mcheli_ce_port",value=Dist.CLIENT)
public final class SmokeBillowProbe {
    private static final boolean ENABLED=Boolean.getBoolean("mcheli.probeSmokeBillows");
    private static final int MODE=Integer.getInteger("mcheli.probeSmokeMode",0);
    private static int ticks,phase=-1,age,peak;private static AircraftEntity actor;private static String capture;
    private static final String[] WRECKS={"m1a2","ah-64","f-35a"};
    private static void hold(AircraftEntity ac){ac.xo=ac.getX();ac.yo=ac.getY();ac.zo=ac.getZ();ac.setPos(0,-60,20);ac.setDeltaMovement(Vec3.ZERO);}
    private static final class Tank extends TankEntity {Tank(Minecraft mc){super(ModEntityTypes.TANK.get(),mc.level);}@Override public void tick(){hold(this);}@Override public boolean isDestroyed(){return age>=40&&age<850;}}
    private static final class Heli extends HeliEntity {Heli(Minecraft mc){super(ModEntityTypes.HELICOPTER.get(),mc.level);}@Override public void tick(){hold(this);}@Override public boolean isDestroyed(){return age>=40&&age<850;}}
    private static final class Plane extends PlaneEntity {Plane(Minecraft mc){super(ModEntityTypes.PLANE.get(),mc.level);}@Override public void tick(){hold(this);}@Override public boolean isDestroyed(){return age>=40&&age<850;}}
    @SubscribeEvent public static void tick(ClientTickEvent.Post event){
        if(!ENABLED)return;var mc=Minecraft.getInstance();if(mc.level==null||mc.player==null||mc.isPaused())return;
        if(++ticks==40){
            for(String c:new String[]{"kill @e[type=!minecraft:player]","gamemode spectator","time set noon","weather clear","gamerule minecraft:advance_time false","tp @s 12 -52 28 130 18"})mc.player.connection.sendCommand(c);
            mc.getWindow().setWindowed(1280,720);mc.options.enableVsync().set(false);mc.options.framerateLimit().set(120);
            if(!mc.gui.hud.isHidden())mc.gui.hud.toggle();
            ClientConfig.EFFECT_QUALITY.set(EffectQuality.ENHANCED);ClientConfig.EFFECT_STYLE.set(ClientConfig.EffectStyle.POLISHED);
            ClientConfig.DEFAULT_EXPLOSION_PARTICLE.set(false);ClientConfig.MODERN_EXPLOSIONS.set(false);
            ClientConfig.EFFECT_BUDGET.set(ClientConfig.EffectBudget.BALANCED);ClientConfig.ADAPTIVE_EFFECTS.set(false);
            ClientConfig.VEHICLE_DAMAGE_DETAILS.set(true);ClientConfig.ENGINE_STARTUP_EFFECTS.set(false);
        }
        if(ticks<160)return;int duration=MODE==0?980:220,next=(ticks-160)/duration;age=(ticks-160)%duration;
        if(next>=(MODE==0?3:5)){System.out.println("M8_SMOKE_BILLOWS mode="+MODE+" all_passed=true");mc.stop();return;}
        if(next!=phase){
            phase=next;if(actor!=null)actor.discard();actor=null;VehicleAtmosphere.clear();LoosePanels.clear();mc.particleEngine.clearParticles();peak=0;
            ClientConfig.EFFECT_STYLE.set(MODE==1&&phase==4?ClientConfig.EffectStyle.CLASSIC:ClientConfig.EffectStyle.POLISHED);
            ClientConfig.MODERN_EXPLOSIONS.set(MODE==1&&phase==2);
            if(MODE==1)mc.player.connection.sendCommand(phase==2?"time set midnight":"time set noon");
            if(MODE==0){actor=phase==0?new Tank(mc):phase==1?new Heli(mc):new Plane(mc);
                actor.setId(977000+phase);actor.setAircraftTypeName(WRECKS[phase]);actor.setTextureName(WRECKS[phase]);actor.setPos(0,-60,20);
                var parts=actor.getParts();for(int i=0;i<parts.length;i++)parts[i].setId(987000+phase*100+i);mc.level.addEntity(actor);
            }
        }
        int total=Integer.parseInt(mc.particleEngine.countParticles().replaceAll(".*T ",""));peak=Math.max(peak,total);
        if(total>EffectTuning.smokeLimit(ClientConfig.get(ClientConfig.EFFECT_BUDGET))+EffectTuning.flashLimit(ClientConfig.get(ClientConfig.EFFECT_BUDGET))+32)throw new IllegalStateException("Particle cap exceeded");
        if(VehicleAtmosphere.emitted()>EngineExhaustLook.cap(ClientConfig.get(ClientConfig.EFFECT_BUDGET)))throw new IllegalStateException("Shared atmosphere births exceeded");
        if(MODE==0){
            if(age==140||age==820)capture=WRECKS[phase]+(age==140?"-hot":"-cool");
            if(age==150||age==830){if(total==0)throw new IllegalStateException("Wreck plume missing");System.out.println("M8_SMOKE_BILLOWS wreck="+WRECKS[phase]+" age="+VehicleAtmosphere.wreckAge(actor)+" peak="+peak+" particles="+total);}
            if(age==960){if(ownedParticles(mc)!=0)throw new IllegalStateException("Repaired wreck particles did not drain: "+ownedParticles(mc));System.out.println("M8_SMOKE_BILLOWS wreck_drain=true");}
        }else{
            if(age==25)ClientEffects.INSTANCE.explosionEffects(mc.level,0,-59.5,20,phase==0?3:12,false,List.of(),phase==0?BlastStyle.BLAST:BlastStyle.HEAVY);
            if(phase==3&&age>=25&&age<=55&&age%5==0){
                for(int i=0;i<6;i++)ClientEffects.INSTANCE.explosionEffects(mc.level,(i%3-1)*5,-59.5,18+i/3*5,12,false,List.of(),BlastStyle.HEAVY);
                for(int i=0;i<4;i++){Vec3 at=new Vec3(i-1.5,-58.5,20);if(VehicleAtmosphere.admit(mc.level,at,1.3F))mc.particleEngine.add(new WreckSmokeParticle(mc.level,at.x,at.y,at.z,1.3F,.8F,.6F,false,i,.5F));}
            }
            if(age==36||age==60||age==100)capture="blast-"+phase+"-age"+(age-25);
            if(age==150)System.out.println("M8_SMOKE_BILLOWS blast="+phase+" peak="+peak+" particles="+total+" classic="+(phase==4));
            if(age==210&&ownedParticles(mc)!=0)throw new IllegalStateException("Blast particles did not drain: "+ownedParticles(mc));
        }
    }
    private static int ownedParticles(Minecraft mc){
        try{
            var field=net.minecraft.client.particle.ParticleEngine.class.getDeclaredField("particles");field.setAccessible(true);
            var particles=net.minecraft.client.particle.ParticleGroup.class.getDeclaredField("particles");particles.setAccessible(true);int count=0;
            for(var group:((java.util.Map<?,?>)field.get(mc.particleEngine)).values())
                for(var particle:(java.util.Collection<?>)particles.get(group))if(particle instanceof McheliParticle)count++;
            return count;
        }catch(ReflectiveOperationException ex){throw new IllegalStateException(ex);}
    }
    @SubscribeEvent public static void camera(ViewportEvent.ComputeFov event){if(ENABLED&&phase>=0)event.getCamera().setPosition(MODE==0?new Vec3(13,-53,29):new Vec3(17,-51,37));}
    @SubscribeEvent public static void angles(ViewportEvent.ComputeCameraAngles event){if(ENABLED&&phase>=0){event.setYaw(MODE==0?125:135);event.setPitch(MODE==0?12:18);event.setRoll(0);}}
    @SubscribeEvent public static void frame(RenderFrameEvent.Post event){if(capture!=null){var mc=Minecraft.getInstance();String name=capture;capture=null;Screenshot.grab(mc.gameDirectory,"m8-smoke-billows-"+name+".png",mc.gameRenderer.mainRenderTarget(),1,m->System.out.println("M8_SMOKE_BILLOWS "+m.getString()));}}
}
