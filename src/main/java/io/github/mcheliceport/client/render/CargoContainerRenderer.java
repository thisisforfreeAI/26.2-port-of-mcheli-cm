package io.github.mcheliceport.client.render;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import io.github.mcheliceport.client.model.BulletModels;
import io.github.mcheliceport.client.model.McheliModel;
import io.github.mcheliceport.entity.container.CargoContainerEntity;
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

/** Stock container OBJ, tint and transforms through the supported submission pipeline. */
public class CargoContainerRenderer extends EntityRenderer<CargoContainerEntity, CargoContainerRenderer.State> {
    private static final Identifier TEXTURE = Identifier.fromNamespaceAndPath("mcheli", "textures/container.png");
    public static class State extends EntityRenderState {
        public @Nullable List<McheliModel.Group> groups;
        public float yaw, pitch;
    }
    public CargoContainerRenderer(EntityRendererProvider.Context context) { super(context); this.shadowRadius = 0.5F; }
    @Override public State createRenderState() { return new State(); }
    @Override protected AABB getBoundingBoxForCulling(CargoContainerEntity entity) {
        var model = BulletModels.get("container");
        if (model == null) { return entity.getBoundingBox(); }
        double radius = Math.max(Math.max(Math.abs(model.minX), Math.abs(model.maxX)),
                Math.max(Math.abs(model.minZ), Math.abs(model.maxZ))) * Math.sqrt(2);
        return entity.getBoundingBox().minmax(new AABB(-radius, model.minY + 0.3, -radius, radius, model.maxY + 0.3, radius).move(entity.position()));
    }
    @Override public void extractRenderState(CargoContainerEntity entity, State state, float partialTick) {
        super.extractRenderState(entity, state, partialTick);
        var model = BulletModels.get("container");
        state.groups = model == null ? null : model.groups;
        state.yaw = -(entity.yRotO + Mth.wrapDegrees(entity.getYRot() - entity.yRotO) * partialTick);
        state.pitch = entity.xRotO + Mth.wrapDegrees(entity.getXRot() - entity.xRotO) * partialTick;
    }
    @Override public void submit(State state, PoseStack stack, SubmitNodeCollector collector, CameraRenderState camera) {
        if (state.groups != null) {
            var groups = state.groups; int light = state.lightCoords;
            stack.pushPose(); stack.translate(0, 0.3, 0);
            stack.mulPose(Axis.YP.rotationDegrees(state.yaw)); stack.mulPose(Axis.XP.rotationDegrees(state.pitch));
            collector.submitCustomGeometry(stack, AircraftRenderTypes.aircraft(TEXTURE),
                    (pose, buffer) -> ModelVertices.emit(buffer, pose, groups, 0xFFBFBFBF, light, false));
            stack.popPose();
        }
        super.submit(state, stack, collector, camera);
    }
}
