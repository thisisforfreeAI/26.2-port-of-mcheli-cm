package io.github.mcheliceport.entity.gunner;

import io.github.mcheliceport.config.CommonConfig;
import io.github.mcheliceport.content.aircraft.AircraftInfo;
import io.github.mcheliceport.content.aircraft.SeatInfo;
import io.github.mcheliceport.content.weapon.WeaponInfo;
import io.github.mcheliceport.entity.aircraft.AircraftEntity;
import io.github.mcheliceport.entity.seat.SeatEntity;
import io.github.mcheliceport.entity.vehicle.VehicleEntity;
import io.github.mcheliceport.entity.weapon.AAMissileEntity;
import io.github.mcheliceport.entity.weapon.ASMissileEntity;
import io.github.mcheliceport.entity.weapon.ATMissileEntity;
import io.github.mcheliceport.entity.weapon.BaseBulletEntity;
import io.github.mcheliceport.entity.weapon.TvMissileEntity;
import io.github.mcheliceport.registry.ModItems;
import io.github.mcheliceport.util.McheliSounds;
import io.github.mcheliceport.weapon.WeaponBase;
import io.github.mcheliceport.weapon.WeaponParam;
import io.github.mcheliceport.weapon.WeaponSet;
import io.github.mcheliceport.weapon.types.EntitySeekerWeapon;
import java.util.List;
import net.minecraft.network.chat.Component;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.Mth;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.damagesource.DamageTypes;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.HumanoidArm;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.monster.EnderMan;
import net.minecraft.world.entity.monster.Enemy;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.scores.PlayerTeam;
import org.jspecify.annotations.Nullable;

/**
 * Upstream's {@code MCH_EntityGunner}: an NPC that sits in a vehicle's seat and, while the
 * vehicle's NPC gunner switch is on, aims and fires the seat's current weapon at the nearest
 * monster (target type 0) or player of another team (target type 1) it can see. Off a vehicle it
 * goes away after a second, leaving its spawn item.
 */
public class GunnerEntity extends LivingEntity {

    private static final EntityDataAccessor<String> DATA_TEAM_NAME =
            SynchedEntityData.defineId(GunnerEntity.class, EntityDataSerializers.STRING);

    public static final int VS_MONSTER = 0;
    public static final int VS_PLAYER = 1;

    public boolean isCreative;
    public String ownerUUID = "";
    public int targetType = VS_MONSTER;
    private int despawnCount;
    private int switchTargetCount;
    private @Nullable LivingEntity targetEntity;
    private double targetPrevPosX;
    private double targetPrevPosY;
    private double targetPrevPosZ;
    private boolean waitCooldown;
    private int idleCount;
    private int idleRotation;
    /** Upstream (Reforged): ticks until the gunner may fire countermeasures again. */
    private int autoCountermeasureCooldown;
    private boolean droppedItem;

    public GunnerEntity(EntityType<? extends GunnerEntity> type, Level level) {
        super(type, level);
    }

    @Override
    protected void defineSynchedData(SynchedEntityData.Builder builder) {
        super.defineSynchedData(builder);
        builder.define(DATA_TEAM_NAME, "");
    }

    public String getTeamName() {
        return this.entityData.get(DATA_TEAM_NAME);
    }

    public void setTeamName(String name) {
        this.entityData.set(DATA_TEAM_NAME, name);
    }

    /** Upstream: the scoreboard team named by {@code TeamName}, not one the gunner joined. */
    @Override
    public @Nullable PlayerTeam getTeam() {
        String name = this.getTeamName();
        return name.isEmpty() ? null : this.level().getScoreboard().getPlayerTeam(name);
    }

    /** Upstream's name tag: the team's name and "Gunner" in the team's colours, else nothing. */
    @Override
    public Component getDisplayName() {
        PlayerTeam team = this.getTeam();
        return team != null ? PlayerTeam.formatNameForTeam(team, Component.literal(team.getName() + " Gunner"))
                : Component.empty();
    }

    public @Nullable LivingEntity getTargetEntity() {
        return this.targetEntity;
    }

    @Override
    public HumanoidArm getMainArm() {
        return HumanoidArm.RIGHT;
    }

    /** Upstream {@code isEntityInvulnerable}: a gunner placed in creative mode takes no damage. */
    @Override
    public boolean isInvulnerableTo(ServerLevel level, DamageSource source) {
        return this.isCreative && !source.is(DamageTypes.FELL_OUT_OF_WORLD) || super.isInvulnerableTo(level, source);
    }

    @Override
    public boolean hurtServer(ServerLevel level, DamageSource source, float damage) {
        if (source.is(DamageTypes.FELL_OUT_OF_WORLD)) {
            this.discard();
        }
        return super.hurtServer(level, source, damage);
    }

    /** Upstream {@code changeDimension}: gunners stay in their dimension. */
    @Override
    public boolean canUsePortal(boolean ignorePassenger) {
        return false;
    }

    /**
     * Upstream {@code processInitialInteract}: a player takes the gunner off the vehicle, getting
     * its spawn item back, unless it was placed in creative mode or belongs to another team.
     */
    @Override
    public InteractionResult interact(Player player, InteractionHand hand, Vec3 location) {
        if (this.level().isClientSide() || this.getVehicle() == null) {
            return InteractionResult.PASS;
        }
        if (player.isCreative()) {
            this.removeFromAircraft(player);
            return InteractionResult.SUCCESS;
        }
        if (this.isCreative) {
            player.sendSystemMessage(Component.literal("Creative mode only."));
            return InteractionResult.PASS;
        }
        if (this.getTeam() != null && !this.isAlliedTo(player)) {
            player.sendSystemMessage(Component.literal("You are other team."));
            return InteractionResult.PASS;
        }
        this.removeFromAircraft(player);
        return InteractionResult.SUCCESS;
    }

    public void removeFromAircraft(Player player) {
        if (this.level().isClientSide()) {
            return;
        }
        McheliSounds.playAt(player, "wrench", 1.0F, 1.0F);
        AircraftEntity ac = this.getAc();
        String name = "";
        if (ac != null && ac.getAcInfo() != null) {
            name = " on " + ac.getAcInfo().displayName + " seat " + (ac.getSeatIdByEntity(this) + 1);
        }
        this.discard();
        player.sendSystemMessage(Component.literal("Remove gunner" + name + " by ")
                .append(PlayerTeam.formatNameForTeam(player.getTeam(), player.getDisplayName())).append("."));
        this.stopRiding();
    }

    /** Upstream {@code setDead}: a gunner that dies or is removed drops its spawn item, unless creative. */
    @Override
    public void remove(Entity.RemovalReason reason) {
        if (!this.droppedItem && !this.isCreative && this.level() instanceof ServerLevel level
                && (reason == Entity.RemovalReason.KILLED || reason == Entity.RemovalReason.DISCARDED)) {
            this.droppedItem = true;
            this.spawnAtLocation(level, this.targetType == VS_MONSTER ? ModItems.SPAWN_GUNNER_VS_MONSTER.get()
                    : ModItems.SPAWN_GUNNER_VS_PLAYER.get());
        }
        super.remove(reason);
    }

    @Override
    public void tick() {
        super.tick();
        if (!this.level().isClientSide() && this.isAlive()) {
            Entity vehicle = this.getVehicle();
            if (vehicle != null && vehicle.isRemoved()) {
                this.stopRiding();
                vehicle = null;
            }
            AircraftEntity host = this.getAc();
            if (host != null) {
                this.shotTarget(host);
            } else if (this.despawnCount < 20) {
                this.despawnCount++;
            } else if (vehicle == null || this.tickCount > 100) {
                this.discard();
            }
            if (host != null && this.isAlive()) {
                this.autoUseCountermeasures(host);
            }
            if (this.targetEntity == null) {
                if (this.idleCount == 0) {
                    this.idleCount = (3 + this.random.nextInt(5)) * 20;
                    this.idleRotation = this.random.nextInt(5) - 2;
                }
                this.setYRot(this.getYRot() + this.idleRotation / 2.0F);
            } else {
                this.idleCount = 60;
            }
        }
        if (this.autoCountermeasureCooldown > 0) {
            this.autoCountermeasureCooldown--;
        }
        if (this.switchTargetCount > 0) {
            this.switchTargetCount--;
        }
        if (this.idleCount > 0) {
            this.idleCount--;
        }
    }

    // ---- countermeasures (upstream Reforged) ----

    /**
     * When a guided weapon homes on the host vehicle, the gunner fires the countermeasures it
     * carries, then waits 30 ticks; checked every 5 ticks.
     */
    private void autoUseCountermeasures(AircraftEntity ac) {
        if (ac.isDestroyed() || this.autoCountermeasureCooldown > 0 || this.tickCount % 5 != 0
                || !this.hasIncomingGuidedThreat(ac)) {
            return;
        }
        boolean used = false;
        int flareType = this.getAutoCountermeasureFlareType(ac);
        if (flareType > 0 && ac.canUseFlare() && ac.useFlare(flareType)) {
            used = true;
        }
        if (ac.canUseChaff() && ac.useChaff()) {
            used = true;
        }
        if (ac.canUseAPS() && ac.useAPS()) {
            used = true;
        }
        if (used) {
            this.autoCountermeasureCooldown = 30;
        }
    }

    /** The decoy pattern 10 when the vehicle has it, else its selected or first pattern. */
    private int getAutoCountermeasureFlareType(AircraftEntity ac) {
        AircraftInfo info = ac.getAcInfo();
        if (!ac.haveFlare() || info == null || info.flare == null || info.flare.types == null) {
            return 0;
        }
        int[] types = info.flare.types;
        for (int type : types) {
            if (type == 10) {
                return 10;
            }
        }
        int current = ac.getCurrentFlareType();
        if (current > 0) {
            return current;
        }
        return types.length > 0 ? types[0] : 0;
    }

    /** A guided weapon within 220 blocks homes on the vehicle or one of its riders. */
    private boolean hasIncomingGuidedThreat(AircraftEntity ac) {
        List<BaseBulletEntity> list = this.level().getEntitiesOfClass(BaseBulletEntity.class,
                ac.getBoundingBox().inflate(220.0));
        for (BaseBulletEntity bullet : list) {
            WeaponInfo info = bullet.getInfo();
            Entity target = bullet.targetEntity;
            if (!bullet.isAlive() || info == null || target == null) {
                continue;
            }
            if (!ac.isMountedEntity(target) && target != ac) {
                continue;
            }
            if (info.isHeatSeekerMissile || info.isRadarMissile || info.passiveRadar || info.activeRadar
                    || info.laserGuidance || info.isGPSMissile || bullet instanceof AAMissileEntity
                    || bullet instanceof ATMissileEntity || bullet instanceof ASMissileEntity
                    || bullet instanceof TvMissileEntity) {
                return true;
            }
        }
        return false;
    }

    // ---- targeting ----

    /** Upstream {@code canAttackEntity}. */
    public boolean canAttackEntity(LivingEntity entity, AircraftEntity ac, WeaponSet ws) {
        boolean ret;
        if (this.targetType == VS_MONSTER) {
            ret = entity != this && !(entity instanceof EnderMan) && entity.isAlive() && !this.isAlliedTo(entity)
                    && entity.getHealth() > 0.0F && !ac.isMountedEntity(entity);
        } else {
            ret = entity != this && !(entity instanceof Player player && player.isCreative()) && entity.isAlive()
                    && !this.getTeamName().isEmpty() && !this.isAlliedTo(entity) && entity.getHealth() > 0.0F
                    && !ac.isMountedEntity(entity);
        }
        if (ret && ws.getCurrentWeapon().getGuidanceSystem() != null) {
            ret = ws.getCurrentWeapon().getGuidanceSystem().canLockEntity(entity);
        }
        return ret;
    }

    /** Upstream {@code shotTarget}: picks the nearest target, turns towards where it will be and fires. */
    public void shotTarget(AircraftEntity ac) {
        if (ac.isDestroyed() || !ac.getGunnerStatus()) {
            return;
        }
        WeaponSet ws = ac.getCurrentWeapon(this);
        if (ws == null || ws.getInfo() == null || ws.getCurrentWeapon() == null) {
            return;
        }
        WeaponBase cw = ws.getCurrentWeapon();
        if (this.targetEntity != null && (!this.targetEntity.isAlive() || this.targetEntity.getHealth() <= 0.0F)
                && this.switchTargetCount > 20) {
            this.switchTargetCount = 20;
        }
        Vec3 pos = this.getGunnerWeaponPos(ac, ws);
        if (this.switchTargetCount <= 0) {
            this.switchTargetCount = 20;
            LivingEntity nextTarget = null;
            List<? extends LivingEntity> list;
            if (this.targetType == VS_MONSTER) {
                int rh = CommonConfig.GUNNER_VS_MONSTER_HORIZONTAL.get();
                int rv = CommonConfig.GUNNER_VS_MONSTER_VERTICAL.get();
                list = this.level().getEntitiesOfClass(LivingEntity.class,
                        this.getBoundingBox().inflate(rh, rv, rh), e -> e instanceof Enemy);
            } else {
                int rh = CommonConfig.GUNNER_VS_PLAYER_HORIZONTAL.get();
                int rv = CommonConfig.GUNNER_VS_PLAYER_VERTICAL.get();
                list = this.level().getEntitiesOfClass(Player.class, this.getBoundingBox().inflate(rh, rv, rh));
            }
            for (LivingEntity entity : list) {
                if (this.canAttackEntity(entity, ac, ws) && this.checkPitch(entity, ac, pos)
                        && (nextTarget == null || this.distanceTo(entity) < this.distanceTo(nextTarget))
                        && this.hasLineOfSight(entity) && this.isInAttackable(entity, ac, pos)) {
                    nextTarget = entity;
                    this.switchTargetCount = 60;
                }
            }
            if (nextTarget != null && this.targetEntity != nextTarget) {
                this.targetPrevPosX = nextTarget.getX();
                this.targetPrevPosY = nextTarget.getY();
                this.targetPrevPosZ = nextTarget.getZ();
            }
            this.targetEntity = nextTarget;
        }
        LivingEntity target = this.targetEntity;
        if (target == null) {
            this.setXRot(this.getXRot() * 0.95F);
            return;
        }
        float rotSpeed = 10.0F;
        if (ac.isPilot(this)) {
            rotSpeed = ac.getAcInfo().cameraRotationSpeed / 10.0F;
        }
        float rotPitch = Mth.wrapDegrees(this.getXRot());
        float rotYaw = Mth.wrapDegrees(this.getYRot());
        double dist = this.distanceTo(target);
        double tick = 1.0;
        if (dist >= 10.0 && ws.getInfo().acceleration > 1.0F) {
            tick = dist / ws.getInfo().acceleration;
        }
        if (target.getVehicle() instanceof SeatEntity || target.getVehicle() instanceof AircraftEntity) {
            tick -= CommonConfig.HIT_BOX_DELAY_TICK.get();
        }
        double dx = (target.getX() - this.targetPrevPosX) * tick;
        double dy = (target.getY() - this.targetPrevPosY) * tick + target.getBbHeight() * this.random.nextDouble();
        double dz = (target.getZ() - this.targetPrevPosZ) * tick;
        double d0 = target.getX() + dx - pos.x;
        double d1 = target.getY() + dy - pos.y;
        double d2 = target.getZ() + dz - pos.z;
        double d3 = Math.sqrt(d0 * d0 + d2 * d2);
        float yaw = Mth.wrapDegrees((float) (Math.atan2(d2, d0) * 180.0 / Math.PI) - 90.0F);
        float pitch = (float) -(Math.atan2(d1, d3) * 180.0 / Math.PI);
        if (Math.abs(rotPitch - pitch) < rotSpeed && Math.abs(rotYaw - yaw) < rotSpeed) {
            float r = ac.isPilot(this) ? 0.1F : 0.5F;
            rotPitch = pitch + (this.random.nextFloat() - 0.5F) * r - cw.fixRotationPitch;
            rotYaw = yaw + (this.random.nextFloat() - 0.5F) * r;
            this.setXRot(rotPitch);
            this.setYRot(rotYaw);
            if (!this.waitCooldown || ws.getCurrentHeat() <= 0 || ws.getInfo().maxHeatCount <= 0) {
                this.waitCooldown = false;
                WeaponParam prm = new WeaponParam();
                prm.setPosition(ac.getX(), ac.getY(), ac.getZ());
                prm.user = this;
                prm.entity = ac;
                prm.option1 = cw instanceof EntitySeekerWeapon ? target.getId() : 0;
                if (ac.weapons().useCurrentWeapon(prm) && ws.getInfo().maxHeatCount > 0
                        && ws.getCurrentHeat() > ws.getInfo().maxHeatCount * 4 / 5) {
                    this.waitCooldown = true;
                }
            }
        }
        if (Math.abs(pitch - rotPitch) >= rotSpeed) {
            rotPitch += pitch > rotPitch ? rotSpeed : -rotSpeed;
        }
        if (Math.abs(yaw - rotYaw) >= rotSpeed) {
            if (Math.abs(yaw - rotYaw) <= 180.0F) {
                rotYaw += yaw > rotYaw ? rotSpeed : -rotSpeed;
            } else {
                rotYaw += yaw > rotYaw ? -rotSpeed : rotSpeed;
            }
        }
        this.setXRot(rotPitch);
        this.setYRot(rotYaw);
        this.setYHeadRot(rotYaw);
        this.targetPrevPosX = target.getX();
        this.targetPrevPosY = target.getY();
        this.targetPrevPosZ = target.getZ();
    }

    /** Upstream {@code checkPitch}: the target is within the vehicle's and the weapon's pitch limits. */
    private boolean checkPitch(LivingEntity entity, AircraftEntity ac, Vec3 pos) {
        double d0 = entity.getX() - pos.x;
        double d1 = entity.getY() - pos.y;
        double d2 = entity.getZ() - pos.z;
        double d3 = Math.sqrt(d0 * d0 + d2 * d2);
        float pitch = (float) -(Math.atan2(d1, d3) * 180.0 / Math.PI);
        AircraftInfo ai = ac.getAcInfo();
        if (ai == null) {
            return true;
        }
        if (ac instanceof VehicleEntity && ac.isPilot(this)
                && Math.abs(ai.minRotationPitch) + Math.abs(ai.maxRotationPitch) > 0.0F) {
            if (pitch < ai.minRotationPitch || pitch > ai.maxRotationPitch) {
                return false;
            }
        }
        if (!(ac.getCurrentWeapon(this).getCurrentWeapon() instanceof EntitySeekerWeapon)) {
            AircraftInfo.Weapon wi = ai.getWeaponById(ac.getCurrentWeaponID(this));
            if (wi != null && Math.abs(wi.minPitch) + Math.abs(wi.maxPitch) > 0.0F) {
                return pitch >= wi.minPitch && pitch <= wi.maxPitch;
            }
        }
        return true;
    }

    /** Upstream {@code getGunnerWeaponPos}: where the seat's current weapon is in the world. */
    public Vec3 getGunnerWeaponPos(AircraftEntity ac, WeaponSet ws) {
        SeatInfo seatInfo = ac.getSeatInfo(ac.getSeatIdByEntity(this));
        Vec3 position = ws.getCurrentWeapon().position;
        return (seatInfo == null || !seatInfo.rotSeat) && !(ac instanceof VehicleEntity)
                ? ac.getTransformedPosition(position)
                : ac.calcOnTurretPos(position).add(ac.getX(), ac.getY(), ac.getZ());
    }

    /** Upstream {@code isInAttackable}: the target is within the weapon's yaw range. */
    private boolean isInAttackable(LivingEntity entity, AircraftEntity ac, Vec3 pos) {
        if (ac instanceof VehicleEntity || ac.getCurrentWeapon(this).getCurrentWeapon() instanceof EntitySeekerWeapon) {
            return true;
        }
        AircraftInfo.Weapon wi = ac.getAcInfo().getWeaponById(ac.getCurrentWeaponID(this));
        if (wi == null) {
            return false;
        }
        float yaw = -ac.getYaw() + (wi.maxYaw + wi.minYaw) / 2.0F - wi.defaultYaw;
        Vec3 v1 = new Vec3(0.0, 0.0, 1.0).yRot(yaw * (float) Math.PI / 180.0F);
        Vec3 v2 = new Vec3(entity.getX() - pos.x, 0.0, entity.getZ() - pos.z).normalize();
        double deg = Math.toDegrees(Math.acos(v1.dot(v2)));
        return deg < Math.abs(wi.maxYaw - wi.minYaw) / 2.0F;
    }

    /** The vehicle the gunner sits in, directly or through one of its seats. */
    public @Nullable AircraftEntity getAc() {
        Entity vehicle = this.getVehicle();
        if (vehicle instanceof AircraftEntity ac) {
            return ac;
        }
        return vehicle instanceof SeatEntity seat ? seat.getParent() : null;
    }

    @Override
    protected void addAdditionalSaveData(ValueOutput output) {
        super.addAdditionalSaveData(output);
        output.putBoolean("Creative", this.isCreative);
        output.putString("OwnerUUID", this.ownerUUID);
        output.putString("TeamName", this.getTeamName());
        output.putInt("TargetType", this.targetType);
    }

    @Override
    protected void readAdditionalSaveData(ValueInput input) {
        super.readAdditionalSaveData(input);
        this.isCreative = input.getBooleanOr("Creative", false);
        this.ownerUUID = input.getStringOr("OwnerUUID", "");
        this.setTeamName(input.getStringOr("TeamName", ""));
        this.targetType = input.getIntOr("TargetType", VS_MONSTER);
    }
}
