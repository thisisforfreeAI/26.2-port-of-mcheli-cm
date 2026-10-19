package io.github.mcheliceport.entity.uav;

import io.github.mcheliceport.entity.aircraft.AircraftEntity;
import io.github.mcheliceport.item.AircraftItem;
import io.github.mcheliceport.menu.UavStationMenu;
import io.github.mcheliceport.registry.ModItems;
import java.util.UUID;
import java.util.List;
import java.util.LinkedHashSet;
import io.github.mcheliceport.item.UavControllerItem;
import io.github.mcheliceport.item.UavTabletData;
import io.github.mcheliceport.registry.ModDataComponents;
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
    private static final EntityDataAccessor<Boolean> HANDHELD = SynchedEntityData.defineId(UavStationEntity.class, EntityDataSerializers.BOOLEAN);
    private final LinkedHashSet<UUID> paired = new LinkedHashSet<>();
    private @Nullable UUID tabletOwner, tabletId;
    private InteractionHand tabletHand = InteractionHand.MAIN_HAND;
    private ItemStack tabletStack = ItemStack.EMPTY;
    private @Nullable UUID lastUav;
    private boolean contentsDropped, legacyPairing;
    private @Nullable UavRemoteSession remoteSession;
    private @Nullable AircraftEntity linkedAircraft;
    private @Nullable UUID loadingUav, loadingPlayer;
    private net.minecraft.world.level.@Nullable ChunkPos loadingChunk;
    private @Nullable ServerLevel loadingLevel;
    private int loadingTicks;
    public boolean streamingFor(ServerPlayer player) { return remoteSession != null && remoteSession.belongsTo(player); }
    public int remoteTicketCount() { return remoteSession != null ? remoteSession.ticketCount() : 0; }
    private final SimpleContainer inventory = new SimpleContainer(1);
    private final InterpolationHandler interpolation = new InterpolationHandler(this, 3);
    public float cover, previousCover;
    public UavStationEntity(EntityType<? extends UavStationEntity> type, Level level) { super(type, level); }
    @Override protected void defineSynchedData(SynchedEntityData.Builder b) {
        b.define(HANDHELD, false); b.define(SMALL, false); b.define(OPEN, true); b.define(CONTROLLED, 0); b.define(LAST, 0);
        b.define(X, 12); b.define(Y, 2); b.define(Z, 12); b.define(RANGE, 5000); b.define(FEEDBACK, "");
    }
    public boolean handheld() { return entityData.get(HANDHELD); }
    public UUID controllerId() { return tabletId != null ? tabletId : getUUID(); }
    public List<UUID> pairedUavs() { return List.copyOf(paired); }
    public @Nullable UUID lastId() { return lastUav; }
    public @Nullable AircraftEntity resolve(UUID id) {
        return level() instanceof ServerLevel sl && sl.getEntity(id) instanceof AircraftEntity ac && !ac.isRemoved() ? ac : null;
    }
    public void initTablet(Player player, InteractionHand hand, ItemStack stack, UavTabletData data) {
        entityData.set(HANDHELD, true); tabletOwner = player.getUUID(); tabletId = data.id(); tabletHand = hand; tabletStack = stack;
        paired.addAll(data.uavs()); setSmall(!UavControllerItem.allowsNormal());
        snapTo(player.getX(), player.getY(), player.getZ(), player.getYRot(), 0);
        if (!paired.isEmpty()) { lastUav = paired.iterator().next(); }
    }
    private boolean tabletValid(Player player) {
        var d = tabletStack.get(ModDataComponents.UAV_TABLET.get());
        return player.getUUID().equals(tabletOwner) && player.getItemInHand(tabletHand) == tabletStack
                && d != null && d.id().equals(tabletId);
    }
    private void saveTablet() {
        if (handheld() && tabletId != null) { tabletStack.set(ModDataComponents.UAV_TABLET.get(), new UavTabletData(tabletId, pairedUavs())); }
    }
    public boolean pair(Player player, AircraftEntity ac) {
        if (level().isClientSide() || handheld() || player.isSpectator() || player.distanceToSqr(this) > 36
                || getFirstPassenger() != null && operator() != player) { return false; }
        if (!compatible(ac) || ac.getAcInfo().isTargetDrone || ac.getAcInfo().creativeOnly && !player.isCreative()
                || ac.uavStation() != null) { return fail(player, "Use an idle compatible UAV."); }
        if (ac.pairedController() != null && !ac.pairedController().equals(controllerId())) { return fail(player, "UAV is paired to another controller."); }
        if (!paired.contains(ac.getUUID()) && paired.size() >= UavControllerItem.maxPairs()) { return fail(player, "Controller pairing list is full."); }
        paired.add(ac.getUUID()); ac.setPairedController(controllerId()); UavPositionIndex.update(ac);
        if (lastUav == null) { lastUav = ac.getUUID(); entityData.set(LAST, ac.getId()); }
        entityData.set(FEEDBACK, "UAV paired to station."); if (player instanceof ServerPlayer sp) { sp.sendSystemMessage(Component.literal("UAV paired to station."), true); } return true;
    }
    public boolean unpair(Player player, UUID id, boolean menuAction) {
        if (level().isClientSide() || player.isSpectator() || !paired.contains(id)
                || (menuAction ? !usableBy(player) : player.distanceToSqr(this) > 36 || getFirstPassenger() != null && operator() != player)) { return false; }
        var ac = resolve(id);
        if (ac != null && ac.uavStation() != null && ac.uavStation() != this) { return false; }
        if (ac != null && controlled() == ac) { disconnect(); }
        return forgetPairing(id, ac);
    }
    /** Physical pairing-tool interaction already checks the player and the idle aircraft. */
    public void releaseIdlePairing(AircraftEntity ac) {
        if (!level().isClientSide() && ac.uavStation() == null && controllerId().equals(ac.pairedController())) { forgetPairing(ac.getUUID(), ac); }
    }
    private boolean forgetPairing(UUID id, @Nullable AircraftEntity ac) {
        paired.remove(id);
        if (ac != null && controllerId().equals(ac.pairedController())) { ac.setPairedController(null); }
        if (id.equals(lastUav)) { lastUav = null; entityData.set(LAST, 0); }
        saveTablet(); return true;
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
                && !p.isSpectator() && p.getVehicle() == this && (level().isClientSide() || !handheld() || tabletValid(p)) ? p : null;
    }
    public @Nullable AircraftEntity controlled() {
        // Keep the live reference while crossing a chunk edge; the level lookup can briefly
        // hide an entity before the next camera window has become accessible.
        return !level().isClientSide() && linkedAircraft != null && !linkedAircraft.isRemoved()
                && linkedAircraft.getId() == entityData.get(CONTROLLED) ? linkedAircraft : aircraft(entityData.get(CONTROLLED));
    }
    public @Nullable AircraftEntity lastAircraft() { return aircraft(entityData.get(LAST)); }
    private @Nullable AircraftEntity aircraft(int id) {
        return id != 0 && level().getEntity(id) instanceof AircraftEntity ac && !ac.isRemoved() ? ac : null;
    }
    public boolean validLink(AircraftEntity ac) {
        return linkIdentityValid(ac) && tracked(ac) && level().hasChunkAt(ac.blockPosition());
    }
    private boolean linkIdentityValid(AircraftEntity ac) {
        int allowedRange = level().isClientSide() ? range() : rangeLimit(ac);
        return !isPassenger() && controlled() == ac && ac.uavStation() == this && operator() != null && open() && !ac.isDestroyed() && ac.isUAV()
                && ac.level() == level() && !ac.isPassenger() && !ac.isTowed()
                && (!small() || ac.getAcInfo() != null && ac.getAcInfo().isSmallUAV)
                && (level().isClientSide() || paired.contains(ac.getUUID()) && controllerId().equals(ac.pairedController()))
                && distanceToSqr(ac) <= (double) allowedRange * allowedRange;
    }
    private boolean tracked(AircraftEntity ac) {
        return !(operator() instanceof ServerPlayer sp) || sp.connection == null || streamingFor(sp) || sp.getChunkTrackingView().contains(ac.chunkPosition());
    }
    private int rangeLimit(AircraftEntity ac) {
        int cap = io.github.mcheliceport.config.ServerConfig.SPEC.isLoaded() ? io.github.mcheliceport.config.ServerConfig.UAV_CONTROL_RANGE.get() : 5000;
        int configured = ac.getAcInfo() != null ? ac.getAcInfo().uavRange : -1;
        return configured >= 0 ? Math.min(configured, cap) : cap;
    }
    public boolean usableBy(Player player) { return operator() == player && player.distanceToSqr(this) <= 16 && open(); }
    public boolean accepts(ItemStack stack) {
        return !handheld() && stack.getItem() instanceof AircraftItem item && item.getAircraftInfo() != null
                && item.isPlaceable() && item.getAircraftInfo().isUAV && (!small() || item.getAircraftInfo().isSmallUAV);
    }
    private boolean compatible(AircraftEntity ac) {
        return ac.isUAV() && !ac.isDestroyed() && !ac.isRemoved() && !ac.isPassenger() && !ac.isTowed()
                && ac.getAcInfo() != null && ac.getPassengers().stream().noneMatch(e -> !(e instanceof io.github.mcheliceport.entity.seat.SeatEntity))
                && (!small() || ac.getAcInfo().isSmallUAV);
    }
    public boolean deploy(Player player) {
        if (level().isClientSide() || handheld() || !usableBy(player) || controlled() != null) { return false; }
        ItemStack stack = inventory.getItem(0);
        if (stack.getCount() != 1 || !accepts(stack)) { return fail(player, "Insert a compatible UAV item first."); }
        var item = (AircraftItem) stack.getItem();
        if (item.getAircraftInfo().creativeOnly && !player.isCreative()) { return fail(player, "This UAV requires creative mode."); }
        if (!item.getAircraftInfo().isTargetDrone && paired.size() >= UavControllerItem.maxPairs()) { return fail(player, "Unpair a UAV before deploying another."); }
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
        if (!ac.getAcInfo().isTargetDrone) { paired.add(ac.getUUID()); ac.setPairedController(controllerId()); UavPositionIndex.update(ac); lastUav = ac.getUUID(); entityData.set(LAST, ac.getId()); }
        entityData.set(FEEDBACK, ac.getAcInfo().isTargetDrone ? "Target drone launched." : "Deployed. Connect, then I to refuel.");
        return true;
    }
    public String connectionStatus(AircraftEntity ac) {
        if (!controllerId().equals(ac.pairedController())) { return "Paired elsewhere"; }
        if (ac.isDestroyed()) { return "Destroyed"; }
        if (!compatible(ac)) { return "Incompatible"; }
        if (ac.uavStation() != null) { return "In use"; }
        if (distanceToSqr(ac) > (double) rangeLimit(ac) * rangeLimit(ac)) { return "Out of range"; }
        if (!tracked(ac)) { return "Terrain loading"; }
        return "Ready";
    }
    private void migrateLegacyPairing(AircraftEntity ac) {
        if (!legacyPairing || !ac.getUUID().equals(lastUav)) { return; }
        legacyPairing = false;
        if (ac.pairedController() == null && paired.isEmpty() && UavControllerItem.maxPairs() > 0
                && ac.isUAV() && ac.getAcInfo() != null && !ac.getAcInfo().isTargetDrone && !ac.isDestroyed()) {
            paired.add(ac.getUUID()); ac.setPairedController(controllerId()); UavPositionIndex.update(ac);
        }
    }
    public boolean connect(Player player) { return connect(player, lastUav); }
    public boolean connect(Player player, @Nullable UUID id) {
        if (level().isClientSide() || !usableBy(player) || controlled() != null || id == null) { return fail(player, "No available remote UAV selected."); }
        if (!paired.contains(id) && !id.equals(lastUav)) { return fail(player, "UAV is not paired to this controller."); }
        var ac = resolve(id);
        if (ac == null) {
            var sl = (ServerLevel) level(); var pos = UavPositionIndex.get(sl).position(id);
            int cap = io.github.mcheliceport.config.ServerConfig.SPEC.isLoaded() ? io.github.mcheliceport.config.ServerConfig.UAV_CONTROL_RANGE.get() : 5000;
            if (pos == null) { return fail(player, "UAV location is unknown. Visit it once to update."); }
            if (position().distanceToSqr(Vec3.atCenterOf(pos)) > (double) (cap + 2) * (cap + 2)) { return fail(player, "Selected UAV is out of control range."); }
            if (!sl.getWorldBorder().isWithinBounds(pos)) { return fail(player, "UAV is outside the world border."); }
            cancelLoading(); loadingChunk = new net.minecraft.world.level.ChunkPos(pos.getX() >> 4, pos.getZ() >> 4); loadingLevel = sl;
            loadingUav = id; loadingPlayer = player.getUUID(); loadingTicks = 100;
            UavRemoteSession.TICKETS.forceChunk(sl, getUUID(), loadingChunk.x(), loadingChunk.z(), true, false);
            entityData.set(FEEDBACK, "Loading selected UAV..."); return false;
        }
        cancelLoading();
        migrateLegacyPairing(ac);
        if (!paired.contains(id) || !controllerId().equals(ac.pairedController())) { return fail(player, "UAV pairing belongs to another controller."); }
        if (!compatible(ac) || ac.uavStation() != null || ac.getAcInfo().isTargetDrone
                || ac.getAcInfo().creativeOnly && !player.isCreative()) { return fail(player, "Selected UAV cannot be controlled now."); }
        entityData.set(RANGE, rangeLimit(ac));
        if (distanceToSqr(ac) > (double) range() * range() || !level().hasChunkAt(ac.blockPosition())) {
            return fail(player, "Last UAV is out of control range.");
        }
        lastUav = ac.getUUID(); entityData.set(LAST, ac.getId());
        linkedAircraft = ac; entityData.set(CONTROLLED, ac.getId()); ac.setUavStation(this);
        if (player instanceof ServerPlayer sp && sp.connection != null) { remoteSession = new UavRemoteSession(this, sp); remoteSession.update(ac); }
        player.setYRot(ac.getYaw()); player.setXRot(ac.getPitch());
        if (player instanceof ServerPlayer sp) { sp.closeContainer(); }
        return true;
    }
    private void cancelLoading() {
        if (loadingLevel != null && loadingChunk != null) { UavRemoteSession.TICKETS.forceChunk(loadingLevel, getUUID(), loadingChunk.x(), loadingChunk.z(), false, false); }
        loadingUav = loadingPlayer = null; loadingChunk = null; loadingLevel = null; loadingTicks = 0;
    }
    public void disconnect() {
        cancelLoading();
        if (remoteSession != null) { remoteSession.close(); remoteSession = null; }
        var ac = controlled(); entityData.set(CONTROLLED, 0);
        linkedAircraft = null;
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
        if (!handheld() && player.getItemInHand(hand).getItem() instanceof UavControllerItem item && player.getItemInHand(hand).is(ModItems.UAV_PAIRING_DEVICE.get())) {
            return item.onStation(player, hand, this);
        }
        if (handheld() || hand != InteractionHand.MAIN_HAND || player.isSpectator() || isRemoved() || isPassenger()) { return InteractionResult.PASS; }
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
        if (handheld()) {
            if (operator() == null) { discard(); return; }
            setSmall(!UavControllerItem.allowsNormal());
        } else {
            setDeltaMovement(0, getDeltaMovement().y - 0.03, 0); move(MoverType.SELF, getDeltaMovement());
            setDeltaMovement(getDeltaMovement().multiply(0, 0.96, 0));
        }
        if (lastUav != null && level() instanceof ServerLevel sl) {
            var found = sl.getEntity(lastUav); entityData.set(LAST, found instanceof AircraftEntity ac && !ac.isRemoved() ? ac.getId() : 0);
            if (found instanceof AircraftEntity previous && !previous.isRemoved()) { migrateLegacyPairing(previous); }
        }
        tickLoading();
        var ac = controlled();
        if (ac != null) {
            entityData.set(RANGE, rangeLimit(ac));
            if (remoteSession != null && linkIdentityValid(ac)) { remoteSession.update(ac); }
        }
        if (entityData.get(CONTROLLED) != 0 && (ac == null || !validLink(ac))) { disconnect(); }
        else if (ac != null && operator() instanceof ServerPlayer sp && sp.containerMenu != sp.inventoryMenu) {
            ac.throttleUp = ac.throttleDown = ac.moveLeft = ac.moveRight = false;
        }
    }
    private void tickLoading() {
        if (loadingUav == null) { return; }
        var p = operator();
        if (p == null || !p.getUUID().equals(loadingPlayer) || --loadingTicks <= 0) {
            cancelLoading(); if (p != null) { fail(p, "UAV could not be found at its last location."); } return;
        }
        var ac = resolve(loadingUav);
        if (ac != null) { UUID id = loadingUav; cancelLoading(); connect(p, id); }
    }
    @Override public void onPassengerTurned(Entity passenger) {
        var ac = controlled(); if (ac != null && validLink(ac)) { ac.onPassengerTurned(passenger); }
    }
    @Override protected boolean canAddPassenger(Entity passenger) { return getPassengers().isEmpty() && passenger instanceof Player; }
    @Override protected void positionRider(Entity passenger, MoveFunction move) {
        if (hasPassenger(passenger)) {
            if (handheld()) { move.accept(passenger, getX(), getY(), getZ()); return; }
            double angle = Math.toRadians(getYRot());
            move.accept(passenger, getX() - Math.sin(angle) * .9, getY() + .35, getZ() + Math.cos(angle) * .9);
        }
    }
    @Override public InterpolationHandler getInterpolation() { return interpolation; }
    @Override public boolean isPickable() { return !handheld() && !isRemoved(); }
    @Override public boolean hurtServer(ServerLevel level, DamageSource source, float damage) {
        if (handheld() || isInvulnerableToBase(source) || !Float.isFinite(damage) || damage <= 0 || isRemoved()) { return false; }
        if (getFirstPassenger() instanceof Player rider && source.getEntity() instanceof Player attacker && !attacker.canHarmPlayer(rider)) { return false; }
        damage = io.github.mcheliceport.weapon.DamageScaling.applyDamageByExternal(this, source, damage);
        // Upstream: "hit" for a player's attack, else "helidmg", even when the damage scales to none.
        boolean byPlayer = source.getEntity() instanceof Player;
        io.github.mcheliceport.util.McheliSounds.playAt(this, byPlayer ? "hit" : "helidmg", 1.0F,
                byPlayer ? 1.0F : 0.9F + random.nextFloat() * 0.1F);
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
        if (!level().isClientSide()) {
            disconnect();
            if (reason.shouldDestroy()) {
                dropContents();
                if (!handheld()) {
                    for (var id : paired) { var ac = resolve(id); if (ac != null && controllerId().equals(ac.pairedController())) { ac.setPairedController(null); } }
                }
            }
        }
        super.remove(reason);
    }
    @Override public boolean shouldBeSaved() { return !handheld() && super.shouldBeSaved(); }
    /** ServerPlayer otherwise writes an empty RootVehicle wrapper for this unsaved temporary seat. */
    @Override public boolean hasExactlyOnePlayerPassenger() { return !handheld() && super.hasExactlyOnePlayerPassenger(); }
    @Override public boolean saveAsPassenger(ValueOutput output) { return !handheld() && super.saveAsPassenger(output); }
    @Override protected void addAdditionalSaveData(ValueOutput output) {
        output.store("PairedUavs", UUIDUtil.CODEC.listOf(), pairedUavs());
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
        var savedPairs = input.read("PairedUavs", UUIDUtil.CODEC.sizeLimitedListOf(UavTabletData.MAX));
        paired.clear(); paired.addAll(savedPairs.orElse(List.of())); legacyPairing = savedPairs.isEmpty() && lastUav != null;
        var items = NonNullList.withSize(1, ItemStack.EMPTY); ContainerHelper.loadAllItems(input, items); inventory.setItem(0, items.getFirst());
        entityData.set(CONTROLLED, 0);
    }
}
