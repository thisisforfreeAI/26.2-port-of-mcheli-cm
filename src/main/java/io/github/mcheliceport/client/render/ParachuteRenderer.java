package io.github.mcheliceport.client.render;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import io.github.mcheliceport.client.model.BulletModels;
import io.github.mcheliceport.client.model.McheliModel;
import io.github.mcheliceport.entity.parachute.ParachuteEntity;
import java.util.List;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.state.EntityRenderState;
import net.minecraft.client.renderer.state.level.CameraRenderState;
import net.minecraft.resources.Identifier;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.AABB;
import org.jspecify.annotations.Nullable;

/** Upstream's two parachute models and the ejection seat's $seat/$parachute groups. */
public class ParachuteRenderer extends EntityRenderer<ParachuteEntity, ParachuteRenderer.State> {
    public static class State extends EntityRenderState {
        public @Nullable List<McheliModel.Group> groups;
        public Identifier texture = Identifier.fromNamespaceAndPath("mcheli", "textures/parachute1.png");
        public float yaw;
    }

    public ParachuteRenderer(EntityRendererProvider.Context context) {
        super(context);
        this.shadowRadius = 0.5F;
    }

    @Override
    public State createRenderState() {
        return new State();
    }

    @Override
    protected AABB getBoundingBoxForCulling(ParachuteEntity entity) {
        McheliModel model = entity.getModelType() > 0
                ? BulletModels.get(entity.getModelType() == 1 ? "parachute1" : "parachute2") : null;
        if (model == null) {
            return entity.getBoundingBox();
        }
        double radius = Math.max(Math.max(Math.abs(model.minX), Math.abs(model.maxX)),
                Math.max(Math.abs(model.minZ), Math.abs(model.maxZ))) * Math.sqrt(2);
        return entity.getBoundingBox().minmax(new AABB(-radius, model.minY, -radius, radius, model.maxY, radius)
                .move(entity.position()));
    }

    @Override
    public void extractRenderState(ParachuteEntity entity, State state, float partialTick) {
        super.extractRenderState(entity, state, partialTick);
        int type = entity.getModelType();
        McheliModel model = type > 0 ? BulletModels.get(type == 1 ? "parachute1" : "parachute2") : null;
        state.groups = model == null ? null : type == 1 ? model.groups
                : model.part(type == 2 && !entity.isOpenParachute() ? "$seat" : "$parachute");
        state.texture = Identifier.fromNamespaceAndPath("mcheli", "textures/parachute" + (type == 1 ? 1 : 2) + ".png");
        state.yaw = -(entity.yRotO + Mth.wrapDegrees(entity.getYRot() - entity.yRotO) * partialTick);
    }

    @Override
    public void submit(State state, PoseStack poseStack, SubmitNodeCollector collector, CameraRenderState camera) {
        if (state.groups != null) {
            List<McheliModel.Group> groups = state.groups;
            int light = state.lightCoords;
            poseStack.pushPose();
            poseStack.mulPose(Axis.YP.rotationDegrees(state.yaw));
            collector.submitCustomGeometry(poseStack, AircraftRenderTypes.aircraft(state.texture),
                    (pose, buffer) -> ModelVertices.emit(buffer, pose, groups, 0xFFBFBFBF, light, false));
            poseStack.popPose();
        }
        super.submit(state, poseStack, collector, camera);
    }
}
