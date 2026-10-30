package io.github.mcheliceport.client.render;

import io.github.mcheliceport.client.particle.ClientEffects;
import io.github.mcheliceport.config.*;
import io.github.mcheliceport.entity.plane.PlaneEntity;
import io.github.mcheliceport.registry.ModEntityTypes;
import java.util.*;
import net.minecraft.client.*;
import net.minecraft.core.BlockPos;
import net.minecraft.world.phys.Vec3;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.*;

/**
 * Before/after captures of realistic lighting with real stock aircraft: noon, low sun, a night
 * explosion beside the jet and a bomber half inside a hangar. Each scene runs with the setting off,
 * then on, from the same camera.
 */
@EventBusSubscriber(modid="mcheli_ce_port",value=Dist.CLIENT)
public final class RealisticLightingProbe {
    private static final boolean ENABLED=Boolean.getBoolean("mcheli.probeLighting");
    private static final String[] SCENES={"noon","dusk","night-explosion","hangar"};
    private static final int PHASE_TICKS=170;
    private static final List<Plane> actors=new ArrayList<>();
    private static int waiting,ticks,phase=-1,age,peakLights;private static boolean captured;private static String capture;
    private static String scene(){return SCENES[phase/2];}private static boolean on(){return phase%2==1;}
    private static final class Plane extends PlaneEntity {
        Plane(Minecraft mc){super(ModEntityTypes.PLANE.get(),mc.level);}
        @Override public void tick(){xo=getX();yo=getY();zo=getZ();yRotO=getYRot();xRotO=0;prevRoll=0;tickCount++;setPitch(0);setRoll(0);setDeltaMovement(Vec3.ZERO);setOnGround(true);}
        @Override public int getHP(){return getMaxHP();}@Override public boolean isDestroyed(){return false;}
    }
    @SubscribeEvent public static void tick(ClientTickEvent.Post event){
        if(!ENABLED)return;var mc=Minecraft.getInstance();
        if(mc.level==null&&++waiting%200==0)System.out.println("LIGHTING waiting screen="+(mc.gui.screen()==null?"none":mc.gui.screen().getClass().getName()));
        if(mc.level==null||mc.player==null||mc.isPaused())return;
        if(++ticks==40){
            for(String c:new String[]{"kill @e[type=!minecraft:player]","gamemode spectator","effect clear @s","weather clear","gamerule minecraft:advance_time false","fill -30 -61 -10 30 -61 40 grass_block","tp @s 0 -48 10 0 0"})mc.player.connection.sendCommand(c);
            mc.options.enableVsync().set(false);mc.options.framerateLimit().set(120);mc.options.cloudStatus().set(CloudStatus.OFF);mc.options.fov().set(60);mc.options.gamma().set(.5D);
            ClientConfig.ENGINE_STARTUP_EFFECTS.set(false);ClientConfig.ADAPTIVE_EFFECTS.set(false);ClientConfig.HIDE_KEY_HINTS.set(true);
            if(!mc.gui.hud.isHidden())mc.gui.hud.toggle();
        }
        if(ticks<160)return;int next=(ticks-160)/PHASE_TICKS;age=(ticks-160)%PHASE_TICKS;
        if(next>=SCENES.length*2){System.out.println("LIGHTING all_passed=true");mc.stop();return;}
        if(next!=phase){
            actors.forEach(Plane::discard);actors.clear();SceneLights.clear();phase=next;captured=false;peakLights=0;
            ClientConfig.EFFECT_QUALITY.set(EffectQuality.ENHANCED);ClientConfig.EFFECT_STYLE.set(ClientConfig.EffectStyle.POLISHED);
            ClientConfig.EFFECT_BUDGET.set(ClientConfig.EffectBudget.BALANCED);ClientConfig.REALISTIC_LIGHTING.set(on());
            String time=switch(scene()){case "dusk"->"12350";case "night-explosion"->"18000";default->"6000";};
            mc.player.connection.sendCommand("time set "+time);
            for(int z=-10;z<40;z+=10)mc.player.connection.sendCommand("fill -30 -60 "+z+" 30 -40 "+(z+9)+" air");mc.player.connection.sendCommand("kill @e[type=minecraft:slime]");
            boolean hangar=scene().equals("hangar");
            if(hangar){
                // A closed shed over the bomber's tail half, open towards the camera's side only at the front.
                mc.player.connection.sendCommand("fill -16 -60 13 16 -52 30 stone_bricks hollow");
                mc.player.connection.sendCommand("fill -16 -60 13 16 -53 13 air");
                mc.player.connection.sendCommand("fill -15 -59 14 15 -53 29 air");
            }
            var ac=new Plane(mc);ac.setId(1190000+phase);String model=hangar?"b29":"f-15e";ac.setAircraftTypeName(model);ac.setTextureName(model);
            ac.setPos(0,-60,hangar?14:10);ac.setYRot(hangar?180:0);ac.yRotO=ac.getYRot();
            var parts=ac.getParts();for(int k=0;k<parts.length;k++)parts[k].setId(1490000+phase*100+k);actors.add(ac);mc.level.addEntity(ac);
            System.out.println("LIGHTING scene="+scene()+" realistic="+on());
        }
        if(scene().equals("night-explosion")&&age==110)ClientEffects.INSTANCE.explosionEffects(mc.level,5,-60,6,4,false,List.of(new BlockPos(5,-61,6)));
        peakLights=Math.max(peakLights,SceneLights.count());
        if(age==PHASE_TICKS-10){
            if(scene().equals("night-explosion")&&on()&&peakLights==0)throw new IllegalStateException("Explosion registered no scene light");
            if(!on()&&SceneLights.count()!=0)throw new IllegalStateException("Scene lights while disabled");
            System.out.println("LIGHTING passed=true scene="+scene()+" realistic="+on()+" peak_lights="+peakLights);
        }
    }
    private static Vec3 eye(){return switch(scene()){case "hangar"->new Vec3(26,-50,-14);case "dusk"->new Vec3(-22,-54,-7);default->new Vec3(18,-55,-5);};}
    @SubscribeEvent public static void camera(ViewportEvent.ComputeFov e){if(ENABLED&&!actors.isEmpty())e.getCamera().setPosition(eye());}
    @SubscribeEvent public static void angles(ViewportEvent.ComputeCameraAngles e){if(ENABLED&&phase>=0){boolean h=scene().equals("hangar");e.setYaw(h?48:scene().equals("dusk")?-52:51);e.setPitch(h?18:12);e.setRoll(0);}}
    @SubscribeEvent public static void frame(RenderFrameEvent.Post e){
        if(!ENABLED||phase<0)return;
        int at=scene().equals("night-explosion")?113:120;
        if(!captured&&age>=at){capture="lighting-"+scene()+(on()?"-on":"-off");captured=true;}
        if(capture!=null){var mc=Minecraft.getInstance();String n=capture;capture=null;Screenshot.grab(mc.gameDirectory,n+".png",mc.gameRenderer.mainRenderTarget(),1,m->System.out.println("LIGHTING "+m.getString()));}
    }
}
