package io.github.mcheliceport.gametest;

import static io.github.mcheliceport.gametest.GameTestSupport.check;

import io.github.mcheliceport.compat.hbm.NtmPayloads;
import io.github.mcheliceport.content.ContentLocation;
import io.github.mcheliceport.content.ContentRegistries;
import io.github.mcheliceport.content.aircraft.PlaneInfo;
import io.github.mcheliceport.content.weapon.WeaponInfo;
import io.github.mcheliceport.content.weapon.hbm.FireContainer;
import io.github.mcheliceport.entity.weapon.RocketEntity;
import java.util.List;
import java.util.Map;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.monster.zombie.Husk;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

/**
 * NTM payloads without NTM Next installed: the weapon explodes normally, and an effect-only one
 * not at all, as upstream. {@link HbmGameTests} covers the payloads themselves.
 */
final class NtmGameTests {

    private NtmGameTests() {
    }

    /** The port's NTM Next bombs and the stock planes that carry them. */
    static final Map<String, List<String>> PLANE_BOMBS = Map.ofEntries(
            Map.entry("b29", List.of("ntm_little_boy", "ntm_fat_man")),
            Map.entry("b-2a", List.of("ntm_b61", "ntm_b83", "ntm_balefire_bomb")),
            Map.entry("f-15e", List.of("ntm_b61_tactical")),
            Map.entry("f-35a", List.of("ntm_b61_tactical")),
            Map.entry("tornado-ids", List.of("ntm_we177")),
            Map.entry("mig29", List.of("ntm_rn40")),
            Map.entry("su37", List.of("ntm_odab500", "ntm_chlorine_bomb")),
            Map.entry("f4a", List.of("ntm_mk77_napalm")),
            Map.entry("fa18f", List.of("ntm_mk77_napalm")),
            Map.entry("harrier", List.of("ntm_mk77_napalm")),
            Map.entry("a-10", List.of("ntm_wp_bomb")),
            Map.entry("mq-9", List.of("ntm_mini_nuke_bomb")),
            Map.entry("x-47b", List.of("ntm_mini_nuke_bomb")),
            Map.entry("pzl-m18", List.of("ntm_agent_orange")));

    static void register() {
        ModGameTests.test("ntm_bombs_only_with_ntm_next", 20, NtmGameTests::planeBombs);
        if (NtmPayloads.available()) {
            HbmGameTests.register();
        } else {
            ModGameTests.test("ntm_payload_without_mod_explodes_normally", 20, NtmGameTests::withoutMod);
        }
    }

    /** A weapon definition with a fire payload, as an addon's {@code NTM: {PayloadType: FIRE}} gives. */
    static WeaponInfo fireWeapon(String name, boolean effectOnly) {
        WeaponInfo info = new WeaponInfo(ContentLocation.builtin(name), name + ".yml", "yaml");
        info.useHBM = true;
        info.payloadNTM = WeaponInfo.Payload.NTM_FIRE;
        info.effectOnly = effectOnly;
        info.fireContainer = new FireContainer();
        return info;
    }

    /** A rocket that reads as {@code info} and stays put, for calling its explosion directly. */
    static RocketEntity rocket(GameTestHelper h, @Nullable WeaponInfo info, Vec3 relative) {
        Vec3 at = h.absoluteVec(relative);
        RocketEntity rocket = new RocketEntity(h.getLevel(), at.x, at.y, at.z, 0.0, 0.0, 0.0, 0.0F, 0.0F, 0.0) {
            @Override
            public @Nullable WeaponInfo getInfo() {
                return info;
            }

            @Override
            public void tick() {
            }
        };
        h.getLevel().addFreshEntity(rocket);
        return rocket;
    }

    /**
     * Each bomb needs NTM Next and carries a payload; the planes list it as a weapon exactly when
     * NTM Next is installed, after all of their own weapons.
     */
    private static void planeBombs(GameTestHelper h) {
        boolean installed = NtmPayloads.available();
        for (Map.Entry<String, List<String>> entry : PLANE_BOMBS.entrySet()) {
            PlaneInfo plane = ContentRegistries.plane().get(entry.getKey());
            check(h, plane != null, "no plane " + entry.getKey());
            List<String> types = plane.weaponSetList.stream().map(set -> set.type).toList();
            for (String bomb : entry.getValue()) {
                WeaponInfo info = ContentRegistries.weapon().get(bomb);
                check(h, info != null && "hbm".equals(info.requiredMod) && info.useHBM
                        && info.payloadNTM != WeaponInfo.Payload.NONE, bomb + " is not an NTM Next payload weapon");
                check(h, types.contains(bomb) == installed, entry.getKey() + (installed ? " lacks " : " offers ") + bomb);
            }
            if (installed) {
                int first = types.indexOf(entry.getValue().get(0));
                check(h, types.subList(first, types.size()).stream().allMatch(t -> t.startsWith("ntm_")),
                        entry.getKey() + "'s bombs are not after its own weapons: " + types);
            }
        }
        h.succeed();
    }

    private static void withoutMod(GameTestHelper h) {
        Husk normal = h.spawnWithNoFreeWill(EntityTypes.HUSK, new Vec3(4.5, 1, 4.5));
        Husk spared = h.spawnWithNoFreeWill(EntityTypes.HUSK, new Vec3(16.5, 1, 16.5));
        RocketEntity loud = rocket(h, fireWeapon("ntm_fallback", false), new Vec3(4.5, 2, 4.5));
        RocketEntity quiet = rocket(h, fireWeapon("ntm_fallback_effect", true), new Vec3(16.5, 2, 16.5));
        Vec3 a = loud.position();
        Vec3 b = quiet.position();
        loud.newExplosion(a.x, a.y, a.z, 3.0F, 0.0F, false);
        quiet.newExplosion(b.x, b.y, b.z, 3.0F, 0.0F, false);
        check(h, normal.getHealth() < normal.getMaxHealth(), "the payload weapon did not explode normally");
        check(h, spared.getHealth() == spared.getMaxHealth(), "an effect-only payload weapon still exploded");
        loud.discard();
        quiet.discard();
        normal.discard();
        spared.discard();
        h.succeed();
    }
}
