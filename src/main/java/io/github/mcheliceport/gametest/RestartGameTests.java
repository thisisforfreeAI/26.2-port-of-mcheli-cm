package io.github.mcheliceport.gametest;

import static io.github.mcheliceport.gametest.GameTestSupport.AH64;
import static io.github.mcheliceport.gametest.GameTestSupport.check;

import io.github.mcheliceport.entity.heli.HeliEntity;
import io.github.mcheliceport.entity.weapon.ATMissileEntity;
import io.github.mcheliceport.entity.weapon.BaseBulletEntity;
import io.github.mcheliceport.entity.weapon.BulletEntity;
import io.github.mcheliceport.wingman.handler.MissionChunkLoader;
import io.github.mcheliceport.wingman.mission.AutonomousState;
import io.github.mcheliceport.wingman.mission.MissionNode;
import io.github.mcheliceport.wingman.mission.MissionOrder;
import io.github.mcheliceport.wingman.mission.MissionType;
import io.github.mcheliceport.wingman.wingman.WingmanEntry;
import io.github.mcheliceport.wingman.wingman.WingmanPersistence;
import io.github.mcheliceport.wingman.wingman.WingmanRegistry;
import java.util.List;
import java.util.UUID;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.util.ProblemReporter;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.level.storage.TagValueInput;
import net.minecraft.world.level.storage.TagValueOutput;
import net.minecraft.world.phys.Vec3;

/**
 * A port addition: wingmen, orders, planner missions and projectiles in flight survive a server
 * restart ({@link WingmanPersistence}, {@link BaseBulletEntity}'s saving).
 */
final class RestartGameTests {

    private RestartGameTests() {
    }

    static void register() {
        ModGameTests.testAlone("restart_keeps_orders_and_wingmen", 120, RestartGameTests::ordersAndWingmen);
        ModGameTests.testAlone("restart_wingman_waits_for_unloaded_leader", 120, RestartGameTests::leaderUnloads);
        // In a batch of its own: other tests' blasts (NTM Next bombs among them) could destroy its
        // target first, and its missile would lease chunks that the "alone" batch's lease test counts.
        ModGameTests.testInBatch("restart_missiles", "restart_keeps_missiles_in_flight", 60, RestartGameTests::missiles);
    }

    private static CompoundTag save(GameTestHelper h, Entity e) {
        TagValueOutput output = TagValueOutput.createWithContext(ProblemReporter.DISCARDING,
                h.getLevel().registryAccess());
        e.saveWithoutId(output);
        CompoundTag tag = output.buildResult();
        tag.putString("id", EntityType.getKey(e.getType()).toString());
        return tag;
    }

    private static Entity load(GameTestHelper h, CompoundTag tag) {
        return EntityType.loadEntityRecursive(TagValueInput.create(ProblemReporter.DISCARDING,
                h.getLevel().registryAccess(), tag), h.getLevel(), EntitySpawnReason.LOAD, e -> e);
    }

    /**
     * An order, a planner mission and a wingman are written and read back as at a restart: the
     * order and the mission go straight back into the registry with their chunks loaded, and the
     * wingman waits until its leader is loaded, then rejoins in its old slot with its old orders.
     */
    private static void ordersAndWingmen(GameTestHelper h) {
        HeliEntity leader = GameTestSupport.spawn(h, AH64, new Vec3(2, 2, 2), 0.0F);
        HeliEntity wingman = GameTestSupport.spawn(h, AH64, new Vec3(10, 2, 2), 0.0F);
        HeliEntity ordered = GameTestSupport.spawn(h, AH64, new Vec3(18, 2, 2), 0.0F);
        HeliEntity planned = GameTestSupport.spawn(h, AH64, new Vec3(26, 2, 2), 0.0F);
        // A wingman of a leader nobody flies holds fire, so the leader needs a pilot for Auto to stay.
        net.minecraft.server.level.ServerPlayer pilot = h.makeMockServerPlayerInLevel();
        pilot.setGameMode(net.minecraft.world.level.GameType.SURVIVAL);
        UUID wid = wingman.getUUID();
        UUID oid = ordered.getUUID();
        UUID pid = planned.getUUID();
        h.runAfterDelay(2, () -> {
            pilot.startRiding(leader, true, true);
            WingmanEntry w = new WingmanEntry(leader, 3);
            w.attackMode = WingmanEntry.ATK_AUTO;
            w.weaponType = "gun";
            WingmanRegistry.put(wid, w);

            WingmanEntry o = new WingmanEntry();
            MissionOrder order = new MissionOrder();
            order.missionTypes.add(MissionType.CAP);
            order.missionTypes.add(MissionType.RECON);
            order.weapons.add("aamissile");
            order.targetX = 1234.0;
            order.targetZ = -567.0;
            order.baseId = "alpha";
            order.cruiseAlt = 140.0;
            order.timeLimitSeconds = 900;
            o.order = order;
            o.autoState = AutonomousState.ON_STATION;
            o.orderTimer = 321;
            o.teamName = "red";
            o.taxiWpQueue.add("wp1");
            o.taxiWpQueue.add("wp2");
            o.taxiWpIndex = 1;
            o.parkingHeadingYaw = 90.0F;
            WingmanRegistry.put(oid, o);

            WingmanEntry p = new WingmanEntry();
            p.startMission(List.of(MissionNode.flyTo(100, 90, 200), MissionNode.loiter(400),
                    MissionNode.land("alpha")));
            p.advanceMission();
            p.missionNodeTimer = 55;
            WingmanRegistry.put(pid, p);

            CompoundTag saved = WingmanPersistence.snapshotForTest(h.getLevel().getServer());
            WingmanPersistence.restoreForTest(h.getLevel().getServer(), saved);
            try {
                WingmanEntry ro = WingmanRegistry.get(oid);
                check(h, ro != null && ro.order != null, "the order was not restored");
                check(h, ro.order.missionTypes.equals(order.missionTypes) && ro.order.weapons.contains("aamissile")
                        && ro.order.targetX == 1234.0 && ro.order.targetZ == -567.0
                        && "alpha".equals(ro.order.baseId) && ro.order.cruiseAlt == 140.0
                        && ro.order.timeLimitSeconds == 900, "the order's values changed");
                check(h, ro.autoState == AutonomousState.ON_STATION && ro.orderTimer == 321
                        && "red".equals(ro.teamName) && ro.taxiWpQueue.equals(List.of("wp1", "wp2"))
                        && ro.taxiWpIndex == 1 && ro.parkingHeadingYaw == 90.0F, "the order's state changed");
                check(h, MissionChunkLoader.loadedCenter(oid) != null, "the order's chunks were not loaded");

                WingmanEntry rp = WingmanRegistry.get(pid);
                check(h, rp != null && rp.mission != null && rp.mission.size() == 3 && rp.missionIndex == 1
                        && rp.missionNodeTimer == 55 && rp.autoState == AutonomousState.ENROUTE
                        && rp.currentNode() != null && rp.currentNode().type() == MissionNode.Type.LOITER
                        && rp.currentNode().durationTicks() == 400, "the planner mission was not restored");

                check(h, WingmanRegistry.get(wid) == null && WingmanPersistence.isWaiting(wid),
                        "the wingman did not wait for its leader");
            } finally {
                WingmanRegistry.remove(oid);
                WingmanRegistry.remove(pid);
            }
            h.succeedWhen(() -> {
                WingmanEntry rw = WingmanRegistry.get(wid);
                check(h, rw != null, "the wingman did not rejoin its leader");
                check(h, rw.leader == leader && rw.formationSlot == 3 && rw.attackMode == WingmanEntry.ATK_AUTO
                        && "gun".equals(rw.weaponType), "the wingman rejoined with other orders");
                check(h, MissionChunkLoader.loadedCenter(oid) == null, "the ended order still loads chunks");
                WingmanRegistry.remove(wid);
            });
        });
    }

    /**
     * A leader that unloads (its pilot logged out) leaves its wingman waiting instead of dropping it;
     * when the leader loads again the wingman follows it again.
     */
    private static void leaderUnloads(GameTestHelper h) {
        HeliEntity leader = GameTestSupport.spawn(h, AH64, new Vec3(2, 2, 2), 0.0F);
        HeliEntity wingman = GameTestSupport.spawn(h, AH64, new Vec3(10, 2, 2), 0.0F);
        UUID wid = wingman.getUUID();
        h.runAfterDelay(2, () -> {
            WingmanRegistry.put(wid, new WingmanEntry(leader, 2));
            CompoundTag tag = save(h, leader);
            leader.getPassengers().forEach(p -> p.remove(Entity.RemovalReason.UNLOADED_TO_CHUNK));
            leader.remove(Entity.RemovalReason.UNLOADED_TO_CHUNK);
            h.runAfterDelay(2, () -> {
                check(h, WingmanRegistry.get(wid) == null && WingmanPersistence.isWaiting(wid),
                        "the wingman was dropped when its leader unloaded");
                Entity back = load(h, tag);
                check(h, back instanceof HeliEntity && back.getUUID().equals(leader.getUUID()),
                        "the leader did not load again");
                h.getLevel().addFreshEntity(back);
                back.getPassengers().forEach(p -> h.getLevel().addFreshEntity(p));
                h.succeedWhen(() -> {
                    WingmanEntry e = WingmanRegistry.get(wid);
                    check(h, e != null && e.leader == back && e.formationSlot == 2,
                            "the wingman did not follow its leader again");
                    WingmanRegistry.remove(wid);
                    GameTestSupport.cleanUp((HeliEntity) back);
                });
            });
        });
    }

    /**
     * A guided missile is saved with its flight and its target and comes back flying, with its target
     * found again by UUID; gun rounds are still not saved, and a projectile saved by an older version
     * is discarded as before.
     */
    private static void missiles(GameTestHelper h) {
        HeliEntity shooter = GameTestSupport.spawn(h, AH64, new Vec3(2, 2, 2), 0.0F);
        HeliEntity target = GameTestSupport.spawn(h, AH64, new Vec3(30, 2, 30), 0.0F);
        h.runAfterDelay(2, () -> {
            Vec3 from = h.absoluteVec(new Vec3(4.5, 20, 4.5));
            ATMissileEntity missile = new ATMissileEntity(h.getLevel(), from.x, from.y, from.z, 1.0, 0.0, 0.0, 0.0F,
                    0.0F, 1.5);
            missile.setName("9k114");
            missile.shootingEntity = shooter;
            missile.shootingAircraft = shooter;
            missile.setTargetEntity(target);
            missile.guidanceType = 1;
            missile.setPower(77);
            missile.explosionPower = 3;
            missile.tickCount = 15;
            check(h, missile.shouldBeSaved(), "a missile in flight is not saved");
            CompoundTag tag = save(h, missile);
            missile.discard();

            Entity loaded = load(h, tag);
            check(h, loaded instanceof ATMissileEntity, "the missile did not load");
            ATMissileEntity copy = (ATMissileEntity) loaded;
            check(h, !copy.isRemoved() && copy.wasRestored(), "the loaded missile was discarded");
            check(h, "9k114".equals(copy.getInfoName()) && copy.getPower() == 77 && copy.explosionPower == 3
                    && copy.guidanceType == 1 && copy.tickCount == 15, "the missile's values changed");
            check(h, copy.getDeltaMovement().x > 1.0, "the missile lost its speed: " + copy.getDeltaMovement());
            check(h, copy.isResolving() && copy.targetEntity == null, "the target was known before it was found");
            copy.setNoGravity(true);
            h.getLevel().addFreshEntity(copy);

            BulletEntity round = new BulletEntity(h.getLevel(), from.x, from.y, from.z, 1.0, 0.0, 0.0, 0.0F, 0.0F, 3.0);
            round.setName("m230");
            check(h, !round.shouldBeSaved(), "a gun round would be saved");

            CompoundTag old = tag.copy();
            old.remove("McFlight");
            Entity legacy = load(h, old);
            check(h, legacy == null || legacy.isRemoved(), "a projectile from an older save was kept");

            // One tick of its own, so the check does not depend on where the level ticks it or what
            // it flies into afterwards.
            copy.tick();
            try {
                check(h, copy.targetEntity == target, "the target was not found again (target removed: "
                        + target.getRemovalReason() + ", found " + copy.targetEntity + ")");
                check(h, copy.shootingEntity == shooter && copy.shootingAircraft == shooter,
                        "the shooter was not found again");
                check(h, !copy.isResolving(), "the missile still looks for what it found");
            } finally {
                copy.discard();
            }
            h.succeed();
        });
    }
}
