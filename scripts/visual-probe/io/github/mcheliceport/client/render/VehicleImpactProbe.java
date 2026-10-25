package io.github.mcheliceport.client.render;

import io.github.mcheliceport.client.model.AircraftModels;
import io.github.mcheliceport.config.ClientConfig;
import io.github.mcheliceport.config.EffectQuality;
import io.github.mcheliceport.content.ContentRegistries;
import io.github.mcheliceport.entity.aircraft.AircraftEntity;
import io.github.mcheliceport.entity.tank.TankEntity;
import io.github.mcheliceport.entity.weapon.BulletEntity;
import io.github.mcheliceport.registry.ModEntityTypes;
import io.github.mcheliceport.weapon.WeaponCreator;
import java.util.List;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Screenshot;
import net.minecraft.world.phys.Vec3;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.RenderFrameEvent;

/** Real gun collision/optional-packet/stock-model/repair fixture in the disposable world only. */
@EventBusSubscriber(modid="mcheli_ce_port",value=Dist.CLIENT)
public final class VehicleImpactProbe {
    private static int tick;
    private static volatile TankEntity serverTank;
    private static volatile int tankId;
    private static String capture;
    private static org.joml.Vector3f turretStart;
    private static void commands(Minecraft mc,String...list){for(String s:list)mc.player.connection.sendCommand(s);}
    @SubscribeEvent public static void tick(ClientTickEvent.Post event) throws Exception {
        if(!Boolean.getBoolean("mcheli.probeVehicleImpacts"))return;
        var mc=Minecraft.getInstance();if(mc.level==null||mc.player==null||mc.isPaused())return;
        tick++;
        if(tick==40){
            commands(mc,"kill @e[type=!minecraft:player]","gamemode spectator","time set noon","weather clear",
                    "gamerule minecraft:advance_time false","fill -20 -60 0 20 -43 40 air","fill -20 -42 0 20 -25 40 air","fill -20 -24 0 20 -16 40 air",
                    "fill -20 -62 0 20 -61 40 stone","tp @s 9 -57 20 90 10");
            mc.getWindow().setWindowed(1920,1080);mc.options.enableVsync().set(false);mc.options.framerateLimit().set(120);
            if(!mc.gui.hud.isHidden())mc.gui.hud.toggle();
            ClientConfig.EFFECT_QUALITY.set(EffectQuality.ENHANCED);ClientConfig.EFFECT_STYLE.set(ClientConfig.EffectStyle.POLISHED);
            ClientConfig.EFFECT_BUDGET.set(ClientConfig.EffectBudget.BALANCED);ClientConfig.ADAPTIVE_EFFECTS.set(false);
            ClientConfig.VEHICLE_DAMAGE_DETAILS.set(true);
        }
        if(tick==140){
            mc.getSingleplayerServer().execute(()->{
                var level=mc.getSingleplayerServer().overworld();var tank=new TankEntity(ModEntityTypes.TANK.get(),level);
                tank.setAircraftTypeName("m1a2");tank.setTextureName("m1a2");tank.setPos(0,-60,20);tank.setYaw(0);
                level.addFreshEntity(tank);serverTank=tank;tankId=tank.getId();System.out.println("M8_IMPACTS spawned="+tankId);
            });
        }
        if(tick>=220&&tick<=292&&tick%12==4)shoot(mc,false,(tick-220)/12);
        if(tick==330||tick==350||tick==370)shoot(mc,true,(tick-330)/20);
        if(tick==395)capture="before-check";
        if(tick==410){
            check(VehicleImpactMarks.stored()>0,"No marks from real gun hits");
            System.out.println("M8_IMPACTS real_hits stored="+VehicleImpactMarks.stored()+" pending="+VehicleImpactMarks.pending());
            printGroups();capture="gunfire";
            turretStart=turretPoint(mc);
        }
        if(tick>=430&&tick<480){
            int angle=tick-430;mc.getSingleplayerServer().execute(()->serverTank.setYaw(angle));
            if(mc.level.getEntity(tankId) instanceof AircraftEntity ac){
                var weapon=ac.weapons().getWeapon(0);weapon.setYaw(angle);weapon.setTurretYaw(angle);
                ac.lastRiderYaw=ac.getYaw()+angle;ac.prevLastRiderYaw=ac.lastRiderYaw;
            }
        }
        if(tick==485){
            check(VehicleImpactMarks.stored()>0,"Marks lost on vehicle rotation");capture="rotated";
            System.out.println("M8_IMPACTS rotation=true stored="+VehicleImpactMarks.stored());
            check(turretStart.distance(turretPoint(mc))>.1F,"Marked weapon part did not rotate relative to hull");
            System.out.println("M8_IMPACTS marked_turret_rotates=true");
        }
        if(tick==488)io.github.mcheliceport.client.model.VehicleImpactSurface.clear();
        if(tick==490)shoot(mc,false,0);
        if(tick==520){check(VehicleImpactMarks.stored()>10,"Evicted surface index did not recover");System.out.println("M8_IMPACTS cache_recovered=true stored="+VehicleImpactMarks.stored());}
        if(tick==500)mc.getSingleplayerServer().execute(()->serverTank.setDamageTaken(serverTank.getMaxHP()/2));
        if(tick==505)mc.getSingleplayerServer().execute(()->serverTank.repair(serverTank.getMaxHP()/30));
        if(tick==515){check(hasPartialOpacity(),"Partial repair did not fade marks");System.out.println("M8_IMPACTS partial_repair_fades=true");}
        if(tick==525)mc.getSingleplayerServer().execute(()->serverTank.repair(serverTank.getMaxHP()));
        if(tick==555){check(VehicleImpactMarks.stored()==0,"Repair left old impact marks");capture="repaired";System.out.println("M8_IMPACTS repair_cleared=true");}
        if(tick==570)ClientConfig.VEHICLE_DAMAGE_DETAILS.set(false);
        if(tick==580)shoot(mc,false,0);
        if(tick==610){check(VehicleImpactMarks.stored()==0,"Disabled damage marks still stored");System.out.println("M8_IMPACTS disabled=true");}
        if(tick==620)ClientConfig.VEHICLE_DAMAGE_DETAILS.set(true);
        if(tick>=630&&tick<675&&mc.level.getEntity(tankId) instanceof AircraftEntity ac){
            for(int i=0;i<256;i++)VehicleImpactMarks.receive(new io.github.mcheliceport.net.VehicleImpactPayload(
                    ac.getId(),ac.getUUID(),1.2F,1,0,-1,0,0,.12F,false,tick*256+i));
        }
        if(tick==690){check(VehicleImpactMarks.stored()>0&&VehicleImpactMarks.stored()<=16,"Burst exceeded per-vehicle cap");
            System.out.println("M8_IMPACTS burst_bounds=true stored="+VehicleImpactMarks.stored()+" pending="+VehicleImpactMarks.pending());}
        if(tick==692)ClientConfig.EFFECT_BUDGET.set(ClientConfig.EffectBudget.LOW);
        if(tick==696){check(VehicleImpactMarks.stored()<=8,"Low quality retained excess marks");System.out.println("M8_IMPACTS low_releases_excess=true stored="+VehicleImpactMarks.stored());}
        if(tick==700)ClientConfig.VEHICLE_DAMAGE_DETAILS.set(false);
        if(tick==710){check(VehicleImpactMarks.stored()==0&&VehicleImpactMarks.pending()==0,"Disable did not clear burst state");System.out.println("M8_IMPACTS burst_cleanup=true");}
        if(tick==730){ClientConfig.VEHICLE_DAMAGE_DETAILS.set(true);ClientConfig.EFFECT_BUDGET.set(ClientConfig.EffectBudget.BALANCED);mc.stop();}
        check(VehicleImpactMarks.stored()<=512&&VehicleImpactMarks.pending()<=256,"Unbounded marks/pending");
    }
    private static void shoot(Minecraft mc,boolean cannon,int index){
        mc.getSingleplayerServer().execute(()->{
            var level=mc.getSingleplayerServer().overworld();
            var info=cannon?ContentRegistries.weapon().get("m230"):ContentRegistries.weapon().findFirst(w->w.type.equals("machinegun1")&&w.explosion==0&&w.power>0);
            if(info==null)throw new IllegalStateException("Missing stock gun");
            var w=WeaponCreator.createWeapon(level,info.name,Vec3.ZERO,90,0,null,false);
            var boxes=serverTank.getHitBoxes();
            Vec3 target=boxes.length>0?boxes[index%boxes.length].getBoundingBox().getCenter():serverTank.getBoundingBox().getCenter();
            System.out.println("M8_IMPACTS target hp="+serverTank.getHP()+" bbox="+serverTank.getBoundingBox()+" part_count="+boxes.length);
            Vec3 origin=target.add(12,0,0),dir=new Vec3(-1,0,0);
            var bullet=new BulletEntity(level,origin.x,origin.y,origin.z,dir.x,dir.y,dir.z,90,0,w.acceleration){
                @Override protected void onImpact(net.minecraft.world.phys.HitResult hit,float factor){
                    System.out.println("M8_IMPACTS collision="+hit.getType()+" at="+hit.getLocation());super.onImpact(hit,factor);
                }
            };
            bullet.setName(info.name);bullet.setParameterFromWeapon(w,null,mc.getSingleplayerServer().getPlayerList().getPlayers().getFirst());
            level.addFreshEntity(bullet);System.out.println("M8_IMPACTS shot="+info.name+" cannon="+cannon+" position="+target);
        });
    }
    private static void printGroups() throws Exception {
        var field=VehicleImpactMarks.class.getDeclaredField("STATES");field.setAccessible(true);
        for(Object state:((java.util.Map<?,?>)field.get(null)).values()){
            var marks=state.getClass().getDeclaredField("marks");marks.setAccessible(true);
            for(Object mark:(Iterable<?>)marks.get(state)){
                var group=mark.getClass().getDeclaredField("group");group.setAccessible(true);
                var tile=mark.getClass().getDeclaredField("tile");tile.setAccessible(true);
                System.out.println("M8_IMPACTS mark="+((io.github.mcheliceport.client.model.McheliModel.Group)group.get(mark)).name()+" tile="+tile.get(mark));
            }
        }
    }
    @SuppressWarnings("unchecked") private static org.joml.Vector3f turretPoint(Minecraft mc){
        var ac=(AircraftEntity)mc.level.getEntity(tankId);
        var renderer=(AircraftRenderer<AircraftEntity>)mc.getEntityRenderDispatcher().getRenderer(ac);
        var state=new AircraftRenderState();renderer.extractRenderState(ac,state,1);
        var inverse=io.github.mcheliceport.effect.VehicleImpactLook.body(state.yaw,state.pitch,state.roll).invert();
        for(var draw:state.draws)for(var group:draw.groups())if(group.name().equals("$weapon0"))
            return new org.joml.Matrix4f(inverse).mul(draw.pose()).transformPosition(new org.joml.Vector3f(1,1,1));
        throw new IllegalStateException("No stock weapon0 part");
    }
    private static boolean hasPartialOpacity() throws Exception {
        var field=VehicleImpactMarks.class.getDeclaredField("STATES");field.setAccessible(true);
        for(Object state:((java.util.Map<?,?>)field.get(null)).values()){
            var marks=state.getClass().getDeclaredField("marks");marks.setAccessible(true);
            for(Object mark:(Iterable<?>)marks.get(state)){
                var opacity=mark.getClass().getDeclaredField("opacity");opacity.setAccessible(true);
                float value=opacity.getFloat(mark);if(value>0&&value<.99F)return true;
            }
        }
        return false;
    }
    private static void check(boolean condition,String text){if(!condition)throw new IllegalStateException(text);}
    @SubscribeEvent public static void frame(RenderFrameEvent.Post event){
        if(capture==null)return;var mc=Minecraft.getInstance();String name=capture;capture=null;
        Screenshot.grab(mc.gameDirectory,"m8-vehicle-impacts-"+name+".png",mc.gameRenderer.mainRenderTarget(),1,
                m->System.out.println("M8_IMPACTS "+m.getString()));
    }
}
