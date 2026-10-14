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
    public static final ModConfigSpec SPEC;

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

        SPEC = builder.build();
    }

    private ServerConfig() {}
}
