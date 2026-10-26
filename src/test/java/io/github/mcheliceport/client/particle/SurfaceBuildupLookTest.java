package io.github.mcheliceport.client.particle;
import static org.junit.jupiter.api.Assertions.*;
import org.junit.jupiter.api.Test;
class SurfaceBuildupLookTest {
    @Test void pavementDoesNotAddDirtAndParkingPreservesExistingMud(){
        var s=new SurfaceBuildupLook();for(int i=0;i<500;i++)s.tick(true,false,false,false,false);assertEquals(0,s.dirt());
        for(int i=0;i<100;i++)s.tick(true,true,false,false,true);float before=s.dirt();
        for(int i=0;i<100;i++)s.tick(false,true,false,false,false);assertEquals(before,s.dirt());assertTrue(s.soot()>0);
        for(int i=0;i<100;i++)s.tick(true,true,true,false,false);assertEquals(0,s.dirt());
    }
    @Test void rainGraduallyWashesAndStatesRemainBounded(){
        var s=new SurfaceBuildupLook();for(int i=0;i<1000;i++)s.tick(true,true,false,false,true);assertEquals(1,s.dirt());assertEquals(1,s.soot());
        s.tick(false,false,false,true,false);assertTrue(s.dirt()>.99F&&s.dirt()<1);
        for(int i=0;i<5000;i++)s.tick(false,false,false,true,false);assertEquals(0,s.dirt());assertEquals(0,s.soot());
    }
}
