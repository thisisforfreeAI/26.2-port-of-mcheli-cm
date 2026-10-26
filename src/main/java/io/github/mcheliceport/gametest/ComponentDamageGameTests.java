package io.github.mcheliceport.gametest;

import static io.github.mcheliceport.gametest.GameTestSupport.check;

import io.github.mcheliceport.content.ContentType;
import io.github.mcheliceport.damage.ComponentHealth;
import io.github.mcheliceport.damage.ComponentKind;
import io.github.mcheliceport.damage.ComponentState;
import io.github.mcheliceport.damage.ComponentView;
import io.github.mcheliceport.damage.VehicleComponents;
import io.github.mcheliceport.effect.VehicleImpactLook;
import io.github.mcheliceport.entity.aircraft.AircraftEntity;
import io.github.mcheliceport.entity.weapon.BulletEntity;
import io.github.mcheliceport.item.AircraftItem;
import io.github.mcheliceport.registry.ModEntityTypes;
import io.github.mcheliceport.registry.ModItems;
import java.util.List;
import net.minecraft.core.registries.Registries;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.util.ProblemReporter;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.damagesource.DamageTypes;
import net.minecraft.world.level.storage.TagValueInput;
import net.minecraft.world.level.storage.TagValueOutput;
import net.minecraft.world.phys.Vec3;
import org.joml.Vector3f;

/** M9 component damage on a stock AH-64: hits pick the right system, repairs and saves keep it. */
final class ComponentDamageGameTests {
    private ComponentDamageGameTests() { }

    static void register() {
        ModGameTests.test("component_damage_hits_repair_save", 30, ComponentDamageGameTests::ah64);
    }

    private static AircraftEntity spawn(GameTestHelper h) {
        var item = (AircraftItem) ModItems.contentItems().stream()
                .filter(c -> c.type() == ContentType.HELICOPTER && c.contentName().equals("ah-64"))
                .findFirst().orElseThrow().item().get();
        Vec3 p = h.absoluteVec(new Vec3(12, 2, 12));
        AircraftEntity heli = item.createAircraft(h.getLevel(), p.x, p.y, p.z);
        h.getLevel().addFreshEntity(heli);
        heli.tick();
        return heli;
    }

    private static Vec3 world(AircraftEntity ac, Vec3 body) {
        Vector3f v = VehicleImpactLook.body(ac.getYaw(), ac.getPitch(), ac.getRoll())
                .transformPosition(new Vector3f((float) body.x, (float) body.y, (float) body.z));
        return ac.position().add(v.x, v.y, v.z);
    }

    private static ComponentView find(List<ComponentView> views, ComponentKind kind) {
        return views.stream().filter(v -> v.kind() == kind).findFirst().orElseThrow();
    }

    private static void ah64(GameTestHelper h) {
        AircraftEntity heli = spawn(h);
        List<ComponentView> views = VehicleComponents.of(heli);
        check(h, views.size() == 5 && views.stream().allMatch(v -> v.state() == ComponentState.OK),
                "a new AH-64 has five intact components: " + views);

        // A round from the outer side of the first engine: it enters that engine before the second.
        Vec3 target = world(heli, views.get(0).center());
        Vec3 outward = target.subtract(world(heli, views.get(1).center())).normalize();
        BulletEntity round = new BulletEntity(ModEntityTypes.BULLET.get(), h.getLevel());
        round.setPos(target.add(outward.scale(6.0)));
        round.setDeltaMovement(outward.scale(-3.0));
        int hp = heli.getDamageTaken();
        heli.hurtServer(h.getLevel(), h.getLevel().damageSources().thrown(round, null), 20.0F);
        check(h, heli.getDamageTaken() > hp, "the hull still takes the hit");
        views = VehicleComponents.of(heli);
        check(h, views.get(0).kind() == ComponentKind.ENGINE && views.get(0).health() < 1.0F
                && views.get(1).health() == 1.0F, "only the engine the round entered first is damaged: " + views);
        check(h, find(views, ComponentKind.TAIL_ROTOR).health() == 1.0F
                && find(views, ComponentKind.MAIN_ROTOR).health() == 1.0F, "the rotors are untouched");
        check(h, views.getFirst().lastChangeTick() >= 0, "the change is stamped");

        // A blast at the tail rotor.
        heli.timeSinceHit = 0;
        ComponentView tail = find(views, ComponentKind.TAIL_ROTOR);
        var explosion = h.getLevel().registryAccess().lookupOrThrow(Registries.DAMAGE_TYPE).getOrThrow(DamageTypes.EXPLOSION);
        heli.hurtServer(h.getLevel(), new DamageSource(explosion, world(heli, tail.center())), 60.0F);
        views = VehicleComponents.of(heli);
        check(h, find(views, ComponentKind.TAIL_ROTOR).health() < 0.5F, "a blast at the tail damages the tail rotor: " + views);
        check(h, find(views, ComponentKind.ENGINE).health() < 1.0F, "the engine stays damaged");

        // Saved and loaded.
        var saved = TagValueOutput.createWithContext(ProblemReporter.DISCARDING, h.getLevel().registryAccess());
        heli.save(saved);
        long packed = heli.getComponentHealth();
        check(h, saved.buildResult().getLong("AcComponents").orElse(0L) == packed, "component health is saved");
        AircraftEntity loaded = spawn(h);
        loaded.load(TagValueInput.create(ProblemReporter.DISCARDING, h.getLevel().registryAccess(), saved.buildResult()));
        check(h, loaded.getComponentHealth() == packed, "and restored");

        // A partial repair heals the worst component first; a full repair heals all.
        heli.repair(10);
        check(h, heli.getComponentHealth() != packed, "a repair heals components");
        heli.repair(heli.getMaxHP());
        check(h, heli.getComponentHealth() == ComponentHealth.FULL, "a full repair restores every component");

        GameTestSupport.cleanUp(heli);
        GameTestSupport.cleanUp(loaded);
        h.succeed();
    }
}
