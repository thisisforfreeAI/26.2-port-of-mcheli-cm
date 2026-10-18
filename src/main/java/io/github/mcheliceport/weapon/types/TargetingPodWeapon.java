package io.github.mcheliceport.weapon.types;

import io.github.mcheliceport.content.weapon.WeaponInfo;
import io.github.mcheliceport.entity.aircraft.AircraftEntity;
import io.github.mcheliceport.multiplay.Spotting;
import io.github.mcheliceport.weapon.WeaponBase;
import io.github.mcheliceport.weapon.WeaponParam;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;

/**
 * Targeting pod (upstream {@code MCH_WeaponTargetingPod}): with target bit 64 it marks the block
 * the user looks at for the user's team; otherwise it spots the entities its target filter takes
 * within {@code length} blocks and {@code angle} degrees, for {@code markTime} seconds. Plays the
 * weapon's sound on success and "ng" otherwise.
 */
public class TargetingPodWeapon extends WeaponBase {

    /** Upstream's target bit for marking a point instead of spotting. */
    public static final int TARGET_MARK_POINT = 64;

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
            WeaponInfo info = this.getInfo();
            Vec3 pos = new Vec3(prm.posX, prm.posY, prm.posZ);
            if ((info.target & TARGET_MARK_POINT) != 0) {
                if (prm.user instanceof Player player) {
                    this.playSound(player, Spotting.markPoint(player, pos) ? info.fireSound : "ng");
                }
            } else if (prm.user instanceof LivingEntity user && prm.entity != null) {
                AircraftEntity ac = prm.entity instanceof AircraftEntity a ? a : null;
                boolean spotted = Spotting.spotEntity(user, ac, pos, info.target, info.length, info.markTime,
                        info.angle);
                this.playSound(prm.entity, spotted ? info.fireSound : "ng");
            }
        }
        return true;
    }
}
