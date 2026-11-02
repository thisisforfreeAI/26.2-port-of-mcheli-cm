package io.github.mcheliceport.fabric.mixin.client;

import java.util.List;
import net.minecraft.client.particle.ParticleEngine;
import net.minecraft.client.particle.ParticleRenderType;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(ParticleEngine.class)
public interface ParticleEngineAccessor {
    @Accessor("RENDER_ORDER")
    static List<ParticleRenderType> mcheli$renderOrder() {
        throw new AssertionError();
    }
}
