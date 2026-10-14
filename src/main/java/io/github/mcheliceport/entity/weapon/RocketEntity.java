package io.github.mcheliceport.entity.weapon;

import io.github.mcheliceport.registry.ModEntityTypes;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;

/** An unguided rocket with a smoke trail; cluster rockets split into bomblet rockets. Ported from upstream {@code MCH_EntityRocket}. */
public class RocketEntity extends BaseBulletEntity {

    public RocketEntity(EntityType<? extends RocketEntity> type, Level level) {
        super(type, level);
    }

    public RocketEntity(Level level, double x, double y, double z, double mx, double my, double mz, float yaw,
            float pitch, double acceleration) {
        super(ModEntityTypes.ROCKET.get(), level, x, y, z, mx, my, mz, yaw, pitch, acceleration);
    }

    @Override
    public void tick() {
        super.tick();
        this.onUpdateBomblet();
        if (this.isBomblet <= 0 && this.getInfo() != null && !this.getInfo().disableSmoke) {
            this.spawnParticle(this.getInfo().trajectoryParticleName, 3, 5.0F * this.getInfo().smokeSize * 0.5F);
        }
    }

    @Override
    public void sprinkleBomblet() {
        if (!this.level().isClientSide()) {
            Vec3 m = this.getDeltaMovement();
            RocketEntity e = new RocketEntity(this.level(), this.getX(), this.getY(), this.getZ(), m.x, m.y, m.z,
                    this.getYRot(), this.getXRot(), this.acceleration);
            e.setName(this.getInfoName());
            e.setParameterFromWeapon(this, this.shootingAircraft, this.shootingEntity);
            float spread = this.getInfo().bombletDiff;
            e.setDeltaMovement(e.getDeltaMovement().add((this.random.nextFloat() - 0.5) * spread,
                    (this.random.nextFloat() - 0.5) * spread, (this.random.nextFloat() - 0.5) * spread));
            e.setBomblet();
            this.level().addFreshEntity(e);
        }
    }

    @Override
    public String getDefaultBulletModelName() {
        return "rocket";
    }
}
