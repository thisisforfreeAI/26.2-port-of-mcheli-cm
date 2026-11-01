package io.github.mcheliceport.platform;

import io.github.mcheliceport.platform.registry.DeferredRegister;
import net.minecraft.world.entity.EntityType;

/** How each loader fills its registries from the shared {@link DeferredRegister}s. */
public interface Registrar {

    /** Registers every entry of {@code register} (now, or when the loader's registry event fires). */
    <R> void register(DeferredRegister<R> register);

    /** Makes entities of the built type send their velocity on every tracking update. */
    void alwaysUpdateVelocity(EntityType.Builder<?> builder);
}
