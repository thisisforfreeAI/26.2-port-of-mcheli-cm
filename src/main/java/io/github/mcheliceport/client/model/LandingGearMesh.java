package io.github.mcheliceport.client.model;

import io.github.mcheliceport.content.aircraft.PlaneInfo;
import java.util.*;
import net.minecraft.world.phys.Vec3;

/** Small loader-built footprints of primary landing-gear groups. No render-thread mesh rebuilds. */
public final class LandingGearMesh {
    public record Shape(float bottom,List<Vec3> contacts){}
    private static final Map<McheliModel.Group,Shape> CACHE=new WeakHashMap<>();
    private LandingGearMesh(){}
    public static synchronized void clear(){CACHE.clear();}
    public static synchronized Shape get(McheliModel.Group group){return CACHE.get(group);}
    public static void prepare(PlaneInfo info,AircraftModels.VehicleModels models){
        for(int i=0;i<Math.min(8,info.landingGear.size());i++){
            var gear=info.landingGear.get(i);if(gear.hatch||gear.reverse)continue;
            for(var group:models.part(gear.modelName)){
                var shape=build(group);
                if(shape==null||shape.bottom>models.body().minY+.35F||gear.pos.y-shape.bottom<.5)continue;
                synchronized(LandingGearMesh.class){if(CACHE.size()<256)CACHE.put(group,shape);}
            }
        }
    }
    public static Shape build(McheliModel.Group group){
        var data=group.data();if(data.length==0||group.vertexCount()>20000)return null;
        float bottom=Float.POSITIVE_INFINITY;
        for(int i=0;i<data.length;i+=8){if(!Float.isFinite(data[i]+data[i+1]+data[i+2])||Math.abs(data[i])+Math.abs(data[i+1])+Math.abs(data[i+2])>128)return null;bottom=Math.min(bottom,data[i+1]);}
        // Low vertices form one footprint per wheel, including combined left/right assemblies.
        var clusters=new ArrayList<float[]>();
        for(int i=0;i<data.length;i+=8){
            if(data[i+1]>bottom+.06F)continue;
            float x=data[i],z=data[i+2];float[] found=null;
            for(var c:clusters){double dx=x-(c[0]+c[1])*.5,dz=z-(c[2]+c[3])*.5;if(dx*dx+dz*dz<1.25*1.25){found=c;break;}}
            if(found==null){if(clusters.size()>=4)return null;clusters.add(new float[]{x,x,z,z});}
            else{found[0]=Math.min(found[0],x);found[1]=Math.max(found[1],x);found[2]=Math.min(found[2],z);found[3]=Math.max(found[3],z);}
        }
        var contacts=new ArrayList<Vec3>();for(var c:clusters)contacts.add(new Vec3((c[0]+c[1])*.5,bottom,(c[2]+c[3])*.5));
        return contacts.isEmpty()?null:new Shape(bottom,List.copyOf(contacts));
    }
}
