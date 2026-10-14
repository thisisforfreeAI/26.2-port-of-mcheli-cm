package io.github.mcheliceport.config;

import net.neoforged.neoforge.common.ModConfigSpec;

/**
 * CLIENT config ({@code config/mcheli_ce_port-client.toml}): per-player settings that never leave the
 * client. Registered by {@link io.github.mcheliceport.client.McheliCePortClient}.
 *
 * <p>Upstream's mouse, HUD and render keys from {@code mcheli.cfg} move here as they are ported
 * (PORTING_PLAN.md section 5.6).
 */
public final class ClientConfig {
    /** Decision D7: {@code /mcheli sendss} screen capture is opt-in. */
    public static final ModConfigSpec.BooleanValue ALLOW_SCREEN_CAPTURE;
    public static final ModConfigSpec SPEC;

    static {
        ModConfigSpec.Builder builder = new ModConfigSpec.Builder();

        builder.push("privacy");
        ALLOW_SCREEN_CAPTURE = builder
                .comment("Allow server operators to capture your screen with /mcheli sendss.",
                        "Requests are refused while this is false, and you get a chat notice whenever a capture happens.",
                        "Has no effect yet: /mcheli sendss is not ported.")
                .define("allowScreenCapture", false);
        builder.pop();

        SPEC = builder.build();
    }

    private ClientConfig() {}
}
