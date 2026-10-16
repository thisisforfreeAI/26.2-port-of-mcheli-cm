package io.github.mcheliceport.menu;

import io.github.mcheliceport.entity.uav.UavStationEntity;
import java.util.UUID;
import java.util.List;
import io.github.mcheliceport.net.UavRosterPayload.Row;
import io.github.mcheliceport.net.UavRosterPayload;
import io.github.mcheliceport.net.UavRosterActionPayload;

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

/** Current menu, operator and stable UAV UUID authorize roster and deployment actions. */
public class UavStationMenu extends AbstractContainerMenu {
    private final @Nullable UavStationEntity station;
    private final Player viewer;
    private List<Row> rows = List.of();
    private @Nullable UUID selected;
    private @Nullable UavRosterPayload previousRoster;
    private int rosterTicks;
    public UavStationMenu(int id, Inventory inventory, RegistryFriendlyByteBuf data) {
        this(id, inventory, inventory.player.level().getEntity(data.readVarInt()) instanceof UavStationEntity s ? s : null);
    }
    public UavStationMenu(int id, Inventory inventory, @Nullable UavStationEntity station) {
        super(ModMenuTypes.UAV_STATION.get(), id); this.station = station; this.viewer = inventory.player;
        selected = station != null ? station.lastId() : null;
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
    public List<Row> rows() { return rows; }
    public @Nullable UUID selected() { return selected; }
    public @Nullable Row selectedRow() {
        return rows.stream().filter(r -> r.id().equals(selected)).findFirst().orElse(null);
    }
    public void applyRoster(UavRosterPayload p) {
        if (viewer.level().isClientSide() && station != null && p.menuId() == containerId && p.stationId() == station.getId()) { rows = p.rows(); selected = p.selected(); }
    }
    public boolean rosterAction(Player player, UavRosterActionPayload p) {
        if (player.level().isClientSide() || player != viewer || player.containerMenu != this || !stillValid(player)
                || p.menuId() != containerId || p.stationId() != station.getId() || !station.pairedUavs().contains(p.uav())) { return false; }
        if (p.action() == 0) { selected = p.uav(); return true; }
        if (p.action() == 1) { return station.connect(player, p.uav()); }
        if (p.action() == 2) { return station.unpair(player, p.uav(), true); }
        return false;
    }
    @Override public void broadcastChanges() {
        super.broadcastChanges();
        if (station == null || viewer.level().isClientSide() || !stillValid(viewer) || ++rosterTicks % 5 != 0) { return; }
        var ids = station.pairedUavs();
        if (selected == null || !ids.contains(selected)) { selected = station.lastId() != null && ids.contains(station.lastId()) ? station.lastId() : ids.isEmpty() ? null : ids.getFirst(); }
        rows = ids.stream().map(id -> {
            var ac = station.resolve(id);
            if (ac == null || ac.getAcInfo() == null) { return new Row(id, id.toString().substring(0, 8), "Not loaded", 0, 0, 0, -1); }
            String name = ac.getAcInfo().displayName; if (name.length() > 128) { name = name.substring(0, 128); }
            return new Row(id, name, station.connectionStatus(ac),
                    (int) (ac.getFuelPercentage() * 100), ac.getHP(), ac.getMaxHP(), (int) station.distanceTo(ac));
        }).toList();
        var p = new UavRosterPayload(containerId, station.getId(), rows, selected);
        if (!p.equals(previousRoster) && viewer instanceof ServerPlayer sp) { net.neoforged.neoforge.network.PacketDistributor.sendToPlayer(sp, p); previousRoster = p; }
    }
    public static void open(ServerPlayer player, UavStationEntity station) {
        player.openMenu(new SimpleMenuProvider((id, inventory, p) -> new UavStationMenu(id, inventory, station),
                Component.literal(station.handheld() ? "UAV Tablet" : station.small() ? "Portable UAV Controller" : "UAV Station")), b -> b.writeVarInt(station.getId()));
    }
    @Override public boolean stillValid(Player player) { return station != null && station.usableBy(player); }
    @Override public boolean clickMenuButton(Player player, int id) {
        if (player.level().isClientSide() || !stillValid(player) || player.containerMenu != this) { return false; }
        if (id == 0) { boolean deployed = station.deploy(player); if (deployed) { selected = station.lastId(); } return deployed; }
        if (id == 1) { return station.connect(player); }
        if (id == 2) { return station.exit(player); }
        if (id >= 3 && id < 9 && !station.handheld()) { station.adjust((id - 3) / 2, id % 2 == 0 ? 1 : -1); return true; }
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
