package io.github.mcheliceport.client.render;

import io.github.mcheliceport.config.*;
import io.github.mcheliceport.entity.aircraft.AircraftEntity;
import io.github.mcheliceport.registry.ModEntityTypes;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Screenshot;
import net.minecraft.client.gui.screens.DisconnectedScreen;
import net.minecraft.client.gui.screens.TitleScreen;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.Difficulty;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.LevelSettings;
import net.minecraft.world.level.WorldDataConfiguration;
import net.minecraft.world.level.levelgen.WorldOptions;
import net.minecraft.world.level.levelgen.presets.WorldPresets;
import net.minecraft.world.phys.Vec3;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.*;

/**
 * Non-shipping scene for crashed plane wrecks: a real server-side plane is shot down at low
 * altitude, crashes into flat ground, and is photographed from the impact through the cold hulk.
 * Run with {@code -Dmcheli.probeWreckRealism=true -I scripts/visual-probe.init.gradle runClient};
 * {@code -Dmcheli.probeWreckTag} prefixes the file names (for before/after pairs).
 */
@EventBusSubscriber(modid="mcheli_ce_port",value=Dist.CLIENT)
public final class WreckRealismProbe {
    private static final boolean ENABLED=Boolean.getBoolean("mcheli.probeWreckRealism");
    private static final String[] NAMES=System.getProperty("mcheli.probeWreckPlanes","f22a").split(",");
    private static final String TAG=System.getProperty("mcheli.probeWreckTag","after");
    private static final int END=Integer.getInteger("mcheli.probeWreckEnd",1500);
    private static Vec3 HOME=Vec3.ZERO;
    private static int ticks,phase=-1,age,impactAge=-1;private static boolean created,killed;
    private static AircraftEntity server;private static Vec3 impact,heading=new Vec3(1,0,0);
    private static Vec3 eye,target;private static String capture;
    @SubscribeEvent public static void screen(ScreenEvent.Init.Post event){
        if(!ENABLED||created||Minecraft.getInstance().level!=null)return;
        if(!(event.getScreen() instanceof TitleScreen||event.getScreen() instanceof DisconnectedScreen
                ||event.getScreen() instanceof net.minecraft.client.gui.screens.AccessibilityOnboardingScreen))return;
        var mc=Minecraft.getInstance();created=true;
        if(mc.getLevelSource().levelExists("M6Smoke")){mc.createWorldOpenFlows().openWorld("M6Smoke",()->mc.gui.setScreen(new TitleScreen()));return;}
        var settings=new LevelSettings("M6Smoke",GameType.CREATIVE,new LevelSettings.DifficultySettings(Difficulty.PEACEFUL,false,false),true,WorldDataConfiguration.DEFAULT);
        mc.createWorldOpenFlows().createFreshLevel("M6Smoke",settings,new WorldOptions(20261028L,false,false),WorldPresets::createTestWorldDimensions,new TitleScreen());
    }
    private static void spawn(Minecraft mc,String name){
        var level=mc.getSingleplayerServer().overworld();
        mc.getSingleplayerServer().execute(()->{
            var ac=ModEntityTypes.PLANE.get().create(level,EntitySpawnReason.COMMAND);
            // Approach along +x, descending at about 25 degrees, so the wreck hits HOME at speed.
            var start=HOME.add(-30,20,0);ac.setPos(start.x,start.y,start.z);ac.xo=start.x;ac.yo=start.y;ac.zo=start.z;
            ac.setAircraftTypeName(name);ac.setTextureName(ac.getAcInfo().getTextureName());
            ac.setYaw(-90);ac.setPitch(24);ac.setCurrentThrottle(1);ac.setDeltaMovement(1.0,-.75,0);
            level.addFreshEntity(ac);server=ac;
        });
    }
    private static void shot(String name,Vec3 from,Vec3 at){target=at;eye=at.add(from);capture=TAG+"-"+NAMES[phase]+"-"+name;}
    @SubscribeEvent public static void tick(ClientTickEvent.Post event){
        if(!ENABLED)return;var mc=Minecraft.getInstance();if(mc.level==null||mc.player==null||mc.isPaused())return;
        if(++ticks==40){
            for(String c:new String[]{"kill @e[type=!minecraft:player]","gamemode spectator","time set noon","weather clear","gamerule minecraft:advance_time false"})mc.player.connection.sendCommand(c);
            mc.getWindow().setWindowed(1280,720);mc.options.enableVsync().set(false);mc.options.framerateLimit().set(60);if(!mc.gui.hud.isHidden())mc.gui.hud.toggle();
            ClientConfig.EFFECT_QUALITY.set(EffectQuality.ENHANCED);ClientConfig.VEHICLE_DAMAGE_DETAILS.set(true);ClientConfig.VEHICLE_DESTRUCTION_SEQUENCES.set(true);
            ClientConfig.EFFECT_STYLE.set(ClientConfig.EffectStyle.POLISHED);ClientConfig.ENGINE_STARTUP_EFFECTS.set(false);
            ClientConfig.EFFECT_BUDGET.set(ClientConfig.EffectBudget.BALANCED);ClientConfig.ADAPTIVE_EFFECTS.set(false);
        }
        if(ticks==150){
            var at=mc.player.blockPosition().offset(0,0,24);
            double ground=mc.level.getHeight(net.minecraft.world.level.levelgen.Heightmap.Types.MOTION_BLOCKING,at.getX(),at.getZ());
            HOME=new Vec3(at.getX()+.5,ground,at.getZ()+.5);System.out.println("M8_WRECK home="+HOME);
        }
        if(ticks<160)return;int next=(ticks-160)/END;age=(ticks-160)%END;
        if(next>=NAMES.length){System.out.println("M8_WRECK done");mc.stop();return;}
        if(next!=phase){
            phase=next;killed=false;impactAge=-1;impact=null;server=null;
            mc.player.connection.sendCommand("kill @e[type=!minecraft:player]");
            mc.player.connection.sendCommand("fill "+(int)(HOME.x-30)+" "+(int)(HOME.y)+" "+(int)(HOME.z-30)+" "+(int)(HOME.x+40)+" "+(int)(HOME.y+20)+" "+(int)(HOME.z+30)+" air replace minecraft:fire");
            eye=HOME.add(-14,6,-26);target=HOME.add(-10,6,0);
        }
        if(age==20)spawn(mc,NAMES[phase]);
        var s=server;
        if(s!=null&&age==34&&!killed){killed=true;mc.getSingleplayerServer().execute(()->s.hurtServer((ServerLevel)s.level(),s.damageSources().generic(),100000));}
        if(s!=null&&killed&&impactAge<0&&(s.onGround()||s.isRemoved())){
            impactAge=age;impact=s.position();var v=s.getDeltaMovement();if(v.horizontalDistanceSqr()>.01)heading=new Vec3(v.x,0,v.z).normalize();
            System.out.println("M8_WRECK impact at "+impact+" age="+age+" v="+v);
        }
        if(s!=null&&age%20==0)System.out.println("M8_WRECK age="+age+" pos="+s.position()+" destroyed="+s.isDestroyed()+" removed="+s.isRemoved()
                +" fire="+countFire(mc)+" pieces="+LoosePanels.pieces()+" debris="+WreckDebrisField.pieces()+" ground="+io.github.mcheliceport.client.effect.WreckGround.pieces());
        if(impactAge<0){if(s!=null&&killed){target=s.position();}return;}
        int t=age-impactAge;Vec3 at=s.isRemoved()?impact:s.position();
        var side=new Vec3(-heading.z,0,heading.x);
        if(t==10)shot("1-impact",heading.scale(-6).add(side.scale(-16)).add(0,7,0),at);
        if(t==60)shot("2-burning",heading.scale(10).add(side.scale(-11)).add(0,5,0),at);
        if(t==120)shot("3-close",heading.scale(3).add(side.scale(-5.5)).add(0,2.6,0),at);
        if(t==200){var mid=impact.add(at).scale(.5);shot("4-trail",heading.scale(-6).add(side.scale(-13)).add(0,10,0),mid);}
        if(t==330)shot("5-from-above",side.scale(-3).add(0,18,0).add(heading.scale(-2)),at);
        if(t==440)shot("6-smolder",heading.scale(9).add(side.scale(-10)).add(0,4.5,0),at);
        if(t==1300)shot("7-cold-hulk",heading.scale(10).add(side.scale(-11)).add(0,5,0),at);
        if(t==1320)shot("8-cold-close",heading.scale(3).add(side.scale(-5.5)).add(0,2.6,0),at);
        // Framed by the airframe's size, so a big bomber's breakup is seen whole.
        var scene=mc.level.getEntity(s.getId()) instanceof AircraftEntity c?WreckScene.get(c):null;
        float size=scene==null?10:Math.max(10,scene.size());
        if(t==90)shot("2b-burning-wide",heading.scale(size*.5).add(side.scale(-size*1.3)).add(0,size*.55,0),at);
        if(t==1340)shot("9-cold-overview",heading.scale(size*.6).add(side.scale(-size*1.1)).add(0,size*1.1,0),at);
    }
    private static int countFire(Minecraft mc){
        int n=0;var b=net.minecraft.core.BlockPos.containing(HOME);
        for(var p:net.minecraft.core.BlockPos.betweenClosed(b.offset(-30,-3,-30),b.offset(40,6,30)))if(mc.level.getBlockState(p).is(net.minecraft.world.level.block.Blocks.FIRE))n++;
        return n;
    }
    @SubscribeEvent public static void camera(ViewportEvent.ComputeFov event){if(ENABLED&&eye!=null)event.getCamera().setPosition(eye);}
    @SubscribeEvent public static void angles(ViewportEvent.ComputeCameraAngles event){
        if(!ENABLED||eye==null||target==null)return;var d=target.subtract(eye);
        event.setYaw((float)Math.toDegrees(Math.atan2(-d.x,d.z)));event.setPitch((float)-Math.toDegrees(Math.atan2(d.y,Math.sqrt(d.x*d.x+d.z*d.z))));event.setRoll(0);
    }
    @SubscribeEvent public static void frame(RenderFrameEvent.Post event){
        if(capture==null)return;var mc=Minecraft.getInstance();String name=capture;capture=null;
        Screenshot.grab(mc.gameDirectory,"wreck-"+name+".png",mc.gameRenderer.mainRenderTarget(),1,m->System.out.println("M8_WRECK "+m.getString()));
    }
}
