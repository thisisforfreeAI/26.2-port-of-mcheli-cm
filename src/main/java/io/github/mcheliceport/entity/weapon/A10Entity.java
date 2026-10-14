package io.github.mcheliceport.entity.weapon;

import io.github.mcheliceport.content.ContentRegistries;
import io.github.mcheliceport.content.weapon.WeaponInfo;
import io.github.mcheliceport.registry.ModEntityTypes;
import io.github.mcheliceport.util.McheliSounds;
import java.util.Locale;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

/**
 * The A-10 of a close air support call: it hangs unseen for 20 ticks while its GAU-8 fires two
 * shells a tick (40 in all, every fourth explosive), then flies off climbing and vanishes after 70
 * ticks. Ported from upstream {@code MCH_EntityA10}.
 */
public class A10Entity extends Entity {

    private static final EntityDataAccessor<String> WEAPON_NAME =
            SynchedEntityData.defineId(A10Entity.class, EntityDataSerializers.STRING);
    public static final int DESPAWN_COUNT_MAX = 70;
    private static final int RENDER_START_TICK = 20;

    private int despawnCount = 0;
    private int shotCount = 0;
    public @Nullable Entity shootingAircraft;
    public @Nullable Entity shootingEntity;
    /** 0 south, 1 west, 2 north, 3 east. */
    public int direction = 0;
    public int power = 32;
    public float acceleration = 4.0F;
    public int explosionPower = 1;
    public @Nullable WeaponInfo weaponInfo;

    public A10Entity(EntityType<? extends A10Entity> type, Level level) {
        super(type, level);
    }

    public A10Entity(Level level, double x, double y, double z) {
        this(ModEntityTypes.A10.get(), level);
        this.setPos(x, y, z);
        this.xo = this.xOld = x;
        this.yo = this.yOld = y;
        this.zo = this.zOld = z;
    }

    @Override
    protected void defineSynchedData(SynchedEntityData.Builder builder) {
        builder.define(WEAPON_NAME, "");
    }

    @Override
    protected void addAdditionalSaveData(ValueOutput output) {
        output.putString("WeaponName", this.getWeaponName());
    }

    @Override
    protected void readAdditionalSaveData(ValueInput input) {
        this.despawnCount = 200;
        String name = input.getStringOr("WeaponName", "");
        if (!name.isEmpty()) {
            this.setWeaponName(name);
        }
    }

    @Override
    public boolean shouldBeSaved() {
        return false;
    }

    public void setWeaponName(@Nullable String s) {
        if (s == null || s.isEmpty()) {
            return;
        }
        this.weaponInfo = ContentRegistries.weapon().get(s.toLowerCase(Locale.ROOT));
        if (this.weaponInfo != null && !this.level().isClientSide()) {
            this.entityData.set(WEAPON_NAME, s);
        }
    }

    public String getWeaponName() {
        return this.entityData.get(WEAPON_NAME);
    }

    @Override
    public void tick() {
        super.tick();
        if (!this.isRemoved()) {
            this.despawnCount++;
        }
        if (this.weaponInfo == null) {
            this.setWeaponName(this.getWeaponName());
            if (this.weaponInfo == null) {
                if (!this.level().isClientSide()) {
                    this.discard();
                }
                return;
            }
        }
        if (this.level().isClientSide()) {
            this.shotCount += 4;
        } else {
            this.updateServer();
        }
        this.updateMotion();
    }

    private void updateServer() {
        if (this.isRemoved()) {
            return;
        }
        if (this.despawnCount > DESPAWN_COUNT_MAX) {
            this.discard();
        } else if (this.despawnCount > 0 && this.shotCount < 40) {
            for (int i = 0; i < 2; i++) {
                this.shotGAU8(this.shotCount++);
            }
            if (this.shotCount == 38) {
                McheliSounds.play(this.level(), this.getX(), this.getY(), this.getZ(), "gau-8_snd", 150.0F, 1.0F);
            }
        }
    }

    private void updateMotion() {
        if (this.isRemoved()) {
            return;
        }
        Vec3 m = this.getDeltaMovement();
        if (this.despawnCount <= RENDER_START_TICK) {
            this.setDeltaMovement(m.x, -0.3, m.z);
        } else {
            this.setPos(this.getX() + m.x, this.getY() + m.y, this.getZ() + m.z);
            this.setDeltaMovement(m.x, m.y + 0.02, m.z);
        }
    }

    /** One GAU-8 shell, aimed ahead and down along the run with a little scatter. */
    protected void shotGAU8(int currentShot) {
        float yaw = 90.0F * this.direction;
        float pitch = 30.0F;
        double offset = currentShot * 0.6;
        double spawnX = this.getX();
        double spawnZ = this.getZ();
        double tX = this.random.nextDouble() - 0.5;
        double tY = -2.6;
        double tZ = this.random.nextDouble() - 0.5;
        switch (this.direction) {
            case 0 -> {
                tZ += 10.0;
                spawnZ += offset;
            }
            case 1 -> {
                tX -= 10.0;
                spawnX -= offset;
            }
            case 2 -> {
                tZ -= 10.0;
                spawnZ -= offset;
            }
            case 3 -> {
                tX += 10.0;
                spawnX += offset;
            }
            default -> {
            }
        }
        double dist = Math.sqrt(tX * tX + tY * tY + tZ * tZ);
        BulletEntity bullet = new BulletEntity(this.level(), spawnX, this.getY(), spawnZ, tX * 4.0 / dist,
                tY * 4.0 / dist, tZ * 4.0 / dist, yaw, pitch, this.acceleration);
        bullet.setName(this.getWeaponName());
        bullet.explosionPower = currentShot % 4 == 0 ? this.explosionPower : 0;
        bullet.setPower(this.power);
        bullet.shootingEntity = this.shootingEntity;
        bullet.shootingAircraft = this.shootingAircraft;
        this.level().addFreshEntity(bullet);
    }

    /** Drawn only once it starts its run. */
    public boolean isRender() {
        return this.despawnCount > RENDER_START_TICK;
    }

    @Override
    public boolean hurtServer(ServerLevel level, DamageSource source, float amount) {
        return false;
    }

    @Override
    public boolean fireImmune() {
        return true;
    }

    @Override
    public boolean shouldRenderAtSqrDistance(double distance) {
        double d = this.getBoundingBox().getSize() * 64.0 * 10.0;
        return distance < d * d;
    }
}
