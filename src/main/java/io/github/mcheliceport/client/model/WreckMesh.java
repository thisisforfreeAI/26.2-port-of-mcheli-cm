package io.github.mcheliceport.client.model;
import java.util.*;
import java.util.concurrent.*;
import org.joml.Vector3f;
/** Immutable loader-built burn patches and restrained outer-wing breakup. */
public final class WreckMesh {
 public record Parts(McheliModel.Group shape,McheliModel.Group paint,McheliModel.Group metal,Vector3f center){}
 private record Key(McheliModel.Group group,boolean wing){}
 private static final Map<Key,CompletableFuture<Parts>> CACHE=new LinkedHashMap<>();
 private static final ExecutorService LOADER=Executors.newSingleThreadExecutor(r->{var t=new Thread(r,"mcheli-wreck-mesh");t.setDaemon(true);return t;});
 private static int pending;
 public static Parts get(McheliModel.Group source,boolean wing){
  var key=new Key(source,wing);CompletableFuture<Parts> f;
  synchronized(CACHE){f=CACHE.get(key);if(f==null){
   if(pending>=4||source.vertexCount()<3||source.vertexCount()>60000)return null;
   if(CACHE.size()>=24){var it=CACHE.entrySet().iterator();boolean removed=false;while(it.hasNext())if(it.next().getValue().isDone()){it.remove();removed=true;break;}if(!removed)return null;}
   f=new CompletableFuture<>();CACHE.put(key,f);pending++;var result=f;
   LOADER.execute(()->{Parts p=null;try{p=build(source,wing);var m=new McheliModel("wreck",List.of(p.shape()),0,0,0,0,0,0);VehicleSurfaceShade.prepare(m);VehicleImpactSurface.prepare(m);}finally{result.complete(p);synchronized(CACHE){pending--;}}});
  }}return f.getNow(null);
 }
 public static Parts build(McheliModel.Group source,boolean wing){
  var d=source.data();var lo=new Vector3f(Float.POSITIVE_INFINITY);var hi=new Vector3f(Float.NEGATIVE_INFINITY);
  for(int i=0;i+7<d.length;i+=8){var v=new Vector3f(d[i],d[i+1],d[i+2]);lo.min(v);hi.max(v);}
  var shape=new ArrayList<Float>();var paint=new ArrayList<Float>();var metal=new ArrayList<Float>();float extent=Math.max(Math.abs(lo.x),Math.abs(hi.x));
  for(int t=0;t+23<d.length;t+=24){
   float cx=(d[t]+d[t+8]+d[t+16])/3,cy=(d[t+1]+d[t+9]+d[t+17])/3,cz=(d[t+2]+d[t+10]+d[t+18])/3;
   if(wing&&extent>3&&cx>extent*.95F&&t/24%3==0)continue;
   int hash=Float.floatToIntBits((float)Math.floor(cx*1.7))^Integer.rotateLeft(Float.floatToIntBits((float)Math.floor(cz*1.7)),11);
   var patch=Math.floorMod(hash,13)<3?paint:Math.floorMod(hash,19)==5?metal:null;
   for(int k=0;k<3;k++){int i=t+k*8;float[] v=Arrays.copyOfRange(d,i,i+8);
    if(wing&&extent>3){float w=Math.clamp((Math.abs(v[0])/extent-.65F)/.35F,0,1);v[1]-=w*w*.55F;}
    for(float a:v)shape.add(a);
    if(patch!=null&&patch.size()<9600){for(int a=0;a<3;a++)patch.add(v[a]+v[5+a]*.005F);for(int a=3;a<8;a++)patch.add(a==3&&patch==metal?v[0]:a==4&&patch==metal?v[2]:v[a]);}
   }
  }
  return new Parts(group(source.name(),shape),group("$wreck_paint",paint),group("$wreck_metal",metal),lo.add(hi).mul(.5F));
 }
 private static McheliModel.Group group(String name,List<Float> v){float[] d=new float[v.size()];for(int i=0;i<d.length;i++)d[i]=v.get(i);return new McheliModel.Group(name,d);}
 public static void clear(){synchronized(CACHE){CACHE.clear();}}
}
