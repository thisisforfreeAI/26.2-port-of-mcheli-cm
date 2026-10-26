package io.github.mcheliceport.client.particle;

import io.github.mcheliceport.client.model.*;
import io.github.mcheliceport.client.render.*;
import io.github.mcheliceport.config.*;
import io.github.mcheliceport.entity.aircraft.AircraftEntity;
import java.util.*;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.Identifier;
import net.minecraft.world.level.biome.Biome;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientTickEvent;

/** Cached climate sampling: eight exposed-biome checks per tick, 64 nearby vehicle states. */
@EventBusSubscriber(modid="mcheli_ce_port",value=Dist.CLIENT)
public final class VehicleWeather {
    private static final class State {final VehicleWeatherLook look=new VehicleWeatherLook();boolean rain,snow,cold,exposed;}
    private static final Map<AircraftEntity,State> STATES=new LinkedHashMap<>();
    private static final Identifier SNOW=Identifier.withDefaultNamespace("textures/block/snow.png");
    private static ClientLevel world;private static long lastTick=Long.MIN_VALUE;private static int samples,shed;
    private VehicleWeather(){}
    private static boolean enabled(){return EffectQuality.enhanced()&&ClientConfig.get(ClientConfig.VEHICLE_WEATHER);}
    public static void clear(){STATES.clear();world=null;lastTick=Long.MIN_VALUE;samples=shed=0;}
    public static float wet(AircraftEntity ac){var s=STATES.get(ac);return s==null?0:s.look.wet();}
    public static float snow(AircraftEntity ac){var s=STATES.get(ac);return s==null?0:s.look.snow();}
    public static int samples(){return samples;}
    @SubscribeEvent public static void tick(ClientTickEvent.Post event){
        var mc=Minecraft.getInstance();if(world!=mc.level||!enabled()){clear();world=mc.level;}
        if(world==null||!enabled()||mc.player==null||mc.isPaused())return;
        long now=world.getGameTime();if(now==lastTick)return;lastTick=now;samples=shed=0;
        STATES.keySet().removeIf(ac->ac.isRemoved()||ac.level()!=world||mc.gameRenderer.mainCamera().position().distanceToSqr(ac.position())>64*64);
        int scanned=0;
        for(var entity:world.entitiesForRendering()){
            if(++scanned>128)break;
            if(!(entity instanceof AircraftEntity ac)||ac.getAcInfo()==null||mc.gameRenderer.mainCamera().position().distanceToSqr(ac.position())>48*48)continue;
            if(STATES.size()>=64&&!STATES.containsKey(ac))continue;
            var state=STATES.computeIfAbsent(ac,k->new State());
            if(samples<8&&Math.floorMod(now+ac.getId(),10)==0){
                var pos=BlockPos.containing(ac.position().add(0,1,0));
                if(world.hasChunkAt(pos)){
                    samples++;var biome=world.getBiome(pos).value();var precipitation=biome.getPrecipitationAt(pos,world.getSeaLevel());
                    state.exposed=world.canSeeSky(pos);state.cold=biome.coldEnoughToSnow(pos,world.getSeaLevel());
                    state.rain=world.isRaining()&&precipitation==Biome.Precipitation.RAIN;state.snow=world.isRaining()&&precipitation==Biome.Precipitation.SNOW;
                }else{state.rain=state.snow=state.exposed=false;}
            }
            boolean moving=ac.getDeltaMovement().lengthSqr()>.001||Math.abs(ac.getPitch())>12||Math.abs(ac.getRoll())>12;
            float before=state.look.snow();state.look.tick(state.rain,state.snow,state.cold,state.exposed,moving);
            if(moving&&before>.05F&&shed<4&&Math.floorMod(now+ac.getId(),3)==0){
                var model=AircraftModels.peek(ac.getAcInfo());double top=model==null?1.5:Math.clamp(model.body().maxY*.65,1,4);
                var p=ac.position().add(Math.sin(now*.7)*.7,top,Math.cos(now*.7)*.7);
                if(VehicleAtmosphere.admit(world,p,.035F)){
                    mc.particleEngine.add(new SurfaceDebrisParticle(world,p.x,p.y,p.z,ac.getDeltaMovement().x*.15,-.04,ac.getDeltaMovement().z*.15,.03F,.95F,.98F,1,
                            Double.NEGATIVE_INFINITY,16,.01F));shed++;
                }
            }
        }
    }
    public static void extract(AircraftEntity ac,AircraftRenderState render){
        if(!enabled()||render.thermal||render.far)return;var state=STATES.get(ac);if(state==null)return;
        render.color=state.look.tint(render.color);
        if(state.look.snow()<.02F)return;int coated=0;
        for(var draw:render.draws){
            if(draw.pose().m11()<.65F)continue;
            for(var group:draw.groups()){
                String name=group.name().toLowerCase(Locale.ROOT);
                if(!(name.equals("$body")||name.startsWith("$canopy")||name.startsWith("$wing")||name.startsWith("$weapon")&&!name.contains("_")))continue;
                if(coated++>=4)return;var skin=VehicleSkinMeshes.get(group);if(skin==null)continue;
                int alpha=Math.round(210*state.look.snow());
                VehicleDetailLayers.add(render,draw.pose(),SNOW,List.of(skin.top()),(alpha<<24)|0xF5F9FF);
            }
        }
    }
}
