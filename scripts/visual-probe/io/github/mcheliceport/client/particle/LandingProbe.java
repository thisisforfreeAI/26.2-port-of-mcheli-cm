package io.github.mcheliceport.client.particle;

import io.github.mcheliceport.config.*;
import io.github.mcheliceport.entity.plane.PlaneEntity;
import io.github.mcheliceport.registry.ModEntityTypes;
import java.util.*;
import net.minecraft.client.*;
import net.minecraft.world.phys.Vec3;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.*;

/** Held intact aircraft on real terrain; exercises production footprint sampling and rendering. */
@EventBusSubscriber(modid="mcheli_ce_port",value=Dist.CLIENT)
public final class LandingProbe {
    private static final boolean ENABLED=Boolean.getBoolean("mcheli.probeLanding");
    private static final int START=Integer.getInteger("mcheli.probeLandingStart",0),END=Integer.getInteger("mcheli.probeLandingEnd",12);
    private static final String[] NAMES={"f-35a-runway","f-15e-sand","a-10-grass","hard-contact","parked","gear-up","disabled","classic","warp","shared-load","uneven-load","water"};
    private static final List<Plane> actors=new ArrayList<>();
    private static int ticks,phase=-1,age,peakBirths,peakRays,peakStates,peakPuffs,peakSparks,photoAt;private static float peakCompression;private static String capture;
    private static final class Plane extends PlaneEntity {
        Plane(Minecraft mc){super(ModEntityTypes.PLANE.get(),mc.level);}
        @Override public void tick(){
            int i=actors.indexOf(this);xo=getX();yo=getY();zo=getZ();yRotO=0;xRotO=0;prevRoll=getRoll();
            double height=phase==4||phase==10?0:phase==8?(age<60?20:0):Math.max(0,(60-age)*(phase==3?.65:.22));
            double x=phase==9?(i%8-3.5)*3:0,z=phase==9?10+i/8*3:10+Math.min(age,100)*.05;
            setPos(x,-60+height,z);setDeltaMovement(position().subtract(xo,yo,zo));setYaw(0);setPitch(0);setRoll(phase==10?2:0);setOnGround(height==0);
            if(phase==5&&partLandingGear!=null)partLandingGear.rotation=partLandingGear.prevRotation=90;
        }
        @Override public int getHP(){return getMaxHP();}@Override public boolean isDestroyed(){return false;}
    }
    @SubscribeEvent public static void tick(ClientTickEvent.Post event){
        if(!ENABLED)return;var mc=Minecraft.getInstance();if(mc.level==null||mc.player==null||mc.isPaused())return;
        if(++ticks==40){
            for(String c:new String[]{"kill @e[type=!minecraft:player]","gamemode spectator","time set noon","weather clear","gamerule minecraft:advance_time false","fill -24 -60 0 24 -50 40 air","fill -24 -61 0 24 -61 40 stone"})mc.player.connection.sendCommand(c);
            mc.getWindow().setWindowed(1280,720);mc.options.enableVsync().set(false);mc.options.framerateLimit().set(120);mc.options.cloudStatus().set(CloudStatus.OFF);mc.options.fov().set(60);if(!mc.gui.hud.isHidden())mc.gui.hud.toggle();
            ClientConfig.ENGINE_STARTUP_EFFECTS.set(false);ClientConfig.ADAPTIVE_EFFECTS.set(false);ClientConfig.SUPERSONIC_EFFECTS.set(false);
        }
        if(ticks<160)return;int next=START+(ticks-160)/170;age=(ticks-160)%170;
        if(next>=Math.min(END,NAMES.length)){System.out.println("M8_LANDING all_passed=true start="+START+" end="+END);mc.stop();return;}
        if(next!=phase){
            actors.forEach(Plane::discard);actors.clear();LandingEffects.clear();mc.particleEngine.clearParticles();phase=next;peakBirths=peakRays=peakStates=peakPuffs=peakSparks=0;peakCompression=0;photoAt=-100;
            ClientConfig.EFFECT_QUALITY.set(EffectQuality.ENHANCED);ClientConfig.EFFECT_STYLE.set(phase==7?ClientConfig.EffectStyle.CLASSIC:ClientConfig.EffectStyle.POLISHED);
            ClientConfig.LANDING_FEEDBACK.set(phase!=6);ClientConfig.EFFECT_BUDGET.set(ClientConfig.EffectBudget.BALANCED);
            mc.player.connection.sendCommand("fill -24 -61 0 24 -61 40 "+(phase==1?"sand":phase==2?"grass_block":phase==11?"water":"stone"));
            String model=phase==1?"f-15e":phase==2?"a-10":"f-35a";
            for(int j=0;j<(phase==9?64:1);j++){
                var ac=new Plane(mc);ac.setId(1080000+phase*100+j);ac.setAircraftTypeName(model);ac.setTextureName(model);ac.setPos(0,-40,10);
                var parts=ac.getParts();for(int k=0;k<parts.length;k++)parts[k].setId(1200000+phase*10000+j*100+k);actors.add(ac);mc.level.addEntity(ac);
            }
            System.out.println("M8_LANDING scene="+NAMES[phase]+" actors="+actors.size());
        }
        var p=actors.getFirst().position();if(age%30==0)mc.player.connection.sendCommand("tp @s "+(p.x+10)+" "+(p.y+4)+" "+(p.z-12)+" 40 12");
        var all=particles(mc);int puffs=(int)all.stream().filter(LandingPuffParticle.class::isInstance).count(),sparks=(int)all.stream().filter(SparkParticle.class::isInstance).count();
        peakBirths=Math.max(peakBirths,LandingEffects.births());peakRays=Math.max(peakRays,LandingEffects.rays());peakStates=Math.max(peakStates,LandingEffects.states());peakPuffs=Math.max(peakPuffs,puffs);peakSparks=Math.max(peakSparks,sparks);peakCompression=Math.max(peakCompression,LandingEffects.compression(actors.getFirst()));
        if(LandingEffects.births()>12||LandingEffects.rays()>24||LandingEffects.states()>32||LandingEffects.compression(actors.getFirst())>.1201F)throw new IllegalStateException("Landing effect bounds exceeded");
        if(photoAt==-100&&LandingEffects.births()>0){photoAt=age;capture=NAMES[phase]+"-touchdown";}
        if(age==photoAt+5)capture=NAMES[phase]+"-gear";
        if(age==100)capture=NAMES[phase]+"-settled";
        if(age==145){
            if((phase<4||phase==9)&&(LandingEffects.touchdowns()==0||peakBirths==0))throw new IllegalStateException("No real ground touchdown "+NAMES[phase]+" supported="+LandingEffects.supported(actors.getFirst()));
            if(phase==3&&peakSparks==0)throw new IllegalStateException("Hard stone contact did not emit sparks");
            if((phase>=4&&phase<=8||phase==11)&&LandingEffects.touchdowns()!=0)throw new IllegalStateException("Excluded or stationary touchdown "+NAMES[phase]);
            if((phase==4||phase==8||phase==10)&&LandingEffects.compression(actors.getFirst())<.01)throw new IllegalStateException("No parked gear load");
            if(phase==10&&LandingEffects.spread(actors.getFirst())<.005)throw new IllegalStateException("No uneven suspension response");
            if((phase==5||phase==6||phase==7)&&LandingEffects.states()!=0)throw new IllegalStateException("Excluded landing retained state");
            if(phase==9&&peakStates<16)throw new IllegalStateException("Landing state load not exercised");
            System.out.println("M8_LANDING passed=true scene="+NAMES[phase]+" peak_states="+peakStates+" peak_rays="+peakRays+" peak_births="+peakBirths+" peak_puffs="+peakPuffs+" peak_sparks="+peakSparks+" peak_compression="+peakCompression+" touchdowns="+LandingEffects.touchdowns());
        }
    }
    private static Collection<?> particles(Minecraft mc){try{
        var f=net.minecraft.client.particle.ParticleEngine.class.getDeclaredField("particles");f.setAccessible(true);var p=net.minecraft.client.particle.ParticleGroup.class.getDeclaredField("particles");p.setAccessible(true);var all=new ArrayList<>();
        for(var group:((Map<?,?>)f.get(mc.particleEngine)).values())all.addAll((Collection<?>)p.get(group));return all;
    }catch(ReflectiveOperationException ex){throw new IllegalStateException(ex);}}
    @SubscribeEvent public static void camera(ViewportEvent.ComputeFov e){if(ENABLED&&!actors.isEmpty())e.getCamera().setPosition(actors.getFirst().position().add(age>photoAt&&age<=photoAt+7?new Vec3(9,3,-13):new Vec3(13,6,-19)));}
    @SubscribeEvent public static void angles(ViewportEvent.ComputeCameraAngles e){if(ENABLED&&phase>=0){e.setYaw(35);e.setPitch(age>photoAt&&age<=photoAt+7?12:16);e.setRoll(0);}}
    @SubscribeEvent public static void frame(RenderFrameEvent.Post e){if(capture!=null){var mc=Minecraft.getInstance();String n=capture;capture=null;Screenshot.grab(mc.gameDirectory,"m8-landing-"+n+".png",mc.gameRenderer.mainRenderTarget(),1,m->System.out.println("M8_LANDING "+m.getString()));}}
}
