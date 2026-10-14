package io.github.mcheliceport.entity.throwable;

import io.github.mcheliceport.content.ContentRegistries;
import io.github.mcheliceport.content.throwable.ThrowableInfo;
import io.github.mcheliceport.effect.McheliEffects;
import io.github.mcheliceport.effect.ParticleParam;
import io.github.mcheliceport.registry.ModEntityTypes;
import io.github.mcheliceport.weapon.WeaponWorld;
import io.github.mcheliceport.weapon.explosion.McheliExplosion;
import net.minecraft.core.Direction;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.Mth;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

/**
 * A thrown grenade or smoke grenade: it flies with its definition's gravity, bounces off blocks,
 * and when its {@code TimeFuse} runs out either explodes or, without an explosion, pours out
 * coloured smoke until {@code AliveTime}. Ported from upstream {@code MCH_EntityThrowable} and the
 * 1.12 {@code EntityThrowable} it extended.
 *
 * <p>Upstream's base class also traced entities, but an entity hit carries no side and so never
 * changed the flight; only blocks are traced here. Like the other projectiles, a throwable is not
 * saved with the world.
 */
public class ThrowableEntity extends Entity {

    private static final EntityDataAccessor<String> INFO_NAME =
            SynchedEntityData.defineId(ThrowableEntity.class, EntityDataSerializers.STRING);

    private double boundPosX;
    private double boundPosY;
    private double boundPosZ;
    private @Nullable BlockHitResult lastOnImpact;
    private int noInfoCount;
    private int countOnUpdate;
    private @Nullable ThrowableInfo throwableInfo;

    public ThrowableEntity(EntityType<? extends ThrowableEntity> type, Level level) {
        super(type, level);
    }

    /** Upstream {@code MCH_EntityThrowable(world, x, y, z)}. */
    public ThrowableEntity(Level level, double x, double y, double z) {
        this(ModEntityTypes.THROWABLE.get(), level);
        this.snapTo(x, y, z, 0.0F, 0.0F);
    }

    /** Upstream {@code EntityThrowable(world, thrower)}: at the thrower's eye, less 0.1. */
    public ThrowableEntity(Level level, Entity thrower) {
        this(level, thrower.getX(), thrower.getEyeY() - 0.1, thrower.getZ());
    }

    @Override
    protected void defineSynchedData(SynchedEntityData.Builder builder) {
        builder.define(INFO_NAME, "");
    }

    /**
     * Upstream {@code shoot(thrower, pitch, yaw, pitchOffset, velocity, inaccuracy)}: thrown along
     * the look at {@code velocity}, without the thrower's own motion.
     */
    public void shootFromRotation(float pitch, float yaw, float pitchOffset, float velocity, float inaccuracy) {
        float f = 0.4F;
        double mx = -Mth.sin(yaw * Mth.DEG_TO_RAD) * Mth.cos(pitch * Mth.DEG_TO_RAD) * f;
        double mz = Mth.cos(yaw * Mth.DEG_TO_RAD) * Mth.cos(pitch * Mth.DEG_TO_RAD) * f;
        double my = -Mth.sin((pitch + pitchOffset) * Mth.DEG_TO_RAD) * f;
        this.shoot(mx, my, mz, velocity, inaccuracy);
    }

    /** 1.12 {@code EntityThrowable#shoot(x, y, z, velocity, inaccuracy)}. */
    public void shoot(double x, double y, double z, float velocity, float inaccuracy) {
        double len = Math.sqrt(x * x + y * y + z * z);
        x = x / len + this.random.nextGaussian() * 0.0075 * inaccuracy;
        y = y / len + this.random.nextGaussian() * 0.0075 * inaccuracy;
        z = z / len + this.random.nextGaussian() * 0.0075 * inaccuracy;
        x *= velocity;
        y *= velocity;
        z *= velocity;
        this.setDeltaMovement(x, y, z);
        double horizontal = Math.sqrt(x * x + z * z);
        this.setYRot((float) (Mth.atan2(x, z) * Mth.RAD_TO_DEG));
        this.setXRot((float) (Mth.atan2(y, horizontal) * Mth.RAD_TO_DEG));
        this.yRotO = this.getYRot();
        this.xRotO = this.getXRot();
    }

    @Override
    public void tick() {
        Level level = this.level();
        this.boundPosX = this.getX();
        this.boundPosY = this.getY();
        this.boundPosZ = this.getZ();
        ThrowableInfo info = this.getInfo();
        if (info != null) {
            Vec3 m = this.getDeltaMovement();
            boolean water = WeaponWorld.isBlockWater(level, (int) (this.getX() + 0.5), (int) this.getY(),
                    (int) (this.getZ() + 0.5));
            this.setDeltaMovement(m.x, m.y + (water ? info.gravityInWater : info.gravity), m.z);
        }
        super.tick();
        this.throwableTick();
        if (this.lastOnImpact != null) {
            this.boundBullet(this.lastOnImpact);
            Vec3 m = this.getDeltaMovement();
            this.setPos(this.boundPosX + m.x, this.boundPosY + m.y, this.boundPosZ + m.z);
            this.lastOnImpact = null;
        }
        this.countOnUpdate++;
        if (this.countOnUpdate >= 2147483632) {
            this.discard();
            return;
        }
        if (info == null) {
            String s = this.entityData.get(INFO_NAME);
            if (!s.isEmpty()) {
                this.setInfo(ContentRegistries.get().throwable().get(s));
            }
            info = this.getInfo();
            if (info == null) {
                if (++this.noInfoCount > 10) {
                    this.discard();
                }
                return;
            }
        }
        if (this.isRemoved()) {
            return;
        }
        if (!level.isClientSide()) {
            if (this.countOnUpdate == info.timeFuse && info.explosion > 0) {
                this.explode(info);
                this.discard();
                return;
            }
            if (this.countOnUpdate >= info.aliveTime) {
                this.discard();
            }
        } else if (this.countOnUpdate >= info.timeFuse && info.explosion <= 0) {
            for (int i = 0; i < info.smokeNum; i++) {
                float r = info.smokeColor.r * 0.9F + this.random.nextFloat() * 0.1F;
                float g = info.smokeColor.g * 0.9F + this.random.nextFloat() * 0.1F;
                float b = info.smokeColor.b * 0.9F + this.random.nextFloat() * 0.1F;
                if (info.smokeColor.r == info.smokeColor.g) {
                    g = r;
                }
                if (info.smokeColor.r == info.smokeColor.b) {
                    b = r;
                }
                if (info.smokeColor.g == info.smokeColor.b) {
                    b = g;
                }
                this.spawnSmoke(4, info.smokeSize + this.random.nextFloat() * info.smokeSize / 3.0F, r, g, b,
                        info.smokeVelocityHorizontal * (this.random.nextFloat() - 0.5F),
                        info.smokeVelocityVertical * this.random.nextFloat(),
                        info.smokeVelocityHorizontal * (this.random.nextFloat() - 0.5F));
            }
        }
    }

    /**
     * 1.12 {@code EntityThrowable#onUpdate}'s flight: trace blocks along the motion (an impact is
     * kept for {@link #boundBullet}), move, turn towards the motion, then slow by air or water.
     */
    private void throwableTick() {
        Level level = this.level();
        Vec3 from = this.position();
        Vec3 m = this.getDeltaMovement();
        BlockHitResult hit = WeaponWorld.clip(level, from, from.add(m));
        if (hit != null && this.getInfo() != null) {
            this.lastOnImpact = hit;
        }
        this.setPos(this.getX() + m.x, this.getY() + m.y, this.getZ() + m.z);
        double horizontal = Math.sqrt(m.x * m.x + m.z * m.z);
        float yaw = (float) (Mth.atan2(m.x, m.z) * Mth.RAD_TO_DEG);
        float pitch = (float) (Mth.atan2(m.y, horizontal) * Mth.RAD_TO_DEG);
        float prevPitch = this.xRotO;
        float prevYaw = this.yRotO;
        while (pitch - prevPitch < -180.0F) {
            prevPitch -= 360.0F;
        }
        while (pitch - prevPitch >= 180.0F) {
            prevPitch += 360.0F;
        }
        while (yaw - prevYaw < -180.0F) {
            prevYaw -= 360.0F;
        }
        while (yaw - prevYaw >= 180.0F) {
            prevYaw += 360.0F;
        }
        this.xRotO = prevPitch;
        this.yRotO = prevYaw;
        this.setXRot(prevPitch + (pitch - prevPitch) * 0.2F);
        this.setYRot(prevYaw + (yaw - prevYaw) * 0.2F);
        float drag = 0.99F;
        if (this.isInWater()) {
            for (int i = 0; i < 4; i++) {
                level.addParticle(ParticleTypes.BUBBLE, this.getX() - m.x * 0.25, this.getY() - m.y * 0.25,
                        this.getZ() - m.z * 0.25, m.x, m.y, m.z);
            }
            drag = 0.8F;
        }
        this.setDeltaMovement(m.scale(drag));
    }

    /** Upstream {@code boundBullet}: bounce off the face hit, by {@code Bound}; floors also slow it. */
    private void boundBullet(BlockHitResult m) {
        ThrowableInfo info = this.getInfo();
        if (info == null) {
            return;
        }
        float bound = info.bound;
        Vec3 v = this.getDeltaMovement();
        double mx = v.x;
        double my = v.y;
        double mz = v.z;
        Direction side = m.getDirection();
        switch (side) {
            case DOWN, UP -> {
                mx *= 0.9F;
                mz *= 0.9F;
                this.boundPosY = m.getLocation().y;
                if (side == Direction.DOWN && my > 0.0 || side == Direction.UP && my < 0.0) {
                    my = -my * bound;
                } else {
                    my = 0.0;
                }
            }
            case NORTH -> {
                if (mz > 0.0) {
                    mz = -mz * bound;
                }
            }
            case SOUTH -> {
                if (mz < 0.0) {
                    mz = -mz * bound;
                }
            }
            case WEST -> {
                if (mx > 0.0) {
                    mx = -mx * bound;
                }
            }
            case EAST -> {
                if (mx < 0.0) {
                    mx = -mx * bound;
                }
            }
        }
        this.setDeltaMovement(mx, my, mz);
    }

    /** Upstream's explosion at the fuse, with the new-style radii when {@code IsNewExplosionBreak} is set. */
    private void explode(ThrowableInfo info) {
        float size = info.explosion;
        float sizeBlock = info.explosion;
        float blockPower = info.explosion;
        if (info.isNewExplosionBreak) {
            size = info.explosionRadius > 0.0F ? info.explosionRadius : info.explosion;
            sizeBlock = size;
            blockPower = info.explosionBlock >= 0 ? info.explosionBlock : info.explosion;
        }
        float damageRadius = info.isNewExplosionBreak && info.explosionDamageRadius > 0.0F
                ? info.explosionDamageRadius : size;
        McheliExplosion.newExplosion(this.level(), new McheliExplosion.Params(null, null, this.getX(), this.getY(),
                this.getZ(), size, sizeBlock, info.explosion, blockPower, damageRadius, true, false, true, 0, null));
    }

    /** Upstream {@code spawnParticle("explode", ...)}: McHeli smoke along the last tick's path. */
    private void spawnSmoke(int num, float size, float r, float g, float b, float mx, float my, float mz) {
        double x = (this.getX() - this.xo) / num;
        double y = (this.getY() - this.yo) / num;
        double z = (this.getZ() - this.zo) / num;
        for (int i = 0; i < num; i++) {
            ParticleParam prm = new ParticleParam("smoke", this.xo + x * i, 1.0 + this.yo + y * i, this.zo + z * i);
            prm.setMotion(mx, my, mz);
            prm.size = size;
            prm.setColor(1.0F, r, g, b);
            prm.isEffectWind = true;
            prm.toWhite = true;
            McheliEffects.spawnParticle(this.level(), prm);
        }
    }

    public @Nullable ThrowableInfo getInfo() {
        return this.throwableInfo;
    }

    public void setInfo(@Nullable ThrowableInfo info) {
        this.throwableInfo = info;
        if (info != null && !this.level().isClientSide()) {
            this.entityData.set(INFO_NAME, info.name);
        }
    }

    /** The definition's name, synced to clients. */
    public String getInfoName() {
        return this.entityData.get(INFO_NAME);
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
    public boolean hurtServer(ServerLevel level, DamageSource source, float damage) {
        return false;
    }
}
