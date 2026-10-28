package io.github.mcheliceport.entity.aircraft;

import io.github.mcheliceport.content.aircraft.AircraftInfo;
import io.github.mcheliceport.content.aircraft.CrewRole;
import io.github.mcheliceport.content.aircraft.SeatInfo;
import io.github.mcheliceport.content.aircraft.SeatRackInfo;
import io.github.mcheliceport.weapon.WeaponSet;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

/**
 * M10b crew roles, a port addition. A seat's role comes from its file ({@code Role:}); without one,
 * seat 0 is the pilot, a gunner seat or a seat any weapon belongs to is a gunner, and the rest
 * (including entity racks) are passengers. So stock vehicles keep exactly who may fire what.
 *
 * <p>A manned loader seat (a player or gunner mob in it) makes the vehicle's guns reload in
 * {@link #LOADER_FACTOR} of their time; a vehicle marked {@code LoaderRequired} reloads in
 * {@link #NO_LOADER_FACTOR} while its loader seat is empty. Vehicles without a loader seat are
 * unchanged.
 */
public final class CrewRoles {

    public static final float LOADER_FACTOR = 0.6F;
    public static final float NO_LOADER_FACTOR = 1.4F;
    /** How long a commander's mark lasts, in ticks. */
    public static final int MARK_TICKS = 200;
    /** How far a commander can mark, in blocks. */
    public static final double MARK_RANGE = 512.0;

    private CrewRoles() {
    }

    /** The role of seat {@code seatId} (0 = the pilot) of a vehicle type. */
    public static CrewRole role(AircraftInfo info, int seatId) {
        SeatInfo seat = seatId >= 0 && seatId < info.seatList.size() ? info.seatList.get(seatId) : null;
        if (seat != null && seat.role != null) {
            return seat.role;
        }
        if (seatId == 0) {
            return CrewRole.PILOT;
        }
        if (seat != null && seat.gunner && !(seat instanceof SeatRackInfo)) {
            return CrewRole.GUNNER;
        }
        // A weapon's seat may lie past the vehicle's seats; upstream lets the pilot use it then.
        for (AircraftInfo.WeaponSet set : info.weaponSetList) {
            for (AircraftInfo.Weapon w : set.weapons) {
                if (w.seatID == seatId) {
                    return CrewRole.GUNNER;
                }
            }
        }
        return CrewRole.PASSENGER;
    }

    /** The role of the seat {@code rider} sits in, or null when it rides no seat of {@code ac}. */
    public static @Nullable CrewRole roleOf(AircraftEntity ac, @Nullable Entity rider) {
        AircraftInfo info = ac.getAcInfo();
        int sid = ac.getSeatIdByEntity(rider);
        return info == null || sid < 0 ? null : role(info, sid);
    }

    /** Whether the vehicle type has a seat with this role. */
    public static boolean hasRole(AircraftInfo info, CrewRole role) {
        for (int i = 0; i < info.seatList.size(); i++) {
            if (role(info, i) == role) {
                return true;
            }
        }
        return false;
    }

    /** Whether some seat with this role has a living rider. */
    public static boolean manned(AircraftEntity ac, CrewRole role) {
        AircraftInfo info = ac.getAcInfo();
        if (info == null) {
            return false;
        }
        for (int i = 0; i < info.seatList.size(); i++) {
            Entity rider = ac.getEntityBySeatId(i);
            if (rider != null && rider.isAlive() && role(info, i) == role) {
                return true;
            }
        }
        return false;
    }

    /** The factor on every reload time of the vehicle's guns for its crew right now. */
    public static float reloadFactor(@Nullable AircraftEntity ac) {
        AircraftInfo info = ac == null ? null : ac.getAcInfo();
        if (info == null || !hasRole(info, CrewRole.LOADER)) {
            return 1.0F;
        }
        if (manned(ac, CrewRole.LOADER)) {
            return LOADER_FACTOR;
        }
        return info.loaderRequired ? NO_LOADER_FACTOR : 1.0F;
    }

    /** A reload of {@code ticks} for the vehicle's crew, at least 1 tick when it was any. */
    public static int reloadTicks(@Nullable AircraftEntity ac, int ticks) {
        if (ticks <= 0) {
            return ticks;
        }
        return Math.max(1, Math.round(ticks * reloadFactor(ac)));
    }

    /**
     * What a commander marks: the nearest living entity or vehicle their view ray meets within
     * {@link #MARK_RANGE} and before any block, other than their own vehicle and its riders; null
     * when nothing, which clears the mark.
     */
    public static @Nullable Entity markTarget(AircraftEntity ac, Entity commander) {
        Vec3 eye = commander.getEyePosition();
        Vec3 view = commander.getViewVector(1.0F);
        Vec3 end = eye.add(view.scale(MARK_RANGE));
        BlockHitResult block = ac.level().clip(new ClipContext(eye, end, ClipContext.Block.COLLIDER,
                ClipContext.Fluid.NONE, commander));
        if (block.getType() != HitResult.Type.MISS) {
            end = block.getLocation();
        }
        Entity best = null;
        double bestDist = Double.POSITIVE_INFINITY;
        AABB sweep = new AABB(eye, end).inflate(2.0);
        for (Entity e : ac.level().getEntities(commander, sweep,
                e -> e.isAlive() && (e instanceof LivingEntity || e instanceof AircraftEntity))) {
            if (e == ac || e.getRootVehicle() == ac) {
                continue;
            }
            var hit = e.getBoundingBox().inflate(0.5).clip(eye, end);
            if (hit.isPresent()) {
                double d = hit.get().distanceToSqr(eye);
                if (d < bestDist) {
                    bestDist = d;
                    best = e;
                }
            }
        }
        return best;
    }

    /**
     * The gun a loader's reload key serves: the one with the emptiest magazine that has room and
     * reserve ammunition and is not already reloading, the first on ties; -1 when none.
     */
    public static int loaderTarget(AircraftWeapons weapons) {
        int best = -1;
        float bestFill = Float.POSITIVE_INFINITY;
        for (int i = 0; i < weapons.getWeaponNum(); i++) {
            WeaponSet set = weapons.getWeapon(i);
            if (set == null || set.getCurrentWeapon() == null || set.getCurrentWeapon().getInfo() == null) {
                continue;
            }
            int mag = set.getMagSize();
            if (mag <= 0 || set.getAmmo() >= mag || set.getRestAllAmmoNum() <= 0 || set.isBusy()) {
                continue;
            }
            float fill = set.getAmmo() / (float) mag;
            if (fill < bestFill) {
                bestFill = fill;
                best = i;
            }
        }
        return best;
    }
}
