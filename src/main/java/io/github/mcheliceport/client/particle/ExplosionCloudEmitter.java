package io.github.mcheliceport.client.particle;

import io.github.mcheliceport.config.ClientConfig;
import io.github.mcheliceport.effect.BlastStyle;
import java.util.Random;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;

final class ExplosionCloudEmitter {
    private ExplosionCloudEmitter() {}

    static void emit(ClientLevel level, double x, double y, double z, float power, BlastStyle style) {
        var quality = ClientConfig.get(ClientConfig.EFFECT_BUDGET);
        int count = ExplosionSequence.lobes(power, style, quality, EffectPerformance.pressure());
        if (count == 0) return;
        float radius = ExplosionSequence.radius(power, style);
        float flash = ExplosionSequence.flashRadius(power, style);
        if (EffectPerformance.optionalGlows() && EffectAdmission.admit(level, x, y, z, false, flash)) {
            Minecraft.getInstance().particleEngine.add(new ImpactCoreParticle(level, x, y + 0.3, z, flash,
                    ExplosionLook.broad(style) ? 6 : 3));
        }
        Random random = new Random(Double.doubleToLongBits(x) ^ Long.rotateLeft(Double.doubleToLongBits(z), 21)
                ^ level.getGameTime() ^ 0xB1A57L);
        if (ExplosionSequence.smokeLed(power, style)) {
            for (int i = 0; i < count; i++) {
                double angle = i * 2.3999632297 + random.nextDouble() * 0.4;
                double spread = radius * random.nextDouble() * 0.6;
                oldSmoke(level, x + Math.cos(angle) * spread, y + random.nextDouble() * radius * 0.4,
                        z + Math.sin(angle) * spread, power, style, random);
            }
            ClientEffects.spawnExplosionSparks(level, x, y, z, power, style);
            return;
        }
        for (int i = 0; i < count; i++) {
            double angle = (i * 2.3999632297) + random.nextDouble() * 0.35;
            // Dark smoke develops behind a much faster, uneven shell of fire.
            boolean crown = i > 0 && i % 3 == 0;
            int smokeTier = crown ? i / 3 - 1 : -1;
            double spread = i == 0 ? 0 : radius * (crown ? 0.2 : 0.3);
            double dx = Math.cos(angle), dz = Math.sin(angle);
            double px = x + dx * spread, pz = z + dz * spread;
            double py = y + 0.3 + (crown ? radius * Math.min(0.6, smokeTier * 0.3) : random.nextDouble() * radius * 0.3);
            if (i % 6 == 2) {
                oldSmoke(level, px, py, pz, power, style, random);
                continue;
            }
            float r = radius * (i == 0 ? 1.2F : crown ? Math.min(1.1F, 0.65F + smokeTier * 0.23F) : 0.45F + random.nextFloat() * 0.35F);
            // Charge the final size, so large growing clouds cannot evade screen-coverage limits.
            if (!EffectAdmission.admit(level, px, py, pz, false, r)) continue;
            double speed = Math.min(1.15, radius * (crown ? 0.004 : 0.14)) * (0.75 + random.nextDouble() * 0.4);
            int fireTicks = crown ? 0 : i == 0 ? 5 : ExplosionSequence.fireTicks(power, style) * (65 + random.nextInt(45)) / 100;
            int life = crown ? ExplosionSequence.life(power, style) + random.nextInt(12)
                    : Math.max(18, fireTicks + 12 + random.nextInt(10));
            Minecraft.getInstance().particleEngine.add(new BlastCloudParticle(level, px, py, pz,
                    i == 0 ? 0 : dx * speed, crown ? 0.025 + Math.min(3, smokeTier) * 0.06 : speed * (0.12 + random.nextDouble() * 0.35),
                    i == 0 ? 0 : dz * speed, r, life, fireTicks, crown ? 0.9F : 0.95F));
        }
        ClientEffects.spawnExplosionAccents(level, x, y, z, power, style);
        ClientEffects.spawnExplosionSparks(level, x, y, z, power, style);
    }

    /** Earlier McHeli growing puffs and two-way ejection, within the same primary request cap. */
    private static void oldSmoke(ClientLevel level, double x, double y, double z, float power, BlastStyle style, Random random) {
        float nominal = power < 8 ? power * 2 : 16;
        var quality = ClientConfig.get(ClientConfig.EFFECT_BUDGET);
        nominal = ExplosionLook.puffSize(nominal, power, style, quality);
        if (!EffectAdmission.admit(level, x, y, z, false, nominal * 0.2F)) return;
        double angle = random.nextDouble() * Math.PI * 2;
        double speed = Math.min(0.7, power * 0.09) * (0.5 + random.nextDouble() * 0.5);
        boolean upward = random.nextBoolean();
        SmokeParticle puff = new SmokeParticle(level, x, y, z,
                Math.cos(angle) * speed * (upward ? 0.1 : 1), speed * (upward ? 1.2 : 0.2),
                Math.sin(angle) * speed * (upward ? 0.1 : 1));
        float colour = 0.3F + random.nextFloat() * 0.4F;
        puff.setColor(colour + 0.1F, colour + 0.05F, colour);
        puff.diffusible = true;
        puff.disableCollision();
        puff.particleScale = nominal * ExplosionLook.startScale(power, style, quality);
        puff.particleMaxScale = nominal * 2;
        puff.heat = ExplosionSequence.smokeLed(power, style) ? 0.55F : 0.3F;
        puff.coolingShare = ExplosionLook.coolingShare(style);
        puff.motionYUpAge = 2;
        puff.setParticleMaxAge(Math.clamp((int) ((10 + random.nextInt(30)) * Math.min(power, 6) * 2 / 3), 8, 100));
        Minecraft.getInstance().particleEngine.add(puff);
    }
}
