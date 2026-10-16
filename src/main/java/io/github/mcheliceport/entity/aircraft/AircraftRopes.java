package io.github.mcheliceport.entity.aircraft;

import io.github.mcheliceport.content.aircraft.SeatRackInfo;
import io.github.mcheliceport.entity.rappel.RappellingEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.portal.TeleportTransition;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import net.minecraft.world.phys.Vec3;

/** Upstream deployment delay, round-robin player hooks and scheduled non-player crew descent. */
public final class AircraftRopes {
    private final AircraftEntity ac;
    private int lastHook;
    public AircraftRopes(AircraftEntity aircraft) { this.ac = aircraft; }
    public boolean active() { return this.ac.getCommonStatus(AircraftEntity.STATUS_REPELLING); }
    public boolean ready() { return this.active() && this.ac.ropeTicks() > 50; }
    public boolean supported() { return this.ac.getAcInfo() != null && this.ac.getAcInfo().haveRepellingHook(); }
    public boolean canStart() {
        return this.supported() && !this.ac.isDestroyed() && !this.ac.isPassenger() && !this.ac.isTowed()
                && this.ac.isHovering() && Math.abs(this.ac.getPitch()) < 3 && Math.abs(this.ac.getRoll()) < 3
                && this.ac.oldestPosition().distanceTo(this.ac.position()) < .3 && !this.ac.blockWithin(3, -4);
    }
    public boolean toggle() {
        if (this.ac.level().isClientSide()) { return false; }
        if (this.active()) { this.stop(); return true; }
        if (!this.canStart()) { return false; }
        this.ac.setCommonStatus(AircraftEntity.STATUS_REPELLING, true);
        this.ac.setRopeState(0, 0); this.clearControls(); return true;
    }
    public void stop() {
        if (this.ac.level().isClientSide()) { return; }
        boolean wasActive = this.active();
        this.ac.setCommonStatus(AircraftEntity.STATUS_REPELLING, false); this.ac.setRopeState(0, 0);
        if (wasActive && !this.ac.isRemoved() && !this.ac.isPassenger()) {
            this.ac.teleport(new TeleportTransition((ServerLevel) this.ac.level(), this.ac.position(),
                    this.ac.getDeltaMovement(), this.ac.getYaw(), this.ac.getPitch(), TeleportTransition.DO_NOTHING));
        }
    }
    public void clearControls() {
        this.ac.throttleUp = this.ac.throttleDown = this.ac.moveLeft = this.ac.moveRight = false;
    }
    public Vec3 hook(int index) { return this.ac.getTransformedPosition(this.ac.getAcInfo().repellingHooks.get(index).pos()); }
    public void tick() {
        if (!this.active()) { return; }
        this.clearControls();
        if (this.ac.level().isClientSide()) { return; }
        if (!this.supported() || this.ac.isDestroyed() || this.ac.isPassenger() || this.ac.isTowed() || !this.ac.isHovering()) {
            this.stop(); return;
        }
        int altitude = 0;
        while (altitude < 50 && this.ac.getY() - altitude >= this.ac.level().getMinY()
                && this.ac.level().getBlockState(BlockPos.containing(this.ac.getX(), this.ac.getY() - altitude, this.ac.getZ())).isAir()) { altitude++; }
        float length = Math.min(Math.min(50, altitude), this.ac.ropeLength() + .25F);
        int ticks = this.ac.ropeTicks();
        this.ac.setRopeState(length, Math.min(60, ticks + 1));
        if (ticks < 60) { return; }
        for (int index = 0; index < this.ac.getAcInfo().repellingHooks.size(); index++) {
            var hook = this.ac.getAcInfo().repellingHooks.get(index);
            if (this.ac.getCountOnUpdate() % Math.max(1, hook.interval()) != 0) { continue; }
            // Upstream starts at getSeat(1): the first crew/gunner seat is deliberately skipped.
            for (int seat = 1; seat < this.ac.getSeats().length; seat++) {
                Entity rider = this.ac.getEntityBySeatId(seat + 1);
                if (rider instanceof LivingEntity && !(rider instanceof Player)
                        && rider.getType() != io.github.mcheliceport.registry.ModEntityTypes.GUNNER.get()
                        && !(this.ac.getSeatInfo(seat + 1) instanceof SeatRackInfo)) {
                    if (this.begin(rider, index, false)) { this.lastHook = index; }
                    break;
                }
            }
        }
    }
    public boolean canDescend(Entity rider) {
        int seat = this.ac.getSeatIdByEntity(rider);
        return this.ready() && !this.ac.isDestroyed() && rider instanceof LivingEntity && seat > 0
                && !(this.ac.getSeatInfo(seat) instanceof SeatRackInfo);
    }
    public boolean descend(Entity rider) {
        if (this.ac.level().isClientSide() || !this.canDescend(rider)) { return false; }
        int next = (this.lastHook + 1) % this.ac.getAcInfo().repellingHooks.size();
        if (!this.begin(rider, next, true)) { return false; }
        this.lastHook = next; return true;
    }
    private boolean begin(Entity rider, int index, boolean manual) {
        var offset = this.ac.getAcInfo().repellingHooks.get(index).pos();
        Vec3 origin = this.ac.oldestPosition();
        Vec3 start = this.ac.getTransformedPosition(offset.x, offset.y, offset.z, origin.x, origin.y, origin.z)
                .add(0, manual ? -4 : -2, 0);
        int seat = this.ac.getSeatIdByEntity(rider);
        if (RappellingEntity.deploy(this.ac, rider, index, start) == null) { return false; }
        this.ac.camera.initCamera(seat, rider); return true;
    }
    public void save(ValueOutput output) {
        output.putBoolean("RopesDeployed", this.active()); output.putFloat("RopeLength", this.ac.ropeLength());
        output.putInt("RopeTicks", this.ac.ropeTicks()); output.putInt("LastRopeHook", this.lastHook);
    }
    public void load(ValueInput input) {
        boolean active = input.getBooleanOr("RopesDeployed", false) && this.supported() && !this.ac.isDestroyed();
        this.ac.setCommonStatus(AircraftEntity.STATUS_REPELLING, active);
        float length = input.getFloatOr("RopeLength", 0);
        this.ac.setRopeState(active && Float.isFinite(length) ? Math.clamp(length, 0, 50) : 0,
                active ? Math.clamp(input.getIntOr("RopeTicks", 0), 0, 60) : 0);
        this.lastHook = Math.max(0, input.getIntOr("LastRopeHook", 0))
                % Math.max(1, this.supported() ? this.ac.getAcInfo().repellingHooks.size() : 1);
    }
}
