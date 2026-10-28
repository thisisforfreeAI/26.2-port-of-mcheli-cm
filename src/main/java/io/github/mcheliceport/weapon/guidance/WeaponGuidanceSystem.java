package io.github.mcheliceport.weapon.guidance;

import io.github.mcheliceport.entity.aircraft.AircraftEntity;
import io.github.mcheliceport.entity.flare.ChaffEntity;
import io.github.mcheliceport.entity.flare.FlareEntity;
import io.github.mcheliceport.entity.plane.PlaneEntity;
import io.github.mcheliceport.entity.seat.SeatEntity;
import io.github.mcheliceport.entity.weapon.AAMissileEntity;
import io.github.mcheliceport.entity.weapon.ASMissileEntity;
import io.github.mcheliceport.entity.weapon.ATMissileEntity;
import io.github.mcheliceport.entity.weapon.BaseBulletEntity;
import io.github.mcheliceport.entity.weapon.TvMissileEntity;
import io.github.mcheliceport.net.ModNetwork;
import io.github.mcheliceport.util.McheliMath;
import io.github.mcheliceport.util.McheliSounds;
import io.github.mcheliceport.weapon.DamageScaling;
import io.github.mcheliceport.weapon.WeaponWorld;
import io.github.mcheliceport.weather.FlightWeather;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

/**
 * A missile seeker: searches for and holds a lock on an entity in front of the user. Locking runs
 * on the client (the shooter's view); the client then sends the locked entity's ID with the shot.
 * Ported from upstream {@code MCH_WeaponGuidanceSystem}.
 *
 * <p>Differences from upstream:
 * <ul>
 *   <li>The pulse-Doppler notch compares against this seeker's {@code PDHDNMaxDegree}, where
 *       upstream read it from the shooter's current weapon, which is this seeker's own.</li>
 *   <li>Other mods' vehicles count as targets when their entity type ID contains
 *       {@code vehicle}, {@code plane}, {@code mecha} or {@code aagun} (upstream matched the
 *       Java class names {@code EntityVehicle} and so on; decision D19).</li>
 * </ul>
 */
public class WeaponGuidanceSystem extends EntityGuidanceSystem {

    private static final String[] OTHER_VEHICLE_IDS = {"vehicle", "plane", "mecha", "aagun"};

    public @Nullable Level world;
    public @Nullable Entity lastLockEntity;
    protected @Nullable Entity user;
    private @Nullable Entity targetEntity;

    public WeaponGuidanceSystem() {
        this(null);
    }

    public WeaponGuidanceSystem(@Nullable Level w) {
        this.world = w;
        this.targetEntity = null;
        this.lastLockEntity = null;
        this.lockCount = 0;
        this.continueLockCount = 0;
        this.lockCountMax = 1;
        this.prevLockCount = 0;
        this.canLockInWater = false;
        this.canLockOnGround = false;
        this.canLockInAir = false;
        this.ridableOnly = false;
        this.lockRange = 50.0;
        this.lockAngle = 10;
        this.checker = null;
    }

    public void setTarget(@Nullable Entity target) {
        this.targetEntity = target;
    }

    public @Nullable Entity getTarget() {
        return this.targetEntity;
    }

    /** Upstream {@code isEntityOnGround}: on the ground, or a non-water block within {@code height} below. */
    public static boolean isEntityOnGround(@Nullable Entity entity, int height) {
        if (entity != null && !entity.isRemoved()) {
            if (entity.onGround()) {
                return true;
            }
            int x = Mth.floor(entity.getX() + 0.5);
            int z = Mth.floor(entity.getZ() + 0.5);
            int baseY = Mth.floor(entity.getY() + 0.5);
            for (int i = 0; i < height; i++) {
                BlockPos pos = new BlockPos(x, baseY - i, z);
                if (!entity.level().getBlockState(pos).isAir() && !WeaponWorld.isBlockWater(entity.level(),
                        pos.getX(), pos.getY(), pos.getZ())) {
                    return true;
                }
            }
        }
        return false;
    }

    /** Upstream {@code getEntityStealth}: the vehicle's stealth, or the stealth of the vehicle it rides. */
    public static float getEntityStealth(@Nullable Entity entity) {
        if (entity instanceof AircraftEntity aircraft) {
            return aircraft.getStealth();
        }
        AircraftEntity ridden = WeaponWorld.aircraftOf(entity);
        return entity != null && entity.getVehicle() instanceof AircraftEntity && ridden != null
                ? ridden.getStealth() : 0.0F;
    }

    /** Upstream {@code inLockAngle}: the target's centre lies within {@code lockAng} of the view. */
    public static boolean inLockAngle(Entity entity, float rotationYaw, float rotationPitch, Entity target,
            float lockAng) {
        double dx = target.getX() - entity.getX();
        double dy = target.getY() + target.getBbHeight() / 2.0F - entity.getY();
        double dz = target.getZ() - entity.getZ();
        float entityYaw = (float) McheliMath.getRotate360(rotationYaw);
        float targetYaw = (float) McheliMath.getRotate360(Math.atan2(dz, dx) * 180.0 / Math.PI);
        float diffYaw = (float) McheliMath.getRotate360(targetYaw - entityYaw - 90.0F);
        double dxz = Math.sqrt(dx * dx + dz * dz);
        float targetPitch = -((float) (Math.atan2(dy, dxz) * 180.0 / Math.PI));
        float diffPitch = targetPitch - rotationPitch;
        return (diffYaw < lockAng || diffYaw > 360.0F - lockAng) && Math.abs(diffPitch) < lockAng;
    }

    public void setWorld(@Nullable Level w) {
        this.world = w;
    }

    @Override
    public int getLockCountMax() {
        float stealth = getEntityStealth(this.targetEntity);
        return (int) (this.lockCountMax + this.lockCountMax * stealth);
    }

    @Override
    public void setLockCountMax(int i) {
        this.lockCountMax = i > 0 ? i : 1;
    }

    @Override
    protected @Nullable Entity getLastLockEntity() {
        return null;
    }

    @Override
    public int getLockCount() {
        return this.lockCount;
    }

    @Override
    public boolean isLockingEntity(Entity entity) {
        return this.getLockCount() > 0 && this.targetEntity != null && !this.targetEntity.isRemoved()
                && WeaponWorld.isEqual(entity, this.targetEntity);
    }

    public @Nullable Entity getLockingEntity() {
        return this.getLockCount() > 0 && this.targetEntity != null && !this.targetEntity.isRemoved()
                ? this.targetEntity : null;
    }

    public @Nullable Entity getTargetEntity() {
        return this.targetEntity;
    }

    public boolean isLockComplete() {
        return this.getLockCount() == this.getLockCountMax() && this.lastLockEntity != null;
    }

    /** A lock that is not advanced in a tick falls back to nothing (client only). */
    @Override
    public void update() {
        if (this.world != null && this.world.isClientSide()) {
            if (this.lockCount != this.prevLockCount) {
                this.prevLockCount = this.lockCount;
            } else {
                this.lockCount = this.prevLockCount = 0;
            }
        }
    }

    @Override
    public boolean lock(Entity user) {
        return this.lock(user, true);
    }

    /** Advances the lock by a tick while the fire key is held; true once locked. Client only. */
    public boolean lock(Entity user, boolean isLockContinue) {
        if (this.world == null || !this.world.isClientSide()) {
            return false;
        }
        if (this.lockCount == 0) {
            return this.searchForTarget(user);
        }
        if (this.targetEntity == null || this.targetEntity.isRemoved()) {
            this.clearLock();
            return this.playLockSound(user, false);
        }
        if (!this.canContinueLocking(user)) {
            this.clearLock();
            return this.playLockSound(user, false);
        }
        this.processLockLogic(user, isLockContinue);
        boolean locked = this.lockCount >= this.getLockCountMax();
        return this.playLockSound(user, locked);
    }

    private boolean searchForTarget(Entity user) {
        List<Entity> canLock = this.world.getEntities(user, user.getBoundingBox().inflate(this.lockRange));
        Entity potentialTarget = null;
        double minDistSq = this.lockRange * this.lockRange * 2.0;
        FlightWeather.Conditions weather = FlightWeather.at(this.world, user.position());
        for (Entity current : canLock) {
            if (!this.canLockEntity(current)) {
                continue;
            }
            double range = this.lockRange * this.weatherFactor(weather, user, current);
            double dx = current.getX() - user.getX();
            double dy = current.getY() - user.getY();
            double dz = current.getZ() - user.getZ();
            double distSq = dx * dx + dy * dy + dz * dz;
            Entity locker = this.getLockEntity(user);
            float stealth = 1.0F - getEntityStealth(current);
            float angle = this.lockAngle * (stealth / 2.0F + 0.5F);
            if (distSq < range * range && distSq < minDistSq
                    && inLockAngle(locker, user.getYRot(), user.getXRot(), current, angle)) {
                Vec3 v1 = locker.position();
                Vec3 v2 = new Vec3(current.getX(), current.getY() + current.getBbHeight() / 2.0F, current.getZ());
                if (WeaponWorld.rayTrace(this.world, v1, v2, false, true) == null) {
                    potentialTarget = current;
                    minDistSq = distSq;
                }
            }
        }
        this.targetEntity = potentialTarget;
        if (potentialTarget != null) {
            this.lockCount++;
        }
        return this.playLockSound(user, false);
    }

    /** Whether a lock already started may go on this tick (upstream {@code canContinueLocking}). */
    public boolean canContinueLocking(Entity user) {
        if (this.targetEntity instanceof AircraftEntity ac && this.isRadarMissile && ac.getChaffUseTime() > 0) {
            return false;
        }
        if (!this.canLockInWater && this.targetEntity.isInWater()) {
            return false;
        }
        boolean onGround = isEntityOnGround(this.targetEntity, this.lockMinHeight);
        if (!this.canLockOnGround && onGround) {
            return false;
        }
        if (!this.canLockInAir && !onGround) {
            return false;
        }
        // Upstream's pulse-Doppler notch: a plane beaming a plane's seeker breaks the lock.
        if (riddenAircraft(user) instanceof PlaneEntity p1 && this.targetEntity instanceof PlaneEntity p2) {
            Vec3 v1 = p1.getDeltaMovement();
            Vec3 v2 = p2.getDeltaMovement();
            if (v1.length() > 0.001 && v2.length() > 0.001) {
                double angle = Math.acos(Mth.clamp(v1.dot(v2) / (v1.length() * v2.length()), -1.0, 1.0));
                if (angle > Math.PI / 2) {
                    angle = Math.PI - angle;
                }
                return Math.toDegrees(angle) <= this.pdHDNMaxDegree;
            }
        }
        return true;
    }

    /** The sensor this seeker locks with (M10e). */
    public FlightWeather.Sensor sensor() {
        return this.isRadarMissile ? FlightWeather.Sensor.RADAR
                : this.isHeatSeekerMissile ? FlightWeather.Sensor.IR : FlightWeather.Sensor.VISUAL;
    }

    /** M10e: how far {@code user} locks on {@code target} in the weather and light at the user. */
    public double lockRangeOn(Entity user, Entity target) {
        return this.lockRange * this.weatherFactor(FlightWeather.at(user.level(), user.position()), user, target);
    }

    /**
     * M10e: the share of the lock range left in the weather at the user; at night the eye needs
     * night or thermal vision, a searchlight or a lit target to keep its range.
     */
    private double weatherFactor(FlightWeather.Conditions weather, Entity user, Entity target) {
        if (weather.equals(FlightWeather.Conditions.CLEAR)) {
            return 1.0;
        }
        FlightWeather.Sensor sensor = this.sensor();
        boolean seesInDark = false;
        if (sensor == FlightWeather.Sensor.VISUAL && weather.night()) {
            AircraftEntity ac = riddenAircraft(user);
            seesInDark = FlightWeather.lit(target) || ac != null && FlightWeather.seesInDark(ac, user);
        }
        return FlightWeather.sensorFactor(weather, sensor, seesInDark);
    }

    private static @Nullable AircraftEntity riddenAircraft(Entity user) {
        return user instanceof Player player ? ModNetwork.riddenAircraft(player)
                : user.getVehicle() instanceof AircraftEntity ac ? ac
                : user.getVehicle() instanceof SeatEntity seat ? seat.getParent() : null;
    }

    private void processLockLogic(Entity user, boolean isLockContinue) {
        double dx = this.targetEntity.getX() - user.getX();
        double dy = this.targetEntity.getY() - user.getY();
        double dz = this.targetEntity.getZ() - user.getZ();
        double distSq = dx * dx + dy * dy + dz * dz;
        float stealth = 1.0F - getEntityStealth(this.targetEntity);
        double currentRange = this.lockRangeOn(user, this.targetEntity) * stealth;
        if (distSq >= currentRange * currentRange) {
            this.clearLock();
            return;
        }
        this.lockSoundCount = (this.lockSoundCount + 1) % 15;
        if (inLockAngle(this.getLockEntity(user), user.getYRot(), user.getXRot(), this.targetEntity,
                this.lockAngle)) {
            if (this.lockCount < this.getLockCountMax()) {
                this.lockCount++;
            }
        } else if (this.continueLockCount > 0) {
            if (--this.continueLockCount <= 0 && this.lockCount > 0) {
                this.lockCount--;
            }
        } else {
            this.continueLockCount = 0;
            this.lockCount--;
        }
        if (this.lockCount >= this.getLockCountMax()) {
            if (this.continueLockCount <= 0) {
                this.continueLockCount = Math.min(this.getLockCountMax() / 3, 20);
            }
            this.lastLockEntity = this.targetEntity;
            if (!isLockContinue) {
                this.clearLock();
            }
        }
    }

    private boolean playLockSound(Entity user, boolean locked) {
        McheliSounds.playLocal(this.world, user.getX(), user.getY(), user.getZ(),
                locked ? "ir_basic_tone" : "ir_lock_tone", SoundSource.PLAYERS, 1.0F, 1.0F);
        if (locked) {
            this.lastLockEntity = this.targetEntity;
        }
        return locked;
    }

    public void clearLock() {
        this.targetEntity = null;
        this.lockCount = 0;
        this.continueLockCount = 0;
        this.lockSoundCount = 0;
    }

    /** The entity the seeker looks from (upstream: a UAV station's controlled vehicle; UAVs come in M6). */
    public Entity getLockEntity(Entity entity) {
        return entity;
    }

    @Override
    public boolean canLockEntity(@Nullable Entity entity) {
        if (entity == null) {
            return false;
        }
        if (entity instanceof Player && this.ridableOnly && entity.getVehicle() == null) {
            return false;
        }
        if (entity instanceof Player && entity.getVehicle() != null) {
            return false;
        }
        Boolean specialized = null;
        if (entity instanceof FlareEntity) {
            specialized = this.isHeatSeekerMissile ? Boolean.TRUE : null;
        } else if (entity instanceof ChaffEntity) {
            specialized = this.isRadarMissile ? Boolean.TRUE : null;
        } else if (entity instanceof AircraftEntity ac) {
            if (this.isRadarMissile && ac.getChaffUseTime() > 0) {
                specialized = Boolean.FALSE;
            } else if (this.isRadarMissile && ac.getAcInfo() != null && ac.getAcInfo().ecmJammerType == 2
                    && ac.isECMJammerUsing()) {
                // A lock-breaking jammer denies radar-missile locks.
                specialized = Boolean.FALSE;
            } else if (ac.isFlareUsing()) {
                specialized = Boolean.FALSE;
            }
        } else if (entity instanceof BaseBulletEntity bullet) {
            if (this.canLockMissile && (bullet instanceof AAMissileEntity || bullet instanceof ATMissileEntity
                    || bullet instanceof ASMissileEntity || bullet instanceof TvMissileEntity)) {
                specialized = !WeaponWorld.isEqual(this.user, bullet.shootingEntity);
            }
        }
        if (specialized != null) {
            return specialized;
        }

        boolean isTargetable = entity instanceof LivingEntity || entity instanceof AircraftEntity
                || isOtherModVehicle(entity);
        if (!isTargetable) {
            return false;
        }
        if (!this.canLockInWater && entity.isInWater()) {
            return false;
        }
        if (this.checker != null && !this.checker.canLockEntity(entity)) {
            return false;
        }
        boolean onGround = isEntityOnGround(entity, this.lockMinHeight);
        return (this.canLockOnGround || !onGround) && (this.canLockInAir || onGround);
    }

    private static boolean isOtherModVehicle(Entity entity) {
        String id = DamageScaling.typeId(entity);
        if (id == null) {
            return false;
        }
        for (String s : OTHER_VEHICLE_IDS) {
            if (id.contains(s)) {
                return true;
            }
        }
        return false;
    }

    @Override
    public double getLockPosX() {
        return this.targetEntity.getX();
    }

    @Override
    public double getLockPosY() {
        return this.targetEntity.getY();
    }

    @Override
    public double getLockPosZ() {
        return this.targetEntity.getZ();
    }
}
