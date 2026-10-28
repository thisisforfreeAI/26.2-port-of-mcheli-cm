package io.github.mcheliceport.client.model;

import it.unimi.dsi.fastutil.floats.FloatArrayList;
import java.util.*;
import net.minecraft.world.phys.Vec3;

/** Closed cut contours, small spars and wires. Pure bounded work on the existing mesh loader. */
public final class FractureInteriorMeshes {
    public record Details(McheliModel.Group cavity,McheliModel.Group ribs,McheliModel.Group cables,int contours) {
        public int vertices(){return cavity.vertexCount()+ribs.vertexCount()+cables.vertexCount();}
    }
    private record Key(long u,long v) {}
    private static final int NODES=128;
    private FractureInteriorMeshes() {}
    private static McheliModel.Group empty(String name){return new McheliModel.Group(name,new float[0]);}
    public static Details empty(){return new Details(empty("$fracture_cavity"),empty("$fracture_ribs"),empty("$fracture_cables"),0);}

    public static Details build(McheliModel.Group frame,int axis,float cut,int outward) {
        if(axis!=0&&axis!=2||outward!=1&&outward!=-1)return empty();
        var ids=new HashMap<Key,Integer>();var points=new ArrayList<Vec3>();var edges=new ArrayList<Set<Integer>>();
        var d=frame.data();int segments=0;
        for(int t=0;t+23<d.length;t+=24) {
            int[] pair=new int[3];int count=0;
            for(int k=0;k<3;k++)if(Math.abs(d[t+k*8+axis]-cut)<.002F)pair[count++]=t+k*8;
            if(count!=2)continue;int[] nodes=new int[2];
            for(int k=0;k<2;k++) {
                int at=pair[k];double u=axis==0?d[at+2]:d[at],v=d[at+1];
                if(!Double.isFinite(u)||!Double.isFinite(v))return empty();
                var key=new Key(Math.round(u*1000),Math.round(v*1000));Integer id=ids.get(key);
                if(id==null){if(points.size()>=NODES)return empty();id=points.size();ids.put(key,id);
                    points.add(axis==0?new Vec3(cut,key.v*.001,key.u*.001):new Vec3(key.u*.001,key.v*.001,cut));edges.add(new LinkedHashSet<>());}
                nodes[k]=id;
            }
            if(nodes[0]!=nodes[1]&&edges.get(nodes[0]).add(nodes[1])){
                if(++segments>256)return empty();edges.get(nodes[1]).add(nodes[0]);}
        }
        if(points.size()<2)return empty();
        var caps=new FloatArrayList();boolean[] seen=new boolean[points.size()];int contours=0;
        for(int start=0;start<points.size();start++)if(!seen[start]) {
            var component=new ArrayList<Integer>();var pending=new ArrayDeque<Integer>();pending.add(start);seen[start]=true;boolean closed=true;
            while(!pending.isEmpty()){
                int at=pending.removeFirst();component.add(at);closed&=edges.get(at).size()==2;
                for(int next:edges.get(at))if(!seen[next]){seen[next]=true;pending.add(next);}
            }
            if(!closed||component.size()<3)continue;
            var polygon=new ArrayList<Vec3>();int previous=-1,current=start;
            do {
                polygon.add(points.get(current));int next=-1;for(int n:edges.get(current))if(n!=previous){next=n;break;}
                previous=current;current=next;
            }while(current>=0&&current!=start&&polygon.size()<=component.size());
            if(current!=start||polygon.size()!=component.size())continue;
            var triangles=triangulate(polygon,axis);
            if(triangles.isEmpty())continue;contours++;
            double minU=polygon.stream().mapToDouble(p->tangent(p,axis)).min().orElse(0),maxU=polygon.stream().mapToDouble(p->tangent(p,axis)).max().orElse(1);
            double minV=polygon.stream().mapToDouble(p->p.y).min().orElse(0),maxV=polygon.stream().mapToDouble(p->p.y).max().orElse(1);
            for(var tri:triangles)triangle(caps,polygon.get(tri[0]),polygon.get(tri[1]),polygon.get(tri[2]),axis,cut-outward*.025F,outward,minU,maxU,minV,maxV);
        }
        var ribs=new DetailBoxes();var cables=new FloatArrayList();
        double low=points.stream().mapToDouble(p->tangent(p,axis)).min().orElse(0),high=points.stream().mapToDouble(p->tangent(p,axis)).max().orElse(0);
        int count=Math.min(5,points.size());
        for(int i=0;i<count;i++) {
            Vec3 p=surface(points,edges,axis,low+(high-low)*(i+1)/(count+1));float length=.10F+.025F*(i%4);
            if(axis==0)ribs.box((float)Math.min(cut-outward*.025,cut+outward*length),(float)p.y-.018F,(float)p.z-.026F,
                    (float)Math.max(cut-outward*.025,cut+outward*length),(float)p.y+.018F,(float)p.z+.026F);
            else ribs.box((float)p.x-.026F,(float)p.y-.018F,(float)Math.min(cut-outward*.025,cut+outward*length),
                    (float)p.x+.026F,(float)p.y+.018F,(float)Math.max(cut-outward*.025,cut+outward*length));
        }
        Vec3 normal=axis==0?new Vec3(outward,0,0):new Vec3(0,0,outward),along=axis==0?new Vec3(0,0,1):new Vec3(1,0,0);
        for(int i=0;i<2;i++) {
            Vec3 p=surface(points,edges,axis,low+(high-low)*(i==0?.32:.70));
            Vec3 a=p.add(normal.scale(.045)),b=p.add(normal.scale(.11)).add(0,-.11,0),c=p.add(normal.scale(.15)).add(along.scale((i==0?-1:1)*.025)).add(0,-.29,0),end=p.add(normal.scale(.11)).add(along.scale((i==0?-1:1)*.06)).add(0,-.46,0);
            cable(cables,a,b,normal);cable(cables,b,c,normal);cable(cables,c,end,normal);
        }
        return new Details(new McheliModel.Group("$fracture_cavity",caps.toFloatArray()),ribs.group("$fracture_ribs"),
                new McheliModel.Group("$fracture_cables",cables.toFloatArray()),contours);
    }
    private static double tangent(Vec3 p,int axis){return axis==0?p.z:p.x;}
    private static Vec3 surface(List<Vec3> points,List<Set<Integer>> edges,int axis,double target) {
        Vec3 best=points.getFirst(),hit=null;double closest=Double.POSITIVE_INFINITY,height=Double.NEGATIVE_INFINITY;
        for(int i=0;i<points.size();i++) {
            Vec3 a=points.get(i);double u=tangent(a,axis),dist=Math.abs(u-target);
            if(dist<closest){best=a;closest=dist;}
            for(int j:edges.get(i))if(j>i){Vec3 b=points.get(j);double v=tangent(b,axis);
                if(Math.abs(v-u)<1.0E-9||target<Math.min(u,v)||target>Math.max(u,v))continue;
                double t=(target-u)/(v-u),y=a.y+(b.y-a.y)*t;
                if(y>height){height=y;hit=axis==0?new Vec3(a.x,y,target):new Vec3(target,y,a.z);}
            }
        }
        return hit==null?best:hit;
    }
    private static double cross(Vec3 a,Vec3 b,Vec3 c,int axis){return (tangent(b,axis)-tangent(a,axis))*(c.y-a.y)-(b.y-a.y)*(tangent(c,axis)-tangent(a,axis));}
    private static List<int[]> triangulate(List<Vec3> polygon,int axis) {
        var indices=new ArrayList<Integer>();for(int i=0;i<polygon.size();i++)indices.add(i);
        boolean changed=true;while(changed&&indices.size()>3){changed=false;for(int i=0;i<indices.size();i++){
            int n=indices.size();if(Math.abs(cross(polygon.get(indices.get((i+n-1)%n)),polygon.get(indices.get(i)),polygon.get(indices.get((i+1)%n)),axis))<1.0E-8){indices.remove(i);changed=true;break;}
        }}
        double area=0;for(int i=0;i<indices.size();i++){Vec3 a=polygon.get(indices.get(i)),b=polygon.get(indices.get((i+1)%indices.size()));area+=tangent(a,axis)*b.y-a.y*tangent(b,axis);}
        if(Math.abs(area)<1.0E-8)return List.of();if(area<0)Collections.reverse(indices);
        var result=new ArrayList<int[]>();
        while(indices.size()>3) {
            boolean found=false;for(int i=0;i<indices.size();i++) {
                int n=indices.size(),a=indices.get((i+n-1)%n),b=indices.get(i),c=indices.get((i+1)%n);
                if(cross(polygon.get(a),polygon.get(b),polygon.get(c),axis)<=1.0E-9)continue;
                boolean occupied=false;for(int p:indices)if(p!=a&&p!=b&&p!=c&&cross(polygon.get(a),polygon.get(b),polygon.get(p),axis)>=-1.0E-9
                        &&cross(polygon.get(b),polygon.get(c),polygon.get(p),axis)>=-1.0E-9&&cross(polygon.get(c),polygon.get(a),polygon.get(p),axis)>=-1.0E-9){occupied=true;break;}
                if(!occupied){result.add(new int[]{a,b,c});indices.remove(i);found=true;break;}
            }
            if(!found)return List.of();
        }
        if(indices.size()==3)result.add(new int[]{indices.get(0),indices.get(1),indices.get(2)});return result;
    }
    private static void triangle(FloatArrayList out,Vec3 a,Vec3 b,Vec3 c,int axis,double cut,int outward,double minU,double maxU,double minV,double maxV) {
        Vec3 normal=axis==0?new Vec3(outward,0,0):new Vec3(0,0,outward);
        if(b.subtract(a).cross(c.subtract(a)).dot(normal)<0){Vec3 swap=b;b=c;c=swap;}
        for(Vec3 p:new Vec3[]{a,b,c})vertex(out,axis==0?new Vec3(cut,p.y,p.z):new Vec3(p.x,p.y,cut),
                (tangent(p,axis)-minU)/Math.max(.001,maxU-minU),(p.y-minV)/Math.max(.001,maxV-minV),normal);
    }
    private static void vertex(FloatArrayList out,Vec3 p,double u,double v,Vec3 normal){
        out.add((float)p.x);out.add((float)p.y);out.add((float)p.z);out.add((float)u);out.add((float)v);out.add((float)normal.x);out.add((float)normal.y);out.add((float)normal.z);
    }
    private static void cable(FloatArrayList out,Vec3 a,Vec3 b,Vec3 normal) {
        Vec3 direction=b.subtract(a).normalize(),u=normal.cross(direction).normalize().scale(.012),v=direction.cross(u).normalize().scale(.012);
        Vec3[] lo={a.add(u).add(v),a.subtract(u).add(v),a.subtract(u).subtract(v),a.add(u).subtract(v)};
        Vec3 delta=b.subtract(a);
        for(int side=0;side<4;side++) {
            Vec3 p=lo[side],q=lo[(side+1)%4],r=q.add(delta),s=p.add(delta),face=q.subtract(p).cross(r.subtract(p)).normalize();
            vertex(out,p,0,0,face);vertex(out,q,1,0,face);vertex(out,r,1,1,face);vertex(out,p,0,0,face);vertex(out,r,1,1,face);vertex(out,s,0,1,face);
        }
    }
    public static Details merge(List<Details> entries) {
        return new Details(join("$fracture_cavity",entries.stream().map(Details::cavity).toList()),join("$fracture_ribs",entries.stream().map(Details::ribs).toList()),
                join("$fracture_cables",entries.stream().map(Details::cables).toList()),entries.stream().mapToInt(Details::contours).sum());
    }
    private static McheliModel.Group join(String name,List<McheliModel.Group> groups){var data=new FloatArrayList();for(var g:groups)data.addElements(data.size(),g.data());return new McheliModel.Group(name,data.toFloatArray());}
}
