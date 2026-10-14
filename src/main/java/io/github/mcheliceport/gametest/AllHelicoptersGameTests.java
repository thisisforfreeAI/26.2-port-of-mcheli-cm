package io.github.mcheliceport.gametest;

import static io.github.mcheliceport.gametest.GameTestSupport.check;

import io.github.mcheliceport.content.ContentLocation;
import io.github.mcheliceport.content.ContentType;
import io.github.mcheliceport.content.aircraft.HeliInfo;
import io.github.mcheliceport.entity.PlaceholderEntity;
import io.github.mcheliceport.entity.aircraft.AircraftHitBox;
import io.github.mcheliceport.entity.heli.HeliEntity;
import io.github.mcheliceport.entity.seat.SeatEntity;
import io.github.mcheliceport.item.AircraftItem;
import io.github.mcheliceport.registry.ModEntityTypes;
import io.github.mcheliceport.registry.ModItems;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.phys.Vec3;

/**
 * Exit criterion E4 (planned as a JUnit test, run here because creating entities needs a level):
 * every stock helicopter's item creates a helicopter with its content's values, seats, rotors and
 * hit boxes.
 */
final class AllHelicoptersGameTests {

    private static final int STOCK_HELICOPTERS = 34;

    private AllHelicoptersGameTests() {
    }

    static void register() {
        ModGameTests.test("all_helicopters_spawn", 20, AllHelicoptersGameTests::allSpawn);
    }

    private static void allSpawn(GameTestHelper helper) {
        check(helper, ModEntityTypes.HELICOPTER.get().create(helper.getLevel(), EntitySpawnReason.COMMAND)
                instanceof HeliEntity, "the helicopter entity type is real");
        check(helper, ModEntityTypes.SEAT.get().create(helper.getLevel(), EntitySpawnReason.COMMAND)
                instanceof SeatEntity, "the seat entity type is real");
        check(helper, ModEntityTypes.PLANE.get().create(helper.getLevel(), EntitySpawnReason.COMMAND)
                instanceof PlaceholderEntity, "planes stay placeholders until M6");

        List<ModItems.ContentItem> items = ModItems.contentItems().stream()
                .filter(i -> i.type() == ContentType.HELICOPTER).toList();
        List<HeliEntity> helis = new ArrayList<>();
        int stock = 0;
        for (ModItems.ContentItem contentItem : items) {
            AircraftItem item = (AircraftItem) contentItem.item().get();
            HeliInfo info = (HeliInfo) item.getAircraftInfo();
            check(helper, info != null, contentItem.contentName() + " has content");
            if (!ContentLocation.BUILTIN_DOMAIN.equals(info.getLocation().addonDomain())) {
                continue;
            }
            stock++;
            Vec3 pos = helper.absoluteVec(new Vec3(12.5, 20.0, 12.5));
            HeliEntity heli = (HeliEntity) item.createAircraft(helper.getLevel(), pos.x, pos.y, pos.z);
            check(helper, heli != null, contentItem.contentName() + " creates a helicopter");
            heli.setNoGravity(true);
            helper.getLevel().addFreshEntity(heli);
            helis.add(heli);
        }
        int stockCount = stock;
        helper.runAfterDelay(2, () -> {
            check(helper, stockCount == STOCK_HELICOPTERS, "34 stock helicopters, got " + stockCount);
            for (HeliEntity heli : helis) {
                HeliInfo info = heli.getHeliInfo();
                String name = heli.getAircraftTypeName();
                check(helper, info != null && info.name.equals(name), name + ": type name");
                check(helper, heli.getMaxHP() == info.maxHp, name + ": MaxHP " + heli.getMaxHP());
                check(helper, heli.getMaxFuel() == info.maxFuel, name + ": MaxFuel " + heli.getMaxFuel());
                long seats = heli.getPassengers().stream().filter(p -> p instanceof SeatEntity).count();
                check(helper, seats == heli.getSeatNum() && heli.getSeats().length == heli.getSeatNum(),
                        name + ": " + seats + " seats for " + heli.getSeatNum());
                check(helper, heli.getRotors().length == info.rotorList.size(), name + ": rotors");
                AircraftHitBox[] boxes = heli.getHitBoxes();
                check(helper, boxes.length == info.extraBoundingBox.size(), name + ": hit boxes " + boxes.length);
                for (AircraftHitBox box : boxes) {
                    check(helper, box.getInfo() != null && box.getBoundingBox().getSize() > 0.0, name
                            + ": hit box placed");
                }
                GameTestSupport.cleanUp(heli);
            }
            helper.succeed();
        });
    }
}
