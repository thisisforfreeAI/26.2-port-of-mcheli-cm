package io.github.mcheliceport.client.model;
import static org.junit.jupiter.api.Assertions.*;
import java.util.ArrayList;
import org.joml.Vector3f;
import org.junit.jupiter.api.Test;
class WreckCrumpleTest {
 private static McheliModel.Group fuselage(){var boxes=new DetailBoxes();boxes.box(-1,0,-8,1,1.5F,8);return boxes.group("$body");}
 @Test void onlyTheFrontOfACrashedHullMovesAndTheNoseIsShortenedAndLowered(){
  var source=fuselage();var wreck=WreckMesh.build(source,true,true);var d=source.data();var w=wreck.shape().data();
  assertEquals(d.length,w.length);float noseBefore=-99,noseAfter=-99,lowBefore=99,lowAfter=99;
  for(int i=0;i+7<d.length;i+=8){
   if(d[i+2]<=-8+.62F*16){assertEquals(d[i],w[i]);assertEquals(d[i+1],w[i+1]);assertEquals(d[i+2],w[i+2]);}
   noseBefore=Math.max(noseBefore,d[i+2]);noseAfter=Math.max(noseAfter,w[i+2]);
   if(d[i+2]>7.9F){lowBefore=Math.min(lowBefore,d[i+1]);lowAfter=Math.min(lowAfter,w[i+1]);}
  }
  assertTrue(noseAfter<noseBefore-.8F,"nose pushed back: "+noseAfter);
  assertTrue(lowAfter<lowBefore-.3F,"nose droops: "+lowAfter);
 }
 @Test void aBombersCrushedNoseIsDentedNotSpiked(){
  // A 38-block hull: wrinkles scaled by the whole length used to throw corners a block out.
  var boxes=new DetailBoxes();for(int i=0;i<40;i++)boxes.box(-1.6F,0,-19+i*.95F,1.6F,3.2F,-19+(i+1)*.95F);
  var source=boxes.group("$body");var w=WreckMesh.build(source,false,true).shape().data();var d=source.data();
  for(int t=0;t+23<d.length;t+=24)for(int k=0;k<3;k++){
   int i=t+k*8,j=t+(k+1)%3*8;
   float before=new Vector3f(d[i]-d[j],d[i+1]-d[j+1],d[i+2]-d[j+2]).length(),after=new Vector3f(w[i]-w[j],w[i+1]-w[j+1],w[i+2]-w[j+2]).length();
   // Neighbouring corners move together: no edge is torn out into a spike.
   assertTrue(Math.abs(after-before)<=2*WreckMesh.WRINKLE+.3F*before,"edge at z="+d[i+2]+" went from "+before+" to "+after);
  }
  for(int i=0;i+7<d.length;i+=8)assertTrue(d[i+2]-w[i+2]<=WreckMesh.CRUSH+.01F,"crushed back "+(d[i+2]-w[i+2]));
 }
 @Test void sharedCornersStayTogetherSoTheCrushedSkinHasNoCracks(){
  var source=fuselage();var w=WreckMesh.build(source,true,true).shape().data();var d=source.data();
  var moved=new java.util.HashMap<String,String>();
  for(int i=0;i+7<d.length;i+=8){String before=d[i]+","+d[i+1]+","+d[i+2],after=w[i]+","+w[i+1]+","+w[i+2];
   var old=moved.putIfAbsent(before,after);if(old!=null)assertEquals(old,after);}
 }
 @Test void crushedFacesAreFacetedAndStillFaceOutwards(){
  var source=fuselage();var w=WreckMesh.build(source,true,true).shape().data();var d=source.data();
  for(int t=0;t+23<d.length;t+=24){
   var before=new Vector3f(d[t+5],d[t+6],d[t+7]);var after=new Vector3f(w[t+5],w[t+6],w[t+7]);
   assertTrue(before.dot(after)>0,"normal flipped at "+t);assertEquals(1,after.length(),1e-3);
  }
 }
 @Test void charredSkinCoversEveryTriangleJustOffTheHull(){
  var source=fuselage();var parts=WreckMesh.build(source,false);var hull=parts.shape().data();var soot=parts.soot().data();
  assertEquals(hull.length,soot.length);
  for(int i=0;i+7<hull.length;i+=8){
   float lift=(soot[i]-hull[i])*hull[i+5]+(soot[i+1]-hull[i+1])*hull[i+6]+(soot[i+2]-hull[i+2])*hull[i+7];
   assertEquals(WreckMesh.SOOT_LIFT,lift,1e-5);
  }
  // Projected uvs tile with world position, not the authored texture layout.
  assertNotEquals(hull[3],soot[3]);
  assertSame(WreckMesh.sootOf(source),WreckMesh.sootOf(source));
 }
 @Test void emberTiersAreBoundedAndDebrisChunksAreSmallRecentredPieces(){
  var boxes=new DetailBoxes();for(int i=0;i<8;i++)boxes.box(-3+i*.7F,0,-8,-2.4F+i*.7F,1.4F,8);
  var parts=WreckMesh.build(boxes.group("$body"),true,true);
  assertEquals(WreckMesh.EMBER_TIERS,parts.embers().size());
  int total=parts.embers().stream().mapToInt(McheliModel.Group::vertexCount).sum();
  assertTrue(total>0&&total<parts.shape().vertexCount(),"embers "+total);
  assertTrue(parts.chunks().size()<=WreckMesh.MAX_CHUNKS&&!parts.chunks().isEmpty());
  for(var c:parts.chunks()){var d=c.group().data();float reach=0;for(int i=0;i+7<d.length;i+=8)reach=Math.max(reach,new Vector3f(d[i],d[i+1],d[i+2]).length());
   assertTrue(reach<c.size().length(),"chunk is centred");}
  assertTrue(WreckMesh.build(boxes.group("$body"),true,false).chunks().isEmpty(),"only crashed hulls shed chunks");
 }
 @Test void debrisChunksAreCutToACellSoLowPolySliversNeverBecomeSpikes(){
  // A low-poly nose: a fan of long thin triangles meeting at the tip, on a long slab fuselage.
  var boxes=new DetailBoxes();boxes.box(-1.5F,0,-12,1.5F,2,8);var body=boxes.group("$body").data();
  var data=new ArrayList<Float>();for(float f:body)data.add(f);
  for(int i=0;i<24;i++){double a=i*Math.PI*2/24,b=(i+1)*Math.PI*2/24;
   float[][] tri={{0,1,12},{(float)Math.cos(a)*1.5F,1+(float)Math.sin(a),8},{(float)Math.cos(b)*1.5F,1+(float)Math.sin(b),8}};
   for(var v:tri){data.add(v[0]);data.add(v[1]);data.add(v[2]);data.add(0F);data.add(0F);data.add(v[0]);data.add(v[1]-1);data.add(0F);}}
  float[] d=new float[data.size()];for(int i=0;i<d.length;i++)d[i]=data.get(i);
  var parts=WreckMesh.build(new McheliModel.Group("$body",d),true,true);
  assertFalse(parts.chunks().isEmpty());
  float cell=WreckMesh.chunkCell(new Vector3f(-1.5F,0,-12),new Vector3f(1.5F,2,12));
  for(var c:parts.chunks()){
   // Folded and dented, a chunk still fits in the sphere around its cell.
   float reach=cell*1.7321F+.2F;
   assertTrue(c.size().x<=reach&&c.size().y<=reach&&c.size().z<=reach,"chunk fits its cell: "+c.size());
   var g=c.group().data();for(int t=0;t+23<g.length;t+=24)assertTrue(WreckMesh.edge(g,t)<=reach,"no spike longer than the cell");
  }
 }
 @Test void bigLowPolyFacesCarryNoFlatPatchTriangles(){
  var boxes=new DetailBoxes();boxes.box(-8,0,-8,8,.2F,8);
  var parts=WreckMesh.build(boxes.group("$body"),false);
  var d=parts.paint().data();for(int t=0;t+23<d.length;t+=24)assertTrue(WreckMesh.edge(d,t)<=WreckMesh.PATCH_EDGE+.05F);
  d=parts.metal().data();for(int t=0;t+23<d.length;t+=24)assertTrue(WreckMesh.edge(d,t)<=WreckMesh.PATCH_EDGE+.05F);
 }
}
