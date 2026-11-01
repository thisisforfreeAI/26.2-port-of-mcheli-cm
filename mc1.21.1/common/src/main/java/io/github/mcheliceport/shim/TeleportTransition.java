package io.github.mcheliceport.shim;

import java.util.Set;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.portal.DimensionTransition;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

/**
 * 1.21.1 stand-in for 26.2's {@code TeleportTransition}. The port only moves entities within a
 * level or to another one with a velocity, which 1.21.1 does with {@code teleportTo} and
 * {@code changeDimension}; {@link #teleport} plays the part of 26.2's {@code Entity#teleport}.
 */
public record TeleportTransition(ServerLevel newLevel, Vec3 position, Vec3 deltaMovement, float yRot, float xRot,
        Object postTransition) {

    /** 26.2's {@code TeleportTransition.DO_NOTHING}. */
    public static final Object DO_NOTHING = new Object();

    /** 26.2's {@code entity.teleport(transition)}: the moved entity, or null when it failed. */
    public static @Nullable Entity teleport(Entity entity, TeleportTransition t) {
        if (entity.level() == t.newLevel()) {
            if (!entity.teleportTo(t.newLevel(), t.position().x, t.position().y, t.position().z, Set.of(), t.yRot(),
                    t.xRot())) {
                return null;
            }
            entity.setDeltaMovement(t.deltaMovement());
            return entity;
        }
        return entity.changeDimension(new DimensionTransition(t.newLevel(), t.position(), t.deltaMovement(), t.yRot(),
                t.xRot(), DimensionTransition.DO_NOTHING));
    }
}
