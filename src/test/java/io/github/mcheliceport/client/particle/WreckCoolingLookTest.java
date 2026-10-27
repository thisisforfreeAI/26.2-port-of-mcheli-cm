package io.github.mcheliceport.client.particle;

import static org.junit.jupiter.api.Assertions.*;
import org.junit.jupiter.api.Test;
class WreckCoolingLookTest {
    @Test void freshWreckFireCoolsBeforeSmokeAndBothStop() {
        var look=new WreckCoolingLook(); look.tick(false); look.tick(true);
        assertEquals(1,look.fire()); assertEquals(1,look.smoke());
        for(int i=0;i<360;i++)look.tick(true);
        assertEquals(0,look.fire()); assertTrue(look.smoke()>0);
        for(int i=0;i<1200;i++)look.tick(true);
        assertEquals(0,look.smoke()); assertEquals(0,look.fire());
    }
    @Test void joiningOldWreckDoesNotReplayFireAndRepairsResetBurn() {
        var look=new WreckCoolingLook(); look.tick(true);
        assertEquals(0,look.fire()); assertTrue(look.smoke()>0);
        look.tick(false); assertEquals(0,look.smoke());
        look.tick(true); assertEquals(1,look.fire());
    }
}
