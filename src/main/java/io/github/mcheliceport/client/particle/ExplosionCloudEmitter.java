package io.github.mcheliceport.client.particle;

import io.github.mcheliceport.config.ClientConfig;
import io.github.mcheliceport.effect.BlastStyle;
import io.github.mcheliceport.effect.BlastDirection;
import net.minecraft.world.phys.Vec3;
import java.util.Random;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;

final class ExplosionCloudEmitter {
    private ExplosionCloudEmitter() {}

    static void emit(ClientLevel level, double x, double y, double z, float power, BlastStyle style, BlastDirection direction) {
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
        Vec3 axis = direction.burstAxis();
        if (ExplosionSequence.smokeLed(power, style)) {
            boolean directed = !direction.equals(BlastDirection.NONE);
            for (int i = 0; i < count; i++) {
                double angle = i * 2.3999632297 + random.nextDouble() * 0.4;
                double spread = radius * random.nextDouble() * 0.6;
                // Keep the old puff sheet and growth, concentrating known rocket impacts along their heading.
                double reach = radius * i / Math.max(1.0, count - 1);
                oldSmoke(level, x + Math.cos(angle) * spread * (directed ? 0.65 : 1) + axis.x * reach,
                        y + random.nextDouble() * radius * (directed ? 0.25 : 0.4) + axis.y * reach,
                        z + Math.sin(angle) * spread * (directed ? 0.65 : 1) + axis.z * reach, power, style, random, axis);
            }
            BlastGroundEffects.emit(level, x, y, z, power, style, direction);
            ClientEffects.spawnExplosionSparks(level, x, y, z, power, style);
            return;
        }
        int smokeCount = ExplosionSequence.smokeLobes(count);
        int baseCount = (smokeCount + 1) / 2;
        int priority = Math.min(4, smokeCount);
        // Interleave low and upper support, one rolling fire pocket, then surface response.
        // Close, large quads can consume the shared coverage allowance before a simple count cap;
        // this order retains the blast's distinct stages without reserving or increasing any budget.
        for (int slot = 0; slot < priority; slot++) {
            int i = smokeIndex(slot, baseCount, smokeCount);
            smoke(level, x, y, z, power, style, radius, i, baseCount, random);
        }
        fire(level, x, y, z, power, style, radius, 0, random);
        BlastGroundEffects.emit(level, x, y, z, power, style, direction);
        for (int slot = priority; slot < smokeCount; slot++) {
            int i = smokeIndex(slot, baseCount, smokeCount);
            smoke(level, x, y, z, power, style, radius, i, baseCount, random);
        }
        for (int i = 1; i < count - smokeCount; i++) fire(level, x, y, z, power, style, radius, i, random);
        ClientEffects.spawnExplosionSparks(level, x, y, z, power, style);
    }

    private static int smokeIndex(int slot, int baseCount, int total) {
        int paired = Math.min(baseCount, total - baseCount) * 2;
        return slot >= paired ? baseCount - 1 : slot % 2 == 0 ? slot / 2 : baseCount + slot / 2;
    }

    private static void smoke(ClientLevel level, double x, double y, double z, float power, BlastStyle style,
            float radius, int i, int baseCount, Random random) {
        boolean base = i < baseCount;
        int tier = i - baseCount;
        double angle = i * 2.3999632297 + random.nextDouble() * 0.6;
        double dx = Math.cos(angle), dz = Math.sin(angle);
        double spread = radius * (base ? 0.43 : 0.12 + random.nextDouble() * 0.16);
        double px = x + dx * spread, pz = z + dz * spread;
        double py = y + radius * (base ? 0.07 : 0.22 + tier * 0.12);
        float r = radius * (base ? 0.78F + random.nextFloat() * 0.18F : 0.70F + random.nextFloat() * 0.20F);
        if (!EffectAdmission.admitBlastLobe(level, px, py, pz, r)) return;
        double speed = radius * (base ? 0.055 : 0.009) * (0.8 + random.nextDouble() * 0.4);
        int delay = base ? i % 3 : 3 + Math.min(7, tier * 2);
        int life = Math.min(116, ExplosionSequence.life(power, style) + (base ? 12 : 20) + random.nextInt(8));
        Minecraft.getInstance().particleEngine.add(new BlastCloudParticle(level, px, py, pz,
                dx * speed, base ? 0.01 : 0.045 + Math.min(3, tier) * 0.018, dz * speed,
                r, life, 0, base ? 0.94F : 0.9F, delay, base,
                base || tier < 2 ? ExplosionSequence.fireTicks(power, style) : 0));
    }

    private static void fire(ClientLevel level, double x, double y, double z, float power, BlastStyle style,
            float radius, int i, Random random) {
        double angle = i * 2.3999632297 + random.nextDouble() * 0.6;
        double dx = Math.cos(angle), dz = Math.sin(angle);
        // Overlapping pockets punch through the developing smoke, rather than flying away
        // as a ring of identical fireballs. Each one leaves its own cooling smoke behind.
        double spread = i == 0 ? 0 : radius * (0.12 + random.nextDouble() * 0.12);
        double px = x + dx * spread, pz = z + dz * spread;
        boolean lingering = style == BlastStyle.INCENDIARY;
        double py = y + radius * (lingering ? 0.07 : 0.10 + random.nextDouble() * 0.10);
        float r = radius * (i == 0 ? 1.05F : 0.60F + random.nextFloat() * 0.25F);
        if (!EffectAdmission.admitBlastLobe(level, px, py, pz, r)) return;
        double speed = i == 0 ? 0 : Math.min(0.7, radius * 0.07) * (0.7 + random.nextDouble() * 0.3);
        int fireTicks = ExplosionSequence.fireTicks(power, style) * (55 + random.nextInt(40)) / 100;
        int life = Math.min(100, ExplosionSequence.life(power, style) + random.nextInt(8));
        Minecraft.getInstance().particleEngine.add(new BlastCloudParticle(level, px, py, pz,
                dx * speed, lingering ? 0.008 : 0.04 + random.nextDouble() * 0.06, dz * speed, r, life,
                fireTicks, 0.95F, i == 0 ? 0 : 1 + i % 4, lingering));
    }

    /** Earlier McHeli growing puffs and two-way ejection, within the same primary request cap. */
    private static void oldSmoke(ClientLevel level, double x, double y, double z, float power, BlastStyle style, Random random, Vec3 axis) {
        float nominal = power < 8 ? power * 2 : 16;
        var quality = ClientConfig.get(ClientConfig.EFFECT_BUDGET);
        nominal = ExplosionLook.puffSize(nominal, power, style, quality);
        if (!EffectAdmission.admitBlastLobe(level, x, y, z, nominal * 0.2F)) return;
        double angle = random.nextDouble() * Math.PI * 2;
        double speed = Math.min(0.7, power * 0.09) * (0.5 + random.nextDouble() * 0.5);
        boolean upward = random.nextBoolean();
        boolean directed = !axis.equals(Vec3.ZERO);
        SmokeParticle puff = new SmokeParticle(level, x, y, z,
                Math.cos(angle) * speed * (upward ? 0.1 : directed ? 0.5 : 1) + axis.x * speed * 0.8,
                speed * (upward ? directed ? 0.9 : 1.2 : 0.2) + axis.y * speed * 0.5,
                Math.sin(angle) * speed * (upward ? 0.1 : directed ? 0.5 : 1) + axis.z * speed * 0.8);
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
