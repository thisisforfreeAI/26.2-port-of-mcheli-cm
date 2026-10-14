package io.github.mcheliceport.weapon;

/**
 * Upstream {@code MCH_WeaponSet}'s ammunition, reload, heat and cooldown code, transcribed on plain
 * numbers for {@link WeaponSetTest}: a server-side set fired by a non-player, with mounts that
 * always shoot. Aim, recoil, barrel spin and sounds are left out.
 */
final class UpstreamWeaponSetOracle {

    // MCH_WeaponInfo / MCH_WeaponBase values.
    final int magSize;
    final int maxAmmo;
    final int reloadTime;
    final int maxHeatCount;
    final int soundDelay;
    final int interval;
    final int delayedInterval;
    final int heatCount;
    final int mounts;

    int ammo;
    int ammoReserve;
    int currentHeat;
    int cooldownSpeed = 1;
    int cooldown;
    int reloadCooldown;
    int soundWait;
    int currentWeaponIndex;
    final int[] lastUsedCount;

    UpstreamWeaponSetOracle(int magSize, int maxAmmo, int reloadTime, int maxHeatCount, int soundDelay, int interval,
            int delayedInterval, int heatCount, int mounts) {
        this.magSize = magSize;
        this.maxAmmo = maxAmmo;
        this.reloadTime = reloadTime;
        this.maxHeatCount = maxHeatCount;
        this.soundDelay = soundDelay;
        this.interval = interval;
        this.delayedInterval = delayedInterval;
        this.heatCount = heatCount;
        this.mounts = mounts;
        this.lastUsedCount = new int[mounts];
    }

    void setReserveAmmo(int amount) {
        int capacityRemaining = this.maxAmmo - this.ammo;
        this.ammoReserve = Math.min(amount, capacityRemaining);
    }

    boolean isBusy() {
        return this.cooldown < 0 || this.reloadCooldown > 0;
    }

    boolean canFire() {
        return this.cooldown == 0 && this.reloadCooldown == 0;
    }

    boolean manualReload() {
        int inMag = this.ammo;
        int inReserve = this.ammoReserve;
        if (!this.isBusy() && inMag < this.magSize && inReserve > 0) {
            this.ammo = 0;
            this.setReserveAmmo(inReserve + inMag);
            this.reload();
            return true;
        }
        return false;
    }

    void reload() {
        if (this.magSize > 0 && this.ammo < this.magSize && this.reloadTime > 0) {
            this.reloadCooldown = Math.max(1, this.reloadTime - 20);
        }
    }

    void reloadMag() {
        int needed = this.magSize - this.ammo;
        if (needed > 0) {
            int transfer = Math.min(needed, this.ammoReserve);
            this.ammo += transfer;
            this.setReserveAmmo(this.ammoReserve - transfer);
        }
    }

    void onSwitchWeapon() {
        int threshold = 15;
        if (this.cooldown >= -threshold) {
            this.cooldown = this.cooldown > threshold ? -this.cooldown : -threshold;
        }
        this.currentWeaponIndex = 0;
    }

    void update() {
        if (this.reloadCooldown > 0 && --this.reloadCooldown == 0) {
            this.reloadMag();
        }
        for (int i = 0; i < this.lastUsedCount.length; i++) {
            if (this.lastUsedCount[i] <= 0) {
                continue;
            }
            if (this.lastUsedCount[i] == 4) {
                // getMaxAmmo is the first weapon's getAllAmmoNum: MaxAmmo.
                if (this.currentWeaponIndex == 0 && this.canFire() && (this.ammo > 0 || this.maxAmmo <= 0)) {
                    this.lastUsedCount[i]--;
                }
            } else {
                this.lastUsedCount[i]--;
            }
        }
        if (this.currentHeat > 0) {
            if (this.currentHeat < this.maxHeatCount) {
                this.cooldownSpeed++;
            }
            this.currentHeat = Math.max(0, this.currentHeat - (this.cooldownSpeed / 20 + 1));
        }
        if (this.cooldown > 0) {
            this.cooldown--;
        } else if (this.cooldown < 0) {
            this.cooldown++;
        }
        if (this.soundWait > 0) {
            this.soundWait--;
        }
    }

    /** {@code use}: returns whether it fired, and sets {@code reloaded} as {@code prm.reload}. */
    boolean use(boolean isInfinity) {
        boolean hasAmmo = this.magSize <= 0 || this.ammo > 0;
        boolean notOverheated = this.maxHeatCount <= 0 || this.currentHeat < this.maxHeatCount;
        if (!hasAmmo || !notOverheated) {
            return false;
        }
        if (this.maxHeatCount > 0) {
            this.cooldownSpeed = 1;
            this.currentHeat += this.heatCount;
            if (this.currentHeat >= this.maxHeatCount) {
                this.currentHeat += 30;
            }
        }
        if (this.soundDelay > 0 && this.soundWait == 0) {
            this.soundWait = this.soundDelay;
        }
        this.lastUsedCount[this.currentWeaponIndex] = this.interval > 0 ? this.interval : -this.interval;
        this.currentWeaponIndex = (this.currentWeaponIndex + 1) % this.mounts;
        this.cooldown = this.delayedInterval;
        this.reloadCooldown = 0;
        if (this.ammo > 0) {
            this.ammo--;
        }
        if (this.ammo <= 0) {
            if (isInfinity && this.ammoReserve < this.magSize) {
                this.setReserveAmmo(this.magSize);
            }
            this.reload();
        }
        return true;
    }

    void waitAndReloadByOther(boolean reload) {
        this.cooldown = this.interval;
        this.reloadCooldown = 0;
        if (reload && this.magSize > 0 && this.reloadTime > 0) {
            this.reloadCooldown = Math.max(1, this.reloadTime - 20);
        }
    }
}
