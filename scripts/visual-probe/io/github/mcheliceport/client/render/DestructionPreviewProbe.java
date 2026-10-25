package io.github.mcheliceport.client.render;

import io.github.mcheliceport.client.particle.VehicleAtmosphere;
import io.github.mcheliceport.config.*;
import io.github.mcheliceport.entity.aircraft.AircraftEntity;
import io.github.mcheliceport.entity.tank.TankEntity;
import io.github.mcheliceport.entity.heli.HeliEntity;
import io.github.mcheliceport.entity.plane.PlaneEntity;
import io.github.mcheliceport.registry.ModEntityTypes;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Screenshot;
import net.minecraft.world.phys.Vec3;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.RenderFrameEvent;
import net.neoforged.neoforge.client.event.ViewportEvent;

/** Controlled cosmetic-state previews; real production renderers, never shipped. */
@EventBusSubscriber(modid="mcheli_ce_port",value=Dist.CLIENT)
public final class DestructionPreviewProbe {
    private static final int STEP=Integer.getInteger("mcheli.destructionPreview",0);
    private static final String[] NAMES={"m1a2","ah-64","f-35a"};
    private static int tick,phase=-1,age;
    private static AircraftEntity vehicle;
    private static String capture;
    private static int damage(AircraftEntity e){return (int)(e.getMaxHP()*(STEP==5?1:STEP==7?.94:.78));}
    private static void move(AircraftEntity e){
        e.xo=e.getX();e.yo=e.getY();e.zo=e.getZ();
        double speed=STEP==1?(phase==2?.5:phase==1?.25:.1):0;
        e.setPos(0,STEP==1&&phase>0?-55:-60,STEP==1?-20+Math.max(0,age-50)*speed:20);e.setDeltaMovement(0,0,speed);
    }
    private static final class Tank extends TankEntity {
        Tank(Minecraft mc){super(ModEntityTypes.TANK.get(),mc.level);}
        @Override public void tick(){DestructionPreviewProbe.move(this);}
        @Override public int getDamageTaken(){return DestructionPreviewProbe.damage(this);}
    }
    private static final class Heli extends HeliEntity {
        Heli(Minecraft mc){super(ModEntityTypes.HELICOPTER.get(),mc.level);}
        @Override public void tick(){DestructionPreviewProbe.move(this);}
        @Override public int getDamageTaken(){return DestructionPreviewProbe.damage(this);}
    }
    private static final class Plane extends PlaneEntity {
        Plane(Minecraft mc){super(ModEntityTypes.PLANE.get(),mc.level);}
        @Override public void tick(){DestructionPreviewProbe.move(this);}
        @Override public int getDamageTaken(){return DestructionPreviewProbe.damage(this);}
    }
    private static void commands(Minecraft mc,String...c){for(String s:c)mc.player.connection.sendCommand(s);}
    @SubscribeEvent public static void tick(ClientTickEvent.Post event){
        if(STEP==0)return;var mc=Minecraft.getInstance();if(mc.level==null||mc.player==null||mc.isPaused())return;
        if(++tick==40){
            commands(mc,"kill @e[type=!minecraft:player]","gamemode spectator","time set noon","weather clear","gamerule minecraft:advance_time false");
            for(int y=-60;y<4;y+=7)commands(mc,"fill -30 "+y+" -30 30 "+(y+6)+" 40 air");
            commands(mc,"fill -30 -64 -30 30 -61 40 stone","tp @s 12 -53 8 55 18");
            mc.getWindow().setWindowed(1280,720);mc.options.enableVsync().set(false);mc.options.framerateLimit().set(120);
            if(!mc.gui.hud.isHidden())mc.gui.hud.toggle();
            ClientConfig.EFFECT_QUALITY.set(EffectQuality.ENHANCED);ClientConfig.EFFECT_STYLE.set(ClientConfig.EffectStyle.POLISHED);
            ClientConfig.EFFECT_BUDGET.set(ClientConfig.EffectBudget.BALANCED);ClientConfig.ADAPTIVE_EFFECTS.set(false);
            ClientConfig.VEHICLE_DAMAGE_DETAILS.set(true);ClientConfig.ENGINE_STARTUP_EFFECTS.set(false);ClientConfig.ENGINE_HEAT_HAZE.set(false);
        }
        if(tick<160)return;
        int next=(tick-160)/220;age=(tick-160)%220;
        if(next>=NAMES.length){mc.stop();return;}
        if(next!=phase){
            phase=next;if(vehicle!=null)vehicle.discard();mc.particleEngine.clearParticles();VehicleAtmosphere.clear();VehicleImpactMarks.clear();
            vehicle=phase==0?new Tank(mc):phase==1?new Heli(mc):new Plane(mc);
            vehicle.setId(970000+phase);vehicle.setAircraftTypeName(NAMES[phase]);vehicle.setTextureName(NAMES[phase]);vehicle.setPos(0,-59,20);
            var parts=vehicle.getParts();for(int i=0;i<parts.length;i++)parts[i].setId(980000+phase*100+i);
            mc.level.addEntity(vehicle);System.out.println("M8_DESTRUCTION step="+STEP+" vehicle="+NAMES[phase]);
        }
        if(age==145||age==180)capture=NAMES[phase]+"-age"+age;
        if(age==200)System.out.println("M8_DESTRUCTION state_count="+VehicleAtmosphere.stateCount()+" emitted="+VehicleAtmosphere.emitted());
    }
    @SubscribeEvent public static void angles(ViewportEvent.ComputeCameraAngles e){
        if(STEP==0||vehicle==null)return;e.setYaw(94);e.setPitch(17);e.setRoll(0);
    }
    @SubscribeEvent public static void position(ViewportEvent.ComputeFov e){
        if(STEP==0||vehicle==null)return;e.getCamera().setPosition(vehicle.position().add(15,5,1));
    }
    @SubscribeEvent public static void frame(RenderFrameEvent.Post event){
        if(capture==null)return;var mc=Minecraft.getInstance();String name=capture;capture=null;
        Screenshot.grab(mc.gameDirectory,"m8-destruction-"+STEP+"-"+name+".png",mc.gameRenderer.mainRenderTarget(),1,
                m->System.out.println("M8_DESTRUCTION "+m.getString()));
    }
}
