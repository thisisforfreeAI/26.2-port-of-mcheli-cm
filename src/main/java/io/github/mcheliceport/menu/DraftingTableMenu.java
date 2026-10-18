package io.github.mcheliceport.menu;

import io.github.mcheliceport.block.DraftingTableBlock;
import io.github.mcheliceport.crafting.DraftingTableRecipes;
import io.github.mcheliceport.registry.ModMenuTypes;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.core.registries.Registries;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.Container;
import net.minecraft.world.SimpleMenuProvider;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ResultContainer;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.RecipeHolder;
import io.github.mcheliceport.net.DraftingTableRecipesPayload;
import net.neoforged.neoforge.network.PacketDistributor;

/**
 * The drafting table's menu (upstream {@code MCH_DraftingTableGuiContainer}): the player's
 * inventory and one output slot. The screen picks a recipe and asks the server to make it
 * ({@link #create}); the result waits in the output slot and drops if the screen closes with it
 * there.
 *
 * <p>The client does not know the server's recipes in 26.2, so the server sends the list the
 * screen shows once the menu is open ({@link DraftingTableRecipesPayload}).
 */
public class DraftingTableMenu extends AbstractContainerMenu {

    /** Upstream: within 12 blocks of the table's corner. */
    private static final double MAX_DISTANCE_SQ = 144.0;

    private final Player player;
    private final BlockPos pos;
    private List<DraftingTableRecipes.Entry> recipes;
    private final Container output = new ResultContainer();
    public final int outputSlotIndex;
    /** Client only: the recipe list page hides the slots, as upstream. */
    private boolean slotsShown = true;

    public DraftingTableMenu(int id, Inventory inventory, RegistryFriendlyByteBuf data) {
        this(id, inventory, data.readBlockPos(), List.of());
    }

    public DraftingTableMenu(int id, Inventory inventory, BlockPos pos, List<DraftingTableRecipes.Entry> recipes) {
        super(ModMenuTypes.DRAFTING_TABLE.get(), id);
        this.player = inventory.player;
        this.pos = pos;
        this.recipes = List.copyOf(recipes);
        for (int y = 0; y < 3; y++) {
            for (int x = 0; x < 9; x++) {
                this.addSlot(new TableSlot(inventory, 9 + x + y * 9, 30 + x * 18, 140 + y * 18));
            }
        }
        for (int x = 0; x < 9; x++) {
            this.addSlot(new TableSlot(inventory, x, 30 + x * 18, 198));
        }
        this.outputSlotIndex = this.slots.size();
        this.addSlot(new TableSlot(this.output, 0, 178, 90) {
            @Override
            public boolean mayPlace(ItemStack stack) {
                return false;
            }
        });
    }

    public static void open(ServerPlayer player, BlockPos pos) {
        List<DraftingTableRecipes.Entry> recipes = DraftingTableRecipes.collect(player.level().getServer()
                .getRecipeManager());
        player.openMenu(new SimpleMenuProvider((id, inventory, p) -> new DraftingTableMenu(id, inventory, pos, recipes),
                Component.translatable("block.mcheli_ce_port.drafting_table")), buf -> buf.writeBlockPos(pos));
        if (player.containerMenu instanceof DraftingTableMenu menu) {
            PacketDistributor.sendToPlayer(player, new DraftingTableRecipesPayload(menu.containerId, recipes));
        }
    }

    /** Client: the list the server sent for this menu. */
    public void applyRecipes(DraftingTableRecipesPayload payload) {
        if (this.player.level().isClientSide() && payload.menuId() == this.containerId) {
            this.recipes = List.copyOf(payload.recipes());
        }
    }

    public List<DraftingTableRecipes.Entry> recipes() {
        return this.recipes;
    }

    public boolean hasOutput() {
        return this.getSlot(this.outputSlotIndex).hasItem();
    }

    public void showSlots(boolean shown) {
        this.slotsShown = shown;
    }

    /**
     * Upstream {@code createRecipeItem}: makes the recipe into the output slot when it is empty
     * and the player carries the ingredients, which are used up; creative players need neither.
     * The recipe must still be one the table lists.
     */
    public boolean create(Identifier recipeId) {
        if (this.player.level().isClientSide() || this.player.containerMenu != this || !this.stillValid(this.player)) {
            return false;
        }
        boolean creative = this.player.getAbilities().instabuild;
        if (this.hasOutput() && !creative) {
            return false;
        }
        var server = this.player.level().getServer();
        RecipeHolder<?> holder = server.getRecipeManager()
                .byKey(ResourceKey.create(Registries.RECIPE, recipeId)).orElse(null);
        DraftingTableRecipes.Entry entry = holder != null ? DraftingTableRecipes.entry(holder) : null;
        if (entry == null) {
            return false;
        }
        if (!creative && !DraftingTableRecipes.consume(this.player.getInventory(), entry)) {
            return false;
        }
        this.getSlot(this.outputSlotIndex).set(entry.result().copy());
        this.broadcastChanges();
        return true;
    }

    @Override
    public boolean stillValid(Player player) {
        return player.level().getBlockState(this.pos).getBlock() instanceof DraftingTableBlock
                && player.distanceToSqr(this.pos.getX(), this.pos.getY(), this.pos.getZ()) <= MAX_DISTANCE_SQ;
    }

    /** Upstream {@code transferStackInSlot}: only the output moves, into the inventory. */
    @Override
    public ItemStack quickMoveStack(Player player, int index) {
        if (index != this.outputSlotIndex) {
            return ItemStack.EMPTY;
        }
        Slot slot = this.slots.get(index);
        if (!slot.hasItem()) {
            return ItemStack.EMPTY;
        }
        ItemStack stack = slot.getItem();
        ItemStack original = stack.copy();
        if (!this.moveItemStackTo(stack, 0, 36, true)) {
            return ItemStack.EMPTY;
        }
        slot.onQuickCraft(stack, original);
        if (stack.isEmpty()) {
            slot.setByPlayer(ItemStack.EMPTY);
        } else {
            slot.setChanged();
        }
        if (stack.getCount() == original.getCount()) {
            return ItemStack.EMPTY;
        }
        slot.onTake(player, stack);
        return original;
    }

    /** Upstream {@code onContainerClosed}: a result left in the output slot is dropped. */
    @Override
    public void removed(Player player) {
        super.removed(player);
        if (!player.level().isClientSide()) {
            ItemStack stack = this.output.removeItemNoUpdate(0);
            if (!stack.isEmpty()) {
                player.drop(stack, false);
            }
        }
    }

    private class TableSlot extends Slot {

        TableSlot(Container container, int index, int x, int y) {
            super(container, index, x, y);
        }

        @Override
        public boolean isActive() {
            return DraftingTableMenu.this.slotsShown;
        }
    }
}
