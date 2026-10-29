package io.github.mcheliceport.client.sound;

import static org.junit.jupiter.api.Assertions.*;
import static io.github.mcheliceport.client.sound.ProjectileFlybyLook.Kind.*;
import net.minecraft.world.phys.Vec3;
import org.junit.jupiter.api.Test;

class ProjectileFlybyLookTest {
    @Test void onlyConventionalRocketsMissilesAndCannonRoundsQualify(){
        assertEquals(ROCKET,ProjectileFlybyLook.kind("rocket",false,false,0,0));
        assertEquals(ROCKET,ProjectileFlybyLook.kind("asmissile",false,false,0,0));
        assertEquals(SHELL,ProjectileFlybyLook.kind("machinegun2",false,true,20,0));
        assertEquals(NONE,ProjectileFlybyLook.kind("machinegun1",false,true,4,0));
        for(String type:new String[]{"rocket","asmissile","machinegun2"})assertEquals(NONE,ProjectileFlybyLook.kind(type,true,true,100,20));
        for(String type:new String[]{"bomb","torpedo","markerrocket","dispenser"})assertEquals(NONE,ProjectileFlybyLook.kind(type,false,false,100,20));
    }
    @Test void closeIncomingPassesArePredictedInThreeDimensions(){
        assertTrue(ProjectileFlybyLook.incoming(new Vec3(2,0,20),new Vec3(0,0,-4),Vec3.ZERO,ROCKET));
        assertTrue(ProjectileFlybyLook.incoming(new Vec3(2,20,0),new Vec3(0,-4,0),Vec3.ZERO,SHELL));
        assertFalse(ProjectileFlybyLook.incoming(new Vec3(20,0,20),new Vec3(0,0,-4),Vec3.ZERO,ROCKET));
        assertFalse(ProjectileFlybyLook.incoming(new Vec3(2,0,20),new Vec3(0,0,4),Vec3.ZERO,SHELL));
        assertFalse(ProjectileFlybyLook.incoming(new Vec3(2,0,200),new Vec3(0,0,-4),Vec3.ZERO,SHELL));
    }
    @Test void stationaryWarpsInvalidAndAlreadyPassingShotsDoNotGenerateCues(){
        for(Vec3 motion:new Vec3[]{Vec3.ZERO,new Vec3(0,0,-100),new Vec3(Double.NaN,0,-4)})assertFalse(ProjectileFlybyLook.incoming(new Vec3(0,0,20),motion,Vec3.ZERO,ROCKET));
        assertFalse(ProjectileFlybyLook.incoming(new Vec3(0,0,-20),new Vec3(0,0,-4),Vec3.ZERO,ROCKET));
        assertFalse(ProjectileFlybyLook.incoming(new Vec3(0,0,2),new Vec3(0,0,-4),Vec3.ZERO,SHELL));
    }
}
