package io.github.mcheliceport.gametest;

import static io.github.mcheliceport.gametest.GameTestSupport.check;

import io.github.mcheliceport.config.CommonConfig;
import io.github.mcheliceport.config.ServerConfig;
import io.github.mcheliceport.entity.aircraft.AircraftEntity;
import io.github.mcheliceport.entity.chain.ChainEntity;
import io.github.mcheliceport.entity.container.CargoContainerEntity;
import io.github.mcheliceport.entity.tank.TankEntity;
import io.github.mcheliceport.item.ChainItem;
import io.github.mcheliceport.net.AircraftMotionPayload;
import io.github.mcheliceport.net.ModNetwork;
import io.github.mcheliceport.registry.ModEntityTypes;
import io.github.mcheliceport.registry.ModItems;
import java.util.function.Consumer;
import net.minecraft.core.BlockPos;
import net.minecraft.core.component.DataComponents;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.util.ProblemReporter;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.CustomData;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.storage.TagValueInput;
import net.minecraft.world.level.storage.TagValueOutput;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.entity.EntityJoinLevelEvent;
import net.neoforged.neoforge.event.entity.player.PlayerInteractEvent;

/** Real chain item/event, motion constraints, authority, lifecycle and UUID reconnection. */
final class ChainGameTests {
    private ChainGameTests() { }
    static void register() {
        ModGameTests.test("chains_item_two_click_and_disconnect", 30, ChainGameTests::interaction);
        ModGameTests.test("chains_target_and_range_refusals", 30, ChainGameTests::refusals);
        ModGameTests.test("chains_cancelled_spawn", 30, ChainGameTests::cancelled);
        ModGameTests.test("chains_slack_pull_and_hard_limit", 30, ChainGameTests::motion);
        ModGameTests.test("chains_blocked_pull_disconnects", 30, ChainGameTests::blocked);
        ModGameTests.test("chains_lifecycle_and_orphan_timeout", 80, ChainGameTests::lifecycle);
        ModGameTests.test("chains_uuid_reload_preserves_inventory", 80, ChainGameTests::persistence);
        ModGameTests.test("chains_occupied_trailer_authority_and_collision", 30, ChainGameTests::authority);
        ModGameTests.test("chains_rack_exclusion", 30, ChainGameTests::racks);
    }
    private static CargoContainerEntity crate(GameTestHelper h, Vec3 relative) {
        var crate = new CargoContainerEntity(ModEntityTypes.CONTAINER.get(), h.getLevel());
        crate.setPos(h.absoluteVec(relative)); h.getLevel().addFreshEntity(crate); return crate;
    }
    private static TankEntity truck(GameTestHelper h, Vec3 relative) {
        var truck = new TankEntity(ModEntityTypes.TANK.get(), h.getLevel()) {
            @Override protected void onUpdateAircraft() { }
        };
        truck.setAircraftTypeName("growler"); truck.setPos(h.absoluteVec(relative));
        h.getLevel().addFreshEntity(truck); truck.tick(); return truck;
    }
    private static ChainEntity connect(GameTestHelper h, Entity first, Entity second, int length) {
        var chain = new ChainEntity(ModEntityTypes.CHAIN.get(), h.getLevel());
        chain.setPos(second.position()); chain.setLength(length); h.getLevel().addFreshEntity(chain);
        check(h, chain.connect(first, second), "valid distinct unlinked endpoints connect"); return chain;
    }
    private static void click(Player player, ItemStack stack, Entity target) {
        player.setPos(target.position().add(0, 0, 2)); ChainItem.interact(player, stack, target);
    }
    private static void cleanup(Entity... entities) {
        for (var entity : entities) {
            if (entity instanceof CargoContainerEntity crate) { crate.clearContent(); }
            entity.discard();
        }
    }
    private static void interaction(GameTestHelper h) {
        var first = crate(h, new Vec3(5, 2, 6)); var second = truck(h, new Vec3(13, 2, 6));
        var player = GameTestSupport.player(h, GameType.SURVIVAL, new Vec3(5, 2, 8));
        var item = new ItemStack(ModItems.CHAIN.get()); player.setItemInHand(InteractionHand.MAIN_HAND, item);
        var event = new PlayerInteractEvent.EntityInteract(player, InteractionHand.MAIN_HAND, first, Vec3.ZERO);
        NeoForge.EVENT_BUS.post(event);
        check(h, event.isCanceled() && ChainItem.selected(item, h.getLevel()) == first && !ChainEntity.linked(first),
                "first actual interaction selects without opening cargo or spawning a chain");
        var hull = new io.github.mcheliceport.entity.aircraft.AircraftHitBox(second);
        hull.setInfo(new io.github.mcheliceport.content.aircraft.BoundingBoxInfo(0, 1, 0, 2, 1, 1));
        hull.updatePosition(second.getX(), second.getY(), second.getZ(), 0, 0, 0);
        player.setPos(hull.getBoundingBox().getCenter().add(0, 0, 2));
        NeoForge.EVENT_BUS.post(new PlayerInteractEvent.EntityInteract(player, InteractionHand.MAIN_HAND, hull, Vec3.ZERO));
        var chain = ChainEntity.attached(first);
        check(h, chain != null && chain.towed() == first && chain.tow() == second && chain.length() == 8,
                "second interaction creates one chain with distinct trailer/tug and initial distance");
        check(h, ChainItem.selected(item, h.getLevel()) == null && item.getCount() == 1 && !player.isPassenger(),
                "successful creation clears selection, keeps reusable item and does not board target");
        click(player, item, second);
        check(h, chain.isRemoved() && !ChainEntity.linked(first) && !second.isTowed(), "clicking the tug disconnects both endpoints");
        cleanup(first, second); h.succeed();
    }
    private static void refusals(GameTestHelper h) {
        var first = crate(h, new Vec3(3, 2, 6)); var second = crate(h, new Vec3(4, 2, 6));
        var player = GameTestSupport.player(h, GameType.SURVIVAL, new Vec3(3, 2, 8)); var item = new ItemStack(ModItems.CHAIN.get());
        click(player, item, first); click(player, item, first); click(player, item, second);
        check(h, !ChainEntity.linked(first) && ChainItem.selected(item, h.getLevel()) == first, "same entity and distance under two retain first selection");
        second.setPos(h.absoluteVec(new Vec3(20, 2, 6))); click(player, item, second);
        check(h, !ChainEntity.linked(first), "distance over sixteen is refused");
        player.setPos(second.position().add(10, 0, 0)); ChainItem.interact(player, item, second);
        check(h, !ChainEntity.linked(second), "out-of-reach interaction cannot create a chain");
        var cow = EntityTypes.COW.create(h.getLevel(), EntitySpawnReason.COMMAND);
        cow.setPos(first.position()); h.getLevel().addFreshEntity(cow);
        var drop = new ItemEntity(h.getLevel(), first.getX(), first.getY(), first.getZ(), new ItemStack(Items.STICK));
        var chute = new io.github.mcheliceport.entity.parachute.ParachuteEntity(ModEntityTypes.PARACHUTE.get(), h.getLevel());
        check(h, !ChainItem.eligible(player) && !ChainItem.eligible(cow) && !ChainItem.eligible(drop) && !ChainItem.eligible(chute),
                "living entities, items and parachutes cannot be endpoints");
        var externallyControlled = new CargoContainerEntity(ModEntityTypes.CONTAINER.get(), h.getLevel()) {
            @Override public boolean isClientAuthoritative() { return true; }
        };
        check(h, ChainItem.eligible(externallyControlled) && !ChainItem.canBeTowed(externallyControlled),
                "generic client-driven vehicles can tug but cannot fight the chain as trailers");
        var gun = new io.github.mcheliceport.entity.vehicle.VehicleEntity(ModEntityTypes.VEHICLE.get(), h.getLevel());
        GameTestSupport.withConfig(CommonConfig.FIX_VEHICLE_AT_PLACED_POINT, true, () -> check(h, !ChainItem.eligible(gun), "anchored placed vehicles are refused"));
        GameTestSupport.withConfig(CommonConfig.FIX_VEHICLE_AT_PLACED_POINT, false, () -> check(h, ChainItem.eligible(gun), "unanchored placed vehicles remain eligible"));
        CustomData.update(DataComponents.CUSTOM_DATA, item, tag -> tag.putString("TowedEntityUUID", "not-a-uuid"));
        click(player, item, second);
        check(h, ChainItem.selected(item, h.getLevel()) == second, "malformed/stale selection starts a new first click");
        first.startRiding(second, true, true);
        check(h, !ChainItem.eligible(first), "passengers cannot be chained");
        cleanup(first, second, cow); h.succeed();
    }
    private static void cancelled(GameTestHelper h) {
        var first = truck(h, new Vec3(4, 2, 6)); var second = crate(h, new Vec3(12, 2, 6));
        var player = GameTestSupport.player(h, GameType.SURVIVAL, new Vec3(4, 2, 8)); var item = new ItemStack(ModItems.CHAIN.get());
        click(player, item, first);
        Consumer<EntityJoinLevelEvent> deny = event -> { if (event.getEntity() instanceof ChainEntity) { event.setCanceled(true); } };
        NeoForge.EVENT_BUS.addListener(EntityJoinLevelEvent.class, deny);
        try { click(player, item, second); } finally { NeoForge.EVENT_BUS.unregister(deny); }
        check(h, !ChainEntity.linked(first) && !first.isTowed() && ChainItem.selected(item, h.getLevel()) == first && item.getCount() == 1,
                "cancelled spawn preserves selection/item and leaves no authority change");
        click(player, item, second); var chain = ChainEntity.attached(first);
        check(h, chain != null && first.isTowed(), "retry after cancellation creates usable chain");
        click(player, item, first);
        check(h, chain.isRemoved() && !first.isTowed(), "clicking trailer also disconnects");
        cleanup(first, second); h.succeed();
    }
    private static void motion(GameTestHelper h) {
        var first = crate(h, new Vec3(8, 5, 6)); var second = crate(h, new Vec3(4, 5, 6)); var chain = connect(h, first, second, 4);
        Vec3 before = first.position(); first.setDeltaMovement(Vec3.ZERO); chain.tick();
        check(h, first.position().equals(before) && first.getDeltaMovement().equals(Vec3.ZERO), "slack chain does not move its trailer");
        first.setPos(second.position().add(5, 0, 0)); chain.tick();
        check(h, Math.abs(first.getYRot() - 90) < 1e-5 && Math.abs(first.getDeltaMovement().x + .005) < 1e-7,
                "stretched chain faces tug and applies upstream weak pull");
        first.setDeltaMovement(Vec3.ZERO); first.setPos(second.position().add(4.000001, 0, 0)); chain.tick();
        check(h, first.getDeltaMovement().length() < .1, "near-taut boundary cannot produce an explosive inverse-gap impulse");
        first.setPos(second.position().add(12, 0, 0)); chain.tick();
        check(h, Math.abs(first.distanceTo(second) - 6) < 1e-5 && !chain.isRemoved(), "clear movement enforces length plus two hard limit");
        first.setPos(second.position()); chain.tick();
        check(h, Double.isFinite(first.getDeltaMovement().x) && !chain.isRemoved(), "coincident endpoints do not divide by zero");
        chain.setLength(1); check(h, chain.length() == 3, "minimum length three");
        chain.setLength(100); check(h, chain.length() == 15, "maximum length fifteen");
        cleanup(chain, first, second); h.succeed();
    }
    private static void blocked(GameTestHelper h) {
        var first = crate(h, new Vec3(16, 3, 6)); var second = crate(h, new Vec3(4, 3, 6)); var chain = connect(h, first, second, 4);
        for (int y = 1; y < 7; y++) { for (int z = 3; z < 10; z++) { h.setBlock(new BlockPos(10, y, z), Blocks.STONE); } }
        chain.tick();
        check(h, chain.isRemoved() && first.getX() > h.absolutePos(new BlockPos(10, 3, 6)).getX() + 1,
                "solid obstruction disconnects rather than teleporting the trailer through a wall");
        cleanup(first, second); h.succeed();
    }
    private static void lifecycle(GameTestHelper h) {
        var first = truck(h, new Vec3(4, 3, 6)); var second = crate(h, new Vec3(12, 3, 6)); var third = crate(h, new Vec3(8, 3, 10));
        var chain = connect(h, first, second, 8);
        var duplicate = new ChainEntity(ModEntityTypes.CHAIN.get(), h.getLevel()); duplicate.setPos(first.position()); h.getLevel().addFreshEntity(duplicate);
        check(h, !duplicate.connect(third, first) && !duplicate.connect(first, third), "one active chain per endpoint prevents duplicate links/cycles");
        duplicate.discard(); second.discard(); chain.tick();
        check(h, chain.isRemoved() && !first.isTowed(), "removed tug disconnects and restores trailer authority");
        var next = connect(h, first, third, 4); first.discard(); next.tick();
        check(h, next.isRemoved() && !ChainEntity.linked(third), "removed trailer leaves no dangling tug link");
        var orphan = new ChainEntity(ModEntityTypes.CHAIN.get(), h.getLevel()); orphan.setPos(third.position()); h.getLevel().addFreshEntity(orphan);
        for (int i = 0; i < 49; i++) { orphan.tick(); }
        check(h, !orphan.isRemoved(), "unresolved saved endpoints have fifty-tick grace"); orphan.tick();
        check(h, orphan.isRemoved(), "orphan eventually removes itself");
        check(h, h.getLevel().getEntitiesOfClass(ItemEntity.class, third.getBoundingBox().inflate(8)).isEmpty(), "chain removal never duplicates an item");
        cleanup(third); h.succeed();
    }
    private static void persistence(GameTestHelper h) {
        var first = crate(h, new Vec3(4, 2, 6)); first.setItem(53, new ItemStack(Items.DIAMOND, 27));
        var second = truck(h, new Vec3(12, 2, 6)); var chain = connect(h, first, second, 8);
        var output = TagValueOutput.createWithContext(ProblemReporter.DISCARDING, h.getLevel().registryAccess());
        check(h, chain.save(output), "chain saves UUID endpoints and length");
        chain.remove(Entity.RemovalReason.UNLOADED_TO_CHUNK);
        h.runAfterDelay(2, () -> {
            var copy = (ChainEntity) EntityType.loadEntityRecursive(TagValueInput.create(ProblemReporter.DISCARDING,
                    h.getLevel().registryAccess(), output.buildResult()), h.getLevel(), EntitySpawnReason.LOAD, e -> e);
            h.getLevel().addFreshEntity(copy); copy.tick();
            check(h, copy.towed() == first && copy.tow() == second && copy.length() == 8,
                    "UUID search reconnects exact distinct endpoints after reload");
            check(h, first.getItem(53).getCount() == 27 && ChainEntity.attached(first) == copy, "reconnection preserves inventory and only one chain");
            cleanup(copy, first, second); h.succeed();
        });
    }
    private static void authority(GameTestHelper h) {
        var first = truck(h, new Vec3(6, 3, 6)); var second = truck(h, new Vec3(14, 3, 6));
        var driver = h.makeMockServerPlayerInLevel(); driver.setGameMode(GameType.SURVIVAL); driver.startRiding(first, true, true);
        var tugDriver = h.makeMockServerPlayerInLevel(); tugDriver.setGameMode(GameType.SURVIVAL); tugDriver.startRiding(second, true, true);
        GameTestSupport.withConfig(ServerConfig.SERVER_AUTHORITATIVE_MOVEMENT, false, () -> {
            check(h, first.followsPilot() && second.followsPilot(), "both occupied vehicles initially follow clients");
            var chain = connect(h, first, second, 8);
            check(h, first.simulates() && !first.followsPilot() && second.followsPilot() && first.getPilot() == driver,
                    "only trailer becomes server-authoritative; driver stays aboard");
            first.setDeltaMovement(.2, 0, 0);
            ModNetwork.applyPilotMotion(first, new AircraftMotionPayload(first.getId(), 10, 1, 10, 1));
            check(h, first.getDeltaMovement().equals(new Vec3(.2, 0, 0)), "trailer rejects stale client motion");
            second.setPos(first.position()); second.updateHitBoxPosition();
            var cow = EntityTypes.COW.create(h.getLevel(), EntitySpawnReason.COMMAND);
            cow.setPos(first.position().add(.8, 0, 0)); h.getLevel().addFreshEntity(cow); first.tick();
            check(h, second.getDamageTaken() == 0 && cow.getHealth() < cow.getMaxHealth(), "linked truck cannot ram tug while collision damage remains active");
            chain.discard();
            check(h, !first.isTowed() && first.followsPilot() && first.getPilot() == driver, "disconnect returns client authority with driver intact");
            first.setDeltaMovement(.2, 0, 0); first.tick();
            check(h, second.getDamageTaken() == 0, "partner collision grace survives immediate disconnection");
            cleanup(cow);
        });
        GameTestSupport.withConfig(ServerConfig.SERVER_AUTHORITATIVE_MOVEMENT, true, () -> {
            var chain = connect(h, first, second, 4);
            check(h, first.simulates() && second.simulates(), "server-authoritative setting remains server-simulated at both ends"); cleanup(chain);
        });
        cleanup(first, second, driver, tugDriver); h.succeed();
    }
    private static void racks(GameTestHelper h) {
        var carrier = GameTestSupport.spawn(h, "mh-53e", new Vec3(12, 1, 17), 0);
        carrier.tick();
        int rack = -1;
        for (int i = 0; i < carrier.getSeats().length; i++) {
            if (carrier.getSeatInfo(i + 1) instanceof io.github.mcheliceport.content.aircraft.SeatRackInfo) { rack = i; break; }
        }
        var first = truck(h, new Vec3(12, 1, 6)); var second = crate(h, new Vec3(18, 1, 6));
        var info = (io.github.mcheliceport.content.aircraft.SeatRackInfo) carrier.getSeatInfo(rack + 1);
        first.setPos(carrier.getTransformedPosition(info.getEntryPos())); var chain = connect(h, first, second, 6);
        check(h, carrier.racks().load() == 0 && !first.isPassenger(), "linked truck cannot enter a carrier rack");
        chain.discard();
        check(h, carrier.racks().load() == 1 && first.isPassenger() && !ChainItem.eligible(first), "disconnected truck boards normally and then cannot be chained");
        cleanup(first, second, carrier); h.succeed();
    }
}
