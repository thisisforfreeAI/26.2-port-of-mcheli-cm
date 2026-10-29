package io.github.mcheliceport.client.render;

import io.github.mcheliceport.appearance.AircraftAppearance;
import io.github.mcheliceport.client.gui.*;
import io.github.mcheliceport.config.*;
import io.github.mcheliceport.entity.plane.PlaneEntity;
import io.github.mcheliceport.registry.ModEntityTypes;
import java.util.*;
import net.minecraft.client.*;
import net.minecraft.client.gui.components.*;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.world.phys.Vec3;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.*;

/** Held real models plus one actual server menu -> appearance packet -> synced entity check. */
@EventBusSubscriber(modid="mcheli_ce_port",value=Dist.CLIENT)
public final class AircraftAppearanceProbe {
    private static final boolean ENABLED=Boolean.getBoolean("mcheli.probeAppearance");
    private static final int START=Integer.getInteger("mcheli.probeAppearanceStart",0),END=Integer.getInteger("mcheli.probeAppearanceEnd",7);
    private static final String[] NAMES={"f-35a-127","f-15e-204","a-10-042","disabled","classic","shared-load","server-menu"};
    private static final List<Plane> ACTORS=new ArrayList<>();
    private static int ticks,phase=-1,age,peak;private static String capture;private static volatile int serverId=-1;private static Vec3 center=new Vec3(0,-60,20);
    private static final class Plane extends PlaneEntity{
        Plane(Minecraft mc){super(ModEntityTypes.PLANE.get(),mc.level);}
        @Override public void tick(){xo=getX();yo=getY();zo=getZ();yRotO=xRotO=prevRoll=0;tickCount++;setYaw(0);setPitch(0);setRoll(0);setDeltaMovement(Vec3.ZERO);setOnGround(true);}
        @Override public int getHP(){return getMaxHP();}@Override public boolean isDestroyed(){return false;}
    }
    private static void press(Minecraft mc,String label){
        for(var widget:mc.gui.screen().children())if(widget instanceof Button b&&b.getMessage().getString().startsWith(label)){b.onPress(new KeyEvent(257,0,0));return;}
        throw new IllegalStateException("Missing button "+label);
    }
    @SubscribeEvent public static void tick(ClientTickEvent.Post e){
        if(!ENABLED)return;var mc=Minecraft.getInstance();if(mc.level==null||mc.player==null||mc.isPaused())return;
        if(++ticks==40){
            for(String c:new String[]{"kill @e[type=!minecraft:player]","gamemode creative","time set noon","weather clear","gamerule minecraft:advance_time false","fill -24 -60 0 24 -48 42 air","fill -24 -61 0 24 -61 42 stone"})mc.player.connection.sendCommand(c);
            mc.getWindow().setWindowed(1280,720);mc.options.enableVsync().set(false);mc.options.framerateLimit().set(120);mc.options.cloudStatus().set(CloudStatus.OFF);mc.options.fov().set(55);if(!mc.gui.hud.isHidden())mc.gui.hud.toggle();
            ClientConfig.ADAPTIVE_EFFECTS.set(false);ClientConfig.ENGINE_STARTUP_EFFECTS.set(false);
        }
        if(ticks<160)return;int next=START+(ticks-160)/220;age=(ticks-160)%220;
        if(next>=Math.min(END,NAMES.length)){System.out.println("M8_APPEARANCE all_passed=true start="+START+" end="+END);mc.stop();return;}
        if(next!=phase){
            if(mc.gui.screen()!=null)mc.gui.screen().onClose();for(var ac:ACTORS)ac.discard();ACTORS.clear();phase=next;peak=0;center=new Vec3(0,-60,20);mc.particleEngine.clearParticles();
            ClientConfig.EFFECT_QUALITY.set(EffectQuality.ENHANCED);ClientConfig.EFFECT_STYLE.set(phase==4?ClientConfig.EffectStyle.CLASSIC:ClientConfig.EffectStyle.POLISHED);ClientConfig.EFFECT_BUDGET.set(ClientConfig.EffectBudget.BALANCED);ClientConfig.AIRCRAFT_CUSTOMIZATION.set(phase!=3);
            if(phase!=6)for(int i=0;i<(phase==5?32:1);i++){
                var ac=new Plane(mc);String name=phase==1?"f-15e":phase==2?"a-10":"f-35a";ac.setId(1181000+i);ac.setAircraftTypeName(name);ac.setTextureName(name);ac.setPos(center.add(phase==5?(i%8-3)*14:0,0,phase==5?(i/8)*15:0));
                ac.setAppearance(new AircraftAppearance(phase==1?204:phase==2?42:127,phase==1?3:phase==2?2:1,phase==1?2:1,phase==1?2:0));
                var parts=ac.getParts();for(int j=0;j<parts.length;j++)parts[j].setId(1660000+i*100+j);mc.level.addEntity(ac);ACTORS.add(ac);
            }
            else mc.getSingleplayerServer().execute(()->{
                var level=mc.getSingleplayerServer().overworld();var ac=new PlaneEntity(ModEntityTypes.PLANE.get(),level);ac.setAircraftTypeName("f-35a");ac.setTextureName("f-35a");ac.setPos(0,-60,20);ac.setYaw(0);level.addFreshEntity(ac);serverId=ac.getId();
                var player=mc.getSingleplayerServer().getPlayerList().getPlayers().getFirst();player.teleportTo(-2,-58,25);ac.openContainer(player);
            });
            System.out.println("M8_APPEARANCE scene="+NAMES[phase]);
        }
        if(phase==6){
            if(age==50){mc.getWindow().setWindowed(960,720);mc.options.guiScale().set(3);mc.resizeGui();}
            if(age==60){if(!(mc.gui.screen() instanceof AircraftScreen))throw new IllegalStateException("Server menu did not open");press(mc,"Appearance");}
            if(age==70){if(!(mc.gui.screen() instanceof AircraftAppearanceScreen))throw new IllegalStateException("Appearance menu did not open");
                for(var widget:mc.gui.screen().children())if(widget instanceof EditBox box)box.setValue("999");
                press(mc,"Squadron mark:");press(mc,"Pale paint");press(mc,"Clean");press(mc,"Service wear");press(mc,"Livery:");}
            if(age==80)capture="server-menu";
            if(age==90)press(mc,"Apply");
            if(age==110){var ac=(PlaneEntity)mc.level.getEntity(serverId);if(ac==null||!ac.getAppearance().equals(new AircraftAppearance(999,1,2,1)))throw new IllegalStateException("Appearance did not round-trip through server");
                if(!(mc.gui.screen() instanceof AircraftScreen))throw new IllegalStateException("Appearance did not return to live menu");mc.gui.screen().onClose();center=ac.position();System.out.println("M8_APPEARANCE server_packet_synced=true appearance="+ac.getAppearance());}
            if(age==150)capture="server-applied";
        }else if(age%30==0)mc.player.connection.sendCommand("tp @s -7 -57 27 235 8");
        if(age>20)peak=Math.max(peak,AircraftMarkings.vertices());
        if(AircraftMarkings.vertices()>6144)throw new IllegalStateException("Marking allowance exceeded");
        if(phase<3&&age==95)capture=NAMES[phase];
        if(age==195){if((phase<3||phase==5)&&peak==0)throw new IllegalStateException("Supported marking missing");if((phase==3||phase==4)&&peak!=0)throw new IllegalStateException("Marking opt-out failed");
            System.out.println("M8_APPEARANCE passed=true scene="+NAMES[phase]+" peak_vertices="+peak);}
    }
    @SubscribeEvent public static void camera(ViewportEvent.ComputeFov e){if(ENABLED&&phase>=0)e.getCamera().setPosition(center.add(phase==5?new Vec3(20,14,32):new Vec3(-5,3,8)));}
    @SubscribeEvent public static void angles(ViewportEvent.ComputeCameraAngles e){if(ENABLED&&phase>=0){e.setYaw(phase==5?145:235);e.setPitch(phase==5?20:6);e.setRoll(0);}}
    @SubscribeEvent public static void frame(RenderFrameEvent.Post e){if(capture!=null){var mc=Minecraft.getInstance();String name=capture;capture=null;Screenshot.grab(mc.gameDirectory,"m8-appearance-"+name+".png",mc.gameRenderer.mainRenderTarget(),1,m->System.out.println("M8_APPEARANCE "+m.getString()));}}
}
