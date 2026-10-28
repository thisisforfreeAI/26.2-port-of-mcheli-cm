package io.github.mcheliceport.entity.aircraft;

import com.mojang.logging.LogUtils;
import io.github.mcheliceport.compat.energy.EnergyCompat;
import io.github.mcheliceport.compat.fluid.FuelFluids;
import io.github.mcheliceport.config.CommonConfig;
import io.github.mcheliceport.config.ServerSettings;
import io.github.mcheliceport.content.ContentType;
import io.github.mcheliceport.content.aircraft.AircraftInfo;
import io.github.mcheliceport.content.aircraft.BoundingBoxInfo;
import io.github.mcheliceport.content.aircraft.SeatInfo;
import io.github.mcheliceport.content.aircraft.SeatRackInfo;
import io.github.mcheliceport.entity.parachute.ParachuteEntity;
import io.github.mcheliceport.entity.gunner.GunnerEntity;
import io.github.mcheliceport.entity.seat.SeatEntity;
import io.github.mcheliceport.entity.uav.UavStationEntity;
import io.github.mcheliceport.entity.weapon.TvMissileEntity;
import io.github.mcheliceport.item.AircraftItem;
import io.github.mcheliceport.menu.AircraftMenu;
import io.github.mcheliceport.registry.ModEntityTypes;
import io.github.mcheliceport.registry.ModItems;
import io.github.mcheliceport.util.McheliMath;
import io.github.mcheliceport.util.McheliSounds;
import io.github.mcheliceport.weapon.DamageScaling;
import io.github.mcheliceport.weapon.WeaponSet;
import io.github.mcheliceport.weapon.explosion.McheliExplosion;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import net.minecraft.core.NonNullList;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientGamePacketListener;
import net.minecraft.network.protocol.game.ClientboundAddEntityPacket;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerEntity;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.permissions.Permissions;
import net.minecraft.tags.DamageTypeTags;
import net.minecraft.tags.ItemTags;
import net.minecraft.util.Mth;
import net.minecraft.world.ContainerHelper;
import net.minecraft.world.Difficulty;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.damagesource.DamageTypes;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityDimensions;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.InterpolationHandler;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.MoverType;
import net.minecraft.world.entity.Pose;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.scores.Team;
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.entity.IEntityWithComplexSpawn;
import net.neoforged.neoforge.entity.PartEntity;
import net.neoforged.neoforge.transfer.ResourceHandler;
import net.neoforged.neoforge.transfer.access.ItemAccess;
import net.neoforged.neoforge.transfer.fluid.FluidResource;
import net.neoforged.neoforge.transfer.item.ItemResource;
import net.neoforged.neoforge.transfer.item.VanillaContainerWrapper;
import net.neoforged.neoforge.transfer.transaction.Transaction;
import org.joml.Quaternionf;
import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;

/**
 * The common part of every McHeli vehicle: upstream's {@code MCH_EntityAircraft}, including seats,
 * fuel, damage, weapons, countermeasures and support effects. Remaining systems follow the roadmap.
 *
 * <p>Seat 0 (the pilot) rides this entity directly; every other seat is a {@link SeatEntity}
 * passenger. The content's bounding boxes become {@link AircraftHitBox} parts.
 */
public abstract class AircraftEntity extends Entity
        implements IEntityWithComplexSpawn, AircraftAttitude.Rotatable, AircraftPart.Host {

    private static final Logger LOGGER = LogUtils.getLogger();

    /** Upstream {@code W_Entity.GLOBAL_Y_OFFSET}, added to the pilot's riding height. */
    public static final double GLOBAL_Y_OFFSET = 0.35;
    /** Upstream {@code GLOBAL_SEAT_OFFSET}, added to the crew seats' height. */
    public static final double GLOBAL_SEAT_OFFSET = 0.22443;

    /** Common status bits ({@code setCommonStatus}). */
    /** Upstream status bit 0: a flare pattern started on the server and has not finished. */
    public static final int STATUS_FLARE = 0;
    public static final int STATUS_FREE_LOOK = 1;
    /** Upstream status bit 2: the pilot has the vehicle's inventory open, supplying ammunition. */
    public static final int STATUS_RELOADING = 2;
    public static final int STATUS_INFINITY_AMMO = 3;
    public static final int STATUS_INFINITY_FUEL = 4;
    public static final int STATUS_REPELLING = 5;
    public static final int STATUS_MOVE_LEFT = 7;
    public static final int STATUS_MOVE_RIGHT = 8;
    public static final int STATUS_THROTTLE_UP = 9;
    public static final int STATUS_THROTTLE_DOWN = 10;
    public static final int STATUS_GUNNER = 12;
    /** Upstream status bit 13: the vehicle's radar is switched on. */
    public static final int STATUS_RADAR = 13;
    /** Upstream status bit 15: the ECM jammer is running (set by the server). */
    public static final int STATUS_ECM_JAMMER = 15;

    /** Fuel can slots of the aircraft inventory; the trunk follows them. */
    public static final int SLOT_FUEL_IN = 0;
    public static final int SLOT_FUEL_OUT = 1;
    public static final int FIXED_SLOTS = 2;

    private static final EntityDataAccessor<String> DATA_TYPE_NAME =
            SynchedEntityData.defineId(AircraftEntity.class, EntityDataSerializers.STRING);
    private static final EntityDataAccessor<String> DATA_ERA =
            SynchedEntityData.defineId(AircraftEntity.class, EntityDataSerializers.STRING);
    private static final EntityDataAccessor<Integer> DATA_DAMAGE =
            SynchedEntityData.defineId(AircraftEntity.class, EntityDataSerializers.INT);
    /** M9 component health, packed by {@link io.github.mcheliceport.damage.ComponentHealth}. */
    private static final EntityDataAccessor<Long> DATA_COMPONENTS =
            SynchedEntityData.defineId(AircraftEntity.class, EntityDataSerializers.LONG);
    private static final EntityDataAccessor<Integer> DATA_STATUS =
            SynchedEntityData.defineId(AircraftEntity.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<Integer> DATA_FUEL =
            SynchedEntityData.defineId(AircraftEntity.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<String> DATA_FUEL_TYPE =
            SynchedEntityData.defineId(AircraftEntity.class, EntityDataSerializers.STRING);
    private static final EntityDataAccessor<String> DATA_TEXTURE_NAME =
            SynchedEntityData.defineId(AircraftEntity.class, EntityDataSerializers.STRING);
    private static final EntityDataAccessor<Float> DATA_ROLL =
            SynchedEntityData.defineId(AircraftEntity.class, EntityDataSerializers.FLOAT);
    private static final EntityDataAccessor<String> DATA_COMMAND =
            SynchedEntityData.defineId(AircraftEntity.class, EntityDataSerializers.STRING);
    private static final EntityDataAccessor<Integer> DATA_THROTTLE =
            SynchedEntityData.defineId(AircraftEntity.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<Integer> DATA_PART_STAT =
            SynchedEntityData.defineId(AircraftEntity.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<Boolean> DATA_HOVERING =
            SynchedEntityData.defineId(AircraftEntity.class, EntityDataSerializers.BOOLEAN);
    /** Upstream {@code USE_WEAPON}: which weapons fired this tick, for other clients' effects. */
    private static final EntityDataAccessor<Integer> DATA_USE_WEAPON =
            SynchedEntityData.defineId(AircraftEntity.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<Boolean> DATA_TOWED =
            SynchedEntityData.defineId(AircraftEntity.class, EntityDataSerializers.BOOLEAN);
    private static final EntityDataAccessor<Float> DATA_ROPE_LENGTH =
            SynchedEntityData.defineId(AircraftEntity.class, EntityDataSerializers.FLOAT);
    private static final EntityDataAccessor<Integer> DATA_ROPE_TICKS =
            SynchedEntityData.defineId(AircraftEntity.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<Float> DATA_LIGHT_YAW =
            SynchedEntityData.defineId(AircraftEntity.class, EntityDataSerializers.FLOAT);
    private static final EntityDataAccessor<Float> DATA_LIGHT_PITCH =
            SynchedEntityData.defineId(AircraftEntity.class, EntityDataSerializers.FLOAT);
    public float lightHatchRotation, previousLightHatchRotation;
    private static final EntityDataAccessor<Integer> DATA_UAV_STATION =
            SynchedEntityData.defineId(AircraftEntity.class, EntityDataSerializers.INT);
    /** Ticks left on the APS iron curtain (upstream {@code PacketIronCurtainUse}). */
    private static final EntityDataAccessor<Integer> DATA_IRON_CURTAIN =
            SynchedEntityData.defineId(AircraftEntity.class, EntityDataSerializers.INT);

    /** M10b: the entity the commander marked for the crew, or -1. */
    private static final EntityDataAccessor<Integer> DATA_CREW_MARK =
            SynchedEntityData.defineId(AircraftEntity.class, EntityDataSerializers.INT);

    private static final AircraftHitBox[] NO_HIT_BOXES = new AircraftHitBox[0];
    private static final SeatEntity[] NO_SEATS = new SeatEntity[0];

    protected @Nullable AircraftInfo info;
    private final InterpolationHandler interpolation = new InterpolationHandler(this);
    /** M9: the component health last seen, and the tick each slot last changed (-1: never). */
    private long lastComponentHealth = io.github.mcheliceport.damage.ComponentHealth.FULL;
    private final long[] componentChangeTicks = new long[io.github.mcheliceport.damage.ComponentHealth.SLOTS];
    {
        java.util.Arrays.fill(this.componentChangeTicks, -1L);
    }
    /** M9: the velocity the server last sent this watching client, and the tick it arrived. */
    private Vec3 watchedVelocity = Vec3.ZERO;
    private long watchedVelocityTick = Long.MIN_VALUE;
    private int extrapolatedTicks;
    private AircraftHitBox[] hitBoxes = NO_HIT_BOXES;
    private SeatEntity[] seats = NO_SEATS;
    private SimpleContainer inventory = new SimpleContainer(FIXED_SLOTS);
    /** Separate storage preserves the indices of fuel and trunk items in existing saves. */
    private final SimpleContainer parachutes = new SimpleContainer(2);
    private final Vec3[] previousPositions = new Vec3[10];
    private int previousPositionIndex;
    private final AircraftFuel.Meter fuelMeter = new AircraftFuel.Meter();
    /** Local viewer choice, as upstream: neither saved nor sent to other riders. */
    private int cameraId;

    public @Nullable AircraftPart partCanopy;
    public @Nullable AircraftPart partLandingGear;
    public @Nullable AircraftPart partHatch;
    /** Client only: upstream's {@code rotPartRotation}, the angles of the content's rotating parts. */
    public float[] rotPartRotation = new float[0];
    public float[] prevRotPartRotation = new float[0];
    public float steeringYaw;
    public float previousSteeringYaw;

    private double currentThrottle;
    private double prevCurrentThrottle;
    public double currentSpeed;
    private float roll;
    public float prevRoll;
    private @Nullable Quaternionf orientation;
    public boolean throttleUp;
    public boolean throttleDown;
    public boolean moveLeft;
    public boolean moveRight;
    private int commonStatus;
    private int despawnCount;
    private int countOnUpdate;
    private int modeSwitchCooldown;
    public int timeSinceHit;
    private long crewMarkUntil;
    private int repairCount;
    private int beforeDamageTaken;
    public int damageSinceDestroyed;
    public float rotDestroyedYaw;
    public float rotDestroyedPitch;
    public float rotDestroyedRoll;
    private boolean hoveringMode;
    protected boolean gunnerMode;
    public double beforeHoverThrottle;
    public float lastRiderYaw;
    public float prevLastRiderYaw;
    public float lastRiderPitch;
    public float prevLastRiderPitch;
    private float lastHitBoxFactor = 1.0F;
    private @Nullable AircraftHitBox lastHitBox;
    private @Nullable Entity lastPilot;
    private boolean keepOnRideRotation;
    /** Upstream {@code isParachuting}: the crew are being dropped by parachute. Not saved, as upstream. */
    private boolean isParachuting;
    /** Client: the pilot turned the vehicle this tick, so its attitude goes to the server. */
    public boolean rotationChanged;
    private long throttleCapFor = io.github.mcheliceport.damage.ComponentHealth.FULL;
    private double throttleCap = 1.0;
    public boolean rollReversed;
    public float thirdPersonDist = 4.0F;
    private boolean switchSeat;
    /** The gunner camera and the camera modes of seats 0 and 1 (upstream {@code camera}). */
    public final AircraftCamera camera = new AircraftCamera(true);
    /** Client: the entity radar of {@code EnableEntityRadar} vehicles (upstream {@code entityRadar}). */
    public final AircraftRadar radar = new AircraftRadar();
    /** Client: a crew member switched their seat to gunner mode (upstream {@code isGunnerModeOtherSeat}). */
    public boolean gunnerModeOtherSeat;

    private final AircraftWeapons weapons;
    private final AircraftSupport support;
    private final AircraftFluidHandler fluidHandler = new AircraftFluidHandler(this);
    private final AircraftRacks racks = new AircraftRacks(this);
    private final AircraftRopes ropes = new AircraftRopes(this);
    private final AircraftFlares flares;
    private final AircraftChaff chaff;
    private final AircraftAps aps;
    private final AircraftMissileDetector missileDetector;
    private int ecmJammerRunningTick;
    private int ironCurtainRunningTick;
    private int ironCurtainWaveTimer;
    private float ironCurtainLastFactor = 0.5F;
    private float ironCurtainCurrentFactor = 0.5F;
    private int ecmJammerWaitTick;
    private int currentFlareIndex;

    /**
     * What the client side does for vehicles: set by the client entry point, null on a dedicated
     * server. Keeps client classes out of the entity.
     */
    public interface ClientHooks {
        /** The local player turned the mouse while riding {@code aircraft} directly. */
        void onPassengerTurned(AircraftEntity aircraft, Entity passenger);

        void sendToServer(net.minecraft.network.protocol.common.custom.CustomPacketPayload payload);
    }

    private static volatile @Nullable ClientHooks clientHooks;

    public static void setClientHooks(@Nullable ClientHooks hooks) {
        clientHooks = hooks;
    }

    protected static @Nullable ClientHooks clientHooks() {
        return clientHooks;
    }

    protected AircraftEntity(EntityType<? extends AircraftEntity> type, Level level) {
        super(type, level);
        this.currentSpeed = 0.07;
        this.blocksBuilding = true;
        this.weapons = new AircraftWeapons(this);
        this.support = new AircraftSupport(this);
        this.flares = new AircraftFlares(level, this);
        this.chaff = new AircraftChaff(level, this);
        this.aps = new AircraftAps(level, this);
        this.missileDetector = new AircraftMissileDetector(this, level);
        // Upstream entityInit: NPC gunners start switched on (a saved vehicle reads its own setting).
        this.setGunnerStatus(true);
    }

    // ---- type and content ----

    /** The content definition for a type name of this vehicle kind, or null if none is loaded. */
    protected abstract @Nullable AircraftInfo lookupInfo(String typeName);

    /** The content type of this vehicle kind, which picks its item. */
    public abstract ContentType getContentType();

    public @Nullable AircraftInfo getAcInfo() {
        return this.info;
    }

    public String getAircraftTypeName() {
        return this.entityData.get(DATA_TYPE_NAME);
    }

    /** Sets the content type; upstream {@code setTypeName} followed by {@code changeType}. */
    public void setAircraftTypeName(String typeName) {
        if (!typeName.equals(this.getAircraftTypeName())) {
            this.entityData.set(DATA_TYPE_NAME, typeName);
        }
        if (this.info == null || !typeName.equals(this.info.name)) {
            this.changeType(typeName);
        }
    }

    /** Upstream {@code changeType}: loads the definition and builds seats, parts and hit boxes. */
    protected void changeType(String typeName) {
        if (typeName.isEmpty()) {
            return;
        }
        AircraftInfo newInfo = this.lookupInfo(typeName);
        if (newInfo == null) {
            LOGGER.warn("Vehicle {} has unknown type '{}'", this.getId(), typeName);
            if (!this.level().isClientSide()) {
                this.discard();
            }
            return;
        }
        this.info = newInfo;
        this.entityData.set(DATA_ERA, "");
        this.setCameraId(0);
        this.thirdPersonDist = newInfo.thirdPersonDist;
        this.refreshDimensions();
        this.partCanopy = newInfo.haveCanopy() ? AircraftPart.canopy(this) : null;
        this.partLandingGear = newInfo.haveLandingGear() ? AircraftPart.landingGear(this) : null;
        this.partHatch = newInfo.haveHatch() ? AircraftPart.hatch(this) : null;
        this.rotPartRotation = new float[newInfo.partRotPart.size()];
        this.prevRotPartRotation = new float[newInfo.partRotPart.size()];
        SeatEntity[] oldSeats = this.seats;
        this.seats = new SeatEntity[this.getSeatNum()];
        for (SeatEntity seat : oldSeats) {
            if (seat != null) {
                this.registerSeat(seat);
            }
        }
        for (Entity passenger : this.getPassengers()) {
            if (passenger instanceof SeatEntity seat) {
                this.registerSeat(seat);
            }
        }
        this.resizeInventory(FIXED_SLOTS + Math.max(newInfo.inventorySize, 0));
        if (!this.level().isClientSide() || this.hitBoxes.length != newInfo.extraBoundingBox.size()) {
            if (this.hitBoxes.length != newInfo.extraBoundingBox.size()) {
                this.hitBoxes = this.createHitBoxes(newInfo.extraBoundingBox.size());
            }
        }
        for (int i = 0; i < this.hitBoxes.length; i++) {
            this.hitBoxes[i].setInfo(newInfo.extraBoundingBox.get(i));
        }
        this.updateHitBoxPosition();
        this.weapons.create(newInfo, 1 + this.getSeatNum());
        this.currentFlareIndex = 0;
        this.onTypeChanged(newInfo);
    }

    /** Vehicle kinds add their own parts (rotors, wheels) here. */
    protected void onTypeChanged(AircraftInfo newInfo) {
    }

    private AircraftHitBox[] createHitBoxes(int count) {
        AircraftHitBox[] boxes = new AircraftHitBox[count];
        for (int i = 0; i < count; i++) {
            boxes[i] = new AircraftHitBox(this);
        }
        return boxes;
    }

    private void resizeInventory(int size) {
        if (this.inventory.getContainerSize() == size) {
            return;
        }
        SimpleContainer resized = new SimpleContainer(size);
        for (int i = 0; i < Math.min(size, this.inventory.getContainerSize()); i++) {
            resized.setItem(i, this.inventory.getItem(i));
        }
        for (int i = size; i < this.inventory.getContainerSize(); i++) {
            ItemStack lost = this.inventory.getItem(i);
            if (!lost.isEmpty() && !this.level().isClientSide()) {
                this.spawnAtLocation((ServerLevel) this.level(), lost);
            }
        }
        this.inventory = resized;
    }

    @Override
    public EntityDimensions getDimensions(Pose pose) {
        if (this.info != null) {
            return EntityDimensions.scalable(this.info.bodyWidth, this.info.bodyHeight);
        }
        return super.getDimensions(pose);
    }

    @Override
    protected void defineSynchedData(SynchedEntityData.Builder builder) {
        builder.define(DATA_TYPE_NAME, "");
        builder.define(DATA_ERA, "");
        builder.define(DATA_DAMAGE, 0);
        builder.define(DATA_COMPONENTS, io.github.mcheliceport.damage.ComponentHealth.FULL);
        builder.define(DATA_STATUS, 0);
        builder.define(DATA_FUEL, 0);
        builder.define(DATA_FUEL_TYPE, "");
        builder.define(DATA_TEXTURE_NAME, "");
        builder.define(DATA_ROLL, 0.0F);
        builder.define(DATA_COMMAND, "");
        builder.define(DATA_THROTTLE, 0);
        builder.define(DATA_PART_STAT, 0);
        builder.define(DATA_HOVERING, false);
        builder.define(DATA_USE_WEAPON, 0);
        builder.define(DATA_TOWED, false);
        builder.define(DATA_ROPE_LENGTH, 0F); builder.define(DATA_ROPE_TICKS, 0);
        builder.define(DATA_LIGHT_YAW, 0F); builder.define(DATA_LIGHT_PITCH, 0F);
        builder.define(DATA_UAV_STATION, 0);
        builder.define(DATA_IRON_CURTAIN, 0);
        builder.define(DATA_CREW_MARK, -1);
    }

    @Override
    public void onSyncedDataUpdated(EntityDataAccessor<?> accessor) {
        super.onSyncedDataUpdated(accessor);
        if (DATA_COMPONENTS.equals(accessor) && this.componentChangeTicks != null) {
            long before = this.lastComponentHealth;
            long after = this.entityData.get(DATA_COMPONENTS);
            this.lastComponentHealth = after;
            for (int i = 0; i < io.github.mcheliceport.damage.ComponentHealth.SLOTS; i++) {
                if (io.github.mcheliceport.damage.ComponentHealth.level(before, i)
                        != io.github.mcheliceport.damage.ComponentHealth.level(after, i)) {
                    this.componentChangeTicks[i] = this.level().getGameTime();
                }
            }
            io.github.mcheliceport.damage.VehicleComponents.notifyChanged(this, before, after);
        }
        if (DATA_IRON_CURTAIN.equals(accessor) && this.level().isClientSide()) {
            int ticks = this.entityData.get(DATA_IRON_CURTAIN);
            if (ticks > 0) {
                this.ironCurtainRunningTick = ticks;
            } else {
                this.resetIronCurtain();
            }
        }
        if (DATA_TYPE_NAME.equals(accessor) && this.level().isClientSide()) {
            String name = this.getAircraftTypeName();
            if (this.info == null || !name.equals(this.info.name)) {
                this.changeType(name);
            }
        }
    }

    // ---- spawn data and hit box parts ----

    @Override
    public Packet<ClientGamePacketListener> getAddEntityPacket(ServerEntity serverEntity) {
        // The number of hit boxes travels in the add packet: the client must create the parts
        // before the entity joins its level, which is before the spawn data arrives.
        return new ClientboundAddEntityPacket(this, serverEntity, this.hitBoxes.length);
    }

    @Override
    public void recreateFromPacket(ClientboundAddEntityPacket packet) {
        super.recreateFromPacket(packet);
        int count = Mth.clamp(packet.getData(), 0, 1024);
        this.hitBoxes = this.createHitBoxes(count);
        for (int i = 0; i < count; i++) {
            this.hitBoxes[i].setId(packet.getId() + i + 1);
        }
    }

    @Override
    public void writeSpawnData(RegistryFriendlyByteBuf buffer) {
        buffer.writeUtf(this.getAircraftTypeName());
        buffer.writeVarInt(this.hitBoxes.length);
        for (AircraftHitBox box : this.hitBoxes) {
            buffer.writeVarInt(box.getId());
        }
        buffer.writeFloat(this.roll);
    }

    @Override
    public void readSpawnData(RegistryFriendlyByteBuf buffer) {
        String type = buffer.readUtf();
        int count = buffer.readVarInt();
        int[] ids = new int[count];
        for (int i = 0; i < count; i++) {
            ids[i] = buffer.readVarInt();
        }
        this.setRoll(buffer.readFloat());
        this.prevRoll = this.roll;
        for (int i = 0; i < Math.min(count, this.hitBoxes.length); i++) {
            this.hitBoxes[i].setId(ids[i]);
        }
        this.setAircraftTypeName(type);
    }

    @Override
    public boolean isMultipartEntity() {
        return this.hitBoxes.length > 0;
    }

    @Override
    public PartEntity<?>[] getParts() {
        return this.hitBoxes;
    }

    public AircraftHitBox[] getHitBoxes() {
        return this.hitBoxes;
    }

    /**
     * Whether other entities collide with the content's extra boxes as well as the main box, as
     * with upstream's {@code MCH_AircraftBoundingBox}. Only tanks do here, so long hulls and turrets
     * are solid; aircraft keep their extra boxes for hits only.
     */
    public boolean hasSolidHitBoxes() {
        return false;
    }

    public void updateHitBoxPosition() {
        for (AircraftHitBox box : this.hitBoxes) {
            box.updatePosition(this.getX(), this.getY(), this.getZ(), this.getYaw(), this.getPitch(), this.getRoll());
        }
    }

    // ---- attitude ----

    @Override
    public float getYaw() {
        return this.getYRot();
    }

    @Override
    public void setYaw(float yaw) {
        this.setYRot(yaw);
    }

    @Override
    public float getPitch() {
        return this.getXRot();
    }

    @Override
    public void setPitch(float pitch) {
        this.setXRot(pitch);
    }

    @Override
    public float getRoll() {
        return this.roll;
    }

    @Override
    public void setRoll(float roll) {
        this.roll = roll;
    }

    public float getRoll(float partialTick) {
        return Mth.rotLerp(partialTick, this.prevRoll, this.roll);
    }

    @Override
    public @Nullable Quaternionf getOrientation() {
        return this.orientation;
    }

    @Override
    public void setOrientation(@Nullable Quaternionf orientation) {
        this.orientation = orientation;
    }

    /** The roll the server last sent (upstream {@code getServerRoll}). */
    public float getSyncedRoll() {
        return this.entityData.get(DATA_ROLL);
    }

    /** Upstream {@code getTransformedPosition}: a body offset turned into a world position. */
    public Vec3 getTransformedPosition(double x, double y, double z, double px, double py, double pz) {
        Vec3 v = McheliMath.rotVec3(x, y, z, -this.getYaw(), -this.getPitch(), -this.getRoll());
        return v.add(px, py, pz);
    }

    public Vec3 getTransformedPosition(Vec3 v) {
        return this.getTransformedPosition(v.x, v.y, v.z, this.getX(), this.getY(), this.getZ());
    }

    private Vec3 getTransformedPosition(double x, double y, double z, double px, double py, double pz,
            boolean rotSeat) {
        if (rotSeat && this.info != null) {
            Vec3 turretPos = this.info.turretPosition;
            Vec3 relative = new Vec3(x, y, z).subtract(turretPos);
            Vec3 rotated = McheliMath.rotVec3(relative, this.getYaw() - this.getRotSeatYaw(), 0.0F, 0.0F);
            return McheliMath.rotVec3(rotated.add(turretPos), -this.getYaw(), -this.getPitch(), -this.getRoll())
                    .add(px, py, pz);
        }
        return this.getTransformedPosition(x, y, z, px, py, pz);
    }

    private float getRotSeatYaw() {
        Entity pilot = this.getPilot();
        return pilot != null ? pilot.getYRot() : this.lastRiderYaw;
    }

    /** Upstream {@code calcOnTurretPos}: a body offset on the turret, turned with the pilot's view. */
    public Vec3 calcOnTurretPos(Vec3 pos) {
        if (this.info == null) {
            return McheliMath.rotVec3(pos, -this.getYaw(), -this.getPitch(), -this.getRoll());
        }
        return this.getTransformedPosition(pos.x, pos.y, pos.z, 0.0, 0.0, 0.0, true);
    }

    // ---- mouse control (upstream setAngles) ----

    /** Upstream {@code canMouseRot}. */
    public boolean canMouseRot() {
        return !this.isPassenger() && !this.isTowed() && !this.isRemoved() && this.getPilot() != null && !this.isDestroyed();
    }

    /** Upstream {@code canUpdateYaw}: not in the first 30 ticks, nor near the ground. */
    public boolean canUpdateYaw() {
        if (this.getVehicle() != null) {
            return false;
        }
        return this.getCountOnUpdate() >= 30 && !this.blockWithin(3, -2);
    }

    public boolean canUpdatePitch() {
        return this.getCountOnUpdate() >= 30 && !this.blockWithin(3, -2);
    }

    public boolean canUpdateRoll() {
        if (this.getVehicle() != null) {
            return false;
        }
        return this.getCountOnUpdate() >= 30 && !this.blockWithin(3, -2);
    }

    /** Upstream {@code isOverridePlayerYaw}: the pilot looks where the vehicle points unless in free look. */
    public boolean isOverridePlayerYaw() {
        return !this.isFreeLookMode();
    }

    public boolean isOverridePlayerPitch() {
        return !this.isFreeLookMode();
    }

    public double getAddRotationYawLimit() {
        return this.info != null ? 40.0 * this.info.mobilityYaw : 40.0;
    }

    public double getAddRotationPitchLimit() {
        return this.info != null ? 40.0 * this.info.mobilityPitch : 40.0;
    }

    public double getAddRotationRollLimit() {
        return this.info != null ? 40.0 * this.info.mobilityRoll : 40.0;
    }

    public float getYawFactor() {
        return 1.0F;
    }

    public float getPitchFactor() {
        return 1.0F;
    }

    public float getRollFactor() {
        return 1.0F;
    }

    /** Upstream {@code getControlRotYaw/Pitch/Roll}: which stick axis drives each rotation. */
    protected float getControlRotYaw(float inputX, float inputY) {
        return 0.0F;
    }

    protected float getControlRotPitch(float inputX, float inputY) {
        return 0.0F;
    }

    protected float getControlRotRoll(float inputX, float inputY) {
        return 0.0F;
    }

    /** The vehicle kind's own attitude rules after the control rotation (upstream {@code onUpdateAngles}). */
    protected abstract void onUpdateAngles(float deltaSeconds);

    public AircraftAttitude.Limits getAttitudeLimits() {
        if (this.info == null) {
            return AircraftAttitude.Limits.NONE;
        }
        return new AircraftAttitude.Limits(this.info.limitRotation, this.info.minRotationPitch,
                this.info.maxRotationPitch, this.info.minRotationRoll, this.info.maxRotationRoll);
    }

    /**
     * Upstream {@code setAngles} without the player half: turns the vehicle by one frame of stick
     * input. The caller (the pilot's client) then points the player as {@link #isOverridePlayerYaw()}
     * and {@link #isOverridePlayerPitch()} say. Sets {@link #rotationChanged} when the attitude moved.
     */
    public void setAngles(float inputX, float inputY, float deltaSeconds) {
        float prevPitch = this.getPitch();
        float prevYaw = this.getYaw();
        float prevRoll = this.getRoll();
        float[] d = AircraftAttitude.controlDelta(this.isFreeLookMode(), this.canUpdateYaw(), this.canUpdatePitch(),
                this.canUpdateRoll(), this.getControlRotYaw(inputX, inputY), this.getControlRotPitch(inputX, inputY),
                this.getControlRotRoll(inputX, inputY), this.getAddRotationYawLimit(),
                this.getAddRotationPitchLimit(), this.getAddRotationRollLimit(), this.getYawFactor(),
                this.getPitchFactor(), this.getRollFactor(), deltaSeconds);
        boolean quaternion = this.info != null && CommonConfig.EXPERIMENTAL_QUATERNION_ROTATION.get();
        AircraftAttitude.apply(this, d[0], d[1], d[2], deltaSeconds, quaternion, this.getAttitudeLimits(),
                this::onUpdateAngles);
        // Upstream: the pilot's client turns the vehicle every frame, so nothing is interpolated.
        this.prevRoll = this.getRoll();
        this.xRotO = this.getXRot();
        if (this.getVehicle() == null) {
            this.yRotO = this.getYRot();
        }
        if (this.getVehicle() == null && prevYaw != this.getYaw() || prevPitch != this.getPitch()
                || prevRoll != this.getRoll()) {
            this.rotationChanged = true;
        }
    }

    /** Sets the attitude from the pilot's client (upstream {@code PacketIndRotation}). */
    public void setAttitudeFromPilot(float yaw, float pitch, float roll) {
        this.setYRot(yaw);
        this.setXRot(Mth.clamp(pitch, -90.0F, 90.0F));
        this.setRoll(Mth.wrapDegrees(roll));
        this.orientation = null;
    }

    // ---- synced state ----

    public int getMaxHP() {
        return this.info != null ? this.info.maxHp : 100;
    }

    public int getHP() {
        return Math.max(this.getMaxHP() - this.getDamageTaken(), 0);
    }

    public int getDamageTaken() {
        return this.entityData.get(DATA_DAMAGE);
    }

    /** M9: engines', rotors' and fuel tanks' health, packed; read it through {@code VehicleComponents}. */
    public long getComponentHealth() {
        return this.entityData.get(DATA_COMPONENTS);
    }

    public void setComponentHealth(long packed) {
        this.entityData.set(DATA_COMPONENTS, packed);
    }

    /** M10b: the id of the entity the commander marked, or -1. */
    public int getCrewMarkId() {
        return this.entityData.get(DATA_CREW_MARK);
    }

    /** M10b: the marked entity on this side, or null. */
    public @Nullable Entity getCrewMark() {
        int id = this.getCrewMarkId();
        return id < 0 ? null : this.level().getEntity(id);
    }

    /** M10b, server: marks {@code target} for {@link CrewRoles#MARK_TICKS}, or clears the mark. */
    public void setCrewMark(@Nullable Entity target) {
        this.entityData.set(DATA_CREW_MARK, target == null ? -1 : target.getId());
        this.crewMarkUntil = target == null ? 0L : this.level().getGameTime() + CrewRoles.MARK_TICKS;
    }

    /** M10b, server: a mark ends when it times out or its target is gone. */
    private void tickCrewMark() {
        if (this.getCrewMarkId() < 0) {
            return;
        }
        Entity mark = this.getCrewMark();
        if (mark == null || !mark.isAlive() || this.level().getGameTime() >= this.crewMarkUntil) {
            this.setCrewMark(null);
        }
    }

    /** The level game time a component slot last changed on this side, or -1. */
    public long getComponentChangeTick(int slot) {
        return slot >= 0 && slot < this.componentChangeTicks.length ? this.componentChangeTicks[slot] : -1L;
    }

    public void setDamageTaken(int damage) {
        this.entityData.set(DATA_DAMAGE, Mth.clamp(damage, 0, this.getMaxHP()));
    }

    public int getFuel() {
        return this.entityData.get(DATA_FUEL);
    }

    /** Upstream {@code setFuel}: server only, clamped to the tank. */
    public void setFuel(int fuel) {
        if (this.level().isClientSide()) {
            return;
        }
        fuel = Mth.clamp(fuel, 0, Math.max(this.getMaxFuel(), 0));
        if (fuel != this.getFuel()) {
            this.entityData.set(DATA_FUEL, fuel);
        }
    }

    public int getMaxFuel() {
        return this.info != null ? this.info.maxFuel : 0;
    }

    /** Upstream {@code isElectric}: the fuel value is a plain energy store. */
    public boolean isElectric() {
        return this.info != null && this.info.electric;
    }

    /** Upstream {@code allowBattery}: a battery in the fuel slot extends an electric vehicle's store. */
    public boolean allowBattery() {
        return this.isElectric() && this.info.allowBattery;
    }

    /** Upstream {@code addEnergy}: server only; returns what the store took. */
    public int addEnergy(int amount) {
        if (this.level().isClientSide() || amount <= 0) {
            return 0;
        }
        int accepted = Math.max(0, Math.min(amount, this.getMaxFuel() - this.getFuel()));
        if (accepted > 0) {
            this.setFuel(this.getFuel() + accepted);
        }
        return accepted;
    }

    public float getFuelPercentage() {
        int max = this.getMaxFuel();
        return max == 0 ? 0.0F : (float) this.getFuel() / max;
    }

    public String getFuelType() {
        return this.entityData.get(DATA_FUEL_TYPE);
    }

    public void setFuelType(String type) {
        this.entityData.set(DATA_FUEL_TYPE, type);
    }

    public String getTextureName() {
        return this.entityData.get(DATA_TEXTURE_NAME);
    }

    /** Upstream {@code setTextureName}: an empty name keeps the current texture. */
    public void setTextureName(String name) {
        if (!name.isEmpty()) {
            this.entityData.set(DATA_TEXTURE_NAME, name);
        }
    }

    /** Upstream {@code switchNextTextureName} (wrench and sneak). */
    public void switchNextTextureName() {
        if (this.info != null) {
            this.setTextureName(this.info.getNextTextureName(this.getTextureName()));
        }
    }

    public String getCommand() {
        return this.entityData.get(DATA_COMMAND);
    }

    /**
     * Upstream {@code setCommand}: the command runs with full permissions when the vehicle is
     * destroyed, so only players who may use game-master commands can set it (upstream let anyone).
     */
    public void setCommand(String command, Player player) {
        if (canSetCommand(player)) {
            this.setCommandForce(command);
        }
    }

    public static boolean canSetCommand(Player player) {
        return player.permissions().hasPermission(Permissions.COMMANDS_GAMEMASTER);
    }

    public void setCommandForce(String command) {
        if (!this.level().isClientSide()) {
            this.entityData.set(DATA_COMMAND, command);
        }
    }

    /** Upstream {@code getThrottle}: the synced throttle in steps of 0.05. */
    public double getThrottle() {
        return 0.05 * this.entityData.get(DATA_THROTTLE);
    }

    /** Upstream {@code setThrottle}: any running engine syncs as at least one step. */
    public void setThrottle(double throttle) {
        int n = (int) (throttle * 20.0);
        if (n == 0 && throttle > 0.0) {
            n = 1;
        }
        this.entityData.set(DATA_THROTTLE, n);
    }

    public double getCurrentThrottle() {
        return this.currentThrottle;
    }

    public void setCurrentThrottle(double throttle) {
        this.currentThrottle = Math.min(throttle, this.componentThrottleCap());
    }

    /** M9e: the highest throttle the vehicle's engines and rotors allow, 1 when intact. */
    public double componentThrottleCap() {
        long health = this.getComponentHealth();
        if (health == io.github.mcheliceport.damage.ComponentHealth.FULL) {
            return 1.0;
        }
        if (health != this.throttleCapFor) {
            this.throttleCap = io.github.mcheliceport.damage.ComponentEffects.throttleCap(this);
            this.throttleCapFor = health;
        }
        return this.throttleCap;
    }

    /** M9e: turns the vehicle by a yaw the physics adds (a hurt tail rotor), sent like a pilot's turn. */
    public void addPhysicsYaw(float degrees) {
        if (degrees != 0.0F) {
            this.setYRot(this.getYRot() + degrees);
            this.rotationChanged = true;
        }
    }

    public void addCurrentThrottle(double throttle) {
        this.setCurrentThrottle(this.getCurrentThrottle() + throttle);
    }

    public double getPrevCurrentThrottle() {
        return this.prevCurrentThrottle;
    }

    @Override
    public int partStatus() {
        return this.entityData.get(DATA_PART_STAT);
    }

    @Override
    public void setPartStatus(int status) {
        this.entityData.set(DATA_PART_STAT, status);
    }

    @Override
    public boolean isClientSide() {
        return this.level().isClientSide();
    }

    @Override
    public void playPartSound(String name, float volume, float pitch) {
        McheliSounds.playAt(this, name, volume, pitch);
    }

    public boolean getCommonStatus(int bit) {
        return (this.commonStatus >> bit & 1) != 0;
    }

    public void setCommonStatus(int bit, boolean value) {
        this.setCommonStatus(bit, value, false);
    }

    /** Upstream {@code setCommonStatus}; {@code writeClient} lets a client change its own copy. */
    public void setCommonStatus(int bit, boolean value, boolean writeClient) {
        if (!this.level().isClientSide() || writeClient) {
            int before = this.commonStatus;
            int mask = 1 << bit;
            this.commonStatus = value ? this.commonStatus | mask : this.commonStatus & ~mask;
            if (before != this.commonStatus && !this.level().isClientSide()) {
                this.entityData.set(DATA_STATUS, this.commonStatus);
            }
        }
    }

    public int getDespawnCount() {
        return this.despawnCount;
    }

    public void setDespawnCount(int count) {
        this.despawnCount = count;
    }

    public int getCountOnUpdate() {
        return this.countOnUpdate;
    }

    public int getModeSwitchCooldown() {
        return this.modeSwitchCooldown;
    }

    public void setModeSwitchCooldown(int cooldown) {
        this.modeSwitchCooldown = cooldown;
    }

    public boolean isDestroyed() {
        return this.getDespawnCount() > 0;
    }

    public boolean isExploded() {
        return this.isDestroyed() && this.damageSinceDestroyed > this.getMaxHP() / 10 + 1;
    }

    public SimpleContainer getInventory() {
        return this.inventory;
    }

    public SimpleContainer getParachuteInventory() {
        return this.parachutes;
    }

    public int getNumEjectionSeat() {
        return 0;
    }

    public boolean haveParachute() {
        for (int i = 0; i < this.parachutes.getContainerSize(); i++) {
            if (this.parachutes.getItem(i).is(ModItems.PARACHUTE.get())) {
                return true;
            }
        }
        return false;
    }

    private void consumeParachute() {
        for (int i = 0; i < this.parachutes.getContainerSize(); i++) {
            if (this.parachutes.getItem(i).is(ModItems.PARACHUTE.get())) {
                this.parachutes.removeItem(i, 1);
                return;
            }
        }
    }

    /** Upstream allows only pilot and first crew seat, with no altitude restriction. */
    public boolean canEjectSeat(@Nullable Entity rider) {
        int sid = this.getSeatIdByEntity(rider);
        return !this.isDestroyed() && this.info != null && this.info.isEnableEjectionSeat
                && sid >= 0 && sid < 2 && (sid != 0 || !this.isUAV());
    }

    /** Pilot ejection also rescues a non-player in seat 1 when a second chute is stocked. */
    public boolean ejectSeat(Entity rider) {
        if (!(this.level() instanceof ServerLevel) || !this.canEjectSeat(rider) || !this.haveParachute()) {
            return false;
        }
        int sid = this.getSeatIdByEntity(rider);
        if (!this.ejectSeatSub(rider, sid)) {
            return false;
        }
        if (sid == 0) {
            Entity crew = this.getEntityBySeatId(1);
            if (crew != null && !(crew instanceof Player) && this.haveParachute()) {
                this.ejectSeatSub(crew, 1);
            }
        }
        return true;
    }

    private boolean ejectSeatSub(Entity rider, int sid) {
        SeatInfo seat = this.getSeatInfo(sid);
        Vec3 position = seat != null ? this.getTransformedPosition(seat.pos.add(0, 2, 0)) : rider.position();
        Vec3 boost = McheliMath.rotVec3(0, 2, 0, -this.getYaw(), -this.getPitch(), -this.getRoll());
        Vec3 velocity = this.getDeltaMovement().add(boost).add((this.random.nextFloat() - 0.5) * 0.1,
                0, (this.random.nextFloat() - 0.5) * 0.1);
        ParachuteEntity chute = ParachuteEntity.deploy((ServerLevel) this.level(), rider, 2, position, velocity);
        if (chute == null) {
            return false;
        }
        this.consumeParachute();
        this.camera.initCamera(sid, rider);
        if (sid == 0) {
            this.unmountEntity();
        }
        if (this.partCanopy != null && this.isCanopyClose()) {
            this.partCanopy.setStatusServer(true, false);
            this.setModeSwitchCooldown(40);
        }
        McheliSounds.playAt(chute, "eject_seat", 5, 1);
        return true;
    }

    /** Transport seats beyond seat 1 may bail out through an open hatch, clear of the ground. */
    public boolean canParachute(@Nullable Entity rider) {
        return !this.isDestroyed() && this.info != null && this.info.isEnableParachuting
                && this.getSeatIdByEntity(rider) > 1 && !this.blockWithin(3, -13)
                && (this.partHatch == null || this.partHatch.rotation >= 89);
    }

    public boolean parachuteCrew(Entity rider) {
        if (!(this.level() instanceof ServerLevel level) || !this.canParachute(rider)) {
            return false;
        }
        int sid = this.getSeatIdByEntity(rider);
        SeatInfo seat = this.getSeatInfo(sid);
        Vec3 local = seat != null && seat.unmountPos != null ? seat.unmountPos : this.info.mobDropOption.pos;
        // MCH_Queue#oldest uses current + 1, not current, in the pinned source.
        Vec3 origin = this.previousPositions[(this.previousPositionIndex + 1) % this.previousPositions.length];
        if (origin == null) {
            origin = this.position();
        }
        Vec3 position = this.getTransformedPosition(local.x, local.y, local.z, origin.x, origin.y, origin.z);
        if (ParachuteEntity.deploy(level, rider, 3, position, this.getDeltaMovement()) == null) {
            return false;
        }
        this.camera.initCamera(sid, rider);
        return true;
    }

    // ---- riders and seats ----

    /** Seat 0's rider: upstream {@code getRiddenByEntity}. */
    public @Nullable Entity getPilot() {
        for (Entity passenger : this.getPassengers()) {
            if (!(passenger instanceof SeatEntity)) {
                return passenger;
            }
        }
        var station = this.uavStation();
        return station != null && station.validLink(this) ? station.operator() : null;
    }

    @Override
    public @Nullable LivingEntity getControllingPassenger() {
        return !this.isUAV() && this.getPilot() instanceof Player player ? player : null;
    }

    @Override
    protected boolean canAddPassenger(Entity passenger) {
        if (passenger instanceof SeatEntity) {
            return true;
        }
        return !this.isUAV() && this.getPilot() == null;
    }

    @Override
    protected void addPassenger(Entity passenger) {
        super.addPassenger(passenger);
        if (passenger instanceof SeatEntity seat) {
            this.registerSeat(seat);
        } else {
            this.setCameraId(0);
        }
    }

    @Override
    protected void removePassenger(Entity passenger) {
        super.removePassenger(passenger);
        if (passenger instanceof SeatEntity seat) {
            for (int i = 0; i < this.seats.length; i++) {
                if (this.seats[i] == seat) {
                    this.seats[i] = null;
                }
            }
        } else {
            this.setCameraId(0);
        }
    }

    private void registerSeat(SeatEntity seat) {
        int index = seat.getSeatIndex();
        if (seat.getVehicle() == this && index >= 0 && index < this.seats.length) {
            this.seats[index] = seat;
        }
    }

    /** The crew seats (seat IDs 1 and up); entries are null while a seat is missing. */
    public SeatEntity[] getSeats() {
        return this.seats;
    }

    /** Upstream {@code getSeatNum}: crew seats besides the pilot's. */
    public int getSeatNum() {
        if (this.info == null) {
            return 0;
        }
        int s = this.info.getNumSeatAndRack();
        return s >= 1 ? s - 1 : 1;
    }

    public @Nullable SeatInfo getSeatInfo(int seatId) {
        if (this.info == null || seatId < 0 || seatId >= this.info.seatList.size()) {
            return null;
        }
        return this.info.seatList.get(seatId);
    }

    public @Nullable Entity getEntityBySeatId(int seatId) {
        if (seatId == 0) {
            return this.getPilot();
        }
        int index = seatId - 1;
        if (index >= 0 && index < this.seats.length && this.seats[index] != null) {
            return this.seats[index].getRider();
        }
        return null;
    }

    public int getSeatIdByEntity(@Nullable Entity entity) {
        if (entity == null) {
            return -1;
        }
        if (entity == this.getPilot()) {
            return 0;
        }
        for (int i = 0; i < this.seats.length; i++) {
            SeatEntity seat = this.seats[i];
            if (seat != null && seat.getRider() == entity) {
                return i + 1;
            }
        }
        return -1;
    }

    public boolean isPilot(@Nullable Entity entity) {
        return entity != null && entity == this.getPilot();
    }

    public boolean isMountedEntity(@Nullable Entity entity) {
        return this.getSeatIdByEntity(entity) >= 0;
    }

    /** Upstream {@code isRidePlayer}: a player sits in any seat. */
    public boolean isRidePlayer() {
        if (this.getPilot() instanceof Player) {
            return true;
        }
        for (SeatEntity seat : this.seats) {
            if (seat != null && seat.getRider() instanceof Player) {
                return true;
            }
        }
        return false;
    }

    /** Creates the crew seats a vehicle is missing (upstream {@code createSeats}/{@code searchSeat}). */
    private void ensureSeats() {
        if (this.level().isClientSide() || this.info == null || this.isRemoved()) {
            return;
        }
        for (int i = 0; i < this.seats.length; i++) {
            if (this.seats[i] == null || this.seats[i].isRemoved()) {
                SeatEntity seat = ModEntityTypes.SEAT.get().create(this.level(), net.minecraft.world.entity.EntitySpawnReason.LOAD);
                if (seat == null) {
                    return;
                }
                seat.setSeatIndex(i);
                seat.setPos(this.getX(), this.getY() + 1.0, this.getZ());
                if (this.level().addFreshEntity(seat)) {
                    seat.startRiding(this, true, false);
                    this.seats[i] = seat;
                }
            }
        }
    }

    @Override
    protected void positionRider(Entity passenger, Entity.MoveFunction moveFunction) {
        if (!this.hasPassenger(passenger)) {
            return;
        }
        Vec3 v;
        if (passenger instanceof SeatEntity seat) {
            v = this.getSeatPosition(seat.getSeatIndex() + 1);
        } else {
            // Upstream updateRiderPosition.
            SeatInfo seatInfo = this.getSeatInfo(0);
            if (seatInfo != null) {
                v = this.getTransformedPosition(seatInfo.pos.x, seatInfo.pos.y - 0.5, seatInfo.pos.z, this.getX(),
                        this.getY() + GLOBAL_Y_OFFSET, this.getZ(), seatInfo.rotSeat);
            } else {
                v = this.getTransformedPosition(0.0, -1.0, 0.0, this.getX(), this.getY(), this.getZ());
            }
        }
        moveFunction.accept(passenger, v.x, v.y, v.z);
    }

    /** Upstream {@code updateSeatsPosition}: where crew seat {@code seatId} (1 and up) sits. */
    public Vec3 getSeatPosition(int seatId) {
        SeatInfo si = this.getSeatInfo(seatId);
        if (si == null) {
            si = this.getSeatInfo(0);
        }
        if (si == null) {
            return this.position();
        }
        return this.getTransformedPosition(si.pos.x, si.pos.y - 0.5, si.pos.z, this.getX(),
                this.getY() + GLOBAL_SEAT_OFFSET, this.getZ(), si.rotSeat);
    }

    @Override
    public Vec3 getDismountLocationForPassenger(LivingEntity passenger) {
        Vec3 pos = this.getUnmountPosition(passenger, 0);
        return pos != null ? pos : super.getDismountLocationForPassenger(passenger);
    }

    /** Upstream {@code getUnmountPos}: the dismount point of a seat in body coordinates. */
    public Vec3 getUnmountOffset(SeatInfo seatInfo) {
        if (seatInfo.unmountPos != null) {
            return seatInfo.unmountPos;
        }
        if (this.info != null && this.info.unmountPosition != null) {
            return this.info.unmountPosition;
        }
        double x = seatInfo.pos.x;
        x = x >= 0.0 ? x + 3.0 : x - 3.0;
        return new Vec3(x, 2.0, seatInfo.pos.z);
    }

    /**
     * Upstream {@code setUnmountPosition}: the seat's dismount point, pulled back one block towards
     * the rider when a block is in the way.
     */
    public @Nullable Vec3 getUnmountPosition(Entity rider, int seatId) {
        SeatInfo seatInfo = this.getSeatInfo(seatId);
        if (seatInfo == null) {
            return null;
        }
        Vec3 desired = this.getTransformedPosition(this.getUnmountOffset(seatInfo));
        Vec3 from = rider.position();
        BlockHitResult hit = this.level().clip(new ClipContext(from, desired, ClipContext.Block.COLLIDER,
                ClipContext.Fluid.NONE, rider));
        if (hit.getType() == HitResult.Type.MISS) {
            return desired;
        }
        Vec3 dir = desired.subtract(from).normalize();
        return hit.getLocation().subtract(dir);
    }

    /** Upstream {@code canRideSeatOrRack}: the content's exclusion lists. */
    public boolean canRideSeatOrRack(int seatId, Entity entity) {
        if (this.info == null) {
            return false;
        }
        for (Integer[] list : this.info.exclusionSeatList) {
            boolean listed = false;
            for (Integer id : list) {
                if (id != null && id == seatId) {
                    listed = true;
                    break;
                }
            }
            if (listed) {
                for (Integer id : list) {
                    if (id != null && this.getEntityBySeatId(id) != null) {
                        return false;
                    }
                }
            }
        }
        return true;
    }

    /** Upstream {@code interactFirstSeat}: boards the first free crew seat. */
    public boolean interactFirstSeat(Player player) {
        int seatId = 1;
        for (SeatEntity seat : this.seats) {
            if (seat != null && !(this.getSeatInfo(seatId) instanceof SeatRackInfo)
                    && seat.getRider() == null && !this.isMountedEntity(player)
                    && this.canRideSeatOrRack(seatId, player)) {
                if (!this.level().isClientSide()) {
                    player.startRiding(seat);
                }
                break;
            }
            seatId++;
        }
        return true;
    }

    public void onMountPlayerSeat(SeatEntity seat, Entity entity) {
        this.weapons.onMountPlayerSeat(entity);
        int sid = this.getSeatIdByEntity(entity);
        if (sid == 1 && (this.info == null || !this.info.isEnableConcurrentGunnerMode)) {
            this.switchGunnerMode(false);
        }
    }

    public void onUnmountPlayerSeat(SeatEntity seat, Entity entity) {
        // Upstream looked the seat up by the rider, who had already left, so seat 1 kept its mode.
        this.camera.initCamera(seat.getSeatIndex() + 1, entity);
        if (!this.isRidePlayer()) {
            this.switchGunnerMode(false);
            this.switchHoveringMode(false);
        }
    }

    /** Upstream {@code unmountEntity}: the pilot got out. */
    public void unmountEntity() {
        if (!this.isRidePlayer()) {
            this.switchHoveringMode(false);
        }
        this.moveLeft = this.moveRight = this.throttleDown = this.throttleUp = false;
        Entity pilot = this.getPilot();
        if (pilot != null) {
            this.camera.initCamera(0, pilot);
            if (!this.level().isClientSide()) {
                pilot.stopRiding();
            }
        } else if (this.lastPilot instanceof Player) {
            this.camera.initCamera(0, this.lastPilot);
        }
        if (!this.isRidePlayer()) {
            this.switchGunnerMode(false);
        }
        this.setCommonStatus(STATUS_FREE_LOOK, false);
        this.lastPilot = null;
        // Upstream cs_dismountAll, the pilot's DismountAll setting.
        if (!this.level().isClientSide() && CommonConfig.SPEC.isLoaded() && CommonConfig.DISMOUNT_ALL.get()) {
            this.unmountCrew(false);
        }
    }

    // ---- mobs in seats ----

    /**
     * Upstream {@code mountMobToSeats}: when a pilot boards, the living things within three blocks
     * (two up and down) that ride nothing take the free seats in order, up to the first rack.
     */
    public void mountMobToSeats() {
        List<LivingEntity> list = this.level().getEntitiesOfClass(LivingEntity.class,
                this.getBoundingBox().inflate(3.0, 2.0, 3.0));
        for (LivingEntity entity : list) {
            if (entity instanceof Player || entity.getVehicle() != null) {
                continue;
            }
            int sid = 1;
            for (SeatEntity seat : this.seats) {
                if (seat != null && seat.getRider() == null && !this.isMountedEntity(entity)
                        && this.canRideSeatOrRack(sid, entity)) {
                    if (this.getSeatInfo(sid) instanceof SeatRackInfo) {
                        break;
                    }
                    entity.startRiding(seat, true, true);
                }
                sid++;
            }
        }
    }

    /**
     * Upstream {@code unmountCrew()}, the pilot's unload key. With rope hooks: near the ground the
     * crew get out, otherwise the ropes go down or up. Otherwise a vehicle that drops by
     * parachute starts or stops dropping its crew when ten blocks up, and near the ground the crew
     * get out.
     */
    public void unmountCrew() {
        if (this.info == null) {
            return;
        }
        if (this.info.haveRepellingHook()) {
            if (!this.ropes.active() && this.blockWithin(3, -4)) {
                this.unmountCrew(false);
            } else {
                this.ropes.toggle();
            }
        } else if (this.isParachuting) {
            this.stopUnmountCrew();
        } else if (this.info.isEnableParachuting && !this.blockWithin(3, -10)) {
            this.startUnmountCrew();
        } else {
            this.unmountCrew(false);
        }
    }

    /** Upstream {@code startUnmountCrew}: the drop starts, with the hatch opening. */
    public void startUnmountCrew() {
        this.isParachuting = true;
        if (this.partHatch != null) {
            this.foldHatch(true, true);
        }
    }

    public void stopUnmountCrew() {
        this.isParachuting = false;
    }

    public boolean isParachuting() {
        return this.isParachuting;
    }

    /**
     * Upstream {@code onUpdate_UnmountCrew}: while dropping, one rider leaves each drop interval
     * once the hatch is open, until ten blocks from the ground or nobody is left.
     */
    private void tickUnmountCrew() {
        if (this.info == null || !this.isParachuting) {
            return;
        }
        if (this.blockWithin(3, -10)) {
            this.stopUnmountCrew();
        } else if ((this.partHatch == null || this.partHatch.rotation > 89.0F)
                && this.getCountOnUpdate() % Math.max(1, this.info.mobDropOption.interval) == 0
                && !this.unmountCrew(true)) {
            this.stopUnmountCrew();
        }
    }

    /**
     * Upstream {@code unmountCrew(boolean)}: every rider but players and rack loads gets out at its
     * seat's exit. With {@code parachute}, the first such rider from the third seat on jumps
     * instead, with a parachute, from the seat's {@code UnmountPos} or the drop position, and the
     * drop stops there; as upstream, a rider in the second seat (the first crew seat) just gets out.
     *
     * @return whether a rider left
     */
    public boolean unmountCrew(boolean parachute) {
        if (this.level().isClientSide() || this.info == null) {
            return false;
        }
        boolean any = false;
        for (int i = 0; i < this.seats.length; i++) {
            SeatEntity seat = this.seats[i];
            if (seat == null) {
                continue;
            }
            Entity occupant = seat.getRider();
            if (occupant == null || occupant instanceof Player || this.getSeatInfo(i + 1) instanceof SeatRackInfo) {
                continue;
            }
            any = true;
            if (parachute && this.getSeatIdByEntity(occupant) > 1) {
                SeatInfo seatInfo = this.getSeatInfo(i + 1);
                Vec3 local = seatInfo != null && seatInfo.unmountPos != null ? seatInfo.unmountPos
                        : this.info.mobDropOption.pos;
                Vec3 drop = this.getTransformedPosition(local);
                ParachuteEntity.deploy((net.minecraft.server.level.ServerLevel) this.level(), occupant, 3, drop,
                        this.getDeltaMovement());
                break;
            }
            occupant.stopRiding();
        }
        return any;
    }

    /**
     * Upstream {@code switchNextSeat}. Upstream's wrap-around branch called
     * {@code onMountPlayerSeat} instead of seating the rider, so wrapping did nothing; the port
     * seats the rider. Seats blocked by the content's exclusion lists are skipped (KNOWN_ISSUES).
     */
    public void switchNextSeat(Entity entity) {
        if (this.seats.length == 0 || !this.isMountedEntity(entity)) {
            return;
        }
        boolean found = false;
        int sid = 1;
        for (SeatEntity seat : this.seats) {
            if (seat != null) {
                if (this.getSeatInfo(sid) instanceof SeatRackInfo) {
                    break;
                }
                if (seat.getRider() == entity) {
                    found = true;
                } else if (found && seat.getRider() == null && this.canRideSeatOrRack(sid, entity)) {
                    entity.startRiding(seat, true, true);
                    return;
                }
                sid++;
            }
        }
        sid = 1;
        for (SeatEntity seat : this.seats) {
            if (seat != null && seat.getRider() == null) {
                if (!(this.getSeatInfo(sid) instanceof SeatRackInfo)) {
                    if (this.canRideSeatOrRack(sid, entity)) {
                        entity.startRiding(seat, true, true);
                        return;
                    }
                } else {
                    break;
                }
            }
            sid++;
        }
    }

    /** Upstream {@code switchPrevSeat}. */
    public void switchPrevSeat(Entity entity) {
        if (this.seats.length == 0 || !this.isMountedEntity(entity)) {
            return;
        }
        boolean found = false;
        for (int i = this.seats.length - 1; i >= 0; i--) {
            SeatEntity seat = this.seats[i];
            if (seat != null) {
                if (seat.getRider() == entity) {
                    found = true;
                } else if (found && seat.getRider() == null && this.canRideSeatOrRack(i + 1, entity)) {
                    entity.startRiding(seat, true, true);
                    return;
                }
            }
        }
        for (int i = this.seats.length - 1; i >= 0; i--) {
            SeatEntity seat = this.seats[i];
            if (!(this.getSeatInfo(i + 1) instanceof SeatRackInfo) && seat != null && seat.getRider() == null
                    && this.canRideSeatOrRack(i + 1, entity)) {
                entity.startRiding(seat, true, true);
                return;
            }
        }
    }

    /** Upstream {@code notOnSameTeam}: a rider is on a team the player is not on. */
    public boolean notOnSameTeam(Player player) {
        for (int i = 0; i < 1 + this.getSeatNum(); i++) {
            Entity entity = this.getEntityBySeatId(i);
            if (entity instanceof LivingEntity rider) {
                Team team = rider.getTeam();
                if (team != null && !rider.isAlliedTo(player)) {
                    return true;
                }
            }
        }
        return false;
    }

    /** Items whose use on a seat goes to the vehicle (upstream's wrench and gunner spawn items). */
    public static boolean isParentInteractItem(ItemStack stack) {
        return !stack.isEmpty() && (stack.is(ModItems.WRENCH.get()) || stack.is(ModItems.SPAWN_GUNNER_VS_MONSTER.get())
                || stack.is(ModItems.SPAWN_GUNNER_VS_PLAYER.get()));
    }

    /** Upstream {@code processInitialInteract} with {@code switchSeat} set (the seat menu's dismount). */
    public InteractionResult interactSwitchingSeat(Player player, InteractionHand hand) {
        this.switchSeat = true;
        // Upstream: a rider moving to the pilot seat keeps their view and brings no mobs aboard.
        this.keepOnRideRotation = true;
        try {
            return this.interact(player, hand, Vec3.ZERO);
        } finally {
            this.switchSeat = false;
            if (this.getPilot() != player) {
                this.keepOnRideRotation = false;
            }
        }
    }

    /** Upstream {@code processInitialInteract}. */
    @Override
    public InteractionResult interact(Player player, InteractionHand hand, Vec3 location) {
        if (this.notOnSameTeam(player)) { return InteractionResult.PASS; }
        if (player.getItemInHand(hand).getItem() instanceof io.github.mcheliceport.item.UavControllerItem item) {
            return item.onAircraft(player, hand, this);
        }
        if (this.isDestroyed() || this.info == null) {
            return InteractionResult.PASS;
        }
        ItemStack stack = player.getItemInHand(hand);
        if (!stack.isEmpty()) {
            if (stack.is(ModItems.WRENCH.get())) {
                if (!this.level().isClientSide() && player.isShiftKeyDown()) {
                    this.switchNextTextureName();
                }
                return InteractionResult.PASS;
            }
            if (isParentInteractItem(stack)) {
                return InteractionResult.PASS;
            }
        }
        if (player.isShiftKeyDown()) {
            this.openContainer(player);
            return InteractionResult.PASS;
        }
        if (this.isUAV()) { return InteractionResult.PASS; }
        if (!this.info.canRide) {
            return InteractionResult.PASS;
        }
        if (this.getPilot() != null) {
            return this.interactFirstSeat(player) ? InteractionResult.SUCCESS : InteractionResult.PASS;
        }
        if (player.getVehicle() instanceof SeatEntity) {
            return InteractionResult.PASS;
        }
        if (!this.canRideSeatOrRack(0, player)) {
            return InteractionResult.PASS;
        }
        if (!this.switchSeat) {
            if (this.info.haveCanopy() && this.isCanopyClose()) {
                this.openCanopy();
                return InteractionResult.PASS;
            }
            if (this.getModeSwitchCooldown() > 0) {
                return InteractionResult.PASS;
            }
        }
        this.closeCanopy();
        this.lastPilot = null;
        if (!this.level().isClientSide()) {
            player.startRiding(this);
            if (!this.keepOnRideRotation) {
                this.mountMobToSeats();
            }
            if ("uh-1c".equalsIgnoreCase(this.info.name)) {
                io.github.mcheliceport.registry.ModCriteriaTriggers.trigger(io.github.mcheliceport.registry.ModCriteriaTriggers.RIDING_VALKYRIES, player);
            }
        }
        return InteractionResult.SUCCESS;
    }

    /** Upstream {@code openContainer}/{@code openGui}: the trunk and fuel slots (decision D12). */
    public void openContainer(Player player) {
        if (!this.level().isClientSide() && this.info != null && player instanceof ServerPlayer serverPlayer) {
            AircraftMenu.open(serverPlayer, this);
        }
    }

    /** Upstream {@code isUsableByPlayer}. */
    public boolean stillValid(Player player) {
        if (this.isRemoved()) {
            return false;
        }
        if (this.isUAV() && this.isPilot(player)) { return true; }
        return this.getSeatIdByEntity(player) >= 0 ? player.distanceToSqr(this) <= 4096.0
                : player.distanceToSqr(this) <= 64.0;
    }

    /**
     * Upstream drew every vehicle the client had in a pass of its own ({@code VehicleRenderHook}),
     * however far; vanilla's size-based distance would hide a vehicle past about 150 blocks.
     */
    @Override
    public boolean shouldRenderAtSqrDistance(double distance) {
        return true;
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
        return false;
    }

    @Override
    public void push(Entity entity) {
        // Upstream applyEntityCollision does nothing.
    }

    @Override
    public void onPassengerTurned(Entity passenger) {
        ClientHooks hooks = clientHooks;
        if (hooks != null && this.level().isClientSide()) {
            hooks.onPassengerTurned(this, passenger);
        }
    }

    @Override
    public boolean isFlyingVehicle() {
        return true;
    }

    @Override
    public @Nullable ItemStack getPickResult() {
        AircraftItem item = this.getItem();
        return item != null ? new ItemStack(item) : null;
    }

    /** Upstream named a vehicle by its item; the port's entity types are shared by every vehicle of a kind. */
    @Override
    protected Component getTypeName() {
        AircraftItem item = this.getItem();
        return item != null ? Component.translatable(item.getDescriptionId()) : super.getTypeName();
    }

    /** The vehicle's item, or null if its content has none. */
    public @Nullable AircraftItem getItem() {
        if (this.info == null) {
            return null;
        }
        for (ModItems.ContentItem contentItem : ModItems.contentItems()) {
            if (contentItem.type() == this.getContentType() && contentItem.contentName().equals(this.info.name)
                    && contentItem.item().get() instanceof AircraftItem aircraftItem) {
                return aircraftItem;
            }
        }
        return null;
    }

    // ---- authority (decision D2) ----

    /** Whether the pilot's client moves this vehicle (the port's default) rather than the server. */
    @Override
    public boolean isClientAuthoritative() {
        return !this.isUAV() && !this.isPassenger() && !this.isTowed() && !this.ropes.active() && !ServerSettings.forSide(this.level().isClientSide()).serverAuthoritativeMovement()
                && !this.isDestroyed() && super.isClientAuthoritative();
    }

    @Override
    protected boolean isLocalClientAuthoritative() {
        return !this.isUAV() && !this.isPassenger() && !this.isTowed() && !this.ropes.active() && !ServerSettings.client().serverAuthoritativeMovement() && !this.isDestroyed()
                && super.isLocalClientAuthoritative();
    }

    /** Whether this side moves the vehicle (upstream's {@code !world.isRemote}). */
    public boolean simulates() {
        return !this.isPassenger() && this.isLocalInstanceAuthoritative();
    }

    /** The server follows its controlling pilot when movement is client-authoritative. */
    public boolean followsPilot() {
        return !this.isPassenger() && !this.level().isClientSide() && !this.simulates();
    }

    public double getMaxSpeedForChecks() {
        return this.getSpeedLimitForChecks() * 1.5;
    }

    /** Whether this is the pilot's own client (upstream {@code W_Lib.isClientPlayer(getRiddenByEntity())}). */
    public boolean isPilotLocalClient() {
        return this.level().isClientSide() && this.getPilot() instanceof Player player && player.isLocalPlayer();
    }

    /** A linked trailer is simulated by the server, including when its driver remains aboard. */
    public boolean isTowed() { return this.entityData.get(DATA_TOWED); }
    public void setTowed(boolean towed) { this.entityData.set(DATA_TOWED, towed); }

    @Override
    public InterpolationHandler getInterpolation() {
        return this.interpolation;
    }

    /**
     * The server's check of a pilot's move in client-authoritative mode (decision D2): limits the
     * horizontal step to {@code Speed × 1.5} per tick and the climb to one block, which makes
     * vanilla reject the move and snap the vehicle back.
     */
    @Override
    public void move(MoverType type, Vec3 movement) {
        if (type == MoverType.PLAYER && !this.level().isClientSide() && this.info != null) {
            double limit = this.getSpeedLimitForChecks() * 1.5;
            double horizontal = movement.horizontalDistance();
            boolean clamped = false;
            if (horizontal > limit && horizontal > 0.0) {
                double f = limit / horizontal;
                movement = new Vec3(movement.x * f, movement.y, movement.z * f);
                clamped = true;
            }
            if (movement.y > 1.0) {
                movement = new Vec3(movement.x, 1.0, movement.z);
                clamped = true;
            }
            if (clamped) {
                this.logRejectedMove();
            }
        }
        super.move(type, movement);
    }

    private long lastRejectLogTime;

    private void logRejectedMove() {
        long now = this.level().getGameTime();
        if (now - this.lastRejectLogTime >= 1200 || this.lastRejectLogTime == 0) {
            this.lastRejectLogTime = now;
            LOGGER.info("Vehicle {} of {} moved faster than it can fly; it was moved back", this.getAircraftTypeName(),
                    this.getPilot() != null ? this.getPilot().getPlainTextName() : "nobody");
        }
    }

    /** The content speed the server checks a pilot's movement against. */
    protected double getSpeedLimitForChecks() {
        return this.info != null ? this.info.speed : 1.0;
    }

    // ---- tick ----

    @Override
    public void tick() {
        if (this.getCountOnUpdate() < 2) {
            java.util.Arrays.fill(this.previousPositions, this.position());
        }
        this.prevCurrentThrottle = this.getCurrentThrottle();
        this.lastHitBoxFactor = 1.0F;
        if (this.info == null && !this.getAircraftTypeName().isEmpty()) {
            this.changeType(this.getAircraftTypeName());
        }
        this.ropes.tick();
        if (!this.level().isClientSide()) {
            this.tickUnmountCrew();
            this.tickCrewMark();
        }
        this.updateControl();

        Entity pilot = this.getPilot();
        if (this.isDestroyed() && this.getCountOnUpdate() % 20 == 0) {
            for (int sid = 0; sid < this.getSeatNum() + 1; sid++) {
                Entity rider = this.getEntityBySeatId(sid);
                if (rider != null && !rider.fireImmune()) {
                    rider.igniteForSeconds(5.0F);
                }
            }
        }

        if (!this.level().isClientSide() && (int) this.prevRoll != (int) this.getRoll()) {
            this.entityData.set(DATA_ROLL, Mth.wrapDegrees(this.getRoll()));
        } else if (!this.level().isClientSide() && Math.abs(this.getSyncedRoll() - Mth.wrapDegrees(this.getRoll())) > 0.01F) {
            this.entityData.set(DATA_ROLL, Mth.wrapDegrees(this.getRoll()));
        }
        this.prevRoll = this.getRoll();

        if (this.level().isClientSide() && this.info != null && this.getHP() <= 0 && this.getDespawnCount() <= 0) {
            this.destroyAircraft();
        }
        if (!this.level().isClientSide() && this.getDespawnCount() > 0) {
            this.setDespawnCount(this.getDespawnCount() - 1);
            if (this.getDespawnCount() <= 1) {
                this.discard();
                return;
            }
        }

        super.tick();
        this.tickInterpolation();

        this.support.supplyFuel();
        this.weapons.supplyAmmoToOtherAircraft();
        this.updateFuel();
        this.support.repairOtherAircraft();
        if (this.modeSwitchCooldown > 0) {
            this.modeSwitchCooldown--;
        }
        if (this.lastPilot == null && pilot != null) {
            this.onRidePilotFirstUpdate();
        }
        if (this.countOnUpdate == 0) {
            this.onFirstUpdate();
        }
        this.countOnUpdate++;
        if (this.countOnUpdate >= 1000000) {
            this.countOnUpdate = 1;
        }
        if (this.level().isClientSide()) {
            this.commonStatus = this.entityData.get(DATA_STATUS);
            if (!this.isPilotLocalClient()) {
                this.hoveringMode = this.entityData.get(DATA_HOVERING);
            }
        }

        this.resetFallDistance();
        if (pilot != null) {
            pilot.resetFallDistance();
        }
        if (pilot != null && !pilot.isRemoved() && !this.isDestroyed()) {
            this.lastRiderYaw = pilot.getYRot();
            this.prevLastRiderYaw = pilot.yRotO;
            this.lastRiderPitch = pilot.getXRot();
            this.prevLastRiderPitch = pilot.xRotO;
        } else if (this.isTowed() || this.isPassenger()) {
            this.lastRiderYaw = this.prevLastRiderYaw = this.getYaw();
            this.lastRiderPitch = this.prevLastRiderPitch = this.getPitch();
        }

        this.missileDetector.update();
        this.updateSearchLight();
        this.weapons.updateSupplyAmmo();
        this.autoRepair();
        this.updateCountermeasures();
        if (pilot == null && this.lastPilot != null) {
            this.unmountEntity();
        }
        this.ensureSeats();
        this.support.regenerateCrew();

        boolean prevOnGround = this.onGround();
        double prevMotionY = this.getDeltaMovement().y;
        if (this.info != null) {
            if (this.lastPilot == null && pilot != null) {
                this.weapons.initCurrentWeapon(pilot);
            }
            this.weapons.tick(this.entityData, DATA_USE_WEAPON);
            if (!this.isPassenger()) { this.onUpdateAircraft(); }
            else {
                this.setDeltaMovement(Vec3.ZERO); this.resetFallDistance();
                this.setCurrentThrottle(0); this.setThrottle(0);
            }
            this.updateParts(this.partStatus());
            this.updatePartRotation();
            if (this.level().isClientSide()) {
                this.previousSteeringYaw = this.steeringYaw;
                this.steeringYaw = steeringStep(this.steeringYaw, this.moveLeft, this.moveRight);
            }
            this.updateCameraViewers();
        }

        if (!this.level().isClientSide() && this.isDestroyed() && !this.isExploded() && !prevOnGround
                && this.onGround() && prevMotionY < -0.2) {
            this.explosionByCrash(prevMotionY);
            this.damageSinceDestroyed = this.getMaxHP();
        }

        if (this.weapons.recoilCount > 0) {
            this.weapons.recoilCount--;
        }
        if (!this.isPilotLocalClient()) {
            this.updateRecoil(1.0F);
        }

        this.onUpdateParticleSmoke();
        this.updateHitBoxPosition();
        this.onUpdateCollisionGroundDamage();
        this.lastPilot = this.getPilot();
        this.previousPositions[this.previousPositionIndex] = this.position();
        this.previousPositionIndex = (this.previousPositionIndex + 1) % this.previousPositions.length;
        if (this.isPilotLocalClient()) {
            this.sendPilotState();
        }
        io.github.mcheliceport.entity.uav.UavPositionIndex.update(this);
    }

    /** The pilot's client sends its attitude when it changed, and its motion while it moves the vehicle. */
    private void sendPilotState() {
        if (this.isPassenger() || this.isTowed() || this.ropes.active()) { return; }
        ClientHooks hooks = clientHooks;
        if (hooks == null) {
            return;
        }
        if (this.rotationChanged) {
            this.rotationChanged = false;
            hooks.sendToServer(new io.github.mcheliceport.net.AircraftAttitudePayload(this.getId(), this.getYaw(),
                    this.getPitch(), this.getRoll(), this.rollReversed));
            this.rollReversed = false;
        }
        if (this.simulates()) {
            Vec3 motion = this.getDeltaMovement();
            hooks.sendToServer(new io.github.mcheliceport.net.AircraftMotionPayload(this.getId(), motion.x, motion.y,
                    motion.z, (float) this.getCurrentThrottle()));
        }
    }

    /**
     * Applies the server's position to vehicles this side does not move. The pilot's own client
     * keeps the attitude it is steering, and a destroyed vehicle keeps its client-side spin, as
     * upstream's {@code applyServerPositionAndRotation}.
     */
    private void tickInterpolation() {
        if (this.simulates()) {
            this.interpolation.cancel();
            return;
        }
        boolean keepRotation = this.isDestroyed() || !this.isTowed() && !this.ropes.active() && this.isPilotLocalClient()
                && !(this instanceof io.github.mcheliceport.entity.ground.GroundVehicleEntity
                && ServerSettings.client().serverAuthoritativeMovement());
        float yaw = this.getYRot();
        float pitch = this.getXRot();
        if (this.level().isClientSide() && !this.isPilotLocalClient() && !this.isDestroyed()) {
            this.smoothWatched();
        }
        this.interpolation.interpolate();
        if (keepRotation) {
            this.setYRot(yaw);
            this.setXRot(pitch);
        }
        if (this.level().isClientSide() && !this.isPilotLocalClient() && !this.isDestroyed()) {
            float diff = Mth.wrapDegrees(this.getSyncedRoll() - this.getRoll());
            this.setRoll(this.getRoll() + diff / 3.0F);
        }
        if (this.level().isClientSide()) {
            this.setDeltaMovement(Vec3.ZERO);
        }
    }

    /** M9: the server's velocity for a vehicle another player moves; see {@code WatcherMotion}. */
    public void setWatchedVelocity(Vec3 velocity) {
        this.watchedVelocity = velocity;
        this.watchedVelocityTick = this.level().getGameTime();
    }

    /**
     * M9 smoother sync on a watching client: while a fresh velocity is known, interpolate over one
     * position update so the speed stays even, and when an update is late carry the vehicle on along
     * its velocity for a few ticks; the next update then blends it back.
     */
    private void smoothWatched() {
        boolean fresh = io.github.mcheliceport.combat.WatcherMotion.fresh(this.level().getGameTime(),
                this.watchedVelocityTick);
        this.interpolation.setInterpolationLength(fresh ? io.github.mcheliceport.combat.WatcherMotion.SMOOTH_STEPS
                : InterpolationHandler.DEFAULT_INTERPOLATION_STEPS);
        boolean interpolating = this.interpolation.hasActiveInterpolation();
        if (io.github.mcheliceport.combat.WatcherMotion.extrapolates(interpolating, fresh, this.extrapolatedTicks)) {
            this.setPos(this.position().add(this.watchedVelocity));
            this.extrapolatedTicks++;
        } else if (interpolating) {
            this.extrapolatedTicks = 0;
        }
    }

    /** Vehicle kinds run their control and movement here (upstream {@code onUpdateAircraft}). */
    protected abstract void onUpdateAircraft();

    /** Upstream {@code updateControl}: the pilot's switches go to other clients as status bits. */
    private void updateControl() {
        if (!this.level().isClientSide()) {
            this.setCommonStatus(STATUS_MOVE_LEFT, this.moveLeft);
            this.setCommonStatus(STATUS_MOVE_RIGHT, this.moveRight);
            this.setCommonStatus(STATUS_THROTTLE_UP, this.throttleUp);
            this.setCommonStatus(STATUS_THROTTLE_DOWN, this.throttleDown);
        } else if (!this.isPilotLocalClient()) {
            this.moveLeft = this.getCommonStatus(STATUS_MOVE_LEFT);
            this.moveRight = this.getCommonStatus(STATUS_MOVE_RIGHT);
            this.throttleUp = this.getCommonStatus(STATUS_THROTTLE_UP);
            this.throttleDown = this.getCommonStatus(STATUS_THROTTLE_DOWN);
        }
    }

    /** Upstream {@code onFirstUpdate}. */
    protected void onFirstUpdate() {
        if (!this.level().isClientSide()) {
            this.setCommonStatus(STATUS_INFINITY_AMMO, CommonConfig.INFINITY_AMMO.get());
            this.setCommonStatus(STATUS_INFINITY_FUEL, CommonConfig.INFINITY_FUEL.get());
        }
    }

    /** Upstream {@code onRidePilotFirstUpdate}. */
    protected void onRidePilotFirstUpdate() {
        Entity pilot = this.getPilot();
        if (pilot != null) {
            pilot.setYRot(this.lastRiderYaw);
            pilot.setXRot(this.lastRiderPitch);
        }
        this.keepOnRideRotation = false;
        if (this.isPilotLocalClient()) {
            this.hoveringMode = this.entityData.get(DATA_HOVERING);
        }
        if (this.info != null) {
            this.setCommonStatus(STATUS_FREE_LOOK, this.info.defaultFreelook, true);
        }
    }

    // ---- hovering and gunner mode ----

    public boolean isHoveringMode() {
        return this.hoveringMode;
    }

    public boolean isGunnerMode() {
        return this.gunnerMode;
    }

    public boolean isHovering() {
        return this.gunnerMode || this.isHoveringMode();
    }

    public boolean canSwitchHoveringMode() {
        return this.info != null && !this.gunnerMode;
    }

    /** Upstream {@code switchHoveringMode}. */
    public void switchHoveringMode(boolean mode) {
        if (this.canSwitchHoveringMode() && this.isHoveringMode() != mode) {
            if (mode) {
                this.beforeHoverThrottle = this.getCurrentThrottle();
            } else {
                this.setCurrentThrottle(this.beforeHoverThrottle);
            }
            this.hoveringMode = mode;
            if (!this.level().isClientSide()) {
                this.entityData.set(DATA_HOVERING, mode);
            }
            Entity pilot = this.getPilot();
            if (pilot != null) {
                pilot.setXRot(0.0F);
                pilot.xRotO = 0.0F;
            }
        }
    }

    public boolean canSwitchGunnerMode() {
        if (this.info == null || !this.info.isEnableGunnerMode || !this.isCanopyClose()) {
            return false;
        }
        boolean seatOccupied = this.getEntityBySeatId(1) instanceof Player;
        return (this.info.isEnableConcurrentGunnerMode || !seatOccupied) && !this.isHoveringMode();
    }

    /** Upstream {@code switchGunnerMode}; the camera zoom reset is the client camera's (M4). */
    public void switchGunnerMode(boolean mode) {
        Entity pilot = this.getEntityBySeatId(0);
        if (!mode || this.canSwitchGunnerMode()) {
            if (this.gunnerMode && !mode) {
                this.setCurrentThrottle(this.beforeHoverThrottle);
                this.gunnerMode = false;
                this.weapons.getCurrentWeapon(pilot).onSwitchWeapon(this.level().isClientSide(),
                        this.isInfinityAmmo(pilot));
            } else if (!this.gunnerMode && mode) {
                this.beforeHoverThrottle = this.getCurrentThrottle();
                this.gunnerMode = true;
                this.weapons.getCurrentWeapon(pilot).onSwitchWeapon(this.level().isClientSide(),
                        this.isInfinityAmmo(pilot));
            }
        }
    }

    /**
     * Upstream {@code getIsGunnerMode(entity)}: the pilot in gunner mode, or a gunner seat (a crew
     * seat that can switch is in gunner mode only after its rider switched it, on their client).
     */
    public boolean isGunnerMode(@Nullable Entity entity) {
        if (this.info == null) {
            return false;
        }
        int id = this.getSeatIdByEntity(entity);
        if (id < 0) {
            return false;
        }
        if (id == 0 && this.info.isEnableGunnerMode) {
            return this.gunnerMode;
        }
        SeatInfo seat = this.getSeatInfo(id);
        if (seat == null || !seat.gunner) {
            return false;
        }
        return !this.level().isClientSide() || !seat.switchgunner || this.gunnerModeOtherSeat;
    }

    /** Upstream {@code canSwitchGunnerModeOtherSeat}. */
    public boolean canSwitchGunnerModeOtherSeat(@Nullable Entity entity) {
        int sid = this.getSeatIdByEntity(entity);
        SeatInfo seat = sid > 0 ? this.getSeatInfo(sid) : null;
        return seat != null && seat.gunner && seat.switchgunner;
    }

    /** Upstream {@code switchGunnerModeOtherSeat}: a client-side toggle. */
    public void switchGunnerModeOtherSeat() {
        this.gunnerModeOtherSeat = !this.gunnerModeOtherSeat;
    }

    /** Upstream {@code isCameraView}: the view comes from the camera (gunner mode or a UAV). */
    public boolean isCameraView(@Nullable Entity entity) {
        return this.isGunnerMode(entity) || this.isUAV();
    }

    public boolean isUAV() {
        return this.info != null && this.info.isUAV;
    }
    public @Nullable UavStationEntity uavStation() {
        return this.entityData.get(DATA_UAV_STATION) != 0
                && this.level().getEntity(this.entityData.get(DATA_UAV_STATION)) instanceof io.github.mcheliceport.entity.uav.UavStationEntity s
                && !s.isRemoved() ? s : null;
    }
    private @Nullable UUID pairedController;
    public @Nullable UUID pairedController() { return pairedController; }
    public void setPairedController(@Nullable UUID id) { pairedController = id; io.github.mcheliceport.entity.uav.UavPositionIndex.update(this); }
    public void setUavStation(@Nullable UavStationEntity station) {
        this.entityData.set(DATA_UAV_STATION, station == null ? 0 : station.getId());
    }

    public boolean isEntityRadarMounted() {
        return this.info != null && this.info.isEnableEntityRadar;
    }

    // ---- camera ----

    public int getCameraId() {
        return this.cameraId;
    }

    /** Invalid selections fall back to the primary camera, including after content reload. */
    public void setCameraId(int id) {
        this.cameraId = id > 0 && id < this.getCameraPosNum() ? id : 0;
    }

    public int getCameraPosNum() {
        return this.info != null ? this.info.cameraPosition.size() : 1;
    }

    public boolean canSwitchCameraPos() {
        return this.getCameraPosNum() >= 2;
    }

    /** The actual view source; separate from isCameraView's weapon/UAV semantics. */
    public boolean isViewFromCamera(@Nullable Entity viewer) {
        return this.isGunnerMode(viewer) || this.isUAV() && this.isPilot(viewer) || this.isPilot(viewer) && this.info != null
                && (this.info.alwaysCameraView || this.getCameraId() > 0);
    }

    public int getZoomMax() {
        return this.info != null ? this.info.cameraZoom : 1;
    }

    public boolean canZoom() {
        return this.getZoomMax() > 1;
    }

    /** Upstream {@code zoomCamera}: doubles the zoom up to {@code CameraZoom}, then back to 1. */
    public void zoomCamera() {
        if (this.canZoom()) {
            float z = this.camera.getCameraZoom();
            if (z >= this.getZoomMax() - 0.01) {
                z = 1.0F;
            } else {
                z *= 2.0F;
                if (z >= this.getZoomMax()) {
                    z = this.getZoomMax();
                }
            }
            this.camera.setCameraZoom(z <= this.getZoomMax() + 0.01 ? z : 1.0F);
        }
    }

    /**
     * Whether a seat may switch camera modes: upstream's {@code canSwitchCameraMode(seatID)}, which
     * upstream showed in the key hints but did not check when the key was pressed. The port checks
     * it on both sides.
     */
    public boolean canSwitchCameraMode(int seatId) {
        return !this.isDestroyed() && this.info != null && this.info.isEnableNightVision
                && this.camera.isValidUid(seatId);
    }

    public int getCameraMode(@Nullable Entity entity) {
        return this.camera.getMode(this.getSeatIdByEntity(entity));
    }

    /** Upstream {@code switchCameraMode(player, mode)}. */
    public void switchCameraMode(@Nullable Entity entity, int mode) {
        this.camera.setMode(this.getSeatIdByEntity(entity), mode);
    }

    /** Upstream {@code updateCameraViewers}: on the server, keeps night vision on viewers in that mode. */
    public void updateCameraViewers() {
        for (int i = 0; i < this.getSeatNum() + 1; i++) {
            this.camera.updateViewer(i, this.getEntityBySeatId(i));
        }
    }

    /**
     * Upstream {@code getCameraPosInfo}: the camera position for a viewer: the pilot uses the
     * selected {@code CameraPosition}; a crew seat with {@code InvCamPos} uses its own.
     */
    public AircraftInfo.@Nullable CameraPosition getCameraPosInfo(@Nullable Entity viewer) {
        if (this.info == null || this.info.cameraPosition.isEmpty()) {
            return null;
        }
        int sid = this.getSeatIdByEntity(viewer);
        if (sid == 0 && this.cameraId > 0 && this.cameraId < this.info.cameraPosition.size()) {
            return this.info.cameraPosition.get(this.cameraId);
        }
        SeatInfo seat = sid > 0 ? this.getSeatInfo(sid) : null;
        if (seat != null && seat.invCamPos && seat.getCamPos() != null) {
            return seat.getCamPos();
        }
        return this.info.cameraPosition.getFirst();
    }

    public boolean isFreeLookMode() {
        return this.getCommonStatus(STATUS_FREE_LOOK) || this.ropes.active();
    }

    public void switchFreeLookMode(boolean value) {
        this.setCommonStatus(STATUS_FREE_LOOK, value);
    }

    public void switchFreeLookModeClient(boolean value) {
        this.setCommonStatus(STATUS_FREE_LOOK, value, true);
    }

    public boolean getGunnerStatus() {
        return this.getCommonStatus(STATUS_GUNNER);
    }

    public void setGunnerStatus(boolean value) {
        if (!this.level().isClientSide()) {
            this.setCommonStatus(STATUS_GUNNER, value);
        }
    }

    // ---- moving parts ----

    /** Upstream updatePartWheel's steering angle; independent of wheel propulsion. */
    public static float steeringStep(float yaw, boolean left, boolean right) {
        if (left != right) {
            return left ? (float) Math.min(yaw + 0.1, 1.0) : (float) Math.max(yaw - 0.1, -1.0);
        }
        return yaw * 0.9F;
    }

    /**
     * Upstream {@code onUpdate_PartRotation}: on clients, turns the rotating parts while someone
     * flies the vehicle, or always for parts marked so.
     */
    protected void updatePartRotation() {
        if (!this.level().isClientSide() || this.info == null
                || this.rotPartRotation.length != this.info.partRotPart.size()) {
            return;
        }
        for (int i = 0; i < this.rotPartRotation.length; i++) {
            this.prevRotPartRotation[i] = this.rotPartRotation[i];
            if (!this.isDestroyed() && this.info.partRotPart.get(i).rotAlways || this.getPilot() != null) {
                this.rotPartRotation[i] += this.info.partRotPart.get(i).rotSpeed;
                if (this.rotPartRotation[i] < 0.0F) {
                    this.rotPartRotation[i] += 360.0F;
                }
                if (this.rotPartRotation[i] >= 360.0F) {
                    this.rotPartRotation[i] -= 360.0F;
                }
            }
        }
    }

    /** Upstream {@code updateParts}, with its landing gear logic. */
    protected void updateParts(int stat) {
        if (this.isDestroyed()) {
            return;
        }
        for (AircraftPart part : new AircraftPart[] {this.partHatch, this.partCanopy, this.partLandingGear}) {
            if (part != null) {
                part.updateStatusClient(stat);
                part.update();
            }
        }
        if (this.level().isClientSide() || this.partLandingGear == null || this.info == null) {
            return;
        }
        float gearFactor = this.partLandingGear.getFactor();
        boolean folded = this.isLandingGearFolded();
        if (!folded && gearFactor <= 0.1F) {
            boolean blockBelow = this.blockWithin(3, -20);
            boolean safeToFold = (this.getCurrentThrottle() <= 0.8F || this.onGround() || blockBelow)
                    && this.info.isFloat && (this.isInWater() || this.firstBlockBelowIsWater(3, -20));
            if (safeToFold) {
                this.partLandingGear.setStatusServer(true);
            }
        } else if (folded && gearFactor >= 0.9F) {
            boolean blockBelow = this.blockWithin(3, -10);
            boolean shouldUnfold = false;
            if (this.getCurrentThrottle() < this.getUnfoldLandingGearThrottle() && blockBelow) {
                shouldUnfold = true;
                if (this.info.isFloat && GroundProbe.find((x, y, z) -> GroundProbe.isWater(
                        this.level().getBlockState(new net.minecraft.core.BlockPos(x, y, z))), this.level().getMinY(),
                        this.level().getMaxY(), this.getX(), this.getY() + 1.0 + this.info.floatOffset, this.getZ(), 1,
                        65386)) {
                    shouldUnfold = false;
                }
            }
            if (shouldUnfold) {
                this.partLandingGear.setStatusServer(false);
            }
        }
    }

    private boolean firstBlockBelowIsWater(int size, int lenY) {
        BlockState[] first = new BlockState[1];
        net.minecraft.core.BlockPos.MutableBlockPos pos = new net.minecraft.core.BlockPos.MutableBlockPos();
        GroundProbe.find((x, y, z) -> {
            BlockState state = this.level().getBlockState(pos.set(x, y, z));
            if (GroundProbe.counts(state, true)) {
                first[0] = state;
                return true;
            }
            return false;
        }, this.level().getMinY(), this.level().getMaxY(), this.getX(), this.getY(), this.getZ(), size, lenY);
        return first[0] != null && GroundProbe.isWater(first[0]);
    }

    public float getUnfoldLandingGearThrottle() {
        return 0.8F;
    }

    public boolean isLandingGearFolded() {
        return this.partLandingGear != null && this.partLandingGear.getStatus();
    }

    public boolean canFoldLandingGear() {
        if (this.partLandingGear == null || this.partLandingGear.rotation >= 1.0F) {
            return false;
        }
        return !this.isLandingGearFolded() && !this.blockWithin(3, -10, false);
    }

    public boolean canUnfoldLandingGear() {
        return this.partLandingGear != null && !(this.partLandingGear.rotation < 89.0F) && this.isLandingGearFolded();
    }

    public void foldLandingGear() {
        if (this.partLandingGear != null && this.getModeSwitchCooldown() <= 0) {
            this.partLandingGear.setStatusServer(true);
            this.setModeSwitchCooldown(20);
        }
    }

    public void unfoldLandingGear() {
        if (this.partLandingGear != null && this.getModeSwitchCooldown() <= 0 && this.isLandingGearFolded()) {
            this.partLandingGear.setStatusServer(false);
            this.setModeSwitchCooldown(20);
        }
    }

    public void openCanopy() {
        if (this.partCanopy != null && this.getModeSwitchCooldown() <= 0) {
            this.partCanopy.setStatusServer(true);
            this.setModeSwitchCooldown(20);
        }
    }

    public void closeCanopy() {
        if (this.partCanopy != null && this.getModeSwitchCooldown() <= 0 && this.partCanopy.getStatus()) {
            this.partCanopy.setStatusServer(false);
            this.setModeSwitchCooldown(20);
        }
    }

    /** Upstream {@code isCanopyClose}: no canopy, or closed and done moving. */
    public boolean isCanopyClose() {
        return this.partCanopy == null || !this.partCanopy.getStatus() && this.partCanopy.rotation <= 0.01F;
    }

    public boolean canUnfoldHatch() {
        return this.partHatch != null && this.modeSwitchCooldown <= 0 && this.partHatch.isOFF();
    }

    public boolean canFoldHatch() {
        return this.partHatch != null && this.modeSwitchCooldown <= 0 && this.partHatch.isON();
    }

    public void foldHatch(boolean fold) {
        this.foldHatch(fold, false);
    }

    /** Upstream {@code foldHatch(fold, force)}: closing the hatch stops a parachute drop. */
    public void foldHatch(boolean fold, boolean force) {
        if (this.partHatch != null && (force || this.modeSwitchCooldown <= 0)) {
            this.partHatch.setStatusServer(fold);
            this.modeSwitchCooldown = 20;
            if (!fold) {
                this.stopUnmountCrew();
            }
        }
    }

    /** Upstream {@code getLastPartStatusMask}: the bits saved with the vehicle (gear and hatch). */
    public int getLastPartStatusMask() {
        return 24;
    }

    // ---- ground probe and water ----

    /** Upstream {@code MCH_Lib.getBlockIdY(this, size, lenY) > 0}. */
    public boolean blockWithin(int size, int lenY) {
        return this.blockWithin(size, lenY, true);
    }

    public boolean blockWithin(int size, int lenY, boolean collidableOnly) {
        return GroundProbe.find(this.level(), this.getX(), this.getY(), this.getZ(), size, lenY, collidableOnly);
    }

    public boolean canFloatWater() {
        return this.info != null && this.info.isFloat && !this.isDestroyed();
    }

    /** Upstream {@code getWaterDepth}: the fraction of five horizontal slices of the box that hold water. */
    public double getWaterDepth() {
        if (this.info == null) {
            return 0.0;
        }
        AABB box = this.getBoundingBox();
        double height = box.maxY - box.minY;
        double depth = 0.0;
        for (int i = 0; i < 5; i++) {
            double yMin = box.minY + height * i / 5 + -0.125 + this.info.floatOffset;
            double yMax = box.minY + height * (i + 1) / 5 + -0.125 + this.info.floatOffset;
            AABB slice = new AABB(box.minX, yMin, box.minZ, box.maxX, yMax, box.maxZ);
            if (this.containsWater(slice)) {
                depth += 1.0 / 5;
            }
        }
        return depth;
    }

    private boolean containsWater(AABB box) {
        int x0 = Mth.floor(box.minX);
        int x1 = Mth.ceil(box.maxX);
        int y0 = Mth.floor(box.minY);
        int y1 = Mth.ceil(box.maxY);
        int z0 = Mth.floor(box.minZ);
        int z1 = Mth.ceil(box.maxZ);
        net.minecraft.core.BlockPos.MutableBlockPos pos = new net.minecraft.core.BlockPos.MutableBlockPos();
        for (int x = x0; x < x1; x++) {
            for (int y = y0; y < y1; y++) {
                for (int z = z0; z < z1; z++) {
                    if (this.level().getFluidState(pos.set(x, y, z)).is(net.minecraft.tags.FluidTags.WATER)) {
                        return true;
                    }
                }
            }
        }
        return false;
    }

    // ---- fuel ----

    /** Upstream {@code isCreative}: a creative player, or a gunner placed by one. */
    public boolean isCreative(@Nullable Entity entity) {
        return entity instanceof Player player && player.isCreative()
                || entity instanceof GunnerEntity gunner && gunner.isCreative;
    }

    /** Riders that select and aim the seat's weapons: players and gunners (upstream's two checks). */
    public static boolean isWeaponUser(@Nullable Entity entity) {
        return entity instanceof Player || entity instanceof GunnerEntity;
    }

    /** Upstream {@code isInfinityFuel}. */
    public boolean isInfinityFuel(@Nullable Entity player, boolean checkOtherSeats) {
        if (this.isCreative(player) || this.getCommonStatus(STATUS_INFINITY_FUEL)) {
            return true;
        }
        if (checkOtherSeats) {
            for (SeatEntity seat : this.seats) {
                if (seat != null && this.isCreative(seat.getRider())) {
                    return true;
                }
            }
        }
        return false;
    }

    public boolean canUseFuel(boolean checkOtherSeats) {
        return AircraftFuel.canUseFuel(this.getMaxFuel(), this.getFuel(),
                this.isInfinityFuel(this.getPilot(), checkOtherSeats));
    }

    public boolean canUseFuel() {
        return this.canUseFuel(false);
    }

    /** Upstream {@code canSupply}: refuelling needs the vehicle on (or just above) the ground. */
    public boolean canSupply() {
        return this.canFloatWater() ? this.blockWithin(1, -3) : this.blockWithin(1, -3) && !this.isInWater();
    }

    public float getFuelConsumptionFactor() {
        return this.info != null ? this.info.getFuelConsumption(this.getFuelType()) : 1.0F;
    }

    /** Upstream {@code updateFuel}: consumption, then refuelling from a fuel can in the menu. */
    private void updateFuel() {
        if (this.getMaxFuel() == 0 || this.isDestroyed() || this.level().isClientSide() || this.info == null) {
            return;
        }
        int fuel = this.support.pauseFuelConsumption() ? this.getFuel()
                : AircraftFuel.consume(this.fuelMeter, this.getCountOnUpdate(), this.getFuel(), this.getThrottle(),
                        this.info.fuelConsumption, this.getFuelConsumptionFactor());
        if (fuel != this.getFuel()) {
            this.setFuel(fuel);
        }
        if (this.info.electric) {
            this.updateElectricBattery();
            return;
        }
        int current = this.getFuel();
        int max = this.getMaxFuel();
        if (this.canSupply() && this.getCountOnUpdate() % 10 == 0 && current < max) {
            ItemStack input = this.inventory.getItem(SLOT_FUEL_IN);
            if (!input.isEmpty() && input.is(ModItems.FUEL.get())) {
                this.refuelFromCan(input, current, max);
            } else if (!input.isEmpty()) {
                this.refuelFromContainer(input, current, max);
            }
        }
    }

    /**
     * Upstream {@code canAcceptFuel}: a fuel the definition burns, into an empty tank or one that
     * holds the same fuel.
     */
    public boolean canAcceptFuel(String type) {
        return this.info != null && this.info.isFuelValid(type)
                && (this.getFuel() <= 0 || this.getFuelType().isBlank() || this.getFuelType().equals(type));
    }

    /**
     * A fuelled vehicle's tank for pipes and tanks (upstream {@code hasFluidTank}), or {@code null}
     * for an electric vehicle or one without a tank.
     */
    public @Nullable ResourceHandler<FluidResource> fluidHandler() {
        return this.info != null && !this.info.electric && this.getMaxFuel() > 0 ? this.fluidHandler : null;
    }

    /**
     * The fluid half of upstream {@code updateFuel}: one container in the fuel slot gives the
     * first fuel it holds that this vehicle accepts, up to the free space, and what is left of the
     * container goes to the output slot. Nothing moves while the output slot cannot take it.
     */
    private void refuelFromContainer(ItemStack input, int current, int max) {
        ResourceHandler<ItemResource> slot = VanillaContainerWrapper.of(new SimpleContainer(input.copyWithCount(1)));
        ResourceHandler<FluidResource> handler = ItemAccess.forHandlerIndexStrict(slot, 0)
                .getCapability(Capabilities.Fluid.ITEM);
        if (handler == null || this.info == null) {
            return;
        }
        for (int i = 0; i < handler.size(); i++) {
            FluidResource resource = handler.getResource(i);
            String type = resource.isEmpty() ? null : FuelFluids.typeOf(this.info, resource.getFluid());
            if (type == null || !this.canAcceptFuel(type)) {
                continue;
            }
            try (Transaction transaction = Transaction.openRoot()) {
                int drained = handler.extract(i, resource, max - current, transaction);
                ItemStack container = slot.getResource(0).toStack(slot.getAmountAsInt(0));
                if (drained <= 0 || !this.canPutFuelOut(container)) {
                    return;
                }
                transaction.commit();
                input.shrink(1);
                this.putFuelOut(container);
                this.setFuelType(type);
                this.setFuel(current + drained);
                this.inventory.setChanged();
                io.github.mcheliceport.registry.ModCriteriaTriggers.trigger(io.github.mcheliceport.registry.ModCriteriaTriggers.SUPPLY_FUEL, this.getPilot());
            }
            return;
        }
    }

    /** Upstream {@code manageOutputSlot}: whether the output slot can take this container. */
    private boolean canPutFuelOut(ItemStack container) {
        ItemStack out = this.inventory.getItem(SLOT_FUEL_OUT);
        return container.isEmpty() || out.isEmpty() || ItemStack.isSameItemSameComponents(out, container)
                && out.getCount() + container.getCount() <= out.getMaxStackSize();
    }

    private void putFuelOut(ItemStack container) {
        if (container.isEmpty()) {
            return;
        }
        ItemStack out = this.inventory.getItem(SLOT_FUEL_OUT);
        if (out.isEmpty()) {
            this.inventory.setItem(SLOT_FUEL_OUT, container);
        } else {
            out.grow(container.getCount());
        }
    }

    /**
     * Upstream {@code updateElectricBattery}: every 10 ticks a battery in the fuel slot tops up the
     * store, in flight too. The charger fills the store directly.
     */
    private void updateElectricBattery() {
        if (!this.allowBattery() || this.getCountOnUpdate() % 10 != 0) {
            return;
        }
        int need = this.getMaxFuel() - this.getFuel();
        if (need <= 0) {
            return;
        }
        ItemStack battery = this.inventory.getItem(SLOT_FUEL_IN);
        int pulled = EnergyCompat.dischargeFromItem(battery, need);
        if (pulled > 0) {
            this.inventory.setChanged();
            this.setFuel(this.getFuel() + pulled);
        }
    }

    /** Upstream {@code handleItemFuelRefuel}. */
    private void refuelFromCan(ItemStack stack, int current, int max) {
        String type = AircraftFuel.DEFAULT_FUEL_TYPE;
        if (!this.canAcceptFuel(type)) {
            return;
        }
        int amount = AircraftFuel.refuelAmount(current, max, stack.getDamageValue(), stack.getMaxDamage());
        if (amount <= 0) {
            return;
        }
        stack.setDamageValue(stack.getDamageValue() + amount);
        this.setFuelType(type);
        this.setFuel(current + amount);
        if (stack.getDamageValue() >= stack.getMaxDamage()) {
            ItemStack empty = new ItemStack(stack.getItem());
            empty.setDamageValue(stack.getMaxDamage());
            if (this.canPutFuelOut(empty)) {
                this.putFuelOut(empty);
                stack.shrink(1);
            }
        }
        this.inventory.setChanged();
        io.github.mcheliceport.registry.ModCriteriaTriggers.trigger(io.github.mcheliceport.registry.ModCriteriaTriggers.SUPPLY_FUEL, this.getPilot());
    }

    // ---- damage ----

    /** A hit on one of the content's bounding boxes, with that box's damage factor. */
    public boolean hurtFromHitBox(ServerLevel level, AircraftHitBox box, DamageSource source, float damage) {
        if (!this.isHitBoxActive(box)) { return false; }
        this.lastHitBoxFactor = box.getDamageFactor();
        this.lastHitBox = box;
        try { return this.hurtServer(level, source, damage); }
        finally { this.lastHitBox = null; this.lastHitBoxFactor = 1; }
    }

    /** ERA indices count only reactive boxes, matching upstream's AcERAState string and $ERA groups. */
    public boolean isEraActive(int index) {
        String state = this.entityData.get(DATA_ERA);
        return index >= 0 && (index >= state.length() || state.charAt(index) != '0');
    }

    public boolean isHitBoxActive(AircraftHitBox hit) {
        if (hit.getInfo() == null || !hit.getInfo().isERA) { return true; }
        int index = 0;
        for (AircraftHitBox box : this.hitBoxes) {
            if (box.getInfo() == null || !box.getInfo().isERA) { continue; }
            if (box == hit) { return this.isEraActive(index); }
            index++;
        }
        return false;
    }

    public String getEraState() {
        int count = this.info == null ? 0 : (int) this.info.extraBoundingBox.stream().filter(b -> b.isERA).count();
        StringBuilder state = new StringBuilder(count);
        for (int i = 0; i < count; i++) { state.append(this.isEraActive(i) ? '1' : '0'); }
        return state.toString();
    }

    private void popEra(ServerLevel level, @Nullable AircraftHitBox hit, @Nullable Entity attacker, float damage) {
        if (hit == null || hit.getInfo() == null || !hit.getInfo().isERA) { return; }
        int index = 0;
        for (AircraftHitBox box : this.hitBoxes) {
            if (box.getInfo() == null || !box.getInfo().isERA) { continue; }
            if (box == hit && this.isEraActive(index) && damage > box.getInfo().eraMinDamage) {
                StringBuilder state = new StringBuilder(this.getEraState());
                state.setCharAt(index, '0');
                this.entityData.set(DATA_ERA, state.toString());
                if (box.getInfo().eraExplosion > 0) {
                    Vec3 p = box.getBoundingBox().getCenter();
                    McheliExplosion.newExplosion(level, null, attacker instanceof Player ? attacker : null,
                            p.x, p.y, p.z, box.getInfo().eraExplosion, 0, true, false, false, 0);
                }
                return;
            }
            index++;
        }
    }

    private static AircraftDamage.Kind kindOf(DamageSource source) {
        if (source.is(DamageTypes.LAVA)) {
            return AircraftDamage.Kind.LAVA;
        }
        if (source.is(DamageTypeTags.IS_EXPLOSION)) {
            return AircraftDamage.Kind.EXPLOSION;
        }
        if (source.is(DamageTypes.ON_FIRE)) {
            return AircraftDamage.Kind.ON_FIRE;
        }
        if (source.is(DamageTypes.IN_FIRE) || source.is(DamageTypes.CACTUS)) {
            return AircraftDamage.Kind.IGNORED;
        }
        if (source.is(DamageTypes.PLAYER_ATTACK)) {
            return AircraftDamage.Kind.PLAYER;
        }
        return AircraftDamage.Kind.OTHER;
    }

    /** Upstream {@code attackEntityFrom}. */
    @Override
    public boolean hurtServer(ServerLevel level, DamageSource source, float originalDamage) {
        if (this.ironCurtainRunningTick > 0) {
            return false;
        }
        AircraftDamage.Kind kind = kindOf(source);
        if (this.isInvulnerableToBase(source) || this.isRemoved() || this.timeSinceHit > 0
                || kind == AircraftDamage.Kind.IGNORED) {
            return false;
        }
        Entity attacker = source.getEntity();
        AircraftHitBox eraHit = this.lastHitBox;
        this.lastHitBox = null;
        boolean playDamageSound = !(attacker instanceof Player);

        float factor = this.lastHitBoxFactor;
        this.lastHitBoxFactor = 1.0F;
        float damage = 0.0F;
        if (this.canBeAttackedBy(attacker) && this.info != null) {
            // Upstream calculateDamage: the config's ...ByExternal factors first.
            float external = DamageScaling.applyDamageByExternal(this, source, originalDamage);
            AircraftDamage.Result result = AircraftDamage.calculate(kind, external, factor,
                    new AircraftDamage.Armor(this.info.armorMinDamage, this.info.armorMaxDamage,
                            this.info.armorDamageFactor, this.info.invulnerable),
                    this.random, this.timeSinceHit);
            damage = result.damage();
            this.timeSinceHit = result.timeSinceHit();
        }

        boolean isPlayerAttack = false;
        if (attacker instanceof Player player) {
            playDamageSound = true;
            if (kind == AircraftDamage.Kind.PLAYER) {
                isPlayerAttack = AircraftDamage.isPickUp(player.isCreative(), this.info != null && this.info.creativeOnly,
                        CommonConfig.PREVENTING_BROKEN.get(), CommonConfig.BREAKABLE_ONLY_PICKAXE.get(),
                        player.getMainHandItem().is(ItemTags.PICKAXES), this.isRidePlayer());
            }
            if (!isPlayerAttack) {
                McheliSounds.playAt(this, "hit", damage > 0.0F ? 1.0F : 0.5F, 1.0F);
            }
            if (isPlayerAttack && player.isCreative()) {
                this.removeByPlayer(player);
                this.playDamageSound();
                return true;
            }
        }

        if (damage <= 0.0F) {
            return false;
        }

        this.popEra(level, eraHit, attacker, damage);

        if (!this.isDestroyed()) {
            if (!isPlayerAttack) {
                this.setDamageTaken(this.getDamageTaken() + (int) damage);
                io.github.mcheliceport.damage.ComponentDamage.onHurt(this, source, damage);
            }
            this.markHurt();
            if (this.getDamageTaken() >= this.getMaxHP() || isPlayerAttack) {
                if (!isPlayerAttack) {
                    this.setDamageTaken(this.getMaxHP());
                    this.destroyAircraft();
                    this.timeSinceHit = 20;
                    this.runDestroyCommand(level);
                    if (source.is(DamageTypes.IN_WALL)) {
                        this.explosionByCrash(0.0);
                        this.damageSinceDestroyed = this.getMaxHP();
                    } else {
                        McheliExplosion.newExplosion(level, null, attacker, this.getX(), this.getY(), this.getZ(),
                                2.0F, 2.0F, true, true, true, 5);
                    }
                } else {
                    this.removeByPlayer((Player) attacker);
                }
            }
        }

        if (playDamageSound) {
            this.playDamageSound();
        }
        return true;
    }

    /** Upstream {@code MCH_Multiplay.canAttackEntity}: player-versus-player rules for the pilot. */
    private boolean canBeAttackedBy(@Nullable Entity attacker) {
        if (attacker instanceof Player attackingPlayer && this.getPilot() instanceof Player pilot) {
            return attackingPlayer.canHarmPlayer(pilot);
        }
        return true;
    }

    /** A player's pick-up: drops the item as {@code dropItemIfNeeded} decides and removes the vehicle. */
    private void removeByPlayer(Player player) {
        AircraftItem item = this.getItem();
        if (item != null && AircraftDamage.dropsItem(player.isCreative(), player.isShiftKeyDown(),
                CommonConfig.DROP_ITEM_IN_CREATIVE_MODE.get()) && this.level() instanceof ServerLevel serverLevel) {
            ItemStack stack = new ItemStack(item);
            this.writeDataToItem(stack);
            this.spawnAtLocation(serverLevel, stack);
        }
        this.removeVehicle(true);
    }

    private void playDamageSound() {
        McheliSounds.playAt(this, "helidmg", 1.0F, 0.9F + this.random.nextFloat() * 0.1F);
    }

    private void runDestroyCommand(ServerLevel level) {
        String cmd = this.getCommand().trim();
        if (cmd.startsWith("/")) {
            cmd = cmd.substring(1);
        }
        if (!cmd.isEmpty()) {
            level.getServer().getCommands().performPrefixedCommand(
                    level.getServer().createCommandSourceStack().withSuppressedOutput(), cmd);
        }
    }

    /** Upstream {@code destroyAircraft}. */
    public void destroyAircraft() {
        this.setCameraId(0);
        this.ropes.stop();
        this.switchHoveringMode(false);
        this.switchGunnerMode(false);
        for (int i = 0; i < this.getSeatNum() + 1; i++) {
            if (this.getEntityBySeatId(i) instanceof Player player) {
                this.switchCameraMode(player, AircraftCamera.MODE_NORMAL);
            }
        }
        this.setDespawnCount(20 * CommonConfig.DESPAWN_COUNT.get());
        this.rotDestroyedPitch = this.random.nextFloat() - 0.5F;
        this.rotDestroyedRoll = (this.random.nextFloat() - 0.5F) * 0.5F;
        this.rotDestroyedYaw = 0.0F;
        if (this.level() instanceof ServerLevel level) {
            float dmg = CommonConfig.KILL_PASSENGERS_WHEN_DESTROYED.get() ? 100000.0F : 0.001F;
            DamageSource source;
            if (level.getDifficulty() == Difficulty.PEACEFUL) {
                source = this.lastAttacker instanceof Player player ? this.damageSources().playerAttack(player)
                        : this.damageSources().generic();
            } else {
                source = this.damageSources().explosion(this, this.lastAttacker);
            }
            for (int sid = 0; sid < this.getSeatNum() + 1; sid++) {
                Entity rider = this.getEntityBySeatId(sid);
                if (rider != null) {
                    rider.hurtServer(level, source, dmg);
                }
            }
        }
    }

    private @Nullable Entity lastAttacker;

    @Override
    public boolean hurtClient(DamageSource source) {
        return true;
    }

    /**
     * Upstream {@code onUpdate_ParticleSmoke}: while the pilot's selected weapon is a smoke generator
     * and the throttle is open, each of its positions trails coloured smoke. Client only.
     */
    private void onUpdateParticleSmoke() {
        if (!this.level().isClientSide() || this.getCurrentThrottle() <= 0.1F) {
            return;
        }
        WeaponSet ws = this.getCurrentWeapon(this.getPilot());
        if (!(ws.getFirstWeapon() instanceof io.github.mcheliceport.weapon.types.SmokeWeapon)) {
            return;
        }
        float yaw = this.getYaw();
        float pitch = this.getPitch();
        float roll = this.getRoll();
        for (int i = 0; i < ws.getWeaponsCount(); i++) {
            io.github.mcheliceport.weapon.WeaponBase wb = ws.getWeapon(i);
            if (wb == null || wb.getInfo() == null) {
                continue;
            }
            io.github.mcheliceport.content.weapon.WeaponInfo wi = wb.getInfo();
            Vec3 rot = McheliMath.rotVec3(0.0, 0.0, 1.0, -yaw - 180.0F + wb.fixRotationYaw, pitch - wb.fixRotationPitch, roll);
            if (this.random.nextFloat() > this.getCurrentThrottle() * 1.5) {
                continue;
            }
            Vec3 pos = McheliMath.rotVec3(wb.position, -yaw, -pitch, -roll);
            double x = this.getX() + pos.x + rot.x;
            double y = this.getY() + pos.y + rot.y;
            double z = this.getZ() + pos.z + rot.z;
            for (int smk = 0; smk < wi.smokeNum; smk++) {
                float c = this.random.nextFloat() * 0.05F;
                io.github.mcheliceport.effect.ParticleParam prm = new io.github.mcheliceport.effect.ParticleParam("smoke", x, y, z);
                prm.setMotion(rot.x * wi.acceleration + (this.random.nextDouble() - 0.5) * 0.2,
                        rot.y * wi.acceleration + (this.random.nextDouble() - 0.5) * 0.2,
                        rot.z * wi.acceleration + (this.random.nextDouble() - 0.5) * 0.2);
                prm.size = (this.random.nextInt(5) + 5.0F) * wi.smokeSize;
                prm.setColor(wi.color.a + this.random.nextFloat() * 0.05F, wi.color.r + c, wi.color.g + c, wi.color.b + c);
                prm.age = (int) (this.random.nextDouble() * wi.smokeMaxAge);
                prm.toWhite = true;
                prm.diffusible = true;
                io.github.mcheliceport.effect.McheliEffects.spawnParticle(this.level(), prm);
            }
        }
    }

    /**
     * Upstream {@code onUpdate_ParticleSandCloud}: the dust (or, with {@code seaOnly}, sea spray)
     * a vehicle's downwash raises from the ground below it. Client only.
     */
    protected void onUpdateParticleSandCloud(boolean seaOnly) {
        if (!this.level().isClientSide() || this.info == null || seaOnly && !this.info.enableSeaSurfaceParticle) {
            return;
        }
        if (io.github.mcheliceport.effect.McheliEffects.rotorWash(this, seaOnly)) return;
        double particlePosY = (int) this.getY();
        boolean found = false;
        net.minecraft.world.level.block.state.BlockState ground = null;
        float scale = this.info.particlesScale * 3.0F;
        if (seaOnly) {
            scale *= 2.0F;
        }
        double throttle = Math.min(this.getCurrentThrottle() * 2.0, 1.0);
        int count = seaOnly ? (int) (scale * 7.0F) : 0;
        int rangeY = (int) (scale * 10.0F) + 1;
        int y;
        for (y = 0; y < rangeY && !found; y++) {
            for (int x = -1; x <= 1; x++) {
                for (int z = -1; z <= 1; z++) {
                    net.minecraft.world.level.block.state.BlockState state = this.level().getBlockState(new net.minecraft.core.BlockPos(
                            (int) (this.getX() + 0.5) + x, (int) (this.getY() + 0.5) - y, (int) (this.getZ() + 0.5) + z));
                    if (!found && !state.isAir()) {
                        if (seaOnly && state.is(net.minecraft.world.level.block.Blocks.WATER)) {
                            count--;
                        }
                        if (count <= 0) {
                            particlePosY = this.getY() + 1.0 + scale / 5.0F - y;
                            ground = state;
                            found = true;
                            x += 100;
                            break;
                        }
                    }
                }
            }
        }
        double pn = (rangeY - y + 1) / (5.0 * scale) / 2.0;
        if (found && this.info.particlesScale > 0.01F) {
            for (int k = 0; k < (int) (throttle * 6.0 * pn); k++) {
                float r = (float) (this.random.nextDouble() * Math.PI * 2.0);
                double dx = Mth.cos(r);
                double dz = Mth.sin(r);
                io.github.mcheliceport.effect.ParticleParam prm = new io.github.mcheliceport.effect.ParticleParam(
                        "smoke", this.getX() + dx * scale * 3.0,
                        particlePosY + (this.random.nextDouble() - 0.5) * scale, this.getZ() + dz * scale * 3.0,
                        scale * (dx * 0.3), scale * -0.4 * 0.05, scale * (dz * 0.3), scale * 5.0F);
                prm.setColor(prm.a * 0.6F, prm.r, prm.g, prm.b);
                prm.age = (int) (10.0F * scale);
                prm.motionYUpAge = seaOnly ? 0.2F : 0.1F;
                prm.groundState = ground;
                io.github.mcheliceport.effect.McheliEffects.spawnParticle(this.level(), prm);
            }
        }
    }

    /** Upstream {@code explosionByCrash}: power from the tank size, 1 to 15. */
    public void explosionByCrash(double prevMotionY) {
        float exp = this.info != null ? this.info.maxFuel / 400.0F : 2.0F;
        exp = Mth.clamp(exp, 1.0F, 15.0F);
        McheliExplosion.newExplosion(this.level(), null, null, this.getX(), this.getY(), this.getZ(), exp,
                exp >= 2.0F ? exp * 0.5F : 1.0F, true, true, true, 5);
    }

    /** Upstream {@code onUpdate_CollisionGroundDamage}. */
    private void onUpdateCollisionGroundDamage() {
        if (this.isPassenger() || this.isDestroyed() || !(this.level() instanceof ServerLevel level) || this.info == null) {
            return;
        }
        if (this.blockWithin(3, -3)) {
            float roll = Mth.abs(Mth.wrapDegrees(this.getRoll()));
            float pitch = Mth.abs(Mth.wrapDegrees(this.getPitch()));
            if (roll > 40.0F || pitch > 40.0F) {
                float dmg = Mth.abs(roll) + Mth.abs(pitch);
                if (dmg < 90.0F) {
                    dmg *= 0.4F * (float) Math.sqrt(this.distanceToSqr(this.xo, this.yo, this.zo));
                } else {
                    dmg *= 0.4F;
                }
                if (dmg > 1.0F && this.random.nextInt(4) == 0) {
                    this.hurtServer(level, this.damageSources().inWall(), dmg);
                }
            }
        }
        if (this.getCountOnUpdate() % 30 == 0 && !this.info.isFloat) {
            net.minecraft.core.BlockPos pos = net.minecraft.core.BlockPos.containing(this.getX() + 0.5,
                    this.getY() + 1.5 + this.info.submergedDamageHeight, this.getZ() + 0.5);
            if (this.level().getFluidState(pos).is(net.minecraft.tags.FluidTags.WATER)) {
                int hp = Math.max(this.getMaxHP() / 10, 1);
                this.hurtServer(level, this.damageSources().inWall(), hp);
            }
        }
    }

    /** Upstream {@code autoRepair}. */
    private void autoRepair() {
        if (this.timeSinceHit > 0) {
            this.timeSinceHit--;
        }
        if (this.getMaxHP() <= 0 || this.level().isClientSide()) {
            return;
        }
        if (!this.isDestroyed()) {
            if (this.getDamageTaken() > this.beforeDamageTaken) {
                this.repairCount = 600;
            } else if (this.repairCount > 0) {
                this.repairCount--;
            } else {
                this.repairCount = 40;
                double hpp = (double) this.getHP() / this.getMaxHP();
                if (hpp >= CommonConfig.AUTO_REPAIR_HP.get()) {
                    this.repair(this.getMaxHP() / 100);
                }
            }
        }
        this.beforeDamageTaken = this.getDamageTaken();
    }

    /** Upstream {@code repair}. */
    public boolean repair(int amount) {
        if (amount < 1) {
            amount = 1;
        }
        int damage = this.getDamageTaken();
        boolean componentsHurt = this.getComponentHealth() != io.github.mcheliceport.damage.ComponentHealth.FULL;
        if ((damage > 0 || componentsHurt) && !this.level().isClientSide()) {
            this.setDamageTaken(damage - amount);
            io.github.mcheliceport.damage.ComponentDamage.onRepair(this, amount);
            return true;
        }
        return false;
    }

    /** Upstream {@code onUpdate_updateBlock}: breaks snow layers, lily pads and cakes. */
    protected void breakSoftBlocks() {
        if (!CommonConfig.COLLISION_DESTROY_BLOCK.get() || this.level().isClientSide()) {
            return;
        }
        int baseY = Mth.floor(this.getY());
        for (int corner = 0; corner < 4; corner++) {
            int x = Mth.floor(this.getX() + (corner % 2 - 0.5) * 0.8);
            int z = Mth.floor(this.getZ() + (corner / 2.0 - 0.5) * 0.8);
            for (int dy = 0; dy < 2; dy++) {
                net.minecraft.core.BlockPos pos = new net.minecraft.core.BlockPos(x, baseY + dy, z);
                BlockState state = this.level().getBlockState(pos);
                if (state.is(Blocks.SNOW)) {
                    this.level().removeBlock(pos, false);
                } else if (state.is(Blocks.LILY_PAD) || state.is(Blocks.CAKE)) {
                    this.level().destroyBlock(pos, false);
                }
            }
        }
    }

    // ---- item data ----

    /** The vehicle item keeps its command, fuel, damage and exact ammunition counts. */
    public void writeDataToItem(ItemStack stack) {
        AircraftItemData.write(stack, this.getCommand(), this.getFuel(), this.getDamageTaken(),
                CommonConfig.ITEM_FUEL.get(), CommonConfig.ITEM_DAMAGE.get());
        // M9: damaged components ride along with the hull damage, so picking a vehicle up does not repair them.
        if (CommonConfig.ITEM_DAMAGE.get() && this.getComponentHealth() != io.github.mcheliceport.damage.ComponentHealth.FULL) {
            stack.set(io.github.mcheliceport.registry.ModDataComponents.AIRCRAFT_COMPONENTS.get(), this.getComponentHealth());
        } else {
            stack.remove(io.github.mcheliceport.registry.ModDataComponents.AIRCRAFT_COMPONENTS.get());
        }
        this.weapons.writeAmmoToItem(stack);
    }

    /** Upstream {@code getAcDataFromItem}. */
    public void readDataFromItem(ItemStack stack) {
        this.weapons.readAmmoFromItem(stack);
        AircraftItemData.Values values = AircraftItemData.read(stack, CommonConfig.ITEM_FUEL.get(),
                CommonConfig.ITEM_DAMAGE.get());
        if (values == null) {
            return;
        }
        this.setCommandForce(values.command());
        if (values.fuel() != null) {
            this.setFuel(values.fuel());
        }
        if (values.damage() != null) {
            this.setDamageTaken(values.damage());
            this.setComponentHealth(stack.getOrDefault(
                    io.github.mcheliceport.registry.ModDataComponents.AIRCRAFT_COMPONENTS.get(),
                    io.github.mcheliceport.damage.ComponentHealth.FULL));
        }
    }

    /** Upstream {@code initRotationYaw}: the attitude of a vehicle just placed. */
    public void initRotationYaw(float yaw) {
        this.setYRot(yaw);
        this.yRotO = yaw;
        this.lastRiderYaw = yaw;
        this.prevLastRiderYaw = yaw;
        this.orientation = null;
        this.weapons.initRotation();
    }

    // ---- removal ----

    @Override
    public void remove(Entity.RemovalReason reason) {
        if (reason.shouldDestroy() && this.level() instanceof net.minecraft.server.level.ServerLevel sl && this.isUAV()) { io.github.mcheliceport.entity.uav.UavPositionIndex.get(sl).remove(getUUID()); }
        if (reason.shouldDestroy() && !this.level().isClientSide()) {
            for (SeatEntity seat : this.seats) {
                if (seat != null) {
                    seat.ejectPassengers();
                    seat.discard();
                }
            }
            if (this.dropsContents) {
                net.minecraft.world.Containers.dropContents(this.level(), this, this.inventory);
                net.minecraft.world.Containers.dropContents(this.level(), this, this.parachutes);
            }
        }
        super.remove(reason);
    }

    private boolean dropsContents = true;

    /** Removes the vehicle like upstream {@code setDead(dropItems)}. */
    public void removeVehicle(boolean dropContents) {
        this.dropsContents = dropContents;
        this.ejectPassengers();
        this.discard();
    }

    // ---- weapons ----

    public AircraftWeapons weapons() {
        return this.weapons;
    }

    public AircraftRacks racks() { return this.racks; }
    public AircraftRopes ropes() { return this.ropes; }
    public float ropeLength() { return this.entityData.get(DATA_ROPE_LENGTH); }
    public int ropeTicks() { return this.entityData.get(DATA_ROPE_TICKS); }
    public void setRopeState(float length, int ticks) {
        this.entityData.set(DATA_ROPE_LENGTH, length); this.entityData.set(DATA_ROPE_TICKS, ticks);
    }

    /** Upstream queue oldest points to current + 1. */
    public Vec3 oldestPosition() {
        Vec3 oldest = this.previousPositions[(this.previousPositionIndex + 1) % this.previousPositions.length];
        return oldest != null ? oldest : this.position();
    }

    AircraftSupport support() {
        return this.support;
    }

    /** Upstream {@code canLockEntity}: the vehicle's own riders cannot be locked by its weapons. */
    public boolean canLockEntity(Entity entity) {
        return !this.isMountedEntity(entity);
    }

    /** Upstream {@code isInfinityAmmo}: creative riders and the {@code InfinityAmmo} setting. */
    public boolean isInfinityAmmo(@Nullable Entity player) {
        return this.isCreative(player) || this.getCommonStatus(STATUS_INFINITY_AMMO);
    }

    public float getStealth() {
        return this.info != null ? this.info.stealth : 0.0F;
    }

    public WeaponSet getCurrentWeapon(@Nullable Entity entity) {
        return this.weapons.getCurrentWeapon(entity);
    }

    public WeaponSet getWeapon(int id) {
        return this.weapons.getWeapon(id);
    }

    public int getWeaponNum() {
        return this.weapons.getWeaponNum();
    }

    public int getCurrentWeaponID(@Nullable Entity entity) {
        return this.weapons.getCurrentWeaponID(entity);
    }

    public float[] getCurrentWeaponShotDir(@Nullable Entity entity) {
        return this.weapons.getCurrentWeaponShotDir(entity);
    }

    public @Nullable TvMissileEntity getTVMissile() {
        return this.weapons.getTVMissile();
    }

    public void setTVMissile(@Nullable TvMissileEntity missile) {
        this.weapons.setTVMissile(missile);
    }

    /** Upstream {@code isMissileCameraMode}: the rider watches through the TV missile. */
    public boolean isMissileCameraMode(@Nullable Entity entity) {
        return this.getTVMissile() != null && this.isCameraView(entity);
    }

    /** Upstream {@code hitBullet}: shows the vehicle's hit marker. */
    public void hitBullet() {
        this.weapons.hitBullet();
    }

    public int getHitStatus() {
        return this.weapons.hitStatus;
    }

    public boolean isPilotReloading() {
        return this.weapons.isPilotReloading();
    }

    /**
     * Upstream {@code updateRecoil}: a gun with {@code Recoil} kicks the vehicle's pitch and roll
     * for the first 18 of 30 ticks after it fires.
     */
    public void updateRecoil(float partialTicks) {
        AircraftWeapons w = this.weapons;
        if (w.recoilCount > 0 && w.recoilCount >= 12) {
            float pitch = Mth.cos((float) ((w.recoilYaw - this.getRoll()) * Math.PI / 180.0));
            float roll = Mth.sin((float) ((w.recoilYaw - this.getRoll()) * Math.PI / 180.0));
            float recoil = Mth.cos((float) (w.recoilCount * 6 * Math.PI / 180.0)) * w.recoilValue;
            this.setPitch(this.getPitch() + recoil * pitch * partialTicks);
            this.setRoll(this.getRoll() + recoil * roll * partialTicks);
        }
    }

    // ---- countermeasures ----

    /** Upstream's flare, chaff and APS updates in {@code onUpdate}. */
    private void updateCountermeasures() {
        int ft = this.flares.tick;
        this.flares.update();
        if (this.info != null) {
            this.chaff.chaffUseTime = this.info.chaffUseTime;
            this.chaff.chaffWaitTime = this.info.chaffWaitTime;
            this.chaff.onUpdate();
            this.aps.useTime = this.info.apsUseTime;
            this.aps.waitTime = this.info.apsWaitTime;
            this.aps.range = this.info.apsRange;
            this.aps.onUpdate();
        }
        if (this.ironCurtainRunningTick > 0) {
            this.ironCurtainRunningTick--;
            this.ironCurtainWaveTimer++;
            this.ironCurtainLastFactor = this.ironCurtainCurrentFactor;
            this.ironCurtainCurrentFactor = 0.75F + 0.25F * (float) Math.sin(this.ironCurtainWaveTimer * 0.15);
        } else {
            this.ironCurtainWaveTimer = 0;
            this.ironCurtainCurrentFactor = 0.5F;
            this.ironCurtainLastFactor = 0.5F;
        }
        if (!this.level().isClientSide() && this.flares.tick == 0 && ft != 0) {
            this.setCommonStatus(STATUS_FLARE, false);
        }
        if (this.ecmJammerRunningTick > 0) {
            this.ecmJammerRunningTick--;
            if (this.ecmJammerRunningTick <= 0 && !this.level().isClientSide()) {
                this.setECMJammerUsing(false);
            }
        }
        if (this.ecmJammerWaitTick > 0) {
            this.ecmJammerWaitTick--;
        }
    }

    public AircraftFlares flares() {
        return this.flares;
    }

    public AircraftChaff chaff() {
        return this.chaff;
    }

    public boolean haveFlare() {
        return this.info != null && this.info.haveFlare();
    }

    /** Upstream {@code useFlare}: starts a pattern the vehicle has. */
    public boolean useFlare(int type) {
        if (!this.haveFlare()) {
            return false;
        }
        for (int i : this.info.flare.types) {
            if (i == type) {
                this.setCommonStatus(STATUS_FLARE, true);
                if (this.flares.use(type)) {
                    return true;
                }
            }
        }
        return false;
    }

    public int getCurrentFlareType() {
        if (!this.haveFlare() || this.info.flare.types.length == 0) {
            return 0;
        }
        return this.info.flare.types[this.currentFlareIndex % this.info.flare.types.length];
    }

    public void nextFlareType() {
        if (this.haveFlare() && this.info.flare.types.length > 0) {
            this.currentFlareIndex = (this.currentFlareIndex + 1) % this.info.flare.types.length;
        }
    }

    public boolean canUseFlare() {
        return this.haveFlare() && !this.getCommonStatus(STATUS_FLARE) && this.flares.tick == 0;
    }

    public boolean isFlarePreparation() {
        return this.flares.isInPreparation();
    }

    /** Upstream {@code isFlareUsing}: the first part of a flare pattern (both sides). */
    public boolean isFlareUsing() {
        return this.flares.isUsing();
    }

    /** Upstream's "FlareUsing" entity flag, set by the server's flare pattern: missiles in flight lose it. */
    public boolean isFlareDecoying() {
        return this.flares.isFlareUsing();
    }

    public int getFlareTick() {
        return this.flares.tick;
    }

    public boolean canUseChaff() {
        return this.info != null && this.info.haveChaff() && this.chaff.tick == 0;
    }

    public boolean useChaff() {
        return this.info != null && this.info.haveChaff() && this.chaff.onUse();
    }

    public boolean isChaffUsing() {
        return this.chaff.isUsing();
    }

    /** Ticks chaff is still being dispensed; radar locks fail while it is above zero. */
    public int getChaffUseTime() {
        return this.chaff.useTick;
    }

    public int getChaffTick() {
        return this.chaff.tick;
    }

    public boolean haveAPS() {
        return this.info != null && this.info.haveAPS();
    }

    public boolean canUseAPS() {
        return this.haveAPS() && this.aps.tick == 0;
    }

    public boolean useAPS() {
        return this.haveAPS() && this.aps.onUse();
    }

    public int getApsTick() {
        return this.aps.tick;
    }

    /** Upstream's iron curtain (an APS with range 100): the vehicle takes no damage for {@code ticks}. */
    public void startIronCurtain(int ticks) {
        this.ironCurtainRunningTick = ticks;
        if (!this.level().isClientSide()) {
            this.entityData.set(DATA_IRON_CURTAIN, ticks);
        }
    }

    /** Upstream {@code MCH_APS.onEnd}. */
    public void endIronCurtain() {
        this.resetIronCurtain();
        if (!this.level().isClientSide()) {
            this.entityData.set(DATA_IRON_CURTAIN, 0);
        }
    }

    private void resetIronCurtain() {
        this.ironCurtainRunningTick = 0;
        this.ironCurtainWaveTimer = 0;
        this.ironCurtainCurrentFactor = 0.5F;
        this.ironCurtainLastFactor = 0.5F;
    }

    public boolean isIronCurtainActive() {
        return this.ironCurtainRunningTick > 0;
    }

    public int getIronCurtainTick() {
        return this.ironCurtainRunningTick;
    }

    /** The red tint's brightness between ticks, as upstream's renderer eased it. */
    public float getIronCurtainFactor(float partialTicks) {
        return this.ironCurtainLastFactor + (this.ironCurtainCurrentFactor - this.ironCurtainLastFactor)
                * (float) Math.sin(partialTicks * Math.PI / 2);
    }

    /** Upstream {@code getNameOnMyRadar}: what this vehicle's radar calls {@code other}. */
    public String getNameOnMyRadar(AircraftEntity other) {
        if (other.getAcInfo() == null || this.info == null || this.info.radarType == null) {
            return "";
        }
        return radarName(other.getAcInfo(), this.info.radarType);
    }

    /** Upstream {@code getNameOnOtherRadar}: what {@code other}'s radar calls this vehicle. */
    /** Upstream {@code getNameOnMyRadar(EntityInfo)}: a tracked vehicle this client has loaded, else "?". */
    public String getNameOnMyRadar(io.github.mcheliceport.radar.EntityInfo other) {
        return this.level().getEntity(other.entityId()) instanceof AircraftEntity aircraft
                ? this.getNameOnMyRadar(aircraft) : "?";
    }

    public String getNameOnOtherRadar(AircraftEntity other) {
        if (other.getAcInfo() == null || this.info == null || other.getAcInfo().radarType == null) {
            return "?";
        }
        return radarName(this.info, other.getAcInfo().radarType);
    }

    private static String radarName(AircraftInfo shown, io.github.mcheliceport.content.aircraft.RadarType radar) {
        return switch (radar) {
            case MODERN_AA -> shown.nameOnModernAARadar;
            case EARLY_AA -> shown.nameOnEarlyAARadar;
            case MODERN_AS -> shown.nameOnModernASRadar;
            case EARLY_AS -> shown.nameOnEarlyASRadar;
        };
    }

    /** Upstream {@code isRadarEnabledRuntime}: the radar switch, off when the vehicle appears. */
    public boolean isRadarEnabledRuntime() {
        return this.getCommonStatus(STATUS_RADAR);
    }

    public void setRadarEnabledRuntime(boolean enabled, boolean writeClient) {
        this.setCommonStatus(STATUS_RADAR, enabled, writeClient);
    }

    public boolean haveECMJammer() {
        return this.info != null && this.info.enableECMJammer;
    }

    /** Upstream {@code isECMJammerUsing}: the server's jammer status, seen on both sides. */
    public boolean isECMJammerUsing() {
        return this.getCommonStatus(STATUS_ECM_JAMMER);
    }

    public void setECMJammerUsing(boolean enabled) {
        this.setCommonStatus(STATUS_ECM_JAMMER, enabled, true);
    }

    public boolean canUseECMJammer() {
        return this.haveECMJammer() && this.ecmJammerWaitTick <= 0;
    }

    /**
     * Upstream {@code useECMJammer}: runs the jammer for {@code UseTime} ticks, then it waits
     * {@code WaitTime} ticks from the start before it can run again.
     */
    public boolean useECMJammer() {
        if (!this.canUseECMJammer()) {
            return false;
        }
        this.ecmJammerRunningTick = this.info.ecmJammerUseTime;
        this.ecmJammerWaitTick = this.info.ecmJammerWaitTime;
        if (!this.level().isClientSide()) {
            this.setECMJammerUsing(true);
        }
        return true;
    }

    public int getECMJammerWaitTick() {
        return this.ecmJammerWaitTick;
    }

    // ---- searchlights ----

    public boolean haveSearchLight() { return this.info != null && !this.info.searchLights.isEmpty(); }
    public boolean isSearchLightOn() { return this.haveSearchLight() && !this.isDestroyed() && this.getCommonStatus(6); }
    public boolean canSwitchSearchLight(Entity rider) {
        int seat = this.getSeatIdByEntity(rider);
        return this.haveSearchLight() && !this.isDestroyed() && seat >= 0 && seat <= 1;
    }
    public void setSearchLight(boolean enabled) { this.setCommonStatus(6, enabled && this.haveSearchLight() && !this.isDestroyed()); }
    private @Nullable Entity searchLightOperator() {
        Entity gunner = this.getEntityBySeatId(1);
        return gunner != null ? gunner : this.getPilot();
    }
    public float searchLightYaw(float partial) {
        Entity operator = this.searchLightOperator();
        return operator != null ? operator.getYRot(partial) : this.entityData.get(DATA_LIGHT_YAW);
    }
    public float searchLightPitch(float partial) {
        Entity operator = this.searchLightOperator();
        return operator != null ? operator.getXRot(partial) : this.entityData.get(DATA_LIGHT_PITCH);
    }
    private void updateSearchLight() {
        boolean enabled = this.isSearchLightOn();
        this.previousLightHatchRotation = this.lightHatchRotation;
        this.lightHatchRotation = Mth.clamp(this.lightHatchRotation + (enabled ? 0.5F : -0.5F), 0, 1);
        if (!this.level().isClientSide()) {
            if (this.isDestroyed()) { this.setCommonStatus(6, false); }
            Entity operator = this.searchLightOperator();
            if (enabled && operator != null) {
                this.entityData.set(DATA_LIGHT_YAW, Mth.wrapDegrees(operator.getYRot()));
                this.entityData.set(DATA_LIGHT_PITCH, Mth.clamp(operator.getXRot(), -90, 90));
            }
        }
    }

    // ---- save data ----

    @Override
    protected void readAdditionalSaveData(ValueInput input) {
        this.pairedController = input.read("PairedUavController", net.minecraft.core.UUIDUtil.CODEC).orElse(null);
        this.setDespawnCount(input.getIntOr("AcDespawnCount", 0));
        this.setTextureName(input.getStringOr("TextureName", ""));
        this.setRoll(input.getFloatOr("AcRoll", 0.0F));
        this.prevRoll = this.getRoll();
        this.prevLastRiderYaw = this.lastRiderYaw = input.getFloatOr("AcLastRYaw", 0.0F);
        this.prevLastRiderPitch = this.lastRiderPitch = input.getFloatOr("AcLastRPitch", 0.0F);
        this.setPartStatus(input.getIntOr("PartStatus", 0));
        this.setAircraftTypeName(input.getStringOr("TypeName", ""));
        this.entityData.set(DATA_ERA, input.getStringOr("AcERAState", ""));
        NonNullList<ItemStack> items = NonNullList.withSize(this.inventory.getContainerSize(), ItemStack.EMPTY);
        ContainerHelper.loadAllItems(input.childOrEmpty("ItemsAC"), items);
        for (int i = 0; i < items.size(); i++) {
            this.inventory.setItem(i, items.get(i));
        }
        NonNullList<ItemStack> chutes = NonNullList.withSize(2, ItemStack.EMPTY);
        ContainerHelper.loadAllItems(input.childOrEmpty("ParachutesAC"), chutes);
        for (int i = 0; i < chutes.size(); i++) {
            this.parachutes.setItem(i, chutes.get(i));
        }
        this.setCommandForce(input.getStringOr("AcCommand", ""));
        this.setGunnerStatus(input.getBooleanOr("AcGunnerStatus", true));
        this.setFuel(input.getIntOr("AcFuel", 0));
        this.setFuelType(input.getStringOr("AcFuelType", AircraftFuel.DEFAULT_FUEL_TYPE));
        input.getIntArray("AcWeaponsAmmo").ifPresent(this.weapons::loadAmmo);
        if (this.getDespawnCount() > 0) {
            this.setDamageTaken(this.getMaxHP());
        } else {
            input.getInt("AcDamage").ifPresent(this::setDamageTaken);
        }
        this.setComponentHealth(input.getLongOr("AcComponents", io.github.mcheliceport.damage.ComponentHealth.FULL));
        this.setCurrentThrottle(input.getDoubleOr("AcThrottle", 0.0));
        boolean hovering = input.getBooleanOr("AcHoveringMode", false);
        if (hovering != this.hoveringMode) {
            this.beforeHoverThrottle = input.getDoubleOr("AcBeforeHoverThrottle", 0.0);
            this.hoveringMode = hovering;
            this.entityData.set(DATA_HOVERING, hovering);
        }
        this.ropes.load(input);
        this.setSearchLight(input.getBooleanOr("SearchLight", false));
        this.entityData.set(DATA_LIGHT_YAW, input.getFloatOr("SearchLightYaw", this.getYaw()));
        this.entityData.set(DATA_LIGHT_PITCH, input.getFloatOr("SearchLightPitch", this.getPitch()));
        this.orientation = null;
    }

    @Override
    protected void addAdditionalSaveData(ValueOutput output) {
        if (pairedController != null) { output.store("PairedUavController", net.minecraft.core.UUIDUtil.CODEC, pairedController); }
        this.ropes.save(output);
        output.putBoolean("SearchLight", this.isSearchLightOn());
        output.putFloat("SearchLightYaw", this.entityData.get(DATA_LIGHT_YAW));
        output.putFloat("SearchLightPitch", this.entityData.get(DATA_LIGHT_PITCH));
        output.putString("AcERAState", this.getEraState());
        output.putString("TextureName", this.getTextureName());
        output.putString("TypeName", this.getAircraftTypeName());
        output.putInt("PartStatus", this.partStatus() & this.getLastPartStatusMask());
        output.putInt("AcFuel", this.getFuel());
        output.putString("AcFuelType", this.getFuelType());
        output.putInt("AcDespawnCount", this.getDespawnCount());
        output.putFloat("AcRoll", this.getRoll());
        output.putFloat("AcLastRYaw", this.lastRiderYaw);
        output.putFloat("AcLastRPitch", this.lastRiderPitch);
        output.putString("AcCommand", this.getCommand());
        output.putBoolean("AcGunnerStatus", this.getGunnerStatus());
        NonNullList<ItemStack> items = NonNullList.withSize(this.inventory.getContainerSize(), ItemStack.EMPTY);
        for (int i = 0; i < items.size(); i++) {
            items.set(i, this.inventory.getItem(i));
        }
        ContainerHelper.saveAllItems(output.child("ItemsAC"), items);
        NonNullList<ItemStack> chutes = NonNullList.withSize(2, ItemStack.EMPTY);
        for (int i = 0; i < chutes.size(); i++) {
            chutes.set(i, this.parachutes.getItem(i));
        }
        ContainerHelper.saveAllItems(output.child("ParachutesAC"), chutes);
        output.putIntArray("AcWeaponsAmmo", this.weapons.saveAmmo());
        output.putInt("AcDamage", this.getDamageTaken());
        output.putLong("AcComponents", this.getComponentHealth());
        output.putDouble("AcThrottle", this.getCurrentThrottle());
        output.putBoolean("AcHoveringMode", this.hoveringMode);
        output.putDouble("AcBeforeHoverThrottle", this.beforeHoverThrottle);
    }

    /** Remembers who hit the vehicle last, for the passengers' death message. */
    public void setLastAttacker(@Nullable Entity attacker) {
        this.lastAttacker = attacker;
    }

    /** All vehicles of a level near a point, for commands and tests. */
    public static List<AircraftEntity> findNear(Level level, AABB box) {
        return new ArrayList<>(level.getEntitiesOfClass(AircraftEntity.class, box));
    }
}
