package io.github.mcheliceport.entity.rappel;

import io.github.mcheliceport.entity.aircraft.AircraftEntity;
import io.github.mcheliceport.entity.aircraft.AircraftHitBox;
import io.github.mcheliceport.entity.seat.SeatEntity;
import io.github.mcheliceport.registry.ModEntityTypes;
import java.util.UUID;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.InterpolationHandler;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.MoverType;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.portal.TeleportTransition;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

/** Invisible, single-rider, server-driven rope descent; upstream MCH_EntityHide. */
public class RappellingEntity extends Entity {
    private static final EntityDataAccessor<Integer> PARENT = SynchedEntityData.defineId(RappellingEntity.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<Integer> HOOK = SynchedEntityData.defineId(RappellingEntity.class, EntityDataSerializers.INT);
    private final InterpolationHandler interpolation = new InterpolationHandler(this, 13);
    private @Nullable AircraftEntity parent;
    private @Nullable UUID parentUuid;
    private int searchTicks = 50;
    private boolean restored;
    public RappellingEntity(EntityType<? extends RappellingEntity> type, Level level) { super(type, level); }
    @Override protected void defineSynchedData(SynchedEntityData.Builder builder) { builder.define(PARENT, -1); builder.define(HOOK, -1); }
    public int hookIndex() { return this.entityData.get(HOOK); }
    public @Nullable AircraftEntity parent() {
        if (this.level().isClientSide() && this.level().getEntity(this.entityData.get(PARENT)) instanceof AircraftEntity aircraft) { this.parent = aircraft; }
        return this.parent != null && !this.parent.isRemoved() ? this.parent : null;
    }
    public static @Nullable RappellingEntity deploy(AircraftEntity parent, Entity user, int hook, Vec3 position) {
        if (!(parent.level() instanceof ServerLevel level) || !(user instanceof LivingEntity) || !user.isAlive()
                || user.level() != level || !parent.ropes().ready() || hook < 0 || hook >= parent.getAcInfo().repellingHooks.size()) { return null; }
        var helper = new RappellingEntity(ModEntityTypes.HIDE.get(), level);
        helper.parent = parent; helper.parentUuid = parent.getUUID(); helper.searchTicks = 0;
        helper.entityData.set(PARENT, parent.getId()); helper.entityData.set(HOOK, hook);
        helper.snapTo(position.x, position.y, position.z, user.getYRot(), 0);
        Vec3 riderPos = helper.riderPosition(user);
        if (!clearBlocks(level, helper, helper.getBoundingBox()) || !clearBlocks(level, user,
                user.getBoundingBox().move(riderPos.subtract(user.position()))) || !level.addFreshEntity(helper)) { return null; }
        Entity previous = user.getVehicle(); Vec3 previousPos = user.position();
        user.stopRiding();
        if (user.isPassenger()) { helper.discard(); return null; }
        if (!user.startRiding(helper, true, true)) {
            helper.discard(); user.setPos(previousPos);
            if (previous != null && !previous.isRemoved()) { user.startRiding(previous, true, true); previous.positionRider(user); }
            return null;
        }
        user.setDeltaMovement(Vec3.ZERO); user.resetFallDistance(); helper.positionRider(user); return helper;
    }
    private static boolean clearBlocks(Level level, Entity entity, AABB box) {
        for (var shape : level.getBlockCollisions(entity, box.deflate(1e-5))) { if (!shape.isEmpty()) { return false; } }
        return true;
    }
    @Override public InterpolationHandler getInterpolation() { return this.interpolation; }
    @Override public void tick() {
        super.tick(); this.resetFallDistance();
        Entity rider = this.getFirstPassenger(); if (rider != null) { rider.resetFallDistance(); }
        if (this.level().isClientSide()) {
            this.interpolation.interpolate(); return;
        }
        if (rider == null) {
            // Vanilla restores a logging-in player's saved RootVehicle separately from world entities.
            if (this.restored && this.searchTicks > 0) { this.searchTicks--; return; }
            this.discard(); return;
        }
        if (!rider.isAlive()) { this.discard(); return; }
        var level = (ServerLevel) this.level();
        AircraftEntity aircraft = this.parent();
        if (aircraft == null && this.searchTicks > 0) {
            if (this.parentUuid != null && level.getEntity(this.parentUuid) instanceof AircraftEntity found) {
                this.parent = found; aircraft = found; this.entityData.set(PARENT, found.getId()); this.searchTicks = 0;
            } else { this.searchTicks--; this.setDeltaMovement(Vec3.ZERO); return; }
        }
        int hook = this.hookIndex();
        if (aircraft == null || aircraft.isDestroyed() || !aircraft.ropes().active() || aircraft.getAcInfo() == null
                || hook < 0 || hook >= aircraft.getAcInfo().repellingHooks.size()) { this.finish(false); return; }
        if (this.onGround() || this.isInWater()) { this.finish(true); return; }
        Vec3 before = this.position(); Vec3 anchor = aircraft.ropes().hook(hook);
        double vertical = this.getDeltaMovement().y - .03;
        this.move(MoverType.SELF, new Vec3(anchor.x - this.getX(), vertical, anchor.z - this.getZ()));
        Vec3 riderPosition = this.riderPosition(rider);
        if (!clearBlocks(level, rider, rider.getBoundingBox().move(riderPosition.subtract(rider.position())))) {
            this.setPos(before); this.positionRider(rider); this.finish(this.verticalCollisionBelow); return;
        }
        this.positionRider(rider);
        this.setDeltaMovement(0, vertical * .9, 0);
        if (this.horizontalCollision || this.onGround() || Math.abs(this.getY() - anchor.y) > aircraft.ropeLength() + 5) {
            this.finish(this.onGround());
        }
    }
    /** Refused dismounts keep the helper/rider together and retry, rather than leaving a dangling ride. */
    public boolean finish(boolean landing) {
        if (!(this.level() instanceof ServerLevel level)) { return false; }
        Entity rider = this.getFirstPassenger();
        if (rider == null) { this.discard(); return true; }
        if (!rider.isAlive()) {
            rider.stopRiding(); if (rider.isPassenger()) { return false; }
            this.discard(); return true;
        }
        Vec3 target = landing ? this.position().add(0, .1, 0) : rider.position();
        if (!clearBlocks(level, rider, rider.getBoundingBox().move(target.subtract(rider.position())))) {
            target = rider.position();
            if (!clearBlocks(level, rider, rider.getBoundingBox())) { this.setDeltaMovement(Vec3.ZERO); return false; }
        }
        rider.stopRiding();
        if (rider.isPassenger()) { this.setDeltaMovement(Vec3.ZERO); return false; }
        rider.resetFallDistance();
        if (rider.teleport(new TeleportTransition(level, target, Vec3.ZERO, rider.getYRot(), rider.getXRot(), TeleportTransition.DO_NOTHING)) == null) {
            rider.startRiding(this, true, true); this.positionRider(rider); return false;
        }
        this.discard(); return true;
    }
    private Vec3 riderPosition(Entity rider) { return this.position().add(0, -.3F - (rider instanceof Player ? .35 : 0), 0); }
    @Override protected void positionRider(Entity passenger, MoveFunction move) {
        Vec3 position = this.riderPosition(passenger); move.accept(passenger, position.x, position.y, position.z);
        passenger.setDeltaMovement(Vec3.ZERO); passenger.resetFallDistance();
    }
    @Override public Vec3 getDismountLocationForPassenger(LivingEntity passenger) { return passenger.position(); }
    @Override protected boolean canAddPassenger(Entity passenger) { return passenger instanceof LivingEntity && this.getPassengers().isEmpty(); }
    @Override public boolean shouldRiderSit() { return false; }
    @Override public boolean canCollideWith(Entity other) {
        return !(other instanceof LivingEntity || other instanceof SeatEntity || other instanceof AircraftHitBox)
                && other != this.parent() && super.canCollideWith(other);
    }
    @Override public boolean hurtServer(ServerLevel level, DamageSource source, float amount) { return false; }
    @Override public void remove(RemovalReason reason) {
        if (reason.shouldDestroy() && !this.level().isClientSide() && this.getFirstPassenger() != null && !this.isRemoved()) {
            this.finish(false); return;
        }
        super.remove(reason);
    }
    @Override public boolean isIgnoringBlockTriggers() { return true; }
    @Override protected void addAdditionalSaveData(ValueOutput output) {
        if (this.parentUuid != null) { output.putString("RopeParentUUID", this.parentUuid.toString()); }
        output.putInt("RopeHook", this.hookIndex());
    }
    @Override protected void readAdditionalSaveData(ValueInput input) {
        try { this.parentUuid = UUID.fromString(input.getStringOr("RopeParentUUID", "")); }
        catch (IllegalArgumentException ignored) { this.parentUuid = null; }
        this.entityData.set(HOOK, input.getIntOr("RopeHook", -1)); this.parent = null; this.searchTicks = 50; this.restored = true;
    }
}
