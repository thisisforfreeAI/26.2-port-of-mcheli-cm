package io.github.mcheliceport.effect;

import static org.junit.jupiter.api.Assertions.*;
import org.joml.Vector3f;
import org.junit.jupiter.api.Test;

class VehicleImpactLookTest {
    @Test void cosmeticContactKeepsRayHeightRatherThanHitboxOrigin(){
        var point=VehicleImpactLook.contact(new net.minecraft.world.phys.Vec3(10,4,5),new net.minecraft.world.phys.Vec3(-1,0,0),
                new net.minecraft.world.phys.AABB(0,2,3,2,6,7)).orElseThrow();
        assertEquals(2.3,point.x,1e-6);assertEquals(4,point.y);assertEquals(5,point.z);
        assertTrue(VehicleImpactLook.contact(new net.minecraft.world.phys.Vec3(10,20,5),new net.minecraft.world.phys.Vec3(-1,0,0),
                new net.minecraft.world.phys.AABB(0,2,3,2,6,7)).isEmpty());
    }
    @Test void repairFadesCosmeticsWithoutRegrowingThem(){
        float opacity=VehicleImpactLook.repaired(1,10,1000,false);
        assertTrue(opacity<1&&opacity>0);assertEquals(opacity,VehicleImpactLook.repaired(opacity,-100,1000,false));
        assertEquals(0,VehicleImpactLook.repaired(opacity,1,1000,true));
        assertEquals(0,VehicleImpactLook.repaired(opacity,1000,1000,false));
    }
    @Test void notificationGateBoundsBurstsAndRecoversAfterTimeReversal(){
        var gate=new VehicleImpactLook.Gate();for(int i=0;i<4;i++)assertTrue(gate.admit(12,4,4));
        for(int i=0;i<100;i++)assertFalse(gate.admit(15,4,4));
        assertTrue(gate.admit(16,4,4));assertTrue(gate.admit(0,4,4));
    }
    @Test void bodySpaceSurvivesVehicleTranslationAndRotation(){
        var local=new Vector3f(2,1,-3);var pose=VehicleImpactLook.body(35,-12,8);
        var world=pose.transformPosition(new Vector3f(local)).add(300,90,-700);
        var restored=pose.invert().transformPosition(world.sub(300,90,-700));
        assertEquals(local.x,restored.x,1e-4);assertEquals(local.y,restored.y,1e-4);assertEquals(local.z,restored.z,1e-4);
        assertTrue(VehicleImpactLook.radius(true,20)>VehicleImpactLook.radius(false,20));
        assertTrue(VehicleImpactLook.radius(true,10000)<=.55F);
    }
}
