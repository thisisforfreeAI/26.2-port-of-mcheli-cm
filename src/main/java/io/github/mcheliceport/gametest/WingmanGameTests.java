package io.github.mcheliceport.gametest;

import static io.github.mcheliceport.gametest.GameTestSupport.check;

import io.github.mcheliceport.content.ContentType;
import io.github.mcheliceport.entity.ground.GroundVehicleEntity;
import io.github.mcheliceport.entity.heli.HeliEntity;
import io.github.mcheliceport.item.AircraftItem;
import io.github.mcheliceport.net.WingmanPanelActionPayload;
import io.github.mcheliceport.net.WingmanPanelDataPayload;
import io.github.mcheliceport.registry.ModItems;
import io.github.mcheliceport.wingman.WingmanPanel;
import io.github.mcheliceport.wingman.config.WingmanConfig;
import io.github.mcheliceport.wingman.handler.WingmanTickHandler;
import io.github.mcheliceport.wingman.wingman.WingmanEntry;
import io.github.mcheliceport.wingman.wingman.WingmanRegistry;
import io.netty.buffer.Unpooled;
import java.util.Optional;
import java.util.UUID;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.level.GameType;
import net.minecraft.world.phys.Vec3;

/** Upstream's wingmen: {@code WingmanRegistry}, the wingman panel and {@code WingmanTickHandler}'s formation flight. */
final class WingmanGameTests {

    private WingmanGameTests() {
    }

    static void register() {
        ModGameTests.test("wingman_panel_follows_and_orders", 20, WingmanGameTests::panel);
        ModGameTests.testAlone("wingman_steers_to_its_formation_slot", 20, WingmanGameTests::formation);
        ModGameTests.testAlone("wingman_tank_keeps_level", 20, WingmanGameTests::tankKeepsLevel);
    }

    private static boolean listed(WingmanPanelDataPayload data, UUID id) {
        return data.nearby().stream().anyMatch(a -> a.uuid().equals(id));
    }

    private static WingmanPanelActionPayload action(int action, UUID id, String extra) {
        return WingmanPanelActionPayload.of(action, id, extra);
    }

    /**
     * The panel lists the aircraft nobody flies, takes one as a wingman in the lowest free slot up
     * to the limit, and only the leader's pilot orders it; the formation values keep their limits.
     */
    private static void panel(GameTestHelper h) {
        WingmanConfig.reset();
        HeliEntity leader = GameTestSupport.spawn(h, GameTestSupport.AH64, new Vec3(2, 2, 2), 0.0F);
        HeliEntity wingman = GameTestSupport.spawn(h, GameTestSupport.AH64, new Vec3(8, 2, 2), 0.0F);
        HeliEntity flown = GameTestSupport.spawn(h, GameTestSupport.AH64, new Vec3(14, 2, 2), 0.0F);
        ServerPlayer pilot = h.makeMockServerPlayerInLevel();
        pilot.setGameMode(GameType.SURVIVAL);
        pilot.startRiding(leader, true, true);
        ServerPlayer other = h.makeMockServerPlayerInLevel();
        other.setGameMode(GameType.SURVIVAL);
        other.startRiding(flown, true, true);
        try {
            check(h, WingmanPanel.leaderOf(pilot) == leader, "the pilot leads its aircraft");
            WingmanPanelDataPayload data = WingmanPanel.buildData(pilot);
            check(h, listed(data, wingman.getUUID()), "an aircraft nobody flies is not offered");
            check(h, !listed(data, flown.getUUID()) && !listed(data, leader.getUUID()),
                    "a flown aircraft or the leader itself is offered");
            check(h, data.wingmen().isEmpty() && data.sideDist() == 20.0 && data.rearDist() == 30.0
                    && data.maxWings() == 10, "the panel's first data is wrong");

            WingmanPanel.apply(pilot, action(WingmanPanelActionPayload.FOLLOW, flown.getUUID(), ""));
            check(h, WingmanRegistry.get(flown.getUUID()) == null, "an aircraft another player flies became a wingman");
            WingmanPanel.apply(other, action(WingmanPanelActionPayload.FOLLOW, wingman.getUUID(), ""));
            WingmanEntry taken = WingmanRegistry.get(wingman.getUUID());
            check(h, taken != null && taken.leader == flown, "the other player could not take the wingman");
            WingmanPanel.apply(other, new WingmanPanelActionPayload(WingmanPanelActionPayload.STOP_ALL,
                    Optional.empty(), "", 0, 0, 0, 0, 0, 0));
            check(h, WingmanRegistry.get(wingman.getUUID()) == null, "stopping all kept the wingman");

            UUID ghost = UUID.randomUUID();
            WingmanRegistry.put(ghost, new WingmanEntry(leader, 0));
            WingmanPanel.apply(pilot, action(WingmanPanelActionPayload.FOLLOW, wingman.getUUID(), ""));
            WingmanEntry entry = WingmanRegistry.get(wingman.getUUID());
            check(h, entry != null && entry.leader == leader && entry.formationSlot == 1,
                    "the wingman did not take the next free slot");
            WingmanRegistry.remove(ghost);
            data = WingmanPanel.buildData(pilot);
            check(h, data.wingmen().size() == 1 && data.wingmen().getFirst().uuid().equals(wingman.getUUID())
                    && data.wingmen().getFirst().state().equals("FOLLOWING") && !listed(data, wingman.getUUID()),
                    "the panel does not list the wingman");

            WingmanPanel.apply(other, action(WingmanPanelActionPayload.AUTO, wingman.getUUID(), ""));
            WingmanPanel.apply(other, action(WingmanPanelActionPayload.STOP, wingman.getUUID(), ""));
            check(h, entry.attackMode == WingmanEntry.ATK_NONE && WingmanRegistry.get(wingman.getUUID()) == entry,
                    "another leader ordered the wingman");
            WingmanPanel.apply(pilot, action(WingmanPanelActionPayload.AUTO, wingman.getUUID(), ""));
            WingmanPanel.apply(pilot, action(WingmanPanelActionPayload.WEAPON, wingman.getUUID(), "gun"));
            check(h, entry.attackMode == WingmanEntry.ATK_AUTO && "gun".equals(entry.weaponType),
                    "Auto or the weapon type did not apply");
            WingmanPanel.apply(pilot, action(WingmanPanelActionPayload.WEAPON, wingman.getUUID(), ""));
            WingmanPanel.apply(pilot, action(WingmanPanelActionPayload.HOLD, wingman.getUUID(), ""));
            check(h, entry.attackMode == WingmanEntry.ATK_NONE && entry.weaponType == null,
                    "Hold or the weapon type Any did not apply");
            WingmanPanel.apply(pilot, action(WingmanPanelActionPayload.STOP, wingman.getUUID(), ""));
            check(h, WingmanRegistry.get(wingman.getUUID()) == null, "Stop kept the wingman");

            WingmanConfig.maxWingmen = 1;
            WingmanRegistry.put(ghost, new WingmanEntry(leader, 0));
            WingmanPanel.apply(pilot, action(WingmanPanelActionPayload.FOLLOW, wingman.getUUID(), ""));
            check(h, WingmanRegistry.get(wingman.getUUID()) == null, "a wingman joined past the limit");
            WingmanRegistry.remove(ghost);

            WingmanPanelActionPayload formation = new WingmanPanelActionPayload(WingmanPanelActionPayload.FORMATION,
                    Optional.empty(), "", 15, -5, Double.NaN, 3, 0, 0);
            WingmanPanel.apply(other, formation);
            check(h, WingmanConfig.formationSideDist == 20.0, "a formation with a value that is no number applied");
            WingmanPanel.apply(other, new WingmanPanelActionPayload(WingmanPanelActionPayload.FORMATION,
                    Optional.empty(), "", 15, -5, 900, 100, 50, -10));
            check(h, WingmanConfig.formationSideDist == 15 && WingmanConfig.formationAltOffset == -5
                    && WingmanConfig.formationRearDist == 500 && WingmanConfig.maxWingmen == 64
                    && WingmanConfig.minAttackAltitude == 50 && WingmanConfig.maxAttackAltitude == 0,
                    "the formation did not apply within its limits");

            FriendlyByteBuf buf = new FriendlyByteBuf(Unpooled.buffer());
            WingmanPanelDataPayload.CODEC.encode(buf, data);
            check(h, WingmanPanelDataPayload.CODEC.decode(buf).equals(data), "the panel data codec lost data");
            WingmanPanelActionPayload sent = action(WingmanPanelActionPayload.WEAPON, wingman.getUUID(), "bomb");
            WingmanPanelActionPayload.CODEC.encode(buf, sent);
            WingmanPanelActionPayload.CODEC.encode(buf, formation);
            check(h, WingmanPanelActionPayload.CODEC.decode(buf).equals(sent)
                    && WingmanPanelActionPayload.CODEC.decode(buf).uuid().isEmpty(), "the panel action codec lost data");
        } finally {
            WingmanConfig.reset();
            WingmanRegistry.removeForLeader(leader);
            WingmanRegistry.removeForLeader(flown);
            pilot.stopRiding();
            other.stopRiding();
            GameTestSupport.cleanUp(leader);
            GameTestSupport.cleanUp(wingman);
            GameTestSupport.cleanUp(flown);
        }
        h.succeed();
    }

    /**
     * The formation slots, and one tick of steering: a helicopter turns and dips its nose towards the
     * slot while the leader moves, with its throttle holding the slot's height, levels off beside a
     * stopped leader; the slot boost far behind, climbing to attack
     * an enemy, and the wingman released when its leader goes.
     */
    private static void formation(GameTestHelper h) {
        WingmanConfig.reset();
        HeliEntity leader = GameTestSupport.spawn(h, GameTestSupport.AH64, new Vec3(2, 2, 2), 0.0F);
        HeliEntity wingman = GameTestSupport.spawn(h, GameTestSupport.AH64, new Vec3(2, 2, 2), 0.0F);
        var zombie = EntityTypes.ZOMBIE.create(h.getLevel(), EntitySpawnReason.COMMAND);
        var cow = EntityTypes.COW.create(h.getLevel(), EntitySpawnReason.COMMAND);
        try {
            Vec3 l = leader.position();
            check(h, WingmanTickHandler.formationPos(leader, 0).distanceTo(l.add(20, 0, -30)) < 1e-6
                    && WingmanTickHandler.formationPos(leader, 1).distanceTo(l.add(-20, 0, -30)) < 1e-6
                    && WingmanTickHandler.formationPos(leader, 2).distanceTo(l.add(40, 0, -60)) < 1e-6,
                    "the formation slots are not upstream's");
            WingmanConfig.formationSideDist = 0.0;
            check(h, WingmanTickHandler.formationPos(leader, 1).distanceTo(l.add(0, 0, -60)) < 1e-6,
                    "the column slots are not upstream's");
            WingmanConfig.setFormation(4, 0, 6, 10, 0, 0);
            Vec3 slot = WingmanTickHandler.formationPos(leader, 0);
            WingmanRegistry.put(wingman.getUUID(), new WingmanEntry(leader, 0));

            // Moving leader: the wingman turns towards its slot, at most 4 degrees, and a helicopter
            // takes its hover throttle (gravity 0.04 against 0.055 of lift) trimmed 0.03 a block to the slot's height.
            double hover = 0.04 / 0.055;
            wingman.setPos(slot.x + 12, slot.y + 2, slot.z + 10);
            wingman.setYaw(0.0F);
            leader.setDeltaMovement(0, 0, 0.5);
            WingmanTickHandler.tickStart(h.getLevel());
            float turned = Mth.wrapDegrees(wingman.getYaw());
            check(h, turned > 0.0F && turned <= 4.0F + 1e-4F, "the wingman did not turn towards its slot: " + turned);
            check(h, Math.abs(wingman.getCurrentThrottle() - (hover - 0.06)) < 1e-6,
                    "the helicopter's formation throttle is wrong: " + wingman.getCurrentThrottle());
            check(h, Math.abs(wingman.getPitch() - 3.0F) < 1e-4F, "the helicopter did not dip its nose to fly to the slot");

            // Stopped leader, slot near: a helicopter holds the same way.
            leader.setDeltaMovement(Vec3.ZERO);
            wingman.setPos(slot.x + 12, slot.y - 3, slot.z + 10);
            WingmanTickHandler.tickStart(h.getLevel());
            check(h, Math.abs(wingman.getCurrentThrottle() - (hover + 0.09)) < 1e-6, "the hold throttle is wrong");
            check(h, Math.abs(wingman.getPitch()) < 1e-4F, "the waiting helicopter did not level its nose");

            // More than 20 blocks from its slot behind a moving leader, the slot boost moves it towards
            // the slot after the flight model: here 2 x 0.5 x (24 - 20) / (80 - 20) blocks.
            wingman.setPos(slot.x, slot.y, slot.z + 24);
            leader.setDeltaMovement(0, 0, 0.5);
            WingmanTickHandler.tickEnd(h.getLevel());
            double moved = slot.z + 24 - wingman.getZ();
            check(h, Math.abs(moved - 0.5 * 2.0 * (4.0 / 60.0)) < 1e-6 && Math.abs(wingman.getX() - slot.x) < 1e-6,
                    "the slot boost is wrong: " + moved);

            // Auto attack: a hostile mob is an enemy, a cow, the leader and another wingman are not.
            zombie.snapTo(h.absoluteVec(new Vec3(4, 2, 4)));
            cow.snapTo(h.absoluteVec(new Vec3(5, 2, 4)));
            h.getLevel().addFreshEntity(zombie);
            h.getLevel().addFreshEntity(cow);
            check(h, WingmanTickHandler.isEnemy(leader, zombie) && !WingmanTickHandler.isEnemy(leader, cow)
                    && !WingmanTickHandler.isEnemy(leader, leader) && !WingmanTickHandler.isEnemy(leader, wingman),
                    "the enemy check is wrong");
            WingmanEntry entry = WingmanRegistry.get(wingman.getUUID());
            entry.attackMode = WingmanEntry.ATK_AUTO;
            WingmanTickHandler.tickStart(h.getLevel());
            check(h, wingman.getCurrentThrottle() > hover && entry.currentAutoTarget != null
                    && WingmanTickHandler.isEnemy(leader, h.getLevel().getEntity(entry.currentAutoTarget)),
                    "an attacking wingman has no enemy or less than full throttle");
            // With no pilot in the leader the wingman stops attacking.
            WingmanTickHandler.tickEnd(h.getLevel());
            check(h, entry.attackMode == WingmanEntry.ATK_NONE, "the wingman kept attacking without a leader pilot");

            leader.discard();
            WingmanTickHandler.tickEnd(h.getLevel());
            check(h, WingmanRegistry.get(wingman.getUUID()) == null, "the wingman outlived its leader");
        } finally {
            WingmanConfig.reset();
            WingmanRegistry.remove(wingman.getUUID());
            GameTestSupport.cleanUp(leader);
            GameTestSupport.cleanUp(wingman);
            zombie.discard();
            cow.discard();
        }
        h.succeed();
    }

    /**
     * A tank wingman turns to its slot and to its target but leaves its pitch and roll to the ground,
     * aiming its gun through the rider pitch: before, it pitched its nose up at a leader or an
     * enemy above it.
     */
    private static void tankKeepsLevel(GameTestHelper h) {
        WingmanConfig.reset();
        HeliEntity leader = GameTestSupport.spawn(h, GameTestSupport.AH64, new Vec3(2, 2, 2), 0.0F);
        var item = (AircraftItem) ModItems.contentItems().stream()
                .filter(c -> c.type() == ContentType.TANK && c.contentName().equals("m1a2"))
                .findFirst().orElseThrow().item().get();
        Vec3 p = h.absoluteVec(new Vec3(8, 2, 8));
        var tank = (GroundVehicleEntity) item.createAircraft(h.getLevel(), p.x, p.y, p.z);
        h.getLevel().addFreshEntity(tank);
        var zombie = EntityTypes.ZOMBIE.create(h.getLevel(), EntitySpawnReason.COMMAND);
        try {
            leader.setPos(leader.getX(), leader.getY() + 30, leader.getZ());
            leader.setDeltaMovement(0, 0, 0.5);
            tank.setPitch(0.0F);
            tank.setRoll(0.0F);
            WingmanRegistry.put(tank.getUUID(), new WingmanEntry(leader, 0));
            for (int i = 0; i < 5; i++) {
                WingmanTickHandler.tickStart(h.getLevel());
            }
            check(h, tank.getPitch() == 0.0F && tank.getRoll() == 0.0F,
                    "the tank pitched towards a slot above it: " + tank.getPitch());

            zombie.snapTo(h.absoluteVec(new Vec3(8, 12, 20)));
            zombie.setNoGravity(true);
            h.getLevel().addFreshEntity(zombie);
            WingmanEntry entry = WingmanRegistry.get(tank.getUUID());
            entry.attackMode = WingmanEntry.ATK_AUTO;
            entry.weaponType = "cannon";
            for (int i = 0; i < 5; i++) {
                WingmanTickHandler.tickStart(h.getLevel());
            }
            check(h, entry.currentAutoTarget != null, "the tank found no enemy");
            check(h, tank.getPitch() == 0.0F && tank.getRoll() == 0.0F,
                    "the tank pitched towards an enemy above it: " + tank.getPitch());
            check(h, tank.lastRiderPitch < -10.0F, "the tank's gun did not aim up: " + tank.lastRiderPitch);
        } finally {
            WingmanConfig.reset();
            WingmanRegistry.remove(tank.getUUID());
            GameTestSupport.cleanUp(leader);
            GameTestSupport.cleanUp(tank);
            zombie.discard();
        }
        h.succeed();
    }
}
