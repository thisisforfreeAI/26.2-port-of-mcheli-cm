package io.github.mcheliceport.entity.gltd;

import io.github.mcheliceport.config.CommonConfig;
import io.github.mcheliceport.content.ContentRegistries;
import io.github.mcheliceport.content.weapon.WeaponInfo;
import io.github.mcheliceport.entity.aircraft.AircraftCamera;
import io.github.mcheliceport.net.GltdControlPayload;
import io.github.mcheliceport.registry.ModItems;
import io.github.mcheliceport.util.McheliSounds;
import io.github.mcheliceport.weapon.DamageScaling;
import io.github.mcheliceport.weapon.types.CasWeapon;
import net.minecraft.core.BlockPos;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.Mth;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.InterpolationHandler;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.MoverType;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

/**
 * The ground laser target designator (upstream {@code MCH_EntityGLTD}): a tripod one player sits
 * behind. Through its camera (zoom 1 to 10, normal or night vision) the operator calls in the
 * A-10's close air support on the block in view, then waits for the designator to cool down.
 *
 * <p>The view, zoom and controls are client-side ({@code GltdCameraClient}); the camera mode,
 * strikes and dismounting reach the server as a {@code GltdControlPayload}.
 */
public class GltdEntity extends Entity {

    /** Upstream {@code MCH_WeaponCAS}' interval, -300 (-310 on the client), lengthened by 150 for the designator. */
    public static final int SERVER_INTERVAL = -450;
    public static final int CLIENT_INTERVAL = -460;
    public static final float MAX_ZOOM = 10.0F;
    /** The operator may look this far from the tripod's heading, and up or down. */
    public static final float VIEW_LIMIT = 70.0F;
    private static final String CAS_WEAPON = "a10gau8";
    private static final double SPEED_MAX = 0.35;
    private static final double SPEED_MIN = 0.07;

    public final AircraftCamera camera = new AircraftCamera();
    private @Nullable CasWeapon weaponCAS;
    /** Ticks until the next strike; counts towards 0 from either side, as upstream. */
    public int countWait;
    /** The operator's view the head was last drawn with, kept once they leave. */
    public boolean isUsedPlayer;
    public float renderRotationYaw;
    public float renderRotationPitch;
    private int damageTaken;
    private int timeSinceHit;
    private double speedMultiplier = SPEED_MIN;
    private final InterpolationHandler interpolation = new InterpolationHandler(this, 3);

    public GltdEntity(EntityType<? extends GltdEntity> type, Level level) {
        super(type, level);
        this.blocksBuilding = true;
    }

    @Override
    protected void defineSynchedData(SynchedEntityData.Builder builder) {
    }

    /** The A-10 strike, or null when the content defines no {@code a10gau8}. */
    public @Nullable CasWeapon weapon() {
        if (this.weaponCAS == null) {
            WeaponInfo info = ContentRegistries.weapon().get(CAS_WEAPON);
            if (info == null) {
                return null;
            }
            this.weaponCAS = new CasWeapon(this.level(), Vec3.ZERO, 0.0F, 0.0F, CAS_WEAPON, info);
            this.weaponCAS.interval = this.level().isClientSide() ? CLIENT_INTERVAL : SERVER_INTERVAL;
            this.weaponCAS.displayName = "A-10 GAU-8 Avenger";
        }
        return this.weaponCAS;
    }

    public @Nullable Entity operator() {
        return this.getFirstPassenger();
    }

    @Override
    public void tick() {
        super.tick();
        if (this.timeSinceHit > 0) {
            this.timeSinceHit--;
        }
        if (this.damageTaken > 0) {
            this.damageTaken--;
        }
        Entity rider = this.operator();
        if (rider != null) {
            this.camera.updateViewer(0, rider);
        }
        if (this.level().isClientSide()) {
            this.interpolation.interpolate();
        } else {
            this.tickPhysics();
        }
        this.updateCameraPosition(false);
        if (this.countWait > 0) {
            this.countWait--;
        } else if (this.countWait < 0) {
            this.countWait++;
        }
        CasWeapon weapon = this.weapon();
        if (weapon != null) {
            weapon.update(this.countWait);
        }
    }

    /** Upstream's boat-like movement: it falls, slows on the ground and turns towards where it slides. */
    private void tickPhysics() {
        Vec3 motion = this.getDeltaMovement();
        double before = motion.horizontalDistance();
        motion = motion.add(0.0, -0.04, 0.0);
        double horizontal = motion.horizontalDistance();
        if (horizontal > SPEED_MAX) {
            double scale = SPEED_MAX / horizontal;
            motion = new Vec3(motion.x * scale, motion.y, motion.z * scale);
            horizontal = SPEED_MAX;
        }
        if (horizontal > before && this.speedMultiplier < SPEED_MAX) {
            this.speedMultiplier = Math.min(SPEED_MAX, this.speedMultiplier + (SPEED_MAX - this.speedMultiplier) / 35.0);
        } else {
            this.speedMultiplier = Math.max(SPEED_MIN, this.speedMultiplier - (this.speedMultiplier - SPEED_MIN) / 35.0);
        }
        if (this.onGround()) {
            motion = motion.scale(0.5);
        }
        double prevX = this.getX();
        double prevZ = this.getZ();
        this.move(MoverType.SELF, motion);
        this.setDeltaMovement(this.getDeltaMovement().multiply(0.99, 0.95, 0.99));
        double yaw = this.getYRot();
        double dx = prevX - this.getX();
        double dz = prevZ - this.getZ();
        if (dx * dx + dz * dz > 0.001) {
            yaw = (float) (Math.atan2(dz, dx) * 180.0 / Math.PI);
        }
        double turn = Mth.clamp(Mth.wrapDegrees(yaw - this.getYRot()), -20.0, 20.0);
        this.setRot((float) (this.getYRot() + turn), 0.0F);
        if (CommonConfig.COLLISION_DESTROY_BLOCK.get()) {
            for (int l = 0; l < 4; l++) {
                int x = Mth.floor(this.getX() + (l % 2 - 0.5) * 0.8);
                int z = Mth.floor(this.getZ() + (l / 2.0 - 0.5) * 0.8);
                for (int k = 0; k < 2; k++) {
                    BlockPos pos = new BlockPos(x, Mth.floor(this.getY()) + k, z);
                    if (this.level().getBlockState(pos).is(Blocks.SNOW)) {
                        this.level().removeBlock(pos, false);
                    }
                }
            }
        }
    }

    /** Upstream {@code updateCameraPosition}: the optics sit 0.7 up and 0.6 in front of the tripod. */
    public void updateCameraPosition(boolean force) {
        if (force || this.operator() != null) {
            double yaw = Math.toRadians(this.getYRot());
            this.camera.setPosition(this.getX() - Math.sin(yaw) * 0.6, this.getY() + 0.7,
                    this.getZ() + Math.cos(yaw) * 0.6);
        }
    }

    public void zoomCamera(float step) {
        this.camera.setCameraZoom(Mth.clamp(this.camera.getCameraZoom() + step, 1.0F, MAX_ZOOM));
    }

    /**
     * Upstream {@code useCurrentWeapon}: calls in the strike on the block the operator looks at,
     * when the designator has cooled down. The client checks first (80 blocks, at least 20 away)
     * and the server again (150 blocks).
     */
    public boolean useCurrentWeapon() {
        Entity rider = this.operator();
        CasWeapon weapon = this.weapon();
        if (this.countWait != 0 || rider == null || weapon == null
                || !weapon.performStrikeInquiry(rider, null, this.camera.posX, this.camera.posY, this.camera.posZ)) {
            return false;
        }
        this.countWait = weapon.interval;
        if (this.level().isClientSide()) {
            this.countWait += this.countWait > 0 ? 10 : -10;
        } else {
            McheliSounds.play(this.level(), this.getX(), this.getY(), this.getZ(), "gltd", 0.5F, 1.0F);
        }
        return true;
    }

    /** Upstream {@code PacketGLTDControl#onReceive}, for the player's own designator only. */
    public static void handleControl(ServerPlayer player, GltdControlPayload payload) {
        if (!(player.getVehicle() instanceof GltdEntity gltd) || gltd.operator() != player) {
            return;
        }
        if (payload.unmount()) {
            player.stopRiding();
            return;
        }
        if (payload.switchCameraMode() >= 0) {
            gltd.camera.setMode(0, payload.switchCameraMode());
        }
        if (payload.useWeapon()) {
            gltd.useCurrentWeapon();
        }
    }

    @Override
    public InteractionResult interact(Player player, InteractionHand hand, Vec3 location) {
        Entity rider = this.operator();
        if (rider instanceof Player && rider != player) {
            return InteractionResult.PASS;
        }
        player.setYRot(Mth.wrapDegrees(this.getYRot()));
        player.setXRot(Mth.wrapDegrees(this.getXRot()));
        if (!this.level().isClientSide()) {
            if (rider != player && !player.startRiding(this)) {
                return InteractionResult.FAIL;
            }
        } else {
            this.camera.setCameraZoom(1.0F);
            // Upstream: no strike in the first three seconds after sitting down.
            if (this.countWait > 0) {
                this.countWait = -this.countWait;
            }
            if (this.countWait > -60) {
                this.countWait = -60;
            }
        }
        this.updateCameraPosition(true);
        return InteractionResult.SUCCESS;
    }

    /**
     * Upstream {@code updateGLTD}: the operator looks at most 70 degrees from the tripod's
     * heading and 70 up or down; the camera turns with them.
     */
    public void clampView(Entity rider) {
        rider.setXRot(Mth.clamp(rider.getXRot(), -VIEW_LIMIT, VIEW_LIMIT));
        float off = Mth.wrapDegrees(rider.getYRot() - this.getYRot());
        float clamped = Mth.clamp(off, -VIEW_LIMIT, VIEW_LIMIT);
        if (clamped != off) {
            rider.setYRot(rider.getYRot() - off + clamped);
        }
        this.camera.rotationYaw = rider.getYRot();
        this.camera.rotationPitch = rider.getXRot();
    }

    @Override
    public void onPassengerTurned(Entity passenger) {
        this.clampView(passenger);
    }

    @Override
    protected boolean canAddPassenger(Entity passenger) {
        return this.getPassengers().isEmpty();
    }

    /** Upstream {@code updatePassenger}: the operator sits half a block behind the tripod. */
    @Override
    protected void positionRider(Entity passenger, MoveFunction move) {
        if (this.hasPassenger(passenger)) {
            double yaw = Math.toRadians(this.getYRot());
            move.accept(passenger, this.getX() + Math.sin(yaw) * 0.5, this.getY() - 0.65,
                    this.getZ() - Math.cos(yaw) * 0.5);
        }
    }

    /** Upstream {@code setUnmoundPosition}: 1.2 behind the tripod. */
    @Override
    public Vec3 getDismountLocationForPassenger(LivingEntity passenger) {
        double yaw = Math.toRadians(this.getYRot());
        return new Vec3(this.getX() + Math.sin(yaw) * 1.2, this.getY() + 0.35, this.getZ() - Math.cos(yaw) * 1.2);
    }

    /** Upstream {@code unmountEntity}: the camera goes back to normal, which ends its night vision. */
    @Override
    protected void removePassenger(Entity passenger) {
        super.removePassenger(passenger);
        this.camera.setMode(0, AircraftCamera.MODE_NORMAL);
        this.camera.setCameraZoom(1.0F);
        this.camera.updateViewer(0, passenger);
    }

    @Override
    public boolean hurtServer(ServerLevel level, DamageSource source, float damage) {
        if (this.isInvulnerableToBase(source) || this.isRemoved()) {
            return true;
        }
        damage = DamageScaling.applyDamageByExternal(this, source, damage);
        if (this.operator() instanceof Player rider && source.getEntity() instanceof Player attacker
                && !attacker.canHarmPlayer(rider)) {
            return false;
        }
        this.timeSinceHit = 10;
        this.damageTaken = (int) (this.damageTaken + damage * 100.0F);
        boolean creative = source.getEntity() instanceof Player player && player.isCreative();
        if (creative || this.damageTaken > 40) {
            Entity rider = this.operator();
            this.camera.initCamera(0, rider);
            this.ejectPassengers();
            if (!creative) {
                this.spawnAtLocation(level, new ItemStack(ModItems.GLTD.get()));
            }
            McheliSounds.play(level, this.getX(), this.getY(), this.getZ(), "hit", 1.0F, 1.0F);
            this.discard();
        }
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
    public InterpolationHandler getInterpolation() {
        return this.interpolation;
    }

    public int damageTaken() {
        return this.damageTaken;
    }

    /** Upstream saves nothing but the position and heading. */
    @Override
    protected void addAdditionalSaveData(ValueOutput output) {
    }

    @Override
    protected void readAdditionalSaveData(ValueInput input) {
    }
}
