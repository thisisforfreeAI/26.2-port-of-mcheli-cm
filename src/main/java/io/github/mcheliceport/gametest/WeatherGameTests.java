package io.github.mcheliceport.gametest;

import static io.github.mcheliceport.gametest.GameTestSupport.AH64;
import static io.github.mcheliceport.gametest.GameTestSupport.check;

import io.github.mcheliceport.config.ServerConfig;
import io.github.mcheliceport.entity.heli.HeliEntity;
import io.github.mcheliceport.radar.EntityInfoFeed;
import io.github.mcheliceport.weapon.guidance.WeaponGuidanceSystem;
import io.github.mcheliceport.weather.FlightWeather;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.LightningBolt;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.phys.Vec3;

/**
 * M10e weather in the test level: rain and a storm shorten heat seeker, radar seeker and radar feed
 * ranges and gust a flying helicopter, lightning blanks its radar for 2 seconds, and ice thaws.
 * Alone, as the level's weather is everyone's.
 */
final class WeatherGameTests {

    private WeatherGameTests() {
    }

    static void register() {
        ModGameTests.testAlone("weather_rain_and_storm_on_sensors_and_flight", 40, WeatherGameTests::rainAndStorm);
    }

    private static void rainAndStorm(GameTestHelper h) {
        ServerLevel level = h.getLevel();
        // Out under the sky, 10 blocks over whatever the test area has on top.
        BlockPos o = h.absolutePos(BlockPos.ZERO);
        int top = level.getHeight(Heightmap.Types.MOTION_BLOCKING, o.getX() + 8, o.getZ() + 8);
        HeliEntity heli = GameTestSupport.spawn(h, AH64, new Vec3(8.5, top + 10 - o.getY(), 8.5), 0.0F);
        heli.setNoGravity(true);
        var husk = EntityTypes.HUSK.create(level, EntitySpawnReason.COMMAND);
        husk.setNoAi(true);
        husk.snapTo(heli.getX() + 50, heli.getY(), heli.getZ(), 0.0F, 0.0F);
        level.addFreshEntity(husk);
        WeaponGuidanceSystem heat = new WeaponGuidanceSystem(level);
        heat.lockRange = 100.0;
        WeaponGuidanceSystem radar = new WeaponGuidanceSystem(level);
        radar.lockRange = 100.0;
        radar.isHeatSeekerMissile = false;
        radar.isRadarMissile = true;
        try {
            level.setRainLevel(0.0F);
            level.setThunderLevel(0.0F);
            check(h, !FlightWeather.at(level, heli.position()).rain(), "the test starts dry");
            check(h, heat.lockRangeOn(heli, husk) == 100.0 && EntityInfoFeed.rangeAt(heli) == EntityInfoFeed.RANGE,
                    "clear weather keeps every range");
            check(h, FlightWeather.gust(heli) == Vec3.ZERO, "no gust in clear weather");

            level.setRainLevel(1.0F);
            check(h, FlightWeather.at(level, heli.position()).rain(), "it rains on the helicopter");
            check(h, Math.abs(heat.lockRangeOn(heli, husk) - 80.0) < 1e-6, "a heat seeker reaches 80%: " + heat.lockRangeOn(heli, husk));
            check(h, Math.abs(radar.lockRangeOn(heli, husk) - 85.0) < 1e-6, "a radar seeker reaches 85%");
            check(h, Math.abs(EntityInfoFeed.rangeAt(heli) - EntityInfoFeed.RANGE * 0.85) < 1e-6, "the radar feed reaches 85%");
            Vec3 gust = FlightWeather.gust(heli);
            double speed = Math.sqrt(gust.x * gust.x + gust.z * gust.z);
            check(h, speed >= 0.02 - 1e-9 && speed <= 0.10 + 1e-9, "a flying helicopter gusts 0.02 to 0.05, up to doubled by height: " + speed);

            level.setThunderLevel(1.0F);
            check(h, FlightWeather.at(level, heli.position()).thunder(), "a storm");
            check(h, Math.abs(heat.lockRangeOn(heli, husk) - 60.0) < 1e-6, "a heat seeker reaches 60% in a storm");
            check(h, Math.abs(EntityInfoFeed.rangeAt(heli) - EntityInfoFeed.RANGE * 0.70) < 1e-6, "the radar feed reaches 70%");

            GameTestSupport.withConfig(ServerConfig.WEATHER, false, () ->
                    check(h, heat.lockRangeOn(heli, husk) == 100.0 && FlightWeather.gust(heli) == Vec3.ZERO,
                            "with weather gameplay off the storm changes nothing"));

            LightningBolt bolt = EntityTypes.LIGHTNING_BOLT.create(level, EntitySpawnReason.COMMAND);
            bolt.setVisualOnly(true);
            bolt.snapTo(heli.getX() + 20, heli.getY() - 10, heli.getZ());
            level.addFreshEntity(bolt);
            check(h, heli.getRadarBlankTicks() == FlightWeather.RADAR_BLANK_TICKS, "lightning 22 blocks off blanks the radar");

            heli.setIce(0.5F);
            for (int i = 0; i < 20; i++) {
                heli.tick();
            }
            check(h, heli.getRadarBlankTicks() == FlightWeather.RADAR_BLANK_TICKS - 20, "the blank counts down: "
                    + heli.getRadarBlankTicks());
            check(h, heli.getIce() < 0.5F, "out of snow the ice thaws: " + heli.getIce());
        } finally {
            level.setRainLevel(0.0F);
            level.setThunderLevel(0.0F);
            husk.discard();
            GameTestSupport.cleanUp(heli);
        }
        h.succeed();
    }
}
