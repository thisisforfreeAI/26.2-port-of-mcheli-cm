package io.github.mcheliceport.content.parser.yaml;

import static io.github.mcheliceport.content.parser.yaml.YamlParser.*;

import com.mojang.logging.LogUtils;
import io.github.mcheliceport.content.BaseInfo;
import io.github.mcheliceport.content.ContentLocation;
import io.github.mcheliceport.content.ContentRegistries;
import io.github.mcheliceport.content.aircraft.AircraftInfo;
import io.github.mcheliceport.content.aircraft.AircraftInfo.CameraPosition;
import io.github.mcheliceport.content.aircraft.AircraftInfo.DrawnPart;
import io.github.mcheliceport.content.aircraft.BoundingBoxInfo;
import io.github.mcheliceport.content.aircraft.HeliInfo;
import io.github.mcheliceport.content.aircraft.PlaneInfo;
import io.github.mcheliceport.content.aircraft.RadarType;
import io.github.mcheliceport.content.aircraft.RwrType;
import io.github.mcheliceport.content.aircraft.SeatInfo;
import io.github.mcheliceport.content.aircraft.SeatRackInfo;
import io.github.mcheliceport.content.aircraft.ShipInfo;
import io.github.mcheliceport.content.aircraft.TankInfo;
import io.github.mcheliceport.content.aircraft.VehicleInfo;
import io.github.mcheliceport.content.hud.Hud;
import io.github.mcheliceport.content.hud.HudFormula;
import io.github.mcheliceport.content.hud.HudItem;
import io.github.mcheliceport.content.parser.ContentParser;
import io.github.mcheliceport.content.sound.SoundDefinitions;
import io.github.mcheliceport.content.throwable.ThrowableInfo;
import io.github.mcheliceport.content.weapon.ArgbColor;
import io.github.mcheliceport.content.weapon.BulletDecay;
import io.github.mcheliceport.content.weapon.CartridgeInfo;
import io.github.mcheliceport.content.weapon.DamageFactor;
import io.github.mcheliceport.content.weapon.MineFuze;
import io.github.mcheliceport.content.weapon.PotionEffectInfo;
import io.github.mcheliceport.content.weapon.SightType;
import io.github.mcheliceport.content.weapon.WeaponInfo;
import io.github.mcheliceport.content.weapon.hbm.FireContainer;
import io.github.mcheliceport.content.weapon.hbm.MistContainer;
import io.github.mcheliceport.content.weapon.hbm.MukeContainer;
import io.github.mcheliceport.content.weapon.hbm.NTSettingContainer;
import io.github.mcheliceport.content.weapon.hbm.VNTSettingContainer;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.TreeMap;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;
import org.yaml.snakeyaml.Yaml;

/**
 * Parses YAML weapon files. Ported from upstream
 * {@code com.norwood.mcheli.helper.info.parsers.yaml.WeaponParser}.
 */
@SuppressWarnings("unchecked")
public final class WeaponParser {

    private static final Logger LOGGER = LogUtils.getLogger();

    private WeaponParser() {}

    private static WeaponInfo.Payload parsePayload(String s) {
        if (s == null || s.isEmpty())
            return WeaponInfo.Payload.NONE;

        return switch (s.trim().toUpperCase(Locale.ROOT)) {
            case "NTM_VNT", "VNT" -> WeaponInfo.Payload.NTM_VNT;
            case "NTM_NT", "NT" -> WeaponInfo.Payload.NTM_NT;
            case "NTM_NUKE", "NUKE" -> WeaponInfo.Payload.NTM_NUKE;
            case "NTM_MININUKE", "MININUKE" -> WeaponInfo.Payload.NTM_MINI_NUKE;
            case "CHEMICAL", "NTM_CHEMICAL" -> WeaponInfo.Payload.NTM_CHEMICAL;
            case "NTM_MIST", "MIST" -> WeaponInfo.Payload.NTM_MIST;
            case "NTM_FIRE", "FIRE", "PHOSPHORUS", "PHOSPHOR", "WP", "BALEFIRE" -> WeaponInfo.Payload.NTM_FIRE;
            default -> WeaponInfo.Payload.NONE;
        };
    }

    public static WeaponInfo parse(WeaponInfo info, Map<String, Object> root) {
        for (Map.Entry<String, Object> entry : root.entrySet()) {
            switch (entry.getKey()) {
                case "DisplayName" -> info.displayName = ((String) entry.getValue()).trim();
                case "BaseDamage" -> info.power = ((Number) entry.getValue()).intValue();
                case "Ballistics" -> parseBallisitcs(info, (Map<String, Object>) entry.getValue());
                case "Camera" -> parseCam(info, (Map<String, Object>) entry.getValue());
                case "Missile" -> parseMissile(info, (Map<String, Object>) entry.getValue());
                case "Sound" -> parseSound(info, (Map<String, Object>) entry.getValue());
                case "Type" -> info.type = ((String) entry.getValue()).trim().toLowerCase(Locale.ROOT);
                case "Recoil" -> parseRecoil(info, (Map<String, Object>) entry.getValue());
                case "Submunition", "Bomblet" -> parseBomblet(info, (Map<String, Object>) entry.getValue());
                case "Buckshot" -> parseBuckshot(info, (Map<String, Object>) entry.getValue());
                case "Damage" -> parseDamage(info, (Map<String, Object>) entry.getValue());
                case "Ammo" -> parseAmmo(info, (Map<String, Object>) entry.getValue());
                case "Render" -> parseRender(info, (Map<String, Object>) entry.getValue());
                case "Explosion" -> parseExplosion(info, (Map<String, Object>) entry.getValue());
                case "Mode" -> info.modeNum = getClamped(1000, entry.getValue());
                case "FixMode" -> info.fixMode = getClamped(10, entry.getValue());
                case "Group" -> info.group = ((String) entry.getValue()).toLowerCase(Locale.ROOT).trim();
                case "FireCooldown" -> info.delay = ((Number) entry.getValue()).intValue();
                case "CanLoadChunks" -> info.enableChunkLoader = (Boolean) entry.getValue();
                case "SwitchCooldown" -> info.weaponSwitchCount = ((Number) entry.getValue()).intValue();
                case "CrossType" -> info.crossType = ((Number) entry.getValue()).intValue();
                case "RidableOnly" -> info.ridableOnly = (Boolean) entry.getValue();
                case "Accuracy" -> info.accuracy = getClamped(0.0F, 1000.0F, entry.getValue());
                case "SelfDestruct" -> info.destruct = (Boolean) entry.getValue();
                case "DispenseItem" -> parseDispenseItem(info, (Map<String, Object>) entry.getValue());
                case "Length" -> info.length = getClamped(1, 300, entry.getValue());
                case "Radius" -> info.radius = getClamped(1, 1000, entry.getValue());
                case "Target" -> info.target = parseTarget((String) entry.getValue());
                case "MarkTime" -> info.markTime = getClamped(1, 30000, entry.getValue()) + 1;
                case "DamageFactor" -> parseDamageFactor(info, (Map<String, Number>) entry.getValue());
                case "NTM" -> parseHBM(info, (Map<String, Object>) entry.getValue());
                case "Effects" -> parseEffects(info, (Map<String, Object>) entry.getValue());
                case "Mine" -> parseMine(info, (Map<String, Object>) entry.getValue());

                default -> logUnknownEntry(entry, "Weapon");
            }
        }
        return info;
    }

    private static void parseAmmo(WeaponInfo info, Map<String, Object> map) {
        for (var entry : map.entrySet()) {
            switch (entry.getKey()) {
                case "ReloadTime" -> info.reloadTime = getClamped(3, 1000, entry.getValue());
                case "MagSize" -> info.round = getClamped(1, 30000, entry.getValue());
                case "MaxAmmo" -> info.maxAmmo = getClamped(30000, entry.getValue());
                case "ResupplyCount" -> info.suppliedNum = getClamped(1, 30000, entry.getValue());
                case "AmmoItems" -> info.roundItems = ((List<Map<String, Object>>) entry.getValue()).stream()
                        .map(WeaponParser::parseRoundMap).collect(Collectors.toList());
                default -> logUnknownEntry(entry, "Ammo");
            }
        }
    }

    private static void parseRadar(WeaponInfo info, Map<String, Object> map) {
        for (var entry : map.entrySet()) {
            switch (entry.getKey()) {
                case "IsRadarMissile" -> info.isRadarMissile = (Boolean) entry.getValue();
                case "ActiveRadar" -> info.activeRadar = (Boolean) entry.getValue();
                case "PassiveRadar" -> info.passiveRadar = (Boolean) entry.getValue();
                case "SemiActiveRadar" -> info.semiActiveRadar = (Boolean) entry.getValue();
                default -> logUnknownEntry(entry, "Radar");
            }
        }
    }

    @SuppressWarnings("unchecked")
    private static void parseMissile(WeaponInfo info, Map<String, Object> map) {
        for (var entry : map.entrySet()) {
            switch (entry.getKey()) {
                case "MarkerRocket" -> parseMarkerRocket(info, (Map<String, Object>) entry.getValue());
                case "LaserGuidance" -> info.laserGuidance = (Boolean) entry.getValue();
                case "HasLaserGuidancePod" -> info.hasLaserGuidancePod = (Boolean) entry.getValue();
                case "IsOffAxis" -> info.enableOffAxis = (Boolean) entry.getValue();
                case "GuidedTorpedo" -> info.isGuidedTorpedo = (Boolean) entry.getValue();
                case "PredictTargetPos" -> info.predictTargetPos = (Boolean) entry.getValue();
                case "EnableBVR" -> info.enableBVR = (Boolean) entry.getValue();
                case "MinRangeBVR" -> info.minRangeBVR = ((Number) entry.getValue()).intValue();
                case "ScanInterval" -> info.scanInterval = ((Number) entry.getValue()).intValue();
                case "InitGuidance" -> parseInitialGuidance(info, (Map<String, Object>) entry.getValue());
                case "Ballistic" -> parseBallistic(info, (Map<String, Object>) entry.getValue());
                case "ARM" -> parseArm(info, (Map<String, Object>) entry.getValue());
                case "Signature" -> parseSignature(info, (Map<String, Object>) entry.getValue());

                case "TickEndHoming" -> info.tickEndHoming = getClamped(-1, 100000, entry.getValue());
                case "PDHDNMaxDegree" -> info.pdHDNMaxDegree = getClamped(-1.0F, 90.0F, entry.getValue());
                case "PDHDNMaxDegreeLockOutCount" -> info.pdHDNMaxDegreeLockOutCount = getClamped(200,
                        entry.getValue());
                case "TurningFactor" -> info.turningFactor = ((Number) entry.getValue()).doubleValue();
                case "MaxDegreeOfMissile" -> info.maxDegreeOfMissile = getClamped(100000, entry.getValue());
                case "EnableDataLink" -> info.enableDataLink = (Boolean) entry.getValue();
                case "OnlyDataLink" -> info.onlyDataLink = (Boolean) entry.getValue();
                case "EnableHMS" -> info.enableHMS = (Boolean) entry.getValue();
                case "NameOnRWR" -> info.nameOnRWR = ((String) entry.getValue()).trim();
                case "IsGPSMissile" -> info.isGPSMissile = (Boolean) entry.getValue();
                case "Heat" -> parseHeat(info, (Map<String, Object>) entry.getValue());

                case "CanBeIntercepted" -> info.canBeIntercepted = (Boolean) entry.getValue();
                case "LockOn" -> parseLockOn(info, (Map<String, Object>) entry.getValue());
                case "Radar" -> parseRadar(info, (Map<String, Object>) entry.getValue());

                default -> logUnknownEntry(entry, "Missile");
            }
        }
    }

    private static void parseLockOn(WeaponInfo info, Map<String, Object> map) {
        for (var entry : map.entrySet()) {
            switch (entry.getKey()) {
                case "CanLockMissile" -> info.canLockMissile = (Boolean) entry.getValue();
                case "LockInWater" -> info.canLockInWater = (Boolean) entry.getValue();
                case "Time" -> info.lockTime = getClamped(0, 1000, entry.getValue());
                case "Delay" -> info.rigidityTime = getClamped(0, 1_000_000, entry.getValue());
                case "MaxRange" -> info.maxLockOnRange = getClamped(2000, entry.getValue());
                case "MaxAngle" -> info.maxLockOnAngle = getClamped(200, entry.getValue());
                case "MinHeight" -> info.lockMinHeight = getClamped(-1, 100, entry.getValue());
                case "PassiveRadarLockOutCount" -> info.passiveRadarLockOutCount = getClamped(200, entry.getValue());
                case "LockedChaffMax" -> info.numLockedChaffMax = ((Number) entry.getValue()).intValue();
                case "ProximityFuseTick" -> info.proximityFuseTick = ((Number) entry.getValue()).intValue();
                case "ProximityFuseDamage" -> info.proximityFuseDamage = ((Number) entry.getValue()).floatValue();
                case "ProximityFuseHeight" -> info.proximityFuseHeight = ((Number) entry.getValue()).intValue();
                default -> logUnknownEntry(entry, "LockOn");
            }
        }
    }

    private static void parseHeat(WeaponInfo info, Map<String, Object> map) {
        for (var entry : map.entrySet()) {
            switch (entry.getKey()) {
                case "isHeatMissile" -> info.isHeatSeekerMissile = (Boolean) entry.getValue();
                case "HeatCount" -> info.heatCount = getClamped(100000, entry.getValue());
                case "MaxHeatCount" -> info.maxHeatCount = getClamped(100000, entry.getValue());
                case "AntiFlareCount" -> info.antiFlareCount = getClamped(-1, 200, entry.getValue());
                default -> logUnknownEntry(entry, "Heat");
            }
        }
    }

    private static void parseExplosion(WeaponInfo info, Map<String, Object> value) {
        for (Map.Entry<String, Object> entry : value.entrySet()) {
            switch (entry.getKey()) {
                case "Power" -> info.explosion = ((Number) entry.getValue()).intValue();
                case "Radio" -> info.explosionRadius = getClamped(0.0F, 100.0F, entry.getValue());
                case "DamageRadio" -> info.explosionDamageRadius = getClamped(0.0F, 100.0F, entry.getValue());
                case "Type" -> info.explosionType = (String) entry.getValue();
                case "ProximityFuseDist" -> info.proximityFuseDist = getClamped(0.0F, 2000.0F, entry.getValue());
                case "DestructionPower" -> info.explosionBlock = ((Number) entry.getValue()).intValue();
                case "PowerUnderwater" -> info.explosionInWater = ((Number) entry.getValue()).intValue();
                case "ExplosionAltitude" -> info.explosionAltitude = ((Number) entry.getValue()).intValue();
                case "FuseRebound" -> info.bound = getClamped(0.0F, 100000.0F, entry.getValue());
                case "FuseTime" -> info.timeFuse = ((Number) entry.getValue()).intValue();
                case "FuseDelay" -> info.delayFuse = ((Number) entry.getValue()).intValue();
                case "CanAirburst" -> info.canAirburst = (Boolean) entry.getValue();
                case "ExplosionAirburst" -> info.explosionAirburst = getClamped(50, entry.getValue());
                case "Flaming" -> info.flaming = ((Boolean) entry.getValue());
                case "FAE", "FuelAir" -> info.isFAE = (Boolean) entry.getValue();
                case "CanDestroyBlocks" -> info.disableDestroyBlock = !(Boolean) entry.getValue();
                case "ThroughWall" -> info.explosionThroughWall = (Boolean) entry.getValue();
                case "ThroughWallFactor" -> info.explosionThroughWallFactor = ((Number) entry.getValue()).floatValue();
                case "NewBreak" -> info.isNewExplosionBreak = (Boolean) entry.getValue();
            }
        }
    }

    private static void parseDispenseItem(WeaponInfo info, Map<String, Object> value) {
        String loc = ((String) getAny(value, Arrays.asList("Location", "Loc", "Name"), null)).toLowerCase(Locale.ROOT)
                .trim();
        info.dispenseItemLoc = loc;
        info.dispenseDamege = ((Number) getAny(value, Arrays.asList("Meta", "Damage"), 0)).intValue();
        if (value.containsKey("DispenseRange"))
            info.dispenseRange = getClamped(1, 100, value.get("DispenseRange"));
    }

    private static void parseBomblet(WeaponInfo info, Map<String, Object> value) {
        for (Map.Entry<String, Object> entry : value.entrySet()) {
            switch (entry.getKey()) {
                case "Count" -> info.bomblet = getClamped(0, 1000, entry.getValue());
                case "DeployTime" -> info.bombletSTime = getClamped(0, 1000, entry.getValue());
                case "Spread" -> info.bombletDiff = getClamped(0.0F, 1000.0F, entry.getValue());
                case "SpawnInAir" -> info.spawnBulletInAir = (Boolean) entry.getValue();
                case "SpawnMaxNum" -> info.spawnBulletMaxNum = ((Number) entry.getValue()).intValue();
                case "SpawnIntervalTick" -> info.spawnBulletIntervalTick = ((Number) entry.getValue()).intValue();
                case "SpawnPerNum" -> info.spawnBulletPerNum = ((Number) entry.getValue()).intValue();
                case "SpawnInheritSpeed" -> info.spawnBulletInheritSpeed = (Boolean) entry.getValue();
                case "DestructAfterSpawn" -> info.destructAfterSpawnBullet = (Boolean) entry.getValue();
                case "Ahead" -> info.ahead = (Boolean) entry.getValue();
                case "AheadSolveIntervalTick" -> info.aheadSolveIntervalTick = ((Number) entry.getValue()).intValue();
            }
        }
    }

    /**
     * Mine mode for the bomb weapon type. A non-{@code None} {@code Fuze} turns the dropped bomb into
     * a persistent proximity mine (see {@code MCH_EntityBomb}).
     * <pre>
     * Mine:
     *   Fuze: ProximityPlayer   # None | ProximityPlayer | ProximityVehicle
     *   Range: 4.0              # proximity-trigger radius in blocks
     *   ArmDelay: 40            # ticks after landing before it can detonate
     * </pre>
     */
    private static void parseMine(WeaponInfo info, Map<String, Object> value) {
        for (Map.Entry<String, Object> entry : value.entrySet()) {
            switch (entry.getKey()) {
                case "Fuze", "Type", "MineType" -> info.mineFuze = parseMineFuze((String) entry.getValue());
                case "Range" -> info.mineRange = getClamped(0.0F, 256.0F, entry.getValue());
                case "ArmDelay" -> info.mineArmDelay = getClamped(0, 1_000_000, entry.getValue());
                default -> logUnknownEntry(entry, "Mine");
            }
        }
    }

    private static MineFuze parseMineFuze(String s) {
        if (s == null || s.isEmpty())
            return MineFuze.NONE;

        return switch (s.trim().toUpperCase(Locale.ROOT)) {
            case "PROXIMITYPLAYER", "PROXIMITY_PLAYER", "PLAYER" -> MineFuze.PROXIMITY_PLAYER;
            case "PROXIMITYVEHICLE", "PROXIMITY_VEHICLE", "VEHICLE" -> MineFuze.PROXIMITY_VEHICLE;
            default -> MineFuze.NONE;
        };
    }

    private static void parseBuckshot(WeaponInfo info, Map<String, Object> value) {
        for (Map.Entry<String, Object> entry : value.entrySet()) {
            switch (entry.getKey()) {
                case "Count" -> info.buckshotCount = getClamped(1, 1000, entry.getValue());
                case "PayloadType", "Payload" -> info.buckshotPayload = parseBuckshotPayload((String) entry.getValue());
                default -> logUnknownEntry(entry, "Buckshot");
            }
        }
    }

    private static WeaponInfo.BuckshotPayload parseBuckshotPayload(String s) {
        if (s == null || s.isEmpty())
            return WeaponInfo.BuckshotPayload.BULLET;

        return switch (s.trim().toUpperCase(Locale.ROOT)) {
            case "ROCKET" -> WeaponInfo.BuckshotPayload.ROCKET;
            default -> WeaponInfo.BuckshotPayload.BULLET;
        };
    }

    private static void parseCam(WeaponInfo info, Map<String, Object> value) {
        for (Map.Entry<String, Object> entry : value.entrySet()) {
            switch (entry.getKey()) {
                case "EnableMortarRadar" -> info.hasMortarRadar = (Boolean) entry.getValue();
                case "MortarRadarMaxDist" -> info.mortarRadarMaxDist = ((Number) entry.getValue()).doubleValue();
                case "DisplayMortarDistance" -> info.displayMortarDistance = ((Boolean) entry.getValue());
                case "FixPitch" -> info.fixCameraPitch = ((Boolean) entry.getValue());
                case "RotationSpeedPitch" -> info.cameraRotationSpeedPitch = getClamped(0.0F, 100.0F, entry.getValue());
                case "CCIP" -> info.ccip = (Boolean) entry.getValue();
                case "CCIPTexture" -> info.ccipTexture = ((String) entry.getValue()).trim();
                case "CCIPFactor" -> info.ccipFactor = ((Number) entry.getValue()).floatValue();
                case "LockEntity" -> info.lockEntity = (Boolean) entry.getValue();
                case "FollowLockEntity" -> info.cameraFollowLockEntity = (Boolean) entry.getValue();
                case "FollowStrength" -> info.cameraFollowStrength = ((Number) entry.getValue()).floatValue();
                case "Sight" -> {
                    String sight = ((String) entry.getValue()).toLowerCase(Locale.ROOT);
                    if (sight.equals("movesight")) info.sight = SightType.ROCKET;
                    else if (sight.equals("missilesight")) info.sight = SightType.LOCK;
                }
                case "Zoom" -> {
                    Object val = entry.getValue();
                    if (val instanceof List<?>list && !list.isEmpty()) {
                        float[] zoomArray = new float[list.size()];
                        for (int i = 0; i < list.size(); i++) {
                            zoomArray[i] = getClamped(10F, list.get(i));
                        }
                        info.zoom = zoomArray;
                    } else if (val instanceof Number num) {
                        info.zoom = new float[] { num.floatValue() };
                    } else throw new IllegalArgumentException("Zoom must be either a number or a list of numbers!");

                }
            }
        }
    }

    // Okay..?
    private static int parseTarget(String str) {
        str = str.toLowerCase(Locale.ROOT);
        int flags = 0;
        if (str.contains("block")) return 64;
        if (str.contains("planes")) flags |= 32;
        if (str.contains("helicopters")) flags |= 16;
        if (str.contains("vehicles") || str.contains("tanks")) flags |= 8;
        if (str.contains("players")) flags |= 4;
        if (str.contains("monsters")) flags |= 2;
        if (str.contains("others")) flags |= 1;
        return flags;
    }

    /**
     * Target names as in the TXT format ({@code Player}, {@code Heli}, ...) or, as an extension,
     * entity type IDs such as {@code minecraft:zombie}. Upstream instead tried to load other names
     * as Java entity classes, which have no meaning in 26.2. Upstream also truncated the factors to
     * whole numbers; the port reads them as decimals, as the TXT format does.
     */
    private static void parseDamageFactor(WeaponInfo info, Map<String, Number> map) {
        DamageFactor factors = new DamageFactor();
        for (Map.Entry<String, Number> entry : map.entrySet()) {
            String target = DamageFactor.normalizeTarget(entry.getKey());
            if (!DamageFactor.isBuiltinTarget(target) && target.indexOf(':') < 0) {
                LOGGER.warn("Unknown damage factor target '{}' in weapon {}", entry.getKey(), info.name);
                continue;
            }
            factors.add(target, getClamped(0.0F, 1000000.0F, (Object) entry.getValue()));
        }
        if (!factors.getFactors().isEmpty()) {
            info.damageFactor = factors;
        }
    }

    private static CartridgeInfo parseCardridge(Map<String, Object> map) {
        String name = null;
        float accel = 0F;
        float yaw = 0F;
        float pitch = 0.0F;
        float scale = 1F;
        float gravity = -0.04F;
        float bound = 0.5F;

        for (Map.Entry<String, Object> entry : map.entrySet()) {
            switch (entry.getKey()) {
                case "Name" -> name = ((String) entry.getValue()).toLowerCase(Locale.ROOT);
                case "Accel", "Acceleration" -> accel = ((Number) entry.getValue()).floatValue();
                case "Yaw" -> yaw = ((Number) entry.getValue()).floatValue();
                case "Pitch" -> pitch = ((Number) entry.getValue()).floatValue();
                case "Scale" -> scale = ((Number) entry.getValue()).floatValue();
                case "Gravity" -> gravity = ((Number) entry.getValue()).floatValue();
                case "Bound" -> bound = ((Number) entry.getValue()).floatValue();
            }
        }
        return new CartridgeInfo(name, accel, yaw, pitch, bound, gravity, scale);
    }

    private static void parseRender(WeaponInfo info, Map<String, Object> value) {
        for (Map.Entry<String, Object> entry : value.entrySet()) {
            switch (entry.getKey()) {
                case "MuzzleFlashes" -> info.listMuzzleFlash = parseMuzzleFlashes(
                        (List<Map<String, Object>>) entry.getValue());
                case "MuzzleSmoke" -> info.listMuzzleFlashSmoke = parseMuzzleFlashes(
                        (List<Map<String, Object>>) entry.getValue());
                case "BulletModel" -> info.bulletModelName = ((String) entry.getValue()).trim().toLowerCase(Locale.ROOT);
                case "BombletModel" -> info.bombletModelName = ((String) entry.getValue()).trim().toLowerCase(Locale.ROOT);
                case "TrajectoryParticle" -> {
                    var rawString = ((String) entry.getValue()).trim().toLowerCase(Locale.ROOT);
                    info.trajectoryParticleName = "none".equals(rawString) ? "" : rawString;
                }
                case "Smoke" -> parseSmoke(info, (Map<String, Object>) entry.getValue());
                case "TrajectoryParticleStartTick" -> info.trajectoryParticleStartTick = getClamped(10_000,
                        entry.getValue());
                case "TrajectoryParticleEndTick" -> info.trajectoryParticleEndTick = getClamped(-1, 10_000,
                        entry.getValue());
                case "EnableExhaustFlare" -> info.enableExhaustFlare = (Boolean) entry.getValue();
                case "BulletModelEnd" -> parseBulletModelEnd(info, entry.getValue());

                case "FlakParticlesCrack" -> info.flakParticlesCrack = getClamped(300, entry.getValue());
                case "ParticlesFlak" -> info.numParticlesFlak = getClamped(100, entry.getValue());
                case "SpentCasings" -> info.cartridge = parseCardridge((Map<String, Object>) entry.getValue());
            }
        }
    }

    private static void parseSmoke(WeaponInfo info, Map<String, Object> value) {
        for (Map.Entry<String, Object> entry : value.entrySet()) {
            switch (entry.getKey()) {
                case "DisableSmoke" -> info.disableSmoke = (Boolean) entry.getValue();
                case "SmokeSize" -> info.smokeSize = getClamped(0F, 100F, entry.getValue());
                case "SmokeNum" -> info.smokeNum = getClamped(1, 100, entry.getValue());
                case "SmokeMaxAge" -> info.smokeMaxAge = getClamped(2, 1000, entry.getValue());
                case "BulletColorInWater" -> info.colorInWater = new ArgbColor(
                        parseHexColor((String) entry.getValue()));
                case "BulletColor" -> info.color = new ArgbColor(parseHexColor((String) entry.getValue()));
                // Apparently Smoke color and bullet color set the same variable
            }
        }
    }

    private static List<WeaponInfo.MuzzleFlash> parseMuzzleFlashes(List<Map<String, Object>> rawList) {
        if (rawList == null) return Collections.emptyList();

        return rawList.stream()
                .map(map -> WeaponInfo.MuzzleFlash.fromArgb(((Number) map.get("Distance")).floatValue(),
                        ((Number) map.get("Size")).floatValue(), ((Number) map.get("Range")).floatValue(),
                        ((Number) map.get("Age")).intValue(), parseHexColor((String) map.get("Color")),
                        ((Number) map.get("Count")).intValue()))
                .collect(Collectors.toList());
    }

    /**
     *
     * Payload type - Decides the type of the explosion and its effect;
     * EffectOnly - Whenever to only use the particle effect, but default to the explosion sfx selected by PayloadType
     * Fluid - Fluid to disperse on impact, works like the canisters
     */
    private static void parseHBM(WeaponInfo info, Map<String, Object> map) {
        info.useHBM = true;
        for (Map.Entry<String, Object> entry : map.entrySet()) {
            switch (entry.getKey()) {
                case "PayloadType", "Type" -> info.payloadNTM = parsePayload((String) entry.getValue());
                case "EffectOnly" -> info.effectOnly = (Boolean) entry.getValue();
                case "Mist" -> parseMist(info, (Map<String, Object>) entry.getValue());
                case "MiniNuke", "Muke" -> {
                    Object muke = entry.getValue();
                    if (muke instanceof String mukeString) {
                        info.mukeContainer = MukeContainer.preset(mukeString);
                        if (info.mukeContainer == null) {
                            throw new IllegalArgumentException("Unknown Muke type string: '" + mukeString
                                    + "'. Expected one of: SAFE, TOTS, LOW, MEDIUM, HIGH.");
                        }
                    } else if (muke instanceof Map<?, ?>mukeMap) {
                        parseMukeContainer(info, (Map<String, Object>) mukeMap);
                    } else {
                        throw new IllegalArgumentException(
                                "Invalid Muke type: expected a String or Map, but got: " + muke.getClass().getName());
                    }
                }

                case "VNT" -> parseVNT(info, (Map<String, Object>) entry.getValue());

                case "NT" -> parseNTExplosion(info, (Map<String, Object>) entry.getValue());
                case "Fire", "Phosphorus", "Splash" -> parseFire(info, (Map<String, Object>) entry.getValue());
                default -> logUnknownEntry(entry, "NTM");
            }
        }
    }

    private static void parseVNT(WeaponInfo info, Map<String, Object> map) {
        Object attribs = map.get("Attributes");
        Map<String, Object> processorMap = attribs instanceof Map<?, ?> m
                ? (Map<String, Object>) m : new java.util.HashMap<>();
        VNTSettingContainer container = new VNTSettingContainer(processorMap);
        info.vntSettingContainer = container;

        for (Map.Entry<String, Object> entry : map.entrySet()) {
            switch (entry.getKey()) {
                case "Attributes" -> { /* already consumed into the container above */ }
                case "Preset" -> {
                    if (entry.getValue() instanceof String s) container.preset = s;
                }
                case "Effect" -> {
                    if (entry.getValue() instanceof String str) {
                        container.explosionEffect = switch (str.toUpperCase(Locale.ROOT)) {
                            case "LARGE" -> VNTSettingContainer.ExplosionEffect.large();
                            case "STANDARD" -> VNTSettingContainer.ExplosionEffect.standard();
                            case "MEDIUM", "LOW" -> VNTSettingContainer.ExplosionEffect.medium();
                            default -> throw new IllegalArgumentException(
                                    "Unknown VNT Effect string: '" + str +
                                            "'. Expected one of: LARGE, STANDARD, MEDIUM, LOW.");
                        };
                    } else if (entry.getValue() instanceof Map<?, ?>vntMapRaw) {
                        var vntMap = (Map<String, Object>) vntMapRaw;
                        var effect = VNTSettingContainer.ExplosionEffect.standard();
                        for (Map.Entry<String, Object> vntEntry : vntMap.entrySet()) {
                            var value = vntEntry.getValue();
                            switch (vntEntry.getKey()) {
                                case "IsSmall" -> effect.isSmall = (Boolean) value;
                                case "CloudCount" -> effect.cloudCount = ((Number) value).intValue();
                                case "CloudScale" -> effect.cloudScale = ((Number) value).floatValue();
                                case "CloudSpeedMult" -> effect.cloudSpeedMult = ((Number) value).floatValue();
                                case "WaveScale" -> effect.waveScale = ((Number) value).floatValue();
                                case "DebrisCount" -> effect.debrisCount = ((Number) value).intValue();
                                case "DebrisSize" -> effect.debrisSize = ((Number) value).intValue();
                                case "DebrisRetry" -> effect.debrisRetry = ((Number) value).intValue();
                                case "DebrisVelocity" -> effect.debrisVelocity = ((Number) value).floatValue();
                                case "DebrisHorizontalDeviation" -> effect.debrisHorizontalDeviation = ((Number) value)
                                        .floatValue();
                                case "DebrisVerticalOffset" -> effect.debrisVerticalOffset = ((Number) value)
                                        .floatValue();
                                case "SoundRange" -> effect.soundRange = ((Number) value).floatValue();
                                default -> logUnknownEntry(vntEntry, "VNTEffect");
                            }
                        }
                        container.explosionEffect = effect;
                    }
                }
                default -> logUnknownEntry(entry, "VNT");
            }
        }
    }

    private static void parseFire(WeaponInfo info, Map<String, Object> map) {
        FireContainer container = new FireContainer();
        for (Map.Entry<String, Object> entry : map.entrySet()) {
            switch (entry.getKey()) {
                case "FireType", "Type" -> container.fireType = ((String) entry.getValue()).trim();
                case "Count" -> container.count = ((Number) entry.getValue()).intValue();
                case "Width" -> container.width = ((Number) entry.getValue()).floatValue();
                case "Height" -> container.height = ((Number) entry.getValue()).floatValue();
                case "AreaSpread", "Spread" -> container.areaSpread = ((Number) entry.getValue()).intValue();
                case "Lifetime" -> container.lifetime = ((Number) entry.getValue()).intValue();
                case "LifetimeVariance" -> container.lifetimeVariance = ((Number) entry.getValue()).intValue();
                case "Particles" -> container.particles = (Boolean) entry.getValue();
                default -> logUnknownEntry(entry, "Fire");
            }
        }
        info.fireContainer = container;
    }

    private static void parseMist(WeaponInfo info, Map<String, Object> map) {
        MistContainer container = new MistContainer();
        for (Map.Entry<String, Object> entry : map.entrySet()) {
            switch (entry.getKey()) {
                case "FluidType", "Fluid" -> container.fluidType = ((String) entry.getValue()).toUpperCase(Locale.ROOT)
                        .trim();
                case "CloudCount", "Count" -> container.cloudCount = ((Number) entry.getValue()).intValue();
                case "Width" -> container.width = ((Number) entry.getValue()).floatValue();
                case "Height" -> container.height = ((Number) entry.getValue()).floatValue();
                case "AreaSpread", "Spread" -> container.areaSpread = ((Number) entry.getValue()).intValue();
                case "Lifetime" -> container.lifetime = ((Number) entry.getValue()).intValue();
                case "LifetimeVariance" -> container.lifetimeVariance = ((Number) entry.getValue()).intValue();
                default -> logUnknownEntry(entry, "Mist");
            }
        }
        info.mistContainer = container;
    }

    private static void parseMukeContainer(WeaponInfo info, Map<String, Object> map) {
        MukeContainer container = new MukeContainer();

        for (Map.Entry<String, Object> entry : map.entrySet()) {
            switch (entry.getKey()) {
                case "UseTorex" -> container.miniNuke = !((Boolean) entry.getValue());
                case "Safe" -> container.safe = (Boolean) entry.getValue();
                case "BlastRadius" -> container.blastRadius = ((Number) entry.getValue()).floatValue();
                case "KillRadius" -> container.killRadius = ((Number) entry.getValue()).floatValue();
                case "RadiationLevel" -> container.radiationLevel = ((Number) entry.getValue()).floatValue();
                case "Particle" -> container.particle = (String) entry.getValue();
                case "ShrapnelCount" -> container.shrapnelCount = ((Number) entry.getValue()).intValue();
                case "Resolution", "Res" -> container.resolution = ((Number) entry.getValue()).intValue();
                case "Attributes", "Attrib" -> {
                    Object val = entry.getValue();
                    if (val instanceof List<?>list) {
                        container.attributes = list.stream()
                                .map(Object::toString)
                                .collect(Collectors.toList());
                    }
                }

                default -> logUnknownEntry(entry, "MiniNuke");
            }
        }
        info.mukeContainer = container;
    }

    private static void parseNTExplosion(WeaponInfo info, Map<String, Object> map) {
        int res = 16;
        List<String> attribs = null;
        for (Map.Entry<String, Object> entry : map.entrySet()) {
            switch (entry.getKey()) {
                case "Resolution", "Res" -> res = ((Number) entry.getValue()).intValue();
                case "Attributes", "Attrib" -> attribs = (List<String>) entry.getValue();
            }
        }
        info.ntSettingContainer = new NTSettingContainer(attribs, res);
    }

    private static void parseMarkerRocket(WeaponInfo info, Map<String, Object> map) {
        for (Map.Entry<String, Object> entry : map.entrySet()) {
            switch (entry.getKey()) {

                case "Count" -> info.markerRocketSpawnNum = ((Number) entry.getValue()).intValue();
                case "Spread" -> info.markerRocketSpawnDiff = ((Number) entry.getValue()).intValue();
                case "SpawnHeight" -> info.markerRocketSpawnHeight = ((Number) entry.getValue()).intValue();
                case "Acceleration" -> info.markerRocketSpawnSpeed = ((Number) entry.getValue()).intValue();

            }
        }
    }

    // TODO: Oredict
    static WeaponInfo.RoundItem parseRoundMap(Map<String, Object> roundMap) {
        int count = getClamped(1, 64, getAny(roundMap, Arrays.asList("Count", "Num"), 1));
        String loc = ((String) getAny(roundMap, Arrays.asList("Loc", "Name"), null)).toLowerCase(Locale.ROOT)
                .trim();
        int meta = getClamped(Short.MAX_VALUE, getAny(roundMap, Arrays.asList("Meta", "Damage"), 1));
        return WeaponInfo.RoundItem.of(count, loc, meta);
    }

    private static void parseBallisitcs(WeaponInfo info, Map<String, Object> map) {
        for (Map.Entry<String, Object> entry : map.entrySet()) {
            switch (entry.getKey()) {
                case "Acceleration", "Accel" -> info.acceleration = getClamped(0.0F, 100.0F, entry.getValue());
                case "AccelerationInWater" -> info.accelerationInWater = getClamped(0.0F, 100.0F, entry.getValue());
                case "Gravity" -> info.gravity = getClamped(-50.0F, 50.0F, entry.getValue());
                case "GravityInWater" -> info.gravityInWater = getClamped(-50.0F, 50.0F, entry.getValue());
                case "VelocityInWater" -> info.velocityInWater = ((Number) entry.getValue()).floatValue();
                case "SpeedFactor" -> info.speedFactor = ((Number) entry.getValue()).floatValue();
                case "SpeedFactorStartTick" -> info.speedFactorStartTick = ((Number) entry.getValue()).intValue();
                case "SpeedFactorEndTick" -> info.speedFactorEndTick = ((Number) entry.getValue()).intValue();
                case "SpeedDependsAircraft" -> info.speedDependsAircraft = (Boolean) entry.getValue();
                case "Piercing" -> info.piercing = getClamped(1_000, entry.getValue());
                case "FlakSpread" -> info.flakParticlesDiff = ((Number) entry.getValue()).floatValue();
            }
        }
    }

    private static void parseRecoil(WeaponInfo info, Map<String, Object> map) {
        info.recoilBufCount = 1;
        info.recoilBufCountSpeed = 1;
        for (Map.Entry<String, Object> entry : map.entrySet()) {
            String key = entry.getKey();
            Object value = entry.getValue();

            switch (key) {
                case "Power" -> info.recoil = getClamped(100F, entry.getValue());
                case "Pitch" -> info.recoilPitch = ((Number) value).floatValue();
                case "Yaw" -> info.recoilYaw = ((Number) value).floatValue();
                case "PitchRange" -> info.recoilPitchRange = ((Number) value).floatValue();
                case "YawRange" -> info.recoilYawRange = ((Number) value).floatValue();
                case "RecoverFactor" -> info.recoilRecoverFactor = ((Number) value).floatValue();

                case "BufferCount" -> info.recoilBufCount = ((Number) value).intValue();
                case "BufferSpeed" -> info.recoilBufCountSpeed = ((Number) value).intValue();
            }
        }

        if (info.recoilBufCountSpeed > info.recoilBufCount / 2) {
            info.recoilBufCountSpeed = info.recoilBufCount / 2;
        }
    }

    private static void parseSound(WeaponInfo info, Map<String, Object> map) {
        for (Map.Entry<String, Object> entry : map.entrySet()) {
            switch (entry.getKey()) {
                case "Name" -> info.fireSound = INSTANCE.parseSoundEffect(entry.getValue());
                case "Locations" -> parseSoundLoc(info, (Map<String, Object>) entry.getValue());
                case "Delay" -> info.soundDelay = getClamped(0, 1000, entry.getValue());
                case "Volume" -> info.soundVolume = getClamped(0.0F, 1000.0F, entry.getValue());
                case "Pitch" -> info.soundPitch = getClamped(0.0F, 1.0F, entry.getValue());
                case "PitchRandom" -> info.soundPitchRandom = getClamped(0.0F, 1.0F, entry.getValue());
                case "HitSoundRange" -> info.hitSoundRange = ((Number) entry.getValue()).intValue();

            }
        }
    }

    private static void parseSoundLoc(WeaponInfo info, Map<String, Object> map) {
        for (Map.Entry<String, Object> entry : map.entrySet()) {
            switch (entry.getKey()) {
                case "Hit" -> info.hitSound = INSTANCE.parseSoundEffect(entry.getValue());
                case "Fire" -> info.fireSound = INSTANCE.parseSoundEffect(entry.getValue());
                case "HitMetal" -> info.hitSoundIron = INSTANCE.parseSoundEffect(entry.getValue());
                case "Railgun" -> info.railgunSound = INSTANCE.parseSoundEffect(entry.getValue());
                case "WeaponSwitch" -> info.weaponSwitchSound = INSTANCE.parseSoundEffect(entry.getValue());

            }
        }
    }

    private static void parseDamage(WeaponInfo info, Map<String, Object> map) {
        for (Map.Entry<String, Object> entry : map.entrySet()) {
            switch (entry.getKey()) {
                case "ExplosionDamageVsLiving" -> info.explosionDamageVsLiving = ((Number) entry.getValue())
                        .floatValue();
                case "ExplosionDamageVsPlayer" -> info.explosionDamageVsPlayer = ((Number) entry.getValue())
                        .floatValue();
                case "ExplosionDamageVsPlane" -> info.explosionDamageVsPlane = ((Number) entry.getValue()).floatValue();
                case "ExplosionDamageVsVehicle" -> info.explosionDamageVsVehicle = ((Number) entry.getValue())
                        .floatValue();
                case "ExplosionDamageVsTank" -> info.explosionDamageVsTank = ((Number) entry.getValue()).floatValue();
                case "ExplosionDamageVsHeli" -> info.explosionDamageVsHeli = ((Number) entry.getValue()).floatValue();
                case "ExplosionDamageVsShip" -> info.explosionDamageVsShip = ((Number) entry.getValue()).floatValue();
            }
        }
    }

    private static void parseInitialGuidance(WeaponInfo info, Map<String, Object> map) {
        for (Map.Entry<String, Object> entry : map.entrySet()) {
            switch (entry.getKey()) {
                case "InitMaxDegreeTick" -> info.initMaxDegreeTick = ((Number) entry.getValue()).intValue();
                case "InitMaxDegreeOfMissile" -> info.initMaxDegreeOfMissile = ((Number) entry.getValue()).intValue();
                case "InitTurningFactorTick" -> info.initTurningFactorTick = ((Number) entry.getValue()).intValue();
                case "InitTurningFactor" -> info.initTurningFactor = ((Number) entry.getValue()).doubleValue();
                default -> logUnknownEntry(entry, "InitGuidance");
            }
        }
    }

    private static void parseBallistic(WeaponInfo info, Map<String, Object> map) {
        for (Map.Entry<String, Object> entry : map.entrySet()) {
            switch (entry.getKey()) {
                case "BallisticMissile" -> info.ballisticMissile = (Boolean) entry.getValue();
                case "ArcFactor" -> info.ballisticArcFactor = ((Number) entry.getValue()).doubleValue();
                case "ArcMinHeight" -> info.ballisticArcMinHeight = ((Number) entry.getValue()).doubleValue();
                case "ArcMaxHeight" -> info.ballisticArcMaxHeight = ((Number) entry.getValue()).doubleValue();
                case "MinDistance" -> info.ballisticMinDistance = ((Number) entry.getValue()).doubleValue();
                case "LateralSine" -> info.ballisticLateralSine = (Boolean) entry.getValue();
                case "LateralAmplitude" -> info.ballisticLateralAmplitude = ((Number) entry.getValue()).doubleValue();
                case "LateralWaves" -> info.ballisticLateralWaves = ((Number) entry.getValue()).doubleValue();
                case "LateralPhaseDeg" -> info.ballisticLateralPhaseDeg = ((Number) entry.getValue()).doubleValue();
                case "LateralStartRatio" -> info.ballisticLateralStartRatio = ((Number) entry.getValue()).doubleValue();
                case "LateralEndRatio" -> info.ballisticLateralEndRatio = ((Number) entry.getValue()).doubleValue();
                case "TerminalNoWeaveDist" -> info.ballisticTerminalNoWeaveDist = ((Number) entry.getValue()).doubleValue();
                case "TerminalCylinderRadius" -> info.ballisticTerminalCylinderRadius = ((Number) entry.getValue()).doubleValue();
                case "Canister" -> info.canister = ((Number) entry.getValue()).intValue();
                case "CanisterType" -> info.canisterType = ((Number) entry.getValue()).intValue();
                case "DragInAir" -> info.dragInAir = ((Number) entry.getValue()).doubleValue();
                default -> logUnknownEntry(entry, "Ballistic");
            }
        }
    }

    private static void parseArm(WeaponInfo info, Map<String, Object> map) {
        for (Map.Entry<String, Object> entry : map.entrySet()) {
            switch (entry.getKey()) {
                case "AntiRadiationMissile" -> info.antiRadiationMissile = (Boolean) entry.getValue();
                case "EmitterLostGraceTick" -> info.armEmitterLostGraceTick = ((Number) entry.getValue()).intValue();
                case "MemoryTimeTick" -> info.armMemoryTimeTick = ((Number) entry.getValue()).intValue();
                case "CruiseEnable" -> info.armCruiseEnable = (Boolean) entry.getValue();
                case "CruiseStartDistance" -> info.armCruiseStartDistance = ((Number) entry.getValue()).doubleValue();
                case "CruiseTerminalRadius" -> info.armCruiseTerminalRadius = ((Number) entry.getValue()).doubleValue();
                case "CruiseTerminalHeight" -> info.armCruiseTerminalHeight = ((Number) entry.getValue()).doubleValue();
                default -> logUnknownEntry(entry, "ARM");
            }
        }
    }

    private static void parseSignature(WeaponInfo info, Map<String, Object> map) {
        for (Map.Entry<String, Object> entry : map.entrySet()) {
            switch (entry.getKey()) {
                case "Front" -> info.rcsFrontFactor = ((Number) entry.getValue()).floatValue();
                case "Side" -> info.rcsSideFactor = ((Number) entry.getValue()).floatValue();
                case "Rear" -> info.rcsRearFactor = ((Number) entry.getValue()).floatValue();
                case "Time" -> info.rcsTimeFactor = ((Number) entry.getValue()).floatValue();
                default -> logUnknownEntry(entry, "Signature");
            }
        }
    }

    private static void parseEffects(WeaponInfo info, Map<String, Object> map) {
        for (Map.Entry<String, Object> entry : map.entrySet()) {
            switch (entry.getKey()) {
                case "PotionEffects" -> parsePotionEffects(info, (List<Map<String, Object>>) entry.getValue());
                case "BulletDecay" -> parseBulletDecay(info, (Map<String, Object>) entry.getValue());
                default -> logUnknownEntry(entry, "Effects");
            }
        }
    }

    private static void parsePotionEffects(WeaponInfo info, List<Map<String, Object>> effects) {
        for (Map<String, Object> raw : effects) {
            String potionName = null;
            int duration = 0;
            int amplifier = 0;
            int startDist = 0;
            int endDist = Integer.MAX_VALUE;
            for (Map.Entry<String, Object> entry : raw.entrySet()) {
                switch (entry.getKey()) {
                    case "Potion", "Name" -> potionName = ((String) entry.getValue()).trim();
                    case "Duration" -> duration = ((Number) entry.getValue()).intValue();
                    case "Amplifier" -> amplifier = ((Number) entry.getValue()).intValue();
                    case "StartDist" -> startDist = ((Number) entry.getValue()).intValue();
                    case "EndDist" -> endDist = ((Number) entry.getValue()).intValue();
                    default -> logUnknownEntry(entry, "PotionEffect");
                }
            }
            if (potionName == null) {
                continue;
            }
            // Kept as an ID; it is checked against the effect registry when the weapon hits (M5).
            String effectId = potionName.toLowerCase(Locale.ROOT);
            if (effectId.indexOf(':') < 0) {
                effectId = "minecraft:" + effectId;
            }
            info.potionEffect.add(new PotionEffectInfo(effectId, duration, amplifier, startDist, endDist));
        }
    }

    private static void parseBulletDecay(WeaponInfo info, Map<String, Object> map) {
        String type = (String) map.getOrDefault("Type", "Segmented");
        if ("Segmented".equalsIgnoreCase(type) && map.get("Segments") instanceof List<?> segmentsRaw) {
            List<BulletDecay.Segmented.DecaySegment> segments = new ArrayList<>();
            for (Object raw : segmentsRaw) {
                if (!(raw instanceof List<?> pair) || pair.size() < 2) continue;
                segments.add(new BulletDecay.Segmented.DecaySegment(((Number) pair.get(0)).floatValue(), ((Number) pair.get(1)).floatValue()));
            }
            info.bulletDecay.add(new BulletDecay.Segmented(segments));
            info.enableBulletDecay = true;
        }
    }

    private static void parseBulletModelEnd(WeaponInfo info, Object value) {
        if (value instanceof String s) {
            info.bulletModelNameEnd = s.trim().toLowerCase(Locale.ROOT);
            return;
        }
        if (value instanceof Map<?, ?> rawMap) {
            Map<String, Object> map = (Map<String, Object>) rawMap;
            if (map.containsKey("Name")) {
                info.bulletModelNameEnd = ((String) map.get("Name")).trim().toLowerCase(Locale.ROOT);
            }
            if (map.containsKey("EndTick")) {
                info.bulletModelEndTick = ((Number) map.get("EndTick")).intValue();
            }
        }
    }
}
