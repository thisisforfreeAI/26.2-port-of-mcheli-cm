package io.github.mcheliceport.entity.weapon;

import io.github.mcheliceport.registry.ModEntityTypes;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.BlockHitResult;

/**
 * A bullet or shell; explosive ones with a {@code ProximityFuseDist} burst next to whatever they
 * pass. Ported from upstream {@code MCH_EntityBullet} (its copy of {@code onUpdateCollided} was
 * the base class's).
 */
public class BulletEntity extends BaseBulletEntity {

    public BulletEntity(EntityType<? extends BulletEntity> type, Level level) {
        super(type, level);
    }

    public BulletEntity(Level level, double x, double y, double z, double mx, double my, double mz, float yaw,
            float pitch, double acceleration) {
        super(ModEntityTypes.BULLET.get(), level, x, y, z, mx, my, mz, yaw, pitch, acceleration);
    }

    @Override
    public void tick() {
        super.tick();
        if (!this.isRemoved() && !this.level().isClientSide() && this.getCountOnUpdate() > 1 && this.getInfo() != null
                && this.explosionPower > 0) {
            float pDist = this.getInfo().proximityFuseDist;
            if (pDist > 0.1) {
                float rng = ++pDist + Mth.abs(this.getInfo().acceleration);
                for (Entity entity1 : this.level().getEntities(this, this.getBoundingBox().inflate(rng))) {
                    if (this.canBeCollidedEntity(entity1) && entity1.distanceToSqr(this) < pDist * pDist) {
                        this.setPos((entity1.getX() + this.getX()) / 2.0, (entity1.getY() + this.getY()) / 2.0,
                                (entity1.getZ() + this.getZ()) / 2.0);
                        this.onImpact(new BlockHitResult(entity1.position(), Direction.DOWN,
                                BlockPos.containing(this.getX(), this.getY(), this.getZ()), false), 1.0F);
                        break;
                    }
                }
            }
        }
    }

    @Override
    public String getDefaultBulletModelName() {
        return "bullet";
    }
}
