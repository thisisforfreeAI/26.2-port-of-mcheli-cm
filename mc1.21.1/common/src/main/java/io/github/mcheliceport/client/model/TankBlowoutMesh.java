package io.github.mcheliceport.client.model;

import java.util.List;
import java.util.WeakHashMap;
import org.joml.Vector3f;

/** Original-texture M1A2 bustle pressure panels, clipped from its authored roof, not its crew hatches. */
public final class TankBlowoutMesh {
    public record Panel(McheliModel.Group mesh,Vector3f hinge,int sign){}
    public record Parts(McheliModel.Group kept,List<Panel> panels,McheliModel.Group cavity,Vector3f vent){}
    private static final WeakHashMap<McheliModel.Group,Parts> CACHE=new WeakHashMap<>();
    private TankBlowoutMesh(){}
    public static Parts get(McheliModel.Group source){
        if(!CACHE.containsKey(source)){if(CACHE.size()>=32)CACHE.clear();CACHE.put(source,build(source));}return CACHE.get(source);
    }
    public static Parts build(McheliModel.Group source){
        if(source.vertexCount()<3||source.vertexCount()>12000)return null;
        // Stock M1A2 HBD5: separate pressure roof behind the crew hatches and ahead of the rear rack.
        var cut=BreakupMeshes.boxSplit(source,new float[]{-1.08F,2.65F,-3.22F},new float[]{1.01F,2.79F,-1.74F});
        if(cut[1].vertexCount()==0||BreakupMeshes.area(cut[1])<.5||BreakupMeshes.area(cut[1])>BreakupMeshes.area(source)*.15)return null;
        var halves=BreakupMeshes.split(cut[1],0,-.035F);
        if(halves[0].vertexCount()==0||halves[1].vertexCount()==0)return null;
        var cavity=new DetailBoxes();cavity.box(-1.07F,2.61F,-3.21F,1,2.625F,-1.75F);
        cavity.box(-1.08F,2.62F,-3.22F,-1.045F,2.67F,-1.74F);cavity.box(.975F,2.62F,-3.22F,1.01F,2.67F,-1.74F);
        return new Parts(cut[0],List.of(new Panel(halves[0],new Vector3f(-1.07F,2.67F,-2.48F),1),
                new Panel(halves[1],new Vector3f(1,2.67F,-2.48F),-1)),cavity.group("$m8_bustle_cavity"),new Vector3f(-.035F,2.7F,-2.48F));
    }
}
