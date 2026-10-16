package io.github.mcheliceport.entity.container;

import io.github.mcheliceport.config.CommonConfig;
import io.github.mcheliceport.registry.ModItems;
import io.github.mcheliceport.util.McheliSounds;
import io.github.mcheliceport.weapon.DamageScaling;
import net.minecraft.core.BlockPos;
import net.minecraft.core.NonNullList;
import net.minecraft.network.chat.Component;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.FluidTags;
import net.minecraft.util.Mth;
import net.minecraft.world.Container;
import net.minecraft.world.ContainerHelper;
import net.minecraft.world.Containers;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.MenuProvider;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.damagesource.DamageTypes;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.InterpolationHandler;
import net.minecraft.world.entity.MoverType;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ChestMenu;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

/** Upstream's floating, fireproof 54-slot cargo container. Motion and inventory belong to the server. */
public class CargoContainerEntity extends Entity implements Container, MenuProvider {
    private static final EntityDataAccessor<Integer> DAMAGE = SynchedEntityData.defineId(CargoContainerEntity.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<Integer> HIT_TIME = SynchedEntityData.defineId(CargoContainerEntity.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<Integer> FORWARD = SynchedEntityData.defineId(CargoContainerEntity.class, EntityDataSerializers.INT);
    private final NonNullList<ItemStack> items = NonNullList.withSize(54, ItemStack.EMPTY);
    private final InterpolationHandler interpolation = new InterpolationHandler(this, 13);
    private boolean contentsDropped;

    public CargoContainerEntity(EntityType<? extends CargoContainerEntity> type, Level level) {
        super(type, level);
        this.blocksBuilding = true;
    }

    @Override protected void defineSynchedData(SynchedEntityData.Builder builder) {
        builder.define(DAMAGE, 0); builder.define(HIT_TIME, 0); builder.define(FORWARD, 1);
    }

    public int getDamageTaken() { return this.entityData.get(DAMAGE); }
    @Override public boolean isPickable() { return !this.isRemoved(); }
    @Override public boolean isPushable() { return true; }
    @Override public boolean canBeCollidedWith(@Nullable Entity other) { return !this.isRemoved(); }
    @Override public boolean canCollideWith(Entity other) {
        return (other.isPushable() || other.canBeCollidedWith(this)) && !this.isPassengerOfSameVehicle(other);
    }
    @Override public Component getName() {
        return this.hasCustomName() ? super.getName() : Component.translatable("item.mcheli_ce_port.container");
    }
    @Override public float maxUpStep() { return 0.6F; }
    /** Upstream canTriggerWalking is false: cargo motion emits no walking sounds or events. */
    @Override protected MovementEmission getMovementEmission() { return MovementEmission.NONE; }
    @Override public InterpolationHandler getInterpolation() { return this.interpolation; }

    @Override public void tick() {
        super.tick();
        if (this.entityData.get(HIT_TIME) > 0) { this.entityData.set(HIT_TIME, this.entityData.get(HIT_TIME) - 1); }
        if (this.getDamageTaken() > 0) { this.entityData.set(DAMAGE, this.getDamageTaken() - 1); }
        var motion = this.getDeltaMovement();
        if (this.level().isClientSide()) {
            if (this.interpolation.hasActiveInterpolation()) {
                this.interpolation.interpolate();
            } else {
                this.setPos(this.position().add(motion));
                this.setDeltaMovement(motion.multiply(this.onGround() ? 0.9F * 0.99 : 0.99, 0.95,
                        this.onGround() ? 0.9F * 0.99 : 0.99));
            }
            return;
        }
        double depth = this.fluidDepth();
        double vy = depth < 1 ? motion.y + 0.04 * (depth * 2 - 1)
                : (motion.y < 0 ? motion.y / 2 : motion.y) + 0.007;
        double speed = motion.horizontalDistance();
        double cap = speed > 0.35 ? 0.35 / speed : 1;
        double ground = this.onGround() ? 0.9F : 1;
        this.setDeltaMovement(motion.x * cap * ground, vy, motion.z * cap * ground);
        double oldX = this.getX(), oldZ = this.getZ();
        this.move(MoverType.SELF, this.getDeltaMovement());
        this.setDeltaMovement(this.getDeltaMovement().multiply(0.99, 0.95, 0.99));
        this.setXRot(0);
        double dx = oldX - this.getX(), dz = oldZ - this.getZ();
        float yaw = dx * dx + dz * dz > 0.001 ? (float) Math.toDegrees(Math.atan2(dz, dx)) : this.getYRot();
        this.setYRot(this.getYRot() + Mth.clamp(Mth.wrapDegrees(yaw - this.getYRot()), -5, 5));
        for (var other : this.level().getEntitiesOfClass(CargoContainerEntity.class, this.getBoundingBox().inflate(0.2, 0, 0.2))) {
            if (other != this && other.isPushable()) { other.push(this); }
        }
        if (CommonConfig.COLLISION_DESTROY_BLOCK.get()) { this.clearSoftBlocks(); }
    }

    /** Five horizontal slices, including water and lava, with the upstream -0.125 offset. */
    public double fluidDepth() {
        var box = this.getBoundingBox();
        double depth = 0;
        for (int i = 0; i < 5; i++) {
            var slice = new AABB(box.minX, box.minY + box.getYsize() * i / 5 - 0.125, box.minZ,
                    box.maxX, box.minY + box.getYsize() * (i + 1) / 5 - 0.125, box.maxZ);
            if (BlockPos.betweenClosedStream(Mth.floor(slice.minX), Mth.floor(slice.minY), Mth.floor(slice.minZ),
                    Mth.ceil(slice.maxX) - 1, Mth.ceil(slice.maxY) - 1, Mth.ceil(slice.maxZ) - 1)
                    .anyMatch(p -> this.level().getFluidState(p).is(FluidTags.WATER)
                    || this.level().getFluidState(p).is(FluidTags.LAVA))) { depth += 0.2; }
        }
        return depth;
    }

    private void clearSoftBlocks() {
        for (int corner = 0; corner < 4; corner++) {
            int x = Mth.floor(this.getX() + (corner % 2 - 0.5) * 0.8);
            // Preserve upstream's floating-point corner / 2D (not integer division).
            int z = Mth.floor(this.getZ() + (corner / 2.0 - 0.5) * 0.8);
            for (int dy = 0; dy < 2; dy++) {
                var pos = new BlockPos(x, Mth.floor(this.getY()) + dy, z);
                var state = this.level().getBlockState(pos);
                if (state.is(Blocks.SNOW)) { this.level().removeBlock(pos, false); }
                else if (state.is(Blocks.LILY_PAD)) { this.level().destroyBlock(pos, true); }
            }
        }
    }

    @Override public boolean hurtServer(ServerLevel level, DamageSource source, float damage) {
        if (this.isRemoved() || this.isInvulnerableToBase(source) || !source.is(DamageTypes.PLAYER_ATTACK)
                || !(source.getEntity() instanceof Player player)) { return false; }
        if (this.getFirstPassenger() instanceof Player rider && !player.canHarmPlayer(rider)) { return false; }
        damage = DamageScaling.applyDamageByExternal(this, source, damage);
        McheliSounds.playAt(this, "hit", 1, 1.3F);
        this.entityData.set(DAMAGE, this.getDamageTaken() + (int) (damage * 20));
        this.entityData.set(FORWARD, -this.entityData.get(FORWARD)); this.entityData.set(HIT_TIME, 10);
        this.hurtMarked = true;
        if (player.isCreative() || this.getDamageTaken() > 40) {
            if (!player.isCreative()) { this.spawnAtLocation(level, ModItems.CONTAINER.get()); }
            this.discard();
        }
        return true;
    }

    @Override public void remove(RemovalReason reason) {
        if (reason.shouldDestroy() && !this.contentsDropped && this.level() instanceof ServerLevel server) {
            this.contentsDropped = true;
            Containers.dropContents(server, this, this);
            this.clearContent();
        }
        super.remove(reason);
    }

    @Override public InteractionResult interact(Player player, InteractionHand hand, Vec3 location) {
        if (!this.stillValid(player)) { return InteractionResult.PASS; }
        if (!this.level().isClientSide()) { player.openMenu(this); }
        return InteractionResult.SUCCESS;
    }

    @Override public AbstractContainerMenu createMenu(int id, Inventory inventory, Player player) {
        return ChestMenu.sixRows(id, inventory, this);
    }

    @Override public int getContainerSize() { return 54; }
    @Override public boolean isEmpty() { return this.items.stream().allMatch(ItemStack::isEmpty); }
    @Override public ItemStack getItem(int slot) { return this.items.get(slot); }
    @Override public ItemStack removeItem(int slot, int count) { return ContainerHelper.removeItem(this.items, slot, count); }
    @Override public ItemStack removeItemNoUpdate(int slot) { return ContainerHelper.takeItem(this.items, slot); }
    @Override public void setItem(int slot, ItemStack stack) { stack.limitSize(this.getMaxStackSize(stack)); this.items.set(slot, stack); }
    @Override public void setChanged() { }
    @Override public void clearContent() { this.items.clear(); }
    @Override public boolean stillValid(Player player) {
        return !this.isRemoved() && player.level() == this.level() && player.isWithinEntityInteractionRange(this.getBoundingBox(), 4);
    }
    @Override protected void readAdditionalSaveData(ValueInput input) {
        this.clearContent(); ContainerHelper.loadAllItems(input, this.items);
    }
    @Override protected void addAdditionalSaveData(ValueOutput output) { ContainerHelper.saveAllItems(output, this.items); }
}
