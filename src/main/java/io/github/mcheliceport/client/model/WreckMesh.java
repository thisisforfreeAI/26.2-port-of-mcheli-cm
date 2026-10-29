package io.github.mcheliceport.client.model;
import java.util.*;
import java.util.concurrent.*;
import org.joml.Vector3f;
/**
 * Immutable loader-built burn patches, restrained outer-wing breakup and, for crashed planes, a crushed
 * and drooping nose. Also the charred skin layer, ember tiers and small debris chunks cut from the hull.
 */
public final class WreckMesh {
 /** A small hull fragment, recentred on {@code center}, with its box size. */
 public record Chunk(McheliModel.Group group,Vector3f center,Vector3f size){}
 public record Parts(McheliModel.Group shape,McheliModel.Group paint,McheliModel.Group metal,Vector3f center,
         McheliModel.Group soot,List<McheliModel.Group> embers,List<Chunk> chunks){
  public Parts(McheliModel.Group shape,McheliModel.Group paint,McheliModel.Group metal,Vector3f center){this(shape,paint,metal,center,group("$wreck_soot",List.of()),List.of(),List.of());}
 }
 private record Key(McheliModel.Group group,boolean wing){}
 private static final Map<Key,CompletableFuture<Parts>> CACHE=new LinkedHashMap<>();
 private static final Map<McheliModel.Group,McheliModel.Group> SOOT=new WeakHashMap<>();
 private static final ExecutorService LOADER=Executors.newSingleThreadExecutor(r->{var t=new Thread(r,"mcheli-wreck-mesh");t.setDaemon(true);return t;});
 private static int pending;
 /** UV repeats per block of charred skin, and how far the layer sits off the hull. */
 static final float SOOT_SCALE=.9F,SOOT_LIFT=.007F;
 public static final int EMBER_TIERS=3,MAX_CHUNKS=9;
 public static Parts get(McheliModel.Group source,boolean wing){
  var key=new Key(source,wing);CompletableFuture<Parts> f;
  synchronized(CACHE){f=CACHE.get(key);if(f==null){
   if(pending>=4||source.vertexCount()<3||source.vertexCount()>60000)return null;
   if(CACHE.size()>=24){var it=CACHE.entrySet().iterator();boolean removed=false;while(it.hasNext())if(it.next().getValue().isDone()){it.remove();removed=true;break;}if(!removed)return null;}
   f=new CompletableFuture<>();CACHE.put(key,f);pending++;var result=f;
   LOADER.execute(()->{Parts p=null;try{p=build(source,wing,wing);var m=new McheliModel("wreck",List.of(p.shape()),0,0,0,0,0,0);VehicleSurfaceShade.prepare(m);VehicleImpactSurface.prepare(m);}finally{result.complete(p);synchronized(CACHE){pending--;}}});
  }}return f.getNow(null);
 }
 public static Parts build(McheliModel.Group source,boolean wing){return build(source,wing,false);}
 /** {@code crumple}: a crashed plane's hull, nose crushed back and down, front fuselage drooping, skin wrinkled. */
 public static Parts build(McheliModel.Group source,boolean wing,boolean crumple){
  var d=source.data();var lo=new Vector3f(Float.POSITIVE_INFINITY);var hi=new Vector3f(Float.NEGATIVE_INFINITY);
  for(int i=0;i+7<d.length;i+=8){var v=new Vector3f(d[i],d[i+1],d[i+2]);lo.min(v);hi.max(v);}
  var shape=new ArrayList<Float>();var paint=new ArrayList<Float>();var metal=new ArrayList<Float>();float extent=Math.max(Math.abs(lo.x),Math.abs(hi.x));
  for(int t=0;t+23<d.length;t+=24){
   float cx=(d[t]+d[t+8]+d[t+16])/3,cy=(d[t+1]+d[t+9]+d[t+17])/3,cz=(d[t+2]+d[t+10]+d[t+18])/3;
   if(wing&&extent>3&&cx>extent*.95F&&t/24%3==0)continue;
   int hash=Float.floatToIntBits((float)Math.floor(cx*1.7))^Integer.rotateLeft(Float.floatToIntBits((float)Math.floor(cz*1.7)),11);
   var patch=Math.floorMod(hash,13)<3?paint:Math.floorMod(hash,19)==5?metal:null;
   float[][] tri=new float[3][];boolean crushed=false;
   for(int k=0;k<3;k++){int i=t+k*8;float[] v=Arrays.copyOfRange(d,i,i+8);
    if(wing&&extent>3){float w=Math.clamp((Math.abs(v[0])/extent-.65F)/.35F,0,1);v[1]-=w*w*.55F;}
    if(crumple)crushed|=crumple(v,lo,hi);
    tri[k]=v;
   }
   // Crushed skin is faceted: each triangle takes its own face normal instead of the smooth one.
   if(crushed)faceNormal(tri);
   for(var v:tri){
    for(float a:v)shape.add(a);
    if(patch!=null&&patch.size()<9600){for(int a=0;a<3;a++)patch.add(v[a]+v[5+a]*.0095F);for(int a=3;a<8;a++)patch.add(a==3&&patch==metal?v[0]:a==4&&patch==metal?v[2]:v[a]);}
   }
  }
  var hull=group(source.name(),shape);
  return new Parts(hull,group("$wreck_paint",paint),group("$wreck_metal",metal),new Vector3f(lo).add(hi).mul(.5F),
          soot(hull),embers(hull,lo,hi),crumple?chunks(hull,lo,hi):List.of());
 }
 /**
  * Moves one vertex of a crashed plane's hull. Only the front 38% changes (no cut caps there): it droops
  * up to 4 degrees, the front 17% is shortened and flattened, and the skin wrinkles. Displacement depends
  * on position alone, so vertices shared between triangles stay together. Returns whether it was crushed.
  */
 static boolean crumple(float[] v,Vector3f lo,Vector3f hi){
  float length=hi.z-lo.z;if(length<2)return false;
  float bend=lo.z+.62F*length;if(v[2]<=bend)return false;
  float t=(v[2]-bend)/(hi.z-bend),s=Math.clamp((t-.55F)/.45F,0,1);
  float cy=(lo.y+hi.y)*.5F,height=Math.max(.2F,hi.y-lo.y);
  float x=v[0],y=v[1],z=v[2];
  y-=(z-bend)*.07F*t;
  z-=s*s*.07F*length;y-=s*s*.03F*length;y=cy+(y-cy)*(1-.22F*s);x*=1+.16F*s;
  float rx=v[0],ry=v[1]-cy;float r=(float)Math.sqrt(rx*rx+ry*ry);
  float wrinkle=(noise(v[0]*2.3F,v[1]*2.3F,v[2]*2.3F)*(.006F+.022F*s))*length;
  if(r>1e-3F){x+=rx/r*wrinkle*Math.min(1,height);y+=ry/r*wrinkle*Math.min(1,height);}
  v[0]=x;v[1]=y;v[2]=z;return s>0;
 }
 /** Smooth value noise in -1..1 from a position. */
 static float noise(float x,float y,float z){
  int ix=(int)Math.floor(x),iy=(int)Math.floor(y),iz=(int)Math.floor(z);float fx=x-ix,fy=y-iy,fz=z-iz;
  fx=fx*fx*(3-2*fx);fy=fy*fy*(3-2*fy);fz=fz*fz*(3-2*fz);float total=0;
  for(int c=0;c<8;c++){int dx=c&1,dy=c>>1&1,dz=c>>2&1;
   float w=(dx==1?fx:1-fx)*(dy==1?fy:1-fy)*(dz==1?fz:1-fz);total+=w*lattice(ix+dx,iy+dy,iz+dz);}
  return total;
 }
 private static float lattice(int x,int y,int z){int h=x*73856093^y*19349663^z*83492791;h^=h>>>13;h*=0x5bd1e995;h^=h>>>15;return (h&0xFFFF)/32767.5F-1;}
 private static void faceNormal(float[][] tri){
  var a=new Vector3f(tri[1][0]-tri[0][0],tri[1][1]-tri[0][1],tri[1][2]-tri[0][2]);
  var b=new Vector3f(tri[2][0]-tri[0][0],tri[2][1]-tri[0][1],tri[2][2]-tri[0][2]);
  var n=a.cross(b);if(n.lengthSquared()<1e-12F)return;n.normalize();
  // Keep the authored facing: winding in these models is not consistent, the stored normal is.
  var old=new Vector3f(tri[0][5]+tri[1][5]+tri[2][5],tri[0][6]+tri[1][6]+tri[2][6],tri[0][7]+tri[1][7]+tri[2][7]);
  if(old.dot(n)<0)n.negate();
  for(var v:tri){v[5]=n.x;v[6]=n.y;v[7]=n.z;}
 }
 /** The charred skin over any group, cached; used for torn sections and debris that never had wreck parts. */
 public static McheliModel.Group sootOf(McheliModel.Group group){
  synchronized(SOOT){var s=SOOT.get(group);if(s==null){if(SOOT.size()>=256)SOOT.clear();s=soot(group);SOOT.put(group,s);}return s;}
 }
 /** Every triangle again, lifted off the hull, with UVs projected along its dominant axis so the char tiles evenly. */
 static McheliModel.Group soot(McheliModel.Group hull){
  var d=hull.data();float[] out=new float[d.length];
  for(int t=0;t+23<d.length;t+=24){
   float nx=0,ny=0,nz=0;for(int k=0;k<3;k++){nx+=d[t+k*8+5];ny+=d[t+k*8+6];nz+=d[t+k*8+7];}
   int axis=Math.abs(nx)>=Math.abs(ny)&&Math.abs(nx)>=Math.abs(nz)?0:Math.abs(ny)>=Math.abs(nz)?1:2;
   for(int k=0;k<3;k++){int i=t+k*8;
    for(int a=0;a<3;a++)out[i+a]=d[i+a]+d[i+5+a]*SOOT_LIFT;
    float u=axis==0?d[i+2]:d[i],w=axis==1?d[i+2]:d[i+1];
    out[i+3]=u*SOOT_SCALE;out[i+4]=w*SOOT_SCALE;
    for(int a=5;a<8;a++)out[i+a]=d[i+a];
   }
  }
  return new McheliModel.Group("$wreck_soot",out);
 }
 /**
  * Glowing patches in {@link #EMBER_TIERS} nested tiers: tier 0 is the smallest set, and each later
  * tier adds more. Patches gather at the engine end and the wing roots, where fuel burns longest.
  */
 static List<McheliModel.Group> embers(McheliModel.Group hull,Vector3f lo,Vector3f hi){
  var d=hull.data();var tiers=new ArrayList<List<Float>>();for(int i=0;i<EMBER_TIERS;i++)tiers.add(new ArrayList<>());
  float length=Math.max(.5F,hi.z-lo.z),half=Math.max(.25F,Math.max(Math.abs(lo.x),Math.abs(hi.x)));
  for(int t=0;t+23<d.length;t+=24){
   float cx=(d[t]+d[t+8]+d[t+16])/3,cy=(d[t+1]+d[t+9]+d[t+17])/3,cz=(d[t+2]+d[t+10]+d[t+18])/3;
   int cell=Math.floorMod((int)Math.floor(cx*1.4F)*92821^(int)Math.floor(cy*1.4F)*68917^(int)Math.floor(cz*1.4F)*30893,97);
   float rear=Math.clamp(1-(cz-lo.z)/(length*.35F),0,1),root=Math.clamp(1-Math.abs(Math.abs(cx)/half-.35F)/.2F,0,1);
   float chance=.04F+.30F*Math.max(rear,root*.8F);
   int tier=-1;for(int i=0;i<EMBER_TIERS;i++)if(cell<97*chance*(i+1)/EMBER_TIERS){tier=i;break;}
   if(tier<0)continue;var out=tiers.get(tier);if(out.size()>=6000*8)continue;
   for(int k=0;k<3;k++){int i=t+k*8;
    for(int a=0;a<3;a++)out.add(d[i+a]+d[i+5+a]*SOOT_LIFT*1.6F);
    out.add(d[i]*.37F+d[i+2]*.21F);out.add(d[i+1]*.37F+d[i+2]*.29F);
    for(int a=5;a<8;a++)out.add(d[i+a]);
   }
  }
  var groups=new ArrayList<McheliModel.Group>();for(var tier:tiers)groups.add(group("$wreck_embers",tier));return List.copyOf(groups);
 }
 /** Up to {@link #MAX_CHUNKS} compact pieces of skin from separate cells of the hull, for the debris field. */
 static List<Chunk> chunks(McheliModel.Group hull,Vector3f lo,Vector3f hi){
  var d=hull.data();float cell=Math.max(.6F,Math.max(hi.z-lo.z,hi.x-lo.x)/8);var cells=new TreeMap<Long,List<Integer>>();
  for(int t=0;t+23<d.length;t+=24){
   // Long slivers of skin read as sheets, not fragments: keep triangles about a cell across.
   float edge=0;for(int k=0;k<3;k++){int i=t+k*8,j=t+(k+1)%3*8;edge=Math.max(edge,new Vector3f(d[i]-d[j],d[i+1]-d[j+1],d[i+2]-d[j+2]).length());}
   if(edge>cell*1.2F)continue;
   long key=(long)Math.floor((d[t]+d[t+8]+d[t+16])/3/cell)*1_000_003L+(long)Math.floor((d[t+1]+d[t+9]+d[t+17])/3/cell)*1_009L
           +(long)Math.floor((d[t+2]+d[t+10]+d[t+18])/3/cell);
   cells.computeIfAbsent(key,k->new ArrayList<>()).add(t);
  }
  var picks=new ArrayList<Map.Entry<Long,List<Integer>>>();
  for(var e:cells.entrySet())if(e.getValue().size()>=6&&e.getValue().size()<=300)picks.add(e);
  picks.sort(Comparator.comparingInt(e->Long.hashCode(e.getKey()*0x9E3779B97F4A7C15L)));
  var out=new ArrayList<Chunk>();
  for(var e:picks){
   if(out.size()>=MAX_CHUNKS)break;
   var c=new Vector3f(Float.POSITIVE_INFINITY);var h=new Vector3f(Float.NEGATIVE_INFINITY);
   for(int t:e.getValue())for(int k=0;k<3;k++){int i=t+k*8;var p=new Vector3f(d[i],d[i+1],d[i+2]);c.min(p);h.max(p);}
   var size=new Vector3f(h).sub(c);if(size.x<.15F&&size.y<.15F||size.y<.15F&&size.z<.15F||size.x<.15F&&size.z<.15F)continue;
   var center=new Vector3f(c).add(h).mul(.5F);float[] data=new float[e.getValue().size()*24];int o=0;
   for(int t:e.getValue())for(int k=0;k<3;k++){int i=t+k*8;
    data[o++]=d[i]-center.x;data[o++]=d[i+1]-center.y;data[o++]=d[i+2]-center.z;for(int a=3;a<8;a++)data[o++]=d[i+a];}
   out.add(new Chunk(new McheliModel.Group("$wreck_chunk",data),center,size.max(new Vector3f(.12F))));
  }
  return List.copyOf(out);
 }
 private static McheliModel.Group group(String name,List<Float> v){float[] d=new float[v.size()];for(int i=0;i<d.length;i++)d[i]=v.get(i);return new McheliModel.Group(name,d);}
 public static void clear(){synchronized(CACHE){CACHE.clear();}synchronized(SOOT){SOOT.clear();}}
}
