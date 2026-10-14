package io.github.mcheliceport.config;

import net.neoforged.neoforge.common.ModConfigSpec;

/**
 * COMMON config ({@code config/mcheli_ce_port-common.toml}): settings both sides read at startup.
 *
 * <p>Upstream's gameplay and balance keys from {@code mcheli.cfg} (vehicle speeds, damage factors,
 * explosions, collisions, gunner ranges) move here as the features that read them are ported
 * (PORTING_PLAN.md section 5.6).
 */
public final class CommonConfig {
    public static final ModConfigSpec.BooleanValue DEBUG_LOG;
    public static final ModConfigSpec SPEC;

    static {
        ModConfigSpec.Builder builder = new ModConfigSpec.Builder();

        builder.push("general");
        DEBUG_LOG = builder
                .comment("Write extra diagnostic messages to the log.",
                        "Replaces upstream's hidden 'McHeliOutputDebugLog' line in mcheli.cfg.")
                .define("debugLog", false);
        builder.pop();

        SPEC = builder.build();
    }

    private CommonConfig() {}
}
