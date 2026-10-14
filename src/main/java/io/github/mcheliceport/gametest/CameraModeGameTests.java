package io.github.mcheliceport.gametest;

import static io.github.mcheliceport.gametest.GameTestSupport.AH64;
import static io.github.mcheliceport.gametest.GameTestSupport.check;
import static io.github.mcheliceport.gametest.GameTestSupport.player;
import static io.github.mcheliceport.gametest.GameTestSupport.spawn;

import io.github.mcheliceport.entity.aircraft.AircraftCamera;
import io.github.mcheliceport.entity.heli.HeliEntity;
import io.github.mcheliceport.net.HeliControlPayload;
import io.github.mcheliceport.net.ModNetwork;
import io.github.mcheliceport.net.PlayerControlSwitches;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.GameType;
import net.minecraft.world.phys.Vec3;

/** Exit criterion E5: camera modes give and remove night vision as upstream. */
final class CameraModeGameTests {

    private CameraModeGameTests() {
    }

    static void register() {
        ModGameTests.test("camera_night_vision", 80, CameraModeGameTests::nightVision);
        ModGameTests.test("camera_keeps_longer_night_vision", 40, CameraModeGameTests::keepsLongerNightVision);
        ModGameTests.test("camera_needs_night_vision_content", 20, CameraModeGameTests::needsNightVisionContent);
        ModGameTests.test("camera_modes_per_seat", 20, CameraModeGameTests::modesPerSeat);
        ModGameTests.test("camera_destroyed_resets", 20, CameraModeGameTests::destroyedResets);
    }

    private static Player rider(GameTestHelper helper) {
        return player(helper, GameType.SURVIVAL, new Vec3(12.5, 1.0, 9.5));
    }

    /** The camera-mode key as the rider's client sends it: 0 is normal, 1 night vision. */
    private static void switchCamera(HeliEntity heli, Player rider, int mode) {
        ModNetwork.applyHeliControl(heli, rider, new HeliControlPayload(heli.getId(),
                HeliControlPayload.UnmountAction.NONE, HeliControlPayload.ModeSwitch.NONE,
                HeliControlPayload.HatchSwitch.NONE, HeliControlPayload.GearSwitch.NONE,
                PlayerControlSwitches.NONE, (byte) 0, HeliControlPayload.BladeStatus.NONE, (byte) (mode + 1)));
    }

    private static void nightVision(GameTestHelper helper) {
        HeliEntity heli = spawn(helper, AH64, new Vec3(12.5, 1.0, 12.5), 0.0F);
        Player pilot = rider(helper);
        helper.runAfterDelay(5, () -> {
            heli.interact(pilot, InteractionHand.MAIN_HAND, Vec3.ZERO);
            check(helper, heli.getPilot() == pilot, "the pilot boarded");
            check(helper, !pilot.hasEffect(MobEffects.NIGHT_VISION), "no night vision yet");
            switchCamera(heli, pilot, AircraftCamera.MODE_NIGHTVISION);
            check(helper, heli.getCameraMode(pilot) == AircraftCamera.MODE_NIGHTVISION, "the pilot's camera is in night vision");
        });
        helper.runAfterDelay(8, () -> {
            MobEffectInstance effect = pilot.getEffect(MobEffects.NIGHT_VISION);
            check(helper, effect != null, "night vision is given");
            check(helper, effect.getDuration() <= 250 && effect.isAmbient() && !effect.isVisible(),
                    "a short, ambient effect without particles: " + effect);
        });
        helper.runAfterDelay(40, () -> {
            MobEffectInstance effect = pilot.getEffect(MobEffects.NIGHT_VISION);
            check(helper, effect != null && effect.getDuration() > 240, "kept topped up while in night vision: "
                    + effect);
            switchCamera(heli, pilot, AircraftCamera.MODE_NORMAL);
        });
        helper.runAfterDelay(43, () -> {
            check(helper, heli.getCameraMode(pilot) == AircraftCamera.MODE_NORMAL, "back to normal");
            check(helper, !pilot.hasEffect(MobEffects.NIGHT_VISION), "switching back removes the effect");
            GameTestSupport.cleanUp(heli);
            helper.succeed();
        });
    }

    private static void keepsLongerNightVision(GameTestHelper helper) {
        HeliEntity heli = spawn(helper, AH64, new Vec3(12.5, 1.0, 12.5), 0.0F);
        Player pilot = rider(helper);
        helper.runAfterDelay(5, () -> {
            heli.interact(pilot, InteractionHand.MAIN_HAND, Vec3.ZERO);
            switchCamera(heli, pilot, AircraftCamera.MODE_NIGHTVISION);
        });
        helper.runAfterDelay(10, () -> {
            pilot.addEffect(new MobEffectInstance(MobEffects.NIGHT_VISION, 3600));
            switchCamera(heli, pilot, AircraftCamera.MODE_NORMAL);
        });
        helper.runAfterDelay(14, () -> {
            MobEffectInstance effect = pilot.getEffect(MobEffects.NIGHT_VISION);
            check(helper, effect != null && effect.getDuration() > 3000,
                    "a longer effect from a potion is left alone: " + effect);
            GameTestSupport.cleanUp(heli);
            helper.succeed();
        });
    }

    private static void needsNightVisionContent(GameTestHelper helper) {
        HeliEntity heli = spawn(helper, "bell47g", new Vec3(12.5, 1.0, 12.5), 0.0F);
        Player pilot = rider(helper);
        helper.runAfterDelay(5, () -> {
            heli.interact(pilot, InteractionHand.MAIN_HAND, Vec3.ZERO);
            check(helper, heli.getPilot() == pilot, "the pilot boarded");
            check(helper, !heli.getAcInfo().isEnableNightVision, "the Bell 47G has no night vision");
            switchCamera(heli, pilot, AircraftCamera.MODE_NIGHTVISION);
            check(helper, heli.getCameraMode(pilot) == AircraftCamera.MODE_NORMAL, "the switch is refused");
        });
        helper.runAfterDelay(10, () -> {
            check(helper, !pilot.hasEffect(MobEffects.NIGHT_VISION), "and no effect is given");
            GameTestSupport.cleanUp(heli);
            helper.succeed();
        });
    }

    private static void modesPerSeat(GameTestHelper helper) {
        HeliEntity heli = spawn(helper, "uh-1c", new Vec3(12.5, 1.0, 12.5), 0.0F);
        Player pilot = rider(helper);
        Player crew = rider(helper);
        Player passenger = rider(helper);
        helper.runAfterDelay(5, () -> {
            heli.interact(pilot, InteractionHand.MAIN_HAND, Vec3.ZERO);
            heli.interact(crew, InteractionHand.MAIN_HAND, Vec3.ZERO);
            heli.interact(passenger, InteractionHand.MAIN_HAND, Vec3.ZERO);
            check(helper, heli.getSeatIdByEntity(crew) == 1 && heli.getSeatIdByEntity(passenger) == 2,
                    "crew in seat 1, passenger in seat 2");
            switchCamera(heli, crew, AircraftCamera.MODE_NIGHTVISION);
            check(helper, heli.getCameraMode(crew) == AircraftCamera.MODE_NIGHTVISION, "the first crew seat has a camera");
            check(helper, heli.getCameraMode(pilot) == AircraftCamera.MODE_NORMAL, "the crew's switch leaves the pilot's camera");
            switchCamera(heli, passenger, AircraftCamera.MODE_NIGHTVISION);
            check(helper, heli.getCameraMode(passenger) == AircraftCamera.MODE_NORMAL,
                    "seats past the first crew seat have no camera, as upstream");
            check(helper, heli.getCameraMode(pilot) == AircraftCamera.MODE_NORMAL, "nor can they switch the pilot's");
            GameTestSupport.cleanUp(heli);
            helper.succeed();
        });
    }

    private static void destroyedResets(GameTestHelper helper) {
        HeliEntity heli = spawn(helper, AH64, new Vec3(12.5, 1.0, 12.5), 0.0F);
        Player pilot = rider(helper);
        helper.runAfterDelay(5, () -> {
            heli.interact(pilot, InteractionHand.MAIN_HAND, Vec3.ZERO);
            switchCamera(heli, pilot, AircraftCamera.MODE_NIGHTVISION);
            check(helper, heli.getCameraMode(pilot) == AircraftCamera.MODE_NIGHTVISION, "night vision on");
            heli.setDamageTaken(heli.getMaxHP());
            heli.destroyAircraft();
            check(helper, heli.getCameraMode(pilot) == AircraftCamera.MODE_NORMAL,
                    "destruction puts the crew's cameras back to normal, as upstream");
            GameTestSupport.cleanUp(heli);
            helper.succeed();
        });
    }
}
