package io.github.mcheliceport.shim.client;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import net.minecraft.world.entity.LivingEntity;

/**
 * 26.2's render state modifiers for living entities, cut down to what the port uses: changing
 * the packed light an entity is drawn with. Applied by {@code LivingEntityRendererMixin}.
 */
public final class LivingLightModifiers {
    @FunctionalInterface
    public interface Modifier {
        int apply(LivingEntity entity, float partialTick, int lightCoords);
    }

    private static final List<Modifier> MODIFIERS = new CopyOnWriteArrayList<>();

    private LivingLightModifiers() {}

    public static void register(Modifier modifier) {
        MODIFIERS.add(modifier);
    }

    public static int apply(LivingEntity entity, float partialTick, int lightCoords) {
        for (Modifier m : MODIFIERS) {
            lightCoords = m.apply(entity, partialTick, lightCoords);
        }
        return lightCoords;
    }
}
