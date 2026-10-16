package io.github.mcheliceport.entity.parachute;

import io.github.mcheliceport.effect.McheliEffects;
import io.github.mcheliceport.effect.ParticleParam;
import io.github.mcheliceport.registry.ModEntityTypes;
import net.minecraft.core.BlockPos;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.FluidTags;
import net.minecraft.util.Mth;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.InterpolationHandler;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.MoverType;
import net.minecraft.world.entity.vehicle.boat.Boat;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

/** Server-authoritative hand chute (1), ejection seat (2), or transport chute (3). */
public class ParachuteEntity extends Entity {
    private static final EntityDataAccessor<Byte> DATA_MODEL =
            SynchedEntityData.defineId(ParachuteEntity.class, EntityDataSerializers.BYTE);
    /** Upstream's ordinary three interpolation ticks plus its ten-tick parachute extension. */
    private final InterpolationHandler interpolation = new InterpolationHandler(this, 13);
    private double speedMultiplier = 0.07;
    private int groundTicks;

    public ParachuteEntity(EntityType<? extends ParachuteEntity> type, Level level) {
        super(type, level);
    }

    /** Spawn before moving the user, so a refused spawn cannot consume a chute or dismount them. */
    public static @Nullable ParachuteEntity deploy(ServerLevel level, Entity user, int model,
            Vec3 position, Vec3 velocity) {
        if (!user.isAlive()) {
            return null;
        }
        ParachuteEntity chute = new ParachuteEntity(ModEntityTypes.PARACHUTE.get(), level);
        chute.setModelType(model);
        chute.snapTo(position.x, position.y, position.z, user.getYRot(), 0);
        chute.setDeltaMovement(velocity);
        chute.fallDistance = user.fallDistance;
        if (!level.addFreshEntity(chute)) {
            return null;
        }
        Entity previousVehicle = user.getVehicle();
        Vec3 previousPosition = user.position();
        user.stopRiding();
        if (user.isPassenger()) { chute.discard(); return null; }
        if (!user.startRiding(chute, true, true)) {
            chute.discard();
            user.setPos(previousPosition);
            if (previousVehicle != null && !previousVehicle.isRemoved()) {
                user.startRiding(previousVehicle, true, true);
            }
            return null;
        }
        user.resetFallDistance();
        chute.positionRider(user);
        return chute;
    }

    @Override
    protected void defineSynchedData(SynchedEntityData.Builder builder) {
        builder.define(DATA_MODEL, (byte) 0);
    }

    public int getModelType() {
        return this.entityData.get(DATA_MODEL);
    }

    public void setModelType(int model) {
        this.entityData.set(DATA_MODEL, (byte) (model >= 1 && model <= 3 ? model : 0));
    }

    public boolean isOpenParachute() {
        return ParachuteMotion.isOpen(this.getModelType(), this.getDeltaMovement().y);
    }

    @Override
    public InterpolationHandler getInterpolation() {
        return this.interpolation;
    }

    @Override
    public void tick() {
        super.tick();
        if (this.isOpenParachute() && this.getDeltaMovement().y > -0.3 && this.tickCount > 20) {
            this.fallDistance = (float) (this.fallDistance * 0.85);
        }
        if (this.level().isClientSide()) {
            this.tickClient();
            return;
        }
        AABB box = this.getBoundingBox();
        Entity rider = this.getFirstPassenger();
        AABB waterProbe = new AABB(box.minX, box.minY - 0.125, box.minZ,
                box.maxX, box.minY + box.getYsize() / 5.0 - 0.125, box.maxZ);
        if (BlockPos.betweenClosedStream(waterProbe).anyMatch(p -> this.level().getFluidState(p).is(FluidTags.WATER))) {
            this.tryRescueBoat();
            this.discard();
            this.syncReleasedAircraft(rider);
            return;
        }
        if (rider != null && !rider.isAlive()) {
            this.discard();
            return;
        }
        boolean forward = rider instanceof ServerPlayer player ? player.getLastClientInput().forward()
                && !player.getLastClientInput().backward() : rider instanceof LivingEntity living && living.zza > 0;
        ParachuteMotion.Step step = ParachuteMotion.accelerate(this.getModelType(), this.tickCount,
                this.onGround(), this.getDeltaMovement(), this.speedMultiplier, forward,
                rider != null ? rider.getYRot() : this.getYRot());
        this.speedMultiplier = step.speedMultiplier();
        this.setDeltaMovement(step.velocity());
        if (this.onGround() && ++this.groundTicks > 5) {
            this.setPos(this.position().add(0, 1, 0));
            if (rider != null) {
                this.positionRider(rider);
                rider.resetFallDistance();
            }
            this.discard();
            this.syncReleasedAircraft(rider);
            return;
        }
        Vec3 previous = this.position();
        this.move(MoverType.SELF, this.getDeltaMovement());
        this.setDeltaMovement(ParachuteMotion.drag(this.getModelType(), this.tickCount, this.getDeltaMovement()));
        this.setXRot(0);
        double yaw = this.getYRot();
        Vec3 delta = previous.subtract(this.position());
        if (delta.horizontalDistanceSqr() > 0.001) {
            yaw = (float) (Math.atan2(delta.x, delta.z) * 180.0 / Math.PI);
        }
        this.setYRot(rider != null ? rider.getYRot()
                : (float) (this.getYRot() + Mth.clamp(Mth.wrapDegrees(yaw - this.getYRot()), -20, 20)));
        for (Entity entity : this.level().getEntities(this, this.getBoundingBox().inflate(0.2, 0, 0.2),
                e -> e instanceof ParachuteEntity && e.isPushable())) {
            entity.push(this);
        }
        this.applyEffectsFromBlocks();
    }

    /** Returning control to a loaded vehicle also synchronizes its driver and crew positions. */
    private void syncReleasedAircraft(@Nullable Entity rider) {
        if (rider instanceof io.github.mcheliceport.entity.aircraft.AircraftEntity cargo && !cargo.isPassenger()
                && this.level() instanceof ServerLevel server) {
            cargo.teleport(new net.minecraft.world.level.portal.TeleportTransition(server, cargo.position(), Vec3.ZERO,
                    cargo.getYRot(), cargo.getXRot(), net.minecraft.world.level.portal.TeleportTransition.DO_NOTHING));
        }
    }

    private void tickClient() {
        Vec3 previous = this.position();
        if (this.interpolation.hasActiveInterpolation()) {
            this.interpolation.interpolate();
            if (this.getFirstPassenger() != null) {
                this.setYRot(this.getFirstPassenger().yRotO);
            }
        } else {
            this.setPos(this.position().add(this.getDeltaMovement()));
            Vec3 motion = this.getDeltaMovement();
            this.setDeltaMovement(motion.x * 0.99, motion.y * 0.95, motion.z * 0.99);
        }
        if (!this.isOpenParachute() && this.getDeltaMovement().y > 0.01) {
            float color = 0.6F + this.random.nextFloat() * 0.2F;
            Vec3 delta = this.position().subtract(previous);
            int count = 1 + (int) ((float) Math.sqrt(delta.lengthSqr()) * 2.0);
            for (int i = 0; i < count; i++) {
                Vec3 pos = previous.add(delta.scale((double) i / count * 0.8));
                ParticleParam particle = new ParticleParam("smoke", pos.x, pos.y, pos.z);
                // The pinned source assigns motionX three times; preserve its random-call order.
                particle.motionX = this.getDeltaMovement().x * 0.5 + (this.random.nextDouble() - 0.5) * 0.5;
                particle.motionX = this.getDeltaMovement().y * -0.5 + (this.random.nextDouble() - 0.5) * 0.5;
                particle.motionX = this.getDeltaMovement().z * 0.5 + (this.random.nextDouble() - 0.5) * 0.5;
                particle.size = 5;
                particle.setColor(0.8F + this.random.nextFloat(), color, color, color);
                McheliEffects.spawnParticle(this.level(), particle);
            }
        }
    }

    /** Upstream's 5x5x3 water check, extended to modern build heights and water fluid states. */
    private void tryRescueBoat() {
        Entity rider = this.getFirstPassenger();
        if (this.getModelType() != 2 || rider == null || !(this.level() instanceof ServerLevel level)) {
            return;
        }
        BlockPos pos = BlockPos.containing(this.getX() + 0.5, this.getY() + 0.5, this.getZ() + 0.5);
        for (int y = 0; y < 5; y++) {
            BlockPos surface = pos.below(y);
            if (!level.getFluidState(surface).is(FluidTags.WATER)) {
                continue;
            }
            long water = BlockPos.betweenClosedStream(surface.offset(-2, -2, -2), surface.offset(2, 0, 2))
                    .filter(p -> level.getFluidState(p).is(FluidTags.WATER)).count();
            if (water > 37) {
                Boat boat = EntityTypes.OAK_BOAT.create(level, EntitySpawnReason.TRIGGERED);
                if (boat != null) {
                    boat.snapTo(surface.getX(), surface.getY() + 1, surface.getZ(), this.getYRot() - 90, 0);
                    if (level.addFreshEntity(boat)) {
                        rider.startRiding(boat, true, true);
                    }
                }
            }
            return;
        }
    }

    @Override
    protected boolean canAddPassenger(Entity passenger) {
        return this.getPassengers().isEmpty();
    }

    @Override
    protected void positionRider(Entity passenger, Entity.MoveFunction moveFunction) {
        double yaw = this.getYRot() * Math.PI / 180.0;
        // 1.12 players supplied an extra -0.35 riding offset; other living riders supplied zero.
        double offset = passenger instanceof Player ? -0.35 : 0;
        moveFunction.accept(passenger, this.getX() - Math.sin(yaw) * 0.1,
                this.getY() - 0.3F + offset, this.getZ() + Math.cos(yaw) * 0.1);
    }

    @Override
    public Vec3 getDismountLocationForPassenger(LivingEntity passenger) {
        return passenger.position();
    }

    @Override
    public boolean isPickable() {
        return !this.isRemoved();
    }

    @Override
    public boolean canBeCollidedWith(@Nullable Entity other) {
        return !this.isRemoved();
    }

    @Override
    public boolean isPushable() {
        return true;
    }

    @Override
    public boolean isPushedByFluid() {
        return false;
    }

    @Override
    public boolean hurtServer(ServerLevel level, DamageSource source, float amount) {
        return false;
    }

    @Override
    protected void addAdditionalSaveData(ValueOutput output) {
        output.putByte("ParachuteModelType", (byte) this.getModelType());
    }

    @Override
    protected void readAdditionalSaveData(ValueInput input) {
        this.setModelType(input.getByteOr("ParachuteModelType", (byte) 0));
    }
}
