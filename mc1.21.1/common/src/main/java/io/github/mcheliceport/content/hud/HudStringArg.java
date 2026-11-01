package io.github.mcheliceport.content.hud;

import java.util.Locale;

/**
 * Values a HUD {@code DrawString} can insert into its text. Ported from upstream
 * {@code com.norwood.mcheli.hud.MCH_HudItemStringArgs}.
 */
public enum HudStringArg {
    NONE,
    NAME,
    ALTITUDE,
    DATE,
    MC_THOR,
    MC_TMIN,
    MC_TSEC,
    MAX_HP,
    HP,
    HP_PER,
    POS_X,
    POS_Y,
    POS_Z,
    MOTION_X,
    MOTION_Y,
    MOTION_Z,
    INVENTORY,
    WPN_NAME,
    WPN_AMMO,
    WPN_RM_AMMO,
    RELOAD_PER,
    RELOAD_SEC,
    MORTAR_DIST,
    MC_VER,
    MOD_VER,
    MOD_NAME,
    YAW,
    PITCH,
    ROLL,
    PLYR_YAW,
    PLYR_PITCH,
    TVM_POS_X,
    TVM_POS_Y,
    TVM_POS_Z,
    TVM_DIFF,
    CAM_ZOOM,
    UAV_DIST,
    KEY_GUI,
    THROTTLE,
    AIRBURST_DIST;

    /** Looks an argument up by name, ignoring case; unknown names give {@link #NONE}. */
    public static HudStringArg toArgs(String name) {
        try {
            return valueOf(name.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException e) {
            return NONE;
        }
    }
}
