package io.github.mcheliceport.config;

/**
 * How McHeli's effects are drawn (M8, PORTING_PLAN.md section 6). Either level spawns the same
 * particles at the same times; only their look differs, so gameplay never depends on it.
 */
public enum EffectQuality {
    /** Upstream McHeli CE 1.5.1's look, unchanged. */
    CLASSIC,
    /**
     * The M8 refinements: smoke that grows and fades smoothly, fireballs, additive glows for flashes,
     * flares, tracers and missile flames, and downwash coloured by the ground below.
     */
    ENHANCED;

    /** True when the client config asks for the M8 refinements. */
    public static boolean enhanced() {
        return ClientConfig.get(ClientConfig.EFFECT_QUALITY) == ENHANCED;
    }
}
