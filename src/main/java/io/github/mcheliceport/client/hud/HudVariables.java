package io.github.mcheliceport.client.hud;

import io.github.mcheliceport.McheliCePort;
import io.github.mcheliceport.client.input.ModKeyMappings;
import io.github.mcheliceport.client.input.MouseFlight;
import io.github.mcheliceport.config.ClientConfig;
import io.github.mcheliceport.content.aircraft.AircraftInfo;
import io.github.mcheliceport.content.hud.HudStringArg;
import io.github.mcheliceport.entity.aircraft.AircraftEntity;
import io.github.mcheliceport.entity.heli.HeliEntity;
import java.util.Date;
import java.util.Map;
import net.minecraft.SharedConstants;
import net.minecraft.core.BlockPos;
import net.minecraft.util.Mth;
import io.github.mcheliceport.content.weapon.SightType;
import io.github.mcheliceport.content.weapon.WeaponInfo;
import io.github.mcheliceport.weapon.WeaponBase;
import io.github.mcheliceport.weapon.WeaponSet;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import net.neoforged.fml.ModList;

/**
 * Fills a {@link HudFrame} from the ridden vehicle each frame: upstream's
 * {@code MCH_HudItem#update} and {@code updateVarMap}, with the same Java types, and the values
 * of {@code MCH_HudItemString}. As upstream, the altitude is probed every 30 frames, the low-fuel
 * warning blinks on a 280-frame cycle and the stick position is averaged over four frames.
 *
 * <p>The weapon values are upstream's {@code updateWeapon} and {@code updateVarMap_Weapon} for
 * the rider's current weapon set.
 */
public final class HudVariables {

    private static final float[] STICK_X = new float[4];
    private static final float[] STICK_Y = new float[4];
    private static int stickIndex;
    private static int altitudeUpdateCount;
    private static int altitude;
    private static float prevRadarRot;
    private static int countFuelWarn;
    /** Upstream's {@code MortarDist}: the horizontal distance to the impact, or -1. */
    private static float mortarDist = 0.0F;
    private static String weaponName = "";
    private static String weaponAmmo = "";
    private static String weaponAllAmmo = "";
    private static float reloadPer = 0.0F;
    private static float reloadSec = 0.0F;
    private static String airburstDist = "";
    private static @org.jspecify.annotations.Nullable WeaponSet currentWeapon;

    private HudVariables() {
    }

    /** Upstream's {@code Altitude}: blocks between the vehicle and the ground below, from the last probe. */
    public static int altitude() {
        return altitude;
    }

    /** Fills {@code frame} for {@code player} riding {@code ac}, upstream {@code update} then {@code updateVarMap}. */
    public static void fill(HudFrame frame, AircraftEntity ac, Player player, float partialTicks) {
        AircraftInfo info = ac.getAcInfo();
        updateStick();
        updateAltitude(ac);
        frame.entityRadar = ac.radar.entities();
        frame.enemyRadar = ac.radar.enemies();
        WeaponSet ws = ac.getCurrentWeapon(player);
        updateWeapon(ac, player, ws);
        frame.hasWeapon = currentWeapon != null;
        frame.weaponMagSize = currentWeapon != null ? currentWeapon.getMagSize() : 0;
        frame.aircraftYaw = ac.getYaw();
        frame.cameraYaw = ac.camera.rotationYaw;
        frame.cameraPitch = ac.camera.rotationPitch;
        frame.testMode = ClientConfig.get(ClientConfig.TEST_MODE);

        Level level = ac.level();
        Vec3 motion = ac.getDeltaMovement();
        Map<String, Object> v = frame.vars;
        v.clear();
        v.put("time", level.getOverworldClockTime() % 24000L);
        v.put("test_mode", frame.testMode);
        v.put("plyr_yaw", Mth.wrapDegrees(player.getYRot()));
        v.put("plyr_pitch", player.getXRot());
        float[] brlDir = ac.getCurrentWeaponShotDir(player);
        v.put("brl_yaw", brlDir[0]);
        v.put("brl_pitch", brlDir[1]);
        v.put("yaw", Mth.wrapDegrees(ac.getYaw()));
        v.put("pitch", ac.getPitch());
        v.put("roll", Mth.wrapDegrees(ac.getRoll()));
        v.put("altitude", altitude);
        v.put("sea_alt", Math.max(ac.getY() - horizon(level), 0.0));
        v.put("have_radar", ac.isEntityRadarMounted());
        v.put("radar_rot", radarRot(ac, partialTicks));
        v.put("hp", ac.getHP());
        v.put("max_hp", ac.getMaxHP());
        v.put("hp_rto", ac.getMaxHP() > 0 ? (double) ac.getHP() / ac.getMaxHP() : 0.0);
        v.put("throttle", ac.getCurrentThrottle());
        v.put("pos_x", ac.getX());
        v.put("pos_y", ac.getY());
        v.put("pos_z", ac.getZ());
        v.put("motion_x", motion.x);
        v.put("motion_y", motion.y);
        v.put("motion_z", motion.z);
        v.put("speed", Math.sqrt(motion.x * motion.x + motion.y * motion.y + motion.z * motion.z));
        v.put("fuel", ac.getFuelPercentage());
        v.put("low_fuel", isLowFuel(ac, player));
        v.put("stick_x", stickAverage(STICK_X));
        v.put("stick_y", stickAverage(STICK_Y));
        putWeaponVars(v, ws);
        v.put("vtol_stat", 0);
        v.put("free_look", ac.isPilot(player) && ac.isFreeLookMode() ? 1 : 0);
        v.put("gunner_mode", ac.isGunnerMode(player));
        v.put("cam_mode", ac.getCameraMode(player));
        v.put("cam_zoom", ac.camera.getCameraZoom());
        v.put("auto_pilot", 0);
        v.put("have_flare", info != null && info.haveFlare());
        v.put("can_flare", ac.canUseFlare());
        v.put("inventory", info != null ? info.inventorySize : 0);
        v.put("hovering", ac instanceof HeliEntity && ac.isHoveringMode());
        v.put("is_uav", ac.isUAV());
        v.put("uav_fs", 0.0);

        fillStrings(frame, ac, player, info, level, motion);
    }

    private static void fillStrings(HudFrame frame, AircraftEntity ac, Player player, AircraftInfo info, Level level,
            Vec3 motion) {
        Map<HudStringArg, Object> s = frame.stringArgs;
        s.clear();
        int worldTime = (int) ((level.getOverworldClockTime() + 6000L) % 24000L);
        s.put(HudStringArg.NAME, info != null ? info.displayName : "");
        s.put(HudStringArg.ALTITUDE, altitude);
        s.put(HudStringArg.DATE, new Date());
        s.put(HudStringArg.MC_THOR, worldTime / 1000);
        s.put(HudStringArg.MC_TMIN, worldTime % 1000 * 36 / 10 / 60);
        s.put(HudStringArg.MC_TSEC, worldTime % 1000 * 36 / 10 % 60);
        s.put(HudStringArg.MAX_HP, ac.getMaxHP());
        s.put(HudStringArg.HP, ac.getHP());
        s.put(HudStringArg.HP_PER, (ac.getMaxHP() > 0 ? (double) ac.getHP() / ac.getMaxHP() : 0.0) * 100.0);
        s.put(HudStringArg.POS_X, ac.getX());
        s.put(HudStringArg.POS_Y, ac.getY());
        s.put(HudStringArg.POS_Z, ac.getZ());
        s.put(HudStringArg.MOTION_X, motion.x);
        s.put(HudStringArg.MOTION_Y, motion.y);
        s.put(HudStringArg.MOTION_Z, motion.z);
        s.put(HudStringArg.INVENTORY, info != null ? info.inventorySize : 0);
        s.put(HudStringArg.WPN_NAME, weaponName);
        s.put(HudStringArg.WPN_AMMO, weaponAmmo);
        s.put(HudStringArg.WPN_RM_AMMO, weaponAllAmmo);
        s.put(HudStringArg.RELOAD_PER, reloadPer);
        s.put(HudStringArg.RELOAD_SEC, reloadSec);
        s.put(HudStringArg.MORTAR_DIST, mortarDist);
        s.put(HudStringArg.MC_VER, SharedConstants.getCurrentVersion().name());
        s.put(HudStringArg.MOD_VER, ModList.get().getModContainerById(McheliCePort.MOD_ID)
                .map(c -> c.getModInfo().getVersion().toString()).orElse(""));
        s.put(HudStringArg.MOD_NAME, "MC Helicopter MOD");
        s.put(HudStringArg.YAW, HudInterpreter.getRotate360(ac.getYaw() + 180.0F));
        s.put(HudStringArg.PITCH, -ac.getPitch());
        s.put(HudStringArg.ROLL, Mth.wrapDegrees(ac.getRoll()));
        s.put(HudStringArg.PLYR_YAW, HudInterpreter.getRotate360(player.getYRot() + 180.0F));
        s.put(HudStringArg.PLYR_PITCH, -player.getXRot());
        Entity tvm = ac.getTVMissile();
        s.put(HudStringArg.TVM_POS_X, tvm != null ? tvm.getX() : 0.0);
        s.put(HudStringArg.TVM_POS_Y, tvm != null ? tvm.getY() : 0.0);
        s.put(HudStringArg.TVM_POS_Z, tvm != null ? tvm.getZ() : 0.0);
        s.put(HudStringArg.TVM_DIFF, tvm != null ? Math.sqrt(tvm.distanceToSqr(ac)) : 0.0);
        s.put(HudStringArg.CAM_ZOOM, ac.camera.getCameraZoom());
        s.put(HudStringArg.UAV_DIST, 0.0);
        s.put(HudStringArg.KEY_GUI, ModKeyMappings.GUI.getTranslatedKeyMessage().getString());
        s.put(HudStringArg.THROTTLE, ac.getCurrentThrottle() * 100.0);
        s.put(HudStringArg.AIRBURST_DIST, airburstDist);
    }

    /** Upstream {@code updateWeapon}: the weapon strings and reload progress, kept from the last weapon. */
    private static void updateWeapon(AircraftEntity ac, Player player, WeaponSet ws) {
        if (ac.getWeaponNum() <= 0) {
            return;
        }
        currentWeapon = ws;
        boolean reloading = ac.isPilotReloading();
        weaponName = reloading ? "-- Reloading --" : ws.getDisplayName();
        if (ws.getMagSize() > 0) {
            weaponAmmo = reloading ? "----" : String.format("%4d", ws.getAmmo());
            weaponAllAmmo = reloading ? "----" : String.format("%4d", ws.getRestAllAmmoNum());
        } else {
            weaponAmmo = "";
            weaponAllAmmo = "";
        }
        WeaponInfo wi = ws.getInfo();
        mortarDist = wi.displayMortarDistance ? (float) ac.weapons().getLandInDistance(player) : -1.0F;
        if (wi.delay > wi.reloadTime) {
            reloadSec = Math.abs(ws.cooldown);
            reloadPer = Math.min(Math.abs((float) ws.cooldown / (wi.delay > 0 ? wi.delay : 1)), 1.0F);
        } else {
            reloadSec = ws.reloadCooldown;
            reloadPer = (float) ws.reloadCooldown / (wi.reloadTime > 0 ? wi.reloadTime : 1);
        }
        int burst = ws.getCurrentWeapon().airburstDist;
        airburstDist = burst <= 5 || burst >= 3000 ? "---" : burst + "m + 3";
        reloadSec /= 20.0F;
        reloadPer = (1.0F - reloadPer) * 100.0F;
    }

    /** Upstream {@code updateVarMap_Weapon}. */
    private static void putWeaponVars(Map<String, Object> v, WeaponSet ws) {
        double wpnHeat = 0.0;
        float relTime = 0.0F;
        double lock = 0.0;
        int sightType = 0;
        int hasAirburst = 0;
        boolean reloading = false;
        boolean isHeatWpn = false;
        boolean displayMortarDist = false;
        WeaponBase wb = ws.getCurrentWeapon();
        WeaponInfo wi = wb.getInfo();
        if (wi != null) {
            isHeatWpn = wi.maxHeatCount > 0;
            reloading = ws.isBusy();
            displayMortarDist = wi.displayMortarDistance;
            if (wi.delay > wi.reloadTime) {
                relTime = Math.min(Math.abs((float) ws.cooldown / (wi.delay > 0 ? wi.delay : 1)), 1.0F);
            } else {
                relTime = (float) ws.reloadCooldown / (wi.reloadTime > 0 ? wi.reloadTime : 1);
            }
            if (wi.maxHeatCount > 0) {
                wpnHeat = Math.min((double) ws.getCurrentHeat() / wi.maxHeatCount, 1.0);
            }
            int cntLockMax = wb.getLockCountMax();
            SightType sight = wb.getSightType();
            if (sight == SightType.LOCK && cntLockMax > 0) {
                lock = (double) wb.getLockCount() / cntLockMax;
                sightType = 2;
            }
            if (sight == SightType.ROCKET) {
                sightType = 1;
            }
            hasAirburst = wb.airburstDist == 0 ? 0 : 1;
        }
        v.put("reloading", reloading);
        v.put("reload_time", relTime);
        v.put("wpn_heat", wpnHeat);
        v.put("is_heat_wpn", isHeatWpn);
        v.put("sight_type", sightType);
        v.put("lock", lock);
        v.put("dsp_mt_dist", displayMortarDist);
        v.put("mt_dist", mortarDist);
        v.put("has_airburst", hasAirburst);
    }

    /** Upstream {@code getHorizon}: 63, or the bottom of the world on a superflat world. */
    private static double horizon(Level level) {
        return level instanceof net.minecraft.client.multiplayer.ClientLevel client
                ? client.getLevelData().getHorizonHeight(level) : 63.0;
    }

    /** Upstream {@code updateStick}: the mouse stick over its maximum, Y up, through a 4-sample filter. */
    private static void updateStick() {
        STICK_X[stickIndex] = (float) (MouseFlight.getStickX() / MouseFlight.MAX_STICK_LENGTH);
        STICK_Y[stickIndex] = (float) (-MouseFlight.getStickY() / MouseFlight.MAX_STICK_LENGTH);
        stickIndex = (stickIndex + 1) % STICK_X.length;
    }

    private static double stickAverage(float[] samples) {
        float sum = 0.0F;
        for (float f : samples) {
            sum += f;
        }
        return sum / samples.length;
    }

    /**
     * Upstream {@code updateAltitude}: every 30 frames, the distance down to the first block
     * below, at most the height of the world; above the world's top the height over it is added.
     */
    private static void updateAltitude(AircraftEntity ac) {
        if (altitudeUpdateCount > 0) {
            altitudeUpdateCount--;
            return;
        }
        Level level = ac.level();
        int top = level.getMaxY() + 1;
        int heliY = Math.min(Mth.floor(ac.getY()), top);
        BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();
        int x = Mth.floor(ac.getX());
        int z = Mth.floor(ac.getZ());
        for (int i = 0; heliY - i > level.getMinY(); i++) {
            if (!level.getBlockState(pos.set(x, heliY - i, z)).isAir()) {
                altitude = i;
                if (ac.getY() > top) {
                    altitude = (int) (altitude + (ac.getY() - top));
                }
                break;
            }
        }
        altitudeUpdateCount = 30;
    }

    /** Upstream {@code getRadarRot}: the sweep angle, unwrapped and smoothed against the last frame. */
    private static float radarRot(AircraftEntity ac, float partialTicks) {
        float rot = ac.radar.getRadarRotate();
        float prevRot = prevRadarRot;
        if (rot < prevRot) {
            rot += 360.0F;
        }
        prevRadarRot = ac.radar.getRadarRotate();
        return prevRot + (rot - prevRot) * partialTicks;
    }

    /** Upstream {@code isLowFuel}: below 10%, blinking on for 160 of every 280 frames. */
    private static int isLowFuel(AircraftEntity ac, Player player) {
        if (countFuelWarn <= 0) {
            countFuelWarn = 280;
        }
        countFuelWarn--;
        return countFuelWarn < 160 && ac.getMaxFuel() > 0 && ac.getFuelPercentage() < 0.1F
                && !ac.isInfinityFuel(player, false) ? 1 : 0;
    }
}
