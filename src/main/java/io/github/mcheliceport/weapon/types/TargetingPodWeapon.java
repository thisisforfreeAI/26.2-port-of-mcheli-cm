package io.github.mcheliceport.weapon.types;

import io.github.mcheliceport.content.weapon.WeaponInfo;
import io.github.mcheliceport.weapon.WeaponBase;
import io.github.mcheliceport.weapon.WeaponParam;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;

/**
 * Targeting pod: upstream spotted entities or marked a point for the shooter's team
 * ({@code MCH_Multiplay.spotEntity}/{@code markPoint}). The team marker system comes with
 * multiplayer support in M6; until then a shot only plays the refusal sound ("ng"), as a shot
 * that spots nothing did upstream.
 */
public class TargetingPodWeapon extends WeaponBase {

    public TargetingPodWeapon(Level w, Vec3 v, float yaw, float pitch, String nm, WeaponInfo wi) {
        super(w, v, yaw, pitch, nm, wi);
        this.interval = -90;
        if (w.isClientSide()) {
            this.interval -= 10;
        }
    }

    @Override
    public boolean shot(WeaponParam prm) {
        if (!this.world.isClientSide()) {
            if ((this.getInfo().target & 64) != 0) {
                if (prm.user != null) {
                    this.playSound(prm.user, "ng");
                }
            } else if (prm.entity != null) {
                this.playSound(prm.entity, "ng");
            }
        }
        return true;
    }
}
