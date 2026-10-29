package io.github.mcheliceport.weapon.explosion;

import static org.junit.jupiter.api.Assertions.*;
import io.github.mcheliceport.effect.BlastTerrainSample;
import java.util.HashSet;
import net.minecraft.core.BlockPos;
import org.junit.jupiter.api.Test;

class BlastTerrainSampleTest {
    @Test void reservoirStaysBoundedDeterministicAndSamplesBeyondTheFirstBlocks() {
        var first=new BlastTerrainSample();var second=new BlastTerrainSample();
        var pos=new BlockPos.MutableBlockPos();
        for(int i=0;i<100000;i++){pos.set(i,64,0);first.offer(pos);second.offer(pos);}
        var sample=first.snapshot(p->true);
        assertEquals(256,sample.size());assertEquals(sample,second.snapshot(p->true));
        assertEquals(256,new HashSet<>(sample).size());
        assertTrue(sample.stream().anyMatch(p->p.getX()>90000));
        assertTrue(sample.stream().anyMatch(p->p.getX()<10000));
        int[] reads={0};
        assertTrue(first.snapshot(p->{reads[0]++;return false;}).isEmpty());
        assertEquals(256,reads[0]);
    }
    @Test void protectedBlocksAreOmittedAndEmptyAirburstsNeedNoReads() {
        var sample=new BlastTerrainSample();int[] reads={0};
        assertTrue(sample.snapshot(p->{reads[0]++;return true;}).isEmpty());assertEquals(0,reads[0]);
        for(int i=0;i<10;i++)sample.offer(new BlockPos(i,64,0));
        assertEquals(5,sample.snapshot(p->p.getX()%2==0).size());
    }
}
