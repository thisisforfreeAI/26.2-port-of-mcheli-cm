package io.github.mcheliceport.item;

import net.minecraft.core.BlockPos;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.BucketItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.material.Fluid;
import net.minecraft.world.phys.BlockHitResult;
import org.jspecify.annotations.Nullable;

/**
 * The bucket of fuel that upstream got from Forge's {@code addBucketForFluid}. The fluid has no
 * block, so as upstream the bucket is not emptied into the world; it fills vehicles and tanks.
 */
public class FuelBucketItem extends BucketItem {

    public FuelBucketItem(Fluid content, Properties properties) {
        super(content, properties);
    }

    @Override
    public InteractionResult use(Level level, Player player, InteractionHand hand) {
        return InteractionResult.PASS;
    }

    @Override
    public boolean emptyContents(@Nullable LivingEntity user, Level level, BlockPos pos,
            @Nullable BlockHitResult hitResult) {
        return false;
    }

    @Override
    public boolean emptyContents(@Nullable LivingEntity user, Level level, BlockPos pos,
            @Nullable BlockHitResult hitResult, @Nullable ItemStack container) {
        return false;
    }
}
