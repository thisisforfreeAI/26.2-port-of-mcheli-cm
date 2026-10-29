package io.github.mcheliceport.weapon;

import io.github.mcheliceport.content.weapon.WeaponInfo;
import io.github.mcheliceport.entity.aircraft.AircraftEntity;
import io.github.mcheliceport.entity.aircraft.CrewRoles;
import java.util.Random;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

/**
 * The mounts of one weapon definition on a vehicle, fired in turn: shared magazine and reserve
 * ammunition, reload, heat, cooldown, the turret's yaw and pitch, recoil buffers and barrel spin.
 * Ported from upstream {@code MCH_WeaponSet}.
 *
 * <p>Gunner mobs (upstream {@code MCH_EntityGunner}) come in M6; until then only players fire, so
 * their extra reload wait is not ported.
 */
public class WeaponSet {

    private static final Random rand = new Random();
    public final Recoil[] recoilBuffer;
    protected final WeaponBase[] weapons;
    protected final int[] lastUsedCount;
    private final String name;
    private int currentHeat;
    public int cooldownSpeed;
    public int cooldown;
    public int reloadCooldown;
    public int soundWait;
    private float rotBarrelSpd;
    private float rotBarrel;
    private float prevRotBarrel;
    protected int ammo;
    protected int ammoReserve;
    private float yaw;
    private float prevYaw;
    private float defYaw;
    private float turretYaw;
    private float pitch;
    private float prevPitch;
    private int currentWeaponIndex;
    private int optionParm1 = 0;
    private int optionParm2 = 0;

    public WeaponSet(WeaponBase[] weapon) {
        this.name = weapon[0].name;
        this.weapons = weapon;
        this.currentWeaponIndex = 0;
        this.cooldown = 0;
        this.reloadCooldown = 0;
        this.lastUsedCount = new int[weapon.length];
        this.yaw = 0.0F;
        this.prevYaw = 0.0F;
        this.pitch = 0.0F;
        this.prevPitch = 0.0F;
        this.setAmmo(0);
        this.setReserveAmmo(0);
        this.currentHeat = 0;
        this.soundWait = 0;
        this.cooldownSpeed = 1;
        this.rotBarrelSpd = 0.0F;
        this.rotBarrel = 0.0F;
        this.prevRotBarrel = 0.0F;
        this.recoilBuffer = new Recoil[weapon.length];
        for (int i = 0; i < this.recoilBuffer.length; i++) {
            WeaponInfo info = weapon[i].getInfo();
            this.recoilBuffer[i] = new Recoil(info.recoilBufCount, info.recoilBufCountSpeed);
        }
        this.defYaw = 0.0F;
    }

    public WeaponSet(WeaponBase weapon) {
        this(new WeaponBase[] {weapon});
    }

    public boolean isName(String s) {
        return this.name.equalsIgnoreCase(s);
    }

    public String getName() {
        return this.name;
    }

    public int getMagSize() {
        return this.getFirstWeapon().getNumAmmoMax();
    }

    public int getAmmo() {
        return this.ammo;
    }

    public void setAmmo(int ammo) {
        this.ammo = ammo;
    }

    public int getRestAllAmmoNum() {
        return this.ammoReserve;
    }

    /** Sets the reserve, capped so magazine plus reserve stay within the definition's maximum. */
    public void setReserveAmmo(int amount) {
        int capacityRemaining = this.getInfo().maxAmmo - this.getAmmo();
        this.ammoReserve = Math.min(amount, capacityRemaining);
    }

    public int getMaxAmmo() {
        return this.getFirstWeapon().getAllAmmoNum();
    }

    /** Adds one supply's worth ({@code SuppliedNum}) of reserve ammunition. */
    public void supplyRestAllAmmo() {
        WeaponInfo info = this.getInfo();
        if (this.getRestAllAmmoNum() + this.getAmmo() < info.maxAmmo) {
            this.setReserveAmmo(this.getRestAllAmmoNum() + this.getAmmo() + info.suppliedNum);
        }
    }

    public boolean isBusy() {
        return this.cooldown < 0 || this.reloadCooldown > 0;
    }

    public String getDisplayName() {
        WeaponBase current = this.getCurrentWeapon();
        return current != null ? current.getName() : "";
    }

    public boolean canFire() {
        return this.cooldown == 0 && this.reloadCooldown == 0;
    }

    public boolean hasLongDelay() {
        return this.getInfo().delay > 4;
    }

    /** The reload key: empties the magazine into the reserve and reloads, when there is room and ammunition. */
    public boolean manualReload() {
        WeaponBase current = this.getCurrentWeapon();
        if (current == null || current.getInfo() == null) {
            return false;
        }
        int inMag = this.getAmmo();
        int inReserve = this.getRestAllAmmoNum();
        boolean hasRoom = inMag < this.getMagSize();
        boolean hasAmmoToLoad = inReserve > 0;
        if (!this.isBusy() && hasRoom && hasAmmoToLoad) {
            this.setAmmo(0);
            this.setReserveAmmo(inReserve + inMag);
            this.reload();
            if (current.isClientSide()) {
                WeaponClientHooks.playSoundClick(1.0F, 1.0F);
            }
            return true;
        }
        return false;
    }

    /**
     * Starts the reload countdown; the server's is 20 ticks shorter, as upstream. The vehicle's
     * crew scales it (M10b {@link CrewRoles#reloadFactor}).
     */
    public void reload() {
        WeaponBase current = this.getCurrentWeapon();
        boolean hasMag = this.getMagSize() > 0;
        boolean needsAmmo = this.getAmmo() < this.getMagSize();
        if (hasMag && needsAmmo && current.getReloadCount() > 0) {
            this.reloadCooldown = CrewRoles.reloadTicks(current.aircraft, current.getReloadCount());
            if (current.isClientSide()) {
                this.setAmmo(0);
            } else {
                this.reloadCooldown = Math.max(1, this.reloadCooldown - 20);
            }
        }
    }

    /** Fills the magazine from the reserve. */
    public void reloadMag() {
        int needed = this.getMagSize() - this.getAmmo();
        if (needed > 0) {
            int transfer = Math.min(needed, this.getRestAllAmmoNum());
            this.setAmmo(this.getAmmo() + transfer);
            this.setReserveAmmo(this.getRestAllAmmoNum() - transfer);
        }
    }

    public void switchMode() {
        boolean isChanged = false;
        for (WeaponBase w : this.weapons) {
            if (w != null) {
                isChanged |= w.switchMode();
            }
        }
        if (isChanged) {
            this.applySwitchCooldown(15);
            if (this.getCurrentWeapon().isClientSide()) {
                WeaponClientHooks.playSoundClick(1.0F, 1.0F);
            }
        }
    }

    public void onSwitchWeapon(boolean isRemote, boolean isCreative) {
        int cooldownValue = isRemote ? 25 : 15;
        this.applySwitchCooldown(cooldownValue);
        this.currentWeaponIndex = 0;
        if (isCreative) {
            this.setAmmo(this.getMagSize());
        }
    }

    private void applySwitchCooldown(int threshold) {
        if (this.cooldown >= -threshold) {
            this.cooldown = this.cooldown > threshold ? -this.cooldown : -threshold;
        }
    }

    /** Whether mount {@code index} fired recently (drives muzzle flashes and recoil on other clients). */
    public boolean isUsed(int index) {
        WeaponBase w = this.getFirstWeapon();
        if (w == null || index >= this.lastUsedCount.length) {
            return false;
        }
        int count = this.lastUsedCount[index];
        return w.interval >= 4 && count > w.interval / 2 || count >= 4;
    }

    public void update(Entity shooter, boolean isSelected, boolean isUsed) {
        WeaponInfo info = this.getCurrentWeapon().getInfo();
        if (info == null) {
            return;
        }
        if (this.reloadCooldown > 0) {
            if (--this.reloadCooldown == 0) {
                this.reloadMag();
            }
        }
        this.updateLastUsedCounts();
        this.updateHeatMechanics(info);
        this.updateCooldowns();
        this.prevYaw = this.yaw;
        this.prevPitch = this.pitch;
        if (this.weapons != null) {
            for (WeaponBase w : this.weapons) {
                if (w != null) {
                    w.update(this.cooldown);
                }
            }
        }
        if (this.soundWait > 0) {
            this.soundWait--;
        }
        this.updateBarrelRotation(isUsed);
    }

    private void updateLastUsedCounts() {
        for (int i = 0; i < this.lastUsedCount.length; i++) {
            if (this.lastUsedCount[i] <= 0) {
                continue;
            }
            if (this.lastUsedCount[i] == 4) {
                boolean canReset = this.currentWeaponIndex == 0 && this.canFire()
                        && (this.getAmmo() > 0 || this.getMaxAmmo() <= 0);
                if (canReset) {
                    this.lastUsedCount[i]--;
                }
            } else {
                this.lastUsedCount[i]--;
            }
        }
    }

    private void updateHeatMechanics(WeaponInfo info) {
        if (this.currentHeat > 0) {
            if (this.currentHeat < info.maxHeatCount) {
                this.cooldownSpeed++;
            }
            this.currentHeat = Math.max(0, this.currentHeat - (this.cooldownSpeed / 20 + 1));
        }
    }

    private void updateCooldowns() {
        if (this.cooldown > 0) {
            this.cooldown--;
        } else if (this.cooldown < 0) {
            this.cooldown++;
        }
    }

    private void updateBarrelRotation(boolean isBeingUsed) {
        if (isBeingUsed && this.rotBarrelSpd < 75.0F) {
            this.rotBarrelSpd = Math.min(74.0F, this.rotBarrelSpd + (25 + rand.nextInt(3)));
        }
        this.prevRotBarrel = this.rotBarrel;
        this.rotBarrel += this.rotBarrelSpd;
        if (this.rotBarrel >= 360.0F) {
            this.rotBarrel -= 360.0F;
            this.prevRotBarrel -= 360.0F;
        }
        if (this.rotBarrelSpd > 0.0F) {
            this.rotBarrelSpd = Math.max(0.0F, this.rotBarrelSpd - 1.0F);
        }
    }

    /** Per-mount effects of a tick: the ejected cartridge (client) and the recoil buffer. */
    public void updateWeapon(Entity shooter, boolean isUsed, int index) {
        WeaponBase current = this.getWeapon(index);
        if (isUsed && shooter.level().isClientSide() && current != null) {
            WeaponClientHooks.weaponShot(shooter.level(), shooter.position().add(current.getShotPos(shooter)),
                    shooter.getDeltaMovement(), current.getInfo(), shooter.getYRot() + this.yaw, shooter.getXRot() + this.pitch);
            WeaponClientHooks.weaponHeat(shooter,index,current.getInfo(),shooter.position().add(current.getShotPos(shooter)),shooter.getYRot()+this.yaw,shooter.getXRot()+this.pitch);
        }
        if (isUsed && shooter.level().isClientSide() && current != null && current.cartridge != null) {
            Vec3 offset = current.getShotPos(shooter);
            Vec3 motion = shooter.getDeltaMovement();
            WeaponClientHooks.spawnCartridge(shooter.level(), current.cartridge, shooter.getX() + offset.x,
                    shooter.getY() + offset.y, shooter.getZ() + offset.z, motion.x, motion.y, motion.z,
                    shooter.getYRot() + this.yaw, shooter.getXRot() + this.pitch,current.getInfo());
        }
        if (index < this.recoilBuffer.length) {
            updateRecoil(this.recoilBuffer[index], isUsed);
        }
    }

    private static void updateRecoil(Recoil r, boolean isUsed) {
        r.prevRecoilBuf = r.recoilBuf;
        if (isUsed && r.recoilBufCount <= 0) {
            r.recoilBufCount = r.recoilBufCountMax;
        }
        if (r.recoilBufCount > 0) {
            if (r.recoilBufCountMax <= 1) {
                r.recoilBuf = 1.0F;
            } else if (r.recoilBufCountMax == 2) {
                r.recoilBuf = r.recoilBufCount == 2 ? 1.0F : 0.6F;
            } else {
                if (r.recoilBufCount > r.recoilBufCountMax / 2) {
                    r.recoilBufCount -= r.recoilBufCountSpeed;
                }
                float progress = (float) r.recoilBufCount / r.recoilBufCountMax;
                r.recoilBuf = Mth.sin(progress * (float) Math.PI);
            }
            r.recoilBufCount--;
        } else {
            r.recoilBuf = 0.0F;
        }
    }

    /** Fires the current mount (server, and the client's own prediction through {@link #prepareUse}). */
    public boolean use(WeaponParam prm) {
        WeaponBase current = this.getCurrentWeapon();
        if (current == null || current.getInfo() == null) {
            return false;
        }
        WeaponInfo info = current.getInfo();
        boolean hasAmmo = this.getMagSize() <= 0 || this.getAmmo() > 0;
        boolean notOverheated = info.maxHeatCount <= 0 || this.currentHeat < info.maxHeatCount;
        if (hasAmmo && notOverheated) {
            this.setupWeaponParam(prm, current, info);
            if (current.use(prm)) {
                this.handlePostFire(prm, current, info);
                return true;
            }
        }
        return false;
    }

    /** The client's shot: checks the cooldown and calls {@code shot} without the mount offset. */
    public boolean prepareUse(WeaponParam prm) {
        WeaponBase current = this.getCurrentWeapon();
        if (current == null || current.getInfo() == null) {
            return false;
        }
        WeaponInfo info = current.getInfo();
        boolean hasAmmo = this.getMagSize() <= 0 || this.getAmmo() > 0;
        boolean notOverheated = info.maxHeatCount <= 0 || this.currentHeat < info.maxHeatCount;
        if (!hasAmmo || !notOverheated || !this.canFire()) {
            return false;
        }
        this.setupWeaponParam(prm, current, info);
        if (current.shot(prm)) {
            this.handlePostFire(prm, current, info);
            return true;
        }
        return false;
    }

    private void setupWeaponParam(WeaponParam prm, WeaponBase current, WeaponInfo info) {
        current.canPlaySound = this.soundWait == 0;
        if (prm.entity instanceof AircraftEntity aircraft) {
            float[] shotDir = aircraft.getCurrentWeaponShotDir(prm.user);
            prm.rotYaw = shotDir[0];
            prm.rotPitch = shotDir[1];
        } else {
            float baseYaw = prm.entity != null ? prm.entity.getYRot() : 0.0F;
            float basePitch = prm.entity != null ? prm.entity.getXRot() : 0.0F;
            prm.rotYaw = baseYaw + this.yaw + current.fixRotationYaw;
            prm.rotPitch = basePitch + this.pitch + current.fixRotationPitch;
        }
        if (info.accuracy > 0.0F) {
            double r = Math.sqrt(rand.nextDouble()) * (info.accuracy * 0.5);
            double theta = rand.nextDouble() * 2 * Math.PI;
            prm.rotYaw += (float) (r * Math.cos(theta));
            prm.rotPitch += (float) (r * Math.sin(theta));
        }
        prm.rotYaw = Mth.wrapDegrees(prm.rotYaw);
        prm.rotPitch = Mth.wrapDegrees(prm.rotPitch);
    }

    private void handlePostFire(WeaponParam prm, WeaponBase current, WeaponInfo info) {
        if (info.maxHeatCount > 0) {
            this.cooldownSpeed = 1;
            this.currentHeat += current.heatCount;
            if (this.currentHeat >= info.maxHeatCount) {
                this.currentHeat += 30;
            }
        }
        if (info.soundDelay > 0 && this.soundWait == 0) {
            this.soundWait = info.soundDelay;
        }
        this.optionParm1 = current.optionParameter1;
        this.optionParm2 = current.optionParameter2;
        int interval = current.interval > 0 ? current.interval : -current.interval;
        if (current.isCooldownCountReloadTime()) {
            interval = Math.max(interval, current.getReloadCount() - 10);
        }
        this.lastUsedCount[this.currentWeaponIndex] = interval;
        this.currentWeaponIndex = (this.currentWeaponIndex + 1) % this.weapons.length;
        this.cooldown = prm.user instanceof Player ? current.interval : current.delayedInterval;
        this.reloadCooldown = 0;
        if (this.getAmmo() > 0) {
            this.setAmmo(this.getAmmo() - 1);
        }
        if (this.getAmmo() <= 0) {
            if (prm.isInfinity && this.getRestAllAmmoNum() < this.getMagSize()) {
                this.setReserveAmmo(this.getMagSize());
            }
            this.reload();
            prm.reload = true;
            // Upstream: a gunner also waits out the reload before its next shot.
            if (prm.user instanceof io.github.mcheliceport.entity.gunner.GunnerEntity) {
                this.cooldown += (this.cooldown >= 0 ? 1 : -1) * current.getReloadCount();
            }
        }
        prm.result = true;
    }

    /** Another set of the same group fired: wait its interval and reload with it. */
    public void waitAndReloadByOther(boolean reload) {
        WeaponBase current = this.getCurrentWeapon();
        if (current != null && current.getInfo() != null) {
            this.cooldown = current.interval;
            this.reloadCooldown = 0;
            if (reload && this.getMagSize() > 0 && current.getReloadCount() > 0) {
                this.reloadCooldown = Math.max(1, CrewRoles.reloadTicks(current.aircraft, current.getReloadCount())
                        - (current.isClientSide() ? 0 : 20));
            }
        }
    }

    public WeaponBase getFirstWeapon() {
        return this.getWeapon(0);
    }

    public WeaponBase getCurrentWeapon() {
        return this.getWeapon(this.currentWeaponIndex);
    }

    public int getCurrentWeaponIndex() {
        return this.currentWeaponIndex;
    }

    public @Nullable WeaponBase getWeapon(int idx) {
        if (this.weapons == null || idx < 0 || idx >= this.weapons.length) {
            return null;
        }
        return this.weapons[idx];
    }

    public int getWeaponsCount() {
        return this.weapons != null ? this.weapons.length : 0;
    }

    public WeaponInfo getInfo() {
        return this.getFirstWeapon().getInfo();
    }

    public int getCurrentHeat() {
        return this.currentHeat;
    }

    public int getOptionParm1() {
        return this.optionParm1;
    }

    public int getOptionParm2() {
        return this.optionParm2;
    }

    public float getYaw() {
        return this.yaw;
    }

    public void setYaw(float yaw) {
        this.yaw = yaw;
    }

    public float getPrevYaw() {
        return this.prevYaw;
    }

    public void setPrevYaw(float prevYaw) {
        this.prevYaw = prevYaw;
    }

    public float getDefYaw() {
        return this.defYaw;
    }

    public void setDefYaw(float defYaw) {
        this.defYaw = defYaw;
    }

    public float getTurretYaw() {
        return this.turretYaw;
    }

    public void setTurretYaw(float turretYaw) {
        this.turretYaw = turretYaw;
    }

    public float getPitch() {
        return this.pitch;
    }

    public void setPitch(float pitch) {
        this.pitch = pitch;
    }

    public float getPrevPitch() {
        return this.prevPitch;
    }

    public float getRotBarrel() {
        return this.rotBarrel;
    }

    public float getPrevRotBarrel() {
        return this.prevRotBarrel;
    }

    /** Upstream {@code getImpactDistance}: horizontal distance to where the current weapon would land. */
    public double getImpactDistance(WeaponParam prm) {
        WeaponBase current = this.getCurrentWeapon();
        if (current == null || current.getInfo() == null) {
            return -1.0;
        }
        this.setShotRotation(prm, current, false);
        return current.getLandInDistance(prm);
    }

    /** Upstream {@code getPredictedImpactPoint}: the CCIP point of the current weapon. */
    public @Nullable Vec3 getPredictedImpactPoint(WeaponParam prm) {
        WeaponBase current = this.getCurrentWeapon();
        if (current == null || current.getInfo() == null) {
            return null;
        }
        this.setShotRotation(prm, current, true);
        return current.getImpactPos(prm);
    }

    private void setShotRotation(WeaponParam prm, WeaponBase current, boolean withRoll) {
        if (prm.entity instanceof AircraftEntity aircraft) {
            float[] shotDir = aircraft.getCurrentWeaponShotDir(prm.user);
            prm.rotYaw = Mth.wrapDegrees(shotDir[0]);
            prm.rotPitch = Mth.wrapDegrees(shotDir[1]);
            if (withRoll) {
                prm.rotRoll = aircraft.getRoll();
            }
        } else {
            float baseYaw = prm.entity != null ? prm.entity.getYRot() : 0.0F;
            float basePitch = prm.entity != null ? prm.entity.getXRot() : 0.0F;
            prm.rotYaw = Mth.wrapDegrees(baseYaw + this.yaw + current.fixRotationYaw);
            prm.rotPitch = Mth.wrapDegrees(basePitch + this.pitch + current.fixRotationPitch);
            if (withRoll) {
                prm.rotRoll = 0.0F;
            }
        }
    }

    /** One mount's recoil animation state. */
    public static class Recoil {
        public final int recoilBufCountMax;
        public final int recoilBufCountSpeed;
        public int recoilBufCount;
        public float recoilBuf;
        public float prevRecoilBuf;

        public Recoil(int max, int spd) {
            this.recoilBufCountMax = max;
            this.recoilBufCountSpeed = spd;
        }
    }
}
