package io.github.mcheliceport.gametest;

import static io.github.mcheliceport.gametest.GameTestSupport.check;

import io.github.mcheliceport.content.ContentLocation;
import io.github.mcheliceport.content.ContentType;
import io.github.mcheliceport.content.aircraft.PlaneInfo;
import io.github.mcheliceport.config.ServerConfig;
import io.github.mcheliceport.entity.plane.PlaneEntity;
import io.github.mcheliceport.item.AircraftItem;
import io.github.mcheliceport.net.HeliControlPayload;
import io.github.mcheliceport.net.AircraftMotionPayload;
import io.github.mcheliceport.net.ModNetwork;
import io.github.mcheliceport.net.PlaneControlPayload;
import io.github.mcheliceport.net.PlayerControlSwitches;
import io.github.mcheliceport.registry.ModItems;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.util.ProblemReporter;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MoverType;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.storage.TagValueInput;
import net.minecraft.world.level.storage.TagValueOutput;
import net.minecraft.world.phys.Vec3;

/** Real plane placement, vehicle state, flight and server-side authorization checks. */
final class PlaneGameTests {
    private PlaneGameTests() {
    }

    static void register() {
        ModGameTests.test("planes_all_stock_spawn", 30, PlaneGameTests::allSpawn);
        ModGameTests.test("plane_controls_and_vtol_persist", 100, PlaneGameTests::controlsAndPersistence);
        ModGameTests.test("plane_vtol_takes_off", 240, PlaneGameTests::takeOff);
        ModGameTests.test("plane_movement_authority", 30, PlaneGameTests::movementAuthority);
        ModGameTests.test("plane_default_vtol_save", 30, PlaneGameTests::defaultVtolSave);
    }

    private static AircraftItem item(String name) {
        return (AircraftItem) ModItems.contentItems().stream()
                .filter(i -> i.type() == ContentType.PLANE && i.contentName().equals(name))
                .findFirst().orElseThrow().item().get();
    }

    private static PlaneEntity spawn(GameTestHelper helper, String name, Vec3 relative) {
        Vec3 pos = helper.absoluteVec(relative);
        PlaneEntity plane = (PlaneEntity) item(name).createAircraft(helper.getLevel(), pos.x, pos.y, pos.z);
        check(helper, plane != null, "created " + name);
        plane.initRotationYaw(0.0F);
        helper.getLevel().addFreshEntity(plane);
        return plane;
    }

    private static PlaneControlPayload control(PlaneEntity plane, boolean up, PlaneControlPayload.PartSwitch vtol) {
        return new PlaneControlPayload(HeliControlPayload.switchesOnly(plane.getId(), PlayerControlSwitches.NONE
                .with(PlayerControlSwitches.THROTTLE_UP, up)), vtol, PlaneControlPayload.PartSwitch.NONE);
    }

    private static void allSpawn(GameTestHelper helper) {
        List<PlaneEntity> planes = new ArrayList<>();
        for (ModItems.ContentItem content : ModItems.contentItems()) {
            if (content.type() != ContentType.PLANE) { continue; }
            AircraftItem item = (AircraftItem) content.item().get();
            PlaneInfo info = (PlaneInfo) item.getAircraftInfo();
            if (!ContentLocation.BUILTIN_DOMAIN.equals(info.getLocation().addonDomain())) { continue; }
            check(helper, item.isPlaceable(), info.name + ": plane placement enabled");
            PlaneEntity plane = spawn(helper, info.name, new Vec3(12.5, 20.0, 12.5));
            check(helper, plane.getPlaneInfo() == info, info.name + ": definition attached");
            check(helper, plane.getMaxHP() == info.maxHp && plane.getMaxFuel() == info.maxFuel,
                    info.name + ": health and fuel capacity");
            check(helper, (plane.partWing != null) == info.haveWing(), info.name + ": wing parts");
            check(helper, (plane.partNozzle != null) == (info.haveNozzle() || info.haveRotor() || info.isEnableVtol),
                    info.name + ": nozzle/rotor parts");
            planes.add(plane);
        }
        check(helper, planes.size() == 46, "46 stock planes created, got " + planes.size());
        helper.runAfterDelay(2, () -> {
            for (PlaneEntity plane : planes) {
                check(helper, !plane.isRemoved(), plane.getAircraftTypeName() + ": still present");
                check(helper, plane.getSeats().length == plane.getSeatNum(), "crew seats initialized");
                GameTestSupport.cleanUp(plane);
            }
            helper.succeed();
        });
    }

    private static void controlsAndPersistence(GameTestHelper helper) {
        PlaneEntity plane = spawn(helper, "harrier_en", new Vec3(12.5, 15.0, 12.5));
        plane.setFuel(plane.getMaxFuel());
        Player pilot = GameTestSupport.player(helper, GameType.CREATIVE, new Vec3(12.5, 15.0, 10.0));
        Player stranger = GameTestSupport.player(helper, GameType.CREATIVE, new Vec3(11.5, 15.0, 10.0));
        check(helper, pilot.startRiding(plane, true, true), "pilot can mount a plane");
        check(helper, plane.isPilot(pilot), "rider is the controlling pilot");
        PlaneControlPayload on = control(plane, true, PlaneControlPayload.PartSwitch.ON);
        ModNetwork.applyPlaneControl(plane, stranger, on);
        check(helper, !plane.throttleUp && plane.getVtolMode() == 0, "unrelated player cannot control plane");
        PlaneControlPayload wrongId = new PlaneControlPayload(HeliControlPayload.switchesOnly(plane.getId() + 1,
                PlayerControlSwitches.NONE.with(PlayerControlSwitches.THROTTLE_UP, true)),
                PlaneControlPayload.PartSwitch.ON, PlaneControlPayload.PartSwitch.NONE);
        ModNetwork.applyPlaneControl(plane, pilot, wrongId);
        check(helper, !plane.throttleUp && plane.getVtolMode() == 0, "wrong vehicle ID refused");
        ModNetwork.applyPlaneControl(plane, pilot, on);
        check(helper, plane.throttleUp && plane.partNozzle.getStatus(), "pilot controls throttle and VTOL");
        plane.setFuel(120);
        plane.setDamageTaken(15);
        plane.setCurrentThrottle(0.65);
        TagValueOutput out = TagValueOutput.createWithContext(ProblemReporter.DISCARDING, helper.getLevel().registryAccess());
        check(helper, plane.save(out), "plane saved");
        Entity copy = EntityType.loadEntityRecursive(TagValueInput.create(ProblemReporter.DISCARDING,
                helper.getLevel().registryAccess(), out.buildResult()), helper.getLevel(), EntitySpawnReason.LOAD, e -> e);
        check(helper, copy instanceof PlaneEntity, "loaded real plane entity");
        PlaneEntity loaded = (PlaneEntity) copy;
        check(helper, loaded.getAircraftTypeName().equals("harrier_en"), "type persisted");
        check(helper, loaded.getFuel() == 120 && loaded.getDamageTaken() == 15, "fuel and damage persisted");
        check(helper, Math.abs(loaded.getCurrentThrottle() - 0.65) < 1.0E-9, "throttle persisted");
        check(helper, loaded.getVtolMode() == 2, "saved VTOL state restored at its endpoint");
        loaded.getPassengers().forEach(Entity::discard);
        loaded.discard();
        GameTestSupport.cleanUp(plane);
        helper.succeed();
    }

    private static void takeOff(GameTestHelper helper) {
        PlaneEntity plane = spawn(helper, "harrier_en", new Vec3(12.5, 1.0, 12.5));
        Player pilot = GameTestSupport.player(helper, GameType.CREATIVE, new Vec3(12.5, 1.0, 10.0));
        plane.setFuel(plane.getMaxFuel());
        check(helper, pilot.startRiding(plane, true, true), "mounted");
        if (plane.partCanopy != null) { plane.partCanopy.setStatusServer(false); }
        plane.switchVtol(true);
        plane.partNozzle.rotation = plane.partNozzle.prevRotation = 90.0F;
        plane.setCurrentThrottle(1.0);
        ModNetwork.applyPlaneControl(plane, pilot, control(plane, true, PlaneControlPayload.PartSwitch.NONE));
        double y = plane.getY();
        helper.runAfterDelay(120, () -> {
            check(helper, plane.getY() > y + 2.0, "VTOL plane climbed: " + (plane.getY() - y));
            check(helper, plane.getPilot() == pilot, "pilot remains mounted");
            GameTestSupport.cleanUp(plane);
            helper.succeed();
        });
    }

    private static void defaultVtolSave(GameTestHelper helper) {
        PlaneEntity plane = spawn(helper, "mv-22", new Vec3(12.5, 15.0, 12.5));
        check(helper, plane.getVtolMode() == 2, "Osprey initially uses default VTOL");
        plane.switchVtol(false);
        check(helper, !plane.partNozzle.getStatus(), "Osprey switched to forward flight");
        TagValueOutput out = TagValueOutput.createWithContext(ProblemReporter.DISCARDING, helper.getLevel().registryAccess());
        check(helper, plane.save(out), "Osprey saved");
        Entity copy = EntityType.loadEntityRecursive(TagValueInput.create(ProblemReporter.DISCARDING,
                helper.getLevel().registryAccess(), out.buildResult()), helper.getLevel(), EntitySpawnReason.LOAD, e -> e);
        check(helper, copy instanceof PlaneEntity loaded && loaded.getVtolMode() == 0,
                "default VTOL does not overwrite saved forward-flight mode");
        copy.getPassengers().forEach(Entity::discard);
        copy.discard();
        GameTestSupport.cleanUp(plane);
        helper.succeed();
    }

    @SuppressWarnings("removal")
    private static void movementAuthority(GameTestHelper helper) {
        Vec3 pos = helper.absoluteVec(new Vec3(12.5, 15.0, 12.5));
        PlaneEntity plane = (PlaneEntity) item("harrier_en").onTileClick(helper.getLevel(), 90.0F,
                (int) Math.floor(pos.x), (int) Math.floor(pos.y) - 1, (int) Math.floor(pos.z));
        check(helper, plane != null, "plane item places a collision-checked entity");
        helper.getLevel().addFreshEntity(plane);
        ServerPlayer pilot = helper.makeMockServerPlayerInLevel();
        try {
            check(helper, pilot.startRiding(plane, true, true), "pilot boarded");
            plane.setFuel(plane.getMaxFuel());
            check(helper, plane.isClientAuthoritative() && plane.followsPilot() && !plane.simulates(),
                    "real pilot's client simulates by default");
            GameTestSupport.withConfig(ServerConfig.SERVER_AUTHORITATIVE_MOVEMENT, true, () ->
                    check(helper, !plane.isClientAuthoritative() && plane.simulates(), "server mode simulates on server"));
            ModNetwork.applyPilotMotion(plane, new AircraftMotionPayload(plane.getId(), 0.2, 0.25, 0.1, 0.7F));
            check(helper, plane.getDeltaMovement().equals(new Vec3(0.2, 0.25, 0.1)), "plane accepts valid pilot velocity");
            Vec3 old = plane.position();
            plane.move(MoverType.PLAYER, new Vec3(100.0, 5.0, 0.0));
            Vec3 moved = plane.position().subtract(old);
            check(helper, moved.horizontalDistance() <= plane.getMaxSpeedForChecks() + 1.0E-6,
                    "impossible horizontal move capped using the two-step plane speed");
            check(helper, moved.y <= 1.0 + 1.0E-6, "impossible climb capped");
            Vec3 velocity = plane.getDeltaMovement();
            ModNetwork.applyPilotMotion(plane, new AircraftMotionPayload(plane.getId(), Double.NaN, 0, 0, 1.0F));
            check(helper, plane.getDeltaMovement().equals(velocity), "non-finite velocity refused");
        } finally {
            GameTestSupport.cleanUp(plane);
            helper.getLevel().getServer().getPlayerList().remove(pilot);
        }
        helper.succeed();
    }
}
