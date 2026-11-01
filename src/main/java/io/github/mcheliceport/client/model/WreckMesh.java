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
 /**
  * A big structural piece a crashed hull broke into: the forward fuselage ({@link #NOSE}), the outer
  * left wing ({@link #WING}), or on a flying wing too wide to lose its nose, the right half of the centre
  * body ({@link #SPLIT}). It is drawn in the hull's model space, moved by its break pose about
  * {@code hinge}, a point on the bottom of its cut. {@code drop} is how far it falls to the hull's belly.
  * The lining and cap close its own side of the cut.
  */
 public record Section(int kind,McheliModel.Group shape,McheliModel.Group paint,McheliModel.Group soot,List<McheliModel.Group> embers,
         McheliModel.Group lining,McheliModel.Group cap,Vector3f hinge,float drop){
  /**
   * Whether a point in the hull's model space lies on this section's side of its cut, so that a part
   * mounted there (a propeller, a turret, a gun) goes with the section instead of hanging in the air.
   */
  public boolean holds(Vector3f p){return kind==NOSE?p.z>hinge.z:kind==WING?p.x<hinge.x:p.x>hinge.x;}
 }
 public static final int NOSE=0,WING=1,SPLIT=2;
 /**
  * {@code shape} is what stays of the hull; when it broke into {@code sections}, {@code lining} and
  * {@code cap} close the hull's side of those cuts.
  */
 public record Parts(McheliModel.Group shape,McheliModel.Group paint,McheliModel.Group metal,Vector3f center,
         McheliModel.Group soot,List<McheliModel.Group> embers,List<Chunk> chunks,List<Section> sections,
         McheliModel.Group lining,McheliModel.Group cap){
  public Parts(McheliModel.Group shape,McheliModel.Group paint,McheliModel.Group metal,Vector3f center){this(shape,paint,metal,center,group("$wreck_soot",List.of()),List.of(),List.of());}
  public Parts(McheliModel.Group shape,McheliModel.Group paint,McheliModel.Group metal,Vector3f center,
          McheliModel.Group soot,List<McheliModel.Group> embers,List<Chunk> chunks){this(shape,paint,metal,center,soot,embers,chunks,List.of(),group("$fracture_lining",List.of()),group("$fracture_cavity",List.of()));}
 }
 private record Key(McheliModel.Group group,boolean wing,boolean leftWing){}
 private static final Map<Key,CompletableFuture<Parts>> CACHE=new LinkedHashMap<>(16,.75F,true);
 private static final Map<McheliModel.Group,McheliModel.Group> SOOT=new WeakHashMap<>();
 private static final ExecutorService LOADER=Executors.newSingleThreadExecutor(r->{var t=new Thread(r,"mcheli-wreck-mesh");t.setDaemon(true);return t;});
 private static int pending;
 /** UV repeats per block of charred skin, and how far the layer sits off the hull. */
 static final float SOOT_SCALE=.9F,SOOT_LIFT=.007F;
 public static final int EMBER_TIERS=3,MAX_CHUNKS=14;
 /** Longest triangle edge that may carry a burnt-paint or bare-metal patch, in blocks. */
 static final float PATCH_EDGE=1.5F;
 /** Deepest wrinkle, furthest the nose is crushed back and furthest the front fuselage droops, in blocks. */
 static final float WRINKLE=.16F,CRUSH=1.3F,DROOP=.6F;
 /** Most vertices a broken-off section may have; a bigger one stays on the hull. */
 static final int SECTION_LIMIT=12000;
 public static Parts get(McheliModel.Group source,boolean wing){return get(source,wing,false);}
 /**
  * A wreck part, built in the background (null until ready). For a crashed plane's hull ({@code wing})
  * the forward fuselage breaks off, and with {@code leftWing} (the hull still has its whole left wing)
  * so does the outer left wing.
  */
 public static Parts get(McheliModel.Group source,boolean wing,boolean leftWing){
  var key=new Key(source,wing,wing&&leftWing);CompletableFuture<Parts> f;
  synchronized(CACHE){f=CACHE.get(key);if(f==null){
   if(pending>=4||source.vertexCount()<3||source.vertexCount()>60000)return null;
   if(CACHE.size()>=24){var it=CACHE.entrySet().iterator();boolean removed=false;while(it.hasNext())if(it.next().getValue().isDone()){it.remove();removed=true;break;}if(!removed)return null;}
   f=new CompletableFuture<>();CACHE.put(key,f);pending++;var result=f;
   LOADER.execute(()->{Parts p=null;try{p=build(source,wing,wing,wing,key.leftWing());
     var groups=new ArrayList<McheliModel.Group>();groups.add(p.shape());for(var s:p.sections())groups.add(s.shape());
     var m=new McheliModel("wreck",groups,0,0,0,0,0,0);VehicleSurfaceShade.prepare(m);VehicleImpactSurface.prepare(m);}
    finally{result.complete(p);synchronized(CACHE){pending--;}}});
  }}return f.getNow(null);
 }
 public static Parts build(McheliModel.Group source,boolean wing){return build(source,wing,false);}
 /** {@code crumple}: a crashed plane's hull, nose crushed back and down, front fuselage drooping, skin wrinkled. */
 public static Parts build(McheliModel.Group source,boolean wing,boolean crumple){return build(source,wing,crumple,false,false);}
 /**
  * As above; with {@code breakup} the crashed hull also breaks into sections: the forward fuselage
  * where it is still a narrow tube, and with {@code leftWing} the outer left wing.
  */
 public static Parts build(McheliModel.Group source,boolean wing,boolean crumple,boolean breakup,boolean leftWing){
  var d=source.data();var lo=new Vector3f(Float.POSITIVE_INFINITY);var hi=new Vector3f(Float.NEGATIVE_INFINITY);
  for(int i=0;i+7<d.length;i+=8){var v=new Vector3f(d[i],d[i+1],d[i+2]);lo.min(v);hi.max(v);}
  var shape=new ArrayList<Float>();float extent=Math.max(Math.abs(lo.x),Math.abs(hi.x));
  for(int t=0;t+23<d.length;t+=24){
   // Every triangle is kept: dropping every third one at a wing tip, to look torn, punched holes through
   // the skin there that read as a see-through, glassy edge.
   float[][] tri=new float[3][];boolean crushed=false;
   for(int k=0;k<3;k++){int i=t+k*8;float[] v=Arrays.copyOfRange(d,i,i+8);
    if(wing&&extent>3){float w=Math.clamp((Math.abs(v[0])/extent-.65F)/.35F,0,1);v[1]-=w*w*.55F;}
    if(crumple)crushed|=crumple(v,lo,hi);
    tri[k]=v;
   }
   // Crushed skin is faceted: each triangle takes its own face normal instead of the smooth one.
   if(crushed)faceNormal(tri);
   for(var v:tri)for(float a:v)shape.add(a);
  }
  var hull=group(source.name(),shape);var center=new Vector3f(lo).add(hi).mul(.5F);
  var sections=new ArrayList<Section>();var linings=new ArrayList<McheliModel.Group>();var caps=new ArrayList<McheliModel.Group>();
  var cuts=new ArrayList<float[]>();
  if(crumple&&breakup){
   float belly=lo.y;
   // The outer left wing: cut where the airframe cuts it in flight, so the two breakups line up.
   if(leftWing&&-lo.x>2.6F&&hi.x-lo.x>5){
    float cut=lo.x*.55F;var halves=BreakupMeshes.split(hull,0,cut);
    if(halves[0].vertexCount()>=12&&halves[0].vertexCount()<=SECTION_LIMIT&&halves[1].vertexCount()>=12){
     var foot=foot(halves[0],0,cut);
     sections.add(section(WING,halves[0],0,cut,1,new Vector3f(cut,foot[0],foot[1]),Math.clamp(foot[0]-belly,0,1.5F),lo,hi));
     var shell=FractureInteriorMeshes.shell(halves[1],0,cut,-1,4096);linings.add(shell.lining());caps.add(shell.cap());
     hull=halves[1];cuts.add(new float[]{0,cut});
    }
   }
   float cut=noseCut(hull,lo,hi);
   if(Float.isFinite(cut)){
    var halves=BreakupMeshes.split(hull,2,cut);
    if(halves[1].vertexCount()>=12&&halves[1].vertexCount()<=SECTION_LIMIT&&halves[0].vertexCount()>=12){
     var foot=foot(halves[1],2,cut);
     sections.add(section(NOSE,halves[1],2,cut,-1,new Vector3f(0,foot[0],cut),0,lo,hi));
     var shell=FractureInteriorMeshes.shell(halves[0],2,cut,1,4096);linings.add(shell.lining());caps.add(shell.cap());
     hull=halves[0];cuts.add(new float[]{2,cut});
    }
   }
   // A flying wing (or anything else too wide to shed its nose) splits through the centre body instead,
   // just to the right of the cockpit, so it does not lie there as one huge slab.
   var core=BreakupMeshes.bounds(hull);
   // Only a real flying wing or delta: a conventional plane whose nose did not come
   // off stays in one piece here, instead of losing half its fuselage with the engines on that side.
   if(cuts.stream().noneMatch(c->c[0]==2)&&core.high().x-core.low().x>8&&flyingWing(hull,lo,hi)){
    float split=Math.max(.6F,core.high().x*.12F);var halves=BreakupMeshes.split(hull,0,split);
    if(halves[1].vertexCount()>=12&&halves[1].vertexCount()<=SECTION_LIMIT&&halves[0].vertexCount()>=12){
     var foot=foot(halves[1],0,split);
     sections.add(section(SPLIT,halves[1],0,split,-1,new Vector3f(split,foot[0],foot[2]),0,lo,hi));
     var shell=FractureInteriorMeshes.shell(halves[0],0,split,1,4096);linings.add(shell.lining());caps.add(shell.cap());
     hull=halves[0];cuts.add(new float[]{0,split});
    }
   }
   hull=new McheliModel.Group(source.name(),hull.data());
  }
  var patches=patches(hull);
  return new Parts(hull,patches[0],patches[1],center,soot(hull),embers(hull,lo,hi,cuts),crumple?chunks(hull,lo,hi):List.of(),
          List.copyOf(sections),join("$fracture_lining",linings),join("$fracture_cavity",caps));
 }
 private static Section section(int kind,McheliModel.Group shape,int axis,float cut,int outward,Vector3f hinge,float drop,Vector3f lo,Vector3f hi){
  var named=new McheliModel.Group(kind==NOSE?"$wreck_nose":kind==WING?"$wreck_wing":"$wreck_centre",shape.data());
  var shell=FractureInteriorMeshes.shell(named,axis,cut,outward,2048);
  List<float[]> cuts=new ArrayList<>();cuts.add(new float[]{axis,cut});
  return new Section(kind,named,patches(named)[0],soot(named),embers(named,lo,hi,cuts),shell.lining(),shell.cap(),hinge,drop);
 }
 /** Along a cut: its lowest point, its rearmost and its foremost, {y, z low, z high}. */
 private static float[] foot(McheliModel.Group g,int axis,float cut){
  var d=g.data();float y=Float.POSITIVE_INFINITY,z=Float.POSITIVE_INFINITY,front=Float.NEGATIVE_INFINITY;
  for(int i=0;i+7<d.length;i+=8)if(Math.abs(d[i+axis]-cut)<.01F){y=Math.min(y,d[i+1]);z=Math.min(z,d[i+2]);front=Math.max(front,d[i+2]);}
  if(!Float.isFinite(y)){var b=BreakupMeshes.bounds(g);y=b.low().y;z=b.low().z;front=b.high().z;}
  return new float[]{y,z,front};
 }
 /**
  * Where a crashed hull's forward fuselage snaps off: the furthest-aft station, 16% to 34% of the length
  * back from the nose, where the hull is still a narrow tube (no wider than twice the nose and at most
  * 1.6 blocks or 7.5% of the length either side, or no wider than the nose itself and a third). On a flying wing or a delta the leading edges are already wide there, and the
  * nose stays on: NaN.
  */
 static float noseCut(McheliModel.Group hull,Vector3f lo,Vector3f hi){
  float length=hi.z-lo.z;if(length<5)return Float.NaN;
  float nose=Math.max(.35F,halfWidth(hull,hi.z-length*.08F));
  // A bomber's or an airliner's fuselage is wider than a fighter's, and may be no wider than its own
  // nose: a fixed 1.6-block limit missed all of them, and they split down the middle like a flying wing.
  float tube=Math.max(Math.min(Math.max(1.6F,length*.075F),nose*2),nose*1.3F);
  for(int i=0;i<=9;i++){
   float z=hi.z-length*(.34F-i*.02F),w=halfWidth(hull,z);
   if(w>0&&w<=tube)return z;
  }
  return Float.NaN;
 }
 /**
  * Whether a hull is a flying wing or a delta: from 17% to 34% of its length back from the nose its leading
  * edges sweep outward all the way, never narrowing, and over each half of that stretch by at least 0.3
  * blocks per block. A conventional plane has a fuselage tube there, or a straight wing that is just as
  * wide all along its chord.
  */
 static boolean flyingWing(McheliModel.Group hull,Vector3f lo,Vector3f hi){
  float length=hi.z-lo.z;if(length<5)return false;
  float[] w=new float[9];for(int i=0;i<9;i++)w[i]=halfWidth(hull,hi.z-length*(.17F+i*.02125F));
  for(int i=1;i<9;i++)if(w[i]<w[i-1]-.05F)return false;
  float sweep=.3F*length*.085F;
  return w[4]-w[0]>=sweep&&w[8]-w[4]>=sweep;
 }
 /** Widest |x| of the hull's cross-section at {@code z}: where its triangle edges cross that station. */
 static float halfWidth(McheliModel.Group hull,float z){
  var d=hull.data();float w=0;
  for(int t=0;t+23<d.length;t+=24)for(int k=0;k<3;k++){
   int a=t+k*8,b=t+(k+1)%3*8;float za=d[a+2],zb=d[b+2];
   if((za-z)*(zb-z)>0||za==zb)continue;
   float f=(z-za)/(zb-za);w=Math.max(w,Math.abs(d[a]+(d[b]-d[a])*f));
  }
  return w;
 }
 /** Burnt-paint and bare-metal patches over a hull's small triangles: {paint, metal}. */
 static McheliModel.Group[] patches(McheliModel.Group hull){
  var d=hull.data();var paint=new ArrayList<Float>();var metal=new ArrayList<Float>();
  for(int t=0;t+23<d.length;t+=24){
   float cx=(d[t]+d[t+8]+d[t+16])/3,cz=(d[t+2]+d[t+10]+d[t+18])/3;
   int hash=Float.floatToIntBits((float)Math.floor(cx*1.7))^Integer.rotateLeft(Float.floatToIntBits((float)Math.floor(cz*1.7)),11);
   // A patch is one whole triangle: on a big low-poly face that is a flat, sharp-edged triangle of bare metal.
   var patch=edge(d,t)>PATCH_EDGE?null:Math.floorMod(hash,13)<3?paint:Math.floorMod(hash,19)==5?metal:null;
   if(patch==null||patch.size()>=9600)continue;
   for(int k=0;k<3;k++){int i=t+k*8;
    for(int a=0;a<3;a++)patch.add(d[i+a]+d[i+5+a]*.0095F);
    for(int a=3;a<8;a++)patch.add(a==3&&patch==metal?d[i]:a==4&&patch==metal?d[i+2]:d[i+a]);
   }
  }
  return new McheliModel.Group[]{group("$wreck_paint",paint),group("$wreck_metal",metal)};
 }
 private static McheliModel.Group join(String name,List<McheliModel.Group> groups){
  int n=0;for(var g:groups)n+=g.data().length;float[] out=new float[n];n=0;
  for(var g:groups){System.arraycopy(g.data(),0,out,n,g.data().length);n+=g.data().length;}
  return new McheliModel.Group(name,out);
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
  y-=Math.min((z-bend)*.07F*t,DROOP);
  z-=s*s*Math.min(.07F*length,CRUSH);y-=s*s*Math.min(.03F*length,CRUSH*.45F);y=cy+(y-cy)*(1-.22F*s);x*=1+.16F*s;
  float rx=v[0],ry=v[1]-cy;float r=(float)Math.sqrt(rx*rx+ry*ry);
  // Wrinkles are dents in sheet metal, a few hands deep at most: scaled by the whole airframe's length
  // they reached a block on a bomber, and neighbouring corners shot apart into spikes.
  float wrinkle=noise(v[0]*2.3F,v[1]*2.3F,v[2]*2.3F)*Math.min((.006F+.022F*s)*length,Math.min(WRINKLE,r*.12F));
  if(r>1e-3F){x+=rx/r*wrinkle*Math.min(1,height);y+=ry/r*wrinkle*Math.min(1,height);}
  v[0]=x;v[1]=y;v[2]=z;return s>0;
 }
 private static final Map<McheliModel.Group,Vector3f[]> BOUNDS=new WeakHashMap<>();
 /**
  * Where a point on a crashed plane's hull ends up once the hull is wrecked ({@link #get} with {@code wing}):
  * outer wings droop and the nose is crushed. Parts mounted there (turrets, guns, propellers) move with it,
  * instead of floating above the crushed skin.
  */
 public static Vector3f wrecked(McheliModel.Group hull,Vector3f p){
  Vector3f[] b;synchronized(BOUNDS){b=BOUNDS.get(hull);if(b==null){
   var lo=new Vector3f(Float.POSITIVE_INFINITY);var hi=new Vector3f(Float.NEGATIVE_INFINITY);var d=hull.data();
   for(int i=0;i+7<d.length;i+=8){var v=new Vector3f(d[i],d[i+1],d[i+2]);lo.min(v);hi.max(v);}
   b=new Vector3f[]{lo,hi};if(BOUNDS.size()>=64)BOUNDS.clear();BOUNDS.put(hull,b);}}
  if(!b[0].isFinite())return new Vector3f(p);
  float[] v={p.x,p.y,p.z,0,0,0,1,0};float extent=Math.max(Math.abs(b[0].x),Math.abs(b[1].x));
  if(extent>3){float w=Math.clamp((Math.abs(v[0])/extent-.65F)/.35F,0,1);v[1]-=w*w*.55F;}
  crumple(v,b[0],b[1]);return new Vector3f(v[0],v[1],v[2]);
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
 static float edge(float[] d,int t){
  float edge=0;for(int k=0;k<3;k++){int i=t+k*8,j=t+(k+1)%3*8;edge=Math.max(edge,new Vector3f(d[i]-d[j],d[i+1]-d[j+1],d[i+2]-d[j+2]).length());}return edge;
 }
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
 static List<McheliModel.Group> embers(McheliModel.Group hull,Vector3f lo,Vector3f hi){return embers(hull,lo,hi,List.of());}
 /**
  * Glowing patches in {@link #EMBER_TIERS} nested tiers: tier 0 is the smallest set, and each later
  * tier adds more. Patches gather where fuel burns longest: the engine end, around the two wing roots
  * (not along the whole span, which on a flying wing lit up the entire airframe), and along the torn
  * edges of {@code cuts} ({axis, position} pairs), where the structure split open over the fire.
  */
 static List<McheliModel.Group> embers(McheliModel.Group hull,Vector3f lo,Vector3f hi,List<float[]> cuts){
  var d=hull.data();var tiers=new ArrayList<List<Float>>();for(int i=0;i<EMBER_TIERS;i++)tiers.add(new ArrayList<>());
  float length=Math.max(.5F,hi.z-lo.z),half=Math.max(.25F,Math.max(Math.abs(lo.x),Math.abs(hi.x)));
  float rootX=half*.32F,rootZ=(lo.z+hi.z)*.5F,reach=Math.max(1.2F,half*.18F);
  for(int t=0;t+23<d.length;t+=24){
   float cx=(d[t]+d[t+8]+d[t+16])/3,cy=(d[t+1]+d[t+9]+d[t+17])/3,cz=(d[t+2]+d[t+10]+d[t+18])/3;
   int cell=Math.floorMod((int)Math.floor(cx*1.4F)*92821^(int)Math.floor(cy*1.4F)*68917^(int)Math.floor(cz*1.4F)*30893,97);
   float rear=Math.clamp(1-(cz-lo.z)/(length*.3F),0,1);
   float root=half<2?0:Math.clamp(1-(float)Math.hypot(Math.abs(cx)-rootX,(cz-rootZ)*.6F)/reach,0,1);
   float torn=0;for(var c:cuts){float at=c[0]==0?cx:cz;torn=Math.max(torn,Math.clamp(1-Math.abs(at-c[1])/.8F,0,1));}
   float chance=.015F+.30F*Math.max(rear,Math.max(root*.8F,torn));
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
 /**
  * Up to {@link #MAX_CHUNKS} pieces of skin from separate cells of the hull, for the debris field. Each is
  * the hull cut to one small cell, so a piece is a bent patch of skin no larger than the cell. Whole
  * triangles are not used: on a low-poly hull they are long slivers and nose-cone fans, which tumble
  * through the air as stretched spikes.
  */
 static List<Chunk> chunks(McheliModel.Group hull,Vector3f lo,Vector3f hi){
  var d=hull.data();float cell=chunkCell(lo,hi);var cells=new TreeMap<Long,int[]>();
  for(int t=0;t+23<d.length;t+=24){
   int x=(int)Math.floor((d[t]+d[t+8]+d[t+16])/3/cell),y=(int)Math.floor((d[t+1]+d[t+9]+d[t+17])/3/cell),z=(int)Math.floor((d[t+2]+d[t+10]+d[t+18])/3/cell);
   cells.computeIfAbsent(x*1_000_003L+y*1_009L+z,k->new int[]{x,y,z,0})[3]++;
  }
  // Cells with some detail first (a seam, a frame, a curve), in a stable shuffled order.
  var picks=new ArrayList<Map.Entry<Long,int[]>>(cells.entrySet());
  picks.sort(Comparator.<Map.Entry<Long,int[]>>comparingInt(e->e.getValue()[3]>=4?0:1).thenComparingInt(e->Long.hashCode(e.getKey()*0x9E3779B97F4A7C15L)));
  var out=new ArrayList<Chunk>();int tries=0;
  for(var e:picks){
   if(out.size()>=MAX_CHUNKS||++tries>MAX_CHUNKS*6)break;
   var c=e.getValue();float[] boxLo={c[0]*cell,c[1]*cell,c[2]*cell},boxHi={boxLo[0]+cell,boxLo[1]+cell,boxLo[2]+cell};
   var near=new ArrayList<Float>();
   for(int t=0;t+23<d.length;t+=24){
    boolean overlaps=true;
    for(int a=0;a<3&&overlaps;a++){float min=Math.min(d[t+a],Math.min(d[t+8+a],d[t+16+a])),max=Math.max(d[t+a],Math.max(d[t+8+a],d[t+16+a]));overlaps=max>=boxLo[a]&&min<=boxHi[a];}
    if(overlaps)for(int i=t;i<t+24;i++)near.add(d[i]);
   }
   var piece=BreakupMeshes.boxSplit(group("$wreck_chunk",near),boxLo,boxHi)[1].data();if(piece.length<24)continue;
   var min=new Vector3f(Float.POSITIVE_INFINITY);var max=new Vector3f(Float.NEGATIVE_INFINITY);
   for(int i=0;i+7<piece.length;i+=8){var p=new Vector3f(piece[i],piece[i+1],piece[i+2]);min.min(p);max.max(p);}
   var size=new Vector3f(max).sub(min);if(size.x<.15F&&size.y<.15F||size.y<.15F&&size.z<.15F||size.x<.15F&&size.z<.15F)continue;
   var center=new Vector3f(min).add(max).mul(.5F);float[] data=piece.clone();
   for(int i=0;i+7<data.length;i+=8){data[i]-=center.x;data[i+1]-=center.y;data[i+2]-=center.z;}
   data=crumpleChunk(data,size,Long.hashCode(e.getKey()*0x9E3779B97F4A7C15L));
   var bent=BreakupMeshes.bounds(new McheliModel.Group("$wreck_chunk",data));var shift=bent.center();
   for(int i=0;i+7<data.length;i+=8){data[i]-=shift.x;data[i+1]-=shift.y;data[i+2]-=shift.z;}
   var moved=new Vector3f(center).add(shift);
   out.add(new Chunk(new McheliModel.Group("$wreck_chunk",data),moved,new Vector3f(bent.high()).sub(bent.low()).max(new Vector3f(.12F))));
  }
  return List.copyOf(out);
 }
 /** Edge of the cells chunks are cut from: about a sixteenth of the airframe, 0.5 to 1.1 blocks. */
 static float chunkCell(Vector3f lo,Vector3f hi){return Math.clamp(Math.max(hi.z-lo.z,hi.x-lo.x)/16,.5F,1.1F);}
 /**
  * Bends a centred chunk like torn sheet metal instead of leaving it a flat cut slab: it is folded along a
  * crease across its middle (cut there first, so the crease is a clean line), by 35 to 80 degrees, then
  * dented. The fold axis lies in the chunk's broad plane; distances along the skin are kept.
  */
 static float[] crumpleChunk(float[] data,Vector3f size,int seed){
  var r=new java.util.Random(seed);
  int thin=size.x<=size.y&&size.x<=size.z?0:size.y<=size.z?1:2;
  int a=(thin+1+r.nextInt(2))%3;// the crease runs along axis c, and the fold lifts the side above it along a
  int c=3-thin-a;
  float crease=(r.nextFloat()-.5F)*.3F*size.get(a);
  var halves=BreakupMeshes.split(new McheliModel.Group("$wreck_chunk",data),a,crease);
  float angle=(float)Math.toRadians(35+r.nextFloat()*45)*(r.nextBoolean()?1:-1);
  var axis=new Vector3f();axis.setComponent(c,1);
  var turn=new org.joml.Quaternionf().fromAxisAngleRad(axis,angle);
  float[] low=halves[0].data(),high=halves[1].data().clone();
  for(int i=0;i+7<high.length;i+=8){
   var p=new Vector3f(high[i],high[i+1],high[i+2]);p.setComponent(a,p.get(a)-crease);turn.transform(p);p.setComponent(a,p.get(a)+crease);
   var n=turn.transform(new Vector3f(high[i+5],high[i+6],high[i+7]));
   high[i]=p.x;high[i+1]=p.y;high[i+2]=p.z;high[i+5]=n.x;high[i+6]=n.y;high[i+7]=n.z;
  }
  float[] out=new float[low.length+high.length];System.arraycopy(low,0,out,0,low.length);System.arraycopy(high,0,out,low.length,high.length);
  // Dents along the thin axis, by position, so corners shared between triangles stay together.
  float dent=Math.min(.08F,Math.max(size.get(a),size.get(c))*.06F);
  for(int i=0;i+7<out.length;i+=8)out[i+thin]+=noise(out[i]*3.1F+seed%17,out[i+1]*3.1F,out[i+2]*3.1F)*dent;
  return out;
 }
 private static McheliModel.Group group(String name,List<Float> v){float[] d=new float[v.size()];for(int i=0;i<d.length;i++)d[i]=v.get(i);return new McheliModel.Group(name,d);}
 public static void clear(){synchronized(CACHE){CACHE.clear();}synchronized(SOOT){SOOT.clear();}synchronized(BOUNDS){BOUNDS.clear();}}
}
