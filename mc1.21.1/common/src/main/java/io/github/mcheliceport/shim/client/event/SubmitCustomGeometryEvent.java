package io.github.mcheliceport.shim.client.event;

import com.mojang.blaze3d.vertex.PoseStack;
import io.github.mcheliceport.shim.client.CameraRenderState;
import io.github.mcheliceport.shim.client.SubmitNodeCollector;

/**
 * NeoForge's level-wide custom geometry: posted after the level's translucent pass with a pose
 * stack in camera-relative world space (26.2 posts it while the level is extracted).
 */
public final class SubmitCustomGeometryEvent extends ClientEvent {
    private final PoseStack poseStack;
    private final SubmitNodeCollector collector;
    private final LevelRenderState level;

    public SubmitCustomGeometryEvent(PoseStack poseStack, SubmitNodeCollector collector) {
        this.poseStack = poseStack;
        this.collector = collector;
        this.level = new LevelRenderState(CameraRenderState.current());
    }

    public PoseStack getPoseStack() {
        return poseStack;
    }

    public SubmitNodeCollector getSubmitNodeCollector() {
        return collector;
    }

    public LevelRenderState getLevelRenderState() {
        return level;
    }

    /** The part of 26.2's {@code LevelRenderState} the listeners read. */
    public static final class LevelRenderState {
        public final CameraRenderState cameraRenderState;

        LevelRenderState(CameraRenderState camera) {
            this.cameraRenderState = camera;
        }
    }
}
