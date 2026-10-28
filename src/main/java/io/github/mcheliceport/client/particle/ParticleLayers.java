package io.github.mcheliceport.client.particle;

import com.mojang.blaze3d.pipeline.BlendFunction;
import com.mojang.blaze3d.pipeline.ColorTargetState;
import com.mojang.blaze3d.pipeline.DepthStencilState;
import com.mojang.blaze3d.pipeline.RenderPipeline;
import com.mojang.blaze3d.platform.CompareOp;
import io.github.mcheliceport.McheliCePort;
import net.minecraft.client.particle.SingleQuadParticle;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.client.renderer.texture.TextureAtlas;
import net.minecraft.resources.Identifier;
import net.neoforged.neoforge.client.event.RegisterRenderPipelinesEvent;

/**
 * The particle layer of the M8 glows: vanilla's particle shaders with additive blending
 * ({@code src alpha, one}) and no depth writes, so overlapping glows brighten each other and never
 * hide the smoke behind them. Vanilla pipeline state only, so it runs on OpenGL and Vulkan.
 */
public final class ParticleLayers {

    static final RenderPipeline ADDITIVE_PIPELINE = RenderPipeline.builder(RenderPipelines.PARTICLE_SNIPPET)
            .withLocation(Identifier.fromNamespaceAndPath(McheliCePort.MOD_ID, "pipeline/additive_particle"))
            .withColorTargetState(new ColorTargetState(BlendFunction.LIGHTNING))
            .withDepthStencilState(new DepthStencilState(CompareOp.GREATER_THAN_OR_EQUAL, false))
            .build();
    /** One instance: the particle renderer groups layers by identity. */
    public static final SingleQuadParticle.Layer ADDITIVE = new SingleQuadParticle.Layer(true,
            TextureAtlas.LOCATION_PARTICLES, ADDITIVE_PIPELINE);

    private static final RenderPipeline ERODING_SMOKE_PIPELINE = RenderPipeline.builder(RenderPipelines.PARTICLE_SNIPPET)
            .withLocation(Identifier.fromNamespaceAndPath(McheliCePort.MOD_ID, "pipeline/eroding_smoke"))
            .withFragmentShader(Identifier.fromNamespaceAndPath(McheliCePort.MOD_ID, "core/eroding_smoke"))
            .withColorTargetState(new ColorTargetState(BlendFunction.TRANSLUCENT))
            .withDepthStencilState(new DepthStencilState(CompareOp.GREATER_THAN_OR_EQUAL, false))
            .build();
    static final SingleQuadParticle.Layer ERODING_SMOKE = new SingleQuadParticle.Layer(true,
            TextureAtlas.LOCATION_PARTICLES, ERODING_SMOKE_PIPELINE);

    private ParticleLayers() {
    }

    public static void registerPipelines(RegisterRenderPipelinesEvent event) {
        event.registerPipeline(ADDITIVE_PIPELINE);
        event.registerPipeline(ERODING_SMOKE_PIPELINE);
    }
}
