package io.github.mcheliceport.weapon.types;

import io.github.mcheliceport.content.weapon.WeaponInfo;
import io.github.mcheliceport.entity.weapon.BaseBulletEntity;
import io.github.mcheliceport.entity.weapon.BulletEntity;
import io.github.mcheliceport.entity.weapon.RocketEntity;
import io.github.mcheliceport.weapon.WeaponBase;
import io.github.mcheliceport.weapon.WeaponClientHooks;
import io.github.mcheliceport.weapon.WeaponParam;
import io.github.mcheliceport.weapon.WeaponWorld;
import net.minecraft.util.Mth;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;

/**
 * Shotgun: {@code BuckshotCount} pellets (bullets or rockets) per shot, each scattered inside a
 * circular cone the size of the definition's accuracy. Ported from upstream
 * {@code MCH_WeaponBuckshot}.
 */
public class BuckshotWeapon extends WeaponBase {

    public BuckshotWeapon(Level world, Vec3 pos, float yaw, float pitch, String name, WeaponInfo info) {
        super(world, pos, yaw, pitch, name, info);
        this.power = 8;
        this.acceleration = 4.0F;
        this.explosionPower = 0;
        this.interval = 0;
    }

    @Override
    public boolean shot(WeaponParam prm) {
        WeaponInfo info = this.getInfo();
        if (!this.world.isClientSide()) {
            float[] baseRot = this.calculateShotRotation(prm, false, false);
            int count = Math.max(1, info.buckshotCount);
            float accuracy = info.accuracy;
            for (int i = 0; i < count; i++) {
                float yaw = baseRot[0];
                float pitch = baseRot[1];
                if (accuracy > 0.0F) {
                    double r = Math.sqrt(rand.nextDouble()) * (accuracy * 0.5);
                    double theta = rand.nextDouble() * 2 * Math.PI;
                    yaw += (float) (r * Math.cos(theta));
                    pitch += (float) (r * Math.sin(theta));
                }
                yaw = Mth.wrapDegrees(yaw);
                pitch = Mth.wrapDegrees(pitch);
                Vec3 motion = WeaponWorld.fromPitchYaw(pitch, yaw);
                BaseBulletEntity pellet = this.createPellet(info.buckshotPayload, prm, motion, yaw, pitch);
                pellet.setName(this.name);
                pellet.setParameterFromWeapon(this, prm.entity, prm.user);
                pellet.setAirburstDist(this.airburstDist);
                pellet.advanceHalfStep();
                this.world.addFreshEntity(pellet);
            }
            this.playSound(prm.entity);
        } else {
            WeaponClientHooks.applyRecoil(info.getRecoilPitch(), info.getRecoilYaw(), info.recoilRecoverFactor);
        }
        return true;
    }

    private BaseBulletEntity createPellet(WeaponInfo.BuckshotPayload payload, WeaponParam prm, Vec3 motion, float yaw,
            float pitch) {
        if (payload == WeaponInfo.BuckshotPayload.ROCKET) {
            return new RocketEntity(this.world, prm.posX, prm.posY, prm.posZ, motion.x, motion.y, motion.z, yaw,
                    pitch, this.acceleration);
        }
        return new BulletEntity(this.world, prm.posX, prm.posY, prm.posZ, motion.x, motion.y, motion.z, yaw, pitch,
                this.acceleration);
    }
}
