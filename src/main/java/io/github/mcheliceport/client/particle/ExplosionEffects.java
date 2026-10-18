package io.github.mcheliceport.client.particle;

import io.github.mcheliceport.config.ClientConfig;
import io.github.mcheliceport.config.EffectQuality;
import io.github.mcheliceport.effect.ParticleParam;
import io.github.mcheliceport.weapon.explosion.McheliExplosion;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.BlockPos;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.block.Blocks;

/**
 * The client half of upstream {@code MCH_ExplosionV2}: what {@code PacketParticleEffect} draws for
 * an explosion ({@code effectMODExplosion}, {@code effectVanillaExplosion} and
 * {@code effectExplosionInWater}).
 *
 * <p>As upstream, the client runs the block ray march again and adds what it finds to the blocks
 * the server sent, so each block can give particles twice. Upstream's client march also pushed
 * the client's entities; the port leaves knockback to the server (see {@link McheliExplosion}).
 */
public final class ExplosionEffects {

    /** Hook for the flares that large explosions throw (set once the flare entity exists). */
    public interface FlareSpawner {
        void spawn(ClientLevel level, double x, double y, double z, double mx, double my, double mz, float size,
                int type);
    }

    static volatile FlareSpawner flareSpawner = (level, x, y, z, mx, my, mz, size, type) -> {
    };

    private ExplosionEffects() {
    }

    public static void setFlareSpawner(FlareSpawner spawner) {
        flareSpawner = spawner;
    }

    static void play(ClientLevel level, double x, double y, double z, float size, boolean inWater,
            List<BlockPos> affected) {
        if (!EffectAdmission.inRange(level, x, y, z, size * 2)) return;
        if (inWater) {
            effectExplosionInWater(level, x, y, z, size);
            return;
        }
        List<BlockPos> blocks = new ArrayList<>(affected);
        blocks.addAll(McheliExplosion.rayMarchBlocks(level, null, x, y, z, size, 1.0F, false, level.getRandom()));
        boolean vanilla = ClientConfig.get(ClientConfig.DEFAULT_EXPLOSION_PARTICLE);
        if (!vanilla && size >= 1.0F && EffectQuality.enhanced()) {
            ClientEffects.spawnExplosionGlow(level, x, y, z, size);
            ClientEffects.spawnExplosionSparks(level, x, y, z, size);
        }
        int cnt = 0;
        int flareCnt = (int) size;
        for (BlockPos pos : blocks) {
            cnt++;
            if (vanilla) {
                spawnVanillaExplosionEffect(level, x, y, z, size, pos.getX(), pos.getY(), pos.getZ());
            } else if (spawnExplosionEffect(level, x, y, z, size, cnt, pos.getX(), pos.getY(), pos.getZ(),
                    flareCnt > 0)) {
                flareCnt--;
            }
        }
    }

    /** Upstream {@code spawnExlosionEffect}: flares, block dust and smoke for one affected block. */
    private static boolean spawnExplosionEffect(ClientLevel level, double ex, double ey, double ez, float size,
            int cnt, int i, int j, int k, boolean spawnFlare) {
        RandomSource rand = level.getRandom();
        boolean spawnedFlare = false;
        double d0 = i + rand.nextFloat();
        double d1 = j + rand.nextFloat();
        double d2 = k + rand.nextFloat();
        double mx = d0 - ex;
        double my = d1 - ey;
        double mz = d2 - ez;
        double d6 = Mth.sqrt((float) (mx * mx + my * my + mz * mz));
        mx /= d6;
        my /= d6;
        mz /= d6;
        double d7 = 0.5 / (d6 / size + 0.1);
        d7 *= rand.nextFloat() * rand.nextFloat() + 0.3F;
        mx *= d7 * 0.5;
        my *= d7 * 0.5;
        mz *= d7 * 0.5;
        double px = (d0 + ex) / 2.0;
        double py = (d1 + ey) / 2.0;
        double pz = (d2 + ez) / 2.0;
        double r = Math.PI * rand.nextInt(360) / 180.0;
        if (size >= 4.0F && spawnFlare) {
            double a = Math.min(size / 12.0F, 0.6) * (0.5F + rand.nextFloat() * 0.5F);
            flareSpawner.spawn(level, px, py + 2.0, pz, Math.sin(r) * a, (1.0 + my / 5.0) * a, Math.cos(r) * a,
                    2.0F, 0);
            spawnedFlare = true;
        }
        if (cnt % 4 == 0) {
            float bdf = Math.min(size / 3.0F, 2.0F) * (0.5F + rand.nextFloat() * 0.5F);
            ClientEffects.INSTANCE.spawnTileDust(level, (int) (px + 0.5), (int) (py - 0.5), (int) (pz + 0.5), px,
                    py + 1.0, pz, Math.sin(r) * bdf, 0.5 + my / 5.0 * bdf, Math.cos(r) * bdf,
                    Math.min(size / 2.0F, 3.0F) * (0.5F + rand.nextFloat() * 0.5F));
        }
        int es = (int) Math.max(size, 4.0F);
        if (size <= 1.0F || cnt % es == 0) {
            if (rand.nextBoolean()) {
                my *= 3.0;
                mx *= 0.1;
                mz *= 0.1;
            } else {
                my *= 0.2;
                mx *= 3.0;
                mz *= 3.0;
            }
            ParticleParam prm = new ParticleParam("explode", px, py, pz, mx, my, mz, size < 8.0F ? size * 2.0F : 16.0F);
            prm.r = prm.g = prm.b = 0.3F + rand.nextFloat() * 0.4F;
            prm.r += 0.1F;
            prm.g += 0.05F;
            prm.age = 10 + rand.nextInt(30);
            prm.age = (int) (prm.age * Math.min(size, 6.0F));
            prm.age = prm.age * 2 / 3;
            prm.diffusible = true;
            ClientEffects.INSTANCE.spawnParticle(level, prm);
        }
        return spawnedFlare;
    }

    /** Upstream {@code spawnVanillaExlosionEffect}: vanilla's explosion particles. */
    private static void spawnVanillaExplosionEffect(ClientLevel level, double ex, double ey, double ez, float size,
            int i, int j, int k) {
        RandomSource rand = level.getRandom();
        double d0 = i + rand.nextFloat();
        double d1 = j + rand.nextFloat();
        double d2 = k + rand.nextFloat();
        double d3 = d0 - ex;
        double d4 = d1 - ey;
        double d5 = d2 - ez;
        double d6 = Mth.sqrt((float) (d3 * d3 + d4 * d4 + d5 * d5));
        d3 /= d6;
        d4 /= d6;
        d5 /= d6;
        double d7 = 0.5 / (d6 / size + 0.1);
        d7 *= rand.nextFloat() * rand.nextFloat() + 0.3F;
        d3 *= d7;
        d4 *= d7;
        d5 *= d7;
        ClientEffects.INSTANCE.spawnVanilla(level, "explode", (d0 + ex) / 2.0, (d1 + ey) / 2.0, (d2 + ez) / 2.0,
                d3, d4, d5);
        ClientEffects.INSTANCE.spawnVanilla(level, "smoke", d0, d1, d2, d3, d4, d5);
    }

    /** Upstream {@code effectExplosionInWater}: splashes thrown up from the water around the blast. */
    private static void effectExplosionInWater(ClientLevel level, double x, double y, double z, float size) {
        if (size <= 0.0F) {
            return;
        }
        RandomSource rand = level.getRandom();
        int range = (int) (size + 0.5);
        int ex = (int) (x + 0.5);
        int ey = (int) (y + 0.5);
        int ez = (int) (z + 0.5);
        for (int i1 = -range; i1 <= range; i1++) {
            if (ey + i1 < level.getMinY() + 1) {
                continue;
            }
            for (int j1 = -range; j1 <= range; j1++) {
                for (int k1 = -range; k1 <= range; k1++) {
                    int d = j1 * j1 + i1 * i1 + k1 * k1;
                    if (d < range * range
                            && level.getBlockState(new BlockPos(ex + j1, ey + i1, ez + k1)).is(Blocks.WATER)) {
                        int n = rand.nextInt(2);
                        for (int i = 0; i < n; i++) {
                            ParticleParam prm = new ParticleParam("splash", ex + j1, ey + i1, ez + k1,
                                    (double) j1 / range * (rand.nextFloat() - 0.2),
                                    1.0 - Math.sqrt(j1 * j1 + k1 * k1) / range
                                            + rand.nextFloat() * 0.4 * range * 0.4,
                                    (double) k1 / range * (rand.nextFloat() - 0.2), rand.nextInt(range) * 3 + range);
                            ClientEffects.INSTANCE.spawnParticle(level, prm);
                        }
                    }
                }
            }
        }
    }
}
