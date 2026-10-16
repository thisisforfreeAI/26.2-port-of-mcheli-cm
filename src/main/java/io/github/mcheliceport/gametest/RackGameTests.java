package io.github.mcheliceport.gametest;

import static io.github.mcheliceport.gametest.GameTestSupport.check;

import io.github.mcheliceport.config.CommonConfig;
import io.github.mcheliceport.config.ServerConfig;
import io.github.mcheliceport.content.ContentLocation;
import io.github.mcheliceport.content.ContentType;
import io.github.mcheliceport.content.aircraft.AircraftInfo;
import io.github.mcheliceport.content.aircraft.HeliInfo;
import io.github.mcheliceport.content.aircraft.SeatInfo;
import io.github.mcheliceport.content.aircraft.SeatRackInfo;
import io.github.mcheliceport.entity.aircraft.AircraftEntity;
import io.github.mcheliceport.entity.aircraft.AircraftRacks;
import io.github.mcheliceport.entity.container.CargoContainerEntity;
import io.github.mcheliceport.entity.heli.HeliEntity;
import io.github.mcheliceport.entity.parachute.ParachuteEntity;
import io.github.mcheliceport.entity.seat.SeatEntity;
import io.github.mcheliceport.item.AircraftItem;
import io.github.mcheliceport.net.AircraftMotionPayload;
import io.github.mcheliceport.net.ModNetwork;
import io.github.mcheliceport.net.RackControlPayload;
import io.github.mcheliceport.registry.ModEntityTypes;
import io.github.mcheliceport.registry.ModItems;
import io.github.mcheliceport.util.McheliMath;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.util.ProblemReporter;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.storage.TagValueInput;
import net.minecraft.world.level.storage.TagValueOutput;
import net.minecraft.world.phys.Vec3;

/** Rack eligibility, real passenger trees, authority, release and stock content integration. */
final class RackGameTests {
    private RackGameTests() { }
    static void register() {
        ModGameTests.test("racks_stock_container_and_exclusions", 30, RackGameTests::stockContainers);
        ModGameTests.test("racks_stock_vehicle_eligibility", 30, RackGameTests::stockVehicles);
        ModGameTests.test("racks_load_gates_and_opt_in", 30, RackGameTests::gates);
        ModGameTests.test("racks_pilot_authority_and_pose", 30, RackGameTests::authority);
        ModGameTests.test("racks_release_order_and_blocked_exit", 30, RackGameTests::release);
        ModGameTests.test("racks_airdrop_and_landing", 180, RackGameTests::airdrop);
        ModGameTests.test("racks_passenger_tree_persistence", 30, RackGameTests::persistence);
        ModGameTests.test("racks_carrier_removal", 30, RackGameTests::removal);
        ModGameTests.test("racks_cancelled_mount_and_drop", 30, RackGameTests::cancelled);
        ModGameTests.test("racks_released_tank_carrier_grace", 30, RackGameTests::carrierGrace);
        ModGameTests.test("racks_aircraft_touchdown_handoff", 200, RackGameTests::aircraftTouchdown);
    }
    private static List<Integer> racks(AircraftEntity carrier) {
        var ids = new ArrayList<Integer>();
        for (int i = 0; i < carrier.getSeats().length; i++) {
            if (carrier.getSeatInfo(i + 1) instanceof SeatRackInfo) { ids.add(i); }
        }
        return ids;
    }
    private static AircraftEntity stock(GameTestHelper h, String name, ContentType type, Vec3 relative) {
        var item = (AircraftItem) ModItems.contentItems().stream().filter(c -> c.type() == type && c.contentName().equals(name))
                .findFirst().orElseThrow().item().get();
        var pos = h.absoluteVec(relative);
        var ac = item.createAircraft(h.getLevel(), pos.x, pos.y, pos.z);
        h.getLevel().addFreshEntity(ac); ac.tick(); return ac;
    }
    private static HeliInfo definition(String... names) {
        var info = new HeliInfo(ContentLocation.builtin("rack_fixture"), "rack_fixture.yml", "yaml");
        info.maxHp = 1000; info.maxFuel = 1000;
        info.seatList.add(new SeatInfo(Vec3.ZERO, false));
        info.seatList.add(new SeatInfo(Vec3.ZERO, false));
        info.seatList.add(new SeatRackInfo(names, 0, 1.5, 1, new Vec3(0, 1, -6),
                new AircraftInfo.CameraPosition(new Vec3(0, 1, -5), false, 0, 0), 3, 20, 30, 12, true));
        return info;
    }
    private static HeliEntity fixture(GameTestHelper h, HeliInfo definition, Vec3 relative) {
        var ac = new HeliEntity(ModEntityTypes.HELICOPTER.get(), h.getLevel()) {
            @Override protected AircraftInfo lookupInfo(String name) { return definition; }
            @Override protected void onUpdateAircraft() { }
        };
        ac.setAircraftTypeName("rack_fixture"); ac.setPos(h.absoluteVec(relative));
        h.getLevel().addFreshEntity(ac); ac.tick(); return ac;
    }
    private static CargoContainerEntity cargo(GameTestHelper h, Vec3 absolute) {
        var cargo = new CargoContainerEntity(ModEntityTypes.CONTAINER.get(), h.getLevel());
        cargo.setPos(absolute); h.getLevel().addFreshEntity(cargo); return cargo;
    }
    private static Vec3 entry(AircraftEntity ac, int index) {
        return ac.getTransformedPosition(((SeatRackInfo) ac.getSeatInfo(index + 1)).getEntryPos());
    }
    private static void cleanup(Entity... entities) {
        for (var e : entities) {
            if (e instanceof CargoContainerEntity cargo) { cargo.clearContent(); }
            e.discard();
        }
    }
    private static void stockContainers(GameTestHelper h) {
        var carrier = stock(h, "ch47", ContentType.HELICOPTER, new Vec3(12, 1, 18));
        var ids = racks(carrier); var loads = new ArrayList<CargoContainerEntity>();
        for (int i = 0; i < 4; i++) { loads.add(cargo(h, entry(carrier, ids.get(0)))); }
        check(h, carrier.racks().load() == 4 && loads.stream().allMatch(Entity::isPassenger), "CH-47 loads four containers in stock racks");
        var zodiac = stock(h, "zodiac", ContentType.SHIP, new Vec3(12, 1, 5));
        check(h, !AircraftRacks.accepts(carrier, ids.get(4), zodiac), "occupied container racks exclude stock Zodiac rack");
        for (var load : loads) { cleanup(load); }
        zodiac.setPos(entry(carrier, ids.get(4)));
        check(h, carrier.racks().load() == 1 && zodiac.getVehicle() == carrier.getSeats()[ids.get(4)], "Zodiac can use the fifth rack once exclusions are clear");
        check(h, carrier.racks().load() == 0, "occupied racks do not duplicate loads");
        var player = GameTestSupport.player(h, GameType.SURVIVAL, new Vec3(12, 1, 12));
        for (int i = 0; i < carrier.getSeats().length; i++) {
            if (!(carrier.getSeatInfo(i + 1) instanceof SeatRackInfo)) {
                var cow = EntityTypes.COW.create(h.getLevel(), EntitySpawnReason.COMMAND);
                h.getLevel().addFreshEntity(cow); cow.startRiding(carrier.getSeats()[i], true, true);
            }
        }
        carrier.interactFirstSeat(player);
        check(h, !player.isPassenger(), "normal boarding never occupies a rack");
        for (var seat : carrier.getSeats()) { if (seat.getRider() != null && seat.getRider() != zodiac) { cleanup(seat.getRider()); } }
        cleanup(zodiac, carrier); h.succeed();
    }
    private static void stockVehicles(GameTestHelper h) {
        for (var pair : List.of(new String[] {"mh-53e", "HELICOPTER", "growler", "TANK"},
                new String[] {"mv-22", "PLANE", "growler", "TANK"}, new String[] {"sh-60", "HELICOPTER", "zodiac", "SHIP"},
                new String[] {"mark5", "SHIP", "zodiac", "SHIP"}, new String[] {"c5", "PLANE", "m1a2", "TANK"})) {
            var carrier = stock(h, pair[0], ContentType.valueOf(pair[1]), new Vec3(12, 5, 12));
            var child = stock(h, pair[2], ContentType.valueOf(pair[3]), new Vec3(12, 5, 12));
            check(h, racks(carrier).stream().anyMatch(i -> AircraftRacks.accepts(carrier, i, child)), pair[0] + " accepts " + pair[2] + " by name or RideRack");
            cleanup(child, carrier);
        }
        var parent = fixture(h, definition("HELICOPTER"), new Vec3(12, 5, 12));
        var child = fixture(h, definition("container"), new Vec3(12, 5, 8));
        var load = cargo(h, entry(child, 1)); child.racks().load();
        check(h, !AircraftRacks.accepts(parent, 1, child), "a loaded transport cannot be nested into another carrier");
        load.stopRiding();
        check(h, AircraftRacks.accepts(parent, 1, child), "case-insensitive kind-name matching accepts empty transport");
        cleanup(load, child, parent); h.succeed();
    }
    private static void gates(GameTestHelper h) {
        var carrier = fixture(h, definition("container"), new Vec3(12, 1, 12));
        var load = cargo(h, entry(carrier, 1));
        GameTestSupport.withConfig(CommonConfig.ENABLE_PUT_RACK_IN_FLYING, false, () -> {
            carrier.setCurrentThrottle(.31);
            check(h, carrier.racks().load() == 0, "disabled airborne loading rejects throttle over .3");
            carrier.setCurrentThrottle(.3); carrier.setPos(carrier.position().add(0, 8, 0)); load.setPos(entry(carrier, 1));
            check(h, carrier.racks().load() == 0, "disabled airborne loading requires ground within three blocks");
        });
        GameTestSupport.withConfig(CommonConfig.ENABLE_PUT_RACK_IN_FLYING, true, () -> check(h, carrier.racks().load() == 1, "default permits airborne carrier loading"));
        load.stopRiding(); load.setPos(entry(carrier, 1).add(10, 0, 0));
        check(h, carrier.racks().load() == 0, "rack entry range limits loading");
        var cow = EntityTypes.COW.create(h.getLevel(), EntitySpawnReason.COMMAND); cow.setPos(entry(carrier, 1)); h.getLevel().addFreshEntity(cow);
        check(h, carrier.racks().load() == 0, "arbitrary entities are refused by default");
        cow.getPersistentData().putBoolean("CanMountEntity", true);
        check(h, carrier.racks().load() == 1 && cow.getVehicle() == carrier.getSeats()[1], "server-side upstream CanMountEntity opt-in works");
        cleanup(cow, load, carrier); h.succeed();
    }
    private static void authority(GameTestHelper h) {
        var carrier = fixture(h, definition("tank"), new Vec3(12, 6, 15));
        var child = stock(h, "growler", ContentType.TANK, new Vec3(12, 6, 10)); child.setPos(entry(carrier, 1));
        var pilot = GameTestSupport.player(h, GameType.SURVIVAL, new Vec3(12, 6, 14)); pilot.startRiding(carrier, true, true);
        var stranger = GameTestSupport.player(h, GameType.SURVIVAL, new Vec3(12, 6, 13));
        ModNetwork.applyRackControl(stranger, new RackControlPayload(carrier.getId(), RackControlPayload.Action.LOAD));
        ModNetwork.applyRackControl(pilot, new RackControlPayload(child.getId(), RackControlPayload.Action.LOAD));
        check(h, !child.isPassenger(), "outsider and wrong-id rack packets rejected");
        stranger.startRiding(carrier.getSeats()[0], true, true);
        ModNetwork.applyRackControl(stranger, new RackControlPayload(carrier.getId(), RackControlPayload.Action.LOAD));
        check(h, !child.isPassenger(), "crew cannot operate racks");
        ModNetwork.applyRackControl(pilot, new RackControlPayload(carrier.getId(), RackControlPayload.Action.LOAD));
        check(h, child.getVehicle() == carrier.getSeats()[1], "carrier pilot loads eligible truck");
        carrier.setAttitudeFromPilot(65, 18, -25); carrier.getSeats()[1].positionCargo();
        var q = McheliMath.worldOrientationQuat(65, 18, -25).mul(McheliMath.worldOrientationQuat(30, 12, 0));
        Vec3 expected = McheliMath.applyQuat(q, 1, 2, 3);
        Vec3 actual = McheliMath.applyQuat(McheliMath.worldOrientationQuat(child.getYaw(), child.getPitch(), child.getRoll()), 1, 2, 3);
        check(h, expected.distanceTo(actual) < 1e-4 && child.position().distanceTo(carrier.getSeatPosition(2)) < 1e-6, "carried world attitude composes carrier tilt and rack angles");
        var protectedBlock = child.blockPosition();
        h.getLevel().setBlock(protectedBlock, Blocks.GLASS_PANE.defaultBlockState(), 3);
        for (boolean serverAuthority : new boolean[] {false, true}) {
            GameTestSupport.withConfig(ServerConfig.SERVER_AUTHORITATIVE_MOVEMENT, serverAuthority, () -> {
                var pos = child.position(); child.setDeltaMovement(3, 2, 1); child.setCurrentThrottle(1); child.throttleUp = true;
                ModNetwork.applyPilotMotion(child, new AircraftMotionPayload(child.getId(), 3, 2, 1, 1)); child.tick();
                check(h, !child.simulates() && !child.followsPilot() && child.position().distanceTo(pos) < 1e-6
                        && child.getCurrentThrottle() == 0 && !child.canMouseRot(), "carried truck cannot drive or accept motion in either authority mode");
            });
        }
        check(h, h.getLevel().getBlockState(protectedBlock).is(Blocks.GLASS_PANE), "carried truck cannot crush blocks");
        h.getLevel().removeBlock(protectedBlock, false);
        var childPilot = GameTestSupport.player(h, GameType.SURVIVAL, new Vec3(12, 6, 12)); childPilot.startRiding(child, true, true);
        ModNetwork.applyRackControl(childPilot, new RackControlPayload(child.getId(), RackControlPayload.Action.LEAVE));
        check(h, !child.isPassenger() && child.getPilot() == childPilot, "loaded vehicle's pilot leaves while keeping its own driver");
        GameTestSupport.withConfig(CommonConfig.ENABLE_PUT_RACK_IN_FLYING, false, () -> {
            check(h, child.racks().canRide(), "released vehicle sees the nearby compatible rack");
            ModNetwork.applyRackControl(childPilot, new RackControlPayload(child.getId(), RackControlPayload.Action.RIDE));
            check(h, !child.isPassenger(), "vanilla dismount cooldown prevents immediate reboarding");
            child.throttleUp = false;
            for (int i = 0; i < 60; i++) { child.tick(); }
            child.setPos(entry(carrier, 1)); child.setDeltaMovement(Vec3.ZERO);
            ModNetwork.applyRackControl(childPilot, new RackControlPayload(child.getId(), RackControlPayload.Action.RIDE));
            check(h, child.getVehicle() == carrier.getSeats()[1], "self-boarding uses its independent upstream path even with carrier flying-load disabled");
        });
        cleanup(child, carrier); h.succeed();
    }
    private static void release(GameTestHelper h) {
        var carrier = fixture(h, definition("container"), new Vec3(12, 1, 12));
        var load = cargo(h, entry(carrier, 1)); carrier.racks().load();
        var exit = carrier.getTransformedPosition(new Vec3(0, 1, -6)); var block = BlockPos.containing(exit);
        h.getLevel().setBlock(block, Blocks.STONE.defaultBlockState(), 3);
        check(h, !carrier.racks().unload() && load.getVehicle() == carrier.getSeats()[1], "blocked release keeps cargo attached");
        h.getLevel().removeBlock(block, false);
        check(h, carrier.racks().unload() && !load.isPassenger() && load.position().distanceTo(exit) < 1e-6, "safe explicit release uses body-space position");
        cleanup(load, carrier);
        var ch47 = stock(h, "ch47", ContentType.HELICOPTER, new Vec3(12, 1, 18));
        var a = cargo(h, entry(ch47, racks(ch47).get(0))); var b = cargo(h, a.position()); ch47.racks().load();
        check(h, ch47.racks().unload() && a.isPassenger() != b.isPassenger(), "only one load is released per action");
        check(h, ch47.getSeats()[racks(ch47).get(0)].getRider() != null && ch47.getSeats()[racks(ch47).get(1)].getRider() == null,
                "unload visits highest occupied rack first");
        cleanup(a, b, ch47, load, carrier); h.succeed();
    }
    private static void airdrop(GameTestHelper h) {
        var carrier = fixture(h, definition("container"), new Vec3(12, 26, 12));
        var load = cargo(h, entry(carrier, 1)); load.setItem(53, new ItemStack(Items.DIAMOND, 17)); carrier.racks().load();
        carrier.setDeltaMovement(.1, -.05, .02);
        check(h, carrier.racks().unload() && load.getVehicle() instanceof ParachuteEntity, "high release deploys type-3 transport parachute");
        var chute = (ParachuteEntity) load.getVehicle();
        check(h, chute.getModelType() == 3 && chute.getDeltaMovement().equals(carrier.getDeltaMovement()) && load.getItem(53).getCount() == 17,
                "airdrop inherits carrier velocity and preserves last-slot cargo");
        h.succeedWhen(() -> {
            check(h, chute.isRemoved() && !load.isPassenger(), "transport chute releases cargo after landing");
            check(h, load.getItem(53).getCount() == 17 && !load.isRemoved(), "landed cargo is intact"); cleanup(load, carrier);
        });
    }
    private static AircraftEntity copy(GameTestHelper h, AircraftEntity carrier) {
        var output = TagValueOutput.createWithContext(ProblemReporter.DISCARDING, h.getLevel().registryAccess());
        check(h, carrier.save(output), "carrier tree saves");
        return (AircraftEntity) EntityType.loadEntityRecursive(TagValueInput.create(ProblemReporter.DISCARDING,
                h.getLevel().registryAccess(), output.buildResult()), h.getLevel(), EntitySpawnReason.LOAD, e -> e);
    }
    private static void persistence(GameTestHelper h) {
        var carrier = stock(h, "growler", ContentType.TANK, new Vec3(12, 1, 15));
        var index = racks(carrier).getFirst(); var load = cargo(h, entry(carrier, index));
        load.setItem(53, new ItemStack(Items.DIAMOND, 31)); carrier.racks().load();
        var copy = copy(h, carrier); var seat = copy.getSeats()[index];
        check(h, seat != null && seat.getRider() instanceof CargoContainerEntity
                && ((CargoContainerEntity) seat.getRider()).getItem(53).getCount() == 31, "rack, load and inventory reload in one passenger tree");
        int count = copy.getPassengers().size(); copy.tick();
        check(h, copy.getPassengers().size() == count && copy.getSeats()[index] == seat, "ensureSeats reuses saved rack without duplication");
        cleanup(seat.getRider(), copy, load, carrier); h.succeed();
    }
    private static void removal(GameTestHelper h) {
        var carrier = fixture(h, definition("container"), new Vec3(12, 5, 12));
        var load = cargo(h, entry(carrier, 1)); load.setItem(53, new ItemStack(Items.DIAMOND, 19)); carrier.racks().load();
        carrier.discard();
        check(h, load.isAlive() && !load.isPassenger() && load.getItem(53).getCount() == 19,
                "carrier removal ejects the surviving load without dropping its contents");
        check(h, h.getLevel().getEntitiesOfClass(ItemEntity.class, load.getBoundingBox().inflate(3)).isEmpty(), "no duplicate world items on carrier removal");
        cleanup(load); h.succeed();
    }
    private static void cancelled(GameTestHelper h) {
        var carrier = fixture(h, definition("container"), new Vec3(12, 26, 12));
        var load = cargo(h, entry(carrier, 1)); load.setItem(53, new ItemStack(Items.DIAMOND, 19));
        java.util.function.Consumer<net.neoforged.neoforge.event.entity.EntityMountEvent> denyMount = e -> {
            if (e.getEntityMounting() == load && e.isMounting()) { e.setCanceled(true); }
        };
        var bus = net.neoforged.neoforge.common.NeoForge.EVENT_BUS;
        bus.addListener(net.neoforged.neoforge.event.entity.EntityMountEvent.class, denyMount);
        try { check(h, carrier.racks().load() == 0 && !load.isPassenger(), "cancelled mount is not counted as loaded"); }
        finally { bus.unregister(denyMount); }
        check(h, carrier.racks().load() == 1, "load succeeds after mount cancellation clears");
        java.util.function.Consumer<net.neoforged.neoforge.event.entity.EntityMountEvent> denyDismount = e -> {
            if (e.getEntityMounting() == load && e.isDismounting()) { e.setCanceled(true); }
        };
        bus.addListener(net.neoforged.neoforge.event.entity.EntityMountEvent.class, denyDismount);
        try { check(h, !carrier.racks().unload() && load.getVehicle() == carrier.getSeats()[1], "cancelled dismount keeps the rack tree intact"); }
        finally { bus.unregister(denyDismount); }
        java.util.function.Consumer<net.neoforged.neoforge.event.entity.EntityJoinLevelEvent> denySpawn = e -> {
            if (e.getEntity() instanceof ParachuteEntity) { e.setCanceled(true); }
        };
        bus.addListener(net.neoforged.neoforge.event.entity.EntityJoinLevelEvent.class, denySpawn);
        try { check(h, !carrier.racks().unload() && load.getVehicle() == carrier.getSeats()[1], "refused parachute spawn does not release cargo"); }
        finally { bus.unregister(denySpawn); }
        java.util.function.Consumer<net.neoforged.neoforge.event.entity.EntityMountEvent> denyChute = e -> {
            if (e.getEntityMounting() == load && e.isMounting() && e.getEntityBeingMounted() instanceof ParachuteEntity) { e.setCanceled(true); }
        };
        bus.addListener(net.neoforged.neoforge.event.entity.EntityMountEvent.class, denyChute);
        try { check(h, !carrier.racks().unload() && load.getVehicle() == carrier.getSeats()[1], "refused parachute mount restores the original rack"); }
        finally { bus.unregister(denyChute); }
        check(h, load.getItem(53).getCount() == 19 && h.getLevel().getEntitiesOfClass(ParachuteEntity.class, carrier.getBoundingBox().inflate(15)).isEmpty(),
                "failure paths preserve inventory and leave no empty parachutes");
        cleanup(load, carrier); h.succeed();
    }
    private static io.github.mcheliceport.entity.tank.TankEntity stationaryTruck(GameTestHelper h, Vec3 position) {
        var truck = new io.github.mcheliceport.entity.tank.TankEntity(ModEntityTypes.TANK.get(), h.getLevel()) {
            @Override protected void onUpdateAircraft() { }
        };
        truck.setAircraftTypeName("growler"); truck.setPos(position); h.getLevel().addFreshEntity(truck); truck.tick(); return truck;
    }
    private static void carrierGrace(GameTestHelper h) {
        var carrier = fixture(h, definition("tank"), new Vec3(12, 5, 12));
        var truck = stationaryTruck(h, entry(carrier, 1));
        check(h, carrier.racks().load() == 1 && truck.racks().leave(), "truck releases using the normal handoff path");
        truck.setPos(carrier.position());
        var cow = EntityTypes.COW.create(h.getLevel(), EntitySpawnReason.COMMAND);
        cow.setNoAi(true); cow.setNoGravity(true); cow.setPos(carrier.position().add(.3, 0, 0)); h.getLevel().addFreshEntity(cow);
        truck.setDeltaMovement(.2, 0, 0); truck.tick();
        check(h, carrier.getDamageTaken() == 0 && cow.getHealth() < cow.getMaxHealth(),
                "released truck resumes collision damage but retains upstream carrier grace");
        cleanup(cow, truck, carrier); h.succeed();
    }
    private static void aircraftTouchdown(GameTestHelper h) {
        var carrier = fixture(h, definition("tank"), new Vec3(12, 26, 12));
        var truck = stationaryTruck(h, entry(carrier, 1));
        carrier.racks().load(); check(h, carrier.racks().unload() && truck.getVehicle() instanceof ParachuteEntity, "aircraft release mounts transport chute");
        var chute = (ParachuteEntity) truck.getVehicle();
        h.succeedWhen(() -> {
            check(h, chute.isRemoved() && !truck.isPassenger(), "aircraft is handed back after chute touchdown");
            check(h, truck.getDeltaMovement().equals(Vec3.ZERO) && truck.isAlive(), "touchdown synchronizes a surviving stationary vehicle");
            cleanup(truck, carrier);
        });
    }
}
