package io.github.mcheliceport.gametest;

import static io.github.mcheliceport.gametest.GameTestSupport.AH64;
import static io.github.mcheliceport.gametest.GameTestSupport.check;

import io.github.mcheliceport.config.ServerConfig;
import io.github.mcheliceport.entity.heli.HeliEntity;
import io.github.mcheliceport.entity.weapon.ATMissileEntity;
import io.github.mcheliceport.net.RwrThreatPayload;
import io.github.mcheliceport.radar.RwrThreat;
import io.github.mcheliceport.radar.RwrThreatManager;
import io.github.mcheliceport.weapon.WeaponSet;
import io.netty.buffer.Unpooled;
import java.util.List;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.level.GameType;
import net.minecraft.world.phys.Vec3;

/** The radar warning receiver: upstream's {@code MCH_RWRThreatManager}, which upstream never ran. */
final class RwrGameTests {

    private RwrGameTests() {
    }

    static void register() {
        ModGameTests.test("rwr_warns_of_search_lock_and_launch", 40, RwrGameTests::warns);
        ModGameTests.test("rwr_table_round_trips", 20, RwrGameTests::roundTrip);
    }

    private static boolean has(List<RwrThreat> threats, int emitterId, byte mode) {
        for (RwrThreat t : threats) {
            if (t.emitterId() == emitterId && t.mode() == mode) {
                return true;
            }
        }
        return false;
    }

    @SuppressWarnings("removal")
    private static ServerPlayer board(GameTestHelper h, HeliEntity heli) {
        ServerPlayer pilot = h.makeMockServerPlayerInLevel();
        pilot.setGameMode(GameType.SURVIVAL);
        pilot.snapTo(heli.getX(), heli.getY(), heli.getZ(), 0.0F, 0.0F);
        heli.interact(pilot, InteractionHand.MAIN_HAND, Vec3.ZERO);
        return pilot;
    }

    /**
     * A crewed AH-64 (entity radar) facing another warns it of its search radar; its seeker's lock
     * and a missile homing on the target show as a lock and a launch. Chaff hides the target from
     * the search radar, and without autoFit an AH-64 (no RWR section) has no receiver.
     */
    private static void warns(GameTestHelper h) {
        HeliEntity target = GameTestSupport.spawn(h, AH64, new Vec3(12.5, 2, 3.5), 0.0F);
        HeliEntity emitter = GameTestSupport.spawn(h, AH64, new Vec3(12.5, 2, 20.5), 180.0F);
        h.runAfterDelay(2, () -> {
            ServerPlayer targetPilot = board(h, target);
            ServerPlayer emitterPilot = board(h, emitter);
            check(h, target.getPilot() == targetPilot && emitter.getPilot() == emitterPilot, "the pilots did not board");
            check(h, RwrThreatManager.hasReceiver(target), "an AH-64 (flares) has no receiver under autoFit");
            GameTestSupport.withConfig(ServerConfig.RWR_AUTO_FIT, false,
                    () -> check(h, !RwrThreatManager.hasReceiver(target), "an AH-64 has a receiver without autoFit"));
            RwrThreatManager.clear();

            RwrThreatManager.processLevel(h.getLevel().getServer(), h.getLevel());
            check(h, has(RwrThreatManager.threatsFor(target.getId()), emitter.getId(), RwrThreat.SEARCH),
                    "the search radar ahead is not on the scope");

            int seeker = -1;
            for (int i = 0; i < emitter.getWeaponNum() && seeker < 0; i++) {
                WeaponSet ws = emitter.getWeapon(i);
                if (ws.getCurrentWeapon() != null && ws.getCurrentWeapon().getGuidanceSystem() != null) {
                    seeker = i;
                }
            }
            check(h, seeker >= 0, "the AH-64 has no seeker");
            emitter.weapons().switchWeapon(emitterPilot, seeker);
            RwrThreatManager.reportLock(emitterPilot, target.getId());
            RwrThreatManager.processLevel(h.getLevel().getServer(), h.getLevel());
            check(h, has(RwrThreatManager.threatsFor(target.getId()), emitter.getId(), RwrThreat.LOCK),
                    "the seeker's lock is not on the scope");
            check(h, !has(RwrThreatManager.threatsFor(emitter.getId()), target.getId(), RwrThreat.LOCK),
                    "the shooter is warned of its own lock");

            Vec3 from = target.position().add(0.0, 6.0, 8.0);
            ATMissileEntity missile = new ATMissileEntity(h.getLevel(), from.x, from.y, from.z, 0.0, 0.0, 0.0, 0.0F,
                    0.0F, 0.0F) {
                @Override
                public void tick() {
                    // Held in place for the test.
                }
            };
            missile.setNoGravity(true);
            h.getLevel().addFreshEntity(missile);
            missile.setTargetEntity(target);
            RwrThreatManager.processLevel(h.getLevel().getServer(), h.getLevel());
            List<RwrThreat> threats = RwrThreatManager.threatsFor(target.getId());
            check(h, has(threats, missile.getId(), RwrThreat.LAUNCH), "the homing missile is not on the scope");

            RwrThreatManager.clear();
            check(h, target.useChaff() || target.isChaffUsing() || !target.getAcInfo().haveChaff(),
                    "the chaff did not start");
            if (target.isChaffUsing()) {
                RwrThreatManager.processLevel(h.getLevel().getServer(), h.getLevel());
                check(h, !has(RwrThreatManager.threatsFor(target.getId()), emitter.getId(), RwrThreat.SEARCH),
                        "chaff did not hide the vehicle from the search radar");
            }

            missile.discard();
            RwrThreatManager.clear();
            targetPilot.stopRiding();
            emitterPilot.stopRiding();
            targetPilot.discard();
            emitterPilot.discard();
            GameTestSupport.cleanUp(target);
            GameTestSupport.cleanUp(emitter);
            h.succeed();
        });
    }

    /** A table survives the payload's codec; symbols and bearings come out as on a real scope. */
    private static void roundTrip(GameTestHelper h) {
        RwrThreat t = new RwrThreat(42, RwrThreat.AIRCRAFT, RwrThreat.LOCK, "29", 1.5, 70.0, -3.25);
        FriendlyByteBuf buf = new FriendlyByteBuf(Unpooled.buffer());
        RwrThreatPayload.CODEC.encode(buf, new RwrThreatPayload(7, true, List.of(t)));
        RwrThreatPayload back = RwrThreatPayload.CODEC.decode(buf);
        check(h, back.receiverId() == 7 && back.fitted() && back.threats().equals(List.of(t)), "the table changed");
        check(h, RwrThreat.symbolFor("mig29").equals("29") && RwrThreat.symbolFor("f-15e").equals("15")
                && RwrThreat.symbolFor("cessna172").equals("172") && RwrThreat.symbolFor("harrier").equals("HAR"),
                "wrong symbols");
        // Facing south (yaw 0): south is ahead, east (+x) on the left, west on the right, north behind.
        check(h, Math.abs(RwrThreat.relativeBearing(0, 10, 0.0F)) < 1e-6, "ahead is not 0");
        check(h, Math.abs(RwrThreat.relativeBearing(10, 0, 0.0F) + 90.0) < 1e-6, "east is not on the left");
        check(h, Math.abs(RwrThreat.relativeBearing(-10, 0, 0.0F) - 90.0) < 1e-6, "west is not on the right");
        check(h, Math.abs(Math.abs(RwrThreat.relativeBearing(0, -10, 0.0F)) - 180.0) < 1e-6, "north is not behind");
        check(h, Math.abs(RwrThreat.relativeBearing(-10, 0, 90.0F)) < 1e-6, "facing west, west is not ahead");
        h.succeed();
    }
}
