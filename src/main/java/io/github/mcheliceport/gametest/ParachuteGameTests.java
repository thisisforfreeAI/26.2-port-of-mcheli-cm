package io.github.mcheliceport.gametest;

import static io.github.mcheliceport.gametest.GameTestSupport.check;

import io.github.mcheliceport.content.ContentType;
import io.github.mcheliceport.entity.aircraft.AircraftEntity;
import io.github.mcheliceport.entity.parachute.ParachuteEntity;
import io.github.mcheliceport.entity.plane.PlaneEntity;
import io.github.mcheliceport.item.AircraftItem;
import io.github.mcheliceport.menu.AircraftMenu;
import io.github.mcheliceport.net.HeliControlPayload;
import io.github.mcheliceport.net.ModNetwork;
import io.github.mcheliceport.net.PlaneControlPayload;
import io.github.mcheliceport.net.PlayerControlSwitches;
import io.github.mcheliceport.registry.ModEntityTypes;
import io.github.mcheliceport.registry.ModItems;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.util.ProblemReporter;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.animal.pig.Pig;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.vehicle.boat.Boat;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.storage.TagValueInput;
import net.minecraft.world.level.storage.TagValueOutput;
import net.minecraft.world.phys.Vec3;

/** Deployment, reserve inventory, server authorization and actual landing/water behavior. */
final class ParachuteGameTests {
    private ParachuteGameTests() {
    }

    static void register() {
        ModGameTests.test("parachute_item_deploy", 30, ParachuteGameTests::itemDeploy);
        ModGameTests.test("parachute_item_refusals", 30, ParachuteGameTests::itemRefusals);
        ModGameTests.test("parachute_landing", 220, ParachuteGameTests::landing);
        ModGameTests.test("ejection_authorization_and_consumption", 30, ParachuteGameTests::ejection);
        ModGameTests.test("ejection_opens_in_flight", 110, ParachuteGameTests::opensInFlight);
        ModGameTests.test("ejection_first_crew", 40, ParachuteGameTests::firstCrew);
        ModGameTests.test("ejection_keeps_other_player", 40, ParachuteGameTests::keepsOtherPlayer);
        ModGameTests.test("ejection_rescues_mob", 40, ParachuteGameTests::rescuesMob);
        ModGameTests.test("parachute_menu_transfers", 30, ParachuteGameTests::menuTransfers);
        ModGameTests.test("parachute_inventory_persistence", 30, ParachuteGameTests::inventoryPersistence);
        ModGameTests.test("parachute_water_rescue", 40, ParachuteGameTests::waterRescue);
        ModGameTests.test("parachute_small_pool", 40, ParachuteGameTests::smallPool);
        ModGameTests.test("parachute_transport_seat", 40, ParachuteGameTests::transportSeat);
    }

    private static Player player(GameTestHelper helper, GameType mode) {
        return GameTestSupport.player(helper, mode, new Vec3(12.5, 20, 12.5));
    }

    private static PlaneEntity plane(GameTestHelper helper, String name) {
        AircraftItem item = (AircraftItem) ModItems.contentItems().stream()
                .filter(i -> i.type() == ContentType.PLANE && i.contentName().equals(name))
                .findFirst().orElseThrow().item().get();
        Vec3 pos = helper.absoluteVec(new Vec3(12.5, 20, 12.5));
        PlaneEntity plane = (PlaneEntity) item.createAircraft(helper.getLevel(), pos.x, pos.y, pos.z);
        helper.getLevel().addFreshEntity(plane);
        return plane;
    }

    private static Player pilot(GameTestHelper helper, PlaneEntity plane) {
        Player player = player(helper, GameType.CREATIVE);
        check(helper, player.startRiding(plane, true, true), "pilot boarded");
        return player;
    }

    private static void stock(PlaneEntity plane, int count) {
        for (int i = 0; i < count; i++) {
            plane.getParachuteInventory().setItem(i, new ItemStack(ModItems.PARACHUTE.get()));
        }
    }

    private static PlaneControlPayload ejectPacket(PlaneEntity plane, int id) {
        return new PlaneControlPayload(HeliControlPayload.switchesOnly(id, PlayerControlSwitches.NONE
                .with(PlayerControlSwitches.EJECT_SEAT, true)), PlaneControlPayload.PartSwitch.NONE,
                PlaneControlPayload.PartSwitch.NONE);
    }

    private static void clean(GameTestHelper helper, AircraftEntity aircraft) {
        aircraft.removeVehicle(false);
        helper.getEntities(ModEntityTypes.PARACHUTE.get()).forEach(Entity::discard);
    }

    private static void itemDeploy(GameTestHelper helper) {
        Player user = player(helper, GameType.SURVIVAL);
        user.setOnGround(false);
        user.setDeltaMovement(0.1, -0.6, -0.2);
        user.fallDistance = 15;
        user.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(ModItems.PARACHUTE.get()));
        Vec3 from = user.position();
        ModItems.PARACHUTE.get().use(helper.getLevel(), user, InteractionHand.MAIN_HAND);
        check(helper, user.getVehicle() instanceof ParachuteEntity, "item mounts the user immediately");
        ParachuteEntity chute = (ParachuteEntity) user.getVehicle();
        check(helper, chute.getModelType() == 1 && chute.isOpenParachute(), "hand chute is already open");
        check(helper, chute.position().distanceTo(from.add(0.5, 3.5, 0.5)) < 1.0E-9,
                "upstream deployment offset");
        check(helper, chute.getDeltaMovement().equals(new Vec3(0.1, -0.6, -0.2)) && chute.fallDistance == 15,
                "momentum and accumulated fall are inherited");
        check(helper, user.fallDistance == 0 && user.getItemInHand(InteractionHand.MAIN_HAND).isEmpty(),
                "survival deployment consumes one chute and resets rider fall distance");
        chute.discard();
        helper.succeed();
    }

    private static void itemRefusals(GameTestHelper helper) {
        Player user = player(helper, GameType.SURVIVAL);
        user.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(ModItems.PARACHUTE.get()));
        user.setOnGround(true);
        ModItems.PARACHUTE.get().use(helper.getLevel(), user, InteractionHand.MAIN_HAND);
        check(helper, user.getVehicle() == null && user.getItemInHand(InteractionHand.MAIN_HAND).getCount() == 1,
                "grounded use neither deploys nor consumes");
        user.setOnGround(false);
        ParachuteEntity chute = ParachuteEntity.deploy(helper.getLevel(), user, 1, user.position(), Vec3.ZERO);
        ModItems.PARACHUTE.get().use(helper.getLevel(), user, InteractionHand.MAIN_HAND);
        check(helper, user.getVehicle() == chute && helper.getEntities(ModEntityTypes.PARACHUTE.get()).size() == 1
                && user.getItemInHand(InteractionHand.MAIN_HAND).getCount() == 1, "mounted use cannot duplicate a chute");
        chute.discard();
        Player creative = player(helper, GameType.CREATIVE);
        creative.setOnGround(false);
        creative.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(ModItems.PARACHUTE.get()));
        ModItems.PARACHUTE.get().use(helper.getLevel(), creative, InteractionHand.MAIN_HAND);
        check(helper, creative.getVehicle() instanceof ParachuteEntity
                && creative.getItemInHand(InteractionHand.MAIN_HAND).getCount() == 1, "creative deployment keeps item");
        creative.getVehicle().discard();
        helper.succeed();
    }

    private static void landing(GameTestHelper helper) {
        Player rider = player(helper, GameType.SURVIVAL);
        ParachuteEntity chute = ParachuteEntity.deploy(helper.getLevel(), rider, 1,
                helper.absoluteVec(new Vec3(12, 30, 12)), Vec3.ZERO);
        double startY = chute.getY();
        helper.runAfterDelay(35, () -> {
            check(helper, !chute.isRemoved() && chute.getY() < startY, "chute descends on the server");
            check(helper, chute.getDeltaMovement().y > -0.3 && chute.getDeltaMovement().y < -0.25,
                    "upstream descent speed");
            check(helper, rider.getVehicle() == chute, "rider stays attached during descent");
        });
        helper.runAfterDelay(180, () -> {
            check(helper, chute.isRemoved() && rider.getVehicle() == null, "landing removes chute and releases rider");
            check(helper, rider.isAlive() && rider.getHealth() == rider.getMaxHealth(), "rider lands without fall damage");
            helper.succeed();
        });
    }

    private static void ejection(GameTestHelper helper) {
        PlaneEntity plane = plane(helper, "harrier_en");
        Player pilot = pilot(helper, plane);
        Player stranger = player(helper, GameType.CREATIVE);
        PlaneControlPayload eject = ejectPacket(plane, plane.getId());
        ModNetwork.applyPlaneControl(plane, pilot, eject);
        check(helper, pilot.getVehicle() == plane, "empty reserve refuses ejection");
        plane.getParachuteInventory().setItem(1, new ItemStack(ModItems.PARACHUTE.get()));
        ModNetwork.applyPlaneControl(plane, stranger, eject);
        ModNetwork.applyPlaneControl(plane, pilot, ejectPacket(plane, plane.getId() + 1));
        check(helper, pilot.getVehicle() == plane && plane.haveParachute(), "wrong sender and vehicle ID refused");
        plane.setYaw(30); plane.setPitch(20); plane.setRoll(-15);
        Vec3 motion = new Vec3(0.2, -0.1, 0.15);
        plane.setDeltaMovement(motion);
        Vec3 expectedPosition = plane.getTransformedPosition(plane.getSeatInfo(0).pos.add(0, 2, 0));
        Vec3 expectedMotion = plane.getTransformedPosition(0, 2, 0, 0, 0, 0).add(motion);
        ModNetwork.applyPlaneControl(plane, pilot, eject);
        check(helper, pilot.getVehicle() instanceof ParachuteEntity, "authorized pilot ejected");
        ParachuteEntity chute = (ParachuteEntity) pilot.getVehicle();
        check(helper, chute.getModelType() == 2 && !chute.isOpenParachute(), "ejection starts with the seat model");
        check(helper, chute.position().distanceTo(expectedPosition) < 1.0E-9, "launch uses rotated seat position");
        check(helper, Math.abs(chute.getDeltaMovement().y - expectedMotion.y) < 1.0E-9
                && Math.abs(chute.getDeltaMovement().x - expectedMotion.x) <= 0.05
                && Math.abs(chute.getDeltaMovement().z - expectedMotion.z) <= 0.05, "rotated boost and inherited motion");
        check(helper, !plane.haveParachute() && plane.getParachuteInventory().getItem(1).isEmpty(),
                "consumes the same slot that supplied the chute");
        ModNetwork.applyPlaneControl(plane, pilot, eject);
        check(helper, helper.getEntities(ModEntityTypes.PARACHUTE.get()).size() == 1, "replayed request cannot eject again");
        clean(helper, plane);
        helper.succeed();
    }

    private static void firstCrew(GameTestHelper helper) {
        PlaneEntity plane = plane(helper, "f14d");
        Player pilot = pilot(helper, plane);
        Player crew = player(helper, GameType.CREATIVE);
        stock(plane, 1);
        helper.runAfterDelay(3, () -> {
            check(helper, crew.startRiding(plane.getSeats()[0], true, true), "first crew boarded");
            ModNetwork.applyPlaneControl(plane, crew, ejectPacket(plane, plane.getId()));
            check(helper, crew.getVehicle() instanceof ParachuteEntity && pilot.getVehicle() == plane,
                    "first crew ejects independently of pilot");
            check(helper, !plane.haveParachute(), "one reserve consumed");
            check(helper, plane.partCanopy == null || plane.partCanopy.getStatus(), "ejection opens canopy");
            clean(helper, plane);
            helper.succeed();
        });
    }

    private static void opensInFlight(GameTestHelper helper) {
        PlaneEntity plane = plane(helper, "harrier_en");
        Player pilot = pilot(helper, plane);
        stock(plane, 1);
        check(helper, plane.ejectSeat(pilot), "ejection started");
        ParachuteEntity chute = (ParachuteEntity) pilot.getVehicle();
        double start = chute.getY();
        helper.runAfterDelay(10, () -> check(helper, !chute.isOpenParachute() && chute.getY() > start,
                "closed seat rises during the initial boost"));
        helper.runAfterDelay(70, () -> {
            check(helper, !chute.isRemoved() && chute.isOpenParachute() && chute.getDeltaMovement().y < -0.1,
                    "seat opens during actual descent");
            check(helper, pilot.getVehicle() == chute && !plane.haveParachute(), "rider stays attached without another charge");
            clean(helper, plane);
            helper.succeed();
        });
    }

    private static void keepsOtherPlayer(GameTestHelper helper) {
        PlaneEntity plane = plane(helper, "f14d");
        Player pilot = pilot(helper, plane);
        Player crew = player(helper, GameType.CREATIVE);
        stock(plane, 2);
        helper.runAfterDelay(3, () -> {
            crew.startRiding(plane.getSeats()[0], true, true);
            ModNetwork.applyPlaneControl(plane, pilot, ejectPacket(plane, plane.getId()));
            check(helper, pilot.getVehicle() instanceof ParachuteEntity && crew.getVehicle() == plane.getSeats()[0],
                    "pilot cannot force another player out");
            check(helper, plane.haveParachute(), "other player's chute remains stocked");
            clean(helper, plane);
            helper.succeed();
        });
    }

    private static void rescuesMob(GameTestHelper helper) {
        PlaneEntity plane = plane(helper, "f14d");
        Player pilot = pilot(helper, plane);
        Pig mob = EntityTypes.PIG.create(helper.getLevel(), EntitySpawnReason.COMMAND);
        mob.setNoAi(true);
        mob.setPos(plane.position());
        helper.getLevel().addFreshEntity(mob);
        stock(plane, 2);
        helper.runAfterDelay(3, () -> {
            mob.startRiding(plane.getSeats()[0], true, true);
            ModNetwork.applyPlaneControl(plane, pilot, ejectPacket(plane, plane.getId()));
            check(helper, pilot.getVehicle() instanceof ParachuteEntity && mob.getVehicle() instanceof ParachuteEntity,
                    "pilot also rescues non-player first crew");
            check(helper, pilot.getVehicle() != mob.getVehicle() && !plane.haveParachute(), "two separate chutes consumed");
            clean(helper, plane);
            mob.discard();
            helper.succeed();
        });
    }

    private static void menuTransfers(GameTestHelper helper) {
        PlaneEntity plane = plane(helper, "f14d");
        Player player = player(helper, GameType.SURVIVAL);
        AircraftMenu menu = new AircraftMenu(1, player.getInventory(), plane);
        check(helper, menu.getParachuteSlots() == 2, "two-seat fighter exposes two reserve slots");
        int chuteSlot = menu.getParachuteSlotStart();
        int playerSlots = chuteSlot + menu.getParachuteSlots();
        check(helper, !menu.getSlot(chuteSlot).mayPlace(new ItemStack(Items.DIRT))
                && menu.getSlot(chuteSlot).getMaxStackSize() == 1, "reserve accepts only one parachute");
        player.getInventory().setItem(9, new ItemStack(ModItems.PARACHUTE.get()));
        player.getInventory().setItem(10, new ItemStack(ModItems.PARACHUTE.get()));
        menu.quickMoveStack(player, playerSlots);
        menu.quickMoveStack(player, playerSlots + 1);
        check(helper, plane.getParachuteInventory().getItem(0).getCount() == 1
                && plane.getParachuteInventory().getItem(1).getCount() == 1, "shift-click fills both reserves");
        check(helper, player.getInventory().getItem(9).isEmpty() && player.getInventory().getItem(10).isEmpty(),
                "items leave player inventory");
        menu.quickMoveStack(player, chuteSlot);
        check(helper, plane.getParachuteInventory().getItem(0).isEmpty()
                && plane.getParachuteInventory().getItem(1).getCount() == 1, "shift-click returns only selected reserve");
        int total = player.getInventory().countItem(ModItems.PARACHUTE.get())
                + plane.getParachuteInventory().countItem(ModItems.PARACHUTE.get());
        check(helper, total == 2, "menu transfers conserve items");
        menu.removed(player);
        clean(helper, plane);
        helper.succeed();
    }

    private static CompoundTag save(GameTestHelper helper, Entity entity) {
        var output = TagValueOutput.createWithContext(ProblemReporter.DISCARDING, helper.getLevel().registryAccess());
        check(helper, entity.save(output), "entity saved");
        return output.buildResult();
    }

    private static Entity load(GameTestHelper helper, CompoundTag tag) {
        return EntityType.loadEntityRecursive(TagValueInput.create(ProblemReporter.DISCARDING,
                helper.getLevel().registryAccess(), tag), helper.getLevel(), EntitySpawnReason.LOAD, e -> e);
    }

    private static void inventoryPersistence(GameTestHelper helper) {
        PlaneEntity plane = plane(helper, "mv-22");
        plane.getInventory().setItem(AircraftEntity.FIXED_SLOTS, new ItemStack(Items.DIAMOND, 7));
        plane.getParachuteInventory().setItem(1, new ItemStack(ModItems.PARACHUTE.get()));
        CompoundTag tag = save(helper, plane);
        PlaneEntity loaded = (PlaneEntity) load(helper, tag);
        check(helper, loaded.getInventory().getItem(AircraftEntity.FIXED_SLOTS).getCount() == 7
                && loaded.getInventory().getItem(AircraftEntity.FIXED_SLOTS).is(Items.DIAMOND), "trunk indices preserved");
        check(helper, loaded.getParachuteInventory().getItem(0).isEmpty()
                && loaded.getParachuteInventory().getItem(1).is(ModItems.PARACHUTE.get()), "reserves save independently");
        loaded.removeVehicle(false);
        tag.remove("ParachutesAC");
        PlaneEntity oldSave = (PlaneEntity) load(helper, tag);
        check(helper, oldSave.getInventory().getItem(AircraftEntity.FIXED_SLOTS).getCount() == 7
                && !oldSave.haveParachute(), "older saves keep trunk and start with empty reserves");
        oldSave.removeVehicle(false);
        ParachuteEntity chute = new ParachuteEntity(ModEntityTypes.PARACHUTE.get(), helper.getLevel());
        chute.setModelType(2);
        chute.setPos(plane.position());
        chute.setDeltaMovement(0.1, 1.5, 0.2);
        Entity copy = load(helper, save(helper, chute));
        check(helper, copy instanceof ParachuteEntity saved && saved.getModelType() == 2
                && saved.getDeltaMovement().equals(chute.getDeltaMovement()), "chute model and motion persist");
        copy.discard(); chute.discard();
        clean(helper, plane);
        helper.succeed();
    }

    private static void waterRescue(GameTestHelper helper) {
        for (int x = 9; x <= 15; x++) for (int z = 9; z <= 15; z++) for (int y = 1; y <= 3; y++) {
            helper.setBlock(new BlockPos(x, y, z), Blocks.WATER);
        }
        Player rider = player(helper, GameType.SURVIVAL);
        ParachuteEntity chute = ParachuteEntity.deploy(helper.getLevel(), rider, 2,
                helper.absoluteVec(new Vec3(12, 4, 12)), new Vec3(0, -0.2, 0));
        helper.runAfterDelay(2, () -> {
            check(helper, chute.isRemoved() && rider.getVehicle() instanceof Boat, "ejection seat makes rescue boat in deep water");
            rider.getVehicle().discard();
            helper.succeed();
        });
    }

    private static void smallPool(GameTestHelper helper) {
        helper.setBlock(new BlockPos(12, 1, 12), Blocks.WATER);
        Player rider = player(helper, GameType.SURVIVAL);
        ParachuteEntity chute = ParachuteEntity.deploy(helper.getLevel(), rider, 2,
                helper.absoluteVec(new Vec3(12, 2, 12)), Vec3.ZERO);
        helper.runAfterDelay(2, () -> {
            check(helper, chute.isRemoved() && rider.getVehicle() == null, "small pool releases rider without generating boat");
            check(helper, helper.getEntities(EntityTypes.OAK_BOAT).isEmpty(), "water-volume threshold enforced");
            helper.succeed();
        });
    }

    private static void transportSeat(GameTestHelper helper) {
        PlaneEntity plane = plane(helper, "mv-22");
        Player pilot = pilot(helper, plane);
        Player passenger = player(helper, GameType.CREATIVE);
        helper.runAfterDelay(3, () -> {
            check(helper, passenger.startRiding(plane.getSeats()[1], true, true), "passenger boards seat 2");
            check(helper, !plane.canParachute(pilot) && !plane.canEjectSeat(passenger), "pilot/transport seat restrictions");
            if (plane.partHatch != null) {
                plane.partHatch.rotation = 0;
                check(helper, !plane.canParachute(passenger), "closed hatch refuses parachuting");
                plane.partHatch.rotation = 90;
            }
            check(helper, plane.canParachute(passenger) && plane.parachuteCrew(passenger), "open transport seat can bail out");
            check(helper, passenger.getVehicle() instanceof ParachuteEntity chute && chute.getModelType() == 3,
                    "transport chute used without ejection reserve");
            check(helper, pilot.getVehicle() == plane && !plane.haveParachute(), "pilot and reserve left alone");
            clean(helper, plane);
            helper.succeed();
        });
    }
}
