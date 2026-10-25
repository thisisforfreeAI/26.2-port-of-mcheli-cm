package io.github.mcheliceport.gametest;

import static io.github.mcheliceport.gametest.GameTestSupport.check;

import io.github.mcheliceport.compat.hbm.NtmPayloads;
import io.github.mcheliceport.content.ContentLocation;
import io.github.mcheliceport.content.weapon.WeaponInfo;
import io.github.mcheliceport.content.weapon.hbm.FireContainer;
import io.github.mcheliceport.entity.weapon.RocketEntity;
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

    static void register() {
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
