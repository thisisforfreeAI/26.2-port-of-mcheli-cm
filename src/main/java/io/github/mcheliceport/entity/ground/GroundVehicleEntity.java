package io.github.mcheliceport.entity.ground;

import io.github.mcheliceport.config.CommonConfig;
import io.github.mcheliceport.content.aircraft.AircraftInfo;
import io.github.mcheliceport.content.aircraft.TankInfo;
import io.github.mcheliceport.content.aircraft.VehicleInfo;
import io.github.mcheliceport.entity.aircraft.AircraftEntity;
import io.github.mcheliceport.entity.aircraft.GroundProbe;
import io.github.mcheliceport.entity.plane.PlaneFlightModel;
import io.github.mcheliceport.net.AircraftStatusRequestPayload;
import net.minecraft.core.BlockPos;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MoverType;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import net.minecraft.world.phys.Vec3;

/** Shared ground lifecycle; physics remain specific to tanks and placed vehicles. */
public abstract class GroundVehicleEntity extends AircraftEntity implements PlaneFlightModel.Body {
    private static final EntityDataAccessor<Boolean> BRAKE = SynchedEntityData.defineId(GroundVehicleEntity.class, EntityDataSerializers.BOOLEAN);
    private static final EntityDataAccessor<Float> REVERSE = SynchedEntityData.defineId(GroundVehicleEntity.class, EntityDataSerializers.FLOAT);
    private float throttleBack;
    private boolean requestedStatus;
    public float steerAngle;
    public float soundVolume;
    public float soundTarget;
    public final GroundAnimation animation = new GroundAnimation();

    protected GroundVehicleEntity(EntityType<? extends GroundVehicleEntity> type, Level level) { super(type, level); }

    @Override protected void defineSynchedData(SynchedEntityData.Builder builder) {
        super.defineSynchedData(builder);
        builder.define(BRAKE, false);
        builder.define(REVERSE, 0.0F);
    }

    public boolean getBrake() { return this.entityData.get(BRAKE); }
    @Override public float maxUpStep() { return this.getAcInfo() != null ? this.getAcInfo().stepHeight : 0; }
    public void setBrake(boolean value) { this.entityData.set(BRAKE, value); }
    @Override public boolean canSwitchGunnerMode() { return false; }
    @Override public boolean canSwitchHoveringMode() { return false; }
    @Override public boolean isOverridePlayerYaw() { return false; }
    @Override public boolean isOverridePlayerPitch() { return false; }
    // Mouse movement aims the weapon; keyboard steering runs once per simulation tick.
    @Override public void setAngles(float x, float y, float seconds) { }
    @Override protected void onUpdateAngles(float seconds) { }

    public boolean cycleCameraView(Entity viewer) {
        if (!this.isPilot(viewer) || this.isDestroyed() || !this.canSwitchCameraPos()) { return false; }
        this.setCameraId(this.getCameraId() + 1);
        return true;
    }

    protected void updateWheels() { }

    @Override protected void onUpdateAircraft() {
        AircraftInfo info = this.getAcInfo();
        if (info == null) { return; }
        if (!this.requestedStatus) {
            this.requestedStatus = true;
            if (this.level().isClientSide() && clientHooks() != null) {
                clientHooks().sendToServer(new AircraftStatusRequestPayload(this.getId()));
            }
        }
        boolean active = this.getPilot() != null && this.getPilot().isAlive() && !this.isDestroyed();
        if (!this.followsPilot() || !this.level().isClientSide()) {
            if (info instanceof TankInfo tank) {
                GroundMotion.tankControl(this, tank, this.throttleUp, this.throttleDown, this.getBrake(),
                        active && this.canUseFuel() && this.isCanopyClose(), CommonConfig.AUTO_THROTTLE_DOWN_TANK.get());
            } else if (!active) { this.setCurrentThrottle(Math.max(0, this.getCurrentThrottle() - 0.00125)); }
        }
        if (this.level().isClientSide() && !this.isPilotLocalClient()) {
            this.throttleBack = this.entityData.get(REVERSE);
            double target = this.getThrottle();
            if (this.getCurrentThrottle() > target) { this.setCurrentThrottle(this.getCurrentThrottle() - 0.005); }
            if (this.getCurrentThrottle() < target) { this.setCurrentThrottle(this.getCurrentThrottle() + 0.005); }
        }
        if (this.simulates()) {
            float yaw = this.getYaw(), pitch = this.getPitch(), roll = this.getRoll();
            if (!this.isDestroyed()) { this.onUpdateAngles(0.05F); }
            if (info instanceof TankInfo tank) {
                GroundMotion.tankPhysics(this, tank, this.canFloatWater(), this::updateWheels);
            } else {
                VehicleInfo vehicle = (VehicleInfo) info;
                this.steerAngle = GroundMotion.vehicleSteer(this.steerAngle, this.moveLeft, this.moveRight);
                GroundMotion.vehiclePhysics(this, vehicle, this.throttleUp, this.throttleDown,
                        this.steerAngle, active, this.canFloatWater());
            }
            this.rotationChanged |= yaw != this.getYaw() || pitch != this.getPitch() || roll != this.getRoll();
            this.breakSoftBlocks();
        } else if (this.level().isClientSide()) { this.updateWheels(); }
        if (!this.level().isClientSide()) {
            this.setThrottle(this.getCurrentThrottle());
            this.entityData.set(REVERSE, this.throttleBack);
        }
        if (this.isDestroyed() && this.getCurrentThrottle() > 0) {
            if (this.blockWithin(3, -2)) { this.setCurrentThrottle(this.getCurrentThrottle() * 0.8); }
            if (this.isExploded()) { this.setCurrentThrottle(this.getCurrentThrottle() * 0.98); }
        }
        if (this.level().isClientSide()) {
            this.animation.tick(info, this.getCurrentThrottle(), this.throttleBack, this.moveLeft, this.moveRight);
            float target = (float) this.getCurrentThrottle();
            if (active && (this.partCanopy == null || this.partCanopy.rotation < 1)) { target += 0.1F; }
            this.soundTarget = !this.moveLeft && !this.moveRight && !this.throttleDown ? this.soundTarget * 0.8F
                    : Math.min(0.75F, this.soundTarget + 0.1F);
            target = Math.max(target, this.soundTarget);
            this.soundVolume = this.soundVolume < target ? Math.min(target, this.soundVolume + 0.02F)
                    : Math.max(target, this.soundVolume - 0.02F);
            this.onUpdateParticleSandCloud(true);
        }
    }

    @Override public double motionX() { return this.getDeltaMovement().x; }
    @Override public double motionY() { return this.getDeltaMovement().y; }
    @Override public double motionZ() { return this.getDeltaMovement().z; }
    @Override public void setMotion(double x, double y, double z) { this.setDeltaMovement(x, y, z); }
    @Override public double currentThrottle() { return this.getCurrentThrottle(); }
    @Override public float throttleBack() { return this.throttleBack; }
    @Override public void setThrottleBack(float value) { this.throttleBack = value; }
    @Override public double currentSpeed() { return this.currentSpeed; }
    @Override public void setCurrentSpeed(double value) { this.currentSpeed = value; }
    @Override public boolean inWater() { return this.isInWater(); }
    @Override public double waterDepth() { return this.getWaterDepth(); }
    @Override public void move(double x, double y, double z) { this.move(MoverType.SELF, new Vec3(x, y, z)); }
    @Override protected double getSpeedLimitForChecks() { return this.getAcInfo() != null ? this.getAcInfo().speed : 1; }
    @Override public boolean groundIsSolid() {
        boolean[] solid = {false};
        GroundProbe.find((x, y, z) -> {
            var state = this.level().getBlockState(new BlockPos(x, y, z));
            if (state.isAir()) { return false; }
            solid[0] = !GroundProbe.isWater(state);
            return true;
        }, this.level().getMinY(), this.level().getMaxY(), this.getX(), this.getY(), this.getZ(), 3, -2);
        return solid[0];
    }

    @Override protected void addAdditionalSaveData(ValueOutput out) {
        super.addAdditionalSaveData(out);
        out.putDouble("GroundThrottle", this.getCurrentThrottle());
        out.putFloat("GroundReverse", this.throttleBack);
        out.putBoolean("GroundBrake", this.getBrake());
    }
    @Override protected void readAdditionalSaveData(ValueInput in) {
        super.readAdditionalSaveData(in);
        this.setCurrentThrottle(Math.clamp(in.getDoubleOr("GroundThrottle", 0), 0, 1));
        this.throttleBack = Math.max(0, in.getFloatOr("GroundReverse", 0));
        this.setBrake(in.getBooleanOr("GroundBrake", false));
    }
}
