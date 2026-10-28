package io.github.mcheliceport.client.particle;

import io.github.mcheliceport.config.*;
import io.github.mcheliceport.entity.aircraft.AircraftEntity;
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

/** Production atmosphere on held turning aircraft. Never shipped in a normal build. */
@EventBusSubscriber(modid="mcheli_ce_port",value=Dist.CLIENT)
public final class ManeuverSmokeProbe {
    private static final boolean ENABLED=Boolean.getBoolean("mcheli.probeManeuverSmoke");
    private static final int START=Integer.getInteger("mcheli.probeManeuverStart",0);
    private static final String[] NAMES={"f-35a","f-15e-remote","a-10","stopped","night","settings","shared-load"};
    private static final List<PlaneEntity> actors=new ArrayList<>();
    private static final Map<DamageSmokeParticle,Vec3> sampled=new IdentityHashMap<>();
    private static int ticks,phase=-1,age,peak,smokePeak;private static String capture;
    private static final class Plane extends PlaneEntity {
        Plane(Minecraft mc){super(ModEntityTypes.PLANE.get(),mc.level);}
        @Override public void tick(){
            int i=actors.indexOf(this);double t=Math.max(0,age-40)*.045,r=phase==3?0:18;
            xo=getX();yo=getY();zo=getZ();yRotO=getYaw();xRotO=getPitch();prevRoll=getRoll();
            setPos(Math.sin(t)*r+(phase==6?(i%4-1.5)*7:0),-40,20+(1-Math.cos(t))*r+(phase==6?i/4*5:0));
            setYaw((float)(-90+Math.toDegrees(t)));setRoll(phase==3?0:-18);
            setDeltaMovement(phase==1?Vec3.ZERO:new Vec3(Math.cos(t)*r*.045,0,Math.sin(t)*r*.045));
        }
        @Override public int getHP(){return age>=40&&age<200?Math.max(1,getMaxHP()/4):getMaxHP();}
        @Override public boolean isDestroyed(){return false;}
    }
    @SubscribeEvent public static void tick(ClientTickEvent.Post event){
        if(!ENABLED)return;var mc=Minecraft.getInstance();if(mc.level==null||mc.player==null||mc.isPaused())return;
        if(++ticks==40){
            for(String c:new String[]{"kill @e[type=!minecraft:player]","gamemode spectator","time set noon","weather clear","gamerule minecraft:advance_time false"})mc.player.connection.sendCommand(c);
            mc.getWindow().setWindowed(1280,720);mc.options.enableVsync().set(false);mc.options.framerateLimit().set(120);if(!mc.gui.hud.isHidden())mc.gui.hud.toggle();
            ClientConfig.EFFECT_QUALITY.set(EffectQuality.ENHANCED);ClientConfig.EFFECT_BUDGET.set(ClientConfig.EffectBudget.BALANCED);ClientConfig.ADAPTIVE_EFFECTS.set(false);
            ClientConfig.ENGINE_STARTUP_EFFECTS.set(false);ClientConfig.VEHICLE_DESTRUCTION_SEQUENCES.set(false);
        }
        if(ticks<160)return;int next=START+(ticks-160)/300;age=(ticks-160)%300;
        if(next>=NAMES.length){System.out.println("M8_MANEUVER all_passed=true");mc.stop();return;}
        if(next!=phase){
            for(var ac:actors)ac.discard();actors.clear();sampled.clear();VehicleAtmosphere.clear();mc.particleEngine.clearParticles();phase=next;peak=smokePeak=0;
            ClientConfig.EFFECT_STYLE.set(phase==5?ClientConfig.EffectStyle.CLASSIC:ClientConfig.EffectStyle.POLISHED);ClientConfig.VEHICLE_DAMAGE_DETAILS.set(true);
            mc.player.connection.sendCommand(phase==4?"time set midnight":"time set noon");
            String model=phase==1?"f-15e":phase==2||phase==3?"a-10":"f-35a";
            for(int j=0;j<(phase==6?32:1);j++){
                var ac=new Plane(mc);ac.setId(972000+phase*100+j);ac.setAircraftTypeName(model);ac.setTextureName(model);actors.add(ac);ac.setPos(0,-40,20);
                var parts=ac.getParts();for(int i=0;i<parts.length;i++)parts[i].setId(982000+phase*10000+j*100+i);mc.level.addEntity(ac);
            }
            System.out.println("M8_MANEUVER scene="+NAMES[phase]+" actors="+actors.size());
        }
        if(age%40==0){var p=actors.getFirst().position();mc.player.connection.sendCommand("tp @s "+(p.x+14)+" "+(p.y+12)+" "+(p.z-22)+" 33 25");}
        if(phase==5){if(age==120)ClientConfig.EFFECT_STYLE.set(ClientConfig.EffectStyle.POLISHED);if(age==180)ClientConfig.VEHICLE_DAMAGE_DETAILS.set(false);}
        var smoke=smoke(mc);int total=countMod(mc);peak=Math.max(peak,total);smokePeak=Math.max(smokePeak,smoke.size());
        if(total>1664||VehicleAtmosphere.emitted()>16||VehicleAtmosphere.stateCount()>128)throw new IllegalStateException("Maneuver smoke exceeded shared allowance");
        if(age==80&&phase<3)for(var p:smoke)sampled.put(p,p.getBoundingBox().getCenter());
        if(age==110&&phase<3){
            int survivors=0;double maxDrift=0,maxSeparation=0;
            for(var p:smoke)if(sampled.containsKey(p)){survivors++;Vec3 at=p.getBoundingBox().getCenter();maxDrift=Math.max(maxDrift,at.distanceTo(sampled.get(p)));maxSeparation=Math.max(maxSeparation,at.distanceTo(actors.getFirst().position()));}
            if(survivors==0||maxDrift>12||maxSeparation<5)throw new IllegalStateException("Older smoke did not remain on the flight path: "+NAMES[phase]+" survivors="+survivors+" drift="+maxDrift+" separation="+maxSeparation);
            System.out.println("M8_MANEUVER path="+NAMES[phase]+" survivors="+survivors+" max_drift="+maxDrift+" separation="+maxSeparation);
        }
        if(age==150&&phase!=5&&smoke.isEmpty())throw new IllegalStateException("No damage billows: "+NAMES[phase]);
        if(phase==5&&age<120&&!smoke.isEmpty())throw new IllegalStateException("Classic emitted new damage smoke");
        if(age==90||age==130||age==170||age==285)capture=NAMES[phase]+(age==90?"-early":age==130?"-turn":age==170?"-mature":"-repaired");
        if(age==285){if(!smoke.isEmpty())throw new IllegalStateException("Repair/settings retained damage smoke");
            System.out.println("M8_MANEUVER drained=true scene="+NAMES[phase]+" peak_mod_particles="+peak+" peak_damage_smoke="+smokePeak);}
    }
    private static Collection<?> particles(Minecraft mc){
        try{var field=net.minecraft.client.particle.ParticleEngine.class.getDeclaredField("particles");field.setAccessible(true);
            var p=net.minecraft.client.particle.ParticleGroup.class.getDeclaredField("particles");p.setAccessible(true);var all=new ArrayList<>();
            for(var group:((Map<?,?>)field.get(mc.particleEngine)).values())all.addAll((Collection<?>)p.get(group));return all;
        }catch(ReflectiveOperationException ex){throw new IllegalStateException(ex);}
    }
    private static List<DamageSmokeParticle> smoke(Minecraft mc){return particles(mc).stream().filter(DamageSmokeParticle.class::isInstance).map(DamageSmokeParticle.class::cast).toList();}
    private static int countMod(Minecraft mc){return (int)particles(mc).stream().filter(McheliParticle.class::isInstance).count();}
    @SubscribeEvent public static void camera(ViewportEvent.ComputeFov e){if(ENABLED&&!actors.isEmpty())e.getCamera().setPosition(actors.getFirst().position().add(14,12,-22));}
    @SubscribeEvent public static void angles(ViewportEvent.ComputeCameraAngles e){if(ENABLED&&phase>=0){e.setYaw(33);e.setPitch(25);e.setRoll(0);}}
    @SubscribeEvent public static void frame(RenderFrameEvent.Post e){if(capture!=null){var mc=Minecraft.getInstance();String name=capture;capture=null;Screenshot.grab(mc.gameDirectory,"m8-maneuver-"+name+".png",mc.gameRenderer.mainRenderTarget(),1,m->System.out.println("M8_MANEUVER "+m.getString()));}}
}
