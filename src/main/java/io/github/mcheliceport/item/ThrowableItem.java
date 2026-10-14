package io.github.mcheliceport.item;

import io.github.mcheliceport.content.ContentRegistries;
import io.github.mcheliceport.content.throwable.ThrowableInfo;
import io.github.mcheliceport.entity.throwable.ThrowableEntity;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.ItemUseAnimation;
import net.minecraft.world.level.Level;
import org.jspecify.annotations.Nullable;

/**
 * The item of one throwable definition, such as a grenade or a smoke grenade (upstream
 * {@code MCH_ItemThrowable}): hold use to wind up like a bow, release to throw. Held under 35
 * ticks the throw is weaker, never under a fifth of full strength.
 */
public class ThrowableItem extends Item {

    private final String contentName;

    public ThrowableItem(Properties properties, String contentName) {
        super(properties);
        this.contentName = contentName;
    }

    public String getContentName() {
        return this.contentName;
    }

    /** The current definition of this throwable, or null if it is no longer loaded. */
    public @Nullable ThrowableInfo getThrowableInfo() {
        return ContentRegistries.get().throwable().get(this.contentName);
    }

    @Override
    public InteractionResult use(Level level, Player player, InteractionHand hand) {
        player.startUsingItem(hand);
        return InteractionResult.CONSUME;
    }

    @Override
    public boolean releaseUsing(ItemStack stack, Level level, LivingEntity entity, int remainingTime) {
        if (!(entity instanceof Player player) || stack.isEmpty()) {
            return false;
        }
        ThrowableInfo info = this.getThrowableInfo();
        if (info == null) {
            return false;
        }
        stack.consume(1, player);
        level.playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.ARROW_SHOOT,
                SoundSource.PLAYERS, 0.5F, 0.4F / (level.getRandom().nextFloat() * 0.4F + 0.8F));
        if (!level.isClientSide()) {
            float acceleration = 1.0F;
            int held = this.getUseDuration(stack, entity) - remainingTime;
            if (held <= 35) {
                acceleration = Math.max(held, 5) / 25.0F;
            }
            ThrowableEntity thrown = new ThrowableEntity(level, player);
            thrown.shootFromRotation(player.getXRot(), player.getYRot(), 0.0F, acceleration, 1.0F);
            thrown.setInfo(info);
            level.addFreshEntity(thrown);
        }
        return true;
    }

    @Override
    public int getUseDuration(ItemStack stack, LivingEntity user) {
        return 72000;
    }

    @Override
    public ItemUseAnimation getUseAnimation(ItemStack stack) {
        return ItemUseAnimation.BOW;
    }
}
