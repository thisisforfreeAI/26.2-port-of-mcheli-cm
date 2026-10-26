package io.github.mcheliceport.client.particle;
import static org.junit.jupiter.api.Assertions.*;
import net.minecraft.world.phys.Vec3;
import org.junit.jupiter.api.Test;
class VehicleShrapnelLookTest {
    @Test void fragmentsLeaveThePanelAndFollowAStableDirectionalFan(){
        for(Vec3 n:new Vec3[]{new Vec3(1,0,0),new Vec3(0,1,0),new Vec3(0,0,-1)}){
            for(int i=0;i<8;i++){
                var v=VehicleShrapnelLook.velocity(n.scale(-1),n,52,i,true);
                assertTrue(v.dot(n)>.07);assertTrue(v.length()<.8);
                assertEquals(v,VehicleShrapnelLook.velocity(n.scale(-1),n,52,i,true));
            }
        }
    }
    @Test void GlancingHitsStillThrowFragmentsAwayFromTheSkin(){
        var n=new Vec3(0,1,0);var incoming=new Vec3(1,-.01,0);
        for(int i=0;i<8;i++)assertTrue(VehicleShrapnelLook.velocity(incoming,n,-12,i,false).dot(n)>.07);
    }
}
