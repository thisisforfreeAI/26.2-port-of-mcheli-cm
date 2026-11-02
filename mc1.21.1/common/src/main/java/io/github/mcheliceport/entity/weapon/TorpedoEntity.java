package io.github.mcheliceport.entity.weapon;

import io.github.mcheliceport.registry.ModEntityTypes;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;

/**
 * A torpedo: in water it settles to {@code AccelerationInWater}, running straight (unguided) or to
 * its target point (guided). Ported from upstream {@code MCH_EntityTorpedo}.
 */
public class TorpedoEntity extends BaseBulletEntity {

    public double targetPosX;
    public double targetPosY;
    public double targetPosZ;
    public double accelerationInWater = 2.0;

    public TorpedoEntity(EntityType<? extends TorpedoEntity> type, Level level) {
        super(type, level);
    }

    public TorpedoEntity(Level level, double x, double y, double z, double mx, double my, double mz, float yaw,
            float pitch, double acceleration) {
        super(ModEntityTypes.TORPEDO.get(), level, x, y, z, mx, my, mz, yaw, pitch, acceleration);
    }

    @Override
    public void tick() {
        super.tick();
        if (this.isRemoved()) {
            return;
        }
        if (this.getInfo() != null && this.getInfo().isGuidedTorpedo) {
            this.onUpdateGuided();
        } else {
            this.onUpdateNoGuided();
        }
        if (this.isInWater() && this.getInfo() != null && !this.getInfo().disableSmoke) {
            this.spawnParticle(this.getInfo().trajectoryParticleName, 3, 5.0F * this.getInfo().smokeSize * 0.5F);
        }
    }

    private void settleSpeed() {
        if (this.acceleration < this.accelerationInWater) {
            this.acceleration += 0.1;
        } else if (this.acceleration > this.accelerationInWater + 0.2F) {
            this.acceleration -= 0.1;
        }
    }

    private void onUpdateNoGuided() {
        if (!this.level().isClientSide() && this.isInWater()) {
            Vec3 m = this.getDeltaMovement();
            m = new Vec3(m.x, m.y * 0.8F, m.z);
            this.settleSpeed();
            this.setDeltaMovement(m.scale(this.acceleration / m.length()));
        }
        if (this.isInWater()) {
            Vec3 m = this.getDeltaMovement();
            double a = (float) Math.atan2(m.z, m.x);
            this.setYRot((float) (a * 180.0 / Math.PI) - 90.0F);
        }
    }

    private void onUpdateGuided() {
        if (!this.level().isClientSide() && this.isInWater()) {
            this.settleSpeed();
            double x = this.targetPosX - this.getX();
            double y = this.targetPosY - this.getY();
            double z = this.targetPosZ - this.getZ();
            double d = Math.sqrt(x * x + y * y + z * z);
            this.setDeltaMovement(x * this.acceleration / d, y * this.acceleration / d, z * this.acceleration / d);
        }
        if (this.isInWater()) {
            Vec3 m = this.getDeltaMovement();
            double a = (float) Math.atan2(m.z, m.x);
            this.setYRot((float) (a * 180.0 / Math.PI) - 90.0F);
            double r = Math.sqrt(m.x * m.x + m.z * m.z);
            this.setXRot(-((float) (Math.atan2(m.y, r) * 180.0 / Math.PI)));
        }
    }

    @Override
    public String getDefaultBulletModelName() {
        return "torpedo";
    }
}
