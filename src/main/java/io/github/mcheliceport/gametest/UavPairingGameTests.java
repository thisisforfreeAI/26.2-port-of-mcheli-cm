package io.github.mcheliceport.gametest;

import static io.github.mcheliceport.gametest.GameTestSupport.check;
import static io.github.mcheliceport.gametest.UavGameTests.*;

import io.github.mcheliceport.config.ServerConfig;
import io.github.mcheliceport.entity.uav.UavStationEntity;
import io.github.mcheliceport.item.UavControllerItem;
import io.github.mcheliceport.menu.UavStationMenu;
import io.github.mcheliceport.net.UavRosterActionPayload;
import io.github.mcheliceport.registry.ModDataComponents;
import io.github.mcheliceport.registry.ModItems;
import java.util.UUID;
import java.util.function.Consumer;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.util.ProblemReporter;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.storage.TagValueInput;
import net.minecraft.world.level.storage.TagValueOutput;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.entity.EntityJoinLevelEvent;
import net.neoforged.neoforge.event.entity.EntityMountEvent;

final class UavPairingGameTests {
    private UavPairingGameTests() { }
    static void register() {
        ModGameTests.test("uav_pairing_device_transfer_and_single_controller", 60, UavPairingGameTests::transfer);
        ModGameTests.test("uav_pairing_limit_retains_deployment_item", 60, UavPairingGameTests::limit);
        ModGameTests.test("uav_roster_uuid_authority_and_stale_menus", 60, UavPairingGameTests::roster);
        ModGameTests.test("uav_pairings_save_both_ends_and_migrate_legacy", 60, UavPairingGameTests::persistence);
        ModGameTests.test("uav_tablet_holds_identity_and_cleans_up", 60, UavPairingGameTests::tablet);
        ModGameTests.test("uav_tablet_replaced_item_and_death_release", 60, UavPairingGameTests::tabletLoss);
        ModGameTests.test("uav_tablet_small_only_and_full_size_config", 60, UavPairingGameTests::tabletConfig);
        ModGameTests.test("uav_tablet_cancelled_spawn_and_mount", 60, UavPairingGameTests::cancelled);
        ModGameTests.test("uav_tablet_player_save_omits_transient_root_vehicle", 60, UavPairingGameTests::playerSave);
    }
    private static Player nearby(GameTestHelper h, UavStationEntity s) {
        var p = GameTestSupport.player(h, GameType.SURVIVAL, new Vec3(8, 2, 8)); p.setPos(s.position()); return p;
    }
    private static void transfer(GameTestHelper h) {
        var a = station(h, false); var p = operator(h, a); var ac = deploy(h, a, p, "rc-goblin");
        check(h, a.pairedUavs().contains(ac.getUUID()) && ac.pairedController().equals(a.getUUID()), "deployment pairs both ends");
        p.stopRiding(); var b = station(h, false); p.setPos(b.position());
        check(h, !b.pair(p, ac), "another controller cannot claim paired UAV");
        var tool = ModItems.UAV_PAIRING_DEVICE.get(); p.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(tool));
        p.setPos(ac.position()); p.setShiftKeyDown(true); tool.onAircraft(p, InteractionHand.MAIN_HAND, ac); p.setShiftKeyDown(false);
        p.setPos(b.position());
        check(h, ac.pairedController() == null && !a.pairedUavs().contains(ac.getUUID()), "physical release clears loaded owner and aircraft");
        tool.onAircraft(p, InteractionHand.MAIN_HAND, ac);
        check(h, p.getMainHandItem().get(ModDataComponents.UAV_BOUND.get()).equals(ac.getUUID()), "device retains selected UUID");
        tool.onStation(p, InteractionHand.MAIN_HAND, b);
        check(h, b.pairedUavs().contains(ac.getUUID()) && b.getUUID().equals(ac.pairedController())
                && !p.getMainHandItem().has(ModDataComponents.UAV_BOUND.get()), "pairing transfers and clears selector");
        p.startRiding(b, true, true); b.positionRider(p); check(h, b.connect(p), "new owner connects");
        p.setShiftKeyDown(true); tool.onAircraft(p, InteractionHand.MAIN_HAND, ac);
        check(h, b.getUUID().equals(ac.pairedController()) && b.controlled() == ac, "live remote session cannot be stolen by tool");
        clean(ac, p, a, b); h.succeed();
    }
    private static void limit(GameTestHelper h) {
        var s = station(h, false); var p = operator(h, s); var ac = deploy(h, s, p, "rc-goblin");
        GameTestSupport.withConfig(ServerConfig.MAX_PAIRED_UAVS, 1, () -> {
            s.inventory().setItem(0, item("skylark"));
            check(h, !s.deploy(p) && s.inventory().getItem(0).getCount() == 1, "full list retains deployment item");
            check(h, s.unpair(p, ac.getUUID(), true), "menu releases pairing capacity");
            ac.setPos(s.position().add(2, 2, 2));
            check(h, s.deploy(p), "deployment succeeds after unpairing");
        });
        clean(s.lastAircraft(), ac, p, s); h.succeed();
    }
    private static void roster(GameTestHelper h) {
        var s = station(h, false); var p = operator(h, s); var first = deploy(h, s, p, "rc-goblin");
        s.adjust(0, -6); var second = deploy(h, s, p, "skylark");
        var menu = new UavStationMenu(20, p.getInventory(), s); p.containerMenu = menu;
        var outsider = nearby(h, s);
        check(h, !menu.rosterAction(outsider, new UavRosterActionPayload(20, s.getId(), first.getUUID(), 1)), "outsider denied");
        check(h, !menu.rosterAction(p, new UavRosterActionPayload(19, s.getId(), first.getUUID(), 1)), "stale menu ID denied");
        check(h, !menu.rosterAction(p, new UavRosterActionPayload(20, s.getId() + 99, first.getUUID(), 1)), "wrong station denied");
        check(h, !menu.rosterAction(p, new UavRosterActionPayload(20, s.getId(), UUID.randomUUID(), 1)), "unpaired UUID denied");
        check(h, !menu.rosterAction(p, new UavRosterActionPayload(20, s.getId(), first.getUUID(), 99)), "unknown action denied");
        for (int i = 0; i < 5; i++) { menu.broadcastChanges(); }
        check(h, menu.rows().size() == 2, "roster exposes both UAVs with telemetry");
        check(h, menu.rosterAction(p, new UavRosterActionPayload(20, s.getId(), first.getUUID(), 0)) && menu.selected().equals(first.getUUID()), "select by stable UUID");
        check(h, menu.rosterAction(p, new UavRosterActionPayload(20, s.getId(), first.getUUID(), 1)) && s.controlled() == first, "connect selected rather than last deployment");
        check(h, menu.rosterAction(p, new UavRosterActionPayload(20, s.getId(), first.getUUID(), 2)) && s.controlled() == null && first.pairedController() == null, "unpair cleans active control");
        p.containerMenu = p.inventoryMenu;
        check(h, !menu.rosterAction(p, new UavRosterActionPayload(20, s.getId(), second.getUUID(), 2)), "closed menu cannot remove pairing");
        clean(first, second, outsider, p, s); h.succeed();
    }
    private static void persistence(GameTestHelper h) {
        var s = station(h, false); var p = operator(h, s); var ac = deploy(h, s, p, "rc-goblin");
        var stationTag = save(h, s); var aircraftTag = save(h, ac); p.stopRiding();
        s.remove(net.minecraft.world.entity.Entity.RemovalReason.UNLOADED_TO_CHUNK);
        var copy = new UavStationEntity(io.github.mcheliceport.registry.ModEntityTypes.UAV_STATION.get(), h.getLevel());
        copy.load(TagValueInput.create(ProblemReporter.DISCARDING, h.getLevel().registryAccess(), stationTag)); h.getLevel().addFreshEntity(copy);
        ac.setPairedController(null); ac.load(TagValueInput.create(ProblemReporter.DISCARDING, h.getLevel().registryAccess(), aircraftTag));
        check(h, copy.pairedUavs().equals(java.util.List.of(ac.getUUID())) && copy.getUUID().equals(ac.pairedController()), "both UUID endpoints persist");
        p.startRiding(copy, true, true); copy.positionRider(p); check(h, copy.connect(p), "saved pairing reconnects");
        copy.disconnect(); p.stopRiding();
        var legacyTag = save(h, copy); legacyTag.remove("PairedUavs"); ac.setPairedController(null);
        copy.load(TagValueInput.create(ProblemReporter.DISCARDING, h.getLevel().registryAccess(), legacyTag));
        p.startRiding(copy, true, true); copy.positionRider(p);
        copy.tick(); check(h, copy.pairedUavs().contains(ac.getUUID()), "pre-pairing last UAV appears in roster after loading");
        check(h, copy.connect(p), "migrated last UAV connects");
        clean(ac, p, copy); h.succeed();
    }
    private static UavStationEntity openTablet(GameTestHelper h, Player p) {
        ModItems.UAV_TABLET.get().use(h.getLevel(), p, InteractionHand.MAIN_HAND);
        check(h, p.getVehicle() instanceof UavStationEntity, "tablet creates seated session"); return (UavStationEntity) p.getVehicle();
    }
    private static void tablet(GameTestHelper h) {
        var s = station(h, false); var p = operator(h, s); var ac = deploy(h, s, p, "rc-goblin"); p.stopRiding(); s.unpair(p, ac.getUUID(), false);
        var stack = new ItemStack(ModItems.UAV_TABLET.get()); p.setItemInHand(InteractionHand.MAIN_HAND, stack);
        ModItems.UAV_TABLET.get().onAircraft(p, InteractionHand.MAIN_HAND, ac);
        var data = UavControllerItem.data(stack); check(h, data.uavs().contains(ac.getUUID()) && data.id().equals(ac.pairedController()), "tablet owns binding");
        var t = openTablet(h, p);
        check(h, t.handheld() && !t.isPickable() && !t.shouldBeSaved() && !t.saveAsPassenger(TagValueOutput.createWithoutContext(ProblemReporter.DISCARDING)), "temporary seat is invisible, unpickable and unsaved");
        check(h, !t.accepts(item("rc-goblin")) && !t.deploy(p), "tablet has no transient deployment inventory");
        check(h, t.connect(p) && ac.getPilot() == p && p.getVehicle() == t, "tablet controls bound UAV");
        check(h, t.unpair(p, ac.getUUID(), true) && UavControllerItem.data(stack).uavs().isEmpty() && ac.pairedController() == null, "tablet roster removal writes immediately to held item");
        t.exit(p); t.tick(); check(h, t.isRemoved() && s.isAlive() && stack.getCount() == 1, "exit removes helper, retains item and placed station");
        clean(ac, p, s); h.succeed();
    }
    private static void tabletLoss(GameTestHelper h) {
        var s = station(h, false); var p = operator(h, s); var ac = deploy(h, s, p, "rc-goblin"); p.stopRiding(); s.unpair(p, ac.getUUID(), false);
        var stack = new ItemStack(ModItems.UAV_TABLET.get()); p.setItemInHand(InteractionHand.MAIN_HAND, stack);
        ModItems.UAV_TABLET.get().onAircraft(p, InteractionHand.MAIN_HAND, ac); var t = openTablet(h, p); t.connect(p); ac.throttleUp = true;
        p.setItemInHand(InteractionHand.MAIN_HAND, stack.copy()); t.tick();
        check(h, t.isRemoved() && ac.uavStation() == null && !ac.throttleUp && ac.getCurrentThrottle() == 0, "replacing even an identical tablet ends active session");
        var next = openTablet(h, p); check(h, next.connect(p), "pairings remain on copied tablet after helper loss"); p.setHealth(0); next.tick();
        check(h, next.isRemoved() && ac.uavStation() == null, "operator death removes tablet helper and control");
        clean(ac, p, s); h.succeed();
    }
    private static void tabletConfig(GameTestHelper h) {
        var s = station(h, false); var p = operator(h, s); var ac = deploy(h, s, p, "mq-8b"); p.stopRiding(); s.unpair(p, ac.getUUID(), false);
        var stack = new ItemStack(ModItems.UAV_TABLET.get()); p.setItemInHand(InteractionHand.MAIN_HAND, stack);
        GameTestSupport.withConfig(ServerConfig.TABLET_NORMAL_UAVS, false, () -> {
            ModItems.UAV_TABLET.get().onAircraft(p, InteractionHand.MAIN_HAND, ac); check(h, UavControllerItem.data(stack).uavs().isEmpty(), "small-only default rejects full-size UAV");
        });
        GameTestSupport.withConfig(ServerConfig.TABLET_NORMAL_UAVS, true, () -> {
            ModItems.UAV_TABLET.get().onAircraft(p, InteractionHand.MAIN_HAND, ac); var t = openTablet(h, p);
            check(h, !t.small() && t.connect(p), "server setting permits full-size binding and control"); t.exit(p); t.tick();
        });
        clean(ac, p, s); h.succeed();
    }
    private static void cancelled(GameTestHelper h) {
        var s = station(h, false); var p = nearby(h, s); p.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(ModItems.UAV_TABLET.get()));
        Consumer<EntityJoinLevelEvent> spawn = e -> { if (e.getEntity() instanceof UavStationEntity t && t.handheld()) { e.setCanceled(true); } };
        NeoForge.EVENT_BUS.addListener(EntityJoinLevelEvent.class, spawn);
        try { ModItems.UAV_TABLET.get().use(h.getLevel(), p, InteractionHand.MAIN_HAND); check(h, !p.isPassenger(), "cancelled spawn does not mount"); }
        finally { NeoForge.EVENT_BUS.unregister(spawn); }
        var created = new java.util.ArrayList<UavStationEntity>();
        Consumer<EntityMountEvent> mount = e -> { if (e.getEntityBeingMounted() instanceof UavStationEntity t && t.handheld() && e.isMounting()) { created.add(t); e.setCanceled(true); } };
        NeoForge.EVENT_BUS.addListener(EntityMountEvent.class, mount);
        try { ModItems.UAV_TABLET.get().use(h.getLevel(), p, InteractionHand.MAIN_HAND); check(h, !p.isPassenger() && created.size() == 1 && created.getFirst().isRemoved(), "cancelled mount removes temporary station"); }
        finally { NeoForge.EVENT_BUS.unregister(mount); }
        check(h, p.getMainHandItem().getCount() == 1, "failed tablet use never consumes item"); clean(p, s); h.succeed();
    }
    private static void playerSave(GameTestHelper h) {
        var p = h.makeMockServerPlayerInLevel(); p.setGameMode(GameType.SURVIVAL);
        var placed = station(h, false); p.setPos(placed.position()); p.startRiding(placed, true, true);
        check(h, save(h, p).contains("RootVehicle"), "ordinary station remains in vanilla player vehicle saves");
        p.stopRiding();
        var stack = new ItemStack(ModItems.UAV_TABLET.get()); p.setItemInHand(InteractionHand.MAIN_HAND, stack);
        var tablet = new UavStationEntity(io.github.mcheliceport.registry.ModEntityTypes.UAV_STATION.get(), h.getLevel());
        tablet.initTablet(p, InteractionHand.MAIN_HAND, stack, UavControllerItem.data(stack)); h.getLevel().addFreshEntity(tablet);
        p.startRiding(tablet, true, true);
        check(h, tablet.operator() == p && !save(h, p).contains("RootVehicle"), "real player serializer omits temporary root instead of writing an invalid empty entity");
        clean(tablet, placed, p); h.succeed();
    }
}
