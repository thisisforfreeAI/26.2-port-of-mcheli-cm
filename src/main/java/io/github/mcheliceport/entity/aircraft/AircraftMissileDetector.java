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
 * ported. The UAV branch (an alert heard only on the operator's client) comes with UAVs (M6), as
 * does the ECM jammer's soft kill.
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
        }
    }

    private boolean targetsUs(BaseBulletEntity msl) {
        return msl.targetEntity != null && (this.ac.isMountedEntity(msl.targetEntity) || msl.targetEntity.equals(this.ac));
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
