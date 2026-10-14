package io.github.mcheliceport.content.parser.yaml;

import static io.github.mcheliceport.content.hud.HudFormula.toFormula;
import static io.github.mcheliceport.content.parser.yaml.YamlParser.getAny;
import static io.github.mcheliceport.content.parser.yaml.YamlParser.logUnknownEntry;

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
import io.github.mcheliceport.content.parser.yaml.YamlParser.Pair;
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
 * Parses YAML HUD files. Ported from upstream {@code com.norwood.mcheli.helper.info.parsers.yaml.HUDParser}.
 */
@SuppressWarnings("unchecked")
public final class HudParser {

    private static final Logger LOGGER = LogUtils.getLogger();

    private HudParser() {}

    public static Pair<String, String> setTuple(List<String> mapKeys, Object object) {
        if (object instanceof List<?>tupleList) {
            if (tupleList.size() != 2) throw new IllegalArgumentException("Tuple list must have exactly 2 variables");
            List<String> tupleListTyped = toStringList(tupleList);
            return new Pair<>(tupleListTyped.get(0), tupleListTyped.get(1));
        } else if (object instanceof Map<?, ?>map) {
            Map<String, String> tupleMapTyped = toStringMap((Map<String, ?>) map);
            return new Pair<>(tupleMapTyped.get(mapKeys.get(0)), tupleMapTyped.get(mapKeys.get(1)));
        } else {
            throw new IllegalArgumentException("Tuple parsing failed, the values must be either string or a number!");
        }
    }

    public static List<String> toStringList(List<?> list) {
        if (list.get(0) instanceof String) return (List<String>) list;
        if (list.get(0) instanceof Number)
            return ((List<Number>) list).stream().map(Number::toString).collect(Collectors.toList());
        throw new ClassCastException();
    }

    public static Map<String, String> toStringMap(Map<String, ?> map) {
        if (map.isEmpty()) return new HashMap<>();
        Object firstValue = map.values().iterator().next();
        if (firstValue instanceof String) {
            return (Map<String, String>) map;
        }
        if (firstValue instanceof Number) {
            return map.entrySet().stream()
                    .collect(Collectors.toMap(Map.Entry::getKey, e -> ((Number) e.getValue()).toString()));
        }
        throw new ClassCastException("Map values are not String or Number");
    }

    public static void parse(Hud info, Object rootObj) {
        if (rootObj instanceof List<?>) {
            for (Object item : (List<?>) rootObj) {
                parseHudItem(info, item);
            }
        } else {
            throw new IllegalArgumentException("Root object must be a List");
        }
    }

    private static HudItem parseHUDCommands(Map.Entry<String, Object> entry) {
        switch (entry.getKey()) {

            case "Exit" -> {
                return new HudItem.Exit(0);

            }
            case "Color" -> {
                return new HudItem.Color(0, toFormula((String) entry.getValue()));

            }
            case "DrawTexture" -> {
                return parseDrawTexture((Map<String, Object>) entry.getValue());
            }

            case "DrawString" -> {
                return parseDrawString((Map<String, Object>) entry.getValue());
            }

            case "CameraRot", "CameraRotation" -> {
                return parseCameraRot((Map<String, Object>) entry.getValue());
            }

            case "DrawRectangle", "DrawRect" -> {
                return parseRact((Map<String, Object>) entry.getValue());
            }

            case "DrawLine" -> {
                return parseLine((Map<String, Object>) entry.getValue());
            }

            case "Call" -> {
                return new HudItem.Call(0, (String) entry.getValue());
            }
            case "DrawRadar" -> {
                return parseRadar((Map<String, Object>) entry.getValue());
            }

            case "DrawGraduation" -> {
                return parseGraduation((Map<String, Object>) entry.getValue());
            }

            default -> {
                return null;
            }
        }
    }

    private static HudItem parseGraduation(Map<String, Object> value) {
        GraduationType type = null;
        String xCoord = null;
        String yCoord = null;
        String rot = "0";
        String roll = "0";

        for (Map.Entry<String, Object> entry : value.entrySet()) {
            switch (entry.getKey()) {
                case "Type" -> type = GraduationType
                        .valueOf(((String) entry.getValue()).toUpperCase(Locale.ROOT).trim());
                case "Pos", "Position" -> {
                    Pair<String, String> pos = setTuple(Arrays.asList("x", "y"), entry.getValue());
                    xCoord = HudFormula.toFormula(pos.a());
                    yCoord = HudFormula.toFormula(pos.b());
                }
                case "Rotation", "Rot" -> rot = (String) entry.getValue();
                case "Roll" -> roll = (String) entry.getValue();
                default -> logUnknownEntry(entry, "VehicleFeatures");
            }
        }

        if (xCoord == null || yCoord == null)
            throw new IllegalArgumentException("Texture, Pos fields are required for drawTexture element.");
        return new HudItem.Graduation(0, type == null ? -1 : type.ordinal(), rot, roll, xCoord, yCoord);
    }

    private static HudItem.Radar parseRadar(Map<String, Object> value) {
        boolean isEntityRadar = false;
        String xCoord = null;
        String yCoord = null;
        String width = null;
        String height = null;
        String rot = "0";

        for (Map.Entry<String, Object> entry : value.entrySet()) {
            switch (entry.getKey()) {
                case "EntityRadar" -> isEntityRadar = (Boolean) entry.getValue();
                case "Pos", "Position" -> {
                    Pair<String, String> pos = setTuple(Arrays.asList("x", "y"), entry.getValue());
                    xCoord = HudFormula.toFormula(pos.a());
                    yCoord = HudFormula.toFormula(pos.b());
                }
                case "Size" -> {
                    Pair<String, String> size = setTuple(Arrays.asList("width", "height"), entry.getValue());
                    width = HudFormula.toFormula(size.a());
                    height = HudFormula.toFormula(size.b());
                }
                case "Rotation", "Rot" -> rot = toFormula((String) entry.getValue());
                default -> logUnknownEntry(entry, "VehicleFeatures");
            }
        }

        if (xCoord == null || yCoord == null)
            throw new IllegalArgumentException("Texture, Pos fields are required for drawTexture element.");

        return new HudItem.Radar(0, isEntityRadar, rot, xCoord, yCoord, width, height);
    }

    private static HudItem parseLine(Map<String, Object> value) {
        boolean isStriped = false;
        String pattern = "0";
        String factor = "0";
        List<List<String>> positions = new ArrayList<>();

        for (Map.Entry<String, Object> entry : value.entrySet()) {
            switch (entry.getKey()) {
                case "Striped" -> isStriped = (Boolean) entry.getValue();
                case "Fac", "Factor" -> factor = toFormula((String) entry.getValue());
                case "Pat", "Pattern" -> pattern = toFormula((String) entry.getValue());
                case "StartPos", "Position", "Start" -> {
                    Object raw = entry.getValue();

                    if (raw instanceof List<?>rawList) {
                        for (Object pairObj : rawList) {
                            if (pairObj instanceof List<?>pair && pair.size() == 2) {
                                String x = HudFormula.toFormula(pair.get(0).toString());
                                String y = HudFormula.toFormula(pair.get(1).toString());
                                positions.add(Arrays.asList(x, y));
                            } else {
                                throw new IllegalArgumentException(
                                        "Each position must be a list of exactly 2 elements.");
                            }
                        }
                    } else {
                        throw new IllegalArgumentException("Position field must be a list of lists.");
                    }
                }
                default -> logUnknownEntry(entry, "Line");
            }
        }

        if (positions.isEmpty()) {
            throw new IllegalArgumentException("At least one position is required for Line element.");
        }

        // Flatten the list of lists into a single String[] array
        String[] coordsArr = positions.stream()
                .flatMap(List::stream)
                .toArray(String[]::new);

        return isStriped ? new HudItem.LineStipple(0, pattern, factor, coordsArr) :
                new HudItem.Line(0, coordsArr);
    }

    private static HudItem parseCameraRot(Map<String, Object> value) {
        String xCoord = null;
        String yCoord = null;

        for (Map.Entry<String, Object> entry : value.entrySet()) {
            switch (entry.getKey()) {
                case "Pos", "Position" -> {
                    Pair<String, String> pos = setTuple(Arrays.asList("x", "y"), entry.getValue());
                    xCoord = HudFormula.toFormula(pos.a());
                    yCoord = HudFormula.toFormula(pos.b());
                }
                default -> logUnknownEntry(entry, "VehicleFeatures");
            }
        }

        if (xCoord == null || yCoord == null)
            throw new IllegalArgumentException("Pos fields are required for drawTexture element.");

        return new HudItem.CameraRot(0, xCoord, yCoord);
    }

    private static HudItem.Rect parseRact(Map<String, Object> value) {
        String xCoord = null;
        String yCoord = null;
        String width = null;
        String height = null;

        for (Map.Entry<String, Object> entry : value.entrySet()) {
            switch (entry.getKey()) {
                case "Pos", "Position" -> {
                    Pair<String, String> pos = setTuple(Arrays.asList("x", "y"), entry.getValue());
                    xCoord = HudFormula.toFormula(pos.a());
                    yCoord = HudFormula.toFormula(pos.b());
                }
                case "Size" -> {
                    Pair<String, String> size = setTuple(Arrays.asList("width", "height"), entry.getValue());
                    width = HudFormula.toFormula(size.a());
                    height = HudFormula.toFormula(size.b());
                }
                default -> logUnknownEntry(entry, "VehicleFeatures");
            }
        }

        if (xCoord == null || yCoord == null)
            throw new IllegalArgumentException("Pos fields are required for drawTexture element.");

        return new HudItem.Rect(0, xCoord, yCoord, width, height);
    }

    private static HudItem.Str parseDrawString(Map<String, Object> map) {
        String text = null;
        List<String> varSubstitute = null;
        String xCoord = null;
        String yCoord = null;
        boolean center = false;

        for (Map.Entry<String, Object> entry : map.entrySet()) {
            switch (entry.getKey()) {
                case "Text" -> {
                    var formatEntry = (Map<String, Object>) entry.getValue();
                    text = (String) getAny(formatEntry, Arrays.asList("Fmt", "Format"), null);
                    varSubstitute = (List<String>) getAny(formatEntry, Arrays.asList("Vars", "Variables"),
                            null);
                }
                case "Center" -> center = (Boolean) entry.getValue();
                case "Pos", "Position" -> {
                    Pair<String, String> pos = setTuple(Arrays.asList("x", "y"), entry.getValue());
                    xCoord = pos.a();
                    yCoord = pos.b();
                }
                default -> logUnknownEntry(entry, "DrawString");

            }
        }

        if (xCoord == null || yCoord == null || text == null)
            throw new IllegalArgumentException("Pos, Format, fields are required for drawTexture element.");

        String[] args = Stream.concat(
                Stream.of(xCoord, yCoord, text),
                varSubstitute != null ? varSubstitute.stream() : Stream.empty()).toArray(String[]::new);

        return new HudItem.Str(0, xCoord, yCoord, text, args, center);
    }

    private static HudItem.Texture parseDrawTexture(Map<String, Object> map) {
        String name = null;
        String xCoord = null;
        String yCoord = null;
        String width = null;
        String height = null;
        String uLeft = null;
        String vTop = null;
        String uWidth = null;
        String vHeight = null;
        String rot = "0";

        for (Map.Entry<String, Object> entry : map.entrySet()) {
            switch (entry.getKey()) {
                case "Texture", "Name" -> name = (String) entry.getValue();
                case "Pos", "Position" -> {
                    Pair<String, String> pos = setTuple(Arrays.asList("x", "y"), entry.getValue());
                    xCoord = HudFormula.toFormula(pos.a());
                    yCoord = HudFormula.toFormula(pos.b());
                }
                case "Size" -> {
                    Pair<String, String> size = setTuple(Arrays.asList("width", "height"), entry.getValue());
                    width = HudFormula.toFormula(size.a());
                    height = HudFormula.toFormula(size.b());
                }
                case "UVPos" -> {
                    Pair<String, String> uvPos = setTuple(Arrays.asList("x", "y"), entry.getValue());
                    uLeft = HudFormula.toFormula(uvPos.a());
                    vTop = HudFormula.toFormula(uvPos.b());
                }
                case "UVSize" -> {
                    Pair<String, String> uvSize = setTuple(Arrays.asList("width", "height"), entry.getValue());
                    uWidth = HudFormula.toFormula(uvSize.a());
                    vHeight = HudFormula.toFormula(uvSize.b());
                }
                case "Rotation", "Rot" -> rot = toFormula((String) entry.getValue());
                default -> logUnknownEntry(entry, "DrawTexture");
            }
        }

        if (name == null || xCoord == null || yCoord == null)
            throw new IllegalArgumentException("Texture, Pos fields are required for drawTexture element.");

        return new HudItem.Texture(0, name, xCoord, yCoord, width, height, uLeft, vTop, uWidth, vHeight, rot);
    }

    private static void parseHudItem(Hud info, Object obj) {
        if (obj instanceof Map<?, ?>map) {
            if (map.containsKey("If")) {
                parseConditional(info, (LinkedHashMap<String, Object>) map);
            } else {
                Map.Entry<String, Object> entry = ((Map<String, Object>) map).entrySet().iterator().next();
                if (parseHudSetting(info, entry)) {
                    return;
                }
                var element = parseHUDCommands(entry);
                if (element == null) logUnknownEntry(entry, "Hud");
                else info.list.add(element);
            }
        } else {
            throw new IllegalArgumentException("Each HUD item must be a Map");
        }
    }

    private static boolean parseHudSetting(Hud info, Map.Entry<String, Object> entry) {
        switch (entry.getKey()) {
            case "IgnoreAutoScale", "IgnoreAutoScaleGui", "IgnoreScaledGui", "IgnoreAutomaticScale" -> {
                info.ignoreAutoScale = (Boolean) entry.getValue();
                return true;
            }
            default -> {
                return false;
            }
        }
    }

    private static void parseConditional(Hud info, Map<String, Object> map) {
        String condition = ((String) map.get("If")).trim();
        if (condition == null || condition.isEmpty())
            throw new IllegalArgumentException("Condition cannot be blank!");

        var conditional = new HudItem.Conditional(0, false, condition);
        info.list.add(conditional);

        List<Object> doBlock = (List<Object>) map.get("Do");
        if (doBlock.isEmpty())
            LOGGER.warn("Hud " + info.getLocation().path() + " contains empty conditionals!");

        for (Object obj : doBlock) {
            if (obj instanceof Map<?, ?>objMap) {
                for (Map.Entry<String, Object> entry : ((Map<String, Object>) objMap).entrySet()) {
                    HudItem element = parseHUDCommands(entry);
                    if (element != null) info.list.add(element);
                    else logUnknownEntry(entry, "Hud");
                }
            } else {
                throw new IllegalArgumentException("Each Do block item must be a Map");
            }
        }

        info.list.add(new HudItem.Conditional(0, true, null));
    }

    public static enum GraduationType {
        YAW,
        PITCH,
        PITCH_ROLL,
        PITCH_ROLL_ALT;
    }
}
