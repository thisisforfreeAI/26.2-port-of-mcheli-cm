package io.github.mcheliceport.groundcrew;

import io.github.mcheliceport.block.RepairPadBlock;
import io.github.mcheliceport.config.ServerConfig;
import io.github.mcheliceport.content.aircraft.AircraftInfo;
import io.github.mcheliceport.entity.aircraft.AircraftEntity;
import io.github.mcheliceport.entity.aircraft.AircraftSupport;
import java.util.Map;
import java.util.WeakHashMap;
import net.minecraft.core.BlockPos;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

/**
 * M10c ground crew, a port addition, on the server.
 *
 * <p>A vehicle standing still with its engine off over the middle of a 3x3 repair pad is serviced
 * once a second: it repairs {@code groundCrew.padRepairHp} HP (components worst first, through the
 * M9 repair path), and a fuel or ammo supply vehicle within {@code groundCrew.padSupplyRange} of the
 * pad's centre tops it up: a twentieth of its tank, and one weapon by a tenth of its load, the
 * emptiest first. A supply vehicle with a stock restocks a tenth of it a second on a pad.
 *
 * <p>Every service sets a flag the riders' HUD shows for a few seconds ({@link #mark}).
 */
public final class GroundCrew {

    public static final int REPAIR = 1;
    public static final int REARM = 2;
    public static final int REFUEL = 4;
    public static final int RESTOCK = 8;
    private static final int FLAGS = 4;
    /** How long a service flag stays up after the last service, in ticks. */
    public static final int SHOW_TICKS = 50;
    /** Faster than this, in blocks a tick, a vehicle is not standing still. */
    public static final double STILL_SPEED = 0.1;

    private static final Map<AircraftEntity, long[]> LAST = new WeakHashMap<>();

    private GroundCrew() {
    }

    /** Server: records that {@code ac} got a service now. */
    public static void mark(AircraftEntity ac, int flag) {
        if (ac.level().isClientSide()) {
            return;
        }
        long[] last = LAST.computeIfAbsent(ac, k -> new long[FLAGS]);
        for (int i = 0; i < FLAGS; i++) {
            if ((flag & (1 << i)) != 0) {
                last[i] = ac.level().getGameTime();
            }
        }
        ac.setServiceFlags(ac.getServiceFlags() | flag);
    }

    /** Whether the vehicle stands still with its engine off. */
    public static boolean stillAndOff(AircraftEntity ac) {
        return ac.getCurrentThrottle() <= 0.01 && ac.getDeltaMovement().length() < STILL_SPEED && !ac.isDestroyed();
    }

    /** The centre tile of the full pad the vehicle is over, or null. */
    public static @Nullable BlockPos pad(AircraftEntity ac) {
        return RepairPadBlock.padUnder(ac.level(), ac.position());
    }

    /** Server, every tick of a vehicle. */
    public static void tick(AircraftEntity ac) {
        expire(ac);
        AircraftInfo info = ac.getAcInfo();
        if (info == null || ac.getCountOnUpdate() % 20 != 0 || !ServerConfig.GROUND_CREW.get()) {
            return;
        }
        BlockPos pad = pad(ac);
        if (pad == null || ac.isDestroyed()) {
            return;
        }
        if (info.ammoSupplyStock > 0 && ac.getAmmoStock() < info.ammoSupplyStock) {
            ac.setAmmoStock(ac.getAmmoStock() + Math.max(1, info.ammoSupplyStock / 10));
            mark(ac, RESTOCK);
        }
        if (!stillAndOff(ac)) {
            return;
        }
        int hp = ServerConfig.PAD_REPAIR_HP.get();
        if (hp > 0 && ac.getDamageTaken() > 0 && ac.repair(hp)) {
            mark(ac, REPAIR);
        }
        double range = ServerConfig.PAD_SUPPLY_RANGE.get();
        if (range <= 0.0) {
            return;
        }
        Vec3 center = Vec3.atBottomCenterOf(pad);
        boolean fuelled = false;
        boolean armed = false;
        for (AircraftEntity supply : ac.level().getEntitiesOfClass(AircraftEntity.class, new AABB(center, center)
                .inflate(range))) {
            AircraftInfo s = supply.getAcInfo();
            if (supply == ac || s == null || supply.isDestroyed()
                    || supply.position().distanceToSqr(center) > range * range) {
                continue;
            }
            if (!fuelled && s.fuelSupplyRange > 0 && ac.getFuel() < ac.getMaxFuel()) {
                fuelled = AircraftSupport.transferFuel(supply, ac, Math.max(30, ac.getMaxFuel() / 20)) > 0;
            }
            if (!armed && s.ammoSupplyRange > 0) {
                int budget = s.ammoSupplyStock > 0 ? supply.getAmmoStock() : Integer.MAX_VALUE;
                int given = ac.weapons().rearmEmptiest(budget);
                if (given > 0 && s.ammoSupplyStock > 0) {
                    supply.setAmmoStock(supply.getAmmoStock() - given);
                }
                armed = given > 0;
            }
        }
        if (fuelled) {
            mark(ac, REFUEL);
        }
        if (armed) {
            mark(ac, REARM);
        }
    }

    private static void expire(AircraftEntity ac) {
        int flags = ac.getServiceFlags();
        if (flags == 0) {
            return;
        }
        long[] last = LAST.get(ac);
        long now = ac.level().getGameTime();
        int kept = 0;
        for (int i = 0; i < FLAGS; i++) {
            if ((flags & (1 << i)) != 0 && last != null && now - last[i] < SHOW_TICKS) {
                kept |= 1 << i;
            }
        }
        ac.setServiceFlags(kept);
    }
}
