package io.github.mcheliceport.client.model;
import static org.junit.jupiter.api.Assertions.*;
import io.github.mcheliceport.client.render.WreckAppearance;
import org.joml.Vector3f;
import org.junit.jupiter.api.Test;
class WreckSectionsTest {
 /** A fighter: a slim fuselage with a straight wing across its middle. */
 private static McheliModel.Group fighter(){var b=new DetailBoxes();b.box(-1,0,-8,1,1.5F,8);b.box(-8,.6F,-2,8,.8F,2);return b.group("$body");}
 private static WreckMesh.Section find(WreckMesh.Parts p,int kind){for(var s:p.sections())if(s.kind()==kind)return s;return null;}
 @Test void aCrashedFighterBreaksIntoHullNoseAndOuterLeftWingWithNothingLost(){
  var source=fighter();var whole=WreckMesh.build(source,true,true);var parts=WreckMesh.build(source,true,true,true,true);
  var nose=find(parts,WreckMesh.NOSE);var wing=find(parts,WreckMesh.WING);
  assertNotNull(nose,"forward fuselage breaks off");assertNotNull(wing,"outer left wing breaks off");
  double total=BreakupMeshes.area(parts.shape())+BreakupMeshes.area(nose.shape())+BreakupMeshes.area(wing.shape());
  assertEquals(BreakupMeshes.area(whole.shape()),total,1e-3*total,"the pieces add up to the hull");
  var n=BreakupMeshes.bounds(nose.shape());var w=BreakupMeshes.bounds(wing.shape());var core=BreakupMeshes.bounds(parts.shape());
  assertTrue(n.low().z>=nose.hinge().z()-1e-3F&&core.high().z<=nose.hinge().z()+1e-3F,"nose ahead of its cut");
  assertTrue(nose.hinge().z()>8-.35F*16&&nose.hinge().z()<8-.17F*16,"cut a fifth to a third back from the nose: "+nose.hinge());
  assertTrue(w.high().x<=wing.hinge().x()+1e-3F&&core.low().x>=wing.hinge().x()-1e-3F,"wing outboard of its cut");
  assertTrue(parts.cap().vertexCount()>0&&nose.cap().vertexCount()>0&&wing.cap().vertexCount()>0,"every cut is capped on both sides");
 }
 @Test void aFlyingWingKeepsItsNoseAndAWingAlreadyLostInFlightIsNotCutAgain(){
  // A delta: the leading edges are wide right behind the nose.
  var b=new DetailBoxes();for(int i=0;i<10;i++){float z=6-i*1.4F,half=.4F+i*1.1F;b.box(-half,0,z-1.4F,half,.5F,z);}
  var parts=WreckMesh.build(b.group("$body"),true,true,true,false);
  assertNull(find(parts,WreckMesh.NOSE));assertNull(find(parts,WreckMesh.WING));
  // It splits through its wide centre body instead, and both sides of that cut are closed.
  var half=find(parts,WreckMesh.SPLIT);assertNotNull(half);
  assertTrue(BreakupMeshes.bounds(half.shape()).low().x()>=half.hinge().x()-1e-3F);
  assertTrue(parts.cap().vertexCount()>0&&half.cap().vertexCount()>0);
 }
 @Test void sectionsStayJoinedUntilImpactThenTearAway(){
  var parts=WreckMesh.build(fighter(),true,true,true,true);
  for(var s:parts.sections()){
   assertTrue(WreckAppearance.breakPose(s,42,0).equals(new org.joml.Matrix4f(),1e-5F),"joined before impact");
   var tip=s.kind()==WreckMesh.NOSE?new Vector3f(0,.75F,7):new Vector3f(-7.5F,.7F,0);
   var moved=WreckAppearance.breakPose(s,42,1).transformPosition(new Vector3f(tip));
   if(s.kind()==WreckMesh.NOSE)assertTrue(moved.y<tip.y,"nose dug in: "+moved);
   else assertTrue(moved.z<tip.z-1,"wing swept back: "+moved);
   assertTrue(moved.distance(tip)>.3F);
  }
 }
 @Test void debrisChunksAreBentSheetsNotFlatSlabs(){
  var b=new DetailBoxes();b.box(-6,0,-6,6,.05F,6);
  var parts=WreckMesh.build(b.group("$body"),false,true);
  assertFalse(parts.chunks().isEmpty());int bent=0;
  for(var c:parts.chunks())if(Math.min(c.size().x,Math.min(c.size().y,c.size().z))>.15F)bent++;
  assertTrue(bent*2>=parts.chunks().size(),"most chunks are folded out of the skin's plane: "+bent+"/"+parts.chunks().size());
 }
}
