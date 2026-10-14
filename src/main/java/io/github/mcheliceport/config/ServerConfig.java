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
    public static final ModConfigSpec SPEC;

    static {
        ModConfigSpec.Builder builder = new ModConfigSpec.Builder();

        builder.push("vehicles");
        SERVER_AUTHORITATIVE_MOVEMENT = builder
                .comment("false: the pilot's client moves the vehicle it controls and the server checks the movement.",
                        "true: clients only send control inputs and the server simulates every vehicle, as upstream McHeli CE does.",
                        "Has no effect yet: vehicles are not ported.")
                .define("serverAuthoritativeMovement", false);
        builder.pop();

        SPEC = builder.build();
    }

    private ServerConfig() {}
}
