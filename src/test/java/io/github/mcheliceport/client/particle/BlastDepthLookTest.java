package io.github.mcheliceport.client.particle;
import static org.junit.jupiter.api.Assertions.*;
import io.github.mcheliceport.config.ClientConfig.EffectBudget;
import org.junit.jupiter.api.Test;
class BlastDepthLookTest {
    @Test void nearbyGeometryDropsBeforePrimaryParticlesUnderLoad() {
        assertTrue(BlastDepthLook.detail(EffectBudget.BALANCED,0,48*48));
        assertFalse(BlastDepthLook.detail(EffectBudget.BALANCED,0,49*49));
        assertTrue(BlastDepthLook.detail(EffectBudget.HIGH,0,64*64));
        for(var q:EffectBudget.values()) {
            assertFalse(BlastDepthLook.detail(q,1,1));
            assertFalse(BlastDepthLook.detail(q,0,Double.NaN));
        }
        assertFalse(BlastDepthLook.detail(EffectBudget.LOW,0,1));
        int crossed=0,layered=0;
        for(int i=0;i<28;i++) {
            if(BlastDepthLook.cloudCards(true,false,i)>1)crossed++;
            if(BlastDepthLook.cloudCards(true,true,i)>1)layered++;
            assertEquals(1,BlastDepthLook.cloudCards(false,true,i));
        }
        assertEquals(4,crossed);assertEquals(2,layered);
    }
    @Test void largeChunksAndTaperedColumnsKeepTheirChargedBounds() {
        for(int i=0;i<28;i++) {
            assertTrue(BlastDepthLook.chunkScale(15,true,i)<=2);
            assertEquals(1,BlastDepthLook.chunkScale(3,true,i));
            assertEquals(1,BlastDepthLook.chunkScale(15,false,i));
        }
        assertTrue(BlastDepthLook.columnScale(0)>BlastDepthLook.columnScale(1));
        assertTrue(BlastDepthLook.columnScale(1)>BlastDepthLook.columnScale(2));
        for(int i=0;i<3;i++)assertTrue(i*.55+Math.sqrt(2)*BlastDepthLook.columnScale(i)<2.2);
    }
}
