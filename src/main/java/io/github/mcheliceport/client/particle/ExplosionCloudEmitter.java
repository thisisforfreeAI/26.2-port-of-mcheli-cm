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
        int smokeCount = ExplosionSequence.smokeLobes(count);
        int baseCount = (smokeCount + 1) / 2;
        // Admit the supporting cloud first. Even a thinned blast keeps smoke at the impact.
        for (int i = 0; i < smokeCount; i++) {
            boolean base = i < baseCount;
            int tier = i - baseCount;
            double angle = i * 2.3999632297 + random.nextDouble() * 0.6;
            double dx = Math.cos(angle), dz = Math.sin(angle);
            double spread = radius * (base ? 0.28 : 0.12 + random.nextDouble() * 0.18);
            double px = x + dx * spread, pz = z + dz * spread;
            double py = y + radius * (base ? 0.12 : 0.28 + tier * 0.20);
            float r = radius * (base ? 0.78F + random.nextFloat() * 0.18F : 0.85F + random.nextFloat() * 0.25F);
            if (!EffectAdmission.admitBlastLobe(level, px, py, pz, r)) continue;
            double speed = radius * (base ? 0.035 : 0.009) * (0.8 + random.nextDouble() * 0.4);
            int delay = base ? i % 3 : 3 + Math.min(7, tier * 2);
            int life = Math.min(116, ExplosionSequence.life(power, style) + (base ? 12 : 20) + random.nextInt(8));
            Minecraft.getInstance().particleEngine.add(new BlastCloudParticle(level, px, py, pz,
                    dx * speed, base ? 0.01 : 0.045 + Math.min(3, tier) * 0.018, dz * speed,
                    r, life, 0, base ? 0.94F : 0.9F, delay, base));
        }
        int fireCount = count - smokeCount;
        for (int i = 0; i < fireCount; i++) {
            double angle = i * 2.3999632297 + random.nextDouble() * 0.6;
            double dx = Math.cos(angle), dz = Math.sin(angle);
            // Overlapping pockets punch through the developing smoke, rather than flying away
            // as a ring of identical fireballs. Each one leaves its own cooling smoke behind.
            double spread = i == 0 ? 0 : radius * (0.12 + random.nextDouble() * 0.12);
            double px = x + dx * spread, pz = z + dz * spread;
            double py = y + radius * (0.13 + random.nextDouble() * 0.20);
            float r = radius * (i == 0 ? 1.05F : 0.60F + random.nextFloat() * 0.25F);
            if (!EffectAdmission.admitBlastLobe(level, px, py, pz, r)) continue;
            double speed = i == 0 ? 0 : Math.min(0.7, radius * 0.07) * (0.7 + random.nextDouble() * 0.3);
            int fireTicks = ExplosionSequence.fireTicks(power, style) * (55 + random.nextInt(40)) / 100;
            int life = Math.min(100, ExplosionSequence.life(power, style) + random.nextInt(8));
            Minecraft.getInstance().particleEngine.add(new BlastCloudParticle(level, px, py, pz,
                    dx * speed, 0.04 + random.nextDouble() * 0.08, dz * speed, r, life,
                    fireTicks, 0.95F, i == 0 ? 0 : 1 + i % 4, false));
        }
        ClientEffects.spawnExplosionAccents(level, x, y, z, power, style);
        ClientEffects.spawnExplosionSparks(level, x, y, z, power, style);
    }

    /** Earlier McHeli growing puffs and two-way ejection, within the same primary request cap. */
    private static void oldSmoke(ClientLevel level, double x, double y, double z, float power, BlastStyle style, Random random) {
        float nominal = power < 8 ? power * 2 : 16;
        var quality = ClientConfig.get(ClientConfig.EFFECT_BUDGET);
        nominal = ExplosionLook.puffSize(nominal, power, style, quality);
        if (!EffectAdmission.admitBlastLobe(level, x, y, z, nominal * 0.2F)) return;
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
