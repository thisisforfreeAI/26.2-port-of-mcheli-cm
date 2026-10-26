package io.github.mcheliceport.client.render;

import io.github.mcheliceport.config.*;
import io.github.mcheliceport.entity.aircraft.*;

/** Shared intensity for cones, entity highlights and lamp-local sparks. */
public final class VehicleLightDamage {
    private VehicleLightDamage() {}
    public static float strength(AircraftEntity aircraft, int lamp) {
        if (!EffectQuality.enhanced() || !ClientConfig.get(ClientConfig.VEHICLE_DAMAGE_DETAILS)) return 1;
        return DamagedLightLook.intensity(aircraft.getMaxHP() > 0
                ? 1 - (float) aircraft.getHP() / aircraft.getMaxHP() : 0,
                aircraft.isDestroyed(), aircraft.getId(), lamp, aircraft.level().getGameTime());
    }
}
