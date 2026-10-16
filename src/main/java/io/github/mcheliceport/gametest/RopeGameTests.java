package io.github.mcheliceport.gametest;

import static io.github.mcheliceport.gametest.GameTestSupport.check;

import io.github.mcheliceport.config.ServerConfig;
import io.github.mcheliceport.content.ContentLocation;
import io.github.mcheliceport.content.aircraft.AircraftInfo;
import io.github.mcheliceport.content.aircraft.HeliInfo;
import io.github.mcheliceport.content.aircraft.SeatInfo;
import io.github.mcheliceport.content.aircraft.SeatRackInfo;
import io.github.mcheliceport.entity.aircraft.AircraftEntity;
import io.github.mcheliceport.entity.heli.HeliEntity;
import io.github.mcheliceport.entity.rappel.RappellingEntity;
import io.github.mcheliceport.net.AircraftMotionPayload;
import io.github.mcheliceport.net.HeliControlPayload;
import io.github.mcheliceport.net.ModNetwork;
import io.github.mcheliceport.net.PlayerControlSwitches;
import io.github.mcheliceport.registry.ModEntityTypes;
import java.util.function.Consumer;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.util.ProblemReporter;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.storage.TagValueInput;
import net.minecraft.world.level.storage.TagValueOutput;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.entity.EntityJoinLevelEvent;
import net.neoforged.neoforge.event.entity.EntityMountEvent;

/** Deployment gates, crew authority, actual descent, cancellation and saved parent/rider recovery. */
final class RopeGameTests {
    private RopeGameTests() { }
    static void register() {
        ModGameTests.test("ropes_deployment_gates_timing_and_length", 30, RopeGameTests::deployment);
        ModGameTests.test("ropes_pilot_and_crew_packet_authority", 30, RopeGameTests::authority);
        ModGameTests.test("ropes_scheduled_mob_crew_and_exclusions", 30, RopeGameTests::mobs);
        ModGameTests.test("ropes_player_descent_and_landing", 220, RopeGameTests::landing);
        ModGameTests.test("ropes_spawn_mount_and_blocked_exit_rollback", 30, RopeGameTests::cancelled);
        ModGameTests.test("ropes_following_obstruction_and_interruption", 30, RopeGameTests::interruption);
        ModGameTests.test("ropes_saved_parent_and_passenger_recovery", 100, RopeGameTests::persistence);
        ModGameTests.test("ropes_saved_empty_player_reattachment_grace", 100, RopeGameTests::reattachment);
        ModGameTests.test("ropes_orphan_death_and_cancelled_release", 80, RopeGameTests::lifecycle);
    }
    private static HeliEntity fixture(GameTestHelper h) {
        var info = new HeliInfo(ContentLocation.builtin("rope_fixture"), "rope_fixture.yml", "yaml");
        info.maxHp = 1000; info.maxFuel = 1000;
        for (int i = 0; i < 4; i++) { info.seatList.add(new SeatInfo(new Vec3(0, 1, -i), false)); }
        info.seatList.add(new SeatRackInfo(new String[] {"container"}, 0, 0, -6, new Vec3(0, 0, -6),
                new AircraftInfo.CameraPosition(Vec3.ZERO, false, 0, 0), 3, 20, 0, 0, false));
        info.repellingHooks.add(new AircraftInfo.RepellingHook(new Vec3(3, 2, 0), 3));
        info.repellingHooks.add(new AircraftInfo.RepellingHook(new Vec3(-3, 2, 0), 3));
        var definition = info;
        var ac = new HeliEntity(ModEntityTypes.HELICOPTER.get(), h.getLevel()) {
            @Override protected AircraftInfo lookupInfo(String name) { return definition; }
            @Override protected void onUpdateAircraft() { }
        };
        ac.setAircraftTypeName("rope_fixture"); ac.setPos(h.absoluteVec(new Vec3(12, 20, 12)));
        ac.setFuel(900); h.getLevel().addFreshEntity(ac); ac.tick(); return ac;
    }
    private static void arm(GameTestHelper h, HeliEntity ac) {
        ac.switchHoveringMode(true); check(h, ac.ropes().toggle(), "steady hover deploys ropes");
        for (int i = 0; i < 51; i++) { ac.ropes().tick(); }
        check(h, ac.ropes().ready(), "ropes ready after fifty-one ticks");
    }
    private static Player crew(GameTestHelper h, HeliEntity ac, int index) {
        var player = GameTestSupport.player(h, GameType.SURVIVAL, new Vec3(12, 20, 12));
        check(h, player.startRiding(ac.getSeats()[index], true, true), "crew boards requested seat"); return player;
    }
    private static LivingEntity cow(GameTestHelper h, Entity vehicle) {
        var cow = EntityTypes.COW.create(h.getLevel(), EntitySpawnReason.COMMAND);
        cow.setPos(vehicle.position()); h.getLevel().addFreshEntity(cow); cow.startRiding(vehicle, true, true); return cow;
    }
    private static HeliControlPayload action(HeliEntity ac, HeliControlPayload.UnmountAction action) {
        return new HeliControlPayload(ac.getId(), action, HeliControlPayload.ModeSwitch.NONE,
                HeliControlPayload.HatchSwitch.NONE, HeliControlPayload.GearSwitch.NONE, PlayerControlSwitches.NONE,
                (byte) 0, HeliControlPayload.BladeStatus.NONE, (byte) 0);
    }
    private static void cleanup(Entity... entities) { for (var entity : entities) { entity.discard(); } }
    private static void deployment(GameTestHelper h) {
        var ac = fixture(h);
        check(h, !ac.ropes().toggle(), "deployment requires hover"); ac.switchHoveringMode(true);
        ac.setAttitudeFromPilot(0, 3, 0); check(h, !ac.ropes().toggle(), "three degrees pitch is too much");
        ac.setAttitudeFromPilot(0, 0, -3); check(h, !ac.ropes().toggle(), "three degrees roll is too much");
        ac.setAttitudeFromPilot(0, 0, 0); Vec3 initial = ac.position(); ac.setPos(initial.add(1, 0, 0));
        check(h, !ac.ropes().toggle(), "recent displacement must be below .3 blocks"); ac.setPos(initial);
        ac.setPos(initial.add(0, -18, 0)); ac.tick(); ac.tick();
        check(h, !ac.ropes().toggle(), "ground within four blocks prevents rope deployment");
        ac.setPos(initial); for (int i = 0; i < 10; i++) { ac.tick(); }
        check(h, ac.ropes().toggle(), "steady level hover deploys");
        for (int i = 0; i < 50; i++) { ac.ropes().tick(); }
        check(h, !ac.ropes().ready() && ac.ropeLength() == 12.5F, "fifty tick warmup and quarter-block extension");
        ac.ropes().tick(); check(h, ac.ropes().ready(), "crew may descend after tick fifty");
        // Stay more than fifty blocks above GameTest's barrier enclosure as well as its floor.
        ac.setPos(initial.add(0, 100, 0));
        for (int i = 0; i < 250; i++) { ac.ropes().tick(); }
        check(h, ac.ropeLength() == 50, "maximum rope length fifty, actual=" + ac.ropeLength()); ac.setPos(initial); ac.ropes().tick();
        check(h, ac.ropeLength() == 20, "modern terrain probe works below world Y zero");
        check(h, ac.ropes().toggle() && !ac.ropes().active() && ac.ropeLength() == 0 && ac.ropeTicks() == 0, "retract clears deployment state");
        cleanup(ac); h.succeed();
    }
    private static void authority(GameTestHelper h) {
        var ac = fixture(h); var pilot = h.makeMockServerPlayerInLevel(); pilot.setGameMode(GameType.SURVIVAL); pilot.startRiding(ac, true, true);
        var crew = crew(h, ac, 0); var outsider = GameTestSupport.player(h, GameType.SURVIVAL, new Vec3(12, 1, 12));
        ac.switchHoveringMode(true);
        ModNetwork.applyHeliControl(ac, outsider, action(ac, HeliControlPayload.UnmountAction.UNMOUNT_CREW));
        ModNetwork.applyHeliControl(ac, crew, action(ac, HeliControlPayload.UnmountAction.UNMOUNT_CREW));
        check(h, !ac.ropes().active(), "outsider and crew cannot deploy ropes");
        GameTestSupport.withConfig(ServerConfig.SERVER_AUTHORITATIVE_MOVEMENT, false, () -> {
            check(h, ac.followsPilot(), "default mode initially follows pilot client");
            ModNetwork.applyHeliControl(ac, pilot, action(ac, HeliControlPayload.UnmountAction.UNMOUNT_CREW));
            check(h, ac.ropes().active() && ac.simulates() && !ac.followsPilot() && ac.isFreeLookMode(), "deployment gives server stable-hover authority and free look");
            ModNetwork.applyHeliControl(ac, crew, action(ac, HeliControlPayload.UnmountAction.UNMOUNT_SELF));
            check(h, crew.getVehicle() == ac.getSeats()[0], "Y during rope warmup keeps crew seated");
            ac.setDeltaMovement(Vec3.ZERO); ModNetwork.applyPilotMotion(ac, new AircraftMotionPayload(ac.getId(), 1, 1, 1, 1));
            check(h, ac.getDeltaMovement().equals(Vec3.ZERO), "stale pilot motion cannot move a deployed helicopter");
            ac.throttleUp = ac.moveLeft = true; ac.ropes().tick();
            check(h, !ac.throttleUp && !ac.moveLeft, "deployment suppresses flight controls");
            for (int i = 0; i < 50; i++) { ac.ropes().tick(); }
            ModNetwork.applyHeliControl(ac, crew, action(ac, HeliControlPayload.UnmountAction.UNMOUNT_SELF));
            check(h, crew.getVehicle() instanceof RappellingEntity, "ready crew uses Y to rappel");
            var helper = (RappellingEntity) crew.getVehicle();
            check(h, helper.hookIndex() == 1 && helper.parent() == ac && helper.position().distanceTo(ac.ropes().hook(1).add(0, -4, 0)) < 1e-5,
                    "manual first hook and upstream double two-block drop are preserved");
            ac.ropes().stop(); helper.tick();
            check(h, !crew.isPassenger() && ac.followsPilot() && ac.getPilot() == pilot, "retraction releases crew and returns pilot authority");
        });
        GameTestSupport.withConfig(ServerConfig.SERVER_AUTHORITATIVE_MOVEMENT, true, () -> {
            arm(h, ac); check(h, ac.simulates() && !ac.followsPilot(), "server movement mode remains server-driven with ropes"); ac.ropes().stop();
        });
        cleanup(ac, pilot); h.succeed();
    }
    private static void mobs(GameTestHelper h) {
        var ac = fixture(h); var gunnerSeat = cow(h, ac.getSeats()[0]);
        var a = cow(h, ac.getSeats()[1]); var b = cow(h, ac.getSeats()[2]);
        var crate = new io.github.mcheliceport.entity.container.CargoContainerEntity(ModEntityTypes.CONTAINER.get(), h.getLevel());
        crate.setPos(ac.position()); h.getLevel().addFreshEntity(crate); crate.startRiding(ac.getSeats()[3], true, true);
        ac.switchHoveringMode(true); ac.ropes().toggle();
        for (int i = 0; i < 65; i++) { ac.tick(); }
        check(h, a.getVehicle() instanceof RappellingEntity && b.getVehicle() instanceof RappellingEntity,
                "scheduled crew dropping uses both configured hooks");
        check(h, ((RappellingEntity) a.getVehicle()).hookIndex() != ((RappellingEntity) b.getVehicle()).hookIndex(), "one non-player crew member per hook per interval");
        check(h, gunnerSeat.getVehicle() == ac.getSeats()[0] && crate.getVehicle() == ac.getSeats()[3], "first gunner seat and rack cargo stay aboard");
        var ha = a.getVehicle(); var hb = b.getVehicle(); cleanup(a, b, ha, hb, gunnerSeat, crate, ac); h.succeed();
    }
    private static void landing(GameTestHelper h) {
        var ac = fixture(h); var rider = crew(h, ac, 1); arm(h, ac);
        check(h, ac.ropes().descend(rider), "crew begins descent"); var helper = (RappellingEntity) rider.getVehicle();
        check(h, helper.getDeltaMovement().equals(Vec3.ZERO) && !helper.shouldRiderSit(), "descent starts still with an upright rider");
        h.succeedWhen(() -> {
            check(h, helper.isRemoved() && !rider.isPassenger(), "helper finishes at the ground");
            check(h, rider.isAlive() && rider.getHealth() == rider.getMaxHealth() && rider.fallDistance == 0,
                    "survival rider lands without fall damage");
            check(h, h.getLevel().noCollision(rider, rider.getBoundingBox().deflate(1e-5)), "rider lands outside solid blocks");
            cleanup(ac);
        });
    }
    private static void cancelled(GameTestHelper h) {
        var ac = fixture(h); var rider = crew(h, ac, 1); arm(h, ac); var original = rider.getVehicle();
        Consumer<EntityJoinLevelEvent> spawn = event -> { if (event.getEntity() instanceof RappellingEntity) { event.setCanceled(true); } };
        NeoForge.EVENT_BUS.addListener(EntityJoinLevelEvent.class, spawn);
        try { check(h, !ac.ropes().descend(rider) && rider.getVehicle() == original, "refused helper spawn keeps seat"); }
        finally { NeoForge.EVENT_BUS.unregister(spawn); }
        Consumer<EntityMountEvent> dismount = event -> { if (event.getEntityMounting() == rider && event.isDismounting()) { event.setCanceled(true); } };
        NeoForge.EVENT_BUS.addListener(EntityMountEvent.class, dismount);
        try { check(h, !ac.ropes().descend(rider) && rider.getVehicle() == original, "cancelled dismount keeps original ride"); }
        finally { NeoForge.EVENT_BUS.unregister(dismount); }
        Consumer<EntityMountEvent> mount = event -> { if (event.getEntityMounting() == rider && event.isMounting() && event.getEntityBeingMounted() instanceof RappellingEntity) { event.setCanceled(true); } };
        NeoForge.EVENT_BUS.addListener(EntityMountEvent.class, mount);
        try { check(h, !ac.ropes().descend(rider) && rider.getVehicle() == original, "cancelled helper mount restores original ride"); }
        finally { NeoForge.EVENT_BUS.unregister(mount); }
        Vec3 start = ac.ropes().hook(1).add(0, -4, 0); var block = BlockPos.containing(start);
        h.getLevel().setBlock(block, Blocks.STONE.defaultBlockState(), 3);
        check(h, !ac.ropes().descend(rider) && rider.getVehicle() == original, "blocked hook exit keeps crew aboard");
        h.getLevel().removeBlock(block, false);
        check(h, h.getLevel().getEntitiesOfClass(RappellingEntity.class, ac.getBoundingBox().inflate(20)).isEmpty(), "failure paths leave no empty helpers");
        check(h, ac.ropes().descend(rider), "clear retry succeeds"); var helper = rider.getVehicle(); cleanup(rider, helper, ac); h.succeed();
    }
    private static void interruption(GameTestHelper h) {
        var ac = fixture(h); var rider = crew(h, ac, 1); arm(h, ac); ac.ropes().descend(rider); var helper = (RappellingEntity) rider.getVehicle();
        ac.setPos(ac.position().add(.2, 0, .2)); helper.tick(); var hook = ac.ropes().hook(helper.hookIndex());
        check(h, Math.abs(helper.getX() - hook.x) < 1e-6 && Math.abs(helper.getZ() - hook.z) < 1e-6 && helper.getDeltaMovement().y < 0,
                "helper follows transformed hook horizontally and accelerates downwards");
        ac.setPos(ac.position().add(3, 0, 0));
        var wall = BlockPos.containing(ac.ropes().hook(helper.hookIndex()).x - 1, helper.getY(), helper.getZ());
        h.getLevel().setBlock(wall, Blocks.STONE.defaultBlockState(), 3); helper.tick();
        check(h, helper.isRemoved() && !rider.isPassenger() && rider.isAlive(), "horizontal obstruction releases rider without clipping through wall");
        var second = crew(h, ac, 2); ac.ropes().descend(second); var next = (RappellingEntity) second.getVehicle();
        ac.switchHoveringMode(false); ac.ropes().tick(); next.tick();
        check(h, !ac.ropes().active() && next.isRemoved() && !second.isPassenger(), "ending hover retracts and releases active descent");
        cleanup(ac); h.succeed();
    }
    private static CompoundTag save(GameTestHelper h, Entity entity) {
        var output = TagValueOutput.createWithContext(ProblemReporter.DISCARDING, h.getLevel().registryAccess());
        check(h, entity.save(output), "entity tree saves"); return output.buildResult();
    }
    private static Entity load(GameTestHelper h, CompoundTag tag) {
        return EntityType.loadEntityRecursive(TagValueInput.create(ProblemReporter.DISCARDING,
                h.getLevel().registryAccess(), tag), h.getLevel(), EntitySpawnReason.LOAD, e -> e);
    }
    private static void persistence(GameTestHelper h) {
        var ac = GameTestSupport.spawn(h, "mh-6", new Vec3(12, 30, 12), 0); ac.setFuel(600); ac.tick();
        arm(h, ac); var rider = cow(h, ac.getSeats()[1]); ac.ropes().descend(rider); var helper = (RappellingEntity) rider.getVehicle();
        var acTag = save(h, ac); var hideTag = save(h, helper); int hook = helper.hookIndex();
        helper.remove(Entity.RemovalReason.UNLOADED_TO_CHUNK); rider.discard();
        for (var seat : ac.getSeats()) { seat.remove(Entity.RemovalReason.UNLOADED_TO_CHUNK); }
        ac.remove(Entity.RemovalReason.UNLOADED_TO_CHUNK);
        h.runAfterDelay(2, () -> {
            var copy = (AircraftEntity) load(h, acTag); var descent = (RappellingEntity) load(h, hideTag);
            h.getLevel().addFreshEntityWithPassengers(copy); h.getLevel().addFreshEntityWithPassengers(descent); descent.tick();
            check(h, copy.ropes().ready() && copy.ropeLength() > 0 && descent.parent() == copy && descent.hookIndex() == hook,
                    "saved deployment and helper resolve the exact parent/hook by UUID");
            check(h, descent.getFirstPassenger() instanceof LivingEntity && descent.getFirstPassenger().isAlive(), "saved passenger remains on its descent helper");
            var passenger = descent.getFirstPassenger(); cleanup(passenger, descent, copy); h.succeed();
        });
    }
    private static void lifecycle(GameTestHelper h) {
        var ac = fixture(h); var rider = crew(h, ac, 1); arm(h, ac); ac.ropes().descend(rider); var helper = (RappellingEntity) rider.getVehicle();
        Consumer<EntityMountEvent> deny = event -> { if (event.getEntityMounting() == rider && event.isDismounting()) { event.setCanceled(true); } };
        NeoForge.EVENT_BUS.addListener(EntityMountEvent.class, deny);
        try { helper.discard(); check(h, !helper.isRemoved() && rider.getVehicle() == helper, "cancelled destructive release keeps helper/rider together"); }
        finally { NeoForge.EVENT_BUS.unregister(deny); }
        helper.discard(); check(h, helper.isRemoved() && !rider.isPassenger(), "release retries after cancellation clears");
        var second = crew(h, ac, 2); ac.ropes().descend(second); var next = (RappellingEntity) second.getVehicle(); ac.discard(); next.tick();
        check(h, next.isRemoved() && !second.isPassenger() && second.isAlive(), "parent destruction releases living crew without dangling passengers");
        var orphan = new RappellingEntity(ModEntityTypes.HIDE.get(), h.getLevel()); orphan.setPos(h.absoluteVec(new Vec3(12, 5, 12)));
        h.getLevel().addFreshEntity(orphan); var cow = cow(h, orphan);
        for (int i = 0; i < 50; i++) { orphan.tick(); }
        check(h, !orphan.isRemoved() && cow.getVehicle() == orphan, "unresolved parent has bounded fifty-tick recovery grace"); orphan.tick();
        check(h, orphan.isRemoved() && !cow.isPassenger(), "missing parent eventually releases rider and removes helper"); cleanup(cow);
        var abandoned = new RappellingEntity(ModEntityTypes.HIDE.get(), h.getLevel()); abandoned.setPos(orphan.position());
        h.getLevel().addFreshEntity(abandoned); var dead = cow(h, abandoned); dead.discard(); abandoned.tick();
        check(h, abandoned.isRemoved(), "dead/removed rider leaves no empty descent helper"); h.succeed();
    }
    private static void reattachment(GameTestHelper h) {
        var ac = fixture(h); var rider = cow(h, ac.getSeats()[1]); arm(h, ac); ac.ropes().descend(rider);
        var helper = (RappellingEntity) rider.getVehicle(); var tag = save(h, helper); tag.remove("Passengers");
        cleanup(rider, helper);
        h.runAfterDelay(2, () -> {
            var copy = (RappellingEntity) load(h, tag); h.getLevel().addFreshEntity(copy);
            for (int i = 0; i < 25; i++) { copy.tick(); }
            check(h, !copy.isRemoved() && copy.getFirstPassenger() == null, "loaded empty helper waits for separately restored player ride");
            var returning = cow(h, copy); copy.tick();
            check(h, copy.parent() == ac && returning.getVehicle() == copy && !copy.isRemoved(), "reattached rider resumes with the saved parent and hook");
            cleanup(returning, copy, ac); h.succeed();
        });
    }
}
