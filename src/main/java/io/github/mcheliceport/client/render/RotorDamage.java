package io.github.mcheliceport.client.render;

import io.github.mcheliceport.client.model.*;
import io.github.mcheliceport.config.*;
import io.github.mcheliceport.content.aircraft.HeliInfo;
import io.github.mcheliceport.entity.heli.HeliEntity;
import java.util.*;
import java.util.concurrent.*;
import org.joml.Matrix4f;
import org.joml.Vector3f;

/** Eight cached rotor groups, four pending jobs, no mesh copies or builds during drawing. */
public final class RotorDamage {
    private record Key(McheliModel.Group group,float x,float y,float z,float ax,float ay,float az){}
    private record Pair(RotorDamageMesh.Variant bent,RotorDamageMesh.Variant chipped){}
    private static final Map<Key,CompletableFuture<Pair>> CACHE=new LinkedHashMap<>(16,.75F,true);
    private static final Map<HeliEntity,Integer> APPLIED=Collections.synchronizedMap(new WeakHashMap<>());
    private static final ExecutorService LOADER=Executors.newSingleThreadExecutor(r->{var t=new Thread(r,"mcheli-rotor-damage");t.setDaemon(true);return t;});
    private static int pending;
    private RotorDamage(){}
    private static Pair variants(McheliModel.Group group,Vector3f hub,Vector3f axis){
        var key=new Key(group,hub.x,hub.y,hub.z,axis.x,axis.y,axis.z);CompletableFuture<Pair> future;
        synchronized(CACHE){
            future=CACHE.get(key);
            if(future==null){
                if(pending>=4||group.vertexCount()>24000)return null;
                if(CACHE.size()>=8){var it=CACHE.entrySet().iterator();boolean removed=false;while(it.hasNext())if(it.next().getValue().isDone()){it.remove();removed=true;break;}if(!removed)return null;}
                future=new CompletableFuture<>();CACHE.put(key,future);pending++;var result=future;
                LOADER.execute(()->{Pair pair=null;try{
                    var bent=RotorDamageMesh.build(group,hub,axis,false);var chipped=RotorDamageMesh.build(group,hub,axis,true);
                    pair=new Pair(bent,chipped);
                    for(var variant:new RotorDamageMesh.Variant[]{bent,chipped})if(variant!=null){
                        var model=new McheliModel("rotor-damage",List.of(variant.group()),0,0,0,0,0,0);
                        VehicleSurfaceShade.prepare(model);VehicleImpactSurface.prepare(model);
                    }
                }finally{result.complete(pair);synchronized(CACHE){pending--;}}});
            }
        }
        return future.getNow(null);
    }
    public static void apply(HeliEntity entity,HeliInfo info,AircraftModels.VehicleModels models,AircraftPartPoses.Input input,List<AircraftPartPoses.Draw> draws){
        APPLIED.put(entity,0);
        if(!EffectQuality.enhanced()||!ClientConfig.get(ClientConfig.VEHICLE_DAMAGE_DETAILS)||entity.getMaxHP()<=0)return;
        float damage=1-(float)entity.getHP()/entity.getMaxHP();int stage=RotorDamageMesh.stage(damage);if(stage==0)return;
        var camera=net.minecraft.client.Minecraft.getInstance().gameRenderer.mainCamera();if(camera.position().distanceToSqr(entity.position())>48*48)return;
        int applied=0;
        for(int r=0;r<Math.min(8,info.rotorList.size());r++){
            var rotor=info.rotorList.get(r);var groups=models.part(rotor.modelName);if(groups.isEmpty()||rotor.rot.lengthSqr()<.01)continue;
            var hub=rotor.oldRenderMethod?new Vector3f():new Vector3f((float)rotor.pos.x,(float)rotor.pos.y,(float)rotor.pos.z);
            var axis=new Vector3f((float)rotor.rot.x,(float)rotor.rot.y,(float)rotor.rot.z).normalize();
            int n=r<entity.getRotors().length?entity.getRotors()[r].getBladeNum():1,instance=Math.floorMod(entity.getId()+r,Math.max(1,n)),seen=0;
            for(int i=1;i<draws.size();i++){
                var draw=draws.get(i);if(Collections.disjoint(draw.groups(),groups))continue;
                if(seen++!=instance)continue;
                var damaged=new ArrayList<McheliModel.Group>();boolean changed=false;
                for(var group:draw.groups()){
                    var pair=groups.contains(group)?variants(group,hub,axis):null;
                    var variant=pair==null?null:stage==2?pair.chipped:pair.bent;
                    if(variant==null){damaged.add(group);continue;}damaged.add(variant.group());changed|=variant.bentVertices()>0;
                }
                if(changed){
                    Vector3f tilt=Math.abs(axis.y)>.8F?new Vector3f(1,0,0):new Vector3f(0,1,0);
                    float wobble=RotorDamageMesh.wobble(damage,entity.level().getGameTime()+input.tickTime(),entity.getId());
                    var pose=new Matrix4f(draw.pose()).translate(hub).rotate(wobble,tilt.x,tilt.y,tilt.z).translate(-hub.x,-hub.y,-hub.z);
                    draws.set(i,new AircraftPartPoses.Draw(pose,List.copyOf(damaged)));applied++;
                }
            }
        }
        APPLIED.put(entity,applied);
    }
    public static int applied(HeliEntity entity){return APPLIED.getOrDefault(entity,0);}
    public static void clear(){synchronized(CACHE){CACHE.clear();}APPLIED.clear();}
}
