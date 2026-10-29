package io.github.mcheliceport.client.render;

import io.github.mcheliceport.entity.aircraft.*;
import java.util.*;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.*;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.*;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.*;

/** Four cached surface patches, offered by the existing beam extraction. At most 19 rays/tick. */
@EventBusSubscriber(modid="mcheli_ce_port",value=Dist.CLIENT)
public final class LandingLightGround {
    private record Key(AircraftEntity aircraft,io.github.mcheliceport.content.aircraft.AircraftInfo.SearchLight lamp){}
    private static final class Source {
        Vec3 origin,end,patchAt,sampledOrigin;SearchLightBeam beam;List<GlowGeometry.Surface> surfaces=List.of();long seen,updated;
    }
    private static final Map<Key,Source> SOURCES=new LinkedHashMap<>();
    private static ClientLevel world;private static long tick;private static int cursor,rays;
    private LandingLightGround(){}
    public static int sources(){return SOURCES.size();}public static int rays(){return rays;}
    public static int surfaces(){return SOURCES.values().stream().mapToInt(s->s.surfaces.size()).sum();}
    public static void clear(){SOURCES.clear();world=null;tick=0;cursor=rays=0;}
    private static boolean enabled(){var level=Minecraft.getInstance().level;return FlightLighting.enabled()&&level!=null&&FlightLightLook.night(level.getSkyDarken())>.03F;}
    public static void offer(AircraftEntity ac,SearchLightBeam beam,float partial){
        var mc=Minecraft.getInstance();if(!enabled()||mc.level!=ac.level()||ac.isDestroyed()||!ac.isSearchLightOn()||!FlightLighting.allowed(ac,beam.light()))return;
        if(world!=mc.level){clear();world=mc.level;}
        Vec3 position=ac.getPosition(partial),at=position.add(beam.point(0,0,0));
        if(mc.gameRenderer.mainCamera().position().distanceToSqr(at)>96*96)return;
        var key=new Key(ac,beam.light());var source=SOURCES.get(key);
        if(source==null){
            if(SOURCES.size()>=4){
                var eye=mc.gameRenderer.mainCamera().position();var far=SOURCES.entrySet().stream().max(Comparator.comparingDouble(e->eye.distanceToSqr(e.getValue().origin))).orElseThrow();
                if(eye.distanceToSqr(at)+1>=eye.distanceToSqr(far.getValue().origin))return;SOURCES.remove(far.getKey());
            }
            source=new Source();SOURCES.put(key,source);
        }
        source.origin=at;source.end=position.add(beam.point(0,Math.min(48,beam.light().height),0));source.beam=beam;source.seen=tick;
        if(source.sampledOrigin!=null&&source.origin.distanceToSqr(source.sampledOrigin)>1)source.surfaces=List.of();
    }
    @SubscribeEvent public static void tick(ClientTickEvent.Post event){
        var mc=Minecraft.getInstance();if(world!=mc.level||!enabled()){clear();world=mc.level;}
        if(world==null||!enabled()||mc.isPaused())return;++tick;rays=0;
        var eye=mc.gameRenderer.mainCamera().position();
        SOURCES.entrySet().removeIf(e->{var ac=e.getKey().aircraft;var source=e.getValue();return ac.isRemoved()||ac.level()!=world||ac.isDestroyed()||!ac.isSearchLightOn()
                ||!FlightLighting.allowed(ac,e.getKey().lamp)||tick-source.seen>6||eye.distanceToSqr(source.origin)>96*96;});
        if(SOURCES.isEmpty())return;var list=new ArrayList<>(SOURCES.values());sample(list.get(Math.floorMod(cursor++,list.size())));
    }
    private static void sample(Source source){
        source.surfaces=List.of();source.updated=tick;source.sampledOrigin=source.origin;
        if(!world.hasChunkAt(BlockPos.containing(source.origin))||!world.hasChunkAt(BlockPos.containing(source.end)))return;
        rays++;var center=world.clip(new ClipContext(source.origin,source.end,ClipContext.Block.COLLIDER,ClipContext.Fluid.ANY,CollisionContext.empty()));
        if(center.getType()!=HitResult.Type.BLOCK||center.getDirection()!=Direction.UP)return;
        Vec3 at=center.getLocation();float radius=radius(at.distanceTo(source.origin),source.beam.light().width,source.beam.light().height);
        var origin=BlockPos.containing(at);var surfaces=new ArrayList<GlowGeometry.Surface>();
        for(int x=-1;x<=1;x++)for(int z=-1;z<=1;z++){
            double px=origin.getX()+x+.5,pz=origin.getZ()+z+.5;
            if(!world.hasChunkAt(BlockPos.containing(px,at.y,pz)))continue;
            rays++;var hit=world.clip(new ClipContext(new Vec3(px,at.y+2,pz),new Vec3(px,at.y-2,pz),ClipContext.Block.COLLIDER,ClipContext.Fluid.ANY,CollisionContext.empty()));
            if(hit.getType()!=HitResult.Type.BLOCK||hit.getDirection()!=Direction.UP)continue;
            var pos=hit.getBlockPos();var block=world.getBlockState(pos);if(!block.getFluidState().isEmpty())continue;
            var shape=block.getCollisionShape(world,pos);if(shape.isEmpty()||shape.toAabbs().size()!=1)continue;
            rays++;var obstruction=world.clip(new ClipContext(source.origin,hit.getLocation().add(0,.04,0),ClipContext.Block.COLLIDER,ClipContext.Fluid.ANY,CollisionContext.empty()));
            if(obstruction.getType()!=HitResult.Type.MISS)continue;
            var bounds=shape.bounds().move(pos);float x0=(float)(bounds.minX-at.x),x1=(float)(bounds.maxX-at.x),z0=(float)(bounds.minZ-at.z),z1=(float)(bounds.maxZ-at.z);
            float a=falloff(x0,z0,radius),b=falloff(x0,z1,radius),c=falloff(x1,z1,radius),d=falloff(x1,z0,radius);
            if(a+b+c+d>0)surfaces.add(new GlowGeometry.Surface(x0,x1,(float)(bounds.maxY+.012-at.y),z0,z1,a,b,c,d));
        }
        source.patchAt=at;source.surfaces=List.copyOf(surfaces);
    }
    static float falloff(float x,float z,float radius){return Math.clamp(1-(x*x+z*z)/(radius*radius),0,1);}
    static float radius(double distance,float width,float height){return Double.isFinite(distance)&&Float.isFinite(width+height)&&height>0?(float)Math.clamp(distance*width/(2*height),.9,1.9):.9F;}
    @SubscribeEvent public static void submit(SubmitCustomGeometryEvent event){
        var mc=Minecraft.getInstance();if(world!=mc.level||!enabled())return;
        var pose=event.getPoseStack();var camera=event.getLevelRenderState().cameraRenderState.pos;
        float night=FlightLightLook.night(world.getSkyDarken());
        for(var entry:List.copyOf(SOURCES.entrySet())){
            var source=entry.getValue();var ac=entry.getKey().aircraft;
            if(ac.isDestroyed()||!ac.isSearchLightOn()||ac.getAcInfo()==null)continue;
            int lamp=ac.getAcInfo().searchLights.indexOf(entry.getKey().lamp);if(lamp<0)continue;
            float strength=VehicleLightDamage.strength(ac,lamp);if(strength<=0)continue;
            if(source.surfaces.isEmpty()||source.patchAt==null||tick-source.updated>6)continue;
            Vec3 at=source.patchAt;pose.pushPose();pose.translate(at.x-camera.x,at.y-camera.y,at.z-camera.z);
            GlowGeometry.surfaces(pose,event.getSubmitNodeCollector(),source.surfaces,night*.24F*strength,source.surfaces.size(),source.beam.light().colorStart&0xFFFFFF);pose.popPose();
        }
    }
}
