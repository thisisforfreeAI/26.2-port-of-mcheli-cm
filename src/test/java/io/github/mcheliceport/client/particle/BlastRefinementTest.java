package io.github.mcheliceport.client.particle;

import static org.junit.jupiter.api.Assertions.*;
import io.github.mcheliceport.effect.BlastStyle;
import java.util.AbstractList;
import java.util.ArrayList;
import net.minecraft.core.BlockPos;
import net.minecraft.world.phys.Vec3;
import org.junit.jupiter.api.Test;

class BlastRefinementTest {
    @Test void largePenetratorSmokeFollowsItsReportedCraterWithoutEnlargingTheCore() {
        float core=ExplosionSequence.radius(15,BlastStyle.PENETRATOR);
        float cloud=ExplosionCraterScale.cloudRadius(core,18,BlastStyle.PENETRATOR,
                io.github.mcheliceport.config.ClientConfig.EffectBudget.BALANCED);
        assertTrue(cloud>core*3 && cloud<=16);
        assertEquals(core,ExplosionCraterScale.cloudRadius(core,0,BlastStyle.PENETRATOR,
                io.github.mcheliceport.config.ClientConfig.EffectBudget.BALANCED));
    }
    @Test void angularFootprintFollowsUnevenCraterAndIgnoresIsolatedRayTips() {
        var positions=new ArrayList<BlockPos>();
        for(int x=-12;x<=12;x++)for(int z=-12;z<=12;z++) {
            int radius=x>0?12:5;
            if(x*x+z*z<=radius*radius)positions.add(new BlockPos(x, x>0?64:61,z));
        }
        var footprint=BlastSurfaceFootprint.sample(.5,.5,positions);
        double east=.10,west=-Math.PI+.10;
        assertTrue(footprint.reach(east,1)>footprint.reach(west,1)*1.7);
        assertEquals(65,footprint.surfaceTop(east,64));
        assertEquals(62,footprint.surfaceTop(west,64));
        float before=footprint.reach(east,1);
        positions.add(new BlockPos(100000,64,0));
        assertEquals(before,BlastSurfaceFootprint.sample(.5,.5,positions).reach(east,1),.6);
        assertEquals(9,BlastSurfaceFootprint.EMPTY.reach(0,9));
        assertEquals(64,BlastSurfaceFootprint.EMPTY.surfaceTop(0,64));
    }
    @Test void samplingHugePacketsIsBoundedAndDoesNotFillEmptySectors() {
        int[] reads={0};
        var huge=new AbstractList<BlockPos>() {
            public int size(){return Integer.MAX_VALUE;}
            public BlockPos get(int i){reads[0]++;return new BlockPos(1000000,1000000,0);}
        };
        var footprint=BlastSurfaceFootprint.sample(0,0,huge);
        assertEquals(4096,reads[0]);
        assertTrue(footprint.reach(.001,9)<=24);
        assertEquals(0,footprint.reach(-Math.PI+.001,9));
        assertEquals(72,footprint.surfaceTop(.001,64));
    }
    @Test void silhouettesKeepDominantCoresAndWeaponShapesRemainDistinct() {
        for(int index=2;index<28;index++)for(float t:new float[]{0,.5F,1}) {
            assertTrue(BlastShape.scale(index,t)<BlastShape.scale(0,0));
            assertTrue(BlastShape.scale(index,t)>0 && BlastShape.scale(index,t)<=1);
        }
        var axis=new Vec3(1,.3,0).normalize();
        var rocket=BlastShape.offset(BlastStyle.BLAST,axis,1,1,3);
        var bomb=BlastShape.offset(BlastStyle.HEAVY,axis,1,1,3);
        var penetrator=BlastShape.offset(BlastStyle.PENETRATOR,axis,1,1,3);
        assertTrue(rocket.x>bomb.x && rocket.z<bomb.z);
        assertTrue(penetrator.length()<bomb.length());
        assertTrue(BlastShape.height(BlastStyle.PENETRATOR,false,.5F)>BlastShape.height(BlastStyle.HEAVY,false,.5F));
        assertEquals(new Vec3(1,0,1),BlastShape.offset(BlastStyle.BLAST,Vec3.ZERO,1,1,3));
    }
    @Test void detailTransitionsCannotJumpWhenPressureChanges() {
        float value=1;
        for(int i=0;i<5;i++) {
            float next=BlastDepthLook.approach(value,0);
            assertTrue(next>=0 && value-next<=.221F);value=next;
        }
        assertEquals(0,value);
        for(int i=0;i<5;i++)value=BlastDepthLook.approach(value,1);
        assertEquals(1,value);
    }
}
