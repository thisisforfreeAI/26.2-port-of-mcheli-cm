package io.github.mcheliceport.client.render;
import static org.junit.jupiter.api.Assertions.*;
import org.junit.jupiter.api.Test;
class WreckAppearanceTest {
 @Test void paintCharsDuringTheFireAndEmbersOutlastIt(){
  assertEquals(.25F,WreckAppearance.burn(0),1e-6);assertEquals(1,WreckAppearance.burn(360));assertEquals(1,WreckAppearance.burn(-1),"unknown age reads as burnt");
  assertTrue(WreckAppearance.burn(100)<WreckAppearance.burn(200));
  assertEquals(1,WreckAppearance.embers(500));assertTrue(WreckAppearance.embers(1200)>0&&WreckAppearance.embers(1200)<1);assertEquals(0,WreckAppearance.embers(2400));
  assertTrue((WreckAppearance.charColor(1)>>>24)>(WreckAppearance.charColor(0)>>>24));
 }
 @Test void hullTintDarkensFromFreshToBurnt(){
  assertEquals(WreckAppearance.FRESH_TINT,WreckAppearance.lerp(0,WreckAppearance.FRESH_TINT,WreckAppearance.BURNT_TINT));
  assertEquals(WreckAppearance.BURNT_TINT,WreckAppearance.lerp(1,WreckAppearance.FRESH_TINT,WreckAppearance.BURNT_TINT));
  int mid=WreckAppearance.lerp(.5F,WreckAppearance.FRESH_TINT,WreckAppearance.BURNT_TINT);
  assertEquals(0xFF,mid>>>24);assertTrue((mid&0xFF)<(WreckAppearance.FRESH_TINT&0xFF)&&(mid&0xFF)>(WreckAppearance.BURNT_TINT&0xFF));
 }
 @Test void aStoppedWreckSettlesOntoItsBellyOrItsBack(){
  var a=WreckAppearance.restAttitude(-25,60,1);assertEquals(0,a[0],1e-5);assertEquals(0,a[1],1e-5);
  var b=WreckAppearance.restAttitude(18,-130,1);assertEquals(0,b[0],1e-5);assertEquals(180,Math.abs(b[1]),1e-5);
  var half=WreckAppearance.restAttitude(-20,40,.5F);assertEquals(-10,half[0],1e-5);assertEquals(20,half[1],1e-5);
  // No settling leaves the pose exactly as the entity has it.
  var none=WreckAppearance.restCorrection(37,-22,15,0,1.2F);assertTrue(none.equals(new org.joml.Matrix4f(),1e-5F));
  // Fully settled: the corrected nose points level along the heading, and the hull is lowered by the drop.
  var full=WreckAppearance.restCorrection(0,-30,0,1,1);
  var y=io.github.mcheliceport.content.aircraft.AircraftInfo.GLOBAL_Y_OFFSET;
  var pose=new org.joml.Matrix4f(full).translate(0,y,0).rotateX((float)Math.toRadians(-30));
  var nose=pose.transformPosition(new org.joml.Vector3f(0,0,5));assertEquals(y-1,nose.y,1e-4);assertEquals(5,nose.z,1e-4);
 }
}
