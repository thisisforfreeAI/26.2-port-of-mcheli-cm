package io.github.mcheliceport.gametest;

import static io.github.mcheliceport.gametest.GameTestSupport.check;

import io.github.mcheliceport.content.ContentType;
import io.github.mcheliceport.entity.aircraft.AircraftEntity;
import io.github.mcheliceport.item.AircraftItem;
import io.github.mcheliceport.registry.ModItems;
import io.github.mcheliceport.entity.parachute.ParachuteEntity;
import io.github.mcheliceport.entity.seat.SeatEntity;
import java.util.List;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.animal.cow.Cow;
import net.minecraft.world.level.GameType;
import net.minecraft.world.phys.Vec3;

/** Mobs in seats (upstream {@code mountMobToSeats} and {@code unmountCrew}). */
final class MobSeatGameTests {

    private static final String AN2 = "an2";

    private MobSeatGameTests() {
    }

    static void register() {
        ModGameTests.test("mobs_board_with_pilot", 40, MobSeatGameTests::board);
        ModGameTests.test("mobs_drop_by_parachute", 40, MobSeatGameTests::parachute);
    }

    private static AircraftEntity an2(GameTestHelper h, Vec3 relative) {
        Vec3 pos = h.absoluteVec(relative);
        AircraftItem item = (AircraftItem) ModItems.contentItems().stream()
                .filter(i -> i.type() == ContentType.PLANE && i.contentName().equals(AN2))
                .findFirst().orElseThrow().item().get();
        AircraftEntity plane = item.createAircraft(h.getLevel(), pos.x, pos.y, pos.z);
        check(h, plane != null, "created " + AN2);
        plane.initRotationYaw(0.0F);
        h.getLevel().addFreshEntity(plane);
        return plane;
    }

    private static Cow cow(GameTestHelper h, Vec3 pos) {
        Cow cow = EntityTypes.COW.create(h.getLevel(), EntitySpawnReason.COMMAND);
        cow.setNoAi(true);
        cow.setPos(pos);
        h.getLevel().addFreshEntity(cow);
        return cow;
    }

    /**
     * Mobs next to the vehicle take the free seats when a pilot boards, and get out on the ground
     * when the pilot unloads the crew; a mob further away stays where it is.
     */
    private static void board(GameTestHelper h) {
        AircraftEntity plane = an2(h, new Vec3(8.5, 1, 8.5));
        h.runAfterDelay(2, () -> {
            List<Cow> near = List.of(cow(h, plane.position().add(2, 0, 0)), cow(h, plane.position().add(-2, 0, 0)),
                    cow(h, plane.position().add(0, 0, 2)));
            Cow far = cow(h, plane.position().add(0, 0, 12));
            ServerPlayer pilot = h.makeMockServerPlayerInLevel();
            pilot.setGameMode(GameType.SURVIVAL);
            pilot.setPos(plane.position().add(1, 0, 1));
            try {
                plane.interact(pilot, InteractionHand.MAIN_HAND, Vec3.ZERO);
                check(h, plane.isPilot(pilot), "the player flies the plane");
                for (Cow cow : near) {
                    check(h, cow.getVehicle() instanceof SeatEntity seat && seat.getParent() == plane,
                            "a cow next to the plane took a seat");
                }
                check(h, far.getVehicle() == null, "a cow 12 blocks away stays");

                plane.unmountCrew();
                for (Cow cow : near) {
                    check(h, cow.getVehicle() == null, "the cow got out on the ground");
                }
                check(h, plane.isPilot(pilot), "the pilot stays aboard");
            } finally {
                pilot.stopRiding();
            }
            near.forEach(Entity::discard);
            far.discard();
            GameTestSupport.cleanUp(plane);
            h.succeed();
        });
    }

    /**
     * In the air, the first mob from the third seat on jumps with a parachute, and a mob in the
     * second seat just gets out, as upstream.
     */
    private static void parachute(GameTestHelper h) {
        AircraftEntity plane = an2(h, new Vec3(8.5, 40, 8.5));
        h.runAfterDelay(2, () -> {
            SeatEntity[] seats = plane.getSeats();
            Cow second = cow(h, plane.position());
            Cow third = cow(h, plane.position());
            Cow fourth = cow(h, plane.position());
            check(h, second.startRiding(seats[0], true, true) && third.startRiding(seats[1], true, true)
                    && fourth.startRiding(seats[2], true, true), "the cows sit in seats 2 to 4");

            check(h, plane.unmountCrew(true), "a rider left");
            check(h, second.getVehicle() == null, "the cow in the second seat got out");
            check(h, third.getVehicle() instanceof ParachuteEntity, "the cow in the third seat jumped");
            check(h, fourth.getVehicle() == seats[2], "one jump at a time");
            Entity chute = third.getVehicle();
            third.stopRiding();
            if (chute != null) {
                chute.discard();
            }
            fourth.stopRiding();
            List.of(second, third, fourth).forEach(Entity::discard);
            GameTestSupport.cleanUp(plane);
            h.succeed();
        });
    }
}
