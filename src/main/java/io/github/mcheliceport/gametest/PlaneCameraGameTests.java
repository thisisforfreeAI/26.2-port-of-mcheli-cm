package io.github.mcheliceport.gametest;

import static io.github.mcheliceport.gametest.GameTestSupport.check;

import io.github.mcheliceport.content.ContentLocation;
import io.github.mcheliceport.content.aircraft.AircraftInfo;
import io.github.mcheliceport.content.aircraft.PlaneInfo;
import io.github.mcheliceport.content.aircraft.SeatInfo;
import io.github.mcheliceport.entity.aircraft.AircraftCamera;
import io.github.mcheliceport.entity.plane.PlaneEntity;
import io.github.mcheliceport.net.HeliControlPayload;
import io.github.mcheliceport.net.ModNetwork;
import io.github.mcheliceport.net.PlaneControlPayload;
import io.github.mcheliceport.net.PlayerControlSwitches;
import io.github.mcheliceport.registry.ModEntityTypes;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.util.ProblemReporter;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.storage.TagValueInput;
import net.minecraft.world.level.storage.TagValueOutput;
import net.minecraft.world.phys.Vec3;

/** Local camera choices exercised on real planes, without modifying the stock registry. */
final class PlaneCameraGameTests {
    private PlaneCameraGameTests() {
    }

    static void register() {
        ModGameTests.test("plane_camera_cycle", 30, PlaneCameraGameTests::cycle);
        ModGameTests.test("plane_camera_without_gunner", 30, PlaneCameraGameTests::withoutGunner);
        ModGameTests.test("plane_camera_per_viewer", 40, PlaneCameraGameTests::perViewer);
        ModGameTests.test("plane_camera_lifecycle", 30, PlaneCameraGameTests::lifecycle);
        ModGameTests.test("plane_camera_gunner_network", 30, PlaneCameraGameTests::gunnerNetwork);
    }

    /** Synthetic addon definition: three separated cameras and two crew seats. */
    private static PlaneEntity spawn(GameTestHelper helper, boolean gunner, int cameraCount) {
        PlaneInfo fixture = new PlaneInfo(ContentLocation.builtin("harrier_en"), "camera_fixture.yml", "yaml");
        fixture.isEnableGunnerMode = gunner;
        fixture.isEnableNightVision = true;
        fixture.seatList.add(new SeatInfo(Vec3.ZERO, false));
        fixture.seatList.add(new SeatInfo(new Vec3(1, 0, 0), true,
                new AircraftInfo.CameraPosition(new Vec3(2, 3, 4), false, 0, 0),
                true, false, false, 0, 0, false));
        fixture.seatList.add(new SeatInfo(new Vec3(-1, 0, 0), false));
        for (int i = 0; i < cameraCount; i++) {
            fixture.cameraPosition.add(new AircraftInfo.CameraPosition(new Vec3(i * 8, 2, -i * 6), false, 0, 0));
        }
        try {
            fixture.validate();
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
        PlaneEntity plane = new PlaneEntity(ModEntityTypes.PLANE.get(), helper.getLevel()) {
            @Override
            protected AircraftInfo lookupInfo(String name) {
                return name.equals("harrier_en") ? fixture : super.lookupInfo(name);
            }
        };
        plane.setAircraftTypeName("harrier_en");
        plane.setPos(helper.absoluteVec(new Vec3(12.5, 20, 12.5)));
        plane.setCurrentThrottle(0.9);
        plane.setFuel(plane.getMaxFuel());
        helper.getLevel().addFreshEntity(plane);
        return plane;
    }

    private static Player pilot(GameTestHelper helper, PlaneEntity plane) {
        Player pilot = GameTestSupport.player(helper, GameType.CREATIVE, new Vec3(12.5, 20, 10));
        check(helper, pilot.startRiding(plane, true, true), "pilot mounted");
        return pilot;
    }

    /** Expected sequence transcribed from MCP_ClientPlaneTickHandler#playerControl at cbaff7f. */
    private static void cycle(GameTestHelper helper) {
        PlaneEntity plane = spawn(helper, true, 3);
        Player pilot = pilot(helper, plane);
        check(helper, !plane.isViewFromCamera(pilot), "normal pilot view starts at the player");
        int[] cameras = {0, 1, 2, 0, 0};
        boolean[] gunners = {true, false, false, false, true};
        for (int i = 0; i < cameras.length; i++) {
            check(helper, plane.cycleCameraView(pilot), "H accepted at step " + i);
            check(helper, plane.getCameraId() == cameras[i] && plane.isGunnerMode() == gunners[i],
                    "upstream gunner/camera sequence at step " + i);
            check(helper, plane.getCameraPosInfo(pilot) == plane.getAcInfo().cameraPosition.get(cameras[i]),
                    "selected content position at step " + i);
            check(helper, plane.isViewFromCamera(pilot) == (gunners[i] || cameras[i] > 0),
                    "view entity switches with the selection at step " + i);
        }
        GameTestSupport.cleanUp(plane);
        helper.succeed();
    }

    private static void withoutGunner(GameTestHelper helper) {
        PlaneEntity plane = spawn(helper, false, 3);
        Player pilot = pilot(helper, plane);
        for (int expected : new int[] {1, 2, 0, 1}) {
            check(helper, plane.cycleCameraView(pilot) && plane.getCameraId() == expected && !plane.isGunnerMode(),
                    "camera-only vehicle cycles to " + expected);
        }
        plane.setCameraId(-1);
        check(helper, plane.getCameraId() == 0, "negative camera falls back to primary");
        plane.setCameraId(3);
        check(helper, plane.getCameraId() == 0, "past-end camera falls back to primary");
        GameTestSupport.cleanUp(plane);

        PlaneEntity single = spawn(helper, false, 1);
        Player singlePilot = pilot(helper, single);
        check(helper, !single.cycleCameraView(singlePilot) && !single.isViewFromCamera(singlePilot),
                "single camera without gunner refuses H");
        single.getPlaneInfo().alwaysCameraView = true;
        check(helper, single.isViewFromCamera(singlePilot), "AlwaysCameraView works for planes");
        GameTestSupport.cleanUp(single);

        PlaneEntity blocked = spawn(helper, true, 3);
        Player blockedPilot = pilot(helper, blocked);
        blocked.setCurrentThrottle(0);
        check(helper, !blocked.canSwitchGunnerMode() && blocked.cycleCameraView(blockedPilot)
                && blocked.getCameraId() == 1 && !blocked.isGunnerMode(),
                "camera positions remain available when flight conditions forbid gunner mode");
        GameTestSupport.cleanUp(blocked);
        helper.succeed();
    }

    private static void perViewer(GameTestHelper helper) {
        PlaneEntity plane = spawn(helper, false, 3);
        Player pilot = pilot(helper, plane);
        Player crew = GameTestSupport.player(helper, GameType.CREATIVE, new Vec3(12.5, 20, 10));
        Player passenger = GameTestSupport.player(helper, GameType.CREATIVE, new Vec3(12.5, 20, 10));
        Player stranger = GameTestSupport.player(helper, GameType.CREATIVE, new Vec3(12.5, 20, 10));
        helper.runAfterDelay(3, () -> {
            check(helper, crew.startRiding(plane.getSeats()[0], true, true), "gunner boarded seat 1");
            check(helper, passenger.startRiding(plane.getSeats()[1], true, true), "passenger boarded seat 2");
            check(helper, plane.cycleCameraView(pilot) && plane.getCameraId() == 1, "pilot chose extra camera");
            check(helper, plane.getCameraPosInfo(crew) == plane.getSeatInfo(1).getCamPos(),
                    "InvCamPos gunner keeps the seat camera");
            check(helper, plane.getCameraPosInfo(passenger) == plane.getAcInfo().cameraPosition.getFirst(),
                    "ordinary passenger keeps primary camera");
            check(helper, plane.isViewFromCamera(crew) && !plane.isViewFromCamera(passenger)
                    && !plane.isViewFromCamera(stranger), "pilot camera choice does not move other viewers");
            check(helper, !plane.cycleCameraView(crew) && !plane.cycleCameraView(stranger)
                    && plane.getCameraId() == 1, "crew and strangers cannot cycle the pilot camera");
            GameTestSupport.cleanUp(plane);
            helper.succeed();
        });
    }

    private static void lifecycle(GameTestHelper helper) {
        PlaneEntity plane = spawn(helper, false, 3);
        Player pilot = pilot(helper, plane);
        plane.cycleCameraView(pilot);
        pilot.stopRiding();
        check(helper, plane.getCameraId() == 0, "dismount resets local selection");
        check(helper, pilot.startRiding(plane, true, true), "pilot remounted");
        plane.cycleCameraView(pilot);
        TagValueOutput out = TagValueOutput.createWithContext(ProblemReporter.DISCARDING, helper.getLevel().registryAccess());
        check(helper, plane.save(out), "plane saved while viewing extra camera");
        Entity loaded = EntityType.loadEntityRecursive(TagValueInput.create(ProblemReporter.DISCARDING,
                helper.getLevel().registryAccess(), out.buildResult()), helper.getLevel(), EntitySpawnReason.LOAD, e -> e);
        check(helper, loaded instanceof PlaneEntity copy && copy.getCameraId() == 0,
                "local camera choice is not persisted or inherited by another session");
        loaded.getPassengers().forEach(Entity::discard);
        loaded.discard();
        plane.setAircraftTypeName("ac-130");
        check(helper, plane.getCameraId() == 0, "content type change resets selection");
        GameTestSupport.cleanUp(plane);

        PlaneEntity wreck = spawn(helper, false, 3);
        Player wreckPilot = pilot(helper, wreck);
        wreck.cycleCameraView(wreckPilot);
        wreck.setDamageTaken(wreck.getMaxHP());
        wreck.destroyAircraft();
        check(helper, wreck.getCameraId() == 0 && !wreck.cycleCameraView(wreckPilot),
                "destroyed plane resets selection and refuses cycling");
        GameTestSupport.cleanUp(wreck);
        helper.succeed();
    }

    private static PlaneControlPayload gunnerPacket(PlaneEntity plane, boolean gunner, int cameraMode) {
        return new PlaneControlPayload(new HeliControlPayload(plane.getId(), HeliControlPayload.UnmountAction.NONE,
                gunner ? HeliControlPayload.ModeSwitch.GUNNER_ON : HeliControlPayload.ModeSwitch.GUNNER_OFF,
                HeliControlPayload.HatchSwitch.NONE, HeliControlPayload.GearSwitch.NONE, PlayerControlSwitches.NONE,
                (byte) 0, HeliControlPayload.BladeStatus.NONE, (byte) (cameraMode + 1)),
                PlaneControlPayload.PartSwitch.NONE, PlaneControlPayload.PartSwitch.NONE);
    }

    private static void gunnerNetwork(GameTestHelper helper) {
        PlaneEntity plane = spawn(helper, true, 3);
        Player pilot = pilot(helper, plane);
        Player stranger = GameTestSupport.player(helper, GameType.CREATIVE, new Vec3(12.5, 20, 10));
        ModNetwork.applyPlaneControl(plane, stranger, gunnerPacket(plane, true, AircraftCamera.MODE_NIGHTVISION));
        check(helper, !plane.isGunnerMode() && plane.getCameraMode(pilot) == AircraftCamera.MODE_NORMAL,
                "unrelated sender cannot change pilot gunner/camera mode");
        ModNetwork.applyPlaneControl(plane, pilot, gunnerPacket(plane, true, AircraftCamera.MODE_NIGHTVISION));
        check(helper, plane.isGunnerMode() && plane.getCameraMode(pilot) == AircraftCamera.MODE_NIGHTVISION,
                "plane pilot gunner and night vision switches reach server");
        plane.setCameraId(1);
        ModNetwork.applyPlaneControl(plane, pilot, gunnerPacket(plane, false, AircraftCamera.MODE_NORMAL));
        check(helper, !plane.isGunnerMode() && plane.getCameraId() == 1,
                "gunner-off packet leaves local position selection alone");
        GameTestSupport.cleanUp(plane);
        helper.succeed();
    }
}
