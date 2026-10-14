package io.github.mcheliceport.util;

import net.minecraft.core.Holder;
import net.minecraft.resources.Identifier;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.Entity;

/**
 * Plays McHeli's sound events (the {@code mcheli} namespace of the generated {@code sounds.json})
 * by name, as upstream's {@code W_WorldFunc.MOD_playSoundAtEntity} did.
 *
 * <p>The events are not in the sound event registry (content may name new ones), so they travel
 * as direct holders.
 */
public final class McheliSounds {

    public static final String NAMESPACE = "mcheli";

    private McheliSounds() {
    }

    /** The event of a McHeli sound name such as {@code helidmg}. */
    public static Holder<SoundEvent> event(String name) {
        return Holder.direct(SoundEvent.createVariableRangeEvent(Identifier.fromNamespaceAndPath(NAMESPACE, name)));
    }

    /** Plays a sound at an entity for everyone nearby; does nothing on the client. */
    public static void playAt(Entity entity, String name, float volume, float pitch) {
        if (entity.level().isClientSide() || name.isEmpty()) {
            return;
        }
        entity.level().playSound(null, entity.getX(), entity.getY(), entity.getZ(), event(name),
                SoundSource.NEUTRAL, volume, pitch);
    }
}
