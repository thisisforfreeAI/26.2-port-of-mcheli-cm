package io.github.mcheliceport.client.render;

import io.github.mcheliceport.client.particle.VehicleAtmosphere;
import io.github.mcheliceport.config.*;
import io.github.mcheliceport.entity.aircraft.AircraftEntity;
import io.github.mcheliceport.entity.heli.HeliEntity;
import io.github.mcheliceport.entity.plane.PlaneEntity;
import io.github.mcheliceport.registry.ModEntityTypes;
import java.util.*;
import net.minecraft.client.*;
import net.minecraft.client.gui.screens.TitleScreen;
import net.minecraft.world.phys.Vec3;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.*;

/** Held client wreck/HP states using production fire emitters, not staged server crashes. */
@EventBusSubscriber(modid="mcheli_ce_port",value=Dist.CLIENT)
public final class FireLightingProbe {
    private static final boolean ENABLED=Boolean.getBoolean("mcheli.probeFireLighting");
    private static final String[] NAMES={"ah64-lighting-off","ah64-lighting-on","f35-engine-fire","ah64-compatibility","shared-load","classic-quality"};
    private static final List<AircraftEntity> actors=new ArrayList<>();
    private static int ticks,phase=-1,age,peak;private static boolean opened;private static String capture;
    private static int duration(){return phase==1?850:280;}
    private static boolean wreck(){return phase!=2&&age>=30;}
    private static void hold(AircraftEntity ac){
        ac.xo=ac.getX();ac.yo=ac.getY();ac.zo=ac.getZ();ac.yRotO=ac.getYRot();ac.xRotO=0;ac.prevRoll=0;
        ac.tickCount++;ac.setPitch(0);ac.setRoll(0);ac.setOnGround(true);ac.setDeltaMovement(Vec3.ZERO);
    }
    private static final class Heli extends HeliEntity {
        Heli(Minecraft mc){super(ModEntityTypes.HELICOPTER.get(),mc.level);}
        @Override public void tick(){hold(this);}@Override public boolean isDestroyed(){return wreck();}
        @Override public int getHP(){return wreck()?0:getMaxHP();}
    }
    private static final class Plane extends PlaneEntity {
        Plane(Minecraft mc){super(ModEntityTypes.PLANE.get(),mc.level);}
        @Override public void tick(){hold(this);}@Override public boolean isDestroyed(){return false;}
        @Override public int getHP(){return age>=30&&age<200?Math.max(1,getMaxHP()/8):getMaxHP();}
    }
    @SubscribeEvent public static void screen(ScreenEvent.Init.Post e){
        if(!ENABLED||opened||!(e.getScreen() instanceof TitleScreen)||Minecraft.getInstance().level!=null)return;
        var mc=Minecraft.getInstance();opened=true;
        mc.createWorldOpenFlows().openWorld("M6Smoke",()->mc.gui.setScreen(new TitleScreen()));
    }
    @SubscribeEvent public static void tick(ClientTickEvent.Post e){
        if(!ENABLED)return;var mc=Minecraft.getInstance();if(mc.level==null||mc.player==null||mc.isPaused())return;
        if(++ticks==40){
            for(String c:new String[]{"kill @e[type=!minecraft:player]","gamemode spectator","time set midnight","weather clear","gamerule minecraft:advance_time false",
                    "fill -28 -61 -8 28 -61 45 grass_block","fill -28 -60 -8 28 -43 45 air","tp @s 0 -54 6"})mc.player.connection.sendCommand(c);
            mc.getWindow().setWindowed(1280,720);mc.options.enableVsync().set(false);mc.options.framerateLimit().set(120);mc.options.gamma().set(.5D);mc.options.fov().set(60);mc.options.cloudStatus().set(CloudStatus.OFF);
            if(!mc.gui.hud.isHidden())mc.gui.hud.toggle();
            ClientConfig.ADAPTIVE_EFFECTS.set(false);ClientConfig.ENGINE_STARTUP_EFFECTS.set(false);
            ClientConfig.NIGHT_FLIGHT_LIGHTING.set(false);ClientConfig.VEHICLE_DESTRUCTION_SEQUENCES.set(false);
            ClientConfig.VEHICLE_DAMAGE_DETAILS.set(true);ClientConfig.EFFECT_QUALITY.set(EffectQuality.ENHANCED);
            ClientConfig.EFFECT_STYLE.set(ClientConfig.EffectStyle.POLISHED);ClientConfig.EFFECT_BUDGET.set(ClientConfig.EffectBudget.BALANCED);
        }
        if(ticks<160)return;
        if(phase<0||++age>=duration()){
            actors.forEach(AircraftEntity::discard);actors.clear();VehicleAtmosphere.clear();WreckScene.clear();SceneLights.clear();mc.particleEngine.clearParticles();
            phase++;age=0;peak=0;
            if(phase>=NAMES.length){ClientConfig.GPU_MODEL_BUFFERS.set(true);ClientConfig.REALISTIC_LIGHTING.set(true);ClientConfig.EFFECT_QUALITY.set(EffectQuality.ENHANCED);System.out.println("FIRE_LIGHT all_passed=true");mc.stop();return;}
            ClientConfig.REALISTIC_LIGHTING.set(phase!=0);ClientConfig.GPU_MODEL_BUFFERS.set(phase!=3);
            ClientConfig.EFFECT_QUALITY.set(phase==5?EffectQuality.CLASSIC:EffectQuality.ENHANCED);
            for(int j=0;j<(phase==4?24:1);j++){
                AircraftEntity ac=phase==2?new Plane(mc):new Heli(mc);String model=phase==2?"f-35a":"ah-64";
                ac.setId(1260000+phase*100+j);ac.setAircraftTypeName(model);ac.setTextureName(model);
                ac.setPos(phase==4?(j%6-2.5)*7:0,-60,phase==4?8+(j/6)*8:18);
                for(int k=0;k<ac.getParts().length;k++)ac.getParts()[k].setId(1560000+phase*10000+j*100+k);
                actors.add(ac);mc.level.addEntity(ac);
            }
            System.out.println("FIRE_LIGHT scene="+NAMES[phase]+" actors="+actors.size());
        }
        peak=Math.max(peak,SceneLights.count());
        if(phase==5&&age==60)SceneLights.fire(mc.level,actors.getFirst(),actors.getFirst().position(),1);
        if(phase==4&&age==29){
            // Fill the source cap with distant weak cells before the 24 wrecks ignite.
            for(int i=0;i<96;i++)SceneLights.flame(mc.level,new Vec3(200+i*3,-50,200),.2F,.8F);
            peak=Math.max(peak,SceneLights.count());
            if(SceneLights.count()!=96)throw new IllegalStateException("Source-cap pressure was not exercised");
        }
        try{var keyed=SceneLights.class.getDeclaredField("KEYED");keyed.setAccessible(true);
            if(((Map<?,?>)keyed.get(null)).size()>SceneLights.count())throw new IllegalStateException("Evicted light leaked a keyed entry");
        }catch(ReflectiveOperationException ex){throw new IllegalStateException(ex);}
        if(phase==4&&age==45){
            for(var ac:actors)if(!hasSource(ac,0)||!hasSource(ac,1))throw new IllegalStateException("Main fires could not evict weak flame cells");
            report("cap-pressure");
        }
        if(SceneLights.count()>96||SceneLights.frame(mc.gameRenderer.mainCamera().position()).count()>8)
            throw new IllegalStateException("Fire light budget exceeded");
        if(age==120){
            if((phase==0||phase==5)&&SceneLights.count()!=0)throw new IllegalStateException("Lighting persisted while disabled");
            if(phase>0&&phase<5&&(SceneLights.count()==0||sampledFaces()==0))throw new IllegalStateException("Missing fire light/ground patch");
            if(phase==2&&!hasSource(actors.getFirst(),2))throw new IllegalStateException("Engine fire has no source");
            if(phase==4)for(var ac:actors)if(!hasSource(ac,0)||!hasSource(ac,1))throw new IllegalStateException("Particle cap starved a fire light");
            capture=NAMES[phase];report("burning");
        }
        if(phase==1&&age==460){
            if(hasSource(actors.getFirst(),0)||!hasSource(actors.getFirst(),1))throw new IllegalStateException("Pool light did not outlive hull fire");
            capture="ah64-late-fuel-fire";report("late-pool");
        }
        if((phase==2&&age==250)||(phase==1&&age==835)){
            if(SceneLights.count()!=0)throw new IllegalStateException("Repaired/cold source kept emitting light: "+SceneLights.count());
            capture=phase==2?"f35-repaired":"ah64-cooled";report("drained");
        }
    }
    private static void report(String stage){System.out.println("FIRE_LIGHT passed=true scene="+NAMES[phase]+" stage="+stage+" lights="+SceneLights.count()+" peak="+peak+" sampled_faces="+sampledFaces()+" emitted_particles="+VehicleAtmosphere.emitted());}
    private static List<?> lights(){try{var f=SceneLights.class.getDeclaredField("LIGHTS");f.setAccessible(true);return (List<?>)f.get(null);}catch(Exception e){throw new IllegalStateException(e);}}
    private static Object field(Object object,String name)throws Exception{var f=object.getClass().getDeclaredField(name);f.setAccessible(true);return f.get(object);}
    private static boolean hasSource(Object source,int channel){try{for(var light:lights()){var key=field(light,"key");if(key!=null&&key.getClass().getSimpleName().equals("FireKey")&&field(key,"source")==source&&(int)field(key,"channel")==channel)return true;}return false;}catch(Exception e){throw new IllegalStateException(e);}}
    private static int sampledFaces(){try{int n=0;for(var light:lights())n+=((List<?>)field(light,"surfaces")).size();return n;}catch(Exception e){throw new IllegalStateException(e);}}
    private static Vec3 target(){return phase==4?new Vec3(0,-58,20):actors.getFirst().position().add(0,1.3,0);}
    private static Vec3 eye(){return target().add(phase==4?22:12,phase==4?14:4.5,phase==4?-24:-16);}
    @SubscribeEvent public static void camera(ViewportEvent.ComputeFov e){if(ENABLED&&!actors.isEmpty())e.getCamera().setPosition(eye());}
    @SubscribeEvent public static void angles(ViewportEvent.ComputeCameraAngles e){if(!ENABLED||actors.isEmpty())return;var d=target().subtract(eye());e.setYaw((float)Math.toDegrees(Math.atan2(-d.x,d.z)));e.setPitch((float)-Math.toDegrees(Math.atan2(d.y,d.horizontalDistance())));e.setRoll(0);}
    @SubscribeEvent public static void frame(RenderFrameEvent.Post e){if(capture==null)return;var mc=Minecraft.getInstance();String name=capture;capture=null;Screenshot.grab(mc.gameDirectory,"m8-fire-light-"+name+".png",mc.gameRenderer.mainRenderTarget(),1,m->System.out.println("FIRE_LIGHT "+m.getString()));}
}
