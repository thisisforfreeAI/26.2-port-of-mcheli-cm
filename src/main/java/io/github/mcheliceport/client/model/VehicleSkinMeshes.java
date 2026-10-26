package io.github.mcheliceport.client.model;

import java.util.*;
import java.util.concurrent.*;

/** Bounded asynchronous overlay geometry; original groups and their UVs remain unchanged. */
public final class VehicleSkinMeshes {
    public record Skin(McheliModel.Group raised,McheliModel.Group top,McheliModel.Group lower){}
    private static final Map<McheliModel.Group,CompletableFuture<Skin>> CACHE=new LinkedHashMap<>();
    private static final ExecutorService LOADER=Executors.newSingleThreadExecutor(r->{var t=new Thread(r,"mcheli-vehicle-skin");t.setDaemon(true);return t;});
    private record Region(McheliModel.Group group,net.minecraft.world.phys.Vec3 point,float radius){}
    private static final Map<Region,CompletableFuture<McheliModel.Group>> REGIONS=new LinkedHashMap<>();
    private static int pending;
    public static McheliModel.Group region(McheliModel.Group group,net.minecraft.world.phys.Vec3 point,float radius){
        var key=new Region(group,point,radius);CompletableFuture<McheliModel.Group> future;
        synchronized(CACHE){future=REGIONS.get(key);if(future==null){
            if(pending>=4||group.vertexCount()>60000)return null;
            if(REGIONS.size()>=32){var it=REGIONS.entrySet().iterator();boolean removed=false;while(it.hasNext())if(it.next().getValue().isDone()){it.remove();removed=true;break;}if(!removed)return null;}
            future=new CompletableFuture<>();REGIONS.put(key,future);pending++;var result=future;
            LOADER.execute(()->{McheliModel.Group mesh=null;try{mesh=regionMesh(group,point,radius);}finally{result.complete(mesh);synchronized(CACHE){pending--;}}});
        }}return future.getNow(null);
    }
    static McheliModel.Group regionMesh(McheliModel.Group group,net.minecraft.world.phys.Vec3 p,float radius){
        var out=new java.util.ArrayList<Float>();var d=group.data();
        for(int t=0;t+23<d.length&&out.size()<24000;t+=24){
            var polygon=new ArrayList<float[]>();for(int k=0;k<3;k++)polygon.add(Arrays.copyOfRange(d,t+k*8,t+k*8+8));
            for(int axis=0;axis<3;axis++){
                float center=(float)(axis==0?p.x:axis==1?p.y:p.z);
                polygon=clipRegion(polygon,axis,center-radius,true);polygon=clipRegion(polygon,axis,center+radius,false);
            }
            for(int n=1;n+1<polygon.size()&&out.size()+24<=24000;n++)for(var v:new float[][]{polygon.getFirst(),polygon.get(n),polygon.get(n+1)}){
                for(int a=0;a<3;a++)out.add(v[a]+v[5+a]*.006F);
                boolean top=Math.abs(v[6])>.6F,side=Math.abs(v[5])>.6F;
                out.add((float)(.5+((side?v[2]:v[0])-(side?p.z:p.x))/(radius*2)));
                out.add((float)(.5+((top?v[2]:v[1])-(top?p.z:p.y))/(radius*2)));
                for(int a=5;a<8;a++)out.add(v[a]);
            }
        }
        float[] data=new float[out.size()];for(int i=0;i<data.length;i++)data[i]=out.get(i);return new McheliModel.Group(group.name()+"_soot",data);
    }
    private static ArrayList<float[]> clipRegion(java.util.List<float[]> input,int axis,float at,boolean above){
        var out=new ArrayList<float[]>();if(input.isEmpty())return out;var prev=input.getLast();boolean pin=above?prev[axis]>=at:prev[axis]<=at;
        for(var next:input){boolean nin=above?next[axis]>=at:next[axis]<=at;
            if(pin!=nin){float f=(at-prev[axis])/(next[axis]-prev[axis]);var edge=new float[8];for(int i=0;i<8;i++)edge[i]=prev[i]+(next[i]-prev[i])*f;out.add(edge);}
            if(nin)out.add(next);prev=next;pin=nin;
        }return out;
    }
    private VehicleSkinMeshes(){}
    public static Skin get(McheliModel.Group group){
        CompletableFuture<Skin> future;
        synchronized(CACHE){future=CACHE.get(group);if(future==null){
            if(pending>=4||group.vertexCount()>60000)return null;
            if(CACHE.size()>=16){var it=CACHE.entrySet().iterator();boolean removed=false;while(it.hasNext())if(it.next().getValue().isDone()){it.remove();removed=true;break;}if(!removed)return null;}
            future=new CompletableFuture<>();CACHE.put(group,future);pending++;var result=future;
            LOADER.execute(()->{Skin skin=null;try{skin=build(group);}finally{result.complete(skin);synchronized(CACHE){pending--;}}});
        }}return future.getNow(null);
    }
    static Skin build(McheliModel.Group group){
        float[] d=group.data();float low=Float.POSITIVE_INFINITY,high=-low;
        for(int i=1;i<d.length;i+=8){low=Math.min(low,d[i]);high=Math.max(high,d[i]);}
        float limit=low+(high-low)*.45F;
        float[] raised=group.vertexCount()<=24000?d.clone():new float[0],top=new float[Math.min(d.length,6000*8)],lower=new float[Math.min(d.length,6000*8)];int nt=0,nl=0;
        int stride=Math.max(1,(d.length/24)/1800);
        for(int t=0;t+23<d.length;t+=24){
            for(int k=0;k<3;k++){int i=t+k*8;if(raised.length>0)for(int axis=0;axis<3;axis++)raised[i+axis]+=d[i+5+axis]*.003F;}
            if(t/24%stride!=0)continue;
            float ny=(d[t+6]+d[t+14]+d[t+22])/3,cy=(d[t+1]+d[t+9]+d[t+17])/3;
            if(ny>.55F&&nt+24<=top.length){
                System.arraycopy(d,t,top,nt,24);for(int k=0;k<3;k++){int i=nt+k*8;top[i+1]+=.012F;top[i+3]=top[i]*.75F;top[i+4]=top[i+2]*.75F;}nt+=24;
            }
            if(cy<limit&&nl+24<=lower.length){
                System.arraycopy(d,t,lower,nl,24);for(int k=0;k<3;k++){int i=nl+k*8;for(int a=0;a<3;a++)lower[i+a]+=lower[i+5+a]*.004F;
                    lower[i+3]=Math.abs(lower[i+5])>.7F?lower[i+2]*.75F:lower[i]*.75F;lower[i+4]=lower[i+1]*.75F;}nl+=24;
            }
        }
        return new Skin(new McheliModel.Group(group.name()+"_raised",raised),new McheliModel.Group(group.name()+"_snow",Arrays.copyOf(top,nt)),new McheliModel.Group(group.name()+"_dirt",Arrays.copyOf(lower,nl)));
    }
    public static void clear(){synchronized(CACHE){CACHE.clear();REGIONS.clear();}}
}
