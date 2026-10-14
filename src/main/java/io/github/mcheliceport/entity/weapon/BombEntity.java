package io.github.mcheliceport.entity.weapon;

import io.github.mcheliceport.content.weapon.MineFuze;
import io.github.mcheliceport.content.weapon.WeaponInfo;
import io.github.mcheliceport.entity.aircraft.AircraftEntity;
import io.github.mcheliceport.net.ClientSoundPayload;
import io.github.mcheliceport.registry.ModEntityTypes;
import io.github.mcheliceport.registry.ModItems;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

/**
 * A free-fall bomb (air drag, water drag, an optional proximity fuse against living things,
 * cluster bomblets), or a mine: it settles where it lands, arms after {@code MineArmDelay} ticks
 * and goes off when a player (not creative, not sneaking unless within a block) or a vehicle comes
 * within {@code MineRange}. The mine defuser removes it. Mines are saved with the world. Ported
 * from upstream {@code MCH_EntityBomb}.
 */
public class BombEntity extends BaseBulletEntity {

    private static final EntityDataAccessor<Byte> MINE_FLAGS =
            SynchedEntityData.defineId(BombEntity.class, EntityDataSerializers.BYTE);
    private static final byte LANDED_BIT = 1;
    private static final double MINE_SNEAK_TRIGGER_DIST_SQ = 1.0;

    private boolean mineArmed;
    private int mineArmTimer;
    private int mineTick;

    public BombEntity(EntityType<? extends BombEntity> type, Level level) {
        super(type, level);
    }

    public BombEntity(Level level, double x, double y, double z, double mx, double my, double mz, float yaw,
            float pitch, double acceleration) {
        super(ModEntityTypes.BOMB.get(), level, x, y, z, mx, my, mz, yaw, pitch, acceleration);
    }

    @Override
    protected void defineSynchedData(SynchedEntityData.Builder builder) {
        super.defineSynchedData(builder);
        builder.define(MINE_FLAGS, (byte) 0);
    }

    @Override
    public void tick() {
        WeaponInfo info = this.getInfo();
        if (info != null && info.isMine()) {
            if (this.isMineLanded()) {
                this.onUpdateLandedMine(info);
            } else {
                super.tick();
            }
            return;
        }
        super.tick();
        info = this.getInfo();
        if (!this.level().isClientSide() && info != null && !this.isRemoved()) {
            Vec3 m = this.getDeltaMovement().multiply(0.999, 1.0, 0.999);
            if (this.isInWater()) {
                m = m.scale(info.velocityInWater);
            }
            this.setDeltaMovement(m);
            float dist = info.proximityFuseDist;
            if (dist > 0.1F && this.getCountOnUpdate() % 10 == 0) {
                for (Entity entity : this.level().getEntities(this, this.getBoundingBox().inflate(dist))) {
                    if (entity instanceof LivingEntity && this.canBeCollidedEntity(entity)) {
                        this.onImpact(new BlockHitResult(this.position(), Direction.DOWN,
                                BlockPos.containing(this.getX() + 0.5, this.getY() + 0.5, this.getZ() + 0.5), false),
                                1.0F);
                        break;
                    }
                }
            }
        }
        this.onUpdateBomblet();
    }

    public boolean isMineLanded() {
        return (this.entityData.get(MINE_FLAGS) & LANDED_BIT) != 0;
    }

    private void setMineFlag(byte bit, boolean on) {
        byte cur = this.entityData.get(MINE_FLAGS);
        byte next = (byte) (on ? cur | bit : cur & ~bit);
        if (next != cur) {
            this.entityData.set(MINE_FLAGS, next);
        }
    }

    private void landMine(HitResult m) {
        Vec3 at = m.getLocation();
        this.setPos(at.x, at.y, at.z);
        this.setDeltaMovement(Vec3.ZERO);
        this.mineArmed = false;
        this.mineArmTimer = 0;
        this.mineTick = 0;
        this.setMineFlag(LANDED_BIT, true);
    }

    private void onUpdateLandedMine(WeaponInfo info) {
        this.setDeltaMovement(Vec3.ZERO);
        if (this.level().isClientSide()) {
            return;
        }
        if (!this.mineArmed) {
            if (++this.mineArmTimer >= info.mineArmDelay) {
                this.mineArmed = true;
            }
            return;
        }
        if (++this.mineTick % 5 == 0) {
            this.scanAndTrigger(info);
        }
    }

    private void scanAndTrigger(WeaponInfo info) {
        double range = info.mineRange;
        if (range <= 0.0) {
            return;
        }
        for (Entity e : this.level().getEntities(this, this.getBoundingBox().inflate(range))) {
            if (!this.triggersMine(e, info)) {
                continue;
            }
            double cx = e.getX() - this.getX();
            double cy = e.getY() + e.getBbHeight() * 0.5 - (this.getY() + this.getBbHeight() * 0.5);
            double cz = e.getZ() - this.getZ();
            double distSq = cx * cx + cy * cy + cz * cz;
            if (distSq > range * range) {
                continue;
            }
            if (info.mineFuze == MineFuze.PROXIMITY_PLAYER && e instanceof Player p && p.isShiftKeyDown()
                    && distSq > MINE_SNEAK_TRIGGER_DIST_SQ) {
                continue;
            }
            this.detonateMine(info);
            return;
        }
    }

    private boolean triggersMine(Entity e, WeaponInfo info) {
        if (e == null || !e.isAlive()) {
            return false;
        }
        return switch (info.mineFuze) {
            case PROXIMITY_PLAYER -> e instanceof Player p && !p.isSpectator() && !p.isCreative();
            case PROXIMITY_VEHICLE -> e instanceof AircraftEntity;
            default -> false;
        };
    }

    private void detonateMine(WeaponInfo info) {
        if (this.isInWater() && this.explosionPowerInWater > 0) {
            this.newExplosion(this.getX(), this.getY(), this.getZ(), this.explosionPowerInWater,
                    this.explosionPowerInWater, true);
        } else if (this.explosionPower > 0) {
            float size = this.explosionPower;
            float sizeBlock = this.explosionPower;
            float blockPower = info.explosionBlock;
            if (info.isNewExplosionBreak) {
                size = info.explosionRadius > 0.0F ? info.explosionRadius : this.explosionPower;
                sizeBlock = size;
                blockPower = info.explosionBlock >= 0 ? info.explosionBlock : this.explosionPower;
            }
            this.newExplosion(this.getX(), this.getY(), this.getZ(), size, sizeBlock, this.explosionPower, blockPower,
                    false);
        } else {
            this.playExplosionSound();
        }
        ClientSoundPayload.send(this.level(), this.getX(), this.getY(), this.getZ(), info.hitSoundRange, info.hitSound,
                true);
        this.discard();
    }

    @Override
    protected void onImpact(HitResult m, float damageFactor) {
        WeaponInfo info = this.getInfo();
        if (!this.level().isClientSide() && info != null && info.isMine() && !this.isMineLanded()) {
            if (m.getType() == HitResult.Type.BLOCK) {
                this.landMine(m);
            }
            return;
        }
        super.onImpact(m, damageFactor);
    }

    @Override
    public InteractionResult interact(Player player, InteractionHand hand, Vec3 location) {
        WeaponInfo info = this.getInfo();
        if (info != null && info.isMine()) {
            ItemStack held = player.getItemInHand(hand);
            if (held.is(ModItems.MINE_DEFUSER.get())) {
                if (!this.level().isClientSide()) {
                    this.level().playSound(null, this.getX(), this.getY(), this.getZ(), SoundEvents.LEVER_CLICK,
                            SoundSource.BLOCKS, 0.6F, 1.6F);
                    this.discard();
                }
                player.swing(hand);
                return InteractionResult.SUCCESS;
            }
        }
        return super.interact(player, hand, location);
    }

    /** Mines are kept with the world; other bombs are not saved. */
    @Override
    public boolean shouldBeSaved() {
        WeaponInfo info = this.getInfo();
        return info != null && info.isMine() && !this.isRemoved() && !this.isPassenger();
    }

    @Override
    protected void addAdditionalSaveData(ValueOutput output) {
        super.addAdditionalSaveData(output);
        WeaponInfo info = this.getInfo();
        if (info != null && info.isMine()) {
            output.putBoolean("MchMine", true);
            output.putBoolean("MineLanded", this.isMineLanded());
            output.putBoolean("MineArmed", this.mineArmed);
            output.putInt("MineArmTimer", this.mineArmTimer);
            output.putInt("ExplosionPower", this.explosionPower);
            output.putInt("ExplosionPowerInWater", this.explosionPowerInWater);
            output.putInt("Power", this.getPower());
        }
    }

    @Override
    protected void readAdditionalSaveData(ValueInput input) {
        if (input.getBooleanOr("MchMine", false)) {
            this.setName(input.getStringOr("WeaponName", ""));
            this.mineArmed = input.getBooleanOr("MineArmed", false);
            this.mineArmTimer = input.getIntOr("MineArmTimer", 0);
            this.setMineFlag(LANDED_BIT, input.getBooleanOr("MineLanded", false));
            WeaponInfo info = this.getInfo();
            this.explosionPower = input.getIntOr("ExplosionPower", info != null ? info.explosion : 0);
            this.explosionPowerInWater = input.getIntOr("ExplosionPowerInWater",
                    info != null ? info.explosionInWater : 0);
            this.setPower(input.getIntOr("Power", info != null ? info.power : 0));
            this.setDeltaMovement(Vec3.ZERO);
            return;
        }
        super.readAdditionalSaveData(input);
    }

    @Override
    public void sprinkleBomblet() {
        if (!this.level().isClientSide()) {
            Vec3 m = this.getDeltaMovement();
            BombEntity e = new BombEntity(this.level(), this.getX(), this.getY(), this.getZ(), m.x, m.y, m.z,
                    this.random.nextInt(360), 0.0F, this.acceleration);
            e.setParameterFromWeapon(this, this.shootingAircraft, this.shootingEntity);
            e.setName(this.getInfoName());
            float spread = this.getInfo().bombletDiff;
            e.setDeltaMovement(m.x + (this.random.nextFloat() - 0.5F) * spread,
                    m.y / 2.0 + (this.random.nextFloat() - 0.5F) * spread / 2.0F,
                    m.z + (this.random.nextFloat() - 0.5F) * spread);
            e.setBomblet();
            this.level().addFreshEntity(e);
        }
    }

    @Override
    public String getDefaultBulletModelName() {
        return "bomb";
    }
}
