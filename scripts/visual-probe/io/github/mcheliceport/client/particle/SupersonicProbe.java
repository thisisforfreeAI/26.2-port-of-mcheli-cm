package io.github.mcheliceport.client.particle;

import io.github.mcheliceport.config.*;
import io.github.mcheliceport.damage.*;
import io.github.mcheliceport.entity.plane.PlaneEntity;
import io.github.mcheliceport.registry.ModEntityTypes;
import java.util.*;
import net.minecraft.client.*;
import net.minecraft.resources.Identifier;
import net.minecraft.world.phys.Vec3;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.*;

/** Real aircraft on controlled acceleration/pass trajectories, using production sound/particle admission. */
@EventBusSubscriber(modid="mcheli_ce_port",value=Dist.CLIENT)
public final class SupersonicProbe {
    private static final boolean ENABLED=Boolean.getBoolean("mcheli.probeSupersonic");
    private static final int START=Integer.getInteger("mcheli.probeSonicStart",0),END=Integer.getInteger("mcheli.probeSonicEnd",15);
    private static final String[] NAMES={"f-35a-wet","f-15e-wet","f22a-wet","dry-pass","already-fast","unsupported-a-10","distant-pass","missing-wing","disabled","classic","remote-motion","shared-load","warp","low","boom-load"};
    private static final List<Plane> actors=new ArrayList<>();
    private static int ticks,phase=-1,age,peak,conePeak,pendingPeak;private static String capture;
    private static boolean fixed(){return phase==3||phase==4||phase==6||phase==13||phase==14;}
    private static final class Plane extends PlaneEntity {
        Plane(Minecraft mc){super(ModEntityTypes.PLANE.get(),mc.level);}
        @Override public void tick(){
            int i=actors.indexOf(this);double threshold=getAcInfo().machOneSpeed>0?getAcInfo().machOneSpeed:.75;
            int crossing=phase==3?100:40;
            double mach=phase==4?1.2:age<crossing?.75:Math.min(1.2,.75+(age-crossing)*.02);
            double speed=age>=170?0:threshold*mach;
            xo=getX();yo=getY();zo=getZ();yRotO=0;xRotO=0;prevRoll=0;
            setPos(getX()+(phase==12&&age==56?100:0),100,getZ()+speed);setDeltaMovement(phase==10?Vec3.ZERO:new Vec3(0,0,speed));
            setYaw(0);setPitch(0);setRoll(0);setOnGround(false);
        }
        @Override public double getCurrentThrottle(){return .9;}
        @Override public int getFuel(){return getMaxFuel();}
        @Override public int getHP(){return getMaxHP();}
        @Override public boolean isDestroyed(){return false;}
    }
    @SubscribeEvent public static void tick(ClientTickEvent.Post event){
        if(!ENABLED)return;var mc=Minecraft.getInstance();if(mc.level==null||mc.player==null||mc.isPaused())return;
        if(++ticks==40){
            for(String c:new String[]{"kill @e[type=!minecraft:player]","gamemode spectator","time set noon","weather rain","gamerule minecraft:advance_time false"})mc.player.connection.sendCommand(c);
            mc.getWindow().setWindowed(1280,720);mc.options.enableVsync().set(false);mc.options.framerateLimit().set(120);mc.options.cloudStatus().set(CloudStatus.OFF);
            if(!mc.gui.hud.isHidden())mc.gui.hud.toggle();ClientConfig.ENGINE_STARTUP_EFFECTS.set(false);ClientConfig.ADAPTIVE_EFFECTS.set(false);
        }
        if(ticks<160)return;int next=START+(ticks-160)/220;age=(ticks-160)%220;
        if(next>=Math.min(END,NAMES.length)){System.out.println("M8_SONIC all_passed=true start="+START+" end="+END);mc.stop();return;}
        if(next!=phase){
            for(var ac:actors)ac.discard();actors.clear();SupersonicEffects.clear();mc.particleEngine.clearParticles();phase=next;peak=conePeak=pendingPeak=0;
            ClientConfig.EFFECT_QUALITY.set(EffectQuality.ENHANCED);ClientConfig.EFFECT_STYLE.set(phase==9?ClientConfig.EffectStyle.CLASSIC:ClientConfig.EffectStyle.POLISHED);
            ClientConfig.SUPERSONIC_EFFECTS.set(phase!=8);ClientConfig.AIRCRAFT_CONDENSATION.set(true);ClientConfig.EFFECT_BUDGET.set(phase==13?ClientConfig.EffectBudget.LOW:ClientConfig.EffectBudget.BALANCED);
            mc.player.connection.sendCommand(phase==3||phase==4?"weather clear":"weather rain");mc.options.fov().set(phase==6?35:60);mc.options.renderDistance().set(phase==6?16:4);mc.options.broadcastOptions();
            String model=phase==1?"f-15e":phase==2?"f22a":phase==5?"a-10":"f-35a";
            for(int j=0;j<(phase==11||phase==14?32:1);j++){
                var ac=new Plane(mc);ac.setId(975000+phase*100+j);ac.setAircraftTypeName(model);ac.setTextureName(model);ac.setPos((j%4-1.5)*7,100,20+j/4*4);
                var parts=ac.getParts();for(int k=0;k<parts.length;k++)parts[k].setId(995000+phase*10000+j*100+k);actors.add(ac);mc.level.addEntity(ac);
                if(phase==7){long hp=ComponentHealth.FULL;var zones=DamageZones.of(ac.getAcInfo());for(int k=0;k<zones.size();k++)if(AirframeDamage.bit(zones.get(k).kind(),zones.get(k).index())==1)hp=ComponentHealth.withLevel(hp,k,0);ac.setComponentHealth(hp);}
            }
            if(mc.getSoundManager().getSoundEvent(Identifier.fromNamespaceAndPath("mcheli_ce_port","m8_sonic_boom"))==null)throw new IllegalStateException("Sonic sound resource missing");
            System.out.println("M8_SONIC scene="+NAMES[phase]+" actors="+actors.size()+" mach_one="+actors.getFirst().getAcInfo().machOneSpeed);
        }
        if(age%30==0){var p=actors.getFirst().position();Vec3 at=fixed()?new Vec3(15,phase==6?300:180,90):p.add(14,9,-24);mc.player.connection.sendCommand("tp @s "+at.x+" "+at.y+" "+at.z+" 30 "+(fixed()?60:18));}
        var all=particles(mc);int cone=(int)all.stream().filter(SupersonicConeParticle.class::isInstance).count();int total=(int)all.stream().filter(McheliParticle.class::isInstance).count();
        peak=Math.max(peak,total);conePeak=Math.max(conePeak,cone);pendingPeak=Math.max(pendingPeak,SupersonicEffects.pending());
        if(total>1664||cone>28||SupersonicEffects.conesThisTick()>2||SupersonicEffects.stateCount()>64||pendingPeak>16||SupersonicEffects.voicesPlaying()>4)throw new IllegalStateException("Supersonic bounds exceeded");
        if((phase==3||phase==5||phase==7||phase==8||phase==9||phase==13)&&cone!=0)throw new IllegalStateException("Excluded cone "+NAMES[phase]);
        if(phase==4&&age<45&&(cone!=0||SupersonicEffects.boomsPlayed()!=0))throw new IllegalStateException("Already-fast aircraft invented initial events");
        if(phase==12&&age==59&&cone!=0)throw new IllegalStateException("Warp dragged the vapor cone");
        if(age==140&&(phase<3||phase==10||phase==11)&&conePeak==0)throw new IllegalStateException("No wet transonic cone "+NAMES[phase]);
        if(age==165&&fixed()&&SupersonicEffects.boomsPlayed()==0)throw new IllegalStateException("No delayed observer boom "+NAMES[phase]);
        if(age==55||age==58||age==145)capture=NAMES[phase]+(age==55?"-crossing":age==58?"-cone":"-after");
        if(age==205){if(cone!=0)throw new IllegalStateException("Cone failed to drain");System.out.println("M8_SONIC passed=true scene="+NAMES[phase]+" peak_mod="+peak+" peak_cones="+conePeak+" peak_pending="+pendingPeak+" booms="+SupersonicEffects.boomsPlayed());}
    }
    private static Collection<?> particles(Minecraft mc){try{
        var f=net.minecraft.client.particle.ParticleEngine.class.getDeclaredField("particles");f.setAccessible(true);var p=net.minecraft.client.particle.ParticleGroup.class.getDeclaredField("particles");p.setAccessible(true);var all=new ArrayList<>();
        for(var group:((Map<?,?>)f.get(mc.particleEngine)).values())all.addAll((Collection<?>)p.get(group));return all;
    }catch(ReflectiveOperationException ex){throw new IllegalStateException(ex);}}
    @SubscribeEvent public static void camera(ViewportEvent.ComputeFov e){if(ENABLED&&!actors.isEmpty())e.getCamera().setPosition(fixed()?new Vec3(15,phase==6?300:180,90):actors.getFirst().position().add(14,9,-24));}
    @SubscribeEvent public static void angles(ViewportEvent.ComputeCameraAngles e){if(ENABLED&&phase>=0){e.setYaw(30);e.setPitch(fixed()?60:18);e.setRoll(0);}}
    @SubscribeEvent public static void frame(RenderFrameEvent.Post e){if(capture!=null){var mc=Minecraft.getInstance();String n=capture;capture=null;Screenshot.grab(mc.gameDirectory,"m8-sonic-"+n+".png",mc.gameRenderer.mainRenderTarget(),1,m->System.out.println("M8_SONIC "+m.getString()));}}
}
