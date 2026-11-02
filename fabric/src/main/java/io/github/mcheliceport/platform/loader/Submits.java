package io.github.mcheliceport.platform.loader;

import net.fabricmc.fabric.api.client.rendering.v1.SubmitRenderPhase;
import net.fabricmc.fabric.api.client.rendering.v1.SubmitRenderPhases;
import net.minecraft.client.renderer.OrderedSubmitNodeCollector;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.feature.submit.SubmitNode;

/** Custom submit nodes (Fabric: {@code submitCustom} with a {@link SubmitRenderPhase}). */
public final class Submits {

    private Submits() {
    }

    /** Submits a custom node to the solid phase; only the vanilla collectors keep it. */
    public static void solid(OrderedSubmitNodeCollector collector, SubmitNode node) {
        collector.submitCustom(SubmitRenderPhases.SOLID, node);
    }

    /** A collector that passes the loader's custom submits on to {@link #out()}. */
    public abstract static class Forwarding implements SubmitNodeCollector {
        protected abstract OrderedSubmitNodeCollector out();

        @Override
        public <T extends SubmitNode> void submitCustom(SubmitRenderPhase<T> phase, T node) {
            this.out().submitCustom(phase, node);
        }
    }
}
