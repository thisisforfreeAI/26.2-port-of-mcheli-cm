package io.github.mcheliceport.entity.aircraft;

import io.github.mcheliceport.config.CommonConfig;
import io.github.mcheliceport.content.aircraft.SeatRackInfo;
import io.github.mcheliceport.entity.container.CargoContainerEntity;
import io.github.mcheliceport.entity.parachute.ParachuteEntity;
import io.github.mcheliceport.entity.seat.SeatEntity;
import java.util.Arrays;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

/** Upstream rack matching, loading and reverse-order unloading, using the saved passenger tree. */
public final class AircraftRacks {
    private final AircraftEntity ac;
    private int lastHintTick = -100;
    private boolean rideHint;
    AircraftRacks(AircraftEntity ac) { this.ac = ac; }

    public static @Nullable SeatEntity rackSeat(Entity cargo) {
        if (cargo.getVehicle() instanceof SeatEntity seat && seat.getParent() != null
                && seat.getParent().getSeatInfo(seat.getSeatIndex() + 1) instanceof SeatRackInfo) { return seat; }
        return null;
    }
    public boolean canLoad() {
        return !this.ac.isPassenger() && !this.ac.isDestroyed() && Arrays.stream(this.ac.getSeats()).anyMatch(s ->
                s != null && s.getRider() == null && this.ac.getSeatInfo(s.getSeatIndex() + 1) instanceof SeatRackInfo);
    }
    public boolean canUnload() {
        return Arrays.stream(this.ac.getSeats()).anyMatch(s -> s != null && s.getRider() != null
                && this.ac.getSeatInfo(s.getSeatIndex() + 1) instanceof SeatRackInfo);
    }
    /** Zero-based crew-seat index, as upstream's canRideAircraft. */
    public static boolean accepts(AircraftEntity carrier, int index, Entity cargo) {
        if (cargo instanceof io.github.mcheliceport.entity.rappel.RappellingEntity
                || io.github.mcheliceport.entity.chain.ChainEntity.linked(carrier)
                || io.github.mcheliceport.entity.chain.ChainEntity.linked(cargo)
                || carrier == cargo || carrier.isRemoved() || carrier.isDestroyed() || carrier.isPassenger()
                || cargo.isRemoved() || cargo.isPassenger() || carrier.level() != cargo.level()
                || index < 0 || index >= carrier.getSeats().length
                || !(carrier.getSeatInfo(index + 1) instanceof SeatRackInfo rack)) { return false; }
        SeatEntity seat = carrier.getSeats()[index];
        if (seat == null || seat.isRemoved() || seat.getRider() != null || !carrier.canRideSeatOrRack(index + 1, cargo)) { return false; }
        if (cargo instanceof CargoContainerEntity) {
            return Arrays.stream(rack.names).anyMatch(n -> n.equalsIgnoreCase("container"));
        }
        if (cargo instanceof AircraftEntity child) {
            var info = child.getAcInfo(); var host = carrier.getAcInfo();
            if (info == null || host == null || child.isDestroyed()) { return false; }
            for (var childSeat : child.getSeats()) {
                if (childSeat != null && (childSeat.getRider() instanceof AircraftEntity
                        || childSeat.getRider() instanceof CargoContainerEntity)) { return false; }
            }
            if (Arrays.stream(rack.names).anyMatch(n -> n.equalsIgnoreCase(info.name) || n.equalsIgnoreCase(info.getKindName()))) { return true; }
            return info.rideRacks.stream().anyMatch(r -> r.name().equalsIgnoreCase(host.name)
                    && host.getNumSeat() - 1 + r.rackID() - 1 == index);
        }
        return cargo.getPersistentData().getBooleanOr("CanMountEntity", false);
    }
    /** Carrier loads every available rack in content order. */
    public int load() {
        if (!(this.ac.level() instanceof ServerLevel) || !this.canLoad()) { return 0; }
        if (!CommonConfig.ENABLE_PUT_RACK_IN_FLYING.get()
                && (this.ac.getCurrentThrottle() > 0.3 || !this.ac.blockWithin(1, -3))) { return 0; }
        int loaded = 0;
        for (int i = 0; i < this.ac.getSeats().length; i++) {
            if (!(this.ac.getSeatInfo(i + 1) instanceof SeatRackInfo rack)) { continue; }
            Vec3 entry = this.ac.getTransformedPosition(rack.getEntryPos());
            for (var cargo : this.ac.level().getEntities(this.ac, new AABB(entry, entry).inflate(rack.range))) {
                if (accepts(this.ac, i, cargo) && cargo.startRiding(this.ac.getSeats()[i], false, true)) {
                    this.ac.getSeats()[i].positionCargo(); loaded++; break;
                }
            }
        }
        if (loaded > 0) { this.click(); }
        return loaded;
    }
    private @Nullable SeatEntity nearbyRack() {
        if (this.ac.isPassenger() || this.ac.isDestroyed()) { return null; }
        for (var carrier : this.ac.level().getEntitiesOfClass(AircraftEntity.class, this.ac.getBoundingBox().inflate(60))) {
            for (int i = 0; i < carrier.getSeats().length; i++) {
                if (!accepts(carrier, i, this.ac)) { continue; }
                var rack = (SeatRackInfo) carrier.getSeatInfo(i + 1);
                Vec3 entry = carrier.getTransformedPosition(rack.getEntryPos());
                Vec3 d = this.ac.position().subtract(entry);
                if (Math.abs(d.x) <= rack.range && Math.abs(d.y) <= rack.range && Math.abs(d.z) <= rack.range) { return carrier.getSeats()[i]; }
            }
        }
        return null;
    }
    public boolean canRide() {
        if (!this.ac.level().isClientSide()) { return this.nearbyRack() != null; }
        if (Math.abs(this.ac.tickCount - this.lastHintTick) >= 10) {
            this.lastHintTick = this.ac.tickCount; this.rideHint = this.nearbyRack() != null;
        }
        return !this.ac.isPassenger() && this.rideHint;
    }
    /** Self-boarding retains upstream's independent path; the carrier loading-flight switch does not gate it. */
    public boolean ride() {
        if (this.ac.level().isClientSide()) { return false; }
        SeatEntity seat = this.nearbyRack();
        if (seat == null || !this.ac.startRiding(seat, false, true)) { return false; }
        seat.positionCargo(); this.click(); return true;
    }
    public boolean unload() {
        if (this.ac.level().isClientSide() || this.ac.isDestroyed() || this.ac.isPassenger()) { return false; }
        for (int i = this.ac.getSeats().length - 1; i >= 0; i--) {
            var seat = this.ac.getSeats()[i];
            if (seat != null && seat.getRider() != null && this.ac.getSeatInfo(i + 1) instanceof SeatRackInfo) { return release(seat, true); }
        }
        return false;
    }
    public boolean leave() {
        if (!this.ac.level().isClientSide() && this.ac.getVehicle() instanceof ParachuteEntity chute) {
            Vec3 pos = this.ac.position(), velocity = chute.getDeltaMovement();
            this.ac.stopRiding();
            if (this.ac.isPassenger()) { return false; }
            if (this.ac.teleport(new net.minecraft.world.level.portal.TeleportTransition((ServerLevel) this.ac.level(), pos, velocity,
                    this.ac.getYaw(), this.ac.getPitch(), net.minecraft.world.level.portal.TeleportTransition.DO_NOTHING)) == null) {
                this.ac.startRiding(chute, true, true); chute.positionRider(this.ac); return false;
            }
            this.ac.hurtMarked = true;
            return true;
        }
        SeatEntity seat = rackSeat(this.ac);
        return seat != null && !this.ac.level().isClientSide() && release(seat, false);
    }
    private static boolean release(SeatEntity seat, boolean carrierRequest) {
        var carrier = seat.getParent(); var cargo = seat.getRider();
        if (carrier == null || cargo == null || !(carrier.level() instanceof ServerLevel level)) { return false; }
        var rack = (SeatRackInfo) carrier.getSeatInfo(seat.getSeatIndex() + 1);
        Vec3 local = rack.unmountPos != null ? rack.unmountPos : rack.getEntryPos();
        if (carrierRequest && rack.unmountPos == null && cargo instanceof AircraftEntity && carrier.getAcInfo() != null) {
            local = local.add(0, 0, local.z >= carrier.getAcInfo().bbZ ? 12 : -12);
        }
        Vec3 pos = carrier.getTransformedPosition(local);
        if (!level.noCollision(cargo, cargo.getBoundingBox().move(pos.subtract(cargo.position())).deflate(1e-5))) { return false; }
        // Spawn/mount the chute first; a refused spawn leaves cargo attached to the rack.
        if (carrierRequest && !carrier.blockWithin(3, -20)) {
            if (ParachuteEntity.deploy(level, cargo, 3, pos, carrier.getDeltaMovement()) == null) { return false; }
        } else {
            cargo.stopRiding();
            if (cargo.isPassenger()) { return false; }
            if (cargo.teleport(new net.minecraft.world.level.portal.TeleportTransition(level, pos, carrier.getDeltaMovement(),
                    cargo.getYRot(), cargo.getXRot(), net.minecraft.world.level.portal.TeleportTransition.DO_NOTHING)) == null) {
                cargo.startRiding(seat, true, true); seat.positionCargo(); return false;
            }
            cargo.fallDistance = carrier.fallDistance; cargo.hurtMarked = true;
        }
        if (cargo instanceof io.github.mcheliceport.entity.tank.TankEntity tank) { tank.rememberCarrier(carrier); }
        if (cargo instanceof AircraftEntity child) { child.setAttitudeFromPilot(carrier.getYaw() + rack.fixYaw, rack.fixPitch, 0); }
        carrier.racks().click(); return true;
    }
    private void click() {
        this.ac.level().playSound(null, this.ac.blockPosition(), SoundEvents.UI_BUTTON_CLICK.value(), SoundSource.PLAYERS, 1, 1);
    }
}
