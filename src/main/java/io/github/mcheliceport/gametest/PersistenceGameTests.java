package io.github.mcheliceport.gametest;

import static io.github.mcheliceport.gametest.GameTestSupport.AH64;
import static io.github.mcheliceport.gametest.GameTestSupport.check;
import static io.github.mcheliceport.gametest.GameTestSupport.spawn;

import io.github.mcheliceport.entity.heli.HeliEntity;
import io.github.mcheliceport.entity.seat.SeatEntity;
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
 * Exit criterion E8: a helicopter's state survives being saved and loaded, through the same
 * save and load calls a chunk uses (with its seats as passengers).
 */
final class PersistenceGameTests {

    private PersistenceGameTests() {
    }

    static void register() {
        ModGameTests.test("persistence_save_and_load", 40, PersistenceGameTests::saveAndLoad);
    }

    /** Saves the helicopter with its passengers and loads a copy, as a chunk would. */
    private static HeliEntity roundTrip(GameTestHelper helper, HeliEntity heli) {
        TagValueOutput output = TagValueOutput.createWithContext(ProblemReporter.DISCARDING,
                helper.getLevel().registryAccess());
        check(helper, heli.save(output), "saved");
        CompoundTag tag = output.buildResult();
        Entity loaded = EntityType.loadEntityRecursive(TagValueInput.create(ProblemReporter.DISCARDING,
                helper.getLevel().registryAccess(), tag), helper.getLevel(), EntitySpawnReason.LOAD, e -> e);
        check(helper, loaded instanceof HeliEntity, "loaded a helicopter");
        return (HeliEntity) loaded;
    }

    private static void discard(HeliEntity copy) {
        copy.getPassengers().forEach(Entity::discard);
        copy.discard();
    }

    private static void saveAndLoad(GameTestHelper helper) {
        HeliEntity heli = spawn(helper, AH64, new Vec3(12.5, 1.0, 12.5), 0.0F);
        helper.runAfterDelay(5, () -> {
            heli.setFuel(345);
            heli.setDamageTaken(20);
            heli.switchHoveringMode(true);
            check(helper, heli.isHoveringMode(), "hovering mode on");
            heli.setCurrentThrottle(0.6);
            heli.setRoll(12.5F);
            int seats = heli.getPassengers().size();
            check(helper, seats == heli.getSeatNum(), "the seats ride the helicopter: " + seats);

            HeliEntity copy = roundTrip(helper, heli);
            check(helper, copy.getAircraftTypeName().equals(AH64), "type " + copy.getAircraftTypeName());
            check(helper, copy.getFuel() == 345, "fuel " + copy.getFuel());
            check(helper, copy.getDamageTaken() == 20, "damage " + copy.getDamageTaken());
            check(helper, copy.isHoveringMode(), "hovering mode");
            check(helper, Math.abs(copy.getCurrentThrottle() - 0.6) < 1.0E-6, "throttle " + copy.getCurrentThrottle());
            check(helper, copy.getRoll() == 12.5F, "roll " + copy.getRoll());
            check(helper, copy.getPassengers().size() == seats
                    && copy.getPassengers().stream().allMatch(p -> p instanceof SeatEntity),
                    "the seats came back as passengers: " + copy.getPassengers().size());
            discard(copy);

            // Folded blades load folded, with the engine off (upstream's forceFoldBlade).
            heli.switchHoveringMode(false);
            heli.setCurrentThrottle(0.0);
            heli.setFoldBladeStat(HeliEntity.BLADES_FOLDED);
            HeliEntity folded = roundTrip(helper, heli);
            check(helper, folded.getFoldBladeStat() == HeliEntity.BLADES_FOLDED && folded.isFoldBlades(),
                    "folded blades " + folded.getFoldBladeStat());
            check(helper, !folded.canUseBlades(), "and cannot fly until unfolded");
            discard(folded);
            GameTestSupport.cleanUp(heli);
            helper.succeed();
        });
    }
}
