package io.github.mcheliceport;

import com.mojang.logging.LogUtils;
import io.github.mcheliceport.platform.Services;
import org.slf4j.Logger;

/**
 * Shared entry point of McHeli CE (1.21.1 Port), an unofficial port of McHeli: Community Edition.
 * The loader modules call {@link #init()} from their own entry points.
 */
public final class McheliCePort {
    /** Must match {@code mod_id} in gradle.properties. */
    public static final String MOD_ID = "mcheli_ce_port";
    public static final Logger LOGGER = LogUtils.getLogger();

    private McheliCePort() {
    }

    public static void init() {
        LOGGER.info("Loading McHeli CE (1.21.1 Port) {} on {}. This is an unofficial port of McHeli CE: "
                + "report problems to the port, not to upstream.",
                Services.PLATFORM.modVersion(), Services.PLATFORM.name());
    }
}
