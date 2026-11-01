package io.github.mcheliceport.client.render;

import io.github.mcheliceport.client.particle.*;
import io.github.mcheliceport.config.*;
import io.github.mcheliceport.entity.aircraft.AircraftEntity;
import io.github.mcheliceport.entity.heli.HeliEntity;
import io.github.mcheliceport.entity.plane.PlaneEntity;
import io.github.mcheliceport.entity.tank.TankEntity;
import io.github.mcheliceport.registry.ModEntityTypes;
import java.util.*;
import net.minecraft.client.*;
import net.minecraft.client.gui.screens.TitleScreen;
import net.minecraft.world.phys.Vec3;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.*;

/** Held client destruction states and real production debris contacts. Never part of normal builds. */
@EventBusSubscriber(modid="mcheli_ce_port",value=Dist.CLIENT)
public final class DramaticDestructionProbe {
    private static final boolean ENABLED=Boolean.getBoolean("mcheli.probeDramaticDestruction");
    private static final int END=Integer.getInteger("mcheli.probeDramaticEnd",8);
    private static final String[] SCENES={"t90","ah64","f35","old-wreck","disabled","shared-load","classic","airborne"};
    private static final List<AircraftEntity> actors=new ArrayList<>();
    private static int ticks,phase=-1,age,peakInterior,peakEmitted,peakLights;private static boolean opened,openingShot,interiorShot,surgeShot,secondaryShot;private static String capture;
    private static boolean wreck(){return phase==3||age>=60&&age<310;}
    private static void hold(AircraftEntity ac){ac.xo=ac.getX();ac.yo=ac.getY();ac.zo=ac.getZ();ac.yRotO=ac.getYaw();ac.xRotO=0;ac.prevRoll=0;ac.tickCount++;ac.setRoll(0);ac.setPitch(0);ac.setDeltaMovement(Vec3.ZERO);ac.setOnGround(phase!=7);}
    private static final class Tank extends TankEntity{Tank(Minecraft mc){super(ModEntityTypes.TANK.get(),mc.level);}@Override public void tick(){hold(this);}@Override public boolean isDestroyed(){return wreck();}@Override public int getHP(){return wreck()?0:getMaxHP();}}
    private static final class Heli extends HeliEntity{Heli(Minecraft mc){super(ModEntityTypes.HELICOPTER.get(),mc.level);}@Override public void tick(){hold(this);}@Override public boolean isDestroyed(){return wreck();}@Override public int getHP(){return wreck()?0:getMaxHP();}}
    private static final class Plane extends PlaneEntity{Plane(Minecraft mc){super(ModEntityTypes.PLANE.get(),mc.level);}@Override public void tick(){hold(this);}@Override public boolean isDestroyed(){return wreck();}@Override public int getHP(){return wreck()?0:getMaxHP();}}
    @SubscribeEvent public static void screen(ScreenEvent.Init.Post e){if(!ENABLED||opened||!(e.getScreen() instanceof TitleScreen)||Minecraft.getInstance().level!=null)return;opened=true;var mc=Minecraft.getInstance();mc.createWorldOpenFlows().openWorld("M6Smoke",()->mc.gui.setScreen(new TitleScreen()));}
    @SubscribeEvent public static void tick(ClientTickEvent.Post e){
        if(!ENABLED)return;var mc=Minecraft.getInstance();if(mc.level==null||mc.player==null||mc.isPaused())return;
        if(++ticks==40){
            for(String c:new String[]{"kill @e[type=!minecraft:player]","gamemode spectator","time set 13000","weather clear","gamerule minecraft:advance_time false",
                    "fill -28 -61 -8 28 -61 45 sand","fill -28 -60 -8 28 -43 45 air","tp @s 0 -54 6"})mc.player.connection.sendCommand(c);
            mc.getWindow().setWindowed(1280,720);mc.options.enableVsync().set(false);mc.options.framerateLimit().set(120);mc.options.gamma().set(.5D);mc.options.fov().set(60);mc.options.cloudStatus().set(CloudStatus.OFF);
            if(!mc.gui.hud.isHidden())mc.gui.hud.toggle();
            ClientConfig.ADAPTIVE_EFFECTS.set(false);ClientConfig.ENGINE_STARTUP_EFFECTS.set(false);ClientConfig.NIGHT_FLIGHT_LIGHTING.set(false);
            ClientConfig.REALISTIC_LIGHTING.set(true);ClientConfig.VEHICLE_DAMAGE_DETAILS.set(true);ClientConfig.GPU_MODEL_BUFFERS.set(true);
            ClientConfig.EFFECT_STYLE.set(ClientConfig.EffectStyle.POLISHED);ClientConfig.EFFECT_BUDGET.set(ClientConfig.EffectBudget.BALANCED);
        }
        if(ticks<160)return;
        if(phase<0||++age>=370){
            actors.forEach(AircraftEntity::discard);actors.clear();VehicleAtmosphere.clear();WreckScene.clear();SceneLights.clear();LoosePanels.clear();WreckDebrisField.clear();WreckAppearance.clear();mc.particleEngine.clearParticles();
            phase++;age=peakInterior=peakEmitted=peakLights=0;openingShot=interiorShot=surgeShot=secondaryShot=false;
            if(phase>=Math.min(END,SCENES.length)){ClientConfig.EFFECT_QUALITY.set(EffectQuality.ENHANCED);ClientConfig.VEHICLE_DESTRUCTION_SEQUENCES.set(true);System.out.println("DRAMATIC all_passed=true scenes="+Math.min(END,SCENES.length));mc.stop();return;}
            ClientConfig.VEHICLE_DESTRUCTION_SEQUENCES.set(phase!=4);ClientConfig.EFFECT_QUALITY.set(phase==6?EffectQuality.CLASSIC:EffectQuality.ENHANCED);
            mc.player.connection.sendCommand("fill -28 -61 -8 28 -61 45 "+(phase==0||phase==5?"stone":phase==1?"grass_block":"sand"));
            String model=phase==0||phase==5?"t-90":phase==1||phase==3||phase==4||phase==6?"ah-64":"f-35a";
            for(int j=0;j<(phase==5?16:1);j++){
                AircraftEntity ac=model.equals("t-90")?new Tank(mc):model.equals("ah-64")?new Heli(mc):new Plane(mc);
                ac.setUUID(new UUID(0,8L+j*8));ac.setId(1280000+phase*100+j);ac.setAircraftTypeName(model);ac.setTextureName(model);
                ac.setPos(phase==5?(j%4-1.5)*4:0,phase==7?-46:-60,phase==5?10+j/4*4:18);
                for(int k=0;k<ac.getParts().length;k++)ac.getParts()[k].setId(1580000+phase*10000+j*100+k);
                for(int k=0;k<ac.getWeaponNum();k++){var w=ac.getWeapon(k);if(w!=null&&w.getFirstWeapon()!=null&&w.getInfo()!=null){w.setAmmo(Math.min(w.getMagSize(),w.getInfo().maxAmmo));w.setReserveAmmo(w.getInfo().maxAmmo);}}
                actors.add(ac);mc.level.addEntity(ac);
            }
            System.out.println("DRAMATIC scene="+SCENES[phase]+" actors="+actors.size()+" conventional_ammo="+CookoffEffects.conventionalAmmo(actors.getFirst()));
        }
        if(age==230){
            boolean excluded=phase==3||phase==4||phase==6;
            if(excluded&&(WreckRuptureEffects.primary()!=0||WreckRuptureEffects.secondary()!=0||peakInterior!=0))throw new IllegalStateException("Excluded wreck replayed dramatic effects "+SCENES[phase]);
            if(!excluded&&(WreckRuptureEffects.primary()==0||WreckRuptureEffects.secondary()==0))throw new IllegalStateException("Missing rupture/cookoff "+SCENES[phase]);
            if(phase<3&&peakInterior==0)throw new IllegalStateException("No exposed-interior light "+SCENES[phase]);
            if(phase<3&&DebrisImpactEffects.surges()==0)throw new IllegalStateException("No grounded settling dust "+SCENES[phase]);
            if(phase==7&&DebrisImpactEffects.surges()!=0)throw new IllegalStateException("Airborne wreck emitted ground dust");
            report("burning");
        }
        if(age==365){if(InteriorFirelight.vertices()!=0||SceneLights.count()!=0)throw new IllegalStateException("Repair retained interior glow or fire light");report("repaired");}
    }
    private static void report(String stage){System.out.println("DRAMATIC passed=true scene="+SCENES[phase]+" stage="+stage+" primary="+WreckRuptureEffects.primary()+" secondary="+WreckRuptureEffects.secondary()+" interior_peak="+peakInterior+" emitted_peak="+peakEmitted+" lights_peak="+peakLights+" contacts="+DebrisImpactEffects.impacts()+" surges="+DebrisImpactEffects.surges()+" pieces="+LoosePanels.pieces()+" field="+WreckDebrisField.pieces());}
    private static Vec3 target(){
        if(phase==5)return new Vec3(0,-58,17);
        var ac=actors.getFirst();var state=WreckSequenceEffects.get(ac);
        return state!=null&&state.opening!=null?state.opening:VehicleAtmosphere.wreckOrigins(ac).getFirst();
    }
    private static Vec3 eye(){
        if(phase<3&&age>=108&&age<145)return target().add(3.3,1.9,-3.1);
        return target().add(phase==5?17:6,phase==5?10:3,phase==5?-16:-6);
    }
    @SubscribeEvent public static void camera(ViewportEvent.ComputeFov e){if(ENABLED&&!actors.isEmpty())e.getCamera().setPosition(eye());}
    @SubscribeEvent public static void angles(ViewportEvent.ComputeCameraAngles e){if(!ENABLED||actors.isEmpty())return;var d=target().subtract(eye());e.setYaw((float)Math.toDegrees(Math.atan2(-d.x,d.z)));e.setPitch((float)-Math.toDegrees(Math.atan2(d.y,d.horizontalDistance())));e.setRoll(0);}
    @SubscribeEvent public static void frame(RenderFrameEvent.Post e){
        if(!ENABLED||actors.isEmpty())return;var mc=Minecraft.getInstance();
        peakInterior=Math.max(peakInterior,InteriorFirelight.vertices());peakEmitted=Math.max(peakEmitted,VehicleAtmosphere.emitted());
        peakLights=Math.max(peakLights,SceneLights.count());
        if(InteriorFirelight.vertices()>1536||VehicleAtmosphere.emitted()>16||LoosePanels.pieces()>8||WreckDebrisField.pieces()>72
                ||SceneLights.count()>96||SceneLights.frame(mc.gameRenderer.mainCamera().position()).count()>8)throw new IllegalStateException("Dramatic effects exceeded shared limits");
        if(phase<3){
            if(!openingShot&&WreckRuptureEffects.primary()>0){openingShot=true;capture=SCENES[phase]+"-rupture";}
            else if(!interiorShot&&age>=114&&InteriorFirelight.vertices()>0){interiorShot=true;capture=SCENES[phase]+"-interior";}
            else if(!secondaryShot&&WreckRuptureEffects.secondary()>0){secondaryShot=true;capture=SCENES[phase]+"-cookoff";}
            else if(!surgeShot&&DebrisImpactEffects.surges()>0){surgeShot=true;capture=SCENES[phase]+"-settling";}
        }
        if(capture!=null){String name=capture;capture=null;Screenshot.grab(mc.gameDirectory,"m8-dramatic-"+name+".png",mc.gameRenderer.mainRenderTarget(),1,m->System.out.println("DRAMATIC "+m.getString()));}
    }
}
