package io.github.mcheliceport.config;

import io.github.mcheliceport.entity.heli.HeliFlightModel;

/**
 * The server settings vehicles read on both sides: upstream's {@code MCH_ServerSettings}
 * (rotation limits, camera distance change, entity markers, the Stinger's lock range) plus the authority mode of decision D2.
 *
 * <p>The server reads {@link ServerConfig}; clients keep the copy the server last sent them in a
 * {@code ServerSettingsPayload}, so an integrated server and its client never share a value by
 * accident.
 */
public final class ServerSettings {

    /** One snapshot of the settings. */
    public record Values(boolean serverAuthoritativeMovement, HeliFlightModel.RotationLimits rotationLimits,
            boolean enableCamDistChange, boolean enableEntityMarker, double stingerLockRange) {
        /** Upstream's client value of the Stinger's lock range is 120 until the server sends its own. */
        public static final Values DEFAULT = new Values(false, HeliFlightModel.RotationLimits.NONE, true, true, 120.0);
    }

    private static volatile Values client = Values.DEFAULT;

    private ServerSettings() {
    }

    /** The settings as the server has them (the defaults while the server config is not loaded). */
    public static Values server() {
        if (!ServerConfig.SPEC.isLoaded()) {
            return Values.DEFAULT;
        }
        return new Values(ServerConfig.SERVER_AUTHORITATIVE_MOVEMENT.get(),
                new HeliFlightModel.RotationLimits(ServerConfig.ENABLE_ROTATION_LIMIT.get(),
                        ServerConfig.PITCH_LIMIT_MAX.get().floatValue(),
                        ServerConfig.PITCH_LIMIT_MIN.get().floatValue(),
                        ServerConfig.ROLL_LIMIT.get().floatValue()),
                ServerConfig.ENABLE_CAM_DIST_CHANGE.get(), ServerConfig.ENABLE_ENTITY_MARKER.get(),
                ServerConfig.STINGER_LOCK_RANGE.get());
    }

    /** The settings the server last sent this client. */
    public static Values client() {
        return client;
    }

    public static void setClient(Values values) {
        client = values;
    }

    public static Values forSide(boolean clientSide) {
        return clientSide ? client() : server();
    }
}
