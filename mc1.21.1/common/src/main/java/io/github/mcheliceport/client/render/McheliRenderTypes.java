package io.github.mcheliceport.client.render;

import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.VertexFormat;
import java.util.List;
import net.minecraft.client.renderer.RenderStateShard;
import net.minecraft.client.renderer.RenderType;

/**
 * The port's own 1.21.1 render types, which 26.2 builds from render pipelines. It extends
 * {@link RenderType} to reach the protected state shards, and builds each type from the public
 * constructor with the shards' setup and clear, since vanilla's {@code create} is not visible.
 */
public final class McheliRenderTypes extends RenderType {

    /** Additive position-colour triangles that test but do not write depth: glows, beams and lamps. */
    public static final RenderType GLOW = of("mcheli_ce_port_glow", DefaultVertexFormat.POSITION_COLOR,
            VertexFormat.Mode.TRIANGLES, true, List.of(RENDERTYPE_LIGHTNING_SHADER, LIGHTNING_TRANSPARENCY,
                    COLOR_WRITE, NO_CULL, LEQUAL_DEPTH_TEST));

    private McheliRenderTypes(String name, VertexFormat format, VertexFormat.Mode mode, int bufferSize,
            boolean crumbling, boolean sort, Runnable setup, Runnable clear) {
        super(name, format, mode, bufferSize, crumbling, sort, setup, clear);
    }

    private static RenderType of(String name, VertexFormat format, VertexFormat.Mode mode, boolean sort,
            List<RenderStateShard> shards) {
        return new McheliRenderTypes(name, format, mode, 1536, false, sort,
                () -> shards.forEach(RenderStateShard::setupRenderState),
                () -> shards.forEach(RenderStateShard::clearRenderState));
    }
}
