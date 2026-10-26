package io.github.mcheliceport.client.model;

import java.util.*;
import java.util.concurrent.*;

/** Bounded asynchronous overlay geometry; original groups and their UVs remain unchanged. */
public final class VehicleSkinMeshes {
    public record Skin(McheliModel.Group raised,McheliModel.Group top,McheliModel.Group lower){}
    private static final Map<McheliModel.Group,CompletableFuture<Skin>> CACHE=new LinkedHashMap<>();
    private static final ExecutorService LOADER=Executors.newSingleThreadExecutor(r->{var t=new Thread(r,"mcheli-vehicle-skin");t.setDaemon(true);return t;});
    private static int pending;
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
    public static void clear(){synchronized(CACHE){CACHE.clear();}}
}
