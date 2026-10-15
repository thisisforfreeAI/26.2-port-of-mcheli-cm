package io.github.mcheliceport.entity.plane;

import io.github.mcheliceport.config.ClientConfig;
import io.github.mcheliceport.config.CommonConfig;
import io.github.mcheliceport.content.ContentRegistries;
import io.github.mcheliceport.content.ContentType;
import io.github.mcheliceport.content.aircraft.AircraftInfo;
import io.github.mcheliceport.content.aircraft.PlaneInfo;
import io.github.mcheliceport.entity.aircraft.AircraftEntity;
import io.github.mcheliceport.entity.aircraft.AircraftPart;
import io.github.mcheliceport.entity.aircraft.GroundProbe;
import io.github.mcheliceport.net.AircraftStatusRequestPayload;
import io.github.mcheliceport.effect.McheliEffects;
import io.github.mcheliceport.effect.ParticleParam;
import net.minecraft.core.BlockPos;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MoverType;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

/** McHeli CE's plane: shared seats, weapons and damage remain in AircraftEntity. */
public class PlaneEntity extends AircraftEntity implements PlaneFlightModel.Body {
    private @Nullable PlaneInfo planeInfo;
    public @Nullable AircraftPart partNozzle;
    public @Nullable AircraftPart partWing;
    public float rotationRotor;
    public float prevRotationRotor;
    public float soundVolume;
    private float throttleBack;
    private float keyRotation;
    private boolean keyRotationApplied;
    private boolean requestedStatus;
    private boolean loading;
    private final PlaneParticles particles = new PlaneParticles();
    public final PlaneExhaustAnimation exhaustAnimation = new PlaneExhaustAnimation();
    public float exhaustYaw;
    public float previousExhaustYaw;

    public PlaneEntity(EntityType<? extends PlaneEntity> type, Level level) {
        super(type, level);
    }

    @Override
    protected @Nullable AircraftInfo lookupInfo(String name) {
        return ContentRegistries.get().plane().get(name);
    }

    @Override
    public ContentType getContentType() {
        return ContentType.PLANE;
    }

    public @Nullable PlaneInfo getPlaneInfo() {
        return this.planeInfo;
    }

    @Override
    public int getNumEjectionSeat() {
        int seats = this.getSeatNum() + 1;
        return this.planeInfo != null && this.planeInfo.isEnableEjectionSeat && seats <= 2 ? seats : 0;
    }

    /**
     * Upstream MCP_ClientPlaneTickHandler's H key: gunner, extra positions, then normal view.
     * Only a gunner-mode change needs a packet; position selection stays on the pilot's client.
     */
    public boolean cycleCameraView(Entity viewer) {
        if (!this.isPilot(viewer) || this.isDestroyed() || this.planeInfo == null) {
            return false;
        }
        if (this.isGunnerMode(viewer) && this.canSwitchCameraPos()) {
            this.switchGunnerMode(false);
            this.setCameraId(1);
        } else if (this.getCameraId() > 0) {
            this.setCameraId(this.getCameraId() + 1);
        } else if (this.canSwitchGunnerMode()) {
            this.switchGunnerMode(!this.isGunnerMode());
            this.setCameraId(0);
        } else if (this.canSwitchCameraPos()) {
            this.setCameraId(1);
        } else {
            return false;
        }
        return true;
    }

    @Override
    protected void onTypeChanged(AircraftInfo info) {
        this.planeInfo = (PlaneInfo) info;
        this.particles.reset();
        this.exhaustAnimation.reset(this.planeInfo.exhaustFlames.size());
        this.partNozzle = this.planeInfo.haveNozzle() || this.planeInfo.haveRotor() || this.planeInfo.isEnableVtol
                ? this.createPart(1, "Nozzle", 1.5F) : null;
        this.partWing = this.planeInfo.haveWing() ? this.createPart(3, "Wing", 2.5F) : null;
        if (this.partNozzle != null && this.planeInfo.isDefaultVtol && !this.level().isClientSide() && !this.loading) {
            this.partNozzle.forceSwitch(true);
        }
    }

    private AircraftPart createPart(int bit, String name, float step) {
        AircraftPart part = new AircraftPart(this, bit, name);
        part.rotationInv = step;
        AircraftPart.Sound click = new AircraftPart.Sound("plane_cc", 1.0F, 0.5F);
        part.soundStartSwitchOn = part.soundEndSwitchOn = part.soundStartSwitchOff = part.soundEndSwitchOff = click;
        part.soundSwitching = new AircraftPart.Sound("plane_cv", 1.0F, 0.5F);
        return part;
    }

    private PlaneFlightModel.Params params() {
        return PlaneFlightModel.Params.of(this.planeInfo, CommonConfig.ALL_PLANE_SPEED.get());
    }

    private PlaneFlightModel.Input input() {
        return new PlaneFlightModel.Input(this.throttleUp, this.throttleDown, this.moveLeft, this.moveRight);
    }

    public int getVtolMode() {
        if (this.partNozzle == null || !this.partNozzle.getStatus()) {
            return this.getNozzleRotation() <= 0.005F ? 0 : 1;
        }
        return this.getNozzleRotation() >= 89.995F ? 2 : 1;
    }

    public float getNozzleRotation() {
        return this.partNozzle != null ? this.partNozzle.rotation : 0.0F;
    }

    public boolean canSwitchVtol() {
        return this.planeInfo != null && this.planeInfo.isEnableVtol && this.getModeSwitchCooldown() <= 0
                && this.getVtolMode() != 1 && Math.abs(this.getRoll()) <= 30.0F
                && !(this.onGround() && this.planeInfo.isDefaultVtol);
    }

    public void switchVtol(boolean enabled) {
        if (this.partNozzle == null || this.planeInfo == null
                || !enabled && this.onGround() && this.planeInfo.isDefaultVtol) {
            return;
        }
        this.partNozzle.setStatusServer(enabled);
        if (this.getPilot() != null) {
            this.getPilot().setXRot(0.0F);
            this.getPilot().xRotO = 0.0F;
        }
    }

    public boolean canUseWing() {
        return this.partWing == null || this.planeInfo != null && this.planeInfo.isVariableSweepWing
                && this.getCurrentThrottle() >= 0.2 || this.partWing.isOFF();
    }

    public boolean canFoldWing() {
        if (this.partWing == null || this.planeInfo == null || this.getModeSwitchCooldown() > 0) {
            return false;
        }
        if (this.planeInfo.isVariableSweepWing) {
            if (!this.onGround() && !this.blockWithin(3, -20)) {
                if (this.getCurrentThrottle() < 0.7F) {
                    return false;
                }
            } else if (this.getCurrentThrottle() > 0.1F) {
                return false;
            }
        } else if (!this.onGround() && !this.blockWithin(3, -3) || this.getCurrentThrottle() > 0.01F) {
            return false;
        }
        return this.partWing.isOFF();
    }

    public boolean canUnfoldWing() {
        return this.partWing != null && this.getModeSwitchCooldown() <= 0 && this.partWing.isON();
    }

    public void foldWing(boolean folded) {
        if (this.partWing != null && this.getModeSwitchCooldown() <= 0) {
            this.partWing.setStatusServer(folded);
            this.setModeSwitchCooldown(20);
        }
    }

    private float wingFactor() {
        AircraftPart wing = this.partWing != null ? this.partWing : this.partHatch;
        return wing != null ? wing.getFactor() : 0.0F;
    }

    @Override
    protected void updateParts(int status) {
        super.updateParts(status);
        if (this.isDestroyed()) {
            return;
        }
        for (AircraftPart part : new AircraftPart[] {this.partNozzle, this.partWing}) {
            if (part != null) {
                part.updateStatusClient(status);
                part.update();
            }
        }
        if (!this.level().isClientSide() && this.partWing != null && this.planeInfo != null
                && this.planeInfo.isVariableSweepWing && this.partWing.isON() && this.getCurrentThrottle() >= 0.2F
                && (this.getCurrentThrottle() < 0.5 || this.blockWithin(1, -10))) {
            this.partWing.setStatusServer(false);
        }
    }

    @Override
    public boolean canSwitchGunnerMode() {
        return super.canSwitchGunnerMode() && Math.abs(Mth.wrapDegrees(this.getRoll())) <= 40.0F
                && Math.abs(Mth.wrapDegrees(this.getPitch())) <= 40.0F && this.getCurrentThrottle() > 0.6F
                && !this.blockWithin(3, -5);
    }

    @Override
    public boolean canSwitchHoveringMode() {
        return false;
    }

    @Override
    public boolean canUpdateYaw() { return super.canUpdateYaw() && !this.isHovering(); }
    @Override
    public boolean canUpdatePitch() { return super.canUpdatePitch() && !this.isHovering(); }
    @Override
    public boolean canUpdateRoll() { return super.canUpdateRoll() && !this.isHovering(); }
    @Override
    public boolean isOverridePlayerYaw() { return super.isOverridePlayerYaw() && !this.isHovering(); }
    @Override
    public boolean isOverridePlayerPitch() { return super.isOverridePlayerPitch() && !this.isHovering(); }
    @Override
    public float getYawFactor() { return (this.getVtolMode() > 0 && this.planeInfo != null ? this.planeInfo.vtolYaw : super.getYawFactor()) * 0.8F; }
    @Override
    public float getPitchFactor() { return (this.getVtolMode() > 0 && this.planeInfo != null ? this.planeInfo.vtolPitch : super.getPitchFactor()) * 0.8F; }
    @Override
    public float getRollFactor() { return (this.getVtolMode() > 0 && this.planeInfo != null ? this.planeInfo.vtolYaw : super.getRollFactor()) * 0.8F; }

    private boolean flightSim() {
        return this.level().isClientSide() && ClientConfig.MOUSE_CONTROL_FLIGHT_SIM_MODE.get();
    }

    @Override
    public void setAngles(float x, float y, float seconds) {
        if (this.flightSim()) {
            this.rotationByKey(seconds);
        }
        super.setAngles(x, y, seconds);
    }

    private void rotationByKey(float seconds) {
        if (!this.keyRotationApplied) {
            this.keyRotationApplied = true;
            if (this.moveLeft != this.moveRight && (this.flightSim() || this.getVtolMode() == 0)) {
                this.keyRotation += (this.moveLeft ? -4.0F : 4.0F) * seconds;
            }
        }
    }

    @Override
    protected float getControlRotYaw(float x, float y) {
        return this.flightSim() ? this.keyRotation * 20.0F : x;
    }

    @Override
    protected float getControlRotPitch(float x, float y) { return y; }

    @Override
    protected float getControlRotRoll(float x, float y) {
        return this.flightSim() ? x * 2.0F : this.getVtolMode() == 0 ? x * 0.5F : x;
    }

    @Override
    protected void onUpdateAngles(float seconds) {
        if (this.planeInfo == null || this.isDestroyed()) {
            return;
        }
        if (this.isGunnerMode()) {
            this.setPitch(this.getPitch() * (float) Math.pow(0.95, seconds * 20.0F));
            this.setYaw(this.getYaw() + this.planeInfo.autoPilotRot * 4.0F * seconds);
            if (Math.abs(this.getRoll()) > 20.0F) {
                this.setRoll(this.getRoll() * (float) Math.pow(0.95, seconds * 20.0F));
            }
        }
        boolean flying = !this.blockWithin(3, -3);
        if (!flying || this.isFreeLookMode() || this.isGunnerMode()
                || this.planeInfo.isFloat && this.getWaterDepth() > 0.0) {
            float yaw = !flying ? this.planeInfo.mobilityYawOnGround : 1.0F;
            if (!flying && !this.planeInfo.canRotOnGround && this.groundIsSolid()) {
                yaw = 0.0F;
            }
            if (this.moveLeft != this.moveRight) {
                this.setYaw(this.getYaw() + (this.moveLeft ? -12.0F : 12.0F) * yaw * seconds);
            }
        } else {
            this.rotationByKey(seconds);
            if (!this.flightSim()) {
                this.setRoll(this.getRoll() + this.keyRotation * 0.5F * this.planeInfo.mobilityRoll);
            }
        }
        this.keyRotation = (float) (this.keyRotation * Math.pow(0.9, seconds * 20.0F));
        if (!flying && Math.abs(this.getPitch()) < 40.0F) {
            PlaneFlightModel.groundPitch(this, this.params(), 0.97F);
        }
        if (this.getNozzleRotation() > 0.001F) {
            this.setPitch(this.getPitch() * (float) Math.pow(0.97, seconds * 20.0F));
            this.setRoll(this.getRoll() * (float) Math.pow(0.9, seconds * 20.0F));
        }
        this.previousExhaustYaw = this.exhaustYaw;
        this.exhaustYaw = PlaneExhaustAnimation.steer(this.exhaustYaw, this.moveLeft, this.moveRight, seconds);
    }

    @Override
    protected void onUpdateAircraft() {
        if (this.planeInfo == null) {
            return;
        }
        this.keyRotationApplied = false;
        if (!this.requestedStatus) {
            this.requestedStatus = true;
            if (this.level().isClientSide() && clientHooks() != null) {
                clientHooks().sendToServer(new AircraftStatusRequestPayload(this.getId()));
            }
        }
        if (!this.followsPilot()) {
            this.throttleBack = (float) (this.throttleBack * 0.8);
            if (this.isGunnerMode() && !this.canUseFuel()) {
                this.switchGunnerMode(false);
            }
            if (!this.isDestroyed() && this.canUseFuel() && (this.planeInfo.isTargetDrone
                    || this.getPilot() != null && this.isCanopyClose() && this.canUseWing())) {
                PlaneFlightModel.Input controls = this.planeInfo.isTargetDrone
                        ? new PlaneFlightModel.Input(true, this.throttleDown, this.moveLeft, this.moveRight) : this.input();
                double carrierSpeed = this.getVehicle() != null ? this.getVehicle().getDeltaMovement().horizontalDistance() : -1.0;
                PlaneFlightModel.control(this, this.params(), controls, this.isGunnerMode(),
                        CommonConfig.AUTO_THROTTLE_DOWN_PLANE.get(), carrierSpeed);
            } else {
                PlaneFlightModel.idle(this, this.params());
            }
            this.setCurrentThrottle(Math.max(0.0, this.getCurrentThrottle()));
            if (this.level().isClientSide() && !this.isPilotLocalClient()) {
                double throttle = this.getThrottle();
                if (this.getCurrentThrottle() > throttle) { this.setCurrentThrottle(this.getCurrentThrottle() - 0.005); }
                if (this.getCurrentThrottle() < throttle) { this.setCurrentThrottle(this.getCurrentThrottle() + 0.005); }
            }
        }
        if (!this.level().isClientSide()) {
            this.setThrottle(this.getCurrentThrottle());
        }
        this.prevRotationRotor = this.rotationRotor;
        this.rotationRotor = (float) (this.rotationRotor + this.getCurrentThrottle() * this.planeInfo.rotorSpeed);
        if (this.rotationRotor > 360.0F) { this.rotationRotor -= 360.0F; this.prevRotationRotor -= 360.0F; }
        if (this.rotationRotor < 0.0F) { this.rotationRotor += 360.0F; this.prevRotationRotor += 360.0F; }
        if (this.onGround() && this.getVtolMode() == 0 && this.planeInfo.isDefaultVtol) {
            this.switchVtol(true);
        }
        if (!this.followsPilot() && !this.isDestroyed() && this.isHovering() && Math.abs(this.getPitch()) < 70.0F) {
            this.setPitch(this.getPitch() * 0.95F);
        }
        if (this.isDestroyed() && this.getCurrentThrottle() > 0.0) {
            if (this.blockWithin(3, -2)) { this.setCurrentThrottle(this.getCurrentThrottle() * 0.8); }
            if (this.isExploded()) { this.setCurrentThrottle(this.getCurrentThrottle() * 0.98); }
        }
        if (this.simulates()) {
            boolean fold = PlaneFlightModel.physics(this, this.params(), this.getNozzleRotation(), this.wingFactor(),
                    this.isGunnerMode(), this.canFloatWater(), this.planeInfo.isTargetDrone && this.canUseFuel() && !this.isDestroyed());
            if (fold && this.canFoldLandingGear()) { this.foldLandingGear(); }
            this.breakSoftBlocks();
        }
        if (this.level().isClientSide()) {
            if (this.isDestroyed()) {
                PlaneVisualMotion.destroyed(this, this.blockWithin(3, -3), this.rotDestroyedPitch, this.rotDestroyedRoll);
            }
            float target = (float) this.getCurrentThrottle();
            if (this.getPilot() != null && (this.partCanopy == null || this.partCanopy.rotation < 1.0F)) { target += 0.1F; }
            this.soundVolume = this.soundVolume < target ? Math.min(target, this.soundVolume + 0.02F)
                    : Math.max(target, this.soundVolume - 0.02F);
            this.onUpdateParticleSandCloud(true);
            this.updatePlaneParticles();
        }
    }

    @Override
    public void destroyAircraft() {
        super.destroyAircraft();
        this.rotDestroyedRoll = (0.5F + this.random.nextFloat()) * PlaneVisualMotion.destroyedRollDirection(this.getRoll());
    }

    private void updatePlaneParticles() {
        var seat = this.getSeatInfo(0);
        PlaneParticles.Frame frame = new PlaneParticles.Frame(this.position(), new Vec3(this.xo, this.yo, this.zo),
                this.getDeltaMovement(), this.getYaw(), this.getPitch(), this.getRoll(), this.getNozzleRotation(),
                this.getCurrentThrottle(), this.getHP(), this.getMaxHP(), this.getBbWidth(), this.getBoundingBox().minY,
                seat != null ? seat.pos : null);
        this.particles.tick(this.planeInfo, frame, this.random, new PlaneParticles.World() {
            @Override
            public boolean isAir(int x, int y, int z) {
                return level().getBlockState(new BlockPos(x, y, z)).isAir();
            }
            @Override
            public boolean isWater(int x, int y, int z) {
                return GroundProbe.isWater(level().getBlockState(new BlockPos(x, y, z)));
            }
        }, new PlaneParticles.Sink() {
            @Override
            public void accept(ParticleParam param) {
                McheliEffects.spawnParticle(level(), param);
            }
            @Override
            public void tileCrack(int bx, int by, int bz, double x, double y, double z,
                    double mx, double my, double mz) {
                McheliEffects.spawnTileCrack(level(), bx, by, bz, x, y, z, mx, my, mz);
            }
        });
    }

    @Override
    public float getUnfoldLandingGearThrottle() { return 0.7F; }
    @Override
    protected double getSpeedLimitForChecks() {
        // Upstream onUpdateAircraft calls onUpdate_Server once per tick.
        return this.planeInfo != null ? this.params().speedLimit(this.wingFactor()) : 1.0;
    }
    @Override
    public double motionX() { return this.getDeltaMovement().x; }
    @Override
    public double motionY() { return this.getDeltaMovement().y; }
    @Override
    public double motionZ() { return this.getDeltaMovement().z; }
    @Override
    public void setMotion(double x, double y, double z) { this.setDeltaMovement(x, y, z); }
    @Override
    public double currentThrottle() { return this.getCurrentThrottle(); }
    @Override
    public float throttleBack() { return this.throttleBack; }
    @Override
    public void setThrottleBack(float throttle) { this.throttleBack = throttle; }
    @Override
    public double currentSpeed() { return this.currentSpeed; }
    @Override
    public void setCurrentSpeed(double speed) { this.currentSpeed = speed; }
    @Override
    public boolean inWater() { return this.isInWater(); }
    @Override
    public double waterDepth() { return this.getWaterDepth(); }
    @Override
    public void move(double x, double y, double z) { this.move(MoverType.SELF, new Vec3(x, y, z)); }
    @Override
    public boolean groundIsSolid() {
        boolean[] solid = {false};
        GroundProbe.find((x, y, z) -> {
            var state = this.level().getBlockState(new BlockPos(x, y, z));
            if (state.isAir()) { return false; }
            solid[0] = !GroundProbe.isWater(state);
            return true;
        }, this.level().getMinY(), this.level().getMaxY(), this.getX(), this.getY(), this.getZ(), 3, -2);
        return solid[0];
    }

    @Override
    public int getLastPartStatusMask() {
        return super.getLastPartStatusMask() | 1 << 1 | 1 << 3;
    }

    @Override
    protected void addAdditionalSaveData(ValueOutput output) {
        super.addAdditionalSaveData(output);
        output.putDouble("PlaneThrottle", this.getCurrentThrottle());
        output.putFloat("PlaneRotor", this.rotationRotor);
    }

    @Override
    protected void readAdditionalSaveData(ValueInput input) {
        this.loading = true;
        try {
            super.readAdditionalSaveData(input);
        } finally {
            this.loading = false;
        }
        this.setCurrentThrottle(input.getDoubleOr("PlaneThrottle", 0.0));
        this.rotationRotor = this.prevRotationRotor = input.getFloatOr("PlaneRotor", 0.0F);
        for (AircraftPart part : new AircraftPart[] {this.partNozzle, this.partWing}) {
            if (part != null) {
                boolean on = (this.partStatus() & 1 << (part == this.partNozzle ? 1 : 3)) != 0;
                part.setStatusServer(on);
                part.rotation = part.prevRotation = on ? part.rotationMax : 0.0F;
            }
        }
    }
}
