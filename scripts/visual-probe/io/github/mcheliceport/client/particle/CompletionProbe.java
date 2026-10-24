package io.github.mcheliceport.client.particle;

import io.github.mcheliceport.client.model.AircraftModels;
import io.github.mcheliceport.client.model.VehicleDetailShade;
import io.github.mcheliceport.client.model.VehicleSurfaceShade;
import io.github.mcheliceport.config.ClientConfig;
import io.github.mcheliceport.config.EffectQuality;
import io.github.mcheliceport.content.ContentRegistries;
import io.github.mcheliceport.effect.BlastDirection;
import io.github.mcheliceport.effect.BlastStyle;
import io.github.mcheliceport.entity.aircraft.AircraftEntity;
import io.github.mcheliceport.entity.heli.HeliEntity;
import io.github.mcheliceport.entity.plane.PlaneEntity;
import io.github.mcheliceport.entity.tank.TankEntity;
import io.github.mcheliceport.registry.ModEntityTypes;
import java.util.List;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Screenshot;
import net.minecraft.world.phys.Vec3;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.RenderFrameEvent;

/** Content-driven visual comparisons in the disposable M6Smoke world, never shipped. */
@EventBusSubscriber(modid="mcheli_ce_port", value=Dist.CLIENT)
public final class CompletionProbe {
    private static final String[] NAMES={"m1a2","ah-64","ch47","f-35a","m230","hydra70","agm114","agm158","mk82","gbu57","s-8dm"};
    private static final BlastStyle[] STYLES={BlastStyle.CANNON,BlastStyle.BLAST,BlastStyle.PENETRATOR,
            BlastStyle.BLAST,BlastStyle.HEAVY,BlastStyle.PENETRATOR,BlastStyle.INCENDIARY};
    private static int tick, phase=-1, age;
    private static AircraftEntity vehicle;
    private static String capture;
    private static final class Tank extends TankEntity {
        Tank(Minecraft mc){super(ModEntityTypes.TANK.get(),mc.level);}
        @Override public void tick(){xo=getX();yo=getY();zo=getZ();}
    }
    private static final class Heli extends HeliEntity {
        Heli(Minecraft mc){super(ModEntityTypes.HELICOPTER.get(),mc.level);}
        @Override public void tick(){xo=getX();yo=getY();zo=getZ();}
    }
    private static final class Plane extends PlaneEntity {
        Plane(Minecraft mc){super(ModEntityTypes.PLANE.get(),mc.level);}
        @Override public void tick(){xo=getX();yo=getY();zo=getZ();}
    }
    private static void commands(Minecraft mc,String...list){for(var s:list)mc.player.connection.sendCommand(s);}
    @SubscribeEvent public static void tick(ClientTickEvent.Post event){
        if(!Boolean.getBoolean("mcheli.probeCompletion"))return;
        var mc=Minecraft.getInstance(); if(mc.level==null||mc.player==null||mc.isPaused())return;
        if(++tick==40){
            commands(mc,"kill @e[type=!minecraft:player]","gamemode spectator","time set noon","weather clear",
                    "gamerule minecraft:advance_time false","fill -16 -60 4 16 -40 40 air","fill -16 -61 4 16 -61 40 stone");
            mc.getWindow().setWindowed(1920,1080);mc.options.enableVsync().set(false);mc.options.framerateLimit().set(120);
            if(!mc.gui.hud.isHidden())mc.gui.hud.toggle();
            ClientConfig.EFFECT_TARGET_FPS.set(60);ClientConfig.ADAPTIVE_EFFECTS.set(false);
            ClientConfig.EFFECT_QUALITY.set(EffectQuality.ENHANCED);ClientConfig.EFFECT_STYLE.set(ClientConfig.EffectStyle.POLISHED);
            ClientConfig.EFFECT_BUDGET.set(ClientConfig.EffectBudget.BALANCED);ClientConfig.DEFAULT_EXPLOSION_PARTICLE.set(false);
            ClientConfig.VEHICLE_SURFACE_SHADE.set(true);ClientConfig.ENGINE_HEAT_HAZE.set(false);
            ClientConfig.ENGINE_STARTUP_EFFECTS.set(false);ClientConfig.VEHICLE_DAMAGE_DETAILS.set(false);
        }
        if(tick<160)return;phase=(tick-160)/160 + (Boolean.getBoolean("mcheli.probePunch") ? 4 : 0);age=(tick-160)%160;
        if(phase>=NAMES.length){mc.stop();return;}
        if(age==0){
            if(vehicle!=null){vehicle.discard();vehicle=null;}
            mc.particleEngine.clearParticles();BlastGroundEffects.clear();
            if(phase<4){
                vehicle=phase==0?new Tank(mc):phase==3?new Plane(mc):new Heli(mc);
                vehicle.setId(-970000-phase);vehicle.setAircraftTypeName(NAMES[phase]);vehicle.setTextureName(NAMES[phase]);
                vehicle.setPos(0,-60,phase==2?27:20);
                var parts=vehicle.getParts();for(int i=0;i<parts.length;i++)parts[i].setId(-980000-phase*100-i);
                mc.level.addEntity(vehicle);
                commands(mc,phase==2?"tp @s 12 -51 6 31 20":phase==3?"tp @s 14 -50 -5 29 19":"tp @s 10 -53 7 38 20");
            }else commands(mc,"tp @s 0 -54 1 0 14");
            System.out.println("M8_COMPLETE phase="+NAMES[phase]);
        }
        if(phase<4&&age==70){
            var model=AircraftModels.get(vehicle.getAcInfo().getDirectoryName(),vehicle.getAcInfo());
            if(model==null)throw new IllegalStateException("Model not ready: "+NAMES[phase]);
            var zones=VehicleDetailShade.zones(vehicle.getAcInfo());int hits=0;
            for(var group:model.body().body())for(int i=0;i+8<=group.data().length;i+=8)
                if(VehicleDetailShade.factor(zones,group.data()[i],group.data()[i+1],group.data()[i+2])<.9999F)hits++;
            if(zones.isEmpty()||hits==0)throw new IllegalStateException("Detail anchors miss hull: "+NAMES[phase]);
            System.out.println("M8_COMPLETE zones="+zones.size()+" detail_vertices="+hits);
            capture=NAMES[phase]+"-detail";
        }
        if(phase<4&&age==85){
            var model=AircraftModels.get(vehicle.getAcInfo().getDirectoryName(),vehicle.getAcInfo());
            VehicleSurfaceShade.prepare(model.body());capture=NAMES[phase]+"-geometry";
        }
        if(phase<4&&age==95){
            var model=AircraftModels.get(vehicle.getAcInfo().getDirectoryName(),vehicle.getAcInfo());
            VehicleSurfaceShade.prepare(model.body(),VehicleDetailShade.zones(vehicle.getAcInfo()));
        }
        if(phase>=4&&age==40){
            var weapon=ContentRegistries.weapon().get(NAMES[phase]);
            if(weapon==null)throw new IllegalStateException("Missing weapon "+NAMES[phase]);
            var style=BlastStyle.forWeapon(weapon,weapon.flaming);
            if(style!=STYLES[phase-4])throw new IllegalStateException("Wrong family "+NAMES[phase]+":"+style);
            System.out.println("M8_COMPLETE weapon="+NAMES[phase]+" power="+weapon.explosion+" family="+style
                    +" lobes="+ExplosionSequence.lobes(weapon.explosion,style,ClientConfig.EffectBudget.BALANCED,0)
                    +" fire_ticks="+ExplosionSequence.fireTicks(weapon.explosion,style));
            io.github.mcheliceport.client.sound.ExplosionAudio.play(new io.github.mcheliceport.net.ExplosionSoundPayload(0,-59.5,19,weapon.explosion));
            ClientEffects.INSTANCE.explosionEffects(mc.level,0,-59.5,19,weapon.explosion,false,List.of(),style,
                    BlastDirection.from(new Vec3(.75,-.45,1)));
        }
        if(phase>=4&&(age==41||age==43||age==46||age==60||age==90))capture=NAMES[phase]+"-age"+(age-40);
        if(Boolean.getBoolean("mcheli.probePunch") && phase>=4) {
            if(age>=110 && age<=125) for(int i=0;i<80;i++) io.github.mcheliceport.client.sound.ExplosionAudio.play(
                    new io.github.mcheliceport.net.ExplosionSoundPayload(0,-59.5,19,10));
            if(phase==NAMES.length-1) {
                if(age==130) ClientConfig.EFFECT_STYLE.set(ClientConfig.EffectStyle.CLASSIC);
                if(age>=130 && age<=137) for(int i=0;i<80;i++) io.github.mcheliceport.client.sound.ExplosionAudio.play(
                        new io.github.mcheliceport.net.ExplosionSoundPayload(0,-59.5,19,10));
                if(age==140) ClientConfig.EFFECT_STYLE.set(ClientConfig.EffectStyle.POLISHED);
            }
            try {
                var field=io.github.mcheliceport.client.sound.ExplosionAudio.class.getDeclaredField("pending");
                field.setAccessible(true);int queued=((io.github.mcheliceport.client.sound.ExplosionAudioQueue<?>)field.get(null)).size();
                if(queued>32 || age==150 && queued!=0) throw new IllegalStateException("Audio queue did not stay bounded/drain: "+queued);
                if(age==150)System.out.println("M8_PUNCH audio_drained="+NAMES[phase]);
            } catch(ReflectiveOperationException e) { throw new IllegalStateException(e); }
        }
        if(BlastGroundEffects.trackedFragments()>24||BlastGroundEffects.surfaceReads()>96)
            throw new IllegalStateException("Weapon comparison exceeded ground budgets");
    }
    @SubscribeEvent public static void frame(RenderFrameEvent.Post event){
        if(!Boolean.getBoolean("mcheli.probeCompletion")||capture==null)return;
        var mc=Minecraft.getInstance();String name=capture;capture=null;
        Screenshot.grab(mc.gameDirectory,"m8-complete-"+name+".png",mc.gameRenderer.mainRenderTarget(),1,
                message->System.out.println("M8_COMPLETE "+message.getString()));
    }
}
