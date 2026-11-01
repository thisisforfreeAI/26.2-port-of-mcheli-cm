package io.github.mcheliceport.weapon.types;

import io.github.mcheliceport.content.weapon.WeaponInfo;
import io.github.mcheliceport.entity.weapon.RocketEntity;
import io.github.mcheliceport.util.McheliMath;
import io.github.mcheliceport.weapon.WeaponBase;
import io.github.mcheliceport.weapon.WeaponParam;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;

/**
 * Unguided rockets, with an armour-piercing mode when the definition has two modes. Ported from
 * upstream {@code MCH_WeaponRocket}.
 */
public class RocketWeapon extends WeaponBase {

    public RocketWeapon(Level w, Vec3 v, float yaw, float pitch, String nm, WeaponInfo wi) {
        super(w, v, yaw, pitch, nm, wi);
        this.acceleration = 4.0F;
        this.explosionPower = 3;
        this.power = 22;
        this.interval = 5;
        if (w.isClientSide()) {
            this.interval += 2;
        }
    }

    @Override
    public String getName() {
        return super.getName() + (this.getCurrentMode() == 0 ? "" : " [HEIAP]");
    }

    @Override
    public boolean shot(WeaponParam prm) {
        if (!this.world.isClientSide()) {
            this.playSound(prm.entity);
            Vec3 v = McheliMath.rotVec3(0.0, 0.0, 1.0, -prm.rotYaw, -prm.rotPitch, -prm.rotRoll);
            RocketEntity e = new RocketEntity(this.world, prm.posX, prm.posY, prm.posZ, v.x, v.y, v.z, prm.rotYaw,
                    prm.rotPitch, this.acceleration);
            e.setName(this.name);
            e.setParameterFromWeapon(this, prm.entity, prm.user);
            if (prm.option1 == 0 && this.numMode > 1) {
                e.piercing = 0;
            }
            this.world.addFreshEntity(e);
        } else {
            this.optionParameter1 = this.getCurrentMode();
        }
        return true;
    }
}
