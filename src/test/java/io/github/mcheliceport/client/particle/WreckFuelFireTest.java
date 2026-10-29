package io.github.mcheliceport.client.particle;
import static org.junit.jupiter.api.Assertions.*;
import org.junit.jupiter.api.Test;
class WreckFuelFireTest {
 @Test void thePoolBurnsFullThenDiesOutAfterTheHullFire(){
  assertEquals(0,WreckFuelFire.strength(-1));assertEquals(1,WreckFuelFire.strength(0));assertEquals(1,WreckFuelFire.strength(WreckFuelFire.FULL-1));
  assertTrue(WreckFuelFire.strength(400)>0&&WreckFuelFire.strength(400)<1);
  assertEquals(0,WreckFuelFire.strength(WreckFuelFire.FULL+WreckFuelFire.DECAY));
  var look=new WreckCoolingLook();look.tick(false);look.tick(true);for(int i=0;i<360;i++)look.tick(true);
  assertEquals(0,look.fire());assertTrue(WreckFuelFire.strength(look.age())>0,"fuel outlasts the hull's flames");
 }
 @Test void aHulkKeepsAFaintWispAfterItsSmokeEnds(){
  var look=new WreckCoolingLook();look.tick(false);look.tick(true);
  assertEquals(0,look.lingering());
  for(int i=0;i<1300;i++)look.tick(true);
  assertEquals(0,look.smoke());assertTrue(look.lingering()>0&&look.lingering()<.2F);
  for(int i=0;i<WreckCoolingLook.LINGER;i++)look.tick(true);
  assertEquals(0,look.lingering());assertEquals(WreckCoolingLook.LINGER,look.age());
 }
}
