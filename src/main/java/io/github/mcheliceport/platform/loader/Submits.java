package io.github.mcheliceport.platform.loader;

import net.minecraft.client.renderer.OrderedSubmitNodeCollector;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.feature.submit.SubmitNode;
import net.neoforged.neoforge.client.submit.RenderPhaseKey;
import net.neoforged.neoforge.client.submit.RenderPhaseKeys;

/** Custom submit nodes (NeoForge: {@code submitSpecial} with a {@code RenderPhaseKey}). */
public final class Submits {

    private Submits() {
    }

    /** Submits a custom node to the solid phase; only the vanilla collectors keep it. */
    public static void solid(OrderedSubmitNodeCollector collector, SubmitNode node) {
        collector.submitSpecial(RenderPhaseKeys.SOLID, node);
    }

    /** A collector that passes the loader's custom submits on to {@link #out()}. */
    public abstract static class Forwarding implements SubmitNodeCollector {
        protected abstract OrderedSubmitNodeCollector out();

        @Override
        public <T extends SubmitNode, S extends T> void submitSpecial(RenderPhaseKey<T> phaseKey, S submitNode) {
            this.out().submitSpecial(phaseKey, submitNode);
        }

        @Override
        public void submitMultiLayerBlockModel(com.mojang.blaze3d.vertex.PoseStack poseStack,
                java.util.List<net.minecraft.client.renderer.block.dispatch.BlockStateModelPart> modelParts,
                boolean translucent, int[] tintLayers, int lightCoords, int overlayCoords, int outlineColor) {
            this.out().submitMultiLayerBlockModel(poseStack, modelParts, translucent, tintLayers, lightCoords,
                    overlayCoords, outlineColor);
        }
    }
}
