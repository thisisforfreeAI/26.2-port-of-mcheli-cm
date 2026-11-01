package io.github.mcheliceport.item;

import io.github.mcheliceport.entity.aircraft.AircraftEntity;
import io.github.mcheliceport.registry.ModItems;
import net.minecraft.core.Direction;
import net.minecraft.core.dispenser.BlockSource;
import net.minecraft.core.dispenser.DefaultDispenseItemBehavior;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.DispenserBlock;

/**
 * Dispensers place vehicles two blocks in front of them, facing south (upstream's
 * {@code MCH_ItemAircraftDispenseBehavior}). Creative-only vehicles and UAVs are not placed, and
 * the item stays in the dispenser.
 */
public class AircraftDispenseBehavior extends DefaultDispenseItemBehavior {

    /** Registers the behaviour for every vehicle item that can be placed. */
    public static void register() {
        AircraftDispenseBehavior behavior = new AircraftDispenseBehavior();
        for (ModItems.ContentItem contentItem : ModItems.contentItems()) {
            if (contentItem.item().get() instanceof AircraftItem item && item.isPlaceable()) {
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
        if (stack.getItem() instanceof AircraftItem item) {
            AircraftEntity aircraft = item.onTileClick(source.level(), 0.0F, (int) x, (int) y, (int) z);
            if (aircraft != null && aircraft.getAcInfo() != null && !aircraft.getAcInfo().creativeOnly
                    && !aircraft.getAcInfo().isUAV) {
                aircraft.readDataFromItem(stack);
                source.level().addFreshEntity(aircraft);
                stack.shrink(1);
            }
        }
        return stack;
    }
}
