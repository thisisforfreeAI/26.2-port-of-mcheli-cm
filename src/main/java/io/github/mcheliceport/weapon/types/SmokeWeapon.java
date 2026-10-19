package io.github.mcheliceport.weapon.types;

import io.github.mcheliceport.content.weapon.WeaponInfo;
import io.github.mcheliceport.weapon.WeaponBase;
import io.github.mcheliceport.weapon.WeaponParam;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;

/**
 * Smoke generator: never fires; the vehicle trails smoke while it is selected and the throttle is
 * open (see {@code AircraftEntity#onUpdateParticleSmoke}). Ported from upstream {@code MCH_WeaponSmoke}.
 */
public class SmokeWeapon extends WeaponBase {

    public SmokeWeapon(Level w, Vec3 v, float yaw, float pitch, String nm, WeaponInfo wi) {
        super(w, v, yaw, pitch, nm, wi);
        this.power = 0;
    }

    @Override
    public boolean shot(WeaponParam prm) {
        return false;
    }
}
