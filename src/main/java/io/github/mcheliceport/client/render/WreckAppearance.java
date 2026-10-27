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
/** Render-only breakup, restrained settling and burn contrast. No entity pose or collision edits. */
public final class WreckAppearance {
 private static final class State {boolean seen,dead;int removed;final Set<McheliModel.Group> scattered=Collections.newSetFromMap(new IdentityHashMap<>());}
 private static final Map<AircraftEntity,State> STATES=Collections.synchronizedMap(new WeakHashMap<>());
 private static final Identifier SOOT=Identifier.fromNamespaceAndPath("mcheli_ce_port","textures/effect/m8_fluid_stains.png");
 private static final Identifier METAL=Identifier.withDefaultNamespace("textures/block/iron_block.png");
 private static java.lang.ref.WeakReference<Object> world=new java.lang.ref.WeakReference<>(null);
 public static boolean enabled(){return EffectQuality.enhanced()&&ClientConfig.get(ClientConfig.VEHICLE_DAMAGE_DETAILS);}
 public static int tint(){return 0xFF77736E;}
 public static boolean removable(String n){return n.matches("\\$(canopy|hatch|panel|enginecover|cover)[0-9]*")||n.equals("$track_roller0");}
 public static int removed(AircraftEntity ac){var s=STATES.get(ac);return s==null?0:s.removed;}
 public static void clear(){STATES.clear();world.clear();WreckMesh.clear();}
 public static void apply(AircraftEntity ac,AircraftRenderState render){
  if(world.get()!=ac.level()){clear();world=new java.lang.ref.WeakReference<>(ac.level());}if(!enabled())return;
  var s=STATES.computeIfAbsent(ac,k->new State());boolean fresh=s.seen&&!s.dead&&ac.isDestroyed();s.seen=true;s.dead=ac.isDestroyed();
  if(!s.dead){s.removed=0;s.scattered.clear();return;}
  if(render.far||Minecraft.getInstance().gameRenderer.mainCamera().position().distanceToSqr(ac.position())>48*48)return;
  boolean plane=ac instanceof io.github.mcheliceport.entity.plane.PlaneEntity;
  boolean heli=ac instanceof io.github.mcheliceport.entity.heli.HeliEntity;
  float settle=Math.clamp((VehicleAtmosphere.wreckAge(ac)+1)/60F,0,1);
  var root=new Matrix4f().translate(0,-.10F*settle,0).rotateZ(.025F*settle).rotateX(-.018F*settle);
  var draws=new ArrayList<AircraftPartPoses.Draw>();int removed=0;
  for(var draw:render.draws)for(var group:draw.groups()){
   String name=group.name();
   boolean supported=name.equals("$body")||name.equals("$weapon0")||name.startsWith("$lg")||removable(name)||heli&&name.equals("$weapon1");
   var mesh=supported?WreckMesh.get(group,plane&&name.equals("$body")):null;
   if(mesh==null){draws.add(new AircraftPartPoses.Draw(new Matrix4f(root).mul(draw.pose()),List.of(group)));continue;}
   boolean detach=removed<2&&(removable(group.name())||heli&&group.name().equals("$weapon1")&&group.vertexCount()<=1200);
   if(detach){
    removed++;PanelInteriors.add(render,new Matrix4f(root).mul(draw.pose()),group);
    // Fresh transition only; late-loaded old wrecks never replay flying debris.
    if((fresh||VehicleAtmosphere.wreckAge(ac)>=0&&VehicleAtmosphere.wreckAge(ac)<35)&&s.scattered.add(group))LoosePanels.wreckPiece(ac,render.texture,group,draw.pose(),mesh.center());
    continue;
   }
   var pose=new Matrix4f(root).mul(draw.pose());
   if(group.name().startsWith("$lg"))pose.translate(0,-.35F*settle,0).rotateZ(.16F*settle);
   if(group.name().equals("$weapon0")&&ac instanceof io.github.mcheliceport.entity.tank.TankEntity){var c=mesh.center();pose.translate(c).rotateZ(-.045F*settle).translate(-c.x,-c.y,-c.z);}
   draws.add(new AircraftPartPoses.Draw(pose,List.of(mesh.shape())));
   VehicleDetailLayers.add(render,pose,render.texture,List.of(mesh.paint()),0x707E7769);
   VehicleDetailLayers.add(render,pose,METAL,List.of(mesh.metal()),0xA0807C75);
   if(name.equals("$body")&&!render.thermal){
    var inverse=new Matrix4f(draw.pose()).invert();
    for(var origin:VehicleAtmosphere.wreckOrigins(ac)){
     var relative=origin.subtract(ac.position());var local=inverse.transformPosition(new org.joml.Vector3f((float)relative.x,(float)relative.y,(float)relative.z));
     var soot=VehicleSkinMeshes.region(group,new Vec3(local.x,local.y,local.z),1.65F);
     if(soot!=null)VehicleDetailLayers.add(render,pose,SOOT,List.of(soot),0xEE171511);
    }
   }
  }
  s.removed=removed;render.draws.clear();render.draws.addAll(draws);
 }
}
