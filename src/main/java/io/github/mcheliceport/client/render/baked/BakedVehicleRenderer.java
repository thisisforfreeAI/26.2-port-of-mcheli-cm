package io.github.mcheliceport.client.render.baked;

import com.mojang.blaze3d.buffers.GpuBufferSlice;
import com.mojang.blaze3d.pipeline.RenderPipeline;
import com.mojang.blaze3d.pipeline.RenderTarget;
import com.mojang.blaze3d.systems.RenderPass;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.textures.FilterMode;
import com.mojang.blaze3d.textures.GpuSampler;
import com.mojang.blaze3d.textures.GpuTextureView;
import io.github.mcheliceport.client.model.McheliModel;
import io.github.mcheliceport.client.model.VehicleSurfaceShade;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.OptionalDouble;
import net.minecraft.client.renderer.feature.FeatureFrameContext;
import net.minecraft.client.renderer.feature.FeatureRenderer;
import net.minecraft.client.renderer.rendertype.OutputTarget;
import net.minecraft.client.renderer.texture.AbstractTexture;

/**
 * Draws {@link BakedVehicles.Submit}s. Preparing a group (before the frame is drawn) uploads model
 * groups not yet on the GPU and writes each part's uniform block; executing it (with the solid entities) opens one render pass for the whole group, as
 * {@code PreparedRenderType#drawFromBuffer} does for one draw.
 */
final class BakedVehicleRenderer implements FeatureRenderer<BakedVehicles.Submit> {

    private record Draw(GpuTextureView texture, GpuSampler sampler, GpuBufferSlice part, BakedModelCache.Baked model) {
    }

    /** The group's draws: with fog, and without it for vehicles past the render distance. */
    private record Prepared(GpuBufferSlice transforms, List<Draw> draws, List<Draw> farDraws) {
    }

    private final List<Prepared> groups = new ArrayList<>();

    @Override
    public void prepareGroup(FeatureFrameContext context, List<BakedVehicles.Submit> submits, boolean strictlyOrdered) {
        List<Draw> draws = new ArrayList<>();
        List<Draw> farDraws = new ArrayList<>();
        GpuBufferSlice transforms = null;
        try {
            transforms = RenderSystem.getDynamicUniforms().writeTransform(RenderSystem.getModelViewMatrixCopy());
            for (BakedVehicles.Submit submit : submits) {
                AbstractTexture texture = context.textureManager().getTexture(submit.texture());
                // As ModelVertices: baked shade colours replace only the base colour.
                int flags = (submit.flat() ? BakedVehicles.FLAG_FLAT : 0)
                        | (submit.surfaceShade()
                                ? BakedVehicles.FLAG_SURFACE_SHADE : 0);
                for (BakedVehicles.Part part : submit.parts()) {
                    GpuBufferSlice uniform = null;
                    for (McheliModel.Group group : part.groups()) {
                        BakedModelCache.Baked model = BakedVehicles.CACHE.get(group);
                        if (model == null) {
                            continue;
                        }
                        if (uniform == null) {
                            uniform = BakedVehicles.parts().writeUniform(new BakedVehicles.PartUniform(part.partMat(),
                                    part.normalMat(), submit.color(), submit.light(), flags));
                        }
                        (submit.far() ? farDraws : draws).add(new Draw(texture.getTextureView(), texture.getSampler(),
                                uniform, model));
                    }
                }
            }
        } catch (RuntimeException e) {
            BakedVehicles.fail(e);
            draws.clear();
            farDraws.clear();
        }
        this.groups.add(new Prepared(transforms, draws, farDraws));
    }

    @Override
    public void executeGroup(FeatureFrameContext context, int groupIndex, List<BakedVehicles.Submit> submits,
            boolean strictlyOrdered) {
        Prepared prepared = this.groups.get(groupIndex);
        if (prepared.transforms() == null) {
            return;
        }
        draw(context, prepared.transforms(), BakedVehicles.PIPELINE, prepared.draws());
        draw(context, prepared.transforms(), BakedVehicles.PIPELINE_FAR, prepared.farDraws());
    }

    private static void draw(FeatureFrameContext context, GpuBufferSlice transforms, RenderPipeline pipeline,
            List<Draw> draws) {
        if (draws.isEmpty()) {
            return;
        }
        RenderTarget target = OutputTarget.MAIN_TARGET.getRenderTarget();
        GpuTextureView color = RenderSystem.outputColorTextureOverride != null
                ? RenderSystem.outputColorTextureOverride : target.getColorTextureView();
        GpuTextureView depth = target.useDepth
                ? (RenderSystem.outputDepthTextureOverride != null ? RenderSystem.outputDepthTextureOverride
                        : target.getDepthTextureView())
                : null;
        try (RenderPass pass = RenderSystem.getDevice().createCommandEncoder().createRenderPass(
                () -> "McHeli vehicles from GPU buffers", color, Optional.empty(), depth, OptionalDouble.empty())) {
            pass.setPipeline(pipeline);
            RenderSystem.bindDefaultUniforms(pass);
            pass.setUniform("DynamicTransforms", transforms);
            pass.bindTexture("Sampler2", context.lightmap(), RenderSystem.getSamplerCache().getClampToEdge(FilterMode.LINEAR));
            GpuTextureView bound = null;
            for (Draw draw : draws) {
                if (draw.texture() != bound) {
                    pass.bindTexture("Sampler0", draw.texture(), draw.sampler());
                    bound = draw.texture();
                }
                pass.setUniform("McheliPart", draw.part());
                pass.setVertexBuffer(0, draw.model().buffer().slice());
                pass.draw(draw.model().vertexCount(), 1, 0, 0);
            }
        } catch (RuntimeException e) {
            BakedVehicles.fail(e);
        }
    }

    @Override
    public void finishExecute(FeatureFrameContext context) {
        this.groups.clear();
    }

    @Override
    public void close() {
        BakedVehicles.CACHE.clear();
    }
}
