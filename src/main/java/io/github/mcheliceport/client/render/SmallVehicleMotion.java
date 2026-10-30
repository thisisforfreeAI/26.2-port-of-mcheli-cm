package io.github.mcheliceport.client.render;

import io.github.mcheliceport.client.model.*;
import io.github.mcheliceport.config.*;
import io.github.mcheliceport.content.aircraft.PlaneInfo;
import io.github.mcheliceport.entity.aircraft.AircraftEntity;
import java.util.*;
import java.util.concurrent.*;
import org.joml.Matrix4f;

/** Cached whip segments and tiny vibration of authored nozzle covers; no physical forces. */
public final class SmallVehicleMotion {
    private record Key(String name,McheliModel.Group group){}
    private static final Map<Key,CompletableFuture<SmallMotionMesh.Parts>> CACHE=new LinkedHashMap<>(16,.75F,true);
    private static final Map<AircraftEntity,Integer> MOVED=Collections.synchronizedMap(new WeakHashMap<>());
    private static final ExecutorService LOADER=Executors.newSingleThreadExecutor(r->{var t=new Thread(r,"mcheli-small-motion");t.setDaemon(true);return t;});
    private static int pending;
    private SmallVehicleMotion(){}
    private static SmallMotionMesh.Parts get(String name,McheliModel.Group group){
        var key=new Key(name,group);CompletableFuture<SmallMotionMesh.Parts> future;
        synchronized(CACHE){future=CACHE.get(key);if(future==null){
            if(pending>=4||group.vertexCount()>60000)return null;
            if(CACHE.size()>=16){var it=CACHE.entrySet().iterator();boolean removed=false;while(it.hasNext())if(it.next().getValue().isDone()){it.remove();removed=true;break;}if(!removed)return null;}
            future=new CompletableFuture<>();CACHE.put(key,future);pending++;var result=future;
            LOADER.execute(()->{SmallMotionMesh.Parts parts=null;try{parts=SmallMotionMesh.build(name,group);
                if(parts!=null){var groups=new ArrayList<McheliModel.Group>();groups.add(parts.fixed());for(var whip:parts.whips()){groups.add(whip.lower());groups.add(whip.upper());}
                    var model=new McheliModel("small-motion",groups,0,0,0,0,0,0);VehicleSurfaceShade.prepare(model);VehicleImpactSurface.prepare(model);}
            }finally{result.complete(parts);synchronized(CACHE){pending--;}}});
        }}return future.getNow(null);
    }
    public static void apply(AircraftEntity ac,AircraftRenderState render,float partial){
        MOVED.put(ac,0);if(ac.isDestroyed()||render.far||render.thermal||!EffectQuality.enhanced()||!ClientConfig.get(ClientConfig.VEHICLE_SMALL_MOTION))return;
        var info=ac.getAcInfo();if(info==null)return;float time=ac.level().getGameTime()+partial;int count=0;
        boolean moving=ac.getDeltaMovement().lengthSqr()>.001||ac.getCurrentThrottle()>.2;
        var extra=new ArrayList<AircraftPartPoses.Draw>();
        for(int i=0;i<render.draws.size();i++){
            var draw=render.draws.get(i);var fixed=new ArrayList<McheliModel.Group>();boolean changed=false;
            for(var group:draw.groups()){
                var parts=group.name().equals("$weapon0")&&!SmallMotionMesh.profile(info.name).isEmpty()?get(info.name,group):null;
                if(parts!=null){fixed.add(parts.fixed());changed=true;
                    for(var whip:parts.whips()){
                        float angle=SmallMotionMesh.sway(time,ac.getId()+count,moving);var p=whip.base();var m=whip.middle();
                        var lower=new Matrix4f(draw.pose()).translate(p).rotateZ(angle).translate(-p.x,-p.y,-p.z);
                        var upper=new Matrix4f(lower).translate(m).rotateZ(angle*.7F).translate(-m.x,-m.y,-m.z);
                        extra.add(new AircraftPartPoses.Draw(lower,List.of(whip.lower())));extra.add(new AircraftPartPoses.Draw(upper,List.of(whip.upper())));count++;
                    }
                }else fixed.add(group);
            }
            if(changed)render.draws.set(i,new AircraftPartPoses.Draw(draw.pose(),List.copyOf(fixed)));
            if(info instanceof PlaneInfo plane&&ac.canUseFuel()&&ac.getCurrentThrottle()>.2&&!ac.isDestroyed()){
                for(var nozzle:plane.nozzles)if(AircraftModels.peek(info)!=null&&!Collections.disjoint(draw.groups(),AircraftModels.peek(info).part(nozzle.modelName))){
                    var p=nozzle.pos;var pose=new Matrix4f(draw.pose()).translate((float)p.x,(float)p.y,(float)p.z).rotateZ((float)Math.sin(time*2+ac.getId())*.002F).translate((float)-p.x,(float)-p.y,(float)-p.z);
                    render.draws.set(i,new AircraftPartPoses.Draw(pose,draw.groups()));count++;break;
                }
            }
        }
        render.draws.addAll(extra);MOVED.put(ac,count);
    }
    public static int moved(AircraftEntity ac){return MOVED.getOrDefault(ac,0);}
    public static void clear(){synchronized(CACHE){CACHE.clear();}MOVED.clear();}
}
