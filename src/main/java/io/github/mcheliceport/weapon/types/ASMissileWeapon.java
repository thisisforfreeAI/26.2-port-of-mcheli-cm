package io.github.mcheliceport.weapon.types;

import io.github.mcheliceport.content.weapon.WeaponInfo;
import io.github.mcheliceport.entity.weapon.ASMissileEntity;
import io.github.mcheliceport.weapon.WeaponBase;
import io.github.mcheliceport.weapon.WeaponParam;
import io.github.mcheliceport.weapon.WeaponWorld;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;

/**
 * Air-to-surface missile flying to the block the user looks at, up to 1500 blocks away; a GPS one
 * flies to the shooter's GPS point instead and an anti-radiation one at the radar emitter nearest
 * the line of sight, if any. Ported from upstream {@code MCH_WeaponASMissile}.
 */
public class ASMissileWeapon extends WeaponBase {

    public ASMissileWeapon(Level world, Vec3 position, float yaw, float pitch, String name, WeaponInfo weaponInfo) {
        super(world, position, yaw, pitch, name, weaponInfo);
        this.acceleration = 3.0F;
        this.explosionPower = 9;
        this.power = 40;
        this.interval = -350;
        if (world.isClientSide()) {
            this.interval -= 10;
        }
    }

    @Override
    public boolean isCooldownCountReloadTime() {
        return true;
    }

    @Override
    public boolean shot(WeaponParam prm) {
        if (this.getInfo().isGPSMissile && this.world.isClientSide()) {
            return GuidedShots.clientHasGpsPoint(prm.user);
        }
        Vec3 gps = null;
        if (this.getInfo().isGPSMissile && !this.world.isClientSide()
                && (gps = GuidedShots.serverGpsPoint(prm)) == null) {
            return false;
        }
        if (!this.world.isClientSide() && prm.user != null && prm.entity != null) {
            float yaw = prm.user.getYRot();
            float pitch = prm.user.getXRot();
            Vec3 lookDir = WeaponWorld.fromPitchYaw(pitch, yaw);
            Vec3 src = new Vec3(prm.entity.getX(), prm.entity.getY() + prm.entity.getEyeHeight(), prm.entity.getZ());
            Vec3 dst = src.add(lookDir.scale(1500.0));
            BlockHitResult hit = WeaponWorld.rayTrace(this.world, src, dst, false, true);
            Vec3 hitVec = gps != null ? gps : hit != null ? hit.getLocation() : dst;
            ASMissileEntity missile = new ASMissileEntity(this.world, prm.posX, prm.posY, prm.posZ, lookDir.x,
                    lookDir.y, lookDir.z, yaw, pitch, this.acceleration);
            missile.setName(this.name);
            missile.setParameterFromWeapon(this, prm.entity, prm.user);
            missile.targetPosX = hitVec.x;
            missile.targetPosY = hitVec.y;
            missile.targetPosZ = hitVec.z;
            missile.gpsGuided = gps != null;
            if (gps == null && this.getInfo().antiRadiationMissile) {
                Entity emitter = GuidedShots.antiRadiationTarget(this.world, prm, this.getInfo(), null);
                if (emitter != null) {
                    missile.setTargetEntity(emitter);
                }
            }
            this.world.addFreshEntity(missile);
            this.playSound(prm.entity);
        }
        return true;
    }
}
