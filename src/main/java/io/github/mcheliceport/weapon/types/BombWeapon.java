package io.github.mcheliceport.weapon.types;

import io.github.mcheliceport.content.weapon.WeaponInfo;
import io.github.mcheliceport.entity.weapon.BombEntity;
import io.github.mcheliceport.weapon.WeaponBase;
import io.github.mcheliceport.weapon.WeaponParam;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;

/**
 * Free-fall bombs and mines, released with the vehicle's velocity. Ported from upstream
 * {@code MCH_WeaponBomb}.
 *
 * <p>A {@code Destruct} bomb blows up the UAV carrying it; UAVs come in M6, so until then such a
 * bomb does nothing (as upstream on a manned vehicle) but still uses its ammunition.
 */
public class BombWeapon extends WeaponBase {

    public BombWeapon(Level w, Vec3 v, float yaw, float pitch, String nm, WeaponInfo wi) {
        super(w, v, yaw, pitch, nm, wi);
        this.acceleration = 0.5F;
        this.explosionPower = 9;
        this.power = 35;
        this.interval = -90;
        if (w.isClientSide()) {
            this.interval -= 10;
        }
    }

    @Override
    public boolean shot(WeaponParam prm) {
        if (this.getInfo() != null && this.getInfo().destruct) {
            return true;
        }
        if (!this.world.isClientSide() && prm.entity != null) {
            this.playSound(prm.entity);
            Vec3 motion = prm.entity.getDeltaMovement();
            BombEntity e = new BombEntity(this.world, prm.posX, prm.posY, prm.posZ, motion.x, motion.y, motion.z,
                    prm.entity.getYRot(), 0.0F, this.acceleration);
            e.setName(this.name);
            e.setParameterFromWeapon(this, prm.entity, prm.user);
            e.setDeltaMovement(motion);
            this.world.addFreshEntity(e);
        }
        return true;
    }
}
