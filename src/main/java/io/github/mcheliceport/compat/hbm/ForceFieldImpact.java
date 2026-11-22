package io.github.mcheliceport.compat.hbm;

import io.github.mcheliceport.entity.aircraft.AircraftEntity;
import io.github.mcheliceport.entity.aircraft.AircraftHitBox;
import io.github.mcheliceport.entity.seat.SeatEntity;
import net.minecraft.world.entity.Entity;

/**
 * NTM Next's force field emitter takes damage from every non-player entity that crosses its
 * sphere: box height x width x width x speed x 50. A McHeli vehicle's box is many blocks wide, so
 * one touch, or its hitboxes and seats crossing with it, empties the shield at once. Vehicles,
 * their parts and anything riding in them therefore cost the shield nothing; the field still
 * bounces them back. Projectiles fired from vehicles keep NTM's normal impact damage.
 */
public final class ForceFieldImpact {

    private ForceFieldImpact() {
    }

    /** Whether the force field should take no damage when {@code entity} crosses it. */
    public static boolean isVehicle(Entity entity) {
        if (entity instanceof AircraftEntity || entity instanceof AircraftHitBox || entity instanceof SeatEntity) {
            return true;
        }
        return entity.getRootVehicle() instanceof AircraftEntity;
    }
}
