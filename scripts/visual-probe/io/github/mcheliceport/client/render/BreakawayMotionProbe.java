package io.github.mcheliceport.client.render;

import io.github.mcheliceport.config.*;
import io.github.mcheliceport.damage.*;
import io.github.mcheliceport.entity.plane.PlaneEntity;
import io.github.mcheliceport.registry.ModEntityTypes;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Screenshot;
import net.minecraft.client.gui.screens.DisconnectedScreen;
import net.minecraft.client.gui.screens.TitleScreen;
import net.minecraft.world.Difficulty;
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
 * Non-shipping scene for torn-section motion: a banking plane in flight loses its left wing and then its
 * tail, and the camera follows each section as it folds, tumbles, strikes the ground, slides and settles.
 * Run with {@code -Dmcheli.probeBreakawayMotion=true -I scripts/visual-probe.init.gradle runClient}.
 */
@EventBusSubscriber(modid="mcheli_ce_port",value=Dist.CLIENT)
public final class BreakawayMotionProbe {
    private static final boolean ENABLED=Boolean.getBoolean("mcheli.probeBreakawayMotion");
    private static final String[] NAMES=System.getProperty("mcheli.probeMotionPlanes","f-35a,a-10").split(",");
    private static final Vec3 SPEED=new Vec3(0,0,.7);
    private static Vec3 START=new Vec3(0,-38,-30);
    private static final int PHASE=420;
    private static final int[] WING={41,43,45,48,52,58,66,78,92,110,135,170},TAIL={221,223,226,230,238,250,265,290,330,390};
    private static int ticks,phase=-1,age;private static PlaneEntity plane;private static String capture;private static boolean created;
    private static Vec3 eye,target,lastWing,lastTail;
    private static final class Plane extends PlaneEntity {
        int flying;
        Plane(Minecraft mc){super(ModEntityTypes.PLANE.get(),mc.level);}
        @Override public void tick(){
            xo=getX();yo=getY();zo=getZ();yRotO=getYRot();xRotO=getXRot();prevRoll=getRoll();flying++;
            setDeltaMovement(SPEED);setPos(START.add(SPEED.scale(flying)));setRoll((float)(-12*Math.sin(flying*.02)));
        }
    }
    private static void damage(int mask){
        long health=ComponentHealth.FULL;var zones=DamageZones.of(plane.getAcInfo());
        for(int i=0;i<zones.size();i++)if((AirframeDamage.bit(zones.get(i).kind(),zones.get(i).index())&mask)!=0)health=ComponentHealth.withLevel(health,i,0);
        plane.setComponentHealth(health);plane.setDamageTaken(mask==0?0:(int)(plane.getMaxHP()*.4));
    }
    @SubscribeEvent public static void screen(ScreenEvent.Init.Post event){
        if(!ENABLED||created||Minecraft.getInstance().level!=null)return;
        if(!(event.getScreen() instanceof TitleScreen||event.getScreen() instanceof DisconnectedScreen))return;
        var mc=Minecraft.getInstance();created=true;
        if(mc.getLevelSource().levelExists("M6Smoke")){mc.createWorldOpenFlows().openWorld("M6Smoke",()->mc.gui.setScreen(new TitleScreen()));return;}
        var settings=new LevelSettings("M6Smoke",GameType.CREATIVE,new LevelSettings.DifficultySettings(Difficulty.PEACEFUL,false,false),true,WorldDataConfiguration.DEFAULT);
        mc.createWorldOpenFlows().createFreshLevel("M6Smoke",settings,new WorldOptions(20261028L,false,false),WorldPresets::createTestWorldDimensions,new TitleScreen());
    }
    private static int hold;
    private static void follow(String name,Vec3 at,Vec3 from){if(at==null)return;target=at;eye=at.add(from);capture=NAMES[phase]+"-"+name;hold=1000;}
    @SubscribeEvent public static void tick(ClientTickEvent.Post event){
        if(!ENABLED)return;var mc=Minecraft.getInstance();if(mc.level==null||mc.player==null||mc.isPaused())return;
        if(ticks==20){
            // Fly 22 blocks above the highest ground along the path, in daylight.
            int top=-64;for(int z=-30;z<=180;z+=6)for(int x=-12;x<=4;x+=4)top=Math.max(top,mc.level.getHeight(net.minecraft.world.level.levelgen.Heightmap.Types.MOTION_BLOCKING,x,z));
            START=new Vec3(0,top+22,-30);System.out.println("M8_MOTION start="+START);
        }
        if(++ticks==40){
            for(String c:new String[]{"kill @e[type=!minecraft:player]","gamemode spectator","time set noon","weather clear","gamerule minecraft:advance_time false","tp @s -10 "+(int)(START.y-12)+" 20 -45 20"})mc.player.connection.sendCommand(c);
            mc.getWindow().setWindowed(1280,720);mc.options.enableVsync().set(false);mc.options.framerateLimit().set(60);if(!mc.gui.hud.isHidden())mc.gui.hud.toggle();
            ClientConfig.EFFECT_QUALITY.set(EffectQuality.ENHANCED);ClientConfig.VEHICLE_DAMAGE_DETAILS.set(true);ClientConfig.VEHICLE_DESTRUCTION_SEQUENCES.set(true);
            ClientConfig.EFFECT_STYLE.set(ClientConfig.EffectStyle.POLISHED);ClientConfig.ENGINE_STARTUP_EFFECTS.set(false);
            ClientConfig.EFFECT_BUDGET.set(ClientConfig.EffectBudget.BALANCED);ClientConfig.ADAPTIVE_EFFECTS.set(false);
        }
        if(ticks<160)return;int next=(ticks-160)/PHASE;age=(ticks-160)%PHASE;
        if(next>=NAMES.length){System.out.println("M8_MOTION done");mc.stop();return;}
        if(next!=phase){
            if(plane!=null)plane.discard();LoosePanels.clear();WreckAppearance.clear();mc.particleEngine.clearParticles();
            phase=next;plane=new Plane(mc);plane.setId(975000+phase);plane.setAircraftTypeName(NAMES[phase]);plane.setTextureName(NAMES[phase]);
            plane.setPos(START.x,START.y,START.z);var parts=plane.getParts();for(int i=0;i<parts.length;i++)parts[i].setId(985000+phase*100+i);
            mc.level.addEntity(plane);damage(0);lastWing=lastTail=null;hold=0;System.out.println("M8_MOTION scene="+NAMES[phase]+" ground="+mc.level.getHeight(net.minecraft.world.level.levelgen.Heightmap.Types.MOTION_BLOCKING,0,0)+" player="+mc.player.position());
        }
        // Hold a follow shot until its frame is captured; otherwise chase the plane.
        if(capture==null&&--hold<=0&&age<220){eye=plane.position().add(-14,4,-6);target=plane.position();}
        if(age==40)damage(AirframeDamage.LEFT);
        if(age==220)damage(AirframeDamage.LEFT|AirframeDamage.TAIL);
        var wing=LoosePanels.airframePosition(plane,AirframeDamage.LEFT);if(wing!=null)lastWing=wing;
        var tail=LoosePanels.airframePosition(plane,AirframeDamage.TAIL);if(tail!=null)lastTail=tail;
        for(int i=0;i<WING.length;i++)if(age==WING[i])follow("wing-"+(char)('a'+i),lastWing,new Vec3(-9,3.5,-7));
        for(int i=0;i<TAIL.length;i++)if(age==TAIL[i])follow("tail-"+(char)('a'+i),lastTail,new Vec3(-8,3.5,6));
        if(age%5==0&&age>=40)System.out.println("M8_MOTION age="+age+" "+LoosePanels.airframeMotion(plane));
    }
    @SubscribeEvent public static void camera(ViewportEvent.ComputeFov event){if(ENABLED&&eye!=null&&plane!=null)event.getCamera().setPosition(eye);}
    @SubscribeEvent public static void angles(ViewportEvent.ComputeCameraAngles event){
        if(!ENABLED||eye==null||target==null||plane==null)return;var d=target.subtract(eye);
        event.setYaw((float)Math.toDegrees(Math.atan2(-d.x,d.z)));event.setPitch((float)-Math.toDegrees(Math.atan2(d.y,Math.sqrt(d.x*d.x+d.z*d.z))));event.setRoll(0);
    }
    @SubscribeEvent public static void frame(RenderFrameEvent.Post event){
        if(capture==null)return;var mc=Minecraft.getInstance();String name=capture;capture=null;
        Screenshot.grab(mc.gameDirectory,"motion-"+name+".png",mc.gameRenderer.mainRenderTarget(),1,m->System.out.println("M8_MOTION "+m.getString()));
    }
}
