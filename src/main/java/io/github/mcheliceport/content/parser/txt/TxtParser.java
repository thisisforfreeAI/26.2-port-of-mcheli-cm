package io.github.mcheliceport.content.parser.txt;

import com.mojang.logging.LogUtils;
import io.github.mcheliceport.content.BaseInfo;
import io.github.mcheliceport.content.ContentLocation;
import io.github.mcheliceport.content.ContentParseException;
import io.github.mcheliceport.content.ContentRegistries;
import io.github.mcheliceport.content.aircraft.AircraftInfo;
import io.github.mcheliceport.content.aircraft.AircraftInfo.CameraPosition;
import io.github.mcheliceport.content.aircraft.AircraftInfo.Canopy;
import io.github.mcheliceport.content.aircraft.AircraftInfo.Hatch;
import io.github.mcheliceport.content.aircraft.AircraftInfo.ParticleSplash;
import io.github.mcheliceport.content.aircraft.AircraftInfo.PartWeapon;
import io.github.mcheliceport.content.aircraft.AircraftInfo.PartWeaponChild;
import io.github.mcheliceport.content.aircraft.AircraftInfo.RepellingHook;
import io.github.mcheliceport.content.aircraft.AircraftInfo.RideRack;
import io.github.mcheliceport.content.aircraft.AircraftInfo.SearchLight;
import io.github.mcheliceport.content.aircraft.AircraftInfo.SignMarker;
import io.github.mcheliceport.content.aircraft.AircraftInfo.Weapon;
import io.github.mcheliceport.content.aircraft.AircraftInfo.WeaponBay;
import io.github.mcheliceport.content.aircraft.AircraftInfo.WeaponSet;
import io.github.mcheliceport.content.aircraft.AircraftInfo.Wheel;
import io.github.mcheliceport.content.aircraft.BoundingBoxInfo;
import io.github.mcheliceport.content.aircraft.CrewRole;
import io.github.mcheliceport.content.aircraft.HeliInfo;
import io.github.mcheliceport.content.aircraft.PlaneInfo;
import io.github.mcheliceport.content.aircraft.SeatInfo;
import io.github.mcheliceport.content.aircraft.SeatRackInfo;
import io.github.mcheliceport.content.aircraft.ShipInfo;
import io.github.mcheliceport.content.aircraft.TankInfo;
import io.github.mcheliceport.content.aircraft.VehicleInfo;
import io.github.mcheliceport.content.hud.Hud;
import io.github.mcheliceport.content.hud.HudItem;
import io.github.mcheliceport.content.parser.ContentParser;
import io.github.mcheliceport.content.sound.SoundDefinitions;
import io.github.mcheliceport.content.throwable.ThrowableInfo;
import io.github.mcheliceport.content.weapon.ArgbColor;
import io.github.mcheliceport.content.weapon.BulletDecay;
import io.github.mcheliceport.content.weapon.CartridgeInfo;
import io.github.mcheliceport.content.weapon.DamageFactor;
import io.github.mcheliceport.content.weapon.PotionEffectInfo;
import io.github.mcheliceport.content.weapon.SightType;
import io.github.mcheliceport.content.weapon.WeaponInfo;
import io.github.mcheliceport.content.weapon.WeaponInfo.MuzzleFlash;
import io.github.mcheliceport.content.weapon.WeaponInfo.RoundItem;
import io.github.mcheliceport.damage.ComponentKind;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.function.Supplier;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;

/**
 * Parser for the legacy {@code .txt} content format of McHeli and McHeli Reforged.
 *
 * <p>Ported from upstream {@code com.norwood.mcheli.helper.info.parsers.txt.TxtParser}. Upstream
 * keeps the format for old content packs and deprecates it in favour of YAML; so does the port.
 * Each line is {@code Key = value}; keys are case-insensitive. Unknown keys are ignored.
 *
 * <p>Differences from upstream, all so that a TXT file and its YAML conversion load the same:
 * <ul>
 *   <li>Throwable {@code TimeFuse}, {@code AliveTime} and {@code DelayFuse} are converted from
 *       seconds to ticks here; upstream did it in {@code validate()} (see {@link ThrowableInfo}).</li>
 *   <li>{@code HUD} lines are read on both sides and store HUD names (see {@link AircraftInfo}).</li>
 * </ul>
 */
public final class TxtParser implements ContentParser {

    public static final TxtParser INSTANCE = new TxtParser();
    public static final String IDENTIFIER = "txt";
    private static final Logger LOGGER = LogUtils.getLogger();

    private TxtParser() {
    }

    @Override
    public String getIdentifier() {
        return IDENTIFIER;
    }

    private static void logDeprecation(String filepath) {
        LOGGER.debug("Loading legacy .txt content '{}': the TXT format is deprecated, convert it to YAML with"
                + " /mcheli dumpyaml.", filepath);
    }

    @Override
    public HeliInfo parseHelicopter(ContentLocation location, String filepath, List<String> lines, boolean reload)
            throws Exception {
        return parse(filepath, lines, reload, () -> new HeliInfo(location, filepath, IDENTIFIER),
                this::applyAircraftLine, this::applyHelicopterLine);
    }

    @Override
    public PlaneInfo parsePlane(ContentLocation location, String filepath, List<String> lines, boolean reload)
            throws Exception {
        return parse(filepath, lines, reload, () -> new PlaneInfo(location, filepath, IDENTIFIER),
                this::applyAircraftLine, this::applyPlaneLine);
    }

    @Override
    public ShipInfo parseShip(ContentLocation location, String filepath, List<String> lines, boolean reload)
            throws Exception {
        return parse(filepath, lines, reload, () -> new ShipInfo(location, filepath, IDENTIFIER),
                this::applyAircraftLine, this::applyShipLine);
    }

    @Override
    public TankInfo parseTank(ContentLocation location, String filepath, List<String> lines, boolean reload)
            throws Exception {
        return parse(filepath, lines, reload, () -> new TankInfo(location, filepath, IDENTIFIER),
                this::applyAircraftLine, this::applyTankLine);
    }

    @Override
    public VehicleInfo parseVehicle(ContentLocation location, String filepath, List<String> lines, boolean reload)
            throws Exception {
        return parse(filepath, lines, reload, () -> new VehicleInfo(location, filepath, IDENTIFIER),
                this::applyAircraftLine, this::applyVehicleLine);
    }

    @Override
    public WeaponInfo parseWeapon(ContentLocation location, String filepath, List<String> lines, boolean reload)
            throws Exception {
        return parse(filepath, lines, reload, () -> new WeaponInfo(location, filepath, IDENTIFIER),
                this::applyWeaponLine);
    }

    @Override
    public ThrowableInfo parseThrowable(ContentLocation location, String filepath, List<String> lines,
            boolean reload) throws Exception {
        return parse(filepath, lines, reload, () -> new ThrowableInfo(location, filepath, IDENTIFIER),
                this::applyThrowableLine);
    }

    @Override
    public Hud parseHud(ContentLocation location, String filepath, List<String> lines, boolean reload) {
        logDeprecation(filepath);
        Hud info = new Hud(location, filepath, IDENTIFIER);
        int lineNumber = 0;
        try {
            for (String raw : lines) {
                lineNumber++;
                String str = raw.trim();
                if (str.equalsIgnoreCase("endif")) {
                    str = "endif=0";
                } else if (str.equalsIgnoreCase("exit")) {
                    str = "exit=0";
                }

                int eqIdx = str.indexOf('=');
                if (eqIdx >= 0 && str.length() > eqIdx + 1) {
                    String item = str.substring(0, eqIdx).trim().toLowerCase(Locale.ROOT);
                    String data = str.substring(eqIdx + 1).trim();
                    if (!reload || info.canReloadItem(item)) {
                        this.applyHudLine(info, lineNumber, item, data);
                    }
                }
            }
            return info;
        } catch (Exception ex) {
            throw new ContentParseException(ex, lineNumber);
        }
    }

    @SafeVarargs
    private <T extends BaseInfo> T parse(String filepath, List<String> lines, boolean reload, Supplier<T> factory,
            LineHandler<? super T>... handlers) {
        logDeprecation(filepath);
        T info = factory.get();
        parseLines(lines, (lineNumber, item, data) -> {
            if (reload && !info.canReloadItem(item)) {
                return;
            }
            for (LineHandler<? super T> handler : handlers) {
                handler.accept(info, lineNumber, item, data);
            }
        });
        return info;
    }

    private static void parseLines(List<String> lines, LineProcessor processor) {
        int lineIdx = 0;
        try {
            for (String raw : lines) {
                lineIdx++;
                String str = raw.trim();
                int eqIdx = str.indexOf('=');
                if (eqIdx >= 0 && str.length() > eqIdx + 1) {
                    String item = str.substring(0, eqIdx).trim().toLowerCase(Locale.ROOT);
                    String data = str.substring(eqIdx + 1).trim();
                    processor.accept(lineIdx, item, data);
                }
            }
        } catch (Exception ex) {
            throw new ContentParseException(ex, lineIdx);
        }
    }

    // ===================================================================================
    // Aircraft (shared base for heli / plane / ship / tank / vehicle)
    // ===================================================================================

    private void applyAircraftLine(AircraftInfo info, int lineNumber, String item, String data) {
        switch (item) {
            case "displayname" -> info.displayName = data.trim();
            case "adddisplayname" -> {
                String[] s = data.split("\\s*,\\s*");
                if (s.length == 2) {
                    info.displayNameLang.put(s[0].toLowerCase(Locale.ROOT).trim(), s[1].trim());
                }
            }
            case "category" -> info.category = data.toUpperCase(Locale.ROOT).replaceAll("[,;:]", ".")
                    .replaceAll("[ \t]", "");
            case "canride" -> info.canRide = info.toBool(data, true);
            case "creativeonly" -> info.creativeOnly = info.toBool(data, false);
            case "invulnerable" -> info.invulnerable = info.toBool(data, false);
            case "maxfuel" -> info.maxFuel = info.toInt(data, 0, 100000000);
            case "fuelconsumption" -> info.fuelConsumption = info.toFloat(data, 0.0F, 10000.0F);
            case "fuelsupplyrange" -> info.fuelSupplyRange = info.toFloat(data, 0.0F, 1000.0F);
            case "ammosupplyrange" -> info.ammoSupplyRange = info.toFloat(data, 0.0F, 1000.0F);
            case "repairothervehicles" -> {
                String[] s = info.splitParam(data);
                if (s.length >= 1) {
                    info.repairOtherVehiclesRange = info.toFloat(s[0], 0.0F, 1000.0F);
                    if (s.length >= 2) {
                        info.repairOtherVehiclesValue = info.toInt(s[1], 0, 10000000);
                    }
                }
            }
            case "nameonmodernaaradar" -> info.nameOnModernAARadar = data.trim();
            case "nameonearlyaaradar" -> info.nameOnEarlyAARadar = data.trim();
            case "nameonmodernasradar" -> info.nameOnModernASRadar = data.trim();
            case "nameonearlyasradar" -> info.nameOnEarlyASRadar = data.trim();
            case "nameonadvancedaaradar" -> info.nameOnAdvancedAARadar = data.trim();
            case "nameonrwr" -> info.nameOnRWR = data.trim();
            case "explosionsizebycrash" -> info.explosionSizeByCrash = info.toInt(data, 0, 100);
            case "throttledownfactor" -> info.throttleDownFactor = info.toFloat(data, 0, 10);
            case "itemid" -> info.itemID = info.toInt(data, 0, 65535);
            case "addtexture" -> info.addTextureName(data.toLowerCase(Locale.ROOT));
            case "particlesscale" -> info.particlesScale = info.toFloat(data, 0.0F, 50.0F);
            case "addengineoutlet" -> {
                String[] s = info.splitParam(data);
                if (s.length < 6) throw new IllegalArgumentException("AddEngineOutlet requires x,y,z,dx,dy,dz[,size,jet]");
                info.engineOutlets.add(new AircraftInfo.EngineOutlet(info.toVec3(s[0], s[1], s[2]),
                        info.toVec3(s[3], s[4], s[5]), s.length > 6 ? info.toFloat(s[6]) : 1,
                        s.length > 7 && info.toBool(s[7])));
            }
            case "seatrole" -> {
                // M10b: seat number (1 = the pilot), role
                String[] s = info.splitParam(data);
                try {
                    int index = info.toInt(s[0]) - 1;
                    if (index < 0 || index >= info.seatList.size()) {
                        throw new IllegalArgumentException("no seat " + s[0] + " yet; put SeatRole after the seats");
                    }
                    info.seatList.get(index).role = CrewRole.valueOf(s[1].trim().toUpperCase(Locale.ROOT));
                } catch (RuntimeException e) {
                    LOGGER.warn("Skipping SeatRole '{}' in {}: {}", data, info.name, e.getMessage());
                }
            }
            case "loaderrequired" -> info.loaderRequired = info.toBool(data);
            case "adddamagezone" -> {
                // kind,x,y,z,hx,hy,hz[,share[,health]] or a rotor by part: kind,part[,share[,health]]
                String[] s = info.splitParam(data);
                try {
                    ComponentKind kind = ComponentKind.valueOf(s[0].trim().toUpperCase(Locale.ROOT));
                    boolean box = s.length >= 7;
                    int rest = box ? 7 : 2;
                    info.damageZones.add(new AircraftInfo.DamageZoneSpec(kind,
                            box ? info.toVec3(s[1], s[2], s[3]) : null, box ? info.toVec3(s[4], s[5], s[6]) : null,
                            box ? "" : s[1], s.length > rest ? info.toFloat(s[rest]) : Float.NaN,
                            s.length > rest + 1 ? info.toFloat(s[rest + 1]) : Float.NaN));
                } catch (RuntimeException e) {
                    LOGGER.warn("Skipping AddDamageZone '{}' in {}: {}", data, info.name, e.getMessage());
                }
            }
            case "enableseasurfaceparticle" -> info.enableSeaSurfaceParticle = info.toBool(data);
            case "addparticlesplash" -> {
                String[] s = info.splitParam(data);
                if (s.length >= 3) {
                    Vec3 v = info.toVec3(s[0], s[1], s[2]);
                    int num = s.length >= 4 ? info.toInt(s[3], 1, 100) : 2;
                    float size = s.length >= 5 ? info.toFloat(s[4]) : 2.0F;
                    float acc = s.length >= 6 ? info.toFloat(s[5]) : 1.0F;
                    int age = s.length >= 7 ? info.toInt(s[6], 1, 100000) : 80;
                    float motionY = s.length >= 8 ? info.toFloat(s[7]) : 0.01F;
                    float gravity = s.length >= 9 ? info.toFloat(s[8]) : 0.0F;
                    info.particleSplashs.add(ParticleSplash.of(v, num, size, acc, age, motionY, gravity));
                }
            }
            case "addsearchlight", "addfixedsearchlight", "addsteeringsearchlight" -> {
                String[] s = info.splitParam(data);
                if (s.length >= 7) {
                    Vec3 v = info.toVec3(s[0], s[1], s[2]);
                    int cs = info.hex2dec(s[3]);
                    int ce = info.hex2dec(s[4]);
                    float h = info.toFloat(s[5]);
                    float w = info.toFloat(s[6]);
                    float yaw = s.length >= 8 ? info.toFloat(s[7]) : 0.0F;
                    float pitch = s.length >= 9 ? info.toFloat(s[8]) : 0.0F;
                    float stRot = s.length >= 10 ? info.toFloat(s[9]) : 0.0F;
                    boolean fixDir = !item.equalsIgnoreCase("AddSearchLight");
                    boolean steering = item.equalsIgnoreCase("AddSteeringSearchLight");
                    info.searchLights.add(new SearchLight(v, cs, ce, h, w, fixDir, yaw, pitch, steering, stRot));
                }
            }
            case "addpartlighthatch" -> {
                String[] s = info.splitParam(data);
                if (s.length >= 6) {
                    float mx = s.length >= 7 ? info.toFloat(s[6], -1800.0F, 1800.0F) : 90.0F;
                    info.lightHatchList.add(new Hatch(info.toFloat(s[0]), info.toFloat(s[1]), info.toFloat(s[2]),
                            info.toFloat(s[3]), info.toFloat(s[4]), info.toFloat(s[5]), mx,
                            "light_hatch" + info.lightHatchList.size(), false));
                }
            }
            case "addrepellinghook" -> {
                String[] s = info.splitParam(data);
                if (s.length >= 3) {
                    int inv = s.length >= 4 ? info.toInt(s[3], 1, 100000) : 10;
                    info.repellingHooks.add(new RepellingHook(info.toVec3(s[0], s[1], s[2]), inv));
                }
            }
            case "addrack" -> {
                String[] s = data.toLowerCase(Locale.ROOT).split("\\s*,\\s*");
                if (s.length >= 7) {
                    String[] names = s[0].split("\\s*/\\s*");
                    float range = s.length >= 8 ? info.toFloat(s[7]) : 6.0F;
                    float para = s.length >= 9 ? info.toFloat(s[8], 0.0F, 1000000.0F) : 20.0F;
                    float yaw = s.length >= 10 ? info.toFloat(s[9]) : 0.0F;
                    float pitch = s.length >= 11 ? info.toFloat(s[10]) : 0.0F;
                    boolean rs = s.length >= 12 && info.toBool(s[11]);
                    Vec3 up = s.length >= 15 ? info.toVec3(s[12], s[13], s[14]) : null;
                    info.entityRackList.add(new SeatRackInfo(names, info.toDouble(s[1]), info.toDouble(s[2]),
                            info.toDouble(s[3]), up,
                            CameraPosition.legacy(info.toVec3(s[4], s[5], s[6]).add(0.0, 1.5, 0.0)), range, para,
                            yaw, pitch, rs));
                }
            }
            case "riderack" -> {
                String[] s = info.splitParam(data);
                if (s.length >= 2) {
                    info.rideRacks.add(new RideRack(s[0].trim().toLowerCase(Locale.ROOT), info.toInt(s[1], 1, 10000)));
                }
            }
            case "addseat", "addgunnerseat", "addfixrotseat" -> {
                if (info.seatList.size() >= info.getInfo_MaxSeatNum()) {
                    return;
                }

                String[] s = info.splitParam(data);
                if (s.length < 3) {
                    return;
                }

                Vec3 p = info.toVec3(s[0], s[1], s[2]);
                if (item.equalsIgnoreCase("AddSeat")) {
                    boolean rs = s.length >= 4 && info.toBool(s[3]);
                    Vec3 up = s.length >= 7 ? info.toVec3(s[4], s[5], s[6]) : null;
                    info.seatList.add(new SeatInfo(p, up, false, null, false, false, false, 0.0F, 0.0F, -30.0F, 70.0F,
                            rs));
                } else {
                    SeatInfo seat;
                    if (s.length >= 6) {
                        CameraPosition c = CameraPosition.legacy(info.toVec3(s[3], s[4], s[5]));
                        boolean sg = s.length >= 7 && info.toBool(s[6]);
                        if (item.equalsIgnoreCase("AddGunnerSeat")) {
                            if (s.length >= 9) {
                                float minPitch = info.toFloat(s[7], -90.0F, 90.0F);
                                float maxPitch = info.toFloat(s[8], -90.0F, 90.0F);
                                if (minPitch > maxPitch) {
                                    float t = minPitch;
                                    minPitch = maxPitch;
                                    maxPitch = t;
                                }

                                boolean rs = s.length >= 10 && info.toBool(s[9]);
                                Vec3 up = s.length >= 13 ? info.toVec3(s[10], s[11], s[12]) : null;
                                seat = new SeatInfo(p, up, true, c, true, sg, false, 0.0F, 0.0F, minPitch, maxPitch,
                                        rs);
                            } else {
                                seat = new SeatInfo(p, true, c, true, sg, false, 0.0F, 0.0F, false);
                            }
                        } else {
                            boolean fixRot = s.length >= 9;
                            float fixYaw = fixRot ? info.toFloat(s[7]) : 0.0F;
                            float fixPitch = fixRot ? info.toFloat(s[8]) : 0.0F;
                            boolean rs = s.length >= 10 && info.toBool(s[9]);
                            Vec3 up = s.length >= 13 ? info.toVec3(s[10], s[11], s[12]) : null;
                            seat = new SeatInfo(p, up, true, c, true, sg, fixRot, fixYaw, fixPitch, -30.0F, 70.0F,
                                    rs);
                        }
                    } else {
                        seat = new SeatInfo(p, true, new CameraPosition(), false, false, false, 0.0F, 0.0F, false);
                    }

                    info.seatList.add(seat);
                }
            }
            case "setwheelpos" -> {
                String[] sx = info.splitParam(data);
                if (sx.length >= 4) {
                    float x = Math.abs(info.toFloat(sx[0]));
                    float y = info.toFloat(sx[1]);
                    info.wheels.clear();

                    for (int i = 2; i < sx.length; i++) {
                        info.wheels.add(new Wheel(new Vec3(x, y, info.toFloat(sx[i]))));
                    }

                    info.wheels.sort((arg0, arg1) -> arg0.pos().z > arg1.pos().z ? -1 : 1);
                }
            }
            case "exclusionseat" -> {
                String[] sx = info.splitParam(data);
                if (sx.length >= 2) {
                    Integer[] a = new Integer[sx.length];
                    for (int i = 0; i < a.length; i++) {
                        a[i] = info.toInt(sx[i], 1, 10000) - 1;
                    }
                    info.exclusionSeatList.add(a);
                }
            }
            case "hud" -> {
                info.hudList.clear();
                for (String sx : data.split("\\s*,\\s*")) {
                    info.hudList.add(sx);
                }
            }
            case "enablenightvision" -> info.isEnableNightVision = info.toBool(data);
            case "enableentityradar" -> info.isEnableEntityRadar = info.toBool(data);
            case "enableejectionseat" -> info.isEnableEjectionSeat = info.toBool(data);
            case "enableparachuting" -> info.isEnableParachuting = info.toBool(data);
            case "mobdropoption" -> {
                String[] sx = info.splitParam(data);
                if (sx.length >= 3) {
                    info.mobDropOption.pos = info.toVec3(sx[0], sx[1], sx[2]);
                    info.mobDropOption.interval = sx.length >= 4 ? info.toInt(sx[3]) : 12;
                }
            }
            case "width" -> info.bodyWidth = info.toFloat(data, 0.1F, 1000.0F);
            case "height" -> info.bodyHeight = info.toFloat(data, 0.1F, 1000.0F);
            case "float" -> info.isFloat = info.toBool(data);
            case "floatoffset" -> info.floatOffset = -info.toFloat(data);
            case "gravity" -> info.gravity = info.toFloat(data, -50.0F, 50.0F);
            case "gravityinwater" -> info.gravityInWater = info.toFloat(data, -50.0F, 50.0F);
            case "cameraposition" -> {
                String[] sx = data.split("\\s*,\\s*");
                if (sx.length >= 3) {
                    info.alwaysCameraView = sx.length >= 4 && info.toBool(sx[3]);
                    boolean fixRot = sx.length >= 5;
                    float yaw = sx.length >= 5 ? info.toFloat(sx[4]) : 0.0F;
                    float pitch = sx.length >= 6 ? info.toFloat(sx[5]) : 0.0F;
                    info.cameraPosition.add(CameraPosition.legacy(info.toVec3(sx[0], sx[1], sx[2]), fixRot, yaw,
                            pitch));
                }
            }
            case "unmountposition" -> {
                String[] sx = data.split("\\s*,\\s*");
                if (sx.length >= 3) {
                    info.unmountPosition = info.toVec3(sx[0], sx[1], sx[2]);
                }
            }
            case "thirdpersondist" -> info.thirdPersonDist = info.toFloat(data, 4.0F, 100.0F);
            case "turretposition" -> {
                String[] sx = data.split("\\s*,\\s*");
                if (sx.length >= 3) {
                    info.turretPosition = info.toVec3(sx[0], sx[1], sx[2]);
                }
            }
            case "camerarotationspeed" -> info.cameraRotationSpeed = info.toFloat(data, 0.0F, 10000.0F);
            case "regeneration" -> info.regeneration = info.toBool(data);
            case "speed" -> info.speed = info.toFloat(data, 0.0F, info.getMaxSpeed());
            case "enableback" -> info.enableBack = info.toBool(data);
            case "motionfactor" -> info.motionFactor = info.toFloat(data, 0.0F, 1.0F);
            case "mobilityyawonground" -> info.mobilityYawOnGround = info.toFloat(data, 0.0F, 100.0F);
            case "mobilityyaw" -> info.mobilityYaw = info.toFloat(data, 0.0F, 100.0F);
            case "mobilitypitch" -> info.mobilityPitch = info.toFloat(data, 0.0F, 100.0F);
            case "mobilityroll" -> info.mobilityRoll = info.toFloat(data, 0.0F, 100.0F);
            case "minrotationpitch" -> {
                info.limitRotation = true;
                info.minRotationPitch = info.toFloat(data, info.getMinRotationPitch(), 0.0F);
            }
            case "maxrotationpitch" -> {
                info.limitRotation = true;
                info.maxRotationPitch = info.toFloat(data, 0.0F, info.getMaxRotationPitch());
            }
            case "minrotationroll" -> {
                info.limitRotation = true;
                info.minRotationRoll = info.toFloat(data, info.getMinRotationRoll(), 0.0F);
            }
            case "maxrotationroll" -> {
                info.limitRotation = true;
                info.maxRotationRoll = info.toFloat(data, 0.0F, info.getMaxRotationRoll());
            }
            case "throttleupdown" -> info.throttleUpDown = info.toFloat(data, 0.0F, 3.0F);
            case "throttleupdownonentity" -> info.throttleUpDownOnEntity = info.toFloat(data, 0.0F, 100_000.0F);
            case "stealth" -> info.stealth = info.toFloat(data, 0.0F, 1.0F);
            case "entitywidth" -> info.entityWidth = info.toFloat(data, -100.0F, 100.0F);
            case "entityheight" -> info.entityHeight = info.toFloat(data, -100.0F, 100.0F);
            case "entitypitch" -> info.entityPitch = info.toFloat(data, -360.0F, 360.0F);
            case "entityroll" -> info.entityRoll = info.toFloat(data, -360.0F, 360.0F);
            case "stepheight" -> info.stepHeight = info.toFloat(data, 0.0F, 1000.0F);
            case "canmoveonground" -> info.canMoveOnGround = info.toBool(data);
            case "canrotonground" -> info.canRotOnGround = info.toBool(data);
            case "addweapon", "addturretweapon" -> {
                String[] sx = data.split("\\s*,\\s*");
                String type = sx[0].toLowerCase(Locale.ROOT);
                if (sx.length >= 4 && ContentRegistries.weapon().contains(type)) {
                    float y = sx.length >= 5 ? info.toFloat(sx[4]) : 0.0F;
                    float p = sx.length >= 6 ? info.toFloat(sx[5]) : 0.0F;
                    boolean canUsePilot = sx.length < 7 || info.toBool(sx[6]);
                    int seatID = sx.length >= 8 ? info.toInt(sx[7], 1, info.getInfo_MaxSeatNum()) - 1 : 0;
                    if (seatID <= 0) {
                        canUsePilot = true;
                    }

                    float dfy = sx.length >= 9 ? info.toFloat(sx[8]) : 0.0F;
                    dfy = Mth.wrapDegrees(dfy);
                    float mny = sx.length >= 10 ? info.toFloat(sx[9]) : 0.0F;
                    float mxy = sx.length >= 11 ? info.toFloat(sx[10]) : 0.0F;
                    float mnp = sx.length >= 12 ? info.toFloat(sx[11]) : 0.0F;
                    float mxp = sx.length >= 13 ? info.toFloat(sx[12]) : 0.0F;
                    float px = info.toFloat(sx[1]);
                    float py = info.toFloat(sx[2]);
                    float pz = info.toFloat(sx[3]);
                    Weapon e = new Weapon(px, py, pz, px, py, pz, y, p, canUsePilot, seatID, dfy, mny, mxy, mnp, mxp,
                            item.equalsIgnoreCase("AddTurretWeapon"));
                    WeaponSet set = info.getOrCreateWeaponSet(type);
                    set.weapons.add(e);
                }
            }
            case "addpartweapon", "addpartrotweapon", "addpartturretweapon", "addpartturretrotweapon",
                    "addpartweaponmissile" -> {
                String[] sx = data.split("\\s*,\\s*");
                if (sx.length >= 7) {
                    float rx = 0.0F;
                    float ry = 0.0F;
                    float rz = 0.0F;
                    float rb = 0.0F;
                    boolean isRot = item.equalsIgnoreCase("AddPartRotWeapon")
                            || item.equalsIgnoreCase("AddPartTurretRotWeapon");
                    boolean isMissile = item.equalsIgnoreCase("AddPartWeaponMissile");
                    boolean turret = item.equalsIgnoreCase("AddPartTurretWeapon")
                            || item.equalsIgnoreCase("AddPartTurretRotWeapon");
                    if (isRot) {
                        rx = sx.length >= 10 ? info.toFloat(sx[7]) : 0.0F;
                        ry = sx.length >= 10 ? info.toFloat(sx[8]) : 0.0F;
                        rz = sx.length >= 10 ? info.toFloat(sx[9]) : -1.0F;
                    } else {
                        rb = sx.length >= 8 ? info.toFloat(sx[7]) : 0.0F;
                    }

                    PartWeapon w = new PartWeapon(info.splitParamSlash(sx[0].toLowerCase(Locale.ROOT).trim()), isRot,
                            isMissile, info.toBool(sx[1]), info.toBool(sx[2]), info.toBool(sx[3]), info.toFloat(sx[4]),
                            info.toFloat(sx[5]), info.toFloat(sx[6]), "weapon" + info.partWeapon.size(), rx, ry, rz,
                            rb, turret);
                    info.setLastWeaponPart(w);
                    info.partWeapon.add(w);
                }
            }
            case "addpartweaponchild" -> {
                String[] sx = data.split("\\s*,\\s*");
                PartWeapon last = info.getLastWeaponPart();
                if (sx.length >= 5 && last != null) {
                    float rb = sx.length >= 6 ? info.toFloat(sx[5]) : 0.0F;
                    PartWeaponChild w = new PartWeaponChild(last.name, info.toBool(sx[0]), info.toBool(sx[1]),
                            info.toFloat(sx[2]), info.toFloat(sx[3]), info.toFloat(sx[4]),
                            last.modelName + "_" + last.child.size(), 0.0F, 0.0F, 0.0F, rb);
                    last.child.add(w);
                }
            }
            case "addrecipe", "addshapelessrecipe" -> {
                info.isShapedRecipe = item.equals("addrecipe");
                info.recipeString.add(data.toUpperCase(Locale.ROOT));
            }
            case "maxhp" -> info.maxHp = info.toInt(data, 1, 1000000000);
            case "inventorysize" -> info.inventorySize = info.toInt(data, 0, 54);
            case "damagefactor" -> info.damageFactor = info.toFloat(data, 0.0F, 1.0F);
            case "submergeddamageheight" -> info.submergedDamageHeight = info.toFloat(data, -1000.0F, 1000.0F);
            case "armordamagefactor" -> info.armorDamageFactor = info.toFloat(data, 0.0F, 10000.0F);
            case "armormindamage" -> info.armorMinDamage = info.toFloat(data, 0.0F, 1000000.0F);
            case "armormaxdamage" -> info.armorMaxDamage = info.toFloat(data, 0.0F, 1000000.0F);
            case "armorexplosiondamagemultiplier" -> info.armorExplosionDamageMultiplier = info.toFloat(data);
            case "impactanglecoefficient" -> parseImpactAngleCoefficient(info, data);
            case "flaretype" -> {
                String[] sx = data.split("\\s*,\\s*");
                info.flare.types = new int[sx.length];
                for (int i = 0; i < sx.length; i++) {
                    info.flare.types[i] = info.toInt(sx[i], 1, 10);
                }
            }
            case "flareoption" -> {
                String[] sx = info.splitParam(data);
                if (sx.length >= 3) {
                    info.flare.pos = info.toVec3(sx[0], sx[1], sx[2]);
                }
            }
            // ---- Reforged radar suite ----
            case "enableradar" -> info.enableRadar = info.toBool(data);
            case "enablerwr" -> info.hasRWR = info.toBool(data);
            case "enablebvr" -> info.enableBVR = info.toBool(data);
            case "radarmaxtargetrange" -> info.radarMaxTargetRange = info.toFloat(data, 50.0F, 20000.0F);
            case "radarminscanaltitude" -> info.radarMinScanAltitude = info.toFloat(data, -256.0F, 4096.0F);
            case "radarmaxscanaltitude" -> info.radarMaxScanAltitude = info.toFloat(data, -256.0F, 4096.0F);
            case "radarsearchtype" -> {
                String mode = data.trim().toUpperCase(Locale.ROOT).replace(" ", "_");
                info.radarSearchType = switch (mode) {
                    case "TWS", "GMTI_SRC", "GMTI_TWS", "MULTI_SRC", "MULTI_TWS" -> mode;
                    default -> "SRC";
                };
            }
            case "radartrackazimuthdeg" -> info.radarTrackAzimuthDeg = info.toFloat(data, 0.0F, 360.0F);
            case "radartrackelevationdeg" -> info.radarTrackElevationDeg = info.toFloat(data, 0.0F, 180.0F);
            case "radarretargetcooldowntick" -> info.radarRetargetCooldownTick = info.toInt(data, 0, 12000);
            case "radarscanazimuthdeg" -> info.radarScanAzimuthDeg = info.toFloat(data, 0.0F, 360.0F);
            case "radarpanelfillalpha" -> info.radarPanelFillAlpha = info.toFloat(data, 0.0F, 1.0F);
            case "radarfollowturretyaw" -> info.radarFollowTurretYaw = info.toBool(data);
            case "radarscanelevationdeg" -> info.radarScanElevationDeg = info.toFloat(data, 0.0F, 180.0F);
            case "radarscantick" -> info.radarScanTick = info.toInt(data, 1, 1200);
            case "radardetectchancebase" -> info.radarDetectChanceBase = info.toFloat(data, 0.0F, 1.0F);
            case "radargainfactor", "gainfactor" -> {
                String[] s = info.splitParam(data);
                if (s.length >= 1) {
                    info.radarGainNearFactor = info.toFloat(s[0], 0.01F, 10.0F);
                }
                if (s.length >= 2) {
                    info.radarGainFarFactor = info.toFloat(s[1], 0.01F, 10.0F);
                }
            }
            case "radarrcsfactor" -> {
                String[] s = info.splitParam(data);
                if (s.length >= 1) {
                    info.radarRcsFrontFactor = info.toFloat(s[0], 0.01F, 10.0F);
                }
                if (s.length >= 2) {
                    info.radarRcsSideFactor = info.toFloat(s[1], 0.01F, 10.0F);
                }
                if (s.length >= 3) {
                    info.radarRcsRearFactor = info.toFloat(s[2], 0.01F, 10.0F);
                }
                if (s.length >= 4) {
                    info.radarRcsTimeFactor = info.toFloat(s[3], 0.01F, 10.0F);
                }
            }
            case "radarrcsfrontfactor" -> info.radarRcsFrontFactor = info.toFloat(data, 0.01F, 10.0F);
            case "radarrcssidefactor" -> info.radarRcsSideFactor = info.toFloat(data, 0.01F, 10.0F);
            case "radarrcsrearfactor" -> info.radarRcsRearFactor = info.toFloat(data, 0.01F, 10.0F);
            case "radarrcstimefactor" -> info.radarRcsTimeFactor = info.toFloat(data, 0.01F, 10.0F);
            case "radarcontactholdtick" -> info.radarContactHoldTick = info.toInt(data, 1, 1200);
            case "radarelevationreference" -> {
                String mode = data.trim().toUpperCase(Locale.ROOT);
                info.radarElevationReference = mode.equals("AIRCRAFT") ? "AIRCRAFT" : "HORIZON";
            }
            case "radarelevationcoverage" -> {
                String mode = data.trim().toUpperCase(Locale.ROOT);
                info.radarElevationCoverage = switch (mode) {
                    case "FULL", "DOWN_ONLY" -> mode;
                    default -> "UP_ONLY";
                };
            }
            case "hudtype" -> info.hudType = info.toInt(data);
            case "weapongrouptype" -> info.weaponGroupType = info.toInt(data);
            // ---- Reforged defensive systems ----
            case "haschaff" -> info.chaff = new AircraftInfo.Chaff();
            case "chaffusetime" -> info.chaffUseTime = info.toInt(data, 0, 10000);
            case "chaffwaittime" -> info.chaffWaitTime = info.toInt(data, 0, 10000);
            case "hasmaintenance" -> info.enableMaintenance = true;
            case "maintenanceusetime" -> info.maintenanceUseTime = info.toInt(data, 0, 100);
            case "maintenancewaittime" -> info.maintenanceWaitTime = info.toInt(data, 0, 10000);
            case "engineshutdownthreshold" -> info.engineShutdownThreshold = info.toInt(data, 0, 100);
            case "hasaps" -> info.hasAPS = true;
            case "apsusetime" -> info.apsUseTime = info.toInt(data, 0, 10000);
            case "apswaittime" -> info.apsWaitTime = info.toInt(data, 0, 10000);
            case "apsrange" -> info.apsRange = info.toInt(data, 0, 100);
            case "hasecmjammer" -> info.enableECMJammer = true;
            case "ecmjammertype" -> info.ecmJammerType = info.toInt(data);
            case "ecmjammerusetime" -> info.ecmJammerUseTime = info.toInt(data, 0, 10000);
            case "ecmjammerwaittime" -> info.ecmJammerWaitTime = info.toInt(data, 0, 10000);
            case "hasdircm" -> info.hasDIRCM = info.toBool(data);
            case "hasphotoelectricjammer" -> info.hasPhotoelectricJammer = info.toBool(data);
            case "sound" -> info.soundMove = SoundDefinitions.parseSound(data);
            case "soundrange" -> info.soundRange = info.toFloat(data, 1.0F, 1000.0F);
            case "soundvolume" -> info.soundVolume = info.toFloat(data, 0.0F, 10.0F);
            case "soundpitch" -> info.soundPitch = info.toFloat(data, 0.0F, 10.0F);
            case "uav" -> {
                info.isUAV = info.toBool(data);
                info.isSmallUAV = false;
            }
            case "smalluav" -> {
                info.isUAV = info.toBool(data);
                info.isSmallUAV = true;
            }
            case "targetdrone" -> info.isTargetDrone = info.toBool(data);
            case "autopilotrot" -> info.autoPilotRot = info.toFloat(data, -5.0F, 5.0F);
            case "ongroundpitch" -> info.onGroundPitch = -info.toFloat(data, -90.0F, 90.0F);
            case "enablegunnermode" -> info.isEnableGunnerMode = info.toBool(data);
            case "hideentity" -> info.hideEntity = info.toBool(data);
            case "smoothshading" -> info.smoothShading = info.toBool(data);
            case "concurrentgunnermode" -> info.isEnableConcurrentGunnerMode = info.toBool(data);
            case "addpartweaponbay", "addpartslideweaponbay", "addpartturretweaponbay" -> {
                boolean turret = item.equalsIgnoreCase("AddPartTurretWeaponBay");
                boolean slide = !turret && item.equalsIgnoreCase("AddPartSlideWeaponBay");
                String[] sx = data.split("\\s*,\\s*");
                String modelPrefix = turret ? "weaponwb" : "wb";
                List<WeaponBay> targetList = turret ? info.partTurretWeaponBay : info.partWeaponBay;
                if (slide) {
                    if (sx.length >= 4) {
                        targetList.add(new WeaponBay(sx[0].trim().toLowerCase(Locale.ROOT), info.toFloat(sx[1]),
                                info.toFloat(sx[2]), info.toFloat(sx[3]), 0.0F, 0.0F, 0.0F, 90.0F,
                                modelPrefix + targetList.size(), true));
                    }
                } else if (sx.length >= 7) {
                    float mx = sx.length >= 8 ? info.toFloat(sx[7], -180.0F, 180.0F) : 90.0F;
                    targetList.add(new WeaponBay(sx[0].trim().toLowerCase(Locale.ROOT), info.toFloat(sx[1]),
                            info.toFloat(sx[2]), info.toFloat(sx[3]), info.toFloat(sx[4]), info.toFloat(sx[5]),
                            info.toFloat(sx[6]), mx / 90.0F, modelPrefix + targetList.size(), false));
                }
            }
            case "addparthatch", "addpartslidehatch" -> {
                boolean slide = item.equals("addpartslidehatch");
                String[] sx = data.split("\\s*,\\s*");
                if (slide) {
                    if (sx.length >= 3) {
                        info.hatchList.add(new Hatch(info.toFloat(sx[0]), info.toFloat(sx[1]), info.toFloat(sx[2]),
                                0.0F, 0.0F, 0.0F, 90.0F, "hatch" + info.hatchList.size(), true));
                    }
                } else if (sx.length >= 6) {
                    float mx = sx.length >= 7 ? info.toFloat(sx[6], -180.0F, 180.0F) : 90.0F;
                    info.hatchList.add(new Hatch(info.toFloat(sx[0]), info.toFloat(sx[1]), info.toFloat(sx[2]),
                            info.toFloat(sx[3]), info.toFloat(sx[4]), info.toFloat(sx[5]), mx,
                            "hatch" + info.hatchList.size(), false));
                }
            }
            case "addpartcanopy", "addpartslidecanopy" -> {
                String[] sx = data.split("\\s*,\\s*");
                boolean slide = item.equals("addpartslidecanopy");
                int canopyNum = info.canopyList.size();
                if (canopyNum > 0) {
                    canopyNum--;
                }

                if (slide) {
                    if (sx.length >= 3) {
                        Canopy c = new Canopy(info.toFloat(sx[0]), info.toFloat(sx[1]), info.toFloat(sx[2]), 0.0F,
                                0.0F, 0.0F, 90.0F, "canopy" + canopyNum, true);
                        info.canopyList.add(c);
                        if (canopyNum == 0) {
                            c = new Canopy(info.toFloat(sx[0]), info.toFloat(sx[1]), info.toFloat(sx[2]), 0.0F, 0.0F,
                                    0.0F, 90.0F, "canopy", true);
                            info.canopyList.add(c);
                        }
                    }
                } else if (sx.length >= 6) {
                    float mx = sx.length >= 7 ? info.toFloat(sx[6], -180.0F, 180.0F) : 90.0F;
                    mx /= 90.0F;
                    Canopy c = new Canopy(info.toFloat(sx[0]), info.toFloat(sx[1]), info.toFloat(sx[2]),
                            info.toFloat(sx[3]), info.toFloat(sx[4]), info.toFloat(sx[5]), mx, "canopy" + canopyNum,
                            false);
                    info.canopyList.add(c);
                    if (canopyNum == 0) {
                        c = new Canopy(info.toFloat(sx[0]), info.toFloat(sx[1]), info.toFloat(sx[2]),
                                info.toFloat(sx[3]), info.toFloat(sx[4]), info.toFloat(sx[5]), mx, "canopy", false);
                        info.canopyList.add(c);
                    }
                }
            }
            case "addpartlg", "addpartsliderotlg", "addpartlgrev", "addpartlghatch" -> {
                String[] sxx = data.split("\\s*,\\s*");
                if (!item.equalsIgnoreCase("AddPartSlideRotLG") && sxx.length >= 6) {
                    float maxRot = sxx.length >= 7 ? info.toFloat(sxx[6], -180.0F, 180.0F) : 90.0F;
                    maxRot /= 90.0F;
                    AircraftInfo.LandingGear n = new AircraftInfo.LandingGear(info.toFloat(sxx[0]),
                            info.toFloat(sxx[1]), info.toFloat(sxx[2]), info.toFloat(sxx[3]), info.toFloat(sxx[4]),
                            info.toFloat(sxx[5]), "lg" + info.landingGear.size(), maxRot,
                            item.equalsIgnoreCase("AddPartLgRev"), item.equalsIgnoreCase("AddPartLGHatch"));
                    if (sxx.length >= 8) {
                        n.enableRot2 = true;
                        n.maxRotFactor2 = sxx.length >= 11 ? info.toFloat(sxx[10], -180.0F, 180.0F) : 90.0F;
                        n.maxRotFactor2 /= 90.0F;
                        n.rot2 = new Vec3(info.toFloat(sxx[7]), info.toFloat(sxx[8]), info.toFloat(sxx[9]));
                    }

                    info.landingGear.add(n);
                }

                if (item.equalsIgnoreCase("AddPartSlideRotLG") && sxx.length >= 9) {
                    float maxRot = sxx.length >= 10 ? info.toFloat(sxx[9], -180.0F, 180.0F) : 90.0F;
                    maxRot /= 90.0F;
                    AircraftInfo.LandingGear n = new AircraftInfo.LandingGear(info.toFloat(sxx[3]),
                            info.toFloat(sxx[4]), info.toFloat(sxx[5]), info.toFloat(sxx[6]), info.toFloat(sxx[7]),
                            info.toFloat(sxx[8]), "lg" + info.landingGear.size(), maxRot, false, false);
                    n.slide = new Vec3(info.toFloat(sxx[0]), info.toFloat(sxx[1]), info.toFloat(sxx[2]));
                    info.landingGear.add(n);
                }
            }
            case "addpartthrottle" -> {
                String[] sxxx = data.split("\\s*,\\s*");
                if (sxxx.length >= 7) {
                    float x = sxxx.length >= 8 ? info.toFloat(sxxx[7]) : 0.0F;
                    float yx = sxxx.length >= 9 ? info.toFloat(sxxx[8]) : 0.0F;
                    float z = sxxx.length >= 10 ? info.toFloat(sxxx[9]) : 0.0F;
                    info.partThrottle.add(new AircraftInfo.Throttle(info.toFloat(sxxx[0]), info.toFloat(sxxx[1]),
                            info.toFloat(sxxx[2]), info.toFloat(sxxx[3]), info.toFloat(sxxx[4]),
                            info.toFloat(sxxx[5]), info.toFloat(sxxx[6]), "throttle" + info.partThrottle.size(), x,
                            yx, z));
                }
            }
            case "addpartrotation", "addpartturretrotation" -> {
                String[] sxxx = data.split("\\s*,\\s*");
                if (sxxx.length >= 6) {
                    boolean always = sxxx.length < 7 || info.toBool(sxxx[6]);
                    if (item.equalsIgnoreCase("AddPartTurretRotation")) {
                        info.partTurretRotPart.add(new AircraftInfo.TurretRotPart(info.toFloat(sxxx[0]),
                                info.toFloat(sxxx[1]), info.toFloat(sxxx[2]), info.toFloat(sxxx[3]),
                                info.toFloat(sxxx[4]), info.toFloat(sxxx[5]), always,
                                "weaponrotpart" + info.partTurretRotPart.size()));
                    } else if (sxxx.length >= 7) {
                        // Upstream numbers rotating parts by the throttle count; model groups depend on it.
                        info.partRotPart.add(new AircraftInfo.RotPart(info.toFloat(sxxx[0]), info.toFloat(sxxx[1]),
                                info.toFloat(sxxx[2]), info.toFloat(sxxx[3]), info.toFloat(sxxx[4]),
                                info.toFloat(sxxx[5]), info.toFloat(sxxx[6]),
                                sxxx.length < 8 || info.toBool(sxxx[7]), "rotpart" + info.partThrottle.size()));
                    }
                }
            }
            case "addpartcamera" -> {
                String[] sxxx = data.split("\\s*,\\s*");
                if (sxxx.length >= 3) {
                    boolean ys = sxxx.length < 4 || info.toBool(sxxx[3]);
                    boolean ps = sxxx.length >= 5 && info.toBool(sxxx[4]);
                    info.cameraList.add(new AircraftInfo.Camera(info.toFloat(sxxx[0]), info.toFloat(sxxx[1]),
                            info.toFloat(sxxx[2]), 0.0F, -1.0F, 0.0F, "camera" + info.cameraList.size(), ys, ps));
                }
            }
            case "addpartwheel" -> {
                String[] sxxx = info.splitParam(data);
                if (sxxx.length >= 3) {
                    float rd = sxxx.length >= 4 ? info.toFloat(sxxx[3], -1800.0F, 1800.0F) : 0.0F;
                    float rx = sxxx.length >= 7 ? info.toFloat(sxxx[4]) : 0.0F;
                    float ry = sxxx.length >= 7 ? info.toFloat(sxxx[5]) : 1.0F;
                    float rz = sxxx.length >= 7 ? info.toFloat(sxxx[6]) : 0.0F;
                    float px = sxxx.length >= 10 ? info.toFloat(sxxx[7]) : info.toFloat(sxxx[0]);
                    float py = sxxx.length >= 10 ? info.toFloat(sxxx[8]) : info.toFloat(sxxx[1]);
                    float pz = sxxx.length >= 10 ? info.toFloat(sxxx[9]) : info.toFloat(sxxx[2]);
                    info.partWheel.add(new AircraftInfo.PartWheel(info.toFloat(sxxx[0]), info.toFloat(sxxx[1]),
                            info.toFloat(sxxx[2]), rx, ry, rz, rd, px, py, pz, "wheel" + info.partWheel.size()));
                }
            }
            case "addpartsteeringwheel" -> {
                String[] sxxx = info.splitParam(data);
                if (sxxx.length >= 7) {
                    info.partSteeringWheel.add(new AircraftInfo.PartWheel(info.toFloat(sxxx[0]),
                            info.toFloat(sxxx[1]), info.toFloat(sxxx[2]), info.toFloat(sxxx[3]),
                            info.toFloat(sxxx[4]), info.toFloat(sxxx[5]), info.toFloat(sxxx[6]),
                            "steering_wheel" + info.partSteeringWheel.size()));
                }
            }
            case "addtrackroller" -> {
                String[] sxxx = info.splitParam(data);
                if (sxxx.length >= 3) {
                    info.partTrackRoller.add(new AircraftInfo.TrackRoller(info.toFloat(sxxx[0]),
                            info.toFloat(sxxx[1]), info.toFloat(sxxx[2]),
                            "track_roller" + info.partTrackRoller.size()));
                }
            }
            case "addcrawlertrack" -> {
                // Upstream also added the null that an outline with fewer than four points gives.
                AircraftInfo.CrawlerTrack track = info.createCrawlerTrack(data,
                        "crawler_track" + info.partCrawlerTrack.size());
                if (track != null) {
                    info.partCrawlerTrack.add(track);
                }
            }
            case "pivotturnthrottle" -> info.pivotTurnThrottle = info.toFloat(data, 0.0F, 1.0F);
            case "trackrollerrot" -> info.trackRollerRot = info.toFloat(data, -10000.0F, 10000.0F);
            case "partwheelrot" -> info.partWheelRot = info.toFloat(data, -10000.0F, 10000.0F);
            case "camerazoom" -> info.cameraZoom = info.toInt(data, 1, 10);
            case "defaultfreelook" -> info.defaultFreelook = info.toBool(data);
            case "boundingbox" -> {
                String[] sxxx = data.split("\\s*,\\s*");
                if (sxxx.length >= 5) {
                    float df = sxxx.length >= 6 ? info.toFloat(sxxx[5]) : 1.0F;
                    BoundingBoxInfo c = new BoundingBoxInfo(info.toFloat(sxxx[0]), info.toFloat(sxxx[1]),
                            info.toFloat(sxxx[2]), info.toFloat(sxxx[3]), info.toFloat(sxxx[4]), df);
                    info.extraBoundingBox.add(c);
                    updateMarkerBounds(info, c);
                }
            }
            case "boundingerabox" -> {
                String[] sxxx = data.split("\\s*,\\s*");
                if (sxxx.length >= 11) {
                    BoundingBoxInfo bb = new BoundingBoxInfo(info.toFloat(sxxx[0]), info.toFloat(sxxx[1]),
                            info.toFloat(sxxx[2]), info.toFloat(sxxx[3]), info.toFloat(sxxx[4]),
                            info.toFloat(sxxx[5]), info.toFloat(sxxx[6]));
                    bb.isERA = true;
                    bb.eraExplosion = info.toFloat(sxxx[7], 0.0F, 100000.0F);
                    try {
                        bb.boundingBoxType = BoundingBoxInfo.Type.valueOf(sxxx[8].toUpperCase(Locale.ROOT));
                    } catch (IllegalArgumentException ex) {
                        bb.boundingBoxType = BoundingBoxInfo.Type.DEFAULT;
                    }
                    bb.name = sxxx[9];
                    bb.eraMinDamage = info.toFloat(sxxx[10], 0.0F, 100000.0F);
                    info.extraBoundingBox.add(bb);
                    updateMarkerBounds(info, bb);
                }
            }
            case "addsign" -> {
                String[] sxxx = info.splitParam(data);
                if (sxxx.length >= 5) {
                    float sx = info.toFloat(sxxx[0]);
                    float sy = info.toFloat(sxxx[1]);
                    float sz = info.toFloat(sxxx[2]);
                    String signName = sxxx[3].trim();
                    float signSize = info.toFloat(sxxx[4], 0.1F, 1000.0F);
                    boolean perspectiveScale = sxxx.length < 6 || info.toBool(sxxx[5], true);
                    info.signMarkers.add(new SignMarker(sx, sy, sz, signName, signSize, perspectiveScale));
                }
            }
            case "rotorspeed" -> {
                info.rotorSpeed = info.toFloat(data, -10000.0F, 10000.0F);
                if (info.rotorSpeed > 0.01) {
                    info.rotorSpeed = (float) (info.rotorSpeed - 0.01);
                }
                if (info.rotorSpeed < -0.01) {
                    info.rotorSpeed = (float) (info.rotorSpeed + 0.01);
                }
            }
            case "ongroundpitchfactor" -> info.onGroundPitchFactor = info.toFloat(data, 0.0F, 180.0F);
            case "ongroundrollfactor" -> info.onGroundRollFactor = info.toFloat(data, 0.0F, 180.0F);
            default -> {
                // Unknown legacy keys are ignored, as upstream does.
            }
        }
    }

    /**
     * {@code ImpactAngleCoefficient}: comma-separated {@code angle,coeff} pairs; a negative
     * coefficient marks the ricochet start angle and ends the list.
     */
    private static void parseImpactAngleCoefficient(AircraftInfo info, String data) {
        info.impactAngleThresholds.clear();
        info.impactAngleCoefficients.clear();
        info.impactRicochetStartAngle = 181.0F;
        if (data == null || data.trim().isEmpty()) {
            return;
        }
        String[] s = data.split("\\s*,\\s*");
        for (int i = 0; i + 1 < s.length; i += 2) {
            float angle = info.toFloat(s[i], 0.0F, 180.0F);
            float coeff = info.toFloat(s[i + 1], -1.0F, 1000.0F);
            if (coeff < 0.0F) {
                info.impactRicochetStartAngle = angle;
                break;
            }
            info.impactAngleThresholds.add(angle);
            info.impactAngleCoefficients.add(coeff);
        }
        if (info.impactAngleThresholds.isEmpty()) {
            info.impactAngleThresholds.add(0.0F);
            info.impactAngleCoefficients.add(1.0F);
        } else if (info.impactAngleThresholds.get(0) > 0.0F) {
            info.impactAngleThresholds.add(0, 0.0F);
            info.impactAngleCoefficients.add(0, info.impactAngleCoefficients.get(0));
        }
    }

    /** Grows the marker and draw bounds to include an extra bounding box, as upstream. */
    private static void updateMarkerBounds(AircraftInfo info, BoundingBoxInfo c) {
        AABB box = c.getBoundingBox();
        if (box.maxY > info.markerHeight) {
            info.markerHeight = (float) box.maxY;
        }

        info.markerWidth = (float) Math.max(info.markerWidth, Math.abs(box.maxX) / 2.0);
        info.markerWidth = (float) Math.max(info.markerWidth, Math.abs(box.minX) / 2.0);
        info.markerWidth = (float) Math.max(info.markerWidth, Math.abs(box.maxZ) / 2.0);
        info.markerWidth = (float) Math.max(info.markerWidth, Math.abs(box.minZ) / 2.0);
        info.bbZmin = (float) Math.min(info.bbZmin, box.minZ);
        // Upstream takes the minimum here too.
        info.bbZmax = (float) Math.min(info.bbZmax, box.maxZ);
    }

    // ===================================================================================
    // Helicopter
    // ===================================================================================

    private void applyHelicopterLine(HeliInfo info, int lineNumber, String item, String data) {
        switch (item) {
            case "enablefoldblade" -> info.isEnableFoldBlade = info.toBool(data);
            case "addrotor", "addrotorold" -> {
                String[] s = data.split("\\s*,\\s*");
                if (s.length == 8 || s.length == 9) {
                    boolean cfb = s.length == 9 && info.toBool(s[8]);
                    info.rotorList.add(new HeliInfo.Rotor(info.toInt(s[0]), info.toInt(s[1]), info.toFloat(s[2]),
                            info.toFloat(s[3]), info.toFloat(s[4]), info.toFloat(s[5]), info.toFloat(s[6]),
                            info.toFloat(s[7]), "blade" + info.rotorList.size(), cfb, item.equals("addrotorold")));
                }
            }
            default -> {
            }
        }
    }

    // ===================================================================================
    // Plane and ship (same part syntax)
    // ===================================================================================

    private void applyPlaneLine(PlaneInfo info, int lineNumber, String item, String data) {
        if (applyWingedLine(info.rotorList, info.wingList, info.nozzles, info, item, data)) {
            return;
        }
        switch (item) {
            case "variablesweepwing" -> info.isVariableSweepWing = info.toBool(data);
            case "sweepwingspeed" -> info.sweepWingSpeed = info.toFloat(data, 0.0F, 5.0F);
            case "enablevtol" -> info.isEnableVtol = info.toBool(data);
            case "defaultvtol" -> info.isDefaultVtol = info.toBool(data);
            case "vtolyaw" -> info.vtolYaw = info.toFloat(data, 0.0F, 1.0F);
            case "vtolpitch" -> info.vtolPitch = info.toFloat(data, 0.01F, 1.0F);
            case "enableautopilot" -> info.isEnableAutoPilot = info.toBool(data);
            case "addexhaustflame" -> {
                String[] s = data.split("\\s*,\\s*");
                if (s.length == 10) {
                    String modelName = s[0].trim();
                    String texturePrefix = s[1].trim();
                    float x = info.toFloat(s[2]);
                    float y = info.toFloat(s[3]);
                    float z = info.toFloat(s[4]);
                    float rx = info.toFloat(s[5]);
                    float ry = info.toFloat(s[6]);
                    float rz = info.toFloat(s[7]);
                    float degreeYaw = info.toFloat(s[8]);
                    int delay = info.toInt(s[9]);
                    info.exhaustFlames.add(new PlaneInfo.ExhaustFlame(modelName, texturePrefix, x, y, z, rx, ry, rz,
                            degreeYaw, delay));
                }
            }
            default -> {
            }
        }
    }

    private void applyShipLine(ShipInfo info, int lineNumber, String item, String data) {
        if (applyWingedLine(info.rotorList, info.wingList, info.nozzles, info, item, data)) {
            return;
        }
        switch (item) {
            case "variablesweepwing" -> info.isVariableSweepWing = info.toBool(data);
            case "sweepwingspeed" -> info.sweepWingSpeed = info.toFloat(data, 0.0F, 5.0F);
            case "enablevtol" -> info.isEnableVtol = info.toBool(data);
            case "defaultvtol" -> info.isDefaultVtol = info.toBool(data);
            case "vtolyaw" -> info.vtolYaw = info.toFloat(data, 0.0F, 1.0F);
            case "vtolpitch" -> info.vtolPitch = info.toFloat(data, 0.01F, 1.0F);
            case "enableautopilot" -> info.isEnableAutoPilot = info.toBool(data);
            default -> {
            }
        }
    }

    /** Rotor, blade, wing, pylon and nozzle lines, which planes and ships share. */
    private static boolean applyWingedLine(List<PlaneInfo.Rotor> rotorList, List<PlaneInfo.Wing> wingList,
            List<AircraftInfo.DrawnPart> nozzles, AircraftInfo info, String item, String data) {
        switch (item) {
            case "addpartrotor" -> {
                String[] s = data.split("\\s*,\\s*");
                if (s.length >= 6) {
                    float m = s.length >= 7 ? info.toFloat(s[6], -180.0F, 180.0F) / 90.0F : 1.0F;
                    rotorList.add(new PlaneInfo.Rotor(info.toFloat(s[0]), info.toFloat(s[1]), info.toFloat(s[2]),
                            info.toFloat(s[3]), info.toFloat(s[4]), info.toFloat(s[5]), m, "rotor" + rotorList.size()));
                }
            }
            case "addblade" -> {
                int idx = rotorList.size() - 1;
                PlaneInfo.Rotor r = !rotorList.isEmpty() ? rotorList.get(idx) : null;
                if (r != null) {
                    String[] s = data.split("\\s*,\\s*");
                    if (s.length == 8) {
                        r.blades.add(new PlaneInfo.Blade(info.toInt(s[0]), info.toInt(s[1]), info.toFloat(s[2]),
                                info.toFloat(s[3]), info.toFloat(s[4]), info.toFloat(s[5]), info.toFloat(s[6]),
                                info.toFloat(s[7]), "blade" + idx));
                    }
                }
            }
            case "addpartwing" -> {
                String[] s = data.split("\\s*,\\s*");
                if (s.length == 7) {
                    wingList.add(new PlaneInfo.Wing(info.toFloat(s[0]), info.toFloat(s[1]), info.toFloat(s[2]),
                            info.toFloat(s[3]), info.toFloat(s[4]), info.toFloat(s[5]), info.toFloat(s[6]),
                            "wing" + wingList.size()));
                }
            }
            case "addpartpylon" -> {
                String[] s = data.split("\\s*,\\s*");
                if (s.length >= 7 && !wingList.isEmpty()) {
                    PlaneInfo.Wing w = wingList.get(wingList.size() - 1);
                    if (w.pylonList == null) {
                        w.pylonList = new ArrayList<>();
                    }
                    w.pylonList.add(new PlaneInfo.Pylon(info.toFloat(s[0]), info.toFloat(s[1]), info.toFloat(s[2]),
                            info.toFloat(s[3]), info.toFloat(s[4]), info.toFloat(s[5]), info.toFloat(s[6]),
                            w.modelName + "_pylon" + w.pylonList.size()));
                }
            }
            case "addpartnozzle" -> {
                String[] s = data.split("\\s*,\\s*");
                if (s.length == 6) {
                    nozzles.add(new AircraftInfo.DrawnPart(info.toFloat(s[0]), info.toFloat(s[1]), info.toFloat(s[2]),
                            info.toFloat(s[3]), info.toFloat(s[4]), info.toFloat(s[5]), "nozzle" + nozzles.size()));
                }
            }
            default -> {
                return false;
            }
        }
        return true;
    }

    // ===================================================================================
    // Tank
    // ===================================================================================

    private void applyTankLine(TankInfo info, int lineNumber, String item, String data) {
        switch (item) {
            case "weighttype" -> {
                String d = data.toLowerCase(Locale.ROOT);
                info.weightType = d.equals("car") ? 1 : (d.equals("tank") ? 2 : 0);
            }
            case "weightedcenterz" -> info.weightedCenterZ = info.toFloat(data, -1000.0F, 1000.0F);
            default -> {
            }
        }
    }

    // ===================================================================================
    // Vehicle
    // ===================================================================================

    private void applyVehicleLine(VehicleInfo info, int lineNumber, String item, String data) {
        switch (item) {
            case "canmove" -> info.isEnableMove = info.toBool(data);
            case "canrotation" -> info.isEnableRot = info.toBool(data);
            case "rotationpitchmin" -> this.applyAircraftLine(info, lineNumber, "minrotationpitch", data);
            case "rotationpitchmax" -> this.applyAircraftLine(info, lineNumber, "maxrotationpitch", data);
            case "addpart" -> {
                String[] s = data.split("\\s*,\\s*");
                if (s.length >= 7) {
                    float rb = s.length >= 8 ? info.toFloat(s[7]) : 0.0F;
                    info.partList.add(new VehicleInfo.VPart(info.toFloat(s[4]), info.toFloat(s[5]), info.toFloat(s[6]),
                            "part" + info.partList.size(), info.toBool(s[0]), info.toBool(s[1]), info.toBool(s[2]),
                            info.toInt(s[3]), rb));
                }
            }
            case "addchildpart" -> {
                if (info.partList.isEmpty()) {
                    return;
                }
                String[] s = data.split("\\s*,\\s*");
                if (s.length >= 7) {
                    float rb = s.length >= 8 ? info.toFloat(s[7]) : 0.0F;
                    VehicleInfo.VPart p = info.partList.get(info.partList.size() - 1);
                    if (p.child == null) {
                        p.child = new ArrayList<>();
                    }
                    p.child.add(new VehicleInfo.VPart(info.toFloat(s[4]), info.toFloat(s[5]), info.toFloat(s[6]),
                            p.modelName + "_" + p.child.size(), info.toBool(s[0]), info.toBool(s[1]),
                            info.toBool(s[2]), info.toInt(s[3]), rb));
                }
            }
            default -> {
            }
        }
    }

    // ===================================================================================
    // Weapon
    // ===================================================================================

    private void applyWeaponLine(WeaponInfo info, int lineNumber, String item, String data) {
        switch (item) {
            case "displayname" -> info.displayName = data;
            case "type" -> {
                info.type = data.toLowerCase(Locale.ROOT);
                if (info.type.equalsIgnoreCase("bomb") || info.type.equalsIgnoreCase("dispenser")) {
                    info.gravity = -0.03F;
                    info.gravityInWater = -0.03F;
                }
            }
            case "group" -> info.group = data.toLowerCase(Locale.ROOT).trim();
            case "power" -> info.power = info.toInt(data);
            case "sound" -> info.fireSound = SoundDefinitions.parseSound(data);
            case "acceleration" -> info.acceleration = info.toFloat(data, 0.0F, 100.0F);
            case "accelerationinwater" -> info.accelerationInWater = info.toFloat(data, 0.0F, 100.0F);
            case "gravity" -> info.gravity = info.toFloat(data, -50.0F, 50.0F);
            case "gravityinwater" -> info.gravityInWater = info.toFloat(data, -50.0F, 50.0F);
            case "velocityinwater" -> info.velocityInWater = info.toFloat(data);
            case "explosion" -> info.explosion = info.toInt(data, 0, 200);
            case "explosionblock" -> info.explosionBlock = info.toInt(data, 0, 200);
            case "explosioninwater" -> info.explosionInWater = info.toInt(data, 0, 50);
            case "explosionaltitude" -> info.explosionAltitude = info.toInt(data, 0, 100);
            case "canairburst" -> info.canAirburst = info.toBool(data);
            case "explosionairburst" -> info.explosionAirburst = info.toInt(data, 0, 50);
            case "timefuse" -> info.timeFuse = info.toInt(data, 0, 100000);
            case "delayfuse" -> info.delayFuse = info.toInt(data, 0, 100000);
            case "bound" -> info.bound = info.toFloat(data, 0.0F, 100000.0F);
            case "flaming" -> info.flaming = info.toBool(data);
            case "displaymortardistance" -> info.displayMortarDistance = info.toBool(data);
            case "fixcamerapitch" -> info.fixCameraPitch = info.toBool(data);
            case "camerarotationspeedpitch" -> info.cameraRotationSpeedPitch = info.toFloat(data, 0.0F, 100.0F);
            case "sight" -> {
                String d = data.toLowerCase(Locale.ROOT);
                if (d.equals("movesight")) {
                    info.sight = SightType.ROCKET;
                }
                if (d.equals("missilesight")) {
                    info.sight = SightType.LOCK;
                }
            }
            case "zoom" -> {
                String[] s = info.splitParam(data);
                if (s.length > 0) {
                    info.zoom = new float[s.length];
                    for (int i = 0; i < s.length; i++) {
                        info.zoom[i] = info.toFloat(s[i], 0.1F, 10.0F);
                    }
                }
            }
            case "delay" -> info.delay = info.toInt(data, 0, 100000);
            case "reloadtime" -> info.reloadTime = info.toInt(data, 3, 1000);
            case "round" -> info.round = info.toInt(data, 1, 30000);
            case "maxammo" -> info.maxAmmo = info.toInt(data, 0, 30000);
            case "suppliednum" -> info.suppliedNum = info.toInt(data, 1, 30000);
            case "item" -> {
                String[] s = data.split("\\s*,\\s*");
                if (s.length >= 2 && !s[1].isEmpty() && info.roundItems.size() < 3) {
                    int n = info.toInt(s[0], 1, 64);
                    if (n > 0) {
                        int damage = s.length >= 3 ? info.toInt(s[2], 0, 100000000) : 0;
                        info.roundItems.add(RoundItem.of(n, s[1].toLowerCase(Locale.ROOT).trim(), damage));
                    }
                }
            }
            case "sounddelay" -> info.soundDelay = info.toInt(data, 0, 1000);
            case "soundvolume" -> info.soundVolume = info.toFloat(data, 0.0F, 1000.0F);
            case "soundpitch" -> info.soundPitch = info.toFloat(data, 0.0F, 1.0F);
            case "soundpitchrandom" -> info.soundPitchRandom = info.toFloat(data, 0.0F, 1.0F);
            case "locktime" -> info.lockTime = info.toInt(data, 2, 1000);
            case "ridableonly" -> info.ridableOnly = info.toBool(data);
            case "proximityfusedist" -> info.proximityFuseDist = info.toFloat(data, 0.0F, 2000.0F);
            case "rigiditytime" -> info.rigidityTime = info.toInt(data, 0, 1000000);
            case "accuracy" -> info.accuracy = info.toFloat(data, 0.0F, 1000.0F);
            case "bomblet" -> info.bomblet = info.toInt(data, 0, 1000);
            case "bombletstime" -> info.bombletSTime = info.toInt(data, 0, 1000);
            case "bombletdiff" -> info.bombletDiff = info.toFloat(data, 0.0F, 1000.0F);
            case "recoilbufcount" -> {
                String[] s = info.splitParam(data);
                if (s.length >= 1) {
                    info.recoilBufCount = info.toInt(s[0], 1, 10000);
                }
                if (s.length >= 2 && info.recoilBufCount > 2) {
                    info.recoilBufCountSpeed = info.toInt(s[1], 1, 10000) - 1;
                    if (info.recoilBufCountSpeed > info.recoilBufCount / 2) {
                        info.recoilBufCountSpeed = info.recoilBufCount / 2;
                    }
                }
            }
            case "modenum" -> info.modeNum = info.toInt(data, 0, 1000);
            case "fixmode" -> info.fixMode = info.toInt(data, 0, 10);
            case "piercing" -> info.piercing = info.toInt(data, 0, 100000);
            case "heatcount" -> info.heatCount = info.toInt(data, 0, 100000);
            case "maxheatcount" -> info.maxHeatCount = info.toInt(data, 0, 100000);
            case "modelbullet" -> info.bulletModelName = data.toLowerCase(Locale.ROOT).trim();
            case "modelbomblet" -> info.bombletModelName = data.toLowerCase(Locale.ROOT).trim();
            case "modelbulletendtick" -> {
                String[] s = info.splitParam(data);
                if (s.length >= 2) {
                    info.bulletModelEndTick = info.toInt(s[0], -1, 1000000);
                    info.bulletModelNameEnd = s[1].toLowerCase(Locale.ROOT).trim();
                }
            }
            case "fae" -> info.isFAE = info.toBool(data);
            case "guidedtorpedo" -> info.isGuidedTorpedo = info.toBool(data);
            case "destruct" -> info.destruct = info.toBool(data);
            case "addmuzzleflash" -> {
                String[] sx = info.splitParam(data);
                if (sx.length >= 7) {
                    if (info.listMuzzleFlash == null) {
                        info.listMuzzleFlash = new ArrayList<>();
                    }
                    info.listMuzzleFlash.add(new MuzzleFlash(info.toFloat(sx[0]), info.toFloat(sx[1]), 0.0F,
                            info.toInt(sx[2]), info.toFloat(sx[3]) / 255.0F, info.toFloat(sx[4]) / 255.0F,
                            info.toFloat(sx[5]) / 255.0F, info.toFloat(sx[6]) / 255.0F, 1));
                }
            }
            case "addmuzzleflashsmoke" -> {
                String[] sx = info.splitParam(data);
                if (sx.length >= 9) {
                    if (info.listMuzzleFlashSmoke == null) {
                        info.listMuzzleFlashSmoke = new ArrayList<>();
                    }
                    info.listMuzzleFlashSmoke.add(new MuzzleFlash(info.toFloat(sx[0]), info.toFloat(sx[2]),
                            info.toFloat(sx[3]), info.toInt(sx[4]), info.toFloat(sx[5]) / 255.0F,
                            info.toFloat(sx[6]) / 255.0F, info.toFloat(sx[7]) / 255.0F, info.toFloat(sx[8]) / 255.0F,
                            info.toInt(sx[1], 1, 1000)));
                }
            }
            case "trajectoryparticle" -> {
                info.trajectoryParticleName = data.toLowerCase(Locale.ROOT).trim();
                if (info.trajectoryParticleName.equalsIgnoreCase("none")) {
                    info.trajectoryParticleName = "";
                }
            }
            case "trajectoryparticlestarttick" -> info.trajectoryParticleStartTick = info.toInt(data, 0, 10000);
            case "trajectoryparticleendtick" -> info.trajectoryParticleEndTick = info.toInt(data, -1, 10000);
            case "disablesmoke" -> info.disableSmoke = info.toBool(data);
            case "setcartridge" -> {
                String[] sx = data.split("\\s*,\\s*");
                if (sx.length > 0 && !sx[0].isEmpty()) {
                    float ac = sx.length >= 2 ? info.toFloat(sx[1]) : 0.0F;
                    float yw = sx.length >= 3 ? info.toFloat(sx[2]) : 0.0F;
                    float pt = sx.length >= 4 ? info.toFloat(sx[3]) : 0.0F;
                    float sc = sx.length >= 5 ? info.toFloat(sx[4]) : 1.0F;
                    float gr = sx.length >= 6 ? info.toFloat(sx[5]) : -0.04F;
                    float bo = sx.length >= 7 ? info.toFloat(sx[6]) : 0.5F;
                    info.cartridge = new CartridgeInfo(sx[0].toLowerCase(Locale.ROOT), ac, yw, pt, bo, gr, sc);
                }
            }
            case "bulletcolorinwater", "bulletcolor", "smokecolor" -> {
                String[] sx = data.split("\\s*,\\s*");
                if (sx.length >= 4) {
                    ArgbColor c = new ArgbColor(0.003921569F * info.toInt(sx[0], 0, 255),
                            0.003921569F * info.toInt(sx[1], 0, 255), 0.003921569F * info.toInt(sx[2], 0, 255),
                            0.003921569F * info.toInt(sx[3], 0, 255));
                    if (item.equalsIgnoreCase("BulletColorInWater")) {
                        info.colorInWater = c;
                    } else {
                        info.color = c;
                    }
                }
            }
            case "maxdegreeofmissile" -> info.maxDegreeOfMissile = info.toInt(data, 0, 100000);
            case "initmaxdegreeofmissile" -> {
                String[] s = info.splitParam(data);
                if (s.length >= 2) {
                    info.initMaxDegreeTick = info.toInt(s[0], 0, 100000);
                    info.initMaxDegreeOfMissile = info.toInt(s[1], 0, 100000);
                } else {
                    info.initMaxDegreeTick = 0;
                    info.initMaxDegreeOfMissile = info.maxDegreeOfMissile;
                }
            }
            case "tickendhoming" -> info.tickEndHoming = info.toInt(data, -1, 100000);
            case "flakparticlescrack" -> info.flakParticlesCrack = info.toInt(data, 0, 300);
            case "particlesflak" -> info.numParticlesFlak = info.toInt(data, 0, 100);
            case "flakparticlesdiff" -> info.flakParticlesDiff = info.toFloat(data);
            case "isradarmissile" -> info.isRadarMissile = info.toBool(data);
            case "isheatseekermissile" -> info.isHeatSeekerMissile = info.toBool(data);
            case "isgpsmissile" -> info.isGPSMissile = info.toBool(data);
            case "maxlockonrange" -> info.maxLockOnRange = info.toInt(data, 0, 2000);
            case "maxlockonangle" -> info.maxLockOnAngle = info.toInt(data, 0, 200);
            case "pdhdnmaxdegree" -> info.pdHDNMaxDegree = info.toFloat(data, -1, 90);
            case "pdhdnmaxdegreelockoutcount" -> info.pdHDNMaxDegreeLockOutCount = info.toInt(data, 0, 200);
            case "antiflarecount" -> info.antiFlareCount = info.toInt(data, -1, 200);
            case "numlockedchaffmax" -> info.numLockedChaffMax = info.toInt(data);
            case "lockminheight" -> info.lockMinHeight = info.toInt(data, -1, 100);
            case "passiveradar" -> info.passiveRadar = info.toBool(data);
            case "passiveradarlockoutcount" -> info.passiveRadarLockOutCount = info.toInt(data, 0, 200);
            case "laserguidance" -> info.laserGuidance = info.toBool(data);
            case "haslaserguidancepod" -> info.hasLaserGuidancePod = info.toBool(data);
            case "activeradar" -> info.activeRadar = info.toBool(data);
            case "semiactiveradar" -> info.semiActiveRadar = info.toBool(data);
            case "enableoffaxis" -> info.enableOffAxis = info.toBool(data);
            case "turningfactor", "laserstartdistance" -> info.turningFactor = info.toDouble(data);
            case "initturningfactor" -> {
                String[] s = info.splitParam(data);
                if (s.length >= 2) {
                    info.initTurningFactorTick = info.toInt(s[0], 0, 100000);
                    info.initTurningFactor = info.toDouble(s[1]);
                } else {
                    info.initTurningFactorTick = 0;
                    info.initTurningFactor = info.turningFactor;
                }
            }
            case "enablechunkloader" -> info.enableChunkLoader = info.toBool(data);
            case "scaninterval" -> info.scanInterval = info.toInt(data);
            case "weaponswitchcount" -> info.weaponSwitchCount = info.toInt(data);
            case "weaponswitchsound" -> info.weaponSwitchSound = SoundDefinitions.parseSound(data);
            case "recoilpitch" -> info.recoilPitch = info.toFloat(data);
            case "recoilyaw" -> info.recoilYaw = info.toFloat(data);
            case "recoilpitchrange" -> info.recoilPitchRange = info.toFloat(data);
            case "recoilyawrange" -> info.recoilYawRange = info.toFloat(data);
            case "recoilrecoverfactor" -> info.recoilRecoverFactor = info.toFloat(data);
            case "speedfactor" -> info.speedFactor = info.toFloat(data);
            case "speedfactorstarttick" -> info.speedFactorStartTick = info.toInt(data);
            case "speedfactorendtick" -> info.speedFactorEndTick = info.toInt(data);
            case "speeddependsaircraft" -> info.speedDependsAircraft = info.toBool(data);
            case "canlockmissile" -> info.canLockMissile = info.toBool(data);
            case "enablebvr" -> info.enableBVR = info.toBool(data);
            case "minrangebvr" -> info.minRangeBVR = info.toInt(data);
            case "predicttargetpos" -> info.predictTargetPos = info.toBool(data);
            case "smokesize" -> info.smokeSize = info.toFloat(data, 0.0F, 100.0F);
            case "smokenum" -> info.smokeNum = info.toInt(data, 1, 100);
            case "smokemaxage" -> info.smokeMaxAge = info.toInt(data, 2, 1000);
            case "dispenseitem" -> {
                String[] sx = data.split("\\s*,\\s*");
                if (sx.length >= 2) {
                    info.dispenseDamege = info.toInt(sx[1], 0, 100000000);
                }
                info.dispenseItemLoc = sx[0].toLowerCase(Locale.ROOT).trim();
            }
            case "dispenserange" -> info.dispenseRange = info.toInt(data, 1, 100);
            case "length" -> info.length = info.toInt(data, 1, 300);
            case "radius" -> info.radius = info.toInt(data, 1, 1000);
            case "target" -> {
                if (data.contains("block")) {
                    info.target = 64;
                } else {
                    info.target = 0;
                    info.target = info.target | (data.contains("planes") ? 32 : 0);
                    info.target = info.target | (data.contains("helicopters") ? 16 : 0);
                    info.target = info.target | (data.contains("vehicles") ? 8 : 0);
                    info.target = info.target | (data.contains("tanks") ? 8 : 0);
                    info.target = info.target | (data.contains("players") ? 4 : 0);
                    info.target = info.target | (data.contains("monsters") ? 2 : 0);
                    info.target = info.target | (data.contains("others") ? 1 : 0);
                }
            }
            case "marktime" -> info.markTime = info.toInt(data, 1, 30000) + 1;
            case "recoil" -> info.recoil = info.toFloat(data, 0.0F, 100.0F);
            case "damagefactor" -> {
                String[] sx = info.splitParam(data);
                if (sx.length >= 2) {
                    String target = DamageFactor.normalizeTarget(sx[0]);
                    // The TXT format only knows the built-in targets.
                    if (DamageFactor.isBuiltinTarget(target)) {
                        if (info.damageFactor == null) {
                            info.damageFactor = new DamageFactor();
                        }
                        info.damageFactor.add(target, info.toFloat(sx[1], 0.0F, 1000000.0F));
                    }
                }
            }
            // ---- Reforged explosion / damage extensions ----
            case "explosiondamagevsliving" -> info.explosionDamageVsLiving = info.toFloat(data);
            case "explosiondamagevsplayer" -> info.explosionDamageVsPlayer = info.toFloat(data);
            case "explosiondamagevsplane" -> info.explosionDamageVsPlane = info.toFloat(data);
            case "explosiondamagevsvehicle" -> info.explosionDamageVsVehicle = info.toFloat(data);
            case "explosiondamagevstank" -> info.explosionDamageVsTank = info.toFloat(data);
            case "explosiondamagevsheli" -> info.explosionDamageVsHeli = info.toFloat(data);
            case "explosiondamagevsship" -> info.explosionDamageVsShip = info.toFloat(data);
            case "explosionthroughwall" -> info.explosionThroughWall = info.toBool(data);
            case "explosionthroughwallfactor" -> info.explosionThroughWallFactor = info.toFloat(data, 0.0F, 1.0F);
            case "isnewexplosionbreak" -> info.isNewExplosionBreak = info.toBool(data);
            case "disabledestroyblock" -> info.disableDestroyBlock = info.toBool(data);
            case "crosstype" -> info.crossType = info.toInt(data);
            // ---- Reforged CCIP (continuously computed impact point) ----
            case "ccip" -> info.ccip = info.toBool(data);
            case "cciptexture" -> info.ccipTexture = data.trim();
            case "ccipfactor" -> info.ccipFactor = info.toFloat(data, 0.1F, 10.0F);
            // ---- Reforged sounds ----
            case "railgunsound" -> info.railgunSound = SoundDefinitions.parseSound(data);
            case "hitsound" -> info.hitSound = SoundDefinitions.parseSound(data);
            case "hitsoundiron" -> info.hitSoundIron = SoundDefinitions.parseSound(data);
            case "hitsoundrange" -> info.hitSoundRange = info.toInt(data);
            // ---- Reforged guidance / interception ----
            case "canbeintercepted" -> info.canBeIntercepted = info.toBool(data);
            case "enablemortarradar" -> info.hasMortarRadar = info.toBool(data);
            case "mortarradarmaxdist" -> info.mortarRadarMaxDist = info.toDouble(data);
            case "lockentity" -> info.lockEntity = info.toBool(data);
            case "camerafollowlockentity" -> info.cameraFollowLockEntity = info.toBool(data);
            case "camerafollowstrength" -> info.cameraFollowStrength = info.toFloat(data);
            case "antiradiationmissile" -> info.antiRadiationMissile = info.toBool(data);
            case "armemitterlostgracetick" -> info.armEmitterLostGraceTick = info.toInt(data, 0, 10000);
            case "armmemorytimetick" -> info.armMemoryTimeTick = info.toInt(data, 0, 10000);
            case "armcruiseenable" -> info.armCruiseEnable = info.toBool(data);
            case "armcruisestartdistance" -> info.armCruiseStartDistance = info.toDouble(data);
            case "armcruiseterminalradius" -> info.armCruiseTerminalRadius = info.toDouble(data);
            case "armcruiseterminalheight" -> info.armCruiseTerminalHeight = info.toDouble(data);
            case "enabledatalink" -> info.enableDataLink = info.toBool(data);
            case "onlydatalink" -> info.onlyDataLink = info.toBool(data);
            case "enablehms" -> info.enableHMS = info.toBool(data);
            case "nameonrwr" -> {
                String name = data.trim();
                info.nameOnRWR = "NULL".equals(name) ? "" : name;
            }
            case "rcsfactor" -> {
                String[] s = info.splitParam(data);
                if (s.length >= 1) {
                    info.rcsFrontFactor = info.toFloat(s[0], 0.01F, 10.0F);
                }
                if (s.length >= 2) {
                    info.rcsSideFactor = info.toFloat(s[1], 0.01F, 10.0F);
                }
                if (s.length >= 3) {
                    info.rcsRearFactor = info.toFloat(s[2], 0.01F, 10.0F);
                }
                if (s.length >= 4) {
                    info.rcsTimeFactor = info.toFloat(s[3], 0.01F, 10.0F);
                }
            }
            case "rcstimefactor" -> info.rcsTimeFactor = info.toFloat(data, 0.01F, 10.0F);
            case "proximityfusetick" -> info.proximityFuseTick = info.toInt(data);
            case "proximityfusedamage" -> info.proximityFuseDamage = info.toFloat(data);
            case "proximityfuseheight" -> info.proximityFuseHeight = info.toInt(data);
            // ---- Reforged marker rocket ----
            case "markerrocketspawnnum" -> info.markerRocketSpawnNum = info.toInt(data);
            case "markerrocketspawndiff" -> info.markerRocketSpawnDiff = info.toInt(data);
            case "markerrocketspawnheight" -> info.markerRocketSpawnHeight = info.toInt(data);
            case "markerrocketspawnspeed" -> info.markerRocketSpawnSpeed = info.toInt(data);
            case "enableexhaustflare" -> info.enableExhaustFlare = info.toBool(data);
            // ---- Reforged spawn-bullet (cluster) ----
            case "spawnbulletinair" -> info.spawnBulletInAir = info.toBool(data);
            case "spawnbulletmaxnum" -> info.spawnBulletMaxNum = info.toInt(data);
            case "spawnbulletintervaltick" -> info.spawnBulletIntervalTick = info.toInt(data);
            case "spawnbulletpernum" -> info.spawnBulletPerNum = info.toInt(data);
            case "spawnbulletinheritspeed" -> info.spawnBulletInheritSpeed = info.toBool(data);
            case "destructafterspawnbullet" -> info.destructAfterSpawnBullet = info.toBool(data);
            // ---- Reforged AHEAD / canister ----
            case "ahead" -> info.ahead = info.toBool(data);
            case "aheadsolveintervaltick" -> info.aheadSolveIntervalTick = info.toInt(data);
            case "canister" -> info.canister = info.toInt(data);
            case "canistertype" -> info.canisterType = info.toInt(data);
            case "draginair" -> info.dragInAir = info.toDouble(data);
            // ---- Reforged ballistic missile ----
            case "ballisticmissile" -> info.ballisticMissile = info.toBool(data);
            case "ballisticarcfactor" -> info.ballisticArcFactor = info.toDouble(data);
            case "ballisticarcminheight" -> info.ballisticArcMinHeight = info.toDouble(data);
            case "ballisticarcmaxheight" -> info.ballisticArcMaxHeight = info.toDouble(data);
            case "ballisticmindistance" -> info.ballisticMinDistance = info.toDouble(data);
            case "ballisticlateralsine" -> info.ballisticLateralSine = info.toBool(data);
            case "ballisticlateralamplitude" -> info.ballisticLateralAmplitude = info.toDouble(data);
            case "ballisticlateralwaves" -> info.ballisticLateralWaves = info.toDouble(data);
            case "ballisticlateralphasedeg" -> info.ballisticLateralPhaseDeg = info.toDouble(data);
            case "ballisticlateralstartratio" -> info.ballisticLateralStartRatio = info.toDouble(data);
            case "ballisticlateralendratio" -> info.ballisticLateralEndRatio = info.toDouble(data);
            case "ballisticterminalnoweavedist" -> info.ballisticTerminalNoWeaveDist = info.toDouble(data);
            case "ballisticterminalcylinderradius" -> info.ballisticTerminalCylinderRadius = info.toDouble(data);
            // ---- Reforged potion / bullet decay ----
            case "addpotioneffect" -> {
                String[] split = data.split("\\s*,\\s*");
                if (split.length >= 3) {
                    String effectId = PotionEffectInfo.fromLegacyId(info.toInt(split[0]));
                    if (effectId != null) {
                        int duration = info.toInt(split[1]);
                        int amplifier = info.toInt(split[2]);
                        int startDist = -1;
                        int endDist = -1;
                        if (split.length >= 5) {
                            startDist = info.toInt(split[3]);
                            endDist = info.toInt(split[4]);
                        }
                        info.potionEffect.add(new PotionEffectInfo(effectId, duration, amplifier, startDist, endDist));
                    }
                }
            }
            case "bulletdecay" -> {
                String[] split = data.split("\\s*,\\s*");
                if (split.length >= 1) {
                    String type = split[0];
                    String[] args = new String[split.length - 1];
                    System.arraycopy(split, 1, args, 0, args.length);
                    info.bulletDecay.add(BulletDecay.create(type, args));
                    info.enableBulletDecay = true;
                }
            }
            default -> {
                // Unknown legacy keys are ignored. The YAML-only NTM payload settings have no TXT syntax.
            }
        }
    }

    // ===================================================================================
    // Throwable
    // ===================================================================================

    private void applyThrowableLine(ThrowableInfo info, int lineNumber, String item, String data) {
        switch (item) {
            case "displayname" -> info.displayName = data;
            case "adddisplayname" -> {
                String[] s = data.split("\\s*,\\s*");
                if (s.length == 2) {
                    info.displayNameLang.put(s[0].trim(), s[1].trim());
                }
            }
            case "itemid" -> info.itemID = info.toInt(data, 0, 65535);
            case "addrecipe", "addshapelessrecipe" -> {
                info.isShapedRecipe = item.equals("addrecipe");
                info.recipeString.add(data.toUpperCase(Locale.ROOT));
            }
            case "power" -> info.power = info.toInt(data);
            case "acceleration" -> info.acceleration = info.toFloat(data, 0.0F, 100.0F);
            case "accelerationinwater" -> info.accelerationInWater = info.toFloat(data, 0.0F, 100.0F);
            case "dispenseacceleration" -> info.dispenseAcceleration = info.toFloat(data, 0.0F, 1000.0F);
            case "explosion" -> info.explosion = info.toInt(data, 0, 50);
            // TXT times are in seconds; the definition holds ticks.
            case "delayfuse" -> info.delayFuse = info.toInt(data, 0, 100000) * 20;
            case "bound" -> info.bound = info.toFloat(data, 0.0F, 100000.0F);
            case "timefuse" -> info.timeFuse = info.toInt(data, 0, 100000) * 20;
            case "flaming" -> info.flaming = info.toBool(data);
            case "stacksize" -> info.stackSize = info.toInt(data, 1, 64);
            case "soundvolume" -> info.soundVolume = info.toFloat(data, 0.0F, 1000.0F);
            case "soundpitch" -> info.soundPitch = info.toFloat(data, 0.0F, 1.0F);
            case "proximityfusedist" -> info.proximityFuseDist = info.toFloat(data, 0.0F, 20.0F);
            case "accuracy" -> info.accuracy = info.toFloat(data, 0.0F, 1000.0F);
            case "alivetime" -> info.aliveTime = info.toInt(data, 0, 1000000) * 20;
            case "bomblet" -> info.bomblet = info.toInt(data, 0, 1000);
            case "bombletdiff" -> info.bombletDiff = info.toFloat(data, 0.0F, 1000.0F);
            case "smokesize" -> info.smokeSize = info.toFloat(data, 0.0F, 1000.0F);
            case "smokenum" -> info.smokeNum = info.toInt(data, 0, 1000);
            case "smokevelocityvertical" -> info.smokeVelocityVertical = info.toFloat(data, -100.0F, 100.0F);
            case "smokevelocityhorizontal" -> info.smokeVelocityHorizontal = info.toFloat(data, 0.0F, 1000.0F);
            case "gravity" -> info.gravity = info.toFloat(data, -50.0F, 50.0F);
            case "gravityinwater" -> info.gravityInWater = info.toFloat(data, -50.0F, 50.0F);
            case "particle" -> {
                info.particleName = data.toLowerCase(Locale.ROOT).trim();
                if (info.particleName.equalsIgnoreCase("none")) {
                    info.particleName = "";
                }
            }
            case "disablesmoke" -> info.disableSmoke = info.toBool(data);
            case "smokecolor" -> {
                String[] s = data.split("\\s*,\\s*");
                if (s.length >= 3) {
                    info.smokeColor = new ArgbColor(1.0F, 0.003921569F * info.toInt(s[0], 0, 255),
                            0.003921569F * info.toInt(s[1], 0, 255), 0.003921569F * info.toInt(s[2], 0, 255));
                }
            }
            default -> {
                // Reforged's HandFlare throwable setting has no CE field.
            }
        }
    }

    // ===================================================================================
    // HUD
    // ===================================================================================

    private void applyHudLine(Hud info, int lineNumber, String item, String data) {
        String[] prm = data.split("\\s*,\\s*");
        if (prm.length == 0) {
            return;
        }
        switch (item) {
            case "if" -> {
                if (info.isWaitEndif) {
                    throw new IllegalStateException("Endif not found!");
                }
                info.list.add(new HudItem.Conditional(lineNumber, false, prm[0]));
                info.isWaitEndif = true;
            }
            case "endif" -> {
                if (!info.isWaitEndif) {
                    throw new IllegalStateException("IF in a pair can not be found!");
                }
                info.list.add(new HudItem.Conditional(lineNumber, true, ""));
                info.isWaitEndif = false;
            }
            case "drawstring", "drawcenteredstring" -> {
                if (prm.length >= 3) {
                    String s = prm[2];
                    if (s.charAt(0) == '"' && s.charAt(s.length() - 1) == '"') {
                        s = s.substring(1, s.length() - 1);
                        info.list.add(new HudItem.Str(lineNumber, prm[0], prm[1], s, prm,
                                item.equalsIgnoreCase("DrawCenteredString")));
                    }
                }
            }
            case "exit" -> info.list.add(new HudItem.Exit(lineNumber));
            case "color" -> {
                if (prm.length == 1) {
                    HudItem.Color c = HudItem.Color.createByParams(lineNumber, new String[] {prm[0]});
                    if (c != null) {
                        info.list.add(c);
                    }
                } else if (prm.length == 4) {
                    HudItem.Color c = HudItem.Color.createByParams(lineNumber,
                            new String[] {prm[0], prm[1], prm[2], prm[3]});
                    if (c != null) {
                        info.list.add(c);
                    }
                }
            }
            case "drawtexture" -> {
                if (prm.length >= 9 && prm.length <= 10) {
                    String rot = prm.length == 10 ? prm[9] : "0";
                    info.list.add(new HudItem.Texture(lineNumber, prm[0], prm[1], prm[2], prm[3], prm[4], prm[5],
                            prm[6], prm[7], prm[8], rot));
                }
            }
            case "drawrect" -> {
                if (prm.length == 4) {
                    info.list.add(new HudItem.Rect(lineNumber, prm[0], prm[1], prm[2], prm[3]));
                }
            }
            case "drawline" -> {
                int len = prm.length;
                if (len >= 4 && len % 2 == 0) {
                    info.list.add(new HudItem.Line(lineNumber, prm));
                }
            }
            case "drawlinestipple" -> {
                int len = prm.length;
                if (len >= 6 && len % 2 == 0) {
                    info.list.add(new HudItem.LineStipple(lineNumber, prm));
                }
            }
            case "call" -> {
                if (prm.length == 1) {
                    info.list.add(new HudItem.Call(lineNumber, prm[0]));
                }
            }
            case "drawentityradar", "drawenemyradar" -> {
                if (prm.length == 5) {
                    info.list.add(new HudItem.Radar(lineNumber, item.equalsIgnoreCase("DrawEntityRadar"), prm[0],
                            prm[1], prm[2], prm[3], prm[4]));
                }
            }
            case "drawgraduationyaw", "drawgraduationpitch1", "drawgraduationpitch2", "drawgraduationpitch3" -> {
                if (prm.length == 4) {
                    int type = switch (item) {
                        case "drawgraduationyaw" -> 0;
                        case "drawgraduationpitch1" -> 1;
                        case "drawgraduationpitch2" -> 2;
                        case "drawgraduationpitch3" -> 3;
                        default -> -1;
                    };
                    info.list.add(new HudItem.Graduation(lineNumber, type, prm[0], prm[1], prm[2], prm[3]));
                }
            }
            case "drawcamerarot" -> {
                if (prm.length == 2) {
                    info.list.add(new HudItem.CameraRot(lineNumber, prm[0], prm[1]));
                }
            }
            default -> {
            }
        }
    }

    @FunctionalInterface
    private interface LineProcessor {

        void accept(int lineNumber, String item, String data) throws Exception;
    }

    @FunctionalInterface
    private interface LineHandler<T extends BaseInfo> {

        void accept(T info, int lineNumber, String item, String data) throws Exception;
    }
}
