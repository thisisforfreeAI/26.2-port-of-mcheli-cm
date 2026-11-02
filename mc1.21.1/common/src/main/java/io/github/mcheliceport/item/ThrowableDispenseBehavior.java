package io.github.mcheliceport.item;

import io.github.mcheliceport.content.throwable.ThrowableInfo;
import io.github.mcheliceport.entity.throwable.ThrowableEntity;
import io.github.mcheliceport.registry.ModItems;
import net.minecraft.core.Direction;
import net.minecraft.core.dispenser.BlockSource;
import net.minecraft.core.dispenser.DefaultDispenseItemBehavior;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.DispenserBlock;

/**
 * Dispensers throw throwables two blocks in front of them at {@code DispenseAcceleration}
 * (upstream {@code MCH_ItemThrowableDispenseBehavior}).
 */
public class ThrowableDispenseBehavior extends DefaultDispenseItemBehavior {

    /** Registers the behaviour for every throwable item. */
    public static void register() {
        ThrowableDispenseBehavior behavior = new ThrowableDispenseBehavior();
        for (ModItems.ContentItem contentItem : ModItems.contentItems()) {
            if (contentItem.item().get() instanceof ThrowableItem item) {
                DispenserBlock.registerBehavior(item, behavior);
            }
        }
    }

    @Override
    protected ItemStack execute(BlockSource source, ItemStack stack) {
        Direction facing = source.state().getValue(DispenserBlock.FACING);
        double x = source.pos().getX() + facing.getStepX() * 2.0;
        double y = source.pos().getY() + facing.getStepY() * 2.0;
        double z = source.pos().getZ() + facing.getStepZ() * 2.0;
        if (stack.getItem() instanceof ThrowableItem item) {
            ThrowableInfo info = item.getThrowableInfo();
            if (info != null) {
                ServerLevel level = source.level();
                level.playSound(null, x, y, z, SoundEvents.ARROW_SHOOT, SoundSource.BLOCKS, 0.5F,
                        0.4F / (level.getRandom().nextFloat() * 0.4F + 0.8F));
                ThrowableEntity entity = new ThrowableEntity(level, x, y, z);
                entity.setDeltaMovement(facing.getStepX() * info.dispenseAcceleration,
                        facing.getStepY() * info.dispenseAcceleration, facing.getStepZ() * info.dispenseAcceleration);
                entity.setInfo(info);
                level.addFreshEntity(entity);
                stack.shrink(1);
            }
        }
        return stack;
    }
}
