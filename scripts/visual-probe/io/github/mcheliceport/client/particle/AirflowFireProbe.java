package io.github.mcheliceport.client.particle;

import io.github.mcheliceport.config.*;
import io.github.mcheliceport.entity.aircraft.AircraftEntity;
import io.github.mcheliceport.entity.heli.HeliEntity;
import io.github.mcheliceport.entity.plane.PlaneEntity;
import io.github.mcheliceport.registry.ModEntityTypes;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Screenshot;
import net.minecraft.world.phys.Vec3;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.*;

/** Held HP/motion fixtures through production VehicleAtmosphere; never part of normal builds. */
@EventBusSubscriber(modid="mcheli_ce_port",value=Dist.CLIENT)
public final class AirflowFireProbe {
    private static final boolean ENABLED=Boolean.getBoolean("mcheli.probeAirflowFire");
    private static final int START=Integer.getInteger("mcheli.probeAirflowStart",0);
    private static final String[] NAMES={"f-35a","ah-64","f22a","f-35a-wreck","classic-wreck","shared-load"};
    private static final List<AircraftEntity> actors=new ArrayList<>();
    private static int ticks,phase=-1,age,peak,firePeak;private static String capture;
    private static int hp(AircraftEntity ac){return age>=40&&age<240?(phase==3||phase==4?0:Math.max(1,ac.getMaxHP()/8)):ac.getMaxHP();}
    private static boolean wreck(){return (phase==3||phase==4)&&age>=40&&age<240;}
    private static void hold(AircraftEntity ac) {
        int index=actors.indexOf(ac);double speed=age>=40&&age<140?.65:0;
        ac.xo=ac.getX();ac.yo=ac.getY();ac.zo=ac.getZ();
        ac.setPos(phase==5?(index%4-1.5)*7:0,-47,20+Math.clamp(age-40,0,100)*.65+(phase==5?index/4*5:0));
        ac.setDeltaMovement(0,0,phase==1?0:speed); // AH-64 exercises remote interpolation fallback.
    }
    private static final class Plane extends PlaneEntity {
        Plane(Minecraft mc){super(ModEntityTypes.PLANE.get(),mc.level);}
        @Override public void tick(){hold(this);}@Override public int getHP(){return hp(this);}@Override public boolean isDestroyed(){return wreck();}
    }
    private static final class Heli extends HeliEntity {
        Heli(Minecraft mc){super(ModEntityTypes.HELICOPTER.get(),mc.level);}
        @Override public void tick(){hold(this);}@Override public int getHP(){return hp(this);}@Override public boolean isDestroyed(){return wreck();}
    }
    @SubscribeEvent public static void tick(ClientTickEvent.Post event) {
        if(!ENABLED)return;var mc=Minecraft.getInstance();if(mc.level==null||mc.player==null||mc.isPaused())return;
        if(++ticks==40) {
            for(String c:new String[]{"kill @e[type=!minecraft:player]","gamemode spectator","time set noon","weather clear","gamerule minecraft:advance_time false","tp @s 14 -42 7 47 14"})mc.player.connection.sendCommand(c);
            mc.getWindow().setWindowed(1280,720);mc.options.enableVsync().set(false);mc.options.framerateLimit().set(120);
            if(!mc.gui.hud.isHidden())mc.gui.hud.toggle();
            ClientConfig.EFFECT_QUALITY.set(EffectQuality.ENHANCED);ClientConfig.EFFECT_STYLE.set(ClientConfig.EffectStyle.POLISHED);
            ClientConfig.EFFECT_BUDGET.set(ClientConfig.EffectBudget.BALANCED);ClientConfig.ADAPTIVE_EFFECTS.set(false);
            ClientConfig.VEHICLE_DAMAGE_DETAILS.set(true);ClientConfig.VEHICLE_DESTRUCTION_SEQUENCES.set(false);ClientConfig.ENGINE_STARTUP_EFFECTS.set(false);
        }
        if(ticks<160)return;int next=START+(ticks-160)/340;age=(ticks-160)%340;
        if(next>=6){System.out.println("M8_AIRFLOW all_passed=true");mc.stop();return;}
        if(next!=phase) {
            for(var ac:actors)ac.discard();actors.clear();VehicleAtmosphere.clear();mc.particleEngine.clearParticles();
            phase=next;peak=firePeak=0;ClientConfig.EFFECT_STYLE.set(phase==4?ClientConfig.EffectStyle.CLASSIC:ClientConfig.EffectStyle.POLISHED);
            String model=phase==1?"ah-64":phase==2?"f22a":"f-35a";
            for(int j=0;j<(phase==5?32:1);j++) {
                AircraftEntity ac=phase==1?new Heli(mc):new Plane(mc);ac.setId(976000+phase*100+j);
                ac.setAircraftTypeName(model);ac.setTextureName(model);actors.add(ac);hold(ac);
                var parts=ac.getParts();for(int i=0;i<parts.length;i++)parts[i].setId(986000+phase*10000+j*100+i);
                mc.level.addEntity(ac);
            }
            System.out.println("M8_AIRFLOW scene="+NAMES[phase]+" actors="+actors.size());
        }
        if(age%40==0){Vec3 p=actors.getFirst().position();mc.player.connection.sendCommand("tp @s "+(p.x+14)+" "+(p.y+5)+" "+(p.z-13)+" 47 14");}
        int total=Integer.parseInt(mc.particleEngine.countParticles().replaceAll(".*T ","")),fire=count(mc,AirflowFireParticle.class);
        peak=Math.max(peak,total);firePeak=Math.max(firePeak,fire);
        if(total>EffectTuning.smokeLimit(ClientConfig.EffectBudget.BALANCED)+EffectTuning.flashLimit(ClientConfig.EffectBudget.BALANCED)+32
                ||VehicleAtmosphere.emitted()>EngineExhaustLook.cap(ClientConfig.EffectBudget.BALANCED))throw new IllegalStateException("Airflow shared budget exceeded");
        if(age==110||age==200||age==290)capture=NAMES[phase]+(age==110?"-flight":age==200?"-stopped":"-repaired");
        if((age==115||age==205)&&phase!=4&&fire==0)throw new IllegalStateException("Airflow fire missing "+NAMES[phase]);
        if(phase==4&&fire!=0)throw new IllegalStateException("Classic emitted airflow fire");
        if(age==290&&fire!=0)throw new IllegalStateException("Repair did not drain fire");
        if(age==330)System.out.println("M8_AIRFLOW drained=true scene="+NAMES[phase]+" peak="+peak+" fire_peak="+firePeak);
    }
    private static int count(Minecraft mc,Class<?> kind) {
        try {
            var field=net.minecraft.client.particle.ParticleEngine.class.getDeclaredField("particles");field.setAccessible(true);
            var particles=net.minecraft.client.particle.ParticleGroup.class.getDeclaredField("particles");particles.setAccessible(true);int count=0;
            for(var group:((java.util.Map<?,?>)field.get(mc.particleEngine)).values())
                for(var particle:(java.util.Collection<?>)particles.get(group))if(kind.isInstance(particle))count++;
            return count;
        }catch(ReflectiveOperationException ex){throw new IllegalStateException(ex);}
    }
    @SubscribeEvent public static void camera(ViewportEvent.ComputeFov event){
        if(ENABLED&&!actors.isEmpty()){
            var ac=actors.getFirst();
            // Full-file bounds include non-rendered authoring objects. Center the visible aircraft.
            event.getCamera().setPosition(ac.position().add(11,7.5,phase==2?-22:-21));
        }
    }
    @SubscribeEvent public static void angles(ViewportEvent.ComputeCameraAngles event){if(ENABLED&&phase>=0){event.setYaw(36);event.setPitch(15);event.setRoll(0);}}
    @SubscribeEvent public static void frame(RenderFrameEvent.Post event) {
        if(capture!=null){var mc=Minecraft.getInstance();String name=capture;capture=null;
            Screenshot.grab(mc.gameDirectory,"m8-airflow-fire-"+name+".png",mc.gameRenderer.mainRenderTarget(),1,m->System.out.println("M8_AIRFLOW "+m.getString()));}
    }
}
