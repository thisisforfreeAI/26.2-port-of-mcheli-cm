package io.github.mcheliceport.client.particle;

import static org.junit.jupiter.api.Assertions.*;
import io.github.mcheliceport.effect.BlastStyle;
import net.minecraft.world.phys.Vec3;
import org.junit.jupiter.api.Test;

class ExplosionFootprintTest {
    @Test void shallowCruiseBurstsFollowHeadingWithinExistingCloudEnvelope() {
        for (int i = 0; i < 360; i++) {
            double angle = Math.toRadians(i);
            var p = ExplosionFootprint.cloudOffset(10, BlastStyle.BLAST, new Vec3(1,0,0), Math.cos(angle)*0.43, Math.sin(angle)*0.43, 1);
            assertTrue(p.length() < 0.65);
        }
        var along = ExplosionFootprint.cloudOffset(10, BlastStyle.BLAST, new Vec3(1,0,0), .43,0,1);
        var cross = ExplosionFootprint.cloudOffset(10, BlastStyle.BLAST, new Vec3(0,0,1), .43,0,1);
        assertTrue(along.x > cross.x);
        assertTrue(cross.z > 0);
        assertEquals(new Vec3(.43,0,0), ExplosionFootprint.cloudOffset(10, BlastStyle.HEAVY, new Vec3(1,0,0),.43,0,1));
        assertEquals(new Vec3(.43,0,0), ExplosionFootprint.cloudOffset(10, BlastStyle.BLAST, new Vec3(0,1,0),.43,0,1));
    }
}
