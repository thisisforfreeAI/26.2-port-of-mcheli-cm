package io.github.mcheliceport.client.effect;
import static org.junit.jupiter.api.Assertions.*;
import org.junit.jupiter.api.Test;
class FluidLeakLookTest {
    @Test void stainsRequireParkedMotionAndHaveBoundedGrowth(){
        assertTrue(FluidLeakLook.parked(0));assertFalse(FluidLeakLook.parked(.1));assertFalse(FluidLeakLook.parked(Double.NaN));
        float radius=.17F;for(int i=0;i<100;i++)radius=FluidLeakLook.grow(radius);assertEquals(.55F,radius);
    }
    @Test void puddlesDryAfterLeakingStopsAndCoolantDriesSooner(){
        assertTrue(FluidLeakLook.life(true)<FluidLeakLook.life(false));
        for(boolean coolant:new boolean[]{false,true}){
            int life=FluidLeakLook.life(coolant);float previous=1;
            for(int age=10;age<=life;age++){float alpha=FluidLeakLook.alpha(age,age,life);assertTrue(alpha<=previous&&alpha>=0);previous=alpha;}
            assertEquals(0,FluidLeakLook.alpha(life,life,life));
        }
    }
}
