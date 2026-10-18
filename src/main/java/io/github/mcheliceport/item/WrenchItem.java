package io.github.mcheliceport.item;

import io.github.mcheliceport.entity.aircraft.AircraftEntity;
import io.github.mcheliceport.entity.seat.SeatEntity;
import io.github.mcheliceport.registry.ModTags;
import io.github.mcheliceport.util.McheliSounds;
import java.util.function.Consumer;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.ProjectileUtil;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.ItemUseAnimation;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.component.TooltipDisplay;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

/**
 * The wrench (upstream {@code MCH_ItemWrench}): hold use while looking at a vehicle within four
 * blocks to repair it by 10 every second, one durability each; shift-use on a vehicle changes its
 * skin ({@code AircraftEntity#interact}). As a tool it mines 1.12's iron blocks fast and hits like
 * an iron tool, sometimes knocking iron or gunpowder out of what it hits.
 */
public class WrenchItem extends Item {

    /** Upstream {@code getMouseOver}'s reach. */
    public static final double REACH = 4.0;
    public static final int REPAIR_INTERVAL = 20;
    public static final int REPAIR_AMOUNT = 10;

    public WrenchItem(Properties properties) {
        super(properties);
    }

    /**
     * Upstream {@code MCH_ClientProxy#getMouseOverAircraft}: the vehicle (or the vehicle of the
     * seat) the user looks at within {@link #REACH} blocks, unless a block is closer.
     */
    public static @Nullable AircraftEntity aimedAircraft(LivingEntity user) {
        Vec3 eyes = user.getEyePosition();
        Vec3 look = user.getViewVector(1.0F);
        Vec3 end = eyes.add(look.scale(REACH));
        double max = REACH;
        HitResult block = user.level().clip(new ClipContext(eyes, end, ClipContext.Block.OUTLINE,
                ClipContext.Fluid.NONE, user));
        if (block.getType() != HitResult.Type.MISS) {
            max = block.getLocation().distanceTo(eyes);
        }
        AABB search = user.getBoundingBox().expandTowards(look.scale(REACH)).inflate(1.0);
        EntityHitResult hit = ProjectileUtil.getEntityHitResult(user, eyes, eyes.add(look.scale(max)), search,
                e -> e.isPickable() && (e instanceof AircraftEntity || e instanceof SeatEntity), max * max);
        if (hit == null) {
            return null;
        }
        Entity entity = hit.getEntity();
        return entity instanceof AircraftEntity ac ? ac : entity instanceof SeatEntity seat ? seat.getParent() : null;
    }

    @Override
    public InteractionResult use(Level level, Player player, InteractionHand hand) {
        player.startUsingItem(hand);
        return InteractionResult.CONSUME;
    }

    @Override
    public int getUseDuration(ItemStack stack, LivingEntity user) {
        return 72000;
    }

    @Override
    public ItemUseAnimation getUseAnimation(ItemStack stack) {
        return ItemUseAnimation.BLOCK;
    }

    /** Upstream {@code onUsingTick}: every second of holding, repairs the vehicle in view. */
    @Override
    public void onUseTick(Level level, LivingEntity user, ItemStack stack, int remainingTicks) {
        if (level.isClientSide() || remainingTicks >= this.getUseDuration(stack, user)
                || remainingTicks % REPAIR_INTERVAL != 0) {
            return;
        }
        AircraftEntity ac = aimedAircraft(user);
        if (ac != null && ac.getHP() > 0 && ac.repair(REPAIR_AMOUNT)) {
            stack.hurtAndBreak(1, user, user.getUsedItemHand() == InteractionHand.OFF_HAND
                    ? EquipmentSlot.OFFHAND : EquipmentSlot.MAINHAND);
            float pitch = 0.9F + level.getRandom().nextFloat() * 0.2F;
            McheliSounds.play(level, (int) ac.getX(), (int) ac.getY(), (int) ac.getZ(), "wrench", 1.0F, pitch);
        }
    }

    @Override
    public float getDestroySpeed(ItemStack stack, BlockState state) {
        if (state.is(ModTags.WRENCH_METAL)) {
            return 20.5F;
        }
        return state.is(ModTags.WRENCH_LOGIC) ? 5.5F : 2.0F;
    }

    @Override
    public boolean isCorrectToolForDrops(ItemStack stack, BlockState state) {
        return state.is(ModTags.WRENCH_METAL) || state.is(ModTags.WRENCH_LOGIC);
    }

    @Override
    public boolean mineBlock(ItemStack stack, Level level, BlockState state, BlockPos pos, LivingEntity miner) {
        if (state.getDestroySpeed(level, pos) != 0.0F) {
            stack.hurtAndBreak(2, miner, EquipmentSlot.MAINHAND);
        }
        return true;
    }

    /** Upstream {@code hitEntity}: a 1 in 40 chance of an iron ingot, else 1 in 20 of gunpowder. */
    @Override
    public void hurtEnemy(ItemStack stack, LivingEntity target, LivingEntity attacker) {
        if (attacker.level() instanceof ServerLevel server) {
            if (server.getRandom().nextInt(40) == 0) {
                target.spawnAtLocation(server, new ItemStack(Items.IRON_INGOT), 0.0F);
            } else if (server.getRandom().nextInt(20) == 0) {
                target.spawnAtLocation(server, new ItemStack(Items.GUNPOWDER), 0.0F);
            }
        }
    }

    @Override
    public void postHurtEnemy(ItemStack stack, LivingEntity target, LivingEntity attacker) {
        stack.hurtAndBreak(2, attacker, EquipmentSlot.MAINHAND);
    }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext context, TooltipDisplay display,
            Consumer<Component> tooltip, TooltipFlag flag) {
        tooltip.accept(Component.translatable("item.mcheli_ce_port.wrench.tooltip").withStyle(ChatFormatting.YELLOW));
    }
}
