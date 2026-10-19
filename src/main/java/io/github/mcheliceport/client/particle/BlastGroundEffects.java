package io.github.mcheliceport.client.particle;

import io.github.mcheliceport.config.ClientConfig;
import io.github.mcheliceport.effect.BlastStyle;
import io.github.mcheliceport.effect.BlastDirection;
import java.lang.ref.WeakReference;
import java.util.ArrayDeque;
import java.util.Random;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.BlockPos;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;

/** One loaded surface sample supplies dust and a globally bounded set of cosmetic fragments. */
final class BlastGroundEffects {
    private static WeakReference<ClientLevel> world = new WeakReference<>(null);
    private static final ArrayDeque<WeakReference<BlastFragmentParticle>> fragments = new ArrayDeque<>();
    private static long tick = Long.MIN_VALUE;
    private static int emitted, sampled, ringEmitted;

    private BlastGroundEffects() {}

    static void emit(ClientLevel level, double x, double y, double z, float power, BlastStyle style) {
        emit(level, x, y, z, power, style, BlastDirection.NONE);
    }

    static void emit(ClientLevel level, double x, double y, double z, float power, BlastStyle style, BlastDirection direction) {
        var axis = direction.burstAxis();
        var quality = ClientConfig.get(ClientConfig.EFFECT_BUDGET);
        int pressure = EffectPerformance.pressure();
        int count = BlastGroundLook.requests(power, style, quality, pressure);
        if (count == 0 || !EffectAdmission.inRange(level, x, y, z, 4)) return;
        prepare(level);
        BlockPos.MutableBlockPos pos = BlockPos.containing(x, y, z).mutable();
        for (int depth = 0; depth <= Math.min(12, (int) (power * 1.5F)); depth++, pos.move(0, -1, 0)) {
            if (sampled >= BlastGroundLook.surfaceReadCap(quality) || pos.getY() < level.getMinY() || !level.hasChunkAt(pos)) return;
            sampled++;
            BlockState ground = level.getBlockState(pos);
            if (!ground.getFluidState().isEmpty() || ground.is(BlockTags.LEAVES)) return;
            double height = surfaceHeight(level, pos, ground);
            if (height <= 0) continue;
            if (ground.is(Blocks.MOVING_PISTON) || !ground.shouldSpawnTerrainParticles()) return;
            double surfaceY = pos.getY() + height;
            if (surfaceY > y + 0.25) return;
            var material = material(ground);
            float[] tint = tint(level, pos, ground, material);
            Random random = new Random(Double.doubleToLongBits(x)
                    ^ Long.rotateLeft(Double.doubleToLongBits(z), 21) ^ level.getGameTime() ^ 0xD057L);
            for (int i = 0; i < count; i++) {
                boolean jet = ExplosionLook.broad(style) ? i % 3 == 1 : i == 0 && !direction.equals(BlastDirection.NONE);
                int sector = ExplosionLook.broad(style) && i % 3 == 2 ? i - 1 : i;
                double angle = (sector + random.nextDouble() * 0.5) * Math.PI * 2 / count;
                double dx = Math.cos(angle), dz = Math.sin(angle);
                if (!ExplosionLook.broad(style)) { dx = dx * 0.4 + axis.x; dz = dz * 0.4 + axis.z; }
                boolean broad = ExplosionLook.broad(style);
                boolean fragment = count > 1 && (broad ? i % 3 == 2 : i == count - 1);
                if (fragment) {
                    if (emitted >= BlastGroundLook.fragmentTickCap(quality, pressure)
                            || fragments.size() >= BlastGroundLook.fragmentLiveCap(quality)) continue;
                    float radius = BlastGroundLook.fragmentRadius(power, style, material) * (0.75F + random.nextFloat() * 0.25F);
                    double py = surfaceY + radius + 0.15;
                    if (!EffectAdmission.admitFragment(level, x, py, z, radius)) continue;
                    double speed = Math.min(0.9, 0.15 + power * 0.045) * (0.65 + random.nextDouble() * 0.35) * (broad ? 1 : 0.65);
                    BlockState block = material == BlastGroundLook.Surface.SOIL ? Blocks.DIRT.defaultBlockState()
                            : material == BlastGroundLook.Surface.SNOW ? Blocks.SNOW_BLOCK.defaultBlockState() : ground;
                    boolean hot = BlastGroundLook.hotFragment(power, style, material, i);
                    var particle = new BlastFragmentParticle(level, x, py, z, dx * speed,
                            0.25 + speed * (0.5 + random.nextDouble() * 0.5), dz * speed,
                            block, pos.immutable(), surfaceY, radius, 26 + random.nextInt(10), hot);
                    Minecraft.getInstance().particleEngine.add(particle);
                    fragments.add(new WeakReference<>(particle)); emitted++;
                } else if (jet) {
                    float radius = Math.min(3, 0.7F + power * 0.15F) * (0.75F + random.nextFloat() * 0.25F);
                    double py = surfaceY + radius * 0.6;
                    if (!EffectAdmission.admit(level, x, py, z, false, radius)) continue;
                    double speed = Math.min(0.85, 0.2 + power * 0.05) * (0.65 + random.nextDouble() * 0.35);
                    Minecraft.getInstance().particleEngine.add(new BlastDirtJetParticle(level, x, py, z,
                            dx * speed, speed * (0.6 + random.nextDouble() * 0.8), dz * speed, radius,
                            tint[0], tint[1], tint[2], 18 + random.nextInt(10), material.opacity));
                } else {
                    float radius = BlastGroundLook.dustRadius(power, style, material);
                    double px = x + dx * 0.6, pz = z + dz * 0.6;
                    if (!EffectAdmission.admit(level, px, surfaceY + 0.35, pz, false, radius)) continue;
                    double speed = Math.min(1.25, 0.3 + power * 0.07) * material.speedScale
                            * (0.7 + random.nextDouble() * 0.3) * (broad ? 1 : 0.5);
                    Minecraft.getInstance().particleEngine.add(new BlastGroundDustParticle(level,
                            px, surfaceY + 0.35, pz, dx * speed, dz * speed, radius,
                            tint[0], tint[1], tint[2], material.life + random.nextInt(8), material.opacity));
                }
            }
            if (y - surfaceY <= Math.min(3, power * 0.4F))
                ring(level, x, surfaceY, z, power, style, tint, material, random);
            return;
        }
    }

    private static void ring(ClientLevel level, double x, double y, double z, float power, BlastStyle style,
            float[] tint, BlastGroundLook.Surface material, Random random) {
        if (!ClientConfig.get(ClientConfig.BLAST_DUST_RING)) return;
        var quality = ClientConfig.get(ClientConfig.EFFECT_BUDGET);
        int sectors = BlastRingLook.sectors(power, style, quality, EffectPerformance.pressure());
        float radius = BlastRingLook.width(power);
        double phase = random.nextDouble() * Math.PI * 2;
        for (int i = 0; i < sectors && ringEmitted < BlastRingLook.tickCap(quality); i++) {
            double angle = phase + (i + random.nextDouble() * 0.35) * Math.PI * 2 / sectors;
            double dx = Math.cos(angle), dz = Math.sin(angle);
            double startRadius = Math.min(2, BlastRingLook.reach(power) * 0.25);
            double px = x + dx * startRadius, pz = z + dz * startRadius;
            BlockPos at = BlockPos.containing(px, y - 0.01, pz);
            if (!level.hasChunkAt(at) || sampled >= BlastGroundLook.surfaceReadCap(quality)) continue;
            sampled++;
            BlockState ground = level.getBlockState(at);
            if (!ground.getFluidState().isEmpty() || ground.is(BlockTags.LEAVES)) continue;
            double height = surfaceHeight(level, at, ground);
            if (height <= 0 || Math.abs(at.getY() + height - y) > 0.6) continue;
            double py = at.getY() + height + radius * 0.3;
            if (!EffectAdmission.admit(level, px, py, pz, false, radius)) continue;
            Minecraft.getInstance().particleEngine.add(new BlastRingParticle(level, px, py, pz, dx, dz,
                    BlastRingLook.reach(power) * material.speedScale, radius, tint,
                    material.opacity * (0.75F + random.nextFloat() * 0.1F)));
            ringEmitted++;
        }
    }

    static BlastGroundLook.Surface material(BlockState state) {
        if (state.is(Blocks.SAND) || state.is(Blocks.RED_SAND) || state.is(Blocks.GRAVEL)) return BlastGroundLook.Surface.SAND;
        if (state.is(Blocks.SNOW) || state.is(Blocks.SNOW_BLOCK) || state.is(Blocks.POWDER_SNOW)) return BlastGroundLook.Surface.SNOW;
        if (state.is(Blocks.GRASS_BLOCK) || state.is(Blocks.MYCELIUM) || state.is(Blocks.PODZOL)
                || state.is(BlockTags.DIRT) || state.is(Blocks.MUD) || state.is(Blocks.FARMLAND)
                || state.is(Blocks.DIRT_PATH)) return BlastGroundLook.Surface.SOIL;
        if (state.is(BlockTags.LOGS) || state.is(BlockTags.PLANKS)) return BlastGroundLook.Surface.WOOD;
        return BlastGroundLook.Surface.SOLID;
    }

    static double surfaceHeight(ClientLevel level, BlockPos pos, BlockState state) {
        if (state.is(Blocks.POWDER_SNOW)) return 1;
        var shape = state.is(Blocks.SNOW) ? state.getShape(level, pos) : state.getCollisionShape(level, pos);
        return shape.isEmpty() ? 0 : shape.bounds().maxY;
    }

    private static float[] tint(ClientLevel level, BlockPos pos, BlockState state, BlastGroundLook.Surface material) {
        return switch (material) {
            case SNOW -> new float[] {0.94F, 0.97F, 1};
            case SOIL -> new float[] {0.43F, 0.34F, 0.23F};
            case WOOD -> ClientEffects.groundTint(level, pos, state, 0.5F, 0.4F, 0.28F);
            default -> ClientEffects.groundTint(level, pos, state, 0.6F, 0.58F, 0.53F);
        };
    }

    private static void prepare(ClientLevel level) {
        if (world.get() != level) { clear(); world = new WeakReference<>(level); }
        if (tick != level.getGameTime()) {
            tick = level.getGameTime(); emitted = sampled = ringEmitted = 0;
            fragments.removeIf(ref -> { var particle = ref.get(); return particle == null || !particle.isAlive(); });
        }
    }

    static void clear() { fragments.clear(); world.clear(); tick = Long.MIN_VALUE; emitted = sampled = ringEmitted = 0; }
    static int trackedFragments() { return fragments.size(); }
    static int emittedFragments() { return emitted; }
    static int surfaceReads() { return sampled; }
    static int ringEmitted() { return ringEmitted; }
}
