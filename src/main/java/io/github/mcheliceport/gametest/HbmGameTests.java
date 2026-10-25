package io.github.mcheliceport.gametest;

import static io.github.mcheliceport.gametest.GameTestSupport.check;

import com.hbm.entity.effect.EntityFireLingering;
import com.hbm.entity.effect.EntityMist;
import com.hbm.entity.effect.EntityNukeTorex;
import com.hbm.entity.logic.EntityNukeExplosionMK5;
import com.hbm.entity.particle.EntityChlorineFX;
import com.hbm.entity.particle.EntityCloudFX;
import com.hbm.entity.particle.EntityOrangeFX;
import com.hbm.entity.particle.EntityPinkCloudFX;
import io.github.mcheliceport.compat.hbm.NtmPayloads;
import io.github.mcheliceport.content.ContentLocation;
import io.github.mcheliceport.content.weapon.WeaponInfo;
import io.github.mcheliceport.content.weapon.hbm.ChemicalContainer;
import io.github.mcheliceport.content.weapon.hbm.MistContainer;
import io.github.mcheliceport.content.weapon.hbm.MukeContainer;
import io.github.mcheliceport.content.weapon.hbm.NTSettingContainer;
import io.github.mcheliceport.content.weapon.hbm.VNTSettingContainer;
import io.github.mcheliceport.entity.weapon.RocketEntity;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

/**
 * Every NTM payload against the installed NTM Next ({@code ./gradlew runGameTestServer -PwithHbm}):
 * each spawns NTM's own entities or explosion, and {@code EffectOnly} keeps only the visuals.
 * Registered only when NTM Next is loaded.
 */
final class HbmGameTests {

    private HbmGameTests() {
    }

    static void register() {
        ModGameTests.test("ntm_rocket_sets_off_its_payload", 20, HbmGameTests::rocketPayload);
        ModGameTests.test("ntm_fire", 20, HbmGameTests::fire);
        ModGameTests.test("ntm_mist", 20, HbmGameTests::mist);
        ModGameTests.test("ntm_chemical", 20, HbmGameTests::chemical);
        ModGameTests.testAlone("ntm_nuke", 20, HbmGameTests::nuke);
        ModGameTests.testAlone("ntm_mini_nuke", 40, HbmGameTests::miniNuke);
        ModGameTests.testAlone("ntm_vnt", 40, HbmGameTests::vnt);
        ModGameTests.testAlone("ntm_nt", 40, HbmGameTests::nt);
    }

    private static WeaponInfo weapon(String name, WeaponInfo.Payload payload, boolean effectOnly) {
        WeaponInfo info = new WeaponInfo(ContentLocation.builtin(name), name + ".yml", "yaml");
        info.useHBM = true;
        info.payloadNTM = payload;
        info.effectOnly = effectOnly;
        return info;
    }

    private static <T extends Entity> List<T> around(GameTestHelper h, Class<T> type, Vec3 relative) {
        return h.getLevel().getEntitiesOfClass(type, new AABB(h.absoluteVec(relative), h.absoluteVec(relative))
                .inflate(12.0));
    }

    private static void detonate(GameTestHelper h, WeaponInfo info, Vec3 relative, float blockPower) {
        Vec3 at = h.absoluteVec(relative);
        NtmPayloads.detonate(h.getLevel(), null, info, at.x, at.y, at.z, blockPower);
    }

    private static void discardAll(List<? extends Entity> entities) {
        entities.forEach(Entity::discard);
    }

    /** A rocket's explosion sets off its payload: here lingering fire, beside the normal blast. */
    private static void rocketPayload(GameTestHelper h) {
        Vec3 spot = new Vec3(12.5, 2, 12.5);
        RocketEntity rocket = NtmGameTests.rocket(h, NtmGameTests.fireWeapon("ntm_rocket", false), spot);
        Vec3 at = rocket.position();
        rocket.newExplosion(at.x, at.y, at.z, 1.0F, 0.0F, false);
        List<EntityFireLingering> fires = around(h, EntityFireLingering.class, spot);
        check(h, fires.size() == 1, "the rocket left " + fires.size() + " lingering fires, not 1");
        discardAll(fires);
        rocket.discard();
        h.succeed();
    }

    private static void fire(GameTestHelper h) {
        WeaponInfo info = NtmGameTests.fireWeapon("ntm_fire", false);
        info.fireContainer.fireType = "PHOSPHORUS";
        info.fireContainer.count = 3;
        info.fireContainer.areaSpread = 2;
        detonate(h, info, new Vec3(6.5, 1, 6.5), 0.0F);
        List<EntityFireLingering> fires = around(h, EntityFireLingering.class, new Vec3(6.5, 1, 6.5));
        check(h, fires.size() == 3, "3 fires expected, got " + fires.size());
        check(h, fires.stream().allMatch(f -> f.getFireType() == EntityFireLingering.TYPE_PHOSPHORUS),
                "the fires are not phosphorus");
        discardAll(fires);
        detonate(h, NtmGameTests.fireWeapon("ntm_fire_effect", true), new Vec3(18.5, 1, 18.5), 0.0F);
        check(h, around(h, EntityFireLingering.class, new Vec3(18.5, 1, 18.5)).isEmpty(),
                "an effect-only fire payload left lingering fire");
        h.succeed();
    }

    private static void mist(GameTestHelper h) {
        WeaponInfo info = weapon("ntm_mist", WeaponInfo.Payload.NTM_MIST, false);
        info.mistContainer = new MistContainer();
        info.mistContainer.fluidType = "WATER";
        info.mistContainer.cloudCount = 2;
        detonate(h, info, new Vec3(12.5, 1, 12.5), 0.0F);
        List<EntityMist> mists = around(h, EntityMist.class, new Vec3(12.5, 1, 12.5));
        check(h, mists.size() == 2, "2 mists expected, got " + mists.size());
        check(h, mists.stream().allMatch(m -> m.getFluid() != null), "a mist has no fluid");
        discardAll(mists);
        info.effectOnly = true;
        detonate(h, info, new Vec3(12.5, 1, 12.5), 0.0F);
        check(h, around(h, EntityMist.class, new Vec3(12.5, 1, 12.5)).isEmpty(), "an effect-only mist spawned");
        h.succeed();
    }

    private static void chemical(GameTestHelper h) {
        Map<ChemicalContainer.ChemType, Class<? extends Entity>> types = Map.of(
                ChemicalContainer.ChemType.CHLORINE, EntityChlorineFX.class,
                ChemicalContainer.ChemType.CLOUD, EntityCloudFX.class,
                ChemicalContainer.ChemType.PINK_CLOUD, EntityPinkCloudFX.class,
                ChemicalContainer.ChemType.ORANGE, EntityOrangeFX.class);
        Vec3 spot = new Vec3(12.5, 2, 12.5);
        for (Map.Entry<ChemicalContainer.ChemType, Class<? extends Entity>> type : types.entrySet()) {
            WeaponInfo info = weapon("ntm_chemical", WeaponInfo.Payload.NTM_CHEMICAL, false);
            info.chemicalContainer = new ChemicalContainer(0.1, 5, type.getKey());
            detonate(h, info, spot, 0.0F);
            List<? extends Entity> clouds = around(h, type.getValue(), spot);
            check(h, clouds.size() == 5, type.getKey() + ": 5 clouds expected, got " + clouds.size());
            discardAll(clouds);
        }
        detonate(h, weapon("ntm_chemical_default", WeaponInfo.Payload.NTM_CHEMICAL, false), spot, 0.0F);
        List<EntityChlorineFX> chlorine = around(h, EntityChlorineFX.class, spot);
        check(h, chlorine.size() == ChemicalContainer.DEFAULT_COUNT,
                "a chemical payload without settings should release chlorine, got " + chlorine.size());
        discardAll(chlorine);
        h.succeed();
    }

    /** The MK5 blast and its cloud; both are removed before they tick, so nothing is destroyed. */
    private static void nuke(GameTestHelper h) {
        Vec3 spot = new Vec3(12.5, 2, 12.5);
        detonate(h, weapon("ntm_nuke", WeaponInfo.Payload.NTM_NUKE, false), spot, 20.0F);
        List<EntityNukeExplosionMK5> blasts = around(h, EntityNukeExplosionMK5.class, spot);
        List<EntityNukeTorex> clouds = around(h, EntityNukeTorex.class, spot);
        check(h, blasts.size() == 1 && blasts.get(0).getRadius() == 20, "one 20-block MK5 blast expected, got "
                + blasts.size());
        check(h, clouds.size() == 1, "one torex cloud expected, got " + clouds.size());
        discardAll(blasts);
        discardAll(clouds);
        detonate(h, weapon("ntm_nuke_effect", WeaponInfo.Payload.NTM_NUKE, true), spot, 20.0F);
        check(h, around(h, EntityNukeExplosionMK5.class, spot).isEmpty(), "an effect-only nuke started a blast");
        List<EntityNukeTorex> cloudOnly = around(h, EntityNukeTorex.class, spot);
        check(h, cloudOnly.size() == 1, "an effect-only nuke should still show its cloud");
        discardAll(cloudOnly);
        h.succeed();
    }

    /** A small mini-nuke breaks the block it hits; effect only, it breaks nothing. */
    private static void miniNuke(GameTestHelper h) {
        BlockPos block = new BlockPos(12, 3, 12);
        h.setBlock(block, Blocks.STONE);
        WeaponInfo info = weapon("ntm_mini_nuke", WeaponInfo.Payload.NTM_MINI_NUKE, true);
        info.mukeContainer = new MukeContainer();
        info.mukeContainer.blastRadius = 3.0F;
        info.mukeContainer.killRadius = 0.0F;
        info.mukeContainer.radiationLevel = 0.0F;
        info.mukeContainer.shrapnelCount = 0;
        detonate(h, info, new Vec3(12.5, 3.5, 12.5), 0.0F);
        check(h, h.getBlockState(block).is(Blocks.STONE), "an effect-only mini-nuke broke a block");
        info.effectOnly = false;
        detonate(h, info, new Vec3(12.5, 3.5, 12.5), 0.0F);
        check(h, h.getBlockState(block).isAir(), "the mini-nuke did not break the block it hit");
        h.succeed();
    }

    /** A VNT payload builds its parts from the weapon's maps: no drops, and fire left behind. */
    private static void vnt(GameTestHelper h) {
        BlockPos block = new BlockPos(12, 2, 12);
        for (int x = 10; x <= 14; x++) {
            for (int z = 10; z <= 14; z++) {
                h.setBlock(new BlockPos(x, 1, z), Blocks.OAK_PLANKS);
            }
        }
        h.setBlock(block, Blocks.OAK_PLANKS);
        Map<String, Object> attributes = new LinkedHashMap<>();
        attributes.put("BlockAllocator", Map.of("Type", "BlockAllocatorStandard", "Constructor", List.of(16)));
        attributes.put("BlockProcessor", Map.of("Type", "BlockProcessorStandard", "NoDrop", true,
                "WithBlockEffect", Map.of("Type", "BlockMutatorFire")));
        attributes.put("EntityProcessor", Map.of("Type", "EntityProcessorStandard"));
        attributes.put("SFX", List.of(Map.of("Type", "ExplosionEffectStandard")));
        WeaponInfo info = weapon("ntm_vnt", WeaponInfo.Payload.NTM_VNT, false);
        info.vntSettingContainer = new VNTSettingContainer(attributes);
        detonate(h, info, new Vec3(12.5, 2.5, 12.5), 3.0F);
        check(h, h.getBlockState(block).isAir() || h.getBlockState(block).is(Blocks.FIRE),
                "the VNT blast did not break the block it hit");
        check(h, around(h, ItemEntity.class, new Vec3(12.5, 2, 12.5)).isEmpty(), "NoDrop blocks dropped items");
        boolean fire = false;
        for (BlockPos pos : BlockPos.betweenClosed(new BlockPos(9, 1, 9), new BlockPos(15, 4, 15))) {
            fire |= h.getBlockState(pos).is(Blocks.FIRE);
        }
        check(h, fire, "the fire block effect left no fire");
        h.succeed();
    }

    /** Legacy NT explosions run on VNT parts: they break blocks, and NODROP keeps the drops away. */
    private static void nt(GameTestHelper h) {
        BlockPos block = new BlockPos(12, 2, 12);
        h.setBlock(block, Blocks.OAK_PLANKS);
        WeaponInfo info = weapon("ntm_nt", WeaponInfo.Payload.NTM_NT, false);
        info.ntSettingContainer = new NTSettingContainer(List.of("NODROP", "NOSOUND"), 16);
        detonate(h, info, new Vec3(12.5, 2.5, 12.5), 3.0F);
        check(h, h.getBlockState(block).isAir(), "the NT blast did not break the block it hit");
        check(h, around(h, ItemEntity.class, new Vec3(12.5, 2, 12.5)).isEmpty(), "NODROP blocks dropped items");
        h.succeed();
    }
}
