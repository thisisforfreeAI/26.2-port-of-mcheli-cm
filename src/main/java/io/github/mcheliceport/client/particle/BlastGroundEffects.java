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
        emit(level, x, y, z, power, style, direction, 0);
    }

    static void emit(ClientLevel level, double x, double y, double z, float power, BlastStyle style,
            BlastDirection direction, float footprint) {
        emit(level, x, y, z, power, style, direction, footprint, BlastSurfaceFootprint.EMPTY);
    }

    static void emit(ClientLevel level, double x, double y, double z, float power, BlastStyle style,
            BlastDirection direction, float footprint, BlastSurfaceFootprint surface) {
        var quality = ClientConfig.get(ClientConfig.EFFECT_BUDGET);
        if (BlastGroundLook.requests(power, style, quality, EffectPerformance.pressure()) == 0
                || !EffectAdmission.inRange(level, x, y, z, 4)) return;
        prepare(level);
        boolean centerFound = center(level, x, y, z, power, style, direction);
        // A deep or unsupported center must not suppress dust on the surviving crater rim.
        // Airbursts still require a nearby surface: nominal rings search at most three blocks.
        ring(level, x, y, z, power, style, new Random(Double.doubleToLongBits(x)
                ^ Long.rotateLeft(Double.doubleToLongBits(z), 21) ^ level.getGameTime() ^ 0xD057L), footprint, surface, !centerFound);
    }

    private static boolean center(ClientLevel level, double x, double y, double z, float power,
            BlastStyle style, BlastDirection direction) {
        var axis = direction.burstAxis();
        var quality = ClientConfig.get(ClientConfig.EFFECT_BUDGET);
        int pressure = EffectPerformance.pressure();
        int count = BlastGroundLook.requests(power, style, quality, pressure);
        if (count == 0 || !EffectAdmission.inRange(level, x, y, z, 4)) return false;
        prepare(level);
        BlockPos.MutableBlockPos pos = BlockPos.containing(x, y, z).mutable();
        for (int depth = 0; depth <= Math.min(12, (int) (power * 1.5F)); depth++, pos.move(0, -1, 0)) {
            if (sampled >= BlastGroundLook.surfaceReadCap(quality) || pos.getY() < level.getMinY() || !level.hasChunkAt(pos)) return false;
            sampled++;
            BlockState ground = level.getBlockState(pos);
            if (!ground.getFluidState().isEmpty() || ground.is(BlockTags.LEAVES)) return false;
            double height = surfaceHeight(level, pos, ground);
            if (height <= 0) continue;
            if (ground.is(Blocks.MOVING_PISTON) || !ground.shouldSpawnTerrainParticles()) return false;
            double surfaceY = pos.getY() + height;
            if (surfaceY > y + 0.25) return false;
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
                boolean fragment = BlastGroundLook.fragmentSlot(i, count, broad, jet, material);
                if (fragment) {
                    if (emitted >= BlastGroundLook.fragmentTickCap(quality, pressure)
                            || fragments.size() >= BlastGroundLook.fragmentLiveCap(quality)) continue;
                    boolean largeChunk = BlastDepthLook.chunkScale(power,broad,i)>1;
                    float radius = BlastGroundLook.fragmentRadius(power, style, material) * (0.75F + random.nextFloat() * 0.25F)
                            * BlastDepthLook.chunkScale(power, broad, i);
                    double py = surfaceY + radius + 0.15;
                    if (!EffectAdmission.admitFragment(level, x, py, z, radius)) continue;
                    double speed = Math.min(0.9, 0.15 + power * 0.045) * (0.65 + random.nextDouble() * 0.35) * (broad ? 1 : 0.65);
                    BlockState block = material == BlastGroundLook.Surface.SOIL ? Blocks.DIRT.defaultBlockState()
                            : material == BlastGroundLook.Surface.SNOW ? Blocks.SNOW_BLOCK.defaultBlockState() : ground;
                    boolean hot = BlastGroundLook.hotFragment(power, style, material, i);
                    var particle = new BlastFragmentParticle(level, x, py, z, dx * speed,
                            (0.25 + speed * (0.5 + random.nextDouble() * 0.5)) * (largeChunk ? 0.65 : 1), dz * speed,
                            block, pos.immutable(), surfaceY, radius,
                            largeChunk ? 48 + random.nextInt(12) : 26 + random.nextInt(10), hot);
                    Minecraft.getInstance().particleEngine.add(particle);
                    fragments.add(new WeakReference<>(particle)); emitted++;
                } else if (jet) {
                    float radius = Math.min(3, 0.7F + power * 0.15F) * (0.75F + random.nextFloat() * 0.25F);
                    double py = surfaceY + radius * 0.6;
                    boolean column = EffectAdmission.depthDetail(level,x,py,z);
                    if (!EffectAdmission.admitGeometry(level,x,py,z,radius*(column?2.2F:1),column?4:1)) {
                        if (!column || !EffectAdmission.admit(level,x,py,z,false,radius)) continue;
                        column=false;
                    }
                    double speed = Math.min(0.85, 0.2 + power * 0.05) * (0.65 + random.nextDouble() * 0.35);
                    Minecraft.getInstance().particleEngine.add(new BlastDirtJetParticle(level, x, py, z,
                            dx * speed, speed * (0.6 + random.nextDouble() * 0.8), dz * speed, radius,
                            tint[0], tint[1], tint[2], 18 + random.nextInt(10), material.opacity, column));
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
            return true;
        }
        return false;
    }

    private static void ring(ClientLevel level, double x, double y, double z, float power, BlastStyle style,
            Random random, float footprint, BlastSurfaceFootprint surface, boolean missingCenter) {
        if (!ClientConfig.get(ClientConfig.BLAST_DUST_RING)) return;
        var quality = ClientConfig.get(ClientConfig.EFFECT_BUDGET);
        int sectors = BlastRingLook.sectors(power, style, quality, EffectPerformance.pressure(), footprint);
        float reach = ExplosionCraterScale.groundReach(BlastRingLook.reach(power), footprint);
        float radius = Math.min(2.5F, Math.max(BlastRingLook.width(power), 0.6F + reach * 0.09F));
        double phase = random.nextDouble() * Math.PI * 2;
        for (int i = 0; i < sectors && ringEmitted < BlastRingLook.tickCap(quality); i++) {
            double angle = phase + (i + random.nextDouble() * 0.35) * Math.PI * 2 / sectors;
            double dx = Math.cos(angle), dz = Math.sin(angle);
            float localReach = surface.reach(angle, reach);
            if (localReach < .5F) continue;
            double startRadius = footprint > 0 ? localReach * (.64 + random.nextDouble() * .30) : Math.min(2, reach * .25);
            double px = x + dx * startRadius, pz = z + dz * startRadius;
            double top = footprint > 0 ? surface.surfaceTop(angle, y) : y;
            BlockPos.MutableBlockPos at = BlockPos.containing(px, top + .25, pz).mutable();
            BlockState ground = Blocks.AIR.defaultBlockState();
            double height = 0;
            for (int depth = 0; depth <= (footprint > 0 ? 8 : 3); depth++, at.move(0, -1, 0)) {
                if (!level.hasChunkAt(at) || at.getY() < level.getMinY()
                        || sampled >= BlastGroundLook.surfaceReadCap(quality)) break;
                sampled++;
                ground = level.getBlockState(at);
                if (!ground.getFluidState().isEmpty() || ground.is(BlockTags.LEAVES)) break;
                height = surfaceHeight(level, at, ground);
                if (height > 0) break;
            }
            if (height <= 0 || !ground.getFluidState().isEmpty() || ground.is(BlockTags.LEAVES)) continue;
            if (footprint <= 0 && Math.abs(at.getY() + height - y) > 3) continue;
            // Each sector already sampled its own supporting block. Reuse it for color and
            // motion so fronts crossing sand/grass/stone do not carry the origin's material.
            var sectorMaterial = material(ground);
            float[] sectorTint = tint(level, at, ground, sectorMaterial);
            // Deep holes lose their central ejecta. Replace a few rim wisps with narrow dirt jets.
            // Same sector/tick cap and same sampled block; no new emitter or surface reads.
            if (missingCenter && power>=6 && i%5==1 && sectorMaterial!=BlastGroundLook.Surface.SOLID) {
                float jetRadius=Math.min(2.5F,.65F+power*.1F);
                double py=at.getY()+height+jetRadius*.6;
                if(!EffectAdmission.admitGeometry(level,px,py,pz,jetRadius,1))continue;
                double speed=Math.min(.8,.25+power*.025)*( .75+random.nextDouble()*.25);
                Minecraft.getInstance().particleEngine.add(new BlastDirtJetParticle(level,px,py,pz,dx*speed*.55,
                        speed,dz*speed*.55,jetRadius,sectorTint[0],sectorTint[1],sectorTint[2],22,sectorMaterial.opacity));
                ringEmitted++;continue;
            }
            boolean mesh = i%2==0 && EffectAdmission.depthDetail(level,px,y,pz);
            double py = at.getY() + height + (mesh ? 0.035 : radius * 0.3);
            if (!EffectAdmission.admitGeometry(level,px,py,pz,radius*(mesh?1.6F:1),mesh?4:1)) {
                if (!mesh || !EffectAdmission.admit(level,px,py,pz,false,radius)) continue;
                mesh=false; py=at.getY()+height+radius*.3;
            }
            Minecraft.getInstance().particleEngine.add(new BlastRingParticle(level, px, py, pz, dx, dz,
                    (float) (footprint > 0 ? Math.max(0.5, localReach * 1.08 - startRadius) : reach) * sectorMaterial.speedScale, radius, sectorTint,
                    sectorMaterial.opacity * (0.75F + random.nextFloat() * 0.1F), mesh));
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
