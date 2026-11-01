package io.github.mcheliceport.platform.loader;

import net.minecraft.world.entity.Entity;
import net.neoforged.neoforge.entity.PartEntity;

/**
 * A part of a multipart entity (a vehicle hit box): NeoForge's {@link PartEntity}. It is never
 * added to a level on its own; {@link SpawnDataEntity#partEntities()} lists it.
 */
public abstract class PartEntityBase<T extends Entity> extends PartEntity<T> {

    protected PartEntityBase(T parent) {
        super(parent);
    }
}
