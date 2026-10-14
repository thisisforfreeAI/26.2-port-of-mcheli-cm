package io.github.mcheliceport.weapon;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.github.mcheliceport.content.ContentRegistries;
import io.github.mcheliceport.content.ContentTestSupport;
import io.github.mcheliceport.content.weapon.WeaponInfo;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.world.phys.Vec3;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

/**
 * Exit criterion E2: a weapon set's ammunition, reload, heat and cooldown follow upstream tick for
 * tick. The port's {@link WeaponSet} and {@link UpstreamWeaponSetOracle} run the same scripted
 * trigger, reload, switch and shared-reload sequence for stock weapons and must agree every tick.
 */
class WeaponSetTest {

    private static ContentRegistries.Snapshot content;

    @BeforeAll
    static void load() {
        content = ContentTestSupport.load(ContentTestSupport.stockPack());
    }

    /** A server-side mount that always fires, as upstream's mounts do once their checks pass. */
    private static final class AlwaysFires extends WeaponBase {
        AlwaysFires(WeaponInfo info) {
            super(null, Vec3.ZERO, 0.0F, 0.0F, info.name, info);
        }

        @Override
        public boolean shot(WeaponParam prm) {
            return true;
        }
    }

    private enum Action { FIRE, IDLE, RELOAD, SWITCH, OTHER }

    /** Hold the trigger, pause, reload by hand, switch away and back, and reload with another set. */
    private static List<Action> script() {
        List<Action> s = new ArrayList<>();
        add(s, Action.FIRE, 80);
        add(s, Action.IDLE, 40);
        add(s, Action.RELOAD, 1);
        add(s, Action.FIRE, 260);
        add(s, Action.SWITCH, 1);
        add(s, Action.FIRE, 60);
        add(s, Action.OTHER, 1);
        add(s, Action.IDLE, 150);
        add(s, Action.FIRE, 400);
        add(s, Action.IDLE, 200);
        return s;
    }

    private static void add(List<Action> s, Action a, int n) {
        for (int i = 0; i < n; i++) {
            s.add(a);
        }
    }

    @ParameterizedTest
    @ValueSource(strings = {"m230", "hydra70", "agm114", "127mmgun", "2a42"})
    void matchesUpstream(String name) {
        for (boolean infinity : new boolean[] {false, true}) {
            for (int mounts : new int[] {1, 2}) {
                run(name, infinity, mounts);
            }
        }
    }

    private static void run(String name, boolean infinity, int mounts) {
        WeaponInfo info = content.weapon().get(name);
        assertNotNull(info, name);
        WeaponBase[] weapons = new WeaponBase[mounts];
        for (int i = 0; i < mounts; i++) {
            AlwaysFires w = new AlwaysFires(info);
            // WeaponCreator's server-side values.
            w.interval = info.delay;
            w.heatCount = info.maxHeatCount > 0 && info.heatCount < 2 ? 2 : info.heatCount;
            w.delayedInterval = WeaponCreator.adjustInterval(info.delay);
            weapons[i] = w;
        }
        WeaponSet port = new WeaponSet(weapons);
        UpstreamWeaponSetOracle up = new UpstreamWeaponSetOracle(info.round, info.maxAmmo, info.reloadTime,
                info.maxHeatCount, info.soundDelay, weapons[0].interval, weapons[0].delayedInterval,
                weapons[0].heatCount, mounts);
        port.setAmmo(info.round);
        port.setReserveAmmo(info.maxAmmo);
        up.ammo = info.round;
        up.setReserveAmmo(info.maxAmmo);

        int tick = 0;
        int shots = 0;
        for (Action a : script()) {
            String at = name + " infinity=" + infinity + " mounts=" + mounts + " tick " + tick + " " + a;
            switch (a) {
                case FIRE -> {
                    if (up.canFire()) {
                        WeaponParam prm = new WeaponParam();
                        prm.isInfinity = infinity;
                        boolean fired = port.use(prm);
                        assertEquals(up.use(infinity), fired, at);
                        if (fired) {
                            shots++;
                        }
                    }
                }
                case RELOAD -> assertEquals(up.manualReload(), port.manualReload(), at);
                case SWITCH -> {
                    up.onSwitchWeapon();
                    port.onSwitchWeapon(false, false);
                }
                case OTHER -> {
                    up.waitAndReloadByOther(true);
                    port.waitAndReloadByOther(true);
                }
                case IDLE -> {
                }
            }
            up.update();
            port.update(null, true, a == Action.FIRE);
            assertEquals(up.ammo, port.getAmmo(), at + " ammo");
            assertEquals(up.ammoReserve, port.getRestAllAmmoNum(), at + " reserve");
            assertEquals(up.cooldown, port.cooldown, at + " cooldown");
            assertEquals(up.reloadCooldown, port.reloadCooldown, at + " reload");
            assertEquals(up.currentHeat, port.getCurrentHeat(), at + " heat");
            assertEquals(up.cooldownSpeed, port.cooldownSpeed, at + " heat cooldown speed");
            assertEquals(up.soundWait, port.soundWait, at + " sound wait");
            assertEquals(up.currentWeaponIndex, port.getCurrentWeaponIndex(), at + " mount");
            assertArrayEquals(up.lastUsedCount, port.lastUsedCount, at + " last used");
            tick++;
        }
        assertTrue(shots > 0, name + " never fired");
    }
}
