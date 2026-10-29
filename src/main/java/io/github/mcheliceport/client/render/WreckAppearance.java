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
/**
 * Render-only breakup, restrained settling and burn. The paint chars over the first fire (a charred skin
 * layer thickens and the hull darkens), embers glow in its seams and fade as it cools, a crashed plane's
 * canopy shatters and its landing gear is torn off into the debris field. No entity pose or collision edits.
 */
public final class WreckAppearance {
 private static final class State {boolean seen,dead,glass;int removed;long restStart=-1;float drop=-1;Matrix4f rest=new Matrix4f();final Set<McheliModel.Group> scattered=Collections.newSetFromMap(new IdentityHashMap<>());}
 private static final Map<AircraftEntity,State> STATES=Collections.synchronizedMap(new WeakHashMap<>());
 private static final Identifier SOOT=Identifier.fromNamespaceAndPath("mcheli_ce_port","textures/effect/m8_fluid_stains.png");
 static final Identifier CHAR=Identifier.fromNamespaceAndPath("mcheli_ce_port","textures/effect/m8_wreck_char.png");
 static final Identifier EMBERS=Identifier.fromNamespaceAndPath("mcheli_ce_port","textures/effect/m8_wreck_embers.png");
 private static final Identifier METAL=Identifier.withDefaultNamespace("textures/block/iron_block.png");
 /** Hull colour as the fire takes hold, and once the paint has burnt. */
 static final int FRESH_TINT=0xFF8A857E,BURNT_TINT=0xFF55514C;
 private static java.lang.ref.WeakReference<Object> world=new java.lang.ref.WeakReference<>(null);
 public static boolean enabled(){return EffectQuality.enhanced()&&ClientConfig.get(ClientConfig.VEHICLE_DAMAGE_DETAILS);}
 public static float settling(AircraftEntity ac){int age=VehicleAtmosphere.wreckAge(ac);return io.github.mcheliceport.client.particle.WreckSequenceEffects.enabled()?io.github.mcheliceport.client.particle.DestructionTimeline.collapse(age):Math.clamp((age+1)/60F,0,1);}
 public static Matrix4f root(AircraftEntity ac){float s=settling(ac);var state=STATES.get(ac);
  var sag=new Matrix4f().translate(0,-.10F*s,0).rotateZ(.025F*s).rotateX(-.018F*s);return state==null||!state.dead?sag:new Matrix4f(state.rest).mul(sag);}
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
 /** How far the hull drops once its gear is gone: from the gear's lowest point to the hull's, in blocks (0 to 2). */
 static float gearDrop(McheliModel.Group hull,List<AircraftPartPoses.Draw> gear){
  float body=Float.POSITIVE_INFINITY,wheels=Float.POSITIVE_INFINITY;var d=hull.data();
  for(int i=1;i<d.length;i+=8)body=Math.min(body,d[i]);
  // Both in model space: the gear's authored (lowered) position against the hull's belly.
  for(var draw:gear)for(var g:draw.groups()){var e=g.data();for(int i=1;i<e.length;i+=8)wheels=Math.min(wheels,e[i]);}
  if(!Float.isFinite(body)||!Float.isFinite(wheels))return 0;
  return Math.clamp(body-wheels,0,2);
 }
 public static boolean removable(String n){return n.matches("\\$(canopy|hatch|panel|enginecover|cover)[0-9]*")||n.equals("$track_roller0");}
 public static int removed(AircraftEntity ac){var s=STATES.get(ac);return s==null?0:s.removed;}
 public static void clear(){STATES.clear();world.clear();WreckMesh.clear();}
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
  boolean still=plane&&down&&ac.position().subtract(ac.xo,ac.yo,ac.zo).horizontalDistanceSqr()<.0025;
  if(!still)s.restStart=-1;else if(s.restStart<0)s.restStart=now;
  float rest=s.restStart<0?0:(now-s.restStart)/(float)SETTLE;rest=rest*rest*(3-2*Math.min(1,rest));
  s.rest=plane&&down&&s.drop>=0?restCorrection(render.yaw,render.pitch,render.roll,Math.min(1,rest),s.drop):new Matrix4f();
  var root=new Matrix4f(s.rest).translate(0,-.10F*settle,0).rotateZ(.025F*settle).rotateX(-.018F*settle);
  var draws=new ArrayList<AircraftPartPoses.Draw>();int removed=0;
  var gear=new ArrayList<AircraftPartPoses.Draw>();WreckMesh.Parts hull=null;Matrix4f hullPose=null;
  for(var draw:render.draws)for(var group:draw.groups()){
   String name=group.name();
   // A crashed plane's gear is sheared off on impact and lies in the debris field instead.
   if(plane&&down&&name.startsWith("$lg")){gear.add(new AircraftPartPoses.Draw(new Matrix4f(draw.pose()),List.of(group)));continue;}
   boolean supported=name.equals("$body")||name.equals("$weapon0")||name.startsWith("$lg")||removable(name)||heli&&name.equals("$weapon1");
   var mesh=supported?WreckMesh.get(group,plane&&name.equals("$body")):null;
   if(mesh==null){draws.add(new AircraftPartPoses.Draw(new Matrix4f(root).mul(draw.pose()),List.of(group)));continue;}
   boolean detach=removed<2&&(!io.github.mcheliceport.client.particle.WreckSequenceEffects.enabled()||!name.startsWith("$enginecover")&&!name.startsWith("$cover"))&&(removable(group.name())||heli&&group.name().equals("$weapon1")&&group.vertexCount()<=1200);
   if(detach){
    removed++;PanelInteriors.add(render,new Matrix4f(root).mul(draw.pose()),group);
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
   var pose=new Matrix4f(root).mul(draw.pose());
   if(group.name().startsWith("$lg"))pose.translate(0,-.35F*settle,0).rotateZ(.16F*settle);
   if(group.name().equals("$weapon0")&&ac instanceof io.github.mcheliceport.entity.tank.TankEntity){var c=mesh.center();pose.translate(c).rotateZ(-.045F*settle).translate(-c.x,-c.y,-c.z);}
   draws.add(new AircraftPartPoses.Draw(pose,List.of(mesh.shape())));
   if(!render.thermal)VehicleDetailLayers.require(render,pose,CHAR,List.of(mesh.soot()),charColor(burn));
   VehicleDetailLayers.add(render,pose,render.texture,List.of(mesh.paint()),(int)(0x70*(1.15F-.55F*burn))<<24|0x7E7769);
   VehicleDetailLayers.add(render,pose,METAL,List.of(mesh.metal()),0xA0807C75);
   if(!render.thermal&&glow>0){
    int tiers=Math.min(mesh.embers().size(),(int)Math.ceil(glow*mesh.embers().size()));
    for(int i=0;i<tiers;i++)if(mesh.embers().get(i).vertexCount()>0)render.emissiveDraws.add(new AircraftRenderState.EmissiveDraw(EMBERS,new Matrix4f(pose),List.of(mesh.embers().get(i))));
   }
   if(name.equals("$body")){hull=mesh;hullPose=new Matrix4f(draw.pose());}
   if(name.equals("$body")&&!render.thermal){
    var inverse=new Matrix4f(draw.pose()).invert();
    for(var origin:VehicleAtmosphere.wreckOrigins(ac)){
     var relative=origin.subtract(ac.position());var local=inverse.transformPosition(new org.joml.Vector3f((float)relative.x,(float)relative.y,(float)relative.z));
     var soot=VehicleSkinMeshes.region(group,new Vec3(local.x,local.y,local.z),1.65F);
     if(soot!=null)VehicleDetailLayers.add(render,pose,SOOT,List.of(soot),0xEE171511);
    }
   }
  }
  if(plane&&down&&hull!=null)WreckDebrisField.offer(ac,render.texture,hullPose,hull.chunks(),gear);
  if(plane&&down&&hull!=null&&s.drop<0)s.drop=gearDrop(hull.shape(),gear);
  s.removed=removed;render.draws.clear();render.draws.addAll(draws);
 }
}
