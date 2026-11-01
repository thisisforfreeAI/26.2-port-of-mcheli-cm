package io.github.mcheliceport.fabric.mixin.client;

import io.github.mcheliceport.client.particle.ParticleLayers;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.client.particle.ParticleEngine;
import net.minecraft.client.particle.ParticleRenderType;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

/** Vanilla draws only its own particle render types; NeoForge draws every type in use. */
@Mixin(ParticleEngine.class)
abstract class ParticleEngineMixin {
    private static List<ParticleRenderType> mcheli$order;

    @Redirect(method = "render", at = @At(value = "FIELD",
            target = "Lnet/minecraft/client/particle/ParticleEngine;RENDER_ORDER:Ljava/util/List;"))
    private List<ParticleRenderType> mcheli$renderOrder(List<ParticleRenderType> order) {
        if (mcheli$order == null) {
            List<ParticleRenderType> all = new ArrayList<>(order);
            all.addAll(ParticleLayers.TYPES);
            mcheli$order = List.copyOf(all);
        }
        return mcheli$order;
    }
}
