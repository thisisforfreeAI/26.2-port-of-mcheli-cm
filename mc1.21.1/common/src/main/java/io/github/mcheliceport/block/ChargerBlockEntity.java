package io.github.mcheliceport.block;

import io.github.mcheliceport.compat.energy.EnergyCompat;
import io.github.mcheliceport.config.ServerConfig;
import io.github.mcheliceport.entity.aircraft.AircraftEntity;
import io.github.mcheliceport.registry.ModBlocks;
import net.minecraft.core.BlockPos;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import io.github.mcheliceport.shim.ValueInput;
import io.github.mcheliceport.shim.ValueOutput;
import net.minecraft.world.phys.AABB;

/**
 * The charger's buffer (upstream {@code MCH_TileEntityCharger}). Cables and generators push
 * energy in through NeoForge's energy capability, nothing can take it out, and every 10 ticks it
 * feeds the electric vehicles in range: first each vehicle's own store, then a battery in its fuel
 * slot when the vehicle takes one.
 */
public class ChargerBlockEntity extends BlockEntity {

    private int energy;
    private int updateCounter;
    private final Handler handler = new Handler();

    public ChargerBlockEntity(BlockPos pos, BlockState state) {
        super(ModBlocks.CHARGER_ENTITY.get(), pos, state);
    }

    public Handler energyHandler() {
        return this.handler;
    }

    public int getEnergy() {
        return this.energy;
    }

    public int getCapacity() {
        return Math.max(0, ServerConfig.CHARGER_CAPACITY.get());
    }

    /** Upstream {@code setEnergyInternal}, clamped to the capacity. */
    public void setEnergy(int value) {
        this.energy = Math.max(0, Math.min(this.getCapacity(), value));
        this.setChanged();
    }

    /** Upstream {@code update}: every 10 ticks, when it holds any energy. */
    public static void serverTick(Level level, BlockPos pos, BlockState state, ChargerBlockEntity charger) {
        if (++charger.updateCounter < 10) {
            return;
        }
        charger.updateCounter = 0;
        if (charger.energy > 0) {
            charger.distributeToAircraft();
        }
    }

    /** Upstream {@code distributeToAircraft}: one shared budget of the transfer rate per update. */
    private void distributeToAircraft() {
        double range = ServerConfig.CHARGER_RANGE.get();
        if (range <= 0.0 || this.level == null) {
            return;
        }
        int budget = Math.max(0, ServerConfig.CHARGER_TRANSFER_RATE.get());
        for (AircraftEntity aircraft : this.level.getEntitiesOfClass(AircraftEntity.class,
                new AABB(this.worldPosition).inflate(range))) {
            if (this.energy <= 0 || budget <= 0) {
                break;
            }
            if (!aircraft.isElectric()) {
                continue;
            }
            int want = aircraft.getMaxFuel() - aircraft.getFuel();
            if (want > 0) {
                int accepted = aircraft.addEnergy(Math.min(Math.min(budget, this.energy), want));
                this.energy -= accepted;
                budget -= accepted;
            }
            if (this.energy > 0 && budget > 0 && aircraft.allowBattery()) {
                ItemStack battery = aircraft.getInventory().getItem(AircraftEntity.SLOT_FUEL_IN);
                int stored = EnergyCompat.chargeToItem(battery, Math.min(budget, this.energy));
                if (stored > 0) {
                    aircraft.getInventory().setChanged();
                }
                this.energy -= stored;
                budget -= stored;
            }
        }
        this.setChanged();
    }

    @Override
    protected void loadAdditional(net.minecraft.nbt.CompoundTag input$tag, net.minecraft.core.HolderLookup.Provider input$registries) { ValueInput input = ValueInput.of(input$tag, input$registries);
        super.loadAdditional(input$tag, input$registries);
        this.energy = Math.max(0, input.getIntOr("Energy", 0));
    }

    @Override
    protected void saveAdditional(net.minecraft.nbt.CompoundTag output$tag, net.minecraft.core.HolderLookup.Provider output$registries) { ValueOutput output = ValueOutput.of(output$tag, output$registries);
        super.saveAdditional(output$tag, output$registries);
        output.putInt("Energy", this.energy);
    }

    /** Upstream's Forge Energy side: a sink with no limit per call but the room left. */
    public final class Handler {

        public int amount() {
            return ChargerBlockEntity.this.energy;
        }

        public int capacity() {
            return ChargerBlockEntity.this.getCapacity();
        }

        public int insert(int amount, boolean simulate) {
            int accepted = Math.max(0, Math.min(amount, ChargerBlockEntity.this.getCapacity()
                    - ChargerBlockEntity.this.energy));
            if (accepted > 0 && !simulate) {
                ChargerBlockEntity.this.energy += accepted;
                ChargerBlockEntity.this.setChanged();
            }
            return accepted;
        }
    }
}
