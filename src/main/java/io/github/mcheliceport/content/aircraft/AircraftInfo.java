package io.github.mcheliceport.content.aircraft;

import io.github.mcheliceport.content.BaseInfo;
import io.github.mcheliceport.content.ContentLocation;
import io.github.mcheliceport.content.hud.Hud;
import io.github.mcheliceport.damage.ComponentKind;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

/**
 * Shared definition of every vehicle type: helicopters, planes, ships, tanks and vehicles.
 *
 * <p>Ported from upstream {@code com.norwood.mcheli.aircraft.MCH_AircraftInfo}. Field names and
 * defaults are upstream's, so the parsers and the YAML emitter port one to one. Differences:
 * <ul>
 *   <li>The definition holds no Minecraft registry objects or models. Upstream's item, recipe and
 *       model fields are gone; items are looked up by content name and models are loaded by the
 *       client from the model names stored here.</li>
 *   <li>HUDs are stored by name ({@link #hudList}, {@link #hudTvMissile}), so both sides hold the
 *       same data. Upstream stored HUD objects, and only on the client.</li>
 *   <li>Upstream's {@code validate()} for helicopters, planes, ships and tanks multiplied
 *       {@link #speed} by the {@code All*Speed} config values. The port applies those multipliers
 *       where the speed is used, so the stored value is always the one from the file.</li>
 * </ul>
 */
public abstract class AircraftInfo extends BaseInfo {

    /**
     * Vertical offset upstream adds to camera and weapon positions ({@code W_Entity.GLOBAL_Y_OFFSET}).
     */
    public static final float GLOBAL_Y_OFFSET = 0.35F;

    public final String name;
    public final List<WeaponSet> weaponSetList = new ArrayList<>();
    public final List<SeatInfo> seatList = new ArrayList<>();
    /** Pairs of seat indices (0-based, racks after seats) that cannot be occupied at the same time. */
    public final List<Integer[]> exclusionSeatList = new ArrayList<>();
    /** HUD names, one per seat; {@link Hud#NONE_NAME} for no HUD. */
    public final List<String> hudList = new ArrayList<>();
    public final List<BoundingBoxInfo> extraBoundingBox = new ArrayList<>();
    public final List<Wheel> wheels = this.getDefaultWheelList();
    public final List<SeatRackInfo> entityRackList = new ArrayList<>();
    public final MobDropOption mobDropOption = new MobDropOption();
    public final List<RepellingHook> repellingHooks = new ArrayList<>();
    public final List<RideRack> rideRacks = new ArrayList<>();
    public final List<ParticleSplash> particleSplashs = new ArrayList<>();
    public final List<SearchLight> searchLights = new ArrayList<>();
    public final List<CameraPosition> cameraPosition = new ArrayList<>();
    public final List<Hatch> hatchList = new ArrayList<>();
    public final List<Camera> cameraList = new ArrayList<>();
    public final List<PartWeapon> partWeapon = new ArrayList<>();
    public final List<WeaponBay> partWeaponBay = new ArrayList<>();
    public final List<WeaponBay> partTurretWeaponBay = new ArrayList<>();
    public final List<Canopy> canopyList = new ArrayList<>();
    public final List<LandingGear> landingGear = new ArrayList<>();
    public final List<Throttle> partThrottle = new ArrayList<>();
    public final List<RotPart> partRotPart = new ArrayList<>();
    public final List<TurretRotPart> partTurretRotPart = new ArrayList<>();
    public final List<CrawlerTrack> partCrawlerTrack = new ArrayList<>();
    public final List<TrackRoller> partTrackRoller = new ArrayList<>();
    public final List<PartWheel> partWheel = new ArrayList<>();
    public final List<PartWheel> partSteeringWheel = new ArrayList<>();
    public final List<Hatch> lightHatchList = new ArrayList<>();
    private final List<String> textureNameList = new ArrayList<>();
    public Flare flare = new Flare();
    public @Nullable Chaff chaff = null;
    public String displayName;
    /** Display names by language code (for example {@code ja_jp}), from {@code AddDisplayName}. */
    public HashMap<String, String> displayNameLang = new HashMap<>();
    public int itemID = 0;
    /** Recipe lines from {@code AddRecipe}/{@code AddShapelessRecipe}, in the legacy TXT syntax. */
    public List<String> recipeString = new ArrayList<>();
    public boolean isShapedRecipe = true;
    public String category = "zzz";
    public boolean creativeOnly = false;
    public boolean invulnerable = false;
    public boolean isEnableGunnerMode = false;
    public int cameraZoom = this.getDefaultMaxZoom();
    public boolean isEnableConcurrentGunnerMode = false;
    public boolean isEnableNightVision = false;
    public boolean isEnableEntityRadar = false;
    public boolean isEnableEjectionSeat = false;
    public boolean isEnableParachuting = false;
    public float bodyHeight = 0.7F;
    public float bodyWidth = 2.0F;
    public boolean isFloat = false;
    public float floatOffset = 0.0F;
    public float gravity = -0.04F;
    public float gravityInWater = -0.04F;
    public int maxHp = 50;
    public float armorMinDamage = 0.0F;
    public float armorMaxDamage = 100000.0F;
    public float armorDamageFactor = 1.0F;
    public boolean enableBack = false;
    public int inventorySize = 0;
    public boolean isUAV = false;
    public boolean isSmallUAV = false;
    public boolean isNewUAV;
    public boolean isTargetDrone = false;
    /**
     * UAV control range in blocks; beyond it the control link drops. {@code -1} (default) uses the
     * global Wingman controller range.
     */
    public int uavRange = -1;
    public float autoPilotRot = -0.6F;
    public float onGroundPitch = 0.0F;
    public boolean canMoveOnGround = true;
    public boolean canRotOnGround = true;
    /** Name of the HUD shown while steering a TV missile; defaults to {@code tv_missile}. */
    public @Nullable String hudTvMissile = null;
    public float damageFactor = 0.2F;
    public float submergedDamageHeight = 0.0F;
    public boolean regeneration = false;
    public int maxFuel = 0;
    public float fuelConsumption = 1.0F;
    public float fuelSupplyRange = 0.0F;
    public String fuelSupplyType = "mch_fuel";
    public float ammoSupplyRange = 0.0F;
    /**
     * M10c: rounds a supply vehicle carries, 0 for upstream's endless supply. A vehicle with a stock
     * rearms one weapon of each vehicle in range per second, the emptiest first, and restocks on a
     * repair pad.
     */
    public int ammoSupplyStock = 0;
    public float repairOtherVehiclesRange = 0.0F;
    public int repairOtherVehiclesValue = 10;
    public float stealth = 0.0F;
    public boolean canRide = true;
    public float entityWidth = 1.0F;
    public float entityHeight = 1.0F;
    public float entityPitch = 0.0F;
    public float entityRoll = 0.0F;
    public float stepHeight = this.getDefaultStepHeight();
    public int mobSeatNum = 0;
    public int entityRackNum = 0;
    public float rotorSpeed = this.getDefaultRotorSpeed();
    public boolean enableSeaSurfaceParticle = false;
    public float pivotTurnThrottle = 0.0F;
    public float trackRollerRot = 30.0F;
    public float partWheelRot = 30.0F;
    public float onGroundPitchFactor = 0.0F;
    public float onGroundRollFactor = 0.0F;
    public Vec3 turretPosition = new Vec3(0.0, 0.0, 0.0);
    public boolean defaultFreelook = false;
    public @Nullable Vec3 unmountPosition = null;
    public float thirdPersonDist = 4.0F;
    public float markerWidth = 2.0F;
    public float markerHeight = 1.0F;
    public float bbZmin = -1.0F;
    public float bbZmax = 1.0F;
    public float bbZ;
    public boolean alwaysCameraView = false;
    public float cameraRotationSpeed = 100.0F;
    public float speed = 0.1F;
    public float motionFactor = 0.96F;
    public float mobilityYaw = 1.0F;
    public float mobilityPitch = 1.0F;
    public float mobilityRoll = 1.0F;
    public float mobilityYawOnGround = 1.0F;
    public float minRotationPitch = this.getMinRotationPitch();
    public float maxRotationPitch = this.getMaxRotationPitch();
    // Upstream initialises the roll limits from the pitch limits too.
    public float minRotationRoll = this.getMinRotationPitch();
    public float maxRotationRoll = this.getMaxRotationPitch();
    public boolean limitRotation = false;
    public float throttleUpDown = 1.0F;
    public float throttleUpDownOnEntity = 2.0F;
    /** Round-robin index into the texture list for newly placed vehicles. */
    public int textureCount = 0;
    public float particlesScale = 1.0F;
    /** Optional cosmetic engine outlets, in model coordinates; absent outlets are never guessed. */
    public final List<EngineOutlet> engineOutlets = new ArrayList<>();
    /** Authored visual lamps. Positions share their named model part's transforms. */
    public final List<FlightLamp> flightLamps = new ArrayList<>();
    /** Optional cosmetic Mach-one mapping in unscaled game blocks/tick; zero disables it. */
    public float machOneSpeed=0;
    /** M10b: guns reload slower (1.4x) while no loader seat is manned; only for vehicles with a loader seat. */
    public boolean loaderRequired = false;
    /** Addon-placed component zones (M10a); when present they replace the derived M9 zones. */
    public final List<DamageZoneSpec> damageZones = new ArrayList<>();
    public boolean hideEntity = false;
    public boolean smoothShading = true;
    /** Engine sound event name in the {@code mcheli} namespace, or null for the type's default. */
    public @Nullable String soundMove = null;
    public float soundVolume = 1.0F;
    public float soundPitch = 1.0F;
    public float soundRange = this.getDefaultSoundRange();
    public RadarType radarType = RadarType.EARLY_AA;
    public RwrType rwrType = RwrType.DIGITAL;
    public String nameOnModernAARadar = "";
    public String nameOnAdvancedAARadar = "";
    public String nameOnEarlyAARadar = "";
    public String nameOnModernASRadar = "";
    public String nameOnEarlyASRadar = "";
    /** Explosion radius when the vehicle is destroyed. */
    public float explosionSizeByCrash = 5;
    /** Reverse speed multiplier. */
    public float throttleDownFactor = 1;
    public int chaffUseTime = 100;
    public int chaffWaitTime = 400;
    public boolean enableMaintenance = false;
    /** Duration of the repair system; also the healed percentage. */
    public int maintenanceUseTime = 20;
    public int maintenanceWaitTime = 300;
    /** HP percentage below which the engine shuts down. */
    public int engineShutdownThreshold = 20;
    public int apsUseTime = 100;
    public int apsWaitTime = 400;
    public int apsRange = 8;
    public boolean hasAPS = false;
    public boolean hasRWR = false;
    public boolean enableRadar = false;
    public boolean passiveRadar = false;
    public float radarMaxTargetRange = 3000.0F;
    public float radarScanAzimuthDeg = 120.0F;
    public float radarPanelFillAlpha = 0.30F;
    public boolean radarFollowTurretYaw = false;
    public float radarScanElevationDeg = 40.0F;
    public int radarScanTick = 12;
    public float radarDetectChanceBase = 0.75F;
    public float radarGainNearFactor = 1.5F;
    public float radarGainFarFactor = 0.5F;
    public float radarRcsFrontFactor = 1.0F;
    public float radarRcsSideFactor = 1.0F;
    public float radarRcsRearFactor = 1.0F;
    public float radarRcsTimeFactor = 1.0F;
    public int radarContactHoldTick = 40;
    public String radarElevationReference = "HORIZON";
    public String radarElevationCoverage = "UP_ONLY";
    public boolean enableBVR = false;
    public float radarMinScanAltitude = 10.0F;
    public float radarMaxScanAltitude = 25.0F;
    public String radarSearchType = "SRC";
    public float radarTrackAzimuthDeg = 90.0F;
    public float radarTrackElevationDeg = 45.0F;
    public int radarRetargetCooldownTick = 40;
    public String nameOnRWR = "?";
    public boolean enableECMJammer = false;
    public int ecmJammerWaitTime = 400;
    public int ecmJammerUseTime = 100;
    public int ecmJammerType = 0;
    /** HUD variant selector for custom HUDs. */
    public int hudType = 0;
    /** Weapon group selector for custom HUDs. */
    public int weaponGroupType = 0;
    /** Explosion damage multiplier. */
    public float armorExplosionDamageMultiplier = 1.0f;
    /** Immune to laser lock-on. */
    public boolean hasPhotoelectricJammer = false;
    public boolean hasDIRCM = false;
    public final List<Float> impactAngleThresholds = new ArrayList<>();
    public final List<Float> impactAngleCoefficients = new ArrayList<>();
    public float impactRicochetStartAngle = 89.0F;
    public final List<SignMarker> signMarkers = new ArrayList<>();
    /** Preview scale in The One Probe (integration not ported yet). */
    public float oneProbeScale = 1F;
    public boolean fuelSupplyInfinite = false;
    /**
     * True if the vehicle runs on electricity: {@link #maxFuel} and {@link #fuelConsumption} then
     * describe energy capacity and drain.
     */
    public boolean electric = false;
    /** With {@link #electric}, batteries in the fuel slot extend the vehicle's energy storage. */
    public boolean allowBattery = false;
    /** Accepted fuel fluids and their consumption multipliers. */
    protected Map<String, Float> fluidType = Map.of("mch_fuel", 1f);
    private String lastWeaponType = "";
    private int lastWeaponIndex = -1;
    private @Nullable PartWeapon lastWeaponPart = null;

    protected AircraftInfo(ContentLocation location, String path, String parserIdentifier) {
        super(location, path, parserIdentifier);
        this.name = location.path();
        this.displayName = this.name;
        this.textureNameList.add(this.name);
    }

    /** TXT keys that {@code /mcheli reconfig} cannot change without a restart. */
    public static String[] getCannotReloadItem() {
        return new String[] {
                "DisplayName", "AddDisplayName", "ItemID", "AddRecipe", "AddShapelessRecipe", "InventorySize",
                "Sound", "UAV", "SmallUAV", "TargetDrone", "Category"
        };
    }

    public Map<String, Float> getFluidType() {
        return this.fluidType;
    }

    public void setFluidType(Map<String, Float> fluidType) {
        this.fluidType = fluidType;
    }

    /** Whether the named fluid can fuel this vehicle. */
    public boolean isFuelValid(@Nullable String fluidName) {
        return fluidName != null && this.fluidType.containsKey(fluidName);
    }

    public float getFuelConsumption(String fluidName) {
        return this.fluidType.getOrDefault(fluidName, 1f);
    }

    /** Folder name of this vehicle type, for example {@code helicopters}. */
    public abstract String getDirectoryName();

    /** Singular name of this vehicle type, for example {@code helicopter}. */
    public abstract String getKindName();

    public float getDefaultSoundRange() {
        return 100.0F;
    }

    public List<Wheel> getDefaultWheelList() {
        return new ArrayList<>();
    }

    public float getDefaultRotorSpeed() {
        return 0.0F;
    }

    // Private, as upstream: subclasses cannot override the default (MCH_TankInfo's 0.6 never applied).
    private float getDefaultStepHeight() {
        return 0.0F;
    }

    public boolean haveRepellingHook() {
        return !this.repellingHooks.isEmpty();
    }

    public boolean haveFlare() {
        return this.flare.types.length > 0;
    }

    public boolean haveAPS() {
        return this.hasAPS;
    }

    public boolean haveMaintenance() {
        return this.maintenanceUseTime > 0;
    }

    public boolean haveECMJammer() {
        return this.enableECMJammer;
    }

    public boolean haveCanopy() {
        return !this.canopyList.isEmpty();
    }

    public boolean haveLandingGear() {
        return !this.landingGear.isEmpty();
    }

    /** Name of the HUD a seat uses when the file does not set one. */
    public abstract String getDefaultHudName(int seatId);

    @Override
    public boolean validate() throws Exception {
        this.ensureImpactDamageDefaults();

        if (this.cameraPosition.isEmpty()) {
            this.cameraPosition.add(new CameraPosition());
        }

        this.bbZ = (this.bbZmax + this.bbZmin) / 2.0F;
        if (this.isTargetDrone) {
            this.isUAV = true;
        }

        if (this.isEnableParachuting && !this.repellingHooks.isEmpty()) {
            this.isEnableParachuting = false;
            this.repellingHooks.clear();
        }

        if (this.isUAV) {
            this.alwaysCameraView = true;
            if (this.seatList.isEmpty()) {
                this.seatList.add(new SeatInfo(new Vec3(0.0, 0.0, 0.0), false));
            }
        }

        this.mobSeatNum = this.seatList.size();
        this.entityRackNum = this.entityRackList.size();
        if (this.getNumSeat() < 1) {
            throw new Exception("At least one seat must be set.");
        }

        if (this.getNumHud() < this.getNumSeat()) {
            for (int i = this.getNumHud(); i < this.getNumSeat(); i++) {
                this.hudList.add(this.getDefaultHudName(i));
            }
        }

        if (this.getNumSeat() == 1 && this.getNumHud() == 1) {
            this.hudList.add(this.getDefaultHudName(1));
        }

        this.seatList.addAll(this.entityRackList);
        this.entityRackList.clear();
        if (this.hudTvMissile == null) {
            this.hudTvMissile = "tv_missile";
        }

        if (this.textureNameList.isEmpty()) {
            throw new Exception("At least one texture must be set.");
        }

        resolveWeaponBays(this.partWeaponBay);
        resolveWeaponBays(this.partTurretWeaponBay);
        return true;
    }

    /**
     * Resolves weapon bay weapon names to weapon set indices and drops bays with no known weapon.
     * Like upstream, removing a bay skips the bay after it.
     */
    private void resolveWeaponBays(List<WeaponBay> bays) {
        for (int i = 0; i < bays.size(); i++) {
            WeaponBay wb = bays.get(i);
            String[] weaponNames = wb.weaponName.split("\\s*/\\s*");
            if (weaponNames.length == 0) {
                bays.remove(i);
            } else {
                List<Integer> list = new ArrayList<>();
                for (String s : weaponNames) {
                    int id = this.getWeaponIdByName(s);
                    if (id >= 0) {
                        list.add(id);
                    }
                }

                if (list.isEmpty()) {
                    bays.remove(i);
                } else {
                    bays.get(i).weaponIds = list.toArray(new Integer[0]);
                }
            }
        }
    }

    private void ensureImpactDamageDefaults() {
        if (this.impactAngleThresholds.isEmpty()) {
            this.impactAngleThresholds.add(0.0F);
            this.impactAngleCoefficients.add(1.0F);
            return;
        }
        if (this.impactAngleThresholds.get(0) > 0.0F) {
            float firstCoeff = this.impactAngleCoefficients.isEmpty() ? 1.0F : this.impactAngleCoefficients.get(0);
            this.impactAngleThresholds.add(0, 0.0F);
            this.impactAngleCoefficients.add(0, firstCoeff);
        }
    }

    public float getImpactDamageCoefficient(float angleDeg) {
        float absAngle = Math.abs(angleDeg);
        float coeff = this.impactAngleCoefficients.isEmpty() ? 1.0F : this.impactAngleCoefficients.get(0);
        for (int i = 1; i < this.impactAngleThresholds.size() && i < this.impactAngleCoefficients.size(); i++) {
            if (absAngle >= this.impactAngleThresholds.get(i)) {
                coeff = this.impactAngleCoefficients.get(i);
            }
        }
        return coeff;
    }

    public boolean shouldRicochetByImpactAngle(float angleDeg) {
        return Math.abs(angleDeg) >= this.impactRicochetStartAngle;
    }

    public int getInfo_MaxSeatNum() {
        return 30;
    }

    public int getNumSeatAndRack() {
        return this.seatList.size();
    }

    public int getNumSeat() {
        return this.mobSeatNum;
    }

    public int getNumRack() {
        return this.entityRackNum;
    }

    public int getNumHud() {
        return this.hudList.size();
    }

    public float getMaxSpeed() {
        return 0.8F;
    }

    public float getMinRotationPitch() {
        return -89.9F;
    }

    public float getMaxRotationPitch() {
        return 80.0F;
    }

    public float getMinRotationRoll() {
        return -80.0F;
    }

    public float getMaxRotationRoll() {
        return 80.0F;
    }

    public int getDefaultMaxZoom() {
        return 1;
    }

    public boolean haveHatch() {
        return !this.hatchList.isEmpty();
    }

    public boolean havePartCamera() {
        return !this.cameraList.isEmpty();
    }

    public boolean havePartThrottle() {
        return !this.partThrottle.isEmpty();
    }

    public @Nullable WeaponSet getWeaponSetById(int id) {
        return id >= 0 && id < this.weaponSetList.size() ? this.weaponSetList.get(id) : null;
    }

    public @Nullable Weapon getWeaponById(int id) {
        WeaponSet ws = this.getWeaponSetById(id);
        return ws != null ? ws.weapons.get(0) : null;
    }

    public int getWeaponIdByName(String s) {
        for (int i = 0; i < this.weaponSetList.size(); i++) {
            if (this.weaponSetList.get(i).type.equalsIgnoreCase(s)) {
                return i;
            }
        }
        return -1;
    }

    public @Nullable Weapon getWeaponByName(String s) {
        for (int i = 0; i < this.weaponSetList.size(); i++) {
            if (this.weaponSetList.get(i).type.equalsIgnoreCase(s)) {
                return this.getWeaponById(i);
            }
        }
        return null;
    }

    public void addTextureName(String name) {
        this.textureNameList.add(name);
    }

    /** All texture names, the default (the content name) first. */
    public List<String> getTextureNames() {
        return this.textureNameList;
    }

    /**
     * Returns the weapon set for {@code type}. Consecutive weapons of the same type share one set,
     * as in upstream; a weapon of another type in between starts a new set.
     */
    public WeaponSet getOrCreateWeaponSet(String type) {
        if (type.compareTo(this.lastWeaponType) != 0 || this.lastWeaponIndex < 0) {
            this.weaponSetList.add(new WeaponSet(type));
            this.lastWeaponIndex = this.weaponSetList.size() - 1;
            this.lastWeaponType = type;
        }
        return this.weaponSetList.get(this.lastWeaponIndex);
    }

    public @Nullable PartWeapon getLastWeaponPart() {
        return this.lastWeaponPart;
    }

    public void setLastWeaponPart(@Nullable PartWeapon part) {
        this.lastWeaponPart = part;
    }

    public int getWeaponCount() {
        return this.weaponSetList.size();
    }

    /**
     * Builds a crawler track from the TXT syntax {@code reverse, segmentLength, z, x/y, x/y, ...},
     * or returns null if fewer than four outline points are given.
     */
    public @Nullable CrawlerTrack createCrawlerTrack(String data, String name) {
        String[] s = this.splitParam(data);
        int pc = s.length - 3;
        boolean rev = this.toBool(s[0]);
        float len = this.toFloat(s[1], 0.001F, 1000.0F) * 0.9F;
        float z = this.toFloat(s[2]);
        if (pc < 4) {
            return null;
        }

        double[] cx = new double[pc];
        double[] cy = new double[pc];
        for (int i = 0; i < pc; i++) {
            int idx = !rev ? i : pc - i - 1;
            String[] xy = this.splitParamSlash(s[3 + idx]);
            cx[i] = this.toFloat(xy[0]);
            cy[i] = this.toFloat(xy[1]);
        }

        return buildCrawlerTrack(name, len, z, cx, cy);
    }

    /**
     * Lays out the track links along the closed outline {@code cx}/{@code cy}, as upstream's
     * {@code createCrawlerTrack}. {@code len} is the link spacing after upstream's 0.9 factor.
     */
    public static CrawlerTrack buildCrawlerTrack(String name, float len, float z, double[] cx, double[] cy) {
        int pc = cx.length;
        List<CrawlerTrackPrm> lp = new ArrayList<>();
        lp.add(new CrawlerTrackPrm((float) cx[0], (float) cy[0]));
        double dist = 0.0;

        for (int i = 0; i < pc; i++) {
            double x = cx[(i + 1) % pc] - cx[i];
            double y = cy[(i + 1) % pc] - cy[i];
            dist += Math.sqrt(x * x + y * y);
            double dist2 = dist;

            for (int j = 1; dist >= len; j++) {
                lp.add(new CrawlerTrackPrm((float) (cx[i] + x * (len * j / dist2)),
                        (float) (cy[i] + y * (len * j / dist2))));
                dist -= len;
            }
        }

        for (int i = 0; i < lp.size(); i++) {
            CrawlerTrackPrm pp = lp.get((i + lp.size() - 1) % lp.size());
            CrawlerTrackPrm cp = lp.get(i);
            CrawlerTrackPrm np = lp.get((i + 1) % lp.size());
            float pr = (float) (Math.atan2(pp.x - cp.x, pp.y - cp.y) * 180.0 / Math.PI);
            float nr = (float) (Math.atan2(np.x - cp.x, np.y - cp.y) * 180.0 / Math.PI);
            float ppr = (pr + 360.0F) % 360.0F;
            float nnr = nr + 180.0F;
            if ((nnr < ppr - 0.3 || nnr > ppr + 0.3) && nnr - ppr < 100.0F && nnr - ppr > -100.0F) {
                nnr = (nnr + ppr) / 2.0F;
            }
            cp.r = nnr;
        }

        CrawlerTrack c = new CrawlerTrack(name);
        c.len = len;
        c.cx = cx;
        c.cy = cy;
        c.lp = lp;
        c.z = z;
        c.side = z >= 0.0F ? 1 : 0;
        return c;
    }

    /** Returns the next texture in round-robin order, for a newly placed vehicle. */
    public String getTextureName() {
        String s = this.textureNameList.get(this.textureCount);
        this.textureCount = (this.textureCount + 1) % this.textureNameList.size();
        return s;
    }

    /** Returns the texture after {@code base}, wrapping around; {@code base} if there is only one. */
    public String getNextTextureName(String base) {
        if (this.textureNameList.size() >= 2) {
            for (int i = 0; i < this.textureNameList.size(); i++) {
                String s = this.textureNameList.get(i);
                if (s.equalsIgnoreCase(base)) {
                    i = (i + 1) % this.textureNameList.size();
                    return this.textureNameList.get(i);
                }
            }
        }
        return base;
    }

    @Override
    public boolean canReloadItem(String item) {
        for (String s : getCannotReloadItem()) {
            if (s.equalsIgnoreCase(item)) {
                return false;
            }
        }
        return true;
    }

    public boolean haveChaff() {
        return this.chaff != null;
    }

    @Override
    public String toString() {
        return this.getClass().getSimpleName() + "{name='" + this.name + "', location=" + this.location + '}';
    }

    /** A model part drawn at a position and rotation. Upstream {@code MCH_AircraftInfo.DrawnPart}. */
    public record EngineOutlet(Vec3 position, Vec3 direction, float size, boolean jet) {
        public EngineOutlet {
            if (!finite(position) || !finite(direction) || direction.lengthSqr() < 1.0E-6
                    || !Float.isFinite(size) || size <= 0 || size > 4) {
                throw new IllegalArgumentException("Engine outlet needs finite position/direction, nonzero direction and size in (0,4]");
            }
            direction = direction.normalize();
        }
        static boolean finite(Vec3 value) {
            return Double.isFinite(value.x) && Double.isFinite(value.y) && Double.isFinite(value.z)
                    && Math.abs(value.x) <= 1024 && Math.abs(value.y) <= 1024 && Math.abs(value.z) <= 1024;
        }
    }

    /**
     * A component zone placed by the vehicle's own file (M10a). {@code center} and {@code halfSize}
     * are in body coordinates; a rotor may name its {@code part} instead and take its hub's box.
     * A NaN {@code share} or {@code health} means the kind's default.
     */
    public record DamageZoneSpec(ComponentKind kind, @Nullable Vec3 center, @Nullable Vec3 halfSize, String part,
            float share, float health) {
        public DamageZoneSpec {
            if (kind == null) {
                throw new IllegalArgumentException("Damage zone needs a kind");
            }
            part = part == null ? "" : part.trim();
            if (center == null || halfSize == null) {
                if (part.isEmpty() || (kind != ComponentKind.MAIN_ROTOR && kind != ComponentKind.TAIL_ROTOR)) {
                    throw new IllegalArgumentException("Damage zone needs Center and HalfSize, or a rotor Part");
                }
                center = null;
                halfSize = null;
            } else if (!EngineOutlet.finite(center) || !EngineOutlet.finite(halfSize)
                    || Math.min(halfSize.x, Math.min(halfSize.y, halfSize.z)) <= 0
                    || Math.max(halfSize.x, Math.max(halfSize.y, halfSize.z)) > 64) {
                throw new IllegalArgumentException("Damage zone needs a finite Center and a HalfSize in (0,64]");
            }
            if (!Float.isNaN(share) && !(share >= 0 && share <= 10)) {
                throw new IllegalArgumentException("Damage zone Share must be in [0,10]");
            }
            if (!Float.isNaN(health) && !(health >= 0.01F && health <= 10)) {
                throw new IllegalArgumentException("Damage zone Health must be in [0.01,10]");
            }
        }
    }

    public static class DrawnPart {

        public final Vec3 pos;
        public final Vec3 rot;
        public final String modelName;

        public DrawnPart(float px, float py, float pz, float rx, float ry, float rz, String name) {
            this.pos = new Vec3(px, py, pz);
            this.rot = new Vec3(rx, ry, rz);
            this.modelName = name;
        }

        public DrawnPart(Vec3 pos, Vec3 rot, String modelName) {
            this.pos = pos;
            this.rot = rot;
            this.modelName = modelName;
        }

        public DrawnPart(DrawnPart other) {
            this.pos = other.pos;
            this.rot = other.rot;
            this.modelName = other.modelName;
        }

        @Override
        public String toString() {
            return this.getClass().getSimpleName() + "{pos=" + this.pos + ", rot=" + this.rot + ", modelName='"
                    + this.modelName + "'}";
        }
    }

    public static class Camera extends DrawnPart {

        public final boolean yawSync;
        public final boolean pitchSync;

        public Camera(float px, float py, float pz, float rx, float ry, float rz, String name, boolean ys,
                boolean ps) {
            super(px, py, pz, rx, ry, rz, name);
            this.yawSync = ys;
            this.pitchSync = ps;
        }

        public Camera(DrawnPart base, boolean yawSync, boolean pitchSync) {
            super(base);
            this.yawSync = yawSync;
            this.pitchSync = pitchSync;
        }
    }

    /**
     * A camera viewpoint.
     *
     * <p>Upstream's record adds {@link #GLOBAL_Y_OFFSET} in its canonical constructor, and its
     * other constructors (used by the TXT parser) add it a second time before delegating. The YAML
     * emitter subtracts it once, so stock YAML files carry the first offset and both formats end up
     * with the same value. The port keeps all three steps: the canonical constructor is the YAML
     * path, {@link #legacy} is the TXT path.
     */
    public record CameraPosition(Vec3 pos, boolean fixRot, float yaw, float pitch) {

        public CameraPosition {
            pos = pos.add(0, GLOBAL_Y_OFFSET, 0);
        }

        /** Upstream {@code CameraPosition(info, vec3, fixRot, yaw, pitch)}: adds the offset twice. */
        public static CameraPosition legacy(Vec3 vec3, boolean fixRot, float yaw, float pitch) {
            return new CameraPosition(vec3.add(0, GLOBAL_Y_OFFSET, 0), fixRot, yaw, pitch);
        }

        /** Upstream {@code CameraPosition(info, vec3)}. */
        public static CameraPosition legacy(Vec3 vec3) {
            return legacy(vec3, false, 0.0F, 0.0F);
        }

        /** Upstream {@code CameraPosition(info)}: the default viewpoint at the origin. */
        public CameraPosition() {
            this(new Vec3(0.0, GLOBAL_Y_OFFSET, 0.0), false, 0.0F, 0.0F);
        }
    }

    public static class Canopy extends DrawnPart {

        public final float maxRotFactor;
        public final boolean isSlide;

        public Canopy(DrawnPart base, float maxRotationFactor, boolean isSlide) {
            super(base);
            this.maxRotFactor = maxRotationFactor;
            this.isSlide = isSlide;
        }

        public Canopy(float px, float py, float pz, float rx, float ry, float rz, float mr, String name,
                boolean slide) {
            super(px, py, pz, rx, ry, rz, name);
            this.maxRotFactor = mr;
            this.isSlide = slide;
        }
    }

    public static class CrawlerTrack extends DrawnPart {

        public float len = GLOBAL_Y_OFFSET;
        public double[] cx = new double[0];
        public double[] cy = new double[0];
        public List<CrawlerTrackPrm> lp = List.of();
        public float z;
        public int side;

        public CrawlerTrack(String name) {
            super(0.0F, 0.0F, 0.0F, 0.0F, 0.0F, 0.0F, name);
        }

        @Override
        public String toString() {
            return "CrawlerTrack{len=" + this.len + ", cx=" + Arrays.toString(this.cx) + ", cy="
                    + Arrays.toString(this.cy) + ", z=" + this.z + ", side=" + this.side + ", modelName='"
                    + this.modelName + "'}";
        }
    }

    public static class CrawlerTrackPrm {

        public final float x;
        public final float y;
        public float nx;
        public float ny;
        public float r;

        public CrawlerTrackPrm(float x, float y) {
            this.x = x;
            this.y = y;
        }

        @Override
        public String toString() {
            return "CrawlerTrackPrm{x=" + this.x + ", y=" + this.y + ", r=" + this.r + '}';
        }
    }

    public static class Flare {

        public Vec3 pos = new Vec3(0.0, 0.0, 0.0);
        public int[] types = new int[0];

        public Flare() {
        }

        public Flare(Vec3 pos, int[] types) {
            this.pos = pos;
            this.types = types;
        }

        @Override
        public String toString() {
            return "Flare{pos=" + this.pos + ", types=" + Arrays.toString(this.types) + '}';
        }
    }

    public static class Hatch extends DrawnPart {

        public final float maxRotFactor;
        public final float maxRot;
        public final boolean isSlide;

        public Hatch(Vec3 pos, Vec3 rot, String modelName, float maxRot, boolean isSlide) {
            super(pos, rot, modelName);
            this.maxRot = maxRot;
            this.maxRotFactor = this.maxRot / 90.0F;
            this.isSlide = isSlide;
        }

        public Hatch(float px, float py, float pz, float rx, float ry, float rz, float mr, String name,
                boolean slide) {
            super(px, py, pz, rx, ry, rz, name);
            this.maxRot = mr;
            this.maxRotFactor = this.maxRot / 90.0F;
            this.isSlide = slide;
        }

        public Hatch(DrawnPart base, float maxRot, boolean isSlide) {
            super(base);
            this.maxRot = maxRot;
            this.maxRotFactor = maxRot / 90.0F;
            this.isSlide = isSlide;
        }
    }

    public static class LandingGear extends DrawnPart {

        public final float maxRotFactor;
        public final boolean reverse;
        public final boolean hatch;
        public @Nullable Vec3 slide = null;
        public boolean enableRot2;
        public Vec3 rot2 = new Vec3(0.0, 0.0, 0.0);
        public float maxRotFactor2;

        /** The YAML path. Upstream left {@link #rot2} null here; the port uses zero as the TXT path does. */
        public LandingGear(DrawnPart other, float maxRotFactor, boolean reverse, boolean hatch) {
            super(other);
            this.maxRotFactor = maxRotFactor;
            this.reverse = reverse;
            this.hatch = hatch;
        }

        public LandingGear(float x, float y, float z, float rx, float ry, float rz, String model, float maxRotF,
                boolean rev, boolean isHatch) {
            super(x, y, z, rx, ry, rz, model);
            this.maxRotFactor = maxRotF;
            this.enableRot2 = false;
            this.rot2 = new Vec3(0.0, 0.0, 0.0);
            this.maxRotFactor2 = 0.0F;
            this.reverse = rev;
            this.hatch = isHatch;
        }
    }

    public static class PartWeapon extends DrawnPart {

        public final String[] name;
        public final boolean rotBarrel;
        public final boolean isMissile;
        public final boolean hideGM;
        public final boolean yaw;
        public final boolean pitch;
        public final float recoilBuf;
        public final boolean turret;
        public final List<PartWeaponChild> child;

        public PartWeapon(String[] name, boolean rotBrl, boolean missile, boolean hgm, boolean y, boolean p,
                float px, float py, float pz, String modelName, float rx, float ry, float rz, float rb,
                boolean turret) {
            super(px, py, pz, rx, ry, rz, modelName);
            this.name = name;
            this.rotBarrel = rotBrl;
            this.isMissile = missile;
            this.hideGM = hgm;
            this.yaw = y;
            this.pitch = p;
            this.recoilBuf = rb;
            this.child = new ArrayList<>();
            this.turret = turret;
        }

        public PartWeapon(DrawnPart other, String[] name, boolean rotBarrel, boolean isMissile, boolean hideGM,
                boolean yaw, boolean pitch, float recoilBuf, boolean turret) {
            super(other);
            this.name = name;
            this.rotBarrel = rotBarrel;
            this.isMissile = isMissile;
            this.hideGM = hideGM;
            this.yaw = yaw;
            this.pitch = pitch;
            this.recoilBuf = recoilBuf;
            this.turret = turret;
            this.child = new ArrayList<>();
        }
    }

    public static class PartWeaponChild extends DrawnPart {

        public final String[] name;
        public final boolean yaw;
        public final boolean pitch;
        public final float recoilBuf;

        public PartWeaponChild(String[] name, boolean y, boolean p, float px, float py, float pz,
                String modelName, float rx, float ry, float rz, float rb) {
            super(px, py, pz, rx, ry, rz, modelName);
            this.name = name;
            this.yaw = y;
            this.pitch = p;
            this.recoilBuf = rb;
        }

        public PartWeaponChild(Vec3 pos, Vec3 rot, String modelName, String[] name, boolean yaw, boolean pitch,
                float recoilBuf) {
            super(pos, rot, modelName);
            this.name = name;
            this.yaw = yaw;
            this.pitch = pitch;
            this.recoilBuf = recoilBuf;
        }
    }

    public static class PartWheel extends DrawnPart {

        public final float rotDir;
        public final Vec3 pos2;

        public PartWheel(DrawnPart other, float rotDir, Vec3 pos2) {
            super(other);
            this.rotDir = rotDir;
            this.pos2 = pos2;
        }

        public PartWheel(float px, float py, float pz, float rx, float ry, float rz, float rd, float px2,
                float py2, float pz2, String name) {
            super(px, py, pz, rx, ry, rz, name);
            this.rotDir = rd;
            this.pos2 = new Vec3(px2, py2, pz2);
        }

        public PartWheel(float px, float py, float pz, float rx, float ry, float rz, float rd, String name) {
            this(px, py, pz, rx, ry, rz, rd, px, py, pz, name);
        }
    }

    public record ParticleSplash(int num, float acceleration, float size, Vec3 pos, int age, float motionY,
            float gravity) {

        /** Upstream's TXT-order constructor: position first, then count, size, acceleration. */
        public static ParticleSplash of(Vec3 v, int nm, float siz, float acc, int ag, float my, float gr) {
            return new ParticleSplash(nm, acc, siz, v, ag, my, gr);
        }
    }

    public record RepellingHook(Vec3 pos, int interval) {
    }

    public record RideRack(String name, int rackID) {
    }

    public static class RotPart extends DrawnPart {

        public final float rotSpeed;
        public final boolean rotAlways;

        public RotPart(float px, float py, float pz, float rx, float ry, float rz, float mr, boolean a,
                String name) {
            super(px, py, pz, rx, ry, rz, name);
            this.rotSpeed = mr;
            this.rotAlways = a;
        }

        public RotPart(DrawnPart other, float rotSpeed, boolean rotAlways) {
            super(other);
            this.rotSpeed = rotSpeed;
            this.rotAlways = rotAlways;
        }
    }

    public static class TurretRotPart extends DrawnPart {

        public final boolean rotAlways;

        public TurretRotPart(float px, float py, float pz, float rx, float ry, float rz, boolean rotAlways,
                String name) {
            super(px, py, pz, rx, ry, rz, name);
            this.rotAlways = rotAlways;
        }

        public TurretRotPart(DrawnPart other, boolean rotAlways) {
            super(other);
            this.rotAlways = rotAlways;
        }
    }

    public static class SearchLight {

        /** Optional lighting refinement; old content keeps its original searchlight behavior. */
        public boolean m8Only;

        public final Vec3 pos;
        public final int colorStart;
        public final int colorEnd;
        public final float height;
        public final float width;
        public final float angle;
        public final boolean fixDir;
        public final float yaw;
        public final float pitch;
        public final boolean steering;
        public final float stRot;

        public SearchLight(Vec3 pos, int cs, int ce, float h, float w, boolean fix, float y, float p, boolean st,
                float stRot) {
            this.colorStart = cs;
            this.colorEnd = ce;
            this.pos = pos;
            this.height = h;
            this.width = w;
            this.angle = (float) (Math.atan2(w / 2.0F, h) * 180.0 / Math.PI);
            this.fixDir = fix;
            this.steering = st;
            this.yaw = y;
            this.pitch = p;
            this.stRot = stRot;
        }
    }

    public static class Throttle extends DrawnPart {

        public final Vec3 slide;
        public final float rot2;

        public Throttle(float px, float py, float pz, float rx, float ry, float rz, float rot, String name,
                float px2, float py2, float pz2) {
            super(px, py, pz, rx, ry, rz, name);
            this.rot2 = rot;
            this.slide = new Vec3(px2, py2, pz2);
        }

        public Throttle(DrawnPart other, Vec3 slide, float rot2) {
            super(other);
            this.slide = slide;
            this.rot2 = rot2;
        }
    }

    public static class TrackRoller extends DrawnPart {

        public final int side;

        public TrackRoller(float px, float py, float pz, String name) {
            super(px, py, pz, 0.0F, 0.0F, 0.0F, name);
            this.side = px >= 0.0F ? 1 : 0;
        }

        public TrackRoller(DrawnPart other) {
            super(other);
            this.side = other.pos.x >= 0 ? 1 : 0;
        }
    }

    /**
     * One weapon mount. As upstream, the constructor adds {@link #GLOBAL_Y_OFFSET} to both positions.
     */
    public static class Weapon {

        public final Vec3 pos;
        public final Vec3 muzzleFlashPos;
        public final float yaw;
        public final float pitch;
        public final boolean canUsePilot;
        public final int seatID;
        public final float defaultYaw;
        public final float minYaw;
        public final float maxYaw;
        public final float minPitch;
        public final float maxPitch;
        public final boolean turret;

        public Weapon(float x, float y, float z, float mx, float my, float mz, float yaw, float pitch,
                boolean canPirot, int seatId, float defy, float mny, float mxy, float mnp, float mxp,
                boolean turret) {
            this.pos = new Vec3(x, y + GLOBAL_Y_OFFSET, z);
            this.muzzleFlashPos = new Vec3(mx, my + GLOBAL_Y_OFFSET, mz);
            this.yaw = yaw;
            this.pitch = pitch;
            this.canUsePilot = canPirot;
            this.seatID = seatId;
            this.defaultYaw = defy;
            this.minYaw = mny;
            this.maxYaw = mxy;
            this.minPitch = mnp;
            this.maxPitch = mxp;
            this.turret = turret;
        }

        @Override
        public String toString() {
            return "Weapon{pos=" + this.pos + ", seatID=" + this.seatID + ", turret=" + this.turret + '}';
        }
    }

    public static class SignMarker {

        public final float px;
        public final float py;
        public final float pz;
        public final String signName;
        public final float signSize;
        public final boolean perspectiveScale;

        public SignMarker(float px, float py, float pz, String signName, float signSize, boolean perspectiveScale) {
            this.px = px;
            this.py = py;
            this.pz = pz;
            this.signName = signName;
            this.signSize = signSize;
            this.perspectiveScale = perspectiveScale;
        }
    }

    public static class WeaponBay extends DrawnPart {

        public final float maxRotFactor;
        public final boolean isSlide;
        public final String weaponName;
        /** Indices into {@link #weaponSetList}, resolved by {@link #validate()}. */
        public Integer[] weaponIds;

        public WeaponBay(String wn, float px, float py, float pz, float rx, float ry, float rz, float mr,
                String name, boolean slide) {
            super(px, py, pz, rx, ry, rz, name);
            this.maxRotFactor = mr;
            this.isSlide = slide;
            this.weaponName = wn;
            this.weaponIds = new Integer[0];
        }

        public WeaponBay(DrawnPart other, float maxRotFactor, boolean isSlide, String weaponName) {
            super(other);
            this.maxRotFactor = maxRotFactor;
            this.isSlide = isSlide;
            this.weaponName = weaponName;
            this.weaponIds = new Integer[0];
        }
    }

    public static class WeaponSet {

        public final String type;
        public final ArrayList<Weapon> weapons;

        public WeaponSet(String t) {
            this.type = t;
            this.weapons = new ArrayList<>();
        }

        @Override
        public String toString() {
            return "WeaponSet{type='" + this.type + "', weapons=" + this.weapons + '}';
        }
    }

    public enum FlightLampKind {NAVIGATION,STROBE,COCKPIT}
    public record FlightLamp(Vec3 position,String part,FlightLampKind kind,int color,float radius){
        public FlightLamp {
            if(part!=null&&part.startsWith("$"))part=part.substring(1);
            if(position==null||!Double.isFinite(position.lengthSqr())||Math.max(Math.abs(position.x),Math.max(Math.abs(position.y),Math.abs(position.z)))>128
                    ||part==null||!part.matches("[a-zA-Z0-9_.-]{1,40}")||kind==null||color<0||color>0xFFFFFF
                    ||!Float.isFinite(radius)||radius<.02F||radius>2)throw new IllegalArgumentException("Invalid flight lamp position, part, RGB color or radius");
        }
    }

    public record Wheel(Vec3 pos, float size) {

        public Wheel(Vec3 v) {
            this(v, 1.0F);
        }
    }

    public static class Chaff {

        public Vec3 pos = Vec3.ZERO;
    }
}
