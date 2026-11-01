package io.github.mcheliceport.client.model;

import java.util.*;
import org.joml.Vector3f;

/** Explicit intact-body layouts, fitted once on the model loader; never projects across a gap. */
public final class AircraftMarkingMesh {
    public record Layout(float y,float z,float radius){}
    public record Patch(VehicleImpactSurface.Mesh mesh,int slot){}
    private static final Map<McheliModel.Group,List<Patch>> CACHE=new WeakHashMap<>();
    private static final Map<String,Layout> LAYOUTS=Map.of("f-35a",new Layout(1.95F,1.1F,.17F),
            "f-15e",new Layout(2.2F,3.1F,.19F),"a-10",new Layout(2.7F,.55F,.18F));
    private AircraftMarkingMesh(){}
    public static boolean supported(String name){return LAYOUTS.containsKey(name);}
    public static synchronized void clear(){CACHE.clear();}
    public static synchronized List<Patch> get(McheliModel.Group group){return CACHE.getOrDefault(group,List.of());}
    public static void prepare(String name,McheliModel model){
        var layout=LAYOUTS.get(name);if(layout==null)return;
        for(var group:model.part("$body")){
            var patches=build(group,layout);synchronized(AircraftMarkingMesh.class){if(CACHE.size()<32)CACHE.put(group,patches);}
        }
    }
    public static List<Patch> build(McheliModel.Group group,Layout layout){
        var index=VehicleImpactSurface.get(group);if(index==null)return List.of();
        var patches=new ArrayList<Patch>();
        for(int side:new int[]{-1,1})for(int slot=0;slot<5;slot++){
            // Glyphs run left-to-right in each side's own tangent, rather than mirroring the number.
            float z=layout.z+(slot<3?(1-slot)*side*layout.radius*1.5F:slot==3?-side*layout.radius*3.5F:0);
            float y=layout.y,r=slot==4?.48F:slot==3?layout.radius*1.25F:layout.radius;
            var origin=new Vector3f(side*16,y,z);var direction=new Vector3f(-side,0,0);
            var hit=index.ray(group.data(),origin,direction,32);
            if(hit==null)continue;
            var mesh=index.decal(group.data(),hit,origin,direction,r,0);
            if(!mesh.vertices().isEmpty()&&Math.abs(mesh.normal().x)>.65F)patches.add(new Patch(mesh,slot));
        }
        return List.copyOf(patches);
    }
}
