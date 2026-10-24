package io.github.mcheliceport.client.particle;

import static org.junit.jupiter.api.Assertions.*;
import io.github.mcheliceport.config.ClientConfig.EffectBudget;
import io.github.mcheliceport.effect.BlastStyle;
import java.util.AbstractList;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.core.BlockPos;
import org.junit.jupiter.api.Test;

class ExplosionCraterScaleTest {
    @Test void affectedFootprintExpandsSmokeWithoutLettingRayOutliersDominate() {
        var positions = new ArrayList<BlockPos>();
        for (int x=-10;x<=10;x++) for(int z=-10;z<=10;z++)
            if(x*x+z*z<=100) positions.add(new BlockPos(x,60,z));
        float footprint = ExplosionCraterScale.footprint(0.5,0.5,positions);
        assertTrue(footprint>=9 && footprint<=10.5);
        positions.add(new BlockPos(10000,60,10000));
        assertEquals(footprint, ExplosionCraterScale.footprint(0.5,0.5,positions),0.5);
        float nominal = ExplosionSequence.radius(10,BlastStyle.BLAST);
        float enlarged = ExplosionCraterScale.cloudRadius(nominal,footprint,BlastStyle.BLAST,EffectBudget.BALANCED);
        assertTrue(enlarged>nominal);
        assertTrue(enlarged<=nominal*2.5F && enlarged<=16);
        assertTrue(ExplosionCraterScale.groundReach(6,footprint)>=footprint);
    }
    @Test void emptyAirburstsCannonsAndInvalidMetadataRetainTheirNominalSize() {
        assertEquals(0,ExplosionCraterScale.footprint(0,0,List.of()));
        for(var quality:EffectBudget.values()) {
            assertEquals(2,ExplosionCraterScale.cloudRadius(2,0,BlastStyle.HEAVY,quality));
            assertEquals(2,ExplosionCraterScale.cloudRadius(2,Float.NaN,BlastStyle.HEAVY,quality));
            assertEquals(2,ExplosionCraterScale.cloudRadius(2,20,BlastStyle.CANNON,quality));
        }
        assertEquals(24,ExplosionCraterScale.groundReach(10,Float.MAX_VALUE));
        assertTrue(ExplosionCraterScale.cloudRadius(8,30,BlastStyle.HEAVY,EffectBudget.LOW)<=10);
    }
    @Test void largePenetratorCratersGainRimDustWithoutChangingSmallDirectionalImpacts() {
        assertEquals(0,BlastRingLook.sectors(15,BlastStyle.PENETRATOR,EffectBudget.BALANCED,0,6));
        assertEquals(14,BlastRingLook.sectors(15,BlastStyle.PENETRATOR,EffectBudget.BALANCED,0,18));
        assertEquals(0,BlastRingLook.sectors(15,BlastStyle.PENETRATOR,EffectBudget.BALANCED,1,18));
        assertEquals(0,BlastRingLook.sectors(15,BlastStyle.PENETRATOR,EffectBudget.LOW,0,18));
    }
    @Test void hugeReportedListsHaveBoundedSamplingAndQualityCaps() {
        int[] reads={0};
        var enormous=new AbstractList<BlockPos>() {
            @Override public int size(){return Integer.MAX_VALUE;}
            @Override public BlockPos get(int index){reads[0]++;return new BlockPos(1000000,60,1000000);}
        };
        assertTrue(ExplosionCraterScale.footprint(0,0,enormous)<=31.5);
        assertEquals(4096,reads[0]);
        assertTrue(ExplosionCraterScale.cloudRadius(8,Float.MAX_VALUE,BlastStyle.HEAVY,EffectBudget.HIGH)<=22);
    }
}
