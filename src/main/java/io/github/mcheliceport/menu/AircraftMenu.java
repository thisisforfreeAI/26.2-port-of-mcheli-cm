package io.github.mcheliceport.menu;

import io.github.mcheliceport.compat.energy.EnergyCompat;
import io.github.mcheliceport.compat.fluid.FuelFluids;
import io.github.mcheliceport.entity.aircraft.AircraftEntity;
import io.github.mcheliceport.registry.ModItems;
import io.github.mcheliceport.registry.ModMenuTypes;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.Container;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.SimpleMenuProvider;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import org.jspecify.annotations.Nullable;

/**
 * A vehicle's fuel slots and trunk (decision D12): the fuel-can part of upstream's aircraft GUI
 * ({@code MCH_AircraftGuiContainer}) and its storage panel ({@code ContainerGui}) in one vanilla
 * menu. Plane ejection reserves use two separate slots; remaining settings come in M6.
 *
 * <p>Slot 0 takes fuel cans, slot 1 receives the empty cans, and the trunk follows. Reserve slots
 * follow the trunk in the menu, backed by separate persistent storage on the vehicle.
 */
public class AircraftMenu extends AbstractContainerMenu {

    /** Left edge of the trunk and the player inventory; the fuel panel sits left of it. */
    public static final int CONTENT_X = 38;
    public static final int COLUMNS = 9;

    private final @Nullable AircraftEntity aircraft;
    private final Container container;
    private final int trunkSize;
    private final int parachuteSlots;

    /** Client side, from the open-menu packet: the vehicle's entity ID and trunk size. */
    public AircraftMenu(int containerId, Inventory playerInventory, RegistryFriendlyByteBuf data) {
        this(containerId, playerInventory, findAircraft(playerInventory.player, data.readVarInt()), data.readVarInt(),
                data.readVarInt());
    }

    public AircraftMenu(int containerId, Inventory playerInventory, AircraftEntity aircraft) {
        this(containerId, playerInventory, aircraft,
                aircraft.getInventory().getContainerSize() - AircraftEntity.FIXED_SLOTS, aircraft.getNumEjectionSeat());
    }

    private AircraftMenu(int containerId, Inventory playerInventory, @Nullable AircraftEntity aircraft,
            int trunkSize, int parachuteSlots) {
        super(ModMenuTypes.AIRCRAFT.get(), containerId);
        this.aircraft = aircraft;
        this.trunkSize = Math.max(trunkSize, 0);
        this.parachuteSlots = Math.clamp(parachuteSlots, 0, 2);
        Container inventory = aircraft != null ? aircraft.getInventory() : null;
        if (inventory == null || inventory.getContainerSize() != AircraftEntity.FIXED_SLOTS + this.trunkSize) {
            inventory = new SimpleContainer(AircraftEntity.FIXED_SLOTS + this.trunkSize);
        }
        this.container = inventory;
        this.container.startOpen(playerInventory.player);

        this.addSlot(new Slot(this.container, AircraftEntity.SLOT_FUEL_IN, 11, 18) {
            @Override
            public boolean mayPlace(ItemStack stack) {
                return AircraftMenu.this.isFuelSlotItem(stack);
            }
        });
        this.addSlot(new Slot(this.container, AircraftEntity.SLOT_FUEL_OUT, 11, 18 + 3 * 18) {
            @Override
            public boolean mayPlace(ItemStack stack) {
                return false;
            }
        });
        for (int i = 0; i < this.trunkSize; i++) {
            this.addSlot(new Slot(this.container, AircraftEntity.FIXED_SLOTS + i, CONTENT_X + 8 + i % COLUMNS * 18,
                    18 + i / COLUMNS * 18));
        }
        Container chutes = aircraft != null ? aircraft.getParachuteInventory() : new SimpleContainer(2);
        for (int i = 0; i < this.parachuteSlots; i++) {
            this.addSlot(new Slot(chutes, i, 11, 118 + i * 18) {
                @Override
                public boolean mayPlace(ItemStack stack) {
                    return stack.is(ModItems.PARACHUTE.get());
                }

                @Override
                public int getMaxStackSize() {
                    return 1;
                }
            });
        }
        int inventoryY = this.getPlayerInventoryY();
        for (int row = 0; row < 3; row++) {
            for (int col = 0; col < 9; col++) {
                this.addSlot(new Slot(playerInventory, col + row * 9 + 9, CONTENT_X + 8 + col * 18,
                        inventoryY + row * 18));
            }
        }
        for (int col = 0; col < 9; col++) {
            this.addSlot(new Slot(playerInventory, col, CONTENT_X + 8 + col * 18, inventoryY + 58));
        }
    }

    private static @Nullable AircraftEntity findAircraft(Player player, int entityId) {
        Entity entity = player.level().getEntity(entityId);
        return entity instanceof AircraftEntity aircraft ? aircraft : null;
    }

    /** Opens the menu for a player (upstream {@code openContainer}/{@code openGui}). */
    public static void open(ServerPlayer player, AircraftEntity aircraft) {
        int trunkSize = aircraft.getInventory().getContainerSize() - AircraftEntity.FIXED_SLOTS;
        player.openMenu(new SimpleMenuProvider((id, inventory, p) -> new AircraftMenu(id, inventory, aircraft),
                aircraft.getDisplayName()), buf -> {
                    buf.writeVarInt(aircraft.getId());
                    buf.writeVarInt(trunkSize);
                    buf.writeVarInt(aircraft.getNumEjectionSeat());
                });
    }

    /**
     * Upstream {@code isFuelSlotItem}: a fuel can, any fluid container, or a battery for an
     * electric vehicle that takes one.
     */
    private boolean isFuelSlotItem(ItemStack stack) {
        return isFuelCan(stack) || FuelFluids.isFluidContainer(stack)
                || this.aircraft != null && this.aircraft.allowBattery() && EnergyCompat.isBattery(stack);
    }

    /**
     * A fuel can with fuel left. Upstream's slot also took empty cans, which then sat there doing
     * nothing and looked like a full can that would not refuel.
     */
    public static boolean isFuelCan(ItemStack stack) {
        return stack.is(ModItems.FUEL.get()) && stack.getDamageValue() < stack.getMaxDamage();
    }

    public @Nullable AircraftEntity getAircraft() {
        return this.aircraft;
    }

    public int getTrunkSize() {
        return this.trunkSize;
    }

    public int getParachuteSlots() {
        return this.parachuteSlots;
    }

    public int getParachuteSlotStart() {
        return AircraftEntity.FIXED_SLOTS + this.trunkSize;
    }

    /** Rows the trunk takes; at least three, so the fuel panel fits beside it. */
    public int getTrunkRows() {
        return Math.max(3, (this.trunkSize + COLUMNS - 1) / COLUMNS);
    }

    public int getPlayerInventoryY() {
        return 18 + this.getTrunkRows() * 18 + 13;
    }

    @Override
    public ItemStack quickMoveStack(Player player, int index) {
        Slot slot = this.slots.get(index);
        if (!slot.hasItem()) {
            return ItemStack.EMPTY;
        }
        ItemStack stack = slot.getItem();
        ItemStack original = stack.copy();
        int vehicleSlots = this.getParachuteSlotStart() + this.parachuteSlots;
        if (index < vehicleSlots) {
            if (!this.moveItemStackTo(stack, vehicleSlots, this.slots.size(), true)) {
                return ItemStack.EMPTY;
            }
        } else if (this.isFuelSlotItem(stack) && this.moveItemStackTo(stack, AircraftEntity.SLOT_FUEL_IN,
                AircraftEntity.SLOT_FUEL_IN + 1, false)) {
            // Moved into the fuel slot.
        } else if (stack.is(ModItems.PARACHUTE.get()) && this.parachuteSlots > 0
                && this.moveItemStackTo(stack, this.getParachuteSlotStart(), vehicleSlots, false)) {
            // Moved into a parachute slot.
        } else if (this.trunkSize == 0
                || !this.moveItemStackTo(stack, AircraftEntity.FIXED_SLOTS, this.getParachuteSlotStart(), false)) {
            return ItemStack.EMPTY;
        }
        if (stack.isEmpty()) {
            slot.setByPlayer(ItemStack.EMPTY);
        } else {
            slot.setChanged();
        }
        return original;
    }

    @Override
    public boolean stillValid(Player player) {
        return this.aircraft == null || this.aircraft.stillValid(player);
    }

    @Override
    public void removed(Player player) {
        super.removed(player);
        this.container.stopOpen(player);
    }
}
