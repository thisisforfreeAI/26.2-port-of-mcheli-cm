package io.github.mcheliceport.weapon.types;

import io.github.mcheliceport.content.weapon.WeaponInfo;
import io.github.mcheliceport.weapon.WeaponBase;
import io.github.mcheliceport.weapon.guidance.EntityLockChecker;
import io.github.mcheliceport.weapon.guidance.WeaponGuidanceSystem;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

/**
 * A missile that locks on to an entity before it fires. Ported from upstream
 * {@code MCH_WeaponEntitySeeker}; its passive-radar bullet list ({@code getShootBullets}) comes
 * with passive radar in M6.
 */
public abstract class EntitySeekerWeapon extends WeaponBase {

    public final WeaponGuidanceSystem guidanceSystem;

    protected EntitySeekerWeapon(Level w, Vec3 v, float yaw, float pitch, String nm, WeaponInfo wi) {
        super(w, v, yaw, pitch, nm, wi);
        this.guidanceSystem = new WeaponGuidanceSystem(w);
        this.guidanceSystem.lockRange = wi.maxLockOnRange;
        this.guidanceSystem.lockAngle = wi.maxLockOnAngle;
        this.guidanceSystem.antiFlareCount = wi.antiFlareCount;
        this.guidanceSystem.pdHDNMaxDegree = wi.pdHDNMaxDegree;
        this.guidanceSystem.pdHDNMaxDegreeLockOutCount = wi.pdHDNMaxDegreeLockOutCount;
        this.guidanceSystem.lockMinHeight = wi.lockMinHeight;
        this.guidanceSystem.setLockCountMax(25);
        this.guidanceSystem.isHeatSeekerMissile = wi.isHeatSeekerMissile;
        this.guidanceSystem.isRadarMissile = wi.isRadarMissile;
        this.guidanceSystem.passiveRadar = wi.passiveRadar;
        this.guidanceSystem.passiveRadarLockOutCount = wi.passiveRadarLockOutCount;
        this.guidanceSystem.canLockMissile = wi.canLockMissile;
        this.guidanceSystem.canLockInWater = wi.canLockInWater;
    }

    @Override
    public WeaponGuidanceSystem getGuidanceSystem() {
        return this.guidanceSystem;
    }

    @Override
    public int getLockCount() {
        return this.guidanceSystem.getLockCount();
    }

    @Override
    public int getLockCountMax() {
        return this.guidanceSystem.getLockCountMax();
    }

    @Override
    public void setLockCountMax(int n) {
        this.guidanceSystem.setLockCountMax(n);
    }

    @Override
    public void setLockChecker(@Nullable EntityLockChecker checker) {
        this.guidanceSystem.checker = checker;
    }

    @Override
    public void update(int countWait) {
        super.update(countWait);
        this.guidanceSystem.update();
    }

    @Override
    public boolean isCooldownCountReloadTime() {
        return true;
    }
}
