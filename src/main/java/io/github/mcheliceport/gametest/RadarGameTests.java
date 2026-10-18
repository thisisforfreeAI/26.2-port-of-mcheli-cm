package io.github.mcheliceport.gametest;

import static io.github.mcheliceport.gametest.GameTestSupport.AH64;
import static io.github.mcheliceport.gametest.GameTestSupport.check;

import io.github.mcheliceport.entity.heli.HeliEntity;
import io.github.mcheliceport.net.HeliControlPayload;
import io.github.mcheliceport.net.ModNetwork;
import io.github.mcheliceport.net.PlayerControlSwitches;
import io.netty.buffer.Unpooled;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.GameType;
import net.minecraft.world.phys.Vec3;

/** Upstream's radar switch ({@code KeyRadarSwitch} and status bit 13). */
final class RadarGameTests {

    private RadarGameTests() {
    }

    static void register() {
        ModGameTests.test("radar_switch_turns_radar_on_and_off", 20, RadarGameTests::radarSwitch);
    }

    /** The switch as the rider's client sends it, through the payload's codec. */
    private static void send(HeliEntity heli, Player rider, int bit) {
        HeliControlPayload sent = HeliControlPayload.switchesOnly(heli.getId(), PlayerControlSwitches.NONE.with(bit, true));
        FriendlyByteBuf buf = new FriendlyByteBuf(Unpooled.buffer());
        HeliControlPayload.CODEC.encode(buf, sent);
        ModNetwork.applyHeliControl(heli, rider, HeliControlPayload.CODEC.decode(buf));
    }

    private static void radarSwitch(GameTestHelper h) {
        HeliEntity heli = GameTestSupport.spawn(h, AH64, new Vec3(8.5, 1, 8.5), 0.0F);
        Player pilot = GameTestSupport.player(h, GameType.SURVIVAL, new Vec3(10.5, 1, 8.5));
        h.runAfterDelay(2, () -> {
            heli.interact(pilot, InteractionHand.MAIN_HAND, Vec3.ZERO);
            check(h, heli.getPilot() == pilot, "the pilot did not board");
            check(h, !heli.isRadarEnabledRuntime(), "the radar is on when the vehicle appears");
            send(heli, pilot, PlayerControlSwitches.RADAR_ON);
            check(h, heli.isRadarEnabledRuntime(), "the radar did not switch on");
            send(heli, pilot, PlayerControlSwitches.RADAR_OFF);
            check(h, !heli.isRadarEnabledRuntime(), "the radar did not switch off");
            send(heli, pilot, PlayerControlSwitches.USE_ECM_JAMMER);
            check(h, !heli.isRadarEnabledRuntime(), "another switch turned the radar on");
            GameTestSupport.cleanUp(heli);
            h.succeed();
        });
    }
}
