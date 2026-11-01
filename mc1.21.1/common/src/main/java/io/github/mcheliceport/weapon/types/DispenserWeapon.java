package io.github.mcheliceport.weapon.types;

import io.github.mcheliceport.content.weapon.WeaponInfo;
import io.github.mcheliceport.entity.weapon.DispensedItemEntity;
import io.github.mcheliceport.util.McheliMath;
import io.github.mcheliceport.weapon.WeaponBase;
import io.github.mcheliceport.weapon.WeaponParam;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;

/** Drops an item (a water bucket…) that is used on the blocks where it lands. Ported from upstream {@code MCH_WeaponDispenser}. */
public class DispenserWeapon extends WeaponBase {

    public DispenserWeapon(Level w, Vec3 v, float yaw, float pitch, String nm, WeaponInfo wi) {
        super(w, v, yaw, pitch, nm, wi);
        this.acceleration = 0.5F;
        this.explosionPower = 0;
        this.power = 0;
        this.interval = -90;
        if (w.isClientSide()) {
            this.interval -= 10;
        }
    }

    @Override
    public boolean shot(WeaponParam prm) {
        if (!this.world.isClientSide() && prm.entity != null) {
            this.playSound(prm.entity);
            Vec3 v = McheliMath.rotVec3(0.0, 0.0, 1.0, -prm.rotYaw, -prm.rotPitch, -prm.rotRoll);
            DispensedItemEntity e = new DispensedItemEntity(this.world, prm.posX, prm.posY, prm.posZ, v.x, v.y, v.z,
                    prm.rotYaw, prm.rotPitch, this.acceleration);
            e.setName(this.name);
            e.setParameterFromWeapon(this, prm.entity, prm.user);
            Vec3 motion = prm.entity.getDeltaMovement().add(e.getDeltaMovement().scale(0.5));
            e.setDeltaMovement(motion);
            e.setPos(e.position().add(motion.scale(0.5)));
            this.world.addFreshEntity(e);
        }
        return true;
    }
}
