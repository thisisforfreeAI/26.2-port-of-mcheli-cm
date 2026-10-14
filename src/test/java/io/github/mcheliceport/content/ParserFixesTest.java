package io.github.mcheliceport.content;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.github.mcheliceport.content.aircraft.AircraftInfo;
import io.github.mcheliceport.content.aircraft.PlaneInfo;
import io.github.mcheliceport.content.aircraft.TankInfo;
import io.github.mcheliceport.content.aircraft.VehicleInfo;
import io.github.mcheliceport.content.parser.txt.TxtParser;
import io.github.mcheliceport.content.parser.yaml.YamlParser;
import io.github.mcheliceport.content.throwable.ThrowableInfo;
import io.github.mcheliceport.content.weapon.DamageFactor;
import io.github.mcheliceport.content.weapon.WeaponInfo;
import java.util.List;
import org.junit.jupiter.api.Test;

/**
 * Regression tests for the places where the port deliberately parses differently from upstream
 * (CHANGELOG.md lists each one).
 */
class ParserFixesTest {

    private static final ContentLocation LOCATION = new ContentLocation("test", "sample");

    private static List<String> lines(String text) {
        return List.of(text.split("\n"));
    }

    private static <T extends ContentData> T validated(T info) throws Exception {
        info.validate();
        return info;
    }

    @Test
    void enableVtolMapFormIsApplied() throws Exception {
        PlaneInfo info = validated(YamlParser.INSTANCE.parsePlane(LOCATION, "sample.yml", lines("""
                Seats:
                - Position: [0.0, 1.0, 0.0]
                PlaneFeatures:
                  EnableVtol:
                    IsDefault: true
                    Yaw: 0.5
                    Pitch: 0.4
                """), false));
        assertTrue(info.isEnableVtol);
        assertTrue(info.isDefaultVtol);
        assertEquals(0.5F, info.vtolYaw);
        assertEquals(0.4F, info.vtolPitch);
    }

    @Test
    void weightedCenterZIsApplied() throws Exception {
        TankInfo info = validated(YamlParser.INSTANCE.parseTank(LOCATION, "sample.yml", lines("""
                Seats:
                - Position: [0.0, 1.0, 0.0]
                TankFeatures:
                  WeightType: TANK
                  WeightedCenterZ: -0.25
                """), false));
        assertEquals(-0.25F, info.weightedCenterZ);
        assertEquals(2, info.weightType);
    }

    @Test
    void smokeVelocityIsApplied() throws Exception {
        ThrowableInfo info = validated(YamlParser.INSTANCE.parseThrowable(LOCATION, "sample.yml", lines("""
                Smoke:
                  Velocity:
                    Vertical: 2.5
                    Horizontal: 3.0
                """), false));
        assertEquals(2.5F, info.smokeVelocityVertical);
        assertEquals(3.0F, info.smokeVelocityHorizontal);
    }

    @Test
    void boundingBoxNameIsKept() throws Exception {
        VehicleInfo info = validated(YamlParser.INSTANCE.parseVehicle(LOCATION, "sample.yml", lines("""
                Seats:
                - Position: [0.0, 1.0, 0.0]
                BoundingBoxes:
                - Position: [0.0, 1.0, 0.0]
                  Size: [1.0, 2.0, 3.0]
                  Name: engine_bay
                  Type: ENGINE
                """), false));
        assertEquals("engine_bay", info.extraBoundingBox.getFirst().name);
    }

    @Test
    void rotBarrelIsAnAliasOfBarrelRot() throws Exception {
        VehicleInfo info = validated(YamlParser.INSTANCE.parseVehicle(LOCATION, "sample.yml", lines("""
                Seats:
                - Position: [0.0, 1.0, 0.0]
                Components:
                  Weapon:
                  - Position: [0.0, 1.0, 0.0]
                    WeaponNames: gun
                    RotBarrel: true
                """), false));
        assertTrue(info.partWeapon.getFirst().rotBarrel);
    }

    @Test
    void racksDoNotCountAsSeatsAfterLoading() throws Exception {
        // Upstream validated YAML vehicles twice, so racks were added to the seat count.
        AircraftInfo info = validated(YamlParser.INSTANCE.parseVehicle(LOCATION, "sample.yml", lines("""
                Seats:
                - Position: [0.0, 1.0, 0.0]
                - Position: [0.0, 1.0, -1.0]
                Racks:
                - Position: [0.0, 0.5, -3.0]
                  Camera:
                    Pos: [0.0, 1.5, -3.0]
                  Name: container
                """), false));
        assertEquals(2, info.getNumSeat());
        assertEquals(1, info.getNumRack());
        assertEquals(3, info.getNumSeatAndRack());
    }

    @Test
    void throwableTimesAreTicksInBothFormats() throws Exception {
        ThrowableInfo yml = validated(YamlParser.INSTANCE.parseThrowable(LOCATION, "sample.yml", lines("""
                TimeFuse: 40
                AliveTime: 600
                """), false));
        ThrowableInfo txt = validated(TxtParser.INSTANCE.parseThrowable(LOCATION, "sample.txt", lines("""
                TimeFuse = 2
                AliveTime = 30
                """), false));
        assertEquals(40, yml.timeFuse);
        assertEquals(600, yml.aliveTime);
        assertEquals(40, txt.timeFuse);
        assertEquals(600, txt.aliveTime);
        // Neither file sets a delay fuse; the default alive time is 10 seconds.
        ThrowableInfo defaults = validated(YamlParser.INSTANCE.parseThrowable(LOCATION, "sample.yml",
                lines("Power: 1"), false));
        assertEquals(200, defaults.aliveTime);
    }

    @Test
    void yamlDamageFactorsKeepDecimals() throws Exception {
        WeaponInfo info = validated(YamlParser.INSTANCE.parseWeapon(LOCATION, "sample.yml", lines("""
                Type: rocket
                DamageFactor:
                  Tank: 2.5
                  Heli: 0.5
                  minecraft:zombie: 1.5
                """), false));
        DamageFactor factors = info.damageFactor;
        assertEquals(2.5F, factors.getDamageFactor(DamageFactor.TANK));
        assertEquals(0.5F, factors.getDamageFactor(DamageFactor.HELICOPTER));
        assertEquals(1.5F, factors.getDamageFactor("minecraft:zombie"));
        assertEquals(1.0F, factors.getDamageFactor(DamageFactor.PLAYER));
    }

    @Test
    void rideRacksMayListSeveralRacks() throws Exception {
        VehicleInfo info = validated(YamlParser.INSTANCE.parseVehicle(LOCATION, "sample.yml", lines("""
                Seats:
                - Position: [0.0, 1.0, 0.0]
                RideRack:
                  c5: [1, 2]
                  mh-53e: 1
                """), false));
        assertEquals(List.of(new AircraftInfo.RideRack("c5", 1), new AircraftInfo.RideRack("c5", 2),
                new AircraftInfo.RideRack("mh-53e", 1)), info.rideRacks);
    }
}
