package io.github.mcheliceport.platform.loader;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.level.Level;

/**
 * An entity that sends extra data with its spawn packet and may have {@link PartEntityBase parts}:
 * the Fabric twin of the NeoForge class. The spawn data travels in a payload right after the
 * vanilla add-entity packet ({@code ServerEntityMixin}); parts are found through
 * {@code io.github.mcheliceport.platform.fabric.FabricParts}.
 */
public abstract class SpawnDataEntity extends Entity {
    private static final PartEntityBase<?>[] NO_PARTS = new PartEntityBase<?>[0];

    protected SpawnDataEntity(EntityType<?> type, Level level) {
        super(type, level);
    }

    /** Server: writes what the client needs when the entity starts being tracked. */
    protected void writeExtraSpawnData(RegistryFriendlyByteBuf buffer) {
    }

    /** Client: reads what {@link #writeExtraSpawnData} wrote. */
    protected void readExtraSpawnData(RegistryFriendlyByteBuf buffer) {
    }

    /** The entity's parts, which lookups and hit tests treat as entities of their own. */
    public PartEntityBase<?>[] partEntities() {
        return NO_PARTS;
    }

    /** For the Fabric glue: {@link #writeExtraSpawnData}. */
    public final void fabricWriteSpawnData(RegistryFriendlyByteBuf buffer) {
        this.writeExtraSpawnData(buffer);
    }

    /** For the Fabric glue: {@link #readExtraSpawnData}. */
    public final void fabricReadSpawnData(RegistryFriendlyByteBuf buffer) {
        this.readExtraSpawnData(buffer);
    }
}
