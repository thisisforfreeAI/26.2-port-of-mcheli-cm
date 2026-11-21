package io.github.mcheliceport.weapon.guidance;

import io.github.mcheliceport.content.aircraft.AircraftInfo;
import io.github.mcheliceport.content.weapon.WeaponInfo;
import io.github.mcheliceport.entity.aircraft.AircraftEntity;
import io.github.mcheliceport.entity.weapon.BaseBulletEntity;
import io.github.mcheliceport.net.ModNetwork;
import io.github.mcheliceport.radar.EntityInfoFeed;
import io.github.mcheliceport.weapon.WeaponBase;
import io.github.mcheliceport.weapon.WeaponSet;
import io.github.mcheliceport.weapon.WeaponWorld;
import io.github.mcheliceport.weather.FlightWeather;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

/**
 * The guidance modes upstream read from weapon files but never flew (KNOWN_ISSUES 88): data link
 * ({@code EnableDataLink}, {@code OnlyDataLink}), semi-active radar ({@code SemiActiveRadar} with
 * {@code PassiveRadarLockOutCount}) and anti-radiation homing ({@code AntiRadiationMissile} and its
 * {@code ARM} settings). GPS missiles fly to a point set at launch ({@link BaseBulletEntity#pointTarget}).
 * Server only; one instance per missile, made when it is fired.
 *
 * <p><b>Launcher track.</b> The launcher still tracks a target when the shooter is alive and still
 * aboard the launching vehicle, the vehicle's radar is not blanked by weather, the target is not
 * hidden by a lock-breaking (type 2) jammer, and either the shooter's seeker holds it as its BVR
 * target (within the radar feed's range) or it is within the weapon's lock range, in clear line of
 * sight and inside a cone around where the shooter looks: twice the weapon's lock angle for
 * semi-active radar, which needs the target kept near the nose, and the vehicle radar's track cone
 * (60 degrees each way without a {@code Radar} section) for a data link.
 *
 * <p><b>Data link.</b> While the launcher tracks the target, the missile keeps it through turns past
 * its own seeker angle, and it takes the launcher's new BVR target when the shooter switches. A
 * missile fired without a target waits for one. With {@code OnlyDataLink} the missile needs the
 * launcher's track all the way: lost for longer than {@code PassiveRadarLockOutCount} ticks, the
 * missile loses its target for good.
 *
 * <p><b>Semi-active radar.</b> As {@code OnlyDataLink}, with the narrower illumination cone, and
 * chaff the target drops breaks the illumination.
 *
 * <p><b>Anti-radiation.</b> The missile homes on radar emitters: a vehicle with a {@code Radar}
 * section that has it switched on or is flown by a non-player, or a crewed vehicle with an
 * {@code EntityRadar} display. Fired with no locked emitter, it takes the emitter nearest the
 * shooter's line of sight within its lock range and angle, and while it has none it searches its own
 * lock cone every {@code ScanInterval} ticks. When its emitter goes quiet it keeps tracking for
 * {@code EmitterLostGraceTick} ticks, then flies at the emitter's last position for
 * {@code MemoryTimeTick} more and resumes if the emitter comes back on; after that it searches
 * again. With {@code CruiseEnable}, a missile whose emitter was more than {@code CruiseStartDistance}
 * blocks away lofts towards a point above it (a quarter of the remaining distance, at most
 * {@code CruiseTerminalHeight} blocks above it and below the build limit) until it is within
 * {@code CruiseTerminalRadius} blocks horizontally, then dives.
 */
public final class MissileGuidance {

    /** Ticks between launcher track checks (they ray-trace). */
    private static final int TRACK_INTERVAL = 4;
    /** Half-angle of a data link's track cone for a vehicle without a {@code Radar} section. */
    private static final float DATALINK_CONE = 60.0F;

    private final boolean link;
    private final boolean linkRequired;
    private final boolean semiActive;
    private final boolean arm;

    private boolean tracking;
    private int trackLostTicks;
    private boolean linkBroken;

    private @Nullable Vec3 emitterLastSeen;
    private int emitterSilentTicks;
    private boolean cruise;
    private boolean cruiseDecided;
    private boolean cruising;

    private MissileGuidance(WeaponInfo info) {
        this.semiActive = info.semiActiveRadar;
        this.link = info.enableDataLink || info.semiActiveRadar;
        this.linkRequired = info.onlyDataLink || info.semiActiveRadar;
        this.arm = info.antiRadiationMissile;
    }

    /** The guidance for a missile fired from {@code info}, or null when it uses none of these modes. */
    public static @Nullable MissileGuidance create(@Nullable WeaponInfo info) {
        if (info == null || info.isGPSMissile) {
            return null;
        }
        return info.enableDataLink || info.semiActiveRadar || info.antiRadiationMissile
                ? new MissileGuidance(info) : null;
    }

    public boolean isAntiRadiation() {
        return this.arm;
    }

    /** Whether the missile should keep flying without a target, waiting for one. */
    public boolean keepsSearching() {
        return this.arm || this.link && !this.linkBroken;
    }

    /** Whether guidance may turn past the missile's own seeker angle this tick. */
    public boolean overridesSeekerLimit() {
        return this.cruising || this.link && this.tracking;
    }

    /** Whether the launcher tracked the target at the last check (data link and semi-active only). */
    public boolean launcherTracking() {
        return this.tracking;
    }

    public boolean linkBroken() {
        return this.linkBroken;
    }

    /** Runs before the missile steers each server tick. */
    public void tick(BaseBulletEntity missile) {
        WeaponInfo info = missile.getInfo();
        if (info == null) {
            return;
        }
        if (this.arm) {
            this.tickAntiRadiation(missile, info);
        } else if (this.link) {
            this.tickLink(missile, info);
        }
    }

    // ---- data link and semi-active radar ----

    private void tickLink(BaseBulletEntity missile, WeaponInfo info) {
        if (this.linkBroken) {
            return;
        }
        if (missile.getCountOnUpdate() % TRACK_INTERVAL == 0 || missile.getCountOnUpdate() <= 1) {
            Entity assigned = launcherSeekerTarget(missile);
            if (assigned != null && assigned != missile.targetEntity && assigned.isAlive()
                    && !missile.isFriendlyTarget(assigned)
                    && launcherTracks(missile, info, assigned, this.semiActive)) {
                missile.setTargetEntity(assigned);
            }
            Entity target = missile.targetEntity;
            this.tracking = target != null && target.isAlive() && launcherTracks(missile, info, target, this.semiActive);
        }
        if (!this.linkRequired) {
            return;
        }
        if (this.tracking) {
            this.trackLostTicks = 0;
        } else if (missile.targetEntity != null && ++this.trackLostTicks > info.passiveRadarLockOutCount) {
            missile.setTargetEntity(null);
            this.linkBroken = true;
        }
    }

    /** The target the shooter's current seeker holds on the server (its BVR lock), if any. */
    public static @Nullable Entity launcherSeekerTarget(BaseBulletEntity missile) {
        if (!(missile.shootingAircraft instanceof AircraftEntity ac) || missile.shootingEntity == null) {
            return null;
        }
        WeaponSet ws = ac.getCurrentWeapon(missile.shootingEntity);
        WeaponBase w = ws != null ? ws.getCurrentWeapon() : null;
        WeaponGuidanceSystem gs = w != null ? w.getGuidanceSystem() : null;
        Entity target = gs != null ? gs.getTarget() : null;
        return target != null && !target.isRemoved() ? target : null;
    }

    /** See the class comment, <b>Launcher track</b>. */
    public static boolean launcherTracks(BaseBulletEntity missile, WeaponInfo info, @Nullable Entity target,
            boolean illuminate) {
        Entity user = missile.shootingEntity;
        if (user == null || !user.isAlive() || user.isRemoved() || target == null || !target.isAlive()) {
            return false;
        }
        AircraftEntity ac = missile.shootingAircraft instanceof AircraftEntity a ? a : null;
        if (ac != null) {
            if (ac.isRemoved() || riddenAircraft(user) != ac || ac.getRadarBlankTicks() > 0) {
                return false;
            }
        } else if (missile.shootingAircraft != null && missile.shootingAircraft.isRemoved()) {
            return false;
        }
        if (target instanceof AircraftEntity t) {
            AircraftInfo ti = t.getAcInfo();
            if (ti != null && ti.ecmJammerType == 2 && t.isECMJammerUsing()) {
                return false;
            }
            if (illuminate && t.getChaffUseTime() > 0) {
                return false;
            }
        }
        Entity from = ac != null ? ac : user;
        double stealth = 1.0 - WeaponGuidanceSystem.getEntityStealth(target);
        double weather = FlightWeather.sensorFactor(missile.level(), from.position(), FlightWeather.Sensor.RADAR, true);
        double distSq = from.distanceToSqr(target);
        if (ac != null && launcherSeekerTarget(missile) == target) {
            double range = Math.max(info.maxLockOnRange, EntityInfoFeed.rangeAt(ac)) * weather;
            return distSq <= range * range;
        }
        double range = info.maxLockOnRange * weather * stealth;
        if (distSq > range * range) {
            return false;
        }
        float cone = illuminate ? Math.max(info.maxLockOnAngle * 2.0F, 5.0F) : datalinkCone(ac);
        if (!WeaponGuidanceSystem.inLockAngle(user, user.getYRot(), user.getXRot(), target, cone)) {
            return false;
        }
        Vec3 eye = user.getEyePosition();
        Vec3 aim = new Vec3(target.getX(), target.getY() + target.getBbHeight() / 2.0, target.getZ());
        return WeaponWorld.rayTrace(missile.level(), eye, aim, false, true) == null;
    }

    private static float datalinkCone(@Nullable AircraftEntity ac) {
        AircraftInfo info = ac != null ? ac.getAcInfo() : null;
        if (info != null && info.enableRadar) {
            return Mth.clamp(Math.max(info.radarTrackAzimuthDeg, info.radarScanAzimuthDeg) / 2.0F, 10.0F, 90.0F);
        }
        return DATALINK_CONE;
    }

    private static @Nullable AircraftEntity riddenAircraft(Entity user) {
        return user instanceof Player player ? ModNetwork.riddenAircraft(player) : WeaponWorld.aircraftOf(user);
    }

    // ---- anti-radiation ----

    private void tickAntiRadiation(BaseBulletEntity missile, WeaponInfo info) {
        Entity target = missile.targetEntity;
        if (target != null && (!target.isAlive() || target.isRemoved())) {
            missile.setTargetEntity(null);
            target = null;
        }
        if (target != null && isEmitter(target) && !jamsAntiRadiation(target)) {
            this.emitterLastSeen = aimPoint(target);
            this.emitterSilentTicks = 0;
            missile.pointTarget = null;
            this.updateCruise(missile, info, this.emitterLastSeen);
            return;
        }
        if (target == null && info.scanInterval > 0 && missile.getCountOnUpdate() % info.scanInterval == 0
                && missile.getCountOnUpdate() > info.rigidityTime) {
            Entity found = findEmitter(missile.level(), missile, missile.position(), missile.getDeltaMovement(),
                    info.maxLockOnRange, info.maxLockOnAngle);
            if (found != null) {
                missile.setTargetEntity(found);
                missile.pointTarget = null;
                this.emitterLastSeen = aimPoint(found);
                this.emitterSilentTicks = 0;
                this.updateCruise(missile, info, this.emitterLastSeen);
                return;
            }
        }
        if (this.emitterLastSeen == null) {
            this.cruising = false;
            return;
        }
        this.emitterSilentTicks++;
        if (target != null && this.emitterSilentTicks <= info.armEmitterLostGraceTick) {
            this.updateCruise(missile, info, aimPoint(target));
            return;
        }
        if (this.emitterSilentTicks <= info.armEmitterLostGraceTick + info.armMemoryTimeTick) {
            missile.pointTarget = this.emitterLastSeen;
            this.updateCruise(missile, info, this.emitterLastSeen);
            return;
        }
        // Memory spent: forget the emitter and search again.
        missile.pointTarget = null;
        this.emitterLastSeen = null;
        this.emitterSilentTicks = 0;
        this.cruising = false;
        if (target != null) {
            missile.setTargetEntity(null);
        }
    }

    private void updateCruise(BaseBulletEntity missile, WeaponInfo info, Vec3 emitter) {
        if (!info.armCruiseEnable) {
            this.cruising = false;
            return;
        }
        double h = horizontalDistance(missile.position(), emitter);
        if (!this.cruiseDecided) {
            this.cruiseDecided = true;
            this.cruise = h > info.armCruiseStartDistance;
        }
        if (this.cruising && h <= info.armCruiseTerminalRadius) {
            this.cruise = false;
        }
        this.cruising = this.cruise && h > info.armCruiseTerminalRadius;
    }

    /** The point to steer at for a target at {@code x, y, z}: above it while an ARM cruises. */
    public Vec3 adjustAim(BaseBulletEntity missile, double x, double y, double z) {
        if (!this.cruising) {
            return new Vec3(x, y, z);
        }
        WeaponInfo info = missile.getInfo();
        double h = horizontalDistance(missile.position(), new Vec3(x, y, z));
        double loft = Math.min(h * 0.25, info != null ? info.armCruiseTerminalHeight : 1024.0);
        double cruiseY = Math.min(y + loft, missile.level().getMaxY() - 4.0);
        return new Vec3(x, Math.max(y, cruiseY), z);
    }

    public boolean isCruising() {
        return this.cruising;
    }

    private static double horizontalDistance(Vec3 a, Vec3 b) {
        double dx = a.x - b.x;
        double dz = a.z - b.z;
        return Math.sqrt(dx * dx + dz * dz);
    }

    private static Vec3 aimPoint(Entity e) {
        return new Vec3(e.getX(), e.getY() + e.getBbHeight() / 2.0, e.getZ());
    }

    /**
     * Whether {@code e} radiates for an anti-radiation seeker: a vehicle with a {@code Radar} section
     * switched on (or flown by a non-player), or a crewed vehicle with an {@code EntityRadar} display.
     */
    public static boolean isEmitter(@Nullable Entity e) {
        if (!(e instanceof AircraftEntity ac) || !ac.isAlive() || ac.isRemoved()) {
            return false;
        }
        AircraftInfo info = ac.getAcInfo();
        if (info == null) {
            return false;
        }
        if (info.enableRadar) {
            LivingEntity pilot = ac.getControllingPassenger();
            if (ac.isRadarEnabledRuntime() || pilot != null && !(pilot instanceof Player)) {
                return true;
            }
        }
        return info.isEnableEntityRadar && hasCrew(ac);
    }

    private static boolean hasCrew(AircraftEntity ac) {
        if (ac.getControllingPassenger() != null) {
            return true;
        }
        for (Entity e : ac.getPassengers()) {
            if (e instanceof LivingEntity || !e.getPassengers().isEmpty()) {
                return true;
            }
        }
        return false;
    }

    /** A lock-breaking (type 2) jammer at work hides its vehicle from anti-radiation seekers too. */
    private static boolean jamsAntiRadiation(Entity e) {
        return e instanceof AircraftEntity ac && ac.getAcInfo() != null && ac.getAcInfo().ecmJammerType == 2
                && ac.isECMJammerUsing();
    }

    /**
     * The radar emitter nearest the axis {@code dir} from {@code from} within {@code range} blocks and
     * {@code angle} degrees, leaving out the shooter's own side and jamming vehicles; null for none.
     */
    public static @Nullable Entity findEmitter(Level level, @Nullable BaseBulletEntity missile, Vec3 from, Vec3 dir,
            double range, double angle) {
        return findEmitter(level, missile, null, null, from, dir, range, angle);
    }

    /** As {@link #findEmitter(Level, BaseBulletEntity, Vec3, Vec3, double, double)}, for a launch. */
    public static @Nullable Entity findEmitter(Level level, @Nullable BaseBulletEntity missile,
            @Nullable Entity shooter, @Nullable Entity shooterVehicle, Vec3 from, Vec3 dir, double range,
            double angle) {
        if (dir.lengthSqr() < 1.0e-8 || range <= 0) {
            return null;
        }
        double r = range * FlightWeather.sensorFactor(level, from, FlightWeather.Sensor.RADAR, true);
        Vec3 axis = dir.normalize();
        double maxAngle = Math.toRadians(angle);
        Entity best = null;
        double bestAngle = Double.MAX_VALUE;
        for (AircraftEntity ac : level.getEntitiesOfClass(AircraftEntity.class,
                new net.minecraft.world.phys.AABB(from, from).inflate(r))) {
            if (!isEmitter(ac) || jamsAntiRadiation(ac)) {
                continue;
            }
            if (missile != null ? missile.isFriendlyTarget(ac) : isOwnSide(ac, shooter, shooterVehicle)) {
                continue;
            }
            Vec3 to = aimPoint(ac).subtract(from);
            double d = to.length();
            if (d > r || d < 1.0e-6) {
                continue;
            }
            double a = Math.acos(Mth.clamp(axis.dot(to.scale(1.0 / d)), -1.0, 1.0));
            if (a <= maxAngle && a < bestAngle) {
                best = ac;
                bestAngle = a;
            }
        }
        return best;
    }

    private static boolean isOwnSide(AircraftEntity ac, @Nullable Entity shooter, @Nullable Entity vehicle) {
        if (ac == vehicle || shooter != null && (ac == shooter || ac.isMountedEntity(shooter))) {
            return true;
        }
        if (shooter instanceof LivingEntity living && ac.getControllingPassenger() instanceof LivingEntity pilot) {
            return pilot.isAlliedTo(living);
        }
        return false;
    }
}
