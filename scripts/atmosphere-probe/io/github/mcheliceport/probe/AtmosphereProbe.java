package io.github.mcheliceport.probe;

import io.github.mcheliceport.client.particle.*;
import io.github.mcheliceport.client.render.EngineHeat;
import io.github.mcheliceport.client.weapon.WeaponClient;
import io.github.mcheliceport.config.ClientConfig;
import io.github.mcheliceport.config.EffectQuality;
import io.github.mcheliceport.content.ContentLocation;
import io.github.mcheliceport.content.weapon.WeaponInfo;
import io.github.mcheliceport.entity.heli.HeliEntity;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Screenshot;
import net.minecraft.world.phys.Vec3;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.RenderFrameEvent;

/** Client pose overrides are deliberately confined to this disposable-world development scene. */
@EventBusSubscriber(modid="mcheli_ce_port",value=Dist.CLIENT)
public final class AtmosphereProbe {
    private static int tick;
    private static String capture;
    private static int wash, details;
    private static long voices;
    private static final WeaponInfo GUN = new WeaponInfo(ContentLocation.builtin("probe"), "probe", "probe");
    static { GUN.type = "machinegun2"; GUN.explosion = 6; }

    @SubscribeEvent(priority=EventPriority.HIGHEST)
    @SuppressWarnings("unchecked")
    public static void tick(ClientTickEvent.Post event) {
        Minecraft mc = Minecraft.getInstance();
        if(mc.level==null||mc.player==null||mc.isPaused())return;
        tick++;
        if(tick==20){
            mc.getWindow().setWindowed(1280,720); mc.options.enableVsync().set(false); mc.options.framerateLimit().set(60);
            mc.options.getSoundSourceOptionInstance(net.minecraft.sounds.SoundSource.MASTER).set(1.0);
            ClientConfig.EFFECT_QUALITY.set(EffectQuality.ENHANCED); ClientConfig.EFFECT_STYLE.set(ClientConfig.EffectStyle.POLISHED);
            ClientConfig.ADAPTIVE_EFFECTS.set(false); ClientConfig.EFFECT_BUDGET.set(ClientConfig.EffectBudget.HIGH);
            var atlas=mc.getAtlasManager().getAtlasOrThrow(net.minecraft.data.AtlasIds.PARTICLES);
            for(String name:new String[]{"m8_wash","m8_debris"}){
                var id=net.minecraft.resources.Identifier.fromNamespaceAndPath("mcheli_ce_port",name);
                if(!atlas.getSprite(id).contents().name().equals(id))throw new IllegalStateException("Missing atmosphere sprite "+id);
            }
            if(!mc.gui.hud.isHidden())mc.gui.hud.toggle();
            commands(mc,"kill @e[type=!minecraft:player]","gamemode spectator","time set noon","weather clear",
                "gamerule minecraft:advance_time false","tp @s 0 -40 -16 0 25",
                "fill -35 -61 5 35 -61 40 grass_block","fill -16 -61 5 -1 -61 40 sand",
                "fill 0 -61 5 15 -61 40 snow_block","fill 16 -61 5 35 -61 40 water");
            for(int x:new int[]{-24,-8,8,24})commands(mc,"summon mcheli_ce_port:helicopter "+x+" -54 14 {TypeName:\"ah-64\",TextureName:\"ah-64\",NoGravity:1b,Invulnerable:1b,AcFuel:1200}");
            commands(mc,"summon mcheli_ce_port:tank 0 -60 16 {TypeName:\"m1a2\",TextureName:\"m1a2\",Invulnerable:1b}");
        }
        for(var entity:mc.level.entitiesForRendering())if(entity instanceof HeliEntity heli){
            double x=heli.getX();
            heli.setPos(x,-54,14); heli.setOldPosAndRot(); heli.setDeltaMovement(Vec3.ZERO);
            double throttle=tick<140?0:Math.min(1,(tick-140)/80.0);
            heli.setCurrentThrottle(throttle);heli.setThrottle(throttle);heli.setFuel(heli.getMaxFuel());
            heli.setDamageTaken(tick>=280?(int)(heli.getMaxHP()*.75):0);
        }
        if(tick==130)commands(mc,"tp @s 2 -51 4 35 8");
        if(tick==190)capture="startup";
        if(tick==230)commands(mc,"tp @s 0 -40 -16 0 25");
        if(tick==250){capture="wash-materials"; if(EngineHeat.sourceCount()==0)throw new IllegalStateException("No warm engine heat sources");}
        if(tick==280)commands(mc,"tp @s 10 -48 -4 40 16");
        if(tick==320)capture="damage-a";
        if(tick==345)capture="damage-b";
        if(tick==380)commands(mc,"time set midnight","tp @s 8 -54 3 35 12");
        if(tick>=400&&tick<510&&tick%6==0){
            io.github.mcheliceport.weapon.WeaponClientHooks.weaponShot(mc.level,new Vec3(0,-57.3,24),Vec3.ZERO,GUN,0,0);
            ClientEffects.INSTANCE.spawnMuzzleFlash(mc.level,0,-57.3,24,.7F,1,.75F,.35F,1,2);
        }
        if(tick==451)capture="cannon-dust-light";
        if(tick==520){
            System.out.println("ATMOSPHERE wash-samples="+wash+" detail-samples="+details+" states="+VehicleAtmosphere.stateCount()+" peak-voices="+voices);
            if(wash==0||details==0||voices==0)throw new IllegalStateException("Missing atmosphere emissions or startup audio");
            ClientConfig.EFFECT_STYLE.set(ClientConfig.EffectStyle.CLASSIC);
        }
        if(tick==550){
            if(VehicleAtmosphere.stateCount()!=0||WeaponGroundEffects.flashes()!=0||EngineHeat.sourceCount()!=0)
                throw new IllegalStateException("Classic retained enhanced atmosphere state");
            int checked = 0;
            for (var entity : mc.level.entitiesForRendering()) {
                if (!(entity instanceof io.github.mcheliceport.entity.aircraft.AircraftEntity aircraft)) continue;
                var renderer = (io.github.mcheliceport.client.render.AircraftRenderer<io.github.mcheliceport.entity.aircraft.AircraftEntity>)
                        (Object) mc.getEntityRenderDispatcher().getRenderer(aircraft);
                var state = renderer.createRenderState(aircraft, 1);
                if (state.surfaceShade || state.contactShadowRadius != 2)
                    throw new IllegalStateException("Classic style retained vehicle shading/shadow sizing");
                checked++;
            }
            if (checked == 0) throw new IllegalStateException("No Classic vehicle render checks");
            System.out.println("ATMOSPHERE classic-render-checks=" + checked);
            System.out.println("ATMOSPHERE classic-cleanup=passed");mc.stop();
        }
    }
    private static void commands(Minecraft mc,String...commands){for(String c:commands)mc.player.connection.sendCommand(c);}
    @SubscribeEvent public static void frame(RenderFrameEvent.Post event){
        wash+=RotorWashEmitter.emitted();details+=VehicleAtmosphere.emitted();
        voices=Math.max(voices,VehicleAtmosphere.startupVoiceCount());
        if(RotorWashEmitter.lookups()>256||VehicleAtmosphere.emitted()>24||VehicleAtmosphere.stateCount()>128
                ||WeaponGroundEffects.rays()>24||WeaponGroundEffects.flashes()>12)throw new IllegalStateException("Effects exceeded hard bounds");
        if(capture==null)return;String name=capture;capture=null;Minecraft mc=Minecraft.getInstance();
        Screenshot.grab(mc.gameDirectory,"atmosphere-"+name+".png",mc.gameRenderer.mainRenderTarget(),1,
                message->System.out.println("ATMOSPHERE "+message.getString()));
    }
}
