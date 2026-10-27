package io.github.mcheliceport.client.model;
import static org.junit.jupiter.api.Assertions.*;
import org.junit.jupiter.api.Test;
class WreckMeshTest {
 @Test void burnVariantsPreserveOriginalGeometryAndUvs(){
  var boxes=new DetailBoxes();boxes.box(-2,0,-3,2,2,3);var original=boxes.group("$body");var before=original.data().clone();
  var wreck=WreckMesh.build(original,false);assertArrayEquals(before,original.data());assertArrayEquals(before,wreck.shape().data());
  assertTrue(wreck.paint().vertexCount()<=1200);assertTrue(wreck.metal().vertexCount()<=1200);
  assertArrayEquals(wreck.paint().data(),WreckMesh.build(original,false).paint().data());
 }
 @Test void onlyAircraftWingModeChangesOuterShape(){
  var boxes=new DetailBoxes();boxes.box(-6,1,-2,6,1.1F,2);var source=boxes.group("$body");var wreck=WreckMesh.build(source,true);
  assertNotEquals(java.util.Arrays.hashCode(source.data()),java.util.Arrays.hashCode(wreck.shape().data()));
  assertTrue(wreck.shape().vertexCount()<=source.vertexCount());
  for(int i=1;i<wreck.shape().data().length;i+=8)assertTrue(wreck.shape().data()[i]>=.44F);
 }
}
