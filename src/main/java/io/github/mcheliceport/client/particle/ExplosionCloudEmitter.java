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
        for (int i = 0; i < count; i++) {
            double angle = (i * 2.3999632297) + random.nextDouble() * 0.35;
            // A central body, outward lobes and a rising crown, rather than a uniform sphere.
            boolean crown = i > 0 && i % 3 == 0;
            double spread = i == 0 ? 0 : radius * (crown ? 0.18 : 0.35);
            double dx = Math.cos(angle), dz = Math.sin(angle);
            double px = x + dx * spread, pz = z + dz * spread;
            double py = y + 0.3 + (crown ? radius * 0.45 : random.nextDouble() * radius * 0.25);
            float r = radius * (i == 0 ? 1.2F : 0.75F + random.nextFloat() * 0.35F);
            // Charge the final size, so large growing clouds cannot evade screen-coverage limits.
            if (!EffectAdmission.admit(level, px, py, pz, false, r)) continue;
            double speed = Math.min(0.4, radius * 0.04);
            Minecraft.getInstance().particleEngine.add(new BlastCloudParticle(level, px, py, pz,
                    dx * speed * (crown ? 0.25 : 1), crown ? speed * 0.9 : speed * 0.25, dz * speed * (crown ? 0.25 : 1),
                    r, ExplosionSequence.life(power, style) + random.nextInt(12),
                    ExplosionSequence.fireTicks(power, style) + random.nextInt(3), 0.8F + random.nextFloat() * 0.15F));
        }
        ClientEffects.spawnExplosionAccents(level, x, y, z, power, style);
        ClientEffects.spawnExplosionSparks(level, x, y, z, power, style);
    }
}
