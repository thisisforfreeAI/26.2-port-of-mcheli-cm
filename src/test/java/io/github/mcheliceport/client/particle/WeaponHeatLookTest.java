package io.github.mcheliceport.client.particle;
import static org.junit.jupiter.api.Assertions.*;
import org.junit.jupiter.api.Test;
class WeaponHeatLookTest {
    @Test void sustainedFireHeatsButDuplicateEventsDoNotAndCoolingEndsSmoke(){
        var heat=new WeaponHeatLook();assertTrue(heat.shot(0,20));float once=heat.heat();assertFalse(heat.shot(0,20));assertEquals(once,heat.heat());
        for(int i=1;i<20;i++)heat.shot(i*2,20);assertTrue(heat.heat()>.9F);assertFalse(heat.cooling(38));
        heat.tick(60);assertTrue(heat.cooling(60));heat.tick(1260);assertTrue(heat.heat()<.02F);assertFalse(heat.cooling(1260));
    }
    @Test void coldBarrelsAreInvisibleAndTimeReversalDoesNotKeepHeat(){var h=new WeaponHeatLook();assertEquals(0,h.color()>>>24);h.shot(50,10);h.tick(0);assertEquals(0,h.heat());}
}
