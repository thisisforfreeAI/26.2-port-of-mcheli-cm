package io.github.mcheliceport.entity.weapon;

import com.mojang.authlib.GameProfile;
import io.github.mcheliceport.config.CommonConfig;
import io.github.mcheliceport.content.weapon.WeaponInfo;
import io.github.mcheliceport.registry.ModEntityTypes;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.BlockTags;
import net.minecraft.tags.FluidTags;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.LiquidBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.common.util.FakePlayer;
import net.neoforged.neoforge.common.util.FakePlayerFactory;
import net.neoforged.neoforge.event.EventHooks;
import org.jspecify.annotations.Nullable;

/**
 * A dropped item (a water bucket, bone meal…) used on every block within {@code DispenseRange}
 * of where it lands, by a stand-in player looking down. Water puts out fire and turns lava into
 * obsidian or cobblestone (with block damage on). Ported from upstream
 * {@code MCH_EntityDispensedItem}.
 *
 * <p>Upstream's stand-in copied the shooter's identity; the port's acts as a fixed "[McHeli]" fake
 * player, as NeoForge expects, and only a living player shooter dispenses at all, as upstream.
 * Upstream crashed when the item hit an entity (no block position); the port uses the entity's
 * block.
 */
public class DispensedItemEntity extends BaseBulletEntity {

    private static final GameProfile DISPENSER_PROFILE =
            new GameProfile(UUID.fromString("5b7a3e5d-6f2e-4b8c-9d0a-7c1e2f3a4b5c"), "[McHeli]");

    public DispensedItemEntity(EntityType<? extends DispensedItemEntity> type, Level level) {
        super(type, level);
    }

    public DispensedItemEntity(Level level, double x, double y, double z, double mx, double my, double mz, float yaw,
            float pitch, double acceleration) {
        super(ModEntityTypes.DISPENSED_ITEM.get(), level, x, y, z, mx, my, mz, yaw, pitch, acceleration);
    }

    @Override
    public void tick() {
        super.tick();
        WeaponInfo info = this.getInfo();
        if (info != null && !info.disableSmoke) {
            this.spawnParticle(info.trajectoryParticleName, 3, 7.0F * info.smokeSize);
        }
        if (!this.level().isClientSide() && info != null && !this.isRemoved()) {
            Vec3 m = this.getDeltaMovement();
            if (this.acceleration < 1.0E-4) {
                m = m.multiply(0.999, 1.0, 0.999);
            }
            if (this.isInWater()) {
                m = m.scale(info.velocityInWater);
            }
            this.setDeltaMovement(m);
        }
        this.onUpdateBomblet();
    }

    /** The item the definition dispenses ({@code DispenseItem}), or null. */
    public static @Nullable Item dispenseItem(@Nullable WeaponInfo info) {
        if (info == null || info.dispenseItemLoc == null || info.dispenseItemLoc.isEmpty()) {
            return null;
        }
        Identifier id = Identifier.tryParse(info.dispenseItemLoc.contains(":") ? info.dispenseItemLoc
                : "minecraft:" + info.dispenseItemLoc);
        if (id == null) {
            return null;
        }
        return BuiltInRegistries.ITEM.get(id).map(h -> h.value()).filter(item -> item != Items.AIR).orElse(null);
    }

    @Override
    protected void onImpact(HitResult m, float damageFactor) {
        if (!(this.level() instanceof ServerLevel server)) {
            return;
        }
        this.setBoundingBox(this.getBoundingBox().move(0.0, 2000.0, 0.0));
        Player player = null;
        Item item = null;
        WeaponInfo info = this.getInfo();
        if (info != null) {
            if (this.shootingAircraft instanceof Player p) {
                player = p;
            }
            if (this.shootingEntity instanceof Player p) {
                player = p;
            }
            item = dispenseItem(info);
        }
        if (player != null && player.isAlive() && item != null) {
            BlockPos center = m instanceof BlockHitResult b && m.getType() == HitResult.Type.BLOCK ? b.getBlockPos()
                    : BlockPos.containing(m.getLocation());
            FakePlayer dummyPlayer = FakePlayerFactory.get(server, DISPENSER_PROFILE);
            dummyPlayer.setXRot(90.0F);
            int rng = info.dispenseRange - 1;
            for (int x = -rng; x <= rng; x++) {
                for (int y = -rng; y <= rng; y++) {
                    for (int z = -rng; z <= rng; z++) {
                        int dist = x * x + y * y + z * z;
                        BlockPos pos = center.offset(x, y, z);
                        if (dist <= rng * rng && server.isInValidBounds(pos)
                                && (dist <= 0.5 * rng * rng || this.random.nextInt(2) == 0)) {
                            this.useItemToBlock(server, pos, item, dummyPlayer);
                        }
                    }
                }
            }
        }
        this.discard();
    }

    private void useItemToBlock(ServerLevel level, BlockPos blockpos, Item item, FakePlayer dummyPlayer) {
        dummyPlayer.snapTo(blockpos.getX() + 0.5, blockpos.getY() + 2.5, blockpos.getZ() + 0.5,
                this.random.nextInt(360), 90.0F);
        BlockState state = level.getBlockState(blockpos);
        if (state.isAir()) {
            return;
        }
        if (item == Items.WATER_BUCKET) {
            if (CommonConfig.get(CommonConfig.COLLISION_DESTROY_BLOCK)) {
                if (state.is(BlockTags.FIRE)) {
                    level.removeBlock(blockpos, false);
                } else if (state.getFluidState().is(FluidTags.LAVA) && state.hasProperty(LiquidBlock.LEVEL)) {
                    int lavaLevel = state.getValue(LiquidBlock.LEVEL);
                    if (lavaLevel == 0) {
                        level.setBlockAndUpdate(blockpos, EventHooks.fireFluidPlaceBlockEvent(level, blockpos, blockpos,
                                Blocks.OBSIDIAN.defaultBlockState()));
                    } else if (lavaLevel <= 4) {
                        level.setBlockAndUpdate(blockpos, EventHooks.fireFluidPlaceBlockEvent(level, blockpos, blockpos,
                                Blocks.COBBLESTONE.defaultBlockState()));
                    }
                }
            }
            return;
        }
        ItemStack stack = new ItemStack(item);
        dummyPlayer.setItemInHand(InteractionHand.MAIN_HAND, stack);
        BlockHitResult hit = new BlockHitResult(Vec3.atLowerCornerOf(blockpos), Direction.UP, blockpos, false);
        UseOnContext context = new UseOnContext(dummyPlayer, InteractionHand.MAIN_HAND, hit);
        if (stack.onItemUseFirst(context) == InteractionResult.PASS && stack.useOn(context) == InteractionResult.PASS) {
            stack.use(level, dummyPlayer, InteractionHand.MAIN_HAND);
        }
        dummyPlayer.setItemInHand(InteractionHand.MAIN_HAND, ItemStack.EMPTY);
    }

    @Override
    public void sprinkleBomblet() {
        if (!this.level().isClientSide()) {
            Vec3 m = this.getDeltaMovement();
            DispensedItemEntity e = new DispensedItemEntity(this.level(), this.getX(), this.getY(), this.getZ(), m.x,
                    m.y, m.z, this.random.nextInt(360), 0.0F, this.acceleration);
            e.setParameterFromWeapon(this, this.shootingAircraft, this.shootingEntity);
            e.setName(this.getInfoName());
            float spread = this.getInfo().bombletDiff;
            e.setDeltaMovement(m.x + (this.random.nextFloat() - 0.5F) * spread,
                    m.y / 2.0 + (this.random.nextFloat() - 0.5F) * spread / 2.0F,
                    m.z + (this.random.nextFloat() - 0.5F) * spread);
            e.setBomblet();
            this.level().addFreshEntity(e);
        }
    }

    @Override
    public String getDefaultBulletModelName() {
        return "bomb";
    }
}
