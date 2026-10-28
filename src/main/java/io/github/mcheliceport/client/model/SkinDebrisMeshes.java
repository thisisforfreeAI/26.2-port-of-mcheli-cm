package io.github.mcheliceport.client.model;

import java.util.*;
import net.minecraft.world.phys.Vec3;

/** Tiny painted skin pieces from an actual cut boundary. Built once on the breakup loader. */
public final class SkinDebrisMeshes {
    public record Chip(McheliModel.Group skin,BreakupMeshes.Bounds bounds) {}
    private SkinDebrisMeshes() {}
    public static List<Chip> build(McheliModel.Group section,int axis,float cut,int outward) {
        if((axis!=0&&axis!=2)||(outward!=1&&outward!=-1)||!Float.isFinite(cut)
                ||section.vertexCount()<3||section.vertexCount()>9000)return List.of();
        var d=section.data();double low=Double.POSITIVE_INFINITY,high=Double.NEGATIVE_INFINITY;
        for(int i=0;i+7<d.length;i+=8)if(Math.abs(d[i+axis]-cut)<.002F){double u=d[i+(axis==0?2:0)];low=Math.min(low,u);high=Math.max(high,u);}
        if(!Double.isFinite(low)||high-low<.25)return List.of();
        var chips=new ArrayList<Chip>();
        for(int slot=0;slot<3;slot++) {
            double target=low+(high-low)*(.2+slot*.3);Vec3 anchor=null;double height=Double.NEGATIVE_INFINITY;
            for(int t=0;t+23<d.length;t+=24) {
                int a=-1,b=-1,count=0;
                for(int k=0;k<3;k++)if(Math.abs(d[t+k*8+axis]-cut)<.002F){if(count++==0)a=t+k*8;else b=t+k*8;}
                if(count!=2)continue;double u=d[a+(axis==0?2:0)],v=d[b+(axis==0?2:0)];
                if(Math.abs(v-u)<1E-8||target<Math.min(u,v)||target>Math.max(u,v))continue;
                double f=(target-u)/(v-u),y=d[a+1]+(d[b+1]-d[a+1])*f;
                if(y>height){height=y;anchor=axis==0?new Vec3(cut,y,target):new Vec3(target,y,cut);}
            }
            if(anchor==null)continue;
            double[] center={anchor.x,anchor.y,anchor.z};center[axis]+=outward*.10;
            float[] lo=new float[3],hi=new float[3];
            for(int k=0;k<3;k++){double half=k==1?.065:k==axis?.11:.16+slot*.015;lo[k]=(float)(center[k]-half);hi[k]=(float)(center[k]+half);}
            var skin=BreakupMeshes.boxSplit(section,lo,hi)[1];double area=BreakupMeshes.area(skin);
            if(skin.vertexCount()<3||skin.vertexCount()>96||area<.002||area>.25)continue;
            var bounds=BreakupMeshes.bounds(skin);boolean overlaps=false;
            for(var chip:chips)if(chip.bounds.center().distanceSquared(bounds.center())<.04F)overlaps=true;
            if(!overlaps)chips.add(new Chip(skin,bounds));
        }
        return List.copyOf(chips);
    }
}
