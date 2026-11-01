package io.github.mcheliceport.weapon.types;

import io.github.mcheliceport.content.weapon.WeaponInfo;
import io.github.mcheliceport.entity.weapon.BulletEntity;
import io.github.mcheliceport.weapon.WeaponBase;
import io.github.mcheliceport.weapon.WeaponClientHooks;
import io.github.mcheliceport.weapon.WeaponParam;
import io.github.mcheliceport.weapon.WeaponWorld;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;

/** Machine gun firing bullets. Ported from upstream {@code MCH_WeaponMachineGun1}. */
public class MachineGun1Weapon extends WeaponBase {

    public MachineGun1Weapon(Level world, Vec3 pos, float yaw, float pitch, String name, WeaponInfo info) {
        super(world, pos, yaw, pitch, name, info);
        this.power = 8;
        this.acceleration = 4.0F;
        this.explosionPower = 0;
        this.interval = 0;
    }

    @Override
    public boolean shot(WeaponParam prm) {
        if (!this.world.isClientSide()) {
            float[] rot = this.calculateShotRotation(prm, false, false);
            Vec3 motion = WeaponWorld.fromPitchYaw(rot[1], rot[0]);
            BulletEntity bullet = new BulletEntity(this.world, prm.posX, prm.posY, prm.posZ, motion.x, motion.y,
                    motion.z, rot[0], rot[1], this.acceleration);
            bullet.setName(this.name);
            bullet.setParameterFromWeapon(this, prm.entity, prm.user);
            bullet.setAirburstDist(this.airburstDist);
            bullet.advanceHalfStep();
            this.world.addFreshEntity(bullet);
            this.playSound(prm.entity);
        } else {
            WeaponInfo info = this.getInfo();
            WeaponClientHooks.applyRecoil(info.getRecoilPitch(), info.getRecoilYaw(), info.recoilRecoverFactor);
        }
        return true;
    }
}
