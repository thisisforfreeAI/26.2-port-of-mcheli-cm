package io.github.mcheliceport.menu;

import io.github.mcheliceport.entity.uav.UavStationEntity;
import io.github.mcheliceport.registry.ModMenuTypes;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.SimpleMenuProvider;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import org.jspecify.annotations.Nullable;

/** Vanilla menu IDs authorize all deployment, connection and offset buttons. */
public class UavStationMenu extends AbstractContainerMenu {
    private final @Nullable UavStationEntity station;
    public UavStationMenu(int id, Inventory inventory, RegistryFriendlyByteBuf data) {
        this(id, inventory, inventory.player.level().getEntity(data.readVarInt()) instanceof UavStationEntity s ? s : null);
    }
    public UavStationMenu(int id, Inventory inventory, @Nullable UavStationEntity station) {
        super(ModMenuTypes.UAV_STATION.get(), id); this.station = station;
        var storage = station != null ? station.inventory() : new SimpleContainer(1);
        addSlot(new Slot(storage, 0, 16, 28) {
            @Override public boolean mayPlace(ItemStack stack) { return station != null && station.accepts(stack); }
            @Override public int getMaxStackSize() { return 1; }
        });
        for (int row = 0; row < 3; row++) {
            for (int col = 0; col < 9; col++) { addSlot(new Slot(inventory, col + row * 9 + 9, 16 + col * 18, 137 + row * 18)); }
        }
        for (int col = 0; col < 9; col++) { addSlot(new Slot(inventory, col, 16 + col * 18, 195)); }
    }
    public @Nullable UavStationEntity station() { return station; }
    public static void open(ServerPlayer player, UavStationEntity station) {
        player.openMenu(new SimpleMenuProvider((id, inventory, p) -> new UavStationMenu(id, inventory, station),
                Component.literal(station.small() ? "Portable UAV Controller" : "UAV Station")), b -> b.writeVarInt(station.getId()));
    }
    @Override public boolean stillValid(Player player) { return station != null && station.usableBy(player); }
    @Override public boolean clickMenuButton(Player player, int id) {
        if (player.level().isClientSide() || !stillValid(player) || player.containerMenu != this) { return false; }
        if (id == 0) { return station.deploy(player); }
        if (id == 1) { return station.connect(player); }
        if (id == 2) { return station.exit(player); }
        if (id >= 3 && id < 9) { station.adjust((id - 3) / 2, id % 2 == 0 ? 1 : -1); return true; }
        return false;
    }
    @Override public ItemStack quickMoveStack(Player player, int index) {
        if (index < 0 || index >= slots.size()) { return ItemStack.EMPTY; }
        var slot = slots.get(index); if (!slot.hasItem()) { return ItemStack.EMPTY; }
        var stack = slot.getItem(); var original = stack.copy();
        if (index == 0) { if (!moveItemStackTo(stack, 1, slots.size(), true)) { return ItemStack.EMPTY; } }
        else if (station == null || !station.accepts(stack) || !moveItemStackTo(stack, 0, 1, false)) { return ItemStack.EMPTY; }
        if (stack.isEmpty()) { slot.setByPlayer(ItemStack.EMPTY); } else { slot.setChanged(); }
        slot.onTake(player, stack); return original;
    }
}
