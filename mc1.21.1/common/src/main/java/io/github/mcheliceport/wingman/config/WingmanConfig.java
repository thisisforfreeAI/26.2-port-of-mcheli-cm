package io.github.mcheliceport.wingman.config;

import io.github.mcheliceport.config.ServerConfig;

/**
 * Upstream's {@code WingmanConfig} formation values, which the wingman panel and the
 * {@code /wingman} command change while the server runs. They are read from the server config's
 * {@code [wingman]} section when the server starts, and as upstream changes are not saved.
 */
public final class WingmanConfig {

    public static final double DEFAULT_SIDE_DIST = 20.0;
    public static final double DEFAULT_ALT_OFFSET = 0.0;
    public static final double DEFAULT_REAR_DIST = 30.0;
    public static final int DEFAULT_MAX_WINGMEN = 10;

    /** Lateral distance from the leader's centre line (blocks); 0 flies a column. */
    public static volatile double formationSideDist = DEFAULT_SIDE_DIST;
    /** Altitude offset from the leader (blocks); positive is above. */
    public static volatile double formationAltOffset = DEFAULT_ALT_OFFSET;
    /** Distance behind the leader (blocks). */
    public static volatile double formationRearDist = DEFAULT_REAR_DIST;
    /** Wingmen per aircraft. */
    public static volatile int maxWingmen = DEFAULT_MAX_WINGMEN;
    /** Lowest absolute Y of attack runs; 0 is no floor. */
    public static volatile double minAttackAltitude = 0.0;
    /** Highest absolute Y of attack runs; 0 is no ceiling. */
    public static volatile double maxAttackAltitude = 0.0;

    private WingmanConfig() {
    }

    /** Upstream {@code load}: the values of the server config, or upstream's defaults before it loads. */
    public static void loadFromServerConfig() {
        if (!ServerConfig.SPEC.isLoaded()) {
            reset();
            return;
        }
        formationSideDist = ServerConfig.WINGMAN_SIDE_DIST.get();
        formationAltOffset = ServerConfig.WINGMAN_ALT_OFFSET.get();
        formationRearDist = ServerConfig.WINGMAN_REAR_DIST.get();
        maxWingmen = ServerConfig.WINGMAN_MAX_WINGMEN.get();
        minAttackAltitude = ServerConfig.WINGMAN_MIN_ATTACK_ALTITUDE.get();
        maxAttackAltitude = ServerConfig.WINGMAN_MAX_ATTACK_ALTITUDE.get();
    }

    /** Upstream's defaults. */
    public static void reset() {
        formationSideDist = DEFAULT_SIDE_DIST;
        formationAltOffset = DEFAULT_ALT_OFFSET;
        formationRearDist = DEFAULT_REAR_DIST;
        maxWingmen = DEFAULT_MAX_WINGMEN;
        minAttackAltitude = 0.0;
        maxAttackAltitude = 0.0;
    }

    /**
     * Upstream's {@code FORMATION} panel action, with its wingman and altitude limits. The port also
     * keeps the distances within upstream's config ranges (the panel's 0 included) and ignores
     * values that are not numbers.
     */
    public static boolean setFormation(double sideDist, double altOffset, double rearDist, int maxWings, double minAlt,
            double maxAlt) {
        if (!Double.isFinite(sideDist) || !Double.isFinite(altOffset) || !Double.isFinite(rearDist)
                || !Double.isFinite(minAlt) || !Double.isFinite(maxAlt)) {
            return false;
        }
        formationSideDist = Math.max(0, Math.min(500, sideDist));
        formationAltOffset = Math.max(-500, Math.min(500, altOffset));
        formationRearDist = Math.max(0, Math.min(500, rearDist));
        maxWingmen = Math.max(1, Math.min(64, maxWings));
        minAttackAltitude = Math.max(0, Math.min(10000, minAlt));
        maxAttackAltitude = Math.max(0, Math.min(10000, maxAlt));
        return true;
    }
}
