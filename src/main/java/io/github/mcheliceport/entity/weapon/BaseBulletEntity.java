package io.github.mcheliceport.entity.weapon;

import io.github.mcheliceport.config.CommonConfig;
import io.github.mcheliceport.content.ContentRegistries;
import io.github.mcheliceport.compat.hbm.NtmPayloads;
import io.github.mcheliceport.content.weapon.WeaponInfo;
import io.github.mcheliceport.effect.McheliEffects;
import io.github.mcheliceport.effect.ParticleParam;
import io.github.mcheliceport.entity.aircraft.AircraftEntity;
import io.github.mcheliceport.entity.aircraft.AircraftHitBox;
import io.github.mcheliceport.entity.aircraft.GroundProbe;
import io.github.mcheliceport.entity.flare.ChaffEntity;
import io.github.mcheliceport.entity.flare.FlareEntity;
import io.github.mcheliceport.entity.seat.SeatEntity;
import io.github.mcheliceport.net.ClientSoundPayload;
import io.github.mcheliceport.net.NotifyHitPayload;
import io.github.mcheliceport.util.McheliSounds;
import io.github.mcheliceport.weapon.BlockList;
import io.github.mcheliceport.weapon.DamageScaling;
import io.github.mcheliceport.weapon.WeaponBase;
import io.github.mcheliceport.weapon.WeaponTargets;
import io.github.mcheliceport.weapon.WeaponWorld;
import io.github.mcheliceport.weapon.explosion.McheliExplosion;
import io.github.mcheliceport.weapon.guidance.WeaponGuidanceSystem;
import io.github.mcheliceport.wingman.handler.ProjectileChunkLoader;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.SectionPos;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.BlockTags;
import net.minecraft.util.Mth;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.damagesource.DamageTypes;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.VegetationBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import org.joml.Vector3f;
import org.jspecify.annotations.Nullable;

/**
 * Every McHeli projectile: flight (gravity, drag in air and water, speed changes), block and entity
 * hits, piercing, fuses (time, delay, altitude, airburst, proximity), sub-munitions and homing.
 * Ported from upstream {@code MCH_EntityBaseBullet}.
 *
 * <p>The server is authoritative; clients run the same tick to move the projectile smoothly
 * between position updates and to spawn the trails, splashes and hit particles. As upstream, the
 * shooter and its vehicle are known only on the server.
 *
 * <p>Differences from upstream (docs/M5_PLAN.md section 10):
 * <ul>
 *   <li>Saved projectiles are discarded when their chunk loads again, as upstream did on reading
 *       them; only mines ({@link BombEntity}) are kept.</li>
 *   <li>Upstream's {@code checkValid} dropped projectiles below y -10, the old world's floor; the
 *       limit now follows the world's floor, so projectiles live in the deep caves and superflat
 *       worlds that reach down to y -64.</li>
 *   <li>Upstream's {@code checkValid} dereferenced a missing shooter when only the vehicle was
 *       known; the vehicle is used then.</li>
 *   <li>Flowers, crops, saplings and grass ({@link VegetationBlock}), leaves and iron bars let
 *       projectiles through without exploding, as upstream's plant and leaf materials; tall grass
 *       (upstream's vine material) is now among them.</li>
 *   <li>The class-name list {@code IgnoreBulletHit}, chunk-loading projectiles, HBM payloads,
 *       iron-curtain particles and passive radar are not ported (D19, M6).</li>
 * </ul>
 */
public abstract class BaseBulletEntity extends Entity implements WeaponTargets.Projectile {

    private static final EntityDataAccessor<Integer> TARGET_ID =
            SynchedEntityData.defineId(BaseBulletEntity.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<String> INFO_NAME =
            SynchedEntityData.defineId(BaseBulletEntity.class, EntityDataSerializers.STRING);
    private static final EntityDataAccessor<Byte> BOMBLET_FLAG =
            SynchedEntityData.defineId(BaseBulletEntity.class, EntityDataSerializers.BYTE);

    public @Nullable Entity shootingEntity;
    public @Nullable Entity shootingAircraft;
    public int explosionPower;
    public int explosionPowerInWater;
    public double acceleration;
    public double accelerationFactor;
    public @Nullable Entity targetEntity;
    public int piercing;
    public int delayFuse;
    public int sprinkleTime;
    public byte isBomblet;
    public double prevPosX2;
    public double prevPosY2;
    public double prevPosZ2;
    public double prevMotionX;
    public double prevMotionY;
    public double prevMotionZ;
    public boolean antiFlareUse;
    public int antiFlareTick;
    public int numLockedChaff = 0;
    public int airburstDist = 0;
    protected boolean doingTopAttack = false;
    private boolean speedAddedFromAircraft = false;
    private int countOnUpdate = 0;
    /** M9 lag compensation: ticks a player's shot rewinds vehicles by, fixed when it is fired. */
    private int rewindTicks;
    private int power;
    private @Nullable WeaponInfo weaponInfo;
    private double airburstTravelled = 0.0;
    private boolean airburstTriggered = false;
    private boolean aheadTriggered = false;
    private int spawnedBulletNum = 0;
    /** Where the projectile was at the start of the previous tick, for {@link #prevPosX2}. */
    private double lastXo;
    private double lastYo;
    private double lastZo;

    protected BaseBulletEntity(EntityType<? extends BaseBulletEntity> type, Level level) {
        super(type, level);
        this.targetEntity = null;
        this.power = 1;
        this.acceleration = 1.0;
        this.accelerationFactor = 1.0;
        this.piercing = 0;
        this.explosionPower = 0;
        this.explosionPowerInWater = 0;
        this.delayFuse = 0;
        this.sprinkleTime = 0;
        this.isBomblet = -1;
        this.weaponInfo = null;
    }

    /** Upstream's spawning constructor: speed capped at 3.9 blocks per tick along the given direction. */
    protected BaseBulletEntity(EntityType<? extends BaseBulletEntity> type, Level level, double px, double py,
            double pz, double mx, double my, double mz, float yaw, float pitch, double acceleration) {
        this(type, level);
        this.snapTo(px, py, pz, yaw, pitch);
        this.lastXo = this.prevPosX2 = px;
        this.lastYo = this.prevPosY2 = py;
        this.lastZo = this.prevPosZ2 = pz;
        if (acceleration > 3.9) {
            acceleration = 3.9;
        }
        double d = Math.sqrt(mx * mx + my * my + mz * mz);
        this.setDeltaMovement(mx * acceleration / d, my * acceleration / d, mz * acceleration / d);
        Vec3 m = this.getDeltaMovement();
        this.prevMotionX = m.x;
        this.prevMotionY = m.y;
        this.prevMotionZ = m.z;
        this.acceleration = acceleration;
    }

    @Override
    protected void defineSynchedData(SynchedEntityData.Builder builder) {
        builder.define(TARGET_ID, 0);
        builder.define(INFO_NAME, "");
        builder.define(BOMBLET_FLAG, (byte) 0);
    }

    /** Moves the projectile half a tick along its velocity (the guns' muzzle offset). */
    public void advanceHalfStep() {
        Vec3 m = this.getDeltaMovement();
        this.setPos(this.getX() + m.x * 0.5, this.getY() + m.y * 0.5, this.getZ() + m.z * 0.5);
    }

    // ---- weapon definition ----

    /** The weapon definition's name (synced). */
    public String getInfoName() {
        return this.entityData.get(INFO_NAME);
    }

    public void setName(@Nullable String s) {
        if (s != null && !s.isEmpty()) {
            this.weaponInfo = ContentRegistries.weapon().get(s.toLowerCase(Locale.ROOT));
            if (this.weaponInfo != null) {
                if (!this.level().isClientSide()) {
                    this.entityData.set(INFO_NAME, s);
                }
                this.onSetWeaponInfo();
            }
        }
    }

    public @Nullable WeaponInfo getInfo() {
        return this.weaponInfo;
    }

    @Override
    public @Nullable WeaponInfo getWeaponInfo() {
        return this.weaponInfo;
    }

    @Override
    public @Nullable Entity getShooter() {
        return this.shootingEntity;
    }

    public void onSetWeaponInfo() {
        WeaponInfo info = this.getInfo();
        if (!this.level().isClientSide()) {
            this.isBomblet = 0;
        }
        if (info.bomblet > 0) {
            this.sprinkleTime = info.bombletSTime;
        }
        this.piercing = info.piercing;
        if (this instanceof BulletEntity) {
            if (info.acceleration > 4.0F) {
                this.accelerationFactor = info.acceleration / 4.0F;
            }
        } else if (this instanceof RocketEntity && this.isBomblet == 0 && info.acceleration > 4.0F) {
            this.accelerationFactor = info.acceleration / 4.0F;
        }
    }

    public void setBomblet() {
        this.isBomblet = 1;
        this.sprinkleTime = 0;
        this.entityData.set(BOMBLET_FLAG, (byte) 1);
    }

    public byte getBomblet() {
        return this.entityData.get(BOMBLET_FLAG);
    }

    /** The model of the bullet models folder drawn when the definition names none. */
    public abstract String getDefaultBulletModelName();

    /** The model this projectile is drawn with: the bomblet or bullet model of its definition. */
    public @Nullable String getBulletModelName() {
        WeaponInfo info = this.getInfo();
        if (info == null || this.isBomblet < 0) {
            return null;
        }
        String name = this.isBomblet == 1 ? info.bombletModelName : info.bulletModelName;
        return name == null || name.isEmpty() ? this.getDefaultBulletModelName() : name;
    }

    public int getPower() {
        return this.power;
    }

    public void setPower(int power) {
        this.power = power;
    }

    public int getCountOnUpdate() {
        return this.countOnUpdate;
    }

    public void clearCountOnUpdate() {
        this.countOnUpdate = 0;
    }

    // ---- target ----

    public void setTargetEntity(@Nullable Entity entity) {
        this.targetEntity = entity;
        if (!this.level().isClientSide()) {
            if (this.targetEntity instanceof ServerPlayer) {
                this.alertTarget();
            }
            this.entityData.set(TARGET_ID, entity != null ? entity.getId() : 0);
        }
    }

    /** The missile alert for a targeted player riding something other than a McHeli vehicle. */
    private void alertTarget() {
        Entity riding = this.targetEntity.getVehicle();
        if (riding != null && !(riding instanceof AircraftEntity) && !(riding instanceof SeatEntity)) {
            McheliSounds.playAt(this.targetEntity, "alert", 2.0F, 1.0F);
        }
    }

    public int getTargetEntityID() {
        return this.targetEntity != null ? this.targetEntity.getId() : this.entityData.get(TARGET_ID);
    }

    public void sprinkleBomblet() {
    }

    /** A trail of {@code num} particles along the last two ticks of flight. Client only. */
    public void spawnParticle(String name, int num, float size) {
        if (!this.level().isClientSide() || name.isEmpty() || num < 1 || num > 50) {
            return;
        }
        double x = (this.getX() - this.xo) / num;
        double y = (this.getY() - this.yo) / num;
        double z = (this.getZ() - this.zo) / num;
        double x2 = (this.xo - this.prevPosX2) / num;
        double y2 = (this.yo - this.prevPosY2) / num;
        double z2 = (this.zo - this.prevPosZ2) / num;
        for (int i = 0; i < num; i++) {
            double px = (this.xo + x * i + (this.prevPosX2 + x2 * i)) / 2.0;
            double py = (this.yo + y * i + (this.prevPosY2 + y2 * i)) / 2.0;
            double pz = (this.zo + z * i + (this.prevPosZ2 + z2 * i)) / 2.0;
            if (name.equals("explode")) {
                ParticleParam prm = new ParticleParam("smoke", px, py, pz);
                prm.size = size + this.random.nextFloat();
                McheliEffects.spawnParticle(this.level(), prm);
            } else {
                McheliEffects.spawnVanilla(this.level(), name, px, py, pz, 0.0, 0.0, 0.0);
            }
        }
    }

    @Override
    public boolean shouldRenderAtSqrDistance(double distance) {
        double d1 = this.getBoundingBox().getSize() * 4.0 * 64.0;
        return distance < d1 * d1;
    }

    public void setParameterFromWeapon(WeaponBase w, @Nullable Entity entity, @Nullable Entity user) {
        this.explosionPower = w.explosionPower;
        this.explosionPowerInWater = w.explosionPowerInWater;
        this.setPower(w.power);
        this.piercing = w.piercing;
        this.shootingAircraft = entity;
        this.shootingEntity = user;
        this.airburstDist = w.airburstDist;
        this.rewindTicks = io.github.mcheliceport.combat.LagCompensation.rewindTicksFor(user);
    }

    public void setParameterFromWeapon(BaseBulletEntity b, @Nullable Entity entity, @Nullable Entity user) {
        this.explosionPower = b.explosionPower;
        this.explosionPowerInWater = b.explosionPowerInWater;
        this.setPower(b.getPower());
        this.piercing = b.piercing;
        this.shootingAircraft = entity;
        this.shootingEntity = user;
        this.airburstDist = b.airburstDist;
        this.rewindTicks = b.rewindTicks;
    }

    public void setAirburstDist(int airburstDist) {
        this.airburstDist = airburstDist;
    }

    /** Upstream {@code setMotion}: points the velocity along a direction at the projectile's speed. */
    public void setMotion(double targetX, double targetY, double targetZ) {
        double d6 = Math.sqrt(targetX * targetX + targetY * targetY + targetZ * targetZ);
        this.setDeltaMovement(targetX * this.acceleration / d6, targetY * this.acceleration / d6,
                targetZ * this.acceleration / d6);
    }

    /** Whether something within 15 blocks of the target is dispensing flares (checked every third tick). */
    public boolean usingFlareOfTarget(Entity entity) {
        if (this.getCountOnUpdate() % 3 == 0) {
            for (Entity e : this.level().getEntities(this, entity.getBoundingBox().inflate(15.0))) {
                if (e instanceof AircraftEntity ac && ac.isFlareDecoying()) {
                    return true;
                }
            }
        }
        return false;
    }

    public void guidanceToTarget(double targetPosX, double targetPosY, double targetPosZ) {
        this.guidanceToTarget(targetPosX, targetPosY, targetPosZ, 1.0F);
    }

    /**
     * Upstream {@code guidanceToTarget}: turns the velocity towards the (lead-predicted) target by
     * the definition's turning factor, dropping the target when it leaves the seeker angle, flies
     * against the missile beyond the pulse-Doppler notch angle, or (air-to-air) is on the ground.
     */
    public void guidanceToTarget(double targetPosX, double targetPosY, double targetPosZ, float accelerationFactor) {
        WeaponInfo info = this.getInfo();
        if (info.tickEndHoming > 0 && this.tickCount > info.tickEndHoming) {
            return;
        }
        if (this.targetEntity == null || !this.targetEntity.isAlive()) {
            return;
        }
        Vec3 motion = this.getDeltaMovement();
        Vec3 targetMotion = this.targetEntity.getDeltaMovement();
        if (info.predictTargetPos) {
            double currentDistance = Math.sqrt(this.distanceToSqr(targetPosX, targetPosY, targetPosZ));
            double missileSpeed = motion.length();
            if (missileSpeed < 0.0001) {
                missileSpeed = this.acceleration;
            }
            double timeToTarget = currentDistance / missileSpeed;
            targetPosX += targetMotion.x * timeToTarget;
            targetPosY += targetMotion.y * timeToTarget;
            targetPosZ += targetMotion.z * timeToTarget;
        }
        double tx = targetPosX - this.getX();
        double ty = targetPosY - this.getY();
        double tz = targetPosZ - this.getZ();
        double d = Math.sqrt(tx * tx + ty * ty + tz * tz);
        double mx = tx * this.acceleration / d;
        double my = ty * this.acceleration / d;
        double mz = tz * this.acceleration / d;
        Vector3f missileDir = new Vector3f((float) motion.x, (float) motion.y, (float) motion.z);
        Vector3f targetDir = new Vector3f((float) tx, (float) ty, (float) tz);
        float angle = missileDir.angle(targetDir);
        if (angle > Math.toRadians(info.maxDegreeOfMissile) && !this.doingTopAttack) {
            this.setTargetEntity(null);
            return;
        }
        Vector3f targetVel = new Vector3f((float) targetMotion.x, (float) targetMotion.y, (float) targetMotion.z);
        if (missileDir.angle(targetVel) > Math.toRadians(info.pdHDNMaxDegree)) {
            this.setTargetEntity(null);
            return;
        }
        if (this instanceof AAMissileEntity
                && WeaponGuidanceSystem.isEntityOnGround(this.targetEntity, info.lockMinHeight)) {
            this.setTargetEntity(null);
            return;
        }
        double factor = info.turningFactor;
        double curSpeed = motion.length();
        double nmx;
        double nmy;
        double nmz;
        if (curSpeed < 1.0e-6) {
            nmx = motion.x + (mx - motion.x) * factor;
            nmy = motion.y + (my - motion.y) * factor;
            nmz = motion.z + (mz - motion.z) * factor;
        } else {
            double newSpeed = curSpeed + (this.acceleration - curSpeed) * factor;
            double cdx = motion.x / curSpeed;
            double cdy = motion.y / curSpeed;
            double cdz = motion.z / curSpeed;
            double tdx = tx / d;
            double tdy = ty / d;
            double tdz = tz / d;
            double ndx = cdx + (tdx - cdx) * factor;
            double ndy = cdy + (tdy - cdy) * factor;
            double ndz = cdz + (tdz - cdz) * factor;
            double nlen = Math.sqrt(ndx * ndx + ndy * ndy + ndz * ndz);
            if (nlen < 1.0e-6) {
                ndx = tdx;
                ndy = tdy;
                ndz = tdz;
                nlen = 1.0;
            }
            nmx = ndx / nlen * newSpeed;
            nmy = ndy / nlen * newSpeed;
            nmz = ndz / nlen * newSpeed;
        }
        this.setDeltaMovement(nmx, nmy, nmz);
        this.setYRot((float) (Math.atan2(nmz, nmx) * 180.0 / Math.PI) - 90.0F);
    }

    /**
     * Upstream {@code checkValid}: within 5,820 blocks of the shooter horizontally, and no more than 10
     * blocks below the world's floor (upstream's y -10, when the floor was y 0).
     */
    public boolean checkValid() {
        if (this.shootingEntity == null && this.shootingAircraft == null) {
            return false;
        }
        Entity shooter = this.shootingEntity != null ? this.shootingEntity : this.shootingAircraft;
        double x = this.getX() - shooter.getX();
        double z = this.getZ() - shooter.getZ();
        return x * x + z * z < 3.38724E7 && this.getY() > this.level().getMinY() - 10.0;
    }

    public float getBulletGravity() {
        return this.getInfo() != null ? this.getInfo().gravity : 0.0F;
    }

    public float getGravityInWater() {
        return this.getInfo() != null ? this.getInfo().gravityInWater : 0.0F;
    }

    @Override
    public void tick() {
        Level level = this.level();
        if (!level.isClientSide()) {
            WeaponInfo info = this.getInfo();
            if (info != null && this.shootingAircraft instanceof AircraftEntity ac && !this.speedAddedFromAircraft
                    && info.speedDependsAircraft) {
                this.acceleration += ac.getDeltaMovement().length();
                Vec3 m = this.getDeltaMovement();
                double d = m.length();
                if (d > 0) {
                    this.setDeltaMovement(m.scale(this.acceleration / d));
                }
                this.speedAddedFromAircraft = true;
            }
            if (this.antiFlareUse) {
                if (this.antiFlareTick > 0) {
                    this.antiFlareTick--;
                } else {
                    this.setTargetEntity(null);
                    this.antiFlareUse = false;
                }
            }
            if (level instanceof ServerLevel serverLevel && ProjectileChunkLoader.isEnabledFor(info)) {
                ProjectileChunkLoader.requestPath(serverLevel, this);
            }
        }
        if (level.isClientSide() && this.countOnUpdate == 0) {
            int tgtEttId = this.getTargetEntityID();
            if (tgtEttId > 0) {
                this.setTargetEntity(level.getEntity(tgtEttId));
            }
        }
        if (!level.isClientSide() && this.getCountOnUpdate() % 20 == 19 && this.targetEntity instanceof ServerPlayer) {
            this.alertTarget();
        }
        Vec3 motion = this.getDeltaMovement();
        this.prevMotionX = motion.x;
        this.prevMotionY = motion.y;
        this.prevMotionZ = motion.z;
        this.prevPosX2 = this.lastXo;
        this.prevPosY2 = this.lastYo;
        this.prevPosZ2 = this.lastZo;
        this.lastXo = this.xo;
        this.lastYo = this.yo;
        this.lastZo = this.zo;
        if (++this.countOnUpdate > 10_000_000) {
            this.clearCountOnUpdate();
        }
        super.tick();
        motion = this.getDeltaMovement();
        boolean motionChanged = this.prevMotionX != motion.x || this.prevMotionY != motion.y
                || this.prevMotionZ != motion.z;
        if (motionChanged && motion.lengthSqr() > 0.1) {
            this.setYRot((float) Math.toDegrees(Math.atan2(motion.z, motion.x)) - 90.0F);
            double r = Math.sqrt(motion.x * motion.x + motion.z * motion.z);
            this.setXRot(-(float) Math.toDegrees(Math.atan2(motion.y, r)));
        }
        if (this.getInfo() == null) {
            if (this.countOnUpdate >= 2) {
                this.discard();
                return;
            }
            this.setName(this.getInfoName());
            if (this.getInfo() == null) {
                return;
            }
        }
        WeaponInfo info = this.getInfo();
        if (info.bound <= 0.0F && this.onGround()) {
            this.setDeltaMovement(this.getDeltaMovement().multiply(0.9, 1.0, 0.9));
        }
        if (level.isClientSide() && this.isBomblet < 0) {
            this.isBomblet = this.getBomblet();
        }
        if (!level.isClientSide()) {
            BlockPos blockpos = BlockPos.containing(this.getX(), this.getY(), this.getZ());
            if (this.getY() <= level.getMaxY() && !level.hasChunk(SectionPos.blockToSectionCoord(blockpos.getX()),
                    SectionPos.blockToSectionCoord(blockpos.getZ()))) {
                if (info.delayFuse <= 0) {
                    this.discard();
                    return;
                }
                if (this.delayFuse == 0) {
                    this.delayFuse = info.delayFuse;
                }
            }
            if (this.delayFuse > 0 && --this.delayFuse == 0) {
                this.onUpdateTimeout();
                this.discard();
                return;
            }
            if (!this.checkValid() || info.timeFuse > 0 && this.getCountOnUpdate() > info.timeFuse) {
                if (info.timeFuse > 0) {
                    this.onUpdateTimeout();
                }
                this.discard();
                return;
            }
            if (info.explosionAltitude > 0
                    && GroundProbe.find(level, this.getX(), this.getY(), this.getZ(), 3, -info.explosionAltitude,
                            true)) {
                this.onImpact(new BlockHitResult(this.position(), Direction.DOWN, blockpos, false), 1.0F);
            }
        }
        if (!this.isInWater()) {
            if (this.tickCount > info.speedFactorStartTick && this.tickCount < info.speedFactorEndTick) {
                Vec3 m = this.getDeltaMovement();
                double currentSpeed = m.length();
                if (currentSpeed > 0) {
                    this.setDeltaMovement(m.add(m.scale(info.speedFactor / currentSpeed)));
                    this.acceleration += info.speedFactor;
                }
            }
            Vec3 m = this.getDeltaMovement().add(0.0, this.getBulletGravity(), 0.0);
            double dragInAir = info.dragInAir;
            if (dragInAir != 0.0) {
                double speed = m.length();
                if (speed > 0.0) {
                    m = new Vec3(m.x - m.x / speed * dragInAir, m.y, m.z - m.z / speed * dragInAir);
                }
            }
            this.setDeltaMovement(m);
        } else {
            this.setDeltaMovement(this.getDeltaMovement().add(0.0, this.getGravityInWater(), 0.0));
        }
        if (!this.isRemoved()) {
            this.onUpdateCollided();
            this.onUpdateAirburst();
            this.onUpdateProximityFuse();
        }
        Vec3 m = this.getDeltaMovement();
        double nx = this.getX() + m.x * this.accelerationFactor;
        double ny = this.getY() + m.y * this.accelerationFactor;
        double nz = this.getZ() + m.z * this.accelerationFactor;
        this.setPos(nx, ny, nz);
        if (level.isClientSide()) {
            this.updateSplash();
            if (this.isInWater()) {
                McheliEffects.spawnVanilla(level, "bubble", nx - m.x * 0.25, ny - m.y * 0.25, nz - m.z * 0.25, m.x,
                        m.y, m.z);
            }
        }
        this.onUpdateSpreader();
    }

    @Override
    public boolean isPushedByFluid() {
        return false;
    }

    private void onUpdateAirburst() {
        int abDist = this.airburstDist;
        if (this.airburstTriggered || abDist <= 5 || abDist >= 3000) {
            return;
        }
        double targetDist = abDist + 3.0;
        Vec3 m = this.getDeltaMovement();
        double dx = m.x * this.accelerationFactor;
        double dy = m.y * this.accelerationFactor;
        double dz = m.z * this.accelerationFactor;
        double segLen = Math.sqrt(dx * dx + dy * dy + dz * dz);
        double newTravel = this.airburstTravelled + segLen;
        if (segLen > 0.0 && newTravel >= targetDist) {
            double t = (targetDist - this.airburstTravelled) / segLen;
            double ex = this.getX() + dx * t;
            double ey = this.getY() + dy * t;
            double ez = this.getZ() + dz * t;
            if (!this.level().isClientSide()) {
                WeaponInfo info = this.getInfo();
                if (info.ahead) {
                    // AHEAD rounds arm and then stream sub-projectiles forward (onUpdateSpreader).
                    if (info.proximityFuseTick < 0 || this.tickCount > info.proximityFuseTick) {
                        this.aheadTriggered = true;
                    }
                    this.airburstTriggered = true;
                    this.airburstTravelled = 0.0;
                    return;
                }
                if (info.explosion > 0) {
                    this.newExplosion(ex, ey, ez, info.explosionAirburst, info.explosionBlock, false);
                } else if (this.explosionPower < 0) {
                    this.playExplosionSound();
                }
                ClientSoundPayload.send(this.level(), ex, ey, ez, info.hitSoundRange, info.hitSound, true);
                this.discard();
            }
            this.airburstTriggered = true;
            this.airburstTravelled = 0.0;
        } else {
            this.airburstTravelled = newTravel;
        }
    }

    /**
     * Proximity fuse: detonates near vehicles (and the locked missile it chases), predicting the
     * closest approach along this tick's flight.
     */
    private void onUpdateProximityFuse() {
        WeaponInfo info = this.getInfo();
        if (this.isRemoved() || info == null) {
            return;
        }
        if (info.proximityFuseTick < 0 || this.tickCount <= info.proximityFuseTick || info.proximityFuseDist <= 0.0F) {
            return;
        }
        if (info.ahead && (this.airburstDist <= 5 || this.airburstDist >= 3000)) {
            return;
        }
        double rewind = this.hitRewindTicks();
        float searchRange = info.proximityFuseDist * 5.0F;
        List<Entity> nearby = this.level().getEntities(this, this.getBoundingBox().inflate(searchRange + rewind * 5.0));
        if (nearby.isEmpty()) {
            return;
        }
        Vec3 m = this.getDeltaMovement();
        double dx = m.x * this.accelerationFactor;
        double dy = m.y * this.accelerationFactor;
        double dz = m.z * this.accelerationFactor;
        double segLen = Math.sqrt(dx * dx + dy * dy + dz * dz);
        if (segLen <= 0.0) {
            return;
        }
        double dirX = dx / segLen;
        double dirY = dy / segLen;
        double dirZ = dz / segLen;
        for (Entity entity : nearby) {
            if (!this.canBeCollidedEntity(entity)) {
                continue;
            }
            boolean isAircraftTarget = entity instanceof AircraftEntity;
            boolean isLockedTarget = entity instanceof BaseBulletEntity && this.targetEntity != null
                    && this.targetEntity.isAlive() && WeaponWorld.isEqual(entity, this.targetEntity);
            if (!isAircraftTarget && !isLockedTarget) {
                continue;
            }
            if (isAircraftTarget && WeaponGuidanceSystem.isEntityOnGround(entity, info.proximityFuseHeight)) {
                continue;
            }
            // M9: a vehicle is fused against where the shooter saw it, while the missile still rewinds.
            Vec3 seen = entity.position().add(io.github.mcheliceport.combat.LagCompensation.shift(entity, rewind));
            double currentDistance = Math.sqrt(this.distanceToSqr(seen.x, seen.y, seen.z));
            double timeToPos = currentDistance / segLen;
            Vec3 em = entity.getDeltaMovement();
            double predX = seen.x + em.x * timeToPos;
            double predY = seen.y + em.y * timeToPos;
            double predZ = seen.z + em.z * timeToPos;
            double toPredX = predX - this.getX();
            double toPredY = predY - this.getY();
            double toPredZ = predZ - this.getZ();
            double dot = toPredX * dirX + toPredY * dirY + toPredZ * dirZ;
            if (dot <= 0.0) {
                continue;
            }
            double projX = this.getX() + dirX * dot;
            double projY = this.getY() + dirY * dot;
            double projZ = this.getZ() + dirZ * dot;
            double perpX = predX - projX;
            double perpY = predY - projY;
            double perpZ = predZ - projZ;
            double predictedDistance = Math.sqrt(perpX * perpX + perpY * perpY + perpZ * perpZ);
            if (predictedDistance > info.proximityFuseDist) {
                continue;
            }
            double distToProj = Math.sqrt((projX - this.getX()) * (projX - this.getX())
                    + (projY - this.getY()) * (projY - this.getY()) + (projZ - this.getZ()) * (projZ - this.getZ()));
            if (distToProj > segLen + 0.1) {
                continue;
            }
            double t = Math.max(0.0, Math.min(1.0, distToProj / segLen));
            double ex = this.getX() + dx * t;
            double ey = this.getY() + dy * t;
            double ez = this.getZ() + dz * t;
            if (this.level() instanceof ServerLevel server) {
                if (info.explosion > 0) {
                    this.newExplosion(ex, ey, ez, info.explosionAirburst, info.explosionBlock, false);
                } else if (this.explosionPower < 0) {
                    this.playExplosionSound();
                }
                ClientSoundPayload.send(server, ex, ey, ez, info.hitSoundRange, info.hitSound, true);
                if (entity.isAlive()) {
                    DamageScaling.applyHurtResistantTime(entity);
                    DamageSource ds = server.damageSources().thrown(this, this.shootingEntity);
                    float damage = DamageScaling.applyDamageVsEntity(entity, info.proximityFuseDamage);
                    damage *= info.getDamageFactor(WeaponTargets.targetName(entity));
                    var blood = io.github.mcheliceport.effect.VehicleBlood.projectile(entity, this, ds,
                            io.github.mcheliceport.effect.VehicleBloodRules.DIRECT, this.getDeltaMovement());
                    boolean applied = entity.hurtServer(server, ds, damage);
                    io.github.mcheliceport.effect.VehicleBlood.finish(blood, applied);
                    if (isLockedTarget) {
                        entity.discard();
                    }
                    if (damage > 0.0F) {
                        this.notifyHitBullet();
                    }
                }
                this.discard();
            }
            return;
        }
    }

    /**
     * Sub-munitions streamed forward: a generic in-air spawner ({@code SpawnBulletInAir}) or AHEAD
     * slugs once armed. Each is a bullet of the {@code BombletModelName} weapon with its own warhead.
     */
    private void onUpdateSpreader() {
        WeaponInfo info = this.getInfo();
        if (this.level().isClientSide() || info == null) {
            return;
        }
        boolean canSpawnInAir = info.spawnBulletInAir;
        boolean canSpawnAhead = info.ahead && this.aheadTriggered;
        if ((canSpawnInAir || canSpawnAhead) && this.spawnedBulletNum < info.spawnBulletMaxNum && !this.isRemoved()
                && this.tickCount > 5 && this.tickCount % info.spawnBulletIntervalTick == 0) {
            this.spawnedBulletNum++;
            WeaponInfo child = ContentRegistries.weapon().get(info.bombletModelName.toLowerCase(Locale.ROOT));
            if (child != null) {
                for (int i = 0; i < info.spawnBulletPerNum; i++) {
                    Vec3 m = new Vec3(1.0E-6, 1.0E-6, 1.0E-6);
                    double speed = 0.001;
                    if (info.spawnBulletInheritSpeed) {
                        m = this.getDeltaMovement();
                        speed = this.acceleration;
                    }
                    BulletEntity e = new BulletEntity(this.level(), this.getX(), this.getY(), this.getZ(), m.x, m.y,
                            m.z, this.getYRot(), this.getXRot(), speed);
                    e.setName(info.bombletModelName);
                    e.setParameterFromWeapon(this, this.shootingAircraft, this.shootingEntity);
                    e.setPower(child.power);
                    e.explosionPower = child.explosion;
                    e.explosionPowerInWater = child.explosionInWater;
                    e.airburstDist = 0;
                    float spread = info.bombletDiff;
                    e.setDeltaMovement(e.getDeltaMovement().add((this.random.nextFloat() - 0.5) * spread,
                            (this.random.nextFloat() - 0.5) * spread, (this.random.nextFloat() - 0.5) * spread));
                    this.level().addFreshEntity(e);
                }
            }
        }
        if (info.destructAfterSpawnBullet && this.spawnedBulletNum >= info.spawnBulletMaxNum) {
            this.discard();
        }
    }

    /** Splash particles where the projectile enters water. Client only. */
    public void updateSplash() {
        WeaponInfo info = this.getInfo();
        if (info == null || info.power <= 0) {
            return;
        }
        Level level = this.level();
        if (WeaponWorld.isBlockWater(level, this.xo, this.yo, this.zo)
                || !WeaponWorld.isBlockWater(level, this.getX(), this.getY(), this.getZ())) {
            return;
        }
        double x = this.getX() - this.xo;
        double y = this.getY() - this.yo;
        double z = this.getZ() - this.zo;
        double d = Math.sqrt(x * x + y * y + z * z);
        if (d <= 0.15) {
            return;
        }
        x /= d;
        y /= d;
        z /= d;
        double px = this.xo;
        double py = this.yo;
        double pz = this.zo;
        for (int i = 0; i <= d; i++) {
            px += x;
            py += y;
            pz += z;
            if (WeaponWorld.isBlockWater(level, px, py, pz)) {
                float pwr = info.power < 20 ? info.power : 20.0F;
                int n = this.random.nextInt(1 + (int) pwr / 3) + (int) pwr / 2 + 1;
                pwr *= 0.03F;
                for (int j = 0; j < n; j++) {
                    ParticleParam prm = new ParticleParam("splash", px, py + 0.5,
                            pz, pwr * (this.random.nextDouble() - 0.5) * 0.3,
                            pwr * (this.random.nextDouble() * 0.5 + 0.5) * 1.8,
                            pwr * (this.random.nextDouble() - 0.5) * 0.3, pwr * 5.0F);
                    McheliEffects.spawnParticle(level, prm);
                }
                break;
            }
        }
    }

    /** The time or delay fuse ran out: explode (in water with the in-water power). */
    public void onUpdateTimeout() {
        if (this.isInWater()) {
            if (this.explosionPowerInWater > 0) {
                this.newExplosion(this.getX(), this.getY(), this.getZ(), this.explosionPowerInWater,
                        this.explosionPowerInWater, true);
            }
        } else if (this.explosionPower > 0) {
            this.newExplosion(this.getX(), this.getY(), this.getZ(), this.explosionPower, this.getInfo().explosionBlock,
                    false);
        } else if (this.explosionPower < 0) {
            this.playExplosionSound();
        }
    }

    /** Counts down the cluster time and releases the bomblets. Server only. */
    public void onUpdateBomblet() {
        if (!this.level().isClientSide() && this.sprinkleTime > 0 && !this.isRemoved()) {
            this.sprinkleTime--;
            if (this.sprinkleTime == 0) {
                for (int i = 0; i < this.getInfo().bomblet; i++) {
                    this.sprinkleBomblet();
                }
                this.discard();
            }
        }
    }

    /** Bounces off the hit face by the definition's {@code Bound} factor. */
    public void boundBullet(Direction sideHit) {
        Vec3 m = this.getDeltaMovement();
        double mx = m.x;
        double my = m.y;
        double mz = m.z;
        float bound = this.getInfo().bound;
        switch (sideHit) {
            case DOWN -> {
                if (my > 0.0) {
                    my = -my * bound;
                }
            }
            case UP -> {
                if (my < 0.0) {
                    my = -my * bound;
                }
            }
            case NORTH -> {
                if (mz > 0.0) {
                    mz = -mz * bound;
                } else {
                    this.setPos(this.getX(), this.getY(), this.getZ() + mz);
                }
            }
            case SOUTH -> {
                if (mz < 0.0) {
                    mz = -mz * bound;
                } else {
                    this.setPos(this.getX(), this.getY(), this.getZ() + mz);
                }
            }
            case WEST -> {
                if (mx > 0.0) {
                    mx = -mx * bound;
                } else {
                    this.setPos(this.getX() + mx, this.getY(), this.getZ());
                }
            }
            case EAST -> {
                if (mx < 0.0) {
                    mx = -mx * bound;
                } else {
                    this.setPos(this.getX() + mx, this.getY(), this.getZ());
                }
            }
            default -> {
            }
        }
        if (bound <= 0.0F) {
            mx *= 0.25;
            my *= 0.25;
            mz *= 0.25;
        }
        this.setDeltaMovement(mx, my, mz);
    }

    /**
     * Upstream {@code onUpdateCollided}: the block along this tick's flight (breaking up to five
     * bullet-breakable blocks on the way), then the nearest collidable entity before it.
     */
    protected void onUpdateCollided() {
        float damageFactor = 1.0F;
        Vec3 motion = this.getDeltaMovement();
        double mx = motion.x * this.accelerationFactor;
        double my = motion.y * this.accelerationFactor;
        double mz = motion.z * this.accelerationFactor;
        BlockHitResult blockHit = null;
        Level level = this.level();
        for (int i = 0; i < 5; i++) {
            Vec3 from = this.position();
            Vec3 to = from.add(mx, my, mz);
            blockHit = WeaponWorld.clip(level, from, to);
            boolean continueClip = false;
            if (this.shootingEntity != null && blockHit != null) {
                BlockPos pos = blockHit.getBlockPos();
                if (BlockList.of(CommonConfig.get(CommonConfig.BULLET_BREAKABLE_BLOCKS))
                        .contains(level.getBlockState(pos))) {
                    level.destroyBlock(pos, true);
                    continueClip = true;
                }
            }
            if (!continueClip) {
                break;
            }
        }
        Vec3 from = this.position();
        Vec3 to = from.add(mx, my, mz);
        if (this.getInfo().delayFuse > 0) {
            if (blockHit != null) {
                this.boundBullet(blockHit.getDirection());
                if (this.delayFuse == 0) {
                    this.delayFuse = this.getInfo().delayFuse;
                }
            }
            return;
        }
        if (blockHit != null) {
            to = blockHit.getLocation();
        }
        HitResult hit = blockHit;
        Entity entity = this.findEntityOnPath(from, to, mx, my, mz);
        if (entity != null) {
            hit = new EntityHitResult(entity);
        }
        if (hit != null) {
            this.onImpact(hit, damageFactor);
        }
    }

    /** Sets the rewind a shot was fired with (tests; real shots take it from the shooter's latency). */
    public void setRewindTicks(int ticks) {
        this.rewindTicks = Math.max(ticks, 0);
    }

    /**
     * M9 lag compensation: how many ticks back this projectile tests vehicles. Gun rounds keep the
     * shooter's rewind for their whole flight; steered weapons (missiles, rockets, bombs) give it
     * up as they fly, so a missile cannot hit where a dodging target used to be.
     */
    public double hitRewindTicks() {
        if (this.rewindTicks <= 0 || this.level().isClientSide()) {
            return 0.0;
        }
        return this instanceof BulletEntity ? this.rewindTicks : Math.max(0, this.rewindTicks - this.tickCount);
    }

    /**
     * The nearest collidable entity whose box (grown by 0.3) the segment crosses. Vehicles are
     * tested where the shooter saw them ({@link #hitRewindTicks}).
     */
    protected @Nullable Entity findEntityOnPath(Vec3 from, Vec3 to, double mx, double my, double mz) {
        Entity found = null;
        double d0 = 0.0;
        double rewind = this.hitRewindTicks();
        // A rewound vehicle can be up to one rewind of flight from where it is now (about 5 blocks a tick at most).
        AABB search = this.getBoundingBox().expandTowards(mx, my, mz).inflate(21.0 + rewind * 5.0);
        for (Entity candidate : this.level().getEntities(this, search)) {
            if (!this.canBeCollidedEntity(candidate)) {
                continue;
            }
            Vec3 shift = io.github.mcheliceport.combat.LagCompensation.shift(candidate, rewind);
            Optional<Vec3> intercept = candidate.getBoundingBox().move(shift).inflate(0.3).clip(from, to);
            if (intercept.isPresent()) {
                double d1 = from.distanceTo(intercept.get());
                if (d1 < d0 || d0 == 0.0) {
                    found = candidate;
                    d0 = d1;
                }
            }
            if (candidate instanceof AircraftEntity aircraft) {
                for (AircraftHitBox box : aircraft.getHitBoxes()) {
                    if (!box.isPickable()) { continue; }
                    Optional<Vec3> partHit = box.getBoundingBox().move(shift).inflate(0.3).clip(from, to);
                    if (partHit.isPresent()) {
                        double distance = from.distanceTo(partHit.get());
                        if (found == null || distance < d0) { found = box; d0 = distance; }
                    }
                }
            }
        }
        return found;
    }

    /**
     * Upstream {@code canBeCollidedEntity}: what a projectile can hit. Not seats, the vehicle's
     * own hit boxes, the shooter, its vehicle or anyone aboard it; other projectiles only on the
     * server and when they are not from the same shooter or vehicle.
     */
    public boolean canBeCollidedEntity(@Nullable Entity entity) {
        if (entity == null || !entity.isPickable()) {
            return false;
        }
        if (entity instanceof SeatEntity || entity instanceof AircraftHitBox) {
            return false;
        }
        if (entity instanceof BaseBulletEntity blt) {
            if (this.level().isClientSide()) {
                return false;
            }
            if (WeaponWorld.isEqual(blt.shootingAircraft, this.shootingAircraft)) {
                return false;
            }
            if (WeaponWorld.isEqual(blt.shootingEntity, this.shootingEntity)) {
                return false;
            }
        }
        if (WeaponWorld.isEqual(entity, this.shootingEntity)) {
            return false;
        }
        if (this.shootingAircraft instanceof AircraftEntity ac) {
            return !WeaponWorld.isEqual(entity, ac) && !ac.isMountedEntity(entity);
        }
        return true;
    }

    /** The hit marker for the shooting player: on their vehicle's HUD and the plain one. */
    public void notifyHitBullet() {
        if (this.shootingEntity instanceof ServerPlayer player) {
            if (this.shootingAircraft instanceof AircraftEntity ac) {
                NotifyHitPayload.send(ac, player);
            }
            NotifyHitPayload.send(null, player);
        }
    }

    /** Block crack and cloud particles at a block hit. Client only. */
    protected void spawnBlockPar(Vec3 hitVec, BlockPos blockPos) {
        WeaponInfo info = this.getInfo();
        Level level = this.level();
        int crackCount = info.flakParticlesCrack + this.random.nextInt(3);
        float diff = info.flakParticlesDiff;
        for (int i = 0; i < crackCount; i++) {
            McheliEffects.spawnTileCrack(level, blockPos.getX(), blockPos.getY(), blockPos.getZ(),
                    hitVec.x + (this.random.nextFloat() - 0.5) * this.getBbWidth(), hitVec.y + 0.1,
                    hitVec.z + (this.random.nextFloat() - 0.5) * this.getBbWidth(),
                    diff / 2.0F * this.random.nextGaussian(), diff * Math.abs(this.random.nextGaussian()),
                    diff / 2.0F * this.random.nextGaussian());
        }
        for (int i = 0; i < info.numParticlesFlak; i++) {
            McheliEffects.spawnVanilla(level, "cloud", hitVec.x + this.random.nextGaussian(),
                    hitVec.y + this.random.nextGaussian(), hitVec.z + this.random.nextGaussian(),
                    this.random.nextGaussian() / 200.0, this.random.nextGaussian() / 200.0,
                    this.random.nextGaussian() / 200.0);
        }
    }

    private static @Nullable AircraftEntity ironCurtainOf(@Nullable Entity hit) {
        AircraftEntity ac = hit instanceof AircraftEntity a ? a
                : hit instanceof AircraftHitBox box ? box.getParent() : null;
        return ac != null && ac.isIronCurtainActive() ? ac : null;
    }

    /**
     * Upstream {@code spawnIronCurtainParticle}: a hit on a vehicle under the iron curtain throws up
     * red sparks and a red cloud. Upstream tinted block cracks and vanilla clouds; here McHeli's
     * smoke is tinted for both.
     */
    protected void spawnIronCurtainParticle(Vec3 hitVec) {
        WeaponInfo info = this.getInfo();
        int crackCount = info.flakParticlesCrack + this.random.nextInt(3);
        int cloudCount = 50 + (int) info.flakParticlesDiff;
        for (int i = 0; i < crackCount + cloudCount; i++) {
            boolean crack = i < crackCount;
            ParticleParam p = crack
                    ? new ParticleParam("smoke", hitVec.x + (this.random.nextFloat() - 0.5) * this.getBbWidth(),
                            hitVec.y + 0.1, hitVec.z + (this.random.nextFloat() - 0.5) * this.getBbWidth(),
                            info.flakParticlesDiff * this.random.nextGaussian() * 0.5,
                            info.flakParticlesDiff * Math.abs(this.random.nextGaussian()),
                            info.flakParticlesDiff * this.random.nextGaussian() * 0.5, 0.8F)
                    : new ParticleParam("smoke", hitVec.x + (this.random.nextFloat() - 0.5) * this.getBbWidth(),
                            hitVec.y + this.random.nextGaussian() * this.getBbHeight(),
                            hitVec.z + (this.random.nextFloat() - 0.5) * this.getBbWidth(),
                            this.random.nextGaussian() / 100.0, this.random.nextGaussian() / 100.0,
                            this.random.nextGaussian() / 100.0, 1.0F);
            p.r = 0.5F;
            p.g = 0.1F;
            p.b = 0.1F;
            p.age = crack ? 10 : 20;
            McheliEffects.spawnParticle(this.level(), p);
        }
    }

    /** The block a block hit hit, or null for an entity hit. */
    protected static @Nullable BlockPos blockOf(HitResult m) {
        return m instanceof BlockHitResult b && m.getType() == HitResult.Type.BLOCK ? b.getBlockPos() : null;
    }

    /** The entity an entity hit hit, or null. */
    protected static @Nullable Entity entityOf(HitResult m) {
        return m instanceof EntityHitResult e ? e.getEntity() : null;
    }

    /**
     * Upstream {@code onImpact}: damages a hit entity, passes through plants, leaves and iron bars,
     * spends piercing, then explodes (in water with the in-water power, or as a fuel-air explosion)
     * and plays the hit sound.
     */
    protected void onImpact(HitResult m, float damageFactor) {
        Entity hitEntity = entityOf(m);
        BlockPos hitBlock = blockOf(m);
        Vec3 hitVec = m.getLocation();
        if (this.level().isClientSide()) {
            if (this.getInfo() != null && hitEntity == null && hitBlock != null) {
                this.spawnBlockPar(hitVec, hitBlock);
            } else if (this.getInfo() != null && ironCurtainOf(hitEntity) != null) {
                this.spawnIronCurtainParticle(hitVec);
            }
            return;
        }
        WeaponInfo info = this.getInfo();
        if (hitEntity != null) {
            if (hitEntity instanceof BaseBulletEntity && !info.canBeIntercepted) {
                return;
            }
            if (hitEntity instanceof FlareEntity || hitEntity instanceof ChaffEntity) {
                return;
            }
            this.onImpactEntity(hitEntity, damageFactor);
            io.github.mcheliceport.effect.VehicleImpactEmitter.hit(this, hitEntity);
            this.piercing--;
        }
        if (hitBlock != null) {
            BlockState state = this.level().getBlockState(hitBlock);
            if (state.is(BlockTags.LEAVES) || state.getBlock() instanceof VegetationBlock
                    || state.is(Blocks.IRON_BARS)) {
                return;
            }
        }
        float p = this.explosionPower * damageFactor;
        float i = this.explosionPowerInWater * damageFactor;
        if (this.piercing > 0) {
            this.piercing--;
            if (p > 0.0F) {
                this.newExplosion(hitVec.x, hitVec.y, hitVec.z, 1.0F, 1.0F, false);
            }
            return;
        }
        if (i == 0.0F) {
            if (info.isFAE) {
                this.newFAExplosion(this.getX(), this.getY(), this.getZ(), p, info.explosionBlock);
            } else if (p > 0.0F) {
                this.newExplosion(hitVec.x, hitVec.y, hitVec.z, p, info.explosionBlock, false);
            } else if (p < 0.0F) {
                this.playExplosionSound();
            }
        } else if (hitEntity != null) {
            if (this.isInWater()) {
                this.newExplosion(hitVec.x, hitVec.y, hitVec.z, i, i, true);
            } else {
                this.newExplosion(hitVec.x, hitVec.y, hitVec.z, p, info.explosionBlock, false);
            }
        } else if (!this.isInWater() && (hitBlock == null || !WeaponWorld.isBlockInWater(this.level(), hitBlock))) {
            if (p > 0.0F) {
                this.newExplosion(hitVec.x, hitVec.y, hitVec.z, p, info.explosionBlock, false);
            } else if (p < 0.0F) {
                this.playExplosionSound();
            }
        } else {
            BlockPos at = hitBlock != null ? hitBlock : this.blockPosition();
            this.newExplosion(at.getX(), at.getY(), at.getZ(), i, i, true);
        }
        ClientSoundPayload.send(this.level(), this.getX(), this.getY(), this.getZ(), info.hitSoundRange, info.hitSound,
                true);
        this.discard();
    }

    /** Thrown damage from this projectile and its shooter, scaled by the config and the definition. */
    public void onImpactEntity(Entity entity, float damageFactor) {
        AircraftHitBox hitBox = entity instanceof AircraftHitBox part ? part : null;
        if (hitBox != null) { entity = hitBox.getParent(); }
        if (entity.isAlive() && this.level() instanceof ServerLevel server) {
            DamageScaling.applyHurtResistantTime(entity);
            DamageSource ds = server.damageSources().thrown(this, this.shootingEntity);
            float damage = DamageScaling.applyDamageVsEntity(entity, this.getPower() * damageFactor);
            damage *= this.getInfo() != null ? this.getInfo().getDamageFactor(WeaponTargets.targetName(entity)) : 1.0F;
            var blood = io.github.mcheliceport.effect.VehicleBlood.projectile(entity, this, ds,
                    io.github.mcheliceport.effect.VehicleBloodRules.DIRECT, this.getDeltaMovement());
            boolean applied = hitBox != null ? hitBox.hurtServer(server, ds, damage) : entity.hurtServer(server, ds, damage);
            io.github.mcheliceport.effect.VehicleBlood.finish(blood, applied);
            if (this instanceof BulletEntity && entity instanceof net.minecraft.world.entity.npc.villager.Villager
                    && this.shootingEntity != null
                    && this.shootingEntity.getVehicle() instanceof io.github.mcheliceport.entity.seat.SeatEntity) {
                io.github.mcheliceport.registry.ModCriteriaTriggers.trigger(io.github.mcheliceport.registry.ModCriteriaTriggers.VILLAGER_HURT_BULLET, this.shootingEntity);
            }
        }
        this.notifyHitBullet();
    }

    /** Fuel-air explosion: always plays its sound and sets 15 entities on fire, but breaks no blocks. */
    public void newFAExplosion(double x, double y, double z, float exp, float expBlock) {
        WeaponInfo info = this.getInfo();
        float size = exp;
        if (info != null && info.isNewExplosionBreak) {
            size = info.explosionRadius > 0.0F ? info.explosionRadius : exp;
        }
        float damageRadius = info != null && info.isNewExplosionBreak && info.explosionDamageRadius > 0.0F
                ? info.explosionDamageRadius : size;
        McheliExplosion.Result result = McheliExplosion.newExplosion(this.level(),
                new McheliExplosion.Params(this, this.shootingEntity, x, y, z, size, size, exp, expBlock,
                        damageRadius, true, info != null && info.flaming, false, 15, null));
        if (result != null && result.hitEntity) {
            this.notifyHitBullet();
        }
    }

    public void newExplosion(double x, double y, double z, float exp, float expBlock, boolean inWater) {
        WeaponInfo info = this.getInfo();
        float size = exp;
        float sizeBlock = expBlock;
        if (info != null && info.isNewExplosionBreak) {
            size = info.explosionRadius > 0.0F ? info.explosionRadius : exp;
            sizeBlock = size;
            expBlock = info.explosionBlock >= 0 ? info.explosionBlock : expBlock;
        }
        this.explode(x, y, z, size, sizeBlock, exp, expBlock, inWater);
    }

    public void newExplosion(double x, double y, double z, float size, float sizeBlock, float damagePower,
            float blockPower, boolean inWater) {
        WeaponInfo info = this.getInfo();
        if (info != null && info.isNewExplosionBreak) {
            size = info.explosionRadius > 0.0F ? info.explosionRadius : size;
            sizeBlock = size;
            blockPower = info.explosionBlock >= 0 ? info.explosionBlock : blockPower;
        }
        this.explode(x, y, z, size, sizeBlock, damagePower, blockPower, inWater);
    }

    private void explode(double x, double y, double z, float size, float sizeBlock, float damagePower,
            float blockPower, boolean inWater) {
        WeaponInfo info = this.getInfo();
        if (this.level() instanceof ServerLevel server) {
            NtmPayloads.detonate(server, this, info, x, y, z, blockPower);
        }
        if (info != null && info.effectOnly) {
            return;
        }
        float damageRadius = info != null && info.isNewExplosionBreak && info.explosionDamageRadius > 0.0F
                ? info.explosionDamageRadius : size;
        McheliExplosion.Params params = new McheliExplosion.Params(this, this.shootingEntity, x, y, z, size,
                sizeBlock, damagePower, blockPower, damageRadius, this.random.nextInt(3) == 0,
                info != null && info.flaming, true, 0, info != null ? info.damageFactor : null);
        McheliExplosion.Result result = inWater ? McheliExplosion.newExplosionInWater(this.level(), params)
                : McheliExplosion.newExplosion(this.level(), params);
        if (result != null && result.hitEntity) {
            this.notifyHitBullet();
        }
    }

    /** Upstream {@code MCH_ExplosionV2.playExplosionSound}: the vanilla explosion sound alone. */
    public void playExplosionSound() {
        this.level().playSound(null, this.getX(), this.getY(), this.getZ(), SoundEvents.GENERIC_EXPLODE,
                SoundSource.BLOCKS, 4.0F,
                (1.0F + (this.random.nextFloat() - this.random.nextFloat()) * 0.2F) * 0.7F);
    }

    // ---- saving ----

    /** Upstream discarded projectiles read from disk; the port does not save them at all (mines aside). */
    @Override
    public boolean shouldBeSaved() {
        return false;
    }

    @Override
    protected void addAdditionalSaveData(ValueOutput output) {
        output.putString("WeaponName", this.getInfoName());
    }

    @Override
    protected void readAdditionalSaveData(ValueInput input) {
        this.discard();
    }

    // ---- being hit ----

    @Override
    public boolean isPickable() {
        return true;
    }

    @Override
    public float getPickRadius() {
        return 1.0F;
    }

    /** Thrown damage (another projectile, a snowball) sets it off where it is. */
    @Override
    public boolean hurtServer(ServerLevel level, DamageSource ds, float damage) {
        if (this.isInvulnerableToBase(ds)) {
            return false;
        }
        if (damage > 0.0F && ds.is(DamageTypes.THROWN)) {
            this.hurtMarked = true;
            this.onImpact(new BlockHitResult(this.position(), Direction.DOWN, this.blockPosition(), false), 1.0F);
            return true;
        }
        return false;
    }

    // ---- active radar ----

    /** Active radar seekers: the chaff or target nearest the missile's axis within the lock cone. */
    protected void scanForTargets() {
        WeaponInfo info = this.getInfo();
        if (this.numLockedChaff >= info.numLockedChaffMax) {
            this.setTargetEntity(null);
            return;
        }
        double range = info.maxLockOnRange;
        double maxAngleRad = Math.toRadians(info.maxLockOnAngle);
        Vec3 missileDir = this.getDeltaMovement();
        List<Entity> entities = this.level().getEntities((Entity) null, this.getBoundingBox().inflate(range),
                e -> true);
        if (entities.isEmpty()) {
            return;
        }
        Entity closestTarget = null;
        Entity nearestChaff = null;
        double minAngle = Double.MAX_VALUE;
        double minChaffDistSq = Double.MAX_VALUE;
        for (Entity entity : entities) {
            if (this.isFriendlyFire(entity)) {
                continue;
            }
            if (this instanceof AAMissileEntity) {
                if (entity instanceof ChaffEntity) {
                    double distSq = this.handleChaff(entity, missileDir, maxAngleRad);
                    if (distSq < minChaffDistSq) {
                        minChaffDistSq = distSq;
                        nearestChaff = entity;
                    }
                } else if (this.isValidAATarget(entity, missileDir, maxAngleRad)) {
                    double angle = this.calculateAngle(entity, missileDir);
                    if (angle < minAngle) {
                        minAngle = angle;
                        closestTarget = entity;
                    }
                }
            } else if (this instanceof ATMissileEntity && this.isValidATTarget(entity, missileDir, maxAngleRad)) {
                double angle = this.calculateAngle(entity, missileDir);
                if (angle < minAngle) {
                    minAngle = angle;
                    closestTarget = entity;
                }
            }
        }
        if (nearestChaff != null) {
            this.targetEntity = nearestChaff;
            this.numLockedChaff++;
        } else {
            this.targetEntity = closestTarget;
        }
    }

    private boolean isFriendlyFire(Entity target) {
        if (WeaponWorld.isEqual(target, this.shootingAircraft) || WeaponWorld.isEqual(target, this.shootingEntity)) {
            return true;
        }
        if (this.shootingEntity instanceof LivingEntity && target.getControllingPassenger() instanceof Player player) {
            return player.isAlliedTo(this.shootingEntity);
        }
        if (this.shootingEntity instanceof LivingEntity shooter && target instanceof LivingEntity living) {
            return living.isAlliedTo(shooter);
        }
        return false;
    }

    private double handleChaff(Entity chaff, Vec3 missileDir, double maxAngle) {
        double angle = this.calculateAngle(chaff, missileDir);
        return angle <= maxAngle ? this.distanceToSqr(chaff) : Double.MAX_VALUE;
    }

    private boolean isValidAATarget(Entity entity, Vec3 missileDir, double maxAngle) {
        if (!(entity instanceof AircraftEntity ac)) {
            return false;
        }
        if (ac.getChaffUseTime() > 0) {
            return false;
        }
        if (WeaponGuidanceSystem.isEntityOnGround(entity, this.getInfo().lockMinHeight)) {
            return false;
        }
        return this.calculateAngle(entity, missileDir) <= maxAngle;
    }

    private boolean isValidATTarget(Entity entity, Vec3 missileDir, double maxAngle) {
        if (!WeaponGuidanceSystem.isEntityOnGround(entity, this.getInfo().lockMinHeight)) {
            return false;
        }
        if (entity instanceof AircraftEntity) {
            return this.calculateAngle(entity, missileDir) <= maxAngle;
        }
        if (!this.getInfo().ridableOnly && entity instanceof LivingEntity && !entity.isPassenger()) {
            return this.calculateAngle(entity, missileDir) <= maxAngle;
        }
        return false;
    }

    private double calculateAngle(Entity target, Vec3 missileDir) {
        Vec3 targetVec = new Vec3(target.getX() - this.getX(), target.getY() - this.getY(),
                target.getZ() - this.getZ());
        double dot = missileDir.normalize().dot(targetVec.normalize());
        return Math.acos(Mth.clamp(dot, -1.0, 1.0));
    }
}
