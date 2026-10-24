package io.github.mcheliceport.client.effect;

import static org.junit.jupiter.api.Assertions.*;
import net.minecraft.world.phys.Vec3;
import org.junit.jupiter.api.Test;

class TreadContactTest {
    @Test void supportChangesOnASlopeKeepTheSameOriginInsteadOfJumpingBetweenWheels() {
        Vec3 center = new Vec3(1.8, 0.5, 0);
        assertEquals(center, TreadContact.center(new Vec3(1.8, 0, -1), -1, new Vec3(1.8, 1, 1), 1));
        assertEquals(center, TreadContact.center(new Vec3(1.8, -0.5, -2), -2, new Vec3(1.8, 1, 1), 1));
        assertEquals(center, TreadContact.center(new Vec3(1.8, 0, -1), -1, new Vec3(1.8, 1.5, 2), 2));
    }
    @Test void invalidUnbracketedOrTeleportContactsCannotCreateImprints() {
        assertNull(TreadContact.center(Vec3.ZERO, 0, Vec3.ZERO, 0));
        assertNull(TreadContact.center(Vec3.ZERO, 1, new Vec3(1, 0, 1), 2));
        assertNull(TreadContact.center(Vec3.ZERO, -1, new Vec3(100, 0, 1), 1));
        assertNull(TreadContact.center(new Vec3(Double.NaN, 0, 0), -1, Vec3.ZERO, 1));
    }
}
