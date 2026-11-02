package io.github.mcheliceport.entity.weapon;

import io.github.mcheliceport.content.weapon.WeaponInfo;
import io.github.mcheliceport.registry.ModEntityTypes;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.EntityHitResult;

/**
 * An anti-tank missile: straight at its target, or (guidance type 1) up 100 blocks above the
 * shooter, level, then down onto the target with 20% more power. Active-radar ones search for a
 * target while they have none. Ported from upstream {@code MCH_EntityATMissile}.
 */
public class ATMissileEntity extends BaseBulletEntity {

    public int guidanceType = 0;

    public ATMissileEntity(EntityType<? extends ATMissileEntity> type, Level level) {
        super(type, level);
    }

    public ATMissileEntity(Level level, double x, double y, double z, double mx, double my, double mz, float yaw,
            float pitch, double acceleration) {
        super(ModEntityTypes.AT_MISSILE.get(), level, x, y, z, mx, my, mz, yaw, pitch, acceleration);
    }

    @Override
    public void tick() {
        super.tick();
        WeaponInfo info = this.getInfo();
        if (info == null || this.isRemoved()) {
            return;
        }
        if (this.getCountOnUpdate() > 4 && !info.disableSmoke) {
            this.spawnParticle(info.trajectoryParticleName, 3, 7.0F * info.smokeSize * 0.5F);
        }
        if (this.level().isClientSide()) {
            return;
        }
        if (this.shootingEntity != null && this.targetEntity != null && this.targetEntity.isAlive()) {
            this.processGuidance(info);
        } else if (info.activeRadar && this.tickCount % info.scanInterval == 0) {
            this.scanForTargets();
        }
    }

    private void processGuidance(WeaponInfo info) {
        double distSq = this.distanceToSqr(this.targetEntity);
        if (distSq > 3422500.0) {
            this.discard();
            return;
        }
        if (this.getCountOnUpdate() <= info.rigidityTime) {
            return;
        }
        if (this.guidanceType == 1) {
            this.handleTopAttack(info);
        } else {
            this.handleDirectGuidance(info, distSq);
        }
    }

    private void handleTopAttack(WeaponInfo info) {
        int age = this.getCountOnUpdate();
        int startTime = info.rigidityTime;
        float accel = age < startTime + info.trajectoryParticleStartTick ? 0.5F : 1.0F;
        if (age <= startTime + 20) {
            this.doingTopAttack = true;
            this.guidanceToTarget(this.targetEntity.getX(), this.shootingEntity.getY() + 100.0,
                    this.targetEntity.getZ(), accel);
        } else if (age <= startTime + 30) {
            this.guidanceToTarget(this.targetEntity.getX(), this.shootingEntity.getY(), this.targetEntity.getZ(),
                    accel);
        } else {
            if (age == startTime + 35) {
                this.setPower((int) (this.getPower() * 1.2F));
                if (this.explosionPower > 0) {
                    this.explosionPower++;
                }
            }
            this.doingTopAttack = false;
            this.guidanceToTarget(this.targetEntity.getX(), this.targetEntity.getY(), this.targetEntity.getZ(), accel);
        }
    }

    private void handleDirectGuidance(WeaponInfo info, double distSq) {
        if (info.proximityFuseDist >= 0.1F && distSq < Math.pow(info.proximityFuseDist, 2)) {
            this.setPos((this.targetEntity.getX() + this.getX()) / 2.0, (this.targetEntity.getY() + this.getY()) / 2.0,
                    (this.targetEntity.getZ() + this.getZ()) / 2.0);
            this.onImpact(new EntityHitResult(this.targetEntity), 1.0F);
        } else {
            this.guidanceToTarget(this.targetEntity.getX(), this.targetEntity.getY(), this.targetEntity.getZ());
        }
    }

    /** Upstream's default for AT missiles is the AS missile model. */
    @Override
    public String getDefaultBulletModelName() {
        return "asmissile";
    }
}
