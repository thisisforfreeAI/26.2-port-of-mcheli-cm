package io.github.mcheliceport.entity.seat;

import io.github.mcheliceport.content.aircraft.SeatRackInfo;
import io.github.mcheliceport.entity.aircraft.AircraftEntity;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

/**
 * A crew seat of a vehicle (every seat but the pilot's): upstream's {@code MCH_EntitySeat}.
 *
 * <p>Upstream's seats were free-standing entities that found their vehicle again through a saved
 * unique ID, and were deleted after 1,200 ticks without one. The port's seats are passengers of
 * their vehicle instead, so vanilla saves, loads, tracks and positions them together with it; a
 * seat that finds itself without a vehicle removes itself at once.
 */
public class SeatEntity extends Entity {

    private static final EntityDataAccessor<Integer> DATA_SEAT_ID =
            SynchedEntityData.defineId(SeatEntity.class, EntityDataSerializers.INT);

    private @Nullable Entity lastRider;
    private int orphanTicks;

    public SeatEntity(EntityType<? extends SeatEntity> type, Level level) {
        super(type, level);
        this.noPhysics = true;
    }

    @Override
    protected void defineSynchedData(SynchedEntityData.Builder builder) {
        builder.define(DATA_SEAT_ID, -1);
    }

    /** The seat's index among the vehicle's crew seats: seat ID 1 is index 0 (upstream {@code seatID}). */
    public int getSeatIndex() {
        return this.entityData.get(DATA_SEAT_ID);
    }

    public void setSeatIndex(int index) {
        this.entityData.set(DATA_SEAT_ID, index);
    }

    public @Nullable AircraftEntity getParent() {
        return this.getVehicle() instanceof AircraftEntity aircraft ? aircraft : null;
    }

    public @Nullable Entity getRider() {
        return this.getFirstPassenger();
    }

    @Override
    public void tick() {
        super.tick();
        this.resetFallDistance();
        Entity rider = this.getRider();
        if (rider != null) {
            rider.resetFallDistance();
        }
        AircraftEntity parent = this.getParent();
        if (parent == null) {
            if (!this.level().isClientSide() && ++this.orphanTicks > 1) {
                this.ejectPassengers();
                this.discard();
            }
            return;
        }
        this.orphanTicks = 0;
        if (this.lastRider == null && rider != null) {
            parent.onMountPlayerSeat(this, rider);
        } else if (this.lastRider != null && rider == null) {
            parent.onUnmountPlayerSeat(this, this.lastRider);
        }
        this.lastRider = rider;
    }

    @Override
    protected void positionRider(Entity passenger, Entity.MoveFunction moveFunction) {
        // Upstream updatePosition: the rider stands at the seat's position.
        moveFunction.accept(passenger, this.getX(), this.getY(), this.getZ());
        passenger.setDeltaMovement(Vec3.ZERO);
        var parent = this.getParent();
        if (parent != null && parent.getSeatInfo(this.getSeatIndex() + 1) instanceof SeatRackInfo rack) {
            if (passenger instanceof AircraftEntity cargo) {
                var q = io.github.mcheliceport.util.McheliMath.worldOrientationQuat(parent.getYaw(), parent.getPitch(), parent.getRoll())
                        .mul(io.github.mcheliceport.util.McheliMath.worldOrientationQuat(rack.fixYaw, rack.fixPitch, 0));
                var euler = io.github.mcheliceport.util.McheliMath.worldEuler(q);
                cargo.setAttitudeFromPilot(euler[1], euler[0], euler[2]);
                cargo.updateHitBoxPosition();
            }
            else { passenger.setYRot(parent.getYaw() + rack.fixYaw); passenger.setXRot(rack.fixPitch); }
        }
    }

    /** Place a newly attached load immediately, before the normal passenger traversal. */
    public void positionCargo() {
        var parent = this.getParent();
        if (parent != null) { this.setPos(parent.getSeatPosition(this.getSeatIndex() + 1)); }
        if (this.getRider() != null) { this.positionRider(this.getRider()); }
    }

    @Override
    public Vec3 getDismountLocationForPassenger(LivingEntity passenger) {
        AircraftEntity parent = this.getParent();
        if (parent != null) {
            Vec3 pos = parent.getUnmountPosition(passenger, this.getSeatIndex() + 1);
            if (pos != null) {
                return pos;
            }
        }
        return super.getDismountLocationForPassenger(passenger);
    }

    @Override
    protected boolean canAddPassenger(Entity passenger) {
        return this.getPassengers().isEmpty();
    }

    /** Upstream {@code canRideMob}: racks carry vehicles, not riders. */
    public boolean canRideMob() {
        AircraftEntity parent = this.getParent();
        return parent != null && this.getSeatIndex() >= 0
                && !(parent.getSeatInfo(this.getSeatIndex() + 1) instanceof SeatRackInfo);
    }

    @Override
    public boolean hurtServer(ServerLevel level, DamageSource source, float damage) {
        AircraftEntity parent = this.getParent();
        return parent != null && parent.hurtServer(level, source, damage);
    }

    @Override
    public InteractionResult interact(Player player, InteractionHand hand, Vec3 location) {
        AircraftEntity parent = this.getParent();
        if (parent == null || parent.isDestroyed() || parent.notOnSameTeam(player)) {
            return InteractionResult.PASS;
        }
        ItemStack stack = player.getItemInHand(hand);
        if (AircraftEntity.isParentInteractItem(stack)) {
            return parent.interact(player, hand, location);
        }
        if (this.getRider() != null || player.getVehicle() != null || !this.canRideMob()) {
            return InteractionResult.PASS;
        }
        if (!this.level().isClientSide()) {
            player.startRiding(this);
        }
        return InteractionResult.SUCCESS;
    }

    @Override
    public boolean isPickable() {
        return !this.isRemoved();
    }

    @Override
    public boolean canBeCollidedWith(@Nullable Entity other) {
        return false;
    }

    @Override
    public boolean isIgnoringBlockTriggers() {
        return true;
    }

    @Override
    public @Nullable ItemStack getPickResult() {
        AircraftEntity parent = this.getParent();
        return parent != null ? parent.getPickResult() : null;
    }

    @Override
    protected void readAdditionalSaveData(ValueInput input) {
        this.setSeatIndex(input.getIntOr("SeatID", -1));
    }

    @Override
    protected void addAdditionalSaveData(ValueOutput output) {
        output.putInt("SeatID", this.getSeatIndex());
    }
}
