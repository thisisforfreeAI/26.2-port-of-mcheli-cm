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
}
