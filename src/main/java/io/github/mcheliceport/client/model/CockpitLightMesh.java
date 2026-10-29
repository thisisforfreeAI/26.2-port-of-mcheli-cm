package io.github.mcheliceport.client.model;

import io.github.mcheliceport.content.aircraft.AircraftInfo;
import java.util.*;

/** Small, UV-preserving instrument-area overlays, clipped only on the existing model loader. */
public final class CockpitLightMesh {
    private static final Map<McheliModel.Group,Map<AircraftInfo.FlightLamp,List<McheliModel.Group>>> CACHE=new WeakHashMap<>();
    private CockpitLightMesh(){}
    public static synchronized void clear(){CACHE.clear();}
    public static synchronized List<McheliModel.Group> get(McheliModel.Group group,AircraftInfo.FlightLamp lamp){var lights=CACHE.get(group);return lights==null?List.of():lights.getOrDefault(lamp,List.of());}
    public static void prepare(AircraftInfo info,AircraftModels.VehicleModels models){
        int count=0;for(var lamp:info.flightLamps){
            if(lamp.kind()!=AircraftInfo.FlightLampKind.COCKPIT||count++>=2)continue;
            for(var group:models.part(lamp.part())){
                if(group.vertexCount()>60000)continue;
                var clipped=build(group,lamp);
                synchronized(CockpitLightMesh.class){if(CACHE.size()<64)CACHE.computeIfAbsent(group,k->new HashMap<>()).put(lamp,clipped);}
            }
        }
    }
    public static List<McheliModel.Group> build(McheliModel.Group group,AircraftInfo.FlightLamp lamp){
        var p=lamp.position();float r=lamp.radius();
        var clipped=BreakupMeshes.boxSplit(group,new float[]{(float)p.x-r,(float)p.y-r,(float)p.z-r},new float[]{(float)p.x+r,(float)p.y+r,(float)p.z+r})[1];
        if(clipped.vertexCount()==0||clipped.vertexCount()>8192)return List.of();
        var data=clipped.data().clone();for(int i=0;i<data.length;i+=8)for(int axis=0;axis<3;axis++)data[i+axis]+=data[i+5+axis]*.0015F;
        return List.of(new McheliModel.Group("$cockpit_light",data));
    }
}
