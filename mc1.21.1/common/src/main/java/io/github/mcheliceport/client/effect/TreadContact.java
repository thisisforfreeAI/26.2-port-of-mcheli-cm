package io.github.mcheliceport.client.effect;

import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

/** Keeps the stamp origin at local z=0 as the supporting tread wheels change on ramps. */
public final class TreadContact {
    private TreadContact() {}
    public static @Nullable Vec3 center(Vec3 rear, double rearZ, Vec3 front, double frontZ) {
        if (!Double.isFinite(rear.x + rear.y + rear.z + front.x + front.y + front.z + rearZ + frontZ)
                || rearZ > 0 || frontZ < 0 || frontZ - rearZ < 1e-6 || rear.distanceToSqr(front) > 144) return null;
        return rear.lerp(front, -rearZ / (frontZ - rearZ));
    }
}
