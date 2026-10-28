package io.github.mcheliceport.client.render;

import static org.junit.jupiter.api.Assertions.*;
import io.github.mcheliceport.client.model.ControlSurfaceMeshes;
import io.github.mcheliceport.client.model.McheliModel;
import net.minecraft.world.phys.Vec3;
import org.joml.Matrix4f;
import org.joml.Vector3f;
import org.junit.jupiter.api.Test;

class DamagedControlLookTest {
    @Test void intactControlsStayStillAndRepairsReduceDamageBeforeSeparation() {
        assertEquals(0,DamagedControlLook.strength(1,0));assertEquals(0,DamagedControlLook.strength(1,.4F));
        assertTrue(DamagedControlLook.strength(.3F,0)>DamagedControlLook.strength(.6F,0));
        assertTrue(DamagedControlLook.detached(.08F));assertFalse(DamagedControlLook.detached(.2F));
        assertFalse(DamagedControlLook.detached(Float.NaN));
        for(int t=0;t<500;t++)assertEquals(0,DamagedControlLook.angle(0,2,t,6,false,1));
    }
    @Test void flutterIsBoundedUnevenAndStopsInStillAirOrAtDistance() {
        float min=Float.POSITIVE_INFINITY,max=Float.NEGATIVE_INFINITY;
        for(int tick=0;tick<300;tick++){
            float angle=DamagedControlLook.angle(1,2,tick,5,false,1);min=Math.min(min,angle);max=Math.max(max,angle);
            assertTrue(Math.abs(angle)<=Math.toRadians(17.001));
            assertEquals(DamagedControlLook.angle(1,0,0,5,false,1),DamagedControlLook.angle(1,0,tick,5,false,1));
            assertEquals(DamagedControlLook.angle(1,2,0,5,false,0),DamagedControlLook.angle(1,2,tick,5,false,0));
        }
        assertTrue(max-min>Math.toRadians(5));
        for(double t:new double[]{Double.NaN,Double.POSITIVE_INFINITY,Double.MAX_VALUE})assertTrue(Float.isFinite(DamagedControlLook.angle(1,2,t,Integer.MAX_VALUE,true,1)));
    }
    @Test void hingeRemainsFixedUnderTheCompleteAircraftBodyPose() {
        var pivot=new Vec3(-3,1,-4);
        var body=new Matrix4f().translate(2,.35F,5).rotateXYZ(.3F,-.8F,.4F);
        Vector3f expected=body.transformPosition(new Vector3f(-3,1,-4));
        for(var axis:new Vec3[]{new Vec3(1,0,0),new Vec3(0,1,0)}){
            var panel=new ControlSurfaceMeshes.Panel(0,1,new McheliModel.Group("$body",new float[0]),pivot,axis,new ControlSurfaceMeshes.Box(pivot,pivot));
            for(float angle:new float[]{-.3F,0,.2F}){
                var actual=DamagedControlSurfaces.hinge(body,panel,angle).transformPosition(new Vector3f(-3,1,-4));
                assertEquals(expected.x,actual.x,.00001);assertEquals(expected.y,actual.y,.00001);assertEquals(expected.z,actual.z,.00001);
            }
        }
    }
}
