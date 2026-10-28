package io.github.mcheliceport.entity.aircraft;

import io.github.mcheliceport.net.ModNetwork;
import io.github.mcheliceport.content.aircraft.AircraftInfo;
import io.github.mcheliceport.content.weapon.WeaponInfo;
import io.github.mcheliceport.effect.McheliEffects;
import io.github.mcheliceport.effect.ParticleParam;
import io.github.mcheliceport.entity.weapon.TvMissileEntity;
import io.github.mcheliceport.net.NotifyAmmoPayload;
import io.github.mcheliceport.net.SyncReloadPayload;
import io.github.mcheliceport.net.WeaponSyncPayload;
import io.github.mcheliceport.util.McheliMath;
import io.github.mcheliceport.weapon.WeaponBase;
import io.github.mcheliceport.weapon.WeaponCreator;
import io.github.mcheliceport.weapon.WeaponParam;
import io.github.mcheliceport.weapon.WeaponSet;
import io.github.mcheliceport.weapon.WeaponWorld;
import io.github.mcheliceport.weapon.types.DummyWeapon;
import java.util.Arrays;
import java.util.Random;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

/**
 * A vehicle's weapons: one {@link WeaponSet} per weapon set of its definition, the weapon each
 * seat has selected, the "weapon fired this tick" bits other clients animate from, turret aim,
 * weapon bays, ammunition supply and the TV missile being steered. Ported from the weapon parts
 * of upstream {@code MCH_EntityAircraft}.
 *
 * <p>Upstream's quaternion aim ({@code ExperimentalQuaternionRotation}) is off by default and not
 * ported; the shot direction is upstream's default Euler sum. Tank and ground vehicle detached
 * aim comes with those vehicles in M6. Gunner mobs (M6) are not seat users yet, so only players
 * select and fire weapons.
 */
public final class AircraftWeapons {

    /** Upstream {@code getMaxHitStatus}: ticks the hit marker shows. */
    public static final int MAX_HIT_STATUS = 15;

    private final AircraftEntity ac;
    private final WeaponSet dummyWeapon;
    private final Random rand = new Random();
    private WeaponSet[] weapons;
    private int[] currentWeaponID = new int[0];
    /** Per weapon of all sets in order, whether it fired this tick (synced as one int). */
    public int useWeaponStat;
    public int hitStatus;
    public int recoilCount;
    public float recoilValue;
    public float recoilYaw;
    public WeaponBay[] weaponBays = new WeaponBay[0];
    private int supplyAmmoWait;
    private boolean beforeSupplyAmmo;
    private @Nullable TvMissileEntity tvMissile;
    private boolean packetWeaponUserAimActive;
    private @Nullable Entity packetWeaponUserAimUser;
    private float packetWeaponUserYaw;
    private float packetWeaponUserPitch;
    private int lastCalcLandInDistanceCount = -1;
    /** Upstream {@code impactPos}/{@code prevImpactPos}: where the current weapon lands, this and the last update. */
    private @Nullable Vec3 impactPos;
    private @Nullable Vec3 prevImpactPos;
    private double lastLandInDistance = -1.0;
    private int lastCalcPredictedImpactPointCount = -1;
    private @Nullable Vec3 lastPredictedImpactPoint;

    AircraftWeapons(AircraftEntity ac) {
        this.ac = ac;
        this.dummyWeapon = new WeaponSet(new DummyWeapon(ac.level(), Vec3.ZERO, 0.0F, 0.0F, "", null));
        this.weapons = new WeaponSet[] {this.dummyWeapon};
    }

    // ---- creation ----

    /** Upstream {@code createWeapon(seatNum)}: weapon sets for the definition, no seat selecting any. */
    void create(AircraftInfo info, int seatNum) {
        this.currentWeaponID = new int[Math.max(seatNum, 0)];
        Arrays.fill(this.currentWeaponID, -1);
        this.weaponBays = new WeaponBay[info.partWeaponBay.size()];
        for (int i = 0; i < this.weaponBays.length; i++) {
            this.weaponBays[i] = new WeaponBay();
        }
        if (info.weaponSetList.isEmpty() || seatNum <= 0) {
            this.weapons = new WeaponSet[] {this.dummyWeapon};
            return;
        }
        WeaponSet[] sets = new WeaponSet[info.weaponSetList.size()];
        for (int i = 0; i < sets.length; i++) {
            AircraftInfo.WeaponSet ws = info.weaponSetList.get(i);
            WeaponBase[] wb = new WeaponBase[ws.weapons.size()];
            boolean complete = true;
            for (int j = 0; j < wb.length; j++) {
                AircraftInfo.Weapon w = ws.weapons.get(j);
                wb[j] = WeaponCreator.createWeapon(this.ac.level(), ws.type, w.pos, w.yaw, w.pitch, this.ac::canLockEntity,
                        w.turret);
                if (wb[j] == null) {
                    complete = false;
                    break;
                }
                wb[j].aircraft = this.ac;
            }
            if (complete && wb.length > 0) {
                float defYaw = ws.weapons.getFirst().defaultYaw;
                sets[i] = new WeaponSet(wb);
                sets[i].setPrevYaw(defYaw);
                sets[i].setYaw(defYaw);
                sets[i].setDefYaw(defYaw);
            } else {
                // Upstream asserted; a set whose weapon is missing (content validation already
                // warned) keeps its index with a dummy so the other sets' ids stay right.
                sets[i] = new WeaponSet(new DummyWeapon(this.ac.level(), Vec3.ZERO, 0.0F, 0.0F, "", null));
            }
        }
        this.weapons = sets;
    }

    // ---- lookup ----

    public int getWeaponNum() {
        return this.weapons.length;
    }

    public WeaponSet getWeapon(int id) {
        return id >= 0 && id < this.weapons.length ? this.weapons[id] : this.dummyWeapon;
    }

    public WeaponSet[] all() {
        return this.weapons;
    }

    public @Nullable WeaponInfo getWeaponInfoById(int id) {
        return id >= 0 ? this.getWeapon(id).getInfo() : null;
    }

    public @Nullable WeaponSet getWeaponByName(String name) {
        for (WeaponSet ws : this.weapons) {
            if (ws.isName(name)) {
                return ws;
            }
        }
        return null;
    }

    /** Upstream {@code getWeaponUserByWeaponName}: who sits in the seat that fires a weapon, or null. */
    public @Nullable Entity getWeaponUserByWeaponName(String name) {
        AircraftInfo info = this.ac.getAcInfo();
        AircraftInfo.Weapon weapon = info != null ? info.getWeaponByName(name) : null;
        if (weapon == null) {
            return null;
        }
        Entity entity = this.ac.getEntityBySeatId(this.getWeaponSeatID(null, weapon));
        return entity == null && weapon.canUsePilot ? this.ac.getPilot() : entity;
    }

    /** Upstream {@code getCurrentWeaponID}: the weapon a player's or gunner's seat has selected, or -1. */
    public int getCurrentWeaponID(@Nullable Entity entity) {
        if (!AircraftEntity.isWeaponUser(entity)) {
            return -1;
        }
        int id = this.ac.getSeatIdByEntity(entity);
        return id >= 0 && id < this.currentWeaponID.length ? this.currentWeaponID[id] : -1;
    }

    public int getWeaponIDBySeatID(int sid) {
        return sid >= 0 && sid < this.currentWeaponID.length ? this.currentWeaponID[sid] : -1;
    }

    public WeaponSet getCurrentWeapon(@Nullable Entity entity) {
        return this.getWeapon(this.getCurrentWeaponID(entity));
    }

    public WeaponSet getFirstSeatWeapon() {
        return this.currentWeaponID.length > 0 && this.currentWeaponID[0] >= 0
                ? this.getWeapon(this.currentWeaponID[0]) : this.getWeapon(0);
    }

    /**
     * Upstream {@code getWeaponSeatID}: the seat a weapon belongs to. Upstream gave weapons with an
     * empty type to seat 1000 in single player; every loaded weapon has a type, so it is always
     * the definition's seat.
     */
    public int getWeaponSeatID(@Nullable WeaponInfo wi, AircraftInfo.Weapon w) {
        return w.seatID;
    }

    /** Upstream {@code getNextWeaponID}: the next weapon (by {@code step}'s sign) this rider may select. */
    public int getNextWeaponID(@Nullable Entity entity, int step) {
        AircraftInfo info = this.ac.getAcInfo();
        if (info == null) {
            return -1;
        }
        int sid = this.ac.getSeatIdByEntity(entity);
        if (sid < 0) {
            return -1;
        }
        int id = this.getCurrentWeaponID(entity);
        int num = this.getWeaponNum();
        int i;
        for (i = 0; i < num; i++) {
            if (step >= 0) {
                id = (id + 1) % num;
            } else {
                id = id > 0 ? id - 1 : num - 1;
            }
            AircraftInfo.Weapon w = info.getWeaponById(id);
            if (w != null) {
                int wpsid = this.getWeaponSeatID(this.getWeaponInfoById(id), w);
                if (wpsid < this.ac.getSeatNum() + 1 + 1 && (wpsid == sid
                        || sid == 0 && w.canUsePilot && !AircraftEntity.isWeaponUser(this.ac.getEntityBySeatId(wpsid)))) {
                    break;
                }
            }
        }
        return i >= num ? -1 : id;
    }

    // ---- selection ----

    /** Upstream {@code switchWeapon}: selects a weapon for the rider's seat and tells clients nearby. */
    public void switchWeapon(Entity entity, int id) {
        int sid = this.ac.getSeatIdByEntity(entity);
        if (sid < 0 || sid >= this.currentWeaponID.length || this.getWeaponNum() <= 0) {
            return;
        }
        if (id < 0) {
            this.currentWeaponID[sid] = -1;
        }
        if (id >= this.getWeaponNum()) {
            id = this.getWeaponNum() - 1;
        }
        this.getCurrentWeapon(entity).reload();
        this.currentWeaponID[sid] = id;
        WeaponSet ws = this.getCurrentWeapon(entity);
        ws.onSwitchWeapon(this.ac.level().isClientSide(), this.ac.isInfinityAmmo(entity));
        if (this.ac.level() instanceof ServerLevel server) {
            ModNetwork.sendToPlayersNear(server, null, this.ac.getX(), this.ac.getY(), this.ac.getZ(), 150.0,
                    WeaponSyncPayload.of(this.ac, sid, id, ws));
        }
    }

    /** Upstream {@code updateWeaponID}: another rider's selection, from the server. */
    public void updateWeaponID(int sid, int id) {
        if (sid < 0 || sid >= this.currentWeaponID.length || this.getWeaponNum() <= 0) {
            return;
        }
        if (id < 0) {
            this.currentWeaponID[sid] = -1;
        }
        if (id >= this.getWeaponNum()) {
            id = this.getWeaponNum() - 1;
        }
        this.currentWeaponID[sid] = id;
    }

    /** Upstream {@code initCurrentWeapon}: a player or gunner who just took a seat gets its first weapon. */
    public void initCurrentWeapon(Entity entity) {
        int sid = this.ac.getSeatIdByEntity(entity);
        if (sid < 0 || sid >= this.currentWeaponID.length) {
            return;
        }
        this.currentWeaponID[sid] = -1;
        if (AircraftEntity.isWeaponUser(entity)) {
            this.currentWeaponID[sid] = this.getNextWeaponID(entity, 1);
            this.switchWeapon(entity, this.getCurrentWeaponID(entity));
            if (this.ac.level().isClientSide()) {
                AircraftEntity.ClientHooks hooks = AircraftEntity.clientHooks();
                if (hooks != null) {
                    hooks.sendToServer(new io.github.mcheliceport.net.AmmoRequestPayload(this.ac.getId(), -1));
                }
            }
        }
    }

    /**
     * Upstream {@code onMountPlayerSeat}'s weapon part: the new rider's first weapon, and the pilot
     * moves off a weapon that now belongs to the new rider's seat.
     */
    void onMountPlayerSeat(Entity entity) {
        this.initCurrentWeapon(entity);
        Entity pilot = this.ac.getPilot();
        int sid = this.ac.getSeatIdByEntity(entity);
        AircraftInfo info = this.ac.getAcInfo();
        if (pilot != null && info != null) {
            int cwid = this.getCurrentWeaponID(pilot);
            AircraftInfo.Weapon w = info.getWeaponById(cwid);
            if (w != null && this.getWeaponSeatID(this.getWeaponInfoById(cwid), w) == sid) {
                int next = this.getNextWeaponID(pilot, 1);
                if (next >= 0) {
                    this.switchWeapon(pilot, next);
                }
            }
        }
    }

    public void reloadAllWeapon() {
        for (int i = 0; i < this.getWeaponNum(); i++) {
            this.getWeapon(i).reloadMag();
        }
    }

    public void switchCurrentWeaponMode(Entity entity) {
        this.getCurrentWeapon(entity).switchMode();
    }

    /**
     * Upstream {@code manualReloadForPlayer}: the R key; the server confirms it to the client. A
     * loader's key (M10b) reloads the vehicle's emptiest gun instead of a selected weapon.
     */
    public void manualReloadForPlayer(Player player) {
        WeaponSet target = this.getCurrentWeapon(player);
        if (CrewRoles.roleOf(this.ac, player) == io.github.mcheliceport.content.aircraft.CrewRole.LOADER) {
            int id = CrewRoles.loaderTarget(this);
            if (id < 0) {
                return;
            }
            target = this.getWeapon(id);
        }
        boolean didReload = target.manualReload();
        if (didReload && player instanceof ServerPlayer serverPlayer) {
            ModNetwork.sendToPlayer(serverPlayer, new SyncReloadPayload(this.ac.getId()));
        }
    }

    /** Upstream {@code resetAirburstDistance}: the distance to the block the player looks at, or 0 to clear it. */
    public int resetAirburstDistance(Player player, @Nullable WeaponBase wb) {
        if (wb == null) {
            return -1;
        }
        int dist = 0;
        if (wb.airburstDist <= 0) {
            Vec3 start = player.getEyePosition(1.0F);
            Vec3 end = start.add(player.getViewVector(1.0F).scale(3000.0));
            BlockHitResult hit = WeaponWorld.rayTrace(this.ac.level(), start, end, false, true);
            if (hit != null) {
                dist = (int) Math.sqrt(player.distanceToSqr(hit.getLocation()));
            }
        }
        wb.airburstDist = dist;
        return dist;
    }

    // ---- firing ----

    private WeaponParam newParam(@Nullable Entity user) {
        WeaponParam prm = new WeaponParam();
        prm.setPosition(this.ac.getX(), this.ac.getY(), this.ac.getZ());
        prm.entity = this.ac;
        prm.user = user;
        prm.isInfinity = this.ac.isInfinityAmmo(user);
        return prm;
    }

    private void setTurretFlag(WeaponParam prm) {
        AircraftInfo info = this.ac.getAcInfo();
        if (info != null) {
            AircraftInfo.WeaponSet seatSet = info.getWeaponSetById(this.ac.getSeatIdByEntity(prm.user));
            if (seatSet != null && !seatSet.weapons.isEmpty()) {
                prm.isTurret = seatSet.weapons.getFirst().turret;
            }
        }
    }

    /**
     * Upstream {@code prepareCurrentWeapon}: the firing client's prediction (cooldown, ammo, sound,
     * recoil); true when the shot should go to the server.
     */
    public boolean prepareCurrentWeapon(@Nullable Entity user) {
        if (user == null) {
            return false;
        }
        WeaponParam prm = this.newParam(user);
        WeaponSet currentWs = this.getCurrentWeapon(user);
        this.setTurretFlag(prm);
        if (!currentWs.prepareUse(prm)) {
            return false;
        }
        // Upstream predicts the group-linked weapons' shared cooldown on the client too.
        String group = currentWs.getInfo().group;
        if (group != null && !group.isEmpty()) {
            for (WeaponSet ws : this.weapons) {
                if (ws != currentWs && group.equals(ws.getInfo().group)) {
                    ws.waitAndReloadByOther(prm.reload);
                }
            }
        }
        return true;
    }

    public boolean useCurrentWeapon(@Nullable Entity user) {
        WeaponParam prm = new WeaponParam();
        prm.setPosition(this.ac.getX(), this.ac.getY(), this.ac.getZ());
        prm.entity = this.ac;
        prm.user = user;
        return this.useCurrentWeapon(prm);
    }

    /** Upstream {@code useCurrentWeapon(prm)}: fires on the server and shares the group's cooldown. */
    public boolean useCurrentWeapon(WeaponParam prm) {
        prm.isInfinity = this.ac.isInfinityAmmo(prm.user);
        if (prm.user == null) {
            return false;
        }
        WeaponSet currentWs = this.getCurrentWeapon(prm.user);
        if (!currentWs.canFire()) {
            return false;
        }
        this.setTurretFlag(prm);
        int lastUsedIndex = currentWs.getCurrentWeaponIndex();
        if (!currentWs.use(prm)) {
            return false;
        }
        String group = currentWs.getInfo().group;
        boolean hasGroup = group != null && !group.isEmpty();
        for (WeaponSet ws : this.weapons) {
            if (ws != currentWs && hasGroup && group.equals(ws.getInfo().group)) {
                ws.waitAndReloadByOther(prm.reload);
            }
        }
        if (!this.ac.level().isClientSide()) {
            int shift = 0;
            for (WeaponSet ws : this.weapons) {
                if (ws == currentWs) {
                    break;
                }
                shift += ws.getWeaponsCount();
            }
            shift += lastUsedIndex;
            if (shift < 32) {
                this.useWeaponStat |= 1 << shift;
            }
            if (prm.reload && prm.user instanceof ServerPlayer mp) {
                int sid = this.ac.getSeatIdByEntity(mp);
                if (sid >= 0) {
                    for (int i = 0; i < this.weapons.length; i++) {
                        WeaponSet ws = this.weapons[i];
                        if (ws == currentWs || hasGroup && group.equals(ws.getInfo().group)) {
                            ModNetwork.sendToPlayer(mp, WeaponSyncPayload.of(this.ac, sid, i, ws));
                        }
                    }
                }
            }
        }
        return true;
    }

    /** After a refused shot the server tells the client the weapon's real state (upstream handleUseWeapon). */
    public void syncCurrentWeapon(ServerPlayer player) {
        int sid = this.ac.getSeatIdByEntity(player);
        int wid = this.getCurrentWeaponID(player);
        if (sid >= 0 && wid >= 0) {
            ModNetwork.sendToPlayer(player, WeaponSyncPayload.of(this.ac, sid, wid, this.getWeapon(wid)));
        }
    }

    // ---- aim ----

    public void setPacketWeaponUserAim(Entity user, float yaw, float pitch) {
        this.packetWeaponUserAimActive = true;
        this.packetWeaponUserAimUser = user;
        this.packetWeaponUserYaw = yaw;
        this.packetWeaponUserPitch = pitch;
    }

    /**
     * A port addition: fires {@code ws} for an AI pilot along {@code yaw}/{@code pitch} (the shot
     * direction of {@code prm.user}, who rides no seat), and puts back any rider's aim after.
     */
    public boolean useAimed(WeaponSet ws, WeaponParam prm, float yaw, float pitch) {
        boolean active = this.packetWeaponUserAimActive;
        Entity user = this.packetWeaponUserAimUser;
        float userYaw = this.packetWeaponUserYaw;
        float userPitch = this.packetWeaponUserPitch;
        // A shot of exactly 0/0 would take the weapon's fixed direction instead.
        this.setPacketWeaponUserAim(prm.user, yaw, yaw == 0.0F && pitch == 0.0F ? 1.0E-3F : pitch);
        try {
            return ws.use(prm);
        } finally {
            this.packetWeaponUserAimActive = active;
            this.packetWeaponUserAimUser = user;
            this.packetWeaponUserYaw = userYaw;
            this.packetWeaponUserPitch = userPitch;
        }
    }

    public void clearPacketWeaponUserAim() {
        this.packetWeaponUserAimActive = false;
        this.packetWeaponUserAimUser = null;
    }

    private boolean usesPacketWeaponUserAim(@Nullable Entity entity) {
        return this.packetWeaponUserAimActive && WeaponWorld.isEqual(entity, this.packetWeaponUserAimUser);
    }

    public float getWeaponUserYaw(@Nullable Entity entity) {
        if (this.usesPacketWeaponUserAim(entity)) {
            return this.packetWeaponUserYaw;
        }
        return entity != null ? entity.getYRot() : this.ac.lastRiderYaw;
    }

    public float getWeaponUserPitch(@Nullable Entity entity) {
        if (this.usesPacketWeaponUserAim(entity)) {
            return this.packetWeaponUserPitch;
        }
        return entity != null ? entity.getXRot() : this.ac.lastRiderPitch;
    }

    public float getWeaponUserYaw(@Nullable Entity entity, float partialTicks) {
        if (this.usesPacketWeaponUserAim(entity)) {
            return this.packetWeaponUserYaw;
        }
        float prev = entity != null ? entity.yRotO : this.ac.prevLastRiderYaw;
        float curr = entity != null ? entity.getYRot() : this.ac.lastRiderYaw;
        return lerpYaw(prev, curr, partialTicks);
    }

    public float getWeaponUserPitch(@Nullable Entity entity, float partialTicks) {
        if (this.usesPacketWeaponUserAim(entity)) {
            return this.packetWeaponUserPitch;
        }
        float prev = entity != null ? entity.xRotO : this.ac.prevLastRiderPitch;
        float curr = entity != null ? entity.getXRot() : this.ac.lastRiderPitch;
        return prev + (curr - prev) * partialTicks;
    }

    private static float lerpYaw(float prev, float curr, float partialTicks) {
        if (curr - prev > 180.0F) {
            prev += 360.0F;
        } else if (curr - prev < -180.0F) {
            prev -= 360.0F;
        }
        return Mth.wrapDegrees(prev + (curr - prev) * partialTicks);
    }

    public float getCurrentWeaponShotYaw(@Nullable Entity entity) {
        WeaponSet ws = entity != null ? this.getCurrentWeapon(entity) : null;
        if (ws == null || ws.getCurrentWeapon() == null) {
            return this.getWeaponUserYaw(entity);
        }
        if (this.ac instanceof io.github.mcheliceport.entity.vehicle.VehicleEntity) {
            return Mth.wrapDegrees(this.getWeaponUserYaw(entity) + ws.getCurrentWeapon().fixRotationYaw);
        }
        return Mth.wrapDegrees(this.ac.getYaw() + ws.getYaw() + ws.getCurrentWeapon().fixRotationYaw);
    }

    public float getCurrentWeaponShotPitch(@Nullable Entity entity) {
        WeaponSet ws = entity != null ? this.getCurrentWeapon(entity) : null;
        if (ws == null || ws.getCurrentWeapon() == null) {
            return this.getWeaponUserPitch(entity);
        }
        if (this.ac instanceof io.github.mcheliceport.entity.vehicle.VehicleEntity && this.ac.getAcInfo() != null) {
            return Mth.wrapDegrees(Mth.clamp(this.getWeaponUserPitch(entity), this.ac.getAcInfo().minRotationPitch,
                    this.ac.getAcInfo().maxRotationPitch) + ws.getCurrentWeapon().fixRotationPitch);
        }
        return Mth.wrapDegrees(this.ac.getPitch() + ws.getPitch() + ws.getCurrentWeapon().fixRotationPitch);
    }

    public float getCurrentWeaponShotYaw(@Nullable Entity entity, float partialTicks) {
        WeaponSet ws = entity != null ? this.getCurrentWeapon(entity) : null;
        if (ws == null || ws.getCurrentWeapon() == null) {
            return this.getWeaponUserYaw(entity, partialTicks);
        }
        if (this.ac instanceof io.github.mcheliceport.entity.vehicle.VehicleEntity) {
            return Mth.wrapDegrees(this.getWeaponUserYaw(entity, partialTicks) + ws.getCurrentWeapon().fixRotationYaw);
        }
        float yaw = this.ac.yRotO + (this.ac.getYRot() - this.ac.yRotO) * partialTicks;
        float wsYaw = ws.getPrevYaw() + (ws.getYaw() - ws.getPrevYaw()) * partialTicks;
        return Mth.wrapDegrees(yaw + wsYaw + ws.getCurrentWeapon().fixRotationYaw);
    }

    public float getCurrentWeaponShotPitch(@Nullable Entity entity, float partialTicks) {
        WeaponSet ws = entity != null ? this.getCurrentWeapon(entity) : null;
        if (ws == null || ws.getCurrentWeapon() == null) {
            return this.getWeaponUserPitch(entity, partialTicks);
        }
        if (this.ac instanceof io.github.mcheliceport.entity.vehicle.VehicleEntity && this.ac.getAcInfo() != null) {
            return Mth.wrapDegrees(Mth.clamp(this.getWeaponUserPitch(entity, partialTicks), this.ac.getAcInfo().minRotationPitch,
                    this.ac.getAcInfo().maxRotationPitch) + ws.getCurrentWeapon().fixRotationPitch);
        }
        float pitch = this.ac.xRotO + (this.ac.getXRot() - this.ac.xRotO) * partialTicks;
        float wsPitch = ws.getPrevPitch() + (ws.getPitch() - ws.getPrevPitch()) * partialTicks;
        return Mth.wrapDegrees(pitch + wsPitch + ws.getCurrentWeapon().fixRotationPitch);
    }

    /** Upstream {@code getCurrentWeaponShotDir} with the quaternion option off: {yaw, pitch}. */
    public float[] getCurrentWeaponShotDir(@Nullable Entity entity) {
        return new float[] {this.getCurrentWeaponShotYaw(entity), this.getCurrentWeaponShotPitch(entity)};
    }

    public float[] getCurrentWeaponShotDir(@Nullable Entity entity, float partialTicks) {
        return new float[] {this.getCurrentWeaponShotYaw(entity, partialTicks),
                this.getCurrentWeaponShotPitch(entity, partialTicks)};
    }

    private static float stepWrappedAngle(float current, float target, float maxStep) {
        return Mth.wrapDegrees(current + Mth.clamp(Mth.wrapDegrees(target - current), -maxStep, maxStep));
    }

    private static float stepLinearAngle(float current, float target, float maxStep) {
        return current + Mth.clamp(target - current, -maxStep, maxStep);
    }

    private static boolean hasWeaponYawControl(AircraftInfo.Weapon weapon) {
        return weapon.turret || Math.abs(weapon.minYaw) > 0.001F || Math.abs(weapon.maxYaw) > 0.001F;
    }

    private static boolean hasExplicitWeaponPitchLimit(AircraftInfo.Weapon weapon) {
        return Math.abs(weapon.minPitch) > 0.001F || Math.abs(weapon.maxPitch) > 0.001F;
    }

    private float getWeaponMinPitch(AircraftInfo info, AircraftInfo.Weapon weapon) {
        return weapon.turret && !hasExplicitWeaponPitchLimit(weapon) ? info.minRotationPitch : weapon.minPitch;
    }

    private float getWeaponMaxPitch(AircraftInfo info, AircraftInfo.Weapon weapon) {
        return weapon.turret && !hasExplicitWeaponPitchLimit(weapon) ? info.maxRotationPitch : weapon.maxPitch;
    }

    /** Upstream {@code getWeaponRotationSpeed}: degrees per second a weapon turns towards its aim. */
    private float getWeaponRotationStep(WeaponSet ws) {
        AircraftInfo info = this.ac.getAcInfo();
        if (info == null) {
            return 0.0F;
        }
        float speed = Math.max(0.0F, info.cameraRotationSpeed);
        if (ws.getInfo() != null) {
            speed *= Math.max(0.0F, ws.getInfo().cameraRotationSpeedPitch);
        }
        return speed / 20.0F;
    }

    /** The rider aiming weapon {@code wid}: its seat's player, or the pilot for a pilot-usable weapon. */
    private @Nullable Entity weaponUser(int wid, AircraftInfo.Weapon wi) {
        Entity entity = this.ac.getEntityBySeatId(this.getWeaponSeatID(this.getWeaponInfoById(wid), wi));
        if (wi.canUsePilot && !AircraftEntity.isWeaponUser(entity)) {
            entity = this.ac.getEntityBySeatId(0);
        }
        return entity;
    }

    /** Turns one weapon set towards its user's aim (shared by the tick and the shot's re-aim). */
    private void aimWeapon(AircraftInfo info, WeaponSet w, AircraftInfo.Weapon wi, @Nullable Entity entity, float yaw,
            float pitch) {
        if (!AircraftEntity.isWeaponUser(entity)) {
            w.setTurretYaw(this.ac.lastRiderYaw - this.ac.getYaw());
            if (this.ac.isTowed() || this.ac.isPassenger()) { w.setYaw(0); }
            return;
        }
        float step = this.getWeaponRotationStep(w);
        if (hasWeaponYawControl(wi)) {
            float entityYaw = this.getWeaponUserYaw(entity);
            float ty = wi.turret ? Mth.wrapDegrees(this.ac.lastRiderYaw) - yaw : 0.0F;
            float ey = Mth.wrapDegrees(entityYaw - yaw - wi.defaultYaw - ty);
            if (Math.abs((int) wi.minYaw) < 360 && Math.abs((int) wi.maxYaw) < 360) {
                float targetYaw = Mth.clamp(ey, wi.minYaw, wi.maxYaw);
                float wy = w.getYaw() - wi.defaultYaw - ty;
                wy = stepWrappedAngle(wy, targetYaw, step);
                w.setYaw(wy + wi.defaultYaw + ty);
            } else {
                w.setYaw(stepWrappedAngle(w.getYaw(), ey + ty, step));
            }
        }
        float ep = Mth.wrapDegrees(this.getWeaponUserPitch(entity) - pitch);
        float targetPitch = Mth.clamp(ep, this.getWeaponMinPitch(info, wi), this.getWeaponMaxPitch(info, wi));
        w.setPitch(stepLinearAngle(w.getPitch(), targetPitch, step));
        w.setTurretYaw(0.0F);
    }

    /** Upstream {@code updateWeaponsRotation}: re-aims the weapons with the shooter's packet aim before a shot. */
    public void updateWeaponsRotation() {
        AircraftInfo info = this.ac.getAcInfo();
        if (info == null || info.getWeaponCount() <= 0 || this.ac.isDestroyed()) {
            return;
        }
        float yaw = Mth.wrapDegrees(this.ac.getYaw());
        float pitch = Mth.wrapDegrees(this.ac.getPitch());
        for (int wid = 0; wid < this.weapons.length; wid++) {
            WeaponSet w = this.weapons[wid];
            AircraftInfo.Weapon wi = info.getWeaponById(wid);
            if (wi != null) {
                this.aimWeapon(info, w, wi, this.weaponUser(wid, wi), yaw, pitch);
            }
            w.setPrevYaw(w.getYaw());
        }
    }

    // ---- tick ----

    /**
     * Upstream {@code updateWeapons}: the fired bits go to clients, which flash the muzzles and
     * kick the vehicle; every weapon set ticks its cooldown, reload and recoil, and turns towards
     * its user's aim.
     */
    public void tick(net.minecraft.network.syncher.SynchedEntityData data,
            net.minecraft.network.syncher.EntityDataAccessor<Integer> useWeaponKey) {
        AircraftInfo info = this.ac.getAcInfo();
        if (info == null || info.getWeaponCount() <= 0) {
            return;
        }
        Level level = this.ac.level();
        int prevUseWeaponStat = this.useWeaponStat;
        if (!level.isClientSide()) {
            this.useWeaponStat |= this.getUsedWeaponStat();
            data.set(useWeaponKey, this.useWeaponStat);
            this.useWeaponStat = 0;
        } else {
            this.useWeaponStat = data.get(useWeaponKey);
        }
        float yaw = Mth.wrapDegrees(this.ac.getYaw());
        float pitch = Mth.wrapDegrees(this.ac.getPitch());
        int id = 0;
        for (int wid = 0; wid < this.weapons.length; wid++) {
            WeaponSet w = this.weapons[wid];
            boolean isLongDelay = w.getFirstWeapon() != null && w.hasLongDelay();
            boolean isSelected = false;
            for (int swid : this.currentWeaponID) {
                if (swid == wid) {
                    isSelected = true;
                    break;
                }
            }
            boolean isWpnUsed = false;
            for (int index = 0; index < w.getWeaponsCount(); index++) {
                boolean isPrevUsed = id < 32 && (prevUseWeaponStat & 1 << id) != 0;
                boolean isUsed = id < 32 && (this.useWeaponStat & 1 << id) != 0;
                if (isLongDelay && isPrevUsed && isUsed) {
                    isUsed = false;
                }
                isWpnUsed |= isUsed;
                if (!isPrevUsed && isUsed && w.getInfo() != null && w.getInfo().recoil > 0.0F) {
                    this.recoilCount = 30;
                    this.recoilValue = w.getInfo().recoil;
                    this.recoilYaw = w.getYaw();
                }
                if (level.isClientSide() && isUsed) {
                    Vec3 wrv = McheliMath.rotVec3(0.0, 0.0, -1.0, -w.getYaw() - yaw, -w.getPitch(), 0.0F);
                    Vec3 spv = w.getCurrentWeapon().getShotPos(this.ac);
                    this.spawnParticleMuzzleFlash(level, w.getInfo(), this.ac.getX() + spv.x, this.ac.getY() + spv.y,
                            this.ac.getZ() + spv.z, wrv);
                }
                w.updateWeapon(this.ac, isUsed, index);
                id++;
            }
            w.update(this.ac, isSelected, isWpnUsed);
            AircraftInfo.Weapon wi = info.getWeaponById(wid);
            if (wi != null && !this.ac.isDestroyed()) {
                this.aimWeapon(info, w, wi, this.weaponUser(wid, wi), yaw, pitch);
            }
        }
        this.updateWeaponBay(info);
        if (this.hitStatus > 0) {
            this.hitStatus--;
        }
    }

    /** Upstream {@code getUsedWeaponStat}: the weapons whose recoil buffer is still moving. */
    private int getUsedWeaponStat() {
        int stat = 0;
        int i = 0;
        for (WeaponSet w : this.weapons) {
            if (i >= 32) {
                break;
            }
            for (int wi = 0; wi < w.getWeaponsCount() && i < 32; wi++) {
                stat |= w.isUsed(wi) ? 1 << i : 0;
                i++;
            }
        }
        return stat;
    }

    /** Upstream {@code isWeaponOnCooldown}: true when weapon {@code index} of the set did not just fire. */
    public boolean isWeaponOnCooldown(WeaponSet checkWs, int index) {
        AircraftInfo info = this.ac.getAcInfo();
        if (info == null || info.getWeaponCount() <= 0) {
            return true;
        }
        int shift = 0;
        for (WeaponSet ws : this.weapons) {
            if (ws == checkWs) {
                break;
            }
            shift += ws.getWeaponsCount();
        }
        shift += index;
        return shift >= 32 || (this.useWeaponStat & 1 << shift) == 0;
    }

    private void spawnParticleMuzzleFlash(Level w, @Nullable WeaponInfo wi, double px, double py, double pz,
            Vec3 wrv) {
        if (wi == null) {
            return;
        }
        if (wi.listMuzzleFlashSmoke != null) {
            for (WeaponInfo.MuzzleFlash mf : wi.listMuzzleFlashSmoke) {
                double x = px - wrv.x * mf.dist;
                double y = py - wrv.y * mf.dist;
                double z = pz - wrv.z * mf.dist;
                ParticleParam p = new ParticleParam("smoke", px, py, pz);
                p.size = mf.size;
                for (int i = 0; i < mf.num; i++) {
                    p.a = mf.a * 0.9F + this.rand.nextFloat() * 0.1F;
                    float color = this.rand.nextFloat() * 0.1F;
                    p.r = color + mf.r * 0.9F;
                    p.g = color + mf.g * 0.9F;
                    p.b = color + mf.b * 0.9F;
                    p.age = (int) (mf.age + 0.1 * mf.age * this.rand.nextFloat());
                    p.posX = x + (this.rand.nextDouble() - 0.5) * mf.range;
                    p.posY = y + (this.rand.nextDouble() - 0.5) * mf.range;
                    p.posZ = z + (this.rand.nextDouble() - 0.5) * mf.range;
                    p.motionX = this.rand.nextDouble() * (p.posX < x ? -0.2 : 0.2);
                    p.motionY = this.rand.nextDouble() * (p.posY < y ? -0.03 : 0.03);
                    p.motionZ = this.rand.nextDouble() * (p.posZ < z ? -0.2 : 0.2);
                    McheliEffects.spawnParticle(w, p);
                }
            }
        }
        if (wi.listMuzzleFlash != null) {
            for (WeaponInfo.MuzzleFlash mf : wi.listMuzzleFlash) {
                float color = this.rand.nextFloat() * 0.1F + 0.9F;
                McheliEffects.spawnMuzzleFlash(w, px - wrv.x * mf.dist, py - wrv.y * mf.dist, pz - wrv.z * mf.dist,
                        mf.size, color * mf.r, color * mf.g, color * mf.b, mf.a, mf.age + this.rand.nextInt(3));
            }
        }
    }

    /** Upstream {@code updateWeaponBay}: bay doors open while a seated rider has one of their weapons selected. */
    private void updateWeaponBay(AircraftInfo info) {
        for (int i = 0; i < this.weaponBays.length && i < info.partWeaponBay.size(); i++) {
            WeaponBay wb = this.weaponBays[i];
            boolean isSelected = false;
            for (int wid : info.partWeaponBay.get(i).weaponIds) {
                for (int sid = 0; sid < this.currentWeaponID.length; sid++) {
                    if (wid == this.currentWeaponID[sid] && this.ac.getEntityBySeatId(sid) != null) {
                        isSelected = true;
                    }
                }
            }
            wb.prevRot = wb.rot;
            if (isSelected) {
                wb.rot = Math.min(wb.rot + 3.0F, 90.0F);
            } else {
                wb.rot = Math.max(wb.rot - 3.0F, 0.0F);
            }
        }
    }

    public void hitBullet() {
        this.hitStatus = MAX_HIT_STATUS;
    }

    /** Upstream {@code initRotationYaw}'s weapon part. */
    void initRotation() {
        for (WeaponSet w : this.weapons) {
            w.setYaw(w.getDefYaw());
            w.setPitch(0.0F);
        }
    }

    // ---- impact prediction ----

    private @Nullable WeaponParam aimParam(@Nullable Entity user) {
        if (user == null) {
            return null;
        }
        WeaponParam prm = this.newParam(user);
        WeaponSet currentWs = this.getCurrentWeapon(user);
        this.setTurretFlag(prm);
        Vec3 shotPos = currentWs.getFirstWeapon().getShotPos(this.ac);
        prm.setPosition(this.ac.getX() + shotPos.x, this.ac.getY() + shotPos.y, this.ac.getZ() + shotPos.z);
        float[] shotDir = this.getCurrentWeaponShotDir(user);
        prm.rotYaw = Mth.wrapDegrees(shotDir[0]);
        prm.rotPitch = Mth.wrapDegrees(shotDir[1]);
        return prm;
    }

    /** Upstream {@code getLandInDistance}: horizontal distance to where the current weapon would land, or -1. */
    public double getLandInDistance(@Nullable Entity user) {
        int count = this.ac.getCountOnUpdate();
        if (this.lastCalcLandInDistanceCount != count && (this.ac.level().isClientSide() || count % 5 == 0)) {
            this.lastCalcLandInDistanceCount = count;
            this.prevImpactPos = this.impactPos;
            WeaponParam prm = this.aimParam(user);
            if (prm != null) {
                Vec3 impact = this.getCurrentWeapon(user).getCurrentWeapon().getImpactPos(prm);
                this.impactPos = impact;
                if (this.prevImpactPos == null) {
                    this.prevImpactPos = impact;
                }
                if (impact != null) {
                    double dx = impact.x - this.ac.getX();
                    double dz = impact.z - this.ac.getZ();
                    this.lastLandInDistance = Math.sqrt(dx * dx + dz * dz);
                } else {
                    this.lastLandInDistance = -1.0;
                }
            }
        }
        return this.lastLandInDistance;
    }

    /** The impact point found by the last {@link #getLandInDistance}, or null. */
    public @Nullable Vec3 impactPos() {
        return this.impactPos;
    }

    public @Nullable Vec3 prevImpactPos() {
        return this.prevImpactPos;
    }

    /** Upstream {@code getPredictedImpactPoint}: the CCIP point of a CCIP weapon, once per tick. */
    public @Nullable Vec3 getPredictedImpactPoint(@Nullable Entity user) {
        int count = this.ac.getCountOnUpdate();
        if (this.lastCalcPredictedImpactPointCount != count) {
            this.lastCalcPredictedImpactPointCount = count;
            this.lastPredictedImpactPoint = null;
            if (user != null) {
                WeaponSet ws = this.getCurrentWeapon(user);
                if (ws.getInfo() != null && ws.getInfo().ccip && WeaponInfo.isCCIPSupportedType(ws.getInfo().type)) {
                    WeaponParam prm = new WeaponParam();
                    prm.setPosition(this.ac.getX(), this.ac.getY(), this.ac.getZ());
                    prm.entity = this.ac;
                    prm.user = user;
                    this.lastPredictedImpactPoint = ws.getPredictedImpactPoint(prm);
                }
            }
        }
        return this.lastPredictedImpactPoint;
    }

    // ---- TV missile ----

    public @Nullable TvMissileEntity getTVMissile() {
        return this.tvMissile != null && !this.tvMissile.isRemoved() ? this.tvMissile : null;
    }

    public void setTVMissile(@Nullable TvMissileEntity entity) {
        this.tvMissile = entity;
    }

    // ---- ammunition ----

    public boolean isPilotReloading() {
        return this.ac.getCommonStatus(AircraftEntity.STATUS_RELOADING) || this.supplyAmmoWait > 0;
    }

    /**
     * Upstream {@code updateSupplyAmmo}: while the pilot has the vehicle's inventory open the
     * weapons are being supplied; closing it reloads every magazine.
     */
    void updateSupplyAmmo() {
        if (!this.ac.level().isClientSide()) {
            Entity pilot = this.ac.getPilot();
            boolean isReloading = pilot instanceof Player player && player.isAlive()
                    && player.containerMenu instanceof io.github.mcheliceport.menu.AircraftMenu menu
                    && menu.getAircraft() == this.ac;
            this.ac.setCommonStatus(AircraftEntity.STATUS_RELOADING, isReloading);
            if (!this.ac.isDestroyed() && this.beforeSupplyAmmo && !isReloading) {
                this.reloadAllWeapon();
                this.sendAllAmmoNum(null);
            }
            this.beforeSupplyAmmo = isReloading;
        }
        if (this.ac.getCommonStatus(AircraftEntity.STATUS_RELOADING)) {
            this.supplyAmmoWait = 20;
        }
        if (this.supplyAmmoWait > 0) {
            this.supplyAmmoWait--;
        }
    }

    /**
     * Upstream {@code supplyAmmo}, from the aircraft menu's reload button: {@code payer} pays the
     * definition's round items to add a supply's worth to the reserve. Upstream always charged the
     * pilot (and did nothing when the button was used by someone else); here whoever presses it
     * pays. The pilot's magazines fill when the menu closes, as upstream; anyone else's supply fills
     * the magazine at once, since nothing else would.
     */
    public void supplyAmmo(int weaponID, Player payer) {
        WeaponSet ws = this.getWeapon(weaponID);
        if (this.ac.level().isClientSide()) {
            ws.supplyRestAllAmmo();
            return;
        }
        // As upstream, the pilot earns it whether or not the supply goes through.
        io.github.mcheliceport.registry.ModCriteriaTriggers.trigger(io.github.mcheliceport.registry.ModCriteriaTriggers.SUPPLY_AMMO, this.ac.getPilot());
        if (!this.canPlayerSupplyAmmo(payer, ws)) {
            return;
        }
        this.payRoundItems(payer, ws);
        ws.supplyRestAllAmmo();
        if (payer != this.ac.getPilot()) {
            ws.reloadMag();
        }
        if (payer instanceof ServerPlayer sp) {
            this.sendAmmoNum(sp, weaponID);
        }
        if (this.ac.getPilot() instanceof ServerPlayer pilot && pilot != payer) {
            this.sendAmmoNum(pilot, weaponID);
        }
    }

    private void payRoundItems(Player player, WeaponSet ws) {
        Inventory inv = player.getInventory();
        for (WeaponInfo.RoundItem ri : ws.getInfo().roundItems) {
            Item item = itemOf(ri);
            if (item == null) {
                continue;
            }
            int num = ri.num();
            for (int i = 0; i < inv.getNonEquipmentItems().size() && num > 0; i++) {
                ItemStack stack = inv.getNonEquipmentItems().get(i);
                if (stack.isEmpty() || !stack.is(item)) {
                    continue;
                }
                if (item != Items.WATER_BUCKET && item != Items.LAVA_BUCKET) {
                    int take = Math.min(stack.getCount(), num);
                    stack.shrink(take);
                    num -= take;
                } else if (stack.getCount() == 1) {
                    inv.getNonEquipmentItems().set(i, new ItemStack(Items.BUCKET));
                    num--;
                }
            }
        }
    }

    /** Upstream's GUI {@code canResupply}: the player can pay, the reserve has room and the magazine is not full. */
    public boolean canResupply(Player player, WeaponSet ws) {
        return this.canPlayerSupplyAmmo(player, ws) && ws.getAmmo() < ws.getMagSize();
    }

    /** Upstream {@code canPlayerSupplyAmmo}: the reserve has room and the player carries every round item. */
    public boolean canPlayerSupplyAmmo(Player player, WeaponSet ws) {
        if (!this.ac.canSupply() || ws.getRestAllAmmoNum() + ws.getAmmo() >= ws.getMaxAmmo()) {
            return false;
        }
        Inventory inv = player.getInventory();
        for (WeaponInfo.RoundItem ri : ws.getInfo().roundItems) {
            Item item = itemOf(ri);
            int num = ri.num();
            if (item != null) {
                for (ItemStack stack : inv.getNonEquipmentItems()) {
                    if (!stack.isEmpty() && stack.is(item)) {
                        num -= stack.getCount();
                    }
                    if (num <= 0) {
                        break;
                    }
                }
            }
            if (num > 0) {
                return false;
            }
        }
        return true;
    }

    /**
     * A round item's 26.2 item. 1.12 metadata ({@code damage}) has no equivalent and is ignored;
     * an unknown item name can never be paid.
     */
    public static @Nullable Item itemOf(WeaponInfo.RoundItem ri) {
        Identifier id = Identifier.tryParse(ri.itemName());
        if (id == null) {
            return null;
        }
        return BuiltInRegistries.ITEM.get(id).map(h -> h.value()).filter(i -> i != Items.AIR).orElse(null);
    }

    /** Upstream {@code supplyAmmoToOtherAircraft}: a supply vehicle tops up the reserves of vehicles nearby. */
    void supplyAmmoToOtherAircraft() {
        AircraftInfo info = this.ac.getAcInfo();
        float range = info != null ? info.ammoSupplyRange : 0.0F;
        if (range > 0.0F && info.ammoSupplyStock > 0 && !this.ac.level().isClientSide()) {
            this.supplyAmmoFromStock(range);
            return;
        }
        if (range <= 0.0F || this.ac.level().isClientSide() || this.ac.getCountOnUpdate() % 40 != 0) {
            return;
        }
        for (AircraftEntity other : this.ac.level().getEntitiesOfClass(AircraftEntity.class,
                this.ac.getBoundingBox().inflate(range))) {
            if (other == this.ac || !other.canSupply()) {
                continue;
            }
            AircraftWeapons ow = other.weapons();
            for (int wid = 0; wid < ow.getWeaponNum(); wid++) {
                WeaponSet ws = ow.getWeapon(wid);
                int num = ws.getRestAllAmmoNum() + ws.getAmmo();
                if (num < ws.getMaxAmmo()) {
                    int ammo = Math.max(ws.getMaxAmmo() / 10, 1);
                    ws.setReserveAmmo(num + ammo);
                    if (num != ws.getRestAllAmmoNum() + ws.getAmmo()) {
                        if (ws.getAmmo() <= 0) {
                            ws.reloadMag();
                        }
                        ow.sendAmmoNum(ow.playerUsingWeapon(wid), wid);
                        io.github.mcheliceport.groundcrew.GroundCrew.mark(other,
                                io.github.mcheliceport.groundcrew.GroundCrew.REARM);
                    }
                }
            }
        }
    }

    /**
     * M10c: a supply vehicle with a stock rearms one weapon of each vehicle in range per second (two
     * with a manned loader seat), the emptiest first, paid from its stock.
     */
    private void supplyAmmoFromStock(float range) {
        int interval = CrewRoles.manned(this.ac, io.github.mcheliceport.content.aircraft.CrewRole.LOADER) ? 10 : 20;
        if (this.ac.getCountOnUpdate() % interval != 0 || this.ac.isDestroyed()) {
            return;
        }
        for (AircraftEntity other : this.ac.level().getEntitiesOfClass(AircraftEntity.class,
                this.ac.getBoundingBox().inflate(range))) {
            int stock = this.ac.getAmmoStock();
            if (stock <= 0) {
                return;
            }
            if (other == this.ac || !other.canSupply()) {
                continue;
            }
            int given = other.weapons().rearmEmptiest(stock);
            if (given > 0) {
                this.ac.setAmmoStock(stock - given);
                io.github.mcheliceport.groundcrew.GroundCrew.mark(other, io.github.mcheliceport.groundcrew.GroundCrew.REARM);
            }
        }
    }

    /**
     * M10c: tops up the weapon with the smallest share of its full load by a tenth of that load (at
     * least one round, at most {@code maxRounds}), loading the magazine if it was empty. Returns the
     * rounds given.
     */
    public int rearmEmptiest(int maxRounds) {
        int best = -1;
        float bestShare = 1.0F;
        for (int wid = 0; wid < this.getWeaponNum(); wid++) {
            WeaponSet ws = this.getWeapon(wid);
            if (ws.getCurrentWeapon() == null || ws.getInfo() == null || ws.getMaxAmmo() <= 0) {
                continue;
            }
            float share = (ws.getRestAllAmmoNum() + ws.getAmmo()) / (float) ws.getMaxAmmo();
            if (share < bestShare) {
                bestShare = share;
                best = wid;
            }
        }
        if (best < 0 || maxRounds <= 0) {
            return 0;
        }
        WeaponSet ws = this.getWeapon(best);
        int num = ws.getRestAllAmmoNum() + ws.getAmmo();
        int give = Math.min(Math.min(Math.max(ws.getMaxAmmo() / 10, 1), ws.getMaxAmmo() - num), maxRounds);
        ws.setReserveAmmo(ws.getRestAllAmmoNum() + give);
        int given = ws.getRestAllAmmoNum() + ws.getAmmo() - num;
        if (given > 0) {
            if (ws.getAmmo() <= 0) {
                ws.reloadMag();
            }
            this.sendAmmoNum(this.playerUsingWeapon(best), best);
        }
        return Math.max(given, 0);
    }

    /** Upstream {@code getEntityByWeaponId}: the player whose seat owns weapon {@code wid}. */
    @Nullable ServerPlayer playerUsingWeapon(int wid) {
        AircraftInfo info = this.ac.getAcInfo();
        AircraftInfo.Weapon w = info != null ? info.getWeaponById(wid) : null;
        if (w == null) {
            return null;
        }
        Entity e = this.ac.getEntityBySeatId(this.getWeaponSeatID(this.getWeaponInfoById(wid), w));
        return e instanceof ServerPlayer sp ? sp : null;
    }

    /** Upstream {@code PacketNotifyAmmoNum.sendAllAmmoNum}: to {@code target}, or every rider when null. */
    public void sendAllAmmoNum(@Nullable ServerPlayer target) {
        int n = this.getWeaponNum();
        short[] ammo = new short[n];
        short[] rest = new short[n];
        for (int i = 0; i < n; i++) {
            ammo[i] = (short) this.getWeapon(i).getAmmo();
            rest[i] = (short) this.getWeapon(i).getRestAllAmmoNum();
        }
        this.sendAmmo(new NotifyAmmoPayload(this.ac.getId(), true, (byte) -1, ammo, rest), target);
    }

    /** Upstream {@code PacketNotifyAmmoNum.sendAmmoNum}: one weapon's counts. */
    public void sendAmmoNum(@Nullable ServerPlayer target, int wid) {
        WeaponSet ws = this.getWeapon(wid);
        this.sendAmmo(new NotifyAmmoPayload(this.ac.getId(), false, (byte) wid, new short[] {(short) ws.getAmmo()},
                new short[] {(short) ws.getRestAllAmmoNum()}), target);
    }

    private void sendAmmo(NotifyAmmoPayload payload, @Nullable ServerPlayer target) {
        if (target != null) {
            ModNetwork.sendToPlayer(target, payload);
            return;
        }
        for (int i = 0; i < this.ac.getSeatNum() + 1; i++) {
            if (this.ac.getEntityBySeatId(i) instanceof ServerPlayer sp) {
                ModNetwork.sendToPlayer(sp, payload);
            }
        }
    }

    /** Applies counts from the server (upstream {@code PacketNotifyAmmoNum#onReceive}). */
    public void applyAmmo(NotifyAmmoPayload p) {
        if (p.all()) {
            for (int i = 0; i < this.getWeaponNum() && i < p.ammo().length; i++) {
                this.getWeapon(i).setAmmo(p.ammo()[i]);
                this.getWeapon(i).setReserveAmmo(p.restAmmo()[i]);
            }
        } else if (p.weaponId() >= 0 && p.weaponId() < this.getWeaponNum() && p.ammo().length > 0) {
            this.getWeapon(p.weaponId()).setAmmo(p.ammo()[0]);
            this.getWeapon(p.weaponId()).setReserveAmmo(p.restAmmo()[0]);
        }
    }

    /** Applies one weapon's state from the server (upstream {@code PacketSyncWeapon#onReceive}). */
    public void applySync(WeaponSyncPayload p, boolean seatIsLocalPlayer) {
        if (p.seatId() < 0 || p.seatId() > this.ac.getSeatNum()) {
            return;
        }
        WeaponSet ws = this.getWeapon(p.weaponId());
        ws.setAmmo(p.ammo());
        ws.setReserveAmmo(p.reserveAmmo());
        ws.cooldown = p.cooldown();
        ws.reloadCooldown = p.reloadCooldown();
        if (!seatIsLocalPlayer) {
            this.updateWeaponID(p.seatId(), p.weaponId());
        }
    }

    /** Writes exact magazine and reserve counts; no reload or supply occurs on pickup. */
    void writeAmmoToItem(ItemStack stack) {
        java.util.List<Integer> ammo = new java.util.ArrayList<>(this.getWeaponNum() * 2);
        for (int i = 0; i < this.getWeaponNum(); i++) {
            WeaponSet weapon = this.getWeapon(i);
            ammo.add(weapon.getAmmo());
            ammo.add(weapon.getRestAllAmmoNum());
        }
        stack.set(io.github.mcheliceport.registry.ModDataComponents.AIRCRAFT_AMMO.get(),
                java.util.List.copyOf(ammo));
    }

    /** Old and newly crafted items keep their default ammo; saved zeroes remain empty. */
    void readAmmoFromItem(ItemStack stack) {
        java.util.List<Integer> ammo = stack.get(
                io.github.mcheliceport.registry.ModDataComponents.AIRCRAFT_AMMO.get());
        if (ammo == null) {
            return;
        }
        int count = Math.min(this.getWeaponNum(), ammo.size() / 2);
        for (int i = 0; i < count; i++) {
            WeaponSet weapon = this.getWeapon(i);
            int capacity = Math.max(0, weapon.getMaxAmmo());
            int loaded = Math.max(0, Math.min(ammo.get(i * 2), Math.min(weapon.getMagSize(), capacity)));
            // Reserve capacity depends on the restored magazine.
            weapon.setAmmo(loaded);
            weapon.setReserveAmmo(Math.max(0, Math.min(ammo.get(i * 2 + 1), capacity - loaded)));
        }
    }

    /** The ammunition saved as {@code AcWeaponsAmmo}: magazine plus reserve per weapon set. */
    int[] saveAmmo() {
        int[] list = new int[this.getWeaponNum()];
        for (int i = 0; i < list.length; i++) {
            list[i] = this.getWeapon(i).getRestAllAmmoNum() + this.getWeapon(i).getAmmo();
        }
        return list;
    }

    void loadAmmo(int[] list) {
        for (int i = 0; i < list.length && i < this.getWeaponNum(); i++) {
            this.getWeapon(i).setReserveAmmo(list[i]);
            this.getWeapon(i).reloadMag();
        }
    }

    /** A weapon bay's door angle (upstream {@code MCH_EntityAircraft.WeaponBay}). */
    public static final class WeaponBay {
        public float rot = 0.0F;
        public float prevRot = 0.0F;
    }
}
