package io.github.mcheliceport.entity.vehicle;

import io.github.mcheliceport.config.CommonConfig;
import io.github.mcheliceport.content.ContentRegistries;
import io.github.mcheliceport.content.ContentType;
import io.github.mcheliceport.content.aircraft.AircraftInfo;
import io.github.mcheliceport.content.aircraft.VehicleInfo;
import io.github.mcheliceport.entity.ground.GroundVehicleEntity;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

/** Placed guns/launchers and addon vehicles; movement permissions come from their content. */
public class VehicleEntity extends GroundVehicleEntity {
    private Vec3 fixedPosition;
    public VehicleEntity(EntityType<? extends VehicleEntity> type, Level level) { super(type, level); }
    @Override protected @Nullable AircraftInfo lookupInfo(String name) { return ContentRegistries.get().vehicle().get(name); }
    @Override public ContentType getContentType() { return ContentType.VEHICLE; }
    public @Nullable VehicleInfo getVehicleInfo() { return (VehicleInfo) this.getAcInfo(); }
    @Override public boolean isCameraView(@Nullable Entity viewer) { return true; }
    @Override public boolean isViewFromCamera(@Nullable Entity viewer) { return this.isMountedEntity(viewer); }
    @Override public void setAttitudeFromPilot(float yaw, float pitch, float roll) {
        VehicleInfo info = this.getVehicleInfo();
        super.setAttitudeFromPilot(info != null && info.isEnableRot ? yaw : this.getYaw(), this.getPitch(), this.getRoll());
    }
    @Override protected double getSpeedLimitForChecks() {
        VehicleInfo info = this.getVehicleInfo();
        return info != null && info.isEnableMove ? info.speed : 0;
    }
    @Override public void zoomCamera() {
        if (this.canZoom()) {
            float z = this.camera.getCameraZoom() + 1;
            this.camera.setCameraZoom(z <= this.getZoomMax() + 0.01 ? z : 1);
        }
    }

    @Override protected void onUpdateAircraft() {
        if (this.fixedPosition == null || this.getCountOnUpdate() < 200) { this.fixedPosition = this.position(); }
        super.onUpdateAircraft();
        if (this.getCountOnUpdate() >= 200 && CommonConfig.FIX_VEHICLE_AT_PLACED_POINT.get()) {
            this.stopRiding();
            this.setDeltaMovement(Vec3.ZERO);
            this.setPos(this.position().add(this.fixedPosition).scale(0.5));
        }
    }
}
