package io.github.mcheliceport.entity.tank;

import io.github.mcheliceport.content.ContentRegistries;
import io.github.mcheliceport.content.ContentType;
import io.github.mcheliceport.content.aircraft.AircraftInfo;
import io.github.mcheliceport.content.aircraft.TankInfo;
import io.github.mcheliceport.entity.ground.GroundMotion;
import io.github.mcheliceport.entity.ground.GroundVehicleEntity;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.level.Level;
import org.jspecify.annotations.Nullable;

/** Tracked/wheeled tank hull, with independent rider-operated weapons. */
public class TankEntity extends GroundVehicleEntity {
    private final TankCollisions collisions = new TankCollisions(this);
    private @Nullable TankTerrain terrain;
    private float previousParticleYaw;

    public TankEntity(EntityType<? extends TankEntity> type, Level level) { super(type, level); }
    @Override public void tick() {
        super.tick();
        if (!this.isRemoved() && this.getTankInfo() != null && this.level() instanceof net.minecraft.server.level.ServerLevel server) {
            this.collisions.tick(server);
        }
    }
    public void rememberCarrier(Entity carrier) { this.collisions.rememberCarrier(carrier); }
    @Override protected @Nullable AircraftInfo lookupInfo(String name) { return ContentRegistries.get().tank().get(name); }
    @Override public ContentType getContentType() { return ContentType.TANK; }
    public @Nullable TankInfo getTankInfo() { return (TankInfo) this.getAcInfo(); }
    public @Nullable TankTerrain terrain() { return this.terrain; }
    @Override protected void onTypeChanged(AircraftInfo info) { this.terrain = new TankTerrain(this, (TankInfo) info); }
    @Override protected void updateWheels() { if (this.terrain != null) { this.terrain.move(); } }

    @Override protected void onUpdateAircraft() {
        super.onUpdateAircraft();
        if (this.level().isClientSide() && this.getTankInfo() != null) {
            TankParticles.tick(this, this.getTankInfo(), this.random, this.previousParticleYaw);
            this.previousParticleYaw = this.getYaw();
        }
    }

    @Override protected void onUpdateAngles(float seconds) {
        TankInfo info = this.getTankInfo();
        if (info == null || this.terrain == null) { return; }
        boolean ground = this.blockWithin(3, -3) || info.isFloat && this.getWaterDepth() > 0;
        double speed = this.getDeltaMovement().horizontalDistance();
        this.setPitch(GroundMotion.terrainAngle(this.getPitch(), this.terrain.targetPitch, seconds, ground, speed, false));
        this.setRoll(GroundMotion.terrainAngle(this.getRoll(), this.terrain.targetRoll, seconds, ground, speed, true));
        float gmy = this.blockWithin(3, -3) ? info.mobilityYawOnGround : 1;
        if (!info.canRotOnGround && this.groundIsSolid()) { gmy = 0; }
        if (this.moveLeft == this.moveRight || this.isDestroyed()) { return; }
        int sign = this.moveLeft ? -1 : 1;
        if (info.weightType == 1 && ground) {
            double yaw = Math.toRadians(this.getYaw());
            double signed = -this.motionX() * Math.sin(yaw) + this.motionZ() * Math.cos(yaw);
            if (Math.abs(signed) < 0.02) { signed = (this.throttleUp ? 0.02 : 0) - (this.throttleDown ? 0.02 : 0); }
            if (Math.abs(signed) > 0.001) {
                this.setYaw(this.getYaw() + sign * (float) Math.min(55, 18 + Math.abs(signed) * 90) * seconds
                        * (signed >= 0 ? 1 : -1) * gmy);
            }
        } else {
            double dx = this.getX() - this.xo, dz = this.getZ() - this.zo;
            double distance = dx * dx + dz * dz;
            float pivot = info.pivotTurnThrottle;
            if (pivot <= 0 || this.getCurrentThrottle() >= pivot || this.throttleBack() >= pivot / 10
                    || distance > this.throttleBack() * 0.01) {
                float f = pivot <= 0 ? 1 : (float) Math.sqrt(Math.min(distance, 1));
                float reverse = !this.throttleUp && this.throttleDown && this.getCurrentThrottle() < pivot + 0.05 ? -1 : 1;
                this.setYaw(this.getYaw() + sign * 12 * gmy * seconds * reverse * f);
            }
        }
    }
}
