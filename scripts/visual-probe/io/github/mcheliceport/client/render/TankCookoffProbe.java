package io.github.mcheliceport.client.render;

import io.github.mcheliceport.client.model.*;
import io.github.mcheliceport.client.particle.*;
import io.github.mcheliceport.config.*;
import io.github.mcheliceport.entity.tank.TankEntity;
import io.github.mcheliceport.registry.ModEntityTypes;
import java.util.*;
import net.minecraft.client.*;
import net.minecraft.client.gui.screens.TitleScreen;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.phys.Vec3;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.*;
import org.joml.Matrix4f;
import org.joml.Vector3f;

/** Real server-owned tanks, filled/destroyed on the server while the observer stays outside. */
@EventBusSubscriber(modid="mcheli_ce_port",value=Dist.CLIENT)
public final class TankCookoffProbe {
    private static final boolean ENABLED=Boolean.getBoolean("mcheli.probeTankCookoff");
    private static final String[] SCENES={"m1a2","t-90","t84","empty-t90","classic-m1a2","busy-m1a2","busy-t90"};
    private static int ticks,phase=Integer.getInteger("mcheli.probeTankStart",0)-1,age=320;private static boolean opened,pressurePrepared;private static String capture;
    private static volatile TankEntity server;private static volatile UUID identity;private static TankEntity client;
    private static McheliModel.Group filler;
    @SubscribeEvent public static void screen(ScreenEvent.Init.Post e){if(!ENABLED||opened||!(e.getScreen() instanceof TitleScreen)||Minecraft.getInstance().level!=null)return;opened=true;var mc=Minecraft.getInstance();mc.createWorldOpenFlows().openWorld("M6Smoke",()->mc.gui.setScreen(new TitleScreen()));}
    @SubscribeEvent public static void tick(ClientTickEvent.Post e){
        if(!ENABLED)return;var mc=Minecraft.getInstance();if(mc.level==null||mc.player==null||mc.isPaused())return;
        if(++ticks==40){
            for(String c:new String[]{"kill @e[type=!minecraft:player]","gamemode spectator","time set noon","weather clear","gamerule minecraft:advance_time false",
                    "fill -25 -61 -8 25 -61 42 sand","fill -25 -60 -8 25 -40 42 air","tp @s 0 -54 5"})mc.player.connection.sendCommand(c);
            mc.getWindow().setWindowed(1280,720);mc.options.enableVsync().set(false);mc.options.framerateLimit().set(120);mc.options.fov().set(60);mc.options.cloudStatus().set(CloudStatus.OFF);
            if(!mc.gui.hud.isHidden())mc.gui.hud.toggle();
            ClientConfig.ADAPTIVE_EFFECTS.set(false);ClientConfig.ENGINE_STARTUP_EFFECTS.set(false);ClientConfig.NIGHT_FLIGHT_LIGHTING.set(false);
            ClientConfig.VEHICLE_DAMAGE_DETAILS.set(true);ClientConfig.VEHICLE_DESTRUCTION_SEQUENCES.set(true);ClientConfig.EFFECT_STYLE.set(ClientConfig.EffectStyle.POLISHED);ClientConfig.EFFECT_BUDGET.set(ClientConfig.EffectBudget.BALANCED);
            var b=new DetailBoxes();b.box(-.03F,-.03F,-.03F,.03F,.03F,.03F);filler=b.group("$probe_budget_filler");
        }
        if(ticks<160)return;
        if(phase<0||++age>=320){
            var old=server;if(old!=null)mc.getSingleplayerServer().execute(old::discard);
            VehicleAtmosphere.clear();WreckAppearance.clear();LoosePanels.clear();SceneLights.clear();mc.particleEngine.clearParticles();
            phase++;age=0;pressurePrepared=false;server=client=null;identity=null;
            if(phase>=SCENES.length){ClientConfig.EFFECT_QUALITY.set(EffectQuality.ENHANCED);System.out.println("TANK_COOKOFF all_passed=true");mc.stop();return;}
            ClientConfig.EFFECT_QUALITY.set(phase==4?EffectQuality.CLASSIC:EffectQuality.ENHANCED);
            String name=phase==1||phase==3||phase==6?"t-90":phase==2?"t84":"m1a2";int index=phase;
            mc.getSingleplayerServer().execute(()->{
                var level=mc.getSingleplayerServer().overworld();var ac=ModEntityTypes.TANK.get().create(level,EntitySpawnReason.COMMAND);
                ac.setUUID(new UUID(1,index+1));ac.setAircraftTypeName(name);ac.setTextureName(name);ac.setPos(0,-60,18);ac.setYaw(index==2?35:0);level.addFreshEntity(ac);server=ac;identity=ac.getUUID();
            });System.out.println("TANK_COOKOFF scene="+SCENES[phase]);
        }
        if(identity!=null)for(var entity:mc.level.entitiesForRendering())if(entity instanceof TankEntity tank&&tank.getUUID().equals(identity)){client=tank;break;}
        if(age==40&&server!=null){var ac=server;boolean empty=phase==3;mc.getSingleplayerServer().execute(()->{
            for(int k=0;k<ac.getWeaponNum();k++){var w=ac.getWeapon(k);if(w!=null&&w.getInfo()!=null&&w.getFirstWeapon()!=null){w.setAmmo(empty?0:Math.min(w.getMagSize(),w.getInfo().maxAmmo));w.setReserveAmmo(empty?0:w.getInfo().maxAmmo);}}
        });}
        if(age==75&&client!=null)System.out.println("TANK_COOKOFF observer_before="+CookoffEffects.explosiveAmmo(client)+" rider="+mc.player.isPassenger());
        if(age==80&&server!=null){var ac=server;mc.getSingleplayerServer().execute(()->{ac.setDamageTaken(ac.getMaxHP());ac.destroyAircraft();});}
        if(client==null)return;
        int wreck=VehicleAtmosphere.wreckAge(client);
        if(phase>=5&&wreck>=5&&!pressurePrepared){LoosePanels.clear();pressurePrepared=true;}
        if(phase>=5&&wreck>=5&&wreck<=25&&LoosePanels.namedPieces(client,"$probe_budget_filler")<8)
            LoosePanels.scatter(client,net.minecraft.resources.Identifier.withDefaultNamespace("textures/block/iron_block.png"),filler,
                    new Matrix4f().translate(10000,10000,10000),new Vector3f(),Vec3.ZERO);
        if(phase>=5&&wreck==30){
            if(LoosePanels.namedPieces(client,"$probe_budget_filler")!=8)throw new IllegalStateException("Pressure fixture did not fill all eight flying slots");
            System.out.println("TANK_COOKOFF budget_fillers=8 pieces="+LoosePanels.pieces());
        }
        if(age==200){
            var s=WreckSequenceEffects.get(client);int ammo=CookoffEffects.explosiveAmmo(client);
            if(phase!=3&&ammo<8)throw new IllegalStateException("Observer never received authoritative ammo: "+ammo);
            if(mc.player.isPassenger())throw new IllegalStateException("Observer became a rider");
            if(phase==4){if(s!=null)throw new IllegalStateException("Classic retained tank cook-off state");}
            else if(phase==3){if(s==null||s.turret||s.blowout)throw new IllegalStateException("Empty tank blew apart");}
            else if(phase==0||phase==5){
                if(s==null||!s.blowout||s.vent==null||s.turret)throw new IllegalStateException("Abrams bustle did not open independently");
                if(phase==0&&(!s.scattered.contains("bustle-panel-0")||!s.scattered.contains("bustle-panel-1")))throw new IllegalStateException("Pressure panels never detached");
                if(phase==5&&s.openLids!=2)throw new IllegalStateException("Budget pressure hid open lids: lids="+s.openLids+" pieces="+LoosePanels.pieces()+" fillers="+LoosePanels.namedPieces(client,"$probe_budget_filler"));
            }else{
                if(s==null||!s.turret||s.tankTurret==null)throw new IllegalStateException("Loaded carousel tank retained turret");
                if(phase<3&&LoosePanels.namedPieces(client,"$m8_complete_turret")!=1)throw new IllegalStateException("Complete visible turret fragment missing");
                if(phase==6&&!s.turretFallback)throw new IllegalStateException("Budget fallback not exercised");
            }
            if(LoosePanels.pieces()>8||VehicleAtmosphere.emitted()>16)throw new IllegalStateException("Shared budget increased");
            System.out.println("TANK_COOKOFF passed=true scene="+SCENES[phase]+" observer_ammo="+ammo+" wreck_age="+wreck+" turret="+(s!=null&&s.turret)+" open_lids="+(s==null?0:s.openLids)+" fallback="+(s!=null&&s.turretFallback)+" pieces="+LoosePanels.pieces());
        }
        if((phase==0||phase==5)&&(age==124||age==132||age==210))capture=SCENES[phase]+(age==124?"-opening":age==132?"-venting":"-aftermath");
        if((phase==1||phase==2||phase==6)&&(age==170||age==185||age==250))capture=SCENES[phase]+(age==170?"-before":age==185?"-separated":"-landed");
        if(age==260&&phase!=3&&phase!=4){var ac=server;mc.getSingleplayerServer().execute(()->{ac.setDamageTaken(0);ac.setDespawnCount(0);});}
        // Wreck revival is not an M9 feature: explicitly reset the local countdown in this dev-only reset check.
        if(age>=275&&phase!=3&&phase!=4&&client.getHP()>0)client.setDespawnCount(0);
        if(age==300&&phase!=3&&phase!=4){var s=WreckSequenceEffects.get(client);
            if(client.isDestroyed()||s==null||s.turret||s.blowout||s.tankTurret!=null||s.openLids!=0||LoosePanels.pieces()!=0)
                throw new IllegalStateException("Repair retained tank cook-off geometry");
            System.out.println("TANK_COOKOFF reset=true scene="+SCENES[phase]);
        }
    }
    private static Vec3 target(){return client.position().add(0,phase==1||phase==2||phase==6?3.8:2.7,phase==0||phase==5?-2.4:0);}
    private static Vec3 eye(){return target().add(phase==0||phase==5?6:11,phase==0||phase==5?4:4,phase==0||phase==5?-7:-14);}
    @SubscribeEvent public static void camera(ViewportEvent.ComputeFov e){if(ENABLED&&client!=null)e.getCamera().setPosition(eye());}
    @SubscribeEvent public static void angles(ViewportEvent.ComputeCameraAngles e){if(!ENABLED||client==null)return;var d=target().subtract(eye());e.setYaw((float)Math.toDegrees(Math.atan2(-d.x,d.z)));e.setPitch((float)-Math.toDegrees(Math.atan2(d.y,d.horizontalDistance())));e.setRoll(0);}
    @SubscribeEvent public static void frame(RenderFrameEvent.Post e){if(capture==null)return;var mc=Minecraft.getInstance();String n=capture;capture=null;Screenshot.grab(mc.gameDirectory,"m8-tank-cookoff-"+n+".png",mc.gameRenderer.mainRenderTarget(),1,m->System.out.println("TANK_COOKOFF "+m.getString()));}
}
