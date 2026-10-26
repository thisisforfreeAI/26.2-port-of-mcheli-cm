package io.github.mcheliceport.client.render;

import io.github.mcheliceport.client.particle.*;
import io.github.mcheliceport.config.*;
import io.github.mcheliceport.content.ContentRegistries;
import io.github.mcheliceport.entity.aircraft.AircraftEntity;
import io.github.mcheliceport.entity.heli.HeliEntity;
import io.github.mcheliceport.entity.plane.PlaneEntity;
import io.github.mcheliceport.entity.tank.TankEntity;
import io.github.mcheliceport.registry.ModEntityTypes;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Screenshot;
import net.minecraft.world.phys.Vec3;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.*;

/** Development-only controlled states rendered by production effects. Never shipped. */
@EventBusSubscriber(modid="mcheli_ce_port",value=Dist.CLIENT)
public final class LivingVehiclePreviewProbe {
    private static final int STEP=Integer.getInteger("mcheli.livingPreview",0);
    private static final String[] NAMES=STEP==2?new String[]{"ah-64","mh-60l_dap","mh-6"}
            :STEP==3?new String[]{"f-35a","f-15e","a-10"}:new String[]{"m1a2","ah-64","f-35a"};
    private static int ticks,phase=-1,age,lastPops,captureTick=-1;
    private static AircraftEntity vehicle;private static String capture;
    private static int damage(AircraftEntity e){return (int)(e.getMaxHP()*(STEP==1?(age>40?1:.3):STEP==2?(age<100?.05:.92):STEP==3?(age<130?.5:.96):.05));}
    private static void held(AircraftEntity e){e.xo=e.getX();e.yo=e.getY();e.zo=e.getZ();e.setPos(0,-60,20);e.setDeltaMovement(Vec3.ZERO);}
    private static final class Tank extends TankEntity {
        Tank(Minecraft mc){super(ModEntityTypes.TANK.get(),mc.level);}
        @Override public void tick(){held(this);}
        @Override public int getDamageTaken(){return damage(this);}
        @Override public boolean isDestroyed(){return STEP==1&&age>40||super.isDestroyed();}
    }
    private static final class Heli extends HeliEntity {
        Heli(Minecraft mc){super(ModEntityTypes.HELICOPTER.get(),mc.level);}
        @Override public void tick(){held(this);for(var rotor:getRotors())rotor.update(25);}
        @Override public int getDamageTaken(){return damage(this);}
        @Override public boolean isDestroyed(){return STEP==1&&age>40||super.isDestroyed();}
    }
    private static final class Plane extends PlaneEntity {
        Plane(Minecraft mc){super(ModEntityTypes.PLANE.get(),mc.level);}
        @Override public void tick(){held(this);}
        @Override public int getDamageTaken(){return damage(this);}
        @Override public boolean isDestroyed(){return STEP==1&&age>40||super.isDestroyed();}
    }
    private static void command(Minecraft mc,String...commands){for(var c:commands)mc.player.connection.sendCommand(c);}
    @SubscribeEvent public static void tick(ClientTickEvent.Post event){
        if(STEP==0)return;var mc=Minecraft.getInstance();if(mc.level==null||mc.player==null||mc.isPaused())return;
        if(++ticks==40){
            command(mc,"kill @e[type=!minecraft:player]","gamemode spectator","time set noon","weather clear","gamerule minecraft:advance_time false");
            for(int y=-60;y<4;y+=7)command(mc,"fill -30 "+y+" -30 30 "+(y+6)+" 40 air");
            command(mc,"fill -30 -64 -30 30 -61 40 stone","tp @s 12 -53 8 55 18");
            mc.getWindow().setWindowed(1280,720);mc.options.enableVsync().set(false);mc.options.framerateLimit().set(120);
            if(!mc.gui.hud.isHidden())mc.gui.hud.toggle();
            ClientConfig.EFFECT_QUALITY.set(EffectQuality.ENHANCED);ClientConfig.EFFECT_STYLE.set(ClientConfig.EffectStyle.POLISHED);
            ClientConfig.EFFECT_BUDGET.set(ClientConfig.EffectBudget.BALANCED);ClientConfig.ADAPTIVE_EFFECTS.set(false);
            ClientConfig.VEHICLE_DAMAGE_DETAILS.set(true);ClientConfig.ENGINE_STARTUP_EFFECTS.set(false);ClientConfig.ENGINE_HEAT_HAZE.set(false);
        }
        if(ticks<160)return;int next=(ticks-160)/220;age=(ticks-160)%220;
        if(next>=NAMES.length){mc.stop();return;}
        if(phase!=next){
            phase=next;if(vehicle!=null)vehicle.discard();mc.particleEngine.clearParticles();VehicleAtmosphere.clear();VehicleImpactMarks.clear();LoosePanels.clear();
            vehicle=ContentRegistries.plane().get(NAMES[phase])!=null?new Plane(mc):ContentRegistries.heli().get(NAMES[phase])!=null?new Heli(mc):new Tank(mc);
            vehicle.setId(975000+phase);vehicle.setAircraftTypeName(NAMES[phase]);vehicle.setTextureName(NAMES[phase]);vehicle.setPos(0,-60,20);
            var parts=vehicle.getParts();for(int i=0;i<parts.length;i++)parts[i].setId(985000+phase*100+i);
            for(int i=0;i<vehicle.getWeaponNum();i++){var set=vehicle.getWeapon(i);if(set!=null)set.setReserveAmmo(200);}
            mc.level.addEntity(vehicle);lastPops=0;captureTick=-1;
            System.out.println("M8_LIVING stage="+STEP+" vehicle="+NAMES[phase]);
        }
        if(age==95||age==145||age==180)capture=NAMES[phase]+"-age"+age;
        if(STEP==1){
            int pops=CookoffEffects.events();if(pops>lastPops){lastPops=pops;captureTick=ticks+1;}
            if(ticks==captureTick)capture=NAMES[phase]+"-pop"+lastPops;
            if(age==200){
                if(pops<2||pops>4||CookoffEffects.sounds()!=pops)throw new IllegalStateException("Missing/unbounded cook-off");
                if(!mc.level.getBlockState(new net.minecraft.core.BlockPos(0,-61,20)).is(net.minecraft.world.level.block.Blocks.STONE))throw new IllegalStateException("Cook-off modified terrain");
                System.out.println("M8_LIVING cookoffs="+pops+" sound_requests="+CookoffEffects.sounds()+" terrain_unchanged=true");
            }
        }
    }
    @SubscribeEvent public static void camera(ViewportEvent.ComputeFov event){
        if(STEP==0||vehicle==null)return;
        Vec3 at=STEP==1?VehicleAtmosphere.wreckOrigins(vehicle).getFirst():vehicle.position().add(0,3,0);
        event.getCamera().setPosition(at.add(STEP==1?new Vec3(7,3,-6):STEP==2?new Vec3(10,10,7):new Vec3(8,3,5)));
    }
    @SubscribeEvent public static void angles(ViewportEvent.ComputeCameraAngles event){
        if(STEP==0||vehicle==null)return;event.setYaw(STEP==1?49:STEP==2?125:122);event.setPitch(STEP==1?18:STEP==2?39:17);event.setRoll(0);
    }
    @SubscribeEvent public static void frame(RenderFrameEvent.Post event){
        if(capture==null)return;var mc=Minecraft.getInstance();String name=capture;capture=null;
        Screenshot.grab(mc.gameDirectory,"m8-living-"+STEP+"-"+name+".png",mc.gameRenderer.mainRenderTarget(),1,m->System.out.println("M8_LIVING "+m.getString()));
    }
}
