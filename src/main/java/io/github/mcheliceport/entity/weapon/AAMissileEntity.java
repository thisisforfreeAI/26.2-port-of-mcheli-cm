package io.github.mcheliceport.entity.weapon;

import io.github.mcheliceport.registry.ModEntityTypes;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.EntityHitResult;

/**
 * An air-to-air missile chasing its target after {@code RigidityTime} ticks; it is lost to flares
 * near the target, or when the target is gone or more than 1,850 blocks away. A GPS missile flies at
 * its point instead, and active radar, anti-radiation and data-link missiles fly on without a target
 * while they search or wait for one (see {@code MissileGuidance}). Ported from upstream
 * {@code MCH_EntityAAMissile}.
 */
public class AAMissileEntity extends BaseBulletEntity {

    public AAMissileEntity(EntityType<? extends AAMissileEntity> type, Level level) {
        super(type, level);
    }

    public AAMissileEntity(Level level, double x, double y, double z, double mx, double my, double mz, float yaw,
            float pitch, double acceleration) {
        super(ModEntityTypes.AA_MISSILE.get(), level, x, y, z, mx, my, mz, yaw, pitch, acceleration);
    }

    @Override
    public void tick() {
        super.tick();
        if (this.getCountOnUpdate() > 4 && this.getInfo() != null && !this.getInfo().disableSmoke) {
            this.spawnParticle(this.getInfo().trajectoryParticleName, 3, 7.0F * this.getInfo().smokeSize * 0.5F);
        }
        if (!this.level().isClientSide() && this.getInfo() != null && !this.isRemoved()) {
            if (this.pointTarget != null) {
                this.guidanceToPoint(this.pointTarget);
                return;
            }
            Entity target = this.targetEntity;
            if (this.shootingEntity != null && (target == null || !target.isAlive()) && this.keepsSearching()) {
                if (this.getInfo().activeRadar && this.getInfo().scanInterval > 0
                        && this.tickCount % this.getInfo().scanInterval == 0) {
                    this.scanForTargets();
                }
            } else if (this.shootingEntity != null && target != null && target.isAlive()) {
                double d = this.distanceToSqr(target);
                if (d > 3422500.0) {
                    this.discard();
                } else if (this.getCountOnUpdate() > this.getInfo().rigidityTime) {
                    if (this.usingFlareOfTarget(target)) {
                        this.discard();
                        return;
                    }
                    if (this.getInfo().proximityFuseDist >= 0.1F && d < this.getInfo().proximityFuseDist) {
                        this.setPos((target.getX() + this.getX()) / 2.0, (target.getY() + this.getY()) / 2.0,
                                (target.getZ() + this.getZ()) / 2.0);
                        this.onImpact(new EntityHitResult(target), 1.0F);
                    } else {
                        this.guidanceToTarget(target.getX(), target.getY(), target.getZ());
                    }
                }
            } else {
                this.discard();
            }
        }
    }

    /**
     * Whether the missile flies on without a target: an active radar, anti-radiation or data-link
     * missile searches or waits for one, where upstream's air-to-air missile was lost at once.
     */
    private boolean keepsSearching() {
        return this.getInfo().activeRadar || this.guidance != null && this.guidance.keepsSearching();
    }

    @Override
    public String getDefaultBulletModelName() {
        return "aamissile";
    }
}
