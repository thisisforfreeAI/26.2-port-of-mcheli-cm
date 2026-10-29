package io.github.mcheliceport.client.effect;

import io.github.mcheliceport.client.gui.*;
import io.github.mcheliceport.config.*;
import io.github.mcheliceport.effectrange.*;
import io.github.mcheliceport.entity.weapon.BaseBulletEntity;
import io.github.mcheliceport.net.EffectsRangePayload;
import java.util.*;
import net.minecraft.client.*;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.world.level.GameType;
import net.minecraft.world.phys.Vec3;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.*;
import net.neoforged.neoforge.client.network.ClientPacketDistributor;
import net.neoforged.neoforge.event.tick.ServerTickEvent;

/** Actual UI, dimension travel, production weapon impacts and return-state checks in the private dev world. */
@EventBusSubscriber(modid="mcheli_ce_port",value=Dist.CLIENT)
public final class EffectsRangeProbe {
    private static final boolean ENABLED=Boolean.getBoolean("mcheli.probeRange");
    private static final int START=Integer.getInteger("mcheli.probeRangeStart",0),END=Integer.getInteger("mcheli.probeRangeEnd",7);
    private static final String[] NAMES={"hydra-sand","agm158-soil","mk82-stone","gbu57-soil-night","hydra-water","classic-hydra","modern-mk82"};
    private static int ticks,phase=-1,age,stage,shotAge,impactAge=-1;private static boolean seen,returning;private static String capture;
    private static volatile int peakBlocks;private static volatile boolean returned;private static Vec3 original;private static GameType originalMode;private static long originalClock;
    private EffectsRangeProbe(){}
    private static void send(int action){var o=EffectsRangeClient.options;ClientPacketDistributor.sendToServer(new EffectsRangePayload(action,o.weapon(),o.surface(),o.lighting()));}
    private static void press(Minecraft mc,String text){
        for(var w:mc.gui.screen().children())if(w instanceof Button b&&b.getMessage().getString().startsWith(text)){b.onPress(new KeyEvent(257,0,0));return;}
        throw new IllegalStateException("Missing range button "+text);
    }
    @SubscribeEvent public static void server(ServerTickEvent.Post e){
        if(!ENABLED)return;peakBlocks=Math.max(peakBlocks,EffectsRange.blocksLastTick());
        if(EffectsRange.blocksLastTick()>2048||EffectsRange.sessions()>4)throw new IllegalStateException("Range work cap exceeded");
        if(original!=null&&!returned&&EffectsRange.sessions()>0){var level=e.getServer().overworld();var clock=level.dimensionType().defaultClock().orElseThrow();
            if(level.clockManager().getTotalTicks(clock)!=originalClock)throw new IllegalStateException("Range changed the frozen original-world clock");}
    }
    @SubscribeEvent public static void tick(ClientTickEvent.Post event){
        if(!ENABLED)return;var mc=Minecraft.getInstance();if(mc.level==null||mc.player==null||mc.isPaused())return;ticks++;
        if(ticks==40){
            for(String c:new String[]{"kill @e[type=!minecraft:player]","gamemode creative","time set noon","weather clear","gamerule minecraft:advance_time false","fill -24 -61 0 24 -61 42 stone","tp @s -7 -60 27 140 16"})mc.player.connection.sendCommand(c);
            mc.getWindow().setWindowed(960,720);mc.options.guiScale().set(3);mc.resizeGui();mc.options.enableVsync().set(false);mc.options.framerateLimit().set(120);mc.options.cloudStatus().set(CloudStatus.OFF);mc.options.fov().set(60);if(!mc.gui.hud.isHidden())mc.gui.hud.toggle();
            ClientConfig.EFFECT_QUALITY.set(EffectQuality.ENHANCED);ClientConfig.EFFECT_STYLE.set(ClientConfig.EffectStyle.POLISHED);ClientConfig.EFFECT_BUDGET.set(ClientConfig.EffectBudget.BALANCED);ClientConfig.ADAPTIVE_EFFECTS.set(false);ClientConfig.MODERN_EXPLOSIONS.set(false);
        }
        if(ticks==150){
            mc.getSingleplayerServer().execute(()->{var p=mc.getSingleplayerServer().getPlayerList().getPlayers().getFirst();original=p.position();originalMode=p.gameMode();var l=mc.getSingleplayerServer().overworld();originalClock=l.clockManager().getTotalTicks(l.dimensionType().defaultClock().orElseThrow());});
            mc.gui.setScreen(new EffectsSettingsScreen(new Screen(Component.literal("Range fixture")){}));press(mc,"Test range");
        }
        if(ticks==170)capture="entry-menu";
        if(ticks==180)press(mc,"Enter range");
        if(returning){
            if(returned&&!EffectsRange.inRange(mc.level)){System.out.println("M8_RANGE all_passed=true start="+START+" end="+END+" peak_blocks="+peakBlocks+" original_mode_restored=true original_position_restored=true");mc.stop();}
            if(++age>500)throw new IllegalStateException("Range return timed out");return;
        }
        if(ticks<190||!EffectsRange.inRange(mc.level)||!EffectsRangeClient.ready){if(ticks>1500&&phase<0)throw new IllegalStateException("Range did not finish entry/reset");return;}
        if(phase<0){phase=START;stage=0;age=0;}
        if(stage==0){
            if(phase>=Math.min(END,NAMES.length)){
                returning=true;age=0;mc.getSingleplayerServer().execute(()->{
                    var p=mc.getSingleplayerServer().getPlayerList().getPlayers().getFirst();if(!EffectsRange.leave(p)||!p.position().closerThan(original,.01)||p.gameMode()!=originalMode)throw new IllegalStateException("Range did not restore original player state");returned=true;
                });return;
            }
            int weapon=phase==1?1:phase==2||phase==6?2:phase==3?3:0;
            int surface=phase==0||phase==5?0:phase==1||phase==3?1:phase==4?3:2;
            ClientConfig.EFFECT_STYLE.set(phase==5?ClientConfig.EffectStyle.CLASSIC:ClientConfig.EffectStyle.POLISHED);ClientConfig.EFFECT_QUALITY.set(phase==5?EffectQuality.CLASSIC:EffectQuality.ENHANCED);ClientConfig.MODERN_EXPLOSIONS.set(phase==6);
            EffectsRangeClient.options=new RangeOptions(weapon,surface,phase==3?2:phase==1?1:0);EffectsRangeClient.ready=false;send(EffectsRange.RESET);
            age=0;seen=false;impactAge=-1;stage=1;System.out.println("M8_RANGE scene="+NAMES[phase]+" options="+EffectsRangeClient.options);return;
        }
        age++;
        if(stage==1){
            // The ready notification has arrived after a bounded terrain reset and particle cleanup.
            if(age<8||EffectsRangeClient.cooling())return;
            if(phase==0){mc.gui.setScreen(new EffectsRangeScreen(new EffectsSettingsScreen(null)));capture="ready-menu";stage=2;return;}
            mc.getWindow().setWindowed(1280,720);mc.options.guiScale().set(2);mc.resizeGui();send(EffectsRange.FIRE);shotAge=age;stage=3;
        }else if(stage==2&&age>=18){
            press(mc,"Fire demo");mc.getWindow().setWindowed(1280,720);mc.options.guiScale().set(2);mc.resizeGui();shotAge=age;stage=3;
        }
        if(stage==3){
            int projectiles=0;for(var entity:mc.level.entitiesForRendering())if(entity instanceof BaseBulletEntity)projectiles++;
            if(projectiles>0)seen=true;
            if(seen&&projectiles==0&&impactAge<0){impactAge=age;capture=NAMES[phase]+"-impact";System.out.println("M8_RANGE real_impact=true scene="+NAMES[phase]+" flight_ticks="+(age-shotAge));}
            if(impactAge>=0&&age==impactAge+24)capture=NAMES[phase]+"-smoke";
            if(impactAge>=0&&age>=impactAge+90){System.out.println("M8_RANGE passed=true scene="+NAMES[phase]+" actual_projectile=true impact_observed=true");phase++;stage=0;}
            if(age-shotAge>900&&impactAge<0)throw new IllegalStateException("No real impact for "+NAMES[phase]+" seen="+seen);
        }
    }
    @SubscribeEvent public static void frame(RenderFrameEvent.Post e){if(capture!=null){var mc=Minecraft.getInstance();String name=capture;capture=null;Screenshot.grab(mc.gameDirectory,"m8-range-"+name+".png",mc.gameRenderer.mainRenderTarget(),1,m->System.out.println("M8_RANGE "+m.getString()));}}
}
