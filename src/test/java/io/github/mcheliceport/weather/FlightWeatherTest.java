package io.github.mcheliceport.weather;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.github.mcheliceport.client.hud.WeatherHudLayer;
import io.github.mcheliceport.weather.FlightWeather.Conditions;
import io.github.mcheliceport.weather.FlightWeather.Sensor;
import java.util.List;
import net.minecraft.world.phys.Vec3;
import org.junit.jupiter.api.Test;

/** M10e weather factors, gusts and icing on plain values, with the default config. */
class FlightWeatherTest {

    private static final Conditions RAIN = new Conditions(true, false, false, false);
    private static final Conditions STORM = new Conditions(true, true, false, false);
    private static final Conditions SNOW = new Conditions(true, false, true, false);
    private static final Conditions NIGHT = new Conditions(false, false, false, true);

    @Test
    void sensorFactorsFollowThePlanTable() {
        for (Sensor s : Sensor.values()) {
            assertEquals(1.0, FlightWeather.sensorFactor(Conditions.CLEAR, s, false), 1e-9, s + " in clear weather");
        }
        assertEquals(0.85, FlightWeather.sensorFactor(RAIN, Sensor.RADAR, false), 1e-9);
        assertEquals(0.70, FlightWeather.sensorFactor(STORM, Sensor.RADAR, false), 1e-9);
        assertEquals(0.80, FlightWeather.sensorFactor(RAIN, Sensor.IR, false), 1e-9);
        assertEquals(0.60, FlightWeather.sensorFactor(STORM, Sensor.IR, false), 1e-9);
        assertEquals(0.85, FlightWeather.sensorFactor(SNOW, Sensor.IR, false), 1e-9);
        assertEquals(0.60, FlightWeather.sensorFactor(NIGHT, Sensor.VISUAL, false), 1e-9);
        assertEquals(1.0, FlightWeather.sensorFactor(NIGHT, Sensor.VISUAL, true), 1e-9, "thermal sees at night");
        assertEquals(1.0, FlightWeather.sensorFactor(NIGHT, Sensor.IR, false), 1e-9, "night does not blind heat seekers");
        assertEquals(1.0, FlightWeather.sensorFactor(NIGHT, Sensor.RADAR, false), 1e-9);
        assertEquals(0.85 * 0.60, FlightWeather.sensorFactor(new Conditions(true, false, false, true), Sensor.VISUAL, false),
                1e-9, "rain and night together");
    }

    @Test
    void gustsStayWithinTheirRange() {
        for (long t = 0; t < 2000; t += 7) {
            assertEquals(Vec3.ZERO, FlightWeather.gust(Conditions.CLEAR, t, 42, 100, 63), "no gust in clear weather");
            double low = horizontal(FlightWeather.gust(RAIN, t, 42, 63, 63));
            assertTrue(low >= 0.02 - 1e-9 && low <= 0.05 + 1e-9, "rain at sea level: " + low);
            double high = horizontal(FlightWeather.gust(RAIN, t, 42, 63 + 200, 63));
            assertEquals(2.0 * low, high, 1e-9, "up to doubled by height");
            double storm = horizontal(FlightWeather.gust(STORM, t, 42, 63, 63));
            assertEquals(2.0 * low, storm, 1e-9, "doubled in a storm");
        }
        assertTrue(FlightWeather.gust(RAIN, 100, 1, 63, 63).distanceTo(FlightWeather.gust(RAIN, 100, 9000, 63, 63)) > 1e-4,
                "vehicles do not gust together");
    }

    private static double horizontal(Vec3 v) {
        return Math.sqrt(v.x * v.x + v.z * v.z);
    }

    @Test
    void iceBuildsInSnowAboveTheCloudsAndThaws() {
        assertTrue(FlightWeather.icing(SNOW, 200));
        assertTrue(!FlightWeather.icing(SNOW, 150) && !FlightWeather.icing(RAIN, 200), "only in snow above the clouds");
        assertEquals(1.0F, FlightWeather.nextIce(0.0F, true, FlightWeather.ICE_TICKS), 1e-6F, "iced up in 30 seconds");
        assertEquals(0.5F, FlightWeather.nextIce(1.0F, false, FlightWeather.THAW_TICKS / 2), 1e-6F, "thaws in a minute");
        assertEquals(0.0F, FlightWeather.nextIce(0.01F, false, 20), "never below none");
        assertEquals(0.90, FlightWeather.iceSpeedFactor(1.0F), 1e-9, "fully iced loses a tenth of its top speed");
        assertEquals(1.0, FlightWeather.iceSpeedFactor(0.0F), 1e-9);
    }

    @Test
    void turbulenceOnlyInAStorm() {
        assertEquals(0.0F, FlightWeather.turbulenceRoll(RAIN, 10.5, 3));
        float most = 0.0F;
        for (int t = 0; t < 400; t++) {
            most = Math.max(most, Math.abs(FlightWeather.turbulenceRoll(STORM, t * 0.5, 3)));
        }
        assertTrue(most > 0.3F && most <= 1.2F, "a small shake: " + most);
    }

    @Test
    void hudWords() {
        assertEquals(List.of(), WeatherHudLayer.words(Conditions.CLEAR, false, 0.0F, 0));
        assertEquals(List.of("GUST", "NIGHT"), WeatherHudLayer.words(new Conditions(true, false, false, true), true, 0.01F, 0));
        assertEquals(List.of("STORM", "ICE 40%", "RADAR OFF"), WeatherHudLayer.words(STORM, true, 0.4F, 12));
    }
}
