package io.github.mcheliceport.client.particle;

import io.github.mcheliceport.config.EffectQuality;
import io.github.mcheliceport.config.ClientConfig;
import io.github.mcheliceport.effect.McheliEffects;
import io.github.mcheliceport.effect.BlastStyle;
import io.github.mcheliceport.client.effect.BlastMarks;
import io.github.mcheliceport.effect.ParticleParam;
import java.util.List;
import java.util.Locale;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.particle.Particle;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.BlockParticleOption;
import net.minecraft.core.particles.ColorParticleOption;
import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.core.particles.ParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.core.particles.SpellParticleOption;
import net.minecraft.server.level.ParticleStatus;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.state.BlockState;
import org.jspecify.annotations.Nullable;

/**
 * The client's {@link McheliEffects.Sink}: upstream {@code MCH_ParticlesUtil}.
 */
public final class ClientEffects implements McheliEffects.Sink {

    public static final ClientEffects INSTANCE = new ClientEffects();
    /** Upstream's draw distance for particles named by content. */
    private static final double MAX_DISTANCE = 300.0;

    private ClientEffects() {
    }
    @Override public boolean projectileTrail(io.github.mcheliceport.entity.weapon.BaseBulletEntity projectile,String name,int count,float size){
        return ProjectileTrailEffects.emit(projectile,name,count,size);
    }

    @Override public boolean damageSmoke(io.github.mcheliceport.entity.aircraft.AircraftEntity aircraft) {
        return VehicleAtmosphere.handlesDamage(aircraft);
    }

    @Override public boolean engineExhaust(io.github.mcheliceport.entity.aircraft.AircraftEntity aircraft) {
        return EffectQuality.enhanced() && EngineExhaustEmitter.emit(aircraft);
    }

    @Override
    public boolean rotorWash(io.github.mcheliceport.entity.aircraft.AircraftEntity aircraft, boolean seaOnly) {
        if (!EffectQuality.enhanced() || seaOnly || !(aircraft instanceof io.github.mcheliceport.entity.heli.HeliEntity heli)) return false;
        RotorWashEmitter.emit(heli);
        return true;
    }

    @Override
    public void spawnParticle(Level level, ParticleParam p) {
        if (!(level instanceof ClientLevel clientLevel)) {
            return;
        }
        if (!EffectAdmission.admit(clientLevel, p.posX, p.posY, p.posZ, false,
                Math.abs(p.size) * (p.diffusible ? 0.2F : 0.1F))) {
            return;
        }
        SmokeParticle fx = p.name.equalsIgnoreCase("Splash")
                ? new SplashParticle(clientLevel, p.posX, p.posY, p.posZ, p.motionX, p.motionY, p.motionZ)
                : new SmokeParticle(clientLevel, p.posX, p.posY, p.posZ, p.motionX, p.motionY, p.motionZ);
        boolean enhanced = EffectQuality.enhanced();
        fx.enhanced = enhanced;
        if (enhanced && p.groundState != null) {
            float[] tint = groundTint(clientLevel, BlockPos.containing(p.posX, p.posY, p.posZ), p.groundState, p.r,
                    p.g, p.b);
            fx.setColor(tint[0], tint[1], tint[2]);
        } else {
            fx.setColor(p.r, p.g, p.b);
        }
        if (enhanced && p.name.equalsIgnoreCase("explode")) {
            // M8 fireballs: explosion puffs are born as fire and cool to their own smoke colour.
            fx.heat = 0.6F + clientLevel.getRandom().nextFloat() * 0.4F;
            fx.coolingShare = p.coolingShare;
        }
        if (enhanced && p.damageHeat > 0 && !(fx instanceof SplashParticle)
                && !clientLevel.getFluidState(BlockPos.containing(p.posX, p.posY, p.posZ))
                        .is(net.minecraft.tags.FluidTags.WATER)) {
            fx.heat = Math.clamp(p.damageHeat, 0, 1);
            fx.coherentDrift = true;
        }
        fx.setAlphaF(p.a);
        if (p.age > 0) {
            fx.setParticleMaxAge(p.age);
        }
        fx.motionYUpAge = p.motionYUpAge;
        fx.gravityFactor = p.gravity;
        fx.isEffectedWind = p.isEffectWind;
        fx.diffusible = p.diffusible;
        fx.toWhite = p.toWhite;
        if (p.diffusible) {
            fx.particleScale = p.size * (enhanced ? Math.clamp(p.diffusionStart, 0.2F, 1.0F) : 0.2F);
            fx.particleMaxScale = p.size * 2.0F;
        } else {
            fx.particleScale = p.size;
        }
        Minecraft.getInstance().particleEngine.add(fx);
    }

    @Override
    public void spawnMuzzleFlash(Level level, double x, double y, double z, float size, float r, float g, float b,
            float a, int age) {
        if (!(level instanceof ClientLevel clientLevel)) {
            return;
        }
        if (!EffectAdmission.admit(clientLevel, x, y, z, true)) {
            return;
        }
        FlashParticle fx = new FlashParticle(clientLevel, x, y, z, size, age);
        fx.setParticleMaxAge(age);
        fx.setColor(r, g, b);
        fx.setAlphaF(a);
        boolean enhanced = EffectQuality.enhanced();
        fx.enhanced = enhanced;
        Minecraft.getInstance().particleEngine.add(fx);
        if (enhanced && optionalGlow(clientLevel, x, y, z, size * 2.08F)) {
            // M8: a short warm glow around the flash.
            Minecraft.getInstance().particleEngine.add(new GlowParticle(clientLevel, x, y, z, size * 1.6F, 0.3F,
                    3, Math.min(1.0F, r * 1.1F), g * 0.85F, b * 0.6F, a * 0.55F));
        }
    }

    /** M8 glow under an explosion's fireball (not in water). */
    static void spawnExplosionGlow(ClientLevel level, double x, double y, double z, float size, BlastStyle style) {
        float radius = ExplosionLook.glowRadius(size, style, ClientConfig.get(ClientConfig.EFFECT_BUDGET));
        if (!optionalGlow(level, x, y, z, radius * 1.5F)) {
            return;
        }
        int life = ExplosionLook.broad(style) || style == BlastStyle.CANNON
                ? (int) Math.min(6.0F + size, 14.0F) : 5;
        Minecraft.getInstance().particleEngine.add(new GlowParticle(level, x, y, z, radius, 0.5F, life,
                1.0F, 0.6F, 0.25F, 0.9F));
    }

    /** Independent cosmetic seed preserves the existing emitters' world RNG sequence. */
    static void spawnExplosionSparks(ClientLevel level, double x, double y, double z, float size) {
        spawnExplosionSparks(level, x, y, z, size, BlastStyle.HEAVY);
    }

    static void spawnExplosionSparks(ClientLevel level, double x, double y, double z, float size, BlastStyle style) {
        int count = EffectTuning.sparkCount(size, ClientConfig.get(ClientConfig.EFFECT_BUDGET), EffectPerformance.pressure());
        if (count == 0) return;
        java.util.Random random = new java.util.Random(Double.doubleToLongBits(x)
                ^ Long.rotateLeft(Double.doubleToLongBits(y), 21)
                ^ Long.rotateLeft(Double.doubleToLongBits(z), 42) ^ level.getGameTime());
        for (int i = 0; i < count; i++) {
            boolean broad = ExplosionLook.broad(style);
            float radius = broad ? Math.clamp(size * 0.018F, 0.08F, 0.22F) : 0.08F;
            if (!EffectAdmission.admit(level, x, y, z, false, radius)) continue;
            double angle = (i + random.nextDouble()) * Math.PI * 2 / count;
            double speed = broad ? Math.min(1.2, 0.2 + size * 0.065) * (0.6 + random.nextDouble() * 0.4)
                    : 0.12 + random.nextDouble() * 0.16;
            boolean tight = style == BlastStyle.BLAST || style == BlastStyle.PENETRATOR;
            Minecraft.getInstance().particleEngine.add(new SparkParticle(level, x, y, z,
                    Math.cos(angle) * speed, (tight ? 0.08 : speed * 0.25) + random.nextDouble() * (tight ? 0.16 : speed * 0.5),
                    Math.sin(angle) * speed, tight ? 5 + random.nextInt(3) : 10 + random.nextInt(5), radius));
        }
    }

    static void spawnExplosionAccents(ClientLevel level, double x, double y, double z, float size, BlastStyle style) {
        BlastGroundEffects.emit(level, x, y, z, size, style);
    }

    /** Optional glow shares the smoke budget, keeping the flash budget available for weapon cues. */
    private static boolean optionalGlow(ClientLevel level, double x, double y, double z, float radius) {
        return EffectPerformance.optionalGlows()
                && EffectAdmission.admit(level, x, y, z, false, radius);
    }

    /**
     * M8 downwash colour: the particle's grey drawn toward the colour of the ground below (white
     * spray over water). Returns {@code r, g, b}.
     */
    static float[] groundTint(ClientLevel level, BlockPos pos, BlockState ground, float r, float g, float b) {
        if (ground.getFluidState().is(net.minecraft.tags.FluidTags.WATER)) {
            return new float[] {0.9F, 0.94F, 1.0F};
        }
        int col = ground.getMapColor(level, pos).col;
        float k = 0.6F;
        return new float[] {
                r + (((col >> 16) & 0xFF) / 255.0F - r) * k,
                g + (((col >> 8) & 0xFF) / 255.0F - g) * k,
                b + ((col & 0xFF) / 255.0F - b) * k};
    }

    @Override
    public void spawnVanilla(Level level, String name, double x, double y, double z, double mx, double my,
            double mz) {
        doSpawnParticle(level, name, null, null, x, y, z, mx, my, mz);
    }

    @Override
    public boolean spawnTileDust(Level level, int blockX, int blockY, int blockZ, double x, double y, double z,
            double mx, double my, double mz, float scale) {
        int[][] offset = {{0, 0, 0}, {0, 0, -1}, {0, 0, 1}, {1, 0, 0}, {-1, 0, 0}};
        for (int[] o : offset) {
            BlockPos pos = new BlockPos(blockX + o[0], blockY + o[1], blockZ + o[2]);
            BlockState state = level.getBlockState(pos);
            if (!state.isAir()) {
                Particle e = doSpawnParticle(level, "blockdust", state, pos, x, y, z, mx, my, mz);
                if (e instanceof BlockDustParticle dust) {
                    dust.setScale(scale * 2.0F);
                    return true;
                }
            }
        }
        return false;
    }

    @Override
    public void spawnTileCrack(Level level, int blockX, int blockY, int blockZ, double x, double y, double z,
            double mx, double my, double mz) {
        BlockPos pos = new BlockPos(blockX, blockY, blockZ);
        BlockState state = level.getBlockState(pos);
        if (!state.isAir()) {
            doSpawnParticle(level, "blockcrack", state, pos, x, y, z, mx, my, mz);
        }
    }

    @Override
    public void explosionEffects(Level level, double x, double y, double z, float size, boolean inWater,
            List<BlockPos> affectedBlocks) {
        explosionEffects(level, x, y, z, size, inWater, affectedBlocks, BlastStyle.BLAST);
    }

    @Override
    public void explosionEffects(Level level, double x, double y, double z, float size, boolean inWater,
            List<BlockPos> affectedBlocks, BlastStyle style) {
        explosionEffects(level, x, y, z, size, inWater, affectedBlocks, style,
                io.github.mcheliceport.effect.BlastDirection.NONE);
    }

    @Override public void explosionEffects(Level level, double x, double y, double z, float size, boolean inWater,
            List<BlockPos> affectedBlocks, BlastStyle style, io.github.mcheliceport.effect.BlastDirection direction) {
        if (level instanceof ClientLevel clientLevel) {
            ExplosionEffects.play(clientLevel, x, y, z, size, inWater, affectedBlocks, style, direction);
            if (!inWater) BlastMarks.add(clientLevel, x, y, z, size, style);
        }
    }

    /**
     * Upstream {@code doSpawnParticle}: nothing beyond 300 blocks of the camera or with minimal
     * particles, and a third of them dropped with decreased particles.
     */
    static @Nullable Particle doSpawnParticle(Level level, String type, @Nullable BlockState state,
            @Nullable BlockPos pos, double x, double y, double z, double mx, double my, double mz) {
        Minecraft mc = Minecraft.getInstance();
        Entity view = mc.getCameraEntity();
        if (view == null || !(level instanceof ClientLevel clientLevel)) {
            return null;
        }
        int setting = mc.options.particles().get().ordinal();
        if (setting == ParticleStatus.DECREASED.ordinal() && level.getRandom().nextInt(3) == 0) {
            setting = ParticleStatus.MINIMAL.ordinal();
        }
        double dx = view.getX() - x;
        double dy = view.getY() - y;
        double dz = view.getZ() - z;
        if (dx * dx + dy * dy + dz * dz > MAX_DISTANCE * MAX_DISTANCE || setting > 1) {
            return null;
        }
        String key = type.toLowerCase(Locale.ROOT);
        if (key.startsWith("blockdust")) {
            if (state == null || state.getRenderShape() == RenderShape.INVISIBLE) {
                return null;
            }
            if (!EffectAdmission.admit(clientLevel, x, y, z, false)) return null;
            BlockDustParticle dust = new BlockDustParticle(clientLevel, x, y, z, mx, my, mz, state,
                    pos != null ? pos : BlockPos.containing(x, y, z));
            mc.particleEngine.add(dust);
            return dust;
        }
        ParticleOptions options = vanillaParticle(key, state);
        return options == null ? null : mc.particleEngine.createParticle(options, x, y, z, mx, my, mz);
    }

    /** The modern particle for a 1.12 particle name (upstream's {@code doSpawnParticle} table). */
    static @Nullable ParticleOptions vanillaParticle(String key, @Nullable BlockState state) {
        return switch (key) {
            case "hugeexplosion" -> ParticleTypes.EXPLOSION_EMITTER;
            case "largeexplode" -> ParticleTypes.EXPLOSION;
            case "fireworksspark" -> ParticleTypes.FIREWORK;
            case "bubble" -> ParticleTypes.BUBBLE;
            case "suspended" -> ParticleTypes.UNDERWATER;
            case "depthsuspend", "townaura" -> ParticleTypes.MYCELIUM;
            case "crit" -> ParticleTypes.CRIT;
            case "magiccrit" -> ParticleTypes.ENCHANTED_HIT;
            case "smoke" -> ParticleTypes.SMOKE;
            case "mobspell" -> ColorParticleOption.create(ParticleTypes.ENTITY_EFFECT, 1.0F, 1.0F, 1.0F);
            case "mobspellambient" -> ColorParticleOption.create(ParticleTypes.ENTITY_EFFECT, 0x26FFFFFF);
            case "spell" -> SpellParticleOption.create(ParticleTypes.EFFECT, 0xFFFFFF, 1.0F);
            case "instantspell" -> SpellParticleOption.create(ParticleTypes.INSTANT_EFFECT, 0xFFFFFF, 1.0F);
            case "witchmagic" -> ParticleTypes.WITCH;
            case "note" -> ParticleTypes.NOTE;
            case "portal" -> ParticleTypes.PORTAL;
            case "enchantmenttable" -> ParticleTypes.ENCHANT;
            case "explode" -> ParticleTypes.POOF;
            case "flame" -> ParticleTypes.FLAME;
            case "lava" -> ParticleTypes.LAVA;
            case "splash" -> ParticleTypes.SPLASH;
            case "wake" -> ParticleTypes.FISHING;
            case "largesmoke" -> ParticleTypes.LARGE_SMOKE;
            case "cloud" -> ParticleTypes.CLOUD;
            case "reddust" -> new DustParticleOptions(0xFF0000, 1.0F);
            case "snowballpoof", "snowshovel" -> ParticleTypes.ITEM_SNOWBALL;
            case "dripwater" -> ParticleTypes.DRIPPING_WATER;
            case "driplava" -> ParticleTypes.DRIPPING_LAVA;
            case "slime" -> ParticleTypes.ITEM_SLIME;
            case "heart" -> ParticleTypes.HEART;
            case "angryvillager" -> ParticleTypes.ANGRY_VILLAGER;
            case "happyvillager" -> ParticleTypes.HAPPY_VILLAGER;
            default -> key.startsWith("blockcrack") && state != null
                    ? new BlockParticleOption(ParticleTypes.BLOCK, state) : null;
        };
    }
}
