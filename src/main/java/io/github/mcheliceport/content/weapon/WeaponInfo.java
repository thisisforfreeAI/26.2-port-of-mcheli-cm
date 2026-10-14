package io.github.mcheliceport.content.weapon;

import io.github.mcheliceport.content.BaseInfo;
import io.github.mcheliceport.content.ContentLocation;
import io.github.mcheliceport.content.sound.SoundDefinitions;
import io.github.mcheliceport.content.weapon.hbm.ChemicalContainer;
import io.github.mcheliceport.content.weapon.hbm.FireContainer;
import io.github.mcheliceport.content.weapon.hbm.MistContainer;
import io.github.mcheliceport.content.weapon.hbm.MukeContainer;
import io.github.mcheliceport.content.weapon.hbm.NTSettingContainer;
import io.github.mcheliceport.content.weapon.hbm.VNTSettingContainer;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Random;
import org.jspecify.annotations.Nullable;

/**
 * Weapon definition: guns, rockets, missiles, bombs, torpedoes and the other weapon types.
 *
 * <p>Ported from upstream {@code com.norwood.mcheli.weapon.MCH_WeaponInfo}. Field names and defaults
 * are upstream's. Sounds, the dispensed item and the bullet models are stored by name; they are
 * resolved when weapons are ported (M5). Sound names are paths in the {@code mcheli} namespace.
 */
public class WeaponInfo extends BaseInfo {

    private static final Random RAND = new Random();

    public final String name;
    // HBM:NTM payloads, see the hbm package.
    public boolean useHBM = false;
    public Payload payloadNTM = Payload.NONE;
    public boolean effectOnly = false;
    public @Nullable VNTSettingContainer vntSettingContainer = null;
    public @Nullable ChemicalContainer chemicalContainer = null;
    public @Nullable MistContainer mistContainer = null;
    public @Nullable MukeContainer mukeContainer = null;
    public @Nullable NTSettingContainer ntSettingContainer = null;
    public @Nullable FireContainer fireContainer = null;
    public @Nullable String explosionType;
    public String displayName;
    public String type = "";
    public int power = 0;
    public float acceleration = 1.0F;
    public float accelerationInWater = 1.0F;
    public int explosion = 0;
    public int explosionBlock = -1;
    public float explosionRadius = 0.0F;
    public float explosionDamageRadius = 0.0F;
    public int explosionInWater = 0;
    public int explosionAltitude = 0;
    public int delayFuse = 0;
    public float bound;
    public int timeFuse = 0;
    public boolean flaming = false;
    public SightType sight = SightType.NONE;
    public float[] zoom = new float[] {1.0F};
    public int delay = 10;
    public int reloadTime = 30;
    public int round = 0;
    public int suppliedNum = 1;
    public int maxAmmo = 0;
    public List<RoundItem> roundItems = new ArrayList<>();
    public int soundDelay = 0;
    public float soundVolume = 1.0F;
    public float soundPitch = 1.0F;
    public float soundPitchRandom = 0.1F;
    public int soundPattern = 0;
    public int lockTime = 30;
    public boolean ridableOnly = false;
    public float proximityFuseDist = 0.0F;
    public int rigidityTime = 7;
    public float accuracy = 0.0F;
    public int bomblet = 0;
    public int bombletSTime = 10;
    public float bombletDiff = 0.3F;
    public int modeNum = 0;
    public int fixMode = 0;
    public int piercing = 0;
    public int heatCount = 0;
    public int maxHeatCount = 0;
    public boolean isFAE = false;
    public boolean isGuidedTorpedo = false;
    public float gravity = 0.0F;
    public float gravityInWater = 0.0F;
    public float velocityInWater = 0.999F;
    public boolean destruct = false;
    public String trajectoryParticleName = "explode";
    public int trajectoryParticleStartTick = 0;
    public int trajectoryParticleEndTick = -1;
    public boolean disableSmoke = false;
    public @Nullable CartridgeInfo cartridge = null;
    public ArgbColor color = new ArgbColor();
    public ArgbColor colorInWater = new ArgbColor();
    public float smokeSize = 2.0F;
    public int smokeNum = 1;
    public int smokeMaxAge = 100;
    /** Item a dispenser weapon places or uses, as a 1.12 item name. */
    public @Nullable String dispenseItemLoc = null;
    public int dispenseDamege = 0;
    public int dispenseRange = 1;
    public int recoilBufCount = 2;
    public int recoilBufCountSpeed = 3;
    public float length = 0.0F;
    public float radius = 0.0F;
    public float angle;
    public boolean displayMortarDistance = false;
    public boolean fixCameraPitch = false;
    public float cameraRotationSpeedPitch = 1.0F;
    /** Bit set of target kinds: 64 blocks, 32 planes, 16 helicopters, 8 vehicles and tanks, 4 players, 2 monsters, 1 others. */
    public int target = 1;
    public int markTime;
    public float recoil = 0.0F;
    public String bulletModelName = "";
    public int bulletModelEndTick = -1;
    public String bulletModelNameEnd = "";
    public String bombletModelName = "";
    public @Nullable DamageFactor damageFactor = null;
    public String group = "";
    public @Nullable List<MuzzleFlash> listMuzzleFlash = null;
    public @Nullable List<MuzzleFlash> listMuzzleFlashSmoke = null;
    public String fireSound;
    public @Nullable String hitSound;
    public String hitSoundIron = "hit_metal";
    public String railgunSound = "railgun";
    public @Nullable String weaponSwitchSound = null;
    /** Block break particle count. */
    public int flakParticlesCrack = 10;
    /** White smoke particle count. */
    public int numParticlesFlak = 3;
    /** Block break particle spread (0.1 rifle to 0.6 anti-tank). */
    public float flakParticlesDiff = 0.3F;
    /** Infrared missile: affected by flares. */
    public boolean isHeatSeekerMissile = true;
    /** Radar missile: affected by chaff. */
    public boolean isRadarMissile = false;
    /** Maximum seeker tracking angle. */
    public int maxDegreeOfMissile = 60;
    public int initMaxDegreeTick = 0;
    public int initMaxDegreeOfMissile = 60;
    /** Ticks until the lock is lost; -1 never. */
    public int tickEndHoming = -1;
    public int maxLockOnRange = 300;
    public int maxLockOnAngle = 10;
    /** Pulse-Doppler / rear-aspect maximum angle. */
    public float pdHDNMaxDegree = 1000f;
    public int pdHDNMaxDegreeLockOutCount = 10;
    /** Jamming resistance duration; -1 to disable. */
    public int antiFlareCount = -1;
    /** Multipath clutter detection height. */
    public int lockMinHeight = 25;
    public boolean passiveRadar = false;
    public boolean semiActiveRadar = false;
    public int passiveRadarLockOutCount = 20;
    public boolean laserGuidance = false;
    public boolean hasLaserGuidancePod = true;
    public boolean enableOffAxis = true;
    /** Missile manoeuvrability; 1.0 is upstream McHeli's, 0.1 recommended. */
    public double turningFactor = 0.5;
    public int initTurningFactorTick = 0;
    public double initTurningFactor = 0.5;
    public boolean enableChunkLoader = false;
    /** Active radar missile: tracks on its own after launch. */
    public boolean activeRadar = false;
    public int scanInterval = 20;
    public int weaponSwitchCount = 0;
    public float recoilPitch = 0.0F;
    public float recoilYaw = 0.0F;
    public float recoilPitchRange = 0.0F;
    public float recoilYawRange = 0.0F;
    public float recoilRecoverFactor = 0.8F;
    /** Velocity change per tick; positive accelerates. */
    public float speedFactor = 0F;
    public int speedFactorStartTick = 0;
    public int speedFactorEndTick = 0;
    /** Add the launching vehicle's velocity to the projectile's. */
    public boolean speedDependsAircraft = false;
    public boolean canLockMissile = false;
    public boolean canLockInWater = false;
    public boolean enableBVR = false;
    public int minRangeBVR = 300;
    public boolean predictTargetPos = true;
    /** Chaff locks before the missile goes unguided. */
    public int numLockedChaffMax = 2;
    public float explosionDamageVsLiving = 1f;
    public float explosionDamageVsPlayer = 1f;
    public float explosionDamageVsPlane = 1f;
    public float explosionDamageVsVehicle = 1f;
    public float explosionDamageVsTank = 1f;
    public float explosionDamageVsHeli = 1f;
    public float explosionDamageVsShip = 1f;
    public boolean explosionThroughWall = false;
    public float explosionThroughWallFactor = 1.0f;
    public boolean isNewExplosionBreak = false;
    public boolean disableDestroyBlock = true;
    public boolean canBeIntercepted = false;
    public boolean canAirburst = false;
    public int explosionAirburst = 0;
    /** Continuously computed impact point display. */
    public boolean ccip = false;
    public String ccipTexture = "CCIP";
    public float ccipFactor = 1.0F;
    /** Crosshair variant for custom HUDs. */
    public int crossType = 0;
    public boolean hasMortarRadar = false;
    public double mortarRadarMaxDist = -1;
    public int markerRocketSpawnNum = 5;
    public int markerRocketSpawnDiff = 15;
    public int markerRocketSpawnHeight = 200;
    public int markerRocketSpawnSpeed = 5;
    public int hitSoundRange = 100;
    public boolean enableExhaustFlare = false;
    public boolean spawnBulletInAir = false;
    public int spawnBulletMaxNum = 1;
    public int spawnBulletIntervalTick = 20;
    public int spawnBulletPerNum = 1;
    public boolean spawnBulletInheritSpeed = false;
    public boolean destructAfterSpawnBullet = false;
    public boolean ahead = false;
    public int aheadSolveIntervalTick = 2;
    public List<BulletDecay> bulletDecay = new ArrayList<>();
    public boolean enableBulletDecay = false;
    public List<PotionEffectInfo> potionEffect = new ArrayList<>();
    public boolean isGPSMissile = false;
    public boolean ballisticMissile = false;
    public double ballisticArcFactor = 0.20D;
    public double ballisticArcMinHeight = 20.0D;
    public double ballisticArcMaxHeight = 400.0D;
    public double ballisticMinDistance = 80.0D;
    public boolean ballisticLateralSine = false;
    public double ballisticLateralAmplitude = 12.0D;
    public double ballisticLateralWaves = 1.5D;
    public double ballisticLateralPhaseDeg = 0.0D;
    public double ballisticLateralStartRatio = 0.20D;
    public double ballisticLateralEndRatio = 0.85D;
    public double ballisticTerminalNoWeaveDist = 80.0D;
    public double ballisticTerminalCylinderRadius = 40.0D;
    public int canister = -1;
    public int canisterType = 0;
    public double dragInAir = 0.0D;
    public int buckshotCount = 6;
    public BuckshotPayload buckshotPayload = BuckshotPayload.BULLET;
    public boolean lockEntity = false;
    public boolean cameraFollowLockEntity = false;
    public float cameraFollowStrength = 0.3f;
    public boolean antiRadiationMissile = false;
    public int armEmitterLostGraceTick = 10;
    public int armMemoryTimeTick = 100;
    public boolean armCruiseEnable = false;
    public double armCruiseStartDistance = 150.0D;
    public double armCruiseTerminalRadius = 50.0D;
    public double armCruiseTerminalHeight = 1024.0D;
    public boolean enableDataLink = false;
    public boolean onlyDataLink = false;
    public boolean enableHMS = true;
    public String nameOnRWR = "MSL";
    public float rcsFrontFactor = 1.0F;
    public float rcsSideFactor = 1.0F;
    public float rcsRearFactor = 1.0F;
    public float rcsTimeFactor = 1.0F;
    public int proximityFuseTick = -1;
    public float proximityFuseDamage = 0.0F;
    public int proximityFuseHeight = 20;
    public MineFuze mineFuze = MineFuze.NONE;
    public float mineRange = 4.0F;
    public int mineArmDelay = 40;

    public WeaponInfo(ContentLocation location, String path, String parserIdentifier) {
        super(location, path, parserIdentifier);
        this.name = location.path();
        this.displayName = this.name;
        this.fireSound = this.name + SoundDefinitions.SND;
    }

    @Override
    public boolean validate() {
        if (this.explosionBlock < 0) {
            this.explosionBlock = this.explosion;
        }
        if (this.explosionRadius <= 0.0F) {
            this.explosionRadius = this.explosion;
        }
        if (this.explosionAirburst <= 0) {
            this.explosionAirburst = this.explosion;
        }
        if (this.fixMode >= this.modeNum) {
            this.fixMode = 0;
        }
        if (this.round <= 0) {
            this.round = this.maxAmmo;
        }
        if (this.round > this.maxAmmo) {
            this.round = this.maxAmmo;
        }
        if (this.explosion <= 0) {
            this.isFAE = false;
        }
        if (this.delayFuse <= 0) {
            this.bound = 0.0F;
        }
        if (this.isFAE) {
            this.explosionInWater = 0;
        }
        if (this.bomblet > 0 && this.bombletSTime < 1) {
            this.bombletSTime = 1;
        }
        if (this.destruct) {
            this.delay = 1000000;
        }
        if (this.spawnBulletIntervalTick < 1) {
            this.spawnBulletIntervalTick = 1;
        }
        if (this.aheadSolveIntervalTick < 1) {
            this.aheadSolveIntervalTick = 1;
        }
        if (this.trajectoryParticleEndTick >= 0 && this.trajectoryParticleEndTick < this.trajectoryParticleStartTick) {
            this.trajectoryParticleEndTick = this.trajectoryParticleStartTick;
        }
        if (this.bulletModelEndTick < -1) {
            this.bulletModelEndTick = -1;
        }
        if (this.mineFuze != MineFuze.NONE) {
            if (this.mineRange <= 0.0F) {
                this.mineRange = 4.0F;
            }
            if (this.mineArmDelay < 0) {
                this.mineArmDelay = 0;
            }
        }

        this.ballisticArcFactor = Math.max(0.0D, this.ballisticArcFactor);
        this.ballisticArcMinHeight = Math.max(0.0D, this.ballisticArcMinHeight);
        this.ballisticArcMaxHeight = Math.max(this.ballisticArcMinHeight, this.ballisticArcMaxHeight);
        this.ballisticMinDistance = Math.max(0.0D, this.ballisticMinDistance);
        this.ballisticLateralAmplitude = Math.max(0.0D, this.ballisticLateralAmplitude);
        this.ballisticLateralWaves = Math.max(0.0D, this.ballisticLateralWaves);
        this.ballisticLateralStartRatio = Math.max(0.0D, Math.min(1.0D, this.ballisticLateralStartRatio));
        this.ballisticLateralEndRatio = Math.max(this.ballisticLateralStartRatio,
                Math.min(1.0D, this.ballisticLateralEndRatio));
        this.ballisticTerminalNoWeaveDist = Math.max(0.0D, this.ballisticTerminalNoWeaveDist);
        this.ballisticTerminalCylinderRadius = Math.max(0.0D, this.ballisticTerminalCylinderRadius);
        this.armCruiseStartDistance = Math.max(0.0D, this.armCruiseStartDistance);
        this.armCruiseTerminalRadius = Math.max(0.0D, this.armCruiseTerminalRadius);
        this.armCruiseTerminalHeight = Math.max(0.0D, this.armCruiseTerminalHeight);

        if (!this.enableDataLink) {
            this.onlyDataLink = false;
        }
        if (!isCCIPSupportedType(this.type)) {
            this.ccip = false;
        }

        this.angle = (float) (Math.atan2(this.radius, this.length) * 180.0D / Math.PI);

        SoundDefinitions.parseSound(this.fireSound);
        if (this.hitSound != null) {
            SoundDefinitions.parseSound(this.hitSound);
        }
        SoundDefinitions.parseSound(this.hitSoundIron);
        SoundDefinitions.parseSound(this.railgunSound);
        if (this.weaponSwitchSound != null) {
            SoundDefinitions.parseSound(this.weaponSwitchSound);
        }
        return true;
    }

    /** Damage multiplier against the given target (see {@link DamageFactor}); 1 if none is set. */
    public float getDamageFactor(@Nullable String target) {
        return this.damageFactor != null ? this.damageFactor.getDamageFactor(target) : 1.0F;
    }

    /** True if this bomb is a persistent proximity mine. */
    public boolean isMine() {
        return this.mineFuze != MineFuze.NONE;
    }

    /** Display name of the weapon type, as shown in tooltips. */
    public String getWeaponTypeName() {
        return switch (this.type.toLowerCase(Locale.ROOT)) {
            case "machinegun1", "machinegun2", "railgun" -> "MachineGun";
            case "torpedo" -> "Torpedo";
            case "cas" -> "CAS";
            case "rocket" -> "Rocket";
            case "buckshot" -> "Buckshot";
            case "asmissile" -> "AS Missile";
            case "aamissile" -> "AA Missile";
            case "tvmissile" -> "TV Missile";
            case "atmissile" -> "AT Missile";
            case "bomb" -> "Bomb";
            case "mkrocket" -> "Mk Rocket";
            case "dummy" -> "Dummy";
            case "smoke" -> "Smoke";
            case "dispenser" -> "Dispenser";
            case "targetingpod" -> "Targeting Pod";
            default -> "";
        };
    }

    public float getRecoilPitch() {
        return this.recoilPitch + (RAND.nextFloat() * this.recoilPitchRange);
    }

    public float getRecoilYaw() {
        return this.recoilYaw + ((RAND.nextFloat() - 0.5F) * this.recoilYawRange);
    }

    public int getCurrentMaxDegree(int missileTick) {
        if (this.initMaxDegreeTick > 0 && missileTick >= 0 && missileTick <= this.initMaxDegreeTick) {
            return this.initMaxDegreeOfMissile;
        }
        return this.maxDegreeOfMissile;
    }

    public double getCurrentTurningFactor(int missileTick) {
        if (this.initTurningFactorTick > 0 && missileTick >= 0 && missileTick <= this.initTurningFactorTick) {
            return this.initTurningFactor;
        }
        return this.turningFactor;
    }

    public static boolean isCCIPSupportedType(@Nullable String type) {
        if (type == null) {
            return false;
        }
        return type.equalsIgnoreCase("rocket") || type.equalsIgnoreCase("atmissile")
                || type.equalsIgnoreCase("tvmissile");
    }

    @Override
    public String toString() {
        return "WeaponInfo{name='" + this.name + "', type='" + this.type + "', location=" + this.location + '}';
    }

    public enum Payload {
        NONE,
        NTM_VNT,
        NTM_NT,
        NTM_MINI_NUKE,
        NTM_NUKE,
        NTM_CHEMICAL,
        NTM_MIST,
        NTM_FIRE
    }

    /** Which entity each buckshot pellet spawns as. */
    public enum BuckshotPayload {
        BULLET,
        ROCKET
    }

    /**
     * An item that reloads this weapon.
     *
     * @param num      how many rounds one item gives
     * @param itemName 1.12 item name, with the namespace ({@code minecraft:} if the file left it out)
     * @param damage   1.12 item metadata
     */
    public record RoundItem(int num, String itemName, int damage) {

        /** Normalises a 1.12 item name as 1.12's {@code ResourceLocation} did: default namespace, lower case. */
        public static RoundItem of(int num, String name, int damage) {
            String lower = name.toLowerCase(Locale.ROOT);
            return new RoundItem(num, lower.indexOf(':') >= 0 ? lower : "minecraft:" + lower, damage);
        }
    }

    /** One muzzle flash or muzzle smoke puff. */
    public static class MuzzleFlash {

        public final float dist;
        public final float size;
        public final float range;
        public final int age;
        public final float a;
        public final float r;
        public final float g;
        public final float b;
        public final int num;

        public MuzzleFlash(float dist, float size, float range, int age, float a, float r, float g, float b, int num) {
            this.dist = dist;
            this.size = size;
            this.range = range;
            this.age = age;
            this.a = a;
            this.r = r;
            this.g = g;
            this.b = b;
            this.num = num;
        }

        /** Upstream {@code MuzzleFlash(MuzzleFlashRaw)}: the colour as an ARGB integer. */
        public static MuzzleFlash fromArgb(float dist, float size, float range, int age, int argb, int num) {
            return new MuzzleFlash(dist, size, range, age, ((argb >> 24) & 0xFF) / 255.0F,
                    ((argb >> 16) & 0xFF) / 255.0F, ((argb >> 8) & 0xFF) / 255.0F, (argb & 0xFF) / 255.0F, num);
        }
    }
}
