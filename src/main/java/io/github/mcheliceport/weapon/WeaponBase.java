package io.github.mcheliceport.weapon;

import io.github.mcheliceport.content.weapon.CartridgeInfo;
import io.github.mcheliceport.content.weapon.SightType;
import io.github.mcheliceport.content.weapon.WeaponInfo;
import io.github.mcheliceport.entity.aircraft.AircraftEntity;
import io.github.mcheliceport.util.McheliMath;
import io.github.mcheliceport.util.McheliSounds;
import io.github.mcheliceport.weapon.guidance.EntityLockChecker;
import io.github.mcheliceport.weapon.guidance.WeaponGuidanceSystem;
import io.github.mcheliceport.weapon.types.BombWeapon;
import io.github.mcheliceport.weapon.types.MachineGun1Weapon;
import io.github.mcheliceport.weapon.types.MachineGun2Weapon;
import io.github.mcheliceport.weapon.types.RocketWeapon;
import io.github.mcheliceport.weapon.types.TorpedoWeapon;
import java.util.Random;
import net.minecraft.core.BlockPos;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

/**
 * One weapon mount: a weapon definition at a position on a vehicle, with the values the creator
 * copies from the definition and the per-type {@link #shot} that fires it. Both sides run the
 * same instances: the client predicts cooldowns and plays effects, the server spawns the
 * projectiles. Ported from upstream {@code MCH_WeaponBase}.
 *
 * <p>Tank and ground vehicle aiming (turret pitch limits, detached aim) comes with those vehicles
 * in M6.
 */
public abstract class WeaponBase {

    protected static final Random rand = new Random();
    public final Level world;
    public final Vec3 position;
    public final float fixRotationYaw;
    public final float fixRotationPitch;
    public final String name;
    public final WeaponInfo weaponInfo;
    public final int lockTime;
    public String displayName;
    public int power;
    public float acceleration;
    public int explosionPower;
    public int explosionPowerInWater;
    public int interval;
    public int delayedInterval;
    public int numMode;
    public int piercing;
    public int heatCount;
    public @Nullable CartridgeInfo cartridge;
    public boolean onTurret;
    public @Nullable AircraftEntity aircraft;
    public int tick;
    public int optionParameter1;
    public int optionParameter2;
    public boolean canPlaySound;
    public int airburstDist = 0;
    private int currentMode;

    protected WeaponBase(Level w, Vec3 v, float yaw, float pitch, String nm, WeaponInfo wi) {
        this.world = w;
        this.position = v;
        this.fixRotationYaw = yaw;
        this.fixRotationPitch = pitch;
        this.name = nm;
        this.weaponInfo = wi;
        this.displayName = wi != null ? wi.displayName : "";
        this.power = 0;
        this.acceleration = 0.0F;
        this.explosionPower = 0;
        this.explosionPowerInWater = 0;
        this.interval = 1;
        this.numMode = 0;
        this.lockTime = 0;
        this.heatCount = 0;
        this.cartridge = null;
        this.tick = 0;
        this.optionParameter1 = 0;
        this.optionParameter2 = 0;
        this.setCurrentMode(0);
        this.canPlaySound = true;
    }

    public WeaponInfo getInfo() {
        return this.weaponInfo;
    }

    public void setAirburstDist(int dist) {
        this.airburstDist = dist;
    }

    /** Called every tick the fire key is held before the shot (lock-on); true when it did something. */
    public boolean lock(WeaponParam prm) {
        return false;
    }

    public void onUnlock(WeaponParam prm) {
    }

    public String getName() {
        return this.displayName;
    }

    /** Fires: spawns the projectile on the server, plays recoil and options on the client. */
    public abstract boolean shot(WeaponParam prm);

    public void setLockChecker(@Nullable EntityLockChecker checker) {
    }

    public int getLockCount() {
        return 0;
    }

    public int getLockCountMax() {
        return 0;
    }

    public void setLockCountMax(int n) {
    }

    public final int getNumAmmoMax() {
        return this.getInfo().round;
    }

    public int getCurrentMode() {
        return this.getInfo() != null && this.getInfo().fixMode > 0 ? this.getInfo().fixMode : this.currentMode;
    }

    public void setCurrentMode(int mode) {
        this.currentMode = mode;
    }

    public final int getAllAmmoNum() {
        return this.getInfo().maxAmmo;
    }

    public final int getReloadCount() {
        return this.getInfo().reloadTime;
    }

    public final SightType getSightType() {
        return this.getInfo().sight;
    }

    public @Nullable WeaponGuidanceSystem getGuidanceSystem() {
        return null;
    }

    public void update(int countWait) {
        if (countWait != 0) {
            this.tick++;
        }
    }

    public boolean isCooldownCountReloadTime() {
        return false;
    }

    public void modifyCommonParameters() {
        this.modifyParameters();
    }

    public void modifyParameters() {
    }

    public boolean switchMode() {
        if (this.getInfo() != null && this.getInfo().fixMode > 0) {
            return false;
        }
        int beforeMode = this.getCurrentMode();
        if (this.numMode > 0) {
            this.setCurrentMode((this.getCurrentMode() + 1) % this.numMode);
        } else {
            this.setCurrentMode(0);
        }
        if (beforeMode != this.getCurrentMode()) {
            this.onSwitchMode();
        }
        return beforeMode != this.getCurrentMode();
    }

    public void onSwitchMode() {
    }

    /** Moves the shot to the mount's position and fires. */
    public boolean use(WeaponParam prm) {
        Vec3 v = this.getShotPos(prm.entity);
        prm.posX = prm.posX + v.x;
        prm.posY = prm.posY + v.y;
        prm.posZ = prm.posZ + v.z;
        if (this.shot(prm)) {
            this.tick = 0;
            return true;
        }
        return false;
    }

    /**
     * Upstream {@code calculateShotRotation}: the shot's yaw and pitch, from the weapon set's
     * direction or, when none was set, the user's or the vehicle's view. Returns {yaw, pitch}.
     */
    protected float[] calculateShotRotation(WeaponParam prm, boolean applyYawLimit, boolean useSeatPitch) {
        float yaw = prm.rotYaw;
        float pitch = prm.rotPitch;
        if (yaw == 0.0F && pitch == 0.0F) {
            Entity from = this.getInfo().enableOffAxis ? prm.user : prm.entity;
            yaw = from.getYRot() + this.fixRotationYaw;
            pitch = from.getXRot() + this.fixRotationPitch;
        }
        return new float[] {Mth.wrapDegrees(yaw), pitch};
    }

    /** Upstream {@code getShotPos}: the mount's offset turned with the vehicle (or its turret). */
    public Vec3 getShotPos(@Nullable Entity entity) {
        if (entity instanceof AircraftEntity ac && this.onTurret) {
            return ac.calcOnTurretPos(this.position);
        }
        if (entity == null) {
            return this.position;
        }
        float roll = entity instanceof AircraftEntity ac ? ac.getRoll() : 0.0F;
        return McheliMath.rotVec3(this.position, -entity.getYRot(), -entity.getXRot(), -roll);
    }

    public void playSound(Entity e) {
        this.playSound(e, this.getInfo().fireSound);
    }

    /** The fire sound for everyone near the vehicle, with the definition's random pitch. Server only. */
    public void playSound(Entity e, String snd) {
        if (!e.level().isClientSide() && this.canPlaySound && this.getInfo() != null) {
            float prnd = this.getInfo().soundPitchRandom;
            float pitch = this.getInfo().soundPitch * (1.0F - prnd) + rand.nextFloat() * prnd;
            McheliSounds.play(this.world, e.getX(), e.getY(), e.getZ(), snd, this.getInfo().soundVolume, pitch);
        }
    }

    /** The fire sound only the local player hears. Client only. */
    public void playSoundClient(Entity e, float volume, float pitch) {
        if (e.level().isClientSide() && this.getInfo() != null) {
            WeaponClientHooks.playSound(this.getInfo().fireSound, volume, pitch);
        }
    }

    /** Upstream {@code getLandInDistance}: horizontal distance to the predicted impact, or -1. */
    public double getLandInDistance(WeaponParam prm) {
        Vec3 impact = this.getImpactPos(prm);
        if (impact == null) {
            return -1.0;
        }
        double dx = impact.x - prm.posX;
        double dz = impact.z - prm.posZ;
        return Math.sqrt(dx * dx + dz * dz);
    }

    /**
     * Upstream {@code getImpactPos}: steps the projectile's flight (gravity in air and water, water
     * drag) for up to 512 ticks or the time fuse, and returns where it meets a block.
     */
    public @Nullable Vec3 getImpactPos(WeaponParam prm) {
        if (this.weaponInfo == null) {
            return null;
        }
        boolean gun = this instanceof MachineGun1Weapon || this instanceof MachineGun2Weapon;
        double accelFactor = 1.0;
        if ((gun || this instanceof RocketWeapon) && this.weaponInfo.acceleration > 4.0F) {
            accelFactor = this.weaponInfo.acceleration / 4.0F;
        }
        Vec3 look = McheliMath.rotVec3(0.0, 0.0, 1.0, -prm.rotYaw, -prm.rotPitch, -prm.rotRoll).normalize();
        Vec3 entityMotion = prm.entity != null ? prm.entity.getDeltaMovement() : Vec3.ZERO;
        Vec3 motion;
        if (this instanceof BombWeapon) {
            motion = entityMotion;
        } else if (this instanceof TorpedoWeapon) {
            motion = new Vec3(look.x * this.weaponInfo.acceleration + entityMotion.x,
                    look.y * this.weaponInfo.acceleration + entityMotion.y,
                    look.z * this.weaponInfo.acceleration + entityMotion.z);
        } else {
            double accel = Math.min(this.acceleration, 3.9F);
            motion = new Vec3(look.x * accel, look.y * accel, look.z * accel);
        }
        double spx = prm.posX;
        double spy = prm.posY;
        double spz = prm.posZ;
        if (gun) {
            spx += motion.x * 0.5;
            spy += motion.y * 0.5;
            spz += motion.z * 0.5;
        }
        double mx = motion.x;
        double my = motion.y;
        double mz = motion.z;
        int maxSteps = this.weaponInfo.timeFuse > 0 ? Math.min(512, this.weaponInfo.timeFuse) : 512;
        for (int i = 0; i < maxSteps; i++) {
            Vec3 start = new Vec3(spx, spy, spz);
            BlockPos pos = BlockPos.containing(spx, spy, spz);
            if (this.world.isLoaded(pos)) {
                if (!this.world.getBlockState(pos).is(Blocks.WATER)) {
                    my += this.weaponInfo.gravity;
                } else {
                    my += this.weaponInfo.gravityInWater;
                    if (this.weaponInfo.velocityInWater > 0.0F) {
                        mx *= this.weaponInfo.velocityInWater;
                        my *= this.weaponInfo.velocityInWater;
                        mz *= this.weaponInfo.velocityInWater;
                    }
                }
            } else {
                my += this.weaponInfo.gravity;
            }
            Vec3 end = start.add(mx * accelFactor, my * accelFactor, mz * accelFactor);
            BlockHitResult hit = WeaponWorld.rayTrace(this.world, start, end, false, true);
            if (hit != null) {
                return hit.getLocation();
            }
            spx = end.x;
            spy = end.y;
            spz = end.z;
            if (spy < prm.posY - 100) {
                return null;
            }
        }
        return null;
    }
}
