package io.github.mcheliceport.entity.aircraft;

import io.github.mcheliceport.entity.weapon.BaseBulletEntity;
import io.github.mcheliceport.util.McheliSounds;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.Level;

/**
 * A flare-equipped vehicle's missile warning: an alert while a missile within 60 blocks homes on
 * the vehicle or a rider, and while flares are out every such missile is destroyed. Ported from
 * upstream {@code MCH_MissileDetector}.
 *
 * <p>Upstream also read "Tracking" and "LockOn" flags from the entity's custom data to send lock
 * warnings ({@code PacketNotifyLock}); nothing in upstream ever set them, so that path is not
 * ported. A UAV's alert is heard only on its operator's client.
 */
public final class AircraftMissileDetector {

    public static final int SEARCH_RANGE = 60;
    private final AircraftEntity ac;
    private final Level world;
    private int alertCount;

    public AircraftMissileDetector(AircraftEntity aircraft, Level w) {
        this.world = w;
        this.ac = aircraft;
    }

    public void update() {
        if (this.ac.isECMJammerUsing()) {
            this.destroyMissileECM();
        }
        if (!this.ac.haveFlare()) {
            return;
        }
        if (this.alertCount > 0) {
            this.alertCount--;
        }
        if (this.ac.isDestroyed()) {
            return;
        }
        Entity rider = this.ac.getPilot();
        if (rider == null) {
            rider = this.ac.getEntityBySeatId(1);
        }
        if (rider == null) {
            return;
        }
        if (this.ac.isFlareUsing()) {
            this.destroyMissile();
        } else if (!this.ac.isUAV() && !this.world.isClientSide()) {
            if (this.alertCount == 0 && this.isLockedByMissile()) {
                this.alertCount = 20;
                McheliSounds.playAt(this.ac, "alert", 1.0F, 1.0F);
            }
        } else if (this.ac.isUAV() && this.world.isClientSide() && this.alertCount == 0 && this.isLockedByMissile()) {
            this.alertCount = 20;
            if (rider instanceof net.minecraft.world.entity.player.Player player && player.isLocalPlayer()) {
                io.github.mcheliceport.weapon.WeaponClientHooks.playSound("alert", 1.0F, 1.0F);
            }
        }
    }

    private boolean targetsUs(BaseBulletEntity msl) {
        // A client knows only the synced target id (0 for none).
        Entity target = msl.targetEntity;
        if (target == null && this.world.isClientSide() && msl.getTargetEntityID() != 0) {
            target = this.world.getEntity(msl.getTargetEntityID());
        }
        return target != null && (this.ac.isMountedEntity(target) || target.equals(this.ac));
    }

    /** Upstream {@code destroyMissile}: missiles homing on the vehicle lose their target and vanish. */
    public void destroyMissile() {
        for (BaseBulletEntity msl : this.world.getEntitiesOfClass(BaseBulletEntity.class,
                this.ac.getBoundingBox().inflate(SEARCH_RANGE))) {
            if (this.targetsUs(msl)) {
                msl.targetEntity = null;
                msl.discard();
            }
        }
    }

    /**
     * Upstream {@code destroyMissileECM}: while a lock-breaking jammer ({@code Type: 2}) runs, radar
     * and anti-radiation missiles within 80 blocks homing on the vehicle lose their target.
     */
    public void destroyMissileECM() {
        if (this.world.isClientSide() || this.ac.getAcInfo() == null || this.ac.getAcInfo().ecmJammerType != 2) {
            return;
        }
        for (BaseBulletEntity msl : this.world.getEntitiesOfClass(BaseBulletEntity.class,
                this.ac.getBoundingBox().inflate(80.0))) {
            if (this.targetsUs(msl) && msl.getInfo() != null
                    && (msl.getInfo().isRadarMissile || msl.getInfo().antiRadiationMissile)) {
                msl.setTargetEntity(null);
            }
        }
    }

    public boolean isLockedByMissile() {
        for (BaseBulletEntity msl : this.world.getEntitiesOfClass(BaseBulletEntity.class,
                this.ac.getBoundingBox().inflate(SEARCH_RANGE))) {
            if (this.targetsUs(msl)) {
                return true;
            }
        }
        return false;
    }
}
