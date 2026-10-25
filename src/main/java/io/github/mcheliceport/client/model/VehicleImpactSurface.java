package io.github.mcheliceport.client.model;

import java.util.*;
import org.joml.Vector3f;

/** Loader-built triangle indexes and clipped, part-local decal geometry. Never builds on the render thread. */
public final class VehicleImpactSurface {
    public static final int MAX_GROUP_TRIANGLES=65536,MAX_CACHED_TRIANGLES=262144,MAX_MARK_TRIANGLES=32;
    private static final Map<McheliModel.Group,Index> CACHE=new WeakHashMap<>();
    private static final Set<McheliModel.Group> PENDING=Collections.newSetFromMap(new IdentityHashMap<>());
    private static final java.util.concurrent.ExecutorService LOADER=java.util.concurrent.Executors.newSingleThreadExecutor(r->{
        Thread t=new Thread(r,"mcheli-impact-index");t.setDaemon(true);return t;
    });
    private static int generation;
    private VehicleImpactSurface() {}
    public record Vertex(float x,float y,float z,float u,float v) {}
    public record Mesh(List<Vertex> vertices,Vector3f normal) {}
    public record Hit(float distance,int triangle) {}
    private record Node(float x0,float y0,float z0,float x1,float y1,float z1,int start,int end,Node left,Node right) {}
    public static void prepare(McheliModel model) {
        for(var g:model.groups) {
            int n=g.data().length/24;if(n==0||n>MAX_GROUP_TRIANGLES)continue;
            synchronized(CACHE){if(CACHE.containsKey(g))continue;}
            Index index=new Index(g.data());
            synchronized(CACHE){put(g,index);}
        }
    }
    private static void put(McheliModel.Group group,Index index){
        CACHE.remove(group);
        int size=CACHE.values().stream().mapToInt(i->i.triangles.length).sum();
        var iterator=CACHE.entrySet().iterator();
        while((size+index.triangles.length>MAX_CACHED_TRIANGLES||CACHE.size()>=1024)&&iterator.hasNext()){
            size-=iterator.next().getValue().triangles.length;iterator.remove();
        }
        CACHE.put(group,index);
    }
    /** Rebuild evicted indexes off-thread; at most eight queued/running groups. */
    public static boolean request(McheliModel.Group group){
        int n=group.data().length/24;if(n==0||n>MAX_GROUP_TRIANGLES)return false;
        final int epoch;
        synchronized(CACHE){
            if(CACHE.containsKey(group)||PENDING.contains(group)||PENDING.size()>=8)return true;
            PENDING.add(group);epoch=generation;
        }
        LOADER.execute(()->{
            try{var index=new Index(group.data());synchronized(CACHE){if(epoch==generation)put(group,index);}}
            finally{synchronized(CACHE){PENDING.remove(group);}}
        });
        return true;
    }
    public static void clear(){synchronized(CACHE){CACHE.clear();generation++;}}
    public static Index get(McheliModel.Group group){synchronized(CACHE){return CACHE.get(group);}}
    public static final class Index {
        private final int[] triangles;
        private final Node root;
        Index(float[] data){
            triangles=new int[data.length/24];for(int i=0;i<triangles.length;i++)triangles[i]=i*24;
            root=build(data,0,triangles.length,0);
        }
        private Node build(float[] d,int lo,int hi,int depth){
            float x0=Float.POSITIVE_INFINITY,y0=x0,z0=x0,x1=-x0,y1=x1,z1=x1;
            for(int i=lo;i<hi;i++)for(int k=0;k<3;k++){
                int t=triangles[i]+k*8;x0=Math.min(x0,d[t]);y0=Math.min(y0,d[t+1]);z0=Math.min(z0,d[t+2]);
                x1=Math.max(x1,d[t]);y1=Math.max(y1,d[t+1]);z1=Math.max(z1,d[t+2]);
            }
            Node left=null,right=null;
            if(hi-lo>12&&depth<32){
                int axis=x1-x0>=y1-y0&&x1-x0>=z1-z0?0:y1-y0>=z1-z0?1:2;
                float split=axis==0?(x0+x1)*.5F:axis==1?(y0+y1)*.5F:(z0+z1)*.5F;
                int mid=lo;
                for(int i=lo;i<hi;i++){
                    int t=triangles[i];float center=(d[t+axis]+d[t+8+axis]+d[t+16+axis])/3;
                    if(center<split){int temp=triangles[mid];triangles[mid++]=t;triangles[i]=temp;}
                }
                if(mid==lo||mid==hi)mid=(lo+hi)/2;
                left=build(d,lo,mid,depth+1);right=build(d,mid,hi,depth+1);
            }
            return new Node(x0,y0,z0,x1,y1,z1,lo,hi,left,right);
        }
        public Hit ray(float[] data,Vector3f origin,Vector3f direction,float max){
            return ray(data,origin,direction,max,new int[]{4096});
        }
        public Hit ray(float[] data,Vector3f origin,Vector3f direction,float max,int[] work){
            float[] best={max,-1};ray(root,data,origin,direction,best,work);
            return best[1]<0?null:new Hit(best[0],(int)best[1]);
        }
        private void ray(Node n,float[] data,Vector3f p,Vector3f d,float[] best,int[] work){
            if(--work[0]<0||!box(n,p,d,best[0]))return;
            if(n.left!=null){ray(n.left,data,p,d,best,work);ray(n.right,data,p,d,best,work);return;}
            for(int i=n.start;i<n.end;i++){
                int t=triangles[i];float hit=triangle(data,t,p,d);
                if(hit>=0&&hit<best[0]){best[0]=hit;best[1]=t;}
            }
        }
        private static boolean box(Node n,Vector3f p,Vector3f d,float max){
            float min=0;
            for(int axis=0;axis<3;axis++){
                float origin=p.get(axis),dir=d.get(axis),lo=axis==0?n.x0:axis==1?n.y0:n.z0,hi=axis==0?n.x1:axis==1?n.y1:n.z1;
                if(Math.abs(dir)<1e-9){if(origin<lo||origin>hi)return false;continue;}
                float a=(lo-origin)/dir,b=(hi-origin)/dir;if(a>b){float tmp=a;a=b;b=tmp;}
                min=Math.max(min,a);max=Math.min(max,b);if(min>max)return false;
            }
            return true;
        }
        private static float triangle(float[] a,int t,Vector3f p,Vector3f d){
            float ex=a[t+8]-a[t],ey=a[t+9]-a[t+1],ez=a[t+10]-a[t+2];
            float fx=a[t+16]-a[t],fy=a[t+17]-a[t+1],fz=a[t+18]-a[t+2];
            float hx=d.y*fz-d.z*fy,hy=d.z*fx-d.x*fz,hz=d.x*fy-d.y*fx;
            float determinant=ex*hx+ey*hy+ez*hz;if(Math.abs(determinant)<1e-8)return -1;
            float sx=p.x-a[t],sy=p.y-a[t+1],sz=p.z-a[t+2],inv=1/determinant;
            float u=(sx*hx+sy*hy+sz*hz)*inv;if(u<0||u>1)return -1;
            float qx=sy*ez-sz*ey,qy=sz*ex-sx*ez,qz=sx*ey-sy*ex;
            float v=(d.x*qx+d.y*qy+d.z*qz)*inv;if(v<0||u+v>1)return -1;
            return (fx*qx+fy*qy+fz*qz)*inv;
        }
        public Mesh decal(float[] data,Hit hit,Vector3f origin,Vector3f direction,float radius,float angle){
            Vector3f center=new Vector3f(direction).mul(hit.distance).add(origin);
            Vector3f normal=normal(data,hit.triangle);if(normal.dot(direction)>0)normal.negate();
            Vector3f tangent=Math.abs(normal.y)<.9F?new Vector3f(0,1,0):new Vector3f(1,0,0);
            tangent.cross(normal).normalize().rotateAxis(angle,normal.x,normal.y,normal.z);
            Vector3f bitangent=new Vector3f(normal).cross(tangent);
            var nearby=new ArrayList<Integer>();nearby.add(hit.triangle);
            around(root,data,center,radius*1.6F,nearby,new int[]{4096});
            nearby.sort(Comparator.comparingDouble(t->distance(data,t,center)));
            var vertices=new ArrayList<Vertex>();
            for(int t:nearby){
                Vector3f n=normal(data,t);if(Math.abs(n.dot(normal))<.85F)continue;
                var poly=new ArrayList<Vertex>(3);boolean close=true;
                for(int k=0;k<3;k++){
                    int i=t+k*8;Vector3f delta=new Vector3f(data[i],data[i+1],data[i+2]).sub(center);
                    if(Math.abs(delta.dot(normal))>Math.max(.025F,radius*.25F))close=false;
                    poly.add(new Vertex(data[i]+normal.x*.003F,data[i+1]+normal.y*.003F,data[i+2]+normal.z*.003F,
                            .5F+delta.dot(tangent)/(radius*2),.5F+delta.dot(bitangent)/(radius*2)));
                }
                if(!close)continue;
                for(int edge=0;edge<4;edge++)poly=clip(poly,edge);
                for(int i=1;i+1<poly.size()&&vertices.size()<MAX_MARK_TRIANGLES*3;i++){
                    vertices.add(poly.getFirst());vertices.add(poly.get(i));vertices.add(poly.get(i+1));
                }
                if(vertices.size()>=MAX_MARK_TRIANGLES*3)break;
            }
            return new Mesh(List.copyOf(vertices),normal);
        }
        private void around(Node n,float[] d,Vector3f p,float r,List<Integer> out,int[] work){
            if(--work[0]<0||out.size()>=128||p.x+r<n.x0||p.x-r>n.x1||p.y+r<n.y0||p.y-r>n.y1||p.z+r<n.z0||p.z-r>n.z1)return;
            if(n.left!=null){around(n.left,d,p,r,out,work);around(n.right,d,p,r,out,work);}
            else for(int i=n.start;i<n.end&&out.size()<128;i++)if(!out.contains(triangles[i]))out.add(triangles[i]);
        }
    }
    private static double distance(float[] d,int t,Vector3f p){
        float x=(d[t]+d[t+8]+d[t+16])/3-p.x,y=(d[t+1]+d[t+9]+d[t+17])/3-p.y,z=(d[t+2]+d[t+10]+d[t+18])/3-p.z;
        return x*x+y*y+z*z;
    }
    private static Vector3f normal(float[] d,int t){
        return new Vector3f(d[t+8]-d[t],d[t+9]-d[t+1],d[t+10]-d[t+2])
                .cross(new Vector3f(d[t+16]-d[t],d[t+17]-d[t+1],d[t+18]-d[t+2])).normalize();
    }
    private static ArrayList<Vertex> clip(List<Vertex> in,int edge){
        var out=new ArrayList<Vertex>();if(in.isEmpty())return out;
        Vertex previous=in.getLast();float previousSide=side(previous,edge);
        for(Vertex current:in){
            float currentSide=side(current,edge);
            if((previousSide>=0)!=(currentSide>=0)){
                float f=previousSide/(previousSide-currentSide);
                out.add(new Vertex(mix(previous.x,current.x,f),mix(previous.y,current.y,f),mix(previous.z,current.z,f),
                        mix(previous.u,current.u,f),mix(previous.v,current.v,f)));
            }
            if(currentSide>=0)out.add(current);previous=current;previousSide=currentSide;
        }
        return out;
    }
    private static float side(Vertex v,int edge){return switch(edge){case 0->v.u;case 1->1-v.u;case 2->v.v;default->1-v.v;};}
    private static float mix(float a,float b,float f){return a+(b-a)*f;}
}
