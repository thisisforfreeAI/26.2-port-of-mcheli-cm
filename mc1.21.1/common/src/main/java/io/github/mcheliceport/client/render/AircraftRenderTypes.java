package io.github.mcheliceport.client.render;

import net.minecraft.client.renderer.RenderType;
import net.minecraft.resources.ResourceLocation;

/**
 * The render type vehicle models draw with: vanilla's {@code entity_translucent_cull}, which matches
 * upstream's GL state for vehicles ({@code W_Render#setCommonRenderParam}: back faces culled, alpha
 * blending, alpha test) and needs no shader of our own.
 */
public final class AircraftRenderTypes {

    private AircraftRenderTypes() {
    }

    public static RenderType aircraft(ResourceLocation texture) {
        return RenderType.entityTranslucentCull(texture);
    }
}
