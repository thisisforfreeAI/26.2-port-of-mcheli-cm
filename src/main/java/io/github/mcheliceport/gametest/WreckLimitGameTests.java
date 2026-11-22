package io.github.mcheliceport.gametest;

import static io.github.mcheliceport.gametest.GameTestSupport.check;
import static io.github.mcheliceport.gametest.GameTestSupport.cleanUp;
import static io.github.mcheliceport.gametest.GameTestSupport.spawn;
import static io.github.mcheliceport.gametest.GameTestSupport.withConfig;

import io.github.mcheliceport.config.CommonConfig;
import io.github.mcheliceport.entity.heli.HeliEntity;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.phys.Vec3;

/** {@code wrecks.maxWrecks} and {@code wrecks.maxWrecksPerArea}: the oldest wrecks go first. */
final class WreckLimitGameTests {

    private WreckLimitGameTests() {
    }

    static void register() {
        // Alone: the caps count every wreck in the level, so other tests' wrecks would count too.
        ModGameTests.testAlone("wreck_limit_removes_oldest", 20, WreckLimitGameTests::caps);
    }

    private static HeliEntity wreck(GameTestHelper h, double x, double z) {
        HeliEntity heli = spawn(h, "uh-1c", new Vec3(x, 1.0, z), 0.0F);
        heli.setDamageTaken(heli.getMaxHP());
        heli.destroyAircraft();
        return heli;
    }

    private static void caps(GameTestHelper h) {
        withConfig(CommonConfig.WRECK_AREA_RADIUS, 6, () ->
        withConfig(CommonConfig.MAX_WRECKS, 0, () ->
        withConfig(CommonConfig.MAX_WRECKS_PER_AREA, 2, () -> {
            HeliEntity far = wreck(h, 14.5, 14.5);
            HeliEntity a = wreck(h, 1.5, 1.5);
            HeliEntity b = wreck(h, 3.5, 1.5);
            check(h, !a.isRemoved() && !b.isRemoved() && !far.isRemoved(), "two nearby wrecks are under the area cap");
            HeliEntity c = wreck(h, 1.5, 3.5);
            check(h, a.isRemoved() && !b.isRemoved() && !c.isRemoved(), "the area cap removes the oldest nearby wreck");
            check(h, !far.isRemoved(), "the area cap leaves wrecks outside the radius alone");
            withConfig(CommonConfig.MAX_WRECKS, 3, () -> {
                HeliEntity d = wreck(h, 8.5, 14.5);
                check(h, far.isRemoved() && !b.isRemoved() && !c.isRemoved() && !d.isRemoved(),
                        "the server-wide cap removes the oldest wreck anywhere");
                cleanUp(d);
            });
            cleanUp(b);
            cleanUp(c);
        })));
        h.succeed();
    }
}
