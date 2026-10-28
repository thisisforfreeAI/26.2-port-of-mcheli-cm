package io.github.mcheliceport.client.model;

import java.util.ArrayList;
import java.util.List;
import net.minecraft.world.phys.Vec3;

/** Small UV-preserving trailing panels. Built only on BreakupMeshes' bounded worker. */
public final class ControlSurfaceMeshes {
    public record Box(Vec3 low,Vec3 high) {}
    public record Panel(int slot,int airframeBit,McheliModel.Group group,Vec3 pivot,Vec3 axis,Box box) {}
    public record Model(McheliModel.Group kept,List<Panel> panels) {}
    private ControlSurfaceMeshes() {}

    public static Model build(McheliModel.Group source,BreakupMeshes.Airframe layout) {
        var boxes=new Box[6];var bounds=layout.bounds();
        float midY=bounds.low().y+(bounds.high().y-bounds.low().y)*.6F;
        float outer=Math.max(Math.abs(bounds.low().x),Math.abs(bounds.high().x))*.30F;
        for(int side=0;side<2;side++) {
            final int sign=side==0?-1:1;
            var wing=horizontal(layout.sections().get(side),-Float.MAX_VALUE,Float.MAX_VALUE,0,sign);
            if(valid(wing,.35,.5)) {
                double width=wing.high.x-wing.low.x,chord=wing.high.z-wing.low.z;
                boxes[side]=new Box(new Vec3(wing.low.x+(side==1?width*.12:-.02),wing.low.y-.06,wing.low.z-.02),
                        new Vec3(wing.high.x+(side==0?-width*.12:.02),wing.high.y+.06,wing.low.z+chord*.24));
            }
            var rudder=vertical(layout.sections().get(2),midY,sign);
            if(valid(rudder,-.00001,.35)&&rudder.high.y-rudder.low.y>.3) {
                boxes[2+side]=new Box(new Vec3(rudder.low.x-.04,rudder.low.y,rudder.low.z-.02),
                        new Vec3(rudder.high.x+.04,rudder.high.y+.04,rudder.low.z+(rudder.high.z-rudder.low.z)*.25));
            }
            var elevator=horizontal(layout.sections().get(2),-Float.MAX_VALUE,midY-.02F,outer,sign);
            if(valid(elevator,.3,.4)) {
                boxes[4+side]=new Box(new Vec3(elevator.low.x-.02,elevator.low.y-.04,elevator.low.z-.02),
                        new Vec3(elevator.high.x+.02,elevator.high.y+.04,elevator.low.z+(elevator.high.z-elevator.low.z)*.28));
            }
        }
        // A single narrow central fin must remain one panel, including both textured sides.
        if(boxes[2]!=null&&boxes[3]!=null&&boxes[2].low.x>-.4&&boxes[3].high.x<.4) {
            Box a=boxes[2],b=boxes[3];
            boxes[2]=new Box(new Vec3(Math.min(a.low.x,b.low.x),Math.min(a.low.y,b.low.y),Math.min(a.low.z,b.low.z)),
                    new Vec3(Math.max(a.high.x,b.high.x),Math.max(a.high.y,b.high.y),Math.max(a.high.z,b.high.z)));
            boxes[3]=null;
        }
        McheliModel.Group kept=source;var panels=new ArrayList<Panel>();
        double total=BreakupMeshes.area(source),removed=0;int panelVertices=0;
        int vertexCap=Math.min(72000,source.vertexCount()+Math.max(4096,source.vertexCount()/3));
        for(int slot=0;slot<boxes.length;slot++) {
            Box box=boxes[slot];if(box==null)continue;
            var split=split(kept,box);double area=BreakupMeshes.area(split[1]);
            if(split[1].vertexCount()<3||split[1].vertexCount()>1200||area<.025||area>total*.09||removed+area>total*.22)continue;
            if(split[0].vertexCount()+panelVertices+split[1].vertexCount()>vertexCap)continue;
            var piece=new McheliModel.Group(source.name(),split[1].data());
            var actual=BreakupMeshes.bounds(piece);
            Vec3 pivot=new Vec3((actual.low().x+actual.high().x)*.5,(actual.low().y+actual.high().y)*.5,box.high.z);
            panels.add(new Panel(slot,slot<2?1<<slot:4,piece,pivot,slot==2||slot==3?new Vec3(0,1,0):new Vec3(1,0,0),box));
            kept=split[0];removed+=area;panelVertices+=piece.vertexCount();
        }
        if(panels.isEmpty())return new Model(source,List.of());
        BreakupMeshes.prepare(kept);for(var p:panels)BreakupMeshes.prepare(p.group);
        return new Model(kept,List.copyOf(panels));
    }
    /** A whole-wing fragment must not regrow a control panel it already shed. */
    static McheliModel.Group strip(McheliModel.Group source,Model profile,int missing) {
        McheliModel.Group kept=source;
        for(var panel:profile.panels)if((missing&(1<<panel.slot))!=0)kept=split(kept,panel.box)[0];
        if(kept!=source)BreakupMeshes.prepare(kept);
        return kept;
    }
    private static McheliModel.Group[] split(McheliModel.Group source,Box box) {
        return BreakupMeshes.boxSplit(source,new float[]{(float)box.low.x,(float)box.low.y,(float)box.low.z},
                new float[]{(float)box.high.x,(float)box.high.y,(float)box.high.z});
    }
    private static boolean valid(Box box,double width,double chord) {
        return box!=null&&box.high.x-box.low.x>width&&box.high.z-box.low.z>chord;
    }
    private static Box horizontal(McheliModel.Group group,float minY,float maxY,float outer,int sign) {
        return bounds(group,minY,maxY,outer,sign,false);
    }
    private static Box vertical(McheliModel.Group group,float minY,int sign) {
        return bounds(group,minY,Float.MAX_VALUE,0,sign,true);
    }
    private static Box bounds(McheliModel.Group group,float minY,float maxY,float outer,int sign,boolean vertical) {
        double lx=Double.POSITIVE_INFINITY,ly=lx,lz=lx,hx=Double.NEGATIVE_INFINITY,hy=hx,hz=hx;int count=0;
        var d=group.data();
        for(int i=0;i+7<d.length;i+=8) {
            int triangle=i/24*24;
            if((d[triangle]+d[triangle+8]+d[triangle+16])/3*sign<=outer)continue;
            if(vertical) {
                if((d[triangle+1]+d[triangle+9]+d[triangle+17])/3<minY)continue;
            }else if(d[i+1]<minY||d[i+1]>maxY)continue;
            if(vertical?(Math.abs(d[i+6])>.6F||Math.abs(d[i+5])<.4F):Math.abs(d[i+6])<.55F)continue;
            lx=Math.min(lx,d[i]);ly=Math.min(ly,d[i+1]);lz=Math.min(lz,d[i+2]);
            hx=Math.max(hx,d[i]);hy=Math.max(hy,d[i+1]);hz=Math.max(hz,d[i+2]);count++;
        }
        return count<3?null:new Box(new Vec3(sign>0?Math.max(outer,lx):lx,Math.max(minY,ly),lz),
                new Vec3(sign<0?Math.min(-outer,hx):hx,Math.min(maxY,hy),hz));
    }
}
