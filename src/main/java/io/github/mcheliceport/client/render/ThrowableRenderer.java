package io.github.mcheliceport.client.render;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import io.github.mcheliceport.client.model.BulletModels;
import io.github.mcheliceport.client.model.McheliModel;
import io.github.mcheliceport.entity.throwable.ThrowableEntity;
import java.util.List;
import java.util.Locale;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.state.EntityRenderState;
import net.minecraft.client.renderer.state.level.CameraRenderState;
import net.minecraft.resources.Identifier;
import org.jspecify.annotations.Nullable;

/**
 * Draws a thrown grenade with its model {@code throwable/<name>} and texture. Ported from upstream
 * {@code MCH_RenderThrowable}.
 */
public class ThrowableRenderer extends EntityRenderer<ThrowableEntity, ThrowableRenderer.State> {

    public static class State extends EntityRenderState {
        public @Nullable List<McheliModel.Group> groups;
        public @Nullable Identifier texture;
        public float yaw;
        public float pitch;
    }

    public ThrowableRenderer(EntityRendererProvider.Context context) {
        super(context);
        this.shadowRadius = 0.0F;
    }

    @Override
    public State createRenderState() {
        return new State();
    }

    @Override
    public void extractRenderState(ThrowableEntity entity, State state, float partialTicks) {
        super.extractRenderState(entity, state, partialTicks);
        String name = entity.getInfoName().toLowerCase(Locale.ROOT);
        McheliModel model = name.isEmpty() ? null : BulletModels.get("throwable/" + name);
        state.groups = model != null ? model.groups : null;
        state.texture = Identifier.fromNamespaceAndPath("mcheli", "textures/throwable/" + name + ".png");
        state.yaw = entity.getYRot();
        state.pitch = entity.getXRot();
    }

    @Override
    public void submit(State state, PoseStack poseStack, SubmitNodeCollector collector, CameraRenderState camera) {
        if (state.groups != null && state.texture != null) {
            List<McheliModel.Group> groups = state.groups;
            int light = state.lightCoords;
            poseStack.pushPose();
            poseStack.mulPose(Axis.YP.rotationDegrees(-state.yaw));
            poseStack.mulPose(Axis.XP.rotationDegrees(state.pitch));
            collector.submitCustomGeometry(poseStack, AircraftRenderTypes.aircraft(state.texture),
                    (pose, buffer) -> ModelVertices.emit(buffer, pose, groups, 0xFFFFFFFF, light, false));
            poseStack.popPose();
        }
        super.submit(state, poseStack, collector, camera);
    }
}
