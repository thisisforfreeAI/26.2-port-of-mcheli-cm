package io.github.mcheliceport.client.effect;
import static org.junit.jupiter.api.Assertions.*;
import org.junit.jupiter.api.Test;
class WreckGroundTest {
 private static double area(double[] p){double a=0;int n=p.length/2;for(int i=0;i<n;i++){int j=(i+1)%n;a+=p[i*2]*p[j*2+1]-p[j*2]*p[i*2+1];}return Math.abs(a)/2;}
 @Test void aRotatedGrooveIsCutToEachColumnAndTheCutsAddUpToItsArea(){
  double angle=Math.toRadians(30);var mark=new WreckGround.Mark(WreckGround.Kind.GROOVE,10.3,-4.6,64,Math.cos(angle),Math.sin(angle),2.2,.7,0);
  double total=0;for(int x=5;x<16;x++)for(int z=-10;z<1;z++){var poly=WreckGround.clip(mark,x,z);if(poly!=null){total+=area(poly);
   for(int i=0;i<poly.length;i+=2){assertTrue(poly[i]>=x-1e-9&&poly[i]<=x+1+1e-9);assertTrue(poly[i+1]>=z-1e-9&&poly[i+1]<=z+1+1e-9);}}}
  assertEquals(4*2.2*.7,total,1e-6);
  assertNull(WreckGround.clip(mark,40,40));
 }
 @Test void scarsAndPoolsScaleWithTheAirframeWithinBounds(){
  assertEquals(1.6F,WreckGround.scarRadius(1));assertEquals(4.5F,WreckGround.scarRadius(40));
  assertTrue(WreckGround.poolRadius(12)<WreckGround.scarRadius(12));
  assertEquals(0,WreckGround.poolGrowth(-1));assertTrue(WreckGround.poolGrowth(0)<WreckGround.poolGrowth(10));assertEquals(1,WreckGround.poolGrowth(40));
 }
}
