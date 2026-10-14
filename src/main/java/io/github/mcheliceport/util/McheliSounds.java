package io.github.mcheliceport.util;

import net.minecraft.core.Holder;
import net.minecraft.resources.Identifier;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.Level;

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

    /**
     * Upstream {@code MCH_SoundEvents#playSound}: a sound at a position for everyone nearby, in the
     * master category; does nothing on the client.
     */
    public static void play(Level level, double x, double y, double z, String name, float volume, float pitch) {
        if (level.isClientSide() || name == null || name.isEmpty()) {
            return;
        }
        level.playSound(null, x, y, z, event(name), SoundSource.MASTER, volume, pitch);
    }

    /** A sound only this client hears ({@code World#playSound(..., false)} on the client). */
    public static void playLocal(Level level, double x, double y, double z, String name, SoundSource source,
            float volume, float pitch) {
        if (level.isClientSide() && name != null && !name.isEmpty()) {
            level.playLocalSound(x, y, z, event(name).value(), source, volume, pitch, false);
        }
    }
}
