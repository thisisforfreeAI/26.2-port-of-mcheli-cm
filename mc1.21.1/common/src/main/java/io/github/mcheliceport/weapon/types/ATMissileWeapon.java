package io.github.mcheliceport.weapon.types;

import io.github.mcheliceport.content.weapon.WeaponInfo;
import io.github.mcheliceport.entity.weapon.ATMissileEntity;
import io.github.mcheliceport.weapon.WeaponParam;
import io.github.mcheliceport.weapon.WeaponWorld;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;

/**
 * Anti-tank missile: locks on to a target on the ground or in water, flying straight at it or, in
 * mode 1, attacking from above. Ported from upstream {@code MCH_WeaponATMissile}.
 */
public class ATMissileWeapon extends EntitySeekerWeapon {

    public ATMissileWeapon(Level world, Vec3 v, float yaw, float pitch, String name, WeaponInfo info) {
        super(world, v, yaw, pitch, name, info);
        this.power = 32;
        this.acceleration = 2.0F;
        this.explosionPower = 4;
        this.interval = world.isClientSide() ? 10 : 5;
        this.numMode = 2;
        this.guidanceSystem.canLockOnGround = true;
        this.guidanceSystem.canLockInWater = true;
        this.guidanceSystem.ridableOnly = info.ridableOnly;
    }

    @Override
    public String getName() {
        return super.getName() + (this.getCurrentMode() == 1 ? " [TA]" : "");
    }

    @Override
    public boolean shot(WeaponParam prm) {
        return this.world.isClientSide() ? this.handleClientShot(prm) : this.handleServerShot(prm);
    }

    private boolean handleServerShot(WeaponParam prm) {
        WeaponInfo info = this.getInfo();
        boolean isRadar = info.passiveRadar || info.activeRadar;
        Entity target = isRadar ? null : this.world.getEntity(prm.option1);
        if (!isRadar && (target == null || !target.isAlive())) {
            return false;
        }
        this.playSound(prm.entity);
        float[] rot = this.calculateShotRotation(prm, isRadar, !isRadar);
        Vec3 motion = WeaponWorld.fromPitchYaw(rot[1], rot[0]);
        ATMissileEntity missile = new ATMissileEntity(this.world, prm.posX, prm.posY, prm.posZ, motion.x, motion.y,
                motion.z, rot[0], rot[1], this.acceleration);
        missile.setName(this.name);
        missile.setParameterFromWeapon(this, prm.entity, prm.user);
        missile.guidanceType = prm.option2;
        if (target != null) {
            missile.setTargetEntity(target);
        }
        this.world.addFreshEntity(missile);
        return true;
    }

    private boolean handleClientShot(WeaponParam prm) {
        WeaponInfo info = this.getInfo();
        if (info.passiveRadar || info.activeRadar) {
            return true;
        }
        if (prm.user != null && this.guidanceSystem.lock(prm.user) && this.guidanceSystem.lastLockEntity != null) {
            this.optionParameter1 = this.guidanceSystem.lastLockEntity.getId();
            this.optionParameter2 = this.getCurrentMode();
            return true;
        }
        return false;
    }
}
