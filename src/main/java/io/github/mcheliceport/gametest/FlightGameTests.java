package io.github.mcheliceport.gametest;

import static io.github.mcheliceport.gametest.GameTestSupport.AH64;
import static io.github.mcheliceport.gametest.GameTestSupport.check;
import static io.github.mcheliceport.gametest.GameTestSupport.control;
import static io.github.mcheliceport.gametest.GameTestSupport.player;
import static io.github.mcheliceport.gametest.GameTestSupport.spawn;

import io.github.mcheliceport.config.ServerConfig;
import io.github.mcheliceport.entity.heli.HeliEntity;
import io.github.mcheliceport.net.AircraftMotionPayload;
import io.github.mcheliceport.net.HeliControlPayload;
import io.github.mcheliceport.net.ModNetwork;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.MoverType;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.Vec3;

/**
 * Exit criterion E7: a scripted take-off, hover and landing on the server.
 *
 * <p>Server-simulated flight (the server-authoritative mode, and any pilot whose client does not
 * move the vehicle) runs the real flight model on the server with a mock pilot's switches. In the
 * client-authoritative mode the pilot's client flies; the test plays that client's part by sending
 * the motion payload and moving the vehicle as vanilla's vehicle-move handler does, and checks the
 * server's side: it follows, it does not simulate, and it caps moves no helicopter can make.
 *
 * <p>{@code runGameTestServer} places its tests at y -59 in a flat world at a random x and z, so
 * these flights run below y 0; {@link #negativeCoordinates} flies at fixed negative x and z.
 */
final class FlightGameTests {

    private FlightGameTests() {
    }

    static void register() {
        ModGameTests.test("flight_take_off_hover_land", 1200, FlightGameTests::takeOffHoverLand);
        ModGameTests.test("flight_negative_coordinates", 600, FlightGameTests::negativeCoordinates);
        ModGameTests.test("flight_crash_explodes", 300, FlightGameTests::crashExplodes);
        ModGameTests.test("flight_client_authoritative", 400, FlightGameTests::clientAuthoritative);
    }

    private static Player pilot(GameTestHelper helper, HeliEntity heli) {
        Player pilot = player(helper, GameType.SURVIVAL, new Vec3(12.5, 1.0, 9.5));
        heli.setFuel(heli.getMaxFuel());
        heli.interact(pilot, InteractionHand.MAIN_HAND, Vec3.ZERO);
        check(helper, heli.getPilot() == pilot, "the pilot boarded");
        check(helper, heli.simulates() && !heli.followsPilot(), "the server flies a mock pilot's helicopter");
        return pilot;
    }

    /** A flight script: climb, hover, come down, land. Ticks count from the first. */
    private static final class Script {
        int tick;
        double startY;
        double climbedY;
        double hoverY = Double.NaN;
        boolean done;
    }

    private static void takeOffHoverLand(GameTestHelper helper) {
        HeliEntity heli = spawn(helper, AH64, new Vec3(12.5, 1.0, 12.5), 0.0F);
        Player pilot = pilot(helper, heli);
        Script s = new Script();
        s.startY = heli.getY();
        float health = pilot.getHealth();
        helper.onEachTick(() -> {
            if (s.done) {
                return;
            }
            int t = ++s.tick;
            if (t <= 100) {
                control(heli, pilot, true, false, false, false);
            } else if (t == 101) {
                s.climbedY = heli.getY();
                check(helper, s.climbedY > s.startY + 3.0, "full throttle for 100 ticks climbs: "
                        + (s.climbedY - s.startY));
                control(heli, pilot, false, false, false, false);
                GameTestSupport.mode(heli, pilot, HeliControlPayload.ModeSwitch.HOVERING_ON);
                check(helper, heli.isHoveringMode(), "hovering mode on");
            } else if (t <= 160) {
                return;
            } else if (t <= 360) {
                if (Double.isNaN(s.hoverY)) {
                    s.hoverY = heli.getY();
                }
                check(helper, Math.abs(heli.getY() - s.hoverY) <= 1.0, "hovering holds height within a block: "
                        + (heli.getY() - s.hoverY) + " at tick " + t);
            } else if (t == 361) {
                GameTestSupport.mode(heli, pilot, HeliControlPayload.ModeSwitch.HOVERING_OFF);
                check(helper, !heli.isHoveringMode(), "hovering mode off");
                control(heli, pilot, false, true, false, false);
            } else if (t == 386) {
                // Let the auto throttle settle for a gentle descent.
                control(heli, pilot, false, false, false, false);
            } else if (t > 386 && heli.onGround()) {
                check(helper, heli.getDamageTaken() == 0, "a normal landing does no damage: "
                        + heli.getDamageTaken());
                check(helper, pilot.getHealth() == health, "nor hurts the pilot");
                check(helper, Math.abs(heli.getY() - s.startY) < 0.5, "back on the floor: " + (heli.getY() - s.startY));
                s.done = true;
                GameTestSupport.cleanUp(heli);
                helper.succeed();
            }
        });
    }

    private static void negativeCoordinates(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        BlockPos origin = helper.absolutePos(BlockPos.ZERO);
        BlockPos pad = new BlockPos(-4000, origin.getY(), -4000);
        int chunkX = pad.getX() >> 4;
        int chunkZ = pad.getZ() >> 4;
        level.getChunk(chunkX, chunkZ);
        level.setChunkForced(chunkX, chunkZ, true);
        for (int dx = -3; dx <= 3; dx++) {
            for (int dz = -3; dz <= 3; dz++) {
                level.setBlockAndUpdate(pad.offset(dx, 0, dz), Blocks.STONE.defaultBlockState());
                for (int dy = 1; dy <= 24; dy++) {
                    level.setBlockAndUpdate(pad.offset(dx, dy, dz), Blocks.AIR.defaultBlockState());
                }
            }
        }
        HeliEntity heli = (HeliEntity) GameTestSupport.item(AH64).createAircraft(level, pad.getX() + 0.5,
                pad.getY() + 1.0, pad.getZ() + 0.5);
        check(helper, heli != null, "created");
        level.addFreshEntity(heli);
        Player pilot = helper.makeMockPlayer(GameType.SURVIVAL);
        pilot.snapTo(pad.getX() + 0.5, pad.getY() + 1.0, pad.getZ() + 3.5, 0.0F, 0.0F);
        heli.setFuel(heli.getMaxFuel());
        heli.interact(pilot, InteractionHand.MAIN_HAND, Vec3.ZERO);
        check(helper, heli.getPilot() == pilot, "the pilot boarded");
        check(helper, heli.blockWithin(3, -3), "the ground probe finds the pad at negative x and z");
        Script s = new Script();
        s.startY = heli.getY();
        helper.onEachTick(() -> {
            if (s.done) {
                return;
            }
            int t = ++s.tick;
            if (t <= 80) {
                control(heli, pilot, true, false, false, false);
            } else if (t == 81) {
                check(helper, heli.getY() > s.startY + 2.0, "climbed at negative x and z: " + (heli.getY() - s.startY));
                control(heli, pilot, false, true, false, false);
            } else if (t == 106) {
                control(heli, pilot, false, false, false, false);
            } else if (t > 106 && heli.onGround()) {
                check(helper, Math.abs(heli.getY() - s.startY) < 0.5, "landed back on the pad: "
                        + (heli.getY() - s.startY));
                check(helper, heli.getDamageTaken() == 0, "without damage");
                s.done = true;
                GameTestSupport.cleanUp(heli);
                level.setChunkForced(chunkX, chunkZ, false);
                helper.succeed();
            }
        });
    }

    private static void crashExplodes(GameTestHelper helper) {
        HeliEntity heli = spawn(helper, AH64, new Vec3(12.5, 31.0, 12.5), 0.0F);
        heli.setDamageTaken(heli.getMaxHP());
        heli.destroyAircraft();
        check(helper, heli.isDestroyed() && !heli.isExploded(), "destroyed, not yet exploded");
        helper.succeedWhen(() -> {
            check(helper, heli.isExploded(), "a destroyed helicopter explodes when it hits the ground");
            GameTestSupport.cleanUp(heli);
        });
    }

    private static void clientAuthoritative(GameTestHelper helper) {
        HeliEntity heli = spawn(helper, AH64, new Vec3(12.5, 1.0, 12.5), 0.0F);
        @SuppressWarnings("removal")
        ServerPlayer pilot = helper.makeMockServerPlayerInLevel();
        pilot.snapTo(heli.getX(), heli.getY(), heli.getZ() - 3.0, 0.0F, 0.0F);
        heli.interact(pilot, InteractionHand.MAIN_HAND, Vec3.ZERO);
        check(helper, heli.getPilot() == pilot, "the pilot boarded");
        check(helper, heli.isClientAuthoritative() && heli.followsPilot() && !heli.simulates(),
                "a real player's client flies the helicopter by default (decision D2)");
        GameTestSupport.withConfig(ServerConfig.SERVER_AUTHORITATIVE_MOVEMENT, true, () -> check(helper,
                !heli.isClientAuthoritative() && heli.simulates(), "serverAuthoritativeMovement makes the server fly it"));

        Script s = new Script();
        s.startY = heli.getY();
        helper.onEachTick(() -> {
            if (s.done) {
                return;
            }
            int t = ++s.tick;
            if (t <= 40) {
                pilotClient(heli, 0.0, 0.25, 0.0, 0.8F);
            } else if (t == 41) {
                check(helper, heli.getY() > s.startY + 8.0, "the server followed the climb: " + (heli.getY() - s.startY));
                check(helper, Math.abs(heli.getThrottle() - 0.8) < 0.051, "and the pilot's throttle: " + heli.getThrottle());
                Vec3 before = heli.position();
                heli.move(MoverType.PLAYER, new Vec3(10.0, 5.0, 0.0));
                Vec3 step = heli.position().subtract(before);
                check(helper, step.horizontalDistance() <= heli.getMaxSpeedForChecks() + 1.0E-6,
                        "a too-fast move is capped: " + step.horizontalDistance());
                check(helper, step.y <= 1.0 + 1.0E-6, "a too-high climb is capped: " + step.y);
            } else if (heli.onGround()) {
                check(helper, heli.getDamageTaken() == 0, "landed without damage");
                s.done = true;
                GameTestSupport.cleanUp(heli);
                helper.getLevel().getServer().getPlayerList().remove(pilot);
                helper.succeed();
            } else {
                pilotClient(heli, 0.0, -0.25, 0.0, 0.4F);
            }
        });
    }

    /** What the pilot's client sends each tick: its motion and throttle, and the move itself. */
    private static void pilotClient(HeliEntity heli, double mx, double my, double mz, float throttle) {
        ModNetwork.applyPilotMotion(heli, new AircraftMotionPayload(heli.getId(), mx, my, mz, throttle));
        heli.move(MoverType.PLAYER, new Vec3(mx, my, mz));
    }
}
