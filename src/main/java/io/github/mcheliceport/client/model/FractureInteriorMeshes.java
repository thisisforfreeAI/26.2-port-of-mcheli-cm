package io.github.mcheliceport.client.model;

import it.unimi.dsi.fastutil.floats.FloatArrayList;
import java.util.*;
import net.minecraft.world.phys.Vec3;

/**
 * Cut caps, inner skin lining, small spars and wires. Pure bounded work on the existing mesh loader.
 * Every contour is capped: closed ones are triangulated exactly, open or very detailed ones get a
 * row-hull bulkhead, so no torn section shows the culled, see-through inside of its skin.
 */
public final class FractureInteriorMeshes {
    public record Details(McheliModel.Group cavity,McheliModel.Group ribs,McheliModel.Group cables,int contours) {
        public int vertices(){return cavity.vertexCount()+ribs.vertexCount()+cables.vertexCount();}
    }
    /** What one side of a cut needs to read as solid: a darkened inner skin and the bulkhead cap. */
    public record Shell(McheliModel.Group lining,McheliModel.Group cap) {
        public int vertices(){return lining.vertexCount()+cap.vertexCount();}
    }
    private record Key(long u,long v) {}
    private static final int NODES=4096,SEGMENTS=8192,EXACT=192,ROWS=64,CAP=6144;
    private static final float INSET=.012F;
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
            if(count!=2)continue;if(points.size()+2>NODES)break;int[] nodes=new int[2];
            for(int k=0;k<2;k++) {
                int at=pair[k];double u=axis==0?d[at+2]:d[at],v=d[at+1];
                if(!Double.isFinite(u)||!Double.isFinite(v))return empty();
                var key=new Key(Math.round(u*1000),Math.round(v*1000));Integer id=ids.get(key);
                if(id==null){id=points.size();ids.put(key,id);
                    points.add(axis==0?new Vec3(cut,key.v*.001,key.u*.001):new Vec3(key.u*.001,key.v*.001,cut));edges.add(new LinkedHashSet<>());}
                nodes[k]=id;
            }
            if(nodes[0]!=nodes[1]&&edges.get(nodes[0]).add(nodes[1])){
                edges.get(nodes[1]).add(nodes[0]);if(++segments>=SEGMENTS)break;}
        }
        if(points.size()<2)return empty();
        var caps=new FloatArrayList();boolean[] seen=new boolean[points.size()];int contours=0;
        for(int start=0;start<points.size()&&caps.size()<CAP*8;start++)if(!seen[start]) {
            var component=new ArrayList<Integer>();var pending=new ArrayDeque<Integer>();pending.add(start);seen[start]=true;boolean closed=true;
            while(!pending.isEmpty()){
                int at=pending.removeFirst();component.add(at);closed&=edges.get(at).size()==2;
                for(int next:edges.get(at))if(!seen[next]){seen[next]=true;pending.add(next);}
            }
            if(component.size()<2)continue;
            // Nested contours (skin and cockpit tub) sit on slightly different planes instead of z-fighting.
            double plane=cut-outward*(.025+.003*(contours%6));int before=caps.size();
            if(closed&&component.size()>=3&&component.size()<=EXACT){
                var polygon=new ArrayList<Vec3>();int previous=-1,current=start;
                do {
                    polygon.add(points.get(current));int next=-1;for(int n:edges.get(current))if(n!=previous){next=n;break;}
                    previous=current;current=next;
                }while(current>=0&&current!=start&&polygon.size()<=component.size());
                if(current==start&&polygon.size()==component.size())
                    for(var tri:triangulate(polygon,axis))triangle(caps,polygon.get(tri[0]),polygon.get(tri[1]),polygon.get(tri[2]),axis,plane,outward);
            }
            if(caps.size()==before)rows(caps,points,edges,component,axis,plane,outward);
            if(caps.size()>before)contours++;
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
    /** World-scaled UVs, so the bulkhead texture keeps one density from a slim fin to a wide fuselage. */
    private static void triangle(FloatArrayList out,Vec3 a,Vec3 b,Vec3 c,int axis,double cut,int outward) {
        Vec3 normal=axis==0?new Vec3(outward,0,0):new Vec3(0,0,outward);
        if(b.subtract(a).cross(c.subtract(a)).dot(normal)<0){Vec3 swap=b;b=c;c=swap;}
        else if(b.subtract(a).cross(c.subtract(a)).lengthSqr()<1.0E-12)return;
        for(Vec3 p:new Vec3[]{a,b,c})vertex(out,axis==0?new Vec3(cut,p.y,p.z):new Vec3(p.x,p.y,cut),tangent(p,axis)*.5,p.y*.5,normal);
    }
    /**
     * Fallback cap for open, self-touching or very detailed contours. Each scan line is filled
     * even-odd between its crossings, so twin fins or a cockpit gap stay open; a line with an odd
     * count (an open contour) falls back to its outermost span. Rows and columns are both tried and
     * the more consistent one wins, so a C-shaped wing contour (its rear cut by the tail plane)
     * still fills between its flat upper and lower skins.
     */
    private static void rows(FloatArrayList out,List<Vec3> points,List<Set<Integer>> edges,List<Integer> component,int axis,double cut,int outward) {
        var across=new FloatArrayList();var along=new FloatArrayList();
        int a=scan(across,points,edges,component,axis,cut,outward,false),b=scan(along,points,edges,component,axis,cut,outward,true);
        var best=b>a||b==a&&area(along)>area(across)?along:across;out.addElements(out.size(),best.toFloatArray());
    }
    /** Emits one orientation and returns how many of its lines had a clean, even set of crossings. */
    private static int scan(FloatArrayList out,List<Vec3> points,List<Set<Integer>> edges,List<Integer> component,int axis,double cut,int outward,boolean columns) {
        double low=Double.POSITIVE_INFINITY,high=Double.NEGATIVE_INFINITY;
        for(int i:component){double s=line(points.get(i),axis,columns);low=Math.min(low,s);high=Math.max(high,s);}
        if(!(high-low>.01))return 0;
        int rows=Math.clamp((int)Math.ceil((high-low)/.04),2,ROWS),clean=0;var spans=new ArrayList<double[]>();var heights=new double[rows+1];
        for(int r=0;r<=rows;r++) {
            double v=Math.clamp(low+(high-low)*r/rows,low+1.0E-4,high-1.0E-4),min=Double.POSITIVE_INFINITY,max=Double.NEGATIVE_INFINITY;
            var crossings=new it.unimi.dsi.fastutil.doubles.DoubleArrayList();heights[r]=v;
            for(int i:component){Vec3 a=points.get(i);for(int j:edges.get(i))if(j>i){Vec3 b=points.get(j);
                double sa=line(a,axis,columns),sb=line(b,axis,columns),ua=span(a,axis,columns),ub=span(b,axis,columns);
                if(v<Math.min(sa,sb)||v>Math.max(sa,sb))continue;
                if(Math.abs(sb-sa)<1.0E-9){min=Math.min(min,Math.min(ua,ub));max=Math.max(max,Math.max(ua,ub));continue;}
                double u=ua+(ub-ua)*(v-sa)/(sb-sa);min=Math.min(min,u);max=Math.max(max,u);
                if(sa<=v&&v<sb||sb<=v&&v<sa)crossings.add(u);
            }}
            crossings.sort(null);
            for(int k=crossings.size()-1;k>0;k--)if(crossings.getDouble(k)-crossings.getDouble(k-1)<1.0E-4){crossings.removeDouble(k);crossings.removeDouble(k-1);k--;}
            if(crossings.size()>=2&&crossings.size()%2==0){clean++;var row=new double[crossings.size()];for(int k=0;k<row.length;k++)row[k]=crossings.getDouble(k);spans.add(row);}
            else spans.add(min<=max?new double[]{min,max}:null);
        }
        for(int r=0;r<rows;r++) {
            var a=spans.get(r);var b=spans.get(r+1);if(a==null||b==null)continue;
            if(a.length==b.length)for(int k=0;k+1<a.length;k+=2)quad(out,axis,cut,outward,columns,a[k],a[k+1],heights[r],b[k],b[k+1],heights[r+1]);
            else {
                // Topology changes between lines (a fin begins): step each span to the midline instead of guessing a join.
                double mid=(heights[r]+heights[r+1])*.5;
                for(int k=0;k+1<a.length;k+=2)quad(out,axis,cut,outward,columns,a[k],a[k+1],heights[r],a[k],a[k+1],mid);
                for(int k=0;k+1<b.length;k+=2)quad(out,axis,cut,outward,columns,b[k],b[k+1],mid,b[k],b[k+1],heights[r+1]);
            }
        }
        return clean;
    }
    private static void quad(FloatArrayList out,int axis,double cut,int outward,boolean columns,double a0,double a1,double va,double b0,double b1,double vb) {
        if(a1-a0<1.0E-4&&b1-b0<1.0E-4)return;
        Vec3 p=at(axis,a0,va,columns),q=at(axis,a1,va,columns),s=at(axis,b1,vb,columns),t=at(axis,b0,vb,columns);
        triangle(out,p,q,s,axis,cut,outward);triangle(out,p,s,t,axis,cut,outward);
    }
    private static double line(Vec3 p,int axis,boolean columns){return columns?tangent(p,axis):p.y;}
    private static double span(Vec3 p,int axis,boolean columns){return columns?p.y:tangent(p,axis);}
    private static double area(FloatArrayList d){return BreakupMeshes.area(new McheliModel.Group("$cap",d.toFloatArray()));}
    private static Vec3 at(int axis,double span,double line,boolean columns){return columns?at(axis,line,span):at(axis,span,line);}
    private static Vec3 at(int axis,double u,double v){return axis==0?new Vec3(0,v,u):new Vec3(u,v,0);}

    /** Both sides of a cut: inner lining plus a cap facing {@code outward} along the cut axis. */
    public static Shell shell(McheliModel.Group group,int axis,float cut,int outward,int limit) {
        if(axis!=0&&axis!=2||outward!=1&&outward!=-1||!Float.isFinite(cut))return new Shell(empty("$fracture_lining"),empty("$fracture_cavity"));
        return new Shell(lining(group,axis,cut,limit),build(group,axis,cut,outward).cavity());
    }
    /**
     * The skin near a cut, mirrored to face inward and pulled a hair inside. Models are single-sided
     * and drawn culled, so without it a torn wing or tail shows straight through its own far wall.
     * The depth shrinks until the copy fits {@code limit} vertices; the source stays untouched.
     */
    public static McheliModel.Group lining(McheliModel.Group group,int axis,float cut,int limit) {
        var d=group.data();float depth=2.5F;int count;
        while(true){
            count=0;for(int t=0;t+23<d.length;t+=24)if(near(d,t,axis,cut,depth))count+=3;
            if(count<=limit||depth<.3F)break;depth*=.6F;
        }
        var out=new FloatArrayList(Math.min(count,limit)*8);
        for(int t=0;t+23<d.length&&out.size()/8+3<=limit;t+=24) {
            if(!near(d,t,axis,cut,depth))continue;
            float ux=d[t+8]-d[t],uy=d[t+9]-d[t+1],uz=d[t+10]-d[t+2],vx=d[t+16]-d[t],vy=d[t+17]-d[t+1],vz=d[t+18]-d[t+2];
            float nx=uy*vz-uz*vy,ny=uz*vx-ux*vz,nz=ux*vy-uy*vx,length=(float)Math.sqrt(nx*nx+ny*ny+nz*nz);
            if(!(length>1.0E-6F))continue;nx/=length;ny/=length;nz/=length;
            for(int k:new int[]{0,2,1}){int i=t+k*8;
                out.add(d[i]-nx*INSET);out.add(d[i+1]-ny*INSET);out.add(d[i+2]-nz*INSET);out.add(d[i+3]);out.add(d[i+4]);out.add(-nx);out.add(-ny);out.add(-nz);}
        }
        return new McheliModel.Group("$fracture_lining",out.toFloatArray());
    }
    private static boolean near(float[] d,int t,int axis,float cut,float depth) {
        for(int k=0;k<3;k++)if(Math.abs(d[t+k*8+axis]-cut)<=depth)return true;return false;
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
