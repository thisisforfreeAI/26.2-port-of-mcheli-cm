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
import net.minecraft.tags.*;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.*;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientTickEvent;

/** Four ground probes per tick, cached material and bounded cosmetic history. */
@EventBusSubscriber(modid="mcheli_ce_port",value=Dist.CLIENT)
public final class VehicleSurfaceBuildup {
    private static final class State {final SurfaceBuildupLook look=new SurfaceBuildupLook();boolean loose,water,rain;int color=0x786044;}
    private static final Map<AircraftEntity,State> STATES=new LinkedHashMap<>();
    private static final Identifier MASK=Identifier.fromNamespaceAndPath("mcheli_ce_port","textures/effect/m8_fluid_stains.png");
    private static ClientLevel world;private static long tick=Long.MIN_VALUE;private static int probes;
    private VehicleSurfaceBuildup(){}
    private static boolean enabled(){return EffectQuality.enhanced()&&ClientConfig.get(ClientConfig.VEHICLE_SURFACE_BUILDUP);}
    public static void clear(){STATES.clear();world=null;tick=Long.MIN_VALUE;probes=0;}
    public static float dirt(AircraftEntity ac){var s=STATES.get(ac);return s==null?0:s.look.dirt();}
    public static float soot(AircraftEntity ac){var s=STATES.get(ac);return s==null?0:s.look.soot();}
    public static int probes(){return probes;}
    @SubscribeEvent public static void tick(ClientTickEvent.Post event){
        var mc=Minecraft.getInstance();if(world!=mc.level||!enabled()){clear();world=mc.level;}
        if(world==null||!enabled()||mc.player==null||mc.isPaused()||tick==world.getGameTime())return;
        tick=world.getGameTime();probes=0;
        STATES.keySet().removeIf(ac->ac.isRemoved()||ac.level()!=world||mc.gameRenderer.mainCamera().position().distanceToSqr(ac.position())>64*64);
        int scanned=0;for(var e:world.entitiesForRendering()){
            if(++scanned>128)break;if(!(e instanceof AircraftEntity ac)||ac.getAcInfo()==null||mc.gameRenderer.mainCamera().position().distanceToSqr(ac.position())>48*48)continue;
            if(STATES.size()>=64&&!STATES.containsKey(ac))continue;var s=STATES.computeIfAbsent(ac,k->new State());
            if(probes<4&&Math.floorMod(tick+ac.getId(),5)==0){
                var at=ac.position().add(0,.5,0);if(world.hasChunkAt(BlockPos.containing(at))){
                    probes++;var hit=world.clip(new ClipContext(at,at.add(0,-2.5,0),ClipContext.Block.COLLIDER,ClipContext.Fluid.ANY,ac));
                    s.loose=s.water=false;
                    if(hit.getType()!=HitResult.Type.MISS){var block=world.getBlockState(hit.getBlockPos());s.water=world.getFluidState(hit.getBlockPos()).is(FluidTags.WATER);
                        s.loose=block.is(BlockTags.DIRT)||block.is(BlockTags.SAND);s.color=block.is(BlockTags.SAND)?0xC4AC78:0x786044;}
                    s.rain=world.isRainingAt(BlockPos.containing(at.add(0,1,0)));
                }else{s.loose=s.water=s.rain=false;}
            }
            s.look.tick(ac.getDeltaMovement().horizontalDistanceSqr()>.001,s.loose,s.water,s.rain,!ac.isDestroyed()&&ac.canUseFuel()&&ac.getCurrentThrottle()>.2);
        }
    }
    public static void extract(AircraftEntity ac,AircraftRenderState render){
        if(!enabled()||render.far||render.thermal)return;var state=STATES.get(ac);if(state==null)return;
        var outlets=EngineExhaustLook.outlets(ac.getAcInfo(),0);int coated=0;
        for(var draw:render.draws)for(var group:draw.groups()){
            String name=group.name().toLowerCase(Locale.ROOT);
            if(!(name.equals("$body")||name.startsWith("$weapon")&&!name.contains("_")))continue;
            if(coated++>=4)return;
            if(state.look.dirt()>.01F){var skin=VehicleSkinMeshes.get(group);if(skin!=null)VehicleDetailLayers.add(render,draw.pose(),MASK,List.of(skin.lower()),(Math.round(195*state.look.dirt())<<24)|state.color);}
            if(state.look.soot()>.01F&&name.equals("$body")){
                var model=AircraftModels.peek(ac.getAcInfo());if(model==null)continue;
                var points=outlets.isEmpty()&&ac instanceof io.github.mcheliceport.entity.tank.TankEntity
                    ?List.of(new Vec3(0,model.body().maxY*.65,model.body().minZ*.8))
                    :outlets.stream().map(o->o.position()).toList();
                for(var point:points){var mesh=VehicleSkinMeshes.region(group,point,1.4F);if(mesh!=null)VehicleDetailLayers.add(render,draw.pose(),MASK,List.of(mesh),(Math.round(200*state.look.soot())<<24)|0x151719);}
            }
        }
    }
}
