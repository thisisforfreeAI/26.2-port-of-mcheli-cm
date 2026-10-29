package io.github.mcheliceport.client.render;

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

/** Real authored lamps and cockpit surfaces, with native terrain/LOS and load checks. */
@EventBusSubscriber(modid="mcheli_ce_port",value=Dist.CLIENT)
public final class NightLightingProbe {
    private static final boolean ENABLED=Boolean.getBoolean("mcheli.probeNightLighting");
    private static final int START=Integer.getInteger("mcheli.probeNightStart",0),END=Integer.getInteger("mcheli.probeNightEnd",16);
    private static final String[] NAMES={"f-35a-night","f-15e-night","a-10-night","f-35a-cockpit","f-15e-cockpit","a-10-cockpit","landing-off","disabled","daylight","classic","gear-folded","water","blocked","unpowered","shared-load","parked-pool"};
    private static final List<Plane> actors=new ArrayList<>();
    private static int ticks,phase=-1,age,peakLamps,peakCockpit,peakRays,peakSurfaces,peakSources;private static boolean captured;private static String capture;
    private static boolean cockpit(){return phase>=3&&phase<=5;}
    private static final class Plane extends PlaneEntity {
        Plane(Minecraft mc){super(ModEntityTypes.PLANE.get(),mc.level);}
        @Override public void tick(){xo=getX();yo=getY();zo=getZ();yRotO=0;xRotO=0;prevRoll=0;tickCount++;setYaw(0);setPitch(0);setRoll(0);setDeltaMovement(Vec3.ZERO);setOnGround(false);}
        @Override public int getHP(){return getMaxHP();}@Override public int getFuel(){return phase==13?0:getMaxFuel();}
        @Override public double getCurrentThrottle(){return phase==13?0:.08;}@Override public boolean isDestroyed(){return false;}
        @Override public boolean isInfinityFuel(net.minecraft.world.entity.Entity rider,boolean others){return phase!=13&&super.isInfinityFuel(rider,others);}
    }
    @SubscribeEvent public static void tick(ClientTickEvent.Post event){
        if(!ENABLED)return;var mc=Minecraft.getInstance();if(mc.level==null||mc.player==null||mc.isPaused())return;
        if(++ticks==40){
            for(String c:new String[]{"kill @e[type=!minecraft:player]","gamemode creative","effect clear @s","time set midnight","weather clear","gamerule minecraft:advance_time false","fill -24 -60 0 24 -50 40 air","fill -24 -61 0 24 -61 40 stone","tp @s 0 -48 10 0 0"})mc.player.connection.sendCommand(c);
            mc.getWindow().setWindowed(1280,720);mc.options.enableVsync().set(false);mc.options.framerateLimit().set(120);mc.options.cloudStatus().set(CloudStatus.OFF);mc.options.fov().set(60);mc.options.gamma().set(.5D);mc.options.guiScale().set(2);mc.resizeGui();
            ClientConfig.ENGINE_STARTUP_EFFECTS.set(false);ClientConfig.ADAPTIVE_EFFECTS.set(false);ClientConfig.SUPERSONIC_EFFECTS.set(false);ClientConfig.LANDING_FEEDBACK.set(false);ClientConfig.HIDE_KEY_HINTS.set(true);
        }
        if(ticks<160)return;int next=START+(ticks-160)/190;age=(ticks-160)%190;
        if(next>=Math.min(END,NAMES.length)){mc.player.stopRiding();System.out.println("M8_NIGHT all_passed=true start="+START+" end="+END);mc.stop();return;}
        if(next!=phase){
            mc.player.stopRiding();actors.forEach(Plane::discard);actors.clear();FlightLighting.clear();phase=next;peakLamps=peakCockpit=peakRays=peakSurfaces=peakSources=0;captured=false;
            ClientConfig.EFFECT_QUALITY.set(EffectQuality.ENHANCED);ClientConfig.EFFECT_STYLE.set(phase==9?ClientConfig.EffectStyle.CLASSIC:ClientConfig.EffectStyle.POLISHED);
            ClientConfig.EFFECT_BUDGET.set(ClientConfig.EffectBudget.BALANCED);ClientConfig.NIGHT_FLIGHT_LIGHTING.set(phase!=7);
            mc.options.setCameraType(CameraType.FIRST_PERSON);
            if(mc.gui.hud.isHidden()==cockpit())mc.gui.hud.toggle();
            for(String c:new String[]{phase==8?"time set noon":"time set midnight","fill -24 -60 0 24 -50 40 air","fill -24 -61 0 24 -61 40 "+(phase==11?"water":"stone")})mc.player.connection.sendCommand(c);
            if(phase==12)mc.player.connection.sendCommand("fill -4 -60 14 4 -50 15 stone");
            String model=phase==1||phase==4?"f-15e":phase==2||phase==5?"a-10":"f-35a";
            for(int j=0;j<(phase==14?32:1);j++){
                var ac=new Plane(mc);ac.setId(1150000+phase*100+j);ac.setAircraftTypeName(model);ac.setTextureName(model);ac.setPos(phase==14?(j%8-3.5)*3:phase==15?.5:0,phase==15?-60:-57,phase==14?10+j/8*4:phase==15?10.5:10);
                var parts=ac.getParts();for(int k=0;k<parts.length;k++)parts[k].setId(1450000+phase*10000+j*100+k);actors.add(ac);mc.level.addEntity(ac);
                if(ac.getAcInfo().flightLamps.size()!=5||ac.getAcInfo().searchLights.size()!=1)throw new IllegalStateException("Managed pack did not refresh night profile "+model);
                // Only this client-only fixture writes the client status; production light changes remain server-owned.
                ac.setCommonStatus(6,phase!=6,true);if(ac.partLandingGear!=null)ac.partLandingGear.rotation=ac.partLandingGear.prevRotation=phase==10?90:0;
                if(ac.partCanopy!=null)ac.partCanopy.rotation=ac.partCanopy.prevRotation=0;
            }
            if(cockpit()&&!mc.player.startRiding(actors.getFirst(),true,true))throw new IllegalStateException("Night cockpit pilot mount failed");
            System.out.println("M8_NIGHT scene="+NAMES[phase]+" actors="+actors.size());
        }
        if(cockpit()){
            if(mc.player.getVehicle()!=actors.getFirst())mc.player.startRiding(actors.getFirst(),true,true);
            mc.player.setYRot(0);mc.player.yRotO=0;mc.player.setXRot(8);mc.player.xRotO=8;
        }else if(age%30==0){var p=actors.getFirst().position();mc.player.connection.sendCommand("tp @s "+(p.x+16)+" "+(p.y+9)+" "+(p.z+8)+" 90 28");}
        if(age>=40){peakLamps=Math.max(peakLamps,FlightLighting.lampsDrawn());peakCockpit=Math.max(peakCockpit,FlightLighting.cockpitVertices());peakRays=Math.max(peakRays,LandingLightGround.rays());peakSurfaces=Math.max(peakSurfaces,LandingLightGround.surfaces());peakSources=Math.max(peakSources,LandingLightGround.sources());}
        if(FlightLighting.lampsDrawn()>64||FlightLighting.cockpitVertices()>4096||LandingLightGround.rays()>19||LandingLightGround.sources()>4||LandingLightGround.surfaces()>36)throw new IllegalStateException("Night lighting bounds exceeded");
        if(age==99)mc.gui.hud.getChat().clearMessages(false);
        if(age==150){
            if((phase<6||phase==14||phase==15)&&(peakLamps==0||peakSurfaces==0))throw new IllegalStateException("No night lamps/terrain patch "+NAMES[phase]+" lamps="+peakLamps+" surfaces="+peakSurfaces+" sky="+mc.level.getSkyDarken());
            if(cockpit()&&peakCockpit==0)throw new IllegalStateException("No instrument-area illumination "+NAMES[phase]);
            if((phase==6||phase==8||phase==10||phase==11||phase==12)&&peakLamps==0)throw new IllegalStateException("Navigation lamps lost with unrelated state "+NAMES[phase]);
            if((phase==6||phase==7||phase==8||phase==9||phase==10||phase==11||phase==12||phase==13)&&peakSurfaces!=0)throw new IllegalStateException("Excluded terrain light patch "+NAMES[phase]);
            if((phase==7||phase==9||phase==13)&&(peakLamps!=0||peakCockpit!=0))throw new IllegalStateException("Excluded lighting still rendered");
            if(phase==14&&(peakLamps!=64||peakSources!=4))throw new IllegalStateException("Night load not exercised "+peakLamps+"/"+peakSources);
            System.out.println("M8_NIGHT passed=true scene="+NAMES[phase]+" peak_lamps="+peakLamps+" peak_cockpit_vertices="+peakCockpit+" peak_rays="+peakRays+" peak_surfaces="+peakSurfaces+" peak_sources="+peakSources);
        }
    }
    @SubscribeEvent public static void camera(ViewportEvent.ComputeFov e){if(ENABLED&&!actors.isEmpty()&&!cockpit())e.getCamera().setPosition(actors.getFirst().position().add(16,7,-13));}
    @SubscribeEvent public static void angles(ViewportEvent.ComputeCameraAngles e){if(ENABLED&&phase>=0&&!cockpit()){e.setYaw(48);e.setPitch(14);e.setRoll(0);}}
    @SubscribeEvent public static void frame(RenderFrameEvent.Post e){
        if(!ENABLED||phase<0)return;
        if(!captured&&age>=100&&(cockpit()?FlightLighting.cockpitVertices()>0:FlightLighting.lampsDrawn()>3||age>=130)){capture=NAMES[phase];captured=true;}
        if(capture!=null){var mc=Minecraft.getInstance();String n=capture;capture=null;Screenshot.grab(mc.gameDirectory,"m8-night-"+n+".png",mc.gameRenderer.mainRenderTarget(),1,m->System.out.println("M8_NIGHT "+m.getString()));}
    }
}
