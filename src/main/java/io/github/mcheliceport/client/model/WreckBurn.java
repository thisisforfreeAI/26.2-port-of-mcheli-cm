package io.github.mcheliceport.client.model;
import java.util.*;
import java.util.concurrent.*;
import org.joml.Vector3f;
/**
 * How far each part of a wreck's skin burnt, from where the fire was. Heat is greatest at the fire
 * sources (engines, the wing roots where the fuel is) and falls off over a few blocks with a ragged
 * edge, so the nose, tail and tips keep most of their paint. Three layers share that heat:
 * <ul>
 * <li>char, black and heaviest at the fire, a light film elsewhere;</li>
 * <li>bare metal, a ring around the char where the paint blistered and peeled;</li>
 * <li>heat tint, a narrow bronze band on the hot side of the bare metal.</li>
 * </ul>
 * Each layer carries a weight per vertex that scales its opacity. Faces are split down to
 * {@link #MAX_EDGE} first, so the gradient is smooth on low-poly hulls instead of following big triangles.
 */
public final class WreckBurn {
 /** A layer's triangles and, per vertex, how strongly it shows (0 to 1). */
 public record Layer(McheliModel.Group group,float[] weight){}
 public record Burn(Layer charred,Layer metal,Layer heat){}
 static final float MAX_EDGE=.75F,FLOOR=.12F;
 /** Heat at which the bare metal and the heat tint peak, and how far either side they fade out. */
 static final float METAL_AT=.34F,METAL_WIDTH=.16F,TINT_AT=.52F,TINT_WIDTH=.11F;
 static final int MAX_VERTICES=90000;
 private record Key(McheliModel.Group group,List<Long> sources,int radius){}
 private static final Map<Key,CompletableFuture<Burn>> CACHE=new LinkedHashMap<>(16,.75F,true);
 private static final ExecutorService LOADER=Executors.newSingleThreadExecutor(r->{var t=new Thread(r,"mcheli-wreck-burn");t.setDaemon(true);return t;});
 private WreckBurn(){}
 /**
  * The burn of {@code soot} (a charred-skin group from {@link WreckMesh}) around {@code sources}, in
  * the group's model space. Built in the background: null until ready.
  */
 public static Burn get(McheliModel.Group soot,List<Vector3f> sources,float radius){
  if(soot.vertexCount()<3||sources.isEmpty())return null;
  // Sources and radius are rounded so a hulk's tiny sync movements reuse the same burn.
  var keys=new ArrayList<Long>();for(var s:sources)keys.add(Math.round(s.x*2)*1_000_003L*1_009L+Math.round(s.y*2)*1_009L+Math.round(s.z*2));
  var key=new Key(soot,List.copyOf(keys),Math.round(radius*2));CompletableFuture<Burn> f;
  synchronized(CACHE){f=CACHE.get(key);if(f==null){
   if(CACHE.size()>=32){var it=CACHE.entrySet().iterator();while(it.hasNext())if(it.next().getValue().isDone()){it.remove();break;}}
   f=new CompletableFuture<>();CACHE.put(key,f);var result=f;var at=List.copyOf(sources);
   LOADER.execute(()->{Burn b=null;try{b=build(soot,at,radius);}finally{result.complete(b);}});
  }}return f.getNow(null);
 }
 private record Shown(List<Vector3f> sources,float radius,Burn burn){}
 private static final Map<McheliModel.Group,Shown> SHOWN=new WeakHashMap<>();
 /**
  * As {@link #get}, but a part keeps the burn it last showed while its fire stays within half a block
  * (one key step) of where it was, and while a moved fire's new burn is still building. Pose jitter at a
  * rounding edge otherwise swapped burns, and every swap fell back to the old full-hull char meanwhile.
  */
 public static Burn stable(McheliModel.Group soot,List<Vector3f> sources,float radius){
  Shown last;synchronized(SHOWN){last=SHOWN.get(soot);}
  if(last!=null&&near(last.sources(),sources,.5F)&&Math.abs(last.radius()-radius)<.5F)return last.burn();
  var burn=get(soot,sources,radius);
  if(burn==null)return last==null?null:last.burn();
  var copy=new ArrayList<Vector3f>();for(var s:sources)copy.add(new Vector3f(s));
  synchronized(SHOWN){SHOWN.put(soot,new Shown(copy,radius,burn));}
  return burn;
 }
 static boolean near(List<Vector3f> a,List<Vector3f> b,float within){
  if(a.size()!=b.size())return false;
  for(int i=0;i<a.size();i++)if(a.get(i).distanceSquared(b.get(i))>=within*within)return false;
  return true;
 }
 /** The char's opacity away from the fire, for a hull whose first burn is still building. */
 public static int waiting(int charColor){return Math.round((charColor>>>24)*FLOOR)<<24|charColor&0xFFFFFF;}
 public static void clear(){synchronized(CACHE){CACHE.clear();}synchronized(SHOWN){SHOWN.clear();}}
 /** Fire radius for an airframe of this size: a fifth of it, never under 2.5 blocks. */
 public static float radius(float size){return Math.max(2.5F,size*.2F);}
 /**
  * Where a plane's fuel burns besides the engines: the wing roots, a third of the way out along the
  * span, at the wing's own height and position. None when the hull has no wings to speak of.
  */
 public static List<Vector3f> wingRoots(McheliModel.Group hull){
  var d=hull.data();float half=0;for(int i=0;i+7<d.length;i+=8)half=Math.max(half,Math.abs(d[i]));
  if(half<2)return List.of();
  float y=0,z=0;int n=0;
  for(int i=0;i+7<d.length;i+=8)if(Math.abs(d[i])>half*.5F){y+=d[i+1];z+=d[i+2];n++;}
  if(n==0)return List.of();y/=n;z/=n;
  return List.of(new Vector3f(half*.32F,y,z),new Vector3f(-half*.32F,y,z));
 }
 /** Heat at a point: 1 at a source, 0 beyond the radius; the edge wanders by up to a third. */
 static float heat(float x,float y,float z,List<Vector3f> sources,float radius){
  float best=Float.POSITIVE_INFINITY;
  for(var s:sources){float dx=x-s.x,dy=y-s.y,dz=z-s.z;best=Math.min(best,dx*dx+dy*dy+dz*dz);}
  float ragged=1+.35F*WreckMesh.noise(x*.7F,y*.7F,z*.7F)+.12F*WreckMesh.noise(x*2.1F+31,y*2.1F,z*2.1F);
  float t=Math.clamp(1-(float)Math.sqrt(best)*ragged/radius,0,1);
  return t*t*(3-2*t);
 }
 /** Opacity of a band of heat centred on {@code centre}, falling to 0 {@code width} either side. */
 static float band(float heat,float centre,float width){float t=Math.clamp(1-Math.abs(heat-centre)/width,0,1);return t*t*(3-2*t);}
 static Burn build(McheliModel.Group soot,List<Vector3f> sources,float radius){
  float edge=MAX_EDGE;float[] d=split(soot.data(),edge);
  while(d.length/McheliModel.STRIDE>MAX_VERTICES&&edge<8){edge*=1.6F;d=split(soot.data(),edge);}
  int count=d.length/McheliModel.STRIDE;float[] heat=new float[count];
  for(int v=0;v<count;v++){int i=v*McheliModel.STRIDE;heat[v]=heat(d[i],d[i+1],d[i+2],sources,radius);}
  float[] charred=new float[count],metal=new float[count],tint=new float[count];
  for(int v=0;v<count;v++){
   float core=Math.clamp((heat[v]-.3F)/.45F,0,1);charred[v]=FLOOR+(1-FLOOR)*core*core*(3-2*core);
   metal[v]=band(heat[v],METAL_AT,METAL_WIDTH);
   tint[v]=band(heat[v],TINT_AT,TINT_WIDTH);
  }
  return new Burn(new Layer(new McheliModel.Group("$wreck_char",d),charred),layer("$wreck_bare",d,metal),layer("$wreck_heat",d,tint));
 }
 /** Only the triangles where the layer shows at all. */
 private static Layer layer(String name,float[] d,float[] weight){
  int stride=McheliModel.STRIDE,tri=3*stride;var out=new ArrayList<float[]>();var w=new ArrayList<Float>();
  for(int t=0;t+tri<=d.length;t+=tri){
   int v=t/stride;if(weight[v]<.02F&&weight[v+1]<.02F&&weight[v+2]<.02F)continue;
   out.add(Arrays.copyOfRange(d,t,t+tri));for(int k=0;k<3;k++)w.add(weight[v+k]);
  }
  float[] data=new float[out.size()*tri];for(int i=0;i<out.size();i++)System.arraycopy(out.get(i),0,data,i*tri,tri);
  float[] weights=new float[w.size()];for(int i=0;i<weights.length;i++)weights[i]=w.get(i);
  return new Layer(new McheliModel.Group(name,data),weights);
 }
 /** Splits every triangle in half across its longest edge until no edge is longer than {@code max}. */
 static float[] split(float[] d,float max){
  int stride=McheliModel.STRIDE;var out=new ArrayList<float[]>();var stack=new ArrayDeque<float[][]>();
  for(int t=0;t+3*stride<=d.length;t+=3*stride){
   stack.push(new float[][]{Arrays.copyOfRange(d,t,t+stride),Arrays.copyOfRange(d,t+stride,t+2*stride),Arrays.copyOfRange(d,t+2*stride,t+3*stride)});
   while(!stack.isEmpty()){
    var tri=stack.pop();int longest=-1;float length=max*max;
    for(int k=0;k<3;k++){var a=tri[k];var b=tri[(k+1)%3];float dx=a[0]-b[0],dy=a[1]-b[1],dz=a[2]-b[2],l=dx*dx+dy*dy+dz*dz;if(l>length){length=l;longest=k;}}
    if(longest<0){for(var v:tri)out.add(v);continue;}
    var a=tri[longest];var b=tri[(longest+1)%3];var c=tri[(longest+2)%3];
    var m=new float[stride];for(int i=0;i<stride;i++)m[i]=(a[i]+b[i])*.5F;
    // Normals are interpolated, then renormalised.
    float n=(float)Math.sqrt(m[5]*m[5]+m[6]*m[6]+m[7]*m[7]);if(n>1e-6F){m[5]/=n;m[6]/=n;m[7]/=n;}
    stack.push(new float[][]{a,m,c});stack.push(new float[][]{m,b,c});
   }
  }
  float[] data=new float[out.size()*stride];for(int i=0;i<out.size();i++)System.arraycopy(out.get(i),0,data,i*stride,stride);
  return data;
 }
}
