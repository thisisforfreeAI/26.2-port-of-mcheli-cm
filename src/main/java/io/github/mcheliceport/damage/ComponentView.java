package io.github.mcheliceport.damage;

import net.minecraft.world.phys.Vec3;

/**
 * One component of a vehicle as other code sees it (M8 damage marks, the HUD): read-only.
 *
 * @param index          its number among components of the same kind, from 0
 * @param health         0 (disabled) to 1 (intact)
 * @param center         zone centre in the vehicle's unanimated body coordinates, the frame
 *                       {@code VehicleImpactLook#body} and the impact payload use
 * @param halfSize       zone half extents in the same frame
 * @param partName       the model part the zone belongs to (a rotor's blade group), or empty
 * @param lastChangeTick level game time this component's health last changed on this side, or -1
 */
public record ComponentView(ComponentKind kind, int index, ComponentState state, float health, Vec3 center,
        Vec3 halfSize, String partName, long lastChangeTick) {
}
