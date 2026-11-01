package io.github.mcheliceport.weapon.types;

import io.github.mcheliceport.content.weapon.WeaponInfo;
import io.github.mcheliceport.entity.weapon.MarkerRocketEntity;
import io.github.mcheliceport.util.McheliMath;
import io.github.mcheliceport.weapon.WeaponBase;
import io.github.mcheliceport.weapon.WeaponParam;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;

/** Marker rocket calling in a bombardment where it lands. Ported from upstream {@code MCH_WeaponMarkerRocket}. */
public class MarkerRocketWeapon extends WeaponBase {

    public MarkerRocketWeapon(Level w, Vec3 v, float yaw, float pitch, String nm, WeaponInfo wi) {
        super(w, v, yaw, pitch, nm, wi);
        this.acceleration = 3.0F;
        this.explosionPower = 0;
        this.power = 0;
        this.interval = 60;
        if (w.isClientSide()) {
            this.interval += 10;
        }
    }

    @Override
    public boolean shot(WeaponParam prm) {
        if (!this.world.isClientSide()) {
            this.playSound(prm.entity);
            Vec3 v = McheliMath.rotVec3(0.0, 0.0, 1.0, -prm.rotYaw, -prm.rotPitch, -prm.rotRoll);
            MarkerRocketEntity e = new MarkerRocketEntity(this.world, prm.posX, prm.posY, prm.posZ, v.x, v.y, v.z,
                    prm.rotYaw, prm.rotPitch, this.acceleration);
            e.setName(this.name);
            e.setParameterFromWeapon(this, prm.entity, prm.user);
            e.setMarkerStatus(1);
            this.world.addFreshEntity(e);
        } else {
            this.optionParameter1 = this.getCurrentMode();
        }
        return true;
    }
}
