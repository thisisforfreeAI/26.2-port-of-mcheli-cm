package io.github.mcheliceport.client.particle;
import static org.junit.jupiter.api.Assertions.*;
import org.junit.jupiter.api.Test;
class DestructionTimelineTest {
    @Test void turretSeparationIsRareAmmoDependentAndExcludesUnsupportedDesigns(){
        int count=0;for(int seed=0;seed<800;seed++)if(DestructionTimeline.turret("t-90",seed,200))count++;
        assertEquals(100,count);assertFalse(DestructionTimeline.turret("t-90",0,0));assertFalse(DestructionTimeline.turret("m1a2",0,200));assertFalse(DestructionTimeline.turret("unknown",0,200));
    }
    @Test void collapseIsDelayedAndRotorsStopWithoutReversing(){
        assertEquals(0,DestructionTimeline.collapse(79));assertEquals(1,DestructionTimeline.collapse(150));
        float previous=0;for(int age=0;age<400;age++){float angle=DestructionTimeline.spin(age,25);assertTrue(angle>=previous);previous=angle;}
        assertEquals(DestructionTimeline.spin(140,25),DestructionTimeline.spin(500,25));assertEquals(0,DestructionTimeline.ember(119));assertEquals(0,DestructionTimeline.ember(1100));
    }
}
