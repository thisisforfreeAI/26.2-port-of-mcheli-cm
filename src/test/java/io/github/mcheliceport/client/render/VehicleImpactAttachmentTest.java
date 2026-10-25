package io.github.mcheliceport.client.render;

import static org.junit.jupiter.api.Assertions.*;
import io.github.mcheliceport.client.model.McheliModel;
import java.util.List;
import org.joml.Matrix4f;
import org.joml.Vector3f;
import org.junit.jupiter.api.Test;

class VehicleImpactAttachmentTest {
    @Test void attachmentFollowsTheMovingPartAndRejectsHiddenOrReplacedInstances(){
        var group=new McheliModel.Group("$turret",new float[24]);
        var first=new AircraftPartPoses.Draw(new Matrix4f(),List.of(group));
        var turned=new AircraftPartPoses.Draw(new Matrix4f().translation(3,0,0).rotateY((float)Math.PI/2),List.of(group));
        var pose=VehicleImpactMarks.partPose(List.of(first,turned),group,1,2);
        var point=pose.transformPosition(new Vector3f(0,0,2));
        assertEquals(5,point.x,1e-5);assertEquals(0,point.z,1e-5);
        assertNull(VehicleImpactMarks.partPose(List.of(first),group,1,2));
        var replacement=new McheliModel.Group("$turret",new float[24]);
        assertNull(VehicleImpactMarks.partPose(List.of(new AircraftPartPoses.Draw(new Matrix4f(),List.of(replacement))),group,0,1));
    }
    @Test void NamedWindowsGetCracksWhileOpaqueBodyKeepsItsGunScar(){
        for(int seed=-9;seed<9;seed++){
            assertTrue(VehicleImpactMarks.tile("$window_front",false,seed)>=6);
            assertTrue(VehicleImpactMarks.tile("$windscreen",true,seed)>=6);
            assertTrue(VehicleImpactMarks.tile("$body",false,seed)<4);
            int cannon=VehicleImpactMarks.tile("$body",true,seed);assertTrue(cannon>=4&&cannon<6);
        }
    }
}
