package io.github.mcheliceport.entity.flare;

import io.github.mcheliceport.registry.ModEntityTypes;
import net.minecraft.core.BlockPos;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import net.minecraft.world.phys.Vec3;
import io.github.mcheliceport.weapon.WeaponWorld;

/**
 * A chaff cloud that radar missiles chase instead of their target; it drifts down for 120 ticks.
 * Ported from upstream {@code MCH_EntityChaff}.
 */
public class ChaffEntity extends Entity {

    public static final int MAX_TICK_EXISTED = 120;
    public double gravity = -0.001;
    public double airResistance = 0.99;

    public ChaffEntity(EntityType<? extends ChaffEntity> type, Level level) {
        super(type, level);
    }

    public ChaffEntity(Level level, double x, double y, double z, double mx, double my, double mz) {
        this(ModEntityTypes.CHAFF.get(), level);
        this.snapTo(x, y, z, 0.0F, 0.0F);
        this.setDeltaMovement(mx, my, mz);
    }

    @Override
    protected void defineSynchedData(SynchedEntityData.Builder builder) {
    }

    @Override
    public void tick() {
        Level level = this.level();
        if (this.tickCount > MAX_TICK_EXISTED) {
            this.discard();
            return;
        }
        if (!level.isClientSide() && !level.isLoaded(BlockPos.containing(this.position()))) {
            this.discard();
            return;
        }
        super.tick();
        if (!level.isClientSide()) {
            Vec3 from = this.position();
            if (WeaponWorld.clip(level, from, from.add(this.getDeltaMovement())) != null) {
                this.discard();
                return;
            }
        }
        Vec3 m = this.getDeltaMovement();
        double nx = this.getX() + m.x;
        double ny = this.getY() + m.y;
        double nz = this.getZ() + m.z;
        this.setDeltaMovement(m.x * this.airResistance, m.y + this.gravity, m.z * this.airResistance);
        if (!level.isClientSide() && (this.isInWater() || this.onGround())) {
            this.discard();
        }
        this.setPos(nx, ny, nz);
    }

    @Override
    public boolean shouldRenderAtSqrDistance(double distance) {
        double d0 = this.getBoundingBox().getSize() * 4.0;
        if (Double.isNaN(d0)) {
            d0 = 4.0;
        }
        d0 *= 64.0;
        return distance < d0 * d0;
    }

    @Override
    public boolean shouldBeSaved() {
        return false;
    }

    @Override
    protected void addAdditionalSaveData(ValueOutput output) {
    }

    @Override
    protected void readAdditionalSaveData(ValueInput input) {
    }

    @Override
    public boolean isPickable() {
        return true;
    }

    @Override
    public boolean hurtServer(ServerLevel level, DamageSource source, float damage) {
        return false;
    }

    @Override
    public boolean isPushedByFluid() {
        return false;
    }
}
