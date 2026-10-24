package io.github.mcheliceport.entity.flare;

import io.github.mcheliceport.effect.McheliEffects;
import io.github.mcheliceport.effect.ParticleParam;
import io.github.mcheliceport.registry.ModEntityTypes;
import io.github.mcheliceport.weapon.WeaponWorld;
import net.minecraft.core.BlockPos;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.entity.IEntityWithComplexSpawn;

/**
 * A decoy flare: falls slowly with a smoke trail and burns out on the ground, in water, against a
 * block or after 300 ticks. Flares with a fuse burst into a grey smoke cloud when it runs out.
 * Heat seekers will not lock on to a vehicle dispensing them. Ported from upstream
 * {@code MCH_EntityFlare}; the fuse travels with the spawn data, as upstream.
 */
public class FlareEntity extends net.minecraft.world.entity.Entity implements IEntityWithComplexSpawn {

    public double gravity = -0.013;
    public double airResistance = 0.992;
    public float size;
    public int fuseCount;
    /**
     * Debris a client spawned for an explosion, with no server copy. Not in upstream, where such a
     * flare never went out on the client; it now goes out where a server flare would.
     */
    public boolean clientOnly;

    public FlareEntity(EntityType<? extends FlareEntity> type, Level level) {
        super(type, level);
        this.size = 6.0F;
        this.fuseCount = 0;
    }

    public FlareEntity(Level level, double x, double y, double z, double mx, double my, double mz, float size,
            int fuseCount) {
        this(ModEntityTypes.FLARE.get(), level);
        this.snapTo(x, y, z, 0.0F, 0.0F);
        this.setDeltaMovement(mx, my, mz);
        this.size = size;
        this.fuseCount = fuseCount;
    }

    @Override
    protected void defineSynchedData(SynchedEntityData.Builder builder) {
    }

    @Override
    public boolean shouldRenderAtSqrDistance(double distance) {
        double d1 = this.getBoundingBox().getSize() * 4.0 * 64.0;
        return distance < d1 * d1;
    }

    @Override
    public void remove(RemovalReason reason) {
        super.remove(reason);
        this.burst();
    }

    @Override
    public void onClientRemoval() {
        super.onClientRemoval();
        this.burst();
    }

    /** A fused flare's smoke cloud when it goes out, once. Client only. */
    private void burst() {
        if (this.fuseCount > 0 && this.level().isClientSide()) {
            this.fuseCount = 0;
            for (int i = 0; i < 20; i++) {
                double x = (this.random.nextDouble() - 0.5) * 10.0;
                double y = (this.random.nextDouble() - 0.5) * 10.0;
                double z = (this.random.nextDouble() - 0.5) * 10.0;
                ParticleParam prm = new ParticleParam("smoke", this.getX() + x, this.getY() + y, this.getZ() + z);
                prm.age = 200 + this.random.nextInt(100);
                prm.size = 20 + this.random.nextInt(25);
                prm.motionX = (this.random.nextDouble() - 0.5) * 0.45;
                prm.motionY = (this.random.nextDouble() - 0.5) * 0.01;
                prm.motionZ = (this.random.nextDouble() - 0.5) * 0.45;
                prm.a = this.random.nextFloat() * 0.1F + 0.85F;
                prm.b = this.random.nextFloat() * 0.2F + 0.5F;
                prm.g = prm.b + 0.05F;
                prm.r = prm.b + 0.1F;
                McheliEffects.spawnParticle(this.level(), prm);
            }
        }
    }

    @Override
    public void writeSpawnData(RegistryFriendlyByteBuf buffer) {
        buffer.writeByte(this.fuseCount);
    }

    @Override
    public void readSpawnData(RegistryFriendlyByteBuf buffer) {
        this.fuseCount = buffer.readByte();
    }

    @Override
    public void tick() {
        Level level = this.level();
        if (this.fuseCount > 0 && this.tickCount >= this.fuseCount) {
            this.discard();
            return;
        }
        boolean authority = !level.isClientSide() || this.clientOnly;
        if (authority && !level.isLoaded(BlockPos.containing(this.position()))) {
            this.discard();
            return;
        }
        if (this.tickCount > 300 && authority) {
            this.discard();
            return;
        }
        super.tick();
        if (authority) {
            this.onUpdateCollided();
            if (this.isRemoved()) {
                return;
            }
        }
        Vec3 m = this.getDeltaMovement();
        double nx = this.getX() + m.x;
        double ny = this.getY() + m.y;
        double nz = this.getZ() + m.z;
        if (level.isClientSide()) {
            double x = (nx - this.xo) / 2.0;
            double y = (ny - this.yo) / 2.0;
            double z = (nz - this.zo) / 2.0;
            for (int i = 0; i < 2; i++) {
                ParticleParam prm = new ParticleParam("smoke", this.xo + x * i, this.yo + y * i, this.zo + z * i);
                prm.size = 6.0F + this.random.nextFloat();
                if (this.size < 5.0F) {
                    prm.a = (float) (prm.a * 0.75);
                }
                if (this.fuseCount > 0) {
                    prm.a = this.random.nextFloat() * 0.1F + 0.85F;
                    prm.b = this.random.nextFloat() * 0.1F + 0.5F;
                    prm.g = prm.b + 0.05F;
                    prm.r = prm.b + 0.1F;
                }
                McheliEffects.spawnParticle(level, prm);
            }
        }
        this.setDeltaMovement(m.x * this.airResistance, m.y + this.gravity, m.z * this.airResistance);
        if ((this.isInWater() || this.onGround()) && authority) {
            this.discard();
        }
        this.setPos(nx, ny, nz);
    }

    protected void onUpdateCollided() {
        Vec3 from = this.position();
        if (WeaponWorld.clip(this.level(), from, from.add(this.getDeltaMovement())) != null) {
            this.discard();
        }
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
        this.discard();
    }

    @Override
    public boolean isPickable() {
        return true;
    }

    @Override
    public float getPickRadius() {
        return 1.0F;
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
