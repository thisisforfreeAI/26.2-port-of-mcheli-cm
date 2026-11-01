package io.github.mcheliceport.client.particle;

import com.mojang.blaze3d.platform.GlStateManager;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.BufferBuilder;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.Tesselator;
import com.mojang.blaze3d.vertex.VertexFormat;
import io.github.mcheliceport.shim.client.SingleQuadParticle;
import java.util.List;
import net.minecraft.client.particle.ParticleRenderType;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.client.renderer.texture.TextureAtlas;
import net.minecraft.client.renderer.texture.TextureManager;

/**
 * The port's particle layers. 26.2 gives them their own pipelines; on 1.21.1 they are particle
 * render types that set the blend state: additive glow, and the translucent smoke and condensation
 * layers, which do not write depth so overlapping puffs blend.
 */
public final class ParticleLayers {

    private static ParticleRenderType type(String name, boolean additive) {
        return new ParticleRenderType() {
            @Override
            public BufferBuilder begin(Tesselator tesselator, TextureManager textures) {
                RenderSystem.setShader(GameRenderer::getParticleShader);
                RenderSystem.setShaderTexture(0, TextureAtlas.LOCATION_PARTICLES);
                RenderSystem.enableBlend();
                RenderSystem.depthMask(false);
                if (additive) {
                    RenderSystem.blendFunc(GlStateManager.SourceFactor.SRC_ALPHA, GlStateManager.DestFactor.ONE);
                } else {
                    RenderSystem.defaultBlendFunc();
                }
                return tesselator.begin(VertexFormat.Mode.QUADS, DefaultVertexFormat.PARTICLE);
            }

            @Override
            public String toString() {
                return name;
            }
        };
    }

    public static final ParticleRenderType ADDITIVE_TYPE = type("MCHELI_ADDITIVE", true);
    public static final ParticleRenderType ERODING_SMOKE_TYPE = type("MCHELI_ERODING_SMOKE", false);
    public static final ParticleRenderType CONDENSATION_TYPE = type("MCHELI_CONDENSATION", false);

    /** Drawn after vanilla's sheets; loaders add these to the particle engine's render order. */
    public static final List<ParticleRenderType> TYPES = List.of(ERODING_SMOKE_TYPE, CONDENSATION_TYPE, ADDITIVE_TYPE);

    public static final SingleQuadParticle.Layer ADDITIVE = new SingleQuadParticle.Layer(ADDITIVE_TYPE);
    static final SingleQuadParticle.Layer ERODING_SMOKE = new SingleQuadParticle.Layer(ERODING_SMOKE_TYPE);
    static final SingleQuadParticle.Layer CONDENSATION = new SingleQuadParticle.Layer(CONDENSATION_TYPE);

    private ParticleLayers() {
    }
}
