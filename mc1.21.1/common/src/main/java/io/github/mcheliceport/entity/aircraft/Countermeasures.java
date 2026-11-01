package io.github.mcheliceport.entity.aircraft;

import io.github.mcheliceport.content.aircraft.AircraftInfo;
import io.github.mcheliceport.content.weapon.WeaponInfo;
import io.github.mcheliceport.entity.weapon.AAMissileEntity;
import io.github.mcheliceport.entity.weapon.ASMissileEntity;
import io.github.mcheliceport.entity.weapon.ATMissileEntity;
import io.github.mcheliceport.entity.weapon.BaseBulletEntity;
import io.github.mcheliceport.entity.weapon.TvMissileEntity;
import net.minecraft.world.entity.Entity;
import org.jspecify.annotations.Nullable;

/**
 * A vehicle's countermeasures as an NPC uses them (upstream Reforged's gunner): the guided weapon
 * homing on it, and firing every decoy it carries at once. The gunner and, since M10d, the AI
 * pilots of wingmen share this.
 */
public final class Countermeasures {

    /** How far a homing weapon is seen, in blocks. */
    public static final double THREAT_RANGE = 220.0;

    private Countermeasures() {
    }

    /** The nearest guided weapon within {@link #THREAT_RANGE} homing on the vehicle or a rider, or null. */
    public static @Nullable BaseBulletEntity incoming(AircraftEntity ac) {
        BaseBulletEntity nearest = null;
        double best = Double.MAX_VALUE;
        for (BaseBulletEntity bullet : ac.level().getEntitiesOfClass(BaseBulletEntity.class,
                ac.getBoundingBox().inflate(THREAT_RANGE))) {
            WeaponInfo info = bullet.getInfo();
            Entity target = bullet.targetEntity;
            if (!bullet.isAlive() || info == null || target == null || (target != ac && !ac.isMountedEntity(target))) {
                continue;
            }
            if (!guided(bullet, info)) {
                continue;
            }
            double d = bullet.distanceToSqr(ac);
            if (d < best) {
                best = d;
                nearest = bullet;
            }
        }
        return nearest;
    }

    private static boolean guided(BaseBulletEntity bullet, WeaponInfo info) {
        return info.isHeatSeekerMissile || info.isRadarMissile || info.passiveRadar || info.activeRadar
                || info.laserGuidance || info.isGPSMissile || bullet instanceof AAMissileEntity
                || bullet instanceof ATMissileEntity || bullet instanceof ASMissileEntity
                || bullet instanceof TvMissileEntity;
    }

    /** Fires the flares, chaff, ECM jammer and APS the vehicle has ready; whether any went. */
    public static boolean fire(AircraftEntity ac) {
        boolean used = false;
        int flareType = flareType(ac);
        if (flareType > 0 && ac.canUseFlare() && ac.useFlare(flareType)) {
            used = true;
        }
        if (ac.canUseChaff() && ac.useChaff()) {
            used = true;
        }
        if (ac.haveECMJammer() && ac.useECMJammer()) {
            used = true;
        }
        if (ac.canUseAPS() && ac.useAPS()) {
            used = true;
        }
        return used;
    }

    /** The decoy pattern 10 when the vehicle has it, else its selected or first pattern; 0 for none. */
    public static int flareType(AircraftEntity ac) {
        AircraftInfo info = ac.getAcInfo();
        if (!ac.haveFlare() || info == null || info.flare == null || info.flare.types == null) {
            return 0;
        }
        int[] types = info.flare.types;
        for (int type : types) {
            if (type == 10) {
                return 10;
            }
        }
        int current = ac.getCurrentFlareType();
        if (current > 0) {
            return current;
        }
        return types.length > 0 ? types[0] : 0;
    }
}
