package io.github.mcheliceport.client.particle;

import static org.junit.jupiter.api.Assertions.*;
import net.minecraft.world.phys.Vec3;
import org.junit.jupiter.api.Test;

class AirframeFuelLookTest {
    @Test void oldAndUnknownLossesNeverReplayAndClockChangesStaySafe() {
        assertEquals(-1,AirframeFuelLook.age(100,null));
        assertEquals(-1,AirframeFuelLook.age(100,101L));
        assertEquals(-1,AirframeFuelLook.age(190,100L));
        assertEquals(-1,AirframeFuelLook.age(Long.MAX_VALUE,Long.MIN_VALUE));
        assertEquals(45,AirframeFuelLook.age(145,100L));
    }
    @Test void shortLeakWindowFadesWithoutAnAbruptPersistentHose() {
        assertEquals(0,AirframeFuelLook.envelope(0));
        assertEquals(1,AirframeFuelLook.envelope(12));
        assertTrue(AirframeFuelLook.envelope(89)<.05F);
        for(int age=90;age<2000;age++)assertEquals(0,AirframeFuelLook.envelope(age));
        for(int age=-4;age<100;age++)assertTrue(AirframeFuelLook.envelope(age)>=0&&AirframeFuelLook.envelope(age)<=1);
    }
    @Test void ignitionRequiresPassingNearTheExistingFireAndRemainsDelayed() {
        int passing=AirframeFuelLook.ignitionTick(new Vec3(4,0,0),new Vec3(-.5,0,0));
        assertTrue(passing>=2&&passing<=12);
        assertEquals(-1,AirframeFuelLook.ignitionTick(new Vec3(4,0,0),new Vec3(.5,0,0)));
        assertEquals(-1,AirframeFuelLook.ignitionTick(new Vec3(4,8,0),new Vec3(-.5,0,0)));
        assertEquals(-1,AirframeFuelLook.ignitionTick(new Vec3(Double.NaN,0,0),Vec3.ZERO));
        assertEquals(-1,AirframeFuelLook.ignitionTick(Vec3.ZERO,new Vec3(Double.POSITIVE_INFINITY,0,0)));
    }
}
