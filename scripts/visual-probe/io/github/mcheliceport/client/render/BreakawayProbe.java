package io.github.mcheliceport.client.render;

import io.github.mcheliceport.client.particle.*;
import io.github.mcheliceport.config.*;
import io.github.mcheliceport.content.aircraft.AircraftInfo;
import io.github.mcheliceport.damage.*;
import io.github.mcheliceport.entity.plane.PlaneEntity;
import io.github.mcheliceport.registry.ModEntityTypes;
import io.github.mcheliceport.util.McheliMath;
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
 * Non-shipping scene for torn wings and tails: separation burst, falling trail, closed stumps seen
 * from grazing angles, and the landed pieces. Creates the flat probe world when it is missing.
 * Run with {@code -Dmcheli.probeBreakaway=true -I scripts/visual-probe.init.gradle runClient}.
 */
@EventBusSubscriber(modid="mcheli_ce_port",value=Dist.CLIENT)
public final class BreakawayProbe {
    private static final boolean ENABLED=Boolean.getBoolean("mcheli.probeBreakaway");
    private static final String[] NAMES=System.getProperty("mcheli.probeBreakawayPlanes","f22a,f-35a,f-15e,a-10").split(",");
    private static Vec3 HOME=Vec3.ZERO;private static double ground;
    private static final int PHASE=300;
    private static int ticks,phase=-1,age;private static PlaneEntity plane;private static String capture;private static boolean created;
    private static Vec3 eye,target;
    private static final class Plane extends PlaneEntity {
        Plane(Minecraft mc){super(ModEntityTypes.PLANE.get(),mc.level);}
        @Override public void tick(){xo=getX();yo=getY();zo=getZ();setPos(HOME.x,HOME.y,HOME.z);setDeltaMovement(0,0,0);}
    }
    private static void damage(int mask){
        long health=ComponentHealth.FULL;var zones=DamageZones.of(plane.getAcInfo());
        for(int i=0;i<zones.size();i++)if((AirframeDamage.bit(zones.get(i).kind(),zones.get(i).index())&mask)!=0)health=ComponentHealth.withLevel(health,i,0);
        plane.setComponentHealth(health);plane.setDamageTaken(mask==0?0:(int)(plane.getMaxHP()*.4));
    }
    @SubscribeEvent public static void screen(ScreenEvent.Init.Post event){
        if(!ENABLED||created||Minecraft.getInstance().level!=null)return;
        if(!(event.getScreen() instanceof TitleScreen||event.getScreen() instanceof DisconnectedScreen
                ||event.getScreen() instanceof net.minecraft.client.gui.screens.AccessibilityOnboardingScreen))return;
        var mc=Minecraft.getInstance();created=true;
        if(mc.getLevelSource().levelExists("M6Smoke")){mc.createWorldOpenFlows().openWorld("M6Smoke",()->mc.gui.setScreen(new TitleScreen()));return;}
        var settings=new LevelSettings("M6Smoke",GameType.CREATIVE,new LevelSettings.DifficultySettings(Difficulty.PEACEFUL,false,false),true,WorldDataConfiguration.DEFAULT);
        mc.createWorldOpenFlows().createFreshLevel("M6Smoke",settings,new WorldOptions(20261028L,false,false),WorldPresets::createTestWorldDimensions,new TitleScreen());
    }
    private static Vec3 opening(int bit){
        var s=WreckSequenceEffects.get(plane);if(s==null||s.airframe==null)return null;var p=s.airframe.openings().get(bit);
        return p==null?null:plane.position().add(McheliMath.rotVec3(p,-plane.getYaw(),-plane.getPitch(),-plane.getRoll())).add(0,AircraftInfo.GLOBAL_Y_OFFSET,0);
    }
    /** The newest flying section, read reflectively so production code needs no probe hook. */
    private static Vec3 piece(){
        try{var f=LoosePanels.class.getDeclaredField("PIECES");f.setAccessible(true);var list=(java.util.List<?>)f.get(null);
            for(int i=list.size()-1;i>=0;i--){var p=list.get(i);var t=p.getClass().getDeclaredField("tear");t.setAccessible(true);
                if(t.get(p)!=null){var at=p.getClass().getDeclaredField("motion");at.setAccessible(true);return ((DebrisMotion)at.get(p)).position;}}
        }catch(ReflectiveOperationException e){throw new IllegalStateException(e);}
        return null;
    }
    private static void shot(String name,Vec3 at,Vec3 from){if(at==null){System.out.println("M8_BREAKAWAY missing anchor for "+name);return;}target=at;eye=at.add(from);capture=NAMES[phase]+"-"+name;}
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
            // Wherever the world spawned us: lit, loaded chunks, about 12 blocks above the local ground.
            var at=mc.player.blockPosition().offset(0,0,14);
            ground=mc.level.getHeight(net.minecraft.world.level.levelgen.Heightmap.Types.MOTION_BLOCKING,at.getX(),at.getZ());
            HOME=new Vec3(at.getX()+.5,Math.max(ground+12,mc.player.getY()+2),at.getZ()+.5);System.out.println("M8_BREAKAWAY home="+HOME+" ground="+ground);
        }
        if(ticks<160)return;int next=(ticks-160)/PHASE;age=(ticks-160)%PHASE;
        if(next>=NAMES.length){System.out.println("M8_BREAKAWAY done");mc.stop();return;}
        if(next!=phase){
            if(plane!=null)plane.discard();VehicleAtmosphere.clear();LoosePanels.clear();WreckAppearance.clear();mc.particleEngine.clearParticles();
            phase=next;plane=new Plane(mc);plane.setId(974000+phase);plane.setAircraftTypeName(NAMES[phase]);plane.setTextureName(NAMES[phase]);
            plane.setPos(HOME.x,HOME.y,HOME.z);var parts=plane.getParts();for(int i=0;i<parts.length;i++)parts[i].setId(984000+phase*100+i);
            mc.level.addEntity(plane);damage(0);eye=HOME.add(-12,4,-8);target=HOME;System.out.println("M8_BREAKAWAY scene="+NAMES[phase]);
        }
        if(age==40)damage(AirframeDamage.LEFT);
        if(age==47)shot("1-wing-separation",piece(),new Vec3(-5,2,-5));
        if(age==43)shot("1a-separation-above",opening(AirframeDamage.LEFT),new Vec3(-3,7,-6));
        if(age==50)shot("1b-separation-above",opening(AirframeDamage.LEFT),new Vec3(-3,8,-7));
        if(age==58)shot("2-falling-trail",piece(),new Vec3(-6,2.5,-6));
        if(age==68)shot("2b-falling-trail",piece(),new Vec3(-5,1.5,-5));
        if(age==100)shot("3-wing-stump",opening(AirframeDamage.LEFT),new Vec3(-2.8,1.3,-1.9));
        if(age==106)shot("4-wing-stump-grazing",opening(AirframeDamage.LEFT),new Vec3(-1.4,.35,2.4));
        if(age==130)damage(AirframeDamage.LEFT|AirframeDamage.TAIL);
        if(age==137)shot("5-tail-separation",piece(),new Vec3(-5,2.5,-7));
        if(age==175)shot("6-tail-stump",opening(AirframeDamage.TAIL),new Vec3(-1.6,1.1,-3.2));
        if(age==200)shot("7-landed-pieces",piece(),new Vec3(-4,2.5,-4));
        if(age==260)shot("8-overview",HOME.add(0,-5,-2),new Vec3(-13,5,-12));
        if(age%20==0)System.out.println("M8_BREAKAWAY age="+age+" pieces="+LoosePanels.pieces()+" detail_vertices="+VehicleDetailLayers.vertices());
    }
    @SubscribeEvent public static void camera(ViewportEvent.ComputeFov event){if(ENABLED&&eye!=null&&plane!=null)event.getCamera().setPosition(eye);}
    @SubscribeEvent public static void angles(ViewportEvent.ComputeCameraAngles event){
        if(!ENABLED||eye==null||target==null||plane==null)return;var d=target.subtract(eye);
        event.setYaw((float)Math.toDegrees(Math.atan2(-d.x,d.z)));event.setPitch((float)-Math.toDegrees(Math.atan2(d.y,Math.sqrt(d.x*d.x+d.z*d.z))));event.setRoll(0);
    }
    @SubscribeEvent public static void frame(RenderFrameEvent.Post event){
        if(capture==null)return;var mc=Minecraft.getInstance();String name=capture;capture=null;
        Screenshot.grab(mc.gameDirectory,"breakaway-"+name+".png",mc.gameRenderer.mainRenderTarget(),1,m->System.out.println("M8_BREAKAWAY "+m.getString()));
    }
}
