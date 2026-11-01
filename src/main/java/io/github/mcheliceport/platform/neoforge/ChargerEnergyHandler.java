package io.github.mcheliceport.platform.neoforge;

import io.github.mcheliceport.block.ChargerBlockEntity;
import net.neoforged.neoforge.transfer.energy.EnergyHandler;
import net.neoforged.neoforge.transfer.transaction.SnapshotJournal;
import net.neoforged.neoforge.transfer.transaction.TransactionContext;

/** The charger's NeoForge energy capability (upstream's Forge Energy side): a sink with no limit per call but the room left. */
final class ChargerEnergyHandler extends SnapshotJournal<Integer> implements EnergyHandler {
    private final ChargerBlockEntity charger;

    ChargerEnergyHandler(ChargerBlockEntity charger) {
        this.charger = charger;
    }

    @Override
    public long getAmountAsLong() {
        return this.charger.getEnergy();
    }

    @Override
    public long getCapacityAsLong() {
        return this.charger.getCapacity();
    }

    @Override
    public int insert(int amount, TransactionContext transaction) {
        int accepted = Math.max(0, Math.min(amount, this.charger.getCapacity() - this.charger.getEnergy()));
        if (accepted > 0) {
            this.updateSnapshots(transaction);
            this.charger.setEnergy(this.charger.getEnergy() + accepted);
        }
        return accepted;
    }

    @Override
    public int extract(int amount, TransactionContext transaction) {
        return 0;
    }

    @Override
    protected Integer createSnapshot() {
        return this.charger.getEnergy();
    }

    @Override
    protected void revertToSnapshot(Integer snapshot) {
        this.charger.setEnergy(snapshot);
    }

    @Override
    protected void onRootCommit(Integer originalState) {
        if (this.charger.getEnergy() != originalState) {
            this.charger.setChanged();
        }
    }
}
