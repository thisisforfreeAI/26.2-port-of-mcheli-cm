package io.github.mcheliceport.client.render;

import io.github.mcheliceport.client.effect.BloodStains;
import io.github.mcheliceport.client.particle.VehicleBloodEffects;
import io.github.mcheliceport.client.particle.VehicleAtmosphere;
import io.github.mcheliceport.config.*;
import io.github.mcheliceport.content.ContentLocation;
import io.github.mcheliceport.content.weapon.WeaponInfo;
import io.github.mcheliceport.effect.VehicleBlood;
import io.github.mcheliceport.entity.aircraft.AircraftEntity;
import io.github.mcheliceport.entity.heli.HeliEntity;
import io.github.mcheliceport.entity.tank.TankEntity;
import io.github.mcheliceport.entity.weapon.BulletEntity;
import io.github.mcheliceport.registry.ModEntityTypes;
import io.github.mcheliceport.weapon.explosion.McheliExplosion;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Screenshot;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.phys.Vec3;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.*;
import net.neoforged.neoforge.event.tick.ServerTickEvent;

/** Non-shipping controlled kills: real hurt, server packet, client admission and production rendering. */
@EventBusSubscriber(modid="mcheli_ce_port",value=Dist.CLIENT)
public final class VehicleBloodPreviewProbe {
    private static final boolean ENABLED=Boolean.getBoolean("mcheli.probeBlood");
    private static int ticks,age,phase=-1;
    private static volatile int request=-1,finished=-1;
    private static int serverPhase=-1,serverAge;
    private static AircraftEntity vehicle;
    private static Mob victim;
    private static String capture;
    private static final String[] NAMES={"ah-64","m1a2","t-90"};
    private static final Vec3 FEET=new Vec3(0,-60,20);

    @SubscribeEvent public static void tick(ClientTickEvent.Post event) {
        if(!ENABLED)return;
        var mc=Minecraft.getInstance();if(mc.level==null||mc.player==null||mc.isPaused())return;
        if(++ticks==40) {
            for(String command:new String[]{"kill @e[type=!minecraft:player]","gamemode spectator","time set noon","weather clear",
                    "gamerule minecraft:advance_time false","fill -20 -62 0 20 -61 40 stone","fill -20 -60 0 20 -45 40 air","tp @s 3 -57 24 143 28"})
                mc.player.connection.sendCommand(command);
            mc.getWindow().setWindowed(1280,720);mc.options.enableVsync().set(false);mc.options.framerateLimit().set(120);
            if(!mc.gui.hud.isHidden())mc.gui.hud.toggle();
            ClientConfig.EFFECT_QUALITY.set(EffectQuality.ENHANCED);ClientConfig.VEHICLE_BLOOD.set(true);
            ClientConfig.EFFECT_BUDGET.set(ClientConfig.EffectBudget.BALANCED);ClientConfig.ADAPTIVE_EFFECTS.set(false);
            ClientConfig.ENGINE_STARTUP_EFFECTS.set(false);
        }
        if(ticks<160)return;
        int next=(ticks-160)/180;age=(ticks-160)%180;
        if(next>=3){System.out.println("M8_BLOOD all_scenes_passed=true");mc.stop();return;}
        if(next!=phase){phase=next;VehicleBloodEffects.clear();mc.particleEngine.clearParticles();ClientConfig.VEHICLE_BLOOD.set(true);request=phase;}
        if(age==95&&finished!=phase)throw new IllegalStateException("Missing confirmed server vehicle kill");
        if(age==100) {
            if(VehicleBloodEffects.droplets()==0||BloodStains.count()==0||BloodStains.rays()>4)
                throw new IllegalStateException("Missing/unbounded blood render: drops="+VehicleBloodEffects.droplets()+" marks="+BloodStains.count());
            System.out.println("M8_BLOOD vehicle="+NAMES[phase]+" drops="+VehicleBloodEffects.droplets()+" marks="+BloodStains.count()+" shared_tick="+VehicleAtmosphere.emitted());
        }
        if(age==105)capture=NAMES[phase]+"-ground";
        if(age==125)ClientConfig.VEHICLE_BLOOD.set(false);
        if(age==130) {
            if(BloodStains.count()!=0||VehicleBloodEffects.droplets()!=0)throw new IllegalStateException("Disabled blood retained state");
            System.out.println("M8_BLOOD toggle_cleared=true");
        }
    }
    @SubscribeEvent public static void server(ServerTickEvent.Post event) {
        if(!ENABLED||request<0)return;
        var level=event.getServer().overworld();
        if(serverPhase!=request) {
            serverPhase=request;serverAge=0;
            if(vehicle!=null)vehicle.discard();if(victim!=null)victim.discard();
            vehicle=serverPhase==0?new HeliEntity(ModEntityTypes.HELICOPTER.get(),level){@Override protected void onUpdateAircraft(){}}
                    :new TankEntity(ModEntityTypes.TANK.get(),level){@Override protected void onUpdateAircraft(){}};
            vehicle.setAircraftTypeName(NAMES[serverPhase]);vehicle.setTextureName(NAMES[serverPhase]);
            vehicle.setPos(serverPhase==2?FEET:FEET.add(-5,0,-3));level.addFreshEntity(vehicle);
            victim=(serverPhase==1?EntityTypes.COW:EntityTypes.HUSK).create(level,EntitySpawnReason.COMMAND);
            victim.setNoAi(true);victim.setNoGravity(true);victim.setPos(serverPhase==2?FEET.add(.1,0,0):FEET);
            victim.setHealth(1);level.addFreshEntity(victim);
        }
        serverAge++;
        if(serverAge==65) {
            if(serverPhase==2) {vehicle.setDeltaMovement(.2,0,0);vehicle.tick();vehicle.setDeltaMovement(Vec3.ZERO);vehicle.setPos(FEET.add(-5,0,-3));}
            else {
                var info=new WeaponInfo(ContentLocation.builtin("blood_preview"),"preview","txt");
                var bullet=new BulletEntity(ModEntityTypes.BULLET.get(),level){@Override public WeaponInfo getInfo(){return info;}@Override public WeaponInfo getWeaponInfo(){return info;}};
                bullet.shootingAircraft=vehicle;bullet.setPower(1000);bullet.setDeltaMovement(0,0,1);bullet.setPos(FEET.add(0,1,-2));
                if(serverPhase==0)bullet.onImpactEntity(victim,1);
                else {var blast=new McheliExplosion(level,bullet,null,FEET.x-1,FEET.y+.5,FEET.z,3,0,false,false);blast.explosionDamagePower=100;blast.doExplosionA();}
            }
            if(victim.isAlive()||!VehicleBlood.reported(level,victim.getUUID()))throw new IllegalStateException("Missing real kill attribution");
            finished=serverPhase;
            System.out.println("M8_BLOOD confirmed_server_kill="+serverPhase);
        }
    }
    @SubscribeEvent public static void camera(ViewportEvent.ComputeFov event){
        if(ENABLED&&phase>=0)event.getCamera().setPosition(FEET.add(3,2.7,4));
    }
    @SubscribeEvent public static void angles(ViewportEvent.ComputeCameraAngles event){
        if(ENABLED&&phase>=0){event.setYaw(143);event.setPitch(28);event.setRoll(0);}
    }
    @SubscribeEvent public static void frame(RenderFrameEvent.Post event){
        if(!ENABLED||phase<0)return;
        if(finished==phase&&VehicleBloodEffects.droplets()>0&&age<100&&capture==null){
            // Capture a single first live frame after the packet has created droplets.
            if(capturedPhase!=phase){capturedPhase=phase;capture=NAMES[phase]+"-impact";}
        }
        if(capture!=null){var mc=Minecraft.getInstance();String name=capture;capture=null;
            Screenshot.grab(mc.gameDirectory,"m8-blood-"+name+".png",mc.gameRenderer.mainRenderTarget(),1,m->System.out.println("M8_BLOOD "+m.getString()));}
    }
    private static int capturedPhase=-1;
}
