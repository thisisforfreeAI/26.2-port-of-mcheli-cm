package io.github.mcheliceport.entity.weapon;

import io.github.mcheliceport.config.CommonConfig;
import io.github.mcheliceport.effect.McheliEffects;
import io.github.mcheliceport.effect.ParticleParam;
import io.github.mcheliceport.registry.ModEntityTypes;
import net.minecraft.core.BlockPos;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;

/**
 * A marker rocket: where it lands (on a block with open sky, or on an entity) it pours red smoke
 * for 100 ticks, and 60 ticks in, six or seven bombs fall from y 260 and above. Status 0 is unfired,
 * 1 flying, 2 marking. Ported from upstream {@code MCH_EntityMarkerRocket}.
 */
public class MarkerRocketEntity extends BaseBulletEntity {

    private static final EntityDataAccessor<Byte> MARKER_STATUS =
            SynchedEntityData.defineId(MarkerRocketEntity.class, EntityDataSerializers.BYTE);
    public int countDown;

    public MarkerRocketEntity(EntityType<? extends MarkerRocketEntity> type, Level level) {
        super(type, level);
        this.countDown = 0;
    }

    public MarkerRocketEntity(Level level, double x, double y, double z, double mx, double my, double mz, float yaw,
            float pitch, double acceleration) {
        super(ModEntityTypes.MARKER_ROCKET.get(), level, x, y, z, mx, my, mz, yaw, pitch, acceleration);
        this.countDown = 0;
    }

    @Override
    protected void defineSynchedData(SynchedEntityData.Builder builder) {
        super.defineSynchedData(builder);
        builder.define(MARKER_STATUS, (byte) 0);
    }

    public int getMarkerStatus() {
        return this.entityData.get(MARKER_STATUS);
    }

    public void setMarkerStatus(int n) {
        if (!this.level().isClientSide()) {
            this.entityData.set(MARKER_STATUS, (byte) n);
        }
    }

    @Override
    public boolean hurtServer(ServerLevel level, DamageSource source, float damage) {
        return false;
    }

    @Override
    public void tick() {
        int status = this.getMarkerStatus();
        if (this.level().isClientSide()) {
            if (this.getInfo() == null) {
                super.tick();
            }
            if (this.getInfo() != null && !this.getInfo().disableSmoke && status != 0) {
                if (status == 1) {
                    super.tick();
                    this.spawnParticle(this.getInfo().trajectoryParticleName, 3, 5.0F * this.getInfo().smokeSize * 0.5F);
                } else {
                    float gb = this.random.nextFloat() * 0.3F;
                    this.spawnSmoke(5, 10 + this.random.nextInt(4), this.random.nextFloat() * 0.2F + 0.8F, gb, gb,
                            (this.random.nextFloat() - 0.5F) * 0.7F, 0.3F + this.random.nextFloat() * 0.3F,
                            (this.random.nextFloat() - 0.5F) * 0.7F);
                }
            }
        } else if (status == 0 || this.isInWater()) {
            this.discard();
        } else if (status == 1) {
            super.tick();
        } else if (this.countDown > 0) {
            this.countDown--;
            if (this.countDown == 40) {
                int num = 6 + this.random.nextInt(2);
                for (int i = 0; i < num; i++) {
                    BombEntity e = new BombEntity(this.level(), this.getX() + (this.random.nextFloat() - 0.5F) * 15.0F,
                            260.0F + this.random.nextFloat() * 10.0F + i * 30,
                            this.getZ() + (this.random.nextFloat() - 0.5F) * 15.0F, 0.0, -0.5, 0.0, 0.0F, 90.0F, 4.0);
                    e.setName(this.getInfoName());
                    e.explosionPower = 3 + this.random.nextInt(2);
                    e.explosionPowerInWater = 0;
                    e.setPower(30);
                    e.piercing = 0;
                    e.shootingAircraft = this.shootingAircraft;
                    e.shootingEntity = this.shootingEntity;
                    this.level().addFreshEntity(e);
                }
            }
        } else {
            this.discard();
        }
    }

    /** The marking smoke: {@code num} coloured, wind-blown smoke particles along the last tick. Client only. */
    private void spawnSmoke(int num, float size, float r, float g, float b, float mx, float my, float mz) {
        double x = (this.getX() - this.xo) / num;
        double y = (this.getY() - this.yo) / num;
        double z = (this.getZ() - this.zo) / num;
        for (int i = 0; i < num; i++) {
            ParticleParam prm = new ParticleParam("smoke", this.xo + x * i, this.yo + y * i, this.zo + z * i);
            prm.motionX = mx;
            prm.motionY = my;
            prm.motionZ = mz;
            prm.size = size + this.random.nextFloat();
            prm.setColor(1.0F, r, g, b);
            prm.isEffectWind = true;
            McheliEffects.spawnParticle(this.level(), prm);
        }
    }

    @Override
    protected void onImpact(HitResult m, float damageFactor) {
        if (this.level().isClientSide()) {
            return;
        }
        Entity hitEntity = entityOf(m);
        if (hitEntity != null) {
            this.setMarkerStatus(2);
            this.markAt(hitEntity.getX(), hitEntity.getY() + hitEntity.getBbHeight() + 1.1, hitEntity.getZ());
        } else if (m instanceof BlockHitResult block && m.getType() == HitResult.Type.BLOCK) {
            this.handleBlockImpact(block);
        }
    }

    private void markAt(double x, double y, double z) {
        this.setPos(x, y, z);
        this.xo = x;
        this.yo = y;
        this.zo = z;
        this.countDown = 100;
    }

    private void handleBlockImpact(BlockHitResult m) {
        Level level = this.level();
        BlockPos targetPos = m.getBlockPos().relative(m.getDirection());
        if (!level.isEmptyBlock(targetPos)) {
            this.discard();
            return;
        }
        if (CommonConfig.get(CommonConfig.EXPLOSION_FLAMING_BLOCK)) {
            level.setBlockAndUpdate(targetPos, Blocks.FIRE.defaultBlockState());
        }
        int nonAirAbove = 0;
        for (int i = targetPos.getY() + 1; i <= level.getMaxY(); i++) {
            if (!level.isEmptyBlock(new BlockPos(targetPos.getX(), i, targetPos.getZ())) && ++nonAirAbove >= 5) {
                break;
            }
        }
        if (nonAirAbove < 5) {
            this.setMarkerStatus(2);
            this.markAt(targetPos.getX() + 0.5, targetPos.getY() + 1.1, targetPos.getZ() + 0.5);
        } else {
            this.discard();
        }
    }

    @Override
    public String getDefaultBulletModelName() {
        return "rocket";
    }
}
