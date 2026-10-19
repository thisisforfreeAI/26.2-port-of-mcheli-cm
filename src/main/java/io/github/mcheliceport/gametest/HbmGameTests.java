package io.github.mcheliceport.gametest;

import static io.github.mcheliceport.gametest.GameTestSupport.check;

import io.github.mcheliceport.compat.hbm.HbmCompat;
import io.github.mcheliceport.content.ContentLocation;
import io.github.mcheliceport.content.weapon.WeaponInfo;
import io.github.mcheliceport.content.weapon.hbm.ChemicalContainer;
import io.github.mcheliceport.content.weapon.hbm.FireContainer;
import io.github.mcheliceport.content.weapon.hbm.MistContainer;
import io.github.mcheliceport.content.weapon.hbm.MukeContainer;
import io.github.mcheliceport.content.weapon.hbm.VNTSettingContainer;
import io.github.mcheliceport.entity.weapon.RocketEntity;
import io.github.mcheliceport.registry.ModEntityTypes;
import java.util.Map;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.monster.zombie.Husk;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.neoforged.fml.ModList;

/** Default runs cover absent-HBM fallback; -PwithHbm also exercises NTM Next's real runtime APIs. */
final class HbmGameTests {
    private HbmGameTests() {}

    static void register() {
        ModGameTests.test("hbm_normal_blast_and_effect_only", 20, HbmGameTests::normalBlast);
        if (ModList.get().isLoaded("hbm")) {
            ModGameTests.test("hbm_next_payloads", 20, HbmGameTests::payloads);
            ModGameTests.test("hbm_next_chemical_payloads", 20, HbmGameTests::chemicalPayloads);
        }
    }

    private static WeaponInfo info(WeaponInfo.Payload payload) {
        WeaponInfo info = new WeaponInfo(ContentLocation.builtin("hbm_test"), "hbm_test.yml", "yaml");
        info.useHBM = true;
        info.payloadNTM = payload;
        return info;
    }

    private static RocketEntity projectile(GameTestHelper h, WeaponInfo info, Vec3 pos) {
        RocketEntity rocket = new RocketEntity(ModEntityTypes.ROCKET.get(), h.getLevel()) {
            @Override public WeaponInfo getInfo() { return info; }
            @Override public WeaponInfo getWeaponInfo() { return info; }
        };
        rocket.setPos(pos);
        return rocket;
    }

    private static void normalBlast(GameTestHelper h) {
        Vec3 pos = h.absoluteVec(new Vec3(12, 8, 12));
        boolean installed = ModList.get().isLoaded("hbm");
        for (WeaponInfo.Payload payload : WeaponInfo.Payload.values()) {
            if (payload == WeaponInfo.Payload.NONE) continue;
            if (installed && payload != WeaponInfo.Payload.NTM_NT && payload != WeaponInfo.Payload.NTM_CHEMICAL)
                continue;
            WeaponInfo info = info(payload);
            RocketEntity rocket = projectile(h, info, pos);
            // No payload settings: a loaded but unavailable API must still use normal McHeli damage.
            // Mist has no damaging effect-only path, even with the optional mod loaded.
            Husk target = EntityTypes.HUSK.create(h.getLevel(), EntitySpawnReason.COMMAND);
            target.setPos(pos.add(1, 0, 0));
            target.setNoAi(true);
            h.getLevel().addFreshEntity(target);
            float before = target.getHealth();
            rocket.newExplosion(pos.x, pos.y, pos.z, 1, 0, false);
            check(h, target.getHealth() < before, payload + " suppressed the normal fallback blast");
            target.discard();

            target = EntityTypes.HUSK.create(h.getLevel(), EntitySpawnReason.COMMAND);
            target.setPos(pos.add(1, 0, 0));
            h.getLevel().addFreshEntity(target);
            before = target.getHealth();
            info.effectOnly = true;
            rocket.newExplosion(pos.x, pos.y, pos.z, 1, 0, 1, 0, true);
            check(h, target.getHealth() == before, payload + " EffectOnly caused fallback damage");
            target.discard();
            if (!installed) check(h, HbmCompat.detonate(h.getLevel(), rocket, info, pos.x, pos.y, pos.z, 0)
                    == HbmCompat.Outcome.FALLBACK, "Missing HBM was not reported as fallback");
        }
        h.succeed();
    }

    private static void payloads(GameTestHelper h) {
        Vec3 pos = h.absoluteVec(new Vec3(12, 8, 12));
        WeaponInfo info = info(WeaponInfo.Payload.NTM_FIRE);
        info.fireContainer = new FireContainer();
        info.fireContainer.fireType = "PHOSPHORUS";
        info.fireContainer.particles = false;
        apply(h, info, pos);
        var fire = h.getLevel().getEntitiesOfClass(Entity.class, new AABB(pos, pos).inflate(2),
                e -> e.getClass().getName().equals("com.hbm.entity.effect.EntityFireLingering"));
        check(h, fire.size() == 1, "NTM Next did not create exactly one lingering fire");
        fire.forEach(Entity::discard);
        info.effectOnly = true;
        apply(h, info, pos);
        check(h, h.getLevel().getEntitiesOfClass(Entity.class, new AABB(pos, pos).inflate(2),
                e -> e.getClass().getName().equals("com.hbm.entity.effect.EntityFireLingering")).isEmpty(),
                "EffectOnly created damaging lingering fire");

        info = info(WeaponInfo.Payload.NTM_MIST);
        info.mistContainer = new MistContainer();
        info.mistContainer.fluidType = "WATER";
        apply(h, info, pos);
        var mist = h.getLevel().getEntitiesOfClass(Entity.class, new AABB(pos, pos).inflate(2),
                e -> e.getClass().getName().equals("com.hbm.entity.effect.EntityMist"));
        check(h, mist.size() == 1, "NTM Next did not create exactly one fluid mist");
        mist.forEach(Entity::discard);
        info.effectOnly = true;
        apply(h, info, pos);
        check(h, h.getLevel().getEntitiesOfClass(Entity.class, new AABB(pos, pos).inflate(2),
                e -> e.getClass().getName().equals("com.hbm.entity.effect.EntityMist")).isEmpty(),
                "EffectOnly created damaging fluid mist");

        info = info(WeaponInfo.Payload.NTM_MINI_NUKE);
        info.mukeContainer = new MukeContainer();
        info.mukeContainer.safe = true;
        info.mukeContainer.shrapnelCount = 0;
        info.mukeContainer.radiationLevel = 0;
        apply(h, info, pos);
        info.effectOnly = true;
        apply(h, info, pos);

        info = info(WeaponInfo.Payload.NTM_VNT);
        info.vntSettingContainer = new VNTSettingContainer(Map.of("BlockAllocator",
                Map.of("Type", "BlockAllocatorStandard", "Constructor", java.util.List.of(8))));
        apply(h, info, pos);
        info.effectOnly = true;
        apply(h, info, pos);

        // Verify both nuclear factories, then discard before their first tick.
        info = info(WeaponInfo.Payload.NTM_NUKE);
        try {
            check(h, HbmCompat.detonate(h.getLevel(), projectile(h, info, pos), info, pos.x, pos.y, pos.z, 1)
                    == HbmCompat.Outcome.APPLIED, "NTM Next nuclear factory failed");
            var nuclear = nuclearEntities(h, pos);
            check(h, nuclear.size() == 2, "Expected one nuclear blast and one cloud");
            nuclear.forEach(Entity::discard);
            info.effectOnly = true;
            check(h, HbmCompat.detonate(h.getLevel(), projectile(h, info, pos), info, pos.x, pos.y, pos.z, 1)
                    == HbmCompat.Outcome.APPLIED, "NTM Next nuclear visual failed");
            nuclear = nuclearEntities(h, pos);
            check(h, nuclear.size() == 1 && nuclear.getFirst().getClass().getSimpleName().equals("EntityNukeTorex"),
                    "EffectOnly created a damaging nuke or duplicated its cloud");
        } finally {
            nuclearEntities(h, pos).forEach(Entity::discard);
        }
        h.succeed();
    }

    private static java.util.List<Entity> nuclearEntities(GameTestHelper h, Vec3 pos) {
        return h.getLevel().getEntitiesOfClass(Entity.class, new AABB(pos, pos).inflate(2),
                e -> e.getClass().getName().equals("com.hbm.entity.logic.EntityNukeExplosionMK5")
                        || e.getClass().getName().equals("com.hbm.entity.effect.EntityNukeTorex"));
    }

    private static void chemicalPayloads(GameTestHelper h) {
        Vec3 pos = h.absoluteVec(new Vec3(12, 8, 12));
        for (var type : ChemicalContainer.ChemType.values()) {
            String expected = switch (type) {
                case CHLORINE -> "EntityChlorineFX";
                case CLOUD -> "EntityCloudFX";
                case PINK_CLOUD -> "EntityPinkCloudFX";
                case ORANGE -> "EntityOrangeFX";
            };
            WeaponInfo info = info(WeaponInfo.Payload.NTM_CHEMICAL);
            info.chemicalContainer = new ChemicalContainer(0, 2, type);
            try {
                apply(h, info, pos);
                var clouds = chemicalEntities(h, pos);
                check(h, clouds.size() == 2, type + " did not spawn exactly two native clouds");
                check(h, clouds.stream().allMatch(e -> e.getClass().getSimpleName().equals(expected)
                                && e.getDeltaMovement().lengthSqr() == 0),
                        type + " selected the wrong cloud type or ignored zero speed");
                clouds.forEach(Entity::discard);

                info.effectOnly = true;
                apply(h, info, pos);
                check(h, chemicalEntities(h, pos).isEmpty(), type + " EffectOnly spawned damaging clouds");

                info.effectOnly = false;
                info.chemicalContainer = new ChemicalContainer(0.125, 1, type);
                apply(h, info, pos);
                clouds = chemicalEntities(h, pos);
                check(h, clouds.size() == 1 && clouds.getFirst().getDeltaMovement().lengthSqr() > 0,
                        type + " ignored cloud speed or count");
                clouds.forEach(Entity::discard);
                info.chemicalContainer = new ChemicalContainer(0.125, 0, type);
                apply(h, info, pos);
                check(h, chemicalEntities(h, pos).isEmpty(), type + " ignored zero Count");
            } finally {
                // These clouds poison on ticking; none should escape into other test arenas.
                chemicalEntities(h, pos).forEach(Entity::discard);
            }
        }
        h.succeed();
    }

    private static java.util.List<Entity> chemicalEntities(GameTestHelper h, Vec3 pos) {
        return h.getLevel().getEntitiesOfClass(Entity.class, new AABB(pos, pos).inflate(2),
                e -> e.getClass().getName().startsWith("com.hbm.entity.particle.Entity")
                        && e.getClass().getName().endsWith("FX"));
    }

    private static void apply(GameTestHelper h, WeaponInfo info, Vec3 pos) {
        check(h, HbmCompat.detonate(h.getLevel(), projectile(h, info, pos), info, pos.x, pos.y, pos.z, 0)
                == HbmCompat.Outcome.APPLIED, "NTM Next API failed for " + info.payloadNTM);
    }
}
