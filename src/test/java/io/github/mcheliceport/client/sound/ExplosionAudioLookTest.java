package io.github.mcheliceport.client.sound;
import static org.junit.jupiter.api.Assertions.*;
import org.junit.jupiter.api.Test;
class ExplosionAudioLookTest {
    @Test void propagationAndTailStayBoundedAndLargeBlastsHaveDeeperLaterTails() {
        assertEquals(0, ExplosionAudioLook.arrival(0));
        assertEquals(2, ExplosionAudioLook.arrival(34.3));
        assertEquals(4, ExplosionAudioLook.arrival(64));
        assertEquals(4, ExplosionAudioLook.arrival(Double.MAX_VALUE));
        assertEquals(0, ExplosionAudioLook.arrival(Double.NaN));
        assertTrue(ExplosionAudioLook.tailDelay(12) > ExplosionAudioLook.tailDelay(3));
        assertTrue(ExplosionAudioLook.tailPitch(12) < ExplosionAudioLook.tailPitch(3));
        assertTrue(Float.isFinite(ExplosionAudioLook.tailPitch(Float.NaN)));
        assertTrue(ExplosionAudioLook.tailPitch(Float.MAX_VALUE) >= 0.45F);
    }
}
