package io.github.mcheliceport.weapon.types;

import io.github.mcheliceport.net.ModNetwork;
import io.github.mcheliceport.content.weapon.WeaponInfo;
import io.github.mcheliceport.entity.aircraft.AircraftEntity;
import io.github.mcheliceport.entity.weapon.TvMissileEntity;
import io.github.mcheliceport.net.TvMissilePayload;
import io.github.mcheliceport.weapon.WeaponBase;
import io.github.mcheliceport.weapon.WeaponClientHooks;
import io.github.mcheliceport.weapon.WeaponParam;
import io.github.mcheliceport.weapon.WeaponWorld;
import io.github.mcheliceport.weapon.guidance.LaserGuidanceSystem;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

/**
 * TV-guided missile: in mode 0 it flies where the shooter looks (wire guidance through the missile
 * camera), in mode 2 straight and 1.5 times faster; laser-guided ones follow the laser point.
 * Ported from upstream {@code MCH_WeaponTvMissile}.
 */
public class TvMissileWeapon extends WeaponBase {

    protected @Nullable TvMissileEntity lastShotTvMissile;
    protected @Nullable Entity lastShotEntity;
    protected boolean isTVGuided;
    public @Nullable LaserGuidanceSystem guidanceSystem;

    public TvMissileWeapon(Level w, Vec3 v, float yaw, float pitch, String nm, WeaponInfo wi) {
        super(w, v, yaw, pitch, nm, wi);
        this.power = 32;
        this.acceleration = 2.0F;
        this.explosionPower = 4;
        this.interval = -100;
        if (w.isClientSide()) {
            this.interval -= 10;
        }
        this.numMode = 2;
        this.lastShotEntity = null;
        this.lastShotTvMissile = null;
        this.isTVGuided = false;
        if (wi.laserGuidance) {
            this.guidanceSystem = new LaserGuidanceSystem();
            this.guidanceSystem.worldObj = w;
            this.guidanceSystem.hasLaserGuidancePod = wi.hasLaserGuidancePod;
            if (w.isClientSide()) {
                this.guidanceSystem.setUser(WeaponClientHooks.localPlayer());
            }
        }
    }

    @Override
    public String getName() {
        String opt = "";
        if (this.getCurrentMode() == 0) {
            opt = " [TV]";
        }
        if (this.getCurrentMode() == 2) {
            opt = " [TA]";
        }
        return super.getName() + opt;
    }

    @Override
    public void update(int countWait) {
        super.update(countWait);
        if (!this.world.isClientSide()) {
            if (this.isTVGuided && this.tick <= 9) {
                if (this.tick % 3 == 0 && this.lastShotTvMissile != null && this.lastShotTvMissile.isAlive()
                        && this.lastShotEntity != null && this.lastShotEntity.isAlive()) {
                    ModNetwork.sendToAllPlayers(this.world.getServer(), new TvMissilePayload(this.lastShotEntity.getId(),
                            this.lastShotTvMissile.getId()));
                }
                if (this.tick == 9) {
                    this.lastShotEntity = null;
                    this.lastShotTvMissile = null;
                }
            }
            if (this.tick <= 2 && this.lastShotEntity instanceof AircraftEntity aircraft) {
                aircraft.setTVMissile(this.lastShotTvMissile);
            }
        }
    }

    @Override
    public boolean shot(WeaponParam prm) {
        return this.world.isClientSide() ? this.shotClient(prm.entity, prm.user) : this.shotServer(prm);
    }

    protected boolean shotClient(@Nullable Entity entity, @Nullable Entity user) {
        this.optionParameter2 = 0;
        this.optionParameter1 = this.getCurrentMode();
        return true;
    }

    protected boolean shotServer(WeaponParam prm) {
        this.playSound(prm.entity);
        float[] rot = this.calculateShotRotation(prm, true, false);
        Vec3 motion = WeaponWorld.fromPitchYaw(rot[1], rot[0]);
        this.isTVGuided = prm.option1 == 0;
        float finalAcceleration = this.isTVGuided ? this.acceleration : this.acceleration * 1.5F;
        TvMissileEntity missile = new TvMissileEntity(this.world, prm.posX, prm.posY, prm.posZ, motion.x, motion.y,
                motion.z, rot[0], rot[1], finalAcceleration);
        missile.setName(this.name);
        missile.setParameterFromWeapon(this, prm.entity, prm.user);
        this.lastShotEntity = prm.entity;
        this.lastShotTvMissile = missile;
        this.world.addFreshEntity(missile);
        return true;
    }
}
