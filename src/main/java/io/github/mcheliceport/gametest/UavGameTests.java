package io.github.mcheliceport.gametest;

import static io.github.mcheliceport.gametest.GameTestSupport.check;

import io.github.mcheliceport.config.ServerConfig;
import io.github.mcheliceport.content.ContentType;
import io.github.mcheliceport.entity.aircraft.AircraftEntity;
import io.github.mcheliceport.entity.heli.HeliEntity;
import io.github.mcheliceport.entity.uav.UavStationEntity;
import io.github.mcheliceport.menu.UavStationMenu;
import io.github.mcheliceport.net.*;
import io.github.mcheliceport.registry.ModEntityTypes;
import io.github.mcheliceport.registry.ModItems;
import java.util.function.Consumer;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.util.ProblemReporter;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.storage.TagValueInput;
import net.minecraft.world.level.storage.TagValueOutput;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.entity.EntityJoinLevelEvent;
import net.neoforged.neoforge.event.entity.EntityMountEvent;

final class UavGameTests {
    private UavGameTests() { }
    static void register() {
        ModGameTests.test("uav_stock_deployment_and_small_controller_limits", 40, UavGameTests::deployment);
        ModGameTests.test("uav_menu_ownership_offsets_and_inventory", 40, UavGameTests::menu);
        ModGameTests.test("uav_cancelled_and_obstructed_deployment_keeps_item", 40, UavGameTests::cancelled);
        ModGameTests.test("uav_remote_control_identity_attitude_and_motion", 40, UavGameTests::authority);
        ModGameTests.test("uav_range_death_and_station_loss_cleanup", 40, UavGameTests::lifecycle);
        ModGameTests.test("uav_saved_identity_inventory_and_session_reset", 40, UavGameTests::persistence);
        ModGameTests.test("uav_exit_cancellation_and_control_release", 40, UavGameTests::exit);
        ModGameTests.test("uav_real_server_flight_with_station_operator", 120, UavGameTests::flight);
        ModGameTests.test("uav_every_stock_unmanned_airframe_deploys", 40, UavGameTests::allStock);
    }
    static UavStationEntity station(GameTestHelper h, boolean small) {
        var s = new UavStationEntity(ModEntityTypes.UAV_STATION.get(), h.getLevel());
        s.setPos(h.absoluteVec(new Vec3(8, 2, 8))); s.setSmall(small); h.getLevel().addFreshEntity(s); return s;
    }
    static Player operator(GameTestHelper h, UavStationEntity s) {
        var p = GameTestSupport.player(h, GameType.SURVIVAL, new Vec3(10, 2, 10));
        check(h, p.startRiding(s, true, true), "operator mounts console"); s.positionRider(p); return p;
    }
    static ItemStack item(String name) {
        return new ItemStack(ModItems.contentItems().stream().filter(i -> i.contentName().equals(name))
                .findFirst().orElseThrow().item().get());
    }
    static AircraftEntity deploy(GameTestHelper h, UavStationEntity s, Player p, String name) {
        s.inventory().setItem(0, item(name)); check(h, s.deploy(p), "deploy " + name);
        var ac = s.lastAircraft(); check(h, ac != null, "saved last drone resolves immediately"); return ac;
    }
    static void clean(Entity... all) { for (var e : all) { e.discard(); } }
    static void deployment(GameTestHelper h) {
        var small = station(h, true); var p = operator(h, small);
        small.inventory().setItem(0, item("mq-8b")); check(h, !small.deploy(p), "portable refuses large UAV");
        check(h, small.inventory().getItem(0).getCount() == 1, "refusal retains item");
        var ac = deploy(h, small, p, "rc-goblin");
        check(h, small.inventory().isEmpty() && ac.getFuel() == 0 && ac.getPilot() == null, "deployment consumes once, awaits fuel and connection");
        check(h, ac.position().distanceTo(small.position().add(12, 2, 12)) < .001, "default offset preserved at negative world Y");
        check(h, !ac.interact(p, InteractionHand.MAIN_HAND, Vec3.ZERO).consumesAction(), "UAV cannot be boarded by right click");
        clean(ac, p, small);
        var full = station(h, false); var second = operator(h, full);
        var large = deploy(h, full, second, "mq-8b"); check(h, full.connect(second), "large station supports large UAV");
        clean(large, second, full);
        var targetStation = station(h, false); var third = operator(h, targetStation);
        targetStation.inventory().setItem(0, item("bqm_74e")); check(h, targetStation.deploy(third), "target drone deploys");
        var target = h.getLevel().getEntitiesOfClass(AircraftEntity.class, targetStation.getBoundingBox().inflate(30)).stream()
                .filter(a -> a.getAcInfo() != null && a.getAcInfo().isTargetDrone
                        && a.position().distanceTo(targetStation.position().add(12, 2, 12)) < .01).findFirst().orElseThrow();
        check(h, target.getFuel() == target.getMaxFuel() && targetStation.lastAircraft() == null, "target drone receives full fuel and no remote binding");
        clean(target, third, targetStation); h.succeed();
    }
    private static void menu(GameTestHelper h) {
        var s = station(h, true); var p = operator(h, s);
        var outsider = GameTestSupport.player(h, GameType.SURVIVAL, new Vec3(10, 2, 10));
        var menu = new UavStationMenu(19, p.getInventory(), s); p.containerMenu = menu;
        check(h, !menu.stillValid(outsider) && !menu.clickMenuButton(outsider, 4), "outsider menu/offset denied");
        check(h, !menu.clickMenuButton(p, 100), "unknown button rejected");
        for (int i = 0; i < 200; i++) { menu.clickMenuButton(p, 4); menu.clickMenuButton(p, 5); }
        check(h, s.offset(0) == 50 && s.offset(1) == -50, "offsets clamp on server");
        check(h, !menu.getSlot(0).mayPlace(item("ah-64")) && !menu.getSlot(0).mayPlace(item("mq-9"))
                && menu.getSlot(0).mayPlace(item("skylark")), "slot filters unsupported and oversized UAVs");
        s.inventory().setItem(0, item("rc-goblin"));
        check(h, !menu.quickMoveStack(p, 0).isEmpty() && s.inventory().isEmpty(), "shift click returns deployment item");
        p.containerMenu = p.inventoryMenu;
        check(h, !menu.clickMenuButton(p, 4), "stale menu rejected even while seated");
        ModNetwork.applyUavStationControl(p, new UavStationControlPayload(s.getId() + 99, true));
        ModNetwork.applyUavStationControl(outsider, new UavStationControlPayload(s.getId(), true));
        check(h, p.getVehicle() == s, "wrong station and outsider exit packets denied");
        clean(p, outsider, s); h.succeed();
    }
    private static void cancelled(GameTestHelper h) {
        var s = station(h, true); var p = operator(h, s); s.inventory().setItem(0, item("rc-goblin"));
        Consumer<EntityJoinLevelEvent> cancel = event -> { if (event.getEntity() instanceof AircraftEntity) { event.setCanceled(true); } };
        NeoForge.EVENT_BUS.addListener(EntityJoinLevelEvent.class, cancel);
        try { check(h, !s.deploy(p) && s.inventory().getItem(0).getCount() == 1, "cancelled spawn retains item"); }
        finally { NeoForge.EVENT_BUS.unregister(cancel); }
        var pos = net.minecraft.core.BlockPos.containing(s.position().add(12, 2, 12));
        h.getLevel().setBlockAndUpdate(pos, Blocks.STONE.defaultBlockState());
        check(h, !s.deploy(p) && s.inventory().getItem(0).getCount() == 1, "solid obstruction retains item");
        h.getLevel().setBlockAndUpdate(pos, Blocks.AIR.defaultBlockState());
        clean(p, s); h.succeed();
    }
    private static void authority(GameTestHelper h) {
        var s = station(h, true); var p = operator(h, s); var ac = (HeliEntity) deploy(h, s, p, "rc-goblin");
        var outsider = GameTestSupport.player(h, GameType.SURVIVAL, new Vec3(10, 2, 10));
        check(h, !s.connect(outsider) && s.connect(p), "only mounted operator connects");
        check(h, p.getVehicle() == s && ac.getPilot() == p && ac.getControllingPassenger() == null
                && ModNetwork.riddenAircraft(p) == ac && ac.stillValid(p), "remote pilot identity stays physically at station");
        var controls = HeliControlPayload.switchesOnly(ac.getId(), PlayerControlSwitches.NONE.with(PlayerControlSwitches.THROTTLE_UP, true));
        ModNetwork.applyHeliControl(ac, outsider, controls); check(h, !ac.throttleUp, "outsider flight controls refused");
        ModNetwork.applyHeliControl(ac, p, controls); check(h, ac.throttleUp, "operator flight controls accepted");
        float yaw = ac.getYaw();
        ModNetwork.applyPilotAttitude(ac, outsider, new AircraftAttitudePayload(ac.getId(), 44, 0, 0, false));
        ModNetwork.applyPilotAttitude(ac, p, new AircraftAttitudePayload(ac.getId(), Float.NaN, 0, 0, false));
        check(h, ac.getYaw() == yaw, "outsider and nonfinite attitude refused");
        ModNetwork.applyPilotAttitude(ac, p, new AircraftAttitudePayload(ac.getId(), 44, 0, 0, false));
        check(h, ac.getYaw() == 44, "operator can steer remote attitude");
        GameTestSupport.withConfig(ServerConfig.SERVER_AUTHORITATIVE_MOVEMENT, false, () -> {
            check(h, ac.simulates() && !ac.followsPilot(), "remote movement stays on server in default mode");
            ac.setDeltaMovement(Vec3.ZERO); ModNetwork.applyPilotMotion(ac, new AircraftMotionPayload(ac.getId(), 99, 99, 99, 1));
            check(h, ac.getDeltaMovement().equals(Vec3.ZERO), "remote cannot spoof passenger motion");
        });
        GameTestSupport.withConfig(ServerConfig.SERVER_AUTHORITATIVE_MOVEMENT, true,
                () -> check(h, ac.simulates() && !ac.followsPilot(), "remote also works with server movement setting"));
        clean(ac, p, outsider, s); h.succeed();
    }
    private static void lifecycle(GameTestHelper h) {
        var s = station(h, true); var p = operator(h, s); var ac = deploy(h, s, p, "rc-goblin"); s.connect(p);
        ac.setCurrentThrottle(1); ac.switchHoveringMode(true); ac.setPos(s.position().add(s.range() + 1, 0, 0));
        check(h, ModNetwork.riddenAircraft(p) == null, "out-of-range packets refused before station tick"); s.tick();
        check(h, s.controlled() == null && ac.getPilot() == null && ac.getCurrentThrottle() == 0 && !ac.isHoveringMode(), "range loss clears controls, throttle and hover");
        ac.setPos(s.position().add(12, 2, 12)); s.positionRider(p); check(h, s.connect(p), "in-range saved UAV reconnects");
        p.stopRiding(); s.tick(); check(h, ac.uavStation() == null && s.controlled() == null, "dismount clears remote link");
        p.startRiding(s, true, true); s.positionRider(p); s.connect(p); ac.destroyAircraft(); s.tick();
        check(h, s.controlled() == null && ac.uavStation() == null, "destroyed UAV releases the station");
        clean(ac, p, s);
        var other = station(h, true); var operator = operator(h, other); var drone = deploy(h, other, operator, "rc-goblin"); other.connect(operator);
        other.discard(); check(h, drone.getPilot() == null && drone.uavStation() == null, "station loss releases aircraft");
        clean(drone, operator);
        var deathStation = station(h, true); var dead = operator(h, deathStation);
        var abandoned = deploy(h, deathStation, dead, "rc-goblin"); deathStation.connect(dead);
        dead.setHealth(0); deathStation.tick();
        check(h, abandoned.getPilot() == null && abandoned.uavStation() == null, "dead operator loses control");
        clean(abandoned, dead, deathStation); h.succeed();
    }
    static CompoundTag save(GameTestHelper h, Entity entity) {
        var output = TagValueOutput.createWithContext(ProblemReporter.DISCARDING, h.getLevel().registryAccess()); entity.saveWithoutId(output); return output.buildResult();
    }
    private static void persistence(GameTestHelper h) {
        var s = station(h, true); var p = operator(h, s); var ac = deploy(h, s, p, "rc-goblin"); s.connect(p);
        s.inventory().setItem(0, item("skylark")); s.adjust(0, 3); var tag = save(h, s); p.stopRiding();
        s.remove(Entity.RemovalReason.UNLOADED_TO_CHUNK);
        var copy = new UavStationEntity(ModEntityTypes.UAV_STATION.get(), h.getLevel());
        copy.load(TagValueInput.create(ProblemReporter.DISCARDING, h.getLevel().registryAccess(), tag));
        h.getLevel().addFreshEntity(copy); copy.tick();
        check(h, copy.small() && copy.offset(0) == 15 && copy.inventory().getItem(0).is(item("skylark").getItem())
                && copy.lastAircraft() == ac && copy.controlled() == null && ac.getPilot() == null, "reload retains UUID/item/offset but not a control session");
        p.startRiding(copy, true, true); copy.positionRider(p); check(h, copy.connect(p), "saved UUID can reconnect");
        clean(ac, p, copy); h.succeed();
    }
    private static void exit(GameTestHelper h) {
        var s = station(h, true); var p = operator(h, s); var ac = deploy(h, s, p, "rc-goblin"); s.connect(p);
        Consumer<EntityMountEvent> cancel = e -> { if (e.getEntityMounting() == p && e.getEntityBeingMounted() == s && e.isDismounting()) { e.setCanceled(true); } };
        NeoForge.EVENT_BUS.addListener(EntityMountEvent.class, cancel);
        try { check(h, !s.exit(p) && ac.getPilot() == p && s.controlled() == ac, "cancelled dismount retains valid session"); }
        finally { NeoForge.EVENT_BUS.unregister(cancel); }
        ModNetwork.applyUavStationControl(p, new UavStationControlPayload(s.getId(), true));
        check(h, !p.isPassenger() && ac.getPilot() == null && s.controlled() == null, "Y exit releases both ends");
        clean(ac, p, s); h.succeed();
    }
    private static void flight(GameTestHelper h) {
        var s = station(h, true); var p = operator(h, s); var ac = (HeliEntity) deploy(h, s, p, "rc-goblin");
        ac.setFuel(ac.getMaxFuel()); check(h, s.connect(p), "flight operator connects: " + s.feedback()); ac.setCurrentThrottle(1);
        Vec3 operatorPosition = p.position(), origin = ac.position();
        ModNetwork.applyHeliControl(ac, p, HeliControlPayload.switchesOnly(ac.getId(), PlayerControlSwitches.NONE.with(PlayerControlSwitches.THROTTLE_UP, true)));
        h.runAfterDelay(60, () -> {
            check(h, p.getVehicle() == s && p.position().distanceTo(operatorPosition) < 2, "operator stays at console");
            check(h, ac.getY() > origin.y + 1 && ac.getPilot() == p && ac.distanceTo(s) < s.range(), "server simulates controlled UAV takeoff: delta="
                    + ac.position().subtract(origin) + " pilot=" + (ac.getPilot() == p) + " fuel=" + ac.getFuel() + " throttle=" + ac.getCurrentThrottle() + " range=" + s.range()
                    + " acRemoved=" + ac.isRemoved() + " updates=" + ac.getCountOnUpdate() + " stationRemoved=" + s.isRemoved() + " operatorAlive=" + p.isAlive()
                    + " seated=" + (p.getVehicle() == s) + " connected=" + (s.controlled() == ac));
            clean(ac, p, s); h.succeed();
        });
    }
    private static void allStock(GameTestHelper h) {
        int count = 0;
        for (var entry : ModItems.contentItems()) {
            if (!(entry.item().get() instanceof io.github.mcheliceport.item.AircraftItem item)
                    || item.getAircraftInfo() == null || !item.getAircraftInfo().isUAV) { continue; }
            var s = station(h, false); var p = operator(h, s); s.inventory().setItem(0, new ItemStack(item));
            boolean deployed = s.deploy(p);
            check(h, deployed, "stock station deploys " + entry.contentName() + ": " + s.feedback());
            var ac = item.getAircraftInfo().isTargetDrone ? h.getLevel().getEntitiesOfClass(AircraftEntity.class, s.getBoundingBox().inflate(24)).stream()
                    .filter(a -> a.getAircraftTypeName().equals(entry.contentName()) && !a.isRemoved()
                            && a.position().distanceTo(s.position().add(12, 2, 12)) < .01).findFirst().orElseThrow() : s.lastAircraft();
            check(h, s.inventory().isEmpty(), "one item consumed for " + entry.contentName());
            clean(ac, p, s); count++;
        }
        check(h, count == 9, "nine stock UAV definitions tested, actual=" + count); h.succeed();
    }
}
