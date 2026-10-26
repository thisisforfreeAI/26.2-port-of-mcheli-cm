package io.github.mcheliceport.gametest;

import static io.github.mcheliceport.gametest.GameTestSupport.check;

import io.github.mcheliceport.combat.LagCompensation;
import io.github.mcheliceport.content.ContentType;
import io.github.mcheliceport.entity.aircraft.AircraftEntity;
import io.github.mcheliceport.entity.aircraft.AircraftHitBox;
import io.github.mcheliceport.entity.weapon.BulletEntity;
import io.github.mcheliceport.entity.weapon.RocketEntity;
import io.github.mcheliceport.item.AircraftItem;
import io.github.mcheliceport.registry.ModEntityTypes;
import io.github.mcheliceport.registry.ModItems;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.Vec3;

/** M9 lag compensation: a lagging shooter's rounds hit a fast vehicle where that shooter saw it. */
final class LagCompensationGameTests {
    private LagCompensationGameTests() { }

    static void register() {
        ModGameTests.test("lag_compensation_rewinds_vehicle_hits", 30, LagCompensationGameTests::rewind);
        ModGameTests.test("lag_compensation_missiles_stop_rewinding", 30, LagCompensationGameTests::missiles);
    }

    private static void rewind(GameTestHelper h) {
        var item = (AircraftItem) ModItems.contentItems().stream()
                .filter(c -> c.type() == ContentType.HELICOPTER && c.contentName().equals("ah-64"))
                .findFirst().orElseThrow().item().get();
        Vec3 p = h.absoluteVec(new Vec3(12, 4, 12));
        AircraftEntity heli = item.createAircraft(h.getLevel(), p.x, p.y, p.z);
        h.getLevel().addFreshEntity(heli);
        heli.setNoGravity(true);
        heli.tick();
        // The heli flew +x at 2 blocks a tick and is now at p: five ticks ago it was 10 blocks back.
        long now = h.getLevel().getGameTime();
        for (int k = 10; k >= 0; k--) {
            LagCompensation.recordAt(heli, now - k, p.x - 2.0 * k, p.y, p.z);
        }
        Vec3 center = heli.getBoundingBox().getCenter();
        Vec3 from = center.add(-10.0, 0.0, -8.0);
        Vec3 to = center.add(-10.0, 0.0, 8.0);

        Probe current = new Probe(h, 0);
        check(h, current.trace(from, to) == null, "without rewind the shot misses where the heli was");
        Probe lagging = new Probe(h, 5);
        Entity hit = lagging.trace(from, to);
        check(h, hit == heli || hit instanceof AircraftHitBox box && box.getParent() == heli,
                "a 5-tick rewind hits the heli where the shooter saw it: " + hit);
        Probe tooLate = new Probe(h, 2);
        check(h, tooLate.trace(from, to) == null, "a shorter rewind does not stretch the heli's box");
        check(h, LagCompensation.shift(heli, 5).x < -9.9, "the shift is the recorded displacement");
        GameTestSupport.cleanUp(heli);
        h.succeed();
    }

    private static void missiles(GameTestHelper h) {
        RocketEntity rocket = new RocketEntity(ModEntityTypes.ROCKET.get(), h.getLevel());
        rocket.setRewindTicks(5);
        check(h, rocket.hitRewindTicks() == 5, "a fresh rocket rewinds like the gun");
        rocket.tickCount = 3;
        check(h, rocket.hitRewindTicks() == 2, "the rewind runs down as it flies");
        rocket.tickCount = 9;
        check(h, rocket.hitRewindTicks() == 0, "after the rewind window it tests current positions");
        Probe bullet = new Probe(h, 5);
        bullet.tickCount = 40;
        check(h, bullet.hitRewindTicks() == 5, "gun rounds keep the rewind for their whole flight");
        h.succeed();
    }

    private static final class Probe extends BulletEntity {
        Probe(GameTestHelper h, int rewind) {
            super(ModEntityTypes.BULLET.get(), h.getLevel());
            this.setRewindTicks(rewind);
        }

        Entity trace(Vec3 from, Vec3 to) {
            Vec3 delta = to.subtract(from);
            this.setPos(from);
            return this.findEntityOnPath(from, to, delta.x, delta.y, delta.z);
        }
    }
}
