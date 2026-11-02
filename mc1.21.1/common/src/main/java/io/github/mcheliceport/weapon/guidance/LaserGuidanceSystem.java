package io.github.mcheliceport.weapon.guidance;

import io.github.mcheliceport.entity.aircraft.AircraftEntity;
import io.github.mcheliceport.weapon.WeaponClientHooks;
import io.github.mcheliceport.weapon.WeaponWorld;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

/**
 * Laser designation for laser-guided TV missiles: on the client, the point 1500 blocks along the
 * user's view (or the vehicle's nose without a targeting pod) where it meets a block. Ported from
 * upstream {@code MCH_LaserGuidanceSystem}; its commented-out lock box is not ported either.
 */
public class LaserGuidanceSystem implements GuidanceSystem {

    public @Nullable Level worldObj;
    public double targetPosX;
    public double targetPosY;
    public double targetPosZ;
    public boolean targeting = false;
    public boolean hasLaserGuidancePod = true;
    protected @Nullable Entity user;

    public void setUser(@Nullable Entity user) {
        this.user = user;
    }

    @Override
    public double getLockPosX() {
        return this.targetPosX;
    }

    @Override
    public double getLockPosY() {
        return this.targetPosY;
    }

    @Override
    public double getLockPosZ() {
        return this.targetPosZ;
    }

    @Override
    public void update() {
        if (this.worldObj == null || !this.worldObj.isClientSide() || !this.targeting || this.user == null) {
            return;
        }
        AircraftEntity ac = WeaponWorld.aircraftOf(this.user);
        if (!this.hasLaserGuidancePod && ac == null) {
            return;
        }
        float yaw = this.hasLaserGuidancePod ? this.user.getYRot() : ac.getYRot();
        float pitch = this.hasLaserGuidancePod ? this.user.getXRot() : ac.getXRot();
        Vec3 lookDir = WeaponWorld.fromPitchYaw(pitch, yaw);
        Vec3 src = WeaponClientHooks.viewPosition();
        if (src == null) {
            src = this.user.getEyePosition();
        }
        Vec3 dst = src.add(lookDir.scale(1500.0));
        BlockHitResult hit = WeaponWorld.rayTrace(this.worldObj, src, dst, false, true);
        Vec3 hitVec = hit != null ? hit.getLocation() : dst;
        this.targetPosX = hitVec.x;
        this.targetPosY = hitVec.y;
        this.targetPosZ = hitVec.z;
    }
}
