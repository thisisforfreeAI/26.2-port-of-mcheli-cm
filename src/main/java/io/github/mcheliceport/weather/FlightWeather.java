package io.github.mcheliceport.weather;

import io.github.mcheliceport.config.ServerConfig;
import io.github.mcheliceport.entity.aircraft.AircraftCamera;
import io.github.mcheliceport.entity.aircraft.AircraftEntity;
import io.github.mcheliceport.entity.heli.HeliEntity;
import io.github.mcheliceport.entity.plane.PlaneEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LightningBolt;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LightLayer;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.common.ModConfigSpec;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.entity.EntityJoinLevelEvent;
import org.jspecify.annotations.Nullable;

/**
 * M10e, a port addition: rain, thunder, snow and night as they change flight and sensors. Every
 * value comes from the level's own weather and time and the vehicle, so the side that moves a
 * vehicle and the side that checks it agree. {@code VehicleWeather} (M8) stays the visual layer.
 * See {@code docs/M10_WEATHER_NIGHT.md}.
 */
public final class FlightWeather {

    /** What a sensor sees by. */
    public enum Sensor {
        /** Radar missiles and the radar feed. */
        RADAR,
        /** Heat seekers. */
        IR,
        /** Everything else: the eye, a TV or laser seeker, an AI pilot looking for targets. */
        VISUAL
    }

    /**
     * The weather at a place.
     *
     * @param rain    rain or snow falls there
     * @param thunder and it is a thunderstorm
     * @param snow    the precipitation there is snow
     * @param night   it is dark outside
     */
    public record Conditions(boolean rain, boolean thunder, boolean snow, boolean night) {
        public static final Conditions CLEAR = new Conditions(false, false, false, false);
    }

    /** Ticks a fully iced vehicle takes to thaw, and half that to ice up. */
    public static final int THAW_TICKS = 1200;
    public static final int ICE_TICKS = 600;
    /** Ticks a lightning strike within {@link #LIGHTNING_RANGE} blanks a vehicle's radar. */
    public static final int RADAR_BLANK_TICKS = 40;
    public static final double LIGHTNING_RANGE = 64.0;

    private FlightWeather() {
    }

    /** Listens for lightning strikes on the server. */
    public static void register() {
        NeoForge.EVENT_BUS.addListener((EntityJoinLevelEvent event) -> {
            if (event.getEntity() instanceof LightningBolt bolt && !event.getLevel().isClientSide()) {
                lightning(event.getLevel(), bolt.position());
            }
        });
    }

    public static boolean enabled() {
        return !ServerConfig.SPEC.isLoaded() || ServerConfig.WEATHER.get();
    }

    private static double cfg(ModConfigSpec.DoubleValue value) {
        return ServerConfig.SPEC.isLoaded() ? value.get() : value.getDefault();
    }

    /** The weather at {@code pos}: clear with weather gameplay off or where the dimension has none. */
    public static Conditions at(Level level, Vec3 pos) {
        if (!enabled()) {
            return Conditions.CLEAR;
        }
        BlockPos at = BlockPos.containing(pos);
        Biome.Precipitation p = level.hasChunkAt(at) ? level.precipitationAt(at) : Biome.Precipitation.NONE;
        boolean rain = p != Biome.Precipitation.NONE;
        return new Conditions(rain, rain && level.isThundering(), p == Biome.Precipitation.SNOW, level.isDarkOutside());
    }

    /**
     * How far a sensor reaches as a share of its clear-weather range. Radar and heat seekers lose
     * range in rain and more in a storm, heat seekers less in snow than rain; the eye loses range at
     * night unless {@code seesInDark} (thermal or night vision, a searchlight, a lit target) and in
     * rain. The losses multiply.
     */
    public static double sensorFactor(Conditions c, Sensor sensor, boolean seesInDark) {
        double f = 1.0;
        switch (sensor) {
            case RADAR -> {
                if (c.thunder()) {
                    f = cfg(ServerConfig.WEATHER_RADAR_THUNDER);
                } else if (c.rain()) {
                    f = cfg(ServerConfig.WEATHER_RADAR_RAIN);
                }
            }
            case IR -> {
                if (c.thunder()) {
                    f = cfg(ServerConfig.WEATHER_IR_THUNDER);
                } else if (c.snow()) {
                    f = cfg(ServerConfig.WEATHER_IR_SNOW);
                } else if (c.rain()) {
                    f = cfg(ServerConfig.WEATHER_IR_RAIN);
                }
            }
            case VISUAL -> {
                if (c.thunder()) {
                    f = cfg(ServerConfig.WEATHER_RADAR_THUNDER);
                } else if (c.rain()) {
                    f = cfg(ServerConfig.WEATHER_RADAR_RAIN);
                }
                if (c.night() && !seesInDark) {
                    f *= cfg(ServerConfig.WEATHER_VISUAL_NIGHT);
                }
            }
        }
        return f;
    }

    /** {@link #sensorFactor} at the sensor's place. */
    public static double sensorFactor(Level level, Vec3 pos, Sensor sensor, boolean seesInDark) {
        return sensorFactor(at(level, pos), sensor, seesInDark);
    }

    /**
     * The gust drift, in blocks a tick, for a vehicle at height {@code y} in rain: a speed between
     * the config's least and most that rises and falls over a few seconds, doubled in a storm and
     * up to doubled again by height (from sea level to 128 blocks above it), in a direction that
     * turns slowly. {@code seed} keeps vehicles from gusting together. Zero in clear weather.
     */
    public static Vec3 gust(Conditions c, long gameTime, int seed, double y, int seaLevel) {
        if (!c.rain()) {
            return Vec3.ZERO;
        }
        double t = gameTime + (seed & 0xFFFF) * 7.0;
        double min = cfg(ServerConfig.WEATHER_GUST_MIN);
        double max = Math.max(min, cfg(ServerConfig.WEATHER_GUST_MAX));
        double swell = 0.5 + 0.5 * Math.sin(t * 0.05) * Math.cos(t * 0.017);
        double speed = Mth.lerp(swell, min, max);
        speed *= c.thunder() ? 2.0 : 1.0;
        speed *= 1.0 + Mth.clamp((y - seaLevel) / 128.0, 0.0, 1.0);
        double dir = (seed % 360) * Mth.DEG_TO_RAD + t * 0.004 + 0.6 * Math.sin(t * 0.011);
        double lift = 0.3 * Math.sin(t * 0.07);
        return new Vec3(-Math.sin(dir) * speed, lift * speed, Math.cos(dir) * speed);
    }

    /** The gust on a flying helicopter or plane this tick; zero on the ground, destroyed, or for other vehicles. */
    public static Vec3 gust(AircraftEntity ac) {
        if (!(ac instanceof HeliEntity || ac instanceof PlaneEntity) || ac.onGround() || ac.isDestroyed() || ac.isInWater() || !enabled()) {
            return Vec3.ZERO;
        }
        Level level = ac.level();
        return gust(at(level, ac.position()), level.getGameTime(), ac.getUUID().hashCode(), ac.getY(), level.getSeaLevel());
    }

    /** A lightning strike blanks the radar of every vehicle within {@link #LIGHTNING_RANGE} for 2 seconds. */
    public static void lightning(Level level, Vec3 at) {
        if (!enabled()) {
            return;
        }
        for (AircraftEntity ac : level.getEntitiesOfClass(AircraftEntity.class,
                new AABB(at, at).inflate(LIGHTNING_RANGE))) {
            if (ac.position().distanceToSqr(at) <= LIGHTNING_RANGE * LIGHTNING_RANGE) {
                ac.setRadarBlankTicks(RADAR_BLANK_TICKS);
            }
        }
    }

    /**
     * Whether a sensor on {@code vehicle} (worked by {@code user}, or null for its AI pilot) sees in
     * the dark: night or thermal vision on that seat's camera, or the vehicle's searchlight on.
     */
    public static boolean seesInDark(AircraftEntity vehicle, @Nullable Entity user) {
        if (vehicle.isSearchLightOn()) {
            return true;
        }
        if (user == null) {
            return false;
        }
        int mode = vehicle.getCameraMode(user);
        return mode == AircraftCamera.MODE_NIGHTVISION || mode == AircraftCamera.MODE_THERMALVISION;
    }

    /** Whether a target shows in the dark: its own searchlight on, or block light 8 or more where it is. */
    public static boolean lit(Entity target) {
        if (target instanceof AircraftEntity ac && ac.isSearchLightOn()) {
            return true;
        }
        return target.level().getBrightness(LightLayer.BLOCK, BlockPos.containing(target.getEyePosition())) >= 8;
    }

    /** Whether a vehicle ices up: in falling snow above the icing height. */
    public static boolean icing(Conditions c, double y) {
        return c.snow() && y >= cfg(ServerConfig.WEATHER_ICING_HEIGHT);
    }

    /** The ice after {@code ticks} more ticks, 0 (none) to 1 (fully iced). */
    public static float nextIce(float ice, boolean icing, int ticks) {
        float step = icing ? ticks / (float) ICE_TICKS : -ticks / (float) THAW_TICKS;
        return Mth.clamp(ice + step, 0.0F, 1.0F);
    }

    /** The share of its top speed an iced vehicle keeps. */
    public static double iceSpeedFactor(float ice) {
        return 1.0 - cfg(ServerConfig.WEATHER_ICE_SLOW) * Mth.clamp(ice, 0.0F, 1.0F);
    }

    /** The camera's turbulence roll, in degrees, for a flying vehicle in a thunderstorm; 0 otherwise. */
    public static float turbulenceRoll(Conditions c, double time, int seed) {
        if (!c.thunder()) {
            return 0.0F;
        }
        double t = time + (seed & 0xFFFF);
        return (float) (0.8 * Math.sin(t * 0.9) * Math.sin(t * 0.31) + 0.4 * Math.sin(t * 2.3));
    }
}
