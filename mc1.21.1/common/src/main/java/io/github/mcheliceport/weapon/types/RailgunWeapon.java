package io.github.mcheliceport.weapon.types;

import io.github.mcheliceport.content.weapon.WeaponInfo;
import io.github.mcheliceport.entity.weapon.BulletEntity;
import io.github.mcheliceport.weapon.WeaponBase;
import io.github.mcheliceport.weapon.WeaponClientHooks;
import io.github.mcheliceport.weapon.WeaponParam;
import io.github.mcheliceport.weapon.WeaponWorld;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

/**
 * Railgun: one fast bullet. On the client the shot charges for the definition's {@code LockTime}
 * ticks with the charge sound before the recoil plays; the server fires at once (upstream's
 * behaviour). Ported from upstream {@code MCH_WeaponRailgun}.
 */
public class RailgunWeapon extends WeaponBase {

    private int lockCount;
    private int prevLockCount;
    private @Nullable Object chargeSound;

    public RailgunWeapon(Level w, Vec3 v, float yaw, float pitch, String nm, WeaponInfo wi) {
        super(w, v, yaw, pitch, nm, wi);
        this.power = 32;
        this.acceleration = 10.0F;
        this.explosionPower = 2;
        this.interval = 0;
    }

    @Override
    public void update(int countWait) {
        super.update(countWait);
        if (this.world.isClientSide()) {
            if (this.lockCount != this.prevLockCount) {
                this.prevLockCount = this.lockCount;
            } else {
                this.lockCount = this.prevLockCount = 0;
                this.stopChargeSound();
            }
        }
    }

    private void stopChargeSound() {
        if (this.chargeSound != null) {
            WeaponClientHooks.stopSound(this.chargeSound);
            this.chargeSound = null;
        }
    }

    private void playChargeSound(double x, double y, double z) {
        if (this.chargeSound == null && this.getInfo() != null && this.getInfo().railgunSound != null) {
            this.chargeSound = WeaponClientHooks.playChargeSound(this.getInfo().railgunSound, x, y, z);
        }
    }

    @Override
    public boolean shot(WeaponParam prm) {
        if (!this.world.isClientSide()) {
            return this.railGunShot(prm);
        }
        int lockTime = this.weaponInfo.lockTime;
        if (lockTime <= 0) {
            return this.railGunShot(prm);
        }
        if (this.lockCount <= lockTime) {
            if (this.lockCount == 1 && prm.user != null) {
                this.playChargeSound(prm.user.getX(), prm.user.getY(), prm.user.getZ());
            }
            this.lockCount++;
            if (this.lockCount >= lockTime) {
                this.lockCount = 0;
                return this.railGunShot(prm);
            }
        }
        return false;
    }

    private boolean railGunShot(WeaponParam prm) {
        float[] rot = this.calculateShotRotation(prm, true, false);
        Vec3 motion = WeaponWorld.fromPitchYaw(rot[1], rot[0]);
        if (!this.world.isClientSide()) {
            BulletEntity e = new BulletEntity(this.world, prm.posX, prm.posY, prm.posZ, motion.x, motion.y, motion.z,
                    rot[0], rot[1], this.acceleration);
            e.setName(this.name);
            e.setParameterFromWeapon(this, prm.entity, prm.user);
            e.setAirburstDist(this.airburstDist);
            e.advanceHalfStep();
            this.world.addFreshEntity(e);
            this.playSound(prm.entity);
        } else {
            WeaponInfo info = this.getInfo();
            WeaponClientHooks.applyRecoil(info.getRecoilPitch(), info.getRecoilYaw(), info.recoilRecoverFactor);
        }
        return true;
    }

    /** The charge progress the HUD shows, 0 to 1. */
    public float getRailgunTime() {
        return this.weaponInfo.lockTime > 0 ? (float) this.lockCount / this.weaponInfo.lockTime : 1.0F;
    }
}
