package io.github.mcheliceport.client.model;

import java.util.*;
import java.util.concurrent.*;
import net.minecraft.world.phys.Vec3;
import org.joml.Vector3f;

/** Loader-built sections. Polygon clipping preserves original UVs and leaves the source immutable. */
public final class BreakupMeshes {
    public record Bounds(Vector3f low,Vector3f high){public Vector3f center(){return new Vector3f(low).add(high).mul(.5F);}}
    public record Part(McheliModel.Group source,Bounds bounds,List<Vec3> tips,McheliModel.Group stem,McheliModel.Group tip){}
    public record Body(McheliModel.Group coverOpen,McheliModel.Group wingGone,McheliModel.Group tailGone,
            McheliModel.Group cover,McheliModel.Group wing,McheliModel.Group tail,McheliModel.Group machinery,
            Bounds bounds,Vec3 opening,float wingCut,float tailCut){}
    private record Key(Object source,String kind,Vec3 anchor){}
    private static final Map<Key,CompletableFuture<Object>> CACHE=new LinkedHashMap<>();
    private static final ExecutorService LOADER=Executors.newSingleThreadExecutor(r->{var t=new Thread(r,"mcheli-breakup-sections");t.setDaemon(true);return t;});
    private static int pending;
    private BreakupMeshes(){}
    private static Object get(Key key,java.util.function.Supplier<Object> build){
        CompletableFuture<Object> future;
        synchronized(CACHE){future=CACHE.get(key);if(future==null){
            if(pending>=4)return null;
            if(CACHE.size()>=32){var it=CACHE.entrySet().iterator();boolean removed=false;while(it.hasNext())if(it.next().getValue().isDone()){it.remove();removed=true;break;}if(!removed)return null;}
            future=new CompletableFuture<>();CACHE.put(key,future);pending++;var result=future;
            LOADER.execute(()->{Object value=null;try{value=build.get();}finally{result.complete(value);synchronized(CACHE){pending--;}}});
        }}return future.getNow(null);
    }
    public static Part part(McheliModel.Group source){if(source.vertexCount()<3||source.vertexCount()>12000)return null;return (Part)get(new Key(source,"part",Vec3.ZERO),()->partBuild(source));}
    public static Part rotor(McheliModel.Group source,Vec3 hub){
        if(source.vertexCount()<3||source.vertexCount()>12000)return null;
        return (Part)get(new Key(source,"rotor",hub),()->partBuild(source,hub));
    }
    public static Part merge(List<McheliModel.Group> groups){
        if(groups.isEmpty()||groups.stream().mapToInt(McheliModel.Group::vertexCount).sum()>12000)return null;
        return (Part)get(new Key(List.copyOf(groups),"merge",Vec3.ZERO),()->{var all=new ArrayList<Float>();for(var g:groups)for(float f:g.data())all.add(f);return partBuild(group("$separated_turret",all));});
    }
    public static Body body(McheliModel.Group source,boolean plane,Vec3 engine){
        if(source.vertexCount()<3||source.vertexCount()>60000)return null;
        return (Body)get(new Key(source,plane?"plane":"body",engine),()->bodyBuild(source,plane,engine));
    }
    public static Part partBuild(McheliModel.Group source){return partBuild(source,Vec3.ZERO);}
    private static Part partBuild(McheliModel.Group source,Vec3 hub){
        Bounds b=bounds(source);var d=source.data();var tips=new ArrayList<Vec3>();
        for(int axis:new int[]{0,1,2})for(int sign:new int[]{-1,1}){
            int best=0;for(int i=8;i<d.length;i+=8)if(d[i+axis]*sign>d[best+axis]*sign)best=i;
            tips.add(new Vec3(d[best],d[best+1],d[best+2]));
        }
        float[] low={b.low.x,b.low.y,b.low.z},high={b.high.x,b.high.y,b.high.z};double[] pivot={hub.x,hub.y,hub.z};
        int axis=0;for(int i=1;i<3;i++)if(high[i]-low[i]>high[axis]-low[axis])axis=i;
        boolean negative=Math.abs(low[axis]-pivot[axis])>Math.abs(high[axis]-pivot[axis]);
        float cut=negative?low[axis]+(high[axis]-low[axis])*.18F:high[axis]-(high[axis]-low[axis])*.18F;
        var halves=split(source,axis,cut);return new Part(source,b,List.copyOf(tips),halves[negative?1:0],halves[negative?0:1]);
    }

    public static Body bodyBuild(McheliModel.Group source,boolean plane,Vec3 engine){
        var b=bounds(source);var d=source.data();Vec3 opening=null;double distance=6.25;
        for(int t=0;t+23<d.length;t+=24){
            if((d[t+6]+d[t+14]+d[t+22])/3<.4F)continue;
            var at=new Vec3((d[t]+d[t+8]+d[t+16])/3,(d[t+1]+d[t+9]+d[t+17])/3,(d[t+2]+d[t+10]+d[t+18])/3);
            double dist=at.distanceToSqr(engine);if(dist<distance){distance=dist;opening=at;}
        }
        McheliModel.Group remaining=source,cover=empty("$blown_cover");var machinery=new DetailBoxes();
        if(opening!=null){
            float[] lo={(float)opening.x-.48F,(float)opening.y-.18F,(float)opening.z-.65F};
            float[] hi={(float)opening.x+.48F,(float)opening.y+.12F,(float)opening.z+.65F};
            var cube=boxSplit(source,lo,hi);remaining=cube[0];cover=cube[1];
            if(cover.vertexCount()>1200||remaining.vertexCount()<3||area(cover)>area(source)*.18){remaining=source;cover=empty("$blown_cover");opening=null;}
            else if(cover.vertexCount()>0){
                machinery.box(lo[0],lo[1]-.08F,lo[2],hi[0],lo[1]-.04F,hi[2]);
                for(int i=0;i<3;i++)machinery.box(lo[0]+.10F+i*.26F,lo[1]-.03F,lo[2]+.12F,lo[0]+.22F+i*.26F,lo[1]+.08F,hi[2]-.12F);
            }
        }
        float wingCut=b.high.x*.68F,tailCut=b.low.z+(b.high.z-b.low.z)*.27F;
        var wing=plane&&b.high.x-b.low.x>5?split(remaining,0,wingCut):new McheliModel.Group[]{remaining,empty("$wing_section")};
        var tail=plane&&b.high.z-b.low.z>6?split(wing[0],2,tailCut):new McheliModel.Group[]{empty("$tail_section"),wing[0]};
        // No unsupported massive falling sections: retain their original surface instead.
        if(wing[1].vertexCount()>9000)wing=new McheliModel.Group[]{remaining,empty("$wing_section")};
        tail=plane&&b.high.z-b.low.z>6?split(wing[0],2,tailCut):new McheliModel.Group[]{empty("$tail_section"),wing[0]};
        if(tail[0].vertexCount()>9000)tail=new McheliModel.Group[]{empty("$tail_section"),wing[0]};
        if(plane){
            var wingEdge=edge(wing[0],0,wingCut);var tailEdge=edge(tail[1],2,tailCut);
            if(!wingEdge.isEmpty())for(int i=0;i<3;i++){
                float z=wingEdge.stream().map(v->v.z).min(Float::compare).orElse(0F)+(wingEdge.stream().map(v->v.z).max(Float::compare).orElse(0F)-wingEdge.stream().map(v->v.z).min(Float::compare).orElse(0F))*(i+1)/4;
                final float target=z;var point=wingEdge.stream().min(java.util.Comparator.comparingDouble(v->Math.abs(v.z-target))).orElseThrow();
                machinery.box(wingCut-.08F,point.y-.02F,point.z-.035F,wingCut+.13F+i*.025F,point.y+.02F,point.z+.035F);
            }
            if(!tailEdge.isEmpty()){
                var point=tailEdge.stream().min(java.util.Comparator.comparingDouble(v->Math.abs(v.x))).orElseThrow();
                machinery.box(point.x-.20F,point.y-.03F,tailCut-.13F,point.x+.20F,point.y+.03F,tailCut+.13F);
            }
        }
        var frames=List.of(remaining,wing[0],tail[1]);for(var frame:frames)prepare(frame);
        return new Body(remaining,wing[0],tail[1],cover,wing[1],tail[0],machinery.group("$exposed_spars"),b,opening,wingCut,tailCut);
    }
    private static double area(McheliModel.Group group){
        double sum=0;var d=group.data();for(int t=0;t+23<d.length;t+=24){
            double ax=d[t+8]-d[t],ay=d[t+9]-d[t+1],az=d[t+10]-d[t+2],bx=d[t+16]-d[t],by=d[t+17]-d[t+1],bz=d[t+18]-d[t+2];
            double x=ay*bz-az*by,y=az*bx-ax*bz,z=ax*by-ay*bx;sum+=Math.sqrt(x*x+y*y+z*z)*.5;
        }return sum;
    }
    private static List<Vector3f> edge(McheliModel.Group group,int axis,float plane){
        var points=new ArrayList<Vector3f>();var d=group.data();for(int i=0;i<d.length;i+=8)if(Math.abs(d[i+axis]-plane)<.002F)points.add(new Vector3f(d[i],d[i+1],d[i+2]));return points;
    }
    public static Bounds bounds(McheliModel.Group source){
        var lo=new Vector3f(Float.POSITIVE_INFINITY);var hi=new Vector3f(Float.NEGATIVE_INFINITY);var d=source.data();
        for(int i=0;i+7<d.length;i+=8){var p=new Vector3f(d[i],d[i+1],d[i+2]);lo.min(p);hi.max(p);}return new Bounds(lo,hi);
    }
    public static McheliModel.Group[] split(McheliModel.Group source,int axis,float at){
        var low=new ArrayList<Float>();var high=new ArrayList<Float>();var d=source.data();
        for(int t=0;t+23<d.length;t+=24){var tri=triangle(d,t);append(clip(tri,axis,at,false),low);append(clip(tri,axis,at,true),high);}
        return new McheliModel.Group[]{group(source.name(),low),group(source.name(),high)};
    }
    private static McheliModel.Group[] boxSplit(McheliModel.Group source,float[] lo,float[] hi){
        var outside=new ArrayList<Float>();var inside=new ArrayList<Float>();var d=source.data();
        for(int t=0;t+23<d.length;t+=24){var polygon=triangle(d,t);
            for(int axis=0;axis<3&&!polygon.isEmpty();axis++){
                append(clip(polygon,axis,lo[axis],false),outside);polygon=clip(polygon,axis,lo[axis],true);
                append(clip(polygon,axis,hi[axis],true),outside);polygon=clip(polygon,axis,hi[axis],false);
            }append(polygon,inside);
        }return new McheliModel.Group[]{group(source.name(),outside),group("$blown_cover",inside)};
    }
    private static ArrayList<float[]> triangle(float[] d,int t){var p=new ArrayList<float[]>();for(int k=0;k<3;k++)p.add(Arrays.copyOfRange(d,t+k*8,t+k*8+8));return p;}
    private static ArrayList<float[]> clip(List<float[]> input,int axis,float at,boolean above){
        var out=new ArrayList<float[]>();if(input.isEmpty())return out;var prev=input.getLast();boolean pin=above?prev[axis]>=at:prev[axis]<=at;
        for(var next:input){boolean nin=above?next[axis]>=at:next[axis]<=at;
            if(pin!=nin){float f=(at-prev[axis])/(next[axis]-prev[axis]);var edge=new float[8];for(int i=0;i<8;i++)edge[i]=prev[i]+(next[i]-prev[i])*f;out.add(edge);}
            if(nin)out.add(next);prev=next;pin=nin;
        }return out;
    }
    private static void append(List<float[]> polygon,List<Float> out){
        for(int i=1;i+1<polygon.size();i++){
            var a=polygon.getFirst();var b=polygon.get(i);var c=polygon.get(i+1);
            float ux=b[0]-a[0],uy=b[1]-a[1],uz=b[2]-a[2],vx=c[0]-a[0],vy=c[1]-a[1],vz=c[2]-a[2];
            float nx=uy*vz-uz*vy,ny=uz*vx-ux*vz,nz=ux*vy-uy*vx;if(nx*nx+ny*ny+nz*nz<1e-12F)continue;
            for(var v:new float[][]{a,b,c})for(float f:v)out.add(f);
        }
    }
    private static McheliModel.Group group(String name,List<Float> v){float[] d=new float[v.size()];for(int i=0;i<d.length;i++)d[i]=v.get(i);return new McheliModel.Group(name,d);}
    private static McheliModel.Group empty(String name){return new McheliModel.Group(name,new float[0]);}
    private static void prepare(McheliModel.Group g){var m=new McheliModel("breakup",List.of(g),0,0,0,0,0,0);VehicleSurfaceShade.prepare(m);VehicleImpactSurface.prepare(m);}
    public static void clear(){synchronized(CACHE){CACHE.clear();}}
}
