package io.github.mcheliceport.entity.weapon;

import io.github.mcheliceport.content.weapon.WeaponInfo;
import io.github.mcheliceport.registry.ModEntityTypes;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.Vec3;

/**
 * An air-to-surface missile flying to a fixed point (climbing for its first ten ticks when it has
 * no gravity); within {@code ProximityFuseDist} it explodes or releases its bomblets. Ported from
 * upstream {@code MCH_EntityASMissile}.
 */
public class ASMissileEntity extends BaseBulletEntity {

    public double targetPosX;
    public double targetPosY;
    public double targetPosZ;

    public ASMissileEntity(EntityType<? extends ASMissileEntity> type, Level level) {
        super(type, level);
    }

    public ASMissileEntity(Level level, double x, double y, double z, double mx, double my, double mz, float yaw,
            float pitch, double acceleration) {
        super(ModEntityTypes.AS_MISSILE.get(), level, x, y, z, mx, my, mz, yaw, pitch, acceleration);
    }

    @Override
    public float getBulletGravity() {
        return this.getBomblet() == 1 ? -0.03F : super.getBulletGravity();
    }

    @Override
    public float getGravityInWater() {
        return this.getBomblet() == 1 ? -0.03F : super.getGravityInWater();
    }

    @Override
    public void tick() {
        super.tick();
        WeaponInfo info = this.getInfo();
        if (info == null || this.isRemoved()) {
            return;
        }
        if (!info.disableSmoke && this.getBomblet() == 0) {
            this.spawnParticle(info.trajectoryParticleName, 3, 10.0F * info.smokeSize * 0.5F);
        }
        if (!this.level().isClientSide() && this.isBomblet != 1) {
            this.guideMissile(info);
        }
        this.updateRotation();
        this.onUpdateBomblet();
    }

    private void guideMissile(WeaponInfo info) {
        BlockPos targetPos = BlockPos.containing(this.targetPosX, this.targetPosY, this.targetPosZ);
        if (!this.level().getBlockState(targetPos).isSolid()) {
            return;
        }
        double distanceToTarget = Math.sqrt(this.distanceToSqr(this.targetPosX, this.targetPosY, this.targetPosZ));
        if (distanceToTarget < info.proximityFuseDist) {
            if (info.bomblet > 0) {
                for (int i = 0; i < info.bomblet; i++) {
                    this.sprinkleBomblet();
                }
            } else {
                this.onImpact(new EntityHitResult(this), 1.0F);
            }
            this.discard();
            return;
        }
        double dirX = this.targetPosX - this.getX();
        double dirY = this.targetPosY - this.getY();
        double dirZ = this.targetPosZ - this.getZ();
        if (this.getBulletGravity() == 0.0) {
            dirY += this.getCountOnUpdate() < 10 ? 20.0 : 0.0;
            double length = Math.sqrt(dirX * dirX + dirY * dirY + dirZ * dirZ);
            this.setDeltaMovement(dirX * this.acceleration / length, dirY * this.acceleration / length,
                    dirZ * this.acceleration / length);
        } else {
            double horizontalLength = Math.sqrt(dirX * dirX + dirZ * dirZ);
            this.setDeltaMovement(dirX * this.acceleration / horizontalLength, this.getDeltaMovement().y,
                    dirZ * this.acceleration / horizontalLength);
        }
    }

    private void updateRotation() {
        Vec3 m = this.getDeltaMovement();
        double horizontalMotion = Math.sqrt(m.x * m.x + m.z * m.z);
        this.setYRot((float) Math.toDegrees(Math.atan2(m.z, m.x)) - 90.0F);
        this.setXRot(-(float) Math.toDegrees(Math.atan2(m.y, horizontalMotion)));
    }

    @Override
    public void sprinkleBomblet() {
        if (this.level().isClientSide()) {
            return;
        }
        Vec3 m = this.getDeltaMovement();
        ASMissileEntity bomblet = new ASMissileEntity(this.level(), this.getX(), this.getY(), this.getZ(), m.x, m.y,
                m.z, this.random.nextInt(360), 0.0F, this.acceleration);
        bomblet.setParameterFromWeapon(this, this.shootingAircraft, this.shootingEntity);
        bomblet.setName(this.getInfoName());
        float spread = this.getInfo().bombletDiff;
        bomblet.setDeltaMovement(m.x * 0.5 + (this.random.nextFloat() - 0.5F) * spread,
                m.y * 0.5 / 2.0 + (this.random.nextFloat() - 0.5F) * spread / 2.0F,
                m.z * 0.5 + (this.random.nextFloat() - 0.5F) * spread);
        bomblet.setBomblet();
        this.level().addFreshEntity(bomblet);
    }

    @Override
    public String getDefaultBulletModelName() {
        return "asmissile";
    }
}
