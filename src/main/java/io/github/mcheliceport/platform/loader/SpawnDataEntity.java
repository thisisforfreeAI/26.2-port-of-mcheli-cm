package io.github.mcheliceport.platform.loader;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.level.Level;
import net.neoforged.neoforge.entity.IEntityWithComplexSpawn;
import net.neoforged.neoforge.entity.PartEntity;

/**
 * An entity that sends extra data with its spawn packet and may have {@link PartEntityBase parts}.
 * On NeoForge this maps onto {@link IEntityWithComplexSpawn} and NeoForge's multipart hooks.
 */
public abstract class SpawnDataEntity extends Entity implements IEntityWithComplexSpawn {
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

    @Override
    public final void writeSpawnData(RegistryFriendlyByteBuf buffer) {
        this.writeExtraSpawnData(buffer);
    }

    @Override
    public final void readSpawnData(RegistryFriendlyByteBuf buffer) {
        this.readExtraSpawnData(buffer);
    }

    @Override
    public final boolean isMultipartEntity() {
        return this.partEntities().length > 0;
    }

    @Override
    public final PartEntity<?>[] getParts() {
        return this.partEntities();
    }
}
