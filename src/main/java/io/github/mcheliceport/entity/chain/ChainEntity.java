package io.github.mcheliceport.entity.chain;

import io.github.mcheliceport.entity.aircraft.AircraftEntity;
import java.util.UUID;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundSource;
import net.minecraft.resources.Identifier;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MoverType;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.portal.TeleportTransition;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

/** Two distinct synchronized endpoints; the first selected entity follows the second. */
public class ChainEntity extends Entity {
    private static final EntityDataAccessor<Integer> TOWED = SynchedEntityData.defineId(ChainEntity.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<Integer> TOW = SynchedEntityData.defineId(ChainEntity.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<Integer> LENGTH = SynchedEntityData.defineId(ChainEntity.class, EntityDataSerializers.INT);
    private @Nullable Entity towed, tow;
    private @Nullable UUID towedId, towId;
    private int searchTicks = 50;

    public ChainEntity(EntityType<? extends ChainEntity> type, Level level) { super(type, level); }
    @Override protected void defineSynchedData(SynchedEntityData.Builder builder) {
        builder.define(TOWED, 0); builder.define(TOW, 0); builder.define(LENGTH, 4);
    }
    public int length() { return this.entityData.get(LENGTH); }
    public void setLength(int length) { this.entityData.set(LENGTH, Math.clamp(length, 3, 15)); }
    public @Nullable Entity towed() { return this.endpoint(true); }
    public @Nullable Entity tow() { return this.endpoint(false); }
    private @Nullable Entity endpoint(boolean first) {
        Entity result = first ? this.towed : this.tow;
        int id = this.entityData.get(first ? TOWED : TOW);
        if (this.level().isClientSide() && (result == null || result.isRemoved() || result.getId() != id)) {
            result = this.level().getEntity(id);
            if (first) { this.towed = result; } else { this.tow = result; }
        }
        return result != null && !result.isRemoved() ? result : null;
    }
    public boolean connects(Entity entity) { return !this.isRemoved() && (this.towed() == entity || this.tow() == entity); }
    public @Nullable Entity other(Entity entity) { return this.towed() == entity ? this.tow() : this.tow() == entity ? this.towed() : null; }
    public static @Nullable ChainEntity attached(Entity entity) {
        for (var chain : entity.level().getEntitiesOfClass(ChainEntity.class, entity.getBoundingBox().inflate(32))) {
            if (chain.connects(entity)) { return chain; }
        }
        return null;
    }
    public static boolean linked(Entity entity) { return attached(entity) != null; }
    public static boolean linked(Entity a, Entity b) {
        var chain = attached(a); return chain != null && chain.other(a) == b;
    }
    /** Bind only after a successful spawn, so cancelled creation cannot change either vehicle. */
    public boolean connect(Entity towed, Entity tow) {
        if (this.level().isClientSide() || towed == tow || towed.level() != this.level() || tow.level() != this.level()
                || !io.github.mcheliceport.item.ChainItem.canBeTowed(towed) || !io.github.mcheliceport.item.ChainItem.eligible(tow)
                || linked(towed) || linked(tow)) { return false; }
        this.towed = towed; this.tow = tow; this.towedId = towed.getUUID(); this.towId = tow.getUUID();
        this.entityData.set(TOWED, towed.getId()); this.entityData.set(TOW, tow.getId()); this.searchTicks = 0;
        if (towed instanceof AircraftEntity aircraft) { aircraft.setTowed(true); }
        this.rememberPartner();
        this.setPos(tow.position().add(0, 2, 0));
        return true;
    }
    @Override public void tick() {
        super.tick();
        if (this.level().isClientSide()) {
            var tug = this.tow(); if (tug != null) { this.setPos(tug.position().add(0, 2, 0)); }
            return;
        }
        var level = (ServerLevel) this.level();
        if (this.searchTicks > 0) {
            Entity first = this.towedId == null ? null : level.getEntity(this.towedId);
            Entity second = this.towId == null ? null : level.getEntity(this.towId);
            if (first != null && second != null && this.connect(first, second)) { return; }
            if (--this.searchTicks == 0) { this.discard(); }
            return;
        }
        Entity first = this.towed(), second = this.tow();
        if (first == null || second == null || !io.github.mcheliceport.item.ChainItem.canBeTowed(first)
                || !io.github.mcheliceport.item.ChainItem.eligible(second)) { this.discard(); return; }
        first.resetFallDistance();
        this.setPos(second.position().add(0, 2, 0));
        Vec3 delta = first.position().subtract(second.position());
        double distance = delta.length();
        if (distance <= this.length()) { return; }
        float yaw = (float) Math.toDegrees(Math.atan2(delta.z, delta.x)) + 90;
        if (first instanceof AircraftEntity aircraft) { aircraft.setYaw(yaw); }
        else { first.setYRot(yaw); }
        first.yRotO = yaw;
        // Upstream's weak pull, with its length+2 hard limit. Use collision-aware movement at the limit.
        first.setDeltaMovement(first.getDeltaMovement().subtract(delta.scale(0.001F / Math.max(0.1, distance - this.length()))));
        if (distance > this.length() + 2) {
            Vec3 target = second.position().add(delta.scale((this.length() + 2) / distance));
            first.move(MoverType.SELF, target.subtract(first.position()));
            first.hurtMarked = true;
            if (first.position().distanceTo(target) > 0.01) { this.discard(); }
        }
    }
    @Override public void remove(RemovalReason reason) {
        if (this.isRemoved()) { return; }
        Entity first = this.towed;
        if (!this.level().isClientSide()) { this.rememberPartner(); }
        super.remove(reason);
        if (!this.level().isClientSide()) {
            if (first instanceof AircraftEntity aircraft) {
                aircraft.setTowed(false);
                if (!aircraft.isRemoved() && !aircraft.isPassenger() && reason.shouldDestroy()) {
                    aircraft.teleport(new TeleportTransition((ServerLevel) this.level(), aircraft.position(),
                            aircraft.getDeltaMovement(), aircraft.getYaw(), aircraft.getPitch(), TeleportTransition.DO_NOTHING));
                }
            }
            if (reason.shouldDestroy() && this.towedId != null) { sound(this, "chain_ct"); }
        }
        this.towed = null; this.tow = null;
    }
    private void rememberPartner() {
        if (this.towed == null || this.tow == null) { return; }
        if (this.towed instanceof io.github.mcheliceport.entity.tank.TankEntity tank) { tank.rememberCarrier(this.tow); }
        if (this.tow instanceof io.github.mcheliceport.entity.tank.TankEntity tank) { tank.rememberCarrier(this.towed); }
    }
    public static void sound(Entity entity, String name) {
        entity.level().playSound(null, entity.blockPosition(), SoundEvent.createVariableRangeEvent(
                Identifier.fromNamespaceAndPath("mcheli", name)), SoundSource.PLAYERS, 1, 1);
    }
    @Override public boolean hurtServer(ServerLevel level, DamageSource source, float damage) { return false; }
    @Override public boolean isIgnoringBlockTriggers() { return true; }
    @Override public boolean shouldRenderAtSqrDistance(double distance) { return distance < 600 * 600; }
    @Override protected void addAdditionalSaveData(ValueOutput output) {
        if (this.towedId != null && this.towId != null) {
            output.putString("TowedEntityUUID", this.towedId.toString()); output.putString("TowEntityUUID", this.towId.toString());
        }
        output.putInt("ChainLength", this.length());
    }
    @Override protected void readAdditionalSaveData(ValueInput input) {
        this.towedId = parseUuid(input.getStringOr("TowedEntityUUID", ""));
        this.towId = parseUuid(input.getStringOr("TowEntityUUID", ""));
        this.setLength(input.getIntOr("ChainLength", 4)); this.searchTicks = 50;
    }
    private static @Nullable UUID parseUuid(String value) {
        try { return UUID.fromString(value); } catch (IllegalArgumentException ignored) { return null; }
    }
}
