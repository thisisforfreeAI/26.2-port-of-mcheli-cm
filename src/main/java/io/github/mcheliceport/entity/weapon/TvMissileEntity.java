package io.github.mcheliceport.entity.weapon;

import io.github.mcheliceport.config.CommonConfig;
import io.github.mcheliceport.content.weapon.WeaponInfo;
import io.github.mcheliceport.entity.aircraft.AircraftEntity;
import io.github.mcheliceport.registry.ModEntityTypes;
import io.github.mcheliceport.weapon.WeaponWorld;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;

/**
 * A TV-guided missile: wire guidance flies it where its shooter looks while it is the vehicle's
 * TV missile; laser guidance flies it to the point the shooter's view (or with no targeting pod
 * the vehicle's nose) meets a block. Lost beyond {@code tvMissileMaxRange} of the shooter. Ported
 * from upstream {@code MCH_EntityTvMissile}.
 *
 * <p>Upstream gated laser guidance on the pod's {@code targeting} flag, which nothing ever set, so
 * a laser-guided missile never steered while its weapon was selected. The port steers it always.
 */
public class TvMissileEntity extends BaseBulletEntity {

    public boolean isSpawnParticle = true;
    public double targetPosX;
    public double targetPosY;
    public double targetPosZ;

    public TvMissileEntity(EntityType<? extends TvMissileEntity> type, Level level) {
        super(type, level);
    }

    public TvMissileEntity(Level level, double x, double y, double z, double mx, double my, double mz, float yaw,
            float pitch, double acceleration) {
        super(ModEntityTypes.TV_MISSILE.get(), level, x, y, z, mx, my, mz, yaw, pitch, acceleration);
    }

    @Override
    public void tick() {
        super.tick();
        if (this.isRemoved()) {
            return;
        }
        this.onUpdateBomblet();
        WeaponInfo info = this.getInfo();
        if (this.isSpawnParticle && info != null && !info.disableSmoke) {
            this.spawnParticle(info.trajectoryParticleName, 3, 5.0F * info.smokeSize * 0.5F);
        }
        if (this.shootingEntity != null) {
            int maxRange = CommonConfig.get(CommonConfig.TV_MISSILE_MAX_RANGE);
            if (maxRange >= 0 && this.distanceToSqr(this.shootingEntity) > (double) maxRange * maxRange) {
                this.discard();
            }
            if (!this.level().isClientSide() && !this.isRemoved()) {
                this.onUpdateMotion();
            }
        } else if (!this.level().isClientSide()) {
            this.discard();
        }
    }

    public void onUpdateMotion() {
        Entity shooter = this.shootingEntity;
        if (shooter == null || !shooter.isAlive()) {
            return;
        }
        AircraftEntity ac = WeaponWorld.aircraftOf(shooter);
        WeaponInfo info = this.getInfo();
        if (info == null) {
            return;
        }
        if (!info.laserGuidance) {
            this.handleWireGuidance(shooter, ac);
        } else {
            this.handleLaserGuidance(shooter, info);
        }
    }

    private void handleWireGuidance(Entity shooter, AircraftEntity ac) {
        if (ac != null && ac.getTVMissile() == this) {
            float yaw = shooter.getYRot();
            float pitch = shooter.getXRot();
            double tX = -Math.sin(Math.toRadians(yaw)) * Math.cos(Math.toRadians(pitch));
            double tZ = Math.cos(Math.toRadians(yaw)) * Math.cos(Math.toRadians(pitch));
            double tY = -Math.sin(Math.toRadians(pitch));
            this.setMotion(tX, tY, tZ);
            this.setYRot(yaw);
        }
    }

    private void handleLaserGuidance(Entity shooter, WeaponInfo info) {
        Entity nose = info.hasLaserGuidancePod || this.shootingAircraft == null ? shooter : this.shootingAircraft;
        float yaw = nose.getYRot();
        float pitch = nose.getXRot();
        double ix = shooter.xo + (shooter.getX() - shooter.xo) * 0.5;
        double iy = shooter.yo + (shooter.getY() - shooter.yo) * 0.5 + shooter.getEyeHeight();
        double iz = shooter.zo + (shooter.getZ() - shooter.zo) * 0.5;
        Vec3 src = new Vec3(ix, iy, iz);
        Vec3 dst = src.add(WeaponWorld.fromPitchYaw(pitch, yaw).scale(1500.0));
        BlockHitResult hitResult = WeaponWorld.rayTrace(this.level(), src, dst, false, true);
        Vec3 hit = hitResult != null ? hitResult.getLocation() : dst;
        this.targetPosX = hit.x;
        this.targetPosY = hit.y;
        this.targetPosZ = hit.z;
        this.onLaserGuide();
    }

    /** Turns towards the laser point when it lies on a solid block, easing speed and heading separately. */
    public void onLaserGuide() {
        BlockPos targetPos = BlockPos.containing(this.targetPosX, this.targetPosY, this.targetPosZ);
        Vec3 m = this.getDeltaMovement();
        if (this.level().getBlockState(targetPos).isSolid()) {
            double deltaX = this.targetPosX - this.getX();
            double deltaY = this.targetPosY - this.getY();
            double deltaZ = this.targetPosZ - this.getZ();
            if (this.getBulletGravity() == 0.0) {
                double distance = Math.sqrt(deltaX * deltaX + deltaY * deltaY + deltaZ * deltaZ);
                if (distance < 0.001) {
                    return;
                }
                double targetMotionX = deltaX * this.acceleration / distance;
                double targetMotionY = deltaY * this.acceleration / distance;
                double targetMotionZ = deltaZ * this.acceleration / distance;
                float turn = (float) this.getInfo().turningFactor;
                double maxSpeed = this.getInfo().acceleration;
                double curSpeed = m.length();
                if (curSpeed < 1.0e-6) {
                    m = new Vec3(m.x + (targetMotionX - m.x) * turn, m.y + (targetMotionY - m.y) * turn,
                            m.z + (targetMotionZ - m.z) * turn);
                } else {
                    double newSpeed = curSpeed + (this.acceleration - curSpeed) * turn;
                    if (newSpeed > maxSpeed) {
                        newSpeed = maxSpeed;
                    }
                    double cdx = m.x / curSpeed;
                    double cdy = m.y / curSpeed;
                    double cdz = m.z / curSpeed;
                    double tdx = deltaX / distance;
                    double tdy = deltaY / distance;
                    double tdz = deltaZ / distance;
                    double ndx = cdx + (tdx - cdx) * turn;
                    double ndy = cdy + (tdy - cdy) * turn;
                    double ndz = cdz + (tdz - cdz) * turn;
                    double nlen = Math.sqrt(ndx * ndx + ndy * ndy + ndz * ndz);
                    if (nlen < 1.0e-6) {
                        ndx = tdx;
                        ndy = tdy;
                        ndz = tdz;
                        nlen = 1.0;
                    }
                    m = new Vec3(ndx / nlen * newSpeed, ndy / nlen * newSpeed, ndz / nlen * newSpeed);
                }
            } else {
                deltaY *= 0.3;
                double distance = Math.sqrt(deltaX * deltaX + deltaY * deltaY + deltaZ * deltaZ);
                if (distance < 0.001) {
                    return;
                }
                m = new Vec3(deltaX * this.acceleration / distance, m.y, deltaZ * this.acceleration / distance);
            }
            this.setDeltaMovement(m);
        }
        double horizontalSpeed = Math.sqrt(m.x * m.x + m.z * m.z);
        this.setYRot((float) Math.toDegrees(Math.atan2(m.z, m.x)) - 90.0F);
        this.setXRot(-(float) Math.toDegrees(Math.atan2(m.y, horizontalSpeed)));
    }

    @Override
    public void sprinkleBomblet() {
        if (this.level().isClientSide()) {
            return;
        }
        Vec3 m = this.getDeltaMovement();
        RocketEntity rocket = new RocketEntity(this.level(), this.getX(), this.getY(), this.getZ(), m.x, m.y, m.z,
                this.getYRot(), this.getXRot(), this.acceleration);
        rocket.setName(this.getInfoName());
        rocket.setParameterFromWeapon(this, this.shootingAircraft, this.shootingEntity);
        float spread = this.getInfo().bombletDiff;
        rocket.setDeltaMovement(rocket.getDeltaMovement().add((this.random.nextFloat() - 0.5) * spread,
                (this.random.nextFloat() - 0.5) * spread, (this.random.nextFloat() - 0.5) * spread));
        rocket.setBomblet();
        this.level().addFreshEntity(rocket);
    }

    /** Upstream's default for TV missiles is the AT missile model, which is the AS missile model. */
    @Override
    public String getDefaultBulletModelName() {
        return "asmissile";
    }
}
