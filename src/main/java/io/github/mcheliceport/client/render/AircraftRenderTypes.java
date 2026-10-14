package io.github.mcheliceport.client.render;

import java.util.function.Function;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.client.renderer.rendertype.RenderSetup;
import net.minecraft.client.renderer.rendertype.RenderType;
import net.minecraft.resources.Identifier;
import net.minecraft.util.Util;

/**
 * The render type vehicle models draw with: vanilla's {@code ENTITY_TRANSLUCENT_CULL} pipeline,
 * which matches upstream's GL state for vehicles ({@code W_Render#setCommonRenderParam}: back faces
 * culled, alpha blending, alpha test) and needs no shader of our own, so it runs on every backend.
 * Translucent quads are sorted, where upstream drew them unsorted.
 */
public final class AircraftRenderTypes {

    private static final Function<Identifier, RenderType> AIRCRAFT = Util.memoize(texture -> RenderType.create(
            "mcheli_ce_port_aircraft",
            RenderSetup.builder(RenderPipelines.ENTITY_TRANSLUCENT_CULL)
                    .withTexture("Sampler0", texture)
                    .useLightmap()
                    .useOverlay()
                    .affectsCrumbling()
                    .sortOnUpload()
                    .setOutline(RenderSetup.OutlineProperty.AFFECTS_OUTLINE)
                    .createRenderSetup()));

    private AircraftRenderTypes() {
    }

    public static RenderType aircraft(Identifier texture) {
        return AIRCRAFT.apply(texture);
    }
}
