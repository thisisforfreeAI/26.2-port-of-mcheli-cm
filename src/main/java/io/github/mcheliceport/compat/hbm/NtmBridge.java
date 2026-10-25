package io.github.mcheliceport.compat.hbm;

import com.hbm.entity.effect.EntityFireLingering;
import com.hbm.entity.effect.EntityMist;
import com.hbm.entity.effect.EntityNukeTorex;
import com.hbm.entity.logic.EntityNukeExplosionMK5;
import com.hbm.entity.particle.EntityChlorineFX;
import com.hbm.entity.particle.EntityCloudFX;
import com.hbm.entity.particle.EntityOrangeFX;
import com.hbm.entity.particle.EntityPinkCloudFX;
import com.hbm.explosion.ExplosionNukeSmall;
import com.hbm.explosion.vanillant.ExplosionVNT;
import com.hbm.explosion.vanillant.standard.BlockAllocatorStandard;
import com.hbm.explosion.vanillant.standard.BlockMutatorBalefire;
import com.hbm.explosion.vanillant.standard.BlockMutatorDigamma;
import com.hbm.explosion.vanillant.standard.BlockMutatorFire;
import com.hbm.explosion.vanillant.standard.BlockMutatorLava;
import com.hbm.explosion.vanillant.standard.BlockProcessorStandard;
import com.hbm.explosion.vanillant.standard.EntityProcessorStandard;
import com.hbm.explosion.vanillant.standard.ExplosionEffectStandard;
import com.hbm.explosion.vanillant.standard.PlayerProcessorStandard;
import com.hbm.handler.threading.TargetPoint;
import com.hbm.inventory.fluid.NTMFluids;
import com.hbm.packet.PacketWire;
import com.hbm.packet.toclient.MukePayload;
import com.hbm.particle.helper.ExplosionCreator;
import com.hbm.particle.helper.ExplosionSmallCreator;
import com.hbm.particle.helper.FlameCreator;
import io.github.mcheliceport.content.weapon.WeaponInfo;
import io.github.mcheliceport.content.weapon.hbm.ChemicalContainer;
import io.github.mcheliceport.content.weapon.hbm.FireContainer;
import io.github.mcheliceport.content.weapon.hbm.MistContainer;
import io.github.mcheliceport.content.weapon.hbm.MukeContainer;
import io.github.mcheliceport.content.weapon.hbm.NTSettingContainer;
import io.github.mcheliceport.content.weapon.hbm.VNTSettingContainer;
import java.util.Locale;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.material.Fluid;
import org.jspecify.annotations.Nullable;

/**
 * The NTM Next calls behind {@link NtmPayloads}, one per upstream payload type, against NTM Next
 * 1.0.3's API. Only {@link NtmPayloads} refers to this class, and only once {@code hbm} is loaded.
 */
final class NtmBridge {

    private NtmBridge() {
    }

    static void detonate(ServerLevel level, @Nullable Entity exploder, WeaponInfo info, double x, double y, double z,
            float blockPower) {
        boolean effectOnly = info.effectOnly;
        switch (info.payloadNTM) {
            case NONE -> {
            }
            case NTM_VNT -> {
                if (info.vntSettingContainer != null) {
                    vnt(level, exploder, info.vntSettingContainer, x, y, z, blockPower, effectOnly);
                }
            }
            case NTM_NT -> {
                if (info.ntSettingContainer != null && !effectOnly) {
                    nt(level, exploder, info.ntSettingContainer, x, y, z, blockPower);
                }
            }
            case NTM_MINI_NUKE -> {
                if (info.mukeContainer != null) {
                    miniNuke(level, info.mukeContainer, x, y, z, effectOnly);
                }
            }
            case NTM_NUKE -> nuke(level, exploder, (int) blockPower, x, y, z, effectOnly);
            case NTM_CHEMICAL -> chemical(level,
                    info.chemicalContainer != null ? info.chemicalContainer : ChemicalContainer.defaults(), x, y, z);
            case NTM_MIST -> {
                if (info.mistContainer != null && !effectOnly) {
                    mist(level, info.mistContainer, x, y, z);
                }
            }
            case NTM_FIRE -> {
                if (info.fireContainer != null) {
                    fire(level, info.fireContainer, x, y, z, effectOnly);
                }
            }
        }
    }

    /** Upstream's mini-nuke: {@code ExplosionNukeSmall}, or only its fireball packet when effect only. */
    static void miniNuke(ServerLevel level, MukeContainer muke, double x, double y, double z, boolean effectOnly) {
        boolean tinyTot = "TinyTot".equalsIgnoreCase(muke.particle.trim());
        if (effectOnly) {
            PacketWire.sendToAllAround(new MukePayload(x, y + 0.5, z, tinyTot, false),
                    new TargetPoint(level, x, y, z, 250));
            return;
        }
        ExplosionNukeSmall.MukeParams params = new ExplosionNukeSmall.MukeParams();
        params.miniNuke = muke.miniNuke;
        params.safe = muke.safe;
        params.blastRadius = muke.blastRadius;
        params.killRadius = muke.killRadius;
        params.radiationLevel = muke.radiationLevel;
        // NTM Next made the particle a pair of switches; upstream's names were "Muke" and "TinyTot".
        params.particle = !"None".equalsIgnoreCase(muke.particle.trim());
        params.tinytot = tinyTot;
        params.shrapnelCount = muke.shrapnelCount;
        params.resolution = muke.resolution;
        ExplosionNukeSmall.explode(level, x, y, z, params);
    }

    /** Upstream's {@code HBMUtil.nukeMK5}: the MK5 blast and its torex cloud. */
    static void nuke(ServerLevel level, @Nullable Entity exploder, int radius, double x, double y, double z,
            boolean effectOnly) {
        radius = Math.max(1, radius);
        if (!effectOnly) {
            EntityNukeExplosionMK5 blast = EntityNukeExplosionMK5.statFac(level, radius, x, y, z);
            if (exploder != null) {
                blast.setDetonator(exploder);
            }
            level.addFreshEntity(blast);
        }
        // statFac adds the cloud itself.
        EntityNukeTorex.statFac(level, x, y, z, radius);
    }

    /** Upstream's {@code HBMUtil.spawnChlorine}, with NTM Next's own gas cloud entities. */
    static void chemical(ServerLevel level, ChemicalContainer chemical, double x, double y, double z) {
        RandomSource random = level.getRandom();
        for (int i = 0; i < chemical.count(); i++) {
            double mx = random.nextGaussian() * chemical.speed();
            double my = random.nextGaussian() * chemical.speed();
            double mz = random.nextGaussian() * chemical.speed();
            Entity cloud = switch (chemical.type()) {
                case CHLORINE -> new EntityChlorineFX(level, x, y, z, mx, my, mz);
                case CLOUD -> new EntityCloudFX(level, x, y, z, mx, my, mz);
                case PINK_CLOUD -> new EntityPinkCloudFX(level, x, y, z, mx, my, mz);
                case ORANGE -> new EntityOrangeFX(level, x, y, z, mx, my, mz);
            };
            level.addFreshEntity(cloud);
        }
    }

    /** Upstream's {@code MistContainer.execute}: fluid mist clouds, like NTM's chemical canisters. */
    static void mist(ServerLevel level, MistContainer mist, double x, double y, double z) {
        if ("NONE".equalsIgnoreCase(mist.fluidType)) {
            return;
        }
        Fluid fluid = fluid(mist.fluidType);
        if (fluid == null) {
            NtmPayloads.warnOnce("fluid:" + mist.fluidType, "Unknown NTM fluid {} in a Mist payload", mist.fluidType);
            return;
        }
        RandomSource random = level.getRandom();
        for (int i = 0; i < mist.cloudCount; i++) {
            EntityMist cloud = new EntityMist(level);
            cloud.setType(fluid);
            cloud.setArea(mist.width, mist.height);
            cloud.setDuration(vary(random, mist.lifetime, mist.lifetimeVariance));
            cloud.setPos(spread(random, x, mist.areaSpread), y, spread(random, z, mist.areaSpread));
            level.addFreshEntity(cloud);
        }
    }

    /** Upstream's {@code FireContainer.execute}: lingering fire, and NTM's flame burst. */
    static void fire(ServerLevel level, FireContainer fire, double x, double y, double z, boolean effectOnly) {
        int type = fireType(fire.fireType);
        if (!effectOnly) {
            RandomSource random = level.getRandom();
            for (int i = 0; i < Math.max(1, fire.count); i++) {
                EntityFireLingering flames = new EntityFireLingering(level);
                flames.setType(type);
                flames.setArea(fire.width, fire.height);
                flames.setDuration(vary(random, fire.lifetime, fire.lifetimeVariance));
                flames.setPos(spread(random, x, fire.areaSpread), y, spread(random, z, fire.areaSpread));
                level.addFreshEntity(flames);
            }
        }
        if (fire.particles) {
            int meta = switch (type) {
                case EntityFireLingering.TYPE_BALEFIRE -> FlameCreator.META_BALEFIRE;
                case EntityFireLingering.TYPE_OXY -> FlameCreator.META_OXY;
                case EntityFireLingering.TYPE_BLACK -> FlameCreator.META_BLACK;
                default -> FlameCreator.META_FIRE;
            };
            FlameCreator.composeEffect(level, x, y, z, meta);
        }
    }

    /** Upstream's {@code VNTSettingContainer.buildExplosion}: a configured VNT blast and its effect. */
    static void vnt(ServerLevel level, @Nullable Entity exploder, VNTSettingContainer settings, double x, double y,
            double z, float size, boolean effectOnly) {
        if (!effectOnly) {
            ExplosionVNT explosion = new ExplosionVNT(level, x, y, z, size, exploder);
            if (settings.preset != null) {
                switch (settings.preset.trim().toUpperCase(Locale.ROOT)) {
                    case "AMAT", "ANTIMATTER" -> explosion.makeAmat();
                    default -> explosion.makeStandard();
                }
            }
            VntProcessors.apply(explosion, settings.rawEntry);
            explosion.explode();
        }
        effect(level, settings.explosionEffect, x, y, z);
    }

    /**
     * Upstream's {@code ExplosionEffect.execute}. NTM Next keeps the small-cloud effect's settings,
     * but replaced the fully configurable large effect with three fixed sizes; the cloud scale
     * picks the nearest (upstream's medium, standard and large presets have 2, 5 and 6.5).
     */
    static void effect(ServerLevel level, VNTSettingContainer.ExplosionEffect effect, double x, double y, double z) {
        if (effect.isSmall) {
            ExplosionSmallCreator.composeEffect(level, x, y, z, effect.cloudCount, effect.cloudScale,
                    effect.cloudSpeedMult);
        } else if (effect.cloudScale < 3.5F) {
            ExplosionCreator.composeEffectSmall(level, x, y, z);
        } else if (effect.cloudScale < 5.75F) {
            ExplosionCreator.composeEffectStandard(level, x, y, z);
        } else {
            ExplosionCreator.composeEffectLarge(level, x, y, z);
        }
    }

    /**
     * Upstream's legacy {@code ExplosionNT}, which NTM Next removed, rebuilt from the VNT parts that
     * replaced it: the same ray resolution and its block effect, drop and damage attributes.
     */
    static void nt(ServerLevel level, @Nullable Entity exploder, NTSettingContainer settings, double x, double y,
            double z, float strength) {
        ExplosionVNT explosion = new ExplosionVNT(level, x, y, z, strength, exploder);
        explosion.setBlockAllocator(new BlockAllocatorStandard(settings.resolution));
        BlockProcessorStandard blocks = new BlockProcessorStandard();
        boolean hurt = true;
        boolean sfx = true;
        for (String attribute : settings.attributes) {
            switch (attribute.trim().toUpperCase(Locale.ROOT)) {
                case "FIRE" -> blocks.withBlockEffect(new BlockMutatorFire());
                case "BALEFIRE" -> blocks.withBlockEffect(new BlockMutatorBalefire());
                case "DIGAMMA" -> blocks.withBlockEffect(new BlockMutatorDigamma(false));
                case "DIGAMMA_CIRCUIT" -> blocks.withBlockEffect(new BlockMutatorDigamma(true));
                case "LAVA", "LAVA_V", "LAVA_R" -> blocks.withBlockEffect(new BlockMutatorLava(Blocks.LAVA));
                case "NODROP" -> blocks.setNoDrop();
                case "ALLDROP" -> blocks.setAllDrop();
                case "NOHURT" -> hurt = false;
                case "NOPARTICLE", "NOSOUND" -> sfx = false;
                default -> NtmPayloads.warnOnce("nt:" + attribute,
                        "NT explosion attribute {} has no NTM Next equivalent and is ignored", attribute);
            }
        }
        explosion.setBlockProcessor(blocks);
        if (hurt) {
            explosion.setEntityProcessor(new EntityProcessorStandard());
            explosion.setPlayerProcessor(new PlayerProcessorStandard());
        }
        if (sfx) {
            explosion.setSFX(new ExplosionEffectStandard());
        }
        explosion.explode();
    }

    /** NTM fluids by upstream's upper-case names, or NTM Next's lower-case registry names. */
    static @Nullable Fluid fluid(String name) {
        for (String candidate : new String[] {name, name.toLowerCase(Locale.ROOT), name.toUpperCase(Locale.ROOT)}) {
            try {
                Fluid fluid = NTMFluids.byName(candidate);
                if (fluid != null) {
                    return fluid;
                }
            } catch (IllegalStateException | IllegalArgumentException unknown) {
                // try the next spelling
            }
        }
        return null;
    }

    static int fireType(String name) {
        return switch (name.toUpperCase(Locale.ROOT).trim()) {
            case "BALEFIRE", "BALE" -> EntityFireLingering.TYPE_BALEFIRE;
            case "PHOSPHORUS", "PHOSPHOR", "WP" -> EntityFireLingering.TYPE_PHOSPHORUS;
            case "OXY", "OXYGEN" -> EntityFireLingering.TYPE_OXY;
            case "BLACK" -> EntityFireLingering.TYPE_BLACK;
            default -> EntityFireLingering.TYPE_DIESEL;
        };
    }

    private static int vary(RandomSource random, int base, int variance) {
        return variance <= 0 ? base : base + random.nextInt(variance);
    }

    private static double spread(RandomSource random, double center, int spread) {
        return spread <= 0 ? center : center + random.nextInt(spread * 2) - spread;
    }
}
