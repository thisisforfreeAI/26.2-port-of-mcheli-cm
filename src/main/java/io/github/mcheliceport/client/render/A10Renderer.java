package io.github.mcheliceport.client.render;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import io.github.mcheliceport.client.model.BulletModels;
import io.github.mcheliceport.client.model.McheliModel;
import io.github.mcheliceport.entity.weapon.A10Entity;
import java.util.List;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.state.EntityRenderState;
import net.minecraft.client.renderer.state.level.CameraRenderState;
import net.minecraft.resources.Identifier;
import net.minecraft.util.Mth;
import org.jspecify.annotations.Nullable;

/** Draws the close air support A-10 once it starts its run. Ported from upstream {@code MCH_RenderA10}. */
public class A10Renderer extends EntityRenderer<A10Entity, A10Renderer.State> {

    private static final Identifier TEXTURE = Identifier.fromNamespaceAndPath("mcheli", "textures/bullets/a10.png");
    private static final int COLOR = 0xFFBFBFBF;

    public static class State extends EntityRenderState {
        public @Nullable List<McheliModel.Group> groups;
        public float yaw;
        public float pitch;
    }

    public A10Renderer(EntityRendererProvider.Context context) {
        super(context);
        this.shadowRadius = 10.5F;
    }

    @Override
    public State createRenderState() {
        return new State();
    }

    @Override
    public void extractRenderState(A10Entity entity, State state, float partialTicks) {
        super.extractRenderState(entity, state, partialTicks);
        McheliModel model = entity.isRender() ? BulletModels.get("a-10") : null;
        state.groups = model != null ? model.groups : null;
        state.yaw = -Mth.lerp(partialTicks, entity.yRotO, entity.getYRot());
        state.pitch = -Mth.lerp(partialTicks, entity.xRotO, entity.getXRot());
    }

    @Override
    public void submit(State state, PoseStack poseStack, SubmitNodeCollector collector, CameraRenderState camera) {
        if (state.groups != null) {
            List<McheliModel.Group> groups = state.groups;
            int light = state.lightCoords;
            poseStack.pushPose();
            poseStack.mulPose(Axis.YP.rotationDegrees(state.yaw));
            poseStack.mulPose(Axis.XP.rotationDegrees(state.pitch));
            collector.submitCustomGeometry(poseStack, AircraftRenderTypes.aircraft(TEXTURE),
                    (pose, buffer) -> ModelVertices.emit(buffer, pose, groups, COLOR, light, false));
            poseStack.popPose();
        }
        super.submit(state, poseStack, collector, camera);
    }
}
