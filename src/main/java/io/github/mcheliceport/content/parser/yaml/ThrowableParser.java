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
 * Parses YAML throwable files. Ported from upstream
 * {@code com.norwood.mcheli.helper.info.parsers.yaml.ThrowableParser}.
 */
@SuppressWarnings("unchecked")
public final class ThrowableParser {

    private static final Logger LOGGER = LogUtils.getLogger();

    private ThrowableParser() {}

    public static void parse(ThrowableInfo info, Map<String, Object> root) {
        for (Map.Entry<String, Object> entry : root.entrySet()) {
            switch (entry.getKey()) {
                case "DisplayName" -> {
                    Object nameObject = entry.getValue();
                    if (nameObject instanceof String name) info.displayName = name.trim();
                    else if (nameObject instanceof Map<?, ?>translationNames) {
                        var userNameMap = (Map<String, String>) translationNames;
                        if (userNameMap.containsKey("DEFAULT")) {
                            info.displayName = userNameMap.get("DEFAULT");
                            userNameMap.remove("DEFAULT");
                        }
                        info.displayNameLang.putAll((Map<String, String>) userNameMap);
                    } else throw new ClassCastException();
                }
                case "Author" -> {
                    // Metadata only; upstream does not store it for throwables.
                }
                // Deprecated since 1.12, kept for 1.7 content.
                case "ItemID" -> {
                    info.itemID = (int) entry.getValue();
                }

                case "Sound" -> {
                    Map<String, Object> soundSettings = (Map<String, Object>) entry.getValue();
                    parseSound(soundSettings, info);
                }

                case "Recepie" -> {
                    Map<String, Object> map = (Map<String, Object>) entry.getValue();
                    for (Map.Entry<String, Object> recMapEntry : map.entrySet()) {
                        switch (recMapEntry.getKey()) {
                            case "isShaped" -> info.isShapedRecipe = (Boolean) recMapEntry.getValue();
                            case "Pattern" -> info.recipeString = ((List<String>) recMapEntry.getValue()).stream()
                                    .map(s -> s.toUpperCase(Locale.ROOT)).map(String::trim).collect(Collectors.toList());
                        }
                    }

                }
                case "Power" -> info.power = getClamped(0, Integer.MAX_VALUE, entry.getValue());

                case "Acceleration" -> info.acceleration = getClamped(100.0F, entry.getValue());

                case "AccelerationInWater" -> info.accelerationInWater = getClamped(100.0F, entry.getValue());

                case "DispenseAcceleration" -> info.dispenseAcceleration = getClamped(1000.0F, entry.getValue());

                case "Explosion" -> info.explosion = getClamped(0, 50, entry.getValue());

                case "DelayFuse" -> info.delayFuse = getClamped(0, 100000, entry.getValue());

                case "Bound" -> info.bound = getClamped(100000.0F, entry.getValue());

                case "TimeFuse" -> info.timeFuse = getClamped(0, 100000, entry.getValue());

                case "Flaming" -> info.flaming = (Boolean) entry.getValue();

                case "StackSize" -> info.stackSize = getClamped(1, 64, entry.getValue());

                case "ProximityFuseDist" -> info.proximityFuseDist = getClamped(20.0F, entry.getValue());

                case "Accuracy" -> info.accuracy = getClamped(1000.0F, entry.getValue());

                case "AliveTime" -> info.aliveTime = getClamped(0, 1_000_000, entry.getValue());

                case "Bomblet" -> info.bomblet = getClamped(0, 1000, entry.getValue());

                case "BombletSpread" -> info.bombletDiff = getClamped(1000.0F, entry.getValue());

                case "Gravity" -> info.gravity = getClamped(-50.0F, 50.0F, entry.getValue());

                case "GravityInWater" -> info.gravityInWater = getClamped(-50.0F, 50.0F, entry.getValue());

                case "Particle" -> info.particleName = ((String) entry.getValue()).trim().toLowerCase(Locale.ROOT);

                case "Smoke" -> parseSmoke(info, (Map<String, Object>) entry.getValue());
                default -> logUnknownEntry(entry, "ThrowableInfo");
            }
        }
    }

    private static void parseSmoke(ThrowableInfo info, Map<String, Object> soundSettings) {
        for (Map.Entry<String, Object> entry : soundSettings.entrySet()) {
            switch (entry.getKey()) {
                case "DisableSmoke" -> info.disableSmoke = (Boolean) entry.getValue();
                case "Size" -> info.smokeSize = getClamped(1000.0F, entry.getValue());

                case "Count" -> info.smokeNum = getClamped(0, 1000, entry.getValue());

                case "Color" -> {
                    info.smokeColor = new ArgbColor(parseHexColor(((String) entry.getValue()).trim()));
                }
                case "Velocity" -> {
                    var velMap = (Map<String, Object>) entry.getValue();

                    // Upstream switched on the outer key here, so these were never applied (D5).
                    for (Map.Entry<String, Object> velEntry : velMap.entrySet()) {
                        switch (velEntry.getKey()) {
                            case "Vertical", "Y" -> info.smokeVelocityVertical = getClamped(-100.0F, 100.0F,
                                    velEntry.getValue());
                            case "Horizontal", "X" -> info.smokeVelocityHorizontal = getClamped(1000.0F,
                                    velEntry.getValue());
                            default -> logUnknownEntry(velEntry, "Smoke.Velocity");
                        }
                    }
                }

                default -> logUnknownEntry(entry, "Smoke");
            }

        }
    }

    private static void parseSound(Map<String, Object> soundSettings, ThrowableInfo info) {
        for (Map.Entry<String, Object> entry : soundSettings.entrySet()) {
            switch (entry.getKey()) {
                case "Volume", "Vol" -> info.soundVolume = getClamped(10F, entry.getValue());
                case "Pitch" -> info.soundPitch = getClamped(1F, 10F, entry.getValue());
                default -> logUnknownEntry(entry, "Sound");
            }

        }
    }
}
