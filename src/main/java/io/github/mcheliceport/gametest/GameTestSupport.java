package io.github.mcheliceport.gametest;

import io.github.mcheliceport.content.ContentType;
import io.github.mcheliceport.entity.aircraft.AircraftEntity;
import io.github.mcheliceport.entity.heli.HeliEntity;
import io.github.mcheliceport.item.AircraftItem;
import io.github.mcheliceport.net.HeliControlPayload;
import io.github.mcheliceport.net.ModNetwork;
import io.github.mcheliceport.net.PlayerControlSwitches;
import io.github.mcheliceport.registry.ModItems;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.common.ModConfigSpec;

/** Helpers the GameTests share. */
final class GameTestSupport {

    /** The reference helicopter of M3 (decision D10). */
    static final String AH64 = "ah-64";

    private GameTestSupport() {
    }

    static AircraftItem item(String contentName) {
        return (AircraftItem) ModItems.contentItems().stream()
                .filter(i -> i.type() == ContentType.HELICOPTER && i.contentName().equals(contentName))
                .findFirst().orElseThrow(() -> new IllegalStateException("No helicopter " + contentName))
                .item().get();
    }

    static ItemStack stack(String contentName) {
        return new ItemStack(item(contentName));
    }

    /** Creates a helicopter at a position relative to the test, facing {@code yaw}, and adds it to the level. */
    static HeliEntity spawn(GameTestHelper helper, String contentName, Vec3 relative, float yaw) {
        Vec3 pos = helper.absoluteVec(relative);
        HeliEntity heli = (HeliEntity) item(contentName).createAircraft(helper.getLevel(), pos.x, pos.y, pos.z);
        if (heli == null) {
            throw helper.assertionException(Component.literal("Could not create " + contentName));
        }
        heli.initRotationYaw(yaw);
        helper.getLevel().addFreshEntity(heli);
        return heli;
    }

    /** The pilot's switches, as the pilot's client would send them. */
    static void control(HeliEntity heli, Player pilot, boolean up, boolean down, boolean left, boolean right) {
        PlayerControlSwitches s = PlayerControlSwitches.NONE
                .with(PlayerControlSwitches.THROTTLE_UP, up)
                .with(PlayerControlSwitches.THROTTLE_DOWN, down)
                .with(PlayerControlSwitches.MOVE_LEFT, left)
                .with(PlayerControlSwitches.MOVE_RIGHT, right);
        ModNetwork.applyHeliControl(heli, pilot, HeliControlPayload.switchesOnly(heli.getId(), s));
    }

    static void mode(HeliEntity heli, Player pilot, HeliControlPayload.ModeSwitch mode) {
        ModNetwork.applyHeliControl(heli, pilot, new HeliControlPayload(heli.getId(),
                HeliControlPayload.UnmountAction.NONE, mode, HeliControlPayload.HatchSwitch.NONE,
                HeliControlPayload.GearSwitch.NONE, PlayerControlSwitches.NONE, (byte) 0,
                HeliControlPayload.BladeStatus.NONE));
    }

    /**
     * Runs {@code body} with a config value changed, and puts it back. The server thread runs one
     * test step at a time, so other tests never see the change.
     */
    static <T> void withConfig(ModConfigSpec.ConfigValue<T> value, T temporary, Runnable body) {
        T old = value.get();
        value.set(temporary);
        try {
            body.run();
        } finally {
            value.set(old);
        }
    }

    static void check(GameTestHelper helper, boolean condition, String message) {
        if (!condition) {
            throw helper.assertionException(Component.literal(message));
        }
    }

    /** Removes a helicopter with its seats so it does not outlive the test. */
    static void cleanUp(AircraftEntity aircraft) {
        if (!aircraft.isRemoved()) {
            aircraft.discard();
        }
    }

    /** A survival or creative mock player standing at a position relative to the test. */
    static Player player(GameTestHelper helper, net.minecraft.world.level.GameType mode, Vec3 relative) {
        Player player = helper.makeMockPlayer(mode);
        mode.updatePlayerAbilities(player.getAbilities());
        Vec3 pos = helper.absoluteVec(relative);
        player.snapTo(pos.x, pos.y, pos.z, 0.0F, 0.0F);
        return player;
    }
}
