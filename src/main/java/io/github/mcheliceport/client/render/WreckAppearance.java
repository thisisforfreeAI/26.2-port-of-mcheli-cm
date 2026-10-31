package io.github.mcheliceport.client.render;
import io.github.mcheliceport.client.model.*;
import io.github.mcheliceport.client.particle.VehicleAtmosphere;
import io.github.mcheliceport.config.*;
import io.github.mcheliceport.entity.aircraft.AircraftEntity;
import java.util.*;
import net.minecraft.client.Minecraft;
import net.minecraft.resources.Identifier;
import net.minecraft.world.phys.Vec3;
import org.joml.Matrix4f;
import org.joml.Vector3f;
/**
 * Render-only breakup, restrained settling and burn. The paint chars over the first fire (a charred skin
 * layer thickens and the hull darkens), embers glow in its seams and fade as it cools, a crashed plane's
 * canopy shatters and its landing gear is torn off into the debris field. No entity pose or collision edits.
 */
public final class WreckAppearance {
 private static final class State {boolean seen,dead,glass;int removed;float restProgress;long restTick=-1;float drop=-1;Matrix4f rest=new Matrix4f();final Set<McheliModel.Group> scattered=Collections.newSetFromMap(new IdentityHashMap<>());}
 private static final Map<AircraftEntity,State> STATES=Collections.synchronizedMap(new WeakHashMap<>());
 private static final Identifier SOOT=Identifier.fromNamespaceAndPath("mcheli_ce_port","textures/effect/m8_fluid_stains.png");
 static final Identifier CHAR=Identifier.fromNamespaceAndPath("mcheli_ce_port","textures/effect/m8_wreck_char.png");
 static final Identifier EMBERS=Identifier.fromNamespaceAndPath("mcheli_ce_port","textures/effect/m8_wreck_embers.png");
 static final Identifier BARE=Identifier.fromNamespaceAndPath("mcheli_ce_port","textures/effect/m8_wreck_bare.png");
 /** Bare aluminium where the paint peeled, and the bronze heat tint on its hot side. */
 static final int BARE_TINT=0xB8B4AD,HEAT_TINT=0xC28A52;
 /**
  * Hull colour as the fire takes hold, and once the paint has burnt: the paint darkens and greys but
  * stays readable. The black is in the char layer, heaviest where the fire was.
  */
 static final int FRESH_TINT=0xFF928D86,BURNT_TINT=0xFF6C6862;
 private static java.lang.ref.WeakReference<Object> world=new java.lang.ref.WeakReference<>(null);
 public static boolean enabled(){return EffectQuality.enhanced()&&ClientConfig.get(ClientConfig.VEHICLE_DAMAGE_DETAILS);}
 public static float settling(AircraftEntity ac){int age=VehicleAtmosphere.wreckAge(ac);return io.github.mcheliceport.client.particle.WreckSequenceEffects.enabled()?io.github.mcheliceport.client.particle.DestructionTimeline.collapse(age):Math.clamp((age+1)/60F,0,1);}
 private static Matrix4f sag(AircraftEntity ac){
  if(io.github.mcheliceport.client.particle.WreckSequenceEffects.enabled()){
   float partial=Minecraft.getInstance().getDeltaTracker().getGameTimeDeltaPartialTick(false);
   var p=io.github.mcheliceport.client.particle.DestructionLook.settle(VehicleAtmosphere.wreckAge(ac)+partial,
           io.github.mcheliceport.client.particle.WreckRuptureEffects.kind(ac),ac.getUUID().hashCode());
   return new Matrix4f().translate(0,p.drop(),0).rotateZ(p.roll()).rotateX(p.pitch());
  }
  float s=settling(ac);return new Matrix4f().translate(0,-.10F*s,0).rotateZ(.025F*s).rotateX(-.018F*s);
 }
 public static Matrix4f root(AircraftEntity ac){var state=STATES.get(ac);var sag=sag(ac);return state==null||!state.dead?sag:new Matrix4f(state.rest).mul(sag);}
 /** Seconds-and-a-half to settle once a crashed plane has stopped sliding. */
 static final int SETTLE=30;
 /** The attitude a stopped wreck settles to: level, on its belly or its back, whichever is nearer. */
 public static float[] restAttitude(float pitch,float roll,float s){
  float r=net.minecraft.util.Mth.wrapDegrees(roll),target=Math.abs(r)>90?Math.copySign(180,r):0;s=Math.clamp(s,0,1);
  return new float[]{pitch*(1-s),r+(target-r)*s};
 }
 /**
  * Render-only correction from the attitude the wreck hit at (kept by the server) to its rest attitude,
  * lowered by its lost landing gear when upright; applied to the hull and everything posed with it.
  */
 static Matrix4f restCorrection(float yaw,float pitch,float roll,float s,float drop){
  var rest=restAttitude(pitch,roll,s);float y=io.github.mcheliceport.content.aircraft.AircraftInfo.GLOBAL_Y_OFFSET;
  var now=new Matrix4f().translate(0,y,0).rotateY((float)Math.toRadians(-yaw)).rotateX((float)Math.toRadians(pitch)).rotateZ((float)Math.toRadians(roll));
  var target=new Matrix4f().translate(0,y,0).rotateY((float)Math.toRadians(-yaw)).rotateX((float)Math.toRadians(rest[0])).rotateZ((float)Math.toRadians(rest[1]));
  boolean upright=Math.abs(rest[1])<90;
  return new Matrix4f().translate(0,upright?-drop*Math.clamp(s,0,1):0,0).mul(target).mul(now.invert());
 }
 /** How far the paint has burnt, 0.25 at the moment of destruction to 1 when the first fire is out. */
 public static float burn(int age){return age<0?1:Math.clamp(.25F+age/360F,0,1);}
 /** Ember glow: full while the wreck burns, then fading over the next 90 seconds. */
 public static float embers(int age){return age<0?0:age<600?1:Math.max(0,1-(age-600)/1800F);}
 public static int tint(){return 0xFF77736E;}
 public static int tint(AircraftEntity ac){return lerp(burn(VehicleAtmosphere.wreckAge(ac)),FRESH_TINT,BURNT_TINT);}
 static int lerp(float t,int a,int b){int out=0;for(int shift=0;shift<32;shift+=8){int x=a>>>shift&255,y=b>>>shift&255;out|=Math.round(x+(y-x)*Math.clamp(t,0,1))<<shift;}return out;}
 /** Charred skin opacity for a burn level: thin soot at first, nearly solid once burnt. */
 public static int charColor(float burn){return ((int)(255*(.42F+.53F*Math.clamp(burn,0,1)))<<24)|0xFFFFFF;}
 /** How far the paint has blistered off around the fire: none until it has burnt a while, full once burnt out. */
 static float blister(float burn){return Math.clamp((burn-.3F)/.55F,0,1);}
 /** Fire sources in a part's own model space: the engines (or where the fire was), plus a plane's wing roots. */
 static List<org.joml.Vector3f> sources(AircraftEntity ac,Matrix4f pose,WreckMesh.Parts mesh,boolean planeBody){
  var inverse=new Matrix4f(pose).invert();var out=new ArrayList<org.joml.Vector3f>();
  for(var origin:VehicleAtmosphere.wreckOrigins(ac)){var r=origin.subtract(ac.position());out.add(inverse.transformPosition(new org.joml.Vector3f((float)r.x,(float)r.y,(float)r.z)));}
  if(planeBody)out.addAll(WreckBurn.wingRoots(mesh.shape()));
  return out;
 }
 /** How far the hull drops once its gear is gone: from the gear's lowest point to the hull's, in blocks (0 to 2). */
 static float gearDrop(McheliModel.Group hull,List<AircraftPartPoses.Draw> gear){
  float body=Float.POSITIVE_INFINITY,wheels=Float.POSITIVE_INFINITY;var d=hull.data();
  for(int i=1;i<d.length;i+=8)body=Math.min(body,d[i]);
  // Both in model space: the gear's authored (lowered) position against the hull's belly.
  for(var draw:gear)for(var g:draw.groups()){var e=g.data();for(int i=1;i<e.length;i+=8)wheels=Math.min(wheels,e[i]);}
  if(!Float.isFinite(body)||!Float.isFinite(wheels))return 0;
  return Math.clamp(body-wheels,0,2);
 }
 /**
  * Whether a plane's hull still has its whole left wing (it was not torn off in flight): its skin
  * reaches out as far as the model's own left tip.
  */
 static boolean hasLeftWing(AircraftEntity ac,McheliModel.Group hull){
  var models=AircraftModels.peek(ac.getAcInfo());if(models==null)return false;
  float tip=models.body().minX;if(tip>-2.6F)return false;
  Float low;synchronized(LOW_X){low=LOW_X.get(hull);}
  if(low==null){float x=0;var d=hull.data();for(int i=0;i<d.length;i+=8)x=Math.min(x,d[i]);low=x;synchronized(LOW_X){LOW_X.put(hull,low);}}
  return low<=tip*.9F;
 }
 private static final Map<McheliModel.Group,Float> LOW_X=new WeakHashMap<>();
 private static final Map<McheliModel.Group,Vector3f> CENTERS=new WeakHashMap<>();
 /** The middle of a part's bounds in its own model space, cached. */
 private static Vector3f center(McheliModel.Group group){
  synchronized(CENTERS){var c=CENTERS.get(group);if(c==null){c=BreakupMeshes.bounds(group).center();CENTERS.put(group,c);}return c;}
 }
 /** Ticks the sections take to tear apart once the hull hits the ground. */
 static final int BREAK=10;
 /**
  * How far a section has torn away from the hull at {@code open} (0 joined, 1 come to rest), in the hull's
  * model space. The forward fuselage is shoved forward, slewed, rolled and nose-down into the ground;
  * the outer left wing is pulled out at the root and swept back about its trailing edge, then drops
  * to the belly; a flying wing's centre body comes apart in two. Angles come from the wreck's seed, so every crash breaks a little differently.
  */
 public static Matrix4f breakPose(WreckMesh.Section s,int seed,float open){
  var r=new java.util.Random(seed*31L+s.kind()*7919L);float e=Math.clamp(open,0,1);e=e*e*(3-2*e);
  var h=s.hinge();var m=new Matrix4f();
  if(s.kind()==WreckMesh.NOSE){
   float yaw=(8+r.nextFloat()*16)*(r.nextBoolean()?1:-1),roll=(5+r.nextFloat()*14)*(r.nextBoolean()?1:-1),pitch=3+r.nextFloat()*5,gap=.35F+r.nextFloat()*.55F;
   m.translate(h.x,h.y,h.z+gap*e).rotateY((float)Math.toRadians(yaw*e)).rotateZ((float)Math.toRadians(roll*e)).rotateX((float)Math.toRadians(pitch*e));
  }else if(s.kind()==WreckMesh.SPLIT){
   // The right half of a flying wing's centre body: shoved out and back, swung back about its leading
   // edge, and its outer edge buckled up off the ground.
   float swing=7+r.nextFloat()*9,buckle=2+r.nextFloat()*4,gap=.5F+r.nextFloat()*.5F,back=.4F+r.nextFloat()*.8F;
   m.translate(h.x+gap*e,h.y,h.z-back*e).rotateY((float)Math.toRadians(swing*e)).rotateZ((float)Math.toRadians(buckle*e));
  }else{
   float sweep=12+r.nextFloat()*18,tilt=(r.nextFloat()-.5F)*4,gap=.3F+r.nextFloat()*.5F;
   m.translate(h.x-gap*e,h.y-s.drop()*e,h.z).rotateY((float)Math.toRadians(-sweep*e)).rotateZ((float)Math.toRadians(tilt*e));
  }
  return m.translate(-h.x,-h.y,-h.z);
 }
 /** How far the sections have torn away: 0 until the hull hits the ground, 1 once they are at rest. */
 private static float open(WreckScene.Scene scene,long now){
  boolean down=scene!=null&&scene.impact!=null;
  return !down?0:scene.late?1:Math.clamp((now-scene.impactTick)/(float)BREAK,0,1);
 }
 private static int seed(AircraftEntity ac,WreckScene.Scene scene){return scene!=null?scene.seed:ac.getUUID().hashCode();}
 /**
  * The pose of a part mounted on a crashed plane's hull (a propeller, a gunner's turret, a gun): moved as
  * the skin under it was drooped and crushed, and with the section it sits on when that broke off.
  * {@code body} is the hull's own draw pose and {@code source} its unwrecked shape, so the part is placed
  * in the hull's model space where the wreck was shaped and its sections cut.
  */
 static Matrix4f follow(McheliModel.Group source,WreckMesh.Parts mesh,Matrix4f body,Matrix4f pose,Vector3f center,int seed,float open){
  var inverse=new Matrix4f(body).invert();var local=new Matrix4f(inverse).mul(pose).transformPosition(new Vector3f(center));
  if(!local.isFinite())return pose;
  var wrecked=WreckMesh.wrecked(source,local);var shift=new Vector3f(wrecked).sub(local);var out=new Matrix4f(body);
  if(mesh!=null&&open>0)for(var s:mesh.sections())if(s.holds(wrecked)){out.mul(breakPose(s,seed,open));break;}
  return out.translate(shift).mul(inverse).mul(pose);
 }
 /** Draws the sections a crashed hull broke into, each burnt, glowing and closed like the hull itself. */
 private static void sections(AircraftEntity ac,AircraftRenderState render,List<AircraftPartPoses.Draw> draws,WreckMesh.Parts mesh,Matrix4f bodyPose,Matrix4f pose,
         WreckScene.Scene scene,float burn,float glow,long now){
  float open=open(scene,now);int seed=seed(ac,scene);
  if(open>0&&!render.thermal){
   VehicleDetailLayers.require(render,pose,render.texture,List.of(mesh.lining()),PlaneAirframeParts.LINING);
   VehicleDetailLayers.require(render,pose,PlaneAirframeParts.METAL,List.of(mesh.cap()),PlaneAirframeParts.CAP);
  }
  float size=scene!=null&&scene.size()>1?scene.size():Math.max(4,ac.getBbWidth());
  for(var s:mesh.sections()){
   var moved=breakPose(s,seed,open);var at=new Matrix4f(pose).mul(moved);
   draws.add(new AircraftPartPoses.Draw(at,List.of(s.shape())));
   if(render.thermal)continue;
   VehicleDetailLayers.add(render,at,render.texture,List.of(s.paint()),(int)(0x70*(1.15F-.55F*burn))<<24|0x7E7769);
   if(open>0){
    VehicleDetailLayers.require(render,at,render.texture,List.of(s.lining()),PlaneAirframeParts.LINING);
    VehicleDetailLayers.require(render,at,PlaneAirframeParts.METAL,List.of(s.cap()),PlaneAirframeParts.CAP);
   }
   // The same fire as the hull, seen from the section where it now lies.
   var back=new Matrix4f(moved).invert();var local=new ArrayList<org.joml.Vector3f>();
   for(var p:sources(ac,bodyPose,mesh,true))local.add(back.transformPosition(new org.joml.Vector3f(p)));
   var fire=WreckBurn.stable(s.soot(),local,WreckBurn.radius(size));
   if(fire==null)VehicleDetailLayers.require(render,at,CHAR,List.of(s.soot()),WreckBurn.waiting(charColor(burn)));
   else{
    int bare=(int)(235*blister(burn));
    if(bare>0){
     VehicleDetailLayers.require(render,at,BARE,fire.metal().group(),bare<<24|BARE_TINT,fire.metal().weight());
     VehicleDetailLayers.require(render,at,BARE,fire.heat().group(),(int)(bare*.7F)<<24|HEAT_TINT,fire.heat().weight());
    }
    VehicleDetailLayers.require(render,at,CHAR,fire.charred().group(),charColor(burn),fire.charred().weight());
   }
   if(glow>0){
    int tiers=Math.min(s.embers().size(),(int)Math.ceil(glow*s.embers().size()));
    for(int i=0;i<tiers;i++)if(s.embers().get(i).vertexCount()>0)render.emissiveDraws.add(new AircraftRenderState.EmissiveDraw(EMBERS,new Matrix4f(at),List.of(s.embers().get(i))));
   }
  }
 }
 public static boolean removable(String n){return n.matches("\\$(canopy|hatch|panel|enginecover|cover)[0-9]*")||n.equals("$track_roller0");}
 public static int removed(AircraftEntity ac){var s=STATES.get(ac);return s==null?0:s.removed;}
 public static void clear(){STATES.clear();world.clear();WreckMesh.clear();WreckBurn.clear();}
 public static void apply(AircraftEntity ac,AircraftRenderState render){
  if(world.get()!=ac.level()){clear();world=new java.lang.ref.WeakReference<>(ac.level());}if(!enabled())return;
  var s=STATES.computeIfAbsent(ac,k->new State());boolean fresh=s.seen&&!s.dead&&ac.isDestroyed();s.seen=true;s.dead=ac.isDestroyed();
  if(!s.dead){s.removed=0;s.scattered.clear();s.glass=false;return;}
  if(render.far||Minecraft.getInstance().gameRenderer.mainCamera().position().distanceToSqr(ac.position())>48*48)return;
  boolean plane=ac instanceof io.github.mcheliceport.entity.plane.PlaneEntity;
  boolean heli=ac instanceof io.github.mcheliceport.entity.heli.HeliEntity;
  var scene=WreckScene.get(ac);boolean down=scene!=null&&scene.impact!=null;
  int age=VehicleAtmosphere.wreckAge(ac);float burn=burn(age),glow=embers(age);
  float settle=settling(ac);
  // A crashed plane that has stopped sliding settles level instead of staying propped at its impact angle.
  long now=ac.level().getGameTime();
  // Progress eases in and out instead of restarting: a stopped hulk that is nudged by a tick of sync
  // movement would otherwise snap back to its impact attitude and settle again, over and over.
  double moved=ac.position().subtract(ac.xo,ac.yo,ac.zo).horizontalDistanceSqr();
  float step=s.restTick<0?0:Math.clamp(now-s.restTick,0,SETTLE)/(float)SETTLE;s.restTick=now;
  if(plane&&down&&moved<.0025)s.restProgress=Math.min(1,s.restProgress+step);
  else if(!(plane&&down)||moved>.04)s.restProgress=Math.max(0,s.restProgress-step);
  float rest=s.restProgress*s.restProgress*(3-2*s.restProgress);
  s.rest=plane&&down&&s.drop>=0?restCorrection(render.yaw,render.pitch,render.roll,Math.min(1,rest),s.drop):new Matrix4f();
  var root=new Matrix4f(s.rest).mul(sag(ac));
  var draws=new ArrayList<AircraftPartPoses.Draw>();int removed=0;
  var gear=new ArrayList<AircraftPartPoses.Draw>();WreckMesh.Parts hull=null;Matrix4f hullPose=null;
  // The broken hull first, so the parts mounted on its sections can go with them.
  WreckMesh.Parts broken=null;Matrix4f brokenPose=null;McheliModel.Group brokenSource=null;
  if(plane)for(var draw:render.draws)for(var group:draw.groups())if(brokenSource==null&&group.name().equals("$body")){
   broken=WreckMesh.get(group,true,hasLeftWing(ac,group));brokenPose=draw.pose();brokenSource=group;
  }
  float open=open(scene,now);int seed=seed(ac,scene);
  for(var draw:render.draws)for(var group:draw.groups()){
   String name=group.name();
   // A crashed plane's gear is sheared off on impact and lies in the debris field instead.
   if(plane&&down&&name.startsWith("$lg")){gear.add(new AircraftPartPoses.Draw(new Matrix4f(draw.pose()),List.of(group)));continue;}
   boolean supported=name.equals("$body")||name.equals("$weapon0")||name.startsWith("$lg")||removable(name)||heli&&name.equals("$weapon1");
   boolean planeBody=plane&&name.equals("$body");
   var mesh=supported?WreckMesh.get(group,planeBody,planeBody&&hasLeftWing(ac,group)):null;
   // Until the wrecked hull is built it is drawn as it was, and so are the parts on it.
   var mounted=broken==null||planeBody?draw.pose():follow(brokenSource,broken,brokenPose,draw.pose(),center(group),seed,open);
   if(mesh==null){draws.add(new AircraftPartPoses.Draw(new Matrix4f(root).mul(mounted),List.of(group)));continue;}
   boolean detach=removed<2&&(!io.github.mcheliceport.client.particle.WreckSequenceEffects.enabled()||!name.startsWith("$enginecover")&&!name.startsWith("$cover"))&&(removable(group.name())||heli&&group.name().equals("$weapon1")&&group.vertexCount()<=1200);
   if(detach){
    removed++;PanelInteriors.add(render,new Matrix4f(root).mul(draw.pose()),group,ac);
    boolean young=fresh||age>=0&&age<35;
    // Glass does not fly off in one piece: a plane or helicopter canopy bursts into shards.
    if((plane||heli)&&name.startsWith("$canopy")){
     if(young&&!s.glass&&s.scattered.add(group)){s.glass=true;var c=draw.pose().transformPosition(new org.joml.Vector3f(mesh.center()));
      var b=io.github.mcheliceport.client.model.BreakupMeshes.part(group);float size=b==null?1.2F:Math.max(.6F,b.bounds().high().x-b.bounds().low().x);
      io.github.mcheliceport.client.particle.WreckGlass.shatter(Minecraft.getInstance().level,ac.position().add(c.x,c.y,c.z),ac.position().subtract(ac.xo,ac.yo,ac.zo),size,ac.getId());}
     continue;
    }
    // Fresh transition only; late-loaded old wrecks never replay flying debris.
    if(young&&s.scattered.add(group))LoosePanels.wreckPiece(ac,render.texture,group,draw.pose(),mesh.center());
    continue;
   }
   var pose=new Matrix4f(root).mul(mounted);
   if(group.name().startsWith("$lg"))pose.translate(0,-.35F*settle,0).rotateZ(.16F*settle);
   if(group.name().equals("$weapon0")&&ac instanceof io.github.mcheliceport.entity.tank.TankEntity){var c=mesh.center();pose.translate(c).rotateZ(-.045F*settle).translate(-c.x,-c.y,-c.z);}
   draws.add(new AircraftPartPoses.Draw(pose,List.of(mesh.shape())));
   VehicleDetailLayers.add(render,pose,render.texture,List.of(mesh.paint()),(int)(0x70*(1.15F-.55F*burn))<<24|0x7E7769);
   if(!render.thermal){
    float size=scene!=null&&scene.size()>1?scene.size():Math.max(4,ac.getBbWidth());
    var fire=WreckBurn.stable(mesh.soot(),sources(ac,draw.pose(),mesh,plane&&name.equals("$body")),WreckBurn.radius(size));
    // Only until the first burn is ready: the faint film the burn has away from the fire, never a full-hull char.
    if(fire==null)VehicleDetailLayers.require(render,pose,CHAR,List.of(mesh.soot()),WreckBurn.waiting(charColor(burn)));
    else{
     // Peeled paint and heat tint first, then the char over them, heaviest at the fire.
     int bare=(int)(235*blister(burn));
     if(bare>0){
      VehicleDetailLayers.require(render,pose,BARE,fire.metal().group(),bare<<24|BARE_TINT,fire.metal().weight());
      VehicleDetailLayers.require(render,pose,BARE,fire.heat().group(),(int)(bare*.7F)<<24|HEAT_TINT,fire.heat().weight());
     }
     VehicleDetailLayers.require(render,pose,CHAR,fire.charred().group(),charColor(burn),fire.charred().weight());
    }
   }
   if(!render.thermal&&glow>0){
    int tiers=Math.min(mesh.embers().size(),(int)Math.ceil(glow*mesh.embers().size()));
    for(int i=0;i<tiers;i++)if(mesh.embers().get(i).vertexCount()>0)render.emissiveDraws.add(new AircraftRenderState.EmissiveDraw(EMBERS,new Matrix4f(pose),List.of(mesh.embers().get(i))));
   }
   if(planeBody&&!mesh.sections().isEmpty())sections(ac,render,draws,mesh,draw.pose(),pose,scene,burn,glow,now);
   if(name.equals("$body")){hull=mesh;hullPose=new Matrix4f(draw.pose());}
   if(name.equals("$body")&&!render.thermal){
    var inverse=new Matrix4f(draw.pose()).invert();
    for(var origin:VehicleAtmosphere.wreckOrigins(ac)){
     var relative=origin.subtract(ac.position());var local=inverse.transformPosition(new org.joml.Vector3f((float)relative.x,(float)relative.y,(float)relative.z));
     // On the wrecked shape, not the airframe as built: the outer wings droop and the nose is crushed,
     // so a patch cut from the original skin hung in the air beside them as a dark see-through ghost.
     var soot=VehicleSkinMeshes.region(mesh.shape(),new Vec3(local.x,local.y,local.z),1.65F);
     if(soot!=null)VehicleDetailLayers.add(render,pose,SOOT,List.of(soot),0xEE171511);
    }
   }
  }
  if(plane&&down&&hull!=null)WreckDebrisField.offer(ac,render.texture,hullPose,hull.chunks(),gear);
  if(plane&&down&&hull!=null&&s.drop<0)s.drop=gearDrop(hull.shape(),gear);
  s.removed=removed;render.draws.clear();render.draws.addAll(draws);
 }
}
