package io.github.mcheliceport.weapon.explosion;

import io.github.mcheliceport.net.ModNetwork;
import io.github.mcheliceport.effect.BlastStyle;
import io.github.mcheliceport.config.CommonConfig;
import io.github.mcheliceport.content.weapon.DamageFactor;
import io.github.mcheliceport.content.weapon.WeaponInfo;
import io.github.mcheliceport.net.ExplosionEffectPayload;
import io.github.mcheliceport.weapon.DamageScaling;
import io.github.mcheliceport.weapon.WeaponTargets;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.ExperienceOrb;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.item.FallingBlockEntity;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.throwableitemprojectile.ThrownExperienceBottle;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.EntityBasedExplosionDamageCalculator;
import net.minecraft.world.level.Explosion;
import net.minecraft.world.level.ExplosionDamageCalculator;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.ServerExplosion;
import net.minecraft.world.level.block.BaseFireBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.gameevent.GameEvent;
import net.minecraft.world.level.gamerules.GameRules;
import net.minecraft.world.level.material.FluidState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.event.EventHooks;
import org.jspecify.annotations.Nullable;

/**
 * McHeli's explosion: upstream {@code MCH_Explosion} and the server half of
 * {@code MCH_ExplosionV2} (decision D18).
 *
 * <p>It differs from vanilla's in upstream's ways: blocks and entities have separate sizes and
 * powers ({@code sizeBlock}, {@code blockPower}, {@code damagePower}, {@code damageRadius}),
 * explosions in water break fewer blocks, damage is {@code (d² + d) / 2 × 8 × damagePower + 1} with
 * McHeli's knockback (0.4 sideways, 0.1 up), entities can be set on fire, weapons can blast through
 * walls, and the config's and the weapon's damage factors apply. Blocks only break with
 * {@code explosions.destroyBlock}, the {@code mobGriefing} rule and an explicit request (weapons
 * ask for it with their {@code explosionBlock}).
 *
 * <p>It extends {@link ServerExplosion} only so vanilla and NeoForge code that expects one (block
 * {@code onExplosionHit}, the explosion events) works; vanilla's {@code explode()} is never called.
 *
 * <p>Differences from upstream: knockback is not computed a second time on the client (upstream's
 * client ran the entity part of the explosion again); players hit get their knockback through the
 * server's velocity update instead. The explosion events let protection mods cancel or filter it.
 */
public class McheliExplosion extends ServerExplosion {

    public final @Nullable Entity exploder;
    public final double x;
    public final double y;
    public final double z;
    public final float size;
    public final boolean causesFire;
    public final boolean damagesTerrain;
    public final ServerLevel world;
    public final @Nullable Player explodedPlayer;
    public boolean isDestroyBlock;
    public int countSetFireEntity;
    public boolean isPlaySound;
    public boolean isInWater;
    /** False for a wreck whose fire is cosmetic; the blast still looks and damages as incendiary. */
    public boolean fireBlocks = true;
    public float explosionSizeBlock;
    public float explosionDamagePower;
    public float explosionBlockPower;
    public float explosionDamageRadius;
    public @Nullable DamageFactor damageFactor;
    public boolean explosionThroughWall;
    public float explosionThroughWallFactor = 1.0F;
    private final List<BlockPos> affectedBlocks = new ArrayList<>();
    private final Result result = new Result();
    private final ExplosionDamageCalculator calculator;

    /** Upstream {@code MCH_Explosion.ExplosionResult}. */
    public static final class Result {
        public boolean hitEntity;
    }

    /** The parameters of upstream's longest {@code newExplosion} overload. */
    public record Params(@Nullable Entity exploder, @Nullable Entity player, double x, double y, double z, float size,
            float sizeBlock, float damagePower, float blockPower, float damageRadius, boolean playSound,
            boolean isFlaming, boolean isDestroyBlock, int countSetFireEntity, @Nullable DamageFactor damageFactor) {

        /** Upstream's short overload: damage and block power follow the sizes. */
        public static Params of(@Nullable Entity exploder, @Nullable Entity player, double x, double y, double z,
                float size, float sizeBlock, boolean playSound, boolean isFlaming, boolean isDestroyBlock,
                int countSetFireEntity, @Nullable DamageFactor damageFactor) {
            return new Params(exploder, player, x, y, z, size, sizeBlock, size, sizeBlock, size, playSound,
                    isFlaming, isDestroyBlock, countSetFireEntity, damageFactor);
        }
    }

    public McheliExplosion(ServerLevel level, @Nullable Entity exploder, @Nullable Entity player, double x, double y,
            double z, float size, float sizeBlock, boolean flaming, boolean damagesTerrain) {
        super(level, exploder, level.damageSources().explosion(exploder, player instanceof Player ? player : null),
                null, new Vec3(x, y, z), sizeBlock, flaming, Explosion.BlockInteraction.DESTROY_WITH_DECAY);
        this.world = level;
        this.exploder = exploder;
        this.explodedPlayer = player instanceof Player p ? p : null;
        this.x = x;
        this.y = y;
        this.z = z;
        this.size = size;
        this.causesFire = flaming;
        this.damagesTerrain = damagesTerrain;
        this.explosionSizeBlock = sizeBlock;
        this.explosionDamagePower = size;
        this.explosionBlockPower = 1.0F;
        this.explosionDamageRadius = size;
        this.isPlaySound = true;
        this.calculator = exploder == null ? new ExplosionDamageCalculator()
                : new EntityBasedExplosionDamageCalculator(exploder);
        // Reforged: through-wall blast settings come from the exploding projectile's weapon.
        if (exploder instanceof WeaponTargets.InfoHolder holder && holder.getWeaponInfo() != null) {
            WeaponInfo wi = holder.getWeaponInfo();
            this.explosionThroughWall = wi.explosionThroughWall;
            this.explosionThroughWallFactor = wi.explosionThroughWallFactor;
        }
    }

    /** Upstream {@code MCH_Explosion#newExplosion}: explodes in air and sends the effects to clients. */
    public static @Nullable Result newExplosion(Level level, Params p) {
        return explode(level, p, false, true);
    }

    /**
     * A destroyed vehicle's blast: incendiary as upstream, but it sets fire blocks only when
     * {@code wrecks.fireBlocks} is on; otherwise clients draw the wreck's own fuel fire.
     */
    public static @Nullable Result newWreckExplosion(Level level, @Nullable Entity player, double x, double y, double z,
            float size, float sizeBlock) {
        return explode(level, Params.of(null, player, x, y, z, size, sizeBlock, true, true, true, 5, null), false,
                CommonConfig.get(CommonConfig.WRECK_FIRE_BLOCKS));
    }

    /** Upstream {@code MCH_Explosion#newExplosionInWater}: blocks resist more, and clients see splashes. */
    public static @Nullable Result newExplosionInWater(Level level, Params p) {
        return explode(level, p, true, true);
    }

    /** Upstream's most used overload: {@code newExplosion(w, exploder, player, x, y, z, size, sizeBlock, playSound, smoking, flaming, destroyBlock, fire)}. */
    public static @Nullable Result newExplosion(Level level, @Nullable Entity exploder, @Nullable Entity player,
            double x, double y, double z, float size, float sizeBlock, boolean playSound, boolean isFlaming,
            boolean isDestroyBlock, int countSetFireEntity) {
        return newExplosion(level, Params.of(exploder, player, x, y, z, size, sizeBlock, playSound, isFlaming,
                isDestroyBlock, countSetFireEntity, null));
    }

    private static @Nullable Result explode(Level level, Params p, boolean inWater, boolean fireBlocks) {
        if (!(level instanceof ServerLevel serverLevel)) {
            return null;
        }
        McheliExplosion exp = new McheliExplosion(serverLevel, p.exploder(), p.player(), p.x(), p.y(), p.z(),
                p.size(), p.sizeBlock(), p.isFlaming(), serverLevel.getGameRules().get(GameRules.MOB_GRIEFING));
        exp.isDestroyBlock = p.isDestroyBlock();
        exp.explosionBlockPower = p.blockPower();
        exp.explosionDamagePower = p.damagePower();
        exp.explosionDamageRadius = p.damageRadius();
        exp.countSetFireEntity = p.countSetFireEntity();
        exp.isPlaySound = p.playSound();
        exp.isInWater = inWater;
        exp.fireBlocks = fireBlocks;
        exp.damageFactor = p.damageFactor();
        if (EventHooks.onExplosionStart(serverLevel, exp)) {
            return exp.result;
        }
        // Capture before damage/knockback callbacks; presentation never changes projectile physics.
        var direction = p.exploder() instanceof io.github.mcheliceport.entity.weapon.BaseBulletEntity bullet
                ? io.github.mcheliceport.effect.BlastDirection.from(bullet.getDeltaMovement())
                : io.github.mcheliceport.effect.BlastDirection.NONE;
        exp.doExplosionA();
        exp.doExplosionB();
        WeaponInfo visualWeapon = p.exploder() instanceof WeaponTargets.InfoHolder holder ? holder.getWeaponInfo() : null;
        BlastStyle style = BlastStyle.forWeapon(visualWeapon, p.isFlaming());
        ModNetwork.sendToPlayersNear(serverLevel, null, p.x(), p.y(), p.z(), ExplosionEffectPayload.RANGE,
                new ExplosionEffectPayload(p.x(), p.y(), p.z(), p.size(), inWater, exp.affectedBlocks, style, direction));
        return exp.result;
    }

    /**
     * The block part of upstream {@code doExplosionA}: 16×16×16 rays from the centre lose power
     * through the blocks they cross; every position reached with power left is affected (air
     * included, for the particles). On the client ({@code explosion} null) resistances come from
     * the block and fluid alone.
     */
    public static List<BlockPos> rayMarchBlocks(Level level, @Nullable McheliExplosion explosion, double x,
            double y, double z, float sizeBlock, float blockPower, boolean inWater, RandomSource rand) {
        Set<BlockPos> set = new HashSet<>();
        for (int i = 0; i < 16; i++) {
            for (int j = 0; j < 16; j++) {
                for (int k = 0; k < 16; k++) {
                    if (i != 0 && i != 15 && j != 0 && j != 15 && k != 0 && k != 15) {
                        continue;
                    }
                    double d3 = i / 15.0F * 2.0F - 1.0F;
                    double d4 = j / 15.0F * 2.0F - 1.0F;
                    double d5 = k / 15.0F * 2.0F - 1.0F;
                    double d6 = Math.sqrt(d3 * d3 + d4 * d4 + d5 * d5);
                    d3 /= d6;
                    d4 /= d6;
                    d5 /= d6;
                    float f1 = sizeBlock * (0.7F + rand.nextFloat() * 0.6F);
                    double d0 = x;
                    double d1 = y;
                    for (double d2 = z; f1 > 0.0F; f1 -= 0.22500001F) {
                        BlockPos pos = new BlockPos(Mth.floor(d0), Mth.floor(d1), Mth.floor(d2));
                        BlockState state = level.getBlockState(pos);
                        FluidState fluid = level.getFluidState(pos);
                        Optional<Float> resistance = explosion != null
                                ? explosion.calculator.getBlockExplosionResistance(explosion, level, pos, state, fluid)
                                : plainResistance(level, pos, state, fluid);
                        if (resistance.isPresent()) {
                            float f3 = resistance.get();
                            if (inWater) {
                                f3 *= rand.nextFloat() * 0.2F + 0.2F;
                            }
                            f3 /= Math.max(1.0F, blockPower);
                            f1 -= (f3 + 0.3F) * 0.3F;
                        }
                        if (f1 > 0.0F && (explosion == null
                                || explosion.calculator.shouldBlockExplode(explosion, level, pos, state, f1))) {
                            set.add(pos);
                        }
                        d0 += d3 * 0.3F;
                        d1 += d4 * 0.3F;
                        d2 += d5 * 0.3F;
                    }
                }
            }
        }
        return new ArrayList<>(set);
    }

    @SuppressWarnings("DataFlowIssue") // vanilla blocks ignore the explosion; modded ones get none on the client
    private static Optional<Float> plainResistance(Level level, BlockPos pos, BlockState state, FluidState fluid) {
        return state.isAir() && fluid.isEmpty() ? Optional.empty()
                : Optional.of(Math.max(state.getExplosionResistance(level, pos, null),
                        fluid.getExplosionResistance(level, pos, null)));
    }

    /**
     * Upstream's entity damage: {@code (int) ((d² + d) / 2 × 8 × damagePower + 1)} where {@code d} is
     * the closeness ({@code 1 - distance / (2 × radius)}) times the exposure; through-wall blasts use
     * full exposure but an occluded target takes {@code throughWallFactor} of the damage.
     */
    public static float entityDamage(double closeness, double exposure, float damagePower, boolean throughWall,
            float throughWallFactor) {
        double d9 = throughWall ? 1.0 : exposure;
        double d10 = closeness * d9;
        float damage = (int) ((d10 * d10 + d10) / 2.0 * 8.0 * damagePower + 1.0);
        if (throughWall && exposure < 1.0) {
            damage *= Math.max(0.0F, Math.min(1.0F, throughWallFactor));
        }
        return damage;
    }

    /** Upstream {@code doExplosionA}: finds the blocks and hurts and pushes the entities. */
    public void doExplosionA() {
        this.world.gameEvent(this.exploder, GameEvent.EXPLODE, this.center());
        this.affectedBlocks.addAll(rayMarchBlocks(this.world, this, this.x, this.y, this.z, this.explosionSizeBlock,
                this.explosionBlockPower, this.isInWater, this.world.getRandom()));
        float damageRadius = this.explosionDamageRadius > 0.0F ? this.explosionDamageRadius : this.size;
        float f = damageRadius * 2.0F;
        int i = Mth.floor(this.x - f - 1.0);
        int j = Mth.floor(this.x + f + 1.0);
        int k = Mth.floor(this.y - f - 1.0);
        int l1 = Mth.floor(this.y + f + 1.0);
        int i2 = Mth.floor(this.z - f - 1.0);
        int j2 = Mth.floor(this.z + f + 1.0);
        List<Entity> list = this.world.getEntities(this.exploder, new AABB(i, k, i2, j, l1, j2));
        EventHooks.onExplosionDetonate(this.world, this, list, this.affectedBlocks);
        Vec3 center = new Vec3(this.x, this.y, this.z);
        DamageSource ds = this.getDamageSource();
        for (Entity entity : list) {
            double d7 = Math.sqrt(entity.distanceToSqr(this.x, this.y, this.z)) / f;
            if (d7 > 1.0) {
                continue;
            }
            double d0 = entity.getX() - this.x;
            double d1 = entity.getY() + entity.getEyeHeight() - this.y;
            double d2 = entity.getZ() - this.z;
            double d8 = Math.sqrt(d0 * d0 + d1 * d1 + d2 * d2);
            if (d8 == 0.0) {
                continue;
            }
            d0 /= d8;
            d1 /= d8;
            d2 /= d8;
            double blockDensity = getBlockDensity(this.world, center, entity.getBoundingBox());
            double d10 = (1.0 - d7) * (this.explosionThroughWall ? 1.0 : blockDensity);
            float damage = entityDamage(1.0 - d7, blockDensity, this.explosionDamagePower, this.explosionThroughWall,
                    this.explosionThroughWallFactor);
            if (damage > 0.0F && !(entity instanceof ItemEntity) && !(entity instanceof ThrownExperienceBottle)
                    && !(entity instanceof ExperienceOrb) && !(entity instanceof FallingBlockEntity)) {
                if (!(entity instanceof WeaponTargets.Projectile projectile) || this.explodedPlayer == null
                        || projectile.getShooter() != this.explodedPlayer) {
                    this.result.hitEntity = true;
                }
            }
            DamageScaling.applyHurtResistantTime(entity);
            damage = DamageScaling.applyDamageVsEntity(entity, damage);
            damage *= this.damageFactor != null ? this.damageFactor.getDamageFactor(WeaponTargets.targetName(entity))
                    : 1.0F;
            var blood = io.github.mcheliceport.effect.VehicleBlood.projectile(entity, this.exploder, ds,
                    io.github.mcheliceport.effect.VehicleBloodRules.BLAST, entity.position().subtract(center));
            boolean applied = entity.hurtServer(this.world, ds, damage);
            io.github.mcheliceport.effect.VehicleBlood.finish(blood, applied);
            double d11 = d10;
            if (entity instanceof LivingEntity living) {
                d11 = d10 * (1.0 - living.getAttributeValue(Attributes.EXPLOSION_KNOCKBACK_RESISTANCE));
            }
            if (!(entity instanceof WeaponTargets.Projectile)) {
                entity.setDeltaMovement(entity.getDeltaMovement().add(d0 * d11 * 0.4, d1 * d11 * 0.1, d2 * d11 * 0.4));
                if (entity instanceof Player) {
                    entity.hurtMarked = true;
                }
            }
            if (damage > 0.0F && this.countSetFireEntity > 0) {
                double fireFactor = 1.0 - d8 / f;
                if (fireFactor > 0.0) {
                    entity.igniteForSeconds((int) (fireFactor * this.countSetFireEntity));
                }
            }
        }
    }

    /**
     * 1.12's {@code Explosion#getBlockDensity}: the share of points of the box that see the centre
     * past colliding blocks.
     */
    static double getBlockDensity(Level level, Vec3 center, AABB bb) {
        double d0 = 1.0 / ((bb.maxX - bb.minX) * 2.0 + 1.0);
        double d1 = 1.0 / ((bb.maxY - bb.minY) * 2.0 + 1.0);
        double d2 = 1.0 / ((bb.maxZ - bb.minZ) * 2.0 + 1.0);
        if (d0 < 0.0 || d1 < 0.0 || d2 < 0.0) {
            return 0.0;
        }
        int seen = 0;
        int count = 0;
        for (float fx = 0.0F; fx <= 1.0F; fx = (float) (fx + d0)) {
            for (float fy = 0.0F; fy <= 1.0F; fy = (float) (fy + d1)) {
                for (float fz = 0.0F; fz <= 1.0F; fz = (float) (fz + d2)) {
                    Vec3 from = new Vec3(bb.minX + (bb.maxX - bb.minX) * fx, bb.minY + (bb.maxY - bb.minY) * fy,
                            bb.minZ + (bb.maxZ - bb.minZ) * fz);
                    if (level.clip(new ClipContext(from, center, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE,
                            net.minecraft.world.phys.shapes.CollisionContext.empty())).getType() == HitResult.Type.MISS) {
                        seen++;
                    }
                    count++;
                }
            }
        }
        return (double) seen / count;
    }

    /** Upstream {@code doExplosionB(false)} on the server: the sound, broken blocks and fire. */
    public void doExplosionB() {
        RandomSource rand = this.world.getRandom();
        if (this.isPlaySound) {
            // Consume the original pitch draws here: later fire/drop randomness must not shift.
            float pitch = (1.0F + (rand.nextFloat() - rand.nextFloat()) * 0.2F) * 0.7F;
            ModNetwork.sendToPlayersNear(this.world, null, this.x, this.y, this.z, 64,
                    new io.github.mcheliceport.net.ExplosionSoundPayload(this.x, this.y, this.z, this.size, pitch));
        }
        if (this.damagesTerrain && this.isDestroyBlock && this.explosionSizeBlock > 0.0F
                && CommonConfig.get(CommonConfig.EXPLOSION_DESTROY_BLOCK)) {
            List<StackCollector> stacks = new ArrayList<>();
            for (BlockPos pos : this.affectedBlocks) {
                BlockState state = this.world.getBlockState(pos);
                if (!state.isAir()) {
                    // Vanilla drops with a survival chance of 1 / radius, which is sizeBlock here, as upstream.
                    state.onExplosionHit(this.world, pos, this, (stack, at) -> StackCollector.add(stacks, stack, at));
                }
            }
            for (StackCollector stack : stacks) {
                Block.popResource(this.world, stack.pos, stack.stack);
            }
        }
        if (this.causesFire && this.fireBlocks && CommonConfig.get(CommonConfig.EXPLOSION_FLAMING_BLOCK)) {
            for (BlockPos pos : this.affectedBlocks) {
                if (this.world.getBlockState(pos).isAir() && this.world.getBlockState(pos.below()).isSolidRender()
                        && rand.nextInt(3) == 0) {
                    this.world.setBlockAndUpdate(pos, BaseFireBlock.getState(this.world, pos));
                }
            }
        }
    }

    public Result getResult() {
        return this.result;
    }

    public List<BlockPos> getAffectedBlocks() {
        return this.affectedBlocks;
    }

    @Override
    public @Nullable LivingEntity getIndirectSourceEntity() {
        // Upstream getExplosivePlacedBy: the player behind the explosion, else vanilla's rule.
        return this.explodedPlayer != null ? this.explodedPlayer : super.getIndirectSourceEntity();
    }

    /** Merges dropped stacks as vanilla's explosion does. */
    private static final class StackCollector {
        private final BlockPos pos;
        private ItemStack stack;

        private StackCollector(BlockPos pos, ItemStack stack) {
            this.pos = pos;
            this.stack = stack;
        }

        static void add(List<StackCollector> stacks, ItemStack stack, BlockPos pos) {
            for (StackCollector collector : stacks) {
                collector.tryMerge(stack);
                if (stack.isEmpty()) {
                    return;
                }
            }
            stacks.add(new StackCollector(pos, stack));
        }

        private void tryMerge(ItemStack input) {
            if (ItemEntity.areMergable(this.stack, input)) {
                this.stack = ItemEntity.merge(this.stack, input, 16);
            }
        }
    }
}
