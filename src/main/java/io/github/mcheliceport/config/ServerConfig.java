package io.github.mcheliceport.config;

import net.neoforged.neoforge.common.ModConfigSpec;

/**
 * SERVER config ({@code config/mcheli_ce_port-server.toml}; a copy in {@code <world>/serverconfig/}
 * overrides it for that world): settings the server owns, loaded when a server starts and synced to
 * every client that joins.
 *
 * <p>Upstream's world rules (rotation limits, lock ranges, marker settings, command permissions)
 * move here as they are ported (PORTING_PLAN.md section 5.6).
 */
public final class ServerConfig {
    /** Decision D2: a server can switch back to upstream's server-authoritative vehicle movement. */
    public static final ModConfigSpec.BooleanValue SERVER_AUTHORITATIVE_MOVEMENT;
    public static final ModConfigSpec.BooleanValue ENABLE_ROTATION_LIMIT;
    public static final ModConfigSpec.IntValue PITCH_LIMIT_MAX;
    public static final ModConfigSpec.IntValue PITCH_LIMIT_MIN;
    public static final ModConfigSpec.IntValue ROLL_LIMIT;
    public static final ModConfigSpec.BooleanValue ENABLE_CAM_DIST_CHANGE;
    public static final ModConfigSpec SPEC;
    public static final ModConfigSpec.IntValue MAX_PAIRED_UAVS;
    public static final ModConfigSpec.BooleanValue TABLET_NORMAL_UAVS;
    public static final ModConfigSpec.IntValue UAV_CONTROL_RANGE;
    public static final ModConfigSpec.IntValue UAV_REMOTE_VIEW_DISTANCE;
    public static final ModConfigSpec.BooleanValue ENABLE_ENTITY_MARKER;
    public static final ModConfigSpec.IntValue RANGE_FINDER_SPOT_DIST;
    public static final ModConfigSpec.IntValue RANGE_FINDER_SPOT_TIME;
    public static final ModConfigSpec.BooleanValue RANGE_FINDER_CONSUME;
    public static final ModConfigSpec.DoubleValue STINGER_LOCK_RANGE;

    static {
        ModConfigSpec.Builder builder = new ModConfigSpec.Builder();

        builder.push("vehicles");
        SERVER_AUTHORITATIVE_MOVEMENT = builder
                .comment("false: the pilot's client moves the vehicle it controls and the server checks the movement.",
                        "true: clients only send control inputs and the server simulates every vehicle, as upstream McHeli CE does.",
                        "Takes effect for vehicles after a world restart.")
                .define("serverAuthoritativeMovement", false);
        builder.pop();

        builder.comment("Rotation limits pilots fly with (upstream's MCH_ServerSettings, sent to every client).")
                .push("rotationLimits");
        ENABLE_ROTATION_LIMIT = builder
                .comment("Pull helicopters back towards the limits below. Upstream: EnableRotationLimit.")
                .define("enableRotationLimit", false);
        PITCH_LIMIT_MAX = builder
                .comment("Nose-down pitch limit in degrees. Upstream: PitchLimitMax.")
                .defineInRange("pitchLimitMax", 10, 0, 80);
        PITCH_LIMIT_MIN = builder
                .comment("Nose-up pitch limit in degrees (negative). Upstream: PitchLimitMin.")
                .defineInRange("pitchLimitMin", -10, -80, 0);
        ROLL_LIMIT = builder
                .comment("Roll limit in degrees either way. Upstream: RollLimit.")
                .defineInRange("rollLimit", 35, 0, 80);
        builder.pop();

        builder.push("camera");
        ENABLE_CAM_DIST_CHANGE = builder
                .comment("Let riders change the third-person camera distance with Page Up and Page Down.",
                        "Upstream: enableCamDistChange.")
                .define("enableCamDistChange", true);
        builder.pop();

        builder.push("uav");
        UAV_CONTROL_RANGE = builder.comment("Maximum station/tablet control distance in blocks.")
                .defineInRange("controlRange", 5000, 24, 5000);
        UAV_REMOTE_VIEW_DISTANCE = builder.comment("Camera chunk radius during UAV control, also bounded by player/server view distance.")
                .defineInRange("remoteViewDistance", 4, 2, 8);
        MAX_PAIRED_UAVS = builder.comment("Maximum paired UAVs per controller. Upstream: MaxPairedUavPerStation.")
                .defineInRange("maxPairedUavs", 5, 0, 64);
        TABLET_NORMAL_UAVS = builder.comment("Allow tablets to bind/control full-size UAVs. Default: small UAVs only.")
                .define("tabletControlNormalUav", false);
        builder.pop();

        builder.comment("Spotting with the rangefinder and targeting pods (upstream's lines of the same names in mcheli.cfg).")
                .push("spotting");
        ENABLE_ENTITY_MARKER = builder
                .comment("Let clients draw markers over spotted entities and teammates. Clients can still turn",
                        "their own markers off. Upstream: the server's DisplayEntityMarker.")
                .define("enableEntityMarker", true);
        RANGE_FINDER_SPOT_DIST = builder
                .comment("How far the rangefinder spots, in blocks. Upstream: RangeFinderSpotDist.")
                .defineInRange("rangeFinderSpotDist", 400, 1, 10000);
        RANGE_FINDER_SPOT_TIME = builder
                .comment("Seconds a player or vehicle the rangefinder spotted stays marked (mobs stay 60). Upstream: RangeFinderSpotTime.")
                .defineInRange("rangeFinderSpotTime", 15, 1, 3600);
        RANGE_FINDER_CONSUME = builder
                .comment("Each spot uses one charge of the rangefinder. Upstream: RangeFinderConsume.")
                .define("rangeFinderConsume", true);
        builder.pop();

        builder.comment("The hand-held Stinger and Javelin.").push("lightWeapons");
        STINGER_LOCK_RANGE = builder
                .comment("How far the hand-held Stinger and Javelin lock on, in blocks. Upstream: StingerLockRange.")
                .defineInRange("stingerLockRange", 320.0, 1.0, 10000.0);
        builder.pop();
        SPEC = builder.build();
    }

    private ServerConfig() {}
}
