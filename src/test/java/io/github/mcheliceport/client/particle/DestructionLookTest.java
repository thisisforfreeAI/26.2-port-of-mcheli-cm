package io.github.mcheliceport.client.particle;

import static org.junit.jupiter.api.Assertions.*;
import net.minecraft.world.phys.Vec3;
import org.junit.jupiter.api.Test;

class DestructionLookTest {
    @Test void directionalRupturesAreFiniteAndWeaponMachineryShapesDiffer(){
        for(var kind:DestructionLook.Kind.values())for(Vec3 at:new Vec3[]{Vec3.ZERO,new Vec3(Double.NaN,0,0),new Vec3(0,0,-1)}){
            var axis=DestructionLook.direction(at,kind);assertEquals(1,axis.length(),1e-6);assertTrue(Double.isFinite(axis.y));
        }
        assertTrue(DestructionLook.direction(new Vec3(0,0,-1),DestructionLook.Kind.TANK).y>
                DestructionLook.direction(new Vec3(0,0,-1),DestructionLook.Kind.PLANE).y);
    }
    @Test void flashAndInteriorCoolWithoutLeavingAPermanentGlowingHull(){
        assertEquals(1,DestructionLook.pulse(0));assertEquals(0,DestructionLook.pulse(10));
        assertEquals(0,DestructionLook.pulse(-1));assertEquals(0,DestructionLook.pulse(Float.NaN));
        assertEquals(0,DestructionLook.interior(1000,20,7));
        assertTrue(DestructionLook.interior(100,0,7)>DestructionLook.interior(100,11,7));
        for(int age=0;age<1500;age++)assertTrue(DestructionLook.interior(age,100,7)<=.18F);
    }
    @Test void collapseStartsLaterNeverAddsDeepSinkingAndEndsStill(){
        for(var kind:DestructionLook.Kind.values())for(int seed:new int[]{0,1}){
            assertEquals(new DestructionLook.Settle(0,0,0),DestructionLook.settle(79,kind,seed));
            for(int age=0;age<500;age++){
                var s=DestructionLook.settle(age,kind,seed);assertTrue(s.drop()>=-.10001F&&s.drop()<=.012F);
                assertTrue(Math.abs(s.roll())<.08F);
            }
            assertEquals(DestructionLook.settle(200,kind,seed),DestructionLook.settle(2000,kind,seed));
        }
        assertEquals(-DestructionLook.settle(200,DestructionLook.Kind.HELICOPTER,0).roll(),DestructionLook.settle(200,DestructionLook.Kind.HELICOPTER,1).roll());
    }
    @Test void OnlySmallPanelsGainExtraTumbleAndPopsRemainVariedButBounded(){
        assertEquals(0,DestructionLook.panelSpin(7,8).length());assertEquals(0,DestructionLook.panelSpin(7,Float.NaN).length());
        assertTrue(DestructionLook.panelSpin(7,1).length()>DestructionLook.panelSpin(7,2).length());
        for(int i=0;i<300;i++){float s=DestructionLook.burstStrength(i,144);assertTrue(s>=.48F&&s<=1);}
    }
}
