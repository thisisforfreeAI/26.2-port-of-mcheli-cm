package io.github.mcheliceport.gametest;

import static io.github.mcheliceport.gametest.GameTestSupport.check;

import io.github.mcheliceport.content.ContentLocation;
import io.github.mcheliceport.content.aircraft.AircraftInfo;
import io.github.mcheliceport.content.aircraft.HeliInfo;
import io.github.mcheliceport.content.aircraft.SeatInfo;
import io.github.mcheliceport.entity.heli.HeliEntity;
import io.github.mcheliceport.net.HeliControlPayload;
import io.github.mcheliceport.net.ModNetwork;
import io.github.mcheliceport.net.PlayerControlSwitches;
import io.github.mcheliceport.registry.ModEntityTypes;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.util.ProblemReporter;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.storage.TagValueInput;
import net.minecraft.world.level.storage.TagValueOutput;
import net.minecraft.world.phys.Vec3;

final class SearchLightGameTests {
    private SearchLightGameTests() { }
    static void register() {
        ModGameTests.test("searchlight_packet_seat_authority", 30, SearchLightGameTests::authority);
        ModGameTests.test("searchlight_aim_priority_hatches_and_retention", 30, SearchLightGameTests::aim);
        ModGameTests.test("searchlight_saved_state_and_destroyed_gates", 30, SearchLightGameTests::persistence);
    }
    private static HeliEntity fixture(GameTestHelper h) {
        var definition = new HeliInfo(ContentLocation.builtin("light_fixture"), "light_fixture.yml", "yaml");
        definition.maxHp = 1000;
        for (int i = 0; i < 3; i++) { definition.seatList.add(new SeatInfo(new Vec3(0, 1, -i), false)); }
        definition.searchLights.add(new AircraftInfo.SearchLight(Vec3.ZERO, 0x50FFFFFF, 0x10FFFFC0,
                60, 20, false, 0, 0, false, 0));
        var ac = new HeliEntity(ModEntityTypes.HELICOPTER.get(), h.getLevel()) {
            @Override protected AircraftInfo lookupInfo(String name) { return definition; }
            @Override protected void onUpdateAircraft() { }
        };
        ac.setAircraftTypeName("light_fixture"); ac.setPos(h.absoluteVec(new Vec3(12, 10, 12)));
        h.getLevel().addFreshEntity(ac); ac.tick(); return ac;
    }
    private static Player rider(GameTestHelper h, HeliEntity ac, int seat) {
        var p = GameTestSupport.player(h, GameType.SURVIVAL, new Vec3(12, 10, 12));
        check(h, p.startRiding(seat == 0 ? ac : ac.getSeats()[seat - 1], true, true), "board seat " + seat);
        ac.tick(); return p;
    }
    private static void toggle(HeliEntity ac, Player rider, int id) {
        ModNetwork.applyHeliControl(ac, rider, HeliControlPayload.switchesOnly(id,
                PlayerControlSwitches.NONE.with(PlayerControlSwitches.SWITCH_SEARCH_LIGHT, true)));
    }
    private static void authority(GameTestHelper h) {
        var ac = fixture(h); var pilot = rider(h, ac, 0); var gunner = rider(h, ac, 1); var crew = rider(h, ac, 2);
        var outsider = GameTestSupport.player(h, GameType.SURVIVAL, new Vec3(12, 10, 12));
        toggle(ac, outsider, ac.getId()); check(h, !ac.isSearchLightOn(), "outsider cannot toggle");
        toggle(ac, crew, ac.getId()); check(h, !ac.isSearchLightOn(), "later crew cannot toggle");
        toggle(ac, pilot, ac.getId() + 999); check(h, !ac.isSearchLightOn(), "wrong entity ID rejected");
        toggle(ac, pilot, ac.getId()); check(h, ac.isSearchLightOn(), "pilot switches on");
        toggle(ac, gunner, ac.getId()); check(h, !ac.isSearchLightOn(), "first gunner switches off");
        check(h, !ac.canSwitchSearchLight(outsider), "negative seat ID is not authorized");
        ac.getAcInfo().searchLights.clear(); toggle(ac, pilot, ac.getId());
        check(h, !ac.isSearchLightOn(), "definitions without lights do not enable the bit");
        pilot.discard(); gunner.discard(); crew.discard(); outsider.discard(); ac.discard(); h.succeed();
    }
    private static void aim(GameTestHelper h) {
        var ac = fixture(h); var pilot = rider(h, ac, 0); pilot.setYRot(35); pilot.setXRot(20);
        ac.setSearchLight(true); ac.tick();
        check(h, ac.searchLightYaw(1) == 35 && ac.searchLightPitch(1) == 20, "pilot aims without first gunner");
        check(h, ac.lightHatchRotation == 0.5F && ac.previousLightHatchRotation == 0, "hatch opens halfway in first tick");
        var gunner = rider(h, ac, 1); gunner.setYRot(-70); gunner.setXRot(45); ac.tick();
        check(h, ac.searchLightYaw(1) == -70 && ac.searchLightPitch(1) == 45, "first gunner overrides pilot aim");
        check(h, ac.lightHatchRotation == 1, "hatch reaches full extension in two ticks");
        pilot.stopRiding(); gunner.stopRiding(); ac.tick();
        check(h, ac.searchLightYaw(1) == -70 && ac.searchLightPitch(1) == 45, "unoccupied vehicle retains synchronized aim");
        ac.setSearchLight(false); ac.tick(); ac.tick();
        check(h, ac.lightHatchRotation == 0, "switching off closes the cover");
        pilot.discard(); gunner.discard(); ac.discard(); h.succeed();
    }
    private static void persistence(GameTestHelper h) {
        var ac = fixture(h); var pilot = rider(h, ac, 0); pilot.setYRot(77); pilot.setXRot(30);
        ac.setSearchLight(true); ac.tick(); pilot.stopRiding();
        var output = TagValueOutput.createWithContext(ProblemReporter.DISCARDING, h.getLevel().registryAccess());
        ac.saveWithoutId(output);
        var restored = fixture(h);
        restored.load(TagValueInput.create(ProblemReporter.DISCARDING, h.getLevel().registryAccess(), output.buildResult()));
        restored.tick();
        check(h, restored.isSearchLightOn() && restored.searchLightYaw(1) == 77 && restored.searchLightPitch(1) == 30,
                "switch and last aim survive save/load");
        restored.destroyAircraft(); restored.tick(); restored.setSearchLight(true);
        check(h, !restored.isSearchLightOn() && !restored.canSwitchSearchLight(pilot), "destroyed vehicles cannot emit light");
        pilot.discard(); ac.discard(); restored.discard(); h.succeed();
    }
}
