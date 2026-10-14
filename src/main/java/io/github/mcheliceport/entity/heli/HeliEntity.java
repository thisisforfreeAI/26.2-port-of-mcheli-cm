package io.github.mcheliceport.entity.heli;

import io.github.mcheliceport.config.CommonConfig;
import io.github.mcheliceport.config.ServerSettings;
import io.github.mcheliceport.content.ContentRegistries;
import io.github.mcheliceport.content.ContentType;
import io.github.mcheliceport.content.aircraft.AircraftInfo;
import io.github.mcheliceport.content.aircraft.HeliInfo;
import io.github.mcheliceport.entity.aircraft.AircraftEntity;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MoverType;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

/**
 * A helicopter: upstream's {@code MCH_EntityHeli}. The formulas live in {@link HeliFlightModel};
 * this class decides which side runs them (decision D2) and keeps the rotor and fold-blade state.
 */
public class HeliEntity extends AircraftEntity implements HeliFlightModel.Body {

    /** Fold-blade states of {@code FOLD_STAT}. */
    public static final byte BLADES_FOLDED = 0;
    public static final byte BLADES_FOLDING = 1;
    public static final byte BLADES_UNFOLDED = 2;
    public static final byte BLADES_UNFOLDING = 3;

    private static final EntityDataAccessor<Byte> DATA_FOLD_STAT =
            SynchedEntityData.defineId(HeliEntity.class, EntityDataSerializers.BYTE);

    private @Nullable HeliInfo heliInfo;
    private HeliRotor[] rotors = new HeliRotor[0];
    public double rotationRotor;
    public double prevRotationRotor;
    public int foldBladesCooldown;
    private int lastFoldBladeStat = -1;
    private float prevRollFactor;
    private boolean requestedSyncStatus;
    /** Client: the status request of the first tick is waiting to be sent. */
    public boolean statusRequestPending;

    public HeliEntity(EntityType<? extends HeliEntity> type, Level level) {
        super(type, level);
        if (level.isClientSide()) {
            this.foldBladesCooldown = 40;
        }
    }

    @Override
    protected void defineSynchedData(SynchedEntityData.Builder builder) {
        super.defineSynchedData(builder);
        builder.define(DATA_FOLD_STAT, BLADES_UNFOLDED);
    }

    @Override
    protected @Nullable AircraftInfo lookupInfo(String typeName) {
        return ContentRegistries.heli().get(typeName);
    }

    @Override
    public ContentType getContentType() {
        return ContentType.HELICOPTER;
    }

    public @Nullable HeliInfo getHeliInfo() {
        return this.heliInfo;
    }

    @Override
    protected void onTypeChanged(AircraftInfo newInfo) {
        this.heliInfo = (HeliInfo) newInfo;
        this.rotors = new HeliRotor[this.heliInfo.rotorList.size()];
        for (int i = 0; i < this.rotors.length; i++) {
            HeliInfo.Rotor r = this.heliInfo.rotorList.get(i);
            this.rotors[i] = new HeliRotor(r.bladeNum, r.bladeRot, 2, r.haveFoldFunc);
        }
        if (this.getFoldBladeStat() == BLADES_FOLDED) {
            this.forceFoldBlade();
        }
    }

    public HeliRotor[] getRotors() {
        return this.rotors;
    }

    // ---- fold blades ----

    public byte getFoldBladeStat() {
        return this.entityData.get(DATA_FOLD_STAT);
    }

    /** Upstream {@code setFoldBladeStat}: server only. */
    public void setFoldBladeStat(byte stat) {
        if (!this.level().isClientSide() && stat >= 0 && stat <= 3) {
            this.entityData.set(DATA_FOLD_STAT, stat);
        }
    }

    protected void forceFoldBlade() {
        if (this.heliInfo != null && this.rotors.length > 0 && this.heliInfo.isEnableFoldBlade) {
            for (HeliRotor r : this.rotors) {
                r.update((float) this.rotationRotor);
                this.foldBlades();
                r.forceFold();
            }
        }
    }

    public boolean isFoldBlades() {
        return this.heliInfo != null && this.rotors.length > 0 && this.getFoldBladeStat() == BLADES_FOLDED;
    }

    public boolean canSwitchFoldBlades() {
        return this.heliInfo != null && this.rotors.length > 0 && this.heliInfo.isEnableFoldBlade
                && this.getCurrentThrottle() <= 0.01 && this.foldBladesCooldown == 0
                && (this.getFoldBladeStat() == BLADES_UNFOLDED || this.getFoldBladeStat() == BLADES_FOLDED);
    }

    public boolean canUseBlades() {
        if (this.heliInfo == null) {
            return false;
        }
        if (this.rotors.length == 0) {
            return true;
        }
        if (this.getFoldBladeStat() != BLADES_UNFOLDED) {
            return false;
        }
        for (HeliRotor r : this.rotors) {
            if (r.isFoldingOrUnfolding()) {
                return false;
            }
        }
        return true;
    }

    protected void foldBlades() {
        if (this.heliInfo != null && this.rotors.length > 0) {
            this.setCurrentThrottle(0.0);
            for (HeliRotor r : this.rotors) {
                r.startFold();
            }
        }
    }

    public void unfoldBlades() {
        if (this.heliInfo != null) {
            for (HeliRotor r : this.rotors) {
                r.startUnfold();
            }
        }
    }

    // ---- mode switches ----

    @Override
    public boolean canSwitchGunnerMode() {
        return super.canSwitchGunnerMode() && this.canUseBlades() && this.levelEnough();
    }

    @Override
    public boolean canSwitchHoveringMode() {
        return super.canSwitchHoveringMode() && this.canUseBlades() && this.levelEnough();
    }

    private boolean levelEnough() {
        float roll = Mth.abs(Mth.wrapDegrees(this.getRoll()));
        float pitch = Mth.abs(Mth.wrapDegrees(this.getPitch()));
        return roll < 40.0F && pitch < 40.0F;
    }

    @Override
    public float getUnfoldLandingGearThrottle() {
        double x = this.getX() - this.xo;
        double y = this.getY() - this.yo;
        double z = this.getZ() - this.zo;
        float s = this.info != null ? this.info.speed / 3.5F : 1.0F;
        return x * x + y * y + z * z <= s ? 0.8F : 0.3F;
    }

    // ---- mouse control (upstream setAngles overrides) ----

    @Override
    public boolean canUpdatePitch() {
        return super.canUpdatePitch() && !this.isHovering();
    }

    @Override
    public boolean canUpdateRoll() {
        return super.canUpdateRoll() && !this.isHovering();
    }

    @Override
    public boolean isOverridePlayerPitch() {
        return super.isOverridePlayerPitch() && !this.isHovering();
    }

    /** Upstream's helicopter roll factor: the average of this and the last call's factor (1). */
    @Override
    public float getRollFactor() {
        float roll = super.getRollFactor();
        float f = this.prevRollFactor;
        this.prevRollFactor = roll;
        return (roll + f) / 2.0F;
    }

    @Override
    protected float getControlRotYaw(float inputX, float inputY) {
        return inputX;
    }

    @Override
    protected float getControlRotPitch(float inputX, float inputY) {
        return inputY;
    }

    @Override
    protected float getControlRotRoll(float inputX, float inputY) {
        return inputX;
    }

    @Override
    protected void onUpdateAngles(float deltaSeconds) {
        if (this.heliInfo == null) {
            return;
        }
        HeliFlightModel.onUpdateAngles(this, this.params(), deltaSeconds, this.isDestroyed(), this.isHovering(),
                ServerSettings.forSide(this.level().isClientSide()).rotationLimits(), this.input(),
                this.isFoldBlades());
    }

    // ---- tick ----

    private HeliFlightModel.Params params() {
        return HeliFlightModel.Params.of(this.heliInfo, CommonConfig.ALL_HELI_SPEED.get());
    }

    private HeliFlightModel.Input input() {
        return new HeliFlightModel.Input(this.throttleUp, this.throttleDown, this.moveLeft, this.moveRight);
    }

    /** The server takes motion and throttle from the pilot's client (decision D2). */
    public boolean followsPilot() {
        return !this.level().isClientSide() && !this.simulates();
    }

    @Override
    protected void onUpdateAircraft() {
        if (this.heliInfo == null) {
            return;
        }
        if (!this.requestedSyncStatus) {
            this.requestedSyncStatus = true;
            if (this.level().isClientSide()) {
                byte stat = this.getFoldBladeStat();
                if (stat == BLADES_FOLDING || stat == BLADES_FOLDED) {
                    this.forceFoldBlade();
                }
                this.statusRequestPending = true;
            }
        }

        if (this.followsPilot()) {
            this.setThrottle(this.getCurrentThrottle());
            this.prevRotationRotor = this.rotationRotor;
            this.rotationRotor = HeliFlightModel.spinRotor(this.rotationRotor, this.getCurrentThrottle(),
                    this.heliInfo.rotorSpeed);
        } else {
            this.onUpdateControl();
        }
        this.onUpdateRotor();

        if (!this.followsPilot()) {
            HeliFlightModel.hoverPitchDamping(this, this.isDestroyed(), this.isHovering());
        }
        if (this.isDestroyed() && this.getCurrentThrottle() > 0.0) {
            if (this.blockWithin(3, -2)) {
                this.setCurrentThrottle(this.getCurrentThrottle() * 0.8);
            }
            if (this.isExploded()) {
                this.setCurrentThrottle(this.getCurrentThrottle() * 0.98);
            }
        }

        if (this.level().isClientSide()) {
            this.onUpdateClient();
        }
        if (this.simulates()) {
            this.onUpdateSimulation();
        }
    }

    /** Upstream {@code onUpdate_Control}. */
    private void onUpdateControl() {
        if (this.isHoveringMode() && !this.canUseFuel(true)) {
            this.switchHoveringMode(false);
        }
        if (this.isGunnerMode() && !this.canUseFuel()) {
            this.switchGunnerMode(false);
        }
        HeliFlightModel.Params p = this.params();
        HeliFlightModel.Input in = this.input();
        boolean simulates = this.simulates();
        if (!this.isDestroyed() && (this.getPilot() != null || this.isHoveringMode()) && this.canUseBlades()
                && this.isCanopyClose() && this.canUseFuel(true)) {
            if (!this.isHovering()) {
                boolean autoThrottle = (!this.level().isClientSide() || this.isPilotLocalClient())
                        && CommonConfig.AUTO_THROTTLE_DOWN_HELI.get();
                HeliFlightModel.controlNotHovering(this, p, in, autoThrottle, simulates);
            } else {
                HeliFlightModel.controlHovering(this, p, in, simulates);
            }
        } else {
            HeliFlightModel.idleThrottle(this);
            if (this.heliInfo.isEnableFoldBlade && this.rotors.length > 0
                    && this.getFoldBladeStat() == BLADES_FOLDED && this.onGround() && !this.isDestroyed()) {
                HeliFlightModel.controlFoldBladeOnGround(this, in, simulates);
            }
        }

        if (this.level().isClientSide()) {
            if (!this.isPilotLocalClient()) {
                HeliFlightModel.chaseSyncedThrottle(this, this.getThrottle());
            }
        } else {
            this.setThrottle(this.getCurrentThrottle());
        }
        if (this.getCurrentThrottle() < 0.0) {
            this.setCurrentThrottle(0.0);
        }

        this.prevRotationRotor = this.rotationRotor;
        this.rotationRotor = HeliFlightModel.spinRotor(this.rotationRotor, this.getCurrentThrottle(),
                this.heliInfo.rotorSpeed);
    }

    /** Upstream {@code onUpdate_Rotor}. */
    private void onUpdateRotor() {
        byte stat = this.getFoldBladeStat();
        boolean isEndSwitch = true;
        if (stat != this.lastFoldBladeStat) {
            if (stat == BLADES_FOLDING) {
                this.foldBlades();
            } else if (stat == BLADES_UNFOLDING) {
                this.unfoldBlades();
            }
            if (this.level().isClientSide()) {
                this.foldBladesCooldown = 40;
            }
            this.lastFoldBladeStat = stat;
        } else if (this.foldBladesCooldown > 0) {
            this.foldBladesCooldown--;
        }
        for (HeliRotor r : this.rotors) {
            r.update((float) this.rotationRotor);
            if (r.isFoldingOrUnfolding()) {
                isEndSwitch = false;
            }
        }
        if (isEndSwitch) {
            if (stat == BLADES_FOLDING) {
                this.setFoldBladeStat(BLADES_FOLDED);
            } else if (stat == BLADES_UNFOLDING) {
                this.setFoldBladeStat(BLADES_UNFOLDED);
            }
        }
    }

    /** The rotation half of upstream's {@code onUpdate_Client}: a destroyed heli spins as it falls. */
    private void onUpdateClient() {
        if (this.isDestroyed()) {
            if (this.rotDestroyedYaw < 15.0F) {
                this.rotDestroyedYaw += 0.3F;
            }
            this.setYaw(this.getYaw() + this.rotDestroyedYaw * (float) this.getCurrentThrottle());
            if (!this.blockWithin(3, -3)) {
                if (Mth.abs(this.getPitch()) < 10.0F) {
                    this.setPitch(this.getPitch() + this.rotDestroyedPitch);
                }
                this.setRoll(this.getRoll() + this.rotDestroyedRoll);
            }
        }
    }

    /** Upstream {@code onUpdate_Server}, run by whichever side moves the heli. */
    private void onUpdateSimulation() {
        HeliFlightModel.physics(this, this.params(), this.isHovering(), this.isDestroyed(), this.canFloatWater());
        this.breakSoftBlocks();
    }

    // ---- HeliFlightModel.Body ----

    @Override
    public double motionX() {
        return this.getDeltaMovement().x;
    }

    @Override
    public double motionY() {
        return this.getDeltaMovement().y;
    }

    @Override
    public double motionZ() {
        return this.getDeltaMovement().z;
    }

    @Override
    public void setMotion(double x, double y, double z) {
        this.setDeltaMovement(x, y, z);
    }

    @Override
    public double currentThrottle() {
        return this.getCurrentThrottle();
    }

    @Override
    public double currentSpeed() {
        return this.currentSpeed;
    }

    @Override
    public void setCurrentSpeed(double speed) {
        this.currentSpeed = speed;
    }

    @Override
    public boolean inWater() {
        return this.isInWater();
    }

    @Override
    public double waterDepth() {
        return this.getWaterDepth();
    }

    @Override
    public void move(double x, double y, double z) {
        this.move(MoverType.SELF, new Vec3(x, y, z));
    }

    @Override
    public RandomSource random() {
        return this.random;
    }

    /** The fastest horizontal speed per tick the server accepts from the pilot. */
    public double getMaxSpeedForChecks() {
        return this.getSpeedLimitForChecks() * 1.5;
    }

    @Override
    protected double getSpeedLimitForChecks() {
        return this.heliInfo != null ? this.heliInfo.speed * CommonConfig.ALL_HELI_SPEED.get() : 1.0;
    }

    // ---- save data ----

    @Override
    protected void addAdditionalSaveData(ValueOutput output) {
        super.addAdditionalSaveData(output);
        output.putDouble("RotorSpeed", this.getCurrentThrottle());
        output.putDouble("rotetionRotor", this.rotationRotor);
        output.putBoolean("FoldBlade", this.getFoldBladeStat() == BLADES_FOLDED);
    }

    @Override
    protected void readAdditionalSaveData(ValueInput input) {
        boolean beforeFoldBlade = this.getFoldBladeStat() == BLADES_FOLDED;
        super.readAdditionalSaveData(input);
        this.setCurrentThrottle(input.getDoubleOr("RotorSpeed", 0.0));
        this.rotationRotor = input.getDoubleOr("rotetionRotor", 0.0);
        this.setFoldBladeStat(input.getBooleanOr("FoldBlade", false) ? BLADES_FOLDED : BLADES_UNFOLDED);
        if (this.heliInfo == null && !this.level().isClientSide()) {
            this.discard();
            return;
        }
        if (!beforeFoldBlade && this.getFoldBladeStat() == BLADES_FOLDED) {
            this.forceFoldBlade();
        }
        this.prevRotationRotor = this.rotationRotor;
    }
}
