package io.github.mcheliceport.client.hud;

import io.github.mcheliceport.content.hud.HudStringArg;
import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.Map;
import org.jspecify.annotations.Nullable;

/**
 * Everything a HUD reads in one frame: upstream's static {@code MCH_HudItem} state ({@code varMap},
 * the {@code DrawString} values, the radar lists, the camera), gathered by {@code HudVariables}
 * in the game or set by hand in tests.
 */
public final class HudFrame {

    /** GUI size, upstream's {@code width} and {@code height}. */
    public double width;
    public double height;
    /** {@code ExperimentalAutoScaleAircraftGui}: HUD coordinates are scaled from a 427×240 canvas. */
    public boolean autoScale;
    /** Upstream's {@code varMap}, in its order and with its Java types (Integer, Float, Boolean...). */
    public final Map<String, Object> vars = new LinkedHashMap<>();
    /** The {@code DrawString} values; a missing argument formats as {@code null}. */
    public final Map<HudStringArg, Object> stringArgs = new EnumMap<>(HudStringArg.class);
    /** Upstream's {@code CurrentWeapon != null}; weapon strings are not drawn without one. */
    public boolean hasWeapon;
    /** Upstream's {@code CurrentWeapon.getMagSize()}. */
    public int weaponMagSize;
    /** Upstream's {@code EntityList} and {@code EnemyList}: x, z offsets from the aircraft, in pairs. */
    public double @Nullable [] entityRadar;
    public double @Nullable [] enemyRadar;
    /** {@code CameraRot}: the aircraft's yaw and the camera's yaw and pitch. */
    public float aircraftYaw;
    public float cameraYaw;
    public float cameraPitch;
    /** {@code TestMode}: the variables are listed on screen. */
    public boolean testMode;
}
