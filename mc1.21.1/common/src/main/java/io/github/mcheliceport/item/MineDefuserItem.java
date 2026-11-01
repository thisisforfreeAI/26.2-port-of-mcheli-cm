package io.github.mcheliceport.item;

import java.util.function.Consumer;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;

/**
 * The mine defuser (upstream {@code MCH_ItemMineDefuser}). Using it on a mine removes the mine;
 * that happens in {@code BombEntity#interact}. Sneaking keeps a player-fuzed mine from going off
 * until the player is within a block of it.
 */
public class MineDefuserItem extends Item {

    public MineDefuserItem(Properties properties) {
        super(properties);
    }

    @Override
    public void appendHoverText(ItemStack stack, Item.TooltipContext context, java.util.List<Component> tooltip$list, TooltipFlag flag) { java.util.function.Consumer<Component> tooltip = tooltip$list::add;
        tooltip.accept(Component.translatable("item.mcheli_ce_port.mine_defuser.tooltip")
                .withStyle(ChatFormatting.DARK_GRAY));
    }
}
