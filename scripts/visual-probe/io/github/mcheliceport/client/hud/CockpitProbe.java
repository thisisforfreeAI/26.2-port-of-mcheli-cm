package io.github.mcheliceport.client.hud;

import io.github.mcheliceport.config.*;
import io.github.mcheliceport.damage.*;
import io.github.mcheliceport.entity.plane.PlaneEntity;
import io.github.mcheliceport.registry.ModEntityTypes;
import net.minecraft.client.*;
import net.minecraft.client.Screenshot;
import net.minecraft.world.phys.Vec3;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.*;

/** Mounted client-only real-model states; not a flight or combat recording. */
@EventBusSubscriber(modid="mcheli_ce_port",value=Dist.CLIENT)
public final class CockpitProbe {
    private static final boolean ENABLED=Boolean.getBoolean("mcheli.probeCockpit");
    private static final int START=Integer.getInteger("mcheli.probeCockpitStart",0);
    private static final int END=Integer.getInteger("mcheli.probeCockpitEnd",11);
    private static final String[] NAMES={"f-35a-rain","f-15e-damage","a-10-night","repaired","open-canopy","third-person","remote-camera","disabled","classic","unmounted","engine-off"};
    private static int ticks,phase=-1,age,peak;private static Plane plane;private static String capture;
    private static boolean damaged(){return phase==1||phase==3&&age<70;}
    private static final class Plane extends PlaneEntity {
        Plane(Minecraft mc){super(ModEntityTypes.PLANE.get(),mc.level);}
        @Override public void tick(){xo=getX();yo=getY();zo=getZ();yRotO=0;xRotO=0;prevRoll=0;setYaw(0);setPitch(0);setRoll(0);setDeltaMovement(.04,0,.6);setOnGround(false);}
        @Override public int getHP(){return damaged()?Math.max(1,getMaxHP()/3):getMaxHP();}
        @Override public int getFuel(){return phase==10?0:damaged()?Math.max(1,getMaxFuel()/10):getMaxFuel();}
        @Override public double getCurrentThrottle(){return .7;}
        @Override public int getRadarBlankTicks(){return damaged()?100:0;}
        @Override public boolean isDestroyed(){return false;}
        @Override public int getCameraId(){return phase==6?1:0;}
    }
    @SubscribeEvent public static void tick(ClientTickEvent.Post event){
        if(!ENABLED)return;var mc=Minecraft.getInstance();if(mc.level==null||mc.player==null||mc.isPaused())return;
        if(++ticks==40){
            for(String c:new String[]{"kill @e[type=!minecraft:player]","gamemode creative","time set noon","weather rain","gamerule minecraft:advance_time false"})mc.player.connection.sendCommand(c);
            mc.getWindow().setWindowed(1280,720);mc.options.enableVsync().set(false);mc.options.framerateLimit().set(120);
            mc.options.guiScale().set(2);mc.resizeGui();if(mc.gui.hud.isHidden())mc.gui.hud.toggle();
            ClientConfig.EFFECT_QUALITY.set(EffectQuality.ENHANCED);ClientConfig.EFFECT_BUDGET.set(ClientConfig.EffectBudget.BALANCED);ClientConfig.ADAPTIVE_EFFECTS.set(false);
            ClientConfig.HIDE_KEY_HINTS.set(true);
        }
        if(ticks<160)return;int next=START+(ticks-160)/200;age=(ticks-160)%200;
        if(next>=Math.min(END,NAMES.length)){mc.player.stopRiding();CockpitFeedbackLayer.clear();System.out.println("M8_COCKPIT all_passed=true start="+START+" end="+END);mc.stop();return;}
        if(next!=phase){
            mc.player.stopRiding();if(plane!=null)plane.discard();CockpitFeedbackLayer.clear();phase=next;peak=0;
            ClientConfig.EFFECT_STYLE.set(phase==8?ClientConfig.EffectStyle.CLASSIC:ClientConfig.EffectStyle.POLISHED);ClientConfig.COCKPIT_FEEDBACK.set(phase!=7);
            mc.options.setCameraType(phase==5?CameraType.THIRD_PERSON_BACK:CameraType.FIRST_PERSON);
            mc.player.connection.sendCommand(phase==2?"time set midnight":"time set noon");mc.player.connection.sendCommand(phase==3?"weather clear":"weather rain");
            plane=new Plane(mc);plane.setId(981000+phase);String model=phase==1?"f-15e":phase==2?"a-10":"f-35a";
            plane.setAircraftTypeName(model);plane.setTextureName(model);plane.setPos(0,100,30);
            var parts=plane.getParts();for(int i=0;i<parts.length;i++)parts[i].setId(991000+phase*100+i);mc.level.addEntity(plane);
            if(phase!=9&&!mc.player.startRiding(plane,true,true))throw new IllegalStateException("Pilot could not mount cockpit fixture");
            System.out.println("M8_COCKPIT scene="+NAMES[phase]+" canopy="+plane.getAcInfo().haveCanopy());
        }
        if(phase!=9&&mc.player.getVehicle()!=plane)mc.player.startRiding(plane,true,true);
        mc.player.setYRot(0);mc.player.yRotO=0;mc.player.setXRot(0);mc.player.xRotO=0;
        if(plane.partCanopy!=null)plane.partCanopy.rotation=plane.partCanopy.prevRotation=phase==4?90:0;
        long health=ComponentHealth.FULL;if(damaged())for(int i=0;i<DamageZones.of(plane.getAcInfo()).size();i++)if(DamageZones.of(plane.getAcInfo()).get(i).kind()==ComponentKind.ENGINE)health=ComponentHealth.withLevel(health,i,64);
        plane.setComponentHealth(health);
        peak=Math.max(peak,CockpitFeedbackLayer.drawnDrops());if(peak>28)throw new IllegalStateException("Unbounded cockpit rain");
        if(age==139)mc.gui.hud.getChat().clearMessages(false);
        if(age==140){
            boolean expected=phase<=3||phase==10;
            if(CockpitFeedbackLayer.active()!=expected)throw new IllegalStateException("Wrong cockpit view gate: "+NAMES[phase]);
            if(phase<3&&(CockpitFeedbackLayer.wetness()<.9||peak==0))throw new IllegalStateException("No closed-canopy rain: "+NAMES[phase]);
            if(phase==3&&(CockpitFeedbackLayer.damage()>.01||CockpitFeedbackLayer.wetness()>.01))throw new IllegalStateException("Repair/weather did not clear cockpit damage/rain");
            if(!expected&&peak>0)throw new IllegalStateException("Rain leaked into excluded view");
            if(phase==10&&CockpitFeedbackLayer.instrumentThrottle(plane)!=0)throw new IllegalStateException("Engine-off cockpit still vibrated");
            capture=NAMES[phase];
        }
        if(age==180)System.out.println("M8_COCKPIT passed=true scene="+NAMES[phase]+" peak_drops="+peak+" damage="+CockpitFeedbackLayer.damage());
    }
    @SubscribeEvent public static void frame(RenderFrameEvent.Post event){if(capture!=null){var mc=Minecraft.getInstance();String name=capture;capture=null;Screenshot.grab(mc.gameDirectory,"m8-cockpit-"+name+".png",mc.gameRenderer.mainRenderTarget(),1,m->System.out.println("M8_COCKPIT "+m.getString()));}}
}
