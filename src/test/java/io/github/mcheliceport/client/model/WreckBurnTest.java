package io.github.mcheliceport.client.model;
import static org.junit.jupiter.api.Assertions.*;
import java.util.List;
import org.joml.Vector3f;
import org.junit.jupiter.api.Test;
class WreckBurnTest {
 /** A 20-block fuselage with 16-block wings, built from a few big low-poly boxes. */
 private static McheliModel.Group plane(){var b=new DetailBoxes();b.box(-1,0,-10,1,2,10);b.box(-8,.8F,-1,8,1.1F,2);return WreckMesh.build(b.group("$body"),false).soot();}
 private static float weightNear(WreckBurn.Layer layer,Vector3f at,float within){
  var d=layer.group().data();float best=0;
  for(int v=0;v<layer.weight().length;v++){int i=v*McheliModel.STRIDE;if(new Vector3f(d[i],d[i+1],d[i+2]).distance(at)<within)best=Math.max(best,layer.weight()[v]);}
  return best;
 }
 @Test void charIsHeaviestAtTheFireAndOnlyAFilmAtTheNoseAndTips(){
  var fire=List.of(new Vector3f(0,1,-9));var burn=WreckBurn.build(plane(),fire,WreckBurn.radius(20));
  assertTrue(weightNear(burn.charred(),new Vector3f(0,2,-9),1)>.9F,"tail fire is charred black");
  assertEquals(WreckBurn.FLOOR,weightNear(burn.charred(),new Vector3f(0,2,10),.6F),.02F,"the nose keeps its paint under a film");
  assertEquals(WreckBurn.FLOOR,weightNear(burn.charred(),new Vector3f(8,1.1F,0),.6F),.02F,"so do the wingtips");
 }
 @Test void bareMetalRingsTheCharAndTheHeatTintSitsOnItsHotSide(){
  var fire=List.of(new Vector3f(0,1,0));float radius=WreckBurn.radius(20);
  assertTrue(WreckBurn.band(WreckBurn.heat(0,1,0,fire,radius),WreckBurn.METAL_AT,WreckBurn.METAL_WIDTH)<.01F,"no bare metal at the core");
  float peakMetal=0,peakTint=0,metalAt=0,tintAt=0;
  for(float h=0;h<=1;h+=.01F){float m=WreckBurn.band(h,WreckBurn.METAL_AT,WreckBurn.METAL_WIDTH),t=WreckBurn.band(h,WreckBurn.TINT_AT,WreckBurn.TINT_WIDTH);if(m>peakMetal){peakMetal=m;metalAt=h;}if(t>peakTint){peakTint=t;tintAt=h;}}
  assertTrue(tintAt>metalAt,"tint is nearer the fire than the peeled metal");
  var burn=WreckBurn.build(plane(),fire,radius);
  assertTrue(burn.metal().group().vertexCount()>0&&burn.metal().group().vertexCount()<burn.charred().group().vertexCount());
  assertEquals(burn.metal().group().vertexCount(),burn.metal().weight().length);
 }
 @Test void bigFacesAreSplitSoTheBurnFadesSmoothly(){
  var b=new DetailBoxes();b.box(-6,0,-6,6,.2F,6);var split=WreckBurn.split(b.group("$x").data(),WreckBurn.MAX_EDGE);
  for(int t=0;t+23<split.length;t+=24)assertTrue(WreckMesh.edge(split,t)<=WreckBurn.MAX_EDGE+1e-4F);
  // Same surface: the area is unchanged.
  assertEquals(area(b.group("$x").data()),area(split),.01);
 }
 @Test void wingRootsSitAThirdOfTheWayOutOnTheWing(){
  var b=new DetailBoxes();b.box(-1,0,-10,1,2,10);b.box(-8,.8F,-1,8,1.1F,2);var roots=WreckBurn.wingRoots(b.group("$body"));
  assertEquals(2,roots.size());assertEquals(8*.32F,Math.abs(roots.get(0).x),1e-3);assertEquals(.5F,roots.get(0).z,.3F);
  var tube=new DetailBoxes();tube.box(-1,0,-10,1,2,10);assertTrue(WreckBurn.wingRoots(tube.group("$body")).isEmpty());
 }
 private static double area(float[] d){double a=0;for(int t=0;t+23<d.length;t+=24){var p=new Vector3f(d[t],d[t+1],d[t+2]);
  a+=new Vector3f(d[t+8],d[t+9],d[t+10]).sub(p).cross(new Vector3f(d[t+16],d[t+17],d[t+18]).sub(p)).length()/2;}return a;}
 @Test void aPartKeepsItsBurnWhileTheFireOnlyJittersOrIsRebuilding() throws Exception{
  var soot=plane();float radius=WreckBurn.radius(20);
  var fire=List.of(new Vector3f(0,1,-9.24F));
  WreckBurn.Burn first=null;for(int i=0;i<400&&first==null;i++){first=WreckBurn.stable(soot,fire,radius);if(first==null)Thread.sleep(10);}
  assertNotNull(first);
  // Across the key's rounding edge (-9.24 and -9.26 round apart), the shown burn stays.
  assertSame(first,WreckBurn.stable(soot,List.of(new Vector3f(0,1,-9.26F)),radius));
  // A fire that really moved gets a new burn, but the old one shows until it is built.
  var moved=List.of(new Vector3f(0,1,4));var meanwhile=WreckBurn.stable(soot,moved,radius);
  assertNotNull(meanwhile,"never falls back to the full-hull char");
  WreckBurn.Burn next=meanwhile;for(int i=0;i<400&&next==first;i++){Thread.sleep(10);next=WreckBurn.stable(soot,moved,radius);}
  assertNotSame(first,next);
  assertTrue(WreckBurn.near(List.of(new Vector3f()),List.of(new Vector3f(.3F,0,0)),.5F));
  assertFalse(WreckBurn.near(List.of(new Vector3f()),List.of(new Vector3f(),new Vector3f()),.5F));
  assertEquals(Math.round(0xC0*WreckBurn.FLOOR),WreckBurn.waiting(0xC0FFFFFF)>>>24);
 }
}
