package io.github.mcheliceport.entity.uav;

import io.github.mcheliceport.entity.aircraft.AircraftEntity;
import io.github.mcheliceport.item.AircraftItem;
import io.github.mcheliceport.menu.UavStationMenu;
import io.github.mcheliceport.registry.ModItems;
import java.util.UUID;
import net.minecraft.core.NonNullList;
import net.minecraft.core.UUIDUtil;
import net.minecraft.network.chat.Component;
import net.minecraft.network.syncher.*;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.*;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.*;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

/** Placed UAV console: one operator, one deployment slot and a saved last-UAV identity. */
public class UavStationEntity extends Entity {
    private static final EntityDataAccessor<Boolean> SMALL = SynchedEntityData.defineId(UavStationEntity.class, EntityDataSerializers.BOOLEAN);
    private static final EntityDataAccessor<Boolean> OPEN = SynchedEntityData.defineId(UavStationEntity.class, EntityDataSerializers.BOOLEAN);
    private static final EntityDataAccessor<Integer> CONTROLLED = SynchedEntityData.defineId(UavStationEntity.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<Integer> LAST = SynchedEntityData.defineId(UavStationEntity.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<Integer> X = SynchedEntityData.defineId(UavStationEntity.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<Integer> Y = SynchedEntityData.defineId(UavStationEntity.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<Integer> Z = SynchedEntityData.defineId(UavStationEntity.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<Integer> RANGE = SynchedEntityData.defineId(UavStationEntity.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<String> FEEDBACK = SynchedEntityData.defineId(UavStationEntity.class, EntityDataSerializers.STRING);
    private @Nullable UUID lastUav;
    private boolean contentsDropped;
    private final SimpleContainer inventory = new SimpleContainer(1);
    private final InterpolationHandler interpolation = new InterpolationHandler(this, 3);
    public float cover, previousCover;
    public UavStationEntity(EntityType<? extends UavStationEntity> type, Level level) { super(type, level); }
    @Override protected void defineSynchedData(SynchedEntityData.Builder b) {
        b.define(SMALL, false); b.define(OPEN, true); b.define(CONTROLLED, 0); b.define(LAST, 0);
        b.define(X, 12); b.define(Y, 2); b.define(Z, 12); b.define(RANGE, 128); b.define(FEEDBACK, "");
    }
    public SimpleContainer inventory() { return inventory; }
    public boolean small() { return entityData.get(SMALL); }
    public void setSmall(boolean small) { entityData.set(SMALL, small); }
    public boolean open() { return !small() || entityData.get(OPEN); }
    public int offset(int axis) { return entityData.get(axis == 0 ? X : axis == 1 ? Y : Z); }
    public void adjust(int axis, int step) {
        entityData.set(axis == 0 ? X : axis == 1 ? Y : Z, Math.clamp(offset(axis) + step, -50, 50));
    }
    public int range() { return entityData.get(RANGE); }
    public String feedback() { return entityData.get(FEEDBACK); }
    public void initOffsets() {
        int quarter = Math.floorMod((int) Math.floor((getYRot() + 45) / 90), 4);
        entityData.set(X, quarter != 0 && quarter != 3 ? -12 : 12);
        entityData.set(Z, quarter != 0 && quarter != 1 ? -12 : 12);
    }
    public @Nullable Player operator() {
        return !isRemoved() && getFirstPassenger() instanceof Player p && p.isAlive() && !p.isRemoved()
                && !p.isSpectator() && p.getVehicle() == this ? p : null;
    }
    public @Nullable AircraftEntity controlled() { return aircraft(entityData.get(CONTROLLED)); }
    public @Nullable AircraftEntity lastAircraft() { return aircraft(entityData.get(LAST)); }
    private @Nullable AircraftEntity aircraft(int id) {
        return id != 0 && level().getEntity(id) instanceof AircraftEntity ac && !ac.isRemoved() ? ac : null;
    }
    public boolean validLink(AircraftEntity ac) {
        int allowedRange = level().isClientSide() ? range() : rangeLimit(ac);
        if (!tracked(ac)) { return false; }
        return !isPassenger() && controlled() == ac && ac.uavStation() == this && operator() != null && open() && !ac.isDestroyed() && ac.isUAV()
                && ac.level() == level() && !ac.isPassenger() && !ac.isTowed()
                && distanceToSqr(ac) <= (double) allowedRange * allowedRange && level().hasChunkAt(ac.blockPosition());
    }
    private boolean tracked(AircraftEntity ac) {
        return !(operator() instanceof ServerPlayer sp) || sp.connection == null || sp.getChunkTrackingView().contains(ac.chunkPosition());
    }
    private int rangeLimit(AircraftEntity ac) {
        int chunks = ((ServerLevel) level()).getServer().getPlayerList().getViewDistance();
        if (operator() instanceof ServerPlayer sp) { chunks = Math.min(chunks, sp.requestedViewDistance()); }
        int cap = Math.min(128, Math.max(24, (chunks - 1) * 16));
        int configured = ac.getAcInfo() != null ? ac.getAcInfo().uavRange : -1;
        return configured >= 0 ? Math.min(configured, cap) : cap;
    }
    public boolean usableBy(Player player) { return operator() == player && player.distanceToSqr(this) <= 16 && open(); }
    public boolean accepts(ItemStack stack) {
        return stack.getItem() instanceof AircraftItem item && item.getAircraftInfo() != null
                && item.isPlaceable() && item.getAircraftInfo().isUAV && (!small() || item.getAircraftInfo().isSmallUAV);
    }
    private boolean compatible(AircraftEntity ac) {
        return ac.isUAV() && !ac.isDestroyed() && !ac.isRemoved() && !ac.isPassenger() && !ac.isTowed()
                && ac.getAcInfo() != null && ac.getPassengers().stream().noneMatch(e -> !(e instanceof io.github.mcheliceport.entity.seat.SeatEntity))
                && (!small() || ac.getAcInfo().isSmallUAV)
                && (!ac.getAcInfo().creativeOnly || operator() != null && operator().isCreative());
    }
    public boolean deploy(Player player) {
        if (level().isClientSide() || !usableBy(player) || controlled() != null) { return false; }
        ItemStack stack = inventory.getItem(0);
        if (stack.getCount() != 1 || !accepts(stack)) { return fail(player, "Insert a compatible UAV item first."); }
        var item = (AircraftItem) stack.getItem();
        Vec3 pos = position().add(offset(0), offset(1), offset(2));
        if (pos.y < level().getMinY() + 1 || pos.y >= level().getMaxY() - 2
                || !level().hasChunkAt(net.minecraft.core.BlockPos.containing(pos))) {
            return fail(player, "Deployment is outside loaded terrain.");
        }
        AircraftEntity ac = item.createAircraft(level(), pos.x, pos.y, pos.z);
        if (ac == null || !compatible(ac)) { return fail(player, "This station cannot deploy that UAV."); }
        ac.initRotationYaw(getYRot() - 180); ac.readDataFromItem(stack);
        if (!level().noCollision(ac, ac.getBoundingBox().deflate(0.1))
                || level().getEntities(this, ac.getBoundingBox(), e -> e.isPickable()).size() > 0) {
            return fail(player, "Deployment obstructed: adjust offset.");
        }
        if (ac.getAcInfo().isTargetDrone) { ac.setFuel(ac.getMaxFuel()); }
        if (!level().addFreshEntity(ac)) { return fail(player, "UAV deployment was cancelled."); }
        inventory.removeItem(0, 1);
        if (!ac.getAcInfo().isTargetDrone) { lastUav = ac.getUUID(); entityData.set(LAST, ac.getId()); }
        entityData.set(FEEDBACK, ac.getAcInfo().isTargetDrone ? "Target drone launched." : "Deployed. Connect, then I to refuel.");
        return true;
    }
    public boolean connect(Player player) {
        var ac = lastAircraft();
        if (level().isClientSide() || !usableBy(player) || controlled() != null || ac == null || !compatible(ac)
                || ac.uavStation() != null || ac.getAcInfo().isTargetDrone) { return fail(player, "Last UAV is unavailable."); }
        entityData.set(RANGE, rangeLimit(ac));
        if (distanceToSqr(ac) > (double) range() * range() || !level().hasChunkAt(ac.blockPosition())) {
            return fail(player, "Last UAV is out of control range.");
        }
        if (!tracked(ac)) { return fail(player, "UAV terrain is still loading."); }
        entityData.set(CONTROLLED, ac.getId()); ac.setUavStation(this);
        player.setYRot(ac.getYaw()); player.setXRot(ac.getPitch());
        if (player instanceof ServerPlayer sp) { sp.closeContainer(); }
        return true;
    }
    public void disconnect() {
        var ac = controlled(); entityData.set(CONTROLLED, 0);
        if (ac != null && ac.uavStation() == this) {
            ac.setUavStation(null); ac.throttleUp = ac.throttleDown = ac.moveLeft = ac.moveRight = false;
            ac.switchGunnerMode(false); ac.switchHoveringMode(false); ac.setCurrentThrottle(0); ac.setThrottle(0);
            ac.switchFreeLookMode(false);
        }
    }
    public boolean exit(Player player) {
        if (player.getVehicle() != this) { return false; }
        player.stopRiding();
        if (player.getVehicle() == this) { return false; }
        disconnect(); return true;
    }
    private boolean fail(Player player, String message) {
        if (!level().isClientSide()) { entityData.set(FEEDBACK, message); }
        if (player instanceof ServerPlayer sp) { sp.sendSystemMessage(Component.literal(message), true); }
        return false;
    }
    public void openMenu(Player player) {
        if (usableBy(player) && player instanceof ServerPlayer sp) { UavStationMenu.open(sp, this); }
    }
    @Override public InteractionResult interact(Player player, InteractionHand hand, Vec3 hit) {
        if (hand != InteractionHand.MAIN_HAND || player.isSpectator() || isRemoved() || isPassenger()) { return InteractionResult.PASS; }
        if (small() && player.isShiftKeyDown() && getFirstPassenger() == null) {
            if (!level().isClientSide()) { entityData.set(OPEN, !open()); }
            return InteractionResult.SUCCESS;
        }
        if (!open() || getFirstPassenger() != null && getFirstPassenger() != player) { return InteractionResult.PASS; }
        if (!level().isClientSide()) {
            if (getFirstPassenger() == null) { disconnect(); }
            if (player.getVehicle() != this && !player.startRiding(this)) { return InteractionResult.FAIL; }
            openMenu(player);
        }
        return InteractionResult.SUCCESS;
    }
    @Override public void tick() {
        super.tick(); previousCover = cover; cover = Math.clamp(cover + (open() ? 0.1F : -0.1F), 0, 1);
        if (level().isClientSide()) { interpolation.interpolate(); return; }
        setDeltaMovement(0, getDeltaMovement().y - 0.03, 0); move(MoverType.SELF, getDeltaMovement());
        setDeltaMovement(getDeltaMovement().multiply(0, 0.96, 0));
        if (lastUav != null && level() instanceof ServerLevel sl) {
            var found = sl.getEntity(lastUav); entityData.set(LAST, found instanceof AircraftEntity ac && !ac.isRemoved() ? ac.getId() : 0);
        }
        var ac = controlled();
        if (ac != null) { entityData.set(RANGE, rangeLimit(ac)); }
        if (entityData.get(CONTROLLED) != 0 && (ac == null || !validLink(ac))) { disconnect(); }
        else if (ac != null && operator() instanceof ServerPlayer sp && sp.containerMenu != sp.inventoryMenu) {
            ac.throttleUp = ac.throttleDown = ac.moveLeft = ac.moveRight = false;
        }
    }
    @Override public void onPassengerTurned(Entity passenger) {
        var ac = controlled(); if (ac != null && validLink(ac)) { ac.onPassengerTurned(passenger); }
    }
    @Override protected boolean canAddPassenger(Entity passenger) { return getPassengers().isEmpty() && passenger instanceof Player; }
    @Override protected void positionRider(Entity passenger, MoveFunction move) {
        if (hasPassenger(passenger)) {
            double angle = Math.toRadians(getYRot());
            move.accept(passenger, getX() - Math.sin(angle) * .9, getY() + .35, getZ() + Math.cos(angle) * .9);
        }
    }
    @Override public InterpolationHandler getInterpolation() { return interpolation; }
    @Override public boolean isPickable() { return !isRemoved(); }
    @Override public boolean hurtServer(ServerLevel level, DamageSource source, float damage) {
        if (isInvulnerableToBase(source) || !Float.isFinite(damage) || damage <= 0 || isRemoved()) { return false; }
        if (getFirstPassenger() instanceof Player rider && source.getEntity() instanceof Player attacker && !attacker.canHarmPlayer(rider)) { return false; }
        damage = io.github.mcheliceport.weapon.DamageScaling.applyDamageByExternal(this, source, damage);
        if (damage <= 0) { return false; }
        if (getFirstPassenger() instanceof Player player && !exit(player)) { return false; }
        disconnect(); dropContents();
        if (!(source.getEntity() instanceof Player player && player.isCreative())) {
            spawnAtLocation(level, new ItemStack(small() ? ModItems.UAV_STATION2.get() : ModItems.UAV_STATION.get()));
        }
        discard(); return true;
    }
    private void dropContents() {
        if (!contentsDropped && level() instanceof ServerLevel server) {
            contentsDropped = true; Containers.dropContents(server, this, inventory);
        }
    }
    @Override public void remove(RemovalReason reason) {
        if (!level().isClientSide()) { disconnect(); if (reason.shouldDestroy()) { dropContents(); } }
        super.remove(reason);
    }
    @Override protected void addAdditionalSaveData(ValueOutput output) {
        output.putBoolean("Small", small()); output.putBoolean("Open", open());
        output.putInt("OffsetX", offset(0)); output.putInt("OffsetY", offset(1)); output.putInt("OffsetZ", offset(2));
        if (lastUav != null) { output.store("LastUav", UUIDUtil.CODEC, lastUav); }
        var items = NonNullList.withSize(1, inventory.getItem(0)); ContainerHelper.saveAllItems(output, items);
    }
    @Override protected void readAdditionalSaveData(ValueInput input) {
        setSmall(input.getBooleanOr("Small", false)); entityData.set(OPEN, input.getBooleanOr("Open", true));
        entityData.set(X, Math.clamp(input.getIntOr("OffsetX", 12), -50, 50));
        entityData.set(Y, Math.clamp(input.getIntOr("OffsetY", 2), -50, 50));
        entityData.set(Z, Math.clamp(input.getIntOr("OffsetZ", 12), -50, 50));
        lastUav = input.read("LastUav", UUIDUtil.CODEC).orElse(null);
        var items = NonNullList.withSize(1, ItemStack.EMPTY); ContainerHelper.loadAllItems(input, items); inventory.setItem(0, items.getFirst());
        entityData.set(CONTROLLED, 0);
    }
}
