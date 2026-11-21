package io.github.mcheliceport.weapon.types;

import io.github.mcheliceport.content.weapon.WeaponInfo;
import io.github.mcheliceport.weapon.GpsPosition;
import io.github.mcheliceport.weapon.WeaponParam;
import io.github.mcheliceport.weapon.WeaponWorld;
import io.github.mcheliceport.weapon.guidance.MissileGuidance;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

/** Launch rules shared by GPS and anti-radiation weapons (see {@link MissileGuidance}). */
final class GuidedShots {

    private GuidedShots() {
    }

    /** Whether a weapon of this kind fires without a lock (radar, GPS and anti-radiation missiles). */
    static boolean firesWithoutLock(WeaponInfo info) {
        return info.passiveRadar || info.activeRadar || info.isGPSMissile || info.antiRadiationMissile;
    }

    /**
     * Client: a GPS weapon fires only with an active GPS point; without one it tells the shooter on
     * the action bar.
     */
    static boolean clientHasGpsPoint(@Nullable Entity user) {
        if (GpsPosition.client() != null) {
            return true;
        }
        if (user instanceof Player player) {
            player.sendOverlayMessage(Component.translatable("message.mcheli_ce_port.gps_no_point"));
        }
        return false;
    }

    /** Server: the shooter's GPS point, or null when they have none (the shot is refused). */
    static @Nullable Vec3 serverGpsPoint(WeaponParam prm) {
        return GpsPosition.target(prm.user);
    }

    /**
     * Server: an anti-radiation missile's emitter at launch: the locked target if it radiates, else
     * the emitter nearest the shooter's line of sight within the weapon's lock range and angle.
     */
    static @Nullable Entity antiRadiationTarget(Level world, WeaponParam prm, WeaponInfo info,
            @Nullable Entity locked) {
        if (MissileGuidance.isEmitter(locked) && locked != prm.entity) {
            return locked;
        }
        Entity user = prm.user != null ? prm.user : prm.entity;
        if (user == null) {
            return null;
        }
        Vec3 dir = WeaponWorld.fromPitchYaw(user.getXRot(), user.getYRot());
        return MissileGuidance.findEmitter(world, null, prm.user, prm.entity, user.getEyePosition(), dir,
                info.maxLockOnRange, info.maxLockOnAngle);
    }
}
