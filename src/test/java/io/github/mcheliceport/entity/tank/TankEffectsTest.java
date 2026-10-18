package io.github.mcheliceport.entity.tank;

import static org.junit.jupiter.api.Assertions.*;
import io.github.mcheliceport.content.ContentLocation;
import io.github.mcheliceport.content.aircraft.TankInfo;
import io.github.mcheliceport.effect.ParticleParam;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.util.RandomSource;
import net.minecraft.world.phys.Vec3;
import org.junit.jupiter.api.Test;

class TankEffectsTest {
    @Test
    void criticalDamageAddsHeatWithoutChangingSmokeMotionOrRandomSequence() {
        TankInfo info = new TankInfo(new ContentLocation("test", "tank"), "tank", "test");
        List<ParticleParam> out = new ArrayList<>();
        RandomSource actual = RandomSource.create(765);
        TankParticles.damage(info, 10, 100, Vec3.ZERO, 0, 0, 0, actual, out::add);
        assertEquals(1, out.size());
        ParticleParam p = out.getFirst();
        RandomSource expected = RandomSource.create(765);
        float color = 0.2F + expected.nextFloat() * 0.3F;
        assertEquals(2 * (expected.nextDouble() - 0.5) * 0.3, p.motionX);
        assertEquals(2 * expected.nextDouble() * 0.1, p.motionY);
        assertEquals(2 * (expected.nextDouble() - 0.5) * 0.3, p.motionZ);
        assertEquals(2 * (expected.nextInt(5) + 5.0F), p.size);
        assertEquals(0.7F + expected.nextFloat() * 0.1F, p.a);
        assertEquals(color, p.r);
        assertEquals(1, p.damageHeat);
        assertEquals(expected.nextLong(), actual.nextLong(), "appearance metadata consumes no random draws");
    }
}
